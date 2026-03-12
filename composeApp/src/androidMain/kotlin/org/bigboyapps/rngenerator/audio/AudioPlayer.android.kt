package org.bigboyapps.rngenerator.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Android AudioTrack implementation.
 * Plays PCM 24kHz 16-bit mono audio streamed from Gemini Live API.
 * Thread-safe — enqueue may be called from multiple Gemini callback threads.
 */
actual class AudioPlayer actual constructor() {

    private val sampleRate = 24000
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val lock = Object()
    private var audioTrack: AudioTrack? = null
    var isPlaying: Boolean = false
        private set

    private fun ensureTrack(): AudioTrack {
        // Already checked under lock by callers
        audioTrack?.let { return it }

        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(audioFormat)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.play()
        audioTrack = track
        isPlaying = true
        return track
    }

    actual fun enqueue(pcmData: ByteArray) {
        synchronized(lock) {
            val track = ensureTrack()
            track.write(pcmData, 0, pcmData.size)
        }
    }

    actual fun clear() {
        synchronized(lock) {
            audioTrack?.let { track ->
                track.pause()
                track.flush()
                track.play()
            }
        }
    }

    actual fun release() {
        synchronized(lock) {
            isPlaying = false
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        }
    }
}
