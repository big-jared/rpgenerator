package org.bigboyapps.rngenerator.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Messages received from the game server via WebSocket.
 */
sealed class ServerMessage {
    data class Audio(val data: ByteArray) : ServerMessage()
    data class MusicAudio(val data: ByteArray) : ServerMessage()
    data class Text(val content: String) : ServerMessage()
    data class Transcript(val role: String, val content: String) : ServerMessage()
    data class ToolCall(val name: String, val args: JsonObject) : ServerMessage()
    data class ToolResult(val name: String, val success: Boolean) : ServerMessage()
    data class GameEvent(val event: JsonObject) : ServerMessage()
    data class SceneImage(val data: ByteArray, val mimeType: String) : ServerMessage()
    data class StateUpdate(val state: JsonObject) : ServerMessage()
    data object TurnComplete : ServerMessage()
    data object Connected : ServerMessage()
    data object Interrupted : ServerMessage()
    data object Disconnected : ServerMessage()
    data class Error(val message: String) : ServerMessage()
    data class OnboardingComplete(val seedId: String, val playerName: String, val backstory: String) : ServerMessage()
}

/**
 * Response from POST /api/game/create
 */
@Serializable
data class CreateGameResponse(val sessionId: String)

/**
 * Request body for POST /api/game/create
 */
@Serializable
data class CreateGameRequest(
    val name: String? = null,
    val backstory: String? = null,
    val systemType: String? = null,
    val seedId: String? = null
)

/**
 * Request body for POST /api/game/{sessionId}/tool
 */
@Serializable
data class ToolExecutionRequest(
    val name: String,
    val args: Map<String, String> = emptyMap()
)

/**
 * Response from POST /api/game/{sessionId}/tool
 */
@Serializable
data class ToolExecutionResult(
    val success: Boolean,
    val data: JsonObject = JsonObject(emptyMap()),
    val events: List<JsonObject> = emptyList(),
    val error: String? = null,
    val imageBase64: String? = null,
    val imageMimeType: String? = null
)

/**
 * Response from GET /api/game/{sessionId}/npc/{npcId}
 */
@Serializable
data class NpcDetailsDto(
    val id: String,
    val name: String,
    val archetype: String = "",
    val description: String = "",
    val lore: String = "",
    val traits: List<String> = emptyList(),
    val speechPattern: String = "",
    val motivations: List<String> = emptyList(),
    val relationshipStatus: String = "Neutral",
    val affinity: Int = 0,
    val hasShop: Boolean = false,
    val shopName: String? = null,
    val shopItems: List<ShopItemDto> = emptyList(),
    val questIds: List<String> = emptyList(),
    val recentConversations: List<ConversationDto> = emptyList()
)

@Serializable
data class ShopItemDto(
    val id: String,
    val name: String,
    val description: String = "",
    val price: Int = 0,
    val stock: Int = -1,
    val requiredLevel: Int = 1
)

@Serializable
data class ConversationDto(
    val playerInput: String,
    val npcResponse: String
)

/**
 * A saved game entry from GET /api/saves
 */
@Serializable
data class SavedGameInfo(
    val gameId: String,
    val playerName: String = "Unknown",
    val systemType: String = "SYSTEM_INTEGRATION",
    val level: Int = 1,
    val lastPlayed: Long = 0
)

/**
 * Response from POST /auth/token
 */
@Serializable
data class TokenResponse(
    val accessToken: String = "",
    val access_token: String = ""
) {
    // Server may use either key name
    val token: String get() = accessToken.ifEmpty { access_token }
}
