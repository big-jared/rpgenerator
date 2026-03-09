package com.rpgenerator.core.tools

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.domain.GameState
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
