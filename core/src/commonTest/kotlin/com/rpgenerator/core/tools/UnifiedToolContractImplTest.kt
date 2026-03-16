package com.rpgenerator.core.tools

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.test.TestHelpers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedToolContractImplTest {

    private val contract = UnifiedToolContractImpl()

    private fun testState(
        playerLevel: Int = 1,
        playerXP: Long = 0L,
        playerClass: PlayerClass = PlayerClass.NONE,
        npcs: List<NPC> = emptyList(),
        items: Map<String, InventoryItem> = emptyMap(),
        seedId: String? = null
    ): GameState {
        val sheet = TestHelpers.createTestCharacterSheet(playerLevel, playerXP).let {
            if (playerClass != PlayerClass.NONE) it.chooseInitialClass(playerClass) else it
        }.let {
            if (items.isNotEmpty()) it.copy(inventory = Inventory(items = items)) else it
        }

        val location = TestHelpers.createTestLocation()
        val npcsByLocation = if (npcs.isNotEmpty()) {
            mapOf(location.id to npcs)
        } else emptyMap()

        return GameState(
            gameId = "test-game",
            systemType = SystemType.SYSTEM_INTEGRATION,
            characterSheet = sheet,
            currentLocation = location,
            npcsByLocation = npcsByLocation,
            hasOpeningNarrationPlayed = true,
            seedId = seedId
        )
    }

    private fun testNPC(name: String = "Test NPC", archetype: NPCArchetype = NPCArchetype.MERCHANT): NPC {
        return NPC(
            id = "npc_${name.lowercase().replace(" ", "_")}",
            name = name,
            archetype = archetype,
            locationId = "test-location",
            personality = NPCPersonality(
                traits = listOf("friendly"),
                speechPattern = "Normal speech",
                motivations = listOf("Help travelers")
            ),
            lore = "A test NPC",
            greetingContext = "Hello"
        )
    }

    // ── State Query Tests ─────────────────────────────────────────

    @Test
    fun getPlayerStats_returnsCorrectData() = runTest {
        val state = testState(playerLevel = 5, playerXP = 300L)
        val result = contract.executeTool("get_player_stats", emptyMap(), state)

        assertTrue(result.success)
        assertNull(result.error)
        assertEquals(5, result.data["level"]?.jsonPrimitive?.int)
        assertEquals(300L, result.data["xp"]?.jsonPrimitive?.long)
    }

    @Test
    fun getCharacterSheet_includesAllFields() = runTest {
        val state = testState(playerLevel = 3)
        val result = contract.executeTool("get_character_sheet", emptyMap(), state)

        assertTrue(result.success)
        assertNotNull(result.data["stats"])
        assertNotNull(result.data["skills"])
        assertNotNull(result.data["equipment"])
    }

    @Test
    fun getInventory_returnsItems() = runTest {
        val items = mapOf(
            "potion_1" to InventoryItem("potion_1", "Health Potion", "Heals 50 HP", ItemType.CONSUMABLE)
        )
        val state = testState(items = items)
        val result = contract.executeTool("get_inventory", emptyMap(), state)

        assertTrue(result.success)
        val itemsArray = result.data["items"]?.jsonArray
        assertNotNull(itemsArray)
        assertEquals(1, itemsArray.size)
    }

    @Test
    fun getNPCsHere_returnsNPCsAtLocation() = runTest {
        val npc = testNPC("Grik the Merchant")
        val state = testState(npcs = listOf(npc))
        val result = contract.executeTool("get_npcs_here", emptyMap(), state)

        assertTrue(result.success)
        val npcsArray = result.data["npcs"]?.jsonArray
        assertNotNull(npcsArray)
        assertEquals(1, npcsArray.size)
    }

    // ── Combat Tests ──────────────────────────────────────────────

    @Test
    fun startCombat_createsEnemyWithHP() = runTest {
        val state = testState(playerLevel = 3)
        val result = contract.executeTool("start_combat", mapOf("enemyName" to "Goblin Scout", "danger" to 3), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertNotNull(result.newState!!.combatState)
        assertTrue(result.data["enemyHP"]?.jsonPrimitive?.int?.let { it > 0 } ?: false)
        assertEquals("Goblin Scout", result.data["enemy"]?.jsonPrimitive?.content)
    }

    @Test
    fun startCombat_failsWhenAlreadyInCombat() = runTest {
        val state = testState(playerLevel = 3)
        val result1 = contract.executeTool("start_combat", mapOf("enemyName" to "Rat", "danger" to 1), state)
        assertTrue(result1.success)

        val result2 = contract.executeTool("start_combat", mapOf("enemyName" to "Another Rat", "danger" to 1), result1.newState!!)
        assertFalse(result2.success)
        assertTrue(result2.error?.contains("Already in combat") == true)
    }

    @Test
    fun combatAttack_resolvesOneRound() = runTest {
        val state = testState(playerLevel = 3)
        val startResult = contract.executeTool("start_combat", mapOf("enemyName" to "Goblin", "danger" to 2), state)
        assertTrue(startResult.success)

        val attackResult = contract.executeTool("combat_attack", emptyMap(), startResult.newState!!)
        assertTrue(attackResult.success)
        assertNotNull(attackResult.newState)
        assertTrue(attackResult.events.any { it is GameEvent.CombatLog })
        assertNotNull(attackResult.data["roundNumber"])
        assertNotNull(attackResult.data["enemyHP"])
        assertNotNull(attackResult.data["playerHP"])
    }

    @Test
    fun combatAttack_failsWhenNotInCombat() = runTest {
        val state = testState()
        val result = contract.executeTool("combat_attack", emptyMap(), state)

        assertFalse(result.success)
        assertNotNull(result.error)
    }

    @Test
    fun combatFlee_removesFromCombat() = runTest {
        val state = testState(playerLevel = 3)
        val startResult = contract.executeTool("start_combat", mapOf("enemyName" to "Rat", "danger" to 1), state)

        // Try fleeing multiple times (RNG-based, but with danger 1 should eventually succeed)
        var current = startResult.newState!!
        var fled = false
        for (i in 0 until 20) {
            val fleeResult = contract.executeTool("combat_flee", emptyMap(), current)
            assertTrue(fleeResult.success)
            current = fleeResult.newState!!
            if (fleeResult.data["fled"]?.jsonPrimitive?.content == "true") {
                fled = true
                assertNull(current.combatState)
                break
            }
        }
        // With 20 attempts at high flee chance, should have fled
        assertTrue(fled, "Should have successfully fled within 20 attempts")
    }

    @Test
    fun getCombatStatus_showsEnemyState() = runTest {
        val state = testState(playerLevel = 3)
        val startResult = contract.executeTool("start_combat", mapOf("enemyName" to "Orc", "danger" to 4), state)

        val statusResult = contract.executeTool("get_combat_status", emptyMap(), startResult.newState!!)
        assertTrue(statusResult.success)
        assertEquals("true", statusResult.data["inCombat"]?.jsonPrimitive?.content)
        assertEquals("Orc", statusResult.data["enemy"]?.jsonPrimitive?.content)
    }

    @Test
    fun getCombatStatus_returnsNotInCombat() = runTest {
        val state = testState()
        val result = contract.executeTool("get_combat_status", emptyMap(), state)

        assertTrue(result.success)
        assertEquals("false", result.data["inCombat"]?.jsonPrimitive?.content)
    }

    @Test
    fun attackTargetLegacy_autoStartsAndAttacks() = runTest {
        val state = testState(playerLevel = 3)
        val result = contract.executeTool("attack_target", mapOf("target" to "goblin"), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertTrue(result.events.any { it is GameEvent.CombatLog })
        assertNotNull(result.data["enemyHP"])
        assertNotNull(result.data["playerHP"])
    }

    @Test
    fun useSkill_failsWhenSkillNotFound() = runTest {
        val state = testState()
        val result = contract.executeTool("use_skill", mapOf("skillId" to "nonexistent"), state)

        assertFalse(result.success)
        assertNotNull(result.error)
    }

    // ── Movement Tests ────────────────────────────────────────────

    @Test
    fun moveToLocation_dynamicallyCreatesNewLocation() = runTest {
        val state = testState() // test location has no connections
        val result = contract.executeTool("move_to_location", mapOf("locationName" to "Floor Two"), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertEquals("Floor Two", result.newState!!.currentLocation.name)
        assertEquals("true", result.data["dynamicallyGenerated"]?.jsonPrimitive?.content)
    }

    @Test
    fun moveToLocation_failsWhenBlankName() = runTest {
        val state = testState()
        val result = contract.executeTool("move_to_location", mapOf("locationName" to ""), state)

        assertFalse(result.success)
        assertNotNull(result.error)
    }

    // ── NPC Tests ─────────────────────────────────────────────────

    @Test
    fun talkToNPC_returnsNPCData() = runTest {
        val npc = testNPC("Aria")
        val state = testState(npcs = listOf(npc))
        val result = contract.executeTool("talk_to_npc", mapOf("npcName" to "Aria"), state)

        assertTrue(result.success)
        assertEquals("Aria", result.data["npcName"]?.jsonPrimitive?.content)
    }

    @Test
    fun talkToNPC_failsWhenNPCNotFound() = runTest {
        val state = testState()
        val result = contract.executeTool("talk_to_npc", mapOf("npcName" to "Ghost"), state)

        assertFalse(result.success)
    }

    @Test
    fun findNPC_matchesByName() = runTest {
        val npc = testNPC("Kira the Scholar", NPCArchetype.SCHOLAR)
        val state = testState(npcs = listOf(npc))
        val result = contract.executeTool("find_npc", mapOf("name" to "Kira"), state)

        assertTrue(result.success)
        assertEquals("Kira the Scholar", result.data["name"]?.jsonPrimitive?.content)
    }

    // ── Item Tests ────────────────────────────────────────────────

    @Test
    fun useItem_consumesAndRemoves() = runTest {
        val items = mapOf(
            "potion_1" to InventoryItem("potion_1", "Health Potion", "Heals HP", ItemType.CONSUMABLE, quantity = 3)
        )
        val state = testState(items = items)
        val result = contract.executeTool("use_item", mapOf("itemId" to "potion_1"), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertEquals(2, result.data["remaining"]?.jsonPrimitive?.int)
    }

    @Test
    fun useItem_failsForNonConsumable() = runTest {
        val items = mapOf(
            "quest_item" to InventoryItem("quest_item", "Ancient Key", "A rusty key", ItemType.QUEST_ITEM)
        )
        val state = testState(items = items)
        val result = contract.executeTool("use_item", mapOf("itemId" to "quest_item"), state)

        assertFalse(result.success)
    }

    // ── Quest Tests ───────────────────────────────────────────────

    @Test
    fun acceptQuest_emitsQuestUpdateEvent() = runTest {
        val state = testState()
        val result = contract.executeTool("accept_quest", mapOf("questId" to "quest_1"), state)

        assertTrue(result.success)
        assertTrue(result.events.any { it is GameEvent.QuestUpdate })
    }

    // ── Character Management Tests ────────────────────────────────

    @Test
    fun setClass_appliesClassBonuses() = runTest {
        val state = testState()
        val result = contract.executeTool("set_class", mapOf("className" to "SLAYER"), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertEquals(PlayerClass.SLAYER, result.newState!!.characterSheet.playerClass)
        assertTrue(result.events.any { it is GameEvent.SystemNotification })
    }

    @Test
    fun setClass_fallsBackToAdapterForUnknownClass() = runTest {
        val state = testState()
        val result = contract.executeTool("set_class", mapOf("className" to "BANANA_WARRIOR"), state)

        // set_class accepts ANY name — unknown classes resolve to the ADAPTER archetype
        // for combat math, while preserving the creative class name for display.
        assertTrue(result.success)
        assertNotNull(result.newState)
        assertEquals(PlayerClass.ADAPTER, result.newState!!.characterSheet.playerClass)
    }

    // ── Profession Tests ──────────────────────────────────────────

    @Test
    fun setProfession_appliesProfessionBonuses() = runTest {
        val state = testState()
        val result = contract.executeTool("set_profession", mapOf("professionName" to "Weaponsmith"), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertEquals(Profession.WEAPONSMITH, result.newState!!.characterSheet.profession)
        assertTrue(result.events.any { it is GameEvent.SystemNotification })
    }

    @Test
    fun setProfession_failsForUnknownProfession() = runTest {
        val state = testState()
        val result = contract.executeTool("set_profession", mapOf("professionName" to "BASKET_WEAVER"), state)

        assertFalse(result.success)
    }

    @Test
    fun setProfession_failsIfAlreadyHasProfession() = runTest {
        val state = testState()
        val first = contract.executeTool("set_profession", mapOf("professionName" to "Alchemist"), state)
        assertTrue(first.success)

        val second = contract.executeTool("set_profession", mapOf("professionName" to "Miner"), first.newState!!)
        assertFalse(second.success)
        assertTrue(second.error!!.contains("already has a profession"))
    }

    @Test
    fun setProfession_worksWithEnumName() = runTest {
        val state = testState()
        val result = contract.executeTool("set_profession", mapOf("professionName" to "HERBALIST"), state)

        assertTrue(result.success)
        assertEquals(Profession.HERBALIST, result.newState!!.characterSheet.profession)
    }

    @Test
    fun addXP_levelsUpWhenThresholdReached() = runTest {
        val state = testState(playerLevel = 1, playerXP = 150L)
        val result = contract.executeTool("add_xp", mapOf("amount" to 100L), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertTrue(result.newState!!.playerLevel >= 2)
    }

    // ── Lore Query Tests ──────────────────────────────────────────

    @Test
    fun queryLore_returnsClassesData() = runTest {
        val state = testState()
        val result = contract.executeTool("query_lore", mapOf("category" to "classes"), state)

        assertTrue(result.success)
        assertNotNull(result.data["classes"])
    }

    @Test
    fun queryLore_returnsSkillsData() = runTest {
        val state = testState()
        val result = contract.executeTool("query_lore", mapOf("category" to "skills"), state)

        assertTrue(result.success)
        assertNotNull(result.data["skills"])
    }

    @Test
    fun queryLore_returnsProgressionData() = runTest {
        val state = testState()
        val result = contract.executeTool("query_lore", mapOf("category" to "progression"), state)

        assertTrue(result.success)
        assertNotNull(result.data["grades"])
    }

    @Test
    fun queryLore_returnsWorldDataForSeed() = runTest {
        val state = testState(seedId = "integration")
        val result = contract.executeTool("query_lore", mapOf("category" to "world"), state)

        assertTrue(result.success)
        assertNotNull(result.data["powerSystem"])
    }

    @Test
    fun queryLore_returnsErrorForUnknownCategory() = runTest {
        val state = testState()
        val result = contract.executeTool("query_lore", mapOf("category" to "nonsense"), state)

        assertTrue(result.success) // Still returns, but with error info
        assertNotNull(result.data["error"])
    }

    // ── World Generation Tests ────────────────────────────────────

    @Test
    fun spawnNPC_addsNPCToState() = runTest {
        val state = testState()
        val result = contract.executeTool("spawn_npc", mapOf(
            "name" to "Test Merchant",
            "role" to "merchant",
            "locationId" to "test-location"
        ), state)

        assertTrue(result.success)
        assertNotNull(result.newState)
        assertTrue(result.newState!!.getNPCsAtCurrentLocation().any { it.name == "Test Merchant" })
    }

    // ── Error Handling Tests ──────────────────────────────────────

    @Test
    fun unknownTool_returnsError() = runTest {
        val state = testState()
        val result = contract.executeTool("totally_fake_tool", emptyMap(), state)

        assertFalse(result.success)
        assertTrue(result.error?.contains("Unknown tool") == true)
    }

    // ── Tool Definitions Tests ────────────────────────────────────

    @Test
    fun getToolDefinitions_returnsAllTools() {
        val defs = contract.getToolDefinitions()
        assertTrue(defs.size > 25)
        assertTrue(defs.any { it.name == "start_combat" })
        assertTrue(defs.any { it.name == "combat_attack" })
        assertTrue(defs.any { it.name == "combat_use_skill" })
        assertTrue(defs.any { it.name == "combat_flee" })
        assertTrue(defs.any { it.name == "get_combat_status" })
        assertTrue(defs.any { it.name == "attack_target" })
        assertTrue(defs.any { it.name == "query_lore" })
        assertTrue(defs.any { it.name == "get_player_stats" })
    }
}

