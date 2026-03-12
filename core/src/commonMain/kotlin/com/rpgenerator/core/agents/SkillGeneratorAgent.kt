package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.DynamicClassInfo
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.skill.DamageType
import com.rpgenerator.core.skill.Skill
import com.rpgenerator.core.skill.SkillCategory
import com.rpgenerator.core.skill.SkillEffect
import com.rpgenerator.core.skill.SkillRarity
import com.rpgenerator.core.skill.StatType
import com.rpgenerator.core.skill.TargetType
import com.rpgenerator.core.skill.AcquisitionSource
import kotlinx.coroutines.flow.toList

/**
 * AI agent that generates dynamic skills based on class, backstory,
 * and progression context. Used for:
 * - Initial skill options after class selection (4-5 options)
 * - Level-up skill offerings
 * - Custom skill creation from player descriptions
 */
internal class SkillGeneratorAgent(private val llm: LLMInterface) {

    private val agentStream by lazy {
        llm.startAgent(SYSTEM_PROMPT)
    }

    /**
     * Generate 4-5 starter skill options for a newly selected class.
     */
    suspend fun generateStarterSkills(classInfo: DynamicClassInfo, state: GameState): List<GeneratedSkill>? {
        val prompt = """
            CONTEXT: Starter skills for a new class selection.
            Class: ${classInfo.name}
            Class Description: ${classInfo.description}
            Class Archetype: ${classInfo.archetype.name}
            Class Traits: ${classInfo.traits.joinToString("; ") { "${it.name}: ${it.description}" }}
            Player Backstory: ${state.backstory}
            Player Level: ${state.playerLevel}
            System Type: ${state.systemType.name}

            Generate 5 starter skill options. Player picks 2. These are level 1 abilities. JSON array only.
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")
        println("[SkillGenerator] Raw response (${response.length} chars): ${response.take(500)}")
        val parsed = parseResponse(response)
        if (parsed == null) {
            println("[SkillGenerator] PARSE FAILED. Full response:\n$response")
        } else {
            println("[SkillGenerator] Parsed ${parsed.size} skills: ${parsed.map { it.name }}")
        }
        return parsed
    }

    /**
     * Generate level-up skill options based on current build.
     */
    suspend fun generateLevelUpSkills(state: GameState, newLevel: Int): List<GeneratedSkill>? {
        val sheet = state.characterSheet
        val classInfo = sheet.dynamicClass ?: return null
        val existingSkills = sheet.skills.joinToString(", ") { it.name }

        val prompt = """
            CONTEXT: Level-up skill options.
            Class: ${classInfo.name} (${classInfo.archetype.name})
            Current Level: $newLevel
            Existing Skills: $existingSkills
            Player Backstory: ${state.backstory}
            ${if (classInfo.physicalMutations.isNotEmpty()) "Physical Mutations: ${classInfo.physicalMutations.joinToString(", ")}" else ""}

            Generate 3 new skill options that build on their existing kit. Should be stronger than starter skills.
            At least one should be surprising or synergize with their traits.
            JSON array only.
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")
        println("[SkillGenerator] Raw response (${response.length} chars): ${response.take(500)}")
        val parsed = parseResponse(response)
        if (parsed == null) {
            println("[SkillGenerator] PARSE FAILED. Full response:\n$response")
        } else {
            println("[SkillGenerator] Parsed ${parsed.size} skills: ${parsed.map { it.name }}")
        }
        return parsed
    }

    /**
     * Generate a single custom skill from a player's description.
     */
    suspend fun generateCustomSkill(description: String, state: GameState): GeneratedSkill? {
        val sheet = state.characterSheet
        val classInfo = sheet.dynamicClass

        val prompt = """
            CONTEXT: Player described a custom skill they want.
            Player Description: "$description"
            Class: ${classInfo?.name ?: sheet.playerClass.displayName}
            Player Level: ${state.playerLevel}
            Existing Skills: ${sheet.skills.joinToString(", ") { it.name }}

            Generate exactly 1 skill based on their description. Make it balanced for their level.
            JSON object only (not array).
        """.trimIndent()

        val response = agentStream.sendMessage(prompt).toList().joinToString("")
        println("[SkillGenerator] Raw response (${response.length} chars): ${response.take(500)}")
        val results = parseResponse(response)
        if (results == null) {
            println("[SkillGenerator] PARSE FAILED. Full response:\n$response")
        } else {
            println("[SkillGenerator] Parsed ${results.size} skills: ${results.map { it.name }}")
        }
        return results?.firstOrNull()
    }

    private fun parseResponse(response: String): List<GeneratedSkill>? {
        return try {
            val json = response.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val skills = mutableListOf<GeneratedSkill>()
            val blocks = extractObjectBlocks(json)

            for (block in blocks) {
                val name = extractString(block, "name") ?: continue
                val description = extractString(block, "description") ?: continue
                val categoryStr = extractString(block, "category") ?: extractString(block, "type") ?: "combat"
                val costType = extractString(block, "costType") ?: "energy"
                val cost = extractInt(block, "cost") ?: 15
                val cooldown = extractInt(block, "cooldown") ?: 2
                val targetStr = extractString(block, "target") ?: "enemy"

                // Parse effects array
                val effects = parseEffectsArray(block)

                // Legacy flat fields as fallback
                val damageStr = extractString(block, "damageType")
                val damage = extractInt(block, "damage")
                val healAmount = extractInt(block, "heal")
                val scalingStatStr = extractString(block, "scalingStat") ?: "STRENGTH"

                skills.add(GeneratedSkill(
                    name = name,
                    description = description,
                    category = categoryStr,
                    costType = costType,
                    cost = cost,
                    cooldown = cooldown,
                    target = targetStr,
                    effectsRaw = effects,
                    damageType = damageStr,
                    damage = damage,
                    heal = healAmount,
                    scalingStat = scalingStatStr
                ))
            }

            skills.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse the "effects" array from a skill JSON block.
     * Each effect is an object like { "type": "damage", "base": 25, "damageType": "FIRE", ... }
     */
    private fun parseEffectsArray(skillBlock: String): List<GeneratedEffect> {
        // Find the "effects" array
        val effectsPattern = Regex("\"effects\"\\s*:\\s*\\[")
        val match = effectsPattern.find(skillBlock) ?: return emptyList()

        // Extract the array content by bracket matching
        val startIdx = match.range.last + 1
        var depth = 1
        var endIdx = startIdx
        for (i in startIdx until skillBlock.length) {
            when (skillBlock[i]) {
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) { endIdx = i; break } }
            }
        }
        val arrayContent = skillBlock.substring(startIdx, endIdx)

        // Extract each effect object
        val effectBlocks = extractObjectBlocks(arrayContent)
        return effectBlocks.mapNotNull { block ->
            val type = extractString(block, "type") ?: return@mapNotNull null
            GeneratedEffect(
                type = type,
                base = extractInt(block, "base") ?: 0,
                damageType = extractString(block, "damageType") ?: "PHYSICAL",
                scalingStat = extractString(block, "scalingStat") ?: "STRENGTH",
                scalingRatio = extractDouble(block, "scalingRatio") ?: 0.5,
                stat = extractString(block, "stat") ?: "",
                amount = extractInt(block, "amount") ?: 0,
                duration = extractInt(block, "duration") ?: 0
            )
        }
    }

    private fun extractDouble(json: String, key: String): Double? {
        val pattern = Regex("\"$key\"\\s*:\\s*([0-9]+\\.?[0-9]*)")
        return pattern.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun extractString(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return pattern.find(json)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\n", "\n")
    }

    private fun extractInt(json: String, key: String): Int? {
        val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractObjectBlocks(json: String): List<String> {
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
            You are the Skill Generator for a LitRPG game engine with a real combat system.
            Skills MUST have concrete numbers because they feed directly into damage/healing calculations.

            ## LEVEL 1 SKILLS — Keep It Simple
            Starter skills should be BASIC, grounded combat moves. The player just got their class.
            Think "Slash", "Fire Bolt", "Guard", "Quick Strike" — not "Cataclysmic Void Rend".
            Names: 1-3 words max. Simple and clear. Save the epic names for level 10+ skills.

            Good level-1 names: Power Strike, Ember Toss, Iron Guard, Quick Heal, Venom Spit
            Bad level-1 names: Devouring Maw of the Infinite Abyss, Quantum Probability Cascade

            Generate balanced skills themed to the player's CLASS (not their backstory).
            The class already reflects their backstory — skills should feel like combat abilities, not jokes about who they used to be.
            A food-themed class can have 1 food-flavored skill, but the rest should be real combat/defense/utility moves.
            Skill names should sound like abilities in a game, not punchlines.

            ## Damage Types (affect resistances/vulnerabilities in combat)
            PHYSICAL (reduced by defense), FIRE (may burn), ICE (may slow), LIGHTNING (may stun),
            POISON (DoT), DARK (lifesteal), HOLY (vs undead), TRUE (ignores defense), MAGICAL (reduced by wisdom)

            ## Scaling Stats
            STRENGTH, DEXTERITY, CONSTITUTION, INTELLIGENCE, WISDOM, CHARISMA

            ## Target Types
            enemy (single target), self, all_enemies (AOE — include radius in description)

            ## Effect Types (a skill can have MULTIPLE effects)
            - "damage": { "base": 25, "type": "FIRE", "scalingStat": "STRENGTH", "scalingRatio": 0.5 }
            - "heal": { "base": 20, "scalingStat": "WISDOM", "scalingRatio": 0.3 }
            - "buff": { "stat": "STRENGTH", "amount": 3, "duration": 3 }
            - "debuff": { "stat": "DEXTERITY", "amount": 4, "duration": 2 }
            - "dot": { "base": 8, "type": "POISON", "duration": 3, "scalingStat": "CONSTITUTION" }
            - "hot": { "base": 5, "duration": 3, "scalingStat": "WISDOM" }
            - "shield": { "base": 30, "duration": 3, "scalingStat": "CONSTITUTION", "scalingRatio": 0.5 }

            ## Response Format (JSON array, no markdown)
            [
              {
                "name": "Quaking Slam",
                "description": "Slam fists into the ground, sending a shockwave in a 10ft radius. Cracks stone.",
                "category": "combat",
                "costType": "energy",
                "cost": 20,
                "cooldown": 2,
                "target": "all_enemies",
                "effects": [
                  { "type": "damage", "base": 22, "damageType": "PHYSICAL", "scalingStat": "STRENGTH", "scalingRatio": 0.5 }
                ]
              },
              {
                "name": "Iron Stomach",
                "description": "Consume a nearby material to regenerate health and gain temporary armor.",
                "category": "defensive",
                "costType": "energy",
                "cost": 15,
                "cooldown": 3,
                "target": "self",
                "effects": [
                  { "type": "heal", "base": 15, "scalingStat": "CONSTITUTION", "scalingRatio": 0.4 },
                  { "type": "buff", "stat": "CONSTITUTION", "amount": 3, "duration": 3 }
                ]
              }
            ]

            ## Balance Rules
            - Damage: Level 1 (15-30 base), Level 5 (25-50), Level 10 (40-80)
            - Healing: ~60-70% of equivalent damage numbers
            - Shield: ~80% of equivalent damage numbers
            - Buff/debuff amounts: 2-5 at level 1, 4-8 at level 5
            - DoT: ~40% of direct damage per tick, 2-4 turns duration
            - Costs: cheap (8-12), medium (15-25), expensive (30-50)
            - Cooldowns: 0 (spammable, low damage), 1-2 (normal), 3-5 (powerful)
            - Every skill MUST have a cost (mana or energy)
            - AOE skills: 20-30% less damage than single-target equivalents
            - Multi-effect skills: each effect slightly weaker than single-effect equivalents
            - Match damage type to class theme (fire for furnace classes, poison for nature, etc.)
            - JSON ONLY. No explanations, no markdown.
        """.trimIndent()
    }
}

/**
 * Intermediate result from skill generation — converted to a real Skill by the tool layer.
 */
internal data class GeneratedSkill(
    val name: String,
    val description: String,
    val category: String = "combat",
    val costType: String = "energy",
    val cost: Int = 15,
    val cooldown: Int = 2,
    val target: String = "enemy",
    val effectsRaw: List<GeneratedEffect> = emptyList(),
    // Legacy flat fields (fallback if effects array not present)
    val damageType: String? = null,
    val damage: Int? = null,
    val heal: Int? = null,
    val scalingStat: String = "STRENGTH"
) {
    /**
     * Convert to a real Skill domain object.
     */
    fun toSkill(className: String): Skill {
        val id = "gen_${name.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")}_${System.currentTimeMillis()}"

        val tgt = when (target.lowercase()) {
            "self" -> TargetType.SELF
            "all_enemies", "all" -> TargetType.ALL_ENEMIES
            else -> TargetType.SINGLE_ENEMY
        }
        val cat = when (category.lowercase()) {
            "support" -> SkillCategory.SUPPORT
            "defensive" -> SkillCategory.DEFENSIVE
            "utility" -> SkillCategory.UTILITY
            "movement" -> SkillCategory.MOVEMENT
            else -> SkillCategory.COMBAT
        }

        val effects = if (effectsRaw.isNotEmpty()) {
            effectsRaw.mapNotNull { it.toSkillEffect() }
        } else {
            // Legacy fallback for flat damage/heal fields
            buildLegacyEffects()
        }

        return Skill(
            id = id,
            name = name,
            description = description,
            rarity = SkillRarity.COMMON,
            manaCost = if (costType.lowercase() == "mana") cost else 0,
            energyCost = if (costType.lowercase() == "energy") cost else 0,
            baseCooldown = cooldown,
            effects = effects,
            targetType = tgt,
            acquisitionSource = AcquisitionSource.ClassStarter(className),
            category = cat
        )
    }

    private fun buildLegacyEffects(): List<SkillEffect> {
        val scalingSt = try { StatType.valueOf(scalingStat.uppercase()) } catch (_: Exception) { StatType.STRENGTH }
        val dmgType = try { DamageType.valueOf((damageType ?: "PHYSICAL").uppercase()) } catch (_: Exception) { DamageType.PHYSICAL }
        val effects = mutableListOf<SkillEffect>()
        if (damage != null && damage > 0) {
            effects.add(SkillEffect.Damage(baseAmount = damage, scalingStat = scalingSt, scalingRatio = 0.5, damageType = dmgType))
        }
        if (heal != null && heal > 0) {
            effects.add(SkillEffect.Heal(baseAmount = heal, scalingStat = if (scalingSt == StatType.STRENGTH) StatType.WISDOM else scalingSt, scalingRatio = 0.4))
        }
        return effects
    }
}

/**
 * A single parsed effect from the LLM's effects array.
 */
internal data class GeneratedEffect(
    val type: String,           // damage, heal, buff, debuff, dot, hot, shield
    val base: Int = 0,
    val damageType: String = "PHYSICAL",
    val scalingStat: String = "STRENGTH",
    val scalingRatio: Double = 0.5,
    val stat: String = "",      // for buff/debuff: which stat
    val amount: Int = 0,        // for buff/debuff: bonus/penalty
    val duration: Int = 0       // for buff/debuff/dot/hot/shield: turns
) {
    fun toSkillEffect(): SkillEffect? {
        val scaling = try { StatType.valueOf(scalingStat.uppercase()) } catch (_: Exception) { StatType.STRENGTH }
        val dmgType = try { DamageType.valueOf(damageType.uppercase()) } catch (_: Exception) { DamageType.PHYSICAL }
        val buffStat = if (stat.isNotBlank()) try { StatType.valueOf(stat.uppercase()) } catch (_: Exception) { StatType.STRENGTH } else StatType.STRENGTH

        return when (type.lowercase()) {
            "damage" -> SkillEffect.Damage(baseAmount = base, scalingStat = scaling, scalingRatio = scalingRatio, damageType = dmgType)
            "heal" -> SkillEffect.Heal(baseAmount = base, scalingStat = scaling, scalingRatio = scalingRatio)
            "buff" -> SkillEffect.Buff(statAffected = buffStat, baseBonus = amount.coerceAtLeast(base), duration = duration.coerceAtLeast(1))
            "debuff" -> SkillEffect.Debuff(statAffected = buffStat, basePenalty = amount.coerceAtLeast(base), duration = duration.coerceAtLeast(1))
            "dot" -> SkillEffect.DamageOverTime(baseDamagePerTurn = base, duration = duration.coerceAtLeast(1), scalingStat = scaling, scalingRatio = scalingRatio.coerceAtMost(0.3), damageType = dmgType)
            "hot" -> SkillEffect.HealOverTime(baseHealPerTurn = base, duration = duration.coerceAtLeast(1), scalingStat = scaling, scalingRatio = scalingRatio.coerceAtMost(0.3))
            "shield" -> SkillEffect.Shield(baseAmount = base, duration = duration.coerceAtLeast(1), scalingStat = scaling, scalingRatio = scalingRatio)
            else -> null
        }
    }
}
