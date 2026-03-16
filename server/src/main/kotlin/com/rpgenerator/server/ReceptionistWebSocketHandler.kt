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
import kotlinx.coroutines.channels.Channel
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
 *   Binary frame: raw PCM 16kHz 16-bit mono
 *   {"type": "text", "content": "..."}
 *   {"type": "disconnect"}
 *
 * Protocol (server -> client):
 *   Binary frame: raw PCM 24kHz 16-bit mono (audio)
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
    private val MODEL_ID get() = GameSessionManager.GEMINI_LIVE_MODEL

    // Tool declarations for the onboarding flow (shared, immutable)
    private val toolDeclarations = listOf(
        FunctionDeclaration.builder()
            .name("set_player_name")
            .description("Set the player's character name once they have chosen one.")
            .parameters(
                Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(mapOf(
                        "name" to Schema.builder()
                            .type(Type.Known.STRING)
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
                    .type(Type.Known.OBJECT)
                    .properties(mapOf(
                        "backstory" to Schema.builder()
                            .type(Type.Known.STRING)
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
                    .type(Type.Known.OBJECT)
                    .properties(mapOf(
                        "seed_id" to Schema.builder()
                            .type(Type.Known.STRING)
                            .description("The world seed ID: integration, tabletop, crawler, or quiet_life")
                            .build()
                    ))
                    .required(listOf("seed_id"))
                    .build()
            )
            .build(),
        FunctionDeclaration.builder()
            .name("set_appearance")
            .description("Set the player's physical appearance for their portrait. Call after they describe what they look like.")
            .parameters(
                Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(mapOf(
                        "description" to Schema.builder()
                            .type(Type.Known.STRING)
                            .description("A short visual description of the player's appearance, e.g. 'mid-30s man, short brown hair, muscular build, beard'")
                            .build()
                    ))
                    .required(listOf("description"))
                    .build()
            )
            .build(),
        FunctionDeclaration.builder()
            .name("finish_onboarding")
            .description("Complete the onboarding process. Call this once the player has a name, backstory, appearance, and world selected.")
            // No .parameters() — SDK docs: "For function with no parameters, this can be left unset."
            .build()
    )

    /** Per-connection mutable state. */
    private class ReceptionistSession {
        @Volatile var toolCallPending = false
        @Volatile var seedId: String? = null
        @Volatile var playerName: String? = null
        @Volatile var backstory: String? = null
        @Volatile var appearance: String? = null
        @Volatile var onboardingDone = false
        var geminiSession: GeminiAsyncSession? = null
        var lyriaMusicService: LyriaMusicService? = null
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        /** Track whether the MODEL actually called each tool (vs fallback extraction). */
        var modelCalledSetName = false
        var modelCalledSetBackstory = false
        var modelCalledSelectWorld = false

        /** Collect user speech per turn to extract name/backstory if model doesn't call tools. */
        val userSpeechPerTurn = mutableListOf<String>()
        val currentTurnUserSpeech = StringBuilder()

        /** Number of turns completed — used to auto-complete after enough conversation. */
        var turnCount = 0
        var audioChunkCount = 0      // cumulative debug counter
        var turnAudioChunks = 0      // per-turn counter, reset on turnComplete

        fun disconnect() {
            geminiSession?.close()
            geminiSession = null
            lyriaMusicService?.close()
            lyriaMusicService = null
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
                        try {
                            gemini.sendRealtimeInput(params)
                        } catch (e: Exception) {
                            session.toolCallPending = true
                            println("ReceptionistWSHandler: sendRealtimeInput rejected (binary), gating input: ${e.message}")
                        }
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

            val toolNames = toolDeclarations.mapNotNull { it.name().orElse(null) }
            println("ReceptionistWSHandler: Connecting to Gemini Live with ${toolDeclarations.size} tools: $toolNames, model=$MODEL_ID")
            println("ReceptionistWSHandler: System prompt (${prompt.length} chars): ${prompt.take(200)}...")

            session.geminiSession = geminiClient.async.live.connect(MODEL_ID, config).get()
            println("ReceptionistWSHandler: Gemini Live session connected successfully")

            // Serialize all Gemini callbacks through a channel to preserve message ordering.
            // The SDK dispatches receive callbacks across a thread pool — without this,
            // audio chunks interleave out of order and produce garbled playback.
            val messageChannel = Channel<LiveServerMessage>(Channel.UNLIMITED)

            session.scope.launch {
                for (msg in messageChannel) {
                    handleGeminiMessage(ws, session, msg)
                }
            }

            session.geminiSession!!.receive { message ->
                // Only gate on tool calls — Gemini rejects input during tool execution
                if (message.toolCall().isPresent) {
                    session.toolCallPending = true
                }
                messageChannel.trySend(message)
            }

            // Start Lyria music for onboarding ambiance
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
                    music.setMood("mysterious") // Onboarding mood
                } catch (e: Exception) {
                    println("ReceptionistWSHandler: Lyria music failed: ${e.message}")
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
        try {
            gemini.sendRealtimeInput(params)
        } catch (e: Exception) {
            session.toolCallPending = true
            println("ReceptionistWSHandler: sendRealtimeInput rejected (json audio), gating input: ${e.message}")
        }
    }

    private suspend fun handleText(session: ReceptionistSession, msg: JsonObject) {
        val gemini = session.geminiSession ?: return
        val text = msg["content"]?.jsonPrimitive?.content ?: return
        println("ReceptionistWSHandler: handleText: ${text.take(80)}")

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
        // Log what Gemini sent — helps debug tool call issues
        val hasToolCall = message.toolCall().isPresent
        val hasContent = message.serverContent().isPresent
        val hasTurnComplete = message.serverContent().orElse(null)?.turnComplete()?.orElse(false) ?: false
        if (hasToolCall || hasTurnComplete) {
            println("ReceptionistWSHandler: Gemini message — toolCall=$hasToolCall content=$hasContent turnComplete=$hasTurnComplete")
        }

        // Handle tool calls — block this serial processor until tool call completes
        val toolCall = message.toolCall().orElse(null)
        if (toolCall != null) {
            val funcNames = toolCall.functionCalls().orElse(emptyList()).mapNotNull { it.name().orElse(null) }
            println("ReceptionistWSHandler: TOOL CALL received: $funcNames")
            session.toolCallPending = true
            try {
                handleToolCall(ws, session, toolCall)
                println("ReceptionistWSHandler: Tool call complete, toolCallPending=false, resuming message processing")
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
                // Text — filter out Gemini thinking/reasoning (starts with **) and control tokens
                val text = part.text().orElse(null)
                if (text != null) {
                    val cleaned = text.replace(Regex("<ctrl\\d+>"), "").trim()
                    if (cleaned.isNotEmpty() && !cleaned.startsWith("**")) {
                        ws.send("""{"type": "text", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(cleaned))}}""")
                    }
                }

                // Audio — send as raw binary frame (PCM 24kHz 16-bit mono)
                val audioBytes = part.inlineData().orElse(null)?.data()?.orElse(null)
                if (audioBytes != null) {
                    session.turnAudioChunks++
                    ws.send(Frame.Binary(true, audioBytes))
                }
            }
        }

        // Input transcription (what the user said)
        val inputText = content.inputTranscription().orElse(null)?.text()?.orElse(null)
        if (inputText != null) {
            ws.send("""{"type": "transcript", "role": "user", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(inputText))}}""")
            // Accumulate user speech for this turn
            session.currentTurnUserSpeech.append(inputText)
            // Detect world selection from user speech if model didn't call select_world
            if (session.seedId == null) {
                val lower = inputText.lowercase()
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

        // Output transcription (what the model said)
        val outputText = content.outputTranscription().orElse(null)?.text()?.orElse(null)
        if (outputText != null) {
            val cleaned = outputText.replace(Regex("<ctrl\\d+>"), "").trim()
            if (cleaned.isNotEmpty()) {
                ws.send("""{"type": "transcript", "role": "model", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(cleaned))}}""")
            }
        }

        if (content.turnComplete().orElse(false)) {
            // Save user speech from this turn
            val userSpeech = session.currentTurnUserSpeech.toString().trim()
            if (userSpeech.isNotEmpty()) {
                session.userSpeechPerTurn.add(userSpeech)
                session.currentTurnUserSpeech.clear()
            }

            session.turnCount++
            val turnAudio = session.turnAudioChunks
            session.turnAudioChunks = 0
            println("ReceptionistWSHandler: Turn ${session.turnCount} complete, $turnAudio audio chunks this turn, userSpeech=${session.userSpeechPerTurn}")

            // Extract name from first user response if model never called set_player_name
            if (session.playerName == null && session.userSpeechPerTurn.isNotEmpty()) {
                val firstResponse = session.userSpeechPerTurn[0].trim()
                    .replace(Regex("[.!?,;:]"), "")  // strip punctuation
                    .split("\\s+".toRegex())
                    .filter { it.length > 1 }  // drop single chars
                val nameWords = firstResponse.filter { it[0].isUpperCase() || firstResponse.size <= 2 }
                if (nameWords.isNotEmpty()) {
                    session.playerName = nameWords.takeLast(2).joinToString(" ")
                    println("ReceptionistWSHandler: Extracted player name from speech: ${session.playerName}")
                }
            }

            // Extract backstory from second user response if model never called set_backstory
            if (session.backstory == null && session.userSpeechPerTurn.size >= 2) {
                session.backstory = session.userSpeechPerTurn.drop(1)
                    .joinToString(" ")
                    .take(500)
                println("ReceptionistWSHandler: Extracted backstory from speech: ${session.backstory?.take(80)}...")
            }

            ws.send("""{"type": "turn_complete"}""")
            // Safety-net auto-complete after turn 10+
            if (!session.onboardingDone && session.turnCount >= 10) {
                val name = session.playerName ?: "Adventurer"
                val seed = session.seedId ?: "integration"
                val story = session.backstory ?: ""
                println("ReceptionistWSHandler: Auto-completing onboarding after turn ${session.turnCount} (name=$name, seed=$seed)")
                session.onboardingDone = true
                val completeMsg = buildJsonObject {
                    put("type", JsonPrimitive("onboarding_complete"))
                    put("seedId", JsonPrimitive(seed))
                    put("playerName", JsonPrimitive(name))
                    put("backstory", JsonPrimitive(story))
                }
                ws.send(json.encodeToString(JsonObject.serializer(), completeMsg))
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
                    session.modelCalledSetName = true
                    session.playerName = args["name"]?.toString()
                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("data", JsonPrimitive("Player name set to: ${session.playerName}"))
                    }
                }
                "set_backstory" -> {
                    session.modelCalledSetBackstory = true
                    session.backstory = args["backstory"]?.toString()
                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("data", JsonPrimitive("Backstory recorded."))
                    }
                }
                "set_appearance" -> {
                    session.appearance = args["description"]?.toString()
                    println("ReceptionistWSHandler: Appearance: ${session.appearance}")
                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("data", JsonPrimitive("Appearance recorded."))
                    }
                }
                "select_world" -> {
                    session.modelCalledSelectWorld = true
                    session.seedId = args["seed_id"]?.toString()
                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("data", JsonPrimitive("World selected: ${session.seedId}. Now give the player their companion assignment and farewell, then call finish_onboarding."))
                    }
                }
                "finish_onboarding" -> {
                    session.onboardingDone = true
                    val completeMsg = buildJsonObject {
                        put("type", JsonPrimitive("onboarding_complete"))
                        put("seedId", JsonPrimitive(session.seedId ?: ""))
                        put("playerName", JsonPrimitive(session.playerName ?: ""))
                        put("backstory", JsonPrimitive(session.backstory ?: ""))
                        put("portraitDescription", JsonPrimitive(session.appearance ?: ""))
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
