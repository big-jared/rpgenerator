package org.bigboyapps.rngenerator.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Android streaming music player for Lyria RealTime PCM audio.
 * Uses AudioTrack in streaming mode at 48kHz stereo 16-bit.
 */
actual class MusicPlayer actual constructor() {

    private var audioTrack: AudioTrack? = null
    private var masterVolume: Float = 0.35f // Lower than voice by default

    private fun ensureTrack(): AudioTrack {
        audioTrack?.let { return it }

        val sampleRate = 48000
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding) * 4

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(encoding)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.setVolume(masterVolume)
        track.play()
        audioTrack = track
        return track
    }

    actual fun enqueueChunk(pcmData: ByteArray) {
        if (pcmData.isEmpty()) return
        val track = ensureTrack()
        track.write(pcmData, 0, pcmData.size)
    }

    actual fun setVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        audioTrack?.setVolume(masterVolume)
    }

    actual fun release() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
