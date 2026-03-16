package com.rpgenerator.server

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.QuestStatus
import kotlin.test.*

/**
 * Tests for FeedStore.seedFromEvents() — converting persisted GameEvents
 * into FeedEntry format for pre-populating the feed on game resume.
 */
class FeedStoreSeedTest {

    @Test
    fun `seedFromEvents converts NarratorText`() {
        val store = FeedStore()
        store.seedFromEvents(listOf(
            GameEvent.NarratorText("You enter the dark cave.")
        ))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals("narration", all[0].type)
        assertEquals("You enter the dark cave.", all[0].text)
    }

    @Test
    fun `seedFromEvents converts NPCDialogue with metadata`() {
        val store = FeedStore()
        store.seedFromEvents(listOf(
            GameEvent.NPCDialogue("npc_grik", "Grik", "Welcome to my shop, adventurer!")
        ))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals("npc_dialogue", all[0].type)
        assertEquals("Welcome to my shop, adventurer!", all[0].text)
        assertEquals("Grik", all[0].metadata["npcName"]?.let {
            (it as kotlinx.serialization.json.JsonPrimitive).content
        })
    }

    @Test
    fun `seedFromEvents converts SystemNotification`() {
        val store = FeedStore()
        store.seedFromEvents(listOf(
            GameEvent.SystemNotification("Level up! You are now level 3.")
        ))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals("system", all[0].type)
        assertEquals("Level up! You are now level 3.", all[0].text)
    }

    @Test
    fun `seedFromEvents converts CombatLog`() {
        val store = FeedStore()
        store.seedFromEvents(listOf(
            GameEvent.CombatLog("You strike the goblin for 12 damage!")
        ))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals("combat_action", all[0].type)
    }

    @Test
    fun `seedFromEvents converts ItemGained with metadata`() {
        val store = FeedStore()
        store.seedFromEvents(listOf(
            GameEvent.ItemGained("item_sword", "Iron Sword", 1)
        ))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals("item_gained", all[0].type)
        assertEquals("Iron Sword", all[0].text)
    }

    @Test
    fun `seedFromEvents converts QuestUpdate with metadata`() {
        val store = FeedStore()
        store.seedFromEvents(listOf(
            GameEvent.QuestUpdate("quest_1", "Slay the Dragon", QuestStatus.NEW)
        ))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals("quest_update", all[0].type)
        assertEquals("Slay the Dragon", all[0].text)
    }

    @Test
    fun `seedFromEvents converts multiple events in order`() {
        val store = FeedStore()
        store.seedFromEvents(listOf(
            GameEvent.NarratorText("You arrive at the village."),
            GameEvent.NPCDialogue("npc_elder", "Elder", "Welcome, stranger."),
            GameEvent.QuestUpdate("quest_defense", "Village Defense", QuestStatus.NEW),
            GameEvent.NarratorText("The elder hands you a sword.")
        ))

        val all = store.all()
        assertEquals(4, all.size)
        assertEquals("narration", all[0].type)
        assertEquals("npc_dialogue", all[1].type)
        assertEquals("quest_update", all[2].type)
        assertEquals("narration", all[3].type)
    }

    @Test
    fun `seedFromEvents skips audio and portrait events`() {
        val store = FeedStore()
        store.seedFromEvents(listOf(
            GameEvent.NarratorText("You see a goblin."),
            GameEvent.MusicChange("battle"),
            GameEvent.NarratorText("It attacks!")
        ))

        val all = store.all()
        assertEquals(3, all.size) // narration + music_change + narration
        assertEquals("narration", all[0].type)
        assertEquals("music_change", all[1].type)
        assertEquals("narration", all[2].type)
    }

    @Test
    fun `seedFromEvents entries are available via recent()`() {
        val store = FeedStore()
        store.seedFromEvents(listOf(
            GameEvent.NarratorText("First"),
            GameEvent.NarratorText("Second"),
            GameEvent.NarratorText("Third")
        ))

        val recent = store.recent(2)
        assertEquals(2, recent.size)
        assertEquals("Second", recent[0].text)
        assertEquals("Third", recent[1].text)
    }

    @Test
    fun `seedFromEvents entries are available via since()`() {
        val store = FeedStore()
        store.seedFromEvents(listOf(
            GameEvent.NarratorText("First"),
            GameEvent.NarratorText("Second")
        ))

        val sinceFirst = store.since(1)
        assertEquals(1, sinceFirst.size)
        assertEquals("Second", sinceFirst[0].text)
    }

    // ── Companion buffer tests ──────────────────────────────────

    @Test
    fun `companion buffer accumulates and flushes`() {
        val store = FeedStore()
        store.appendCompanion("Watch out,")
        store.appendCompanion("kid.")

        val entry = store.flushCompanion("Hank")
        assertNotNull(entry)
        assertEquals("companion_aside", entry.type)
        assertEquals("Watch out, kid.", entry.text)
        assertEquals("Hank", entry.metadata["companionName"]?.let {
            (it as kotlinx.serialization.json.JsonPrimitive).content
        })
    }

    @Test
    fun `companion flush returns null when empty`() {
        val store = FeedStore()
        assertNull(store.flushCompanion())
    }

    @Test
    fun `companion and narration buffers are independent`() {
        val store = FeedStore()
        store.appendNarration("You enter the cave.")
        store.appendCompanion("I hate caves.")

        val narration = store.flushNarration()
        val companion = store.flushCompanion("Hank")

        assertNotNull(narration)
        assertNotNull(companion)
        assertEquals("narration", narration.type)
        assertEquals("companion_aside", companion.type)
        assertEquals("You enter the cave.", narration.text)
        assertEquals("I hate caves.", companion.text)
    }

    @Test
    fun `peekNarration returns current buffer without flushing`() {
        val store = FeedStore()
        store.appendNarration("Hello")
        store.appendNarration("world")

        assertEquals("Hello world", store.peekNarration())

        // Should still be there
        val entry = store.flushNarration()
        assertNotNull(entry)
        assertEquals("Hello world", entry.text)
    }

    @Test
    fun `recent returns last N entries`() {
        val store = FeedStore()
        repeat(10) { store.append("test", "entry $it") }

        val recent5 = store.recent(5)
        assertEquals(5, recent5.size)
        assertEquals("entry 5", recent5[0].text)
        assertEquals("entry 9", recent5[4].text)
    }

    @Test
    fun `recent with limit larger than size returns all`() {
        val store = FeedStore()
        store.append("test", "only one")

        val recent = store.recent(100)
        assertEquals(1, recent.size)
    }
}
