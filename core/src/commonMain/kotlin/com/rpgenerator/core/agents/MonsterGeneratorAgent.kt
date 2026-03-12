package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.EnemyAbility
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.skill.DamageType
import kotlinx.coroutines.flow.toList

/**
 * AI agent that generates rich monster profiles with abilities, resistances,
 * lore descriptions, and visual prompts for image generation.
 *
 * Used for non-bestiary enemies — procedurally generated encounters get
 * creative names, ecologically consistent abilities, and portrait prompts.
 */
internal class MonsterGeneratorAgent(private val llm: LLMInterface) {

    private val agentStream by lazy {
        llm.startAgent(SYSTEM_PROMPT)
    }

    /**
     * Generate a complete monster profile for a non-bestiary enemy.
     * Returns null on failure (caller falls back to procedural generation).
     */
    suspend fun generate(enemyName: String, danger: Int, state: GameState): MonsterGenerationResult? {
        val zone = state.currentLocation
        val prompt = """
            Enemy: $enemyName
            Danger Level: $danger (1-10 scale)
            Zone: ${zone.name} (${zone.biome.name})
            Zone Description: ${zone.description}
            Zone Lore: ${zone.lore}
            Player Level: ${state.playerLevel}

            Generate a complete enemy profile. Respond with JSON only.
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")
        println("[MonsterGenerator] Raw response (${response.length} chars): ${response.take(500)}")
        val parsed = parseResponse(response)
        if (parsed == null) {
            println("[MonsterGenerator] PARSE FAILED. Full response:\n$response")
        } else {
            println("[MonsterGenerator] Parsed monster: abilities=${parsed.abilities.map { it.name }}, immunities=${parsed.immunities}, lootTier=${parsed.lootTier}")
        }
        return parsed
    }

    private fun parseResponse(response: String): MonsterGenerationResult? {
        return try {
            val json = response.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            // Parse fields with regex (same pattern as NPCArchetypeGenerator)
            val description = extractString(json, "description") ?: ""
            val visualDescription = extractString(json, "visualDescription") ?: ""
            val visualPrompt = extractString(json, "visualPrompt") ?: ""
            val lootTier = extractString(json, "lootTier") ?: "normal"

            val immunities = extractStringArray(json, "immunities").mapNotNull { parseDamageType(it) }.toSet()
            val vulnerabilities = extractStringArray(json, "vulnerabilities").mapNotNull { parseDamageType(it) }.toSet()
            val resistances = extractStringArray(json, "resistances").mapNotNull { parseDamageType(it) }.toSet()

            val abilities = parseAbilities(json)

            MonsterGenerationResult(
                description = description,
                visualDescription = visualDescription,
                visualPrompt = visualPrompt,
                abilities = abilities,
                immunities = immunities,
                vulnerabilities = vulnerabilities,
                resistances = resistances,
                lootTier = lootTier
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAbilities(json: String): List<EnemyAbility> {
        // Find the abilities array
        val abilitiesMatch = Regex("\"abilities\"\\s*:\\s*\\[([^\\]]*(?:\\{[^}]*}[^\\]]*)*)]", RegexOption.DOT_MATCHES_ALL)
            .find(json) ?: return emptyList()
        val abilitiesBlock = abilitiesMatch.groupValues[1]

        // Parse individual ability objects
        val abilityPattern = Regex("\\{([^}]+)}")
        return abilityPattern.findAll(abilitiesBlock).mapNotNull { match ->
            val obj = match.groupValues[1]
            val name = extractString(obj, "name") ?: return@mapNotNull null
            val bonusDamage = extractInt(obj, "bonusDamage") ?: 0
            val cooldown = extractInt(obj, "cooldown") ?: 3
            val desc = extractString(obj, "description") ?: ""
            val dmgType = extractString(obj, "damageType")?.let { parseDamageType(it) } ?: DamageType.PHYSICAL

            EnemyAbility(
                name = name,
                damage = bonusDamage,
                cooldown = cooldown,
                description = desc,
                damageType = dmgType
            )
        }.toList()
    }

    private fun extractString(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return pattern.find(json)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\n", "\n")
    }

    private fun extractInt(json: String, key: String): Int? {
        val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractStringArray(json: String, key: String): List<String> {
        val pattern = Regex("\"$key\"\\s*:\\s*\\[([^\\]]*)]")
        val match = pattern.find(json) ?: return emptyList()
        return Regex("\"([^\"]+)\"").findAll(match.groupValues[1]).map { it.groupValues[1] }.toList()
    }

    private fun parseDamageType(s: String): DamageType? {
        return try { DamageType.valueOf(s.uppercase().trim()) } catch (_: Exception) { null }
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You are the Monster Generator for a LitRPG game engine.

            Given an enemy name, danger level (1-10), and zone context, generate a complete enemy profile.

            ## Principles
            - ECOLOGICAL CONSISTENCY: Enemies fit the zone biome and lore. A fire elemental belongs in volcanic zones, not frozen tundra.
            - DAMAGE TYPE LOGIC: Fire creatures immune to FIRE, vulnerable to ICE. Undead immune to POISON, vulnerable to HOLY. Crystal creatures resist PHYSICAL, weak to LIGHTNING.
            - ABILITY DESIGN: Creative names tied to creature nature. "Venomous Lunge" not "Attack 2". Abilities should feel like something THIS creature would do.
            - VISUAL DISTINCTIVENESS: Each enemy should have a unique, memorable visual identity that fits its ecology and danger level.

            ## Response Format (JSON only)
            {
              "description": "2-3 sentence lore description for narration. How it behaves, where it lurks, what makes it dangerous.",
              "visualDescription": "Physical appearance: size relative to human, coloring, distinguishing features, body type. 2-3 sentences.",
              "abilities": [
                {"name": "string", "bonusDamage": int, "cooldown": int, "description": "string", "damageType": "PHYSICAL|FIRE|ICE|LIGHTNING|POISON|HOLY|DARK|ARCANE|TRUE"}
              ],
              "immunities": ["DAMAGE_TYPE"],
              "vulnerabilities": ["DAMAGE_TYPE"],
              "resistances": ["DAMAGE_TYPE"],
              "lootTier": "normal|elite|boss",
              "visualPrompt": "SEE VISUAL PROMPT RULES BELOW"
            }

            ## Ability Count by Danger
            - Danger 1-2: 0-1 abilities
            - Danger 3-5: 1-2 abilities
            - Danger 6-8: 2-3 abilities
            - Danger 9-10: 3-4 abilities

            ## Loot Tiers
            - normal: danger 1-4
            - elite: danger 5-7
            - boss: danger 8-10

            ## Damage Types
            PHYSICAL, FIRE, ICE, LIGHTNING, POISON, HOLY, DARK, ARCANE, TRUE

            ## Visual Prompt Rules
            The visualPrompt is used by an image generation AI to create a circular portrait. It must be:
            - Highly specific and visual — describe exactly what the viewer sees
            - Include: creature's body type, size, coloring, texture, distinguishing features, pose, expression
            - Include zone-appropriate background and color palette
            - Always end with art style instructions
            - Format: "Circular fantasy RPG monster portrait, painterly style, [zone color palette]. [Detailed creature description — body, size, texture, coloring, distinguishing features]. [Pose and expression]. Background: [zone-appropriate environment]. Oil painting style, detailed brushwork, fantasy RPG UI aesthetic."

            Example visual prompt:
            "Circular fantasy RPG monster portrait, painterly style, crystalline black, prismatic light, and void tones. A humanoid figure made entirely of black crystal — featureless face, no mouth, no eyes, just a smooth obsidian surface that reflects everything around it. Perfectly proportioned, eerily still. Prismatic light refracts through its body at the edges, rainbow fractures in the darkness. One hand extended, palm up. The void stretches infinitely behind it. Expression: no face, no emotion — but the tilt of its head says it already knows how you fight. Background: empty platform in infinite void, black crystal floor, distant stars. Oil painting style, detailed brushwork, fantasy RPG UI aesthetic."
        """.trimIndent()
    }
}

/**
 * Result of monster generation — used to enrich Enemy data before combat.
 */
internal data class MonsterGenerationResult(
    val description: String,
    val visualDescription: String,
    val visualPrompt: String,
    val abilities: List<EnemyAbility>,
    val immunities: Set<DamageType>,
    val vulnerabilities: Set<DamageType>,
    val resistances: Set<DamageType>,
    val lootTier: String
)
