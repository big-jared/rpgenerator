package com.rpgenerator.core.orchestration

import com.rpgenerator.core.agents.AutonomousNPCAgent
import com.rpgenerator.core.agents.GameMasterAgent
import com.rpgenerator.core.agents.LocationGeneratorAgent
import com.rpgenerator.core.agents.NarratorAgent
import com.rpgenerator.core.agents.NPCAgent
import com.rpgenerator.core.agents.QuestGeneratorAgent
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.QuestStatus
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.domain.LocationManager
import com.rpgenerator.core.domain.NPC
import com.rpgenerator.core.domain.ObjectiveType
import com.rpgenerator.core.domain.Quest
import com.rpgenerator.core.domain.QuestType
import com.rpgenerator.core.domain.QuestObjective
import com.rpgenerator.core.domain.QuestRewards
import com.rpgenerator.core.domain.PlayerClass
import com.rpgenerator.core.domain.Biome
import com.rpgenerator.core.rules.RulesEngine
import com.rpgenerator.core.skill.ActionContext
import com.rpgenerator.core.skill.SkillAcquisitionService
import com.rpgenerator.core.skill.SkillCombatService
import com.rpgenerator.core.skill.SkillDatabase
import com.rpgenerator.core.skill.SkillExecutionResult
import com.rpgenerator.core.story.MainStoryArc
import com.rpgenerator.core.story.NPCManager
import com.rpgenerator.core.story.StoryFoundation
import com.rpgenerator.core.story.StoryPlanningService
import com.rpgenerator.core.story.NarratorContext
import com.rpgenerator.core.story.WorldSeeds
import com.rpgenerator.core.generation.NPCArchetypeGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.rpgenerator.core.tools.GameTools
import com.rpgenerator.core.tools.GameToolsImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList


internal class GameOrchestrator(
    private val llm: LLMInterface,
    private var gameState: GameState
) {
    // Load the WorldSeed for narrator customization
    private val worldSeed = gameState.seedId?.let { WorldSeeds.byId(it) }

    // Lazy agent initialization - agents are only created when first used
    // Use the seed's narratorPrompt if available
    private val narratorAgent by lazy { NarratorAgent(llm, worldSeed?.narratorPrompt) }
    private val npcAgent by lazy { NPCAgent(llm) }
    private val locationGeneratorAgent by lazy { LocationGeneratorAgent(llm) }
    private val questGeneratorAgent by lazy { QuestGeneratorAgent(llm) }
    private val gameMasterAgent by lazy { GameMasterAgent(llm) }
    private val autonomousNPCAgent by lazy { AutonomousNPCAgent(llm) }
    private val npcArchetypeGenerator by lazy { NPCArchetypeGenerator(llm) }
    private val storyPlanningService by lazy { StoryPlanningService(llm) }
    private val npcManager = NPCManager()
    private val locationManager = LocationManager().apply {
        loadLocations(gameState.systemType)
    }
    private val rulesEngine = RulesEngine()
    private val tools by lazy { GameToolsImpl(locationManager, rulesEngine, locationGeneratorAgent, questGeneratorAgent, llm) }
    private val skillAcquisitionService = SkillAcquisitionService()
    private val skillCombatService = SkillCombatService()

    // Simple in-memory event log
    private val eventLog = mutableListOf<GameEvent>()

    // Story foundation - generated at game start, provides narrative context
    private var storyFoundation: StoryFoundation? = null
    private var storyPlanningStarted = false

    // Background scope for async operations
    private val backgroundScope = CoroutineScope(Dispatchers.Default)

    // Flag to track if we've initialized story NPCs
    private var storyNPCsInitialized = false

    suspend fun processInput(input: String): Flow<GameEvent> = flow {
        // Initialize NPCs first (adds to game state only, no events emitted yet)
        if (!storyNPCsInitialized) {
            initializeDynamicNPCsSilent()
            storyNPCsInitialized = true
        }

        // Play opening narration on first input (now NPCs are available in game state)
        if (!gameState.hasOpeningNarrationPlayed) {
            val openingNarration = narratorAgent.narrateOpening(gameState, storyFoundation?.narratorContext)
            emit(GameEvent.NarratorText(openingNarration))
            eventLog.add(GameEvent.NarratorText(openingNarration))
            gameState = gameState.copy(hasOpeningNarrationPlayed = true)

            // Extract and register any NPCs mentioned in the opening narration
            registerNPCsFromNarration(openingNarration)

            // Now emit quest/NPC events after the opening narration
            emitInitialQuestEvents(this)

            // Don't process empty input after opening - player hasn't acted yet
            if (input.isBlank()) return@flow
        }

        // Skip empty input - player hasn't provided an action
        if (input.isBlank()) {
            return@flow
        }

        // Check if player is dead before processing input
        if (gameState.isDead) {
            emit(GameEvent.SystemNotification("You are dead. Respawning..."))
            handleDeath(this, "previous combat")
            return@flow
        }

        // Use simple event log instead of complex EventStore
        val recentEvents = eventLog.takeLast(5)

        // Analyze intent to determine routing
        val intentAnalysis = tools.analyzeIntent(input, gameState, recentEvents)

        // Menu/system intents short-circuit (no narration needed)
        when (intentAnalysis.intent) {
            Intent.SYSTEM_QUERY -> {
                val status = tools.getPlayerStatus(gameState)
                val statusText = "Level ${status.level}, XP: ${status.xp}/${status.xpToNextLevel}"
                val statusEvent = GameEvent.SystemNotification(statusText)
                emit(statusEvent)
                eventLog.add(statusEvent)
                return@flow
            }
            Intent.SKILL_MENU -> {
                handleSkillMenu(this)
                return@flow
            }
            Intent.SKILL_EVOLUTION -> {
                handleSkillEvolution(intentAnalysis.target, this)
                return@flow
            }
            Intent.SKILL_FUSION -> {
                handleSkillFusion(this, input)
                return@flow
            }
            Intent.STATUS_MENU -> {
                handleStatusMenu(this)
                return@flow
            }
            Intent.INVENTORY_MENU -> {
                handleInventoryMenu(this)
                return@flow
            }
            Intent.QUEST_ACTION -> {
                handleQuestActionMenu(input, this)
                return@flow
            }
            Intent.CLASS_SELECTION -> {
                handleClassSelection(intentAnalysis.target, input, this)
                return@flow
            }
            Intent.USE_SKILL -> {
                handleUseSkill(intentAnalysis.target, this, input)
                return@flow
            }
            else -> {
                // Narrative intents (COMBAT, EXPLORATION, NPC_DIALOGUE, MOVEMENT, INTERACTION)
                // go through the coordinated path: GM plans → mechanics execute → Narrator renders
                handleCoordinatedPath(input, recentEvents, this)
            }
        }
    }


    /**
     * Coordinated path - Game Master creates a scene plan, mechanics execute, Narrator renders.
     *
     * Flow:
     * 1. GM analyzes situation and creates a ScenePlan
     * 2. Execute mechanical actions (combat, movement, etc.)
     * 3. Build SceneResults from mechanical execution
     * 4. Narrator renders plan + results into cohesive prose
     * 5. Emit single unified narrative
     */
    private suspend fun handleCoordinatedPath(
        input: String,
        recentEvents: List<GameEvent>,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        // Convert recent events to strings for context
        val recentEventStrings = recentEvents.map { eventToString(it) }

        // Step 1: Game Master creates the scene plan
        val scenePlan = gameMasterAgent.planScene(
            playerInput = input,
            state = gameState,
            recentEvents = recentEventStrings,
            npcsAtLocation = gameState.getNPCsAtCurrentLocation()
        )

        // Validate the planned action
        val intentAnalysis = tools.analyzeIntent(input, gameState, recentEvents)
        val validation = tools.validateAction(
            intentAnalysis.intent,
            intentAnalysis.target,
            gameState
        )

        if (!validation.valid) {
            flowCollector.emit(GameEvent.SystemNotification("Cannot perform action: ${validation.reason}"))
            return
        }

        // Step 2: Execute mechanical actions and collect results
        val sceneResults = executeMechanicalActions(scenePlan, intentAnalysis, input)

        // Step 3: Narrator renders the complete scene
        val unifiedNarration = narratorAgent.renderScene(
            plan = scenePlan,
            results = sceneResults,
            state = gameState,
            playerInput = input,
            narratorContext = storyFoundation?.narratorContext
        )

        // Step 4: Emit the unified narrative
        val narrativeEvent = GameEvent.NarratorText(unifiedNarration)
        flowCollector.emit(narrativeEvent)
        eventLog.add(narrativeEvent)

        // Extract and register any new NPCs mentioned in the narration
        registerNPCsFromNarration(unifiedNarration)

        // Emit mechanical events for UI tracking (these happen silently in the background)
        emitMechanicalEvents(sceneResults, flowCollector)

        // Handle any triggered events from the scene plan
        handleTriggeredEvents(scenePlan.triggeredEvents, flowCollector)
    }

    /**
     * Execute the mechanical parts of a scene and return results
     */
    private suspend fun executeMechanicalActions(
        plan: ScenePlan,
        intentAnalysis: com.rpgenerator.core.tools.IntentAnalysis,
        input: String
    ): SceneResults {
        var combatResult: CombatSceneResult? = null
        var xpChange: XPChange? = null
        val itemsGained = mutableListOf<ItemGain>()
        val locationsDiscovered = mutableListOf<String>()
        val questUpdates = mutableListOf<QuestProgressUpdate>()
        val stateChanges = mutableListOf<String>()

        when (plan.primaryAction.type) {
            ActionType.COMBAT -> {
                val target = plan.primaryAction.target ?: intentAnalysis.target ?: "enemy"
                val result = tools.resolveCombat(target, gameState)

                combatResult = CombatSceneResult(
                    target = target,
                    damageDealt = result.damage,
                    damageReceived = 0, // TODO: Track damage received
                    enemyDefeated = true, // Simplified for now
                    criticalHit = result.damage > 20, // Simplified crit detection
                    specialEffects = emptyList()
                )

                if (result.xpGained > 0) {
                    val levelBefore = gameState.playerLevel
                    gameState = gameState.gainXP(result.xpGained)
                    val levelAfter = gameState.playerLevel
                    val didLevelUp = levelAfter > levelBefore

                    xpChange = XPChange(
                        xpGained = result.xpGained,
                        newTotal = gameState.playerXP,
                        leveledUp = didLevelUp,
                        newLevel = if (didLevelUp) levelAfter else null
                    )
                }

                // Mark tutorial "test abilities" objective on combat
                val tutorialQuest = gameState.activeQuests["quest_survive_tutorial"]
                if (tutorialQuest != null) {
                    val testObj = tutorialQuest.objectives.find { it.id == "tutorial_obj_test" }
                    if (testObj != null && !testObj.isComplete()) {
                        gameState = gameState.updateQuestObjective(tutorialQuest.id, "tutorial_obj_test", 1)
                    }
                }

                // Handle loot
                result.loot.forEach { generatedItem ->
                    itemsGained.add(ItemGain(
                        itemName = generatedItem.getName(),
                        quantity = generatedItem.getQuantity(),
                        rarity = generatedItem.rarity.name.lowercase()
                    ))
                    gameState = addItemToInventory(generatedItem)
                }

                if (result.gold > 0) {
                    stateChanges.add("Gained ${result.gold} gold")
                }

                // Check for death
                if (gameState.isDead) {
                    stateChanges.add("Player died")
                }
            }

            ActionType.EXPLORATION -> {
                if (intentAnalysis.shouldGenerateLocation && intentAnalysis.locationGenerationContext != null) {
                    val generatedLocation = tools.generateLocation(
                        parentLocation = gameState.currentLocation,
                        discoveryContext = intentAnalysis.locationGenerationContext,
                        state = gameState
                    )
                    if (generatedLocation != null) {
                        gameState = gameState.addCustomLocation(generatedLocation)
                        locationsDiscovered.add(generatedLocation.name)
                    }
                } else {
                    val connectedLocations = tools.getConnectedLocations(gameState)
                    connectedLocations.forEach { loc ->
                        if (!gameState.discoveredTemplateLocations.contains(loc.id)) {
                            gameState = gameState.discoverLocation(loc.id)
                            locationsDiscovered.add(loc.name)
                        }
                    }
                }
            }

            ActionType.DIALOGUE -> {
                val npcName = plan.primaryAction.target ?: intentAnalysis.target
                if (npcName != null) {
                    val npc = gameState.findNPCByName(npcName)
                    if (npc != null) {
                        // Generate NPC dialogue and save to conversation history
                        val npcResponse = npcAgent.generateDialogue(npc, input, gameState)
                        val updatedNPC = npc.addConversation(input, npcResponse, gameState.playerLevel)
                        gameState = gameState.updateNPC(updatedNPC)

                        // Update relationship
                        val relationshipChange = calculateRelationshipChange(input)
                        if (relationshipChange != 0) {
                            val npcWithRelationship = updatedNPC.updateRelationship(gameState.gameId, relationshipChange)
                            gameState = gameState.updateNPC(npcWithRelationship)
                        }

                        stateChanges.add("Spoke with ${npc.name}: $npcResponse")
                    }
                }
            }

            ActionType.QUEST_ACTION -> {
                // Handle quest actions
                handleQuestAction(input, questUpdates, stateChanges)
            }

            ActionType.MOVEMENT -> {
                // Try to resolve movement target to a connected location
                val targetName = plan.primaryAction.target ?: intentAnalysis.target
                if (targetName != null) {
                    val connectedLocations = tools.getConnectedLocations(gameState)
                    val allLocations = connectedLocations + gameState.customLocations.values

                    // Fuzzy match: exact, contains, or partial word match
                    val targetLower = targetName.lowercase()
                    val destination = allLocations.find { it.name.equals(targetName, ignoreCase = true) }
                        ?: allLocations.find { it.name.lowercase().contains(targetLower) }
                        ?: allLocations.find { loc ->
                            targetLower.split(" ").any { word ->
                                word.length > 2 && loc.name.lowercase().contains(word)
                            }
                        }

                    if (destination != null) {
                        gameState = gameState.moveToLocation(destination)
                        gameState = gameState.discoverLocation(destination.id)
                        locationsDiscovered.add(destination.name)
                        stateChanges.add("Moved to ${destination.name}")
                    }
                }
            }

            else -> {
                // SYSTEM_QUERY, INTERACTION - minimal mechanical impact
            }
        }

        // Track quest progress for all action types
        trackQuestProgressSilent(intentAnalysis.intent, intentAnalysis.target, questUpdates)

        return SceneResults(
            combatResult = combatResult,
            itemsGained = itemsGained,
            xpChange = xpChange,
            locationsDiscovered = locationsDiscovered,
            questUpdates = questUpdates,
            stateChanges = stateChanges
        )
    }

    private fun addItemToInventory(generatedItem: com.rpgenerator.core.loot.GeneratedItem): GameState {
        return when (generatedItem) {
            is com.rpgenerator.core.loot.GeneratedItem.GeneratedInventoryItem -> {
                val apiRarity = com.rpgenerator.core.api.ItemRarity.valueOf(generatedItem.rarity.name)
                gameState.addItem(generatedItem.item.copy(rarity = apiRarity))
            }
            else -> {
                val apiRarity = com.rpgenerator.core.api.ItemRarity.valueOf(generatedItem.rarity.name)
                gameState.addItem(
                    com.rpgenerator.core.domain.InventoryItem(
                        id = generatedItem.getId(),
                        name = generatedItem.getName(),
                        description = "Equipment: ${generatedItem.getName()}",
                        type = com.rpgenerator.core.domain.ItemType.MISC,
                        quantity = 1,
                        stackable = false,
                        rarity = apiRarity
                    )
                )
            }
        }
    }

    /**
     * Handle quest menu actions (list, generate, complete) — short-circuit path.
     */
    private suspend fun handleQuestActionMenu(
        input: String,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val lowerInput = input.lowercase()
        when {
            lowerInput.contains("list") || lowerInput.contains("show") -> {
                val quests = tools.getActiveQuests(gameState)
                if (quests.isEmpty()) {
                    val noQuests = GameEvent.SystemNotification("You have no active quests.")
                    flowCollector.emit(noQuests)
                    eventLog.add(noQuests)
                } else {
                    quests.forEach { quest ->
                        val questInfo = GameEvent.SystemNotification(
                            "${quest.name} (${quest.type}) - ${quest.completionPercentage}% complete"
                        )
                        flowCollector.emit(questInfo)
                        eventLog.add(questInfo)
                    }
                }
            }
            lowerInput.contains("new") || lowerInput.contains("generate") || lowerInput.contains("get") -> {
                val newQuest = tools.generateQuest(gameState, null, input)
                if (newQuest != null) {
                    gameState = gameState.addQuest(newQuest)
                    val questEvent = GameEvent.QuestUpdate(
                        questId = newQuest.id,
                        questName = newQuest.name,
                        status = QuestStatus.NEW
                    )
                    flowCollector.emit(questEvent)
                    eventLog.add(questEvent)
                    val questDetails = GameEvent.SystemNotification(
                        "New Quest: ${newQuest.name}\n${newQuest.description}"
                    )
                    flowCollector.emit(questDetails)
                    eventLog.add(questDetails)
                } else {
                    val failure = GameEvent.SystemNotification("Failed to generate quest.")
                    flowCollector.emit(failure)
                    eventLog.add(failure)
                }
            }
            lowerInput.contains("complete") || lowerInput.contains("turn in") -> {
                val completableQuests = gameState.activeQuests.values.filter { it.isComplete() }
                if (completableQuests.isEmpty()) {
                    val none = GameEvent.SystemNotification("You have no quests ready to complete.")
                    flowCollector.emit(none)
                    eventLog.add(none)
                } else {
                    completableQuests.forEach { quest ->
                        gameState = gameState.completeQuest(quest.id)
                        val questCompleted = GameEvent.QuestUpdate(
                            questId = quest.id,
                            questName = quest.name,
                            status = QuestStatus.COMPLETED
                        )
                        flowCollector.emit(questCompleted)
                        eventLog.add(questCompleted)
                        val rewardNotif = GameEvent.SystemNotification(
                            "Quest Complete: ${quest.name}! Gained ${quest.rewards.xp} XP"
                        )
                        flowCollector.emit(rewardNotif)
                        eventLog.add(rewardNotif)
                        quest.rewards.items.forEach { item ->
                            val itemGained = GameEvent.ItemGained(item.id, item.name, item.quantity)
                            flowCollector.emit(itemGained)
                            eventLog.add(itemGained)
                        }
                    }
                }
            }
            else -> {
                val help = GameEvent.SystemNotification(
                    "Quest commands: 'list quests', 'get new quest', 'complete quests'"
                )
                flowCollector.emit(help)
                eventLog.add(help)
            }
        }
    }

    private fun calculateRelationshipChange(input: String): Int {
        val lowerInput = input.lowercase()
        return when {
            lowerInput.contains("thank") || lowerInput.contains("please") -> 5
            lowerInput.contains("insult") || lowerInput.contains("threaten") -> -10
            else -> 1
        }
    }

    private fun handleQuestAction(
        input: String,
        questUpdates: MutableList<QuestProgressUpdate>,
        stateChanges: MutableList<String>
    ) {
        val lowerInput = input.lowercase()
        when {
            lowerInput.contains("complete") || lowerInput.contains("turn in") -> {
                val completableQuests = gameState.activeQuests.values.filter { it.isComplete() }
                completableQuests.forEach { quest ->
                    gameState = gameState.completeQuest(quest.id)
                    questUpdates.add(QuestProgressUpdate(
                        questName = quest.name,
                        objectiveCompleted = "All objectives",
                        questComplete = true
                    ))
                    stateChanges.add("Completed quest: ${quest.name}")
                }
            }
        }
    }

    private fun trackQuestProgressSilent(
        intent: Intent,
        target: String?,
        questUpdates: MutableList<QuestProgressUpdate>
    ) {
        gameState.activeQuests.values.forEach { quest ->
            quest.objectives.forEach { objective ->
                val shouldUpdate = when (objective.type) {
                    ObjectiveType.KILL -> intent == Intent.COMBAT && target != null &&
                            objective.targetId.lowercase() == target.lowercase()
                    ObjectiveType.REACH_LOCATION -> objective.targetId == gameState.currentLocation.id &&
                            !objective.isComplete()
                    ObjectiveType.EXPLORE -> intent == Intent.EXPLORATION &&
                            gameState.discoveredTemplateLocations.contains(objective.targetId)
                    else -> false
                }

                if (shouldUpdate && !objective.isComplete()) {
                    gameState = gameState.updateQuestObjective(quest.id, objective.id, 1)
                    val updatedQuest = gameState.activeQuests[quest.id]
                    val updatedObj = updatedQuest?.objectives?.find { it.id == objective.id }

                    if (updatedObj != null) {
                        questUpdates.add(QuestProgressUpdate(
                            questName = quest.name,
                            objectiveCompleted = updatedObj.progressDescription(),
                            questComplete = updatedQuest.isComplete()
                        ))
                    }
                }
            }
        }
    }

    private suspend fun emitMechanicalEvents(
        results: SceneResults,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        // These are emitted for UI tracking but don't add narrative text

        results.xpChange?.let { xp ->
            val statChange = GameEvent.StatChange(
                "xp",
                (xp.newTotal - xp.xpGained).toInt(),
                xp.newTotal.toInt()
            )
            flowCollector.emit(statChange)
            eventLog.add(statChange)

            if (xp.leveledUp && xp.newLevel != null) {
                val levelUp = GameEvent.SystemNotification("Level up! You are now level ${xp.newLevel}")
                flowCollector.emit(levelUp)
                eventLog.add(levelUp)
            }
        }

        results.itemsGained.forEach { item ->
            val itemEvent = GameEvent.ItemGained(
                itemId = item.itemName.lowercase().replace(" ", "_"),
                itemName = item.itemName,
                quantity = item.quantity
            )
            flowCollector.emit(itemEvent)
            eventLog.add(itemEvent)
        }

        results.questUpdates.filter { it.questComplete }.forEach { update ->
            val questEvent = GameEvent.QuestUpdate(
                questId = update.questName.lowercase().replace(" ", "_"),
                questName = update.questName,
                status = QuestStatus.COMPLETED
            )
            flowCollector.emit(questEvent)
            eventLog.add(questEvent)
        }
    }

    private suspend fun handleTriggeredEvents(
        triggeredEvents: List<TriggeredEvent>,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        triggeredEvents.filter { it.timing == EventTiming.IMMEDIATE }.forEach { event ->
            when (event.eventType) {
                EventType.NPC_ARRIVAL -> {
                    // Handle NPC spawning
                    val notification = GameEvent.SystemNotification(event.description)
                    flowCollector.emit(notification)
                    eventLog.add(notification)
                }
                EventType.ENCOUNTER -> {
                    val notification = GameEvent.SystemNotification(event.description)
                    flowCollector.emit(notification)
                    eventLog.add(notification)
                }
                else -> {
                    // Other event types handled silently or through narrative
                }
            }
        }
    }

    private fun eventToString(event: GameEvent): String = when (event) {
        is GameEvent.NarratorText -> event.text
        is GameEvent.SystemNotification -> event.text
        is GameEvent.NPCDialogue -> "${event.npcName}: ${event.text}"
        else -> event.toString()
    }


    /**
     * Handle class selection during tutorial.
     * Shows classes grouped by archetype and supports custom class generation
     * for players who push for something unique.
     */
    private suspend fun handleClassSelection(
        chosenClassName: String?,
        input: String,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        // Check if player already has a class
        if (gameState.characterSheet.playerClass != PlayerClass.NONE) {
            val alreadyHasClass = GameEvent.SystemNotification(
                "You have already chosen the ${gameState.characterSheet.playerClass.displayName} class."
            )
            flowCollector.emit(alreadyHasClass)
            eventLog.add(alreadyHasClass)
            return
        }

        // Check if player is asking for a custom/unique class
        val lowerInput = input.lowercase()
        val isAskingForCustom = lowerInput.contains("custom") ||
            lowerInput.contains("unique") ||
            lowerInput.contains("special") ||
            lowerInput.contains("different") ||
            lowerInput.contains("create my own") ||
            lowerInput.contains("make my own") ||
            lowerInput.contains("something else") ||
            lowerInput.contains("none of these") ||
            lowerInput.contains("other") ||
            (lowerInput.contains("want") && lowerInput.contains("be")) || // "I want to be a..."
            (lowerInput.contains("can i") && lowerInput.contains("be"))   // "Can I be a..."

        if (isAskingForCustom && chosenClassName == null) {
            // Player is pushing for something custom - engage with them
            handleCustomClassRequest(input, flowCollector)
            return
        }

        // If no specific class chosen, show options grouped by archetype
        if (chosenClassName == null) {
            showClassOptions(flowCollector)
            return
        }

        // Check if this looks like a custom class request disguised as a class name
        val availableClasses = PlayerClass.selectableClasses()
        val selectedClass = availableClasses.find {
            it.name.equals(chosenClassName, ignoreCase = true) ||
            it.displayName.equals(chosenClassName, ignoreCase = true)
        }

        if (selectedClass == null) {
            // Not a standard class - this might be a custom class request!
            // Try to generate a custom class based on what they asked for
            handleCustomClassRequest(input, flowCollector, customClassName = chosenClassName)
            return
        }

        // Apply the class selection
        applyClassSelection(selectedClass, flowCollector)
    }

    /**
     * Show class options grouped by archetype.
     */
    private suspend fun showClassOptions(flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>) {
        val header = GameEvent.SystemNotification(
            "╔══════════════════════════════════════════════════════════════╗\n" +
            "║              SYSTEM CLASS SELECTION                          ║\n" +
            "║  Choose your path. This decision shapes your destiny.        ║\n" +
            "╚══════════════════════════════════════════════════════════════╝"
        )
        flowCollector.emit(header)
        eventLog.add(header)

        // Group classes by archetype
        val classesByArchetype = PlayerClass.byArchetype()

        classesByArchetype.forEach { (archetype, classes) ->
            val archetypeHeader = GameEvent.SystemNotification(
                "\n═══ ${archetype.displayName.uppercase()} ═══"
            )
            flowCollector.emit(archetypeHeader)
            eventLog.add(archetypeHeader)

            classes.forEach { playerClass ->
                val classInfo = GameEvent.SystemNotification(
                    "  【${playerClass.displayName}】 ${playerClass.description}"
                )
                flowCollector.emit(classInfo)
                eventLog.add(classInfo)
            }
        }

        val footer = GameEvent.SystemNotification(
            "\n═══════════════════════════════════════════════════════════════\n" +
            "Type a class name to select it.\n" +
            "Or describe what kind of path you want - the System may accommodate.\n" +
            "═══════════════════════════════════════════════════════════════"
        )
        flowCollector.emit(footer)
        eventLog.add(footer)

        // Generate narrative for class selection moment
        val availableClasses = PlayerClass.selectableClasses()
        val narration = narratorAgent.narrateClassSelection(gameState, availableClasses)
        val narrativeEvent = GameEvent.NarratorText(narration)
        flowCollector.emit(narrativeEvent)
        eventLog.add(narrativeEvent)
    }

    /**
     * Handle requests for custom/unique classes.
     * The System (via LLM) evaluates if the request is worthy and generates a unique class.
     */
    private suspend fun handleCustomClassRequest(
        input: String,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>,
        customClassName: String? = null
    ) {
        val requestedClass = customClassName ?: input

        // Ask the LLM to evaluate and potentially generate a custom class
        val customClassResult = generateCustomClass(requestedClass, gameState)

        if (customClassResult != null) {
            // The System grants a custom class!
            val grantNotice = GameEvent.SystemNotification(
                "╔══════════════════════════════════════════════════════════════╗\n" +
                "║           SYSTEM NOTIFICATION: UNIQUE CLASS DETECTED         ║\n" +
                "╚══════════════════════════════════════════════════════════════╝\n\n" +
                "The System recognizes your unconventional request.\n" +
                "Analyzing compatibility... Generating unique class template..."
            )
            flowCollector.emit(grantNotice)
            eventLog.add(grantNotice)

            // Apply the custom class (we'll map it to the closest archetype)
            val baseClass = customClassResult.baseClass
            applyClassSelection(baseClass, flowCollector, customClassResult.customName, customClassResult.customDescription)
        } else {
            // The System doesn't grant a custom class - guide them back to options
            val denial = GameEvent.SystemNotification(
                "The System considers your request...\n\n" +
                "\"${requestedClass}\" is not recognized as a valid class path.\n" +
                "Perhaps describe what you're looking for? Or choose from the available paths.\n" +
                "\nType 'classes' to see available options."
            )
            flowCollector.emit(denial)
            eventLog.add(denial)
        }
    }

    /**
     * Generate a custom class based on player request.
     * Returns null if the request doesn't warrant a custom class.
     */
    private suspend fun generateCustomClass(request: String, state: GameState): CustomClassResult? {
        // Use LLM to evaluate and generate a custom class
        val systemPrompt = """
You are the System, an impartial cosmic force that assigns classes to Integrated beings.
You evaluate non-standard class requests and either grant unique classes or reject unworthy requests.
Respond ONLY in the exact format specified - no additional text.
""".trim()

        val prompt = """
A player has requested a non-standard class: "$request"
Player backstory: ${state.backstory}
Player name: ${state.playerName}

Evaluate if this is a legitimate creative request worthy of a unique class.
Reject requests that are:
- Too vague (just "custom" or "something cool")
- Joke/troll requests
- Overpowered wish fulfillment ("god" "invincible" etc)

If worthy, generate a unique class that:
1. Fits the LitRPG/System Apocalypse genre
2. Has a unique identity related to their request
3. Maps to one of these base archetypes: SLAYER, BULWARK, STRIKER, CHANNELER, CULTIVATOR, PSION, ADAPTER, SURVIVALIST, BLADE_DANCER, ARTIFICER, HEALER, COMMANDER, CONTRACTOR, GLITCH, ECHO

Respond in EXACTLY this format (or REJECT if not worthy):
ACCEPT
CLASS_NAME: [Unique class name, 1-3 words]
DESCRIPTION: [One sentence description of the class]
BASE_ARCHETYPE: [One of the archetypes listed above]

Or if rejecting:
REJECT
REASON: [Brief reason]
""".trim()

        val agent = llm.startAgent(systemPrompt)
        val response = agent.sendMessage(prompt).toList().joinToString("")
        val lines = response.trim().lines()

        if (lines.isEmpty() || lines[0].trim().uppercase() == "REJECT") {
            return null
        }

        if (lines[0].trim().uppercase() == "ACCEPT" && lines.size >= 4) {
            val className = lines.find { it.startsWith("CLASS_NAME:") }
                ?.substringAfter(":")?.trim() ?: return null
            val description = lines.find { it.startsWith("DESCRIPTION:") }
                ?.substringAfter(":")?.trim() ?: return null
            val archetypeName = lines.find { it.startsWith("BASE_ARCHETYPE:") }
                ?.substringAfter(":")?.trim()?.uppercase() ?: return null

            val baseClass = try {
                PlayerClass.valueOf(archetypeName)
            } catch (e: Exception) {
                PlayerClass.ADAPTER // Default fallback
            }

            return CustomClassResult(
                customName = className,
                customDescription = description,
                baseClass = baseClass
            )
        }

        return null
    }

    /**
     * Apply the class selection to the character.
     */
    private suspend fun applyClassSelection(
        selectedClass: PlayerClass,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>,
        customName: String? = null,
        customDescription: String? = null
    ) {
        val displayName = customName ?: selectedClass.displayName
        val description = customDescription ?: selectedClass.description

        var newSheet = gameState.characterSheet.chooseInitialClass(selectedClass)

        // Generate starter skills via LLM
        val starterSkills = generateStarterSkills(displayName, description)
        for (skill in starterSkills) {
            newSheet = newSheet.addSkill(skill)
        }

        gameState = gameState.copy(characterSheet = newSheet)

        // Mark tutorial objective complete
        val tutorialQuest = gameState.activeQuests["quest_survive_tutorial"]
        if (tutorialQuest != null) {
            gameState = gameState.updateQuestObjective(tutorialQuest.id, "tutorial_obj_class", 1)
        }

        // Emit class selection notification
        val isCustom = customName != null
        val headerText = if (isCustom) "UNIQUE CLASS GRANTED" else "CLASS CHOSEN"
        val paddedName = displayName.uppercase().take(22).padEnd(22)

        val classChosen = GameEvent.SystemNotification(
            "╔════════════════════════════════════════╗\n" +
            "║  $headerText: $paddedName║\n" +
            "╚════════════════════════════════════════╝\n\n" +
            "$description\n\n" +
            if (isCustom) "Base archetype: ${selectedClass.displayName}\n" else "" +
            "Stat bonuses applied!"
        )
        flowCollector.emit(classChosen)
        eventLog.add(classChosen)

        // Emit starter skills notification
        if (starterSkills.isNotEmpty()) {
            val skillsText = starterSkills.joinToString("\n") { skill ->
                "  【${skill.name}】 ${skill.description}"
            }
            val skillsNotification = GameEvent.SystemNotification(
                "╔════════════════════════════════════════╗\n" +
                "║         STARTER SKILLS GRANTED         ║\n" +
                "╚════════════════════════════════════════╝\n\n" +
                skillsText
            )
            flowCollector.emit(skillsNotification)
            eventLog.add(skillsNotification)
        }

        // Generate narrative for class acquisition
        val narration = narratorAgent.narrateClassAcquisition(gameState, selectedClass)
        val narrativeEvent = GameEvent.NarratorText(narration)
        flowCollector.emit(narrativeEvent)
        eventLog.add(narrativeEvent)

        // Quest progress notification
        val progressEvent = GameEvent.SystemNotification(
            "Quest Progress: System Integration - Choose your class (Complete)"
        )
        flowCollector.emit(progressEvent)
        eventLog.add(progressEvent)
    }

    /**
     * Generate starter skills for a class using LLM.
     */
    private suspend fun generateStarterSkills(className: String, classDescription: String): List<com.rpgenerator.core.skill.Skill> {
        val prompt = """
Generate 2 starter skills for a "$className" class in a LitRPG System Integration setting.
Class description: $classDescription

For each skill, provide:
- NAME: A short, evocative skill name (2-4 words)
- TYPE: ACTIVE or PASSIVE
- DESC: One sentence describing what the skill does

Format your response EXACTLY like this (no other text):
SKILL1_NAME: [name]
SKILL1_TYPE: [ACTIVE/PASSIVE]
SKILL1_DESC: [description]
SKILL2_NAME: [name]
SKILL2_TYPE: [ACTIVE/PASSIVE]
SKILL2_DESC: [description]
""".trim()

        return try {
            val agent = llm.startAgent("You generate LitRPG skills. Follow the exact format requested.")
            val response = agent.sendMessage(prompt).toList().joinToString("")

            parseGeneratedSkills(response, className)
        } catch (e: Exception) {
            // Fallback to generic skills if LLM fails
            listOf(
                com.rpgenerator.core.skill.SkillDatabase.createGeneratedSkill(
                    name = "$className Strike",
                    description = "A basic attack technique granted by the System upon class selection.",
                    className = className,
                    isActive = true
                )
            )
        }
    }

    /**
     * Parse LLM response into Skill objects.
     */
    private fun parseGeneratedSkills(response: String, className: String): List<com.rpgenerator.core.skill.Skill> {
        val skills = mutableListOf<com.rpgenerator.core.skill.Skill>()

        val skill1Name = Regex("SKILL1_NAME:\\s*(.+)").find(response)?.groupValues?.get(1)?.trim()
        val skill1Type = Regex("SKILL1_TYPE:\\s*(ACTIVE|PASSIVE)", RegexOption.IGNORE_CASE).find(response)?.groupValues?.get(1)?.trim()
        val skill1Desc = Regex("SKILL1_DESC:\\s*(.+)").find(response)?.groupValues?.get(1)?.trim()

        if (skill1Name != null && skill1Desc != null) {
            skills.add(com.rpgenerator.core.skill.SkillDatabase.createGeneratedSkill(
                name = skill1Name,
                description = skill1Desc,
                className = className,
                isActive = skill1Type?.uppercase() != "PASSIVE"
            ))
        }

        val skill2Name = Regex("SKILL2_NAME:\\s*(.+)").find(response)?.groupValues?.get(1)?.trim()
        val skill2Type = Regex("SKILL2_TYPE:\\s*(ACTIVE|PASSIVE)", RegexOption.IGNORE_CASE).find(response)?.groupValues?.get(1)?.trim()
        val skill2Desc = Regex("SKILL2_DESC:\\s*(.+)").find(response)?.groupValues?.get(1)?.trim()

        if (skill2Name != null && skill2Desc != null) {
            skills.add(com.rpgenerator.core.skill.SkillDatabase.createGeneratedSkill(
                name = skill2Name,
                description = skill2Desc,
                className = className,
                isActive = skill2Type?.uppercase() != "PASSIVE"
            ))
        }

        return skills
    }

    /**
     * Use LLM to resolve which NPC the player is trying to interact with.
     */
    private suspend fun resolveNPCWithLLM(playerInput: String, availableNpcs: List<NPC>): NPC? {
        if (availableNpcs.isEmpty()) return null
        if (availableNpcs.size == 1) return availableNpcs.first()

        val npcList = availableNpcs.mapIndexed { i, npc ->
            "${i + 1}. ${npc.name} (${npc.archetype.name.lowercase().replace("_", " ")})"
        }.joinToString("\n")

        val prompt = """
Player input: "$playerInput"

NPCs present:
$npcList

Which NPC (if any) is the player trying to interact with?
Respond with ONLY the number (1, 2, etc.) or "NONE" if unclear.
""".trim()

        val agent = llm.startAgent("You resolve ambiguous NPC references. Respond only with a number or NONE.")
        val response = agent.sendMessage(prompt).toList().joinToString("").trim()

        val index = response.toIntOrNull()?.minus(1)
        return if (index != null && index in availableNpcs.indices) {
            availableNpcs[index]
        } else {
            null
        }
    }

    /** Result of custom class generation */
    private data class CustomClassResult(
        val customName: String,
        val customDescription: String,
        val baseClass: PlayerClass
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SKILL HANDLERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Display the skill menu.
     */
    private suspend fun handleSkillMenu(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val sheet = gameState.characterSheet
        val skills = sheet.skills

        if (skills.isEmpty()) {
            val noSkills = GameEvent.SystemNotification(
                "╔════════════════════════════════════════╗\n" +
                "║            SKILLS                      ║\n" +
                "╚════════════════════════════════════════╝\n" +
                "\nYou haven't learned any skills yet.\n" +
                "Skills are learned by:\n" +
                "  • Repeating actions (Action Insight)\n" +
                "  • Class selection (starter skills)\n" +
                "  • Quest rewards\n" +
                "  • Skill books and NPCs"
            )
            flowCollector.emit(noSkills)
            eventLog.add(noSkills)
            return
        }

        val header = GameEvent.SystemNotification(
            "╔════════════════════════════════════════╗\n" +
            "║            SKILLS (${skills.size.toString().padStart(2)})                   ║\n" +
            "╚════════════════════════════════════════╝"
        )
        flowCollector.emit(header)
        eventLog.add(header)

        // Display each skill
        skills.forEachIndexed { index, skill ->
            val cooldownStr = if (skill.currentCooldown > 0) " [CD: ${skill.currentCooldown}]" else ""
            val canEvolveStr = if (skill.canEvolve()) " ★EVOLVE READY★" else ""
            val activeStr = if (skill.isActive) "" else " (Passive)"

            val skillInfo = GameEvent.SystemNotification(
                "\n${index + 1}. [${skill.rarity.symbol}] ${skill.name} Lv.${skill.level}$activeStr\n" +
                "   ${skill.xpBar()} ${skill.levelProgress()}% to next level\n" +
                "   Cost: ${skill.costString()}$cooldownStr$canEvolveStr\n" +
                "   ${skill.description}"
            )
            flowCollector.emit(skillInfo)
            eventLog.add(skillInfo)
        }

        // Show partial skills (hints)
        val partialSkills = sheet.getPartialSkills()
        if (partialSkills.isNotEmpty()) {
            val partialHeader = GameEvent.SystemNotification("\n--- Developing Skills (Insights) ---")
            flowCollector.emit(partialHeader)
            eventLog.add(partialHeader)

            partialSkills.forEach { partial ->
                val hintText = GameEvent.SystemNotification("  ${partial.hintText()}")
                flowCollector.emit(hintText)
                eventLog.add(hintText)
            }
        }

        // Show help
        val helpText = GameEvent.SystemNotification(
            "\nCommands: 'use [skill name]', 'evolve skill [name]', 'fuse skills'"
        )
        flowCollector.emit(helpText)
        eventLog.add(helpText)

        // Mark tutorial objective if applicable
        val tutorialQuest = gameState.activeQuests["quest_survive_tutorial"]
        if (tutorialQuest != null) {
            val skillObj = tutorialQuest.objectives.find { it.id == "tutorial_obj_test" }
            if (skillObj != null && !skillObj.isComplete()) {
                gameState = gameState.updateQuestObjective(tutorialQuest.id, "tutorial_obj_test", 1)
                val progressNotif = GameEvent.SystemNotification(
                    "Quest Progress: Survive the Tutorial - Learn a basic skill (Complete)"
                )
                flowCollector.emit(progressNotif)
                eventLog.add(progressNotif)
            }
        }
    }

    /**
     * Handle using a skill.
     */
    private suspend fun handleUseSkill(
        skillNameOrId: String?,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>,
        input: String
    ) {
        if (skillNameOrId == null) {
            val noSkill = GameEvent.SystemNotification("Specify a skill to use. Example: 'use Power Strike'")
            flowCollector.emit(noSkill)
            eventLog.add(noSkill)
            return
        }

        val sheet = gameState.characterSheet
        val skill = sheet.skills.find {
            it.id.equals(skillNameOrId, ignoreCase = true) ||
            it.name.equals(skillNameOrId, ignoreCase = true) ||
            it.name.lowercase().contains(skillNameOrId.lowercase())
        }

        if (skill == null) {
            val unknownSkill = GameEvent.SystemNotification(
                "You don't know a skill called '$skillNameOrId'.\n" +
                "Known skills: ${sheet.skills.joinToString(", ") { it.name }}"
            )
            flowCollector.emit(unknownSkill)
            eventLog.add(unknownSkill)
            return
        }

        if (!skill.isActive) {
            val passiveSkill = GameEvent.SystemNotification(
                "${skill.name} is a passive skill - it's always active!"
            )
            flowCollector.emit(passiveSkill)
            eventLog.add(passiveSkill)
            return
        }

        // Execute the skill
        val result = skillCombatService.executeSkill(
            skill = skill,
            user = sheet,
            targetDefense = 10,  // Default target defense
            targetWisdom = 10
        )

        when (result) {
            is SkillExecutionResult.Success -> {
                // Use the skill (spend resources, start cooldown, gain XP)
                val newSheet = sheet.useSkill(skill.id, result.xpGained)
                if (newSheet != null) {
                    gameState = gameState.copy(characterSheet = newSheet)
                }

                val narration = narratorAgent.narrateSkillUse(skill, result, gameState)
                val skillEvent = GameEvent.NarratorText(narration)
                flowCollector.emit(skillEvent)
                eventLog.add(skillEvent)

                // Combat log
                val combatLog = GameEvent.CombatLog(result.narrativeSummary())
                flowCollector.emit(combatLog)
                eventLog.add(combatLog)

                // Check for level up
                val updatedSkill = newSheet?.getSkill(skill.id)
                if (updatedSkill != null && updatedSkill.level > skill.level) {
                    val levelUpNotif = GameEvent.SystemNotification(
                        "★ ${skill.name} leveled up to Level ${updatedSkill.level}! ★"
                    )
                    flowCollector.emit(levelUpNotif)
                    eventLog.add(levelUpNotif)
                }
            }

            is SkillExecutionResult.OnCooldown -> {
                val cdNotif = GameEvent.SystemNotification(
                    "${skill.name} is on cooldown for ${result.turnsRemaining} more turn(s)."
                )
                flowCollector.emit(cdNotif)
                eventLog.add(cdNotif)
            }

            is SkillExecutionResult.InsufficientResources -> {
                val missing = result.getMissingResources()
                val resourceNotif = GameEvent.SystemNotification(
                    "Not enough resources for ${skill.name}. Need: ${missing.joinToString(", ")}"
                )
                flowCollector.emit(resourceNotif)
                eventLog.add(resourceNotif)
            }
        }

        // Process action insight for this input
        processActionInsight(input, flowCollector)
    }

    /**
     * Handle skill evolution.
     */
    private suspend fun handleSkillEvolution(
        skillNameOrId: String?,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val sheet = gameState.characterSheet

        // Find skills that can evolve
        val evolvableSkills = sheet.skills.filter { it.canEvolve() }

        if (evolvableSkills.isEmpty()) {
            val noEvolve = GameEvent.SystemNotification(
                "No skills ready to evolve. Skills can evolve at max level."
            )
            flowCollector.emit(noEvolve)
            eventLog.add(noEvolve)
            return
        }

        // If no specific skill named, show evolution options
        if (skillNameOrId == null) {
            val header = GameEvent.SystemNotification(
                "╔════════════════════════════════════════╗\n" +
                "║       SKILL EVOLUTION                  ║\n" +
                "╚════════════════════════════════════════╝\n" +
                "\nSkills ready to evolve:"
            )
            flowCollector.emit(header)
            eventLog.add(header)

            evolvableSkills.forEach { skill ->
                val options = skillAcquisitionService.getEvolutionOptions(
                    skill = skill,
                    stats = sheet.effectiveStats(),
                    playerLevel = sheet.level,
                    completedQuests = gameState.completedQuests
                )

                val skillInfo = GameEvent.SystemNotification(
                    "\n[${skill.rarity.symbol}] ${skill.name} (Lv. MAX)\n" +
                    "Evolution paths:"
                )
                flowCollector.emit(skillInfo)
                eventLog.add(skillInfo)

                options.forEach { option ->
                    val statusStr = if (option.requirementsMet) "✓ Ready" else "✗ Locked"
                    val reqStr = if (option.unmetRequirements.isNotEmpty()) {
                        "\n      Requires: ${option.unmetRequirements.joinToString(", ") { it.describe() }}"
                    } else ""

                    val pathInfo = GameEvent.SystemNotification(
                        "  → ${option.path.evolvesIntoName} [$statusStr]$reqStr\n" +
                        "    ${option.path.description}"
                    )
                    flowCollector.emit(pathInfo)
                    eventLog.add(pathInfo)
                }
            }

            val helpText = GameEvent.SystemNotification(
                "\nTo evolve: 'evolve skill [skill name] into [evolution name]'"
            )
            flowCollector.emit(helpText)
            eventLog.add(helpText)
            return
        }

        // Find the specific skill to evolve
        val skill = evolvableSkills.find {
            it.id.equals(skillNameOrId, ignoreCase = true) ||
            it.name.equals(skillNameOrId, ignoreCase = true) ||
            it.name.lowercase().contains(skillNameOrId.lowercase())
        }

        if (skill == null) {
            val notFound = GameEvent.SystemNotification(
                "Skill '$skillNameOrId' not found or not ready to evolve."
            )
            flowCollector.emit(notFound)
            eventLog.add(notFound)
            return
        }

        // For now, auto-select the first available evolution path
        val options = skillAcquisitionService.getEvolutionOptions(
            skill = skill,
            stats = sheet.effectiveStats(),
            playerLevel = sheet.level,
            completedQuests = gameState.completedQuests
        )

        val availableOption = options.find { it.requirementsMet }
        if (availableOption == null) {
            val locked = GameEvent.SystemNotification(
                "No evolution paths available for ${skill.name} yet. Check requirements."
            )
            flowCollector.emit(locked)
            eventLog.add(locked)
            return
        }

        // Perform evolution
        val event = skillAcquisitionService.evolveSkill(
            skill = skill,
            evolutionPathId = availableOption.path.evolvesIntoId,
            stats = sheet.effectiveStats(),
            playerLevel = sheet.level,
            completedQuests = gameState.completedQuests
        )

        when (event) {
            is com.rpgenerator.core.skill.SkillAcquisitionEvent.SkillEvolved -> {
                // Update character sheet
                val newSheet = sheet.removeSkill(skill.id).addSkill(event.newSkill)
                gameState = gameState.copy(characterSheet = newSheet)

                val evolveNotif = GameEvent.SystemNotification(
                    "╔════════════════════════════════════════╗\n" +
                    "║       ★ SKILL EVOLVED! ★               ║\n" +
                    "╚════════════════════════════════════════╝\n\n" +
                    "${skill.name} has evolved into ${event.newSkill.name}!\n" +
                    "[${event.newSkill.rarity.displayName}] ${event.newSkill.description}"
                )
                flowCollector.emit(evolveNotif)
                eventLog.add(evolveNotif)
            }

            is com.rpgenerator.core.skill.SkillAcquisitionEvent.EvolutionFailed -> {
                val failNotif = GameEvent.SystemNotification("Evolution failed: ${event.reason}")
                flowCollector.emit(failNotif)
                eventLog.add(failNotif)
            }

            else -> {}
        }
    }

    /**
     * Handle skill fusion.
     */
    private suspend fun handleSkillFusion(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>,
        input: String
    ) {
        val sheet = gameState.characterSheet

        if (sheet.skills.size < 2) {
            val notEnough = GameEvent.SystemNotification(
                "Need at least 2 skills to attempt fusion."
            )
            flowCollector.emit(notEnough)
            eventLog.add(notEnough)
            return
        }

        // Get available fusions
        val availableFusions = skillAcquisitionService.getAvailableFusions(
            ownedSkills = sheet.skills,
            discoveredFusions = sheet.discoveredFusions
        )

        // Show fusion options
        val header = GameEvent.SystemNotification(
            "╔════════════════════════════════════════╗\n" +
            "║       SKILL FUSION                     ║\n" +
            "╚════════════════════════════════════════╝"
        )
        flowCollector.emit(header)
        eventLog.add(header)

        if (availableFusions.isEmpty()) {
            val noFusions = GameEvent.SystemNotification(
                "\nNo fusion recipes available with your current skills.\n" +
                "Learn more skills to unlock fusion possibilities!"
            )
            flowCollector.emit(noFusions)
            eventLog.add(noFusions)

            // Show hints if any
            val hints = skillAcquisitionService.getFusionHints(
                ownedSkillIds = sheet.skills.map { it.id }.toSet(),
                discoveredFusions = sheet.discoveredFusions
            )
            if (hints.isNotEmpty()) {
                val hintHeader = GameEvent.SystemNotification("\n--- Fusion Hints ---")
                flowCollector.emit(hintHeader)
                eventLog.add(hintHeader)

                hints.take(3).forEach { hint ->
                    val hintText = GameEvent.SystemNotification("  • ${hint.hint}")
                    flowCollector.emit(hintText)
                    eventLog.add(hintText)
                }
            }
            return
        }

        availableFusions.forEachIndexed { index, option ->
            val discoveredStr = if (option.isDiscovered) " (Known)" else " (Undiscovered)"
            val levelStr = if (!option.levelRequirementsMet) {
                "\n   ⚠ Level requirements not met"
            } else ""

            val fusionInfo = GameEvent.SystemNotification(
                "\n${index + 1}. ${option.recipe.name}$discoveredStr\n" +
                "   Combines: ${option.recipe.inputSkillIds.joinToString(" + ")}\n" +
                "   Creates: ${option.recipe.resultSkillName} [${option.recipe.resultRarity.displayName}]$levelStr"
            )
            flowCollector.emit(fusionInfo)
            eventLog.add(fusionInfo)
        }

        val helpText = GameEvent.SystemNotification(
            "\nTo fuse, level up the required skills and try the combination!"
        )
        flowCollector.emit(helpText)
        eventLog.add(helpText)
    }

    /**
     * Display detailed character status.
     */
    private suspend fun handleStatusMenu(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val sheet = gameState.characterSheet
        val stats = sheet.effectiveStats()

        val statusText = """
╔═══════════════════════════════════════════════════════════════╗
║                    CHARACTER STATUS                           ║
╠═══════════════════════════════════════════════════════════════╣
║  Name: ${gameState.playerName.padEnd(20)} Level: ${sheet.level.toString().padEnd(10)} ║
║  Class: ${sheet.playerClass.displayName.padEnd(18)} Grade: ${sheet.currentGrade.name.padEnd(11)} ║
╠═══════════════════════════════════════════════════════════════╣
║  HP:     ${sheet.resources.currentHP}/${sheet.resources.maxHP}${" ".repeat(50 - "${sheet.resources.currentHP}/${sheet.resources.maxHP}".length)}║
║  MP:     ${sheet.resources.currentMana}/${sheet.resources.maxMana}${" ".repeat(50 - "${sheet.resources.currentMana}/${sheet.resources.maxMana}".length)}║
║  Energy: ${sheet.resources.currentEnergy}/${sheet.resources.maxEnergy}${" ".repeat(50 - "${sheet.resources.currentEnergy}/${sheet.resources.maxEnergy}".length)}║
╠═══════════════════════════════════════════════════════════════╣
║  STR: ${stats.strength.toString().padEnd(5)} DEX: ${stats.dexterity.toString().padEnd(5)} CON: ${stats.constitution.toString().padEnd(18)}║
║  INT: ${stats.intelligence.toString().padEnd(5)} WIS: ${stats.wisdom.toString().padEnd(5)} CHA: ${stats.charisma.toString().padEnd(18)}║
║  DEF: ${stats.defense.toString().padEnd(54)}║
╠═══════════════════════════════════════════════════════════════╣
║  XP: ${sheet.xp}/${sheet.xpToNextLevel()} to next level${" ".repeat(40 - "${sheet.xp}/${sheet.xpToNextLevel()} to next level".length)}║
║  Skills: ${sheet.skills.size}   Unspent Points: ${sheet.unspentStatPoints}${" ".repeat(30 - "${sheet.skills.size}   Unspent Points: ${sheet.unspentStatPoints}".length)}║
╚═══════════════════════════════════════════════════════════════╝
        """.trimIndent()

        val statusEvent = GameEvent.SystemNotification(statusText)
        flowCollector.emit(statusEvent)
        eventLog.add(statusEvent)

        // Mark tutorial objective if applicable
        val tutorialQuest = gameState.activeQuests["quest_survive_tutorial"]
        if (tutorialQuest != null) {
            val statsObj = tutorialQuest.objectives.find { it.id == "tutorial_obj_stats" }
            if (statsObj != null && !statsObj.isComplete()) {
                gameState = gameState.updateQuestObjective(tutorialQuest.id, "tutorial_obj_stats", 1)
                val progressNotif = GameEvent.SystemNotification(
                    "Quest Progress: Survive the Tutorial - Check your status (Complete)"
                )
                flowCollector.emit(progressNotif)
                eventLog.add(progressNotif)
            }
        }
    }

    /**
     * Display inventory.
     */
    private suspend fun handleInventoryMenu(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val inventory = gameState.characterSheet.inventory
        val equipment = gameState.characterSheet.equipment

        val header = GameEvent.SystemNotification(
            "╔════════════════════════════════════════╗\n" +
            "║           INVENTORY                    ║\n" +
            "╚════════════════════════════════════════╝"
        )
        flowCollector.emit(header)
        eventLog.add(header)

        // Show equipment
        val equipmentText = GameEvent.SystemNotification(
            "\n--- Equipped ---\n" +
            "  Weapon:    ${equipment.weapon?.name ?: "(none)"}\n" +
            "  Armor:     ${equipment.armor?.name ?: "(none)"}\n" +
            "  Accessory: ${equipment.accessory?.name ?: "(none)"}"
        )
        flowCollector.emit(equipmentText)
        eventLog.add(equipmentText)

        // Show inventory items
        if (inventory.items.isEmpty()) {
            val emptyText = GameEvent.SystemNotification("\n--- Items ---\n  (empty)")
            flowCollector.emit(emptyText)
            eventLog.add(emptyText)
        } else {
            val itemsHeader = GameEvent.SystemNotification("\n--- Items (${inventory.items.size}/${inventory.maxSlots}) ---")
            flowCollector.emit(itemsHeader)
            eventLog.add(itemsHeader)

            inventory.items.values.forEach { item ->
                val qtyStr = if (item.quantity > 1) " x${item.quantity}" else ""
                val itemText = GameEvent.SystemNotification(
                    "  [${item.type.name}] ${item.name}$qtyStr"
                )
                flowCollector.emit(itemText)
                eventLog.add(itemText)
            }
        }
    }

    /**
     * Process player input for action insight skill learning.
     */
    private suspend fun processActionInsight(
        input: String,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        val context = ActionContext(
            equippedWeaponType = gameState.characterSheet.equipment.weapon?.name,
            inCombat = false  // Could track actual combat state
        )

        val (updatedSheet, newSkills) = gameState.characterSheet.processActionInsight(input, context)
        gameState = gameState.copy(characterSheet = updatedSheet)

        // Notify about any new skills learned
        newSkills.forEach { skill ->
            val learnedNotif = GameEvent.SystemNotification(
                "╔════════════════════════════════════════╗\n" +
                "║      ★ NEW SKILL LEARNED! ★            ║\n" +
                "╚════════════════════════════════════════╝\n\n" +
                "Through repeated practice, you've gained insight into:\n\n" +
                "[${skill.rarity.displayName}] ${skill.name}\n" +
                "${skill.description}"
            )
            flowCollector.emit(learnedNotif)
            eventLog.add(learnedNotif)
        }
    }

    /**
     * Handle player death based on system type.
     */
    private suspend fun handleDeath(flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>, cause: String) {
        // Emit death narration
        val deathNarration = narratorAgent.narrateDeath(gameState, cause)
        val deathEvent = GameEvent.NarratorText(deathNarration)
        flowCollector.emit(deathEvent)
        eventLog.add(deathEvent)

        // Increment death count
        val newDeathCount = gameState.deathCount + 1
        gameState = gameState.copy(deathCount = newDeathCount)

        // Handle death based on system type
        when (gameState.systemType) {
            com.rpgenerator.core.api.SystemType.DEATH_LOOP -> {
                // Death makes you stronger - respawn with bonuses
                val deathBonus = newDeathCount * 2 // +2 to all stats per death
                val newStats = gameState.characterSheet.baseStats.copy(
                    strength = gameState.characterSheet.baseStats.strength + deathBonus,
                    dexterity = gameState.characterSheet.baseStats.dexterity + deathBonus,
                    constitution = gameState.characterSheet.baseStats.constitution + deathBonus,
                    intelligence = gameState.characterSheet.baseStats.intelligence + deathBonus,
                    wisdom = gameState.characterSheet.baseStats.wisdom + deathBonus,
                    charisma = gameState.characterSheet.baseStats.charisma + deathBonus
                )

                val newSheet = gameState.characterSheet.copy(baseStats = newStats)
                val restoredSheet = newSheet.copy(resources = newSheet.resources.restore())

                gameState = gameState.copy(characterSheet = restoredSheet)

                // Emit respawn narration
                val respawnNarration = narratorAgent.narrateRespawn(gameState)
                val respawnEvent = GameEvent.NarratorText(respawnNarration)
                flowCollector.emit(respawnEvent)
                eventLog.add(respawnEvent)

                val bonusNotif = GameEvent.SystemNotification(
                    "Death has strengthened you. All stats increased by $deathBonus!"
                )
                flowCollector.emit(bonusNotif)
                eventLog.add(bonusNotif)
            }

            com.rpgenerator.core.api.SystemType.DUNGEON_DELVE -> {
                // Permadeath - game over
                val gameOverEvent = GameEvent.SystemNotification(
                    "GAME OVER - Permadeath. Your adventure ends here. Total deaths: $newDeathCount"
                )
                flowCollector.emit(gameOverEvent)
                eventLog.add(gameOverEvent)
                // Character remains dead - player must start a new game
            }

            else -> {
                // Standard respawn - restore HP, small level penalty
                val restoredSheet = gameState.characterSheet.copy(
                    resources = gameState.characterSheet.resources.restore(),
                    xp = (gameState.playerXP * 0.9).toLong() // 10% XP penalty
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

    /**
     * Initialize dynamically generated NPCs for the current location (if in tutorial).
     * Silent version - only updates game state, does NOT emit any events.
     * Events are emitted separately via emitInitialQuestEvents() after the opening narration.
     */
    /**
     * Extract NPCs mentioned in narration text and register them in game state.
     * This ensures narratively-introduced NPCs appear in game_get_npcs_here and can be interacted with.
     */
    private suspend fun registerNPCsFromNarration(narrationText: String) {
        try {
            val newNPCs = gameMasterAgent.extractNPCsFromNarration(narrationText, gameState)
            for (npc in newNPCs) {
                gameState = gameState.addNPC(npc)
                npcManager.registerGeneratedNPC(npc)
            }
        } catch (e: Exception) {
            // NPC extraction is best-effort — don't break the game if it fails
        }
    }

    private suspend fun initializeDynamicNPCsSilent() {
        // If we have a seed, create NarratorContext from it (no AI call needed)
        // Otherwise fall back to AI-generated story planning
        if (!storyPlanningStarted) {
            storyPlanningStarted = true

            if (worldSeed != null) {
                // Create context directly from the seed - no AI call needed
                storyFoundation = createFoundationFromSeed(worldSeed)
            } else {
                // Fall back to AI-generated story planning for legacy games
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
                    } catch (e: Exception) {
                        // Story planning failed - game continues without it
                        println("Story planning failed: ${e.message}")
                    }
                }
            }
        }

        // Generate tutorial guide dynamically if in tutorial zone
        if (gameState.currentLocation.biome == Biome.TUTORIAL_ZONE) {
            // Generate unique tutorial guide for this playthrough
            val tutorialGuide = npcArchetypeGenerator.generateTutorialGuide(
                playerName = gameState.playerName,
                systemType = gameState.systemType,
                playerLevel = gameState.playerLevel,
                seed = gameState.gameId.hashCode().toLong()
            )

            // Add to game state and NPC manager (no events emitted)
            gameState = gameState.addNPC(tutorialGuide)
            npcManager.registerGeneratedNPC(tutorialGuide)

            // Add tutorial quest if not already present (no events emitted yet)
            if (!gameState.activeQuests.containsKey("quest_survive_tutorial")) {
                val tutorialQuest = createTutorialQuest(tutorialGuide.name)
                gameState = gameState.addQuest(tutorialQuest)
            }
        }
    }

    /**
     * Get the narrator context from the story foundation.
     * Returns a default context if story planning hasn't been initialized.
     */
    internal fun getNarratorContext(): NarratorContext? {
        return storyFoundation?.narratorContext
    }

    /**
     * Get the full story foundation for debugging/inspection.
     */
    internal fun getStoryFoundation(): StoryFoundation? {
        return storyFoundation
    }

    /**
     * Emit initial quest and NPC events AFTER the opening narration has been displayed.
     * This ensures the narrative flow is: Opening narration -> Quest notification -> NPC notice
     */
    private suspend fun emitInitialQuestEvents(flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>) {
        // Emit quest notification for tutorial quest
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

        // Emit notification about tutorial guide's presence (if in tutorial)
        if (gameState.currentLocation.biome == Biome.TUTORIAL_ZONE) {
            val tutorialGuide = gameState.getNPCsAtCurrentLocation().firstOrNull()
            if (tutorialGuide != null) {
                val guideNotice = GameEvent.SystemNotification("${tutorialGuide.name} materializes before you.")
                flowCollector.emit(guideNotice)
                eventLog.add(guideNotice)
            }
        }
    }

    /**
     * Initialize dynamically generated NPCs for the current location (if in tutorial)
     * @deprecated Use initializeDynamicNPCsSilent() + emitInitialQuestEvents() instead
     */
    private suspend fun initializeDynamicNPCs(flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>) {
        // Generate tutorial guide dynamically if in tutorial zone
        if (gameState.currentLocation.biome == Biome.TUTORIAL_ZONE) {
            // Generate unique tutorial guide for this playthrough
            val tutorialGuide = npcArchetypeGenerator.generateTutorialGuide(
                playerName = gameState.playerName,
                systemType = gameState.systemType,
                playerLevel = gameState.playerLevel,
                seed = gameState.gameId.hashCode().toLong()
            )

            // Add to game state and NPC manager
            gameState = gameState.addNPC(tutorialGuide)
            npcManager.registerGeneratedNPC(tutorialGuide)

            // Add tutorial quest if not already present
            if (!gameState.activeQuests.containsKey("quest_survive_tutorial")) {
                val tutorialQuest = createTutorialQuest(tutorialGuide.name)
                gameState = gameState.addQuest(tutorialQuest)

                val questEvent = GameEvent.QuestUpdate(
                    questId = tutorialQuest.id,
                    questName = tutorialQuest.name,
                    status = QuestStatus.NEW
                )
                flowCollector.emit(questEvent)
                eventLog.add(questEvent)
            }

            // Trigger first story beat
            val firstBeat = MainStoryArc.getStoryBeatForLevel(1)
            if (firstBeat != null) {
                val beatEvent = GameEvent.NarratorText(firstBeat.narration)
                flowCollector.emit(beatEvent)
                eventLog.add(beatEvent)

                // Emit notification about tutorial guide's presence
                val guideNotice = GameEvent.SystemNotification("${tutorialGuide.name} materializes before you.")
                flowCollector.emit(guideNotice)
                eventLog.add(guideNotice)
            }
        }
    }

    /**
     * Create the tutorial quest with proper objectives.
     * Class selection is the PRIMARY focus - everything else is secondary.
     *
     * Flow:
     * 1. Choose your class (the foundational decision that shapes everything)
     * 2. Review your status and understand your abilities
     * 3. Test your new powers (optional combat or skill use)
     */
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

    /**
     * Check if NPCs want to take autonomous actions
     */
    private suspend fun checkAutonomousNPCActions(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>
    ) {
        // Get all NPCs at the current location
        val npcsAtLocation = gameState.getNPCsAtCurrentLocation()

        if (npcsAtLocation.isEmpty()) return

        val recentEvents = eventLog.takeLast(5).map {
            when (it) {
                is GameEvent.NarratorText -> it.text
                is GameEvent.SystemNotification -> it.text
                is GameEvent.NPCDialogue -> "${it.npcName}: ${it.text}"
                else -> it.toString()
            }
        }

        // Check each NPC to see if they want to act autonomously
        // Only check one NPC per turn to avoid overwhelming the player
        val npc = npcsAtLocation.randomOrNull() ?: return

        val action = autonomousNPCAgent.shouldNPCActAutonomously(
            npc = npc,
            state = gameState,
            recentEvents = recentEvents,
            timeElapsed = 30 // Could track actual time
        )

        if (action != null) {
            when (action.actionType) {
                com.rpgenerator.core.agents.NPCActionType.APPROACH_PLAYER -> {
                    // NPC initiates conversation
                    val initiatedDialogue = action.dialogue
                        ?: autonomousNPCAgent.generateInitiatedDialogue(npc, gameState, action.reason)

                    val approachEvent = GameEvent.NPCDialogue(
                        npcId = npc.id,
                        npcName = npc.name,
                        text = initiatedDialogue
                    )
                    flowCollector.emit(approachEvent)
                    eventLog.add(approachEvent)
                }
                com.rpgenerator.core.agents.NPCActionType.GIVE_WARNING -> {
                    val warning = action.dialogue ?: "${npc.name} looks concerned about recent events."
                    val warningEvent = GameEvent.NPCDialogue(
                        npcId = npc.id,
                        npcName = npc.name,
                        text = warning
                    )
                    flowCollector.emit(warningEvent)
                    eventLog.add(warningEvent)
                }
                com.rpgenerator.core.agents.NPCActionType.REACT_TO_EVENT -> {
                    val reaction = action.dialogue ?: "${npc.name} reacts to recent events."
                    val reactionEvent = GameEvent.NPCDialogue(
                        npcId = npc.id,
                        npcName = npc.name,
                        text = reaction
                    )
                    flowCollector.emit(reactionEvent)
                    eventLog.add(reactionEvent)
                }
                else -> {
                    // Other action types (move, offer quest) can be implemented later
                }
            }
        }
    }

    /**
     * Check if Game Master wants to spawn an NPC or trigger an encounter
     */
    private suspend fun checkGameMasterEvents(
        flowCollector: kotlinx.coroutines.flow.FlowCollector<GameEvent>,
        playerInput: String
    ) {
        val recentEvents = eventLog.takeLast(5).map {
            when (it) {
                is GameEvent.NarratorText -> it.text
                is GameEvent.SystemNotification -> it.text
                else -> it.toString()
            }
        }

        // Check if a new NPC should appear
        val npcDecision = gameMasterAgent.shouldCreateNPC(playerInput, gameState, recentEvents)

        if (npcDecision.shouldCreate && npcDecision.template != null) {
            val newNPC = gameMasterAgent.createNPC(npcDecision.template)
            gameState = gameState.addNPC(newNPC)
            npcManager.registerGeneratedNPC(newNPC)

            val npcAppearance = GameEvent.SystemNotification("${newNPC.name} appears nearby.")
            flowCollector.emit(npcAppearance)
            eventLog.add(npcAppearance)
        }

        // Check if a random encounter should trigger
        val encounterDecision = gameMasterAgent.shouldTriggerEncounter(gameState, recentEvents)

        if (encounterDecision.shouldTrigger) {
            val encounterNotice = GameEvent.SystemNotification(encounterDecision.description)
            flowCollector.emit(encounterNotice)
            eventLog.add(encounterNotice)
        }
    }

    fun getState(): GameState = gameState

    fun getEventLog(): List<GameEvent> = eventLog.toList()

    /**
     * Create a StoryFoundation from a WorldSeed without AI generation.
     * This allows seeds to provide all narrative context directly.
     */
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

        val plotGraph = com.rpgenerator.core.domain.PlotGraph(
            gameId = gameState.gameId,
            nodes = emptyMap(),
            edges = emptyMap()
        )

        return StoryFoundation(
            systemDefinition = systemDefinition,
            plotGraph = plotGraph,
            plotThreads = emptyList(), // Seeds use reactive storytelling, not pre-planned threads
            initialForeshadowing = emptyList(),
            narratorContext = narratorContext
        )
    }
}
