package com.rpgenerator.server

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.FunctionCallingConfig
import com.google.genai.types.FunctionCallingConfigMode
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.Tool
import com.google.genai.types.ToolConfig
import com.rpgenerator.core.api.AgentChunk
import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.LLMToolCall
import com.rpgenerator.core.api.LLMToolDef
import com.rpgenerator.core.api.ToolExecutor
import com.google.genai.types.Modality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Google Gemini-powered LLM implementation for the server.
 * Reads GOOGLE_API_KEY from environment (set via Secret Manager on Cloud Run).
 */
class GeminiLLM(
    private val model: String = "gemini-2.5-flash"
) : LLMInterface {

    private val client = Client()

    override fun startAgent(systemPrompt: String): AgentStream {
        return GeminiAgentStream(client, model, systemPrompt)
    }

    private class GeminiAgentStream(
        private val client: Client,
        private val model: String,
        private val systemPrompt: String
    ) : AgentStream {

        private val conversationHistory = mutableListOf<Content>()

        override suspend fun sendMessageWithTools(
            message: String,
            tools: List<LLMToolDef>,
            executor: ToolExecutor
        ): Flow<String> = flow {
            val geminiTools = listOf(
                Tool.builder()
                    .functionDeclarations(tools.map { toFunctionDeclaration(it) })
                    .build()
            )

            val userContent = Content.builder()
                .role("user")
                .parts(listOf(Part.builder().text(message).build()))
                .build()

            val contents = mutableListOf<Content>().apply {
                addAll(conversationHistory)
                add(userContent)
            }

            val systemInstr = Content.builder()
                .parts(listOf(Part.builder().text(systemPrompt).build()))
                .build()

            // Force function calling on first round (ANY = must call at least one tool).
            // After tools execute, switch to AUTO so the model can return text.
            val forceToolConfig = ToolConfig.builder()
                .functionCallingConfig(
                    FunctionCallingConfig.builder()
                        .mode(FunctionCallingConfigMode(FunctionCallingConfigMode.Known.ANY))
                )
                .build()
            val autoToolConfig = ToolConfig.builder()
                .functionCallingConfig(
                    FunctionCallingConfig.builder()
                        .mode(FunctionCallingConfigMode(FunctionCallingConfigMode.Known.AUTO))
                )
                .build()

            var finalText = ""
            var rounds = 0
            var toolsExecuted = false

            while (rounds++ < 10) {
                // First round: force tool calls. After tools fire: auto mode.
                val currentToolConfig = if (!toolsExecuted) forceToolConfig else autoToolConfig
                val config = GenerateContentConfig.builder()
                    .systemInstruction(systemInstr)
                    .tools(geminiTools)
                    .toolConfig(currentToolConfig)
                    .maxOutputTokens(10000)
                    .build()

                val response = try {
                    withContext(Dispatchers.IO) {
                        client.models.generateContent(model, contents.toList(), config)
                    }
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("MALFORMED_FUNCTION_CALL", ignoreCase = true)) {
                        // Gemini generated a bad function call — retry without tools
                        val fallbackConfig = GenerateContentConfig.builder()
                            .systemInstruction(systemInstr)
                            .maxOutputTokens(10000)
                            .build()
                        withContext(Dispatchers.IO) {
                            client.models.generateContent(model, contents.toList(), fallbackConfig)
                        }
                    } else {
                        throw e
                    }
                }

                val functionCalls = response.functionCalls()
                val text = response.text() ?: ""

                if (functionCalls.isNullOrEmpty()) {
                    finalText += text
                    if (text.isNotBlank()) {
                        contents.add(
                            Content.builder().role("model")
                                .parts(listOf(Part.builder().text(text).build()))
                                .build()
                        )
                    }
                    break
                }

                toolsExecuted = true

                // Use original model content to preserve thoughtSignature on function call parts
                val modelContent = response.candidates().orElse(emptyList()).firstOrNull()
                    ?.content()?.orElse(null)
                if (modelContent != null) {
                    contents.add(modelContent)
                } else {
                    val modelParts = mutableListOf<Part>()
                    if (text.isNotBlank()) modelParts.add(Part.builder().text(text).build())
                    functionCalls.forEach { fc ->
                        modelParts.add(Part.builder().functionCall(fc).build())
                    }
                    contents.add(Content.builder().role("model").parts(modelParts).build())
                }

                // Execute each function call, collect response parts
                val responseParts = functionCalls.map { fc ->
                    val name = fc.name().orElse("")
                    val id = fc.id().orElse(name)
                    val args = argsToJsonObject(fc.args().orElse(emptyMap()))

                    val result = executor(LLMToolCall(id = id, name = name, arguments = args))
                    Part.fromFunctionResponse(name, jsonObjectToMap(result.result))
                }

                contents.add(Content.builder().role("user").parts(responseParts).build())

                if (text.isNotBlank()) finalText += text
            }

            // Retry if tools executed but Gemini returned no narration
            if (finalText.isBlank()) {
                val nudge = if (toolsExecuted) {
                    "Your tool calls executed successfully. Now narrate what happened to the player in 2-3 sentences. Do NOT call any more tools."
                } else {
                    "Continue. Respond to the player's action with narration and any appropriate tool calls."
                }
                contents.add(
                    Content.builder().role("user")
                        .parts(listOf(Part.builder().text(nudge).build()))
                        .build()
                )

                // Retry without tools if tools already executed, to force text output
                val retryConfig = if (toolsExecuted) {
                    GenerateContentConfig.builder()
                        .systemInstruction(systemInstr)
                        .maxOutputTokens(10000)
                        .build()
                } else {
                    GenerateContentConfig.builder()
                        .systemInstruction(systemInstr)
                        .tools(geminiTools)
                        .toolConfig(forceToolConfig)
                        .maxOutputTokens(10000)
                        .build()
                }

                val retryResponse = try {
                    withContext(Dispatchers.IO) {
                        client.models.generateContent(model, contents.toList(), retryConfig)
                    }
                } catch (_: Exception) { null }

                if (retryResponse != null) {
                    val retryText = retryResponse.text() ?: ""
                    val retryFunctionCalls = retryResponse.functionCalls()

                    if (!retryFunctionCalls.isNullOrEmpty() && !toolsExecuted) {
                        // Use original content to preserve thoughtSignature
                        val retryModelContent = retryResponse.candidates().orElse(emptyList()).firstOrNull()
                            ?.content()?.orElse(null)
                        if (retryModelContent != null) {
                            contents.add(retryModelContent)
                        } else {
                            val retryModelParts = mutableListOf<Part>()
                            if (retryText.isNotBlank()) retryModelParts.add(Part.builder().text(retryText).build())
                            retryFunctionCalls.forEach { fc -> retryModelParts.add(Part.builder().functionCall(fc).build()) }
                            contents.add(Content.builder().role("model").parts(retryModelParts).build())
                        }

                        val responseParts = retryFunctionCalls.map { fc ->
                            val name = fc.name().orElse("")
                            val id = fc.id().orElse(name)
                            val args = argsToJsonObject(fc.args().orElse(emptyMap()))
                            val result = executor(LLMToolCall(id = id, name = name, arguments = args))
                            Part.fromFunctionResponse(name, jsonObjectToMap(result.result))
                        }
                        contents.add(Content.builder().role("user").parts(responseParts).build())

                        val afterToolsConfig = GenerateContentConfig.builder()
                            .systemInstruction(systemInstr)
                            .tools(geminiTools)
                            .toolConfig(autoToolConfig)
                            .maxOutputTokens(10000)
                            .build()
                        val finalResponse = withContext(Dispatchers.IO) {
                            client.models.generateContent(model, contents.toList(), afterToolsConfig)
                        }
                        finalText = (retryText + " " + (finalResponse.text() ?: "")).trim()
                    } else {
                        finalText = retryText
                    }
                }
            }

            // Update conversation history (skip empty model messages)
            conversationHistory.add(userContent)
            if (finalText.isNotBlank()) {
                conversationHistory.add(
                    Content.builder().role("model")
                        .parts(listOf(Part.builder().text(finalText).build()))
                        .build()
                )
            }

            emitWords(finalText)
        }

        override suspend fun sendMessage(message: String): Flow<String> = flow {
            val userContent = Content.builder()
                .role("user")
                .parts(listOf(Part.builder().text(message).build()))
                .build()

            val allContents = buildList {
                addAll(conversationHistory)
                add(userContent)
            }

            val config = GenerateContentConfig.builder()
                .systemInstruction(
                    Content.builder()
                        .parts(listOf(Part.builder().text(systemPrompt).build()))
                        .build()
                )
                .maxOutputTokens(10000)
                .build()

            val response = withContext(Dispatchers.IO) {
                client.models.generateContent(model, allContents, config)
            }

            val responseText = response.text() ?: ""

            conversationHistory.add(userContent)
            conversationHistory.add(
                Content.builder()
                    .role("model")
                    .parts(listOf(Part.builder().text(responseText).build()))
                    .build()
            )

            emitWords(responseText)
        }

        override suspend fun sendMessageMultimodal(
            message: String,
            generateImage: Boolean
        ): Flow<AgentChunk> = flow {
            val userContent = Content.builder()
                .role("user")
                .parts(listOf(Part.builder().text(message).build()))
                .build()

            val allContents = buildList {
                addAll(conversationHistory)
                add(userContent)
            }

            val configBuilder = GenerateContentConfig.builder()
                .systemInstruction(
                    Content.builder()
                        .parts(listOf(Part.builder().text(systemPrompt).build()))
                        .build()
                )
                .maxOutputTokens(10000)

            if (generateImage) {
                configBuilder.responseModalities("TEXT", "IMAGE")
            }

            val config = configBuilder.build()

            // Use Gemini native image generation model
            val imageModel = if (generateImage) "gemini-2.5-flash-image" else model

            val response = withContext(Dispatchers.IO) {
                client.models.generateContent(imageModel, allContents, config)
            }

            // Extract text and image parts from response
            val parts = response.candidates().orElse(emptyList()).firstOrNull()
                ?.content()?.orElse(null)?.parts()?.orElse(emptyList()) ?: emptyList()

            val textParts = mutableListOf<String>()
            for (part in parts) {
                val text = part.text().orElse(null)
                if (text != null) {
                    textParts.add(text)
                    emit(AgentChunk.Text(text))
                }
                val inlineData = part.inlineData().orElse(null)
                if (inlineData != null) {
                    val imageBytes = inlineData.data().orElse(null)
                    val mimeType = inlineData.mimeType().orElse("image/png")
                    if (imageBytes != null) {
                        emit(AgentChunk.Image(data = imageBytes, mimeType = mimeType))
                    }
                }
            }

            val fullText = textParts.joinToString("")
            conversationHistory.add(userContent)
            if (fullText.isNotBlank()) {
                conversationHistory.add(
                    Content.builder().role("model")
                        .parts(listOf(Part.builder().text(fullText).build()))
                        .build()
                )
            }
        }

        private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.emitWords(text: String) {
            val words = text.split(" ")
            words.forEachIndexed { index, word ->
                emit(if (index < words.size - 1) "$word " else word)
            }
        }

        private fun toFunctionDeclaration(def: LLMToolDef): FunctionDeclaration {
            val properties = mutableMapOf<String, Schema>()
            val required = mutableListOf<String>()

            val propsObj = def.parameters["properties"]
            if (propsObj is JsonObject) {
                propsObj.forEach { (name, schema) ->
                    if (schema is JsonObject) {
                        properties[name] = Schema.builder()
                            .type((schema["type"]?.jsonPrimitive?.content ?: "string").uppercase())
                            .description(schema["description"]?.jsonPrimitive?.content ?: "")
                            .build()
                    }
                }
            }

            val reqArray = def.parameters["required"]
            if (reqArray is JsonArray) {
                reqArray.forEach { required.add(it.jsonPrimitive.content) }
            }

            val schema = Schema.builder().type("OBJECT").properties(properties)
            if (required.isNotEmpty()) schema.required(required)

            return FunctionDeclaration.builder()
                .name(def.name)
                .description(def.description)
                .parameters(schema.build())
                .build()
        }

        private fun argsToJsonObject(args: Map<String, Any?>): JsonObject = buildJsonObject {
            args.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, JsonPrimitive(value))
                    is Number -> put(key, JsonPrimitive(value))
                    is Boolean -> put(key, JsonPrimitive(value))
                    null -> {}
                    else -> put(key, JsonPrimitive(value.toString()))
                }
            }
        }

        private fun jsonObjectToMap(json: JsonObject): Map<String, Any> {
            val result = mutableMapOf<String, Any>()
            json.forEach { (key, value) ->
                when (value) {
                    is JsonPrimitive -> when {
                        value.isString -> result[key] = value.content
                        value.content == "true" -> result[key] = true
                        value.content == "false" -> result[key] = false
                        else -> result[key] = value.content.toLongOrNull() ?: value.content
                    }
                    is JsonObject -> result[key] = jsonObjectToMap(value)
                    else -> result[key] = value.toString()
                }
            }
            return result
        }
    }
}
