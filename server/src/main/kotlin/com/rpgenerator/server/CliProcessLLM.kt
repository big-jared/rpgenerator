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
import org.slf4j.LoggerFactory
import java.io.File

/**
 * LLM implementation that shells out to a CLI tool (claude, codex, etc.).
 * Each sendMessage call spawns a subprocess with the conversation so far.
 */
class CliProcessLLM(
    private val command: String,
    private val args: List<String> = emptyList()
) : LLMInterface {

    private val log = LoggerFactory.getLogger("CliProcessLLM[$command]")

    override val maxContextTokens: Int get() = 100_000

    override fun startAgent(systemPrompt: String): AgentStream {
        log.info("startAgent called, prompt length={} chars, preview='{}'",
            systemPrompt.length, systemPrompt.take(80).replace("\n", " "))
        return CliAgentStream(command, args, systemPrompt)
    }

    private class CliAgentStream(
        private val command: String,
        private val extraArgs: List<String>,
        private val systemPrompt: String
    ) : AgentStream {

        private val log = LoggerFactory.getLogger("CliAgent[$command]")
        private val conversationHistory = mutableListOf<Pair<String, String>>() // role, content

        override suspend fun sendMessage(message: String): Flow<String> = flow {
            log.info("sendMessage called, message length={}, preview='{}'",
                message.length, message.take(120).replace("\n", " "))
            log.debug("conversation history: {} previous exchanges", conversationHistory.size)

            val response = runCli(message)

            conversationHistory.add("user" to message)
            conversationHistory.add("assistant" to response)

            log.info("response received, {} chars, preview='{}'",
                response.length, response.take(200).replace("\n", " "))

            // Emit word by word for streaming feel
            val words = response.split(" ")
            words.forEachIndexed { index, word ->
                emit(if (index < words.size - 1) "$word " else word)
            }
        }

        override suspend fun sendMessageWithTools(
            message: String,
            tools: List<LLMToolDef>,
            executor: ToolExecutor
        ): Flow<String> {
            if (command != "claude" || tools.isEmpty()) {
                log.info("sendMessageWithTools: no tools or non-claude command, falling back to sendMessage")
                return sendMessage(message)
            }

            log.info("sendMessageWithTools: {} tools, message length={}", tools.size, message.length)

            // Register tools with the ephemeral MCP bridge
            val token = EphemeralMcpBridge.register(tools, executor)
            log.info("Registered ephemeral MCP session: {}", token)

            // Determine the server port for the internal MCP endpoint
            val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

            // Write a temp MCP config file pointing to our ephemeral endpoint
            val mcpConfig = buildJsonObject {
                putJsonObject("mcpServers") {
                    putJsonObject("rpg-tools") {
                        put("type", JsonPrimitive("streamable-http"))
                        put("url", JsonPrimitive("http://localhost:$port/internal/mcp/$token"))
                    }
                }
            }

            val tempConfigFile = File.createTempFile("mcp-config-", ".json")
            tempConfigFile.writeText(Json.encodeToString(JsonElement.serializer(), mcpConfig))
            log.debug("Wrote temp MCP config to {}", tempConfigFile.absolutePath)

            // Build tool name list for --allowedTools
            val toolNames = tools.joinToString(",") { "mcp__rpg-tools__${it.name}" }

            // IMPORTANT: Cleanup must happen INSIDE the flow builder, not in a finally block.
            // flow {} creates a cold/lazy flow — if cleanup is in finally, it runs immediately
            // when the function returns (before the flow is ever collected), destroying the
            // MCP bridge and config file before claude can use them.
            return flow {
                try {
                    val response = runCliWithMcp(message, tempConfigFile, toolNames)

                    conversationHistory.add("user" to message)
                    conversationHistory.add("assistant" to response)

                    log.info("sendMessageWithTools response: {} chars, preview='{}'",
                        response.length, response.take(200).replace("\n", " "))

                    val words = response.split(" ")
                    words.forEachIndexed { index, word ->
                        emit(if (index < words.size - 1) "$word " else word)
                    }
                } finally {
                    EphemeralMcpBridge.unregister(token)
                    tempConfigFile.delete()
                    log.debug("Cleaned up ephemeral MCP session {} and temp config", token)
                }
            }
        }

        private suspend fun runCliWithMcp(
            message: String,
            mcpConfigFile: File,
            allowedTools: String
        ): String = withContext(Dispatchers.IO) {
            val prompt = buildConversationContext(message)

            val cmdArgs = buildList {
                add("claude")
                add("--print")
                add("--system-prompt")
                add(systemPrompt)
                add("--mcp-config")
                add(mcpConfigFile.absolutePath)
                add("--allowedTools")
                add(allowedTools)
                add("--dangerously-skip-permissions")
                addAll(extraArgs)
                add("-p")
                add(prompt)
            }

            log.info("spawning claude with MCP tools: {} (total {} args)", cmdArgs.take(6).joinToString(" "), cmdArgs.size)

            val processBuilder = ProcessBuilder(cmdArgs)
            processBuilder.redirectErrorStream(false)
            processBuilder.environment().remove("CLAUDECODE")
            processBuilder.environment().remove("CLAUDE_CODE_ENTRYPOINT")

            val startTime = System.currentTimeMillis()
            val process: Process
            try {
                process = processBuilder.start()
                log.info("MCP process started, pid={}", process.pid())
            } catch (e: Exception) {
                log.error("FAILED to start claude with MCP: {}", e.message, e)
                return@withContext "Error: Failed to start claude with MCP: ${e.message}"
            }

            // Close stdin immediately
            process.outputStream.close()

            val stdoutFuture = java.util.concurrent.CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().readText()
            }
            val stderrFuture = java.util.concurrent.CompletableFuture.supplyAsync {
                process.errorStream.bufferedReader().readText()
            }

            val finished = process.waitFor(300, java.util.concurrent.TimeUnit.SECONDS)
            val elapsed = System.currentTimeMillis() - startTime

            if (!finished) {
                log.error("MCP TIMEOUT after {}ms, destroying process pid={}", elapsed, process.pid())
                process.destroyForcibly()
                return@withContext "Error: claude with MCP timed out after ${elapsed}ms"
            }

            val exitCode = process.exitValue()
            val stdout = stdoutFuture.get().trim()
            val stderr = stderrFuture.get().trim()

            log.info("MCP process exited, code={}, elapsed={}ms, stdout={} chars, stderr={} chars",
                exitCode, elapsed, stdout.length, stderr.length)

            if (stderr.isNotBlank()) {
                log.warn("MCP STDERR: {}", stderr.take(1000))
            }

            if (exitCode != 0 && stdout.isBlank()) {
                "Error: claude with MCP exited with code $exitCode. ${stderr.take(200)}"
            } else {
                stdout
            }
        }

        private suspend fun runCli(message: String): String = withContext(Dispatchers.IO) {
            val cmdArgs = buildCliArgs(message)

            log.info("spawning process: {} (total {} args)", cmdArgs.take(4).joinToString(" "), cmdArgs.size)
            log.debug("system prompt length: {} chars", systemPrompt.length)
            log.debug("message/prompt arg length: {} chars", cmdArgs.lastOrNull()?.length ?: 0)

            val processBuilder = ProcessBuilder(cmdArgs)
            // Separate stdout/stderr so we can see errors clearly
            processBuilder.redirectErrorStream(false)
            // Remove all Claude Code env vars so nested claude processes don't refuse to start
            processBuilder.environment().remove("CLAUDECODE")
            processBuilder.environment().remove("CLAUDE_CODE_ENTRYPOINT")

            val env = processBuilder.environment()
            log.debug("Claude env vars removed. PATH={}", env["PATH"]?.take(100))

            val startTime = System.currentTimeMillis()
            val process: Process
            try {
                process = processBuilder.start()
                log.info("process started, pid={}", process.pid())
            } catch (e: Exception) {
                log.error("FAILED to start process: {}", e.message, e)
                return@withContext "Error: Failed to start $command: ${e.message}"
            }

            // Close stdin immediately for claude (it reads args, not stdin).
            // Without this, the subprocess may hang waiting for EOF on the pipe.
            if (command == "claude") {
                process.outputStream.close()
                log.debug("stdin closed for claude")
            }

            // Some CLIs accept message on stdin
            if (command == "codex") {
                log.debug("writing message to stdin for codex")
                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(message)
                    writer.flush()
                }
            }

            // Read stdout and stderr in parallel to avoid deadlocks
            val stdoutFuture = java.util.concurrent.CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().readText()
            }
            val stderrFuture = java.util.concurrent.CompletableFuture.supplyAsync {
                process.errorStream.bufferedReader().readText()
            }

            // Wait for process with timeout (5 minutes)
            val finished = process.waitFor(300, java.util.concurrent.TimeUnit.SECONDS)
            val elapsed = System.currentTimeMillis() - startTime

            if (!finished) {
                log.error("TIMEOUT after {}ms, destroying process pid={}", elapsed, process.pid())
                process.destroyForcibly()
                return@withContext "Error: $command timed out after ${elapsed}ms"
            }

            val exitCode = process.exitValue()
            val stdout = stdoutFuture.get().trim()
            val stderr = stderrFuture.get().trim()

            log.info("process exited, code={}, elapsed={}ms, stdout={} chars, stderr={} chars",
                exitCode, elapsed, stdout.length, stderr.length)

            if (stderr.isNotBlank()) {
                log.warn("STDERR: {}", stderr.take(1000))
            }

            if (exitCode != 0) {
                log.error("non-zero exit code={}, stdout preview='{}'", exitCode, stdout.take(500))
                log.error("stderr: {}", stderr.take(500))
            }

            if (stdout.isBlank() && exitCode == 0) {
                log.warn("process succeeded but returned empty stdout")
            }

            if (exitCode != 0 && stdout.isBlank()) {
                "Error: $command exited with code $exitCode. ${stderr.take(200)}"
            } else {
                stdout
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
                    add("-p")
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
