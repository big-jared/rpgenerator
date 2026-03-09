package org.bigboyapps.rngenerator.audio

/**
 * iOS AudioPlayer stub — implement with AVAudioEngine post-hackathon.
 */
actual class AudioPlayer actual constructor() {
    var isPlaying: Boolean = false
        private set

    actual fun enqueue(pcmData: ByteArray) {
        // TODO: Implement with AVAudioEngine
    }

    actual fun clear() {
        // TODO: Implement with AVAudioEngine
    }

    actual fun release() {
        // TODO: Implement with AVAudioEngine
    }
}
