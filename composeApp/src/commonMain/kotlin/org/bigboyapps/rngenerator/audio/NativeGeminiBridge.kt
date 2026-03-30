package org.bigboyapps.rngenerator.audio

/**
 * Platform-agnostic Gemini Live API bridge.
 * Swift (iOS) implements this as a WebSocket client to the server.
 * Android uses DirectGameConnection → server WebSocket (server owns Gemini session).
 */
interface GeminiMessageCallback {
    fun onAudio(pcmData: ByteArray)
    fun onTranscript(role: String, text: String)
    fun onText(text: String)
    fun onToolCall(id: String, name: String, argsJson: String)
    fun onToolResult(name: String, success: Boolean)
    fun onGameEvent(eventJson: String)
    fun onStateUpdate(stateJson: String)
    fun onSceneImage(imageBase64: String, mimeType: String)
    fun onTurnComplete()
    fun onInterrupted()
    fun onConnected()
    fun onDisconnected()
    fun onError(message: String)
    fun onOnboardingComplete(seedId: String, playerName: String, backstory: String, portraitDescription: String)
    /** Raw feed/feed_sync JSON from server — parsed on Kotlin side */
    fun onFeedMessage(json: String)
}

interface NativeGeminiBridge {
    fun configure(serverUrl: String)
    fun setSessionId(sessionId: String)
    fun setAuthToken(token: String)
    fun setOpeningNarration(narration: String)
    fun startReceptionistSession(prompt: String, toolsJson: String, voiceName: String, callback: GeminiMessageCallback)
    fun startGameSession(systemPrompt: String, toolsJson: String, voiceName: String, callback: GeminiMessageCallback)
    fun sendToolResponse(id: String, name: String, responseJson: String)
    fun sendText(text: String)
    fun startRecording()
    fun stopRecording()
    fun disconnect()
    fun close()
}

object NativeGeminiProvider {
    var bridge: NativeGeminiBridge? = null
}
