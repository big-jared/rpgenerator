package com.rpgenerator.server

import com.rpgenerator.core.gemini.GeminiToolCall
import com.rpgenerator.core.gemini.ToolResult
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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

            // Execute tool
            val jsonArgs = buildJsonObject {
                args.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, JsonPrimitive(value))
                        is Number -> put(key, JsonPrimitive(value))
                        is Boolean -> put(key, JsonPrimitive(value))
                        else -> put(key, JsonPrimitive(value.toString()))
                    }
                }
            }

            val toolCallObj = GeminiToolCall(id = id, name = name, arguments = jsonArgs)
            val result = session.tools.dispatch(toolCallObj)

            // Notify client of result
            ws.send("""{"type": "tool_result", "name": "$name", "success": ${result.success}}""")

            responses.add(
                FunctionResponse.builder()
                    .id(id)
                    .name(name)
                    .response(mapOf<String, Any>("result" to resultToJsonString(result)))
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

    private fun resultToJsonString(result: ToolResult): String {
        val obj = buildJsonObject {
            put("success", JsonPrimitive(result.success))
            result.data?.let { put("data", it) }
            result.error?.let { put("error", JsonPrimitive(it)) }
        }
        return obj.toString()
    }
}
