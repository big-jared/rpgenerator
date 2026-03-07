package com.rpgenerator.core.domain

import com.rpgenerator.core.api.SystemType
import kotlin.test.*

/**
 * Bug 4: Location never changes.
 *
 * Tests that GameState.moveToLocation() correctly updates the current location
 * and that location discovery tracking works. The actual bug was in
 * GameOrchestrator where ActionType.MOVEMENT was a no-op, but these tests
 * verify the underlying domain logic is correct.
 */
class LocationMovementTest {

    private val startLocation = Location(
        id = "spawn_clearing",
        name = "Spawn Clearing",
        zoneId = "tutorial",
        biome = Biome.FOREST,
        description = "A clearing where you first appeared.",
        danger = 1,
        connections = listOf("forest_path", "river_bank"),
        features = emptyList(),
        lore = ""
    )

    private val forestPath = Location(
        id = "forest_path",
        name = "Forest Path",
        zoneId = "tutorial",
        biome = Biome.FOREST,
        description = "A winding path through dense trees.",
        danger = 2,
        connections = listOf("spawn_clearing", "dark_cave"),
        features = emptyList(),
        lore = ""
    )

    private val darkCave = Location(
        id = "dark_cave",
        name = "Dark Cave",
        zoneId = "tutorial",
        biome = Biome.CAVE,
        description = "A damp cave entrance.",
        danger = 3,
        connections = listOf("forest_path"),
        features = emptyList(),
        lore = ""
    )

    private fun createState(): GameState {
        return GameState(
            gameId = "test",
            systemType = SystemType.SYSTEM_INTEGRATION,
            characterSheet = CharacterSheet(
                baseStats = Stats(),
                resources = Resources(100, 100, 50, 50, 100, 100)
            ),
            currentLocation = startLocation
        )
    }

    @Test
    fun `moveToLocation changes current location`() {
        val state = createState()
        assertEquals("Spawn Clearing", state.currentLocation.name)

        val moved = state.moveToLocation(forestPath)
        assertEquals("Forest Path", moved.currentLocation.name)
        assertEquals("forest_path", moved.currentLocation.id)
    }

    @Test
    fun `moveToLocation preserves all other state`() {
        val state = createState()
        val moved = state.moveToLocation(forestPath)

        assertEquals(state.gameId, moved.gameId)
        assertEquals(state.characterSheet, moved.characterSheet)
        assertEquals(state.playerLevel, moved.playerLevel)
        assertEquals(state.playerXP, moved.playerXP)
        assertEquals(state.activeQuests, moved.activeQuests)
    }

    @Test
    fun `multiple moves update location correctly`() {
        var state = createState()
        state = state.moveToLocation(forestPath)
        assertEquals("Forest Path", state.currentLocation.name)

        state = state.moveToLocation(darkCave)
        assertEquals("Dark Cave", state.currentLocation.name)
    }

    @Test
    fun `discoverLocation tracks visited locations`() {
        val state = createState()
        assertTrue(state.discoveredTemplateLocations.isEmpty())

        val discovered = state.discoverLocation("forest_path")
        assertTrue(discovered.discoveredTemplateLocations.contains("forest_path"))
        assertEquals(1, discovered.discoveredTemplateLocations.size)
    }

    @Test
    fun `move and discover together`() {
        var state = createState()
        state = state.moveToLocation(forestPath)
        state = state.discoverLocation(forestPath.id)

        assertEquals("Forest Path", state.currentLocation.name)
        assertTrue(state.discoveredTemplateLocations.contains("forest_path"))
    }

    @Test
    fun `custom locations can be added and moved to`() {
        val customLoc = Location(
            id = "custom_village",
            name = "Hidden Village",
            zoneId = "custom",
            biome = Biome.SETTLEMENT,
            description = "A village hidden in the hills.",
            danger = 1,
            connections = listOf("spawn_clearing"),
            features = emptyList(),
            lore = ""
        )

        var state = createState()
        state = state.addCustomLocation(customLoc)
        assertTrue(state.customLocations.containsKey("custom_village"))

        state = state.moveToLocation(customLoc)
        assertEquals("Hidden Village", state.currentLocation.name)
    }

    @Test
    fun `fuzzy location matching simulation`() {
        // This simulates what GameOrchestrator now does for movement:
        // fuzzy match player input against connected locations
        val connectedLocations = listOf(forestPath, darkCave)
        val playerInput = "forest"

        val inputLower = playerInput.lowercase()
        val match = connectedLocations.find { it.name.lowercase().contains(inputLower) }

        assertNotNull(match, "Should fuzzy-match 'forest' to 'Forest Path'")
        assertEquals("Forest Path", match.name)
    }

    @Test
    fun `fuzzy matching with partial word`() {
        val connectedLocations = listOf(forestPath, darkCave)
        val playerInput = "cave"

        val inputLower = playerInput.lowercase()
        val match = connectedLocations.find { it.name.lowercase().contains(inputLower) }

        assertNotNull(match, "Should fuzzy-match 'cave' to 'Dark Cave'")
        assertEquals("Dark Cave", match.name)
    }

    @Test
    fun `fuzzy matching returns null for no match`() {
        val connectedLocations = listOf(forestPath, darkCave)
        val playerInput = "castle"

        val inputLower = playerInput.lowercase()
        val match = connectedLocations.find { it.name.lowercase().contains(inputLower) }

        assertNull(match, "Should not match 'castle' to any location")
    }
}
