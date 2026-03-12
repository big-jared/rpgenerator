package com.rpgenerator.core.domain

import com.rpgenerator.core.skill.DamageType

/**
 * Template for an enemy type in the bestiary. Used by CombatEngine to spawn
 * enemies with lore-accurate stats, abilities, and descriptions.
 */
internal data class EnemyTemplate(
    val id: String,
    val name: String,
    val zoneId: String,
    val levelRange: IntRange,
    val baseDanger: Int,
    val description: String,
    val visualDescription: String,
    val abilities: List<EnemyAbilityTemplate>,
    val isBoss: Boolean = false,
    val bossPhases: List<BossPhase> = emptyList(),
    val lootTier: String,
    val xpMultiplier: Double = 1.0,
    val immunities: Set<DamageType> = emptySet(),
    val vulnerabilities: Set<DamageType> = emptySet(),
    val resistances: Set<DamageType> = emptySet(), // 50% reduction (not immune)
    val portraitResource: String? = null // Compose Resource drawable name (e.g. "monster_greenreach_bone_crawler")
)

/**
 * Template for an enemy ability. Becomes an [EnemyAbility] when the enemy is spawned.
 */
internal data class EnemyAbilityTemplate(
    val name: String,
    val bonusDamage: Int,
    val cooldown: Int,
    val description: String,
    val unlockPhase: Int = 1,
    val damageType: DamageType = DamageType.PHYSICAL
)

/**
 * A boss phase that activates when the boss drops below [hpThreshold] HP percentage.
 * Phase 1 is always active from the start; later phases add abilities and stat multipliers.
 */
internal data class BossPhase(
    val phase: Int,
    val hpThreshold: Double,
    val description: String,
    val additionalAbilities: List<EnemyAbilityTemplate> = emptyList(),
    val statMultiplier: Double = 1.0
)

/**
 * Interface for looking up enemy templates by name or zone.
 */
internal interface Bestiary {
    fun findEnemy(name: String, zoneId: String? = null): EnemyTemplate?
    fun getEnemiesForZone(zoneId: String): List<EnemyTemplate>
    fun getBossForZone(zoneId: String): EnemyTemplate?
}
