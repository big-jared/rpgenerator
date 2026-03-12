package com.rpgenerator.server

import com.google.genai.Client
import com.google.genai.types.*
import com.google.genai.AsyncSession as GeminiAsyncSession
import com.google.genai.types.VoiceConfig as GeminiVoiceConfig
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.Base64

/**
 * WebSocket handler for the receptionist onboarding flow.
 *
 * Unlike GameWebSocketHandler, this has no game engine — it connects directly
 * to Gemini Live with 4 local tools for collecting onboarding info:
 *   set_player_name, set_backstory, select_world, finish_onboarding
 *
 * Each WebSocket connection gets its own [ReceptionistSession] so concurrent
 * users don't share state.
 *
 * Protocol (client -> server):
 *   {"type": "connect", "voiceName": "Kore", "prompt": "<system prompt>"}
 *   {"type": "audio", "data": "<base64 PCM 16kHz>"}
 *   {"type": "text", "content": "..."}
 *   {"type": "disconnect"}
 *
 * Protocol (server -> client):
 *   {"type": "audio", "data": "<base64 PCM 24kHz>"}
 *   {"type": "text", "content": "..."}
 *   {"type": "transcript", "role": "user|model", "content": "..."}
 *   {"type": "tool_call", "name": "...", "args": {...}}
 *   {"type": "tool_result", "name": "...", "result": {...}}
 *   {"type": "turn_complete"}
 *   {"type": "interrupted"}
 *   {"type": "onboarding_complete", "seedId": "...", "playerName": "...", "backstory": "..."}
 *   {"type": "error", "message": "..."}
 *   {"type": "connected"}
 */
object ReceptionistWebSocketHandler {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val geminiClient: Client by lazy { Client() }
    private const val MODEL_ID = "gemini-2.5-flash-native-audio-preview-12-2025"

    // Tool declarations for the onboarding flow (shared, immutable)
    private val toolDeclarations = listOf(
        FunctionDeclaration.builder()
            .name("set_player_name")
            .description("Set the player's character name once they have chosen one.")
            .parameters(
                Schema.builder()
                    .type("OBJECT")
                    .properties(mapOf(
                        "name" to Schema.builder()
                            .type("STRING")
                            .description("The player's chosen character name")
                            .build()
                    ))
                    .required(listOf("name"))
                    .build()
            )
            .build(),
        FunctionDeclaration.builder()
            .name("set_backstory")
            .description("Set the player's backstory once they have described it or confirmed one.")
            .parameters(
                Schema.builder()
                    .type("OBJECT")
                    .properties(mapOf(
                        "backstory" to Schema.builder()
                            .type("STRING")
                            .description("A short backstory for the player's character")
                            .build()
                    ))
                    .required(listOf("backstory"))
                    .build()
            )
            .build(),
        FunctionDeclaration.builder()
            .name("select_world")
            .description("Select which world/adventure the player wants to play.")
            .parameters(
                Schema.builder()
                    .type("OBJECT")
                    .properties(mapOf(
                        "seed_id" to Schema.builder()
                            .type("STRING")
                            .description("The world seed ID: integration, tabletop, crawler, or quiet_life")
                            .build()
                    ))
                    .required(listOf("seed_id"))
                    .build()
            )
            .build(),
        FunctionDeclaration.builder()
            .name("finish_onboarding")
            .description("Complete the onboarding process. Call this once the player has a name, backstory, and world selected.")
            .parameters(
                Schema.builder()
                    .type("OBJECT")
                    .properties(emptyMap())
                    .build()
            )
            .build()
    )

    /** Per-connection mutable state. */
    private class ReceptionistSession {
        @Volatile var toolCallPending = false
        @Volatile var audioMuted = false
        @Volatile var seedId: String? = null
        @Volatile var playerName: String? = null
        @Volatile var backstory: String? = null
        @Volatile var onboardingDone = false
        var geminiSession: GeminiAsyncSession? = null
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        /** True if we have enough data to proceed even without finish_onboarding. */
        fun hasAllData() = playerName != null

        /** Number of turns completed — used to auto-complete after enough conversation. */
        var turnCount = 0
        var audioChunkCount = 0

        fun disconnect() {
            geminiSession?.close()
            geminiSession = null
        }
    }

    suspend fun handle(ws: DefaultWebSocketServerSession) {
        val session = ReceptionistSession()

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
        }
    }

    private suspend fun handleConnect(
        ws: DefaultWebSocketServerSession,
        session: ReceptionistSession,
        msg: JsonObject
    ) {
        try {
            val voiceName = msg["voiceName"]?.jsonPrimitive?.content ?: "Kore"
            val prompt = msg["prompt"]?.jsonPrimitive?.content
                ?: error("Missing 'prompt' in connect message")

            val config = LiveConnectConfig.builder()
                .responseModalities(Modality.Known.AUDIO)
                .systemInstruction(
                    Content.builder()
                        .parts(listOf(Part.builder().text(prompt).build()))
                        .build()
                )
                .tools(listOf(
                    Tool.builder()
                        .functionDeclarations(toolDeclarations)
                        .build()
                ))
                .speechConfig(
                    SpeechConfig.builder()
                        .voiceConfig(
                            GeminiVoiceConfig.builder()
                                .prebuiltVoiceConfig(
                                    PrebuiltVoiceConfig.builder()
                                        .voiceName(voiceName)
                                )
                        )
                )
                .inputAudioTranscription(AudioTranscriptionConfig.builder().build())
                .outputAudioTranscription(AudioTranscriptionConfig.builder().build())
                .build()

            session.geminiSession = geminiClient.async.live.connect(MODEL_ID, config).get()

            session.geminiSession!!.receive { message ->
                session.scope.launch {
                    handleGeminiMessage(ws, session, message)
                }
            }

            ws.send("""{"type": "connected"}""")
        } catch (e: Exception) {
            ws.send("""{"type": "error", "message": "${e.message?.replace("\"", "\\\"")}"}""")
        }
    }

    private suspend fun handleAudio(session: ReceptionistSession, msg: JsonObject) {
        val gemini = session.geminiSession ?: return
        val b64 = msg["data"]?.jsonPrimitive?.content ?: return
        val pcm = Base64.getDecoder().decode(b64)

        val params = LiveSendRealtimeInputParameters.builder()
            .media(Blob.builder().mimeType("audio/pcm").data(pcm))
            .build()
        gemini.sendRealtimeInput(params)
    }

    private suspend fun handleText(session: ReceptionistSession, msg: JsonObject) {
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
        session: ReceptionistSession,
        message: LiveServerMessage
    ) {
        // Handle tool calls — gate audio IMMEDIATELY before launching handler
        message.toolCall().ifPresent { toolCall ->
            session.toolCallPending = true
            session.scope.launch {
                try {
                    handleToolCall(ws, session, toolCall)
                } finally {
                    session.toolCallPending = false
                }
            }
        }

        // Handle server content (text + audio)
        message.serverContent().ifPresent { content ->
            if (content.interrupted().orElse(false)) {
                session.audioMuted = true
                session.scope.launch { ws.send("""{"type": "interrupted"}""") }
                return@ifPresent
            }

            content.modelTurn().ifPresent { modelTurn ->
                // New model turn — unmute audio (model is responding fresh)
                session.audioMuted = false
                modelTurn.parts().ifPresent { parts ->
                    for (part in parts) {
                        val hasText = part.text().isPresent
                        val hasInline = part.inlineData().isPresent
                        if (hasText || hasInline) {
                            session.audioChunkCount++
                            if (session.audioChunkCount <= 3 || session.audioChunkCount % 100 == 0) {
                                println("ReceptionistWSHandler: part #${session.audioChunkCount} hasText=$hasText hasInline=$hasInline")
                            }
                        }

                        // Text
                        part.text().ifPresent { text ->
                            session.scope.launch {
                                ws.send("""{"type": "text", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(text))}}""")
                            }
                        }

                        // Audio — base64 JSON (drop if muted from interruption)
                        if (!session.audioMuted) {
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
            }

            // Input transcription (what the user said)
            content.inputTranscription().ifPresent { transcript ->
                transcript.text().ifPresent { text ->
                    session.scope.launch {
                        ws.send("""{"type": "transcript", "role": "user", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(text))}}""")
                    }
                    // Detect world selection from user speech if model didn't call select_world
                    if (session.seedId == null) {
                        val lower = text.lowercase()
                        when {
                            "integration" in lower || "system" in lower || "apocalypse" in lower -> session.seedId = "integration"
                            "fantasy" in lower || "tabletop" in lower || "sword" in lower -> session.seedId = "tabletop"
                            "crawler" in lower || "game show" in lower || "tv" in lower -> session.seedId = "crawler"
                            "quiet" in lower || "farming" in lower || "peaceful" in lower -> session.seedId = "quiet_life"
                        }
                        if (session.seedId != null) {
                            println("ReceptionistWSHandler: Detected seed from user speech: ${session.seedId}")
                        }
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
                session.turnCount++
                session.scope.launch {
                    ws.send("""{"type": "turn_complete"}""")
                    // Fallback: if model collected all data but never called finish_onboarding,
                    // emit onboarding_complete so the client can proceed.
                    // After turn 4+, if we have at least a name, auto-complete with defaults.
                    if (!session.onboardingDone && session.hasAllData() && session.turnCount >= 4) {
                        println("ReceptionistWSHandler: Auto-completing onboarding after turn ${session.turnCount} (name=${session.playerName}, seed=${session.seedId ?: "integration"})")
                        session.onboardingDone = true
                        val completeMsg = buildJsonObject {
                            put("type", JsonPrimitive("onboarding_complete"))
                            put("seedId", JsonPrimitive(session.seedId ?: "integration"))
                            put("playerName", JsonPrimitive(session.playerName ?: "Adventurer"))
                            put("backstory", JsonPrimitive(session.backstory ?: ""))
                        }
                        ws.send(json.encodeToString(JsonObject.serializer(), completeMsg))
                    }
                }
            }
        }
    }

    private suspend fun handleToolCall(
        ws: DefaultWebSocketServerSession,
        session: ReceptionistSession,
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

            // Handle tools locally — no game engine needed
            val result = when (name) {
                "set_player_name" -> {
                    session.playerName = args["name"]?.toString()
                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("data", JsonPrimitive("Player name set to: ${session.playerName}"))
                    }
                }
                "set_backstory" -> {
                    session.backstory = args["backstory"]?.toString()
                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("data", JsonPrimitive("Backstory recorded."))
                    }
                }
                "select_world" -> {
                    session.seedId = args["seed_id"]?.toString()
                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("data", JsonPrimitive("World selected: ${session.seedId}. Now give the player their companion assignment and farewell, then call finish_onboarding."))
                    }
                }
                "finish_onboarding" -> {
                    // Mark that onboarding is done — emit to client after farewell audio plays
                    session.onboardingDone = true
                    // Send onboarding_complete to client
                    val completeMsg = buildJsonObject {
                        put("type", JsonPrimitive("onboarding_complete"))
                        put("seedId", JsonPrimitive(session.seedId ?: ""))
                        put("playerName", JsonPrimitive(session.playerName ?: ""))
                        put("backstory", JsonPrimitive(session.backstory ?: ""))
                    }
                    ws.send(json.encodeToString(JsonObject.serializer(), completeMsg))

                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("data", JsonPrimitive("Onboarding complete. Transitioning to game."))
                    }
                }
                else -> {
                    buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Unknown tool: $name"))
                    }
                }
            }

            // Send tool result to client
            ws.send("""{"type": "tool_result", "name": "$name", "result": ${json.encodeToString(JsonObject.serializer(), result)}}""")

            // Build response for Gemini
            responses.add(
                FunctionResponse.builder()
                    .id(id)
                    .name(name)
                    .response(mapOf<String, Any>("result" to result.toString()))
                    .build()
            )
        }

        // Send results back to Gemini so it can continue speaking
        if (responses.isNotEmpty()) {
            session.geminiSession?.sendToolResponse(
                LiveSendToolResponseParameters.builder()
                    .functionResponses(responses)
                    .build()
            )
        }
    }
}
