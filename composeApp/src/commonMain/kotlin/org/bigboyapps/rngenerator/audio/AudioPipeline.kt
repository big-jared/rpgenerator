package org.bigboyapps.rngenerator.audio

import kotlinx.coroutines.flow.Flow

/**
 * Platform-specific audio recorder.
 * Records PCM 16kHz mono audio in ~100ms chunks.
 */
expect class AudioRecorder() {
    fun start()
    fun stop()
    val audioChunks: Flow<ByteArray>
}

/**
 * Platform-specific audio player.
 * Plays PCM 24kHz mono audio streamed from Gemini.
 */
expect class AudioPlayer() {
    fun enqueue(pcmData: ByteArray)
    fun clear()
    fun release()
}
