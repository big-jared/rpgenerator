package com.rpgenerator.server

import com.rpgenerator.core.api.LLMInterface

/**
 * Creates the right LLMInterface based on environment variables.
 *
 * Env vars:
 *   LLM_PROVIDER  — gemini (default), claude-cli, codex-cli, mock
 *   LLM_MODEL     — model name, defaults vary per provider
 *
 * Defaults:
 *   gemini     → gemini-2.5-flash
 *   claude-cli → claude-opus-4-6 (via claude --print --model)
 *   codex-cli  → codex-5.4 (via codex --model)
 *
 * Examples:
 *   LLM_PROVIDER=gemini LLM_MODEL=gemini-2.5-flash       → cheap testing
 *   LLM_PROVIDER=claude-cli                                → claude --print
 *   LLM_PROVIDER=codex-cli                                 → codex --quiet
 */
object LLMFactory {

    fun create(): LLMInterface {
        val provider = System.getenv("LLM_PROVIDER")?.lowercase()?.trim() ?: "gemini"
        val model = System.getenv("LLM_MODEL")?.trim()

        val llm = when (provider) {
            "gemini", "google" -> {
                val m = model ?: "gemini-2.5-flash"
                println("LLM: Gemini API ($m)")
                GeminiLLM(m)
            }
            "claude-cli", "claude" -> {
                val m = model ?: "claude-opus-4-6"
                println("LLM: Claude CLI (claude --print --model $m)")
                CliProcessLLM("claude", listOf("--model", m))
            }
            "codex-cli", "codex" -> {
                val m = model ?: "codex-5.4"
                println("LLM: Codex CLI (codex --model $m)")
                CliProcessLLM("codex", listOf("--model", m))
            }
            else -> {
                // Treat as a generic CLI command
                println("LLM: Custom CLI ($provider)")
                CliProcessLLM(provider)
            }
        }

        return llm
    }
}

