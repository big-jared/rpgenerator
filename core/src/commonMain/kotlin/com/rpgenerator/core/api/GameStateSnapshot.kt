package com.rpgenerator.core.api

import kotlinx.serialization.Serializable

/**
 * Snapshot of current game state for UI display.
 * Returned synchronously from Game.getState()
 */
@Serializable
data class GameStateSnapshot(
    val playerStats: PlayerStats,
    val location: String,
    val currentScene: String,
    val inventory: List<Item>,
    val activeQuests: List<Quest>,
    val npcsAtLocation: List<NPCInfo> = emptyList(),
    val skills: List<SkillInfo> = emptyList(),
    val combat: CombatInfo? = null,
    val recentEvents: List<GameEvent>
)

/**
 * Combat state for UI display — enemy portrait, HP bar, status effects.
 */
@Serializable
data class CombatInfo(
    val enemyName: String,
    val enemyHP: Int,
    val enemyMaxHP: Int,
    val enemyCondition: String,
    val portraitResource: String? = null,
    val roundNumber: Int,
    val enemyDanger: Int,
    val lootTier: String,
    val description: String = "",
    val immunities: List<String> = emptyList(),
    val vulnerabilities: List<String> = emptyList(),
    val resistances: List<String> = emptyList()
)

@Serializable
data class SkillInfo(
    val id: String,
    val name: String,
    val level: Int,
    val isActive: Boolean
)

/**
 * NPC information for UI display.
 */
@Serializable
data class NPCInfo(
    val id: String,
    val name: String,
    val archetype: String,
    val disposition: String,
    val description: String
)

/**
 * Rich NPC details for the detail screen.
 */
@Serializable
data class NPCDetails(
    val id: String,
    val name: String,
    val archetype: String,
    val description: String,
    val lore: String = "",
    val traits: List<String> = emptyList(),
    val speechPattern: String = "",
    val motivations: List<String> = emptyList(),
    val relationshipStatus: String = "Neutral",
    val affinity: Int = 0,
    val hasShop: Boolean = false,
    val shopName: String? = null,
    val shopItems: List<ShopItemInfo> = emptyList(),
    val questIds: List<String> = emptyList(),
    val recentConversations: List<ConversationInfo> = emptyList()
)

@Serializable
data class ShopItemInfo(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val stock: Int,
    val requiredLevel: Int = 1
)

@Serializable
data class ConversationInfo(
    val playerInput: String,
    val npcResponse: String
)

@Serializable
data class PlayerStats(
    val name: String,
    val level: Int,
    val experience: Long,
    val experienceToNextLevel: Long,
    val stats: Map<String, Int>, // e.g., "strength" -> 10, "intelligence" -> 15
    val health: Int,
    val maxHealth: Int,
    val energy: Int,
    val maxEnergy: Int,
    val backstory: String = "",
    val playerClass: String = "",
    val playerProfession: String = ""
)

@Serializable
data class Item(
    val id: String,
    val name: String,
    val description: String,
    val quantity: Int,
    val rarity: ItemRarity,
    val iconUrl: String? = null
)

@Serializable
enum class ItemRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY
}

@Serializable
data class Quest(
    val id: String,
    val name: String,
    val description: String,
    val status: QuestStatus,
    val objectives: List<QuestObjective>
)

@Serializable
data class QuestObjective(
    val description: String,
    val completed: Boolean
)
