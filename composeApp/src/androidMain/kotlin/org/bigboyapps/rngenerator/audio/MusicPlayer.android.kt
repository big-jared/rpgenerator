package org.bigboyapps.rngenerator.audio

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.*

/**
 * Android ambient music player with crossfade support.
 * Uses two MediaPlayer instances to crossfade between mood tracks.
 *
 * Call [initContext] from Application/Activity before using [setMood].
 *
 * Music files should be placed in composeApp/src/androidMain/res/raw/
 * as: music_peaceful.ogg, music_tense.ogg, music_battle.ogg, music_mysterious.ogg
 */
actual class MusicPlayer actual constructor() {

    private var currentPlayer: MediaPlayer? = null
    private var fadingOutPlayer: MediaPlayer? = null
    private var currentMood: String = ""
    private var masterVolume: Float = 0.5f
    private val fadeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    actual fun setMood(mood: String) {
        if (mood == currentMood) return
        val ctx = appContext ?: return

        val resId = getMusicResource(ctx, mood)
        if (resId == 0) return

        currentMood = mood

        // Start crossfade: old player fades out, new player fades in
        fadingOutPlayer?.release()
        fadingOutPlayer = currentPlayer

        val newPlayer = MediaPlayer.create(ctx, resId)?.apply {
            isLooping = true
            setVolume(0f, 0f)
            start()
        } ?: return
        currentPlayer = newPlayer

        fadeScope.launch {
            crossfade(fadingOutPlayer, newPlayer, durationMs = 2000)
            fadingOutPlayer?.release()
            fadingOutPlayer = null
        }
    }

    actual fun setVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        currentPlayer?.setVolume(masterVolume, masterVolume)
    }

    actual fun release() {
        fadeScope.cancel()
        currentPlayer?.release()
        currentPlayer = null
        fadingOutPlayer?.release()
        fadingOutPlayer = null
    }

    private suspend fun crossfade(fadeOut: MediaPlayer?, fadeIn: MediaPlayer, durationMs: Long) {
        val steps = 20
        val stepDelay = durationMs / steps
        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            fadeOut?.setVolume(masterVolume * (1f - progress), masterVolume * (1f - progress))
            fadeIn.setVolume(masterVolume * progress, masterVolume * progress)
            delay(stepDelay)
        }
        fadeOut?.stop()
    }

    private fun getMusicResource(context: Context, mood: String): Int {
        val resName = "music_${mood.lowercase()}"
        return context.resources.getIdentifier(resName, "raw", context.packageName)
    }

    companion object {
        private var appContext: Context? = null

        fun initContext(context: Context) {
            appContext = context.applicationContext
        }
    }
}
