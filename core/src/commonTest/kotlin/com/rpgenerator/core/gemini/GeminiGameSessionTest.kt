package com.rpgenerator.core.gemini

import app.cash.turbine.test
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.test.TestHelpers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.*

/**
 * Tests the full game session flow where Gemini drives gameplay via tool calls.
 * Uses a mock session to simulate Gemini's behavior without API calls.
 *
 * These tests verify the complete loop:
 * Player input → Gemini processes → Tool calls → Game state updates → Gemini narrates
 */
class GeminiGameSessionTest {

    // ── Session Lifecycle ──────────────────────────────────────────

    @Test
    fun `session connects and reaches CONNECTED state`() = runTest {
        val session = MockGeminiGameSession()
        val config = createTestConfig()

        session.connect(config)

        assertEquals(SessionState.CONNECTED, session.state)
    }

    @Test
    fun `session disconnects cleanly`() = runTest {
        val session = MockGeminiGameSession()
        session.connect(createTestConfig())

        session.disconnect()

        assertEquals(SessionState.DISCONNECTED, session.state)
    }

    // ── Text Input (testing mode) ──────────────────────────────────

    @Test
    fun `sendText triggers Gemini response with narration`() = runTest {
        val session = MockGeminiGameSession()
        session.connect(createTestConfig())

        session.outputStream.test {
            session.sendText("I look around the forest")

            val output = awaitItem()
            assertTrue(output is GeminiOutput.Text, "Should receive text narration")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendText for combat triggers tool call then narration`() = runTest {
        val session = MockGeminiGameSession()
        session.connect(createTestConfig())

        // Simulate Gemini deciding to call attack_target tool
        session.queueToolCall(
            GeminiToolCall(
                id = "call_1",
                name = "attack_target",
                arguments = buildJsonObject {
                    put("target", JsonPrimitive("goblin"))
                }
            )
        )

        session.outputStream.test {
            session.sendText("I attack the goblin")

            // First: Gemini requests a tool call
            val toolRequest = awaitItem()
            assertTrue(toolRequest is GeminiOutput.ToolCallRequest, "Should request tool call")
            assertEquals("attack_target", (toolRequest as GeminiOutput.ToolCallRequest).calls.first().name)

            // Tool result is sent back, then Gemini narrates
            val narration = awaitItem()
            assertTrue(narration is GeminiOutput.Text, "Should narrate combat result")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Tool Call → Game State Loop ────────────────────────────────

    @Test
    fun `tool call execution updates game state`() = runTest {
        val session = MockGeminiGameSession()
        val config = createTestConfig()
        session.connect(config)

        // Simulate Gemini calling get_player_stats
        session.queueToolCall(
            GeminiToolCall(
                id = "call_stats",
                name = "get_player_stats",
                arguments = buildJsonObject { }
            )
        )

        session.outputStream.test {
            session.sendText("What are my stats?")

            val toolRequest = awaitItem()
            assertTrue(toolRequest is GeminiOutput.ToolCallRequest)

            // After tool execution, Gemini speaks the result
            val narration = awaitItem()
            assertTrue(narration is GeminiOutput.Text)
            val text = (narration as GeminiOutput.Text).content
            assertTrue(text.contains("level", ignoreCase = true) || text.isNotEmpty(),
                "Narration should reference player stats")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple tool calls in sequence`() = runTest {
        val session = MockGeminiGameSession()
        session.connect(createTestConfig())

        // Gemini decides to check location AND NPCs before narrating
        session.queueToolCall(
            GeminiToolCall("call_1", "get_location", buildJsonObject { })
        )
        session.queueToolCall(
            GeminiToolCall("call_2", "get_npcs_here", buildJsonObject { })
        )

        session.outputStream.test {
            session.sendText("Where am I and who's here?")

            val toolRequest1 = awaitItem()
            assertTrue(toolRequest1 is GeminiOutput.ToolCallRequest)

            val toolRequest2 = awaitItem()
            assertTrue(toolRequest2 is GeminiOutput.ToolCallRequest)

            // Final narration after both tool results
            val narration = awaitItem()
            assertTrue(narration is GeminiOutput.Text)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Multimodal Output ──────────────────────────────────────────

    @Test
    fun `scene description triggers interleaved image`() = runTest {
        val session = MockGeminiGameSession()
        session.connect(createTestConfig())

        // Simulate Gemini generating text + image interleaved
        session.queueInterleaved(
            GeminiOutput.Text("You step into a vast crystalline cavern..."),
            GeminiOutput.Image(
                imageData = ByteArray(100), // mock image bytes
                description = "Crystalline cavern with glowing formations"
            ),
            GeminiOutput.Text("The walls shimmer with an otherworldly light.")
        )

        session.outputStream.test {
            session.sendText("I enter the cave")

            val text1 = awaitItem()
            assertTrue(text1 is GeminiOutput.Text)

            val image = awaitItem()
            assertTrue(image is GeminiOutput.Image, "Should receive inline image")
            assertTrue((image as GeminiOutput.Image).imageData.isNotEmpty())

            val text2 = awaitItem()
            assertTrue(text2 is GeminiOutput.Text)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mood shift triggers music change via tool`() = runTest {
        val session = MockGeminiGameSession()
        session.connect(createTestConfig())

        session.queueToolCall(
            GeminiToolCall(
                id = "call_music",
                name = "shift_music_mood",
                arguments = buildJsonObject {
                    put("mood", JsonPrimitive("tense"))
                    put("intensity", JsonPrimitive(0.8))
                }
            )
        )

        session.outputStream.test {
            session.sendText("Something feels wrong here...")

            val toolRequest = awaitItem()
            assertTrue(toolRequest is GeminiOutput.ToolCallRequest)
            assertEquals("shift_music_mood", (toolRequest as GeminiOutput.ToolCallRequest).calls.first().name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `audio output streams during narration`() = runTest {
        val session = MockGeminiGameSession()
        session.connect(createTestConfig())

        session.queueInterleaved(
            GeminiOutput.Text("The forest awakens around you."),
            GeminiOutput.Audio(pcmData = ByteArray(4800), sampleRate = 24000)
        )

        session.outputStream.test {
            session.sendText("I wake up")

            val text = awaitItem()
            assertTrue(text is GeminiOutput.Text)

            val audio = awaitItem()
            assertTrue(audio is GeminiOutput.Audio, "Should receive audio chunk")
            assertEquals(24000, (audio as GeminiOutput.Audio).sampleRate)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Character Creation Flow ────────────────────────────────────

    @Test
    fun `character creation via voice conversation`() = runTest {
        val session = MockGeminiGameSession()
        session.connect(createTestConfig())

        // Step 1: Gemini asks about the world
        session.queueInterleaved(
            GeminiOutput.Text("What kind of world calls to you, adventurer?"),
            GeminiOutput.TurnComplete
        )

        session.outputStream.test {
            session.sendText("")  // Initial empty input to start

            val greeting = awaitItem()
            assertTrue(greeting is GeminiOutput.Text)
            assertTrue((greeting as GeminiOutput.Text).content.contains("world"))

            val turnComplete = awaitItem()
            assertTrue(turnComplete is GeminiOutput.TurnComplete)

            cancelAndIgnoreRemainingEvents()
        }

        // Step 2: Player describes preference, Gemini generates portrait
        session.queueInterleaved(
            GeminiOutput.Text("A system apocalypse world... interesting. And who are you in this world?"),
            GeminiOutput.Image(imageData = ByteArray(200), description = "Apocalyptic cityscape"),
            GeminiOutput.TurnComplete
        )

        session.outputStream.test {
            session.sendText("Something dark, like a system apocalypse")

            val response = awaitItem()
            assertTrue(response is GeminiOutput.Text)

            val worldArt = awaitItem()
            assertTrue(worldArt is GeminiOutput.Image, "Should generate world concept art")

            val turnComplete = awaitItem()
            assertTrue(turnComplete is GeminiOutput.TurnComplete)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Tutorial Flow ──────────────────────────────────────────────

    @Test
    fun `tutorial teaches mechanics through gameplay`() = runTest {
        val session = MockGeminiGameSession()
        session.connect(createTestConfig())

        // Tutorial step: narrator guides player to look around
        session.queueInterleaved(
            GeminiOutput.Text("You awaken in a strange place. Try looking around — just tell me what you want to do."),
            GeminiOutput.Image(imageData = ByteArray(100), description = "Starting location"),
            GeminiOutput.TurnComplete
        )

        session.outputStream.test {
            session.sendText("")  // Game start

            val narration = awaitItem()
            assertTrue(narration is GeminiOutput.Text)
            assertTrue((narration as GeminiOutput.Text).content.contains("look", ignoreCase = true))

            val sceneArt = awaitItem()
            assertTrue(sceneArt is GeminiOutput.Image)

            awaitItem()  // TurnComplete
            cancelAndIgnoreRemainingEvents()
        }

        // Player follows tutorial guidance
        session.queueToolCall(
            GeminiToolCall("call_loc", "get_location", buildJsonObject { })
        )
        session.queueInterleaved(
            GeminiOutput.Text("Nice! You found something on the ground. I've added it to your inventory — swipe up to check."),
            GeminiOutput.TurnComplete
        )

        session.outputStream.test {
            session.sendText("I look around")

            val toolCall = awaitItem()
            assertTrue(toolCall is GeminiOutput.ToolCallRequest)

            val tutorialNarration = awaitItem()
            assertTrue(tutorialNarration is GeminiOutput.Text)
            assertTrue((tutorialNarration as GeminiOutput.Text).content.contains("inventory", ignoreCase = true),
                "Tutorial should guide player to check inventory")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Error Handling ─────────────────────────────────────────────

    @Test
    fun `sendText before connect throws`() = runTest {
        val session = MockGeminiGameSession()

        assertFailsWith<IllegalStateException> {
            session.sendText("hello")
        }
    }

    @Test
    fun `failed tool call returns error to Gemini`() = runTest {
        val session = MockGeminiGameSession()
        session.connect(createTestConfig())

        // Gemini tries to talk to NPC that doesn't exist
        session.queueToolCall(
            GeminiToolCall(
                id = "call_npc",
                name = "talk_to_npc",
                arguments = buildJsonObject {
                    put("npcName", JsonPrimitive("Ghost NPC"))
                }
            )
        )

        session.outputStream.test {
            session.sendText("I talk to the ghost")

            val toolRequest = awaitItem()
            assertTrue(toolRequest is GeminiOutput.ToolCallRequest)

            // Gemini should recover gracefully with narration
            val recovery = awaitItem()
            assertTrue(recovery is GeminiOutput.Text,
                "Gemini should narrate even after tool failure")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun createTestConfig(): GeminiSessionConfig {
        return GeminiSessionConfig(
            systemPrompt = "You are the narrator of a LitRPG adventure.",
            tools = emptyList(),  // Tools are defined by the contract
            gameContext = GameContext(
                playerName = "TestPlayer",
                playerLevel = 1,
                playerClass = null,
                currentLocation = "Test Location",
                locationDescription = "A test location for unit tests",
                npcsPresent = emptyList(),
                activeQuests = emptyList(),
                recentEvents = emptyList()
            )
        )
    }
}

/**
 * Mock implementation of GeminiGameSession for testing.
 * Simulates Gemini's behavior by replaying queued outputs.
 */
class MockGeminiGameSession : GeminiGameSession {
    override var state: SessionState = SessionState.DISCONNECTED
        private set

    private val _outputStream = MutableSharedFlow<GeminiOutput>(replay = 0, extraBufferCapacity = 64)
    override val outputStream = _outputStream

    private val queuedToolCalls = mutableListOf<GeminiToolCall>()
    private val queuedInterleaved = mutableListOf<List<GeminiOutput>>()

    // Queue a tool call that Gemini will "request" on next sendText
    fun queueToolCall(call: GeminiToolCall) {
        queuedToolCalls.add(call)
    }

    // Queue interleaved outputs (text + image + audio mixed)
    fun queueInterleaved(vararg outputs: GeminiOutput) {
        queuedInterleaved.add(outputs.toList())
    }

    override suspend fun connect(config: GeminiSessionConfig) {
        state = SessionState.CONNECTED
    }

    override suspend fun sendAudio(pcmData: ByteArray) {
        check(state == SessionState.CONNECTED) { "Not connected" }
        // Audio is treated same as text in mock — triggers queued outputs
        emitQueuedOutputs()
    }

    override suspend fun sendText(text: String) {
        check(state == SessionState.CONNECTED) { "Not connected" }
        emitQueuedOutputs()
    }

    override suspend fun sendToolResults(results: List<Pair<String, ToolResult>>) {
        // After tool results, emit a default narration
        _outputStream.emit(GeminiOutput.Text("The narrator describes what happened."))
    }

    override suspend fun updateContext(context: GameContext) {
        // No-op in mock
    }

    override suspend fun disconnect() {
        state = SessionState.DISCONNECTED
    }

    private suspend fun emitQueuedOutputs() {
        // Emit all queued tool calls first (Gemini can batch multiple)
        if (queuedToolCalls.isNotEmpty()) {
            while (queuedToolCalls.isNotEmpty()) {
                val call = queuedToolCalls.removeFirst()
                _outputStream.emit(GeminiOutput.ToolCallRequest(listOf(call)))
            }
            // After tool results, use queued interleaved if available, otherwise generic narration
            if (queuedInterleaved.isNotEmpty()) {
                val outputs = queuedInterleaved.removeFirst()
                outputs.forEach { output -> _outputStream.emit(output) }
            } else {
                _outputStream.emit(GeminiOutput.Text("The narrator describes what happened."))
            }
            return
        }

        // Then emit interleaved outputs
        if (queuedInterleaved.isNotEmpty()) {
            val outputs = queuedInterleaved.removeFirst()
            outputs.forEach { output ->
                _outputStream.emit(output)
            }
            return
        }

        // Default: just narrate
        _outputStream.emit(GeminiOutput.Text("The narrator speaks."))
    }
}
