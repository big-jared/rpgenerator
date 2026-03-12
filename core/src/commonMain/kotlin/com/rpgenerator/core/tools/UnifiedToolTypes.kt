package com.rpgenerator.core.tools

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.util.currentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Return type for all tool executions (both read-only and mutating).
 * Tools are stateless — state is passed in and mutated state is returned.
 */
@Serializable
internal data class ToolOutcome(
    val success: Boolean,
    val data: JsonObject = JsonObject(emptyMap()),
    val newState: GameState? = null,
    val events: List<GameEvent> = emptyList(),
    val error: String? = null
)

/**
 * Comprehensive log entry for every tool call.
 * Captures who called it, what args were passed, what happened, and how long it took.
 */
data class ToolCallLogEntry(
    val sequenceNumber: Int,
    val timestamp: Long = currentTimeMillis(),
    val toolName: String,
    val caller: ToolCaller,
    val args: Map<String, String>,
    val success: Boolean,
    val resultSummary: String,
    val error: String? = null,
    val elapsedMs: Long,
    val stateChanged: Boolean,
    val eventsEmitted: List<String>,
    val location: String,
    val playerLevel: Int,
    val turnNumber: Int
)

enum class ToolCaller {
    GM_AGENT,       // Phase 1+2 — GM decided to call this tool
    EXTERNAL_MCP,   // Claude Code / MCP client called directly
    EXTERNAL_REST,  // Mobile app called via REST
    EXTERNAL_OTHER  // Unknown external caller
}

/**
 * Schema definition for a tool, used to generate LLM tool definitions.
 */
@Serializable
internal data class UnifiedToolDef(
    val name: String,
    val description: String,
    val parameters: List<ToolParam> = emptyList()
)

/**
 * Parameter definition for a tool.
 */
@Serializable
internal data class ToolParam(
    val name: String,
    val type: String, // "string", "integer", "number", "boolean"
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null
)
