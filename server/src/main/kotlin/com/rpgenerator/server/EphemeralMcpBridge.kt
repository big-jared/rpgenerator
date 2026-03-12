package com.rpgenerator.server

import com.rpgenerator.core.api.LLMToolCall
import com.rpgenerator.core.api.LLMToolDef
import com.rpgenerator.core.api.ToolExecutor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Ephemeral MCP bridge for CliProcessLLM tool support.
 *
 * When `claude --print` needs to call game tools, we register the tool definitions
 * and executor lambda here, spin up a temp MCP endpoint, and let the subprocess
 * call tools via MCP HTTP. After the subprocess exits, we unregister.
 */
object EphemeralMcpBridge {

    private val log = LoggerFactory.getLogger("EphemeralMcpBridge")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private data class ToolSession(
        val tools: List<LLMToolDef>,
        val executor: ToolExecutor,
        val mutex: Mutex = Mutex()
    )

    private val sessions = ConcurrentHashMap<String, ToolSession>()

    /**
     * Register tool definitions and an executor for a subprocess invocation.
     * Returns a unique token used in the MCP endpoint URL.
     */
    fun register(tools: List<LLMToolDef>, executor: ToolExecutor): String {
        val token = UUID.randomUUID().toString().take(12)
        sessions[token] = ToolSession(tools, executor)
        log.info("Registered tool session {} with {} tools", token, tools.size)
        return token
    }

    /** Unregister a tool session after the subprocess exits. */
    fun unregister(token: String) {
        sessions.remove(token)
        log.info("Unregistered tool session {}", token)
    }

    /** Handle an MCP JSON-RPC request for the given session token. */
    suspend fun handleRequest(token: String, body: String): Pair<String?, Int> {
        val session = sessions[token]
        if (session == null) {
            log.warn("Unknown tool session token: {}", token)
            val err = errorResponse(JsonNull, -32000, "Unknown tool session")
            return json.encodeToString(JsonElement.serializer(), err) to 404
        }

        val request = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            val err = errorResponse(JsonNull, -32700, "Parse error: ${e.message}")
            return json.encodeToString(JsonElement.serializer(), err) to 200
        }

        val method = request["method"]?.jsonPrimitive?.content ?: ""
        val id = request["id"]

        val response = when (method) {
            "initialize" -> handleInitialize(id)
            "notifications/initialized" -> null
            "tools/list" -> handleToolsList(id, session)
            "tools/call" -> handleToolsCall(id, request, session)
            else -> mcpResponse(id, buildJsonObject {
                put("error", JsonPrimitive("Unknown method: $method"))
            })
        }

        return if (response != null) {
            json.encodeToString(JsonElement.serializer(), response) to 200
        } else {
            null to 202
        }
    }

    private fun handleInitialize(id: JsonElement?): JsonObject {
        return mcpResponse(id, buildJsonObject {
            put("protocolVersion", JsonPrimitive("2025-03-26"))
            putJsonObject("capabilities") {
                putJsonObject("tools") {
                    put("listChanged", JsonPrimitive(false))
                }
            }
            putJsonObject("serverInfo") {
                put("name", JsonPrimitive("rpg-internal-tools"))
                put("version", JsonPrimitive("1.0.0"))
            }
        })
    }

    private fun handleToolsList(id: JsonElement?, session: ToolSession): JsonObject {
        return mcpResponse(id, buildJsonObject {
            putJsonArray("tools") {
                for (tool in session.tools) {
                    addJsonObject {
                        put("name", JsonPrimitive(tool.name))
                        put("description", JsonPrimitive(tool.description))
                        // LLMToolDef.parameters is already {type: "object", properties: {...}, required: [...]}
                        put("inputSchema", tool.parameters)
                    }
                }
            }
        })
    }

    private suspend fun handleToolsCall(
        id: JsonElement?,
        request: JsonObject,
        session: ToolSession
    ): JsonObject {
        val params = request["params"]?.jsonObject
        val toolName = params?.get("name")?.jsonPrimitive?.content ?: ""
        val args = params?.get("arguments")?.jsonObject ?: buildJsonObject {}

        log.info("Tool call: {} args={}", toolName, args.keys)
        val start = System.currentTimeMillis()

        return try {
            val toolCall = LLMToolCall(
                id = toolName, // Use tool name as ID since MCP doesn't provide one
                name = toolName,
                arguments = args
            )

            val result = session.mutex.withLock {
                session.executor(toolCall)
            }

            val elapsed = System.currentTimeMillis() - start
            log.info("Tool done: {} in {}ms", toolName, elapsed)

            mcpResponse(id, buildJsonObject {
                putJsonArray("content") {
                    addJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(
                            json.encodeToString(JsonElement.serializer(), result.result)
                        ))
                    }
                }
            })
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            log.error("Tool error: {} after {}ms: {}", toolName, elapsed, e.message, e)

            mcpResponse(id, buildJsonObject {
                putJsonArray("content") {
                    addJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive("""{"error": "${e.message}"}"""))
                    }
                }
                put("isError", JsonPrimitive(true))
            })
        }
    }

    private fun mcpResponse(id: JsonElement?, result: JsonObject): JsonObject {
        return buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            if (id != null) put("id", id)
            put("result", result)
        }
    }

    private fun errorResponse(id: JsonElement?, code: Int, message: String): JsonObject {
        return buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            if (id != null) put("id", id)
            putJsonObject("error") {
                put("code", JsonPrimitive(code))
                put("message", JsonPrimitive(message))
            }
        }
    }
}
