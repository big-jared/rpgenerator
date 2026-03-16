package com.rpgenerator.server

import kotlin.test.*

class LyriaMusicServiceTest {

    @Test
    fun `setMood is idempotent for same mood`() {
        // LyriaMusicService tracks currentMood and skips if unchanged.
        // We can't test WebSocket sends without a real connection, but we
        // can verify the service doesn't crash when used before connect().
        val chunks = mutableListOf<ByteArray>()
        val service = LyriaMusicService("fake-key") { chunks.add(it) }

        // setMood before connect should not crash
        service.setMood("peaceful")
        service.setMood("peaceful") // duplicate should be no-op

        // No audio since we never connected
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `close is safe to call without connect`() {
        val service = LyriaMusicService("fake-key") {}
        // Should not throw
        service.close()
    }

    @Test
    fun `pause and resume are safe without connect`() {
        val service = LyriaMusicService("fake-key") {}
        // Should not throw
        service.pause()
        service.resume()
        service.close()
    }
}
