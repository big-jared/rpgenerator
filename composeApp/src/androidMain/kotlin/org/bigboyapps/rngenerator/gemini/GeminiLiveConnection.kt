package org.bigboyapps.rngenerator.gemini

import co.touchlab.kermit.Logger
import com.google.genai.AsyncSession
import com.google.genai.Client
import com.google.genai.types.*
import com.google.genai.types.VoiceConfig as GeminiVoiceConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.bigboyapps.rngenerator.network.GameApiClient
import org.bigboyapps.rngenerator.network.GameWebSocketClient
import org.bigboyapps.rngenerator.network.ServerMessage
import org.bigboyapps.rngenerator.network.ToolDefinitionDto
import org.bigboyapps.rngenerator.audio.AudioRecorder
import org.bigboyapps.rngenerator.audio.AudioPlayer
import org.bigboyapps.rngenerator.ui.GameConnection
import org.bigboyapps.rngenerator.ui.OnboardingConnection
import org.bigboyapps.rngenerator.ui.OnboardingResult

private val log = Logger.withTag("GeminiLiveConnection")
private const val MODEL_ID = "gemini-2.5-flash-native-audio-preview-12-2025"

/**
 * GameConnection that connects directly to Gemini Live API from Android.
 * Supports two modes:
 * 1. Receptionist session (pre-game) — onboarding tools handled locally
 * 2. Game session — tool calls routed to server via HTTP REST
 */
class GeminiLiveConnection(
    private val apiClient: GameApiClient,
    private val apiKey: String
) : OnboardingConnection {

    private var session: AsyncSession? = null
    private var client: Client? = null
    private var sessionId: String? = null
    private var isReceptionistMode = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Receptionist collects these via tool calls
    private val _receptionistResult = MutableStateFlow(OnboardingResult())
    val receptionistResult: StateFlow<OnboardingResult> = _receptionistResult.asStateFlow()

    // Emitted when finish_onboarding is called
    private val _onboardingComplete = MutableSharedFlow<OnboardingResult>(extraBufferCapacity = 1)
    override val onboardingComplete: SharedFlow<OnboardingResult> = _onboardingComplete.asSharedFlow()

    private val audioRecorder = AudioRecorder()
    private val audioPlayer = AudioPlayer()
    private val json = Json { ignoreUnknownKeys = true }

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    override val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(GameWebSocketClient.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<GameWebSocketClient.ConnectionState> = _connectionState.asStateFlow()

    // ── Receptionist Session (Pre-Game) ───────────────────────────

    /**
     * Start a Receptionist session for game type selection and character creation.
     * No server game is created yet. Tools are handled locally.
     */
    override fun startReceptionistSession(prompt: String) {
        isReceptionistMode = true
        _connectionState.value = GameWebSocketClient.ConnectionState.CONNECTING

        scope.launch {
            try {
                val genaiClient = Client.builder().apiKey(apiKey).build()
                client = genaiClient

                val config = buildLiveConnectConfig(
                    systemPrompt = prompt,
                    toolDefs = receptionistToolDefs(),
                    voiceName = "Kore" // Receptionist gets a neutral voice
                )

                val asyncSession = genaiClient.async.live.connect(MODEL_ID, config).get()
                session = asyncSession

                asyncSession.receive { message ->
                    scope.launch { handleGeminiMessage(message) }
                }

                // Pipe audio
                scope.launch {
                    audioRecorder.audioChunks.collect { pcmData ->
                        val params = LiveSendRealtimeInputParameters.builder()
                            .media(Blob.builder().mimeType("audio/pcm").data(pcmData))
                            .build()
                        session?.sendRealtimeInput(params)
                    }
                }

                _connectionState.value = GameWebSocketClient.ConnectionState.CONNECTED
                _messages.emit(ServerMessage.Connected)
                log.i { "Receptionist session connected" }

            } catch (e: Exception) {
                log.e(e) { "Failed to connect Receptionist session" }
                _connectionState.value = GameWebSocketClient.ConnectionState.DISCONNECTED
                _messages.emit(ServerMessage.Error("Receptionist connection failed: ${e.message}"))
            }
        }
    }

    // ── Game Session ──────────────────────────────────────────────

    override fun startSession(serverUrl: String, sessionId: String) {
        this.sessionId = sessionId
        isReceptionistMode = false
        _connectionState.value = GameWebSocketClient.ConnectionState.CONNECTING

        scope.launch {
            try {
                val setup = apiClient.getGameSetup(sessionId)
                log.d { "Got setup: ${setup.toolDefinitions.size} tools, prompt ${setup.systemPrompt.length} chars" }

                // Reuse existing client or create new one
                val genaiClient = client ?: Client.builder().apiKey(apiKey).build()
                client = genaiClient

                val config = buildLiveConnectConfig(
                    systemPrompt = setup.systemPrompt,
                    toolDefs = setup.toolDefinitions,
                    voiceName = setup.voiceName ?: "Kore"
                )

                val asyncSession = genaiClient.async.live.connect(MODEL_ID, config).get()
                session = asyncSession

                asyncSession.receive { message ->
                    scope.launch { handleGeminiMessage(message) }
                }

                scope.launch {
                    audioRecorder.audioChunks.collect { pcmData ->
                        val params = LiveSendRealtimeInputParameters.builder()
                            .media(Blob.builder().mimeType("audio/pcm").data(pcmData))
                            .build()
                        session?.sendRealtimeInput(params)
                    }
                }

                _connectionState.value = GameWebSocketClient.ConnectionState.CONNECTED
                _messages.emit(ServerMessage.Connected)
                log.i { "Game session connected (sessionId=$sessionId)" }

            } catch (e: Exception) {
                log.e(e) { "Failed to connect to Gemini Live" }
                _connectionState.value = GameWebSocketClient.ConnectionState.DISCONNECTED
                _messages.emit(ServerMessage.Error("Gemini connection failed: ${e.message}"))
            }
        }
    }

    /**
     * Disconnect current Gemini session without destroying the connection object.
     * Used to transition from Receptionist → Game session.
     */
    override fun disconnectSession() {
        audioRecorder.stop()
        session?.close()
        session = null
        _connectionState.value = GameWebSocketClient.ConnectionState.DISCONNECTED
    }

    override suspend fun sendConnect(voiceName: String) {
        connectionState.first { it == GameWebSocketClient.ConnectionState.CONNECTED }
    }

    override suspend fun sendText(text: String) {
        val s = session ?: return
        val params = LiveSendClientContentParameters.builder()
            .turnComplete(true)
            .turns(
                Content.builder()
                    .role("user")
                    .parts(listOf(Part.builder().text(text).build()))
                    .build()
            )
            .build()
        s.sendClientContent(params)
    }

    override fun startRecording() { audioRecorder.start() }
    override fun stopRecording() { audioRecorder.stop() }
    override fun enqueueAudio(pcmData: ByteArray) { audioPlayer.enqueue(pcmData) }
    override fun clearAudio() { audioPlayer.clear() }
    override fun releaseAudio() { audioPlayer.release() }

    override fun close() {
        audioRecorder.stop()
        audioPlayer.release()
        session?.close()
        session = null
        client = null
        scope.cancel()
        _connectionState.value = GameWebSocketClient.ConnectionState.DISCONNECTED
    }

    // ── Gemini Config ────────────────────────────────────────────

    private fun buildLiveConnectConfig(
        systemPrompt: String,
        toolDefs: List<ToolDefinitionDto>,
        voiceName: String = "Kore"
    ): LiveConnectConfig {
        val toolDeclarations = toolDefs.map { def ->
            FunctionDeclaration.builder()
                .name(def.name)
                .description(def.description)
                .parameters(
                    Schema.builder()
                        .type("OBJECT")
                        .properties(def.parameters.associate { param ->
                            param.name to Schema.builder()
                                .type(param.type.uppercase())
                                .description(param.description)
                                .build()
                        })
                        .required(def.parameters.filter { it.required }.map { it.name })
                        .build()
                )
                .build()
        }

        return LiveConnectConfig.builder()
            .responseModalities(Modality.Known.AUDIO)
            .systemInstruction(
                Content.builder()
                    .parts(listOf(Part.builder().text(systemPrompt).build()))
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
    }

    // ── Receptionist Tool Definitions ─────────────────────────────

    private fun receptionistToolDefs(): List<ToolDefinitionDto> = listOf(
        ToolDefinitionDto(
            name = "select_world",
            description = "Select the game world/type. Options: integration, tabletop, crawler, quiet_life",
            parameters = listOf(
                org.bigboyapps.rngenerator.network.ToolParameterDto("world_id", "string", "World ID: integration, tabletop, crawler, or quiet_life", true)
            )
        ),
        ToolDefinitionDto(
            name = "set_player_name",
            description = "Set the player's character name",
            parameters = listOf(
                org.bigboyapps.rngenerator.network.ToolParameterDto("name", "string", "The character's name", true)
            )
        ),
        ToolDefinitionDto(
            name = "set_backstory",
            description = "Set the player's backstory",
            parameters = listOf(
                org.bigboyapps.rngenerator.network.ToolParameterDto("backstory", "string", "The character's backstory", true)
            )
        ),
        ToolDefinitionDto(
            name = "generate_portrait",
            description = "Generate a character portrait image from a description",
            parameters = listOf(
                org.bigboyapps.rngenerator.network.ToolParameterDto("description", "string", "Visual description of the character for image generation", true)
            )
        ),
        ToolDefinitionDto(
            name = "finish_onboarding",
            description = "Complete onboarding and start the game. Call this after world selection, character name, and backstory are set.",
            parameters = emptyList()
        )
    )

    // ── Gemini Message Handling ──────────────────────────────────

    private suspend fun handleGeminiMessage(message: LiveServerMessage) {
        // Tool calls
        message.toolCall().ifPresent { toolCall ->
            scope.launch { handleToolCall(toolCall) }
        }

        // Server content (text + audio)
        message.serverContent().ifPresent { content ->
            if (content.interrupted().orElse(false)) {
                _messages.tryEmit(ServerMessage.Interrupted)
                return@ifPresent
            }

            content.modelTurn().ifPresent { modelTurn ->
                modelTurn.parts().ifPresent { parts ->
                    for (part in parts) {
                        part.inlineData().ifPresent { blob ->
                            blob.data().ifPresent { audioBytes ->
                                _messages.tryEmit(ServerMessage.Audio(audioBytes))
                            }
                        }
                        part.text().ifPresent { text ->
                            _messages.tryEmit(ServerMessage.Text(text))
                        }
                    }
                }
            }

            content.inputTranscription().ifPresent { transcript ->
                transcript.text().ifPresent { text ->
                    _messages.tryEmit(ServerMessage.Transcript("user", text))
                }
            }

            content.outputTranscription().ifPresent { transcript ->
                transcript.text().ifPresent { text ->
                    _messages.tryEmit(ServerMessage.Transcript("model", text))
                }
            }

            if (content.turnComplete().orElse(false)) {
                _messages.tryEmit(ServerMessage.TurnComplete)
            }
        }
    }

    private suspend fun handleToolCall(toolCall: LiveServerToolCall) {
        val functionCalls = toolCall.functionCalls().orElse(emptyList())
        val responses = mutableListOf<FunctionResponse>()

        for (fc in functionCalls) {
            val name = fc.name().orElse("")
            val id = fc.id().orElse(name)
            val args = fc.args().orElse(emptyMap())

            val argsJson = buildJsonObject {
                args.forEach { (k, v) -> put(k, JsonPrimitive(v.toString())) }
            }
            _messages.emit(ServerMessage.ToolCall(name, argsJson))

            if (isReceptionistMode) {
                // Handle onboarding tools locally — no server needed
                val result = handleReceptionistTool(name, args)
                responses.add(
                    FunctionResponse.builder()
                        .id(id)
                        .name(name)
                        .response(mapOf<String, Any>("result" to result))
                        .build()
                )
            } else {
                // Route to server
                val sid = sessionId ?: continue
                val stringArgs = args.mapValues { (_, v) -> v.toString() }
                try {
                    val result = apiClient.executeTool(sid, name, stringArgs)

                    for (event in result.events) {
                        _messages.emit(ServerMessage.GameEvent(event))
                    }
                    _messages.emit(ServerMessage.ToolResult(name, result.success))

                    if (result.events.isNotEmpty()) {
                        try {
                            val stateJson = apiClient.getGameState(sid)
                            _messages.emit(ServerMessage.StateUpdate(stateJson))
                        } catch (e: Exception) {
                            log.w(e) { "Failed to fetch state after tool" }
                        }
                    }

                    val resultData = buildJsonObject {
                        put("success", JsonPrimitive(result.success))
                        put("data", result.data)
                        result.error?.let { put("error", JsonPrimitive(it)) }
                    }
                    responses.add(
                        FunctionResponse.builder()
                            .id(id)
                            .name(name)
                            .response(mapOf<String, Any>("result" to resultData.toString()))
                            .build()
                    )
                } catch (e: Exception) {
                    log.e(e) { "Tool execution failed: $name" }
                    responses.add(
                        FunctionResponse.builder()
                            .id(id)
                            .name(name)
                            .response(mapOf<String, Any>("result" to """{"success":false,"error":"${e.message}"}"""))
                            .build()
                    )
                }
            }
        }

        if (responses.isNotEmpty()) {
            val params = LiveSendToolResponseParameters.builder()
                .functionResponses(responses)
                .build()
            session?.sendToolResponse(params)
        }
    }

    // ── Receptionist Tool Handlers (local, no server) ─────────────

    private suspend fun handleReceptionistTool(name: String, args: Map<String, Any>): String {
        return when (name) {
            "select_world" -> {
                val worldId = args["world_id"]?.toString() ?: "integration"
                _receptionistResult.update { it.copy(seedId = worldId) }
                log.i { "Receptionist: world selected → $worldId" }
                """{"success":true,"message":"World set to $worldId"}"""
            }
            "set_player_name" -> {
                val playerName = args["name"]?.toString() ?: "Adventurer"
                _receptionistResult.update { it.copy(playerName = playerName) }
                log.i { "Receptionist: name set → $playerName" }
                """{"success":true,"message":"Name set to $playerName"}"""
            }
            "set_backstory" -> {
                val backstory = args["backstory"]?.toString() ?: ""
                _receptionistResult.update { it.copy(backstory = backstory) }
                log.i { "Receptionist: backstory set" }
                """{"success":true,"message":"Backstory recorded"}"""
            }
            "generate_portrait" -> {
                val desc = args["description"]?.toString() ?: ""
                _receptionistResult.update { it.copy(portraitDescription = desc) }
                log.i { "Receptionist: portrait description set" }
                // TODO: Could call image generation API here and return the image
                """{"success":true,"message":"Portrait description saved. Portrait will be generated when the game starts."}"""
            }
            "finish_onboarding" -> {
                val result = _receptionistResult.value
                log.i { "Receptionist: onboarding complete → seed=${result.seedId}, name=${result.playerName}" }
                _onboardingComplete.tryEmit(result)
                """{"success":true,"message":"Onboarding complete! Transferring to game world..."}"""
            }
            else -> """{"success":false,"error":"Unknown tool: $name"}"""
        }
    }
}
