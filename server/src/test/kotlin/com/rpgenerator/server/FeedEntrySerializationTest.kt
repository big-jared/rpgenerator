package com.rpgenerator.server

import kotlinx.serialization.json.*
import kotlin.test.*

class FeedEntrySerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `FeedEntry serializes with all fields`() {
        val entry = FeedEntry(
            id = 42,
            type = "npc_dialogue",
            timestamp = 1710000000000L,
            text = "Well met, adventurer!",
            metadata = buildJsonObject {
                put("npcName", JsonPrimitive("Hank"))
            }
        )

        val serialized = json.encodeToString(FeedEntry.serializer(), entry)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals(42, parsed["id"]?.jsonPrimitive?.long)
        assertEquals("npc_dialogue", parsed["type"]?.jsonPrimitive?.content)
        assertEquals(1710000000000L, parsed["timestamp"]?.jsonPrimitive?.long)
        assertEquals("Well met, adventurer!", parsed["text"]?.jsonPrimitive?.content)
        assertEquals("Hank", parsed["metadata"]?.jsonObject?.get("npcName")?.jsonPrimitive?.content)
    }

    @Test
    fun `FeedEntry serializes with null text`() {
        val entry = FeedEntry(
            id = 1,
            type = "scene_image",
            timestamp = 1710000000000L,
            text = null,
            metadata = buildJsonObject {
                put("data", JsonPrimitive("base64data"))
                put("mimeType", JsonPrimitive("image/png"))
            }
        )

        val serialized = json.encodeToString(FeedEntry.serializer(), entry)
        val parsed = json.parseToJsonElement(serialized).jsonObject

        assertEquals("scene_image", parsed["type"]?.jsonPrimitive?.content)
        assertTrue(parsed["text"] is JsonNull || parsed["text"]?.jsonPrimitive?.contentOrNull == null)
    }

    @Test
    fun `FeedEntry round-trips through serialization`() {
        val original = FeedEntry(
            id = 99,
            type = "combat_action",
            timestamp = 1710000000000L,
            text = "Critical hit! 24 damage to Goblin.",
            metadata = JsonObject(emptyMap())
        )

        val serialized = json.encodeToString(FeedEntry.serializer(), original)
        val deserialized = json.decodeFromString(FeedEntry.serializer(), serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `feed wrapper format matches client expectation`() {
        val entry = FeedEntry(
            id = 1,
            type = "narration",
            timestamp = 1710000000000L,
            text = "You enter the dungeon."
        )

        val entryJson = json.encodeToString(FeedEntry.serializer(), entry)
        val wrapper = """{"type":"feed","entry":$entryJson}"""

        val parsed = json.parseToJsonElement(wrapper).jsonObject
        assertEquals("feed", parsed["type"]?.jsonPrimitive?.content)
        assertNotNull(parsed["entry"]?.jsonObject)
        assertEquals("narration", parsed["entry"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun `feed_sync wrapper format matches client expectation`() {
        val entries = listOf(
            FeedEntry(1, "narration", 1710000000000L, "First"),
            FeedEntry(2, "player", 1710000000001L, "Second")
        )

        val entriesJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(FeedEntry.serializer()),
            entries
        )
        val wrapper = """{"type":"feed_sync","entries":$entriesJson}"""

        val parsed = json.parseToJsonElement(wrapper).jsonObject
        assertEquals("feed_sync", parsed["type"]?.jsonPrimitive?.content)
        val arr = parsed["entries"]?.jsonArray
        assertNotNull(arr)
        assertEquals(2, arr.size)
    }
}
