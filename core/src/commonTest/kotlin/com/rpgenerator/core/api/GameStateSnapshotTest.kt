package com.rpgenerator.core.api

import com.rpgenerator.core.domain.*
import com.rpgenerator.core.domain.Quest as DomainQuest
import com.rpgenerator.core.skill.AcquisitionSource
import com.rpgenerator.core.skill.Skill
import com.rpgenerator.core.skill.SkillRarity
import kotlin.test.*

class GameStateSnapshotTest {

    private fun createTestState(
        playerClass: PlayerClass = PlayerClass.NONE,
        skills: List<Skill> = emptyList(),
        inventory: Inventory = Inventory(),
        quests: Map<String, DomainQuest> = emptyMap(),
        location: Location = Location(
            id = "test-loc",
            name = "Test Location",
            zoneId = "test-zone",
            biome = Biome.FOREST,
            description = "A test location",
            danger = 1,
            connections = emptyList(),
            features = emptyList(),
            lore = ""
        )
    ): GameState {
        return GameState(
            gameId = "test-game",
            systemType = SystemType.SYSTEM_INTEGRATION,
            characterSheet = CharacterSheet(
                level = 1,
                xp = 0,
                baseStats = Stats(),
                resources = Resources(100, 100, 50, 50, 100, 100),
                inventory = inventory,
                skills = skills,
                playerClass = playerClass
            ),
            currentLocation = location,
            playerName = "TestPlayer",
            backstory = "A test character",
            activeQuests = quests
        )
    }

    private fun createTestSkill(
        id: String = "test_skill",
        name: String = "Test Skill",
        isActive: Boolean = true
    ): Skill {
        return Skill(
            id = id,
            name = name,
            description = "A test skill",
            rarity = SkillRarity.COMMON,
            manaCost = 0,
            energyCost = 10,
            healthCost = 0,
            baseCooldown = 0,
            currentCooldown = 0,
            level = 1,
            maxLevel = 10,
            currentXP = 0,
            effects = emptyList(),
            isActive = isActive,
            acquisitionSource = AcquisitionSource.ClassStarter("test")
        )
    }

    // ── Bug 8: Item rarity always COMMON ──

    @Test
    fun `item rarity is preserved in inventory`() {
        val item = InventoryItem(
            id = "rare-sword",
            name = "Rare Sword",
            description = "A rare sword",
            type = ItemType.MISC,
            rarity = ItemRarity.RARE
        )
        val state = createTestState(inventory = Inventory(items = mapOf(item.id to item)))
        val snapshot = createSnapshot(state)

        assertEquals(ItemRarity.RARE, snapshot.inventory.first().rarity,
            "Item rarity should be RARE, not COMMON")
    }

    @Test
    fun `multiple items preserve their distinct rarities`() {
        val common = InventoryItem("hp-pot", "Health Potion", "Heals", ItemType.CONSUMABLE, rarity = ItemRarity.COMMON)
        val epic = InventoryItem("epic-staff", "Epic Staff", "Powerful", ItemType.MISC, rarity = ItemRarity.EPIC)
        val legendary = InventoryItem("legendary-ring", "Ring of Power", "Legendary", ItemType.MISC, rarity = ItemRarity.LEGENDARY)
        val inv = Inventory(items = mapOf(common.id to common, epic.id to epic, legendary.id to legendary))
        val state = createTestState(inventory = inv)
        val snapshot = createSnapshot(state)

        val rarities = snapshot.inventory.associate { it.id to it.rarity }
        assertEquals(ItemRarity.COMMON, rarities["hp-pot"])
        assertEquals(ItemRarity.EPIC, rarities["epic-staff"])
        assertEquals(ItemRarity.LEGENDARY, rarities["legendary-ring"])
    }

    // ── Bug 2: No class on character sheet ──

    @Test
    fun `player class is exposed in snapshot`() {
        val state = createTestState(playerClass = PlayerClass.ADAPTER)
        val snapshot = createSnapshot(state)

        assertEquals("Adapter", snapshot.playerStats.playerClass,
            "PlayerStats should include the player's class name")
    }

    @Test
    fun `player class defaults to empty string when NONE`() {
        val state = createTestState(playerClass = PlayerClass.NONE)
        val snapshot = createSnapshot(state)

        assertEquals("", snapshot.playerStats.playerClass,
            "PlayerStats should show empty string for NONE class")
    }

    // ── Bug 3: Skills appear in snapshot ──

    @Test
    fun `skills are included in snapshot`() {
        val skill = createTestSkill(id = "quick_strike", name = "Quick Strike")
        val state = createTestState(skills = listOf(skill))
        val snapshot = createSnapshot(state)

        assertEquals(1, snapshot.skills.size, "Snapshot should include skills")
        assertEquals("quick_strike", snapshot.skills.first().id)
        assertEquals("Quick Strike", snapshot.skills.first().name)
    }

    @Test
    fun `snapshot with no skills has empty skills list`() {
        val state = createTestState(skills = emptyList())
        val snapshot = createSnapshot(state)

        assertTrue(snapshot.skills.isEmpty(), "Snapshot should have empty skills list")
    }

    // ── Helper to create snapshot the same way GameImpl does ──

    private fun createSnapshot(state: GameState): GameStateSnapshot {
        return GameStateSnapshot(
            playerStats = PlayerStats(
                name = state.playerName,
                level = state.playerLevel,
                experience = state.playerXP,
                experienceToNextLevel = state.characterSheet.xpToNextLevel(),
                stats = mapOf(
                    "strength" to state.characterSheet.effectiveStats().strength,
                    "dexterity" to state.characterSheet.effectiveStats().dexterity,
                    "constitution" to state.characterSheet.effectiveStats().constitution,
                    "intelligence" to state.characterSheet.effectiveStats().intelligence,
                    "wisdom" to state.characterSheet.effectiveStats().wisdom,
                    "charisma" to state.characterSheet.effectiveStats().charisma,
                    "defense" to state.characterSheet.effectiveStats().defense
                ),
                health = state.characterSheet.resources.currentHP,
                maxHealth = state.characterSheet.resources.maxHP,
                energy = state.characterSheet.resources.currentEnergy,
                maxEnergy = state.characterSheet.resources.maxEnergy,
                backstory = state.backstory,
                playerClass = if (state.characterSheet.playerClass == PlayerClass.NONE) "" else state.characterSheet.playerClass.displayName
            ),
            location = state.currentLocation.name,
            currentScene = state.currentLocation.description,
            inventory = state.characterSheet.inventory.items.values.map { item ->
                Item(
                    id = item.id,
                    name = item.name,
                    description = item.description,
                    quantity = item.quantity,
                    rarity = item.rarity
                )
            },
            activeQuests = state.activeQuests.values.map { quest ->
                Quest(
                    id = quest.id,
                    name = quest.name,
                    description = quest.description,
                    status = if (quest.isComplete()) QuestStatus.COMPLETED else QuestStatus.IN_PROGRESS,
                    objectives = quest.objectives.map { obj ->
                        QuestObjective(
                            description = obj.description,
                            completed = obj.isComplete()
                        )
                    }
                )
            },
            skills = state.characterSheet.skills.map { skill ->
                SkillInfo(
                    id = skill.id,
                    name = skill.name,
                    level = skill.level,
                    isActive = skill.isActive
                )
            },
            recentEvents = emptyList()
        )
    }
}
