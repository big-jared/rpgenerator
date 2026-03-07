package com.rpgenerator.core.test

import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MockLLMInterface(
    private val intentOverride: String? = null,
    private val malformed: Boolean = false
) : LLMInterface {
    override fun startAgent(systemPrompt: String): AgentStream {
        return MockAgentStream(intentOverride, malformed)
    }
}

class MockAgentStream(
    private val intentOverride: String? = null,
    private val malformed: Boolean = false
) : AgentStream {
    override suspend fun sendMessage(message: String): Flow<String> {
        return when {
            malformed && message.contains("Analyze this action") -> {
                flowOf("not valid json at all")
            }
            message.contains("Analyze this action") -> {
                val intent = intentOverride ?: detectIntent(message)
                flowOf("""{"intent": "$intent", "target": "test-target", "context": "Test context"}""")
            }
            message.contains("PLAN THIS SCENE") -> {
                // GameMaster scene planning — return valid ScenePlan JSON
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
        // Extract just the player input from the GM prompt to avoid matching
        // keywords in the JSON schema example (e.g., "COMBAT|EXPLORATION|...")
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
        // Try to find NPC names from the "NPCs Present:" section of the GM prompt
        val npcSection = message.substringAfter("NPCs Present:", "")
        if (npcSection.isBlank()) return null
        // Look for "- Name (Archetype)" pattern
        val nameMatch = Regex("""- (\w+) \(""").find(npcSection)
        return nameMatch?.groupValues?.get(1)
    }

    private fun detectIntentFromClassification(message: String): String {
        // Extract player input from the classification prompt
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
        // Simple keyword matching to simulate what the real AI would do
        // Real SystemAgent uses LLM to analyze intent
        return when {
            message.contains("attack") || message.contains("fight") -> "COMBAT"
            message.contains("talk") || message.contains("speak") -> "NPC_DIALOGUE"
            message.contains("stats") || message.contains("status") -> "SYSTEM_QUERY"
            message.contains("look") || message.contains("explore") -> "EXPLORATION"
            else -> "EXPLORATION"
        }
    }

    private fun generateNPCDialogue(message: String): Flow<String> {
        // Extract NPC name and player input from the prompt
        val npcName = message.lines().find { it.startsWith("Name:") }
            ?.substringAfter("Name:")?.trim() ?: "NPC"

        val playerSays = message.lines().find { it.startsWith("The player says:") }
            ?.substringAfter("The player says:")?.trim()?.removeSurrounding("\"") ?: ""

        // Generate contextual response based on archetype and player input
        val response = when {
            message.contains("MERCHANT") -> {
                when {
                    playerSays.contains("buy", ignoreCase = true) ||
                    playerSays.contains("shop", ignoreCase = true) ->
                        "Welcome to my shop! I have fine wares for sale. What catches your eye?"
                    playerSays.contains("sell", ignoreCase = true) ->
                        "I might be interested in buying your goods. Show me what you have."
                    else ->
                        "Greetings, traveler. Looking to trade today?"
                }
            }
            message.contains("QUEST_GIVER") -> {
                when {
                    playerSays.contains("quest", ignoreCase = true) ||
                    playerSays.contains("help", ignoreCase = true) ->
                        "I'm glad you asked. There's a problem that needs solving, and you look capable enough."
                    else ->
                        "These are troubled times. We could use someone with your skills."
                }
            }
            message.contains("GUARD") -> {
                "State your business here, traveler."
            }
            message.contains("INNKEEPER") -> {
                "Welcome to my inn! Can I get you a room, or perhaps some food and drink?"
            }
            message.contains("BLACKSMITH") -> {
                when {
                    playerSays.contains("weapon", ignoreCase = true) ||
                    playerSays.contains("armor", ignoreCase = true) ->
                        "You've come to the right place. I craft the finest equipment in the region."
                    else ->
                        "Need something forged? I can make whatever you need."
                }
            }
            message.contains("ALCHEMIST") -> {
                "Ah, interested in the alchemical arts? I have potions and elixirs for various needs."
            }
            message.contains("SCHOLAR") -> {
                "Knowledge is the most valuable treasure. What would you like to learn?"
            }
            message.contains("WANDERER") -> {
                "The paths of fate are mysterious, traveler. Our meeting may not be coincidence."
            }
            else -> {
                "Hello there, traveler."
            }
        }

        return flowOf(response)
    }
}
