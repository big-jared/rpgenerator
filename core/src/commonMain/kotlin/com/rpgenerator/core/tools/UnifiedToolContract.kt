package com.rpgenerator.core.tools

import com.rpgenerator.core.domain.GameState

/**
 * Single interface for all game tool execution.
 * Replaces GameTools, GeminiToolContract, and McpHandler tool dispatch.
 *
 * Tools are stateless: state is passed in, mutated state returned in ToolOutcome.
 */
internal interface UnifiedToolContract {
    /**
     * Execute a tool by name with the given arguments against the current game state.
     * Returns a ToolOutcome containing result data, optional new state, and events.
     */
    suspend fun executeTool(name: String, args: Map<String, Any?>, state: GameState): ToolOutcome

    /**
     * Get all tool definitions for LLM function calling.
     */
    fun getToolDefinitions(): List<UnifiedToolDef>
}
