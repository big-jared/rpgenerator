package com.rpgenerator.server

import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.LLMToolCall
import com.rpgenerator.core.api.LLMToolDef
import com.rpgenerator.core.api.LLMToolResult
import com.rpgenerator.core.api.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * LLM implementation that shells out to a CLI tool (claude, codex, etc.).
 * Each sendMessage call spawns a subprocess with the conversation so far.
 *
 * CLI tools don't support native function calling, so sendMessageWithTools
 * falls back to sendMessage (the orchestrator's text-based tool extraction
 * handles parsing tool calls from the response text).
 *
 * @param command The CLI binary name (e.g. "claude", "codex")
 * @param args Extra CLI arguments beyond the system prompt and message
 */
class CliProcessLLM(
    private val command: String,
    private val args: List<String> = emptyList()
) : LLMInterface {

    override val maxContextTokens: Int get() = 100_000

    override fun startAgent(systemPrompt: String): AgentStream {
        return CliAgentStream(command, args, systemPrompt)
    }

    private class CliAgentStream(
        private val command: String,
        private val extraArgs: List<String>,
        private val systemPrompt: String
    ) : AgentStream {

        private val conversationHistory = mutableListOf<Pair<String, String>>() // role, content

        override suspend fun sendMessage(message: String): Flow<String> = flow {
            val response = runCli(message)

            conversationHistory.add("user" to message)
            conversationHistory.add("assistant" to response)

            // Emit word by word for streaming feel
            val words = response.split(" ")
            words.forEachIndexed { index, word ->
                emit(if (index < words.size - 1) "$word " else word)
            }
        }

        private suspend fun runCli(message: String): String = withContext(Dispatchers.IO) {
            val cmdArgs = buildCliArgs(message)
            val processBuilder = ProcessBuilder(cmdArgs)
                .redirectErrorStream(true)

            val process = processBuilder.start()

            // Some CLIs accept message on stdin
            if (command == "codex") {
                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(message)
                    writer.flush()
                }
            }

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0 && output.isBlank()) {
                "Error: $command exited with code $exitCode"
            } else {
                output.trim()
            }
        }

        private fun buildCliArgs(message: String): List<String> {
            return when (command) {
                "claude" -> buildList {
                    add("claude")
                    add("--print")
                    add("--system-prompt")
                    add(systemPrompt)
                    addAll(extraArgs)
                    add(buildConversationContext(message))
                }
                "codex" -> buildList {
                    add("codex")
                    add("--quiet")
                    add("--full-auto")
                    addAll(extraArgs)
                    add(buildConversationContext(message))
                }
                else -> buildList {
                    add(command)
                    addAll(extraArgs)
                    add("--system-prompt")
                    add(systemPrompt)
                    add(buildConversationContext(message))
                }
            }
        }

        private fun buildConversationContext(currentMessage: String): String {
            if (conversationHistory.isEmpty()) return currentMessage

            return buildString {
                for ((role, content) in conversationHistory) {
                    appendLine("[$role]: $content")
                    appendLine()
                }
                appendLine("[user]: $currentMessage")
            }
        }
    }
}
