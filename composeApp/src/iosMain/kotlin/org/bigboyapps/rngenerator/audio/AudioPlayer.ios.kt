package org.bigboyapps.rngenerator.audio

actual class AudioPlayer actual constructor() {
    var isPlaying: Boolean = false
        private set

    actual fun enqueue(pcmData: ByteArray) {
        NativeAudioProvider.bridge?.playAudio(pcmData)
    }

    actual fun clear() {
        NativeAudioProvider.bridge?.clearAudio()
    }

    actual fun release() {
        NativeAudioProvider.bridge?.releaseAudio()
    }
}
