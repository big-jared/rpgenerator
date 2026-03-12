package org.bigboyapps.rngenerator.service

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.bigboyapps.rngenerator.network.GameWebSocketClient
import org.bigboyapps.rngenerator.network.ServerMessage
import org.bigboyapps.rngenerator.ui.OnboardingConnection
import org.bigboyapps.rngenerator.ui.OnboardingResult

/**
 * GameConnection backed by GameSessionService.
 * The service keeps the Gemini Live connection + audio alive when the app is backgrounded.
 */
class ServiceGameConnection(
    private val service: GameSessionService
) : OnboardingConnection {

    override val messages: SharedFlow<ServerMessage> = service.messages
    override val connectionState: StateFlow<GameWebSocketClient.ConnectionState> = service.connectionState

    override val onboardingComplete: SharedFlow<OnboardingResult>
        get() = service.connection?.onboardingComplete
            ?: kotlinx.coroutines.flow.MutableSharedFlow()

    override fun startReceptionistSession(prompt: String) {
        service.startReceptionistSession(prompt)
    }

    override fun disconnectSession() {
        service.connection?.disconnectSession()
    }

    override fun startSession(serverUrl: String, sessionId: String) {
        service.startSession(serverUrl, sessionId)
    }

    override suspend fun sendConnect(voiceName: String) {
        service.sendConnect(voiceName)
    }

    override suspend fun sendText(text: String) {
        service.sendText(text)
    }

    override fun startRecording() {
        service.startRecording()
    }

    override fun stopRecording() {
        service.stopRecording()
    }

    override fun enqueueAudio(pcmData: ByteArray) {
        service.connection?.enqueueAudio(pcmData)
    }

    override fun clearAudio() {
        service.connection?.clearAudio()
    }

    override fun releaseAudio() {
        // Don't release — service owns the lifecycle
    }

    override fun close() {
        service.teardown()
    }
}
