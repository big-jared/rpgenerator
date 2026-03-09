package com.rpgenerator.server

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.GameStateSnapshot
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.Base64
import com.google.genai.types.*
import com.google.genai.types.LiveServerMessage

/**
 * Handles WebSocket communication between the mobile app and Gemini Live API.
 *
 * Protocol (client → server):
 *   {"type": "connect", "voiceName": "Kore"}
 *   {"type": "audio", "data": "<base64 PCM 16kHz>"}
 *   {"type": "text", "content": "I look around"}
 *   {"type": "disconnect"}
 *
 * Protocol (server → client):
 *   {"type": "audio", "data": "<base64 PCM 24kHz>"}
 *   {"type": "text", "content": "narrator text"}
 *   {"type": "transcript", "role": "user|model", "content": "transcribed text"}
 *   {"type": "tool_call", "name": "attack_target", "args": {...}}
 *   {"type": "tool_result", "name": "attack_target", "result": {...}}
 *   {"type": "turn_complete"}
 *   {"type": "error", "message": "..."}
 *   {"type": "connected"}
 */
object GameWebSocketHandler {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun handle(ws: DefaultWebSocketServerSession, session: GameSession) {
        try {
            for (frame in ws.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val msg = json.parseToJsonElement(frame.readText()).jsonObject
                        val type = msg["type"]?.jsonPrimitive?.content ?: continue

                        when (type) {
                            "connect" -> handleConnect(ws, session, msg)
                            "audio" -> handleAudio(session, msg)
                            "text" -> handleText(session, msg)
                            "disconnect" -> {
                                session.disconnect()
                                ws.send("""{"type": "disconnected"}""")
                            }
                        }
                    }
                    is Frame.Binary -> {
                        // Raw PCM audio bytes (alternative to base64)
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

            // Set up message handler — forward Gemini outputs to mobile app
            gemini.receive { message ->
                session.scope.launch {
                    handleGeminiMessage(ws, session, message)
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
        // Handle tool calls
        message.toolCall().ifPresent { toolCall ->
            session.scope.launch {
                handleToolCall(ws, session, toolCall)
            }
        }

        // Handle server content (text + audio)
        message.serverContent().ifPresent { content ->
            if (content.interrupted().orElse(false)) {
                session.scope.launch { ws.send("""{"type": "interrupted"}""") }
                return@ifPresent
            }

            content.modelTurn().ifPresent { modelTurn ->
                modelTurn.parts().ifPresent { parts ->
                    for (part in parts) {
                        // Text
                        part.text().ifPresent { text ->
                            session.scope.launch {
                                ws.send("""{"type": "text", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(text))}}""")
                            }
                        }

                        // Audio
                        part.inlineData().ifPresent { blob ->
                            blob.data().ifPresent { audioBytes ->
                                val b64 = Base64.getEncoder().encodeToString(audioBytes)
                                session.scope.launch {
                                    ws.send("""{"type": "audio", "data": "$b64"}""")
                                }
                            }
                        }
                    }
                }
            }

            // Input transcription (what the user said)
            content.inputTranscription().ifPresent { transcript ->
                transcript.text().ifPresent { text ->
                    session.scope.launch {
                        ws.send("""{"type": "transcript", "role": "user", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(text))}}""")
                    }
                }
            }

            // Output transcription (what the model said)
            content.outputTranscription().ifPresent { transcript ->
                transcript.text().ifPresent { text ->
                    session.scope.launch {
                        ws.send("""{"type": "transcript", "role": "model", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(text))}}""")
                    }
                }
            }

            if (content.turnComplete().orElse(false)) {
                session.scope.launch { ws.send("""{"type": "turn_complete"}""") }
            }
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

            // Notify client of tool call
            ws.send("""{"type": "tool_call", "name": "$name", "args": ${json.encodeToString(JsonObject.serializer(), buildJsonObject {
                args.forEach { (k, v) -> put(k, JsonPrimitive(v.toString())) }
            })}}""")

            // Convert args to Map<String, Any?>
            val argsMap: Map<String, Any?> = args.mapValues { (_, value) ->
                when (value) {
                    is String -> value
                    is Number -> value
                    is Boolean -> value
                    else -> value.toString()
                }
            }

            // Execute through real game engine
            val result = session.game.executeTool(name, argsMap)

            // Send game events to client
            for (event in result.events) {
                val eventJson = json.encodeToString(GameEvent.serializer(), event)
                ws.send("""{"type": "game_event", "event": $eventJson}""")
            }

            // Send tool result to client
            ws.send("""{"type": "tool_result", "name": "$name", "success": ${result.success}}""")

            // If this was a mutating tool, push state update
            if (result.events.isNotEmpty()) {
                try {
                    val stateSnapshot = session.game.getState()
                    val stateJson = json.encodeToString(GameStateSnapshot.serializer(), stateSnapshot)
                    ws.send("""{"type": "state_update", "state": $stateJson}""")
                } catch (_: Exception) { /* best-effort */ }
            }

            // Handle scene art generation
            if (name == "generate_scene_art") {
                session.scope.launch {
                    try {
                        val description = argsMap["description"]?.toString() ?: ""
                        val state = session.game.getState()
                        val imgResult = session.imageService.generateSceneArt(
                            SceneArtRequest(
                                locationName = state.location,
                                description = description
                            )
                        )
                        if (imgResult is ImageResult.Success) {
                            val b64 = Base64.getEncoder().encodeToString(imgResult.imageData)
                            ws.send("""{"type": "scene_image", "data": "$b64", "mimeType": "${imgResult.mimeType}"}""")
                        }
                    } catch (_: Exception) { /* best-effort image gen */ }
                }
            }

            // Build response for Gemini
            val resultData = buildJsonObject {
                put("success", JsonPrimitive(result.success))
                result.data.let { put("data", it) }
                result.error?.let { put("error", JsonPrimitive(it)) }
            }

            responses.add(
                FunctionResponse.builder()
                    .id(id)
                    .name(name)
                    .response(mapOf<String, Any>("result" to resultData.toString()))
                    .build()
            )
        }

        // Send results back to Gemini
        if (responses.isNotEmpty()) {
            val params = LiveSendToolResponseParameters.builder()
                .functionResponses(responses)
                .build()
            session.geminiSession?.sendToolResponse(params)
        }
    }
}
