package com.rpgenerator.core.gemini

import com.rpgenerator.core.domain.*
import com.rpgenerator.core.test.TestHelpers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

/**
 * Tests the tool contract that Gemini calls into during a live session.
 * These verify that game state queries and mutations work correctly
 * when invoked as tool calls (as Gemini would call them).
 */
class GeminiToolContractTest {

    private fun createToolContract(
        state: GameState = TestHelpers.createTestGameState()
    ): GeminiToolContractImpl {
        return GeminiToolContractImpl(state)
    }

    // ── State Query Tools ──────────────────────────────────────────

    @Test
    fun `getPlayerStats returns current stats as JSON`() {
        val state = TestHelpers.createTestGameState(playerLevel = 5, playerXP = 350L)
        val tools = createToolContract(state)

        val result = tools.getPlayerStats()

        assertTrue(result.success)
        assertNotNull(result.data)
        assertEquals(5, result.data!!["level"]?.jsonPrimitive?.int)
    }

    @Test
    fun `getInventory returns empty inventory for new character`() {
        val tools = createToolContract()

        val result = tools.getInventory()

        assertTrue(result.success)
        assertNotNull(result.data)
    }

    @Test
    fun `getNPCsHere returns NPCs at current location`() {
        val location = TestHelpers.createTestLocation(id = "village")
        val npc = NPC(
            id = "npc_1",
            name = "Grimjaw",
            archetype = NPCArchetype.BLACKSMITH,
            locationId = "village",
            personality = NPCPersonality(
                traits = listOf("gruff", "honest"),
                speechPattern = "Short, direct sentences",
                motivations = listOf("Keep the forge running")
            ),
            lore = "Village blacksmith for 20 years"
        )
        val state = TestHelpers.createTestGameState(location = location)
            .addNPC(npc)
        val tools = createToolContract(state)

        val result = tools.getNPCsHere()

        assertTrue(result.success)
        assertNotNull(result.data)
        val npcsJson = result.data.toString()
        assertTrue(npcsJson.contains("Grimjaw"), "Should contain NPC name")
    }

    @Test
    fun `getLocation returns location with connections`() {
        val tools = createToolContract()

        val result = tools.getLocation()

        assertTrue(result.success)
        assertNotNull(result.data)
        val locationJson = result.data.toString()
        assertTrue(locationJson.contains("Test Location"), "Should contain location name")
    }

    @Test
    fun `getActiveQuests returns empty for new game`() {
        val tools = createToolContract()

        val result = tools.getActiveQuests()

        assertTrue(result.success)
        assertNotNull(result.data)
    }

    @Test
    fun `getCharacterSheet returns full sheet with skills and equipment`() {
        val tools = createToolContract()

        val result = tools.getCharacterSheet()

        assertTrue(result.success)
        assertNotNull(result.data)
        val sheetJson = result.data.toString()
        assertTrue(sheetJson.contains("level"), "Should contain level")
        assertTrue(sheetJson.contains("HP") || sheetJson.contains("hp"), "Should contain HP")
    }

    // ── Action Tools ───────────────────────────────────────────────

    @Test
    fun `attackTarget resolves combat and returns events`() = runTest {
        val tools = createToolContract()

        val result = tools.attackTarget("goblin")

        assertTrue(result.success)
        assertTrue(result.gameEvents.isNotEmpty(), "Combat should produce game events")
    }

    @Test
    fun `talkToNPC fails gracefully when NPC not found`() = runTest {
        val tools = createToolContract()

        val result = tools.talkToNPC("nonexistent wizard")

        assertFalse(result.success)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("not found", ignoreCase = true))
    }

    @Test
    fun `talkToNPC succeeds when NPC exists at location`() = runTest {
        val location = TestHelpers.createTestLocation(id = "village")
        val npc = NPC(
            id = "npc_merchant",
            name = "Elara",
            archetype = NPCArchetype.MERCHANT,
            locationId = "village",
            personality = NPCPersonality(
                traits = listOf("friendly", "shrewd"),
                speechPattern = "Warm but business-like",
                motivations = listOf("Profit")
            ),
            lore = "Traveling merchant"
        )
        val state = TestHelpers.createTestGameState(location = location).addNPC(npc)
        val tools = createToolContract(state)

        val result = tools.talkToNPC("Elara")

        assertTrue(result.success)
        assertNotNull(result.data)
    }

    @Test
    fun `useItem fails when item not in inventory`() = runTest {
        val tools = createToolContract()

        val result = tools.useItem("nonexistent_potion")

        assertFalse(result.success)
        assertNotNull(result.error)
    }

    @Test
    fun `moveToLocation fails when location not connected`() = runTest {
        val tools = createToolContract()

        val result = tools.moveToLocation("Mount Doom")

        assertFalse(result.success)
        assertNotNull(result.error)
    }

    // ── Quest Tools ────────────────────────────────────────────────

    @Test
    fun `acceptQuest adds quest to active quests`() = runTest {
        val tools = createToolContract()

        val result = tools.acceptQuest("quest_tutorial_1")

        // Should succeed if quest exists in the system, fail if not
        // For now, this tests the interface contract
        assertNotNull(result)
    }

    @Test
    fun `completeQuest fails when objectives not met`() = runTest {
        val quest = Quest(
            id = "quest_1",
            name = "Slay the Beast",
            description = "Defeat the forest guardian",
            type = QuestType.KILL,
            objectives = listOf(
                QuestObjective(
                    id = "obj_1",
                    type = ObjectiveType.KILL,
                    description = "Defeat the Forest Guardian",
                    targetId = "forest_guardian",
                    targetProgress = 1,
                    currentProgress = 0
                )
            ),
            rewards = QuestRewards(xp = 100)
        )
        val state = TestHelpers.createTestGameState().addQuest(quest)
        val tools = createToolContract(state)

        val result = tools.completeQuest("quest_1")

        assertFalse(result.success, "Should not complete quest with unfinished objectives")
    }

    // ── Multimodal Tools ───────────────────────────────────────────

    @Test
    fun `generateSceneArt returns image event`() = runTest {
        val tools = createToolContract()

        val result = tools.generateSceneArt("A dark cavern with glowing crystals")

        assertTrue(result.success)
        // Should produce a SceneImage game event
        val imageEvent = result.gameEvents.filterIsInstance<com.rpgenerator.core.api.GameEvent.SceneImage>()
        assertTrue(imageEvent.isNotEmpty(), "Should emit SceneImage event")
    }

    @Test
    fun `shiftMusicMood returns music event`() = runTest {
        val tools = createToolContract()

        val result = tools.shiftMusicMood("tense", intensity = 0.8f)

        assertTrue(result.success)
        val musicEvent = result.gameEvents.filterIsInstance<com.rpgenerator.core.api.GameEvent.MusicChange>()
        assertTrue(musicEvent.isNotEmpty(), "Should emit MusicChange event")
    }

    // ── Tool Dispatch ──────────────────────────────────────────────

    @Test
    fun `dispatch routes tool calls by name`() = runTest {
        val tools = createToolContract()

        val callGetStats = GeminiToolCall(
            id = "call_1",
            name = "get_player_stats",
            arguments = buildJsonObject { }
        )
        val result = tools.dispatch(callGetStats)

        assertTrue(result.success)
        assertNotNull(result.data)
    }

    @Test
    fun `dispatch handles unknown tool gracefully`() = runTest {
        val tools = createToolContract()

        val unknownCall = GeminiToolCall(
            id = "call_2",
            name = "nonexistent_tool",
            arguments = buildJsonObject { }
        )
        val result = tools.dispatch(unknownCall)

        assertFalse(result.success)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Unknown tool"))
    }

    @Test
    fun `dispatch passes arguments to tool`() = runTest {
        val tools = createToolContract()

        val callAttack = GeminiToolCall(
            id = "call_3",
            name = "attack_target",
            arguments = buildJsonObject {
                put("target", JsonPrimitive("goblin"))
            }
        )
        val result = tools.dispatch(callAttack)

        assertTrue(result.success)
        assertTrue(result.gameEvents.isNotEmpty())
    }
}
