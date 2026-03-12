package org.bigboyapps.rngenerator.audio

/**
 * Platform-agnostic audio bridge that native code (Swift/ObjC) can implement.
 * On iOS, set NativeAudioProvider.bridge from Swift before the app launches.
 * On Android, this is unused — Android uses its own AudioRecord/AudioTrack directly.
 */
interface AudioChunkCallback {
    fun onChunk(data: ByteArray)
}

interface NativeAudioBridge {
    fun startRecording(callback: AudioChunkCallback)
    fun stopRecording()
    fun playAudio(pcmData: ByteArray)
    fun clearAudio()
    fun releaseAudio()
}

object NativeAudioProvider {
    var bridge: NativeAudioBridge? = null
}
