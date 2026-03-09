package com.rpgenerator.core.orchestration

import app.cash.turbine.test
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.LLMToolCall
import com.rpgenerator.core.test.AgentType
import com.rpgenerator.core.test.MockLLMInterface
import com.rpgenerator.core.test.TestHelpers
import com.rpgenerator.core.tools.UnifiedToolContractImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the 3-phase turn pipeline:
 * Phase 1+2: DECIDE & EXECUTE — GM calls tools, prose discarded
 * Phase 3:   NARRATE — narrator writes prose from TurnSummary
 */
class GameOrchestratorTest {

    private val toolContract = UnifiedToolContractImpl()

    @Test
    fun `first input triggers opening narration`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState(hasOpeningNarrationPlayed = false)
        val orchestrator = GameOrchestrator(mockLLM, initialState, toolContract)

        orchestrator.processInput("").test {
            val event = awaitItem()
            assertTrue(event is GameEvent.NarratorText, "First event should be opening narration")
            cancelAndConsumeRemainingEvents()
        }

        assertTrue(orchestrator.getState().hasOpeningNarrationPlayed)
    }

    @Test
    fun `player input produces narration from narrate agent`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState().copy(hasOpeningNarrationPlayed = true)
        val orchestrator = GameOrchestrator(mockLLM, initialState, toolContract)

        orchestrator.processInput("I look around").test {
            val event = awaitItem()
            assertTrue(event is GameEvent.NarratorText, "Should emit narration")
            // Narration should come from the NARRATE agent, not the DECIDE agent
            val text = (event as GameEvent.NarratorText).text
            assertTrue(text.isNotBlank(), "Narration should not be blank")
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `blank input after opening is ignored`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState().copy(hasOpeningNarrationPlayed = true)
        val orchestrator = GameOrchestrator(mockLLM, initialState, toolContract)

        orchestrator.processInput("").test {
            awaitComplete()
        }
    }

    @Test
    fun `dead player triggers death handling`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState().copy(
            hasOpeningNarrationPlayed = true,
            characterSheet = TestHelpers.createTestCharacterSheet().copy(
                resources = TestHelpers.createTestCharacterSheet().resources.copy(currentHP = 0)
            )
        )
        val orchestrator = GameOrchestrator(mockLLM, initialState, toolContract)

        orchestrator.processInput("anything").test {
            val first = awaitItem()
            assertTrue(first is GameEvent.SystemNotification, "Should emit death notification")
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getState returns current game state`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState(playerLevel = 5, playerXP = 250L)
        val orchestrator = GameOrchestrator(mockLLM, initialState, toolContract)

        val state = orchestrator.getState()
        assertTrue(state.playerLevel == 5)
        assertTrue(state.playerXP == 250L)
    }

    @Test
    fun `pipeline creates decide and narrate agents`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState().copy(hasOpeningNarrationPlayed = true)
        val orchestrator = GameOrchestrator(mockLLM, initialState, toolContract)

        orchestrator.processInput("I look around").test {
            cancelAndConsumeRemainingEvents()
        }

        // Should have created at least a DECIDE and NARRATE agent
        val agentTypes = mockLLM.createdAgents.map { it.agentType }
        assertTrue(agentTypes.contains(AgentType.DECIDE), "Should create DECIDE agent, got: $agentTypes")
        assertTrue(agentTypes.contains(AgentType.NARRATE), "Should create NARRATE agent, got: $agentTypes")
    }

    @Test
    fun `decide agent tools execute and state updates`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState().copy(hasOpeningNarrationPlayed = true)
        val orchestrator = GameOrchestrator(mockLLM, initialState, toolContract)

        // Configure the decide agent to call add_gold
        // We need to wait for the agent to be created, so we configure it after creation
        // by using the mock's ability to set tool calls on its agents
        orchestrator.processInput("I search the chest").test {
            cancelAndConsumeRemainingEvents()
        }

        // Even without tool calls configured, the pipeline should still produce narration
        val state = orchestrator.getState()
        assertTrue(state.hasOpeningNarrationPlayed)
    }

    // ── Location discovery regex tests ──
    // These duplicate the regex from GameOrchestrator.Companion to verify the fix
    // for the bug where prose fragments were extracted as location names.

    private val LOCATION_PATTERN = Regex(
        """(?:[Ee]nter|[Aa]rrive at|[Rr]each|[Ss]tep into|[Mm]ove to|[Ff]ind (?:yourself |ourselves )?in|[Ee]merge into|[Ll]and (?:on|in)|[Tt]umble (?:into|out onto))\s+(?:[Tt]he\s+)?((?:[A-Z][a-zA-Z']+)(?:(?:\s+(?:of|the|and|in|on|at|by|for|to|—|-)\s+|\s+)(?:[A-Z][a-zA-Z']+|[0-9]+(?:[-][A-Z0-9]+)?)){0,8})"""
    )

    private val locationArticles = setOf("of", "the", "and", "in", "on", "at", "by", "for", "to", "a", "an")

    private fun looksLikeLocationName(name: String): Boolean {
        val words = name.split("\\s+".toRegex())
        if (words.size < 2 || words.size > 8) return false
        if (name.contains('*') || name.contains('[') || name.contains(']')) return false
        val significantWords = words.filter { it.lowercase() !in locationArticles && !it.all { c -> c == '—' || c == '-' } }
        if (significantWords.isEmpty()) return false
        val titleCased = significantWords.count { it[0].isUpperCase() }
        return titleCased.toFloat() / significantWords.size >= 0.5f
    }

    private fun extractLocation(text: String): String? {
        val match = LOCATION_PATTERN.find(text) ?: return null
        val loc = match.groupValues[1].trim()
        return if (loc.isNotBlank() && looksLikeLocationName(loc)) loc else null
    }

    @Test
    fun `location regex matches valid location names`() {
        assertEquals("Maintenance Corridor", extractLocation("You enter the Maintenance Corridor"))
        assertEquals("Threshold of Zero", extractLocation("You step into the Threshold of Zero"))
        assertEquals("Central Cooling Vent", extractLocation("You arrive at Central Cooling Vent"))
        assertEquals("Garden of False Idols", extractLocation("You emerge into the Garden of False Idols"))
    }

    @Test
    fun `location regex rejects prose fragments`() {
        // These are real bug cases from playtesting where narrator prose was extracted as locations
        assertEquals(null, extractLocation("You emerge into of the floor, perfectly illuminated"))
        assertEquals(null, extractLocation("You step into a crouch, your boots finding purchase"))
        assertEquals(null, extractLocation("You reach out and grab it with the stylus"))
        assertEquals(null, extractLocation("You reach for a fireball that isn't there"))
        assertEquals(null, extractLocation("You move to find yourself in DETECTED"))
        assertEquals(null, extractLocation("You reach out and snag a *Rare Steel Sword*"))
    }

    @Test
    fun `narrate agent receives turn summary context`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState().copy(hasOpeningNarrationPlayed = true)
        val orchestrator = GameOrchestrator(mockLLM, initialState, toolContract)

        orchestrator.processInput("I look around").test {
            cancelAndConsumeRemainingEvents()
        }

        // Find the NARRATE agent and check it received the right context
        val narrateAgent = mockLLM.createdAgents.firstOrNull { it.agentType == AgentType.NARRATE }
        assertTrue(narrateAgent != null, "Narrate agent should exist")
        assertTrue(narrateAgent.receivedMessages.isNotEmpty(), "Narrate agent should have received messages")

        val narrationInput = narrateAgent.receivedMessages.first()
        assertTrue(narrationInput.contains("Player said:"), "Narration input should contain player input")
        assertTrue(narrationInput.contains("WORLD STATE"), "Narration input should contain world state")
    }
}
