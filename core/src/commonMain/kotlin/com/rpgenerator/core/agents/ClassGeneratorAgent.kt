package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.ClassArchetype
import com.rpgenerator.core.domain.ClassTrait
import com.rpgenerator.core.domain.DynamicClassInfo
import com.rpgenerator.core.domain.GameState
import kotlinx.coroutines.flow.toList

/**
 * AI agent that generates dynamic class options based on player backstory,
 * system type, and world context. Returns 3 unique class suggestions with
 * traits, evolution paths, and physical mutations.
 */
internal class ClassGeneratorAgent(private val llm: LLMInterface) {

    private val agentStream by lazy {
        llm.startAgent(SYSTEM_PROMPT)
    }

    /**
     * Generate 4 class suggestions based on backstory and world.
     * Returns null on failure (caller falls back to standard options).
     */
    suspend fun generateClassOptions(state: GameState): List<DynamicClassInfo>? {
        val prompt = """
            Player Name: ${state.playerName}
            Backstory: ${state.backstory}
            System Type: ${state.systemType.name}
            World: ${state.currentLocation.name} — ${state.currentLocation.description}

            Generate 4 unique class options for this player. JSON array only.
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")
        println("[ClassGenerator] Raw response (${response.length} chars): ${response.take(500)}")
        val parsed = parseResponse(response)
        if (parsed == null) {
            println("[ClassGenerator] PARSE FAILED. Full response:\n$response")
        } else {
            println("[ClassGenerator] Parsed ${parsed.size} classes: ${parsed.map { it.name }}")
        }
        return parsed
    }

    private fun parseResponse(response: String): List<DynamicClassInfo>? {
        return try {
            val json = response.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            // Parse JSON array of class objects
            val classes = mutableListOf<DynamicClassInfo>()
            val classBlocks = extractArrayObjects(json)

            for (block in classBlocks) {
                val name = extractString(block, "name") ?: continue
                val description = extractString(block, "description") ?: continue
                val archetypeStr = extractString(block, "archetype") ?: "HYBRID"
                val archetype = try {
                    ClassArchetype.valueOf(archetypeStr.uppercase())
                } catch (_: Exception) {
                    ClassArchetype.HYBRID
                }

                val traits = extractArrayStrings(block, "traits").mapNotNull { traitStr ->
                    val parts = traitStr.split(":", limit = 2)
                    if (parts.size == 2) ClassTrait(
                        name = parts[0].trim(),
                        description = parts[1].trim()
                    ) else null
                }

                val evolutionHints = extractArrayStrings(block, "evolutionHints")
                val physicalMutations = extractArrayStrings(block, "physicalMutations")

                classes.add(DynamicClassInfo(
                    name = name,
                    description = description,
                    archetype = archetype,
                    traits = traits,
                    evolutionHints = evolutionHints,
                    physicalMutations = physicalMutations
                ))
            }

            classes.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractString(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return pattern.find(json)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\n", "\n")
    }

    private fun extractArrayStrings(json: String, key: String): List<String> {
        val pattern = Regex("\"$key\"\\s*:\\s*\\[([^\\]]*)]")
        val match = pattern.find(json) ?: return emptyList()
        val inner = match.groupValues[1]
        return Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(inner).map {
            it.groupValues[1].replace("\\\"", "\"")
        }.toList()
    }

    private fun extractArrayObjects(json: String): List<String> {
        // Simple extraction: find top-level { } blocks in a JSON array
        val result = mutableListOf<String>()
        var depth = 0
        var start = -1
        for (i in json.indices) {
            when (json[i]) {
                '{' -> { if (depth == 0) start = i; depth++ }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        result.add(json.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return result
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You are the Class Generator for a LitRPG game engine.

            Given a player's backstory, personality, and world context, generate 4 UNIQUE class options.

            ## LEVEL 1 CLASSES — Keep It Simple
            These are STARTER classes. The player just entered the system. Names should be short (1-2 words),
            grounded, and immediately understandable. Think "Brawler", "Warden", "Ember Mage" — not
            "Astral-Weave Devourer of the Infinite Maw". Save the epic names for evolutions at higher tiers.

            Good level-1 names: Scout, Ironhide, Flame Caller, Tinkerer, Beastcaller, Bulwark, Hexblade
            Bad level-1 names: Void-Tempered Chronarch, Abyssal Sovereign, Earth-Seated Glutton

            ## Class Design Principles
            - VARIETY IS KEY. Exactly 1 class should directly reference the player's backstory/profession.
            - The other 3 should be RECOGNIZABLE ARCHETYPES that fit the world. Simple, clear roles.
            - A chef gets 1 food-themed class and 3 others: a tank, a caster, something weird. Not 4 food classes.
            - Backstory can subtly FLAVOR the other classes but shouldn't define them.
            - Traits should be MECHANICAL (affect gameplay) not just flavor
            - Physical mutations should be SUBTLE at level 1 — faint glow, slight discoloration, not full body horror
            - Evolution hints show where the class COULD go — this is the exciting part, the payoff for leveling

            ## Archetype Values (for internal combat math)
            COMBAT, MYSTICAL, HYBRID, SUPPORT, UNIQUE
            Pick the closest fit. UNIQUE for anything that doesn't map cleanly.

            ## Trait Format
            Each trait is "Name: mechanical description"
            Good: "Thick Skin: +2 defense, reduce incoming physical damage by 10%"
            Bad: "Strong: You are strong" (too generic, no mechanic)

            ## Response Format (JSON array only, no markdown)
            [
              {
                "name": "Class Name",
                "description": "1-2 sentence class fantasy. What does this class FEEL like?",
                "archetype": "COMBAT",
                "traits": [
                  "Trait Name: mechanical effect description",
                  "Trait Name: mechanical effect description"
                ],
                "evolutionHints": [
                  "Could evolve into X (aggressive path)",
                  "Could evolve into Y (defensive path)",
                  "Could evolve into Z (unique path)"
                ],
                "physicalMutations": [
                  "Subtle physical change caused by the class (e.g. 'eyes glow faintly when using abilities')"
                ]
              }
            ]

            RULES:
            - Exactly 4 classes. No more, no less.
            - MAX 1 class directly themed on backstory. The rest should cover different combat roles.
            - Class names: 1-2 words, simple, evocative. NO compound-hyphenated-title-case monstrosities.
            - Each class should appeal to a DIFFERENT playstyle (tank, DPS, caster, hybrid/support)
            - At least one class should be surprising/unconventional
            - Traits: exactly 2 per class
            - Evolution hints: exactly 3 per class
            - Physical mutations: 1-2 per class (subtle at first, more dramatic as they evolve)
            - JSON ONLY. No explanations, no markdown fences.
        """.trimIndent()
    }
}
