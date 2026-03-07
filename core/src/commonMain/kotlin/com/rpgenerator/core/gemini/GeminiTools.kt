package com.rpgenerator.core.gemini

import com.rpgenerator.core.api.Game
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.rules.RulesEngine
import kotlinx.serialization.json.JsonObject

/**
 * Public API for the Gemini tool system.
 * Wraps internal GeminiToolContractImpl with a public interface.
 */
class GeminiTools private constructor(
    private val impl: GeminiToolContractImpl
) {
    /**
     * Dispatch a tool call from Gemini and return the result.
     */
    suspend fun dispatch(call: GeminiToolCall): ToolResult = impl.dispatch(call)

    /**
     * Get all tool declarations for Gemini session setup.
     */
    fun getToolDeclarations(): List<GeminiToolDeclaration> = impl.getToolDeclarations()

    // Direct access to state query tools
    fun getPlayerStats(): ToolResult = impl.getPlayerStats()
    fun getInventory(): ToolResult = impl.getInventory()
    fun getActiveQuests(): ToolResult = impl.getActiveQuests()
    fun getNPCsHere(): ToolResult = impl.getNPCsHere()
    fun getLocation(): ToolResult = impl.getLocation()
    fun getCharacterSheet(): ToolResult = impl.getCharacterSheet()

    // Direct access to action tools
    suspend fun attackTarget(target: String): ToolResult = impl.attackTarget(target)
    suspend fun talkToNPC(npcName: String, dialogue: String? = null): ToolResult = impl.talkToNPC(npcName, dialogue)
    suspend fun moveToLocation(locationName: String): ToolResult = impl.moveToLocation(locationName)
    suspend fun useItem(itemId: String): ToolResult = impl.useItem(itemId)
    suspend fun generateSceneArt(description: String): ToolResult = impl.generateSceneArt(description)
    suspend fun shiftMusicMood(mood: String, intensity: Float = 0.5f): ToolResult = impl.shiftMusicMood(mood, intensity)

    companion object {
        /**
         * Create GeminiTools with default test state.
         * Use this for test harness and development.
         */
        fun createDefault(
            gameId: String = "test-game",
            systemType: SystemType = SystemType.SYSTEM_INTEGRATION
        ): GeminiTools {
            val state = GameState(
                gameId = gameId,
                systemType = systemType,
                characterSheet = CharacterSheet(
                    baseStats = Stats(),
                    resources = Resources.fromStats(Stats())
                ),
                currentLocation = Location(
                    id = "start",
                    name = "Starting Area",
                    zoneId = "zone-1",
                    biome = Biome.FOREST,
                    description = "The starting area",
                    danger = 1,
                    connections = emptyList(),
                    features = emptyList(),
                    lore = ""
                ),
                hasOpeningNarrationPlayed = true
            )
            return GeminiTools(GeminiToolContractImpl(state))
        }

        /**
         * Create from internal game state (used by GameOrchestrator).
         */
        internal fun createFromState(state: GameState): GeminiTools {
            return GeminiTools(GeminiToolContractImpl(state))
        }
    }
}
