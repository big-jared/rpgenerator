package org.bigboyapps.rngenerator.network

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * WebSocket client that connects to the game server for Gemini Live API streaming.
 */
class GameWebSocketClient(
    private val serverUrl: String
) {
    private val client = HttpClient {
        install(WebSockets) {
            pingIntervalMillis = 15_000
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
    }

    /**
     * Connect to the game server WebSocket.
     */
    suspend fun connect(sessionId: String, scope: CoroutineScope) {
        disconnect()
        _connectionState.value = ConnectionState.CONNECTING

        connectionJob = scope.launch {
            var retryCount = 0
            val maxRetries = 5

            while (isActive && retryCount <= maxRetries) {
                try {
                    val wsUrl = serverUrl
                        .replace("https://", "wss://")
                        .replace("http://", "ws://")

                    client.webSocket("$wsUrl/ws/game/$sessionId") {
                        session = this
                        _connectionState.value = ConnectionState.CONNECTED
                        retryCount = 0

                        try {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val msg = parseMessage(frame.readText())
                                        if (msg != null) _messages.emit(msg)
                                    }
                                    is Frame.Binary -> {
                                        val data = frame.data
                                        if (data.size > 1) {
                                            when (data[0]) {
                                                0x01.toByte() -> _messages.emit(ServerMessage.Audio(data.copyOfRange(1, data.size)))
                                                0x02.toByte() -> _messages.emit(ServerMessage.MusicAudio(data.copyOfRange(1, data.size)))
                                                else -> _messages.emit(ServerMessage.Audio(data)) // legacy fallback
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _messages.emit(ServerMessage.Error("Connection lost: ${e.message}"))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    retryCount++
                    if (retryCount <= maxRetries) {
                        _connectionState.value = ConnectionState.RECONNECTING
                        delay(minOf(1000L * retryCount, 5000L))
                    }
                }
            }

            session = null
            _connectionState.value = ConnectionState.DISCONNECTED
            _messages.emit(ServerMessage.Disconnected)
        }
    }

    /**
     * Connect to the receptionist WebSocket for onboarding.
     */
    suspend fun connectReceptionist(prompt: String, voiceName: String, scope: CoroutineScope) {
        disconnect()
        _connectionState.value = ConnectionState.CONNECTING

        connectionJob = scope.launch {
            try {
                val wsUrl = serverUrl
                    .replace("https://", "wss://")
                    .replace("http://", "ws://")

                client.webSocket("$wsUrl/ws/receptionist") {
                    session = this
                    _connectionState.value = ConnectionState.CONNECTED

                    // Send connect with prompt
                    val escaped = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                    send("""{"type": "connect", "voiceName": "$voiceName", "prompt": "$escaped"}""")

                    try {
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val msg = parseMessage(frame.readText())
                                    if (msg != null) _messages.emit(msg)
                                }
                                is Frame.Binary -> {
                                    val data = frame.data
                                    if (data.size > 1) {
                                        when (data[0]) {
                                            0x01.toByte() -> _messages.emit(ServerMessage.Audio(data.copyOfRange(1, data.size)))
                                            0x02.toByte() -> _messages.emit(ServerMessage.MusicAudio(data.copyOfRange(1, data.size)))
                                            else -> _messages.emit(ServerMessage.Audio(data))
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _messages.emit(ServerMessage.Error("Connection lost: ${e.message}"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(ServerMessage.Error("Failed to connect: ${e.message}"))
            }

            session = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    /**
     * Send a "connect" message to start the Gemini Live session.
     */
    suspend fun sendConnect(voiceName: String = "Kore") {
        session?.send("""{"type": "connect", "voiceName": "$voiceName"}""")
    }

    /**
     * Send audio data (base64 PCM 16kHz).
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun sendAudio(pcmData: ByteArray) {
        val b64 = Base64.encode(pcmData)
        session?.send("""{"type": "audio", "data": "$b64"}""")
    }

    /**
     * Send text input (keyboard fallback).
     */
    suspend fun sendText(text: String) {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
        session?.send("""{"type": "text", "content": "$escaped"}""")
    }

    /**
     * Disconnect from the server.
     */
    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        session = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun close() {
        disconnect()
        client.close()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parseMessage(text: String): ServerMessage? {
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: return null

            when (type) {
                "audio" -> {
                    val data = obj["data"]?.jsonPrimitive?.content ?: return null
                    ServerMessage.Audio(Base64.decode(data))
                }
                "text" -> {
                    val content = obj["content"]?.jsonPrimitive?.content ?: ""
                    ServerMessage.Text(content)
                }
                "transcript" -> {
                    val role = obj["role"]?.jsonPrimitive?.content ?: "model"
                    val content = obj["content"]?.jsonPrimitive?.content ?: ""
                    ServerMessage.Transcript(role, content)
                }
                "tool_call" -> {
                    val name = obj["name"]?.jsonPrimitive?.content ?: ""
                    val args = obj["args"]?.jsonObject ?: JsonObject(emptyMap())
                    ServerMessage.ToolCall(name, args)
                }
                "tool_result" -> {
                    val name = obj["name"]?.jsonPrimitive?.content ?: ""
                    val success = obj["success"]?.jsonPrimitive?.booleanOrNull ?: false
                    ServerMessage.ToolResult(name, success)
                }
                "game_event" -> {
                    val event = obj["event"]?.jsonObject ?: return null
                    ServerMessage.GameEvent(event)
                }
                "scene_image" -> {
                    val data = obj["data"]?.jsonPrimitive?.content ?: return null
                    val mimeType = obj["mimeType"]?.jsonPrimitive?.content ?: "image/jpeg"
                    ServerMessage.SceneImage(Base64.decode(data), mimeType)
                }
                "state_update" -> {
                    val state = obj["state"]?.jsonObject ?: return null
                    ServerMessage.StateUpdate(state)
                }
                "onboarding_complete" -> {
                    val seedId = obj["seedId"]?.jsonPrimitive?.content ?: "integration"
                    val playerName = obj["playerName"]?.jsonPrimitive?.content ?: "Adventurer"
                    val backstory = obj["backstory"]?.jsonPrimitive?.content ?: ""
                    ServerMessage.OnboardingComplete(seedId, playerName, backstory)
                }
                "turn_complete" -> ServerMessage.TurnComplete
                "connected" -> ServerMessage.Connected
                "interrupted" -> ServerMessage.Interrupted
                "disconnected" -> ServerMessage.Disconnected
                "error" -> {
                    val message = obj["message"]?.jsonPrimitive?.content ?: "Unknown error"
                    ServerMessage.Error(message)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
