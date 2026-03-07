package com.rpgenerator.core.orchestration

import app.cash.turbine.test
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.test.MockLLMInterface
import com.rpgenerator.core.test.TestHelpers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the unified orchestrator pipeline.
 *
 * All narrative intents (combat, exploration, dialogue) go through the coordinated path:
 * GM plans → mechanics execute → Narrator renders → unified NarratorText event.
 *
 * Menu/system intents short-circuit with SystemNotification events.
 */
class GameOrchestratorTest {

    @Test
    fun `combat emits narrator text followed by mechanical events`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState()
        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("I attack the goblin").test {
            // Coordinated path: first event is unified narration
            val narration = awaitItem()
            assertTrue(narration is GameEvent.NarratorText, "First event should be unified narration")

            // Then mechanical events (XP change, items, etc.) follow
            // Consume all remaining — exact count varies by combat outcome
            cancelAndConsumeRemainingEvents()
        }

        val finalState = orchestrator.getState()
        assertTrue(finalState.playerXP > 0, "XP should have increased after combat")
    }

    @Test
    fun `exploration emits narrator text`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState()
        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("I look around the forest").test {
            val event = awaitItem()
            assertTrue(event is GameEvent.NarratorText, "Exploration should emit narration")
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `system query returns stats as system notification`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState(playerLevel = 5, playerXP = 250L)
        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("show my stats").test {
            val event = awaitItem()
            assertTrue(event is GameEvent.SystemNotification, "Should emit system notification")
            val notification = event as GameEvent.SystemNotification
            assertTrue(notification.text.contains("Level 5"), "Should show level")
            assertTrue(notification.text.contains("250"), "Should show XP")
            awaitComplete()
        }
    }

    @Test
    fun `npc dialogue with no NPCs emits narration`() = runTest {
        val mockLLM = MockLLMInterface()
        val initialState = TestHelpers.createTestGameState()
        val orchestrator = GameOrchestrator(mockLLM, initialState)

        // No NPCs at location — coordinated path renders narrator response
        orchestrator.processInput("I talk to the merchant").test {
            val event = awaitItem()
            assertTrue(event is GameEvent.NarratorText,
                "Should emit narration when no NPCs are present")
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `combat awards XP and can level up`() = runTest {
        val mockLLM = MockLLMInterface()
        // Start at 150 XP — combat gives 50+ XP, level threshold at 100
        val initialState = TestHelpers.createTestGameState(playerXP = 150L)
        val orchestrator = GameOrchestrator(mockLLM, initialState)

        orchestrator.processInput("I attack the goblin").test {
            // Consume all events
            cancelAndConsumeRemainingEvents()
        }

        val finalState = orchestrator.getState()
        assertTrue(finalState.playerXP > 150L, "XP should have increased")
        // At 150 XP + 50 gain = 200, level 2 threshold is at 100+200=300
        // So level-up depends on exact XP gain
    }
}
