package com.rpgenerator.core.orchestration

import com.rpgenerator.core.agents.GMPromptBuilder
import com.rpgenerator.core.agents.NPCAgent
import com.rpgenerator.core.agents.NarratorAgent
import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.LLMToolCall
import com.rpgenerator.core.api.LLMToolDef
import com.rpgenerator.core.api.LLMToolResult
import com.rpgenerator.core.api.QuestStatus
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.Biome
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.domain.ObjectiveType
import com.rpgenerator.core.domain.Quest
import com.rpgenerator.core.domain.QuestObjective
import com.rpgenerator.core.domain.QuestRewards
import com.rpgenerator.core.domain.QuestType
import com.rpgenerator.core.generation.NPCArchetypeGenerator
import com.rpgenerator.core.rules.CombatEvent
import com.rpgenerator.core.story.CrawlerPlotGraphFactory
import com.rpgenerator.core.story.MainStoryArc
import com.rpgenerator.core.story.NarratorContext
import com.rpgenerator.core.story.StoryFoundation
import com.rpgenerator.core.story.StoryPlanningService
import com.rpgenerator.core.story.WorldSeeds
import com.rpgenerator.core.tools.UnifiedToolContract
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put


internal class GameOrchestrator(
    private val llm: LLMInterface,
    internal var gameState: GameState,
    private val toolContract: UnifiedToolContract
) {
    private val worldSeed = gameState.seedId?.let { WorldSeeds.byId(it) }

    // Agents — lazy so they only appear in debug UI when used
    private val narratorAgent by lazy { NarratorAgent(llm, worldSeed?.narratorPrompt) }
    private val npcAgent by lazy { NPCAgent(llm) }
    private val npcArchetypeGenerator by lazy { NPCArchetypeGenerator(llm) }
    private val storyPlanningService by lazy { StoryPlanningService(llm) }

    // Phase 1+2: Decision agent — calls tools, prose discarded
    private val decideAgent: AgentStream by lazy {
        llm.startAgent(GMPromptBuilder.buildDecidePrompt(gameState))
    }

    // Phase 3: Narration agent — writes prose from TurnSummary, no tools
    private val narrateAgent: AgentStream by lazy {
        llm.startAgent(GMPromptBuilder.buildNarratePrompt(gameState))
    }

    // Legacy single-call GM agent — kept for backward compatibility (server/MCP path)
    private val gmAgent by lazy {
        llm.startAgent(GMPromptBuilder.buildSystemPrompt(gameState))
    }

    // Tool definitions cached for reuse
    private val toolDefs: List<LLMToolDef> by lazy {
        toolContract.getToolDefinitions().map { def ->
            LLMToolDef(
                name = def.name,
                description = def.description,
                parameters = buildJsonObject {
                    put("type", "object")
                    val props = buildJsonObject {
                        for (param in def.parameters) {
                            put(param.name, buildJsonObject {
                                put("type", param.type)
                                put("description", param.description)
                            })
                        }
                    }
                    put("properties", props)
                    val required = def.parameters.filter { it.required }.map { it.name }
                    if (required.isNotEmpty()) {
                        put("required", kotlinx.serialization.json.JsonArray(
                            required.map { kotlinx.serialization.json.JsonPrimitive(it) }
                        ))
                    }
                }
            )
        }
    }

    private val eventLog = mutableListOf<GameEvent>()
    private var storyFoundation: StoryFoundation? = null
    private var storyPlanningStarted = false
    private val backgroundScope = CoroutineScope(Dispatchers.Default)
    private var storyNPCsInitialized = false
    private val storyFoundationDeferred = CompletableDeferred<StoryFoundation?>()
    private var playerTurnCount = 0
    private var lastStoryCheckLevel = 0

    suspend fun processInput(input: String): Flow<GameEvent> = flow {
        // Initialize NPCs on first call
        if (!storyNPCsInitialized) {
            initializeDynamicNPCsSilent()
            storyNPCsInitialized = true
        }

        // Play opening narration on first input
        if (!gameState.hasOpeningNarrationPlayed) {
            // Wait for story planning to finish (up to 8s) so narrator has context
            if (storyFoundation == null) {
                try {
                    withTimeout(8000L) { storyFoundationDeferred.await() }
                } catch (_: Exception) { /* proceed without */ }
            }

            val openingNarration = narratorAgent.narrateOpening(gameState, storyFoundation?.narratorContext)
            emit(GameEvent.NarratorText(openingNarration))
            eventLog.add(GameEvent.NarratorText(openingNarration))
            gameState = gameState.copy(hasOpeningNarrationPlayed = true)

            // Mark story beats up to current level as already delivered,
            // so checkStoryProgression() doesn't re-emit the opening beat.
            lastStoryCheckLevel = gameState.playerLevel

            emitInitialQuestEvents(this)

            // Multimodal atmosphere after opening
            val openingMood = inferOpeningMood(gameState.systemType)
            val musicEvent = GameEvent.MusicChange(mood = openingMood, audioData = null)
            emit(musicEvent)
            eventLog.add(musicEvent)

            val sceneDesc = buildOpeningSceneDescription(gameState)
            val sceneEvent = GameEvent.SceneImage(imageData = ByteArray(0), description = sceneDesc)
            emit(sceneEvent)
            eventLog.add(sceneEvent)

            // Always return after opening — the player's input will be processed on the next call.
            // Without this, the 3-phase turn pipeline runs AGAIN and the narrator re-describes the intro.
            return@flow
        }

        if (input.isBlank()) return@flow

        // Handle death
        if (gameState.isDead) {
            emit(GameEvent.SystemNotification("You are dead. Respawning..."))
            handleDeath(this, "previous combat")
            return@flow
        }

        // ══════════════════════════════════════════════════════════
        // 3-PHASE TURN PIPELINE
        // ══════════════════════════════════════════════════════════
        playerTurnCount++
        val recentEvents = eventLog.takeLast(5).map { eventToString(it) }
        val contextMessage = buildContextMessage(input, recentEvents)

        // ── Phase 1+2: DECIDE & EXECUTE ──────────────────────────
        // GM calls tools (which execute immediately for chaining),
        // but all prose output is DISCARDED.
        val executedTools = mutableListOf<ToolExecutionResult>()
        val pendingEvents = mutableListOf<GameEvent>()
        val combatEvents = mutableListOf<CombatEvent>()
        // Track items added this turn to block fabrication (add_item → use_item same turn)
        val itemsAddedThisTurn = mutableSetOf<String>()

        val executor: suspend (LLMToolCall) -> LLMToolResult = { call ->
            val args = jsonObjectToMap(call.arguments)

            // ── GUARD: Block use of items fabricated this turn ──
            val requestedItemId = args["itemId"]?.toString() ?: ""
            val fabricationBlocked = (call.name == "use_item" || call.name == "combat_use_item") &&
                requestedItemId.isNotBlank() &&
                itemsAddedThisTurn.any { added ->
                    requestedItemId.contains(added, ignoreCase = true) || added.contains(requestedItemId, ignoreCase = true)
                }

            val outcome = if (fabricationBlocked) {
                val itemName = requestedItemId
                val rejection = "REJECTED: Tried to use '$itemName' that was just fabricated this turn"
                pendingEvents.add(GameEvent.SystemNotification(rejection))
                com.rpgenerator.core.tools.ToolOutcome(
                    success = false,
                    error = "Cannot use an item that was just created this turn. The item must exist in inventory BEFORE the turn starts."
                )
            } else {
                toolContract.executeTool(call.name, args, gameState)
            }

            // Apply state mutation
            if (outcome.newState != null) {
                gameState = outcome.newState
            }

            // Track items added this turn for fabrication guard
            if (call.name == "add_item" && outcome.success) {
                val itemName = (args["name"] ?: args["itemName"] ?: "").toString()
                if (itemName.isNotBlank()) itemsAddedThisTurn.add(itemName)
            }

            // Accumulate events
            pendingEvents.addAll(outcome.events)

            // Track combat events for the narration summary
            for (event in outcome.events) {
                when (event) {
                    is GameEvent.CombatLog -> {
                        // Parse combat events from the CombatLog text for structured summary
                    }
                    else -> { /* tracked via pendingEvents */ }
                }
            }

            // Record tool execution for TurnSummary
            executedTools.add(ToolExecutionResult(
                toolName = call.name,
                success = outcome.success,
                resultSummary = summarizeToolResult(call.name, outcome),
                rawData = outcome.data
            ))

            LLMToolResult(
                callId = call.id,
                result = if (outcome.success) {
                    outcome.data
                } else {
                    buildJsonObject { put("error", outcome.error ?: "Unknown error") }
                }
            )
        }

        // Phase 1+2: Send to decide agent — tools execute, but capture text for validation context
        val decideText = decideAgent.sendMessageWithTools(contextMessage, toolDefs, executor).toList().joinToString("")

        // Extract rejection reasons from the decide agent's output (if any)
        val rejections = REJECTION_PATTERN.findAll(decideText)
            .map { it.groupValues[1].trim() }
            .toList()

        // ── Phase 2b: NPC DIALOGUE GENERATION ──────────────────────
        // If talk_to_npc was called, invoke the per-NPC agent to generate real dialogue.
        // This gives each named peer their own voice instead of letting the narrator ventriloquize.
        val npcDialogueResults = mutableListOf<Pair<String, String>>() // npcName -> dialogue
        for (tool in executedTools) {
            if (tool.toolName == "talk_to_npc" && tool.success) {
                val npcName = tool.rawData["npcName"]?.jsonPrimitive?.content ?: continue
                val playerSaid = tool.rawData["playerSaid"]?.jsonPrimitive?.content ?: input
                val npc = gameState.findNPCByName(npcName) ?: continue

                try {
                    val dialogue = npcAgent.generateDialogue(npc, playerSaid, gameState)
                    if (dialogue.isNotBlank()) {
                        npcDialogueResults.add(npc.name to dialogue)
                        val dialogueEvent = GameEvent.NPCDialogue(npc.id, npc.name, dialogue)
                        pendingEvents.add(dialogueEvent)

                        // Update NPC conversation memory
                        val updatedNpc = npc.addConversation(playerSaid, dialogue, gameState.playerLevel)
                        gameState = gameState.updateNPC(updatedNpc)
                    }
                } catch (_: Exception) {
                    // NPC agent failed — narrator will handle dialogue in prose
                }
            }
        }

        // ── Phase 2a: VALIDATION RETRY ──────────────────────────────
        // Check if the decide agent skipped critical action tools.
        // Read-only tools (get_*) don't count as actions — the player's turn needs
        // at least one mutation tool to matter.
        val actionToolNames = executedTools.map { it.toolName }.filter { name ->
            !name.startsWith("get_") && name != "query_lore" && name != "find_npc" &&
            name != "get_combat_status" && name != "get_combat_targets" &&
            name != "get_narration_context" && name != "get_event_summary" &&
            name != "get_tutorial_state" && name != "get_story_state"
        }
        val hasActionTool = actionToolNames.isNotEmpty()

        if (!hasActionTool && rejections.isEmpty()) {
            val missingTools = detectMissingToolCalls(decideText, input)
            if (missingTools.isNotEmpty()) {
                val retryPrompt = buildString {
                    if (executedTools.isEmpty()) {
                        appendLine("You made ZERO tool calls. Your text is DISCARDED — nothing happened.")
                    } else {
                        appendLine("You only called read-only tools (${executedTools.joinToString { it.toolName }}). No action was taken.")
                    }
                    appendLine("The player's turn is WASTED unless you call action tools NOW.")
                    appendLine()
                    appendLine("Missing tool calls detected from player input:")
                    for (hint in missingTools) {
                        appendLine("  - $hint")
                    }
                    appendLine()
                    appendLine("Call these tools immediately. Do NOT write prose — only tool calls matter.")
                }
                decideAgent.sendMessageWithTools(retryPrompt, toolDefs, executor).toList()
            }
        }


        // Build TurnSummary from executed tools
        val combatRound = extractCombatRoundSummary(executedTools, pendingEvents)
        val turnSummary = TurnSummary(
            executedTools = executedTools,
            events = pendingEvents.toList(),
            turnType = inferTurnType(executedTools),
            combatRound = combatRound
        )

        // ── Phase 3: NARRATE ─────────────────────────────────────
        // Narrator writes prose based on the TurnSummary. No tools.
        val baseNarrationInput = buildNarrationMessage(input, turnSummary, gameState, recentEvents)

        // Inject NPC dialogue and rejection context so the narrator can weave them into prose
        val narrationInput = buildString {
            append(baseNarrationInput)

            if (rejections.isNotEmpty()) {
                appendLine()
                appendLine("== FAILED ACTIONS (narrate these as meaningful failures — the player tried but couldn't) ==")
                for (rejection in rejections) {
                    appendLine("- $rejection")
                }
                appendLine("Narrate the failure naturally: reaching for a weapon that isn't there, attempting a spell that fizzles, the System denying the action. Make it feel real, not like an error message.")
            }

            if (npcDialogueResults.isNotEmpty()) {
                appendLine()
                appendLine("== NPC DIALOGUE (already generated — weave into prose, do NOT rewrite or change their words) ==")
                for ((npcName, dialogue) in npcDialogueResults) {
                    appendLine("$npcName says: \"$dialogue\"")
                }
                appendLine("Include their dialogue naturally in the scene. You may add narration around it (body language, tone) but preserve their exact words.")
            }
        }

        val narrativeChunks = narrateAgent.sendMessage(narrationInput).toList()
        val narrativeText = narrativeChunks.joinToString("")

        if (narrativeText.isNotBlank()) {
            val narrativeEvent = GameEvent.NarratorText(narrativeText)
            emit(narrativeEvent)
            eventLog.add(narrativeEvent)
        }

        // Emit accumulated tool events (items gained, quest updates, combat logs, etc.)
        // Deduplicate scene images — multiple tools may generate identical scene descriptions
        val seenSceneDescs = mutableSetOf<String>()
        for (event in pendingEvents) {
            if (event is GameEvent.SceneImage) {
                if (!seenSceneDescs.add(event.description)) continue // skip duplicate
            }
            emit(event)
            eventLog.add(event)
        }

        // ── Phase 3b: AUTO-COMPLETE QUEST OBJECTIVES ─────────────
        // Detect when tool calls satisfy quest objectives the LLM forgot to update.
        val questUpdates = detectQuestCompletions(executedTools, turnSummary)
        for ((questId, objectiveId) in questUpdates) {
            gameState = gameState.updateQuestObjective(questId, objectiveId, 1)
            val quest = gameState.activeQuests[questId]
            if (quest != null) {
                val status = if (quest.isComplete()) QuestStatus.COMPLETED else QuestStatus.IN_PROGRESS
                val questEvent = GameEvent.QuestUpdate(questId, quest.name, status)
                emit(questEvent)
                eventLog.add(questEvent)

                // Auto-complete the quest and award rewards when all objectives are done
                if (quest.isComplete()) {
                    gameState = gameState.completeQuest(questId)
                    val completeNotif = GameEvent.SystemNotification("Quest complete: ${quest.name}!")
                    emit(completeNotif)
                    eventLog.add(completeNotif)
                }
            }
        }

        // ── Phase 3c: STORY PROGRESSION ───────────────────────────
        // Check if level-ups unlocked new story beats or quests.
        val storyEvents = checkStoryProgression()
        for (event in storyEvents) {
            emit(event)
            eventLog.add(event)
        }

        // ── Phase 4: RECONCILE NARRATOR DRIFT ────────────────────
        // Parse narrator output for entities it introduced without mechanical backing.
        if (narrativeText.isNotBlank()) {
            val reconcileEvents = reconcileNarration(narrativeText, executedTools)
            for (event in reconcileEvents) {
                emit(event)
                eventLog.add(event)
            }
        }

        // ── Retry if narration is empty ──────────────────────────
        if (narrativeText.isBlank()) {
            val nudge = buildString {
                appendLine("Your previous response was empty. You MUST narrate what happened.")
                appendLine("Player action: \"$input\"")
                if (turnSummary.executedTools.isNotEmpty()) {
                    appendLine("Tools executed: ${turnSummary.executedTools.joinToString(", ") { "${it.toolName}: ${it.resultSummary}" }}")
                    appendLine("Summarize in 2-3 vivid sentences what happened.")
                } else {
                    appendLine("Nothing mechanical happened. Describe the atmosphere or respond to the player's words in 1-2 sentences.")
                }
            }
            val retryText = narrateAgent.sendMessage(nudge).toList().joinToString("")
            if (retryText.isNotBlank()) {
                val retryEvent = GameEvent.NarratorText(retryText)
                emit(retryEvent)
                eventLog.add(retryEvent)
            } else {
                // Final fallback
                val fallback = GameEvent.SystemNotification(
                    "The world shifts around you, but nothing notable happens. Try a different approach."
                )
                emit(fallback)
                eventLog.add(fallback)
            }
        }
    }

    /**
     * Extract a CombatRoundSummary from tool execution results if combat tools were called.
     */
    private fun extractCombatRoundSummary(
        executedTools: List<ToolExecutionResult>,
        events: List<GameEvent>
    ): CombatRoundSummary? {
        val combatTool = executedTools.lastOrNull { it.toolName in setOf("combat_attack", "combat_use_skill", "combat_flee", "start_combat") }
            ?: return null

        val data = combatTool.rawData
        fun str(key: String) = data[key]?.let {
            try { it.jsonPrimitive.content } catch (_: Exception) { null }
        }

        // Extract combat events from pending GameEvents
        val combatEventTexts = events.filterIsInstance<GameEvent.CombatLog>().map { it.text }

        // Parse structured combat data from tool result
        val enemyName = str("enemyName") ?: gameState.combatState?.enemy?.name ?: "Unknown"
        val enemyHP = str("enemyHP")?.toIntOrNull() ?: 0
        val enemyMaxHP = str("enemyMaxHP")?.toIntOrNull() ?: 0
        val enemyCondition = str("enemyCondition") ?: ""
        val playerHP = str("playerHP")?.toIntOrNull() ?: gameState.characterSheet.resources.currentHP
        val playerMaxHP = str("playerMaxHP")?.toIntOrNull() ?: gameState.characterSheet.resources.maxHP
        val combatOver = str("combatOver") == "true"
        val victory = str("victory") == "true"
        val playerDied = str("playerDied") == "true"
        val xpAwarded = str("xpAwarded")?.toLongOrNull() ?: 0L
        val levelUp = str("levelUp") == "true"
        val newLevel = str("newLevel")?.toIntOrNull() ?: gameState.playerLevel
        val fled = str("fled") == "true"
        val lootTier = str("lootTier") ?: "normal"

        // Reconstruct CombatEvents from the text logs
        val reconstructedEvents = combatEventTexts.mapNotNull { text ->
            when {
                text.contains("deal") && text.contains("damage") -> {
                    val dmg = Regex("""deal (\d+) damage""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    CombatEvent.PlayerHit(dmg, text.contains("CRIT"), enemyHP, enemyMaxHP)
                }
                text.contains("misses") && !text.contains("Enemy") -> CombatEvent.PlayerMiss
                text.contains("Enemy") && text.contains("misses") -> CombatEvent.EnemyMiss
                text.contains("Enemy strikes") || text.contains("Enemy hits") -> {
                    val dmg = Regex("""for (\d+) damage""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    CombatEvent.EnemyHit(dmg, playerHP)
                }
                text.contains("Enemy uses") -> {
                    val ability = Regex("""uses (.+?) for""").find(text)?.groupValues?.get(1) ?: "ability"
                    val dmg = Regex("""for (\d+) damage""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    CombatEvent.EnemyAbility(ability, dmg, playerHP)
                }
                text.contains("defeated") -> CombatEvent.EnemyDefeated(enemyName)
                text.contains("deals") && text.contains("damage") -> {
                    val skillName = Regex("""^(.+?) deals""").find(text)?.groupValues?.get(1) ?: "Skill"
                    val dmg = Regex("""deals (\d+) damage""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    CombatEvent.SkillDamage(skillName, dmg, enemyHP, enemyMaxHP)
                }
                else -> null
            }
        }

        return CombatRoundSummary(
            events = reconstructedEvents,
            enemyName = enemyName,
            enemyHP = enemyHP,
            enemyMaxHP = enemyMaxHP,
            enemyCondition = enemyCondition,
            playerHP = playerHP,
            playerMaxHP = playerMaxHP,
            combatOver = combatOver,
            victory = victory,
            playerDied = playerDied,
            xpAwarded = xpAwarded,
            levelUp = levelUp,
            newLevel = newLevel,
            fled = fled,
            lootTier = lootTier
        )
    }

    // ── Phase 3b: Quest Auto-Completion ──────────────────────────

    /**
     * Detect quest objectives that should be marked complete based on tool calls
     * that the LLM forgot to follow up with update_quest_progress.
     */
    private fun detectQuestCompletions(
        executedTools: List<ToolExecutionResult>,
        summary: TurnSummary
    ): List<Pair<String, String>> {
        val updates = mutableListOf<Pair<String, String>>()
        val toolNames = executedTools.filter { it.success }.map { it.toolName }.toSet()
        val hadCombatVictory = summary.combatRound?.victory == true
        val hasNPCsNearby = gameState.getNPCsAtCurrentLocation().isNotEmpty()

        // Auto-detect for ALL active quests (tutorial and non-tutorial)
        for ((questId, quest) in gameState.activeQuests) {
            for (obj in quest.objectives) {
                if (obj.isComplete()) continue

                val shouldComplete = when {
                    // ── Tutorial quest by targetId ──
                    obj.targetId == "class" -> "set_class" in toolNames ||
                            gameState.characterSheet.playerClass != com.rpgenerator.core.domain.PlayerClass.NONE
                    obj.targetId == "status" -> toolNames.any { it in setOf("get_player_stats", "get_character_sheet") }
                    obj.targetId == "test" -> hadCombatVictory ||
                            toolNames.any { it in setOf("combat_use_skill", "use_skill") }

                    // ── Generic objectives by type ──
                    obj.type == ObjectiveType.KILL -> {
                        if (obj.targetId.isNotBlank()) {
                            // Specific kill target
                            hadCombatVictory &&
                                    summary.combatRound!!.enemyName.contains(obj.targetId, ignoreCase = true)
                        } else {
                            // Generic "kill your first monster" — any combat victory counts
                            hadCombatVictory
                        }
                    }
                    obj.type == ObjectiveType.REACH_LOCATION -> "move_to_location" in toolNames &&
                            executedTools.any {
                                it.toolName == "move_to_location" &&
                                        it.resultSummary.contains(obj.targetId, ignoreCase = true)
                            }
                    obj.type == ObjectiveType.EXPLORE -> "move_to_location" in toolNames

                    // ── Description-based fuzzy matching for objectives the LLM created ──
                    else -> {
                        val desc = obj.description.lowercase()
                        when {
                            desc.contains("kill") || desc.contains("defeat") || desc.contains("slay") ->
                                hadCombatVictory
                            desc.contains("find") && (desc.contains("participant") || desc.contains("survivor") || desc.contains("other")) ->
                                hasNPCsNearby || "spawn_npc" in toolNames || "talk_to_npc" in toolNames
                            desc.contains("choose") && desc.contains("class") ->
                                "set_class" in toolNames ||
                                        gameState.characterSheet.playerClass != com.rpgenerator.core.domain.PlayerClass.NONE
                            desc.contains("talk") || desc.contains("speak") ->
                                "talk_to_npc" in toolNames
                            desc.contains("arrive") || desc.contains("reach") || desc.contains("enter") ->
                                "move_to_location" in toolNames
                            else -> false
                        }
                    }
                }
                if (shouldComplete) {
                    updates.add(questId to obj.id)
                }
            }
        }

        return updates
    }

    // ── Phase 4: Narrator Reconciliation ─────────────────────────

    /**
     * Parse narrator text for entities it introduced without mechanical backing.
     * Makes compensating tool calls to sync game state with narration.
     */
    private suspend fun reconcileNarration(
        narrativeText: String,
        executedTools: List<ToolExecutionResult>
    ): List<GameEvent> {
        val events = mutableListOf<GameEvent>()

        // ── Reconcile location changes ──
        // If narrator described moving to a new location but no move_to_location was called
        val moveWasCalled = executedTools.any { it.toolName == "move_to_location" && it.success }
        if (!moveWasCalled) {
            val locationMatch = LOCATION_DISCOVERY_PATTERN.find(narrativeText)
            if (locationMatch != null) {
                val discoveredLocation = locationMatch.groupValues[1].trim()
                if (discoveredLocation.isNotBlank() &&
                    looksLikeLocationName(discoveredLocation) &&
                    !discoveredLocation.equals(gameState.currentLocation.name, ignoreCase = true)
                ) {
                    val moveResult = toolContract.executeTool(
                        "move_to_location",
                        mapOf("locationName" to discoveredLocation),
                        gameState
                    )
                    if (moveResult.success && moveResult.newState != null) {
                        gameState = moveResult.newState
                        events.add(GameEvent.SystemNotification("Discovered and moved to $discoveredLocation"))
                    }
                }
            }
        }

        // ── Reconcile NPC introductions ──
        // If narrator described an NPC but no spawn_npc was called for them
        val spawnedNPCNames = executedTools
            .filter { it.toolName == "spawn_npc" && it.success }
            .mapNotNull { it.resultSummary.substringAfter("NPC appeared: ").substringBefore(" (").takeIf { n -> n.isNotBlank() }?.lowercase() }
            .toSet()

        val existingNPCNames = gameState.getNPCsAtCurrentLocation().map { it.name.lowercase() }.toSet()

        for (match in NPC_INTRODUCTION_PATTERN.findAll(narrativeText)) {
            val npcName = match.groupValues[1].trim()
            if (npcName.isBlank() || npcName.length < 2 || npcName.length > 30) continue
            if (npcName.lowercase() in existingNPCNames) continue
            if (npcName.lowercase() in spawnedNPCNames) continue
            // Skip common false positives (player name, generic words)
            if (npcName.equals(gameState.playerName, ignoreCase = true)) continue
            if (npcName.lowercase() in NPC_FALSE_POSITIVES) continue

            val role = inferNPCRole(narrativeText, npcName)
            val spawnResult = toolContract.executeTool(
                "spawn_npc",
                mapOf("name" to npcName, "role" to role, "locationId" to ""),
                gameState
            )
            if (spawnResult.success && spawnResult.newState != null) {
                gameState = spawnResult.newState
                events.addAll(spawnResult.events)
            }
        }

        return events
    }

    /**
     * Infer NPC role from narrative context around their name.
     */
    private fun inferNPCRole(narrative: String, npcName: String): String {
        val lowerNarrative = narrative.lowercase()
        val nameIndex = lowerNarrative.indexOf(npcName.lowercase())
        if (nameIndex < 0) return "wanderer"

        // Check surrounding context (200 chars around the name)
        val start = (nameIndex - 100).coerceAtLeast(0)
        val end = (nameIndex + npcName.length + 100).coerceAtMost(lowerNarrative.length)
        val context = lowerNarrative.substring(start, end)

        return when {
            context.containsAny("merchant", "shop", "sell", "buy", "trade", "wares") -> "merchant"
            context.containsAny("guard", "soldier", "patrol", "armor", "weapon") -> "guard"
            context.containsAny("quest", "task", "mission", "job", "help me") -> "quest_giver"
            context.containsAny("inn", "tavern", "drink", "rest", "room") -> "innkeeper"
            context.containsAny("scholar", "book", "library", "know", "lore", "author", "write") -> "scholar"
            context.containsAny("smith", "forge", "hammer", "anvil") -> "blacksmith"
            context.containsAny("alchemist", "potion", "brew", "vial") -> "alchemist"
            context.containsAny("train", "teach", "master", "student") -> "trainer"
            context.containsAny("noble", "lord", "lady", "court") -> "noble"
            else -> "wanderer"
        }
    }

    private fun String.containsAny(vararg words: String): Boolean = words.any { this.contains(it) }

    /** Reject extracted names that look like prose fragments rather than proper location names. */
    private fun looksLikeLocationName(name: String): Boolean {
        val words = name.split("\\s+".toRegex())
        // Single words are rarely valid location names (e.g. "DETECTED")
        if (words.size < 2) return false
        // Too many words is suspicious — locations are typically 2-7 words
        if (words.size > 8) return false
        // Reject if it contains markdown formatting or punctuation that doesn't belong in a name
        if (name.contains('*') || name.contains('[') || name.contains(']')) return false
        // Check that significant words (non-articles) are title-cased
        val articles = setOf("of", "the", "and", "in", "on", "at", "by", "for", "to", "a", "an")
        val significantWords = words.filter { it.lowercase() !in articles && !it.all { c -> c == '—' || c == '-' } }
        if (significantWords.isEmpty()) return false
        val titleCased = significantWords.count { it[0].isUpperCase() }
        return titleCased.toFloat() / significantWords.size >= 0.5f
    }

    companion object {
        // Pattern: "Floor Three — The Garden of False Idols" or "The Curator's Study" etc.
        // NO IGNORE_CASE: location name words must start with uppercase, which prevents
        // capturing prose fragments like "of the floor, perfectly illuminated".
        // Trigger verbs handle both cases via [Ee]nter etc.
        private val LOCATION_DISCOVERY_PATTERN = Regex(
            """(?:[Ee]nter|[Aa]rrive at|[Rr]each|[Ss]tep into|[Mm]ove to|[Ff]ind (?:yourself |ourselves )?in|[Ee]merge into|[Ll]and (?:on|in)|[Tt]umble (?:into|out onto))\s+(?:[Tt]he\s+)?((?:[A-Z][a-zA-Z']+)(?:(?:\s+(?:of|the|and|in|on|at|by|for|to|—|-)\s+|\s+)(?:[A-Z][a-zA-Z']+|[0-9]+(?:[-][A-Z0-9]+)?)){0,8})"""
        )

        // Pattern: Named character introductions — "A figure named X", "X appears", "X says", etc.
        private val NPC_INTRODUCTION_PATTERN = Regex(
            """(?:named|called|introduces? (?:themselves|himself|herself) as|\"[^\"]*\" (?:says|whispers|mutters|shouts|replies|announces))\s+([A-Z][a-z]+(?:\s+[A-Z][a-z]+){0,2})"""
        )

        private val NPC_FALSE_POSITIVES = setOf(
            "the", "you", "your", "they", "this", "that", "what", "floor", "system",
            "dungeon", "combat", "victory", "level", "quest", "sponsor"
        )

        // Pattern to extract rejection lines from decide agent output
        private val REJECTION_PATTERN = Regex(
            """REJECTED?:\s*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE
        )

        // Semantic triggers that suggest the decide agent described an action without calling tools
        private val TOOL_HINT_PATTERNS = listOf(
            Regex("""(?:kill|slay|defeat|destroy|strike|attack|hit|slash|stab|lunge|swing|fight)""", RegexOption.IGNORE_CASE) to "Player is fighting — call start_combat then combat_attack",
            Regex("""(?:move|walk|head|travel|go to|enter|arrive|leave|depart)""", RegexOption.IGNORE_CASE) to "Player is moving — call move_to_location",
            Regex("""(?:pick up|grab|take|loot|find|receive|gain|earn|collect)""", RegexOption.IGNORE_CASE) to "Player gained something — call add_item or add_gold",
            Regex("""(?:talk|speak|ask|say|tell|greet|address|converse|sweet.?talk)""", RegexOption.IGNORE_CASE) to "Player is talking to NPC — call talk_to_npc",
            Regex("""(?:learn|acquire skill|new ability|unlock)""", RegexOption.IGNORE_CASE) to "Player learned something — call grant_skill",
            Regex("""(?:choose|select|pick) (?:class|path|profession)""", RegexOption.IGNORE_CASE) to "Class/profession selection — call set_class or set_profession",
            Regex("""(?:quest complete|objective|finished the|accomplished)""", RegexOption.IGNORE_CASE) to "Quest progress — call update_quest_progress or complete_quest",
            Regex("""(?:search|investigate|examine|inspect|look for|check for|hidden|clue|detect|scan)""", RegexOption.IGNORE_CASE) to "Player is investigating — call skill_check(INVESTIGATION or PERCEPTION, difficulty)",
            Regex("""(?:sneak|stealth|creep|silent|hide|avoid detection|move quiet|slip past|skulk)""", RegexOption.IGNORE_CASE) to "Player is sneaking — call skill_check(STEALTH, difficulty)",
            Regex("""(?:persuade|convince|charm|sweet.?talk|negotiate|haggle|flatter|coax|beg)""", RegexOption.IGNORE_CASE) to "Player is persuading — call skill_check(PERSUASION, difficulty)",
            Regex("""(?:intimidate|threaten|scare|bully|coerce|menace)""", RegexOption.IGNORE_CASE) to "Player is intimidating — call skill_check(INTIMIDATION, difficulty)",
            Regex("""(?:climb|jump|swim|lift|push|pull|break down|force open)""", RegexOption.IGNORE_CASE) to "Player needs physical check — call skill_check(ATHLETICS, difficulty)",
            Regex("""(?:pick.?lock|disarm.?trap|sleight|pickpocket|lockpick)""", RegexOption.IGNORE_CASE) to "Player needs dexterity check — call skill_check(SLEIGHT_OF_HAND, difficulty)",
            Regex("""(?:drink|use|consume|potion|heal).+(?:combat|fight|battle|mid)""", RegexOption.IGNORE_CASE) to "Player wants to use item in combat — call combat_use_item(itemId)",
            Regex("""(?:equip|wear|wield|put on|strap on|don)""", RegexOption.IGNORE_CASE) to "Player is equipping — call equip_item(itemId)",
        )
    }

    /**
     * Scan decide agent's discarded text for semantic triggers indicating
     * tool calls it should have made but didn't.
     */
    private fun detectMissingToolCalls(decideText: String, playerInput: String): List<String> {
        val combined = "$decideText $playerInput"
        val hints = mutableListOf<String>()
        for ((pattern, hint) in TOOL_HINT_PATTERNS) {
            if (pattern.containsMatchIn(combined)) {
                hints.add(hint)
            }
        }
        // Cap at 3 hints to avoid overwhelming the retry prompt
        return hints.take(3)
    }

    private fun buildContextMessage(input: String, recentEvents: List<String>): String = buildString {
        if (playerTurnCount <= 20) {
            val brief = getEarlyTurnBrief(playerTurnCount)
            if (brief.isNotBlank()) {
                appendLine(brief)
                appendLine()
            }
        }
        appendLine("Player input: \"$input\"")
        appendLine()
        appendLine("Current state:")
        appendLine("  Location: ${gameState.currentLocation.name} — ${gameState.currentLocation.description}")
        appendLine("  Level: ${gameState.playerLevel}, XP: ${gameState.playerXP}")
        appendLine("  Grade: ${gameState.characterSheet.currentGrade.displayName} (${gameState.characterSheet.currentGrade.description})")
        appendLine("  HP: ${gameState.characterSheet.resources.currentHP}/${gameState.characterSheet.resources.maxHP}")

        val cls = gameState.characterSheet.playerClass
        if (cls != com.rpgenerator.core.domain.PlayerClass.NONE) {
            appendLine("  Class: ${cls.displayName}")
        } else {
            appendLine("  Class: Not yet chosen")
        }

        // Combat context
        val combat = gameState.combatState
        if (combat != null && !combat.isOver) {
            appendLine()
            appendLine("⚔ IN COMBAT — Round ${combat.roundNumber}")
            appendLine("  Enemy: ${combat.enemy.name} [HP: ${combat.enemy.currentHP}/${combat.enemy.maxHP}, ${combat.enemy.condition}]")
            if (combat.enemy.statusEffects.isNotEmpty()) {
                appendLine("  Enemy effects: ${combat.enemy.statusEffects.joinToString(", ") { "${it.type.name}(${it.remainingTurns}t)" }}")
            }
            if (combat.enemy.abilities.isNotEmpty()) {
                val readyAbilities = combat.enemy.abilities.filter { it.isReady }
                if (readyAbilities.isNotEmpty()) {
                    appendLine("  Enemy abilities ready: ${readyAbilities.joinToString(", ") { it.name }}")
                }
            }
            appendLine("  Player HP: ${gameState.characterSheet.resources.currentHP}/${gameState.characterSheet.resources.maxHP}")
            appendLine("  Mana: ${gameState.characterSheet.resources.currentMana}/${gameState.characterSheet.resources.maxMana}")
            appendLine("  Energy: ${gameState.characterSheet.resources.currentEnergy}/${gameState.characterSheet.resources.maxEnergy}")
            val readySkills = gameState.characterSheet.getReadySkills()
            if (readySkills.isNotEmpty()) {
                appendLine("  Ready skills: ${readySkills.joinToString(", ") { "${it.name}(${it.id})" }}")
            }
            appendLine("  Use combat_attack, combat_use_skill, or combat_flee. Do NOT narrate damage without calling a tool.")
        }

        val npcsHere = gameState.getNPCsAtCurrentLocation()
        if (npcsHere.isNotEmpty()) {
            appendLine("  NPCs here: ${npcsHere.joinToString(", ") { "${it.name} (${it.archetype})" }}")
        }

        // Inventory — so the decide agent can validate player item claims
        val items = gameState.characterSheet.inventory.items.values
        if (items.isNotEmpty()) {
            appendLine("  Inventory: ${items.joinToString(", ") { "${it.name} x${it.quantity}" }}")
        }

        val quests = gameState.activeQuests.values
        if (quests.isNotEmpty()) {
            appendLine("  Active quests: ${quests.joinToString(", ") { it.name }}")
        }

        // Skills available (outside combat)
        if (gameState.combatState == null) {
            val skills = gameState.characterSheet.getReadySkills()
            if (skills.isNotEmpty()) {
                appendLine("  Skills: ${skills.joinToString(", ") { it.name }}")
            }
        }

        // Status effects on player
        if (gameState.characterSheet.statusEffects.isNotEmpty()) {
            appendLine("  Status effects: ${gameState.characterSheet.statusEffects.joinToString(", ") { "${it.name}(${it.duration}t)" }}")
        }

        // Death count — the world remembers
        if (gameState.deathCount > 0) {
            appendLine("  Deaths: ${gameState.deathCount} (NPCs may reference this, the world remembers)")
        }

        if (recentEvents.isNotEmpty()) {
            appendLine()
            appendLine("Recent events (use for callbacks and continuity):")
            recentEvents.forEach { appendLine("  - $it") }
        }

        // Add main story arc context
        val currentAct = MainStoryArc.getCurrentAct(gameState.playerLevel)
        val currentMainQuest = MainStoryArc.getMainQuestForLevel(gameState.playerLevel)
        val nextBeat = MainStoryArc.getStoryBeatsForAct(currentAct)
            .firstOrNull { it.triggerLevel > gameState.playerLevel }
        appendLine()
        appendLine("Story context:")
        appendLine("  Act: $currentAct, Grade: ${gameState.characterSheet.currentGrade.displayName}")
        if (currentAct == 1) {
            appendLine("  (Tutorial — entire Act 1 is E-Grade, levels 1-25)")
        }
        if (currentMainQuest != null) {
            appendLine("  Main quest: ${currentMainQuest.title} — ${currentMainQuest.description}")
            appendLine("  Objectives: ${currentMainQuest.objectives.joinToString("; ")}")
            appendLine("  Level range: ${currentMainQuest.levelRange}")
        }
        if (nextBeat != null) {
            appendLine("  Next story beat at level ${nextBeat.triggerLevel}: ${nextBeat.title}")
            appendLine("  Foreshadow this! Hint at what's coming without spoiling it.")
        }

        // Tutorial-specific context: other participants, leaderboard, social dynamics
        if (currentAct == 1) {
            val participantsRemaining = when {
                gameState.playerLevel >= 20 -> 203
                gameState.playerLevel >= 15 -> 412
                gameState.playerLevel >= 10 -> 847
                gameState.playerLevel >= 5 -> 1193
                else -> 1247
            }
            val leaderboardPosition = when {
                gameState.playerLevel >= 20 -> (1..15).random()
                gameState.playerLevel >= 15 -> (15..50).random()
                gameState.playerLevel >= 10 -> (50..200).random()
                gameState.playerLevel >= 5 -> (200..500).random()
                else -> (500..participantsRemaining).random()
            }
            appendLine("  Tutorial participants remaining: $participantsRemaining/1,247")
            appendLine("  Leaderboard position: $leaderboardPosition/$participantsRemaining")
            appendLine("  IMPORTANT: The player is NEVER alone in the tutorial. Other participants are always around.")
            appendLine("  Reference the leaderboard naturally. Spawn named peers with spawn_npc when appropriate.")

            // Inject relationship-focused peer context from the seed
            val seed = gameState.seedId?.let { WorldSeeds.byId(it) }
            val tutorial = seed?.tutorial
            if (tutorial != null && tutorial.namedPeers.isNotEmpty()) {
                val existingNPCNames = gameState.getNPCsAtCurrentLocation().map { it.name.lowercase() }.toSet() +
                    gameState.npcsByLocation.values.flatten().map { it.name.lowercase() }.toSet()

                appendLine()
                appendLine("  RELATIONSHIPS — The heart of the tutorial. Prioritize peer moments between combat encounters.")
                appendLine("  Rule: Every 2-3 fights, a peer should appear, advance their story, or be referenced by others.")
                appendLine()

                tutorial.namedPeers.forEach { peer ->
                    val alreadySpawned = peer.name.lowercase() in existingNPCNames ||
                        existingNPCNames.any { it.contains(peer.name.split(" ").first().lowercase()) }

                    if (alreadySpawned) {
                        // Already met — show relationship progression guidance
                        appendLine("  [RELATIONSHIP: ${peer.name}] (${peer.className}, ${peer.relationship})")
                        appendLine("    Class drives them: ${peer.classPriority.take(150)}")
                        appendLine("    What they teach the player: ${peer.teachesPlayer.take(120)}")
                        appendLine("    Next relationship beat: Progress from current stage. Shared activity: ${peer.sharedActivity.take(100)}")
                        appendLine("    Their vulnerability: ${peer.vulnerability.take(120)}")
                        appendLine("    They need from the player: ${peer.needFromPlayer.take(100)}")
                        appendLine("    Voice: ${peer.dialogueStyle.take(80)}")
                        appendLine("    Example: ${peer.exampleLines.first()}")
                        appendLine()
                    } else {
                        // Not yet met — show introduction hook
                        val levelHint = peer.firstAppearance.substringBefore(",").trim()
                        appendLine("  [NOT YET MET: ${peer.name}] (${peer.className}, ${peer.relationship})")
                        appendLine("    Introduce at: ${peer.firstAppearance.take(120)}")
                        appendLine("    First impression: ${peer.stageOne.take(150)}")
                        appendLine("    Class philosophy: ${peer.classPhilosophy.take(120)}")
                        appendLine("    What they teach: ${peer.teachesPlayer.take(100)}")
                        appendLine("    Emotional hook: ${peer.moment.take(120)}")
                        appendLine("    Voice: ${peer.dialogueStyle.take(80)}")
                        appendLine("    Example: ${peer.exampleLines.first()}")
                        appendLine("    IMPORTANT: Use spawn_npc when introducing. Show them DOING something, not standing around.")
                        appendLine()
                    }
                }
            }
        }

        // Add plot graph context for seed worlds
        val plotGraph = storyFoundation?.plotGraph
        if (plotGraph != null && plotGraph.nodes.isNotEmpty()) {
            val readyNodes = plotGraph.getReadyNodes(gameState)
            val activeNodes = plotGraph.getActiveNodes()
            if (readyNodes.isNotEmpty() || activeNodes.isNotEmpty()) {
                appendLine()
                appendLine("Plot threads:")
                activeNodes.take(3).forEach { node ->
                    appendLine("  [ACTIVE] ${node.beat.title}: ${node.beat.description}")
                    if (node.beat.foreshadowing != null) appendLine("    Foreshadow: ${node.beat.foreshadowing}")
                }
                readyNodes.take(3).forEach { node ->
                    appendLine("  [READY] ${node.beat.title}: ${node.beat.description}")
                    if (node.beat.foreshadowing != null) appendLine("    Foreshadow: ${node.beat.foreshadowing}")
                }
            }
        }
    }

    private fun jsonObjectToMap(json: JsonObject): Map<String, Any?> {
        return json.mapValues { (_, value) ->
            when (value) {
                is kotlinx.serialization.json.JsonPrimitive -> {
                    when {
                        value.isString -> value.content
                        value.content == "true" -> true
                        value.content == "false" -> false
                        value.content.contains(".") -> value.content.toDoubleOrNull()
                        else -> value.content.toLongOrNull() ?: value.content
                    }
                }
                else -> value.toString()
            }
        }
    }

    // ── Death Handling ────────────────────────────────────────────

    private suspend fun handleDeath(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>,
        cause: String
    ) {
        val deathNarration = narratorAgent.narrateDeath(gameState, cause)
        val deathEvent = GameEvent.NarratorText(deathNarration)
        flowCollector.emit(deathEvent)
        eventLog.add(deathEvent)

        val newDeathCount = gameState.deathCount + 1
        gameState = gameState.copy(deathCount = newDeathCount)

        when (gameState.systemType) {
            SystemType.DEATH_LOOP -> {
                val deathBonus = newDeathCount * 2
                val newStats = gameState.characterSheet.baseStats.copy(
                    strength = gameState.characterSheet.baseStats.strength + deathBonus,
                    dexterity = gameState.characterSheet.baseStats.dexterity + deathBonus,
                    constitution = gameState.characterSheet.baseStats.constitution + deathBonus,
                    intelligence = gameState.characterSheet.baseStats.intelligence + deathBonus,
                    wisdom = gameState.characterSheet.baseStats.wisdom + deathBonus,
                    charisma = gameState.characterSheet.baseStats.charisma + deathBonus
                )
                val restoredSheet = gameState.characterSheet.copy(
                    baseStats = newStats,
                    resources = gameState.characterSheet.resources.restore()
                )
                gameState = gameState.copy(characterSheet = restoredSheet)

                val respawnNarration = narratorAgent.narrateRespawn(gameState)
                flowCollector.emit(GameEvent.NarratorText(respawnNarration))
                eventLog.add(GameEvent.NarratorText(respawnNarration))

                val bonusNotif = GameEvent.SystemNotification(
                    "Death has strengthened you. All stats increased by $deathBonus!"
                )
                flowCollector.emit(bonusNotif)
                eventLog.add(bonusNotif)
            }
            SystemType.DUNGEON_DELVE -> {
                val gameOverEvent = GameEvent.SystemNotification(
                    "GAME OVER - Permadeath. Your adventure ends here. Total deaths: $newDeathCount"
                )
                flowCollector.emit(gameOverEvent)
                eventLog.add(gameOverEvent)
            }
            else -> {
                val restoredSheet = gameState.characterSheet.copy(
                    resources = gameState.characterSheet.resources.restore(),
                    xp = (gameState.playerXP * 0.9).toLong()
                )
                gameState = gameState.copy(characterSheet = restoredSheet)

                val respawnEvent = GameEvent.SystemNotification(
                    "You have respawned. Lost 10% XP as death penalty."
                )
                flowCollector.emit(respawnEvent)
                eventLog.add(respawnEvent)
            }
        }
    }

    // ── Initialization ───────────────────────────────────────────

    private suspend fun initializeDynamicNPCsSilent() {
        if (!storyPlanningStarted) {
            storyPlanningStarted = true

            if (worldSeed != null) {
                storyFoundation = createFoundationFromSeed(worldSeed)
                storyFoundationDeferred.complete(storyFoundation)
            } else {
                backgroundScope.launch {
                    try {
                        val foundation = storyPlanningService.initializeStory(
                            gameId = gameState.gameId,
                            systemType = gameState.systemType,
                            playerName = gameState.playerName,
                            backstory = gameState.backstory,
                            startingLocation = gameState.currentLocation
                        )
                        storyFoundation = foundation
                        storyFoundationDeferred.complete(foundation)
                    } catch (_: Exception) {
                        // Story planning failed — game continues without it
                        storyFoundationDeferred.complete(null)
                    }
                }
            }
        }

        if (gameState.currentLocation.biome == Biome.TUTORIAL_ZONE) {
            // Only spawn a tutorial guide NPC if the seed defines one.
            // Some seeds (e.g. integration/System Apocalypse) have no guide — the System IS the guide.
            val hasGuide = worldSeed?.tutorial?.guide != null
            val guideName = if (hasGuide) {
                val tutorialGuide = npcArchetypeGenerator.generateTutorialGuide(
                    playerName = gameState.playerName,
                    systemType = gameState.systemType,
                    playerLevel = gameState.playerLevel,
                    locationId = gameState.currentLocation.id,
                    seed = gameState.gameId.hashCode().toLong()
                )
                gameState = gameState.addNPC(tutorialGuide)
                tutorialGuide.name
            } else {
                "The System"
            }

            if (!gameState.activeQuests.containsKey("quest_survive_tutorial")) {
                val tutorialQuest = createTutorialQuest(guideName)
                gameState = gameState.addQuest(tutorialQuest)
            }
        }
    }

    private suspend fun emitInitialQuestEvents(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val tutorialQuest = gameState.activeQuests["quest_survive_tutorial"]
        if (tutorialQuest != null) {
            val questEvent = GameEvent.QuestUpdate(
                questId = tutorialQuest.id,
                questName = tutorialQuest.name,
                status = QuestStatus.NEW
            )
            flowCollector.emit(questEvent)
            eventLog.add(questEvent)
        }

        if (gameState.currentLocation.biome == Biome.TUTORIAL_ZONE) {
            val tutorialGuide = gameState.getNPCsAtCurrentLocation().firstOrNull()
            if (tutorialGuide != null) {
                val guideNotice = GameEvent.SystemNotification("${tutorialGuide.name} materializes before you.")
                flowCollector.emit(guideNotice)
                eventLog.add(guideNotice)
            }
        }
    }

    private fun createTutorialQuest(guideName: String): Quest {
        return Quest(
            id = "quest_survive_tutorial",
            name = "System Integration",
            description = "The System requires you to choose a path. Your class defines who you will become.",
            type = QuestType.MAIN_STORY,
            giver = guideName,
            objectives = listOf(
                QuestObjective(
                    id = "tutorial_obj_class",
                    description = "Choose your class - this decision shapes your entire future",
                    type = ObjectiveType.TALK,
                    targetId = "class",
                    targetProgress = 1
                ),
                QuestObjective(
                    id = "tutorial_obj_stats",
                    description = "Review your status and understand your new abilities",
                    type = ObjectiveType.TALK,
                    targetId = "status",
                    targetProgress = 1
                ),
                QuestObjective(
                    id = "tutorial_obj_test",
                    description = "Test your abilities - use a skill or defeat an enemy",
                    type = ObjectiveType.TALK,
                    targetId = "test",
                    targetProgress = 1
                )
            ),
            rewards = QuestRewards(
                xp = 250L,
                unlockedLocationIds = listOf("threshold_settlement", "fringe_zones")
            )
        )
    }

    // ── Story Progression ──────────────────────────────────────

    /**
     * Check if the player's level has crossed a story milestone.
     * Emits story beat narration and creates quests from MainStoryArc.
     */
    private fun checkStoryProgression(): List<GameEvent> {
        val currentLevel = gameState.playerLevel
        if (currentLevel <= lastStoryCheckLevel) return emptyList()

        val events = mutableListOf<GameEvent>()

        // Check each level we passed since last check
        for (level in (lastStoryCheckLevel + 1)..currentLevel) {
            // Story beat narration at milestone levels
            val beat = MainStoryArc.getStoryBeatForLevel(level)
            if (beat != null) {
                // Emit the full story beat narration — these are the key narrative moments
                events.add(GameEvent.NarratorText(beat.narration.trimIndent()))

                // Scene art for the milestone
                events.add(GameEvent.SceneImage(
                    imageData = ByteArray(0),
                    description = "${beat.title}: ${beat.narration.trimIndent().take(200)}"
                ))

                // Music shift for dramatic moments
                val mood = when {
                    "Culling" in beat.title -> "intense"
                    "Tutorial Complete" in beat.title -> "triumphant"
                    "Spire" in beat.title -> "epic"
                    else -> "tense"
                }
                events.add(GameEvent.MusicChange(mood = mood, audioData = null))

                // Apply consequences (zone unlocks, etc.)
                for ((key, value) in beat.consequences) {
                    events.add(GameEvent.SystemNotification("$key: $value"))
                }
            }

            // Auto-create main quest when entering its level range
            val mainQuest = MainStoryArc.getMainQuestForLevel(level)
            if (mainQuest != null &&
                level == mainQuest.levelRange.first &&
                !gameState.activeQuests.containsKey(mainQuest.id) &&
                !gameState.completedQuests.contains(mainQuest.id)
            ) {
                val quest = convertMainQuest(mainQuest)
                gameState = gameState.addQuest(quest)
                events.add(
                    GameEvent.QuestUpdate(
                        questId = quest.id,
                        questName = quest.name,
                        status = QuestStatus.NEW
                    )
                )
            }
        }

        lastStoryCheckLevel = currentLevel
        return events
    }

    /**
     * Convert a MainStoryArc.MainQuest to a domain Quest object.
     */
    private fun convertMainQuest(mq: MainStoryArc.MainQuest): Quest {
        return Quest(
            id = mq.id,
            name = mq.title,
            description = mq.description,
            type = QuestType.MAIN_STORY,
            objectives = mq.objectives.mapIndexed { i, desc ->
                QuestObjective(
                    id = "${mq.id}_obj_$i",
                    description = desc,
                    type = inferObjectiveType(desc),
                    targetId = "${mq.id}_target_$i",
                    targetProgress = 1
                )
            },
            rewards = QuestRewards(
                xp = (mq.levelRange.last - mq.levelRange.first + 1) * 100L
            )
        )
    }

    private fun inferObjectiveType(description: String): ObjectiveType {
        val lower = description.lowercase()
        return when {
            "defeat" in lower || "kill" in lower || "clear" in lower -> ObjectiveType.KILL
            "reach level" in lower || "reach" in lower -> ObjectiveType.EXPLORE
            "find" in lower || "collect" in lower || "earn" in lower -> ObjectiveType.COLLECT
            "talk" in lower || "form" in lower || "contact" in lower -> ObjectiveType.TALK
            else -> ObjectiveType.EXPLORE
        }
    }

    // ── Accessors ────────────────────────────────────────────────

    fun getState(): GameState = gameState

    fun getEventLog(): List<GameEvent> = eventLog.toList()

    internal fun getStoryFoundation(): StoryFoundation? = storyFoundation

    internal fun getNarratorContext(): NarratorContext? = storyFoundation?.narratorContext

    // ── Helpers ──────────────────────────────────────────────────

    private fun eventToString(event: GameEvent): String = when (event) {
        is GameEvent.NarratorText -> event.text
        is GameEvent.SystemNotification -> event.text
        is GameEvent.NPCDialogue -> "${event.npcName}: ${event.text}"
        else -> event.toString()
    }

    private fun getEarlyTurnBrief(turn: Int): String {
        val narratorInfo = storyFoundation?.narratorContext?.let { ctx ->
            "\nContext: System=\"${ctx.systemName}\", Personality=\"${ctx.systemPersonality}\", Theme=\"${ctx.thematicCore}\""
        } ?: ""

        // ── Slow 20-turn intro: let the player lead ──
        // The game should feel like a living audiobook, not a combat tutorial.
        // Player sets the pace. No forced combat. Questions and exploration are rewarded.
        return when (turn) {
            1 -> "=== PACING (Turn 1 — Arrival) ===" +
                "\nPlayer JUST arrived. They're disoriented. Let them REACT." +
                "\nDo NOT push action. Let them look around, ask questions, process what happened." +
                "\nRespond to THEIR energy. If they're confused, be gentle. If they're aggressive, match it." +
                "\nDo NOT spawn enemies. Do NOT force class selection." +
                "\nCall query_lore(category='classes') if you need class info." + narratorInfo

            2 -> "=== PACING (Turn 2 — Grounding) ===" +
                "\nStill early. The player is finding their footing." +
                "\nAnswer their questions. Describe what they see/hear/smell." +
                "\nIntroduce ONE interesting detail — an NPC, a sound, a strange object — but don't force interaction." +
                "\nIf they ask about classes or express a preference, call set_class(). Otherwise, wait." +
                "\nNo enemies. No urgency. Let the world breathe." + narratorInfo

            3 -> "=== PACING (Turn 3 — Discovery) ===" +
                "\nPlayer should be exploring freely. Follow their lead." +
                "\nIf they haven't talked to anyone yet, have an NPC approach THEM (curious, not threatening)." +
                "\nSpawn an NPC if the scene feels empty. Make them interesting — a person, not a quest terminal." +
                "\nStill no combat unless the player explicitly picks a fight." + narratorInfo

            in 4..6 -> "=== PACING (Turn $turn — Exploration) ===" +
                "\nLet the player explore at their own pace. This is THEIR story." +
                "\nReward curiosity: examining things reveals lore, talking to NPCs reveals personality." +
                "\nIf the player seems ready for a class, let it happen naturally through their actions." +
                "\nIf the player asks questions about the world, answer richly — this is worldbuilding time." +
                "\nLight hints of danger are fine (distant sounds, nervous NPCs) but no direct threats yet." + narratorInfo

            in 7..10 -> "=== PACING (Turn $turn — Building) ===" +
                "\nThe world should feel alive now. NPCs have opinions. The environment has texture." +
                "\nClass selection should happen here IF it hasn't already — through narrative, not menus." +
                "\nIt's OK for tension to build: a locked door, a warning from an NPC, signs of something dangerous nearby." +
                "\nBut still follow the player's lead. If they want to keep exploring and talking, let them." +
                "\nFirst combat ONLY if the player seeks it out or makes a choice that logically leads to a fight." + narratorInfo

            in 11..15 -> "=== PACING (Turn $turn — Deepening) ===" +
                "\nBy now the player should have a class and feel grounded in the world." +
                "\nStart weaving in story threads: mysteries, NPC problems, environmental puzzles." +
                "\nCombat can happen naturally now, but it should feel like a consequence of choices, not a random encounter." +
                "\nQuiet moments are still valuable — a conversation, a discovery, a moment of beauty or dread." +
                "\nVary the emotional register: not every turn needs to escalate." + narratorInfo

            in 16..20 -> "=== PACING (Turn $turn — Emerging) ===" +
                "\nThe tutorial phase is ending. The player should feel competent and invested." +
                "\nStory stakes can rise naturally now. Threats feel earned because the player knows the world." +
                "\nMix action with quiet. After a fight, let them rest. After a revelation, let them process." +
                "\nThe player's choices from earlier turns should start having visible consequences." + narratorInfo

            else -> ""
        }
    }

    private fun inferOpeningMood(systemType: SystemType): String = when (systemType) {
        SystemType.SYSTEM_INTEGRATION, SystemType.HERO_AWAKENING -> "dark"
        SystemType.CULTIVATION_PATH, SystemType.ARCANE_ACADEMY -> "mysterious"
        SystemType.DEATH_LOOP, SystemType.DUNGEON_DELVE -> "tense"
        SystemType.TABLETOP_CLASSIC, SystemType.EPIC_JOURNEY -> "epic"
    }

    private fun buildOpeningSceneDescription(state: GameState): String = buildString {
        append(state.currentLocation.name)
        append(": ")
        append(state.currentLocation.description)
        val atmosphere = storyFoundation?.systemDefinition?.worldState
        if (!atmosphere.isNullOrBlank()) {
            append(". ")
            append(atmosphere)
        }
        append(". A lone figure stands amid the aftermath of integration.")
    }

    private fun createFoundationFromSeed(seed: com.rpgenerator.core.story.WorldSeed): StoryFoundation {
        val narratorContext = NarratorContext(
            systemName = seed.name,
            systemPersonality = seed.systemVoice.personality,
            centralMystery = seed.corePlot.centralQuestion,
            primaryThreat = seed.worldState.threats.firstOrNull() ?: "Unknown threats",
            thematicCore = seed.themes.firstOrNull() ?: "Survival and growth",
            activeThreads = seed.corePlot.majorChoices,
            upcomingBeats = listOf(seed.corePlot.actOneGoal),
            currentForeshadowing = seed.systemVoice.exampleMessages.take(2)
        )

        val systemDefinition = com.rpgenerator.core.story.SystemDefinition(
            systemType = gameState.systemType,
            systemName = seed.name,
            systemPersonality = seed.systemVoice.personality,
            uniqueMechanic = seed.powerSystem.uniqueMechanic,
            centralMystery = seed.corePlot.centralQuestion,
            primaryThreat = seed.worldState.threats.firstOrNull() ?: "Unknown threats",
            thematicCore = seed.themes.firstOrNull() ?: "Survival and growth",
            worldState = seed.worldState.atmosphere,
            keyFactions = emptyList(),
            narrativeHooks = seed.corePlot.majorChoices
        )

        val plotGraph = if (seed.id == "crawler") {
            CrawlerPlotGraphFactory.createInitialGraph(gameState.gameId)
        } else {
            com.rpgenerator.core.domain.PlotGraph(
                gameId = gameState.gameId,
                nodes = emptyMap(),
                edges = emptyMap()
            )
        }

        return StoryFoundation(
            systemDefinition = systemDefinition,
            plotGraph = plotGraph,
            plotThreads = emptyList(),
            initialForeshadowing = emptyList(),
            narratorContext = narratorContext
        )
    }
}
