package com.rpgenerator.core.persistence

import com.rpgenerator.core.api.CharacterCreationOptions
import com.rpgenerator.core.api.Difficulty
import com.rpgenerator.core.api.GameConfig
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.domain.Quest
import com.rpgenerator.core.domain.QuestObjective
import com.rpgenerator.core.test.TestHelpers
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Persistence round-trip tests for GameRepository.
 * Uses an in-memory SQLite database — no disk I/O.
 */
class GameRepositoryTest {

    private fun createTestRepo(): Pair<GameRepository, GameDatabase> {
        val driver = createInMemoryDriver()
        val database = GameDatabase(driver)
        return GameRepository(database) to database
    }

    private fun createConfig(
        systemType: SystemType = SystemType.SYSTEM_INTEGRATION,
        difficulty: Difficulty = Difficulty.NORMAL,
        name: String = "Test Hero"
    ) = GameConfig(
        systemType = systemType,
        difficulty = difficulty,
        characterCreation = CharacterCreationOptions(name = name)
    )

    // ── Game CRUD ──────────────────────────────────────────────────

    @Test
    fun `createGame and getAllGames round-trips`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1")
        val config = createConfig()

        repo.createGame("game-1", "Test Hero", config, state)

        val games = repo.getAllGames()
        assertEquals(1, games.size)
        assertEquals("game-1", games[0].id)
        assertEquals("Test Hero", games[0].playerName)
        assertEquals(SystemType.SYSTEM_INTEGRATION, games[0].systemType)
        assertEquals(1, games[0].level)
    }

    @Test
    fun `getGame returns null for nonexistent ID`() = runTest {
        val (repo, _) = createTestRepo()
        assertNull(repo.getGame("nonexistent"))
    }

    @Test
    fun `deleteGame removes all data`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1")
        repo.createGame("game-1", "Hero", createConfig(), state)

        repo.deleteGame("game-1")

        assertNull(repo.getGame("game-1"))
        assertEquals(0, repo.getAllGames().size)
    }

    // ── Game State Persistence ─────────────────────────────────────

    @Test
    fun `saveGame and loadGameState round-trips`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1", playerLevel = 5, playerXP = 500L)
        repo.createGame("game-1", "Hero", createConfig(), state)

        // Modify and save
        val updated = state.copy(
            characterSheet = state.characterSheet.gainXP(200),
            playerName = "Updated Hero"
        )
        repo.saveGame("game-1", updated, playtime = 3600)

        // Load back
        val loaded = repo.loadGameState("game-1")
        assertNotNull(loaded)
        assertEquals("Updated Hero", loaded.playerName)
        assertEquals(700L, loaded.playerXP)
    }

    @Test
    fun `loadGameState returns null for nonexistent game`() = runTest {
        val (repo, _) = createTestRepo()
        assertNull(repo.loadGameState("nonexistent"))
    }

    @Test
    fun `saveGame updates metadata level and playtime`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1", playerLevel = 1)
        repo.createGame("game-1", "Hero", createConfig(), state)

        val leveled = state.copy(
            characterSheet = state.characterSheet.gainXP(500)
        )
        repo.saveGame("game-1", leveled, playtime = 7200)

        val info = repo.getGame("game-1")
        assertNotNull(info)
        assertTrue(info.level > 1, "Level should have increased")
        assertEquals(7200L, info.playtime)
    }

    // ── NPC Persistence ────────────────────────────────────────────

    @Test
    fun `saveNPC and getNPC round-trips`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1")
        repo.createGame("game-1", "Hero", createConfig(), state)

        val npc = NPC(
            id = "npc_grik",
            name = "Grik",
            archetype = NPCArchetype.MERCHANT,
            locationId = "test-location",
            personality = NPCPersonality(
                traits = listOf("grumpy", "honest"),
                speechPattern = "Clipped sentences",
                motivations = listOf("Make gold")
            ),
            lore = "A veteran merchant"
        )

        repo.saveNPC("game-1", npc)
        val loaded = repo.getNPC("game-1", "npc_grik")
        assertNotNull(loaded)
        assertEquals("Grik", loaded.name)
        assertEquals(NPCArchetype.MERCHANT, loaded.archetype)
        assertEquals("test-location", loaded.locationId)
    }

    @Test
    fun `getAllNPCs returns all NPCs for a game`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1")
        repo.createGame("game-1", "Hero", createConfig(), state)

        val npc1 = NPC("npc_1", "Alice", NPCArchetype.GUARD, "loc-a",
            personality = NPCPersonality(listOf("brave"), "Direct", listOf("Protect")), lore = "")
        val npc2 = NPC("npc_2", "Bob", NPCArchetype.SCHOLAR, "loc-b",
            personality = NPCPersonality(listOf("curious"), "Verbose", listOf("Learn")), lore = "")

        repo.saveNPC("game-1", npc1)
        repo.saveNPC("game-1", npc2)

        val all = repo.getAllNPCs("game-1")
        assertEquals(2, all.size)
    }

    @Test
    fun `getNPCsAtLocation filters by location`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1")
        repo.createGame("game-1", "Hero", createConfig(), state)

        val npc1 = NPC("npc_1", "Alice", NPCArchetype.GUARD, "tavern",
            personality = NPCPersonality(listOf("brave"), "", listOf("Protect")), lore = "")
        val npc2 = NPC("npc_2", "Bob", NPCArchetype.SCHOLAR, "library",
            personality = NPCPersonality(listOf("curious"), "", listOf("Learn")), lore = "")

        repo.saveNPC("game-1", npc1)
        repo.saveNPC("game-1", npc2)

        val tavernNPCs = repo.getNPCsAtLocation("game-1", "tavern")
        assertEquals(1, tavernNPCs.size)
        assertEquals("Alice", tavernNPCs[0].name)
    }

    @Test
    fun `deleteNPC removes NPC`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1")
        repo.createGame("game-1", "Hero", createConfig(), state)

        val npc = NPC("npc_1", "Alice", NPCArchetype.GUARD, "loc",
            personality = NPCPersonality(listOf(), "", listOf()), lore = "")
        repo.saveNPC("game-1", npc)
        repo.deleteNPC("game-1", "npc_1")

        assertNull(repo.getNPC("game-1", "npc_1"))
    }

    // ── Quest Persistence ──────────────────────────────────────────

    @Test
    fun `saveQuest and getQuest round-trips`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1")
        repo.createGame("game-1", "Hero", createConfig(), state)

        val quest = Quest(
            id = "quest_1",
            name = "Slay the Dragon",
            description = "A dragon terrorizes the village",
            type = QuestType.MAIN_STORY,
            objectives = listOf(
                QuestObjective("find_lair", "Find the dragon's lair", ObjectiveType.EXPLORE, "dragon_lair"),
                QuestObjective("slay_dragon", "Defeat the dragon", ObjectiveType.KILL, "dragon")
            ),
            rewards = QuestRewards(xp = 500, gold = 100)
        )

        repo.saveQuest("game-1", quest)
        val loaded = repo.getQuest("game-1", "quest_1")
        assertNotNull(loaded)
        assertEquals("Slay the Dragon", loaded.name)
        assertEquals(2, loaded.objectives.size)
    }

    @Test
    fun `getQuestsByStatus filters correctly`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1")
        repo.createGame("game-1", "Hero", createConfig(), state)

        val active = Quest(
            id = "q1", name = "Active Quest", description = "",
            type = QuestType.SIDE_QUEST,
            objectives = listOf(QuestObjective("do_it", "Do thing", ObjectiveType.EXPLORE, "target")),
            rewards = QuestRewards(xp = 100),
            status = QuestProgressStatus.IN_PROGRESS
        )
        val completed = Quest(
            id = "q2", name = "Done Quest", description = "",
            type = QuestType.SIDE_QUEST,
            objectives = listOf(QuestObjective("did_it", "Did thing", ObjectiveType.EXPLORE, "target", currentProgress = 1, targetProgress = 1)),
            rewards = QuestRewards(xp = 50),
            status = QuestProgressStatus.COMPLETED
        )

        repo.saveQuest("game-1", active)
        repo.saveQuest("game-1", completed)

        val inProgress = repo.getQuestsByStatus("game-1", QuestProgressStatus.IN_PROGRESS)
        assertEquals(1, inProgress.size)
        assertEquals("Active Quest", inProgress[0].name)

        val done = repo.getQuestsByStatus("game-1", QuestProgressStatus.COMPLETED)
        assertEquals(1, done.size)
        assertEquals("Done Quest", done[0].name)
    }

    // ── Custom Location Persistence ────────────────────────────────

    @Test
    fun `saveCustomLocation and getCustomLocation round-trips`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1")
        repo.createGame("game-1", "Hero", createConfig(), state)

        val location = Location(
            id = "custom_cave",
            name = "Hidden Cave",
            zoneId = "wilderness",
            biome = Biome.CAVE,
            description = "A dark cave behind a waterfall",
            danger = 4,
            connections = listOf("forest_path"),
            features = listOf("Waterfall", "Stalactites"),
            lore = "Legends speak of treasure within"
        )

        repo.saveCustomLocation("game-1", location)
        val loaded = repo.getCustomLocation("game-1", "custom_cave")
        assertNotNull(loaded)
        assertEquals("Hidden Cave", loaded.name)
        assertEquals(Biome.CAVE, loaded.biome)
        assertEquals(4, loaded.danger)
    }

    // ── Event Logging ──────────────────────────────────────────────

    @Test
    fun `logEvent and getRecentEvents round-trips`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1")
        repo.createGame("game-1", "Hero", createConfig(), state)

        repo.logEvent("game-1", GameEvent.NarratorText("You enter the dungeon."))
        repo.logEvent("game-1", GameEvent.SystemNotification("Level up!"))
        repo.logEvent("game-1", GameEvent.NarratorText("A goblin appears."))

        val events = repo.getRecentEvents("game-1", limit = 10)
        assertEquals(3, events.size)
    }

    @Test
    fun `getRecentEvents respects limit`() = runTest {
        val (repo, _) = createTestRepo()
        val state = TestHelpers.createTestGameState(gameId = "game-1")
        repo.createGame("game-1", "Hero", createConfig(), state)

        repeat(10) { i ->
            repo.logEvent("game-1", GameEvent.NarratorText("Event $i"))
        }

        val limited = repo.getRecentEvents("game-1", limit = 3)
        assertEquals(3, limited.size)
    }

    // ── Full Save/Load Integration ─────────────────────────────────

    @Test
    fun `full save and load preserves NPCs and quests`() = runTest {
        val (repo, _) = createTestRepo()
        val location = TestHelpers.createTestLocation(id = "tavern", name = "The Tavern")
        val npc = NPC("npc_barkeep", "Barkeep", NPCArchetype.INNKEEPER, "tavern",
            personality = NPCPersonality(listOf("jovial"), "Hearty", listOf("Serve drinks")), lore = "")
        val quest = Quest(
            id = "q1", name = "Find the Key", description = "Find the basement key",
            type = QuestType.SIDE_QUEST,
            objectives = listOf(QuestObjective("search", "Search the barrels", ObjectiveType.EXPLORE, "barrels")),
            rewards = QuestRewards(xp = 50),
            status = QuestProgressStatus.IN_PROGRESS
        )

        val state = GameState(
            gameId = "game-1",
            systemType = SystemType.SYSTEM_INTEGRATION,
            characterSheet = TestHelpers.createTestCharacterSheet(level = 3, xp = 250),
            currentLocation = location,
            npcsByLocation = mapOf("tavern" to listOf(npc)),
            activeQuests = mapOf("q1" to quest),
            hasOpeningNarrationPlayed = true
        )

        repo.createGame("game-1", "Hero", createConfig(), state)
        repo.saveGame("game-1", state, playtime = 1800)

        // Load it back
        val loaded = repo.loadGameState("game-1")
        assertNotNull(loaded)
        assertEquals("tavern", loaded.currentLocation.id)
        assertEquals(1, loaded.npcsByLocation["tavern"]?.size ?: 0)
        assertEquals("Barkeep", loaded.npcsByLocation["tavern"]?.first()?.name)
        assertEquals(1, loaded.activeQuests.size)
        assertEquals("Find the Key", loaded.activeQuests["q1"]?.name)
    }

    // ── Multiple Games ─────────────────────────────────────────────

    @Test
    fun `multiple games are isolated`() = runTest {
        val (repo, _) = createTestRepo()
        val state1 = TestHelpers.createTestGameState(gameId = "game-1")
        val state2 = TestHelpers.createTestGameState(gameId = "game-2")

        repo.createGame("game-1", "Alice", createConfig(name = "Alice"), state1)
        repo.createGame("game-2", "Bob", createConfig(name = "Bob"), state2)

        val npc1 = NPC("npc_a", "NPC-A", NPCArchetype.GUARD, "loc",
            personality = NPCPersonality(listOf(), "", listOf()), lore = "")
        val npc2 = NPC("npc_b", "NPC-B", NPCArchetype.GUARD, "loc",
            personality = NPCPersonality(listOf(), "", listOf()), lore = "")

        repo.saveNPC("game-1", npc1)
        repo.saveNPC("game-2", npc2)

        assertEquals(1, repo.getAllNPCs("game-1").size)
        assertEquals(1, repo.getAllNPCs("game-2").size)
        assertEquals("NPC-A", repo.getAllNPCs("game-1")[0].name)
        assertEquals("NPC-B", repo.getAllNPCs("game-2")[0].name)

        // Delete game-1 should not affect game-2
        repo.deleteGame("game-1")
        assertEquals(0, repo.getAllNPCs("game-1").size)
        assertEquals(1, repo.getAllNPCs("game-2").size)
    }
}
