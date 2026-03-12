package org.bigboyapps.rngenerator.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

actual class AudioRecorder actual constructor() {
    private val _audioChunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    actual val audioChunks: Flow<ByteArray> = _audioChunks.asSharedFlow()
    var isRecording: Boolean = false
        private set

    actual fun start() {
        if (isRecording) return
        isRecording = true
        NativeAudioProvider.bridge?.startRecording(object : AudioChunkCallback {
            override fun onChunk(data: ByteArray) { _audioChunks.tryEmit(data) }
        })
    }

    actual fun stop() {
        isRecording = false
        NativeAudioProvider.bridge?.stopRecording()
    }
}
