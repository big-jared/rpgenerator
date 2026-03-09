package org.bigboyapps.rngenerator.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * iOS AudioRecorder stub — implement with AVAudioEngine post-hackathon.
 */
actual class AudioRecorder actual constructor() {
    actual val audioChunks: Flow<ByteArray> = emptyFlow()
    var isRecording: Boolean = false
        private set

    actual fun start() {
        // TODO: Implement with AVAudioEngine
    }

    actual fun stop() {
        // TODO: Implement with AVAudioEngine
    }
}
