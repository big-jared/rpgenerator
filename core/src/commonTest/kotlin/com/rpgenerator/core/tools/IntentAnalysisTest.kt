package com.rpgenerator.core.tools

import com.rpgenerator.core.agents.LocationGeneratorAgent
import com.rpgenerator.core.agents.QuestGeneratorAgent
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.orchestration.Intent
import com.rpgenerator.core.rules.RulesEngine
import com.rpgenerator.core.test.MockLLMInterface
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Tests that LLM-based analyzeIntent correctly classifies player inputs.
 * Uses MockLLMInterface which simulates LLM intent classification responses.
 */
class IntentAnalysisTest {

    private lateinit var gameTools: GameToolsImpl
    private lateinit var baseState: GameState

    @BeforeTest
    fun setup() {
        val locationManager = LocationManager().apply {
            loadLocations(SystemType.SYSTEM_INTEGRATION)
        }
        val mockLLM = MockLLMInterface()
        gameTools = GameToolsImpl(
            locationManager,
            RulesEngine(),
            LocationGeneratorAgent(mockLLM),
            QuestGeneratorAgent(mockLLM),
            mockLLM
        )

        val startingLocation = locationManager.getStartingLocation(SystemType.SYSTEM_INTEGRATION)
        baseState = GameState(
            gameId = "test",
            systemType = SystemType.SYSTEM_INTEGRATION,
            characterSheet = CharacterSheet(
                baseStats = Stats(),
                resources = Resources(100, 100, 50, 50, 100, 100)
            ),
            currentLocation = startingLocation
        )
    }

    @Test
    fun `attack routes to COMBAT`() = runBlocking {
        val result = gameTools.analyzeIntent("attack the goblin", baseState, emptyList())
        assertEquals(Intent.COMBAT, result.intent)
    }

    @Test
    fun `fight routes to COMBAT`() = runBlocking {
        val result = gameTools.analyzeIntent("fight the slime", baseState, emptyList())
        assertEquals(Intent.COMBAT, result.intent)
    }

    @Test
    fun `talk routes to NPC_DIALOGUE`() = runBlocking {
        val result = gameTools.analyzeIntent("talk to the merchant", baseState, emptyList())
        assertEquals(Intent.NPC_DIALOGUE, result.intent)
    }

    @Test
    fun `explore routes to EXPLORATION`() = runBlocking {
        val result = gameTools.analyzeIntent("explore the forest", baseState, emptyList())
        assertEquals(Intent.EXPLORATION, result.intent)
    }

    @Test
    fun `search triggers location generation`() = runBlocking {
        val result = gameTools.analyzeIntent("search for hidden passages", baseState, emptyList())
        assertEquals(Intent.EXPLORATION, result.intent)
        assertTrue(result.shouldGenerateLocation)
    }

    @Test
    fun `skills menu routes to SKILL_MENU`() = runBlocking {
        val result = gameTools.analyzeIntent("show my skills", baseState, emptyList())
        assertEquals(Intent.SKILL_MENU, result.intent)
    }

    @Test
    fun `quest routes to QUEST_ACTION`() = runBlocking {
        val result = gameTools.analyzeIntent("list my quests", baseState, emptyList())
        assertEquals(Intent.QUEST_ACTION, result.intent)
    }

    @Test
    fun `class selection keywords route to CLASS_SELECTION`() = runBlocking {
        val result = gameTools.analyzeIntent("choose warrior class", baseState, emptyList())
        assertEquals(Intent.CLASS_SELECTION, result.intent)
    }

    @Test
    fun `ambiguous input defaults to EXPLORATION`() = runBlocking {
        val result = gameTools.analyzeIntent("look around", baseState, emptyList())
        assertEquals(Intent.EXPLORATION, result.intent)
    }
}
