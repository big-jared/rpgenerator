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

    // ── Tool call logging ────────────────────────────────────────────

    /** All tool calls logged this session, in order. */
    val toolCallLog: List<ToolCallLogEntry>

    /** Set the caller context before executing tools. */
    var currentCaller: ToolCaller

    /** Set the current turn number for log entries. */
    var currentTurnNumber: Int
}
