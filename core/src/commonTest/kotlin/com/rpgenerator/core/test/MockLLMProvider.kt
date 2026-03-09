package com.rpgenerator.core.test

import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.LLMToolCall
import com.rpgenerator.core.api.LLMToolDef
import com.rpgenerator.core.api.LLMToolResult
import com.rpgenerator.core.api.ToolExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MockLLMInterface(
    private val intentOverride: String? = null,
    private val malformed: Boolean = false
) : LLMInterface {
    /** Track agents created by prompt type for testing the 3-phase pipeline */
    val createdAgents = mutableListOf<MockAgentStream>()

    override fun startAgent(systemPrompt: String): AgentStream {
        val agentType = when {
            systemPrompt.contains("CLASSIFY INTENT") || systemPrompt.contains("DECIDE mode") -> AgentType.DECIDE
            systemPrompt.contains("CRITICAL NARRATION RULES") -> AgentType.NARRATE
            else -> AgentType.LEGACY
        }
        val agent = MockAgentStream(intentOverride, malformed, agentType)
        createdAgents.add(agent)
        return agent
    }
}

enum class AgentType { DECIDE, NARRATE, LEGACY }

class MockAgentStream(
    private val intentOverride: String? = null,
    private val malformed: Boolean = false,
    val agentType: AgentType = AgentType.LEGACY
) : AgentStream {
    /** Configure tool calls that sendMessageWithTools should make before returning. */
    var toolCallsToMake: List<LLMToolCall> = emptyList()

    /** Records tool results returned by the executor during sendMessageWithTools. */
    val recordedToolResults: MutableList<LLMToolResult> = mutableListOf()

    /** Track all messages received */
    val receivedMessages: MutableList<String> = mutableListOf()

    override suspend fun sendMessageWithTools(
        message: String,
        tools: List<LLMToolDef>,
        executor: ToolExecutor
    ): Flow<String> {
        receivedMessages.add(message)
        if (toolCallsToMake.isEmpty()) {
            // For DECIDE agent, return empty (prose is discarded anyway)
            if (agentType == AgentType.DECIDE) {
                return flowOf("")
            }
            return sendMessage(message)
        }
        // Execute each configured tool call
        for (call in toolCallsToMake) {
            val result = executor(call)
            recordedToolResults.add(result)
        }
        // For DECIDE agent, return empty (prose is discarded)
        if (agentType == AgentType.DECIDE) {
            return flowOf("")
        }
        // Return mock narrative after tool calls (legacy path)
        return flowOf("Mock narrative response after tool calls. ")
    }

    override suspend fun sendMessage(message: String): Flow<String> {
        receivedMessages.add(message)

        // NARRATE agent: return narration based on turn summary
        if (agentType == AgentType.NARRATE) {
            return when {
                message.contains("COMBAT ROUND") && message.contains("VICTORY") ->
                    flowOf("Your blade finds its mark. The creature crumbles. Victory is yours.")
                message.contains("COMBAT ROUND") ->
                    flowOf("Steel meets flesh. The fight continues, your breathing ragged but steady.")
                message.contains("Moved to") ->
                    flowOf("You step into the new area, senses alert for danger.")
                message.contains("NPC appeared") ->
                    flowOf("A figure emerges from the shadows, regarding you with cautious interest.")
                message.contains("No tools executed") ->
                    flowOf("The world breathes around you. Silence, except for your own heartbeat.")
                else ->
                    flowOf("The moment passes, leaving only the weight of what happened.")
            }
        }

        // Legacy agent responses
        return when {
            malformed && message.contains("Analyze this action") -> {
                flowOf("not valid json at all")
            }
            message.contains("Analyze this action") -> {
                val intent = intentOverride ?: detectIntent(message)
                flowOf("""{"intent": "$intent", "target": "test-target", "context": "Test context"}""")
            }
            message.contains("PLAN THIS SCENE") -> {
                val actionType = detectActionType(message)
                val target = when (actionType) {
                    "COMBAT" -> "goblin"
                    "DIALOGUE" -> extractNPCTarget(message)
                    else -> null
                }
                flowOf("""
                {
                    "primaryAction": {
                        "type": "$actionType",
                        "target": ${if (target != null) "\"$target\"" else "null"},
                        "description": "Player performs $actionType action",
                        "narrativeContext": "The scene unfolds"
                    },
                    "npcReactions": [],
                    "environmentalEffects": [],
                    "narrativeBeats": [],
                    "suggestedActions": [
                        {"action": "Continue", "type": "EXPLORATION", "riskLevel": "SAFE", "context": "Keep going"}
                    ],
                    "sceneTone": "TENSE",
                    "triggeredEvents": []
                }
                """.trimIndent())
            }
            message.contains("Classify this player input") -> {
                val intent = intentOverride ?: detectIntentFromClassification(message)
                flowOf(intent)
            }
            message.contains("RENDER THIS SCENE") || message.contains("Narrate") || message.contains("COMBAT RESULT") -> {
                flowOf("Your blade strikes true, cutting deep into the goblin's flesh.")
            }
            message.contains("Generate a new location") -> {
                flowOf("""
                {
                    "name": "Hidden Cave",
                    "biome": "CAVE",
                    "description": "A dark cavern concealed behind thick vines. Water drips from stalactites overhead.",
                    "danger": 4,
                    "features": ["stalactites", "underground_stream", "bat_colony"],
                    "lore": "Local legends speak of treasures hidden in these caves by ancient explorers."
                }
                """.trimIndent())
            }
            message.contains("NPC Profile:") -> {
                generateNPCDialogue(message)
            }
            else -> {
                flowOf("Mock response for: ${message.take(50)}")
            }
        }
    }

    private fun detectActionType(message: String): String {
        val playerInput = Regex("""Player Input:\s*"([^"]+)"""").find(message)
            ?.groupValues?.get(1)?.lowercase() ?: message.lowercase()
        return when {
            playerInput.contains("attack") || playerInput.contains("fight") || playerInput.contains("combat") -> "COMBAT"
            playerInput.contains("talk") || playerInput.contains("speak") || playerInput.contains("thank") || playerInput.contains("greet") -> "DIALOGUE"
            playerInput.contains("move") || playerInput.contains("go to") || playerInput.contains("travel") -> "MOVEMENT"
            else -> "EXPLORATION"
        }
    }

    private fun extractNPCTarget(message: String): String? {
        val npcSection = message.substringAfter("NPCs Present:", "")
        if (npcSection.isBlank()) return null
        val nameMatch = Regex("""- (\w+) \(""").find(npcSection)
        return nameMatch?.groupValues?.get(1)
    }

    private fun detectIntentFromClassification(message: String): String {
        val playerInput = Regex("""Player input:\s*"([^"]+)"""").find(message)
            ?.groupValues?.get(1)?.lowercase() ?: message.lowercase()
        val words = playerInput.split("\\s+".toRegex())
        fun hasWord(w: String) = words.any { it == w || it == "${w}s" || it == "${w}ed" }
        val intent = when {
            playerInput.contains("skill") && (playerInput.contains("show") || playerInput.contains("list") || playerInput.contains("my") || playerInput.contains("view")) -> "SKILL_MENU"
            hasWord("attack") || hasWord("fight") || hasWord("kill") -> "COMBAT"
            playerInput.contains("talk") || playerInput.contains("speak") || hasWord("ask") -> "NPC_DIALOGUE"
            playerInput.contains("class") || playerInput.contains("choose") -> "CLASS_SELECTION"
            playerInput.contains("quest") -> "QUEST_ACTION"
            playerInput.contains("stats") || playerInput.contains("status") -> "SYSTEM_QUERY"
            playerInput.contains("inventory") || playerInput.contains("bag") || playerInput.contains("items") -> "INVENTORY_MENU"
            playerInput.contains("search") || playerInput.contains("explore") -> "EXPLORATION"
            else -> "EXPLORATION"
        }
        val target = when (intent) {
            "COMBAT" -> Regex("""(?:attack|fight|kill)\s+(?:the\s+)?(\w+)""").find(playerInput)?.groupValues?.get(1) ?: "unknown enemy"
            "NPC_DIALOGUE" -> Regex("""(?:talk|speak|ask)\s+(?:to\s+)?(?:the\s+)?(\w+)""").find(playerInput)?.groupValues?.get(1) ?: "unknown npc"
            else -> "NONE"
        }
        val shouldGen = if (intent == "EXPLORATION" && (playerInput.contains("search") || playerInput.contains("explore"))) "true" else "false"
        return "INTENT: $intent\nTARGET: $target\nREASONING: Mock classification\nSHOULD_GENERATE_LOCATION: $shouldGen"
    }

    private fun detectIntent(message: String): String {
        return when {
            message.contains("attack") || message.contains("fight") -> "COMBAT"
            message.contains("talk") || message.contains("speak") -> "NPC_DIALOGUE"
            message.contains("stats") || message.contains("status") -> "SYSTEM_QUERY"
            message.contains("look") || message.contains("explore") -> "EXPLORATION"
            else -> "EXPLORATION"
        }
    }

    private fun generateNPCDialogue(message: String): Flow<String> {
        val response = when {
            message.contains("MERCHANT") -> "Welcome to my shop! I have fine wares for sale."
            message.contains("QUEST_GIVER") -> "I'm glad you asked. There's a problem that needs solving."
            message.contains("GUARD") -> "State your business here, traveler."
            message.contains("INNKEEPER") -> "Welcome to my inn! Can I get you a room?"
            message.contains("BLACKSMITH") -> "Need something forged? I can make whatever you need."
            message.contains("ALCHEMIST") -> "Interested in the alchemical arts? I have potions."
            message.contains("SCHOLAR") -> "Knowledge is the most valuable treasure."
            message.contains("WANDERER") -> "The paths of fate are mysterious, traveler."
            else -> "Hello there, traveler."
        }
        return flowOf(response)
    }
}
