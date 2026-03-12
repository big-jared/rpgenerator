package com.rpgenerator.core

import com.rpgenerator.core.api.*
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.orchestration.GameOrchestrator
import com.rpgenerator.core.persistence.GameDatabase
import com.rpgenerator.core.persistence.GameRepository
import com.rpgenerator.core.persistence.PlotGraphRepository
import com.rpgenerator.core.story.StoryFoundation
import com.rpgenerator.core.agents.GMPromptBuilder
import com.rpgenerator.core.agents.ClassGeneratorAgent
import com.rpgenerator.core.agents.ItemGeneratorAgent
import com.rpgenerator.core.agents.LocationGeneratorAgent
import com.rpgenerator.core.agents.MonsterGeneratorAgent
import com.rpgenerator.core.agents.SkillGeneratorAgent
import com.rpgenerator.core.generation.NPCArchetypeGenerator
import com.rpgenerator.core.tools.LoreQueryHandler
import com.rpgenerator.core.tools.ToolCaller
import com.rpgenerator.core.tools.ToolCallLogEntry
import com.rpgenerator.core.tools.UnifiedToolContractImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import com.rpgenerator.core.util.currentTimeMillis

/**
 * Implementation of the Game interface.
 * Manages an active game session.
 */
internal class GameImpl(
    gameId: String,
    private val llm: LLMInterface,
    private val repository: GameRepository,
    private val plotRepository: PlotGraphRepository,
    initialState: GameState,
    private val resumeEvents: List<GameEvent> = emptyList()
) : Game(gameId, llm) {

    private val monsterGenerator by lazy { MonsterGeneratorAgent(llm) }
    private val npcGenerator by lazy { NPCArchetypeGenerator(llm) }
    private val locationGenerator by lazy { LocationGeneratorAgent(llm) }
    private val itemGenerator by lazy { ItemGeneratorAgent(llm) }
    private val classGenerator by lazy { ClassGeneratorAgent(llm) }
    private val skillGenerator by lazy { SkillGeneratorAgent(llm) }

    private val toolContract = UnifiedToolContractImpl(
        monsterGenerator = monsterGenerator,
        npcGenerator = npcGenerator,
        locationGenerator = locationGenerator,
        itemGenerator = itemGenerator,
        classGenerator = classGenerator,
        skillGenerator = skillGenerator
    )
    private val orchestrator = GameOrchestrator(llm, initialState, toolContract, resumeEvents)
    private var sessionStartTime = currentTimeMillis()
    private var sessionPlaytime = 0L
    private var plotGraphSaved = false

    override suspend fun processInput(input: String): Flow<GameEvent> {
        return orchestrator.processInput(input).onEach { event ->
            // Log each event to the database
            repository.logEvent(id, event)

            // Save plot graph after story planning completes (first input)
            if (!plotGraphSaved) {
                orchestrator.getStoryFoundation()?.let { foundation ->
                    plotRepository.savePlotGraph(foundation.plotGraph)
                    plotGraphSaved = true
                }
            }
        }
    }

    override fun getState(): GameStateSnapshot {
        val state = orchestrator.getState()
        val recentEvents = emptyList<GameEvent>() // We'll load these on demand if needed

        return GameStateSnapshot(
            playerStats = PlayerStats(
                name = state.playerName,
                level = state.playerLevel,
                experience = state.playerXP,
                experienceToNextLevel = state.characterSheet.xpToNextLevel(),
                stats = mapOf(
                    "strength" to state.characterSheet.effectiveStats().strength,
                    "dexterity" to state.characterSheet.effectiveStats().dexterity,
                    "constitution" to state.characterSheet.effectiveStats().constitution,
                    "intelligence" to state.characterSheet.effectiveStats().intelligence,
                    "wisdom" to state.characterSheet.effectiveStats().wisdom,
                    "charisma" to state.characterSheet.effectiveStats().charisma,
                    "defense" to state.characterSheet.effectiveStats().defense
                ),
                health = state.characterSheet.resources.currentHP,
                maxHealth = state.characterSheet.resources.maxHP,
                energy = state.characterSheet.resources.currentEnergy,
                maxEnergy = state.characterSheet.resources.maxEnergy,
                backstory = state.backstory,
                playerClass = if (state.characterSheet.playerClass == com.rpgenerator.core.domain.PlayerClass.NONE) "" else state.characterSheet.playerClass.displayName,
                playerProfession = if (state.characterSheet.profession == com.rpgenerator.core.domain.Profession.NONE) "" else state.characterSheet.profession.displayName
            ),
            location = state.currentLocation.name,
            currentScene = state.currentLocation.description,
            inventory = state.characterSheet.inventory.items.values.map { item ->
                Item(
                    id = item.id,
                    name = item.name,
                    description = item.description,
                    quantity = item.quantity,
                    rarity = item.rarity
                )
            },
            activeQuests = state.activeQuests.values.map { quest ->
                Quest(
                    id = quest.id,
                    name = quest.name,
                    description = quest.description,
                    status = if (quest.isComplete()) QuestStatus.COMPLETED else QuestStatus.IN_PROGRESS,
                    objectives = quest.objectives.map { obj ->
                        QuestObjective(
                            description = obj.description,
                            completed = obj.isComplete()
                        )
                    }
                )
            },
            npcsAtLocation = state.getNPCsAtCurrentLocation().map { npc ->
                NPCInfo(
                    id = npc.id,
                    name = npc.name,
                    archetype = npc.archetype.name.replace("_", " "),
                    disposition = npc.personality.traits.firstOrNull() ?: "neutral",
                    description = npc.lore.ifEmpty { npc.personality.motivations.joinToString(". ") }
                )
            },
            skills = state.characterSheet.skills.map { skill ->
                SkillInfo(
                    id = skill.id,
                    name = skill.name,
                    level = skill.level,
                    isActive = skill.isActive
                )
            },
            combat = state.combatState?.let { combat ->
                CombatInfo(
                    enemyName = combat.enemy.name,
                    enemyHP = combat.enemy.currentHP,
                    enemyMaxHP = combat.enemy.maxHP,
                    enemyCondition = combat.enemy.condition,
                    portraitResource = combat.enemy.portraitResource,
                    roundNumber = combat.roundNumber,
                    enemyDanger = combat.enemy.danger,
                    lootTier = combat.enemy.lootTier,
                    description = combat.enemy.description,
                    immunities = combat.enemy.immunities.map { it.name },
                    vulnerabilities = combat.enemy.vulnerabilities.map { it.name },
                    resistances = combat.enemy.resistances.map { it.name }
                )
            },
            recentEvents = recentEvents
        )
    }

    override suspend fun save() {
        // Calculate total playtime for this session
        val currentTime = currentTimeMillis()
        val sessionDuration = (currentTime - sessionStartTime) / 1000
        sessionPlaytime += sessionDuration
        sessionStartTime = currentTime

        // Save to database
        repository.saveGame(
            gameId = id,
            state = orchestrator.getState(),
            playtime = sessionPlaytime
        )
    }

    /**
     * Update the session playtime tracker.
     * Should be called periodically during gameplay.
     */
    internal fun updatePlaytime() {
        val currentTime = currentTimeMillis()
        val sessionDuration = (currentTime - sessionStartTime) / 1000
        sessionPlaytime += sessionDuration
        sessionStartTime = currentTime
    }

    /**
     * Set the initial playtime (used when resuming a game).
     */
    internal fun setInitialPlaytime(playtime: Long) {
        sessionPlaytime = playtime
    }

    /**
     * Get the current game state for debugging.
     * Internal use only.
     */
    internal fun getCurrentState(): GameState {
        return orchestrator.getState()
    }

    override fun getEventLog(): List<GameEvent> {
        return orchestrator.getEventLog()
    }

    override fun getToolCallLog(): List<Map<String, Any?>> {
        return toolContract.toolCallLog.map { entry ->
            mapOf(
                "seq" to entry.sequenceNumber,
                "timestamp" to entry.timestamp,
                "tool" to entry.toolName,
                "caller" to entry.caller.name,
                "args" to entry.args,
                "success" to entry.success,
                "result" to entry.resultSummary,
                "error" to entry.error,
                "elapsedMs" to entry.elapsedMs,
                "stateChanged" to entry.stateChanged,
                "events" to entry.eventsEmitted,
                "location" to entry.location,
                "playerLevel" to entry.playerLevel,
                "turn" to entry.turnNumber
            )
        }
    }

    override fun getDebugState(): Map<String, String> {
        val state = orchestrator.getState()
        val foundation = orchestrator.getStoryFoundation()
        val result = mutableMapOf<String, String>()

        result["playerName"] = state.playerName
        result["playerLevel"] = state.playerLevel.toString()
        result["playerClass"] = state.characterSheet.playerClass.name
        result["currentLocation"] = state.currentLocation.name
        result["locationDescription"] = state.currentLocation.description
        result["activeQuestCount"] = state.activeQuests.size.toString()
        result["npcCount"] = state.getNPCsAtCurrentLocation().size.toString()
        result["eventLogSize"] = orchestrator.getEventLog().size.toString()
        result["systemType"] = state.systemType.name
        result["seedId"] = state.seedId ?: "none"
        result["backstory"] = state.backstory ?: "none"

        if (foundation != null) {
            result["storyFoundation.systemName"] = foundation.systemDefinition.systemName
            result["storyFoundation.centralMystery"] = foundation.systemDefinition.centralMystery
            result["storyFoundation.primaryThreat"] = foundation.systemDefinition.primaryThreat
            result["storyFoundation.plotThreadCount"] = foundation.plotThreads.size.toString()
            result["storyFoundation.foreshadowingCount"] = foundation.initialForeshadowing.size.toString()

            // Narrator context
            result["narratorContext.systemName"] = foundation.narratorContext.systemName
            result["narratorContext.systemPersonality"] = foundation.narratorContext.systemPersonality
            result["narratorContext.thematicCore"] = foundation.narratorContext.thematicCore
            result["narratorContext.activeThreads"] = foundation.narratorContext.activeThreads.joinToString("; ")
            result["narratorContext.upcomingBeats"] = foundation.narratorContext.upcomingBeats.joinToString("; ")
            result["narratorContext.currentForeshadowing"] = foundation.narratorContext.currentForeshadowing.joinToString("; ")
        }

        return result
    }

    internal fun getStoryFoundation(): StoryFoundation? {
        return orchestrator.getStoryFoundation()
    }

    // ── Tool execution API ─────────────────────────────────────────

    override suspend fun executeTool(name: String, args: Map<String, Any?>): ToolCallResult {
        toolContract.currentCaller = ToolCaller.EXTERNAL_MCP
        val state = orchestrator.getState()
        val outcome = toolContract.executeTool(name, args, state)

        // Apply state mutations back to the orchestrator
        if (outcome.newState != null) {
            orchestrator.gameState = outcome.newState!!
        }

        return ToolCallResult(
            success = outcome.success,
            data = outcome.data,
            events = outcome.events,
            error = outcome.error
        )
    }

    override fun getToolDefinitions(): List<ToolDefinition> {
        return toolContract.getToolDefinitions().map { def ->
            ToolDefinition(
                name = def.name,
                description = def.description,
                parameters = def.parameters.map { param ->
                    ToolParameterDef(
                        name = param.name,
                        type = param.type,
                        description = param.description,
                        required = param.required
                    )
                }
            )
        }
    }

    override fun getSystemPrompt(): String {
        return GMPromptBuilder.buildCompanionPrompt(orchestrator.getState(), resumeEvents)
    }

    override fun getCompanionVoice(): String {
        return when (orchestrator.getState().seedId) {
            "integration" -> "Charon"  // Deep, gruff — fits Hank the man-fairy
            "tabletop" -> "Puck"       // Bright, energetic — fits Pip the ink sprite
            "crawler" -> "Fenrir"      // Hushed, tense — fits Glitch the drone
            "quiet_life" -> "Kore"     // Warm, gentle — fits Bramble the forest spirit
            else -> "Kore"
        }
    }

    override fun getNpcDetails(npcId: String): NPCDetails? {
        val state = orchestrator.getState()
        val npc = state.findNPC(npcId) ?: return null
        val relationship = npc.getRelationship(id)

        return NPCDetails(
            id = npc.id,
            name = npc.name,
            archetype = npc.archetype.name.replace("_", " "),
            description = npc.lore.ifEmpty { npc.personality.motivations.joinToString(". ") },
            lore = npc.lore,
            traits = npc.personality.traits,
            speechPattern = npc.personality.speechPattern,
            motivations = npc.personality.motivations,
            relationshipStatus = relationship.getStatus().name.replace("_", " "),
            affinity = relationship.affinity,
            hasShop = npc.shop != null,
            shopName = npc.shop?.name,
            shopItems = npc.shop?.inventory?.map { item ->
                ShopItemInfo(
                    id = item.id,
                    name = item.name,
                    description = item.description,
                    price = item.price,
                    stock = item.stock,
                    requiredLevel = item.requiredLevel
                )
            } ?: emptyList(),
            questIds = npc.questIds,
            recentConversations = npc.getRecentConversations(5).map { entry ->
                ConversationInfo(
                    playerInput = entry.playerInput,
                    npcResponse = entry.npcResponse
                )
            }
        )
    }
}
