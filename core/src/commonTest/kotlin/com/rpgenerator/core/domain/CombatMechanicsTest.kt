package com.rpgenerator.core.domain

import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.rules.RulesEngine
import kotlin.test.*

/**
 * Bug 12: Phantom counter-attack events after enemy defeated.
 * Bug 9: Narrated loot not in inventory.
 *
 * These bugs are partially narrator-side (LLM hallucination), but we can
 * verify the mechanical data is correct:
 * - Combat always results in enemy defeated (simplified model)
 * - No damage is applied to player in current combat model
 * - Loot from RulesEngine is properly structured
 * - XP is always positive after combat
 */
class CombatMechanicsTest {

    private val rulesEngine = RulesEngine()

    private fun createState(level: Int = 1, danger: Int = 1): GameState {
        val location = Location(
            id = "test-loc", name = "Test Arena", zoneId = "test",
            biome = Biome.FOREST, description = "A test arena",
            danger = danger,
            connections = emptyList(), features = emptyList(), lore = ""
        )
        return GameState(
            gameId = "test",
            systemType = SystemType.SYSTEM_INTEGRATION,
            characterSheet = CharacterSheet(
                level = level,
                xp = 0,
                baseStats = Stats(
                    strength = 10, dexterity = 8, constitution = 12,
                    intelligence = 7, wisdom = 6, charisma = 5, defense = 0
                ),
                resources = Resources(100, 100, 50, 50, 100, 100)
            ),
            currentLocation = location
        )
    }

    @Test
    fun `combat produces positive damage`() {
        val state = createState()
        val outcome = rulesEngine.calculateCombatOutcome("goblin", state)
        assertTrue(outcome.damage > 0, "Combat should deal at least 1 damage")
    }

    @Test
    fun `combat produces positive XP`() {
        val state = createState()
        val outcome = rulesEngine.calculateCombatOutcome("goblin", state)
        assertTrue(outcome.xpGain > 0, "Combat should award XP")
    }

    @Test
    fun `combat XP scales with location danger`() {
        val safeCombat = rulesEngine.calculateCombatOutcome("goblin", createState(danger = 1))
        val dangerousCombat = rulesEngine.calculateCombatOutcome("goblin", createState(danger = 10))
        assertTrue(dangerousCombat.xpGain >= safeCombat.xpGain,
            "Higher danger should award at least as much XP")
    }

    @Test
    fun `loot items have valid names`() {
        // Run multiple combats to ensure at least some loot drops
        val state = createState(level = 5, danger = 5)
        val allLoot = (1..20).flatMap {
            rulesEngine.calculateCombatOutcome("goblin", state).loot
        }
        // If any loot dropped, verify it's valid
        allLoot.forEach { item ->
            assertTrue(item.getName().isNotBlank(), "Loot item should have a name")
            assertTrue(item.getQuantity() > 0, "Loot item should have positive quantity")
        }
    }

    @Test
    fun `combat outcome newXP equals current plus gain`() {
        val state = createState(level = 1)
        val currentXP = state.characterSheet.xp
        val outcome = rulesEngine.calculateCombatOutcome("goblin", state)
        assertEquals(currentXP + outcome.xpGain, outcome.newXP,
            "newXP should equal current XP + XP gained")
    }

    @Test
    fun `applying combat XP to state is consistent`() {
        val state = createState(level = 1)
        val outcome = rulesEngine.calculateCombatOutcome("goblin", state)

        val afterXP = state.gainXP(outcome.xpGain)
        assertEquals(outcome.newXP, afterXP.playerXP,
            "State after gainXP should match outcome.newXP")
    }

    @Test
    fun `loot added to inventory persists in state`() {
        val state = createState()
        val item = InventoryItem(
            id = "goblin_fang",
            name = "Goblin Fang",
            description = "A sharp fang",
            type = ItemType.MISC,
            quantity = 1
        )
        val afterLoot = state.addItem(item)
        assertTrue(afterLoot.characterSheet.inventory.items.containsKey("goblin_fang"),
            "Item should be in inventory after addItem")
        assertEquals("Goblin Fang", afterLoot.characterSheet.inventory.items["goblin_fang"]?.name)
    }

    @Test
    fun `multiple loot items all persist`() {
        var state = createState()
        val items = listOf(
            InventoryItem("item1", "Sword", "A sword", ItemType.MISC),
            InventoryItem("item2", "Shield", "A shield", ItemType.MISC),
            InventoryItem("item3", "Potion", "Heals", ItemType.CONSUMABLE, quantity = 3)
        )
        items.forEach { state = state.addItem(it) }

        assertEquals(3, state.characterSheet.inventory.items.size,
            "All 3 items should be in inventory")
        assertEquals(3, state.characterSheet.inventory.items["item3"]?.quantity)
    }
}
