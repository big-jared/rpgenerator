package com.rpgenerator.server

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

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
                .maxOutputTokens(2048)
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

            val words = responseText.split(" ")
            words.forEachIndexed { index, word ->
                if (index < words.size - 1) {
                    emit("$word ")
                } else {
                    emit(word)
                }
            }
        }
    }
}
