package org.bigboyapps.rngenerator.audio

/**
 * Platform-specific ambient music player.
 * Streams PCM audio from the Lyria music generation service.
 *
 * Audio format: 48kHz stereo 16-bit PCM (from server via WebSocket).
 */
expect class MusicPlayer() {
    /** Enqueue a chunk of 48kHz stereo 16-bit PCM audio for playback. */
    fun enqueueChunk(pcmData: ByteArray)
    fun setVolume(volume: Float)
    fun release()
}
