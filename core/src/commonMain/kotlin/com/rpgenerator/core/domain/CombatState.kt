package com.rpgenerator.core.domain

import com.rpgenerator.core.skill.DamageType
import kotlinx.serialization.Serializable

/**
 * Tracks an active combat encounter. Stored in GameState when combat is in progress.
 * Null when not in combat.
 */
@Serializable
internal data class CombatState(
    val enemy: Enemy,
    val roundNumber: Int = 1,
    val combatLog: List<String> = emptyList(),
    val playerInitiative: Boolean = true // who goes first this round
) {
    val isOver: Boolean get() = enemy.currentHP <= 0

    fun nextRound(): CombatState = copy(roundNumber = roundNumber + 1)

    fun damageEnemy(damage: Int): CombatState {
        val newHP = (enemy.currentHP - damage).coerceAtLeast(0)
        return copy(enemy = enemy.copy(currentHP = newHP))
    }

    fun logEntry(entry: String): CombatState {
        return copy(combatLog = combatLog + entry)
    }

    fun applyEnemyStatusEffect(effect: EnemyStatusEffect): CombatState {
        return copy(enemy = enemy.copy(statusEffects = enemy.statusEffects + effect))
    }

    fun tickEnemyEffects(): CombatState {
        val updated = enemy.statusEffects.mapNotNull { e ->
            val ticked = e.copy(remainingTurns = e.remainingTurns - 1)
            if (ticked.remainingTurns > 0) ticked else null
        }
        return copy(enemy = enemy.copy(statusEffects = updated))
    }
}

/**
 * An enemy in active combat.
 */
@Serializable
internal data class Enemy(
    val name: String,
    val danger: Int, // 1-10 scale
    val maxHP: Int,
    val currentHP: Int,
    val attack: Int, // base attack power
    val defense: Int, // damage reduction
    val speed: Int, // affects hit/dodge chance
    val abilities: List<EnemyAbility> = emptyList(),
    val statusEffects: List<EnemyStatusEffect> = emptyList(),
    val description: String = "", // for narration context
    val visualDescription: String = "", // for image generation prompts
    val portraitResource: String? = null, // Compose Resource drawable name
    val xpValue: Long = 0L,
    val lootTier: String = "normal", // normal, elite, boss
    val immunities: Set<DamageType> = emptySet(),
    val vulnerabilities: Set<DamageType> = emptySet(),
    val resistances: Set<DamageType> = emptySet() // 50% reduction
) {
    val hpPercent: Int get() = if (maxHP > 0) (currentHP * 100) / maxHP else 0

    val condition: String get() = when {
        hpPercent >= 90 -> "healthy"
        hpPercent >= 60 -> "wounded"
        hpPercent >= 30 -> "badly wounded"
        hpPercent > 0 -> "near death"
        else -> "defeated"
    }

    val isStunned: Boolean get() = statusEffects.any { it.type == EnemyEffectType.STUNNED }
    val isBurning: Boolean get() = statusEffects.any { it.type == EnemyEffectType.BURNING }
    val isPoisoned: Boolean get() = statusEffects.any { it.type == EnemyEffectType.POISONED }
}

@Serializable
internal data class EnemyAbility(
    val name: String,
    val damage: Int, // bonus damage when used
    val cooldown: Int = 0, // turns between uses
    val currentCooldown: Int = 0,
    val description: String = "",
    val damageType: DamageType = DamageType.PHYSICAL
) {
    val isReady: Boolean get() = currentCooldown <= 0
    fun use(): EnemyAbility = copy(currentCooldown = cooldown)
    fun tick(): EnemyAbility = copy(currentCooldown = (currentCooldown - 1).coerceAtLeast(0))
}

@Serializable
internal data class EnemyStatusEffect(
    val type: EnemyEffectType,
    val remainingTurns: Int,
    val potency: Int = 0 // damage per tick for DoTs, stat reduction for debuffs
)

@Serializable
internal enum class EnemyEffectType {
    STUNNED,    // skip turn
    BURNING,    // damage over time
    POISONED,   // damage over time
    WEAKENED,   // reduced attack
    SLOWED      // reduced dodge chance
}
