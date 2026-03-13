package com.rpgenerator.server

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.GameStateSnapshot
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.Base64
import com.google.genai.types.*
import com.google.genai.types.LiveServerMessage

/**
 * Handles WebSocket communication between the mobile app and Gemini Live API.
 *
 * Audio gating: only gate on toolCallPending (Gemini rejects input while
 * waiting for tool responses). The Live API handles interruptions natively —
 * no modelSpeaking or audioMuted gates needed.
 */
object GameWebSocketHandler {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun handle(ws: DefaultWebSocketServerSession, session: GameSession) {
        session.toolCallPending = false
        try {
            for (frame in ws.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val msg = json.parseToJsonElement(frame.readText()).jsonObject
                        val type = msg["type"]?.jsonPrimitive?.content ?: continue

                        when (type) {
                            "connect" -> handleConnect(ws, session, msg)
                            "audio" -> if (!session.toolCallPending) handleAudio(session, msg)
                            "text" -> if (!session.toolCallPending) handleText(session, msg)
                            "disconnect" -> {
                                session.disconnect()
                                ws.send("""{"type": "disconnected"}""")
                            }
                        }
                    }
                    is Frame.Binary -> {
                        if (session.toolCallPending) continue
                        val gemini = session.geminiSession ?: continue
                        val params = LiveSendRealtimeInputParameters.builder()
                            .media(Blob.builder().mimeType("audio/pcm").data(frame.data))
                            .build()
                        gemini.sendRealtimeInput(params)
                    }
                    else -> {}
                }
            }
        } finally {
            session.disconnect()
            GameSessionManager.removeSession(session.id)
        }
    }

    private suspend fun handleConnect(
        ws: DefaultWebSocketServerSession,
        session: GameSession,
        msg: JsonObject
    ) {
        try {
            val voiceName = msg["voiceName"]?.jsonPrimitive?.content ?: "Kore"
            session.connectToGemini(voiceName = voiceName)

            val gemini = session.geminiSession!!

            // Serialize all Gemini callbacks through a channel to preserve message ordering.
            val messageChannel = Channel<LiveServerMessage>(Channel.UNLIMITED)

            session.scope.launch {
                for (msg in messageChannel) {
                    handleGeminiMessage(ws, session, msg)
                }
            }

            gemini.receive { message ->
                // Only gate on tool calls — Gemini rejects input during tool execution
                if (message.toolCall().isPresent) {
                    session.toolCallPending = true
                }
                messageChannel.trySend(message)
            }

            // Start Lyria music streaming
            val apiKey = System.getenv("GOOGLE_API_KEY") ?: ""
            if (apiKey.isNotEmpty()) {
                try {
                    val musicChannel = Channel<ByteArray>(Channel.UNLIMITED)
                    session.scope.launch {
                        for (chunk in musicChannel) {
                            try {
                                ws.send(Frame.Binary(true, byteArrayOf(0x02) + chunk))
                            } catch (_: Exception) { break }
                        }
                    }
                    val music = LyriaMusicService(apiKey) { chunk ->
                        musicChannel.trySend(chunk)
                    }
                    session.lyriaMusicService = music
                    music.connect()
                    music.setMood("peaceful")
                } catch (e: Exception) {
                    println("GameWSHandler: Lyria music failed to start: ${e.message}")
                }
            }

            ws.send("""{"type": "connected"}""")
        } catch (e: Exception) {
            ws.send("""{"type": "error", "message": "${e.message?.replace("\"", "\\\"")}"}""")
        }
    }

    private suspend fun handleAudio(session: GameSession, msg: JsonObject) {
        val gemini = session.geminiSession ?: return
        val b64 = msg["data"]?.jsonPrimitive?.content ?: return
        val pcm = Base64.getDecoder().decode(b64)

        val params = LiveSendRealtimeInputParameters.builder()
            .media(Blob.builder().mimeType("audio/pcm").data(pcm))
            .build()
        gemini.sendRealtimeInput(params)
    }

    private suspend fun handleText(session: GameSession, msg: JsonObject) {
        val gemini = session.geminiSession ?: return
        val text = msg["content"]?.jsonPrimitive?.content ?: return

        val params = LiveSendClientContentParameters.builder()
            .turnComplete(true)
            .turns(
                Content.builder()
                    .role("user")
                    .parts(listOf(Part.builder().text(text).build()))
                    .build()
            )
            .build()
        gemini.sendClientContent(params)
    }

    private suspend fun handleGeminiMessage(
        ws: DefaultWebSocketServerSession,
        session: GameSession,
        message: LiveServerMessage
    ) {
        val hasToolCall = message.toolCall().isPresent
        val hasTurnComplete = message.serverContent().orElse(null)?.turnComplete()?.orElse(false) ?: false
        if (hasToolCall || hasTurnComplete) {
            println("GameWSHandler: Gemini message — toolCall=$hasToolCall turnComplete=$hasTurnComplete")
        }

        // Handle tool calls
        val toolCall = message.toolCall().orElse(null)
        if (toolCall != null) {
            val funcNames = toolCall.functionCalls().orElse(emptyList()).mapNotNull { it.name().orElse(null) }
            println("GameWSHandler: TOOL CALL received: $funcNames")
            session.toolCallPending = true
            try {
                handleToolCall(ws, session, toolCall)
            } finally {
                session.toolCallPending = false
            }
        }

        // Handle server content (text + audio)
        val content = message.serverContent().orElse(null) ?: return

        if (content.interrupted().orElse(false)) {
            ws.send("""{"type": "interrupted"}""")
        }

        val modelTurn = content.modelTurn().orElse(null)
        if (modelTurn != null) {
            val parts = modelTurn.parts().orElse(emptyList())
            for (part in parts) {
                val text = part.text().orElse(null)
                if (text != null) {
                    val cleaned = text.replace(Regex("<ctrl\\d+>"), "").trim()
                    if (cleaned.isNotEmpty() && !cleaned.startsWith("**")) {
                        ws.send("""{"type": "text", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(cleaned))}}""")
                    }
                }

                val audioBytes = part.inlineData().orElse(null)?.data()?.orElse(null)
                if (audioBytes != null) {
                    ws.send(Frame.Binary(true, audioBytes))
                }
            }
        }

        // Cancel silence watchdog when model is producing output
        if (modelTurn != null || content.outputTranscription().isPresent) {
            session.awaitingModelResponse = false
            session.watchdogJob?.cancel()
        }

        // Input transcription
        val inputText = content.inputTranscription().orElse(null)?.text()?.orElse(null)
        if (inputText != null) {
            ws.send("""{"type": "transcript", "role": "user", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(inputText))}}""")
            // Start silence watchdog — nudge model if it doesn't respond within 8s
            session.awaitingModelResponse = true
            session.watchdogJob?.cancel()
            session.watchdogJob = session.scope.launch {
                kotlinx.coroutines.delay(8000)
                if (session.awaitingModelResponse && !session.toolCallPending) {
                    println("GameWSHandler: Model silent for 8s after user input, sending nudge")
                    val nudge = "[SYSTEM: The player is waiting for your response. Please respond with spoken audio now.]"
                    val params = LiveSendClientContentParameters.builder()
                        .turnComplete(false)
                        .turns(Content.builder()
                            .role("user")
                            .parts(listOf(Part.builder().text(nudge).build()))
                            .build())
                        .build()
                    session.geminiSession?.sendClientContent(params)
                }
            }
        }

        // Output transcription
        val outputText = content.outputTranscription().orElse(null)?.text()?.orElse(null)
        if (outputText != null) {
            val cleaned = outputText.replace(Regex("<ctrl\\d+>"), "").trim()
            if (cleaned.isNotEmpty()) {
                ws.send("""{"type": "transcript", "role": "model", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(cleaned))}}""")
            }
        }

        if (content.turnComplete().orElse(false)) {
            ws.send("""{"type": "turn_complete"}""")
        }
    }

    private suspend fun handleToolCall(
        ws: DefaultWebSocketServerSession,
        session: GameSession,
        toolCall: LiveServerToolCall
    ) {
        val functionCalls = toolCall.functionCalls().orElse(emptyList())
        val responses = mutableListOf<FunctionResponse>()

        for (fc in functionCalls) {
            val name = fc.name().orElse("")
            val id = fc.id().orElse(name)
            val args = fc.args().orElse(emptyMap())

            ws.send("""{"type": "tool_call", "name": "$name", "args": ${json.encodeToString(JsonObject.serializer(), buildJsonObject {
                args.forEach { (k, v) -> put(k, JsonPrimitive(v.toString())) }
            })}}""")

            val resultData: JsonObject = try {
                if (name == "send_player_input") {
                    val input = args["input"]?.toString() ?: ""
                    val events = session.game.processInput(input).toList()

                    for (event in events) {
                        val eventJson = json.encodeToString(GameEvent.serializer(), event)
                        ws.send("""{"type": "game_event", "event": $eventJson}""")
                    }

                    try {
                        val stateSnapshot = session.game.getState()
                        val stateJson = json.encodeToString(GameStateSnapshot.serializer(), stateSnapshot)
                        ws.send("""{"type": "state_update", "state": $stateJson}""")
                    } catch (_: Exception) {}

                    // Build human-readable summary for Gemini (not raw JSON)
                    val narrative = buildList {
                        for (event in events) {
                            when (event) {
                                is GameEvent.NarratorText -> add(event.text)
                                is GameEvent.NPCDialogue -> add("${event.npcName} says: ${event.text}")
                                is GameEvent.SystemNotification -> add("[System] ${event.text}")
                                is GameEvent.CombatLog -> add("[Combat] ${event.text}")
                                is GameEvent.StatChange -> add("${event.statName}: ${event.oldValue} → ${event.newValue}")
                                is GameEvent.ItemGained -> add("Gained item: ${event.itemName} x${event.quantity}")
                                is GameEvent.QuestUpdate -> add("Quest '${event.questName}': ${event.status}")
                                else -> {} // skip binary events (images, audio)
                            }
                        }
                    }.joinToString("\n")

                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("narrative", JsonPrimitive(narrative.ifEmpty { "Action processed." }))
                    }
                } else {
                    val argsMap: Map<String, Any?> = args.mapValues { (_, value) ->
                        when (value) {
                            is String -> value
                            is Number -> value
                            is Boolean -> value
                            else -> value.toString()
                        }
                    }

                    val result = session.game.executeTool(name, argsMap)

                    for (event in result.events) {
                        val eventJson = json.encodeToString(GameEvent.serializer(), event)
                        ws.send("""{"type": "game_event", "event": $eventJson}""")
                    }

                    ws.send("""{"type": "tool_result", "name": "$name", "success": ${result.success}}""")

                    if (name == "shift_music_mood") {
                        val mood = argsMap["mood"]?.toString() ?: "peaceful"
                        session.lyriaMusicService?.setMood(mood)
                    }

                    buildJsonObject {
                        put("success", JsonPrimitive(result.success))
                        result.data.let { put("data", it) }
                        result.error?.let { put("error", JsonPrimitive(it)) }
                    }
                }
            } catch (e: Exception) {
                println("GameWSHandler: Tool '$name' failed: ${e.message}")
                e.printStackTrace()
                buildJsonObject {
                    put("success", JsonPrimitive(false))
                    put("error", JsonPrimitive(e.message ?: "Unknown error"))
                }
            }

            responses.add(
                FunctionResponse.builder()
                    .id(id)
                    .name(name)
                    .response(mapOf<String, Any>("result" to resultData.toString()))
                    .build()
            )
        }

        if (responses.isNotEmpty()) {
            val responseNames = responses.mapNotNull { it.name().orElse(null) }
            println("GameWSHandler: Sending ${responses.size} tool response(s) back to Gemini: $responseNames")
            val params = LiveSendToolResponseParameters.builder()
                .functionResponses(responses)
                .build()
            try {
                session.geminiSession?.sendToolResponse(params)
            } catch (e: Exception) {
                println("GameWSHandler: Failed to send tool response to Gemini: ${e.message}")
            }
        }
    }
}
