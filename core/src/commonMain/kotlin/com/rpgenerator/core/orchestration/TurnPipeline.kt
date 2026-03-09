package com.rpgenerator.core.orchestration

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.domain.NPC
import com.rpgenerator.core.domain.Profession
import com.rpgenerator.core.rules.CombatEvent
import com.rpgenerator.core.tools.ToolOutcome
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Intermediate data structures for the 3-phase turn pipeline.
 *
 * Phase 1+2: DECIDE & EXECUTE — GM calls tools, tools execute, prose discarded
 * Phase 3:   NARRATE — Narrator writes prose from TurnSummary, no tools available
 */

internal enum class TurnType {
    COMBAT_ROUND,
    EXPLORATION,
    DIALOGUE,
    MOVEMENT,
    QUERY,
    AMBIENT,
    CHARACTER_SETUP
}

internal data class ToolExecutionResult(
    val toolName: String,
    val success: Boolean,
    val resultSummary: String,
    val rawData: JsonObject
)

internal data class CombatRoundSummary(
    val events: List<CombatEvent>,
    val enemyName: String,
    val enemyHP: Int,
    val enemyMaxHP: Int,
    val enemyCondition: String,
    val playerHP: Int,
    val playerMaxHP: Int,
    val combatOver: Boolean,
    val victory: Boolean,
    val playerDied: Boolean,
    val xpAwarded: Long = 0,
    val levelUp: Boolean = false,
    val newLevel: Int = 0,
    val fled: Boolean = false,
    val lootTier: String = "normal"
)

internal data class TurnSummary(
    val executedTools: List<ToolExecutionResult>,
    val events: List<GameEvent>,
    val turnType: TurnType,
    val combatRound: CombatRoundSummary? = null
)

// ═══════════════════════════════════════════════════════════════
// Helper functions
// ═══════════════════════════════════════════════════════════════

internal fun inferTurnType(executedTools: List<ToolExecutionResult>): TurnType {
    val toolNames = executedTools.map { it.toolName }.toSet()
    return when {
        toolNames.any { it in setOf("combat_attack", "combat_use_skill", "combat_flee") } -> TurnType.COMBAT_ROUND
        toolNames.contains("start_combat") -> TurnType.COMBAT_ROUND
        toolNames.contains("move_to_location") -> TurnType.MOVEMENT
        toolNames.any { it in setOf("talk_to_npc", "spawn_npc") } -> TurnType.DIALOGUE
        toolNames.any { it.startsWith("get_") || it == "query_lore" || it == "ask_world" } -> TurnType.QUERY
        toolNames.any { it in setOf("set_class", "set_profession", "set_player_name", "set_backstory", "complete_tutorial") } -> TurnType.CHARACTER_SETUP
        executedTools.isEmpty() -> TurnType.AMBIENT
        else -> TurnType.EXPLORATION
    }
}

internal fun summarizeToolResult(toolName: String, outcome: ToolOutcome): String {
    if (!outcome.success) return "FAILED: ${outcome.error}"
    val data = outcome.data
    return when (toolName) {
        "combat_attack", "combat_use_skill" -> summarizeCombatToolResult(data)
        "combat_flee" -> if (data.str("fled") == "true") "Fled successfully" else "Flee failed"
        "start_combat" -> "Combat started: ${data.str("enemyName")} [HP: ${data.str("enemyHP")}/${data.str("enemyMaxHP")}, Danger: ${data.str("danger")}]"
        "move_to_location" -> "Moved to ${data.str("locationName")}"
        "spawn_npc" -> "NPC appeared: ${data.str("name")} (${data.str("role")})"
        "add_item" -> "Item gained: ${data.str("itemName")} x${data.str("quantity") ?: "1"}"
        "add_gold" -> "Gold gained: ${data.str("amount")}"
        "add_xp" -> "XP gained: ${data.str("amount")}"
        "set_class" -> "Class set: ${data.str("className")}"
        "set_profession" -> "Profession set: ${data.str("profession")}"
        "set_player_name" -> "Name set: ${data.str("name")}"
        "grant_skill" -> "Skill learned: ${data.str("skillName")}"
        "use_item" -> "Used item: ${data.str("itemName")} — ${data.str("effect") ?: "applied"}"
        "query_lore", "ask_world" -> "Lore: ${data.str("answer")?.take(200) ?: data.toString().take(200)}"
        "complete_tutorial" -> "Tutorial completed"
        "talk_to_npc" -> "Talking to ${data.str("npcName")}"
        "accept_quest" -> "Quest accepted: ${data.str("questName")}"
        "complete_quest" -> "Quest completed: ${data.str("questName")}"
        "generate_scene_art" -> "Scene art generated"
        "shift_music_mood" -> "Music mood: ${data.str("mood")}"
        else -> data.toString().take(200)
    }
}

private fun summarizeCombatToolResult(data: JsonObject): String = buildString {
    val narrative = data.str("narrative") ?: ""
    val enemyHP = data.str("enemyHP") ?: "?"
    val enemyMaxHP = data.str("enemyMaxHP") ?: "?"
    val enemyCondition = data.str("enemyCondition") ?: ""
    val combatOver = data.str("combatOver") == "true"
    val victory = data.str("victory") == "true"
    val playerHP = data.str("playerHP") ?: "?"
    val playerMaxHP = data.str("playerMaxHP") ?: "?"
    val xpAwarded = data.str("xpAwarded") ?: "0"
    val levelUp = data.str("levelUp") == "true"

    append("Combat round: $narrative | ")
    append("Enemy [$enemyHP/$enemyMaxHP, $enemyCondition] | Player [$playerHP/$playerMaxHP]")
    if (combatOver && victory) {
        append(" | VICTORY! +${xpAwarded} XP")
        if (levelUp) append(" | LEVEL UP!")
    }
    if (combatOver && !victory) {
        append(" | Combat ended")
    }
}

internal fun formatCombatEvent(event: CombatEvent): String = when (event) {
    is CombatEvent.PlayerHit -> "Player hits for ${event.damage} damage${if (event.crit) " (CRIT!)" else ""} [Enemy: ${event.enemyHP}/${event.enemyMaxHP}]"
    is CombatEvent.PlayerMiss -> "Player misses!"
    is CombatEvent.SkillDamage -> "${event.skillName} deals ${event.damage} damage [Enemy: ${event.enemyHP}/${event.enemyMaxHP}]"
    is CombatEvent.SkillHeal -> "${event.skillName} heals ${event.amount} HP [Player: ${event.currentHP}]"
    is CombatEvent.SkillBuff -> "${event.skillName}: +${event.amount} ${event.stat} for ${event.duration} rounds"
    is CombatEvent.SkillDebuff -> "${event.skillName}: ${event.effect} for ${event.duration} rounds"
    is CombatEvent.SkillDot -> "${event.skillName}: ${event.perTick} ${event.damageType} damage/round for ${event.duration} rounds"
    is CombatEvent.SkillEffect -> "${event.skillName}: ${event.description}"
    is CombatEvent.DotTick -> "DoT tick: ${event.damage} damage [Enemy: ${event.enemyHP}]"
    is CombatEvent.EnemyHit -> "Enemy hits for ${event.damage} damage [Player: ${event.playerHP}]"
    is CombatEvent.EnemyMiss -> "Enemy misses!"
    is CombatEvent.EnemyAbility -> "Enemy uses ${event.abilityName} for ${event.damage} damage [Player: ${event.playerHP}]"
    is CombatEvent.EnemyStunned -> "Enemy is stunned!"
    is CombatEvent.EnemyDefeated -> "${event.enemyName} is defeated!"
    is CombatEvent.FleeSuccess -> "Fled successfully!"
    is CombatEvent.FleeFailed -> "Failed to flee!"
    is CombatEvent.LevelUp -> "LEVEL UP! Now level ${event.newLevel}"
}

internal fun buildNarrationMessage(
    playerInput: String,
    turnSummary: TurnSummary,
    gameState: GameState,
    recentEvents: List<String>
): String = buildString {
    appendLine("Player said: \"$playerInput\"")
    appendLine()

    // Prevent narrator from re-narrating the opening sequence
    if (gameState.characterSheet.playerClass == com.rpgenerator.core.domain.PlayerClass.NONE) {
        appendLine("== IMPORTANT: The opening narration has ALREADY been delivered. Do NOT re-narrate the sky splitting, the void, or class selection. The player is IN the world now. Narrate their CURRENT action and its results only. ==")
        appendLine()
    }

    if (turnSummary.executedTools.isNotEmpty()) {
        appendLine("== MECHANICAL RESULTS (narrate from these — do NOT invent extra rewards, damage, or NPCs) ==")
        for (result in turnSummary.executedTools) {
            appendLine("- [${result.toolName}] ${if (result.success) "OK" else "FAILED"}: ${result.resultSummary}")
        }
    }

    if (turnSummary.combatRound != null) {
        val cr = turnSummary.combatRound
        appendLine()
        appendLine("== COMBAT ROUND (narrate each event below) ==")
        for (event in cr.events) {
            appendLine("  ${formatCombatEvent(event)}")
        }
        appendLine("  Enemy: ${cr.enemyName} [${cr.enemyHP}/${cr.enemyMaxHP} HP, ${cr.enemyCondition}]")
        appendLine("  Player: [${cr.playerHP}/${cr.playerMaxHP} HP]")
        if (cr.combatOver) {
            if (cr.victory) {
                appendLine("  ★ COMBAT VICTORY ★")
                if (cr.xpAwarded > 0) appendLine("  XP Awarded: ${cr.xpAwarded}")
                if (cr.levelUp) appendLine("  LEVEL UP to ${cr.newLevel}!")
            } else if (cr.fled) {
                appendLine("  Player fled combat")
            } else if (cr.playerDied) {
                appendLine("  Player died!")
            }
        } else {
            appendLine("  ⚠ COMBAT CONTINUES — the enemy is NOT dead. Do NOT narrate a kill. Describe wounds and momentum, but the enemy is still fighting.")
        }
    }

    if (turnSummary.executedTools.isEmpty()) {
        appendLine("(No tools executed — this is a conversational or atmospheric moment. Narrate the player's surroundings or respond to their words.)")
    }

    appendLine()
    appendLine("== WORLD STATE ==")
    appendLine("Location: ${gameState.currentLocation.name} — ${gameState.currentLocation.description}")
    val charProf = gameState.characterSheet.profession
    if (charProf != Profession.NONE) {
        appendLine("Profession: ${charProf.displayName} — the player notices ${charProf.category.displayName.lowercase()}-related details in the environment")
    }

    val npcsHere = gameState.getNPCsAtCurrentLocation()
    if (npcsHere.isNotEmpty()) {
        appendLine("NPCs present (may react to what just happened):")
        for (npc in npcsHere) {
            appendLine("  - ${npc.name} (${npc.archetype}${if (npc.personality.traits.isNotEmpty()) ", ${npc.personality.traits.joinToString(", ")}" else ""})")
            // Include identity data so narrator stays consistent with established backstory
            if (npc.lore.isNotBlank()) {
                // First 300 chars of lore for concise grounding
                appendLine("    Identity: ${npc.lore.take(300)}")
            }
            if (npc.personality.speechPattern.isNotBlank() && npc.personality.speechPattern != "Normal speech") {
                appendLine("    Speech: ${npc.personality.speechPattern}")
            }
            if (npc.conversationHistory.isNotEmpty()) {
                appendLine("    Recent conversation (${npc.conversationHistory.size} exchanges):")
                for (entry in npc.conversationHistory.takeLast(2)) {
                    appendLine("      Player: ${entry.playerInput.take(100)}")
                    appendLine("      ${npc.name}: ${entry.npcResponse.take(100)}")
                }
            }
        }
    }

    // Inventory — narrator must not reference items the player doesn't have
    val items = gameState.characterSheet.inventory.items.values
    if (items.isNotEmpty()) {
        appendLine("Player inventory (ONLY these items exist — reject any item the player claims to have that isn't listed):")
        for (item in items) {
            appendLine("  - ${item.name} x${item.quantity} [${item.rarity}]")
        }
    } else {
        appendLine("Player inventory: EMPTY — the player has no items.")
    }

    val combat = gameState.combatState
    if (combat != null && !combat.isOver) {
        appendLine()
        appendLine("Active combat: ${combat.enemy.name} [${combat.enemy.currentHP}/${combat.enemy.maxHP} HP]")
        appendLine("End your narration on momentum — what's the enemy doing? What's the opening?")
    }

    if (recentEvents.isNotEmpty()) {
        appendLine()
        appendLine("Recent context (for continuity): ${recentEvents.joinToString("; ")}")
    }
}

// Extension to safely pull string values from JsonObject (handles arrays gracefully)
private fun JsonObject.str(key: String): String? =
    when (val elem = this[key]) {
        is JsonPrimitive -> elem.content
        is JsonArray -> elem.joinToString(", ") {
            if (it is JsonPrimitive) it.content else it.toString()
        }
        else -> null
    }
