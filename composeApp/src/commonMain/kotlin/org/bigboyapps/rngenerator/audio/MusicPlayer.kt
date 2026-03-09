package org.bigboyapps.rngenerator.audio

/**
 * Platform-specific ambient music player.
 * Plays looping background music tracks and crossfades between moods.
 *
 * Supported moods: "peaceful", "tense", "battle", "mysterious"
 */
expect class MusicPlayer() {
    fun setMood(mood: String)
    fun setVolume(volume: Float)
    fun release()
}
