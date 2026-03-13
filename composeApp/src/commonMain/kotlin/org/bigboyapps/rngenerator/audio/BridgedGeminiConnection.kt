package org.bigboyapps.rngenerator.audio

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.bigboyapps.rngenerator.network.GameWebSocketClient
import org.bigboyapps.rngenerator.network.ServerMessage
import org.bigboyapps.rngenerator.ui.OnboardingConnection
import org.bigboyapps.rngenerator.ui.OnboardingResult

/**
 * GameConnection backed by NativeGeminiBridge (iOS Swift implementation).
 * Audio and Gemini Live session are proxied through the server — the server
 * handles tool calls directly via Game.executeTool(), eliminating the 1008
 * race condition that occurs with client-side Gemini connections.
 */
class BridgedGeminiConnection : OnboardingConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bridge = NativeGeminiProvider.bridge
        ?: throw IllegalStateException("NativeGeminiProvider.bridge not set")

    private var isReceptionistMode = false
    private val json = Json { ignoreUnknownKeys = true }

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    override val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(GameWebSocketClient.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<GameWebSocketClient.ConnectionState> = _connectionState.asStateFlow()

    private val _onboardingComplete = MutableSharedFlow<OnboardingResult>(extraBufferCapacity = 1)
    override val onboardingComplete: SharedFlow<OnboardingResult> = _onboardingComplete.asSharedFlow()
    // Deferred onboarding result — wait for TurnComplete so farewell audio finishes playing
    private var pendingOnboardingResult: OnboardingResult? = null

    private val callback = object : GeminiMessageCallback {
        override fun onAudio(pcmData: ByteArray) {
            _messages.tryEmit(ServerMessage.Audio(pcmData))
        }
        override fun onTranscript(role: String, text: String) {
            _messages.tryEmit(ServerMessage.Transcript(role, text))
        }
        override fun onText(text: String) {
            _messages.tryEmit(ServerMessage.Text(text))
        }
        override fun onToolCall(id: String, name: String, argsJson: String) {
            val args = try { json.parseToJsonElement(argsJson).jsonObject } catch (_: Exception) { JsonObject(emptyMap()) }
            _messages.tryEmit(ServerMessage.ToolCall(name, args))
        }
        override fun onToolResult(name: String, success: Boolean) {
            _messages.tryEmit(ServerMessage.ToolResult(name, success))
        }
        override fun onGameEvent(eventJson: String) {
            try {
                val eventObj = json.parseToJsonElement(eventJson).jsonObject
                _messages.tryEmit(ServerMessage.GameEvent(eventObj))
            } catch (e: Exception) {
                println("BridgedGeminiConnection: Failed to parse game event: ${e.message}")
            }
        }
        override fun onStateUpdate(stateJson: String) {
            try {
                val stateObj = json.parseToJsonElement(stateJson).jsonObject
                _messages.tryEmit(ServerMessage.StateUpdate(stateObj))
            } catch (e: Exception) {
                println("BridgedGeminiConnection: Failed to parse state update: ${e.message}")
            }
        }
        override fun onSceneImage(imageBase64: String, mimeType: String) {
            try {
                val imageBytes = decodeBase64(imageBase64)
                _messages.tryEmit(ServerMessage.SceneImage(imageBytes, mimeType))
            } catch (e: Exception) {
                println("BridgedGeminiConnection: Failed to decode scene image: ${e.message}")
            }
        }
        override fun onTurnComplete() {
            _messages.tryEmit(ServerMessage.TurnComplete)
            // If onboarding finished, emit now that the farewell audio has played
            pendingOnboardingResult?.let {
                _onboardingComplete.tryEmit(it)
                pendingOnboardingResult = null
            }
        }
        override fun onInterrupted() {
            _messages.tryEmit(ServerMessage.Interrupted)
        }
        override fun onConnected() {
            _connectionState.value = GameWebSocketClient.ConnectionState.CONNECTED
            _messages.tryEmit(ServerMessage.Connected)
        }
        override fun onDisconnected() {
            // If onboarding was pending, this is an expected disconnect — emit result
            pendingOnboardingResult?.let {
                _onboardingComplete.tryEmit(it)
                pendingOnboardingResult = null
                return
            }
            _connectionState.value = GameWebSocketClient.ConnectionState.DISCONNECTED
        }
        override fun onError(message: String) {
            if (pendingOnboardingResult != null) return
            _messages.tryEmit(ServerMessage.Error(message))
        }
        override fun onOnboardingComplete(seedId: String, playerName: String, backstory: String) {
            // Defer until TurnComplete so the receptionist's farewell audio finishes playing
            pendingOnboardingResult = OnboardingResult(seedId = seedId, playerName = playerName, backstory = backstory)
        }
    }

    override fun configure(serverUrl: String) {
        bridge.configure(serverUrl)
    }

    override fun startReceptionistSession(prompt: String, authToken: String?) {
        isReceptionistMode = true
        pendingOnboardingResult = null
        _connectionState.value = GameWebSocketClient.ConnectionState.CONNECTING
        bridge.setAuthToken(authToken ?: "")
        bridge.startReceptionistSession(prompt, "[]", "Kore", callback)
    }

    override fun startSession(serverUrl: String, sessionId: String) {
        isReceptionistMode = false
        _connectionState.value = GameWebSocketClient.ConnectionState.CONNECTING
        // Configure server URL and session ID on the native bridge
        bridge.configure(serverUrl)
        bridge.setSessionId(sessionId)
        // Server handles everything via WebSocket
        bridge.startGameSession(sessionId, "", "Kore", callback)
    }

    override fun disconnectSession() {
        bridge.disconnect()
        _connectionState.value = GameWebSocketClient.ConnectionState.DISCONNECTED
    }

    override suspend fun sendConnect(voiceName: String) {
        connectionState.first { it == GameWebSocketClient.ConnectionState.CONNECTED }
    }

    override suspend fun sendText(text: String) {
        bridge.sendText(text)
    }

    override fun startRecording() { bridge.startRecording() }
    override fun stopRecording() { bridge.stopRecording() }
    override fun enqueueAudio(pcmData: ByteArray) { /* audio played in Swift */ }
    override fun clearAudio() { /* handled in Swift */ }
    override fun releaseAudio() { /* handled in Swift */ }

    override fun close() {
        bridge.close()
        scope.cancel()
        _connectionState.value = GameWebSocketClient.ConnectionState.DISCONNECTED
    }
}

// Simple base64 decode — platform implementations exist but this avoids expect/actual
private fun decodeBase64(input: String): ByteArray {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val stripped = input.filter { it != '\n' && it != '\r' && it != ' ' }
    val output = mutableListOf<Byte>()
    var i = 0
    while (i < stripped.length) {
        val a = chars.indexOf(stripped[i++])
        val b = if (i < stripped.length) chars.indexOf(stripped[i++]) else 0
        val c = if (i < stripped.length) chars.indexOf(stripped[i++]) else 0
        val d = if (i < stripped.length) chars.indexOf(stripped[i++]) else 0
        val triple = (a shl 18) or (b shl 12) or (c shl 6) or d
        output.add((triple shr 16 and 0xFF).toByte())
        if (stripped.getOrNull(i - 2) != '=') output.add((triple shr 8 and 0xFF).toByte())
        if (stripped.getOrNull(i - 1) != '=') output.add((triple and 0xFF).toByte())
    }
    return output.toByteArray()
}
