package com.rpgenerator.server

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Connects to Google's Lyria RealTime music generation API via raw WebSocket.
 * Streams adaptive background music that shifts mood based on gameplay.
 *
 * Audio output: 48kHz stereo 16-bit PCM, delivered as decoded byte arrays
 * via the [onAudioChunk] callback.
 */
class LyriaMusicService(
    private val apiKey: String,
    private val onAudioChunk: (ByteArray) -> Unit
) {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private var webSocket: WebSocket? = null
    @Volatile var connected = false
        private set
    @Volatile private var setupComplete = false
    private var currentMood: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val moodPrompts = mapOf(
        "peaceful" to "gentle ambient fantasy soundtrack, soft flutes and harps, calm and serene, medieval tavern",
        "tense" to "dark suspenseful orchestral music, low strings, building tension, danger approaching",
        "battle" to "intense epic battle music, driving drums, brass fanfare, fast tempo, combat",
        "victory" to "triumphant orchestral fanfare, major key, celebratory horns and strings, heroic",
        "mysterious" to "ethereal mysterious music, haunting choir, ambient pads, curious melody, exploration",
        "dark" to "ominous dark ambient, deep drones, dissonant chords, foreboding, dungeon",
        "epic" to "grand epic orchestral score, soaring strings, powerful brass, heroic adventure theme"
    )

    fun connect() {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateMusic?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                connected = true
                val setup = buildJsonObject {
                    putJsonObject("setup") {
                        put("model", "models/lyria-realtime-exp")
                    }
                }
                ws.send(setup.toString())
                println("LyriaMusicService: WebSocket opened, setup sent")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val msg = json.parseToJsonElement(text).jsonObject

                    if (msg.containsKey("setupComplete")) {
                        setupComplete = true
                        println("LyriaMusicService: Setup complete")
                        currentMood?.let { sendMoodPrompt(it) }
                        return
                    }

                    val serverContent = msg["server_content"]?.jsonObject ?: msg["serverContent"]?.jsonObject
                    if (serverContent != null) {
                        val chunks = serverContent["audioChunks"]?.jsonArray
                            ?: serverContent["audio_chunks"]?.jsonArray
                            ?: run { println("LyriaMusicService: No audioChunks found"); return }
                        for (chunk in chunks) {
                            val b64 = chunk.jsonObject["data"]?.jsonPrimitive?.content ?: continue
                            val pcmBytes = Base64.getDecoder().decode(b64)
                            // Strip WAV header if present (44 bytes starting with "RIFF")
                            val audioData = if (pcmBytes.size > 44 &&
                                pcmBytes[0] == 'R'.code.toByte() &&
                                pcmBytes[1] == 'I'.code.toByte() &&
                                pcmBytes[2] == 'F'.code.toByte() &&
                                pcmBytes[3] == 'F'.code.toByte()
                            ) {
                                pcmBytes.copyOfRange(44, pcmBytes.size)
                            } else {
                                pcmBytes
                            }
                            onAudioChunk(audioData)
                        }
                        return
                    }

                    val filtered = msg["filtered_prompt"]?.jsonObject
                    if (filtered != null) {
                        println("LyriaMusicService: Prompt filtered — ${filtered["filteredReason"]?.jsonPrimitive?.content ?: "unknown"}")
                        return
                    }

                    msg["warning"]?.jsonPrimitive?.content?.let {
                        println("LyriaMusicService: Warning — $it")
                    }

                } catch (e: Exception) {
                    println("LyriaMusicService: Error parsing message — ${e.message}")
                }
            }

            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                onMessage(ws, bytes.utf8())
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                println("LyriaMusicService: onClosing ($code: $reason)")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                println("LyriaMusicService: WebSocket failure — ${t.message}")
                connected = false
                setupComplete = false
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                println("LyriaMusicService: WebSocket closed ($code: $reason)")
                connected = false
                setupComplete = false
            }
        })
    }

    fun setMood(mood: String) {
        if (mood == currentMood) return
        currentMood = mood
        if (setupComplete) {
            sendMoodPrompt(mood)
        }
    }

    private fun sendMoodPrompt(mood: String) {
        val ws = webSocket ?: return
        val promptText = moodPrompts[mood] ?: moodPrompts["peaceful"]!!

        val clientContent = buildJsonObject {
            putJsonObject("client_content") {
                putJsonArray("weightedPrompts") {
                    addJsonObject {
                        put("text", promptText)
                        put("weight", 1.0)
                    }
                }
            }
        }
        ws.send(clientContent.toString())

        val config = buildJsonObject {
            putJsonObject("music_generation_config") {
                put("guidance", 4.0)
                put("density", when (mood) {
                    "battle", "epic" -> 0.8
                    "tense", "dark" -> 0.5
                    "peaceful", "mysterious" -> 0.3
                    else -> 0.5
                })
                put("brightness", when (mood) {
                    "victory", "peaceful" -> 0.7
                    "battle", "epic" -> 0.6
                    "dark", "tense" -> 0.3
                    else -> 0.5
                })
                put("musicGenerationMode", "QUALITY")
            }
        }
        ws.send(config.toString())

        ws.send("""{"playback_control":"PLAY"}""")

        println("LyriaMusicService: Mood set to '$mood' → \"$promptText\"")
    }

    fun pause() {
        webSocket?.send("""{"playback_control":"PAUSE"}""")
    }

    fun resume() {
        webSocket?.send("""{"playback_control":"PLAY"}""")
    }

    fun close() {
        try {
            webSocket?.send("""{"playback_control":"STOP"}""")
            webSocket?.close(1000, "Session ended")
        } catch (_: Exception) { }
        webSocket = null
        connected = false
        setupComplete = false
        scope.cancel()
        println("LyriaMusicService: Closed")
    }
}
