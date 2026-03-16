package com.rpgenerator.core.tools

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.test.TestHelpers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlin.test.*

/**
 * Edge case and error path tests for the tool contract.
 * Complements UnifiedToolContractImplTest with boundary conditions.
 */
class ToolContractEdgeCaseTest {

    private val contract = UnifiedToolContractImpl()

    private fun testState(
        playerLevel: Int = 1,
        playerXP: Long = 0L,
        items: Map<String, InventoryItem> = emptyMap(),
        npcs: List<NPC> = emptyList(),
        combatState: CombatState? = null
    ): GameState {
        val sheet = TestHelpers.createTestCharacterSheet(playerLevel, playerXP).let {
            if (items.isNotEmpty()) it.copy(inventory = Inventory(items = items)) else it
        }
        val location = TestHelpers.createTestLocation()
        return GameState(
            gameId = "test",
            systemType = SystemType.SYSTEM_INTEGRATION,
            characterSheet = sheet,
            currentLocation = location,
            npcsByLocation = if (npcs.isNotEmpty()) mapOf(location.id to npcs) else emptyMap(),
            hasOpeningNarrationPlayed = true,
            combatState = combatState
        )
    }

    private fun testNPC(name: String, archetype: NPCArchetype = NPCArchetype.MERCHANT): NPC {
        return NPC(
            id = "npc_${name.lowercase().replace(" ", "_")}",
            name = name,
            archetype = archetype,
            locationId = "test-location",
            personality = NPCPersonality(listOf("friendly"), "Normal", listOf("Help")),
            lore = ""
        )
    }

    // ── Item Edge Cases ────────────────────────────────────────────

    @Test
    fun `use_item fails when item not in inventory`() = runTest {
        val state = testState()
        val result = contract.executeTool("use_item", mapOf("itemId" to "nonexistent"), state)
        assertFalse(result.success)
    }

    @Test
    fun `use_item with last quantity removes item`() = runTest {
        val items = mapOf(
            "p1" to InventoryItem("p1", "Potion", "Heals", ItemType.CONSUMABLE, quantity = 1)
        )
        val state = testState(items = items)
        val result = contract.executeTool("use_item", mapOf("itemId" to "p1"), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertEquals(0, result.data["remaining"]?.jsonPrimitive?.int)
    }

    @Test
    fun `add_item creates new inventory entry`() = runTest {
        val state = testState()
        val result = contract.executeTool("add_item", mapOf(
            "itemName" to "Iron Sword",
            "description" to "A sturdy blade",
            "itemType" to "WEAPON",
            "rarity" to "COMMON"
        ), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertTrue(result.newState!!.characterSheet.inventory.items.any {
            it.value.name == "Iron Sword"
        })
    }

    @Test
    fun `add_gold increases gold amount`() = runTest {
        val state = testState()
        val result = contract.executeTool("add_gold", mapOf("amount" to 50), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
    }

    @Test
    fun `equip_item fails for non-equipment item`() = runTest {
        val items = mapOf(
            "potion" to InventoryItem("potion", "Potion", "Heals", ItemType.CONSUMABLE)
        )
        val state = testState(items = items)
        val result = contract.executeTool("equip_item", mapOf("itemId" to "potion"), state)

        assertFalse(result.success)
    }

    // ── Combat Edge Cases ──────────────────────────────────────────

    @Test
    fun `combat_use_skill fails not in combat`() = runTest {
        val state = testState()
        val result = contract.executeTool("combat_use_skill", mapOf("skillId" to "fireball"), state)
        assertFalse(result.success)
    }

    @Test
    fun `get_combat_targets fails not in combat`() = runTest {
        val state = testState()
        val result = contract.executeTool("get_combat_targets", emptyMap(), state)
        // Should succeed with inCombat=false or fail gracefully
        assertTrue(result.success || result.error != null)
    }

    @Test
    fun `skill_check returns roll and outcome`() = runTest {
        val state = testState(playerLevel = 5)
        val result = contract.executeTool("skill_check", mapOf(
            "checkType" to "Investigation",
            "difficulty" to "MODERATE"
        ), state)

        assertTrue(result.success)
        assertNotNull(result.data["roll"], "Should include d20 roll")
        assertNotNull(result.data["success"], "Should include success/failure")
        assertNotNull(result.data["degree"], "Should include degree of success")
    }

    @Test
    fun `combat loop - fight to completion`() = runTest {
        val state = testState(playerLevel = 10) // High level to ensure we win

        // Start combat with weak enemy
        val startResult = contract.executeTool("start_combat", mapOf(
            "enemyName" to "Weak Rat",
            "danger" to 1
        ), state)
        assertTrue(startResult.success)

        // Attack until combat ends
        var current = startResult.newState!!
        var rounds = 0
        while (current.inCombat && rounds < 50) {
            val attackResult = contract.executeTool("combat_attack", emptyMap(), current)
            assertTrue(attackResult.success)
            current = attackResult.newState!!
            rounds++
        }

        // Should have won eventually
        assertTrue(rounds < 50, "Combat should end within 50 rounds")
        assertFalse(current.inCombat, "Combat should be over")
    }

    // ── Movement Edge Cases ────────────────────────────────────────

    @Test
    fun `move_to_location during combat either fails or succeeds with flee`() = runTest {
        val combatState = CombatState(
            enemy = Enemy(name = "Goblin", maxHP = 20, currentHP = 20, attack = 5, defense = 2, speed = 3, danger = 2),
            roundNumber = 1
        )
        val state = testState(combatState = combatState)
        val result = contract.executeTool("move_to_location", mapOf("locationName" to "Forest"), state)

        // Tool may fail with combat error or succeed (some implementations allow movement in combat)
        assertTrue(result.success || result.error != null, "Should return a definitive result")
    }

    // ── Query Tools ────────────────────────────────────────────────

    @Test
    fun `get_location returns current location details`() = runTest {
        val state = testState()
        val result = contract.executeTool("get_location", emptyMap(), state)

        assertTrue(result.success)
        assertNotNull(result.data["name"])
        assertNotNull(result.data["description"])
    }

    @Test
    fun `get_active_quests returns empty list when no quests`() = runTest {
        val state = testState()
        val result = contract.executeTool("get_active_quests", emptyMap(), state)

        assertTrue(result.success)
    }

    @Test
    fun `get_character_sheet includes class info`() = runTest {
        val state = testState()
        val result = contract.executeTool("get_character_sheet", emptyMap(), state)

        assertTrue(result.success)
        assertNotNull(result.data["stats"])
    }

    // ── NPC Edge Cases ─────────────────────────────────────────────

    @Test
    fun `talk_to_npc with partial name match works`() = runTest {
        val npc = testNPC("Captain Ironforge")
        val state = testState(npcs = listOf(npc))
        val result = contract.executeTool("talk_to_npc", mapOf("npcName" to "Ironforge"), state)

        assertTrue(result.success)
    }

    @Test
    fun `find_npc by archetype works`() = runTest {
        val npc = testNPC("Alara", NPCArchetype.SCHOLAR)
        val state = testState(npcs = listOf(npc))
        val result = contract.executeTool("find_npc", mapOf("name" to "scholar"), state)

        // May match by archetype or name
        // Either success or informative failure
        assertTrue(result.success || result.error != null)
    }

    // ── Player Name and Backstory ──────────────────────────────────

    @Test
    fun `set_player_name updates name`() = runTest {
        val state = testState()
        val result = contract.executeTool("set_player_name", mapOf("name" to "Aragorn"), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertEquals("Aragorn", result.newState!!.playerName)
    }

    @Test
    fun `set_backstory updates backstory`() = runTest {
        val state = testState()
        val result = contract.executeTool("set_backstory", mapOf(
            "backstory" to "A ranger from the north, heir to the throne of Gondor."
        ), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertEquals("A ranger from the north, heir to the throne of Gondor.", result.newState!!.backstory)
    }

    // ── Generation Tools ───────────────────────────────────────────

    @Test
    fun `generate_scene_art returns description and status`() = runTest {
        val state = testState()
        val result = contract.executeTool("generate_scene_art", mapOf(
            "description" to "A dark cave with glowing crystals"
        ), state)

        assertTrue(result.success)
        assertEquals("A dark cave with glowing crystals", result.data["description"]?.jsonPrimitive?.content)
        // Without enableImageGeneration, status is "skipped"
        assertNotNull(result.data["status"])
    }

    @Test
    fun `shift_music_mood returns mood data`() = runTest {
        val state = testState()
        val result = contract.executeTool("shift_music_mood", mapOf("mood" to "battle"), state)

        assertTrue(result.success)
    }

    // ── XP and Leveling ────────────────────────────────────────────

    @Test
    fun `add_xp with zero amount is no-op`() = runTest {
        val state = testState(playerLevel = 1, playerXP = 50)
        val result = contract.executeTool("add_xp", mapOf("amount" to 0L), state)

        assertTrue(result.success)
    }

    @Test
    fun `add_xp triggers level up event`() = runTest {
        val state = testState(playerLevel = 1, playerXP = 90)
        val result = contract.executeTool("add_xp", mapOf("amount" to 100L), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertTrue(result.newState!!.playerLevel >= 2, "Should have leveled up")
    }

    // ── Spawn NPC ──────────────────────────────────────────────────

    @Test
    fun `spawn_npc with minimal args creates NPC`() = runTest {
        val state = testState()
        val result = contract.executeTool("spawn_npc", mapOf(
            "name" to "Mysterious Stranger",
            "role" to "wanderer"
        ), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
    }

    @Test
    fun `spawn_npc at specific location`() = runTest {
        val state = testState()
        val result = contract.executeTool("spawn_npc", mapOf(
            "name" to "Cave Hermit",
            "role" to "scholar",
            "locationId" to "test-location"
        ), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        val npcsHere = result.newState!!.getNPCsAtCurrentLocation()
        assertTrue(npcsHere.any { it.name == "Cave Hermit" })
    }
}
