package com.rpgenerator.cli

import com.rpgenerator.core.gemini.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Quick test harness for Gemini Live API.
 * Connects via text mode (no microphone needed) with game tools enabled.
 * Run with: ./gradlew :cli:run --args="--live"
 */
object GeminiLiveTest {

    fun run() {
        println("=== Gemini Live API Test ===")
        println("Requires GOOGLE_API_KEY environment variable")
        println()

        val apiKey = System.getenv("GOOGLE_API_KEY")
        if (apiKey.isNullOrEmpty()) {
            println("ERROR: GOOGLE_API_KEY not set")
            return
        }

        val tools = GeminiTools.createDefault()
        val session = GeminiLiveSession(tools)

        val config = GeminiSessionConfig(
            modelId = "gemini-2.5-flash-preview-native-audio-dialog",
            systemPrompt = buildSystemPrompt(),
            tools = tools.getToolDeclarations(),
            voiceConfig = VoiceConfig(voiceName = "Kore"),
            gameContext = GameContext(
                playerName = "Adventurer",
                playerLevel = 1,
                playerClass = null,
                currentLocation = "Starting Area",
                locationDescription = "A peaceful forest clearing with ancient stone markers",
                npcsPresent = emptyList(),
                activeQuests = emptyList(),
                recentEvents = emptyList()
            )
        )

        runBlocking {
            println("Connecting to Gemini Live API...")

            try {
                session.connect(config)
                println("Connected! Session state: ${session.state}")
                println()

                // Collect outputs in background
                val outputJob = launch {
                    session.outputStream.collect { output ->
                        when (output) {
                            is GeminiOutput.Text -> print(output.content)
                            is GeminiOutput.Audio -> print("[audio: ${output.pcmData.size} bytes] ")
                            is GeminiOutput.Image -> println("[image: ${output.description}]")
                            is GeminiOutput.ToolCallRequest -> {
                                println("\n[tool call: ${output.calls.joinToString { it.name }}]")
                            }
                            is GeminiOutput.TurnComplete -> println("\n---")
                        }
                    }
                }

                // Interactive text input loop
                println("Type messages to the narrator (type 'quit' to exit):")
                println()

                while (true) {
                    print("> ")
                    val input = readlnOrNull() ?: break
                    if (input.lowercase() in listOf("quit", "exit", "q")) break
                    if (input.isBlank()) continue

                    session.sendText(input)
                }

                outputJob.cancel()
                session.disconnect()
                println("Session closed.")

            } catch (e: Exception) {
                println("ERROR: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun buildSystemPrompt(): String = """
        You are a narrator for a LitRPG adventure game. You speak in a dramatic,
        immersive style like an audiobook narrator. The player is starting a new adventure.

        You have access to game tools to query and modify the game state. Use them
        to ground your narration in actual game mechanics. When the player takes actions,
        use the appropriate tool and narrate the results dramatically.

        Keep responses concise but vivid. This is a voice experience — shorter is better.

        Current game state:
        - Player: Level 1 Adventurer
        - Location: Starting Area (forest clearing)
        - No active quests yet

        Begin by welcoming the player to the world and setting the scene.
    """.trimIndent()
}
