package com.rpgenerator.core.rules

import com.rpgenerator.core.domain.*
import com.rpgenerator.core.skill.DamageType
import com.rpgenerator.core.skill.Skill
import com.rpgenerator.core.skill.SkillEffect
import com.rpgenerator.core.skill.SkillRarity
import kotlin.random.Random

/**
 * Multi-round combat engine. Each method resolves one round of combat
 * and returns the updated state + a structured result the GM can narrate from.
 */
internal class CombatEngine {

    /**
     * Create an enemy and enter combat.
     */
    fun spawnEnemy(name: String, danger: Int, state: GameState): Pair<GameState, Enemy> {
        val enemy = generateEnemy(name, danger, state.playerLevel)
        val combat = CombatState(
            enemy = enemy,
            playerInitiative = rollInitiative(state.characterSheet.effectiveStats().dexterity, enemy.speed)
        )
        return state.copy(combatState = combat) to enemy
    }

    /**
     * Resolve one round of basic attack: player swings, enemy responds.
     */
    fun resolveAttack(state: GameState): RoundResult {
        val combat = state.combatState ?: return RoundResult.notInCombat()
        if (combat.isOver) return RoundResult.combatAlreadyOver()

        val stats = state.characterSheet.effectiveStats()
        val enemy = combat.enemy
        val events = mutableListOf<CombatEvent>()

        var updatedCombat = combat
        var updatedState = state

        // ── Player attacks ──
        val hitRoll = rollHit(stats.dexterity, enemy.speed)
        if (hitRoll.hit) {
            val damage = calculatePlayerDamage(stats, state.characterSheet.equipment, hitRoll.crit)
            val mitigated = (damage - enemy.defense / 2).coerceAtLeast(1)
            updatedCombat = updatedCombat.damageEnemy(mitigated)
            events.add(CombatEvent.PlayerHit(mitigated, hitRoll.crit, updatedCombat.enemy.currentHP, updatedCombat.enemy.maxHP))
        } else {
            events.add(CombatEvent.PlayerMiss)
        }

        // ── Check if enemy died ──
        if (updatedCombat.isOver) {
            val result = resolveVictory(updatedCombat, updatedState, events)
            return result
        }

        // ── Enemy turn (if not stunned) ──
        if (!updatedCombat.enemy.isStunned) {
            val enemyResult = resolveEnemyTurn(updatedCombat.enemy, stats, updatedState)
            updatedState = enemyResult.first
            events.addAll(enemyResult.second)
        } else {
            events.add(CombatEvent.EnemyStunned)
        }

        // ── Tick status effects ──
        val dotDamage = tickDotEffects(updatedCombat.enemy)
        if (dotDamage > 0) {
            updatedCombat = updatedCombat.damageEnemy(dotDamage)
            events.add(CombatEvent.DotTick(dotDamage, updatedCombat.enemy.currentHP))
        }
        updatedCombat = updatedCombat.tickEnemyEffects()

        // Tick enemy ability cooldowns
        val tickedAbilities = updatedCombat.enemy.abilities.map { it.tick() }
        updatedCombat = updatedCombat.copy(enemy = updatedCombat.enemy.copy(abilities = tickedAbilities))

        // ── Check for enemy death from DoTs ──
        if (updatedCombat.isOver) {
            val result = resolveVictory(updatedCombat, updatedState, events)
            return result
        }

        // Advance round
        updatedCombat = updatedCombat.nextRound()
        updatedState = updatedState.copy(combatState = updatedCombat)

        // Tick player skill cooldowns + passive resource regen
        updatedState = updatedState.updateCharacterSheet(updatedState.characterSheet.tickSkillCooldowns())
        updatedState = regenResources(updatedState)

        return RoundResult(
            success = true,
            events = events,
            newState = updatedState,
            enemyHP = updatedCombat.enemy.currentHP,
            enemyMaxHP = updatedCombat.enemy.maxHP,
            enemyCondition = updatedCombat.enemy.condition,
            playerHP = updatedState.characterSheet.resources.currentHP,
            playerMaxHP = updatedState.characterSheet.resources.maxHP,
            roundNumber = updatedCombat.roundNumber,
            combatOver = false,
            playerDied = updatedState.isDead
        )
    }

    /**
     * Resolve using a skill in combat.
     */
    fun resolveSkillUse(skill: Skill, state: GameState): RoundResult {
        val combat = state.combatState ?: return RoundResult.notInCombat()
        if (combat.isOver) return RoundResult.combatAlreadyOver()

        val stats = state.characterSheet.effectiveStats()
        val events = mutableListOf<CombatEvent>()
        var updatedCombat = combat
        var updatedState = state

        // Spend resources and start cooldown
        val newSheet = updatedState.characterSheet.useSkill(skill.id)
            ?: return RoundResult(success = false, error = "Cannot use ${skill.name}: insufficient resources or on cooldown")
        updatedState = updatedState.updateCharacterSheet(newSheet)

        // Apply skill effects
        val skillRarity = skill.rarity
        for (effect in skill.effects) {
            when (effect) {
                is SkillEffect.Damage -> {
                    val calcDamage = effect.calculateValue(skill.level, stats, skillRarity).toInt()
                    val mitigated = applyDamageTypeReduction(calcDamage, effect.damageType, updatedCombat.enemy)
                    updatedCombat = updatedCombat.damageEnemy(mitigated)
                    events.add(CombatEvent.SkillDamage(skill.name, mitigated, updatedCombat.enemy.currentHP, updatedCombat.enemy.maxHP))

                    // Secondary effects by damage type
                    when (effect.damageType) {
                        DamageType.ICE -> if (Random.nextInt(100) < 30) {
                            updatedCombat = updatedCombat.applyEnemyStatusEffect(
                                EnemyStatusEffect(EnemyEffectType.SLOWED, 2, updatedCombat.enemy.speed / 3)
                            )
                            events.add(CombatEvent.SkillEffect(skill.name, "Enemy is slowed by frost!"))
                        }
                        DamageType.LIGHTNING -> if (Random.nextInt(100) < 20) {
                            updatedCombat = updatedCombat.applyEnemyStatusEffect(
                                EnemyStatusEffect(EnemyEffectType.STUNNED, 1)
                            )
                            events.add(CombatEvent.SkillEffect(skill.name, "Enemy is stunned by lightning!"))
                        }
                        DamageType.DARK -> {
                            val lifesteal = (mitigated * 0.2).toInt().coerceAtLeast(1)
                            updatedState = updatedState.heal(lifesteal)
                            events.add(CombatEvent.SkillHeal(skill.name, lifesteal, updatedState.characterSheet.resources.currentHP))
                        }
                        else -> { /* no secondary effect */ }
                    }
                }
                is SkillEffect.Heal -> {
                    val healAmount = effect.calculateValue(skill.level, stats, skillRarity).toInt()
                    updatedState = updatedState.heal(healAmount)
                    events.add(CombatEvent.SkillHeal(skill.name, healAmount, updatedState.characterSheet.resources.currentHP))
                }
                is SkillEffect.Buff -> {
                    val bonusAmount = effect.calculateValue(skill.level, stats, skillRarity).toInt()
                    val statName = effect.statAffected.name.lowercase()
                    val statusEffect = StatusEffect(
                        id = "buff_${skill.id}",
                        name = "${skill.name} buff",
                        description = "+$bonusAmount ${statName}",
                        duration = effect.duration,
                        statModifiers = Stats(
                            strength = if (statName == "strength") bonusAmount else 0,
                            dexterity = if (statName == "dexterity") bonusAmount else 0,
                            constitution = if (statName == "constitution") bonusAmount else 0,
                            intelligence = if (statName == "intelligence") bonusAmount else 0,
                            defense = if (statName == "defense") bonusAmount else 0
                        )
                    )
                    updatedState = updatedState.applyStatusEffect(statusEffect)
                    events.add(CombatEvent.SkillBuff(skill.name, statName, bonusAmount, effect.duration))
                }
                is SkillEffect.Debuff -> {
                    val penaltyAmount = effect.calculateValue(skill.level, stats, skillRarity).toInt()
                    val statName = effect.statAffected.name.lowercase()
                    val enemyEffect = when (statName) {
                        "dexterity" -> EnemyStatusEffect(EnemyEffectType.SLOWED, effect.duration, penaltyAmount)
                        else -> EnemyStatusEffect(EnemyEffectType.WEAKENED, effect.duration, penaltyAmount)
                    }
                    updatedCombat = updatedCombat.applyEnemyStatusEffect(enemyEffect)
                    events.add(CombatEvent.SkillDebuff(skill.name, statName, effect.duration))
                }
                is SkillEffect.DamageOverTime -> {
                    val tickDamage = effect.calculateValue(skill.level, stats, skillRarity).toInt()
                    val dotType = if (effect.damageType.name == "FIRE") EnemyEffectType.BURNING else EnemyEffectType.POISONED
                    updatedCombat = updatedCombat.applyEnemyStatusEffect(
                        EnemyStatusEffect(dotType, effect.duration, tickDamage)
                    )
                    events.add(CombatEvent.SkillDot(skill.name, effect.damageType.name.lowercase(), tickDamage, effect.duration))
                }
                else -> {
                    events.add(CombatEvent.SkillEffect(skill.name, effect.describe(skill.level)))
                }
            }
        }

        // Check if enemy died from skill
        if (updatedCombat.isOver) {
            return resolveVictory(updatedCombat, updatedState, events)
        }

        // Enemy counterattack (if not stunned)
        if (!updatedCombat.enemy.isStunned) {
            val enemyResult = resolveEnemyTurn(updatedCombat.enemy, stats, updatedState)
            updatedState = enemyResult.first
            events.addAll(enemyResult.second)
        } else {
            events.add(CombatEvent.EnemyStunned)
        }

        // Tick DoTs
        val dotDamage = tickDotEffects(updatedCombat.enemy)
        if (dotDamage > 0) {
            updatedCombat = updatedCombat.damageEnemy(dotDamage)
            events.add(CombatEvent.DotTick(dotDamage, updatedCombat.enemy.currentHP))
        }
        updatedCombat = updatedCombat.tickEnemyEffects()

        if (updatedCombat.isOver) {
            return resolveVictory(updatedCombat, updatedState, events)
        }

        updatedCombat = updatedCombat.nextRound()
        updatedState = updatedState.copy(combatState = updatedCombat)
        updatedState = updatedState.updateCharacterSheet(updatedState.characterSheet.tickSkillCooldowns())
        updatedState = regenResources(updatedState)

        return RoundResult(
            success = true,
            events = events,
            newState = updatedState,
            enemyHP = updatedCombat.enemy.currentHP,
            enemyMaxHP = updatedCombat.enemy.maxHP,
            enemyCondition = updatedCombat.enemy.condition,
            playerHP = updatedState.characterSheet.resources.currentHP,
            playerMaxHP = updatedState.characterSheet.resources.maxHP,
            roundNumber = updatedCombat.roundNumber,
            combatOver = false,
            playerDied = updatedState.isDead
        )
    }

    /**
     * Attempt to flee combat. Success based on DEX vs enemy speed.
     */
    fun resolveFlee(state: GameState): RoundResult {
        val combat = state.combatState ?: return RoundResult.notInCombat()
        val stats = state.characterSheet.effectiveStats()
        val events = mutableListOf<CombatEvent>()

        // Flee chance: 40% base + 3% per DEX advantage
        val dexAdvantage = stats.dexterity - combat.enemy.speed
        val fleeChance = (40 + dexAdvantage * 3).coerceIn(10, 90)
        val roll = Random.nextInt(100)
        val fled = roll < fleeChance

        if (fled) {
            events.add(CombatEvent.FleeSuccess)
            val newState = state.copy(combatState = null)
            return RoundResult(
                success = true,
                events = events,
                newState = newState,
                enemyHP = combat.enemy.currentHP,
                enemyMaxHP = combat.enemy.maxHP,
                enemyCondition = combat.enemy.condition,
                playerHP = newState.characterSheet.resources.currentHP,
                playerMaxHP = newState.characterSheet.resources.maxHP,
                roundNumber = combat.roundNumber,
                combatOver = true,
                fled = true
            )
        } else {
            events.add(CombatEvent.FleeFailed)
            // Enemy gets a free hit
            var updatedState = state
            val enemyResult = resolveEnemyTurn(combat.enemy, stats, updatedState)
            updatedState = enemyResult.first
            events.addAll(enemyResult.second)

            val newCombat = combat.nextRound()
            updatedState = updatedState.copy(combatState = newCombat)

            return RoundResult(
                success = true,
                events = events,
                newState = updatedState,
                enemyHP = newCombat.enemy.currentHP,
                enemyMaxHP = newCombat.enemy.maxHP,
                enemyCondition = newCombat.enemy.condition,
                playerHP = updatedState.characterSheet.resources.currentHP,
                playerMaxHP = updatedState.characterSheet.resources.maxHP,
                roundNumber = newCombat.roundNumber,
                combatOver = false,
                playerDied = updatedState.isDead
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════

    private fun generateEnemy(name: String, danger: Int, playerLevel: Int): Enemy {
        val d = danger.coerceIn(1, 10)
        val levelScale = 1.0 + (playerLevel - 1) * 0.15

        // HP scales with danger^1.5 and player level
        val baseHP = (20 + d * 15) * levelScale
        val hp = baseHP.toInt()

        // Attack/defense/speed scale with danger
        val attack = (2 + d * 2 * levelScale).toInt()
        val defense = (d * 1.5 * levelScale).toInt()
        val speed = (8 + d * 2)

        // XP value
        val xp = (30L + d * 20L) * playerLevel

        // Generate abilities for higher danger enemies
        val abilities = if (d >= 3) {
            generateEnemyAbilities(name, d)
        } else emptyList()

        val lootTier = when {
            d >= 8 -> "boss"
            d >= 5 -> "elite"
            else -> "normal"
        }

        return Enemy(
            name = name,
            danger = d,
            maxHP = hp,
            currentHP = hp,
            attack = attack,
            defense = defense,
            speed = speed,
            abilities = abilities,
            xpValue = xp,
            lootTier = lootTier
        )
    }

    private fun generateEnemyAbilities(name: String, danger: Int): List<EnemyAbility> {
        val abilities = mutableListOf<EnemyAbility>()

        // Every enemy danger 3+ gets a special attack
        abilities.add(EnemyAbility(
            name = "Heavy Strike",
            damage = danger * 2,
            cooldown = 3,
            description = "A powerful blow"
        ))

        // Danger 6+ get a second ability
        if (danger >= 6) {
            abilities.add(EnemyAbility(
                name = "Frenzy",
                damage = danger,
                cooldown = 4,
                description = "A rapid flurry of attacks"
            ))
        }

        // Danger 8+ (boss) get a third
        if (danger >= 8) {
            abilities.add(EnemyAbility(
                name = "Devastating Slam",
                damage = danger * 3,
                cooldown = 5,
                description = "A massive attack that shakes the ground"
            ))
        }

        return abilities
    }

    private fun rollInitiative(playerDex: Int, enemySpeed: Int): Boolean {
        val playerRoll = Random.nextInt(1, 21) + playerDex / 2
        val enemyRoll = Random.nextInt(1, 21) + enemySpeed / 2
        return playerRoll >= enemyRoll
    }

    private data class HitResult(val hit: Boolean, val crit: Boolean)

    private fun rollHit(playerDex: Int, enemySpeed: Int): HitResult {
        // Base hit chance: 80% + 2% per DEX advantage over enemy speed
        val dexAdvantage = playerDex - enemySpeed
        val hitChance = (80 + dexAdvantage * 2).coerceIn(40, 95)
        val roll = Random.nextInt(100)
        val hit = roll < hitChance

        // Crit: 5% base + 1% per 5 DEX
        val critChance = (5 + playerDex / 5).coerceAtMost(25)
        val crit = hit && Random.nextInt(100) < critChance

        return HitResult(hit, crit)
    }

    private fun calculatePlayerDamage(stats: Stats, equipment: Equipment, crit: Boolean): Int {
        val baseDamage = Random.nextInt(1, 7) // 1d6
        val strBonus = stats.strength / 2
        val weaponDamage = equipment.weapon?.baseDamage ?: 0
        val total = baseDamage + strBonus + weaponDamage
        return if (crit) total * 2 else total
    }

    private fun resolveEnemyTurn(enemy: Enemy, playerStats: Stats, state: GameState): Pair<GameState, List<CombatEvent>> {
        val events = mutableListOf<CombatEvent>()
        var updatedState = state

        // Low-HP scaling: wounded enemies are less effective
        val hpRatio = enemy.currentHP.toDouble() / enemy.maxHP.coerceAtLeast(1)
        val woundedPenalty = when {
            hpRatio <= 0.1 -> 30  // Near death: -30% hit, -30% damage
            hpRatio <= 0.25 -> 20 // Badly wounded: -20% hit, -20% damage
            hpRatio <= 0.5 -> 10  // Wounded: -10% hit, -10% damage
            else -> 0
        }
        val damageMult = (100 - woundedPenalty) / 100.0

        // Check if enemy uses an ability (less likely when wounded)
        val readyAbility = enemy.abilities.firstOrNull { it.isReady }
        val abilityChance = (40 - woundedPenalty).coerceAtLeast(5)

        if (readyAbility != null && Random.nextInt(100) < abilityChance) {
            // Ability attack — always hits but can be partially mitigated, reduced by wounds
            val abilityDamage = enemy.attack + readyAbility.damage
            val weakenedReduction = enemy.statusEffects
                .filter { it.type == EnemyEffectType.WEAKENED }
                .sumOf { it.potency }
            val effectiveAttack = ((abilityDamage - weakenedReduction) * damageMult).toInt().coerceAtLeast(1)
            val mitigated = (effectiveAttack - playerStats.defense / 2 - playerStats.constitution / 4).coerceAtLeast(1)
            updatedState = updatedState.takeDamage(mitigated)
            events.add(CombatEvent.EnemyAbility(readyAbility.name, mitigated, updatedState.characterSheet.resources.currentHP))
        } else {
            // Basic attack with hit/miss — wounded enemies miss more
            val isSlowed = enemy.statusEffects.any { it.type == EnemyEffectType.SLOWED }
            val effectiveSpeed = if (isSlowed) enemy.speed / 2 else enemy.speed
            val hitChance = (65 + (effectiveSpeed - playerStats.dexterity) * 2 - woundedPenalty).coerceIn(15, 85)
            val roll = Random.nextInt(100)

            if (roll < hitChance) {
                val weakenedReduction = enemy.statusEffects
                    .filter { it.type == EnemyEffectType.WEAKENED }
                    .sumOf { it.potency }
                val effectiveAttack = (enemy.attack - weakenedReduction).coerceAtLeast(1)
                val baseDamage = ((Random.nextInt(1, 4) + effectiveAttack) * damageMult).toInt().coerceAtLeast(1)
                val damageTaken = (baseDamage - playerStats.defense / 2 - playerStats.constitution / 4).coerceAtLeast(1)
                updatedState = updatedState.takeDamage(damageTaken)
                events.add(CombatEvent.EnemyHit(damageTaken, updatedState.characterSheet.resources.currentHP))
            } else {
                events.add(CombatEvent.EnemyMiss)
            }
        }

        return updatedState to events
    }

    /**
     * Apply damage type-specific mitigation.
     * PHYSICAL → reduced by defense/3
     * MAGICAL/FIRE/ICE/LIGHTNING/HOLY/DARK → reduced by enemy defense/5 (magic pierces armor)
     * TRUE → ignores all defense
     * ICE → chance to apply SLOWED
     * LIGHTNING → chance to apply STUNNED
     * DARK → 20% life steal to player
     */
    private fun applyDamageTypeReduction(rawDamage: Int, type: DamageType, enemy: Enemy): Int {
        return when (type) {
            DamageType.PHYSICAL -> (rawDamage - enemy.defense / 3).coerceAtLeast(1)
            DamageType.TRUE -> rawDamage // ignores all defense
            DamageType.MAGICAL, DamageType.FIRE, DamageType.ICE,
            DamageType.LIGHTNING, DamageType.HOLY, DamageType.DARK,
            DamageType.POISON -> (rawDamage - enemy.defense / 5).coerceAtLeast(1)
        }
    }

    /**
     * Passive mana and energy regeneration per combat round.
     */
    private fun regenResources(state: GameState): GameState {
        val res = state.characterSheet.resources
        val stats = state.characterSheet.effectiveStats()
        // Regen: 2% max + WIS/10 mana, 5% max + DEX/10 energy per round
        val manaRegen = (res.maxMana * 0.02 + stats.wisdom / 10.0).toInt().coerceAtLeast(1)
        val energyRegen = (res.maxEnergy * 0.05 + stats.dexterity / 10.0).toInt().coerceAtLeast(2)
        val newResources = res.copy(
            currentMana = (res.currentMana + manaRegen).coerceAtMost(res.maxMana),
            currentEnergy = (res.currentEnergy + energyRegen).coerceAtMost(res.maxEnergy)
        )
        return state.updateCharacterSheet(state.characterSheet.copy(resources = newResources))
    }

    private fun tickDotEffects(enemy: Enemy): Int {
        return enemy.statusEffects
            .filter { it.type == EnemyEffectType.BURNING || it.type == EnemyEffectType.POISONED }
            .sumOf { it.potency }
    }

    private fun resolveVictory(combat: CombatState, state: GameState, events: MutableList<CombatEvent>): RoundResult {
        events.add(CombatEvent.EnemyDefeated(combat.enemy.name))

        // Clear combat
        var newState = state.copy(combatState = null)

        // Award XP
        val oldLevel = newState.playerLevel
        val oldStats = newState.characterSheet.baseStats
        newState = newState.gainXP(combat.enemy.xpValue)

        val leveledUp = newState.playerLevel > oldLevel
        if (leveledUp) {
            events.add(CombatEvent.LevelUp(newState.playerLevel, oldStats, newState.characterSheet.baseStats))
        }

        return RoundResult(
            success = true,
            events = events,
            newState = newState,
            enemyHP = 0,
            enemyMaxHP = combat.enemy.maxHP,
            enemyCondition = "defeated",
            playerHP = newState.characterSheet.resources.currentHP,
            playerMaxHP = newState.characterSheet.resources.maxHP,
            roundNumber = combat.roundNumber,
            combatOver = true,
            victory = true,
            xpAwarded = combat.enemy.xpValue,
            levelUp = leveledUp,
            newLevel = newState.playerLevel,
            lootTier = combat.enemy.lootTier,
            playerDied = newState.isDead
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// RESULT TYPES
// ═══════════════════════════════════════════════════════════════

internal data class RoundResult(
    val success: Boolean,
    val error: String? = null,
    val events: List<CombatEvent> = emptyList(),
    val newState: GameState? = null,
    val enemyHP: Int = 0,
    val enemyMaxHP: Int = 0,
    val enemyCondition: String = "",
    val playerHP: Int = 0,
    val playerMaxHP: Int = 0,
    val roundNumber: Int = 0,
    val combatOver: Boolean = false,
    val victory: Boolean = false,
    val fled: Boolean = false,
    val playerDied: Boolean = false,
    val xpAwarded: Long = 0L,
    val levelUp: Boolean = false,
    val newLevel: Int = 0,
    val lootTier: String = "normal"
) {
    companion object {
        fun notInCombat() = RoundResult(success = false, error = "Not in combat")
        fun combatAlreadyOver() = RoundResult(success = false, error = "Combat is already over")
    }
}

internal sealed class CombatEvent {
    data class PlayerHit(val damage: Int, val crit: Boolean, val enemyHP: Int, val enemyMaxHP: Int) : CombatEvent()
    data object PlayerMiss : CombatEvent()
    data class SkillDamage(val skillName: String, val damage: Int, val enemyHP: Int, val enemyMaxHP: Int) : CombatEvent()
    data class SkillHeal(val skillName: String, val amount: Int, val currentHP: Int) : CombatEvent()
    data class SkillBuff(val skillName: String, val stat: String, val amount: Int, val duration: Int) : CombatEvent()
    data class SkillDebuff(val skillName: String, val effect: String, val duration: Int) : CombatEvent()
    data class SkillDot(val skillName: String, val damageType: String, val perTick: Int, val duration: Int) : CombatEvent()
    data class SkillEffect(val skillName: String, val description: String) : CombatEvent()
    data class DotTick(val damage: Int, val enemyHP: Int) : CombatEvent()
    data class EnemyHit(val damage: Int, val playerHP: Int) : CombatEvent()
    data object EnemyMiss : CombatEvent()
    data class EnemyAbility(val abilityName: String, val damage: Int, val playerHP: Int) : CombatEvent()
    data object EnemyStunned : CombatEvent()
    data class EnemyDefeated(val enemyName: String) : CombatEvent()
    data object FleeSuccess : CombatEvent()
    data object FleeFailed : CombatEvent()
    data class LevelUp(val newLevel: Int, val oldStats: Stats, val newStats: Stats) : CombatEvent()
}
