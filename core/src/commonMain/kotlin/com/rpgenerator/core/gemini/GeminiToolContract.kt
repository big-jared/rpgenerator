package com.rpgenerator.core.gemini

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.domain.GameState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Contract for tools exposed to Gemini Live API.
 * Gemini calls these functions during voice conversation to interact with the game engine.
 * Each tool maps to a function declaration in the Gemini session config.
 */
interface GeminiToolContract {

    // ── State Queries (read-only) ──────────────────────────────────

    /** Get player stats, level, HP, location */
    fun getPlayerStats(): ToolResult

    /** Get full inventory */
    fun getInventory(): ToolResult

    /** Get active quests and progress */
    fun getActiveQuests(): ToolResult

    /** Get NPCs at current location */
    fun getNPCsHere(): ToolResult

    /** Get current location details and connections */
    fun getLocation(): ToolResult

    /** Get character sheet with skills, equipment, status effects */
    fun getCharacterSheet(): ToolResult

    // ── Actions (modify game state) ────────────────────────────────

    /** Move player to a connected location */
    suspend fun moveToLocation(locationName: String): ToolResult

    /** Initiate combat with a target */
    suspend fun attackTarget(targetName: String): ToolResult

    /** Use an item from inventory */
    suspend fun useItem(itemId: String): ToolResult

    /** Use a skill (optionally on a target) */
    suspend fun useSkill(skillId: String, target: String? = null): ToolResult

    /** Pick up an item in the environment */
    suspend fun pickUpItem(itemName: String): ToolResult

    /** Start or continue conversation with an NPC */
    suspend fun talkToNPC(npcName: String, dialogue: String? = null): ToolResult

    /** Purchase from an NPC's shop */
    suspend fun buyFromShop(npcName: String, itemName: String): ToolResult

    // ── Quest Management ───────────────────────────────────────────

    /** Accept a quest from an NPC or event */
    suspend fun acceptQuest(questId: String): ToolResult

    /** Update quest objective progress */
    suspend fun updateQuestProgress(questId: String, objectiveId: String, progress: Int): ToolResult

    /** Complete a quest (if all objectives met) */
    suspend fun completeQuest(questId: String): ToolResult

    // ── World Generation ───────────────────────────────────────────

    /** Generate a new location (Gemini decides when exploration warrants it) */
    suspend fun generateLocation(description: String): ToolResult

    /** Generate an NPC at the current location */
    suspend fun generateNPC(name: String, role: String, personality: String): ToolResult

    /** Generate a new quest based on current context */
    suspend fun generateQuest(context: String): ToolResult

    // ── Multimodal Triggers ────────────────────────────────────────

    /** Generate scene art for current narrative moment */
    suspend fun generateSceneArt(sceneDescription: String): ToolResult

    /** Shift background music mood */
    suspend fun shiftMusicMood(mood: String, intensity: Float = 0.5f): ToolResult
}

/**
 * Result returned from a tool call to Gemini.
 * Gemini uses this to inform its next spoken response.
 */
@Serializable
data class ToolResult(
    val success: Boolean,
    val data: JsonObject? = null,
    val error: String? = null,
    val gameEvents: List<GameEvent> = emptyList()
)

/**
 * Represents a tool call that Gemini wants to make.
 * Parsed from the Live API WebSocket message.
 */
@Serializable
data class GeminiToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject
)

/**
 * Multimodal content that Gemini generates inline.
 * These arrive interleaved in the Live API response stream.
 */
sealed class GeminiOutput {
    /** Spoken text (also arrives as audio) */
    data class Text(val content: String) : GeminiOutput()

    /** Audio chunk for playback */
    data class Audio(val pcmData: ByteArray, val sampleRate: Int = 24000) : GeminiOutput()

    /** Generated image (scene art, portrait, etc.) */
    data class Image(val imageData: ByteArray, val mimeType: String = "image/png", val description: String = "") : GeminiOutput()

    /** Tool call request — game engine must execute and return result */
    data class ToolCallRequest(val calls: List<GeminiToolCall>) : GeminiOutput()

    /** Turn complete — Gemini finished speaking, waiting for player */
    data object TurnComplete : GeminiOutput()
}
