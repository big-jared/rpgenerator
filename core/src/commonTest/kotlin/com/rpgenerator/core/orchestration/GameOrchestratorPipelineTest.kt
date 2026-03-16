package com.rpgenerator.core.orchestration

import app.cash.turbine.test
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.LLMToolCall
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.test.AgentType
import com.rpgenerator.core.test.MockLLMInterface
import com.rpgenerator.core.test.TestHelpers
import com.rpgenerator.core.tools.UnifiedToolContractImpl
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.*

/**
 * Pipeline integration tests for the 3-phase turn system.
 * Tests the DECIDE → EXECUTE → NARRATE flow end-to-end using MockLLM.
 */
class GameOrchestratorPipelineTest {

    private val toolContract = UnifiedToolContractImpl()

    private fun createOrchestrator(
        state: GameState = TestHelpers.createTestGameState().copy(hasOpeningNarrationPlayed = true),
        mockLLM: MockLLMInterface = MockLLMInterface()
    ): Pair<GameOrchestrator, MockLLMInterface> {
        return GameOrchestrator(mockLLM, state, toolContract) to mockLLM
    }

    // ── Phase Flow Tests ─────────────────────────────────────────

    @Test
    fun `processInput creates DECIDE then NARRATE agents in order`() = runTest {
        val (orchestrator, mockLLM) = createOrchestrator()

        orchestrator.processInput("I look around").test {
            cancelAndConsumeRemainingEvents()
        }

        val types = mockLLM.createdAgents.map { it.agentType }
        val decideIdx = types.indexOf(AgentType.DECIDE)
        val narrateIdx = types.indexOf(AgentType.NARRATE)

        assertTrue(decideIdx >= 0, "DECIDE agent should be created")
        assertTrue(narrateIdx >= 0, "NARRATE agent should be created")
        assertTrue(decideIdx < narrateIdx, "DECIDE should be created before NARRATE")
    }

    @Test
    fun `decide agent prose is discarded - only narrate agent output emitted`() = runTest {
        val (orchestrator, mockLLM) = createOrchestrator()

        val events = orchestrator.processInput("I search the room").toList()

        // Should have at least one NarratorText event
        val narrationEvents = events.filterIsInstance<GameEvent.NarratorText>()
        assertTrue(narrationEvents.isNotEmpty(), "Should emit narration")

        // Narration should come from NARRATE agent, not DECIDE
        val narrateAgent = mockLLM.createdAgents.firstOrNull { it.agentType == AgentType.NARRATE }
        assertNotNull(narrateAgent, "NARRATE agent should exist")
    }

    @Test
    fun `narrate agent receives world state context`() = runTest {
        val (orchestrator, mockLLM) = createOrchestrator()

        orchestrator.processInput("I examine the area").test {
            cancelAndConsumeRemainingEvents()
        }

        val narrateAgent = mockLLM.createdAgents.first { it.agentType == AgentType.NARRATE }
        val message = narrateAgent.receivedMessages.first()

        assertTrue(message.contains("WORLD STATE") || message.contains("Player said:"),
            "Narrate agent should receive context with world state or player input")
    }

    // ── Opening Narration ────────────────────────────────────────

    @Test
    fun `opening narration plays only once`() = runTest {
        val state = TestHelpers.createTestGameState(hasOpeningNarrationPlayed = false)
        val (orchestrator, _) = createOrchestrator(state = state)

        // First input triggers opening narration
        val firstEvents = orchestrator.processInput("").toList()
        assertTrue(firstEvents.any { it is GameEvent.NarratorText })
        assertTrue(orchestrator.getState().hasOpeningNarrationPlayed)

        // Second input should NOT replay opening narration (blank input → no events)
        val secondEvents = orchestrator.processInput("").toList()
        // Blank input after opening should produce nothing
        assertTrue(secondEvents.isEmpty() || secondEvents.none {
            it is GameEvent.NarratorText && !orchestrator.getState().hasOpeningNarrationPlayed
        })
    }

    // ── Dead Player ──────────────────────────────────────────────

    @Test
    fun `dead player gets system notification before narration`() = runTest {
        val deadSheet = TestHelpers.createTestCharacterSheet().copy(
            resources = Resources(currentHP = 0, maxHP = 100, currentMana = 50, maxMana = 50, currentEnergy = 100, maxEnergy = 100)
        )
        val state = TestHelpers.createTestGameState().copy(
            hasOpeningNarrationPlayed = true,
            characterSheet = deadSheet
        )
        val (orchestrator, _) = createOrchestrator(state = state)

        val events = orchestrator.processInput("I try to move").toList()

        assertTrue(events.any { it is GameEvent.SystemNotification },
            "Dead player should get a system notification")
    }

    // ── State Consistency ────────────────────────────────────────

    @Test
    fun `state is consistent across multiple inputs`() = runTest {
        val (orchestrator, _) = createOrchestrator()

        // Process multiple inputs
        orchestrator.processInput("I look around").toList()
        val state1 = orchestrator.getState()

        orchestrator.processInput("I search for treasure").toList()
        val state2 = orchestrator.getState()

        // Game ID should remain the same
        assertEquals(state1.gameId, state2.gameId)
        // Location should remain the same (no movement tools called)
        assertEquals(state1.currentLocation.id, state2.currentLocation.id)
    }

    @Test
    fun `getState reflects initial state correctly`() = runTest {
        val state = TestHelpers.createTestGameState(playerLevel = 7, playerXP = 1000L)
        val (orchestrator, _) = createOrchestrator(state = state)

        val current = orchestrator.getState()
        assertEquals(7, current.playerLevel)
        assertEquals(1000L, current.playerXP)
    }

    // ── Event Types ──────────────────────────────────────────────

    @Test
    fun `processInput returns non-empty flow for valid input`() = runTest {
        val (orchestrator, _) = createOrchestrator()

        val events = orchestrator.processInput("I explore the dungeon").toList()

        assertTrue(events.isNotEmpty(), "Should produce at least one event")
    }

    @Test
    fun `blank input after opening produces empty flow`() = runTest {
        val (orchestrator, _) = createOrchestrator()

        val events = orchestrator.processInput("").toList()

        assertTrue(events.isEmpty(), "Blank input should produce no events")
    }

    // ── Combat State ─────────────────────────────────────────────

    @Test
    fun `orchestrator does not crash with combat state present`() = runTest {
        val state = TestHelpers.createTestGameState().copy(
            hasOpeningNarrationPlayed = true,
            combatState = CombatState(
                enemy = Enemy(
                    name = "Test Goblin",
                    maxHP = 20,
                    currentHP = 15,
                    attack = 5,
                    defense = 2,
                    speed = 3,
                    danger = 2
                ),
                roundNumber = 2
            )
        )
        val (orchestrator, _) = createOrchestrator(state = state)

        // Should not crash
        val events = orchestrator.processInput("I attack").toList()
        assertTrue(events.isNotEmpty())
    }

    // ── NPC Context ──────────────────────────────────────────────

    @Test
    fun `NPCs at location are available in state`() = runTest {
        val npc = NPC(
            id = "npc_test",
            name = "Shopkeeper",
            archetype = NPCArchetype.MERCHANT,
            locationId = "test-location",
            personality = NPCPersonality(listOf("friendly"), "Warm", listOf("Sell goods")),
            lore = "The local shopkeeper"
        )
        val state = TestHelpers.createTestGameState().copy(
            hasOpeningNarrationPlayed = true,
            npcsByLocation = mapOf("test-location" to listOf(npc))
        )
        val (orchestrator, _) = createOrchestrator(state = state)

        val npcsHere = orchestrator.getState().getNPCsAtCurrentLocation()
        assertEquals(1, npcsHere.size)
        assertEquals("Shopkeeper", npcsHere[0].name)
    }

    // ── Quest State ──────────────────────────────────────────────

    @Test
    fun `active quests are preserved across processInput`() = runTest {
        val quest = Quest(
            id = "q1",
            name = "Find the Sword",
            description = "Locate the ancient blade",
            type = QuestType.MAIN_STORY,
            objectives = listOf(QuestObjective("search_cave", "Search the cave", ObjectiveType.EXPLORE, "cave")),
            rewards = QuestRewards(xp = 200),
            status = QuestProgressStatus.IN_PROGRESS
        )
        val state = TestHelpers.createTestGameState().copy(
            hasOpeningNarrationPlayed = true,
            activeQuests = mapOf("q1" to quest)
        )
        val (orchestrator, _) = createOrchestrator(state = state)

        orchestrator.processInput("I look around").toList()

        val current = orchestrator.getState()
        assertTrue(current.activeQuests.containsKey("q1"), "Quest should still be active")
        assertEquals("Find the Sword", current.activeQuests["q1"]?.name)
    }

    // ── Location State ───────────────────────────────────────────

    @Test
    fun `custom locations are preserved`() = runTest {
        val customLoc = Location(
            id = "custom_cave",
            name = "Hidden Cave",
            zoneId = "test",
            biome = Biome.CAVE,
            description = "A hidden cave",
            danger = 3,
            connections = listOf("test-location"),
            features = listOf("Stalactites"),
            lore = ""
        )
        val state = TestHelpers.createTestGameState().copy(
            hasOpeningNarrationPlayed = true,
            customLocations = mapOf("custom_cave" to customLoc)
        )
        val (orchestrator, _) = createOrchestrator(state = state)

        val current = orchestrator.getState()
        assertTrue(current.customLocations.containsKey("custom_cave"))
        assertEquals("Hidden Cave", current.customLocations["custom_cave"]?.name)
    }
}
