package com.rpgenerator.server

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.*

class FeedStoreTest {

    @Test
    fun `append assigns sequential IDs`() {
        val store = FeedStore()
        val e1 = store.append("narration", "Hello world")
        val e2 = store.append("system", "Level up!")
        val e3 = store.append("narration", "You enter the cave")

        assertEquals(1L, e1.id)
        assertEquals(2L, e2.id)
        assertEquals(3L, e3.id)
    }

    @Test
    fun `append stores type and text`() {
        val store = FeedStore()
        val entry = store.append("npc_dialogue", "Greetings traveler", buildJsonObject {
            put("npcName", JsonPrimitive("Hank"))
        })

        assertEquals("npc_dialogue", entry.type)
        assertEquals("Greetings traveler", entry.text)
        assertEquals("Hank", entry.metadata["npcName"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun `all returns all entries in order`() {
        val store = FeedStore()
        store.append("a", "first")
        store.append("b", "second")
        store.append("c", "third")

        val all = store.all()
        assertEquals(3, all.size)
        assertEquals("first", all[0].text)
        assertEquals("second", all[1].text)
        assertEquals("third", all[2].text)
    }

    @Test
    fun `since filters entries after given ID`() {
        val store = FeedStore()
        store.append("a", "first")
        store.append("b", "second")
        store.append("c", "third")

        val sinceFirst = store.since(1L)
        assertEquals(2, sinceFirst.size)
        assertEquals("second", sinceFirst[0].text)
        assertEquals("third", sinceFirst[1].text)

        val sinceSecond = store.since(2L)
        assertEquals(1, sinceSecond.size)
        assertEquals("third", sinceSecond[0].text)

        val sinceAll = store.since(3L)
        assertTrue(sinceAll.isEmpty())
    }

    @Test
    fun `since with zero returns all entries`() {
        val store = FeedStore()
        store.append("a", "first")
        store.append("b", "second")

        val all = store.since(0L)
        assertEquals(2, all.size)
    }

    @Test
    fun `caps at 500 entries`() {
        val store = FeedStore()
        repeat(510) { i ->
            store.append("entry", "entry $i")
        }

        val all = store.all()
        assertEquals(500, all.size)
        // Oldest entries should have been evicted
        assertEquals("entry 10", all[0].text)
        assertEquals("entry 509", all[499].text)
    }

    // ── Narration buffering ─────────────────────────────────────────

    @Test
    fun `appendNarration accumulates text with spaces`() {
        val store = FeedStore()
        store.appendNarration("The cave")
        store.appendNarration("was dark")
        store.appendNarration("and cold.")

        val entry = store.flushNarration()
        assertNotNull(entry)
        assertEquals("narration", entry.type)
        assertEquals("The cave was dark and cold.", entry.text)
    }

    @Test
    fun `appendNarration does not double-space`() {
        val store = FeedStore()
        store.appendNarration("Hello ")
        store.appendNarration("world")

        val entry = store.flushNarration()
        assertNotNull(entry)
        assertEquals("Hello world", entry.text)
    }

    @Test
    fun `flushNarration returns null when empty`() {
        val store = FeedStore()
        assertNull(store.flushNarration())
    }

    @Test
    fun `flushNarration clears buffer`() {
        val store = FeedStore()
        store.appendNarration("First turn narration")
        val first = store.flushNarration()
        assertNotNull(first)

        // Second flush should be null
        assertNull(store.flushNarration())

        // New narration should work independently
        store.appendNarration("Second turn")
        val second = store.flushNarration()
        assertNotNull(second)
        assertEquals("Second turn", second.text)
    }

    @Test
    fun `flushNarration returns null for whitespace-only`() {
        val store = FeedStore()
        store.appendNarration("   ")
        assertNull(store.flushNarration())
    }

    // ── Player speech buffering ─────────────────────────────────────

    @Test
    fun `appendPlayerSpeech accumulates text`() {
        val store = FeedStore()
        store.appendPlayerSpeech("I want to")
        store.appendPlayerSpeech("attack the goblin")

        val entry = store.flushPlayerSpeech()
        assertNotNull(entry)
        assertEquals("player", entry.type)
        assertEquals("I want to attack the goblin", entry.text)
    }

    @Test
    fun `flushPlayerSpeech returns null when empty`() {
        val store = FeedStore()
        assertNull(store.flushPlayerSpeech())
    }

    @Test
    fun `flushPlayerSpeech clears buffer`() {
        val store = FeedStore()
        store.appendPlayerSpeech("Hello")
        store.flushPlayerSpeech()
        assertNull(store.flushPlayerSpeech())
    }

    // ── Narration and player speech are independent ─────────────────

    @Test
    fun `narration and player buffers are independent`() {
        val store = FeedStore()
        store.appendNarration("The dragon roars")
        store.appendPlayerSpeech("I dodge")

        val narration = store.flushNarration()
        val player = store.flushPlayerSpeech()

        assertNotNull(narration)
        assertNotNull(player)
        assertEquals("The dragon roars", narration.text)
        assertEquals("I dodge", player.text)
        assertEquals("narration", narration.type)
        assertEquals("player", player.type)
    }

    // ── Flushed entries appear in all() ──────────────────────────────

    @Test
    fun `flushed narration appears in entry list`() {
        val store = FeedStore()
        store.append("system", "Game started")
        store.appendNarration("You awaken in a dungeon.")
        store.flushNarration()

        val all = store.all()
        assertEquals(2, all.size)
        assertEquals("system", all[0].type)
        assertEquals("narration", all[1].type)
    }

    @Test
    fun `metadata defaults to empty JsonObject`() {
        val store = FeedStore()
        val entry = store.append("narration", "text")
        assertEquals(JsonObject(emptyMap()), entry.metadata)
    }

    @Test
    fun `timestamp is set`() {
        val before = System.currentTimeMillis()
        val store = FeedStore()
        val entry = store.append("test", "text")
        val after = System.currentTimeMillis()

        assertTrue(entry.timestamp in before..after)
    }
}
