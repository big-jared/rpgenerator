package com.rpgenerator.core.character

import com.rpgenerator.core.api.*
import com.rpgenerator.core.domain.Stats as DomainStats
import kotlin.random.Random

/**
 * Service for creating characters based on player choices.
 */
internal object CharacterCreationService {

    /**
     * Create a character based on the provided options.
     */
    fun createCharacter(
        options: CharacterCreationOptions,
        systemType: SystemType,
        difficulty: Difficulty
    ): Pair<DomainStats, String> {
        val stats = generateStats(options, difficulty)
        val backstory = generateBackstory(options, systemType)
        return Pair(stats, backstory)
    }

    /**
     * Generate character stats based on allocation method.
     */
    private fun generateStats(options: CharacterCreationOptions, difficulty: Difficulty): DomainStats {
        val baseStats = when (options.statAllocation) {
            StatAllocation.BALANCED -> CustomStats(10, 10, 10, 10, 10, 10)

            StatAllocation.WARRIOR -> CustomStats(
                strength = 15,
                dexterity = 12,
                constitution = 14,
                intelligence = 8,
                wisdom = 9,
                charisma = 10
            )

            StatAllocation.MAGE -> CustomStats(
                strength = 8,
                dexterity = 10,
                constitution = 10,
                intelligence = 16,
                wisdom = 14,
                charisma = 10
            )

            StatAllocation.ROGUE -> CustomStats(
                strength = 10,
                dexterity = 16,
                constitution = 10,
                intelligence = 12,
                wisdom = 10,
                charisma = 14
            )

            StatAllocation.TANK -> CustomStats(
                strength = 14,
                dexterity = 10,
                constitution = 16,
                intelligence = 8,
                wisdom = 10,
                charisma = 10
            )

            StatAllocation.POINT_BUY -> generatePointBuyStats()

            StatAllocation.CUSTOM -> {
                if (options.customStats != null && options.customStats.isValid()) {
                    options.customStats
                } else {
                    CustomStats(10, 10, 10, 10, 10, 10) // Fallback to balanced
                }
            }

            StatAllocation.RANDOM -> generateRandomStats()
        }

        // Roll backstory-influenced stats (random dice, backstory tilts the rolls)
        val backstoryRolls = inferStatsFromBackstory(options.backstory ?: "")

        // Merge: use base stats as floor, backstory rolls as bonus on top
        // For preset allocations, backstory rolls add variance (+/- a few points)
        // For RANDOM allocation, backstory rolls replace the base entirely
        val finalStats = if (options.statAllocation == StatAllocation.RANDOM) {
            backstoryRolls // Backstory-influenced rolls ARE the stats
        } else {
            // Blend: base allocation + scaled backstory influence (roll - 10 gives -7 to +8 range)
            CustomStats(
                strength = baseStats.strength + (backstoryRolls.strength - 10) / 2,
                dexterity = baseStats.dexterity + (backstoryRolls.dexterity - 10) / 2,
                constitution = baseStats.constitution + (backstoryRolls.constitution - 10) / 2,
                intelligence = baseStats.intelligence + (backstoryRolls.intelligence - 10) / 2,
                wisdom = baseStats.wisdom + (backstoryRolls.wisdom - 10) / 2,
                charisma = baseStats.charisma + (backstoryRolls.charisma - 10) / 2
            )
        }

        // Apply difficulty modifier
        val difficultyBonus = when (difficulty) {
            Difficulty.EASY -> 2
            Difficulty.NORMAL -> 0
            Difficulty.HARD -> -1
            Difficulty.NIGHTMARE -> -2
        }

        return DomainStats(
            strength = (finalStats.strength + difficultyBonus).coerceAtLeast(3),
            dexterity = (finalStats.dexterity + difficultyBonus).coerceAtLeast(3),
            constitution = (finalStats.constitution + difficultyBonus).coerceAtLeast(3),
            intelligence = (finalStats.intelligence + difficultyBonus).coerceAtLeast(3),
            wisdom = (finalStats.wisdom + difficultyBonus).coerceAtLeast(3),
            charisma = (finalStats.charisma + difficultyBonus).coerceAtLeast(3),
            defense = calculateBaseDefense(finalStats.dexterity, finalStats.constitution)
        )
    }

    /**
     * Generate stats using point-buy system (27 points, 8-15 range).
     * Distributed to create a viable character.
     */
    private fun generatePointBuyStats(): CustomStats {
        // Standard point-buy: start at 8, each point costs progressively more
        // We'll distribute 27 points for a balanced but interesting character
        return CustomStats(
            strength = 12,    // 4 points
            dexterity = 14,   // 7 points
            constitution = 13, // 5 points
            intelligence = 10, // 2 points
            wisdom = 12,      // 4 points
            charisma = 11     // 3 points
            // Total: 25 points (close to 27)
        )
    }

    /**
     * Generate random stats (3d6 per stat, classic D&D).
     */
    private fun generateRandomStats(): CustomStats {
        fun rollStat(): Int = (1..3).sumOf { Random.nextInt(1, 7) }

        return CustomStats(
            strength = rollStat(),
            dexterity = rollStat(),
            constitution = rollStat(),
            intelligence = rollStat(),
            wisdom = rollStat(),
            charisma = rollStat()
        )
    }

    /**
     * Roll a stat using backstory-influenced dice.
     * - Advantage stats (strong backstory match): 4d6 drop lowest (avg ~12.2)
     * - Normal stats (no match): 3d6 (avg ~10.5)
     * - Disadvantage stats (opposing backstory): 3d6 drop highest, keep 2, +3 floor (avg ~8)
     * Results are random every time — backstory just tilts the dice.
     */
    private enum class RollTier { ADVANTAGE, NORMAL, DISADVANTAGE }

    private fun rollStat(tier: RollTier): Int {
        val dice = (1..4).map { Random.nextInt(1, 7) }.sorted()
        return when (tier) {
            RollTier.ADVANTAGE -> dice.drop(1).sum()           // 4d6 drop lowest
            RollTier.NORMAL -> dice.drop(1).take(3).sum()      // middle 3 of 4 (effectively 3d6-ish)
            RollTier.DISADVANTAGE -> (dice.take(3).sum())       // 3 lowest of 4d6
                .coerceAtLeast(5)                               // floor at 5 so nobody's useless
        }
    }

    private data class StatTiers(
        var strength: RollTier = RollTier.NORMAL,
        var dexterity: RollTier = RollTier.NORMAL,
        var constitution: RollTier = RollTier.NORMAL,
        var intelligence: RollTier = RollTier.NORMAL,
        var wisdom: RollTier = RollTier.NORMAL,
        var charisma: RollTier = RollTier.NORMAL
    )

    private fun inferStatsFromBackstory(backstory: String): CustomStats {
        val lower = backstory.lowercase()
        val tiers = StatTiers()

        fun promote(current: RollTier): RollTier = if (current == RollTier.DISADVANTAGE) RollTier.NORMAL else RollTier.ADVANTAGE
        fun demote(current: RollTier): RollTier = if (current == RollTier.ADVANTAGE) RollTier.NORMAL else RollTier.DISADVANTAGE

        // Physical backgrounds — STR/CON advantage, INT disadvantage
        if (lower.containsAny("soldier", "military", "warrior", "fighter", "boxer", "wrestler", "bouncer", "bodyguard", "mercenary")) {
            tiers.strength = promote(tiers.strength); tiers.constitution = promote(tiers.constitution); tiers.intelligence = demote(tiers.intelligence)
        }
        if (lower.containsAny("athlete", "runner", "gymnast", "dancer", "acrobat", "parkour", "martial art")) {
            tiers.dexterity = promote(tiers.dexterity); tiers.strength = promote(tiers.strength)
        }
        if (lower.containsAny("laborer", "construction", "miner", "blacksmith", "farmer", "lumberjack", "plumber")) {
            tiers.strength = promote(tiers.strength); tiers.constitution = promote(tiers.constitution); tiers.charisma = demote(tiers.charisma)
        }
        if (lower.containsAny("survivalist", "hunter", "ranger", "outdoors", "camping", "wilderness", "trapper")) {
            tiers.constitution = promote(tiers.constitution); tiers.wisdom = promote(tiers.wisdom)
        }

        // Mental backgrounds — INT/WIS advantage, STR disadvantage
        if (lower.containsAny("scholar", "professor", "scientist", "researcher", "engineer", "programmer", "hacker", "nerd")) {
            tiers.intelligence = promote(tiers.intelligence); tiers.wisdom = promote(tiers.wisdom); tiers.strength = demote(tiers.strength)
        }
        if (lower.containsAny("doctor", "medic", "nurse", "surgeon", "healer", "therapist", "psychologist")) {
            tiers.wisdom = promote(tiers.wisdom); tiers.intelligence = promote(tiers.intelligence); tiers.strength = demote(tiers.strength)
        }
        if (lower.containsAny("monk", "priest", "cleric", "spiritual", "meditation", "philosopher", "sage")) {
            tiers.wisdom = promote(tiers.wisdom); tiers.charisma = promote(tiers.charisma); tiers.dexterity = demote(tiers.dexterity)
        }
        if (lower.containsAny("strategist", "chess", "tactician", "analyst", "detective", "investigator")) {
            tiers.intelligence = promote(tiers.intelligence); tiers.wisdom = promote(tiers.wisdom)
        }

        // Social backgrounds — CHA advantage, CON disadvantage
        if (lower.containsAny("politician", "diplomat", "lawyer", "salesman", "con artist", "actor", "performer", "singer", "celebrity", "influencer")) {
            tiers.charisma = promote(tiers.charisma); tiers.intelligence = promote(tiers.intelligence); tiers.constitution = demote(tiers.constitution)
        }
        if (lower.containsAny("leader", "commander", "captain", "boss", "manager", "ceo", "chief")) {
            tiers.charisma = promote(tiers.charisma); tiers.wisdom = promote(tiers.wisdom)
        }
        if (lower.containsAny("thief", "rogue", "criminal", "pickpocket", "burglar", "assassin", "spy", "smuggler")) {
            tiers.dexterity = promote(tiers.dexterity); tiers.charisma = promote(tiers.charisma); tiers.wisdom = demote(tiers.wisdom)
        }

        // Unique backgrounds
        if (lower.containsAny("fat", "large", "big", "heavy", "obese", "overweight", "sumo", "competitive eat")) {
            tiers.constitution = promote(tiers.constitution); tiers.strength = promote(tiers.strength); tiers.dexterity = demote(tiers.dexterity)
        }
        if (lower.containsAny("small", "tiny", "short", "kid", "child", "young")) {
            tiers.dexterity = promote(tiers.dexterity); tiers.charisma = promote(tiers.charisma); tiers.strength = demote(tiers.strength)
        }
        if (lower.containsAny("old", "elderly", "ancient", "veteran", "retired", "experienced")) {
            tiers.wisdom = promote(tiers.wisdom); tiers.intelligence = promote(tiers.intelligence); tiers.dexterity = demote(tiers.dexterity)
        }
        if (lower.containsAny("noble", "royal", "prince", "princess", "aristocrat", "wealthy", "rich")) {
            tiers.charisma = promote(tiers.charisma); tiers.intelligence = promote(tiers.intelligence)
        }
        if (lower.containsAny("homeless", "street", "orphan", "survivor", "refugee", "poor")) {
            tiers.constitution = promote(tiers.constitution); tiers.wisdom = promote(tiers.wisdom); tiers.charisma = demote(tiers.charisma)
        }
        if (lower.containsAny("crazy", "insane", "psycho", "mental", "psych ward", "asylum", "unstable")) {
            tiers.wisdom = promote(tiers.wisdom); tiers.charisma = demote(tiers.charisma)
        }

        return CustomStats(
            strength = rollStat(tiers.strength),
            dexterity = rollStat(tiers.dexterity),
            constitution = rollStat(tiers.constitution),
            intelligence = rollStat(tiers.intelligence),
            wisdom = rollStat(tiers.wisdom),
            charisma = rollStat(tiers.charisma)
        )
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }

    /**
     * Calculate base defense from stats.
     */
    private fun calculateBaseDefense(dexterity: Int, constitution: Int): Int {
        return 10 + (dexterity / 2) + (constitution / 4)
    }

    /**
     * Generate character backstory.
     */
    private fun generateBackstory(options: CharacterCreationOptions, systemType: SystemType): String {
        // If user provided backstory, use it
        if (!options.backstory.isNullOrBlank()) {
            return options.backstory
        }

        // Generate system-appropriate backstory
        val name = options.name
        val classDesc = options.startingClass?.let { " as a $it" } ?: ""

        return when (systemType) {
            SystemType.SYSTEM_INTEGRATION ->
                "$name was an ordinary person until the System arrived. Now integrated$classDesc, " +
                "they must adapt to this new reality or perish. The old world is gone. Survival is paramount."

            SystemType.CULTIVATION_PATH ->
                "$name begins their journey on the cultivation path$classDesc. " +
                "With determination and enlightenment, they will ascend through the realms " +
                "and comprehend the mysteries of heaven and earth."

            SystemType.DEATH_LOOP ->
                "$name awakens in the Respawn Chamber$classDesc, knowing death is not the end " +
                "but a teacher. Each failure makes them stronger. Each death reveals new secrets."

            SystemType.DUNGEON_DELVE ->
                "$name stands before the dungeon entrance$classDesc, driven by ambition, desperation, " +
                "or perhaps fate itself. The depths promise both treasure and terror."

            SystemType.ARCANE_ACADEMY ->
                "$name has been accepted into the prestigious Arcane Academy$classDesc. " +
                "Years of study and magical practice await, but greatness demands sacrifice."

            SystemType.TABLETOP_CLASSIC ->
                "$name is an adventurer$classDesc seeking fortune and glory. " +
                "With sword and spell, they will face dragons, explore dungeons, and forge legends."

            SystemType.EPIC_JOURNEY ->
                "$name begins an epic quest that will test not just their strength$classDesc, " +
                "but their courage, wisdom, and fellowship. Great deeds await."

            SystemType.HERO_AWAKENING ->
                "$name discovers they possess extraordinary abilities$classDesc. " +
                "As danger threatens the city, they must decide whether to embrace their destiny " +
                "or return to a normal life."
        }
    }
}
