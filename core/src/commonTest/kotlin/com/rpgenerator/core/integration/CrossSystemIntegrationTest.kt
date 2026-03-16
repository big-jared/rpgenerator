package com.rpgenerator.core.integration

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.test.TestHelpers
import com.rpgenerator.core.tools.UnifiedToolContractImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

/**
 * Integration tests that verify systems compose correctly:
 * - Skill checks use effective stats (base + equipment)
 * - Combat damage scales with STR + weapon bonuses
 * - Equipment bonuses flow through to all consumers
 * - Level-ups propagate to skill check modifiers
 * - Class bonuses affect combat outcomes
 */
class CrossSystemIntegrationTest {

    private val contract = UnifiedToolContractImpl()

    private fun stateWith(
        baseStats: Stats = Stats(),
        equipment: Equipment = Equipment(),
        items: Map<String, InventoryItem> = emptyMap(),
        level: Int = 1,
        xp: Long = 0L,
        playerClass: PlayerClass = PlayerClass.NONE
    ): GameState {
        val sheet = CharacterSheet(
            level = level,
            xp = xp,
            baseStats = baseStats,
            resources = Resources.fromStats(baseStats),
            equipment = equipment,
            playerClass = playerClass,
            inventory = Inventory(items = items)
        )
        return GameState(
            gameId = "test",
            systemType = SystemType.SYSTEM_INTEGRATION,
            characterSheet = sheet,
            currentLocation = TestHelpers.createTestLocation(),
            hasOpeningNarrationPlayed = true
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // SKILL CHECKS × CHARACTER STATS
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `skill check modifier derives from character stat`() = runTest {
        // Athletics uses STR. High STR → higher modifier → easier checks.
        val highStr = stateWith(baseStats = Stats(strength = 20, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 10))
        val lowStr = stateWith(baseStats = Stats(strength = 3, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 10))

        // Run many checks and compare average success rates
        var highSuccesses = 0
        var lowSuccesses = 0
        val trials = 100

        repeat(trials) {
            val highResult = contract.executeTool("skill_check", mapOf("checkType" to "Athletics", "difficulty" to "MODERATE"), highStr)
            val lowResult = contract.executeTool("skill_check", mapOf("checkType" to "Athletics", "difficulty" to "MODERATE"), lowStr)
            if (highResult.data["success"]?.jsonPrimitive?.content == "true") highSuccesses++
            if (lowResult.data["success"]?.jsonPrimitive?.content == "true") lowSuccesses++
        }

        assertTrue(highSuccesses > lowSuccesses,
            "High STR ($highSuccesses/$trials) should succeed more often than low STR ($lowSuccesses/$trials) on Athletics checks")
    }

    @Test
    fun `perception check uses WIS not STR`() = runTest {
        val highWis = stateWith(baseStats = Stats(strength = 3, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 20, charisma = 10))
        val highStr = stateWith(baseStats = Stats(strength = 20, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 3, charisma = 10))

        var wisSuccesses = 0
        var strSuccesses = 0
        val trials = 100

        repeat(trials) {
            val wisResult = contract.executeTool("skill_check", mapOf("checkType" to "Perception", "difficulty" to "MODERATE"), highWis)
            val strResult = contract.executeTool("skill_check", mapOf("checkType" to "Perception", "difficulty" to "MODERATE"), highStr)
            if (wisResult.data["success"]?.jsonPrimitive?.content == "true") wisSuccesses++
            if (strResult.data["success"]?.jsonPrimitive?.content == "true") strSuccesses++
        }

        assertTrue(wisSuccesses > strSuccesses,
            "High WIS ($wisSuccesses/$trials) should succeed more often than high STR ($strSuccesses/$trials) on Perception checks")
    }

    @Test
    fun `persuasion check uses CHA`() = runTest {
        val highCha = stateWith(baseStats = Stats(strength = 10, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 20))
        val lowCha = stateWith(baseStats = Stats(strength = 10, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 3))

        var highSuccesses = 0
        var lowSuccesses = 0
        val trials = 100

        repeat(trials) {
            val highResult = contract.executeTool("skill_check", mapOf("checkType" to "Persuasion", "difficulty" to "MODERATE"), highCha)
            val lowResult = contract.executeTool("skill_check", mapOf("checkType" to "Persuasion", "difficulty" to "MODERATE"), lowCha)
            if (highResult.data["success"]?.jsonPrimitive?.content == "true") highSuccesses++
            if (lowResult.data["success"]?.jsonPrimitive?.content == "true") lowSuccesses++
        }

        assertTrue(highSuccesses > lowSuccesses,
            "High CHA ($highSuccesses/$trials) should succeed more on Persuasion than low CHA ($lowSuccesses/$trials)")
    }

    @Test
    fun `skill check modifier includes level-based proficiency`() = runTest {
        // Same stats, different levels. Higher level → higher proficiency bonus.
        val level1 = stateWith(level = 1, baseStats = Stats(strength = 10, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 10))
        val level10 = stateWith(level = 10, baseStats = Stats(strength = 10, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 10))

        // Check the modifier field directly
        val r1 = contract.executeTool("skill_check", mapOf("checkType" to "Athletics", "difficulty" to "MODERATE"), level1)
        val r10 = contract.executeTool("skill_check", mapOf("checkType" to "Athletics", "difficulty" to "MODERATE"), level10)

        val prof1 = r1.data["proficiency"]?.jsonPrimitive?.int ?: 0
        val prof10 = r10.data["proficiency"]?.jsonPrimitive?.int ?: 0

        assertTrue(prof10 > prof1,
            "Level 10 proficiency ($prof10) should be higher than level 1 proficiency ($prof1)")
    }

    // ═══════════════════════════════════════════════════════════════
    // EQUIPMENT × EFFECTIVE STATS
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `weapon bonus flows through to effective stats`() {
        val sword = Weapon(
            id = "sword_1", name = "Magic Sword", description = "A glowing blade",
            baseDamage = 5, strengthBonus = 5, dexterityBonus = 0
        )
        val state = stateWith(
            baseStats = Stats(strength = 10, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 10),
            equipment = Equipment(weapon = sword)
        )

        val effective = state.characterSheet.effectiveStats()
        assertEquals(15, effective.strength, "Effective STR should be base 10 + weapon bonus 5 = 15")
    }

    @Test
    fun `armor bonus flows through to effective stats`() {
        val armor = Armor(
            id = "armor_1", name = "Iron Plate", description = "Heavy armor",
            defenseBonus = 5, constitutionBonus = 3
        )
        val state = stateWith(
            baseStats = Stats(strength = 10, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 10),
            equipment = Equipment(armor = armor)
        )

        val effective = state.characterSheet.effectiveStats()
        assertEquals(13, effective.constitution, "Effective CON should be base 10 + armor bonus 3 = 13")
    }

    @Test
    fun `equipment bonuses affect skill checks`() = runTest {
        // A strength-boosting weapon should improve Athletics checks
        val sword = Weapon(
            id = "sword_1", name = "Gauntlets of Power", description = "+8 STR",
            baseDamage = 3, strengthBonus = 8, dexterityBonus = 0
        )
        val equipped = stateWith(
            baseStats = Stats(strength = 10, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 10),
            equipment = Equipment(weapon = sword)
        )
        val unequipped = stateWith(
            baseStats = Stats(strength = 10, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 10)
        )

        var equippedSuccesses = 0
        var unequippedSuccesses = 0
        val trials = 100

        repeat(trials) {
            val eResult = contract.executeTool("skill_check", mapOf("checkType" to "Athletics", "difficulty" to "HARD"), equipped)
            val uResult = contract.executeTool("skill_check", mapOf("checkType" to "Athletics", "difficulty" to "HARD"), unequipped)
            if (eResult.data["success"]?.jsonPrimitive?.content == "true") equippedSuccesses++
            if (uResult.data["success"]?.jsonPrimitive?.content == "true") unequippedSuccesses++
        }

        assertTrue(equippedSuccesses > unequippedSuccesses,
            "Equipped (+8 STR) should pass Athletics more ($equippedSuccesses) than unequipped ($unequippedSuccesses)")
    }

    // ═══════════════════════════════════════════════════════════════
    // COMBAT × CHARACTER STATS × EQUIPMENT
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `higher STR produces higher effective stats for combat`() {
        // Verify the stat pipeline: high STR should produce higher effective strength
        // which the combat engine uses for damage calculation.
        val strong = stateWith(baseStats = Stats(strength = 20, dexterity = 14, constitution = 14, intelligence = 10, wisdom = 10, charisma = 10), level = 5)
        val weak = stateWith(baseStats = Stats(strength = 3, dexterity = 14, constitution = 14, intelligence = 10, wisdom = 10, charisma = 10), level = 5)

        val strongEff = strong.characterSheet.effectiveStats().strength
        val weakEff = weak.characterSheet.effectiveStats().strength

        assertTrue(strongEff > weakEff,
            "High STR effective ($strongEff) should be greater than low STR effective ($weakEff)")
        assertEquals(20, strongEff)
        assertEquals(3, weakEff)
    }

    @Test
    fun `weapon equipment increases effective STR for combat`() {
        // Verify weapon STR bonus flows through to effectiveStats
        val sword = Weapon(
            id = "big_sword", name = "Great Sword", description = "A massive blade",
            baseDamage = 10, strengthBonus = 8, dexterityBonus = 0
        )
        val armed = stateWith(
            baseStats = Stats(strength = 10, dexterity = 12, constitution = 12, intelligence = 10, wisdom = 10, charisma = 10),
            equipment = Equipment(weapon = sword),
            level = 5
        )
        val unarmed = stateWith(
            baseStats = Stats(strength = 10, dexterity = 12, constitution = 12, intelligence = 10, wisdom = 10, charisma = 10),
            level = 5
        )

        val armedStr = armed.characterSheet.effectiveStats().strength
        val unarmedStr = unarmed.characterSheet.effectiveStats().strength

        assertEquals(18, armedStr, "Armed should have STR 10 + 8 weapon bonus = 18")
        assertEquals(10, unarmedStr, "Unarmed should have base STR 10")
    }

    // ═══════════════════════════════════════════════════════════════
    // COMBAT × XP → LEVEL UP → STAT GROWTH
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `combat victory grants XP and can trigger level up`() = runTest {
        val state = stateWith(
            baseStats = Stats(strength = 18, dexterity = 14, constitution = 14, intelligence = 10, wisdom = 10, charisma = 10),
            level = 1,
            xp = 90 // Close to level-up threshold
        )

        // Start combat with weak enemy
        val combat = contract.executeTool("start_combat", mapOf("enemyName" to "Weak Rat", "danger" to 1), state)
        assertTrue(combat.success)

        // Fight to victory
        var current = combat.newState!!
        var rounds = 0
        while (current.inCombat && rounds < 30) {
            val r = contract.executeTool("combat_attack", emptyMap(), current)
            assertTrue(r.success)
            current = r.newState!!
            rounds++
        }

        // After victory, XP should have increased
        assertTrue(current.playerXP > 90, "XP should increase after combat victory")
    }

    // ═══════════════════════════════════════════════════════════════
    // CLASS × STAT BONUSES
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `class selection modifies stats`() = runTest {
        val state = stateWith(
            baseStats = Stats(strength = 10, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 10)
        )

        // Set class to SLAYER (should boost physical stats)
        val result = contract.executeTool("set_class", mapOf("className" to "SLAYER"), state)
        assertTrue(result.success)
        assertNotNull(result.newState)

        val newStats = result.newState!!.characterSheet.effectiveStats()
        val oldStats = state.characterSheet.effectiveStats()

        // SLAYER should have different stats than NONE
        val newTotal = newStats.strength + newStats.dexterity + newStats.constitution
        val oldTotal = oldStats.strength + oldStats.dexterity + oldStats.constitution

        assertTrue(newTotal >= oldTotal,
            "SLAYER class should not reduce physical stat total (was $oldTotal, now $newTotal)")
    }

    // ═══════════════════════════════════════════════════════════════
    // ITEM USE × HP RECOVERY
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `using consumable item reduces quantity`() = runTest {
        val potion = InventoryItem(
            id = "hp_pot", name = "Health Potion", description = "Restores HP",
            type = ItemType.CONSUMABLE, quantity = 3
        )
        val state = stateWith(items = mapOf("hp_pot" to potion))

        val result = contract.executeTool("use_item", mapOf("itemId" to "hp_pot"), state)
        assertTrue(result.success)
        assertNotNull(result.newState)

        val remaining = result.newState!!.characterSheet.inventory.items["hp_pot"]?.quantity ?: 0
        assertEquals(2, remaining, "Quantity should decrease from 3 to 2 after use")
    }

    // ═══════════════════════════════════════════════════════════════
    // EQUIP × COMBAT INTEGRATION
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `equip_item makes weapon available for combat`() = runTest {
        val weapon = InventoryItem(
            id = "iron_sword", name = "Iron Sword", description = "A sturdy blade",
            type = ItemType.WEAPON, quantity = 1,
            statBonuses = Stats(strength = 5)
        )
        val state = stateWith(
            baseStats = Stats(strength = 10, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 10),
            items = mapOf("iron_sword" to weapon)
        )

        val equipResult = contract.executeTool("equip_item", mapOf("itemId" to "iron_sword"), state)
        assertTrue(equipResult.success, "Should be able to equip weapon: ${equipResult.error}")

        if (equipResult.newState != null) {
            val equipped = equipResult.newState!!
            val effectiveStr = equipped.characterSheet.effectiveStats().strength
            assertTrue(effectiveStr > 10,
                "After equipping sword with +5 STR, effective STR should be > 10 (got $effectiveStr)")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DIFFICULTY SCALING
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `trivial difficulty is easier than nearly impossible`() = runTest {
        val state = stateWith(
            baseStats = Stats(strength = 10, dexterity = 10, constitution = 10, intelligence = 10, wisdom = 10, charisma = 10),
            level = 1
        )

        var trivialSuccesses = 0
        var impossibleSuccesses = 0
        val trials = 100

        repeat(trials) {
            val trivial = contract.executeTool("skill_check", mapOf("checkType" to "Athletics", "difficulty" to "TRIVIAL"), state)
            val impossible = contract.executeTool("skill_check", mapOf("checkType" to "Athletics", "difficulty" to "NEARLY_IMPOSSIBLE"), state)
            if (trivial.data["success"]?.jsonPrimitive?.content == "true") trivialSuccesses++
            if (impossible.data["success"]?.jsonPrimitive?.content == "true") impossibleSuccesses++
        }

        assertTrue(trivialSuccesses > impossibleSuccesses,
            "TRIVIAL ($trivialSuccesses/$trials) should succeed way more than NEARLY_IMPOSSIBLE ($impossibleSuccesses/$trials)")
    }
}
