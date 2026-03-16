package com.rpgenerator.server

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Server-side feed — the single source of truth for what the client displays.
 *
 * Every game event, tool result, narration block, player message, and image
 * is recorded as a FeedEntry. The WebSocket pushes entries to the client in
 * real time. On reconnect, the client sends its last seen ID and the server
 * replays missed entries.
 */
class FeedStore {
    private val entries = mutableListOf<FeedEntry>()
    private var nextId = 1L

    /** Narration text buffer — accumulates across model turn, flushed on turnComplete */
    private val narrationBuffer = StringBuilder()

    /** Player speech buffer — accumulates across user turn, flushed when model starts */
    private val playerBuffer = StringBuilder()

    /** Companion aside buffer — accumulates companion's own dialogue (not narration readback) */
    private val companionBuffer = StringBuilder()

    fun append(type: String, text: String? = null, metadata: JsonObject = JsonObject(emptyMap())): FeedEntry {
        val entry = FeedEntry(
            id = nextId++,
            type = type,
            timestamp = System.currentTimeMillis(),
            text = text,
            metadata = metadata
        )
        synchronized(entries) {
            entries.add(entry)
            // Cap at 500 entries
            if (entries.size > 500) entries.removeAt(0)
        }
        return entry
    }

    fun since(afterId: Long): List<FeedEntry> {
        synchronized(entries) {
            return entries.filter { it.id > afterId }
        }
    }

    fun all(): List<FeedEntry> {
        synchronized(entries) {
            return entries.toList()
        }
    }

    fun recent(limit: Int): List<FeedEntry> {
        synchronized(entries) {
            return entries.takeLast(limit)
        }
    }

    /**
     * Seed the feed from persisted GameEvents (used on game resume).
     * Converts engine events into FeedEntry format so the client can
     * pre-populate the feed before the WebSocket connects.
     */
    fun seedFromEvents(events: List<com.rpgenerator.core.api.GameEvent>) {
        for (event in events) {
            when (event) {
                is com.rpgenerator.core.api.GameEvent.NarratorText ->
                    append("narration", event.text)
                is com.rpgenerator.core.api.GameEvent.NPCDialogue ->
                    append("npc_dialogue", event.text, kotlinx.serialization.json.buildJsonObject {
                        put("npcName", kotlinx.serialization.json.JsonPrimitive(event.npcName))
                    })
                is com.rpgenerator.core.api.GameEvent.SystemNotification ->
                    append("system", event.text)
                is com.rpgenerator.core.api.GameEvent.CombatLog ->
                    append("combat_action", event.text)
                is com.rpgenerator.core.api.GameEvent.ItemGained ->
                    append("item_gained", event.itemName, kotlinx.serialization.json.buildJsonObject {
                        put("itemName", kotlinx.serialization.json.JsonPrimitive(event.itemName))
                        put("quantity", kotlinx.serialization.json.JsonPrimitive(event.quantity))
                    })
                is com.rpgenerator.core.api.GameEvent.QuestUpdate ->
                    append("quest_update", event.questName, kotlinx.serialization.json.buildJsonObject {
                        put("questName", kotlinx.serialization.json.JsonPrimitive(event.questName))
                        put("status", kotlinx.serialization.json.JsonPrimitive(event.status.name))
                    })
                is com.rpgenerator.core.api.GameEvent.StatChange ->
                    append("stat_change", "${event.statName}: ${event.oldValue} → ${event.newValue}", kotlinx.serialization.json.buildJsonObject {
                        put("statName", kotlinx.serialization.json.JsonPrimitive(event.statName))
                        put("oldValue", kotlinx.serialization.json.JsonPrimitive(event.oldValue))
                        put("newValue", kotlinx.serialization.json.JsonPrimitive(event.newValue))
                    })
                is com.rpgenerator.core.api.GameEvent.MusicChange ->
                    append("music_change", event.mood)
                else -> {} // skip audio, portraits, icons
            }
        }
    }

    // ── Narration accumulation ──────────────────────────────────────

    fun appendNarration(text: String) {
        if (narrationBuffer.isNotEmpty() && !text.startsWith(" ") && !narrationBuffer.endsWith(" ")) {
            narrationBuffer.append(" ")
        }
        narrationBuffer.append(text)
    }

    fun peekNarration(): String = narrationBuffer.toString()

    fun flushNarration(): FeedEntry? {
        if (narrationBuffer.isEmpty()) return null
        val text = narrationBuffer.toString().trim()
        narrationBuffer.clear()
        if (text.isEmpty()) return null
        return append("narration", text)
    }

    // ── Player speech accumulation ──────────────────────────────────

    fun appendPlayerSpeech(text: String) {
        if (playerBuffer.isNotEmpty() && !text.startsWith(" ") && !playerBuffer.endsWith(" ")) {
            playerBuffer.append(" ")
        }
        playerBuffer.append(text)
    }

    fun flushPlayerSpeech(): FeedEntry? {
        if (playerBuffer.isEmpty()) return null
        val text = playerBuffer.toString().trim()
        playerBuffer.clear()
        if (text.isEmpty()) return null
        return append("player", text)
    }

    // ── Companion aside accumulation ─────────────────────────────

    fun appendCompanion(text: String) {
        if (companionBuffer.isNotEmpty() && !text.startsWith(" ") && !companionBuffer.endsWith(" ")) {
            companionBuffer.append(" ")
        }
        companionBuffer.append(text)
    }

    fun flushCompanion(companionName: String = "Companion"): FeedEntry? {
        if (companionBuffer.isEmpty()) return null
        val text = companionBuffer.toString().trim()
        companionBuffer.clear()
        if (text.isEmpty()) return null
        return append("companion_aside", text, kotlinx.serialization.json.buildJsonObject {
            put("companionName", kotlinx.serialization.json.JsonPrimitive(companionName))
        })
    }
}

@Serializable
data class FeedEntry(
    val id: Long,
    val type: String,
    val timestamp: Long,
    val text: String? = null,
    val metadata: JsonObject = JsonObject(emptyMap())
)

// ── Helper to send a feed entry over WebSocket ──────────────────────

private val feedJson = Json { encodeDefaults = true }

suspend fun DefaultWebSocketServerSession.sendFeedEntry(entry: FeedEntry) {
    val entryJson = feedJson.encodeToString(FeedEntry.serializer(), entry)
    send("""{"type":"feed","entry":$entryJson}""")
}

suspend fun DefaultWebSocketServerSession.sendFeedSync(entries: List<FeedEntry>) {
    val entriesJson = feedJson.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(FeedEntry.serializer()),
        entries
    )
    send("""{"type":"feed_sync","entries":$entriesJson}""")
}
