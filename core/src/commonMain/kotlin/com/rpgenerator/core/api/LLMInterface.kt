package com.rpgenerator.core.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Definition of a tool that can be called by the LLM.
 */
@Serializable
data class LLMToolDef(val name: String, val description: String, val parameters: JsonObject)

/**
 * A tool call requested by the LLM.
 */
@Serializable
data class LLMToolCall(val id: String, val name: String, val arguments: JsonObject)

/**
 * The result of executing a tool call.
 */
@Serializable
data class LLMToolResult(val callId: String, val result: JsonObject)

/**
 * Function type that executes an LLM tool call and returns its result.
 */
typealias ToolExecutor = suspend (LLMToolCall) -> LLMToolResult

/**
 * Interface for LLM providers.
 * Implementations handle the actual API calls to language models.
 */
interface LLMInterface {
    /**
     * Maximum context tokens supported by this LLM.
     * Used by core library to auto-refresh agents before hitting limits.
     * Default: 200k (Claude's context window)
     */
    val maxContextTokens: Int get() = 200_000

    /**
     * Start a new agent with a system prompt.
     * The agent maintains conversation context across multiple messages.
     *
     * @param systemPrompt Initial system instructions for the agent
     * @return AgentStream for sending messages and receiving responses
     */
    fun startAgent(systemPrompt: String): AgentStream
}

/**
 * A stateful conversation with an LLM agent.
 * Automatically managed by the core library.
 */
interface AgentStream {
    /**
     * Send a message to the agent and receive streaming response.
     *
     * @param message User or game message to send to the agent
     * @return Flow of response text chunks (for streaming display)
     */
    suspend fun sendMessage(message: String): Flow<String>

    /**
     * Send a message with tool definitions, allowing the LLM to call tools.
     * The executor handles tool calls and returns results back to the LLM.
     * Default implementation falls back to sendMessage (no tool support).
     *
     * @param message User or game message to send to the agent
     * @param tools List of tool definitions available to the LLM
     * @param executor Function that executes tool calls and returns results
     * @return Flow of response text chunks (for streaming display)
     */
    suspend fun sendMessageWithTools(
        message: String,
        tools: List<LLMToolDef>,
        executor: ToolExecutor
    ): Flow<String> = sendMessage(message)
}
