package com.rpgenerator.cli

import com.google.genai.AsyncSession
import com.google.genai.Client
import com.google.genai.types.*
import com.google.genai.types.VoiceConfig as GeminiVoiceConfig
import com.rpgenerator.core.gemini.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.*

/**
 * Real implementation of GeminiGameSession using the google-genai Java SDK.
 * Connects to Gemini Live API via WebSocket for real-time voice + tool calling.
 */
class GeminiLiveSession(
    private val tools: GeminiTools,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : GeminiGameSession {

    private var session: AsyncSession? = null
    private var client: Client? = null

    private var _state: SessionState = SessionState.DISCONNECTED
    override val state: SessionState get() = _state

    private val _outputStream = MutableSharedFlow<GeminiOutput>(extraBufferCapacity = 64)
    override val outputStream: SharedFlow<GeminiOutput> = _outputStream

    override suspend fun connect(config: GeminiSessionConfig) {
        _state = SessionState.CONNECTING

        try {
            val genaiClient = Client()
            client = genaiClient

            val liveConfig = buildLiveConnectConfig(config)
            val asyncSession = genaiClient.async.live.connect(config.modelId, liveConfig).get()
            session = asyncSession

            // Start receiving messages
            asyncSession.receive { message -> handleServerMessage(message) }

            _state = SessionState.CONNECTED
        } catch (e: Exception) {
            _state = SessionState.ERROR
            throw e
        }
    }

    override suspend fun sendAudio(pcmData: ByteArray) {
        val s = session ?: return
        val params = LiveSendRealtimeInputParameters.builder()
            .media(Blob.builder().mimeType("audio/pcm").data(pcmData))
            .build()
        s.sendRealtimeInput(params)
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

    override suspend fun sendToolResults(results: List<Pair<String, ToolResult>>) {
        val s = session ?: return
        val functionResponses = results.map { (callId, result) ->
            FunctionResponse.builder()
                .id(callId)
                .name(callId)
                .response(mapOf<String, Any>("result" to result.toJsonString()))
                .build()
        }

        val params = LiveSendToolResponseParameters.builder()
            .functionResponses(functionResponses)
            .build()
        s.sendToolResponse(params)
    }

    override suspend fun updateContext(context: GameContext) {
        // Live API doesn't support mid-session system prompt updates directly.
        // We send a context update as a client message instead.
        val contextMessage = buildContextUpdateMessage(context)
        sendText(contextMessage)
    }

    override suspend fun disconnect() {
        session?.close()?.get()
        session = null
        client = null
        _state = SessionState.DISCONNECTED
    }

    private fun buildLiveConnectConfig(config: GeminiSessionConfig): LiveConnectConfig {
        val toolDeclarations = config.tools.map { decl ->
            FunctionDeclaration.builder()
                .name(decl.name)
                .description(decl.description)
                .parameters(Schema.builder()
                    .type("OBJECT")
                    .properties(decl.parameters.mapValues { (_, param) ->
                        Schema.builder()
                            .type(param.type.uppercase())
                            .description(param.description)
                            .build()
                    })
                    .required(decl.parameters.filter { it.value.required }.keys.toList())
                    .build()
                )
                .build()
        }

        val builder = LiveConnectConfig.builder()
            .responseModalities(Modality.Known.AUDIO)
            .systemInstruction(
                Content.builder()
                    .parts(listOf(Part.builder().text(config.systemPrompt).build()))
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
                                    .voiceName(config.voiceConfig.voiceName)
                            )
                    )
            )
            .inputAudioTranscription(AudioTranscriptionConfig.builder().build())
            .outputAudioTranscription(AudioTranscriptionConfig.builder().build())

        return builder.build()
    }

    private fun handleServerMessage(message: LiveServerMessage) {
        // Handle tool calls
        message.toolCall().ifPresent { toolCall ->
            scope.launch {
                handleToolCall(toolCall)
            }
        }

        // Handle tool call cancellations
        message.toolCallCancellation().ifPresent { cancellation ->
            // Tool call was cancelled (e.g., user interrupted)
        }

        // Handle server content (text, audio)
        message.serverContent().ifPresent { content ->
            // Check for interruption
            if (content.interrupted().orElse(false)) {
                _outputStream.tryEmit(GeminiOutput.TurnComplete)
                return@ifPresent
            }

            // Process model output parts
            content.modelTurn().ifPresent { modelTurn ->
                modelTurn.parts().ifPresent { parts ->
                    for (part in parts) {
                        // Text output
                        part.text().ifPresent { text ->
                            _outputStream.tryEmit(GeminiOutput.Text(text))
                        }

                        // Audio output
                        part.inlineData().ifPresent { blob ->
                            blob.data().ifPresent { audioBytes ->
                                _outputStream.tryEmit(
                                    GeminiOutput.Audio(pcmData = audioBytes, sampleRate = 24000)
                                )
                            }
                        }
                    }
                }
            }

            // Turn complete
            if (content.turnComplete().orElse(false)) {
                _outputStream.tryEmit(GeminiOutput.TurnComplete)
            }
        }
    }

    private suspend fun handleToolCall(toolCall: LiveServerToolCall) {
        val functionCalls = toolCall.functionCalls().orElse(emptyList())
        val results = mutableListOf<Pair<String, ToolResult>>()

        for (fc in functionCalls) {
            val name = fc.name().orElse("")
            val id = fc.id().orElse(name)
            val args = fc.args().orElse(emptyMap())

            // Convert args to JsonObject for our tool contract
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
            val result = tools.dispatch(toolCallObj)
            results.add(id to result)
        }

        // Send all results back to Gemini
        if (results.isNotEmpty()) {
            sendToolResults(results)
        }
    }

    private fun buildContextUpdateMessage(context: GameContext): String {
        return buildString {
            appendLine("[CONTEXT UPDATE]")
            appendLine("Player: ${context.playerName} (Level ${context.playerLevel})")
            context.playerClass?.let { appendLine("Class: $it") }
            appendLine("Location: ${context.currentLocation}")
            appendLine("Description: ${context.locationDescription}")
            if (context.npcsPresent.isNotEmpty()) {
                appendLine("NPCs here: ${context.npcsPresent.joinToString()}")
            }
            if (context.activeQuests.isNotEmpty()) {
                appendLine("Active quests: ${context.activeQuests.joinToString()}")
            }
            if (context.recentEvents.isNotEmpty()) {
                appendLine("Recent events: ${context.recentEvents.joinToString()}")
            }
        }
    }

    private fun ToolResult.toJsonString(): String {
        val json = buildJsonObject {
            put("success", JsonPrimitive(success))
            data?.let { put("data", it) }
            error?.let { put("error", JsonPrimitive(it)) }
        }
        return json.toString()
    }
}
