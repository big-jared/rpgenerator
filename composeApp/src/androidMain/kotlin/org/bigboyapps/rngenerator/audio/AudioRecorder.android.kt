package org.bigboyapps.rngenerator.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Android AudioRecord implementation.
 * Records PCM 16kHz 16-bit mono in ~100ms chunks (3200 bytes).
 */
actual class AudioRecorder actual constructor() {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val chunkSize = 3200 // 100ms at 16kHz, 16-bit mono

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    private val _audioChunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    actual val audioChunks: Flow<ByteArray> = _audioChunks.asSharedFlow()

    var isRecording: Boolean = false
        private set

    actual fun start() {
        if (isRecording) return

        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
            chunkSize * 2
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            return
        }

        isRecording = true
        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ByteArray(chunkSize)
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, chunkSize) ?: -1
                if (bytesRead > 0) {
                    _audioChunks.tryEmit(buffer.copyOf(bytesRead))
                }
            }
        }.apply {
            name = "AudioRecorder"
            start()
        }
    }

    actual fun stop() {
        isRecording = false
        recordingThread?.join(500)
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
