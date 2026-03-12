package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.GameState
import kotlinx.coroutines.flow.toList

/**
 * AI agent that enriches item descriptions and generates visual prompts
 * for item icon generation. Lightweight — only called for add_item.
 */
internal class ItemGeneratorAgent(private val llm: LLMInterface) {

    private val agentStream by lazy {
        llm.startAgent(SYSTEM_PROMPT)
    }

    /**
     * Generate enriched description and visual prompt for an item.
     * Returns null on failure (caller uses default description).
     */
    suspend fun generate(name: String, rarity: String, state: GameState): ItemGenerationResult? {
        val prompt = """
            Item: $name
            Rarity: $rarity
            World: ${state.currentLocation.name} (${state.currentLocation.biome.name})
            Player Level: ${state.playerLevel}

            Generate enhanced description and visual prompt. JSON only.
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")
        println("[ItemGenerator] Raw response (${response.length} chars): ${response.take(500)}")
        val parsed = parseResponse(response)
        if (parsed == null) {
            println("[ItemGenerator] PARSE FAILED. Full response:\n$response")
        } else {
            println("[ItemGenerator] Parsed item: description=${parsed.enhancedDescription.take(80)}...")
        }
        return parsed
    }

    private fun parseResponse(response: String): ItemGenerationResult? {
        return try {
            val json = response.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val description = extractString(json, "enhancedDescription") ?: return null
            val visualPrompt = extractString(json, "visualPrompt") ?: return null

            ItemGenerationResult(
                enhancedDescription = description,
                visualPrompt = visualPrompt
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractString(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return pattern.find(json)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\n", "\n")
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You are the Item Generator for a LitRPG game engine.

            Given an item name, rarity, and world context, generate a flavorful description and a visual prompt for icon generation.

            ## Response Format (JSON only)
            {
              "enhancedDescription": "Flavorful 1-2 sentence item description with lore hints. What does it look like? What's special about it?",
              "visualPrompt": "SEE VISUAL PROMPT RULES BELOW"
            }

            ## Visual Prompt Rules
            The visualPrompt generates a small icon image. It must be:
            - Centered on dark background, clean composition
            - Describe the item's shape, material, color, and any magical effects
            - Include rarity-appropriate glow or aura effects
            - Format: "Fantasy item icon, centered on dark background. [Item shape and type]. [Material and primary color]. [Rarity glow/aura effect]. [Distinctive details or engravings]. Style: clean icon art, painterly, soft lighting, fantasy RPG UI aesthetic."

            ## Rarity Glow Guide
            - COMMON: no glow, simple materials
            - UNCOMMON: faint green shimmer
            - RARE: blue luminous aura
            - EPIC: purple crackling energy
            - LEGENDARY: golden radiance, dramatic lighting
        """.trimIndent()
    }
}

/**
 * Result of item generation — used to enrich item data.
 */
internal data class ItemGenerationResult(
    val enhancedDescription: String,
    val visualPrompt: String
)
