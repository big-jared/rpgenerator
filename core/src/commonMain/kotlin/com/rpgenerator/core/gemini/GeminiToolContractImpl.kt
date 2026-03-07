package com.rpgenerator.core.gemini

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.rules.RulesEngine
import kotlinx.serialization.json.*

/**
 * Implementation of GeminiToolContract that executes tool calls against real game state.
 * This is what Gemini calls into during a Live API session.
 *
 * Maintains a mutable reference to game state so tool calls can read and modify it.
 */
internal class GeminiToolContractImpl(
    initialState: GameState,
    private val rulesEngine: RulesEngine = RulesEngine()
) : GeminiToolContract {

    var gameState: GameState = initialState
        private set

    private val pendingEvents = mutableListOf<GameEvent>()

    // ── State Queries ──────────────────────────────────────────────

    override fun getPlayerStats(): ToolResult {
        val sheet = gameState.characterSheet
        val data = buildJsonObject {
            put("name", JsonPrimitive(gameState.playerName))
            put("level", JsonPrimitive(sheet.level))
            put("xp", JsonPrimitive(sheet.xp))
            put("xpToNextLevel", JsonPrimitive(sheet.xpToNextLevel()))
            put("hp", JsonPrimitive(sheet.resources.currentHP))
            put("maxHP", JsonPrimitive(sheet.resources.maxHP))
            put("mana", JsonPrimitive(sheet.resources.currentMana))
            put("maxMana", JsonPrimitive(sheet.resources.maxMana))
            put("location", JsonPrimitive(gameState.currentLocation.name))
            put("class", JsonPrimitive(sheet.playerClass.name))
            put("grade", JsonPrimitive(sheet.currentGrade.name))
        }
        return ToolResult(success = true, data = data)
    }

    override fun getInventory(): ToolResult {
        val inventory = gameState.characterSheet.inventory
        val data = buildJsonObject {
            putJsonArray("items") {
                inventory.items.values.forEach { item ->
                    addJsonObject {
                        put("id", JsonPrimitive(item.id))
                        put("name", JsonPrimitive(item.name))
                        put("description", JsonPrimitive(item.description))
                        put("type", JsonPrimitive(item.type.name))
                        put("quantity", JsonPrimitive(item.quantity))
                    }
                }
            }
            put("usedSlots", JsonPrimitive(inventory.items.size))
            put("maxSlots", JsonPrimitive(inventory.maxSlots))
        }
        return ToolResult(success = true, data = data)
    }

    override fun getActiveQuests(): ToolResult {
        val data = buildJsonObject {
            putJsonArray("quests") {
                gameState.activeQuests.values.forEach { quest ->
                    addJsonObject {
                        put("id", JsonPrimitive(quest.id))
                        put("name", JsonPrimitive(quest.name))
                        put("description", JsonPrimitive(quest.description))
                        put("status", JsonPrimitive(quest.status.name))
                        put("complete", JsonPrimitive(quest.isComplete()))
                        putJsonArray("objectives") {
                            quest.objectives.forEach { obj ->
                                addJsonObject {
                                    put("description", JsonPrimitive(obj.progressDescription()))
                                    put("progress", JsonPrimitive(obj.currentProgress))
                                    put("target", JsonPrimitive(obj.targetProgress))
                                    put("complete", JsonPrimitive(obj.isComplete()))
                                }
                            }
                        }
                    }
                }
            }
        }
        return ToolResult(success = true, data = data)
    }

    override fun getNPCsHere(): ToolResult {
        val npcs = gameState.getNPCsAtCurrentLocation()
        val data = buildJsonObject {
            putJsonArray("npcs") {
                npcs.forEach { npc ->
                    addJsonObject {
                        put("id", JsonPrimitive(npc.id))
                        put("name", JsonPrimitive(npc.name))
                        put("archetype", JsonPrimitive(npc.archetype.name))
                        put("hasShop", JsonPrimitive(npc.shop != null))
                        put("hasQuests", JsonPrimitive(npc.questIds.isNotEmpty()))
                    }
                }
            }
        }
        return ToolResult(success = true, data = data)
    }

    override fun getLocation(): ToolResult {
        val loc = gameState.currentLocation
        val data = buildJsonObject {
            put("id", JsonPrimitive(loc.id))
            put("name", JsonPrimitive(loc.name))
            put("description", JsonPrimitive(loc.description))
            put("biome", JsonPrimitive(loc.biome.name))
            put("danger", JsonPrimitive(loc.danger))
            putJsonArray("features") {
                loc.features.forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("connections") {
                loc.connections.forEach { add(JsonPrimitive(it)) }
            }
            put("lore", JsonPrimitive(loc.lore))
        }
        return ToolResult(success = true, data = data)
    }

    override fun getCharacterSheet(): ToolResult {
        val sheet = gameState.characterSheet
        val effective = sheet.effectiveStats()
        val data = buildJsonObject {
            put("level", JsonPrimitive(sheet.level))
            put("xp", JsonPrimitive(sheet.xp))
            put("xpToNextLevel", JsonPrimitive(sheet.xpToNextLevel()))
            put("class", JsonPrimitive(sheet.playerClass.name))
            put("grade", JsonPrimitive(sheet.currentGrade.name))
            putJsonObject("HP") {
                put("current", JsonPrimitive(sheet.resources.currentHP))
                put("max", JsonPrimitive(sheet.resources.maxHP))
            }
            putJsonObject("mana") {
                put("current", JsonPrimitive(sheet.resources.currentMana))
                put("max", JsonPrimitive(sheet.resources.maxMana))
            }
            putJsonObject("stats") {
                put("strength", JsonPrimitive(effective.strength))
                put("dexterity", JsonPrimitive(effective.dexterity))
                put("constitution", JsonPrimitive(effective.constitution))
                put("intelligence", JsonPrimitive(effective.intelligence))
                put("wisdom", JsonPrimitive(effective.wisdom))
                put("defense", JsonPrimitive(effective.defense))
            }
            putJsonArray("skills") {
                sheet.skills.forEach { skill ->
                    addJsonObject {
                        put("id", JsonPrimitive(skill.id))
                        put("name", JsonPrimitive(skill.name))
                        put("level", JsonPrimitive(skill.level))
                    }
                }
            }
            putJsonObject("equipment") {
                put("weapon", JsonPrimitive(sheet.equipment.weapon?.name ?: "None"))
                put("armor", JsonPrimitive(sheet.equipment.armor?.name ?: "None"))
                put("accessory", JsonPrimitive(sheet.equipment.accessory?.name ?: "None"))
            }
        }
        return ToolResult(success = true, data = data)
    }

    // ── Actions ────────────────────────────────────────────────────

    override suspend fun moveToLocation(locationName: String): ToolResult {
        // Check connections for matching location name
        val connections = gameState.currentLocation.connections
        // For now, we don't have a location manager here, so fail gracefully
        return ToolResult(
            success = false,
            error = "Location '$locationName' is not accessible from current location"
        )
    }

    override suspend fun attackTarget(targetName: String): ToolResult {
        val outcome = rulesEngine.calculateCombatOutcome(targetName, gameState)
        val events = mutableListOf<GameEvent>()

        events.add(GameEvent.CombatLog("You attack the $targetName for ${outcome.damage} damage!"))
        events.add(GameEvent.StatChange("xp", gameState.playerXP.toInt(), (gameState.playerXP + outcome.xpGain).toInt()))

        // Update game state
        gameState = gameState.gainXP(outcome.xpGain)

        if (outcome.levelUp) {
            gameState = gameState.updateCharacterSheet(
                gameState.characterSheet.copy(level = outcome.newLevel)
            )
            events.add(GameEvent.SystemNotification("Level up! You are now level ${outcome.newLevel}"))
        }

        // Add loot
        outcome.loot.forEach { generatedItem ->
            val itemName = when (generatedItem) {
                is com.rpgenerator.core.loot.GeneratedItem.GeneratedWeapon -> generatedItem.item.name
                is com.rpgenerator.core.loot.GeneratedItem.GeneratedArmor -> generatedItem.item.name
                else -> "Loot Item"
            }
            events.add(GameEvent.ItemGained("loot_${itemName.lowercase().replace(" ", "_")}", itemName, 1))
        }

        val data = buildJsonObject {
            put("target", JsonPrimitive(targetName))
            put("damage", JsonPrimitive(outcome.damage))
            put("xpGained", JsonPrimitive(outcome.xpGain))
            put("levelUp", JsonPrimitive(outcome.levelUp))
            put("defeated", JsonPrimitive(true))
        }

        return ToolResult(success = true, data = data, gameEvents = events)
    }

    override suspend fun useItem(itemId: String): ToolResult {
        val item = gameState.characterSheet.inventory.items[itemId]
            ?: return ToolResult(success = false, error = "Item '$itemId' not found in inventory")

        if (item.type != ItemType.CONSUMABLE) {
            return ToolResult(success = false, error = "'${item.name}' cannot be used")
        }

        gameState = gameState.removeItem(itemId, 1)
        return ToolResult(
            success = true,
            data = buildJsonObject {
                put("used", JsonPrimitive(item.name))
                put("remaining", JsonPrimitive((item.quantity - 1).coerceAtLeast(0)))
            }
        )
    }

    override suspend fun useSkill(skillId: String, target: String?): ToolResult {
        val skill = gameState.characterSheet.skills.find { it.id == skillId }
            ?: return ToolResult(success = false, error = "Skill '$skillId' not found")

        return ToolResult(
            success = true,
            data = buildJsonObject {
                put("skill", JsonPrimitive(skill.name))
                put("target", JsonPrimitive(target ?: "none"))
            }
        )
    }

    override suspend fun pickUpItem(itemName: String): ToolResult {
        // This would normally check the environment for items
        return ToolResult(
            success = true,
            data = buildJsonObject {
                put("item", JsonPrimitive(itemName))
                put("message", JsonPrimitive("Picked up $itemName"))
            },
            gameEvents = listOf(GameEvent.ItemGained("item_${itemName.lowercase().replace(" ", "_")}", itemName, 1))
        )
    }

    override suspend fun talkToNPC(npcName: String, dialogue: String?): ToolResult {
        val npc = gameState.findNPCByName(npcName)
            ?: return ToolResult(success = false, error = "'$npcName' not found at this location")

        val data = buildJsonObject {
            put("npcId", JsonPrimitive(npc.id))
            put("npcName", JsonPrimitive(npc.name))
            put("archetype", JsonPrimitive(npc.archetype.name))
            put("personality", JsonPrimitive(npc.personality.traits.joinToString(", ")))
            put("speechPattern", JsonPrimitive(npc.personality.speechPattern))
            put("hasShop", JsonPrimitive(npc.shop != null))
            put("hasQuests", JsonPrimitive(npc.questIds.isNotEmpty()))
            put("lore", JsonPrimitive(npc.lore))
        }

        return ToolResult(success = true, data = data)
    }

    override suspend fun buyFromShop(npcName: String, itemName: String): ToolResult {
        val npc = gameState.findNPCByName(npcName)
            ?: return ToolResult(success = false, error = "NPC '$npcName' not found")

        val shop = npc.shop
            ?: return ToolResult(success = false, error = "${npc.name} doesn't have a shop")

        return ToolResult(
            success = false,
            error = "Shop transactions not yet implemented for Gemini tools"
        )
    }

    // ── Quest Management ───────────────────────────────────────────

    override suspend fun acceptQuest(questId: String): ToolResult {
        // In practice, quests would come from NPCs or events
        return ToolResult(
            success = true,
            data = buildJsonObject {
                put("questId", JsonPrimitive(questId))
                put("status", JsonPrimitive("accepted"))
            },
            gameEvents = listOf(GameEvent.QuestUpdate(questId, questId, com.rpgenerator.core.api.QuestStatus.NEW))
        )
    }

    override suspend fun updateQuestProgress(questId: String, objectiveId: String, progress: Int): ToolResult {
        val quest = gameState.activeQuests[questId]
            ?: return ToolResult(success = false, error = "Quest '$questId' not found")

        gameState = gameState.updateQuestObjective(questId, objectiveId, progress)

        return ToolResult(
            success = true,
            data = buildJsonObject {
                put("questId", JsonPrimitive(questId))
                put("objectiveId", JsonPrimitive(objectiveId))
                put("progress", JsonPrimitive(progress))
            }
        )
    }

    override suspend fun completeQuest(questId: String): ToolResult {
        val quest = gameState.activeQuests[questId]
            ?: return ToolResult(success = false, error = "Quest '$questId' not found")

        if (!quest.isComplete()) {
            return ToolResult(
                success = false,
                error = "Quest '${quest.name}' has unfinished objectives"
            )
        }

        gameState = gameState.completeQuest(questId)

        return ToolResult(
            success = true,
            data = buildJsonObject {
                put("questId", JsonPrimitive(questId))
                put("questName", JsonPrimitive(quest.name))
                put("xpReward", JsonPrimitive(quest.rewards.xp))
            },
            gameEvents = listOf(
                GameEvent.QuestUpdate(questId, quest.name, com.rpgenerator.core.api.QuestStatus.COMPLETED)
            )
        )
    }

    // ── World Generation ───────────────────────────────────────────

    override suspend fun generateLocation(description: String): ToolResult {
        // Placeholder — will be wired to LocationGeneratorAgent
        return ToolResult(
            success = true,
            data = buildJsonObject {
                put("message", JsonPrimitive("Location generation queued: $description"))
            }
        )
    }

    override suspend fun generateNPC(name: String, role: String, personality: String): ToolResult {
        // Placeholder — will be wired to NPCArchetypeGenerator
        return ToolResult(
            success = true,
            data = buildJsonObject {
                put("name", JsonPrimitive(name))
                put("role", JsonPrimitive(role))
                put("message", JsonPrimitive("NPC generation queued"))
            }
        )
    }

    override suspend fun generateQuest(context: String): ToolResult {
        // Placeholder — will be wired to QuestGeneratorAgent
        return ToolResult(
            success = true,
            data = buildJsonObject {
                put("message", JsonPrimitive("Quest generation queued: $context"))
            }
        )
    }

    // ── Multimodal Triggers ────────────────────────────────────────

    override suspend fun generateSceneArt(sceneDescription: String): ToolResult {
        // Emit a SceneImage event — actual image bytes come from Gemini's image generation
        val event = GameEvent.SceneImage(
            imageData = ByteArray(0), // Placeholder — real impl calls Gemini image gen
            description = sceneDescription
        )
        return ToolResult(
            success = true,
            data = buildJsonObject {
                put("description", JsonPrimitive(sceneDescription))
                put("status", JsonPrimitive("generating"))
            },
            gameEvents = listOf(event)
        )
    }

    override suspend fun shiftMusicMood(mood: String, intensity: Float): ToolResult {
        val event = GameEvent.MusicChange(
            mood = mood,
            audioData = null // Placeholder — real impl generates music
        )
        return ToolResult(
            success = true,
            data = buildJsonObject {
                put("mood", JsonPrimitive(mood))
                put("intensity", JsonPrimitive(intensity))
            },
            gameEvents = listOf(event)
        )
    }

    // ── Tool Dispatch ──────────────────────────────────────────────

    /**
     * Route a Gemini tool call to the correct function.
     * This is called when the Live API sends a tool call request.
     */
    suspend fun dispatch(call: GeminiToolCall): ToolResult {
        return when (call.name) {
            // State queries
            "get_player_stats" -> getPlayerStats()
            "get_inventory" -> getInventory()
            "get_active_quests" -> getActiveQuests()
            "get_npcs_here" -> getNPCsHere()
            "get_location" -> getLocation()
            "get_character_sheet" -> getCharacterSheet()

            // Actions
            "move_to_location" -> moveToLocation(call.stringArg("locationName") ?: call.stringArg("location") ?: "")
            "attack_target" -> attackTarget(call.stringArg("target") ?: call.stringArg("targetName") ?: "")
            "use_item" -> useItem(call.stringArg("itemId") ?: "")
            "use_skill" -> useSkill(call.stringArg("skillId") ?: "", call.stringArg("target"))
            "pick_up_item" -> pickUpItem(call.stringArg("itemName") ?: call.stringArg("item") ?: "")
            "talk_to_npc" -> talkToNPC(call.stringArg("npcName") ?: "", call.stringArg("dialogue"))
            "buy_from_shop" -> buyFromShop(call.stringArg("npcName") ?: "", call.stringArg("itemName") ?: "")

            // Quest management
            "accept_quest" -> acceptQuest(call.stringArg("questId") ?: "")
            "update_quest_progress" -> updateQuestProgress(
                call.stringArg("questId") ?: "",
                call.stringArg("objectiveId") ?: "",
                call.intArg("progress") ?: 0
            )
            "complete_quest" -> completeQuest(call.stringArg("questId") ?: "")

            // World generation
            "generate_location" -> generateLocation(call.stringArg("description") ?: "")
            "generate_npc" -> generateNPC(
                call.stringArg("name") ?: "",
                call.stringArg("role") ?: "",
                call.stringArg("personality") ?: ""
            )
            "generate_quest" -> generateQuest(call.stringArg("context") ?: "")

            // Multimodal
            "generate_scene_art" -> generateSceneArt(call.stringArg("sceneDescription") ?: call.stringArg("description") ?: "")
            "shift_music_mood" -> shiftMusicMood(
                call.stringArg("mood") ?: "",
                call.floatArg("intensity") ?: 0.5f
            )

            else -> ToolResult(success = false, error = "Unknown tool: ${call.name}")
        }
    }

    /**
     * Get all tool declarations for Gemini session setup.
     */
    fun getToolDeclarations(): List<GeminiToolDeclaration> = listOf(
        // State queries
        GeminiToolDeclaration("get_player_stats", "Get player level, HP, XP, location, and class", emptyMap()),
        GeminiToolDeclaration("get_inventory", "Get player's inventory items", emptyMap()),
        GeminiToolDeclaration("get_active_quests", "Get all active quests and their progress", emptyMap()),
        GeminiToolDeclaration("get_npcs_here", "Get NPCs at the player's current location", emptyMap()),
        GeminiToolDeclaration("get_location", "Get current location details, features, and connections", emptyMap()),
        GeminiToolDeclaration("get_character_sheet", "Get full character sheet with stats, skills, equipment", emptyMap()),

        // Actions
        GeminiToolDeclaration("attack_target", "Attack an enemy", mapOf(
            "target" to ToolParameter("string", "Name of enemy to attack")
        )),
        GeminiToolDeclaration("move_to_location", "Move to a connected location", mapOf(
            "locationName" to ToolParameter("string", "Name of location to move to")
        )),
        GeminiToolDeclaration("talk_to_npc", "Start or continue conversation with an NPC", mapOf(
            "npcName" to ToolParameter("string", "Name of NPC to talk to"),
            "dialogue" to ToolParameter("string", "What the player says", required = false)
        )),
        GeminiToolDeclaration("use_item", "Use an item from inventory", mapOf(
            "itemId" to ToolParameter("string", "ID of item to use")
        )),
        GeminiToolDeclaration("use_skill", "Use a skill", mapOf(
            "skillId" to ToolParameter("string", "ID of skill to use"),
            "target" to ToolParameter("string", "Target for the skill", required = false)
        )),
        GeminiToolDeclaration("pick_up_item", "Pick up an item from the environment", mapOf(
            "itemName" to ToolParameter("string", "Name of item to pick up")
        )),

        // Quest management
        GeminiToolDeclaration("accept_quest", "Accept a quest", mapOf(
            "questId" to ToolParameter("string", "ID of quest to accept")
        )),
        GeminiToolDeclaration("complete_quest", "Complete a quest if all objectives are met", mapOf(
            "questId" to ToolParameter("string", "ID of quest to complete")
        )),

        // World generation
        GeminiToolDeclaration("generate_location", "Generate a new discoverable location", mapOf(
            "description" to ToolParameter("string", "Description of what kind of location to generate")
        )),
        GeminiToolDeclaration("generate_npc", "Generate a new NPC at current location", mapOf(
            "name" to ToolParameter("string", "NPC name"),
            "role" to ToolParameter("string", "NPC role (merchant, guard, quest_giver, etc.)"),
            "personality" to ToolParameter("string", "Personality description")
        )),

        // Multimodal
        GeminiToolDeclaration("generate_scene_art", "Generate an image of the current scene", mapOf(
            "sceneDescription" to ToolParameter("string", "Visual description of the scene to generate")
        ), behavior = ToolBehavior.NON_BLOCKING_WHEN_IDLE),
        GeminiToolDeclaration("shift_music_mood", "Change background music mood", mapOf(
            "mood" to ToolParameter("string", "Mood name (peaceful, tense, battle, victory, mysterious, dark, epic)"),
            "intensity" to ToolParameter("number", "Intensity 0.0-1.0", required = false)
        ), behavior = ToolBehavior.NON_BLOCKING_SILENT),
    )
}

// ── Extension helpers for parsing tool call arguments ───────────────

private fun GeminiToolCall.stringArg(name: String): String? =
    arguments[name]?.jsonPrimitive?.contentOrNull

private fun GeminiToolCall.intArg(name: String): Int? =
    arguments[name]?.jsonPrimitive?.intOrNull

private fun GeminiToolCall.floatArg(name: String): Float? =
    arguments[name]?.jsonPrimitive?.floatOrNull
