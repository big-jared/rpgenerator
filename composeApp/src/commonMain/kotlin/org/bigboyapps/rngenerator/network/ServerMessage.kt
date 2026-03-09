package org.bigboyapps.rngenerator.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Messages received from the game server via WebSocket.
 */
sealed class ServerMessage {
    data class Audio(val data: ByteArray) : ServerMessage()
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
    val error: String? = null
)

/**
 * Response from GET /api/game/{sessionId}/setup
 */
@Serializable
data class GameSetupResponse(
    val systemPrompt: String,
    val toolDefinitions: List<ToolDefinitionDto> = emptyList(),
    val voiceName: String? = null
)

@Serializable
data class ToolDefinitionDto(
    val name: String,
    val description: String,
    val parameters: List<ToolParameterDto> = emptyList()
)

@Serializable
data class ToolParameterDto(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
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
