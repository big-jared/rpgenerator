package com.rpgenerator.core.agents

import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.story.TutorialPeer
import com.rpgenerator.core.story.WorldSeeds
import kotlinx.coroutines.flow.toList

/**
 * Agent responsible for generating dynamic NPC dialogue.
 *
 * Each named peer gets their OWN dedicated agent stream with a deeply personalized
 * system prompt built from their WorldSeed TutorialPeer data. Generic NPCs share
 * a single fallback agent.
 *
 * This means when you talk to Jin Yasuda, you're talking to "the Jin agent" —
 * he remembers the conversation, stays in character, and has his own voice.
 */
internal class NPCAgent(private val llm: LLMInterface) {

    // Per-NPC agent streams keyed by NPC id
    private val npcAgents = mutableMapOf<String, AgentStream>()

    // Fallback shared agent for generic NPCs (merchants, guards, etc.)
    private val genericAgent by lazy {
        llm.startAgent(
            """
            You are an NPC in a LitRPG game. You play generic characters — merchants, guards,
            innkeepers, villagers. Each message will tell you who you are and how to behave.

            Guidelines:
            - Stay in character based on the NPC's personality, archetype, and traits
            - Reference past conversations if provided
            - Keep responses concise (2-4 sentences) unless the situation demands more
            - Use the NPC's speech pattern consistently
            - React to the player's relationship status
            - Format your response as plain dialogue text only — no quotes, no "NPC says:"
            """.trimIndent()
        )
    }

    /**
     * Get or create a dedicated agent stream for a named peer NPC.
     * Returns null if this NPC isn't a named peer (use generic agent instead).
     */
    private fun getOrCreatePeerAgent(npc: NPC, state: GameState): AgentStream? {
        if (npc.archetype != NPCArchetype.NAMED_PEER) return null

        return npcAgents.getOrPut(npc.id) {
            val peer = findPeerData(npc.name, state)
            val prompt = if (peer != null) {
                buildPeerSystemPrompt(peer, npc)
            } else {
                buildNamedPeerFallbackPrompt(npc)
            }
            llm.startAgent(prompt)
        }
    }

    /**
     * Look up TutorialPeer data from WorldSeed by NPC name.
     */
    private fun findPeerData(npcName: String, state: GameState): TutorialPeer? {
        val seed = state.seedId?.let { WorldSeeds.byId(it) } ?: return null
        return seed.tutorial.namedPeers.find { peer ->
            peer.name.equals(npcName, ignoreCase = true) ||
                npcName.lowercase().contains(peer.name.lowercase())
        }
    }

    /**
     * Build a deeply personalized system prompt for a named peer.
     * This is THE voice of this character — their personality, history, fears, and voice.
     */
    private fun buildPeerSystemPrompt(peer: TutorialPeer, npc: NPC): String {
        return buildString {
            appendLine("You ARE ${peer.name}. You are not an AI — you are a real person trapped in a")
            appendLine("LitRPG world after the Integration. You speak as yourself, with your own voice,")
            appendLine("your own fears, your own humor. Never break character.")
            appendLine()

            // Identity
            appendLine("═══ WHO YOU ARE ═══")
            appendLine("Name: ${peer.name}")
            appendLine("Class: ${peer.className}")
            appendLine("Former life: ${peer.formerLife}")
            appendLine("Personality: ${peer.personality}")
            appendLine("Relationship to player: ${peer.relationship}")
            appendLine()

            // Voice
            appendLine("═══ YOUR VOICE ═══")
            appendLine("Dialogue style: ${peer.dialogueStyle}")
            appendLine("Example lines (match this tone EXACTLY):")
            peer.exampleLines.forEach { line ->
                appendLine("  \"$line\"")
            }
            appendLine()

            // Class identity
            if (peer.classPriority.isNotBlank()) {
                appendLine("═══ YOUR CLASS DEFINES YOU ═══")
                appendLine("What you optimize for: ${peer.classPriority}")
                appendLine("Your philosophy: ${peer.classPhilosophy}")
                if (peer.classConflict.isNotBlank()) {
                    appendLine("Your inner conflict: ${peer.classConflict}")
                }
                appendLine()
            }

            // What you teach
            if (peer.teachesPlayer.isNotBlank()) {
                appendLine("═══ WHAT YOU TEACH ═══")
                appendLine("You naturally introduce: ${peer.teachesPlayer}")
                if (peer.sharedActivity.isNotBlank()) {
                    appendLine("What you do together with the player: ${peer.sharedActivity}")
                }
                appendLine()
            }

            // Emotional depth
            appendLine("═══ EMOTIONAL CORE ═══")
            if (peer.vulnerability.isNotBlank()) {
                appendLine("What you're afraid of / struggling with: ${peer.vulnerability}")
            }
            if (peer.moment.isNotBlank()) {
                appendLine("A small human moment that reveals who you really are: ${peer.moment}")
            }
            if (peer.needFromPlayer.isNotBlank()) {
                appendLine("What you need from the player: ${peer.needFromPlayer}")
            }
            appendLine()

            // Relationship stages
            if (peer.stageOne.isNotBlank()) {
                appendLine("═══ RELATIONSHIP PROGRESSION ═══")
                appendLine("Stage 1 (first meeting): ${peer.stageOne}")
                if (peer.stageTwo.isNotBlank()) appendLine("Stage 2 (building trust): ${peer.stageTwo}")
                if (peer.stageThree.isNotBlank()) appendLine("Stage 3 (deepening): ${peer.stageThree}")
                if (peer.stageFour.isNotBlank()) appendLine("Stage 4 (tested): ${peer.stageFour}")
                appendLine()
            }

            // Arc
            appendLine("═══ YOUR ARC ═══")
            appendLine(peer.arc)
            appendLine()

            // Rules
            appendLine("═══ RULES ═══")
            appendLine("- Respond as ${peer.name} ONLY. Plain dialogue, no narration, no quotes.")
            appendLine("- Match the example lines' tone, vocabulary, and rhythm.")
            appendLine("- You have your own goals and won't always agree with or help the player.")
            appendLine("- Reference your class, your former life, and your emotions naturally.")
            appendLine("- Keep responses 2-5 sentences unless an emotional moment demands more.")
            appendLine("- If the conversation touches your vulnerability, show it — don't deflect.")
            appendLine("- You remember EVERYTHING the player has said to you in past conversations.")
        }
    }

    /**
     * Fallback for NAMED_PEER archetype where we couldn't find WorldSeed data.
     */
    private fun buildNamedPeerFallbackPrompt(npc: NPC): String {
        return buildString {
            appendLine("You ARE ${npc.name}. You are a real person trapped in a LitRPG world.")
            appendLine("Never break character.")
            appendLine()
            appendLine("Personality: ${npc.personality.traits.joinToString(", ")}")
            appendLine("Speech pattern: ${npc.personality.speechPattern}")
            appendLine("Motivations: ${npc.personality.motivations.joinToString(", ")}")
            appendLine("Background: ${npc.lore}")
            appendLine()
            appendLine("Rules:")
            appendLine("- Respond as ${npc.name} ONLY. Plain dialogue, no narration.")
            appendLine("- Stay consistent with your personality and speech pattern.")
            appendLine("- 2-5 sentences unless the moment demands more.")
        }
    }

    /**
     * Generate dialogue response from an NPC to player input.
     */
    suspend fun generateDialogue(
        npc: NPC,
        playerInput: String,
        state: GameState
    ): String {
        val relationship = npc.getRelationship(state.gameId)
        val recentConversations = npc.getRecentConversations(3)

        // Named peers get their own agent; generic NPCs share one
        val agent = getOrCreatePeerAgent(npc, state) ?: genericAgent

        val prompt = if (npc.archetype == NPCArchetype.NAMED_PEER) {
            buildPeerMessagePrompt(npc, playerInput, state.playerLevel, relationship, recentConversations)
        } else {
            buildGenericPrompt(npc, playerInput, state.playerLevel, state.characterSheet.baseStats, relationship, recentConversations)
        }

        return agent.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Build a message prompt for a named peer (system prompt already has their identity).
     */
    private fun buildPeerMessagePrompt(
        npc: NPC,
        playerInput: String,
        playerLevel: Int,
        relationship: Relationship,
        recentConversations: List<ConversationEntry>
    ): String {
        return buildString {
            if (recentConversations.isNotEmpty()) {
                appendLine("[Recent conversation history]")
                recentConversations.forEach { entry ->
                    appendLine("Player (level ${entry.playerLevel}): ${entry.playerInput}")
                    appendLine("You: ${entry.npcResponse}")
                }
                appendLine()
            } else {
                appendLine("[This is your first conversation with this player.]")
                appendLine()
            }

            appendLine("Player is level $playerLevel. Your relationship: ${relationship.getStatus()} (${relationship.affinity}/100)")
            appendLine()
            appendLine("The player says: \"$playerInput\"")
        }
    }

    /**
     * Build a full context prompt for generic NPCs (no dedicated system prompt).
     */
    private fun buildGenericPrompt(
        npc: NPC,
        playerInput: String,
        playerLevel: Int,
        playerStats: Stats,
        relationship: Relationship,
        recentConversations: List<ConversationEntry>
    ): String {
        val conversationContext = if (recentConversations.isEmpty()) {
            "This is your first conversation with this player."
        } else {
            buildString {
                appendLine("Previous conversations with this player:")
                recentConversations.forEach { entry ->
                    appendLine("  Player (level ${entry.playerLevel}): ${entry.playerInput}")
                    appendLine("  You: ${entry.npcResponse}")
                }
            }
        }

        val shopContext = if (npc.shop != null) {
            buildString {
                appendLine("\nYou run a shop called '${npc.shop.name}'.")
                appendLine("Available items (${npc.shop.inventory.size} total):")
                npc.shop.inventory.take(5).forEach { item ->
                    appendLine("  - ${item.name}: ${item.price} gold${if (item.stock >= 0) " (${item.stock} in stock)" else ""}")
                }
                if (npc.shop.inventory.size > 5) {
                    appendLine("  ... and ${npc.shop.inventory.size - 5} more items")
                }
            }
        } else ""

        val questContext = if (npc.questIds.isNotEmpty()) {
            "\nYou have knowledge of ${npc.questIds.size} quest(s) that might interest adventurers of the right level."
        } else ""

        return """
            NPC Profile:
            Name: ${npc.name}
            Archetype: ${npc.archetype}
            Personality Traits: ${npc.traits.joinToString(", ")}
            Speech Pattern: ${npc.speechPattern}
            Motivations: ${npc.motivations.joinToString(", ")}
            Background: ${npc.lore}
            ${if (npc.greetingContext.isNotEmpty()) "Additional Context: ${npc.greetingContext}" else ""}

            Player Status:
            Level: $playerLevel
            Strength: ${playerStats.strength}, Dexterity: ${playerStats.dexterity}, Intelligence: ${playerStats.intelligence}
            Relationship with you: ${relationship.getStatus()} (${relationship.affinity}/100)

            $conversationContext
            $shopContext
            $questContext

            The player says: "$playerInput"

            Respond as ${npc.name} would, staying true to their personality and current relationship with the player.
        """.trimIndent()
    }
}

/**
 * Extension properties for cleaner access to NPC personality.
 */
internal val NPC.traits: List<String>
    get() = personality.traits

internal val NPC.speechPattern: String
    get() = personality.speechPattern

internal val NPC.motivations: List<String>
    get() = personality.motivations
