package org.bigboyapps.rngenerator.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Android AudioRecord implementation.
 * Records PCM 16kHz 16-bit mono in ~100ms chunks (3200 bytes).
 */
actual class AudioRecorder actual constructor() {

    private val log = Logger.withTag("AudioRecorder")
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val chunkSize = 3200 // 100ms at 16kHz, 16-bit mono

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

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

        // Try VOICE_COMMUNICATION first (enables AEC on real devices), fall back to MIC (emulators)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate, channelConfig, audioFormat, bufferSize
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            log.w { "VOICE_COMMUNICATION failed, falling back to MIC" }
            audioRecord?.release()
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormat, bufferSize
            )
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            log.w { "AudioRecord failed to initialize" }
            audioRecord?.release()
            audioRecord = null
            return
        }

        // Attach echo cancellation and noise suppression
        val sessionId = audioRecord!!.audioSessionId
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true }
            log.i { "AcousticEchoCanceler: ${if (echoCanceler != null) "enabled" else "unavailable"}" }
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId)?.also { it.enabled = true }
            log.i { "NoiseSuppressor: ${if (noiseSuppressor != null) "enabled" else "unavailable"}" }
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
        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
