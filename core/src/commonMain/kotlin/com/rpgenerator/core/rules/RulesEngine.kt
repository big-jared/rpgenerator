package com.rpgenerator.core.rules

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.loot.*
import kotlin.random.Random

internal class RulesEngine {

    private val itemGenerator = ItemGenerator()

    fun calculateCombatOutcome(target: String, state: GameState): CombatOutcome {
        val stats = state.characterSheet.effectiveStats()

        // Player damage: 1d6 + (STR / 2) + weapon bonus
        val baseDamage = Random.nextInt(1, 7)
        val strengthBonus = stats.strength / 2
        val weaponDamage = state.characterSheet.equipment.weapon?.baseDamage ?: 0
        val totalDamage = baseDamage + strengthBonus + weaponDamage

        // Enemy counterattack: scales with location danger and enemy type
        val enemyDanger = inferEnemyDanger(target, state.currentLocation.danger)
        val enemyBaseDamage = Random.nextInt(1, 4) + enemyDanger
        val playerDefense = stats.defense + (stats.constitution / 4)
        val damageTaken = (enemyBaseDamage - playerDefense / 2).coerceAtLeast(1)

        // XP scaling with location danger and level
        val basexp = 50L
        val dangerMultiplier = (state.currentLocation.danger / 5).coerceAtLeast(1)
        val xpGain = basexp * dangerMultiplier

        val newXP = state.characterSheet.xp + xpGain
        val shouldLevelUp = newXP >= state.characterSheet.xpToNextLevel()

        // Generate loot based on enemy type and location danger
        val lootTable = determineLootTable(target, state.currentLocation.danger)
        val lootResult = lootTable.rollLoot(
            playerLevel = state.characterSheet.level,
            locationDanger = state.currentLocation.danger,
            luckModifier = calculateLuckModifier(state)
        )

        // Generate actual items from loot drops
        val generatedItems = itemGenerator.generateLoot(
            lootResult.items,
            state.characterSheet.level
        )

        return CombatOutcome(
            damage = totalDamage,
            damageTaken = damageTaken,
            xpGain = xpGain,
            newXP = newXP,
            levelUp = shouldLevelUp,
            newLevel = if (shouldLevelUp) state.characterSheet.level + 1 else state.characterSheet.level,
            loot = generatedItems,
            gold = lootResult.gold
        )
    }

    private fun inferEnemyDanger(target: String, locationDanger: Int): Int {
        val normalized = target.lowercase()
        val typeDanger = when {
            normalized.contains("rat") || normalized.contains("slime") -> 1
            normalized.contains("goblin") || normalized.contains("kobold") -> 2
            normalized.contains("orc") || normalized.contains("troll") -> 4
            normalized.contains("ogre") || normalized.contains("demon") -> 6
            normalized.contains("dragon") || normalized.contains("boss") -> 8
            else -> locationDanger / 2
        }
        return typeDanger.coerceAtLeast(1)
    }

    fun determineLootTablePublic(lootTier: String, locationDanger: Int): LootTable {
        return when (lootTier) {
            "boss" -> LootTables.forEnemyType("boss")
            "elite" -> LootTables.forLocationDanger((locationDanger * 1.5).toInt().coerceAtMost(10))
            else -> LootTables.forLocationDanger(locationDanger)
        }
    }

    fun calculateLuckModifierPublic(state: GameState): Double = calculateLuckModifier(state)

    private fun determineLootTable(target: String, locationDanger: Int): LootTable {
        // First try to match by enemy type from target name
        val enemyType = extractEnemyType(target)
        val enemyTable = if (enemyType != null) {
            LootTables.forEnemyType(enemyType)
        } else {
            // Fall back to location-based loot
            LootTables.forLocationDanger(locationDanger)
        }

        return enemyTable
    }

    private fun extractEnemyType(target: String): String? {
        val normalized = target.lowercase()
        return when {
            normalized.contains("goblin") -> "goblin"
            normalized.contains("kobold") -> "kobold"
            normalized.contains("orc") -> "orc"
            normalized.contains("troll") -> "troll"
            normalized.contains("ogre") -> "ogre"
            normalized.contains("dragon") -> "dragon"
            normalized.contains("demon") -> "demon"
            normalized.contains("lich") -> "lich"
            normalized.contains("boss") -> "boss"
            else -> null
        }
    }

    private fun calculateLuckModifier(state: GameState): Double {
        // Base luck from wisdom and charisma
        val stats = state.characterSheet.effectiveStats()
        val wisdomBonus = (stats.wisdom - 10) * 0.01
        val charismaBonus = (stats.charisma - 10) * 0.01

        return (wisdomBonus + charismaBonus).coerceIn(-0.2, 0.3)
    }

    /**
     * D&D-style skill check: d20 + stat modifier + proficiency.
     * Returns structured result with roll, DC, and degree of success.
     */
    fun skillCheck(
        checkType: SkillCheckType,
        difficulty: SkillCheckDifficulty,
        state: GameState
    ): SkillCheckResult {
        val stats = state.characterSheet.effectiveStats()

        // Stat modifier (D&D style: (stat - 10) / 2)
        val rawStat = checkType.getStatValue(stats)
        val modifier = (rawStat - 10) / 2

        // Proficiency bonus from level (like D&D: +2 at L1, scaling up)
        val proficiency = 2 + (state.playerLevel / 5)

        // Roll d20
        val roll = Random.nextInt(1, 21)
        val total = roll + modifier + proficiency

        // Natural 20 = critical success, natural 1 = critical failure
        val dc = difficulty.dc
        val critSuccess = roll == 20
        val critFailure = roll == 1

        val success = critSuccess || (!critFailure && total >= dc)
        val margin = total - dc

        val degree = when {
            critSuccess -> SkillCheckDegree.CRITICAL_SUCCESS
            critFailure -> SkillCheckDegree.CRITICAL_FAILURE
            margin >= 10 -> SkillCheckDegree.GREAT_SUCCESS
            margin >= 0 -> SkillCheckDegree.SUCCESS
            margin >= -5 -> SkillCheckDegree.FAILURE
            else -> SkillCheckDegree.BAD_FAILURE
        }

        return SkillCheckResult(
            checkType = checkType,
            roll = roll,
            modifier = modifier,
            proficiency = proficiency,
            total = total,
            dc = dc,
            success = success,
            degree = degree,
            margin = margin
        )
    }

    private fun getXPRequiredForLevel(level: Int): Long {
        return level * 100L
    }
}

/**
 * Non-combat skill check types, each tied to a primary stat.
 */
internal enum class SkillCheckType(val displayName: String, val description: String) {
    // STR
    ATHLETICS("Athletics", "Climbing, jumping, swimming, feats of physical power"),
    INTIMIDATION_PHYSICAL("Physical Intimidation", "Using physical presence to threaten"),

    // DEX
    STEALTH("Stealth", "Moving silently, hiding, avoiding detection"),
    SLEIGHT_OF_HAND("Sleight of Hand", "Pickpocketing, lock-picking, fine motor tasks"),
    ACROBATICS("Acrobatics", "Balance, tumbling, agile maneuvering"),

    // INT
    INVESTIGATION("Investigation", "Searching for clues, deduction, analysis"),
    ARCANA("Arcana", "Knowledge of magic, System mechanics, runes"),
    HISTORY("History", "Knowledge of past events, civilizations, lore"),
    CRAFTING_CHECK("Crafting", "Creating items, improvising tools, repairs"),

    // WIS
    PERCEPTION("Perception", "Noticing details, spotting threats, awareness"),
    INSIGHT("Insight", "Reading people's intentions, detecting lies"),
    SURVIVAL("Survival", "Tracking, navigation, foraging, weather sense"),
    MEDICINE("Medicine", "Treating wounds, diagnosing ailments"),

    // CHA
    PERSUASION("Persuasion", "Convincing through charm and reason"),
    DECEPTION("Deception", "Lying convincingly, misdirection"),
    PERFORMANCE("Performance", "Entertaining, distracting, inspiring"),
    INTIMIDATION("Intimidation", "Coercing through force of personality");

    fun getStatValue(stats: com.rpgenerator.core.domain.Stats): Int = when (this) {
        ATHLETICS, INTIMIDATION_PHYSICAL -> stats.strength
        STEALTH, SLEIGHT_OF_HAND, ACROBATICS -> stats.dexterity
        INVESTIGATION, ARCANA, HISTORY, CRAFTING_CHECK -> stats.intelligence
        PERCEPTION, INSIGHT, SURVIVAL, MEDICINE -> stats.wisdom
        PERSUASION, DECEPTION, PERFORMANCE, INTIMIDATION -> stats.charisma
    }
}

internal enum class SkillCheckDifficulty(val dc: Int, val displayName: String) {
    TRIVIAL(5, "Trivial"),
    EASY(8, "Easy"),
    MODERATE(12, "Moderate"),
    HARD(15, "Hard"),
    VERY_HARD(18, "Very Hard"),
    NEARLY_IMPOSSIBLE(22, "Nearly Impossible");
}

internal enum class SkillCheckDegree {
    CRITICAL_SUCCESS,   // Natural 20
    GREAT_SUCCESS,      // Beat DC by 10+
    SUCCESS,            // Beat DC
    FAILURE,            // Missed DC by 1-5
    BAD_FAILURE,        // Missed DC by 6+
    CRITICAL_FAILURE    // Natural 1
}

internal data class SkillCheckResult(
    val checkType: SkillCheckType,
    val roll: Int,
    val modifier: Int,
    val proficiency: Int,
    val total: Int,
    val dc: Int,
    val success: Boolean,
    val degree: SkillCheckDegree,
    val margin: Int
)

internal data class CombatOutcome(
    val damage: Int,
    val damageTaken: Int = 0,
    val xpGain: Long,
    val newXP: Long,
    val levelUp: Boolean,
    val newLevel: Int,
    val loot: List<GeneratedItem> = emptyList(),
    val gold: Int = 0
)
