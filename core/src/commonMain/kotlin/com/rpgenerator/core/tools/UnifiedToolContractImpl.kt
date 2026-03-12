package com.rpgenerator.core.tools

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.QuestStatus
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.agents.ClassGeneratorAgent
import com.rpgenerator.core.agents.ItemGeneratorAgent
import com.rpgenerator.core.agents.LocationGeneratorAgent
import com.rpgenerator.core.agents.MonsterGeneratorAgent
import com.rpgenerator.core.agents.SkillGeneratorAgent
import com.rpgenerator.core.generation.NPCArchetypeGenerator
import com.rpgenerator.core.rules.CombatEngine
import com.rpgenerator.core.rules.CombatEvent
import com.rpgenerator.core.rules.RoundResult
import com.rpgenerator.core.rules.RulesEngine
import com.rpgenerator.core.rules.SkillCheckDegree
import com.rpgenerator.core.rules.SkillCheckDifficulty
import com.rpgenerator.core.rules.SkillCheckType
import kotlin.random.Random
import com.rpgenerator.core.story.WorldSeeds
import com.rpgenerator.core.util.currentTimeMillis
import kotlinx.serialization.json.*

/**
 * Unified tool contract implementation.
 * Consolidates logic from GameToolsImpl, GeminiToolContractImpl, and McpHandler.
 *
 * All tools are stateless: state is passed in, mutated state returned in ToolOutcome.
 */
internal class UnifiedToolContractImpl(
    private val loreHandler: LoreQueryHandler = LoreQueryHandler(),
    private val rulesEngine: RulesEngine = RulesEngine(),
    private val bestiary: Bestiary? = IntegrationBestiary,
    private val combatEngine: CombatEngine = CombatEngine(bestiary = bestiary),
    private val locationManager: LocationManager = LocationManager(),
    private val monsterGenerator: MonsterGeneratorAgent? = null,
    private val npcGenerator: NPCArchetypeGenerator? = null,
    private val locationGenerator: LocationGeneratorAgent? = null,
    private val itemGenerator: ItemGeneratorAgent? = null,
    private val classGenerator: ClassGeneratorAgent? = null,
    private val skillGenerator: SkillGeneratorAgent? = null,
    private val enableImageGeneration: Boolean = false
) : UnifiedToolContract {

    // ── Tool Call Log ────────────────────────────────────────────────
    private val _toolCallLog = mutableListOf<ToolCallLogEntry>()
    private var _sequenceCounter = 0
    override var currentCaller: ToolCaller = ToolCaller.EXTERNAL_OTHER
    override var currentTurnNumber: Int = 0

    override val toolCallLog: List<ToolCallLogEntry> get() = _toolCallLog.toList()

    fun clearLog() { _toolCallLog.clear(); _sequenceCounter = 0 }

    override suspend fun executeTool(name: String, args: Map<String, Any?>, state: GameState): ToolOutcome {
        val startTime = currentTimeMillis()
        val outcome = try {
            when (name) {
                // ── State Queries ──────────────────────────────────
                "get_player_stats" -> getPlayerStats(state)
                "get_character_sheet" -> getCharacterSheet(state)
                "get_inventory" -> getInventory(state)
                "get_location" -> getLocation(state)
                "get_connected_locations" -> getConnectedLocations(state)
                "get_npcs_here" -> getNPCsHere(state)
                "get_active_quests" -> getActiveQuests(state)
                "get_quest_details" -> getQuestDetails(args.str("questId"), state)
                "find_npc" -> findNPC(args.str("name"), state)
                "get_npc_shop" -> getNPCShop(args.str("npcId"), state)
                "get_event_summary" -> getEventSummary(state)
                "get_combat_targets" -> getCombatTargets(state)
                "get_tutorial_state" -> getTutorialState(state)
                "get_story_state" -> getStoryState(state)
                "get_narration_context" -> getNarrationContext(state)

                // ── Lore Queries ──────────────────────────────────
                "query_lore" -> queryLore(args.str("category"), args.strOpt("filter"), state)

                // ── Combat Actions ────────────────────────────────
                "start_combat" -> startCombat(args.str("enemyName"), args.int("danger", 3), args.strOpt("description"), args.strOpt("zoneId"), state)
                "get_bestiary" -> getBestiary(state)
                "combat_attack" -> combatAttack(state)
                "combat_use_skill" -> combatUseSkill(args.str("skillId"), state)
                "combat_flee" -> combatFlee(state)
                "combat_use_item" -> combatUseItem(args.str("itemId"), state)
                "get_combat_status" -> getCombatStatus(state)
                "skill_check" -> performSkillCheck(args.str("checkType"), args.str("difficulty"), state)
                "attack_target" -> attackTargetLegacy(args.str("target"), args.int("danger", 3), state)
                "use_skill" -> useSkill(args.str("skillId"), args.strOpt("target"), state)

                // ── Movement ──────────────────────────────────────
                "move_to_location" -> moveToLocation(args.str("locationName"), state)

                // ── NPC Interaction ───────────────────────────────
                "talk_to_npc" -> talkToNPC(args.str("npcName"), args.strOpt("dialogue"), state)
                "purchase_from_shop" -> purchaseFromShop(args.str("npcId"), args.str("itemId"), args.int("quantity", 1), state)
                "sell_to_shop" -> sellToShop(args.str("npcId"), args.str("itemId"), args.int("quantity", 1), state)

                // ── Item Usage ────────────────────────────────────
                "use_item" -> useItem(args.str("itemId"), state)
                "equip_item" -> equipItem(args.str("itemId"), state)
                "add_item" -> addItem(args, state)
                "add_gold" -> addGold(args, state)

                // ── Quest Management ──────────────────────────────
                "accept_quest" -> acceptQuest(args.str("questId"), state)
                "update_quest_progress" -> updateQuestProgress(args.str("questId"), args.str("objectiveId"), args.int("progress", 1), state)
                "complete_quest" -> completeQuest(args.str("questId"), state)

                // ── Character Management ──────────────────────────
                "set_player_name" -> setPlayerName(args.str("name"), state)
                "set_backstory" -> setBackstory(args.str("backstory"), state)
                "set_class" -> setClass(
                    args.str("className"),
                    args.strOpt("description"),
                    args.strOpt("traits"),        // JSON array string or comma-separated
                    args.strOpt("evolutionHints"),
                    args.strOpt("physicalChanges"),
                    state
                )
                "suggest_classes" -> suggestClasses(state)
                "suggest_skills" -> suggestSkills(args.strOpt("context"), state)
                "set_profession" -> setProfession(args.str("professionName"), state)
                "add_xp" -> addXP(args.long("amount"), state)
                "allocate_stat_points" -> allocateStatPoints(
                    args.int("strength"), args.int("dexterity"), args.int("constitution"),
                    args.int("intelligence"), args.int("wisdom"), args.int("charisma"),
                    state
                )
                "grant_skill" -> grantSkill(args.str("skillId"), args.strOpt("skillName"), args.strOpt("description"), state)
                "complete_tutorial" -> completeTutorial(state)

                // ── Plot Graph ────────────────────────────────────
                "create_plot_node" -> createPlotNode(args.str("title"), args.str("description"), args.str("threadId"), args.str("beatType"), args.int("triggerLevel", state.playerLevel), state)
                "complete_plot_node" -> completePlotNode(args.str("nodeId"), state)
                "get_active_plot_nodes" -> getActivePlotNodes(state)

                // ── World Generation ──────────────────────────────
                "spawn_npc" -> spawnNPC(args.str("name"), args.str("role"), args.str("locationId"), state)
                "spawn_enemy" -> spawnEnemy(args.str("name"), args.int("danger", 1), state)

                // ── Player-facing Knowledge ─────────────────────
                "ask_world" -> askWorld(args.str("question"), state)
                "generate_character" -> generateCharacter(args.strOpt("style"), state)

                // ── Multimodal Triggers ───────────────────────────
                "generate_scene_art" -> generateSceneArt(args.str("description"), state)
                "shift_music_mood" -> shiftMusicMood(args.str("mood"), state)

                else -> ToolOutcome(success = false, error = "Unknown tool: $name")
            }
        } catch (e: Exception) {
            ToolOutcome(success = false, error = "Tool '$name' failed: ${e.message}")
        }

        // Log every tool call
        val elapsed = currentTimeMillis() - startTime
        val argsStr = args.mapValues { (_, v) ->
            when (v) {
                is String -> if (v.length > 200) v.take(200) + "..." else v
                null -> "null"
                else -> v.toString().let { if (it.length > 200) it.take(200) + "..." else it }
            }
        }
        val entry = ToolCallLogEntry(
            sequenceNumber = _sequenceCounter++,
            toolName = name,
            caller = currentCaller,
            args = argsStr,
            success = outcome.success,
            resultSummary = if (outcome.success) {
                outcome.data.toString().let { if (it.length > 500) it.take(500) + "..." else it }
            } else {
                outcome.error ?: "Unknown error"
            },
            error = outcome.error,
            elapsedMs = elapsed,
            stateChanged = outcome.newState != null,
            eventsEmitted = outcome.events.map { event ->
                when (event) {
                    is GameEvent.NarratorText -> "NarratorText(${event.text.take(60)})"
                    is GameEvent.NPCDialogue -> "NPCDialogue(${event.npcName}: ${event.text.take(60)})"
                    is GameEvent.SystemNotification -> "SystemNotification(${event.text.take(80)})"
                    is GameEvent.CombatLog -> "CombatLog(${event.text.take(60)})"
                    is GameEvent.QuestUpdate -> "QuestUpdate(${event.questName}: ${event.status})"
                    is GameEvent.StatChange -> "StatChange(${event.statName}: ${event.oldValue}→${event.newValue})"
                    is GameEvent.ItemGained -> "ItemGained(${event.itemName} x${event.quantity})"
                    is GameEvent.MusicChange -> "MusicChange(${event.mood})"
                    is GameEvent.SceneImage -> "SceneImage(${event.description.take(40)})"
                    is GameEvent.NPCPortrait -> "NPCPortrait(${event.npcName})"
                    is GameEvent.ItemIconGenerated -> "ItemIcon(${event.itemId})"
                    else -> event::class.simpleName ?: "Unknown"
                }
            },
            location = state.currentLocation.name,
            playerLevel = state.playerLevel,
            turnNumber = currentTurnNumber
        )
        _toolCallLog.add(entry)

        // Print to stdout for immediate visibility
        val status = if (outcome.success) "OK" else "FAIL"
        val argsShort = argsStr.entries.joinToString(", ") { "${it.key}=${it.value.take(50)}" }
        println("[ToolLog #${entry.sequenceNumber}] ${entry.caller} | $name($argsShort) → $status (${elapsed}ms)${if (outcome.error != null) " ERROR: ${outcome.error}" else ""}${if (outcome.newState != null) " [STATE CHANGED]" else ""}${if (outcome.events.isNotEmpty()) " events=${entry.eventsEmitted}" else ""}")

        return outcome
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATE QUERIES
    // ═══════════════════════════════════════════════════════════════════

    private fun getPlayerStats(state: GameState): ToolOutcome {
        val sheet = state.characterSheet
        val data = buildJsonObject {
            put("name", JsonPrimitive(state.playerName))
            put("level", JsonPrimitive(sheet.level))
            put("xp", JsonPrimitive(sheet.xp))
            put("xpToNextLevel", JsonPrimitive(sheet.xpToNextLevel()))
            put("hp", JsonPrimitive(sheet.resources.currentHP))
            put("maxHP", JsonPrimitive(sheet.resources.maxHP))
            put("mana", JsonPrimitive(sheet.resources.currentMana))
            put("maxMana", JsonPrimitive(sheet.resources.maxMana))
            put("energy", JsonPrimitive(sheet.resources.currentEnergy))
            put("maxEnergy", JsonPrimitive(sheet.resources.maxEnergy))
            put("location", JsonPrimitive(state.currentLocation.name))
            put("class", JsonPrimitive(sheet.playerClass.displayName))
            put("profession", JsonPrimitive(sheet.profession.displayName))
            put("grade", JsonPrimitive(sheet.currentGrade.displayName))
            put("isDead", JsonPrimitive(state.isDead))
            put("unspentStatPoints", JsonPrimitive(sheet.unspentStatPoints))
        }
        return ToolOutcome(success = true, data = data)
    }

    private fun getCharacterSheet(state: GameState): ToolOutcome {
        val sheet = state.characterSheet
        val effective = sheet.effectiveStats()
        val data = buildJsonObject {
            put("level", JsonPrimitive(sheet.level))
            put("xp", JsonPrimitive(sheet.xp))
            put("xpToNextLevel", JsonPrimitive(sheet.xpToNextLevel()))
            put("class", JsonPrimitive(sheet.dynamicClass?.name ?: sheet.playerClass.displayName))
            put("classDescription", JsonPrimitive(sheet.dynamicClass?.description ?: sheet.playerClass.description))
            if (sheet.dynamicClass?.traits?.isNotEmpty() == true) {
                putJsonArray("classTraits") {
                    for (trait in sheet.dynamicClass!!.traits) {
                        addJsonObject {
                            put("name", JsonPrimitive(trait.name))
                            put("description", JsonPrimitive(trait.description))
                            if (trait.mechanicalEffect.isNotBlank()) put("effect", JsonPrimitive(trait.mechanicalEffect))
                        }
                    }
                }
            }
            if (sheet.dynamicClass?.physicalMutations?.isNotEmpty() == true) {
                putJsonArray("physicalMutations") {
                    for (mut in sheet.dynamicClass!!.physicalMutations) add(JsonPrimitive(mut))
                }
            }
            if (sheet.dynamicClass?.evolutionHints?.isNotEmpty() == true) {
                putJsonArray("evolutionHints") {
                    for (hint in sheet.dynamicClass!!.evolutionHints) add(JsonPrimitive(hint))
                }
            }
            put("profession", JsonPrimitive(sheet.profession.displayName))
            put("grade", JsonPrimitive(sheet.currentGrade.displayName))
            putJsonObject("hp") {
                put("current", JsonPrimitive(sheet.resources.currentHP))
                put("max", JsonPrimitive(sheet.resources.maxHP))
            }
            putJsonObject("mana") {
                put("current", JsonPrimitive(sheet.resources.currentMana))
                put("max", JsonPrimitive(sheet.resources.maxMana))
            }
            putJsonObject("energy") {
                put("current", JsonPrimitive(sheet.resources.currentEnergy))
                put("max", JsonPrimitive(sheet.resources.maxEnergy))
            }
            putJsonObject("stats") {
                put("strength", JsonPrimitive(effective.strength))
                put("dexterity", JsonPrimitive(effective.dexterity))
                put("constitution", JsonPrimitive(effective.constitution))
                put("intelligence", JsonPrimitive(effective.intelligence))
                put("wisdom", JsonPrimitive(effective.wisdom))
                put("charisma", JsonPrimitive(effective.charisma))
                put("defense", JsonPrimitive(effective.defense))
            }
            putJsonArray("skills") {
                sheet.skills.forEach { skill ->
                    addJsonObject {
                        put("id", JsonPrimitive(skill.id))
                        put("name", JsonPrimitive(skill.name))
                        put("description", JsonPrimitive(skill.description))
                        put("level", JsonPrimitive(skill.level))
                        put("manaCost", JsonPrimitive(skill.manaCost))
                        put("energyCost", JsonPrimitive(skill.energyCost))
                        put("ready", JsonPrimitive(skill.isReady()))
                    }
                }
            }
            putJsonObject("equipment") {
                put("weapon", JsonPrimitive(sheet.equipment.weapon?.name ?: "None"))
                put("armor", JsonPrimitive(sheet.equipment.armor?.name ?: "None"))
                put("accessory", JsonPrimitive(sheet.equipment.accessory?.name ?: "None"))
            }
            putJsonArray("statusEffects") {
                sheet.statusEffects.forEach { effect ->
                    addJsonObject {
                        put("name", JsonPrimitive(effect.name))
                        put("turnsRemaining", JsonPrimitive(effect.duration))
                    }
                }
            }
            put("unspentStatPoints", JsonPrimitive(sheet.unspentStatPoints))
        }
        return ToolOutcome(success = true, data = data)
    }

    private fun getInventory(state: GameState): ToolOutcome {
        val inventory = state.characterSheet.inventory
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
        return ToolOutcome(success = true, data = data)
    }

    private fun getLocation(state: GameState): ToolOutcome {
        val loc = state.currentLocation
        val data = buildJsonObject {
            put("id", JsonPrimitive(loc.id))
            put("name", JsonPrimitive(loc.name))
            put("description", JsonPrimitive(loc.description))
            put("biome", JsonPrimitive(loc.biome.name))
            put("danger", JsonPrimitive(loc.danger))
            putJsonArray("features") { loc.features.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("connections") { loc.connections.forEach { add(JsonPrimitive(it)) } }
            put("lore", JsonPrimitive(loc.lore))
        }
        return ToolOutcome(success = true, data = data)
    }

    private fun getConnectedLocations(state: GameState): ToolOutcome {
        val connected = state.currentLocation.connections.mapNotNull { connectionId ->
            locationManager.getLocation(connectionId, state)
        }
        val data = buildJsonObject {
            putJsonArray("locations") {
                connected.forEach { loc ->
                    addJsonObject {
                        put("id", JsonPrimitive(loc.id))
                        put("name", JsonPrimitive(loc.name))
                        put("biome", JsonPrimitive(loc.biome.name))
                        put("danger", JsonPrimitive(loc.danger))
                    }
                }
            }
        }
        return ToolOutcome(success = true, data = data)
    }

    private fun getNPCsHere(state: GameState): ToolOutcome {
        val npcs = state.getNPCsAtCurrentLocation()
        val data = buildJsonObject {
            putJsonArray("npcs") {
                npcs.forEach { npc ->
                    addJsonObject {
                        put("id", JsonPrimitive(npc.id))
                        put("name", JsonPrimitive(npc.name))
                        put("archetype", JsonPrimitive(npc.archetype.name))
                        put("hasShop", JsonPrimitive(npc.shop != null))
                        put("hasQuests", JsonPrimitive(npc.questIds.isNotEmpty()))
                        put("personality", JsonPrimitive(npc.personality.traits.joinToString(", ")))
                    }
                }
            }
        }
        return ToolOutcome(success = true, data = data)
    }

    private fun getActiveQuests(state: GameState): ToolOutcome {
        val data = buildJsonObject {
            putJsonArray("quests") {
                state.activeQuests.values.forEach { quest ->
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
        return ToolOutcome(success = true, data = data)
    }

    private fun getQuestDetails(questId: String, state: GameState): ToolOutcome {
        val quest = state.activeQuests[questId]
            ?: return ToolOutcome(success = false, error = "Quest '$questId' not found")

        val data = buildJsonObject {
            put("id", JsonPrimitive(quest.id))
            put("name", JsonPrimitive(quest.name))
            put("description", JsonPrimitive(quest.description))
            put("type", JsonPrimitive(quest.type.name))
            put("status", JsonPrimitive(quest.status.name))
            put("canComplete", JsonPrimitive(quest.isComplete()))
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
            putJsonObject("rewards") {
                put("xp", JsonPrimitive(quest.rewards.xp))
                put("gold", JsonPrimitive(quest.rewards.gold))
                putJsonArray("items") { quest.rewards.items.forEach { add(JsonPrimitive(it.name)) } }
            }
        }
        return ToolOutcome(success = true, data = data)
    }

    private fun findNPC(name: String, state: GameState): ToolOutcome {
        val npc = state.findNPCByName(name)
            ?: return ToolOutcome(success = false, error = "NPC '$name' not found at current location")

        val data = buildJsonObject {
            put("id", JsonPrimitive(npc.id))
            put("name", JsonPrimitive(npc.name))
            put("archetype", JsonPrimitive(npc.archetype.name))
            put("personality", JsonPrimitive(npc.personality.traits.joinToString(", ")))
            put("speechPattern", JsonPrimitive(npc.personality.speechPattern))
            put("hasShop", JsonPrimitive(npc.shop != null))
            put("hasQuests", JsonPrimitive(npc.questIds.isNotEmpty()))
            put("lore", JsonPrimitive(npc.lore))
        }
        return ToolOutcome(success = true, data = data)
    }

    private fun getNPCShop(npcId: String, state: GameState): ToolOutcome {
        val npc = state.findNPC(npcId)
            ?: return ToolOutcome(success = false, error = "NPC not found")
        val shop = npc.shop
            ?: return ToolOutcome(success = false, error = "${npc.name} doesn't have a shop")

        val data = buildJsonObject {
            put("shopName", JsonPrimitive(shop.name))
            put("npcName", JsonPrimitive(npc.name))
            put("currency", JsonPrimitive(shop.currency))
            putJsonArray("items") {
                shop.inventory.forEach { item ->
                    addJsonObject {
                        put("id", JsonPrimitive(item.id))
                        put("name", JsonPrimitive(item.name))
                        put("description", JsonPrimitive(item.description))
                        put("price", JsonPrimitive(item.price))
                        put("stock", JsonPrimitive(item.stock))
                    }
                }
            }
        }
        return ToolOutcome(success = true, data = data)
    }

    private fun getEventSummary(state: GameState): ToolOutcome {
        val data = buildJsonObject {
            put("gameId", JsonPrimitive(state.gameId))
            put("playerLevel", JsonPrimitive(state.playerLevel))
            put("location", JsonPrimitive(state.currentLocation.name))
            put("activeQuestCount", JsonPrimitive(state.activeQuests.size))
            put("completedQuestCount", JsonPrimitive(state.completedQuests.size))
            put("deathCount", JsonPrimitive(state.deathCount))
        }
        return ToolOutcome(success = true, data = data)
    }

    private fun getCombatTargets(state: GameState): ToolOutcome {
        val data = buildJsonObject {
            put("locationDanger", JsonPrimitive(state.currentLocation.danger))
            put("playerLevel", JsonPrimitive(state.playerLevel))
            putJsonArray("features") {
                state.currentLocation.features.forEach { add(JsonPrimitive(it)) }
            }
        }
        return ToolOutcome(success = true, data = data)
    }

    private fun getTutorialState(state: GameState): ToolOutcome {
        val hasClass = state.characterSheet.playerClass != PlayerClass.NONE
        val data = buildJsonObject {
            put("hasClass", JsonPrimitive(hasClass))
            put("className", JsonPrimitive(state.characterSheet.playerClass.displayName))
            put("hasOpeningNarration", JsonPrimitive(state.hasOpeningNarrationPlayed))
            put("playerLevel", JsonPrimitive(state.playerLevel))
        }
        return ToolOutcome(success = true, data = data)
    }

    private fun getStoryState(state: GameState): ToolOutcome {
        val data = buildJsonObject {
            put("seedId", JsonPrimitive(state.seedId ?: "none"))
            put("systemType", JsonPrimitive(state.systemType.name))
            put("backstory", JsonPrimitive(state.backstory))
            put("playerName", JsonPrimitive(state.playerName))
            put("deathCount", JsonPrimitive(state.deathCount))
        }
        return ToolOutcome(success = true, data = data)
    }

    private fun getNarrationContext(state: GameState): ToolOutcome {
        val seed = state.seedId?.let { WorldSeeds.byId(it) }
        val npcsHere = state.getNPCsAtCurrentLocation()
        val quests = state.activeQuests.values.toList()

        val data = buildJsonObject {
            // ── Writing voice for this scene ──
            put("voice", JsonPrimitive(buildString {
                if (seed != null) {
                    append("Write like: ${seed.inspirations.joinToString(", ")}. ")
                    append("Tone: ${seed.tone.joinToString(", ")}. ")
                    append("The System speaks with ${seed.systemVoice.personality.lowercase()}.")
                } else {
                    append("Tone: ${state.worldSettings.themes.joinToString(", ") { it.name.lowercase().replace('_', ' ') }}.")
                }
            }))

            // ── Writing craft ──
            put("craft", JsonPrimitive(buildString {
                append("You're a pro LitRPG author — ten books published, editor cuts anything that doesn't earn its place. ")
                append("Tell it like you're recounting it to a friend over drinks. ")
                append("Short sentences hit harder. One sharp detail beats three vague ones. ")
                append("'The knife stuck in the doorframe' not 'the room showed signs of violence.' ")
                append("Show the body, not the feeling. Clenched jaw, not 'anger filled you.' ")
                append("Dialogue does the heavy lifting — people reveal themselves through HOW they talk and what they don't say. ")
                append("End on momentum.")
            }))

            // ── What NOT to do ──
            put("avoid", JsonPrimitive(
                "No purple prose. No stacked adjectives. No restating backstory. " +
                "No option lists. No 'What do you do?' No invented stats — use tool results. " +
                "No narrating emotions — show the body instead."
            ))

            // ── Location feel ──
            val loc = state.currentLocation
            put("scene", JsonPrimitive(buildString {
                append("${loc.name} (${loc.biome.name.lowercase().replace('_', ' ')}, danger ${loc.danger}/10). ")
                if (loc.features.isNotEmpty()) {
                    append("Notable: ${loc.features.joinToString(", ")}. ")
                }
                if (loc.lore.isNotBlank()) append(loc.lore)
            }))

            // ── NPCs present — how they talk and act ──
            if (npcsHere.isNotEmpty()) {
                putJsonArray("npcs_here") {
                    npcsHere.forEach { npc ->
                        addJsonObject {
                            put("name", JsonPrimitive(npc.name))
                            put("archetype", JsonPrimitive(npc.archetype.name.lowercase()))
                            put("speech", JsonPrimitive(npc.personality.speechPattern))
                            put("personality", JsonPrimitive(npc.personality.traits.joinToString(", ")))
                            put("motivations", JsonPrimitive(npc.personality.motivations.joinToString(", ")))
                            if (npc.lore.isNotBlank()) {
                                put("backstory", JsonPrimitive(npc.lore))
                            }
                            // Relationship with player
                            val rel = npc.getRelationship(state.gameId)
                            if (rel != null) {
                                put("attitude", JsonPrimitive("${rel.getStatus().name.lowercase()} (affinity ${rel.affinity})"))
                            }
                            // Recent conversation — so the GM doesn't repeat itself
                            val recent = npc.getRecentConversations(2)
                            if (recent.isNotEmpty()) {
                                put("last_said", JsonPrimitive(recent.last().npcResponse))
                            }
                            put("voice_tip", JsonPrimitive(buildString {
                                append("Voice this NPC through their speech pattern: '${npc.personality.speechPattern}'. ")
                                append("Their traits (${npc.personality.traits.joinToString(", ")}) should come through in HOW they talk, not from you describing them. ")
                                append("Don't tag dialogue with 'they said gruffly' — write gruff dialogue instead.")
                            }))
                        }
                    }
                }
            }

            // ── Player context for this moment ──
            put("player", JsonPrimitive(buildString {
                append("${state.playerName}")
                val cls = state.characterSheet.playerClass
                if (cls != PlayerClass.NONE) append(", ${cls.displayName}")
                append(", level ${state.playerLevel}. ")
                if (state.backstory.isNotBlank()) {
                    // One-line backstory reminder — just the emotional core
                    val core = state.backstory.split(".").firstOrNull()?.trim() ?: state.backstory
                    append("Core identity: $core. ")
                    append("Weave this in only when it fits naturally — a gesture, a reflex, a memory triggered by the moment. Don't force it.")
                }
            }))

            // ── Active narrative threads ──
            if (quests.isNotEmpty()) {
                put("narrative_threads", JsonPrimitive(
                    quests.joinToString("; ") { q ->
                        "${q.name}: ${q.description}"
                    }
                ))
            }

            // ── Seed-specific scene energy ──
            if (seed != null) {
                put("scene_energy", JsonPrimitive(buildString {
                    append("Themes to echo: ${seed.themes.joinToString(", ")}. ")
                    if (state.deathCount > 0) {
                        append("Death count: ${state.deathCount} — this matters narratively. ")
                    }
                    val hp = state.characterSheet.resources
                    if (hp.currentHP < hp.maxHP / 3) {
                        append("Player is badly hurt (${hp.currentHP}/${hp.maxHP} HP) — the world should feel it. ")
                    }
                    append(seed.systemVoice.attitudeTowardPlayer)
                }))
            }
        }

        return ToolOutcome(success = true, data = data)
    }

    // ═══════════════════════════════════════════════════════════════════
    // LORE QUERIES
    // ═══════════════════════════════════════════════════════════════════

    private fun queryLore(category: String, filter: String?, state: GameState): ToolOutcome {
        val data = loreHandler.queryLore(category, filter, state)
        return ToolOutcome(success = true, data = data)
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMBAT ACTIONS
    // ═══════════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════════
    // COMBAT — MULTI-ROUND SYSTEM
    // ═══════════════════════════════════════════════════════════════════

    private suspend fun startCombat(enemyName: String, danger: Int, description: String?, zoneId: String?, state: GameState): ToolOutcome {
        if (state.inCombat) {
            return ToolOutcome(success = false, error = "Already in combat with ${state.combatState!!.enemy.name}. Resolve current combat first.")
        }
        if (enemyName.isBlank()) {
            return ToolOutcome(success = false, error = "Enemy name cannot be blank")
        }

        // For non-bestiary enemies, try AI generation for rich enemy data + portrait prompt
        val template = bestiary?.findEnemy(enemyName, zoneId ?: state.currentLocation.id)
        val monsterResult = if (template == null && monsterGenerator != null) {
            try { monsterGenerator.generate(enemyName, danger, state) } catch (_: Exception) { null }
        } else null

        val (newState, enemy) = combatEngine.spawnEnemy(enemyName, danger, state, zoneId, monsterResult)
        val events = listOf(
            GameEvent.CombatLog("${enemy.name} appears! [HP: ${enemy.currentHP}/${enemy.maxHP}, Danger: ${enemy.danger}]")
        )

        val data = buildJsonObject {
            put("enemy", JsonPrimitive(enemy.name))
            put("enemyHP", JsonPrimitive(enemy.maxHP))
            put("enemyDanger", JsonPrimitive(enemy.danger))
            put("enemyAttack", JsonPrimitive(enemy.attack))
            put("enemyDefense", JsonPrimitive(enemy.defense))
            put("enemySpeed", JsonPrimitive(enemy.speed))
            if (enemy.description.isNotBlank()) put("description", JsonPrimitive(enemy.description))
            if (enemy.visualDescription.isNotBlank()) put("visualDescription", JsonPrimitive(enemy.visualDescription))
            enemy.portraitResource?.let { put("portraitResource", JsonPrimitive(it)) }
            if (enemy.immunities.isNotEmpty()) putJsonArray("immunities") {
                enemy.immunities.forEach { add(JsonPrimitive(it.name)) }
            }
            if (enemy.vulnerabilities.isNotEmpty()) putJsonArray("vulnerabilities") {
                enemy.vulnerabilities.forEach { add(JsonPrimitive(it.name)) }
            }
            if (enemy.resistances.isNotEmpty()) putJsonArray("resistances") {
                enemy.resistances.forEach { add(JsonPrimitive(it.name)) }
            }
            putJsonArray("abilities") {
                enemy.abilities.forEach { ab ->
                    add(buildJsonObject {
                        put("name", JsonPrimitive(ab.name))
                        put("damage", JsonPrimitive(ab.damage))
                        put("cooldown", JsonPrimitive(ab.cooldown))
                        put("damageType", JsonPrimitive(ab.damageType.name))
                    })
                }
            }
            put("playerGoesFirst", JsonPrimitive(newState.combatState!!.playerInitiative))
            put("playerHP", JsonPrimitive(newState.characterSheet.resources.currentHP))
            put("playerMaxHP", JsonPrimitive(newState.characterSheet.resources.maxHP))
            putJsonArray("readySkills") {
                newState.characterSheet.getReadySkills().forEach { skill ->
                    add(buildJsonObject {
                        put("id", JsonPrimitive(skill.id))
                        put("name", JsonPrimitive(skill.name))
                        put("manaCost", JsonPrimitive(skill.manaCost))
                        put("energyCost", JsonPrimitive(skill.energyCost))
                    })
                }
            }
            // Visual prompt for server-side portrait generation (non-bestiary enemies only)
            if (enemy.portraitResource == null && monsterResult?.visualPrompt?.isNotBlank() == true) {
                put("visualPrompt", JsonPrimitive(monsterResult.visualPrompt))
                put("imageType", JsonPrimitive("portrait"))
            }
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = events)
    }

    private fun combatAttack(state: GameState): ToolOutcome {
        val result = combatEngine.resolveAttack(state)
        return roundResultToOutcome(result)
    }

    private fun combatUseSkill(skillId: String, state: GameState): ToolOutcome {
        val skill = state.characterSheet.skills.find { it.id == skillId }
            ?: return ToolOutcome(success = false, error = "Skill '$skillId' not found")

        if (!state.characterSheet.canUseSkill(skill)) {
            return ToolOutcome(success = false, error = "Cannot use '${skill.name}': insufficient resources or on cooldown")
        }

        val result = combatEngine.resolveSkillUse(skill, state)
        return roundResultToOutcome(result)
    }

    private fun combatFlee(state: GameState): ToolOutcome {
        val result = combatEngine.resolveFlee(state)
        return roundResultToOutcome(result)
    }

    /**
     * Use a consumable item during combat without losing your turn.
     * Still costs your action — enemy gets to attack.
     */
    private fun combatUseItem(itemId: String, state: GameState): ToolOutcome {
        val combat = state.combatState
            ?: return ToolOutcome(success = false, error = "Not in combat. Use use_item outside of combat.")
        if (combat.isOver) return ToolOutcome(success = false, error = "Combat is already over")

        // Use the item (validates existence, consumable type, applies effect)
        val itemResult = useItem(itemId, state)
        if (!itemResult.success) return itemResult

        val updatedState = itemResult.newState ?: state
        val events = itemResult.events.toMutableList()

        // Enemy gets a free attack while you use the item
        val stats = updatedState.characterSheet.effectiveStats()
        val enemy = combat.enemy
        if (!enemy.isStunned) {
            val isSlowed = enemy.statusEffects.any { it.type == EnemyEffectType.SLOWED }
            val effectiveSpeed = if (isSlowed) enemy.speed / 2 else enemy.speed
            val hitChance = (65 + (effectiveSpeed - stats.dexterity) * 2).coerceIn(15, 85)
            if (Random.nextInt(100) < hitChance) {
                val baseDamage = (Random.nextInt(1, 4) + enemy.attack)
                val damageTaken = (baseDamage - stats.defense / 2 - stats.constitution / 4).coerceAtLeast(1)
                val finalState = updatedState.takeDamage(damageTaken)
                events.add(GameEvent.CombatLog("Enemy strikes for $damageTaken damage while you use ${itemResult.data["itemName"]?.jsonPrimitive?.contentOrNull ?: "item"}! [HP: ${finalState.characterSheet.resources.currentHP}]"))

                val newCombat = combat.nextRound()
                val stateWithCombat = finalState.copy(combatState = newCombat)
                return ToolOutcome(
                    success = true,
                    data = buildJsonObject {
                        put("itemUsed", itemResult.data["itemName"] ?: JsonPrimitive(itemId))
                        put("effect", itemResult.data["effect"] ?: JsonPrimitive("applied"))
                        put("enemyAttacked", JsonPrimitive(true))
                        put("enemyDamage", JsonPrimitive(damageTaken))
                        put("playerHP", JsonPrimitive(stateWithCombat.characterSheet.resources.currentHP))
                        put("playerMaxHP", JsonPrimitive(stateWithCombat.characterSheet.resources.maxHP))
                        put("combatOver", JsonPrimitive(false))
                    },
                    newState = stateWithCombat,
                    events = events
                )
            } else {
                events.add(GameEvent.CombatLog("Enemy's attack misses while you use your item!"))
            }
        }

        val newCombat = combat.nextRound()
        val stateWithCombat = updatedState.copy(combatState = newCombat)
        return ToolOutcome(
            success = true,
            data = buildJsonObject {
                put("itemUsed", itemResult.data["itemName"] ?: JsonPrimitive(itemId))
                put("effect", itemResult.data["effect"] ?: JsonPrimitive("applied"))
                put("enemyAttacked", JsonPrimitive(false))
                put("playerHP", JsonPrimitive(stateWithCombat.characterSheet.resources.currentHP))
                put("playerMaxHP", JsonPrimitive(stateWithCombat.characterSheet.resources.maxHP))
                put("combatOver", JsonPrimitive(false))
            },
            newState = stateWithCombat,
            events = events
        )
    }

    /**
     * Perform a non-combat skill check (d20 + stat modifier + proficiency vs DC).
     * Used for: Investigation, Perception, Persuasion, Stealth, etc.
     */
    private fun performSkillCheck(checkTypeStr: String, difficultyStr: String, state: GameState): ToolOutcome {
        val checkType = try {
            SkillCheckType.valueOf(checkTypeStr.uppercase().replace(" ", "_"))
        } catch (_: Exception) {
            // Try matching by display name
            SkillCheckType.entries.find { it.displayName.equals(checkTypeStr, ignoreCase = true) }
                ?: return ToolOutcome(success = false, error = "Unknown check type: $checkTypeStr. Available: ${SkillCheckType.entries.joinToString(", ") { it.displayName }}")
        }

        val difficulty = try {
            SkillCheckDifficulty.valueOf(difficultyStr.uppercase().replace(" ", "_"))
        } catch (_: Exception) {
            SkillCheckDifficulty.entries.find { it.displayName.equals(difficultyStr, ignoreCase = true) }
                ?: SkillCheckDifficulty.MODERATE // default to moderate if unparseable
        }

        val result = rulesEngine.skillCheck(checkType, difficulty, state)

        val degreeText = when (result.degree) {
            SkillCheckDegree.CRITICAL_SUCCESS -> "CRITICAL SUCCESS (Natural 20!)"
            SkillCheckDegree.GREAT_SUCCESS -> "GREAT SUCCESS (beat DC by ${result.margin})"
            SkillCheckDegree.SUCCESS -> "SUCCESS (beat DC by ${result.margin})"
            SkillCheckDegree.FAILURE -> "FAILURE (missed DC by ${-result.margin})"
            SkillCheckDegree.BAD_FAILURE -> "BAD FAILURE (missed DC by ${-result.margin})"
            SkillCheckDegree.CRITICAL_FAILURE -> "CRITICAL FAILURE (Natural 1!)"
        }

        val events = listOf(
            GameEvent.SystemNotification("[${checkType.displayName} Check] d20(${result.roll}) + ${result.modifier} mod + ${result.proficiency} prof = ${result.total} vs DC ${result.dc}: $degreeText")
        )

        val data = buildJsonObject {
            put("checkType", JsonPrimitive(checkType.displayName))
            put("roll", JsonPrimitive(result.roll))
            put("modifier", JsonPrimitive(result.modifier))
            put("proficiency", JsonPrimitive(result.proficiency))
            put("total", JsonPrimitive(result.total))
            put("dc", JsonPrimitive(result.dc))
            put("success", JsonPrimitive(result.success))
            put("degree", JsonPrimitive(result.degree.name))
            put("degreeText", JsonPrimitive(degreeText))
            put("margin", JsonPrimitive(result.margin))
        }

        return ToolOutcome(success = true, data = data, events = events)
    }

    private fun getCombatStatus(state: GameState): ToolOutcome {
        val combat = state.combatState
            ?: return ToolOutcome(success = true, data = buildJsonObject {
                put("inCombat", JsonPrimitive(false))
            })

        val data = buildJsonObject {
            put("inCombat", JsonPrimitive(true))
            put("enemy", JsonPrimitive(combat.enemy.name))
            put("enemyHP", JsonPrimitive(combat.enemy.currentHP))
            put("enemyMaxHP", JsonPrimitive(combat.enemy.maxHP))
            put("enemyCondition", JsonPrimitive(combat.enemy.condition))
            combat.enemy.portraitResource?.let { put("portraitResource", JsonPrimitive(it)) }
            if (combat.enemy.immunities.isNotEmpty()) putJsonArray("immunities") {
                combat.enemy.immunities.forEach { add(JsonPrimitive(it.name)) }
            }
            if (combat.enemy.vulnerabilities.isNotEmpty()) putJsonArray("vulnerabilities") {
                combat.enemy.vulnerabilities.forEach { add(JsonPrimitive(it.name)) }
            }
            if (combat.enemy.resistances.isNotEmpty()) putJsonArray("resistances") {
                combat.enemy.resistances.forEach { add(JsonPrimitive(it.name)) }
            }
            putJsonArray("enemyStatusEffects") {
                combat.enemy.statusEffects.forEach { e ->
                    add(buildJsonObject {
                        put("type", JsonPrimitive(e.type.name))
                        put("turns", JsonPrimitive(e.remainingTurns))
                    })
                }
            }
            put("roundNumber", JsonPrimitive(combat.roundNumber))
            put("playerHP", JsonPrimitive(state.characterSheet.resources.currentHP))
            put("playerMaxHP", JsonPrimitive(state.characterSheet.resources.maxHP))
            put("playerMana", JsonPrimitive(state.characterSheet.resources.currentMana))
            put("playerEnergy", JsonPrimitive(state.characterSheet.resources.currentEnergy))
            putJsonArray("readySkills") {
                state.characterSheet.getReadySkills().forEach { skill ->
                    add(buildJsonObject {
                        put("id", JsonPrimitive(skill.id))
                        put("name", JsonPrimitive(skill.name))
                        put("manaCost", JsonPrimitive(skill.manaCost))
                        put("energyCost", JsonPrimitive(skill.energyCost))
                    })
                }
            }
        }
        return ToolOutcome(success = true, data = data)
    }

    /**
     * Legacy attack_target — auto-starts combat and resolves one round.
     * Kept for backward compat with existing GM prompts; starts + attacks in one call.
     */
    private fun attackTargetLegacy(target: String, danger: Int, state: GameState): ToolOutcome {
        // If not in combat, start combat first
        var currentState = state
        if (!currentState.inCombat) {
            val (newState, _) = combatEngine.spawnEnemy(target, danger, currentState)
            currentState = newState
        }
        // Then resolve one attack round
        val result = combatEngine.resolveAttack(currentState)
        return roundResultToOutcome(result)
    }

    /**
     * Convert CombatEngine RoundResult into a ToolOutcome with GameEvents and JSON data.
     */
    private fun roundResultToOutcome(result: RoundResult): ToolOutcome {
        if (!result.success) {
            return ToolOutcome(success = false, error = result.error)
        }

        val gameEvents = mutableListOf<GameEvent>()
        val narrativeLines = mutableListOf<String>()
        var newState = result.newState!!

        // Convert combat events to game events + narrative hints
        for (event in result.events) {
            when (event) {
                is CombatEvent.PlayerHit -> {
                    val critText = if (event.crit) " CRITICAL HIT!" else ""
                    gameEvents.add(GameEvent.CombatLog("You deal ${event.damage} damage!$critText"))
                    narrativeLines.add(if (event.crit) "player_crit:${event.damage}" else "player_hit:${event.damage}")
                }
                is CombatEvent.PlayerMiss -> {
                    gameEvents.add(GameEvent.CombatLog("Your attack misses!"))
                    narrativeLines.add("player_miss")
                }
                is CombatEvent.SkillDamage -> {
                    gameEvents.add(GameEvent.CombatLog("${event.skillName} deals ${event.damage} damage!"))
                    narrativeLines.add("skill_hit:${event.skillName}:${event.damage}")
                }
                is CombatEvent.SkillHeal -> {
                    gameEvents.add(GameEvent.CombatLog("${event.skillName} heals for ${event.amount}!"))
                    narrativeLines.add("skill_heal:${event.skillName}:${event.amount}")
                }
                is CombatEvent.SkillBuff -> {
                    gameEvents.add(GameEvent.SystemNotification("${event.skillName}: +${event.amount} ${event.stat} for ${event.duration} turns"))
                    narrativeLines.add("buff:${event.skillName}")
                }
                is CombatEvent.SkillDebuff -> {
                    gameEvents.add(GameEvent.SystemNotification("${event.skillName}: ${event.effect} applied for ${event.duration} turns"))
                    narrativeLines.add("debuff:${event.skillName}:${event.effect}")
                }
                is CombatEvent.SkillDot -> {
                    gameEvents.add(GameEvent.CombatLog("${event.skillName}: ${event.damageType} ${event.perTick}/turn for ${event.duration} turns"))
                    narrativeLines.add("dot:${event.damageType}")
                }
                is CombatEvent.SkillEffect -> {
                    gameEvents.add(GameEvent.SystemNotification("${event.skillName}: ${event.description}"))
                }
                is CombatEvent.DotTick -> {
                    gameEvents.add(GameEvent.CombatLog("Status effects deal ${event.damage} damage"))
                    narrativeLines.add("dot_tick:${event.damage}")
                }
                is CombatEvent.EnemyHit -> {
                    gameEvents.add(GameEvent.CombatLog("Enemy strikes for ${event.damage} damage! [HP: ${event.playerHP}]"))
                    narrativeLines.add("enemy_hit:${event.damage}")
                }
                is CombatEvent.EnemyMiss -> {
                    gameEvents.add(GameEvent.CombatLog("Enemy's attack misses!"))
                    narrativeLines.add("enemy_miss")
                }
                is CombatEvent.EnemyAbility -> {
                    gameEvents.add(GameEvent.CombatLog("Enemy uses ${event.abilityName} for ${event.damage} damage!"))
                    narrativeLines.add("enemy_ability:${event.abilityName}:${event.damage}")
                }
                is CombatEvent.EnemyStunned -> {
                    gameEvents.add(GameEvent.CombatLog("Enemy is stunned and cannot act!"))
                    narrativeLines.add("enemy_stunned")
                }
                is CombatEvent.EnemyDefeated -> {
                    gameEvents.add(GameEvent.CombatLog("${event.enemyName} is defeated!"))
                    narrativeLines.add("enemy_defeated:${event.enemyName}")
                }
                is CombatEvent.FleeSuccess -> {
                    gameEvents.add(GameEvent.CombatLog("You escape from combat!"))
                    narrativeLines.add("flee_success")
                }
                is CombatEvent.FleeFailed -> {
                    gameEvents.add(GameEvent.CombatLog("You fail to escape!"))
                    narrativeLines.add("flee_failed")
                }
                is CombatEvent.LevelUp -> {
                    gameEvents.add(GameEvent.SystemNotification(
                        "Level up! Now level ${event.newLevel}. " +
                        "STR ${event.oldStats.strength}→${event.newStats.strength}, " +
                        "DEX ${event.oldStats.dexterity}→${event.newStats.dexterity}, " +
                        "CON ${event.oldStats.constitution}→${event.newStats.constitution}, " +
                        "INT ${event.oldStats.intelligence}→${event.newStats.intelligence}, " +
                        "WIS ${event.oldStats.wisdom}→${event.newStats.wisdom}, " +
                        "CHA ${event.oldStats.charisma}→${event.newStats.charisma}"
                    ))
                }
            }
        }

        // Generate loot on victory
        if (result.victory) {
            val lootTable = rulesEngine.determineLootTablePublic(result.lootTier, newState.currentLocation.danger)
            val luckMod = rulesEngine.calculateLuckModifierPublic(newState)
            val lootResult = lootTable.rollLoot(newState.playerLevel, newState.currentLocation.danger, luckMod)
            val itemGenerator = com.rpgenerator.core.loot.ItemGenerator()
            val generatedItems = itemGenerator.generateLoot(lootResult.items, newState.playerLevel)

            generatedItems.forEach { generatedItem ->
                val itemName = generatedItem.getName()
                val itemId = "loot_${itemName.lowercase().replace(" ", "_")}_${currentTimeMillis()}"
                val qty = generatedItem.getQuantity()
                val apiRarity = when (generatedItem.rarity) {
                    com.rpgenerator.core.loot.ItemRarity.COMMON -> com.rpgenerator.core.api.ItemRarity.COMMON
                    com.rpgenerator.core.loot.ItemRarity.UNCOMMON -> com.rpgenerator.core.api.ItemRarity.UNCOMMON
                    com.rpgenerator.core.loot.ItemRarity.RARE -> com.rpgenerator.core.api.ItemRarity.RARE
                    com.rpgenerator.core.loot.ItemRarity.EPIC -> com.rpgenerator.core.api.ItemRarity.EPIC
                    com.rpgenerator.core.loot.ItemRarity.LEGENDARY -> com.rpgenerator.core.api.ItemRarity.LEGENDARY
                }
                // Determine item type: potions/consumables should be CONSUMABLE, not MISC
                val itemType = inferItemType(itemName)
                val inventoryItem = InventoryItem(
                    id = itemId, name = itemName, description = "Battle loot",
                    type = itemType, quantity = qty, rarity = apiRarity
                )
                newState = newState.addItem(inventoryItem)
                gameEvents.add(GameEvent.ItemGained(itemId, itemName, qty))
            }

            if (lootResult.gold > 0) {
                val goldItemId = "currency_gold"
                val existingGold = newState.characterSheet.inventory.items[goldItemId]
                val newQty = (existingGold?.quantity ?: 0) + lootResult.gold
                val goldItem = InventoryItem(
                    id = goldItemId, name = "Gold", description = "Currency",
                    type = ItemType.MISC, quantity = newQty
                )
                val newInventory = newState.characterSheet.inventory.copy(
                    items = newState.characterSheet.inventory.items + (goldItemId to goldItem)
                )
                newState = newState.copy(characterSheet = newState.characterSheet.copy(inventory = newInventory))
                gameEvents.add(GameEvent.SystemNotification("Gained ${lootResult.gold} gold"))
            }
        }

        val data = buildJsonObject {
            put("roundNumber", JsonPrimitive(result.roundNumber))
            put("combatOver", JsonPrimitive(result.combatOver))
            put("victory", JsonPrimitive(result.victory))
            put("fled", JsonPrimitive(result.fled))
            put("playerDied", JsonPrimitive(result.playerDied))
            put("playerHP", JsonPrimitive(result.playerHP))
            put("playerMaxHP", JsonPrimitive(result.playerMaxHP))
            put("playerMana", JsonPrimitive(newState.characterSheet.resources.currentMana))
            put("playerMaxMana", JsonPrimitive(newState.characterSheet.resources.maxMana))
            put("playerEnergy", JsonPrimitive(newState.characterSheet.resources.currentEnergy))
            put("playerMaxEnergy", JsonPrimitive(newState.characterSheet.resources.maxEnergy))
            put("enemyHP", JsonPrimitive(result.enemyHP))
            put("enemyMaxHP", JsonPrimitive(result.enemyMaxHP))
            put("enemyCondition", JsonPrimitive(result.enemyCondition))
            if (result.xpAwarded > 0) put("xpAwarded", JsonPrimitive(result.xpAwarded))
            if (result.levelUp) put("levelUp", JsonPrimitive(true))
            if (result.newLevel > 0) put("newLevel", JsonPrimitive(result.newLevel))
            putJsonArray("narrative") {
                narrativeLines.forEach { add(JsonPrimitive(it)) }
            }
            // Include skill cooldown state so GM knows what's available next turn
            putJsonArray("readySkills") {
                newState.characterSheet.getReadySkills().forEach { skill ->
                    add(buildJsonObject {
                        put("id", JsonPrimitive(skill.id))
                        put("name", JsonPrimitive(skill.name))
                        put("manaCost", JsonPrimitive(skill.manaCost))
                        put("energyCost", JsonPrimitive(skill.energyCost))
                    })
                }
            }
            putJsonArray("cooldownSkills") {
                newState.characterSheet.skills.filter { it.currentCooldown > 0 }.forEach { skill ->
                    add(buildJsonObject {
                        put("id", JsonPrimitive(skill.id))
                        put("name", JsonPrimitive(skill.name))
                        put("cooldownRemaining", JsonPrimitive(skill.currentCooldown))
                    })
                }
            }
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = gameEvents)
    }

    private fun useSkill(skillId: String, target: String?, state: GameState): ToolOutcome {
        // If in combat, route to combat_use_skill
        if (state.inCombat) {
            return combatUseSkill(skillId, state)
        }

        val skill = state.characterSheet.skills.find { it.id == skillId }
            ?: return ToolOutcome(success = false, error = "Skill '$skillId' not found")

        if (!state.characterSheet.canUseSkill(skill)) {
            return ToolOutcome(success = false, error = "Cannot use '${skill.name}': insufficient resources or on cooldown")
        }

        val newSheet = state.characterSheet.useSkill(skillId)
            ?: return ToolOutcome(success = false, error = "Failed to use skill '${skill.name}'")
        val newState = state.updateCharacterSheet(newSheet)

        val events = listOf(
            GameEvent.SystemNotification("Used ${skill.name}${if (target != null) " on $target" else ""}")
        )

        val data = buildJsonObject {
            put("skill", JsonPrimitive(skill.name))
            put("target", JsonPrimitive(target ?: "none"))
            put("manaCost", JsonPrimitive(skill.manaCost))
            put("energyCost", JsonPrimitive(skill.energyCost))
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = events)
    }

    // ═══════════════════════════════════════════════════════════════════
    // MOVEMENT
    // ═══════════════════════════════════════════════════════════════════

    private suspend fun moveToLocation(locationName: String, state: GameState): ToolOutcome {
        if (locationName.isBlank()) return ToolOutcome(success = false, error = "Location name cannot be blank")

        // First: check existing connections
        val connected = state.currentLocation.connections.mapNotNull { connectionId ->
            locationManager.getLocation(connectionId, state)
        }

        val target = connected.find { it.name.equals(locationName, ignoreCase = true) }
            ?: connected.find { it.name.lowercase().contains(locationName.lowercase()) }

        if (target != null) {
            val newState = state.moveToLocation(target).discoverLocation(target.id)
            val events = listOf(GameEvent.SystemNotification("Moved to ${target.name}"))
            val data = buildJsonObject {
                put("locationId", JsonPrimitive(target.id))
                put("locationName", JsonPrimitive(target.name))
                put("biome", JsonPrimitive(target.biome.name))
                put("danger", JsonPrimitive(target.danger))
                put("description", JsonPrimitive(target.description))
            }
            return ToolOutcome(success = true, data = data, newState = newState, events = events)
        }

        // Second: check custom locations by name (not just connections)
        val customTarget = state.customLocations.values.find {
            it.name.equals(locationName, ignoreCase = true) ||
            it.name.lowercase().contains(locationName.lowercase())
        }
        if (customTarget != null) {
            val newState = state.moveToLocation(customTarget)
            val events = listOf(GameEvent.SystemNotification("Moved to ${customTarget.name}"))
            val data = buildJsonObject {
                put("locationId", JsonPrimitive(customTarget.id))
                put("locationName", JsonPrimitive(customTarget.name))
                put("biome", JsonPrimitive(customTarget.biome.name))
                put("danger", JsonPrimitive(customTarget.danger))
                put("description", JsonPrimitive(customTarget.description))
            }
            return ToolOutcome(success = true, data = data, newState = newState, events = events)
        }

        // Third: dynamically generate a new location
        // Try AI generation first for rich description + scene art prompt
        val generated = if (locationGenerator != null) {
            try { locationGenerator.generateLocation(state.currentLocation, locationName, state) } catch (_: Exception) { null }
        } else null

        val newLocation: Location
        val visualPrompt: String?

        if (generated != null) {
            newLocation = generated.location.copy(
                connections = listOf(state.currentLocation.id)
            )
            visualPrompt = generated.visualPrompt.takeIf { it.isNotBlank() }
        } else {
            // Fallback to bare-bones generation
            val newId = "dynamic_${locationName.lowercase().replace(Regex("[^a-z0-9]"), "_")}_${currentTimeMillis()}"
            val dangerLevel = (state.playerLevel + 1).coerceAtMost(20)
            newLocation = Location(
                id = newId,
                name = locationName,
                zoneId = state.currentLocation.zoneId,
                biome = inferBiome(locationName, state),
                description = "A newly discovered area: $locationName",
                danger = dangerLevel,
                connections = listOf(state.currentLocation.id),
                features = emptyList(),
                lore = ""
            )
            visualPrompt = null
        }

        // Update the old location to connect to the new one
        val updatedCurrentLocation = state.currentLocation.copy(
            connections = state.currentLocation.connections + newLocation.id
        )

        val newState = state
            .addCustomLocation(newLocation)
            .addCustomLocation(updatedCurrentLocation)
            .moveToLocation(newLocation)

        val events = listOf(GameEvent.SystemNotification("Discovered and moved to ${newLocation.name}"))
        val data = buildJsonObject {
            put("locationId", JsonPrimitive(newLocation.id))
            put("locationName", JsonPrimitive(newLocation.name))
            put("biome", JsonPrimitive(newLocation.biome.name))
            put("danger", JsonPrimitive(newLocation.danger))
            put("description", JsonPrimitive(newLocation.description))
            put("dynamicallyGenerated", JsonPrimitive(true))
            // Visual prompt for server-side scene art generation
            if (visualPrompt != null) {
                put("visualPrompt", JsonPrimitive(visualPrompt))
                put("imageType", JsonPrimitive("scene"))
            }
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = events)
    }

    private fun inferBiome(locationName: String, state: GameState): Biome {
        val lower = locationName.lowercase()
        return when {
            "floor" in lower || "level" in lower || "dungeon" in lower -> Biome.DUNGEON
            "cave" in lower || "den" in lower -> Biome.CAVE
            "forest" in lower || "wood" in lower -> Biome.FOREST
            "town" in lower || "market" in lower || "settlement" in lower -> Biome.SETTLEMENT
            "ruin" in lower || "ancient" in lower -> Biome.RUINS
            "void" in lower || "rift" in lower || "nexus" in lower -> Biome.COSMIC_VOID
            else -> state.currentLocation.biome // inherit from where we came from
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // NPC INTERACTION
    // ═══════════════════════════════════════════════════════════════════

    private fun talkToNPC(npcName: String, dialogue: String?, state: GameState): ToolOutcome {
        val npc = state.findNPCByName(npcName)
            ?: return ToolOutcome(success = false, error = "'$npcName' not found at current location")

        // Look up peer data for named peers
        val peer = if (npc.archetype == NPCArchetype.NAMED_PEER) {
            val seed = state.seedId?.let { WorldSeeds.byId(it) }
            seed?.tutorial?.namedPeers?.find { it.name.equals(npc.name, ignoreCase = true) }
        } else null

        val relationship = npc.getRelationship(state.gameId)

        // Charisma-based dialogue mutation — how the player COMES ACROSS, not what they said
        val charisma = state.characterSheet.effectiveStats().charisma
        val charismaContext = when {
            charisma >= 18 -> "The player speaks with magnetic confidence. Their words carry natural weight and charm. NPCs feel drawn to listen and trust them."
            charisma >= 14 -> "The player is articulate and likeable. They express themselves well and make a good impression."
            charisma in 8..10 -> "The player is a bit awkward in conversation. They sometimes stumble over words or misjudge tone."
            charisma < 8 -> "The player is socially clumsy. They come across as blunt, off-putting, or uncomfortable. NPCs may be less patient or receptive."
            else -> "" // 11-13 is average, no modifier
        }

        val data = buildJsonObject {
            put("npcId", JsonPrimitive(npc.id))
            put("npcName", JsonPrimitive(npc.name))
            put("archetype", JsonPrimitive(npc.archetype.name))
            put("personality", JsonPrimitive(npc.personality.traits.joinToString(", ")))
            put("speechPattern", JsonPrimitive(npc.personality.speechPattern))
            put("motivations", JsonPrimitive(npc.personality.motivations.joinToString(", ")))
            put("lore", JsonPrimitive(npc.lore))
            put("hasShop", JsonPrimitive(npc.shop != null))
            put("hasQuests", JsonPrimitive(npc.questIds.isNotEmpty()))
            put("relationshipStatus", JsonPrimitive(relationship.getStatus().name))
            put("affinity", JsonPrimitive(relationship.affinity))
            if (dialogue != null) put("playerSaid", JsonPrimitive(dialogue))
            put("playerCharisma", JsonPrimitive(charisma))
            if (charismaContext.isNotBlank()) put("charismaEffect", JsonPrimitive(charismaContext))

            // Named peer enrichment — gives GM deep context for narration
            if (peer != null) {
                put("isPeer", JsonPrimitive(true))
                put("className", JsonPrimitive(peer.className))
                put("peerRelationship", JsonPrimitive(peer.relationship))
                put("dialogueStyle", JsonPrimitive(peer.dialogueStyle))
                put("vulnerability", JsonPrimitive(peer.vulnerability))
                put("needFromPlayer", JsonPrimitive(peer.needFromPlayer))
                // Include relationship stage hints
                val stageHint = when {
                    npc.conversationHistory.size <= 1 -> peer.stageOne
                    npc.conversationHistory.size <= 3 -> peer.stageTwo
                    npc.conversationHistory.size <= 6 -> peer.stageThree
                    else -> peer.stageFour
                }
                if (stageHint.isNotBlank()) put("currentStageHint", JsonPrimitive(stageHint))
            }
        }

        val events = mutableListOf<GameEvent>()
        if (charismaContext.isNotBlank()) {
            events.add(GameEvent.SystemNotification("[CHA $charisma] $charismaContext"))
        }

        return ToolOutcome(success = true, data = data, events = events)
    }

    private fun purchaseFromShop(npcId: String, itemId: String, quantity: Int, state: GameState): ToolOutcome {
        val npc = state.findNPC(npcId)
            ?: return ToolOutcome(success = false, error = "NPC not found")
        val shop = npc.shop
            ?: return ToolOutcome(success = false, error = "${npc.name} doesn't have a shop")
        val item = shop.getItem(itemId)
            ?: return ToolOutcome(success = false, error = "Item not found in shop")

        if (item.requiredLevel > state.playerLevel) {
            return ToolOutcome(success = false, error = "Requires level ${item.requiredLevel}")
        }
        if (item.stock in 0 until quantity) {
            return ToolOutcome(success = false, error = "Only ${item.stock} in stock")
        }

        val totalCost = item.price * quantity

        // Validate player has enough gold
        val playerGold = state.characterSheet.inventory.items["currency_gold"]?.quantity ?: 0
        if (playerGold < totalCost) {
            return ToolOutcome(success = false, error = "Not enough gold. Need $totalCost, have $playerGold")
        }

        // Deduct gold
        val newGoldQty = playerGold - totalCost
        val goldItem = InventoryItem(
            id = "currency_gold", name = "Gold", description = "Currency",
            type = ItemType.MISC, quantity = newGoldQty, rarity = com.rpgenerator.core.api.ItemRarity.COMMON
        )

        // Add purchased item to inventory
        val purchasedItem = InventoryItem(
            id = itemId, name = item.name, description = item.description,
            type = inferItemType(item.name), quantity = quantity,
            rarity = com.rpgenerator.core.api.ItemRarity.COMMON
        )

        var newState = state.addItem(purchasedItem)
        // Update gold in inventory
        val newInventory = newState.characterSheet.inventory.copy(
            items = newState.characterSheet.inventory.items + ("currency_gold" to goldItem)
        )
        val newSheet = newState.characterSheet.copy(inventory = newInventory)
        newState = newState.copy(characterSheet = newSheet)

        // Update shop stock (if stock is tracked, i.e. not unlimited/-1)
        if (item.stock > 0) {
            val updatedShopItem = item.copy(stock = item.stock - quantity)
            val updatedShop = shop.copy(inventory = shop.inventory.map {
                if (it.id == itemId) updatedShopItem else it
            })
            val updatedNpc = npc.copy(shop = updatedShop)
            newState = newState.updateNPC(updatedNpc)
        }

        val events = listOf(
            GameEvent.ItemGained(itemId, item.name, quantity),
            GameEvent.SystemNotification("Spent $totalCost gold")
        )
        val data = buildJsonObject {
            put("item", JsonPrimitive(item.name))
            put("quantity", JsonPrimitive(quantity))
            put("cost", JsonPrimitive(totalCost))
            put("currency", JsonPrimitive(shop.currency))
            put("goldRemaining", JsonPrimitive(newGoldQty))
        }
        return ToolOutcome(success = true, data = data, events = events, newState = newState)
    }

    private fun sellToShop(npcId: String, itemId: String, quantity: Int, state: GameState): ToolOutcome {
        val npc = state.findNPC(npcId)
            ?: return ToolOutcome(success = false, error = "NPC not found")
        val shop = npc.shop ?: return ToolOutcome(success = false, error = "${npc.name} doesn't have a shop")
        val playerItem = state.characterSheet.inventory.items[itemId]
            ?: return ToolOutcome(success = false, error = "You don't have this item")

        if (playerItem.quantity < quantity) {
            return ToolOutcome(success = false, error = "You only have ${playerItem.quantity}")
        }

        val sellValue = (playerItem.rarity.ordinal + 1) * 10 * quantity * shop.buybackPercentage / 100
        var newState = state.removeItem(itemId, quantity)

        // Add gold from sale
        val currentGold = newState.characterSheet.inventory.items["currency_gold"]?.quantity ?: 0
        val newGoldQty = currentGold + sellValue
        val goldItem = InventoryItem(
            id = "currency_gold", name = "Gold", description = "Currency",
            type = ItemType.MISC, quantity = newGoldQty, rarity = com.rpgenerator.core.api.ItemRarity.COMMON
        )
        val newInventory = newState.characterSheet.inventory.copy(
            items = newState.characterSheet.inventory.items + ("currency_gold" to goldItem)
        )
        val newSheet = newState.characterSheet.copy(inventory = newInventory)
        newState = newState.copy(characterSheet = newSheet)

        val events = listOf(GameEvent.SystemNotification("Sold ${playerItem.name} x$quantity for $sellValue gold"))
        val data = buildJsonObject {
            put("item", JsonPrimitive(playerItem.name))
            put("quantity", JsonPrimitive(quantity))
            put("goldGained", JsonPrimitive(sellValue))
            put("goldTotal", JsonPrimitive(newGoldQty))
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = events)
    }

    // ═══════════════════════════════════════════════════════════════════
    // ITEM USAGE
    // ═══════════════════════════════════════════════════════════════════

    private fun useItem(itemId: String, state: GameState): ToolOutcome {
        val item = state.characterSheet.inventory.items[itemId]
            ?: return ToolOutcome(success = false, error = "Item '$itemId' not found in inventory")

        // Allow both CONSUMABLE and MISC items that look consumable (potions, food, stims)
        val isConsumable = item.type == ItemType.CONSUMABLE ||
            item.name.lowercase().let { n ->
                n.contains("potion") || n.contains("stim") || n.contains("salve") ||
                n.contains("elixir") || n.contains("food") || n.contains("ration") ||
                n.contains("bandage") || n.contains("tonic") || n.contains("flask")
            }

        if (!isConsumable && item.type != ItemType.MISC) {
            return ToolOutcome(success = false, error = "'${item.name}' cannot be used")
        }

        // Remove 1 from inventory
        var newState = state.removeItem(itemId, 1)

        // Apply effects based on item name
        val nameLower = item.name.lowercase()
        var healAmount = 0
        var manaAmount = 0
        var energyAmount = 0
        val events = mutableListOf<GameEvent>()

        when {
            nameLower.contains("health potion") || nameLower.contains("stim") || nameLower.contains("salve") || nameLower.contains("bandage") -> {
                // Heal based on rarity
                healAmount = when (item.rarity) {
                    com.rpgenerator.core.api.ItemRarity.COMMON -> 30
                    com.rpgenerator.core.api.ItemRarity.UNCOMMON -> 50
                    com.rpgenerator.core.api.ItemRarity.RARE -> 80
                    com.rpgenerator.core.api.ItemRarity.EPIC -> 120
                    com.rpgenerator.core.api.ItemRarity.LEGENDARY -> 200
                }
                newState = newState.copy(characterSheet = newState.characterSheet.heal(healAmount))
                events.add(GameEvent.SystemNotification("Healed $healAmount HP"))
            }
            nameLower.contains("mana potion") -> {
                manaAmount = when (item.rarity) {
                    com.rpgenerator.core.api.ItemRarity.COMMON -> 25
                    com.rpgenerator.core.api.ItemRarity.UNCOMMON -> 40
                    com.rpgenerator.core.api.ItemRarity.RARE -> 60
                    com.rpgenerator.core.api.ItemRarity.EPIC -> 100
                    com.rpgenerator.core.api.ItemRarity.LEGENDARY -> 150
                }
                val newMana = (newState.characterSheet.resources.currentMana + manaAmount)
                    .coerceAtMost(newState.characterSheet.resources.maxMana)
                newState = newState.copy(characterSheet = newState.characterSheet.copy(
                    resources = newState.characterSheet.resources.copy(currentMana = newMana)
                ))
                events.add(GameEvent.SystemNotification("Restored $manaAmount mana"))
            }
            nameLower.contains("energy") || nameLower.contains("stamina") -> {
                energyAmount = when (item.rarity) {
                    com.rpgenerator.core.api.ItemRarity.COMMON -> 25
                    com.rpgenerator.core.api.ItemRarity.UNCOMMON -> 40
                    com.rpgenerator.core.api.ItemRarity.RARE -> 60
                    com.rpgenerator.core.api.ItemRarity.EPIC -> 100
                    com.rpgenerator.core.api.ItemRarity.LEGENDARY -> 150
                }
                val newEnergy = (newState.characterSheet.resources.currentEnergy + energyAmount)
                    .coerceAtMost(newState.characterSheet.resources.maxEnergy)
                newState = newState.copy(characterSheet = newState.characterSheet.copy(
                    resources = newState.characterSheet.resources.copy(currentEnergy = newEnergy)
                ))
                events.add(GameEvent.SystemNotification("Restored $energyAmount energy"))
            }
            nameLower.contains("elixir") || nameLower.contains("tonic") -> {
                // Full-spectrum: heal + mana + energy
                healAmount = 40; manaAmount = 20; energyAmount = 20
                var sheet = newState.characterSheet.heal(healAmount)
                val newMana = (sheet.resources.currentMana + manaAmount).coerceAtMost(sheet.resources.maxMana)
                val newEnergy = (sheet.resources.currentEnergy + energyAmount).coerceAtMost(sheet.resources.maxEnergy)
                sheet = sheet.copy(resources = sheet.resources.copy(currentMana = newMana, currentEnergy = newEnergy))
                newState = newState.copy(characterSheet = sheet)
                events.add(GameEvent.SystemNotification("Restored $healAmount HP, $manaAmount mana, $energyAmount energy"))
            }
        }

        val data = buildJsonObject {
            put("used", JsonPrimitive(item.name))
            put("remaining", JsonPrimitive((item.quantity - 1).coerceAtLeast(0)))
            if (healAmount > 0) put("hpRestored", JsonPrimitive(healAmount))
            if (manaAmount > 0) put("manaRestored", JsonPrimitive(manaAmount))
            if (energyAmount > 0) put("energyRestored", JsonPrimitive(energyAmount))
            put("currentHP", JsonPrimitive(newState.characterSheet.resources.currentHP))
            put("maxHP", JsonPrimitive(newState.characterSheet.resources.maxHP))
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = events)
    }

    private fun equipItem(itemId: String, state: GameState): ToolOutcome {
        val item = state.characterSheet.inventory.items[itemId]
            ?: return ToolOutcome(success = false, error = "Item not found in inventory")

        // Validate equipment type
        val slot = when (item.type) {
            ItemType.WEAPON -> "weapon"
            ItemType.ARMOR -> "armor"
            ItemType.ACCESSORY -> "accessory"
            else -> return ToolOutcome(success = false, error = "${item.name} is not equippable (type: ${item.type})")
        }

        // Level enforcement
        if (item.requiredLevel > state.playerLevel) {
            return ToolOutcome(
                success = false,
                error = "Requires level ${item.requiredLevel}, you are level ${state.playerLevel}"
            )
        }

        // Build the equipment piece from the inventory item
        val equipment = state.characterSheet.equipment
        val oldItem: EquipmentItem?
        val newEquipment = when (slot) {
            "weapon" -> {
                oldItem = equipment.weapon
                equipment.copy(weapon = Weapon(
                    id = item.id, name = item.name, description = item.description,
                    requiredLevel = item.requiredLevel, baseDamage = item.baseDamage,
                    strengthBonus = item.statBonuses.strength, dexterityBonus = item.statBonuses.dexterity
                ))
            }
            "armor" -> {
                oldItem = equipment.armor
                equipment.copy(armor = Armor(
                    id = item.id, name = item.name, description = item.description,
                    requiredLevel = item.requiredLevel, defenseBonus = item.defenseBonus,
                    constitutionBonus = item.statBonuses.constitution
                ))
            }
            "accessory" -> {
                oldItem = equipment.accessory
                equipment.copy(accessory = Accessory(
                    id = item.id, name = item.name, description = item.description,
                    requiredLevel = item.requiredLevel,
                    intelligenceBonus = item.statBonuses.intelligence,
                    wisdomBonus = item.statBonuses.wisdom
                ))
            }
            else -> return ToolOutcome(success = false, error = "Unknown slot")
        }

        // Remove equipped item from inventory (1 qty)
        var newInventory = state.characterSheet.inventory.removeItem(itemId, 1)

        // Put the old item back into inventory if there was one
        if (oldItem != null) {
            val oldInvItem = InventoryItem(
                id = oldItem.id, name = oldItem.name, description = oldItem.description,
                type = when (oldItem) {
                    is Weapon -> ItemType.WEAPON
                    is Armor -> ItemType.ARMOR
                    is Accessory -> ItemType.ACCESSORY
                },
                quantity = 1, stackable = false, requiredLevel = oldItem.requiredLevel,
                baseDamage = if (oldItem is Weapon) oldItem.baseDamage else 0,
                defenseBonus = if (oldItem is Armor) oldItem.defenseBonus else 0
            )
            newInventory = newInventory.addItem(oldInvItem)
        }

        val newSheet = state.characterSheet.copy(
            equipment = newEquipment,
            inventory = newInventory
        )
        val newState = state.copy(characterSheet = newSheet)

        val events = mutableListOf<GameEvent>(
            GameEvent.SystemNotification("Equipped ${item.name} (${slot})")
        )
        if (oldItem != null) {
            events.add(GameEvent.SystemNotification("Unequipped ${oldItem.name}"))
        }

        return ToolOutcome(
            success = true,
            data = buildJsonObject {
                put("equipped", JsonPrimitive(item.name))
                put("slot", JsonPrimitive(slot))
                if (oldItem != null) put("unequipped", JsonPrimitive(oldItem.name))
                put("baseDamage", JsonPrimitive(item.baseDamage))
                put("defenseBonus", JsonPrimitive(item.defenseBonus))
            },
            newState = newState,
            events = events
        )
    }

    private suspend fun addItem(args: Map<String, Any?>, state: GameState): ToolOutcome {
        val name = args.str("itemName").ifBlank { return ToolOutcome(success = false, error = "itemName required") }
        val desc = args.strOpt("description") ?: "Found item"
        val qty = args.int("quantity", 1)
        val rarityStr = (args.strOpt("rarity") ?: "COMMON").uppercase()
        val rarity = try {
            com.rpgenerator.core.api.ItemRarity.valueOf(rarityStr)
        } catch (_: Exception) {
            com.rpgenerator.core.api.ItemRarity.COMMON
        }
        val itemId = "item_${name.lowercase().replace(" ", "_")}_${currentTimeMillis()}"
        val itemType = inferItemType(name)

        // Try AI enrichment for better description + visual prompt
        val itemGenResult = if (itemGenerator != null) {
            try { itemGenerator.generate(name, rarityStr, state) } catch (_: Exception) { null }
        } else null

        val finalDesc = itemGenResult?.enhancedDescription ?: desc

        // Auto-generate equipment stats based on rarity and player level
        val rarityMultiplier = when (rarity) {
            com.rpgenerator.core.api.ItemRarity.COMMON -> 1.0
            com.rpgenerator.core.api.ItemRarity.UNCOMMON -> 1.5
            com.rpgenerator.core.api.ItemRarity.RARE -> 2.0
            com.rpgenerator.core.api.ItemRarity.EPIC -> 3.0
            com.rpgenerator.core.api.ItemRarity.LEGENDARY -> 4.0
        }
        val levelBase = state.playerLevel.coerceAtLeast(1)
        val baseDamage = if (itemType == ItemType.WEAPON) (2 + levelBase * rarityMultiplier).toInt() else 0
        val defenseBonus = if (itemType == ItemType.ARMOR) (1 + levelBase * rarityMultiplier * 0.8).toInt() else 0

        val item = InventoryItem(
            id = itemId, name = name, description = finalDesc,
            type = itemType, quantity = qty, rarity = rarity,
            baseDamage = baseDamage,
            defenseBonus = defenseBonus,
            requiredLevel = levelBase
        )
        val newState = state.addItem(item)
        val events = listOf(GameEvent.ItemGained(itemId, name, qty))
        return ToolOutcome(success = true, data = buildJsonObject {
            put("itemId", JsonPrimitive(itemId))
            put("itemName", JsonPrimitive(name))
            put("quantity", JsonPrimitive(qty))
            // Visual prompt for server-side item icon generation
            if (itemGenResult?.visualPrompt?.isNotBlank() == true) {
                put("visualPrompt", JsonPrimitive(itemGenResult.visualPrompt))
                put("imageType", JsonPrimitive("item"))
            }
        }, newState = newState, events = events)
    }

    /** Infer item type from name — potions are CONSUMABLE, swords are WEAPON, etc. */
    private fun inferItemType(name: String): ItemType {
        val n = name.lowercase()
        return when {
            n.contains("potion") || n.contains("stim") || n.contains("salve") ||
            n.contains("elixir") || n.contains("food") || n.contains("ration") ||
            n.contains("bandage") || n.contains("tonic") || n.contains("flask") ||
            n.contains("pill") || n.contains("herb") || n.contains("scroll") -> ItemType.CONSUMABLE
            n.contains("sword") || n.contains("dagger") || n.contains("axe") ||
            n.contains("mace") || n.contains("bow") || n.contains("staff") ||
            n.contains("spear") || n.contains("blade") || n.contains("wand") ||
            n.contains("hammer") || n.contains("crossbow") -> ItemType.WEAPON
            n.contains("armor") || n.contains("shield") || n.contains("helm") ||
            n.contains("helmet") || n.contains("chestplate") || n.contains("boots") ||
            n.contains("gauntlet") || n.contains("plate") || n.contains("mail") ||
            n.contains("robe") || n.contains("cloak") -> ItemType.ARMOR
            n.contains("ring") || n.contains("amulet") || n.contains("necklace") ||
            n.contains("trinket") || n.contains("charm") || n.contains("pendant") ||
            n.contains("earring") || n.contains("bracelet") -> ItemType.ACCESSORY
            else -> ItemType.MISC
        }
    }

    private fun addGold(args: Map<String, Any?>, state: GameState): ToolOutcome {
        val amount = args.int("amount", 0)
        if (amount <= 0) return ToolOutcome(success = false, error = "amount required (positive integer)")
        val itemId = "currency_gold"
        val existing = state.characterSheet.inventory.items[itemId]
        val newQty = (existing?.quantity ?: 0) + amount
        val goldItem = InventoryItem(
            id = itemId, name = "Gold", description = "Currency",
            type = ItemType.MISC, quantity = newQty, rarity = com.rpgenerator.core.api.ItemRarity.COMMON
        )
        val newInventory = state.characterSheet.inventory.copy(
            items = state.characterSheet.inventory.items + (itemId to goldItem)
        )
        val newSheet = state.characterSheet.copy(inventory = newInventory)
        val newState = state.copy(characterSheet = newSheet)
        val events = listOf(GameEvent.SystemNotification("Gained $amount gold"))
        return ToolOutcome(success = true, data = buildJsonObject {
            put("amount", JsonPrimitive(amount))
            put("newTotal", JsonPrimitive(newQty))
        }, newState = newState, events = events)
    }

    // ═══════════════════════════════════════════════════════════════════
    // QUEST MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    private fun acceptQuest(questId: String, state: GameState): ToolOutcome {
        val events = listOf(GameEvent.QuestUpdate(questId, questId, QuestStatus.NEW))
        val data = buildJsonObject {
            put("questId", JsonPrimitive(questId))
            put("status", JsonPrimitive("accepted"))
        }
        return ToolOutcome(success = true, data = data, events = events)
    }

    private fun updateQuestProgress(questId: String, objectiveId: String, progress: Int, state: GameState): ToolOutcome {
        state.activeQuests[questId]
            ?: return ToolOutcome(success = false, error = "Quest '$questId' not found")

        val newState = state.updateQuestObjective(questId, objectiveId, progress)
        val data = buildJsonObject {
            put("questId", JsonPrimitive(questId))
            put("objectiveId", JsonPrimitive(objectiveId))
            put("progress", JsonPrimitive(progress))
        }
        return ToolOutcome(success = true, data = data, newState = newState)
    }

    private fun completeQuest(questId: String, state: GameState): ToolOutcome {
        val quest = state.activeQuests[questId]
            ?: return ToolOutcome(success = false, error = "Quest '$questId' not found")

        if (!quest.isComplete()) {
            return ToolOutcome(success = false, error = "Quest '${quest.name}' has unfinished objectives")
        }

        val newState = state.completeQuest(questId)
        val events = listOf(
            GameEvent.QuestUpdate(questId, quest.name, QuestStatus.COMPLETED),
            GameEvent.SystemNotification("Quest completed: ${quest.name}! +${quest.rewards.xp} XP")
        )
        val data = buildJsonObject {
            put("questId", JsonPrimitive(questId))
            put("questName", JsonPrimitive(quest.name))
            put("xpReward", JsonPrimitive(quest.rewards.xp))
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = events)
    }

    // ═══════════════════════════════════════════════════════════════════
    // CHARACTER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════

    private fun setPlayerName(name: String, state: GameState): ToolOutcome {
        val newState = state.copy(playerName = name)
        val data = buildJsonObject { put("name", JsonPrimitive(name)) }
        return ToolOutcome(
            success = true, data = data, newState = newState,
            events = listOf(GameEvent.SystemNotification("Character name set: $name"))
        )
    }

    private fun setBackstory(backstory: String, state: GameState): ToolOutcome {
        val newState = state.copy(backstory = backstory)
        val data = buildJsonObject { put("backstory", JsonPrimitive(backstory)) }
        return ToolOutcome(
            success = true, data = data, newState = newState,
            events = listOf(GameEvent.SystemNotification("Backstory set"))
        )
    }

    // Common aliases players/LLMs might use for classes
    private val classAliases = mapOf(
        "scout" to PlayerClass.STRIKER,
        "ranger" to PlayerClass.SURVIVALIST,
        "rogue" to PlayerClass.STRIKER,
        "assassin" to PlayerClass.STRIKER,
        "tank" to PlayerClass.BULWARK,
        "warrior" to PlayerClass.SLAYER,
        "fighter" to PlayerClass.SLAYER,
        "mage" to PlayerClass.CHANNELER,
        "wizard" to PlayerClass.CHANNELER,
        "sorcerer" to PlayerClass.CHANNELER,
        "cleric" to PlayerClass.HEALER,
        "priest" to PlayerClass.HEALER,
        "paladin" to PlayerClass.BULWARK,
        "bard" to PlayerClass.COMMANDER,
        "monk" to PlayerClass.CULTIVATOR,
        "warlock" to PlayerClass.CONTRACTOR,
        "thief" to PlayerClass.STRIKER,
        "engineer" to PlayerClass.ARTIFICER,
        "tinkerer" to PlayerClass.ARTIFICER,
        "forge-bound" to PlayerClass.ARTIFICER,
        "forgebound" to PlayerClass.ARTIFICER,
        "remnant" to PlayerClass.ADAPTER,
        "mimic" to PlayerClass.ADAPTER,
        "copycat" to PlayerClass.ADAPTER,
        "shapeshifter" to PlayerClass.ADAPTER,
        "berserker" to PlayerClass.SLAYER,
        "barbarian" to PlayerClass.SLAYER,
        "necromancer" to PlayerClass.CONTRACTOR,
        "summoner" to PlayerClass.CONTRACTOR,
        "druid" to PlayerClass.CULTIVATOR,
        "shaman" to PlayerClass.CULTIVATOR,
        "ninja" to PlayerClass.BLADE_DANCER,
        "samurai" to PlayerClass.BLADE_DANCER,
        "hacker" to PlayerClass.GLITCH,
        "psychic" to PlayerClass.PSION,
        "telepath" to PlayerClass.PSION,
        "gourmand" to PlayerClass.CULTIVATOR,
        "devourer" to PlayerClass.CULTIVATOR,
    )

    // Maps archetype keywords to a base class for stat bonuses when creating fully custom classes
    private val archetypeDefaults = mapOf(
        "combat" to PlayerClass.SLAYER,
        "melee" to PlayerClass.SLAYER,
        "tank" to PlayerClass.BULWARK,
        "defense" to PlayerClass.BULWARK,
        "speed" to PlayerClass.STRIKER,
        "agility" to PlayerClass.STRIKER,
        "magic" to PlayerClass.CHANNELER,
        "caster" to PlayerClass.CHANNELER,
        "mystic" to PlayerClass.CULTIVATOR,
        "wisdom" to PlayerClass.CULTIVATOR,
        "mind" to PlayerClass.PSION,
        "psychic" to PlayerClass.PSION,
        "hybrid" to PlayerClass.ADAPTER,
        "adapt" to PlayerClass.ADAPTER,
        "survival" to PlayerClass.SURVIVALIST,
        "support" to PlayerClass.HEALER,
        "heal" to PlayerClass.HEALER,
        "craft" to PlayerClass.ARTIFICER,
        "build" to PlayerClass.ARTIFICER,
        "lead" to PlayerClass.COMMANDER,
        "summon" to PlayerClass.CONTRACTOR,
        "unique" to PlayerClass.GLITCH,
    )

    private suspend fun suggestClasses(state: GameState): ToolOutcome {
        if (classGenerator == null) {
            return ToolOutcome(success = false, error = "Class generator not available. Create classes manually with set_class.")
        }

        val suggestions = try {
            classGenerator.generateClassOptions(state)
        } catch (_: Exception) {
            null
        }

        if (suggestions == null) {
            return ToolOutcome(success = false, error = "Failed to generate class suggestions. Create classes manually with set_class.")
        }

        val data = buildJsonObject {
            putJsonArray("classOptions") {
                for (cls in suggestions) {
                    addJsonObject {
                        put("name", JsonPrimitive(cls.name))
                        put("description", JsonPrimitive(cls.description))
                        put("archetype", JsonPrimitive(cls.archetype.displayName))
                        putJsonArray("traits") {
                            for (trait in cls.traits) {
                                add(JsonPrimitive("${trait.name}: ${trait.description}"))
                            }
                        }
                        putJsonArray("evolutionHints") {
                            for (hint in cls.evolutionHints) add(JsonPrimitive(hint))
                        }
                        if (cls.physicalMutations.isNotEmpty()) {
                            putJsonArray("physicalMutations") {
                                for (mut in cls.physicalMutations) add(JsonPrimitive(mut))
                            }
                        }
                    }
                }
            }
            put("NOTE", JsonPrimitive("Present these options to the player. They can pick one, modify one, or describe their own class. Use set_class with the chosen/custom class details."))
        }

        return ToolOutcome(success = true, data = data)
    }

    private suspend fun suggestSkills(context: String?, state: GameState): ToolOutcome {
        if (skillGenerator == null) {
            return ToolOutcome(success = false, error = "Skill generator not available. Create skills manually with grant_skill.")
        }

        val classInfo = state.characterSheet.dynamicClass
        if (classInfo == null) {
            return ToolOutcome(success = false, error = "No class selected yet. Set a class first with set_class.")
        }

        val skills = try {
            if (context != null) {
                // Generate a custom skill from player description
                val single = skillGenerator.generateCustomSkill(context, state)
                single?.let { listOf(it) }
            } else {
                // Generate starter skill options
                skillGenerator.generateStarterSkills(classInfo, state)
            }
        } catch (_: Exception) {
            null
        }

        if (skills == null) {
            return ToolOutcome(success = false, error = "Failed to generate skill suggestions. Create skills manually with grant_skill.")
        }

        val data = buildJsonObject {
            putJsonArray("skillOptions") {
                for (skill in skills) {
                    addJsonObject {
                        put("name", JsonPrimitive(skill.name))
                        put("description", JsonPrimitive(skill.description))
                        put("category", JsonPrimitive(skill.category))
                        put("costType", JsonPrimitive(skill.costType))
                        put("cost", JsonPrimitive(skill.cost))
                        put("cooldown", JsonPrimitive(skill.cooldown))
                        put("target", JsonPrimitive(skill.target))
                        putJsonArray("effects") {
                            for (effect in skill.effectsRaw) {
                                addJsonObject {
                                    put("type", JsonPrimitive(effect.type))
                                    if (effect.base > 0) put("base", JsonPrimitive(effect.base))
                                    if (effect.damageType != "PHYSICAL") put("damageType", JsonPrimitive(effect.damageType))
                                    if (effect.scalingStat != "STRENGTH") put("scalingStat", JsonPrimitive(effect.scalingStat))
                                    if (effect.scalingRatio != 0.5) put("scalingRatio", JsonPrimitive(effect.scalingRatio))
                                    if (effect.stat.isNotBlank()) put("stat", JsonPrimitive(effect.stat))
                                    if (effect.amount > 0) put("amount", JsonPrimitive(effect.amount))
                                    if (effect.duration > 0) put("duration", JsonPrimitive(effect.duration))
                                }
                            }
                            // Fallback: show legacy flat damage/heal if no effects array
                            if (skill.effectsRaw.isEmpty()) {
                                if (skill.damage != null && skill.damage > 0) {
                                    addJsonObject {
                                        put("type", JsonPrimitive("damage"))
                                        put("base", JsonPrimitive(skill.damage))
                                        put("damageType", JsonPrimitive(skill.damageType ?: "PHYSICAL"))
                                    }
                                }
                                if (skill.heal != null && skill.heal > 0) {
                                    addJsonObject {
                                        put("type", JsonPrimitive("heal"))
                                        put("base", JsonPrimitive(skill.heal))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            put("NOTE", JsonPrimitive("Present ALL options to the player with full stats. They choose exactly 2 (or describe their own). Use grant_skill with name+description for each chosen skill."))
        }

        return ToolOutcome(success = true, data = data)
    }

    private fun setClass(
        className: String,
        description: String?,
        traitsStr: String?,
        evolutionHintsStr: String?,
        physicalChangesStr: String?,
        state: GameState
    ): ToolOutcome {
        // Prevent double class selection stacking stat bonuses
        if (state.characterSheet.playerClass != PlayerClass.NONE) {
            return ToolOutcome(success = false, error = "Class already set to '${state.characterSheet.dynamicClass?.name ?: state.characterSheet.playerClass.displayName}'. Cannot change class.")
        }

        // Resolve the internal archetype for combat math
        // 1. Try exact enum match → 2. Display name → 3. Alias → 4. Keyword archetype → 5. ADAPTER fallback
        val baseArchetype = try {
            PlayerClass.valueOf(className.uppercase().replace(" ", "_").replace("-", "_"))
        } catch (_: IllegalArgumentException) {
            PlayerClass.selectableClasses().find { it.displayName.equals(className, ignoreCase = true) }
                ?: classAliases[className.lowercase().trim()]
                ?: archetypeDefaults.entries.find { (key, _) -> className.lowercase().contains(key) }?.value
                ?: PlayerClass.ADAPTER
        }

        // Parse traits from comma-separated or JSON-like string
        val traits = traitsStr?.split(",", ";")?.map { raw ->
            val parts = raw.trim().split(":", limit = 2)
            if (parts.size == 2) {
                ClassTrait(name = parts[0].trim(), description = parts[1].trim())
            } else {
                ClassTrait(name = raw.trim(), description = raw.trim())
            }
        } ?: emptyList()

        val evolutionHints = evolutionHintsStr?.split(",", ";")?.map { it.trim() } ?: emptyList()
        val physicalChanges = physicalChangesStr?.split(",", ";")?.map { it.trim() } ?: emptyList()

        // Build the dynamic class info
        val classInfo = DynamicClassInfo(
            name = className,
            description = description ?: baseArchetype.description,
            archetype = baseArchetype.archetype,
            traits = traits,
            evolutionHints = evolutionHints,
            physicalMutations = physicalChanges
        )

        val newSheet = state.characterSheet.chooseInitialClass(baseArchetype, classInfo)
        val newState = state.updateCharacterSheet(newSheet)

        return ToolOutcome(
            success = true,
            data = buildJsonObject {
                put("class", JsonPrimitive(className))
                put("baseArchetype", JsonPrimitive(baseArchetype.displayName))
                put("description", JsonPrimitive(classInfo.description))
                if (traits.isNotEmpty()) {
                    putJsonArray("traits") {
                        for (trait in traits) {
                            addJsonObject {
                                put("name", JsonPrimitive(trait.name))
                                put("description", JsonPrimitive(trait.description))
                            }
                        }
                    }
                }
                if (evolutionHints.isNotEmpty()) {
                    putJsonArray("evolutionHints") {
                        for (hint in evolutionHints) add(JsonPrimitive(hint))
                    }
                }
                if (physicalChanges.isNotEmpty()) {
                    putJsonArray("physicalChanges") {
                        for (change in physicalChanges) add(JsonPrimitive(change))
                    }
                }
                put("NOTE", JsonPrimitive("Class set. NO skills granted yet — present skill choices to the player. They can pick from suggestions or describe their own. Use grant_skill for each. Aim for 2-3 starter skills."))
            },
            newState = newState,
            events = listOf(GameEvent.SystemNotification("Class selected: $className"))
        )
    }

    private fun setProfession(professionName: String, state: GameState): ToolOutcome {
        val profession = try {
            Profession.valueOf(professionName.uppercase())
        } catch (e: IllegalArgumentException) {
            Profession.selectableProfessions().find { it.displayName.equals(professionName, ignoreCase = true) }
                ?: return ToolOutcome(success = false, error = "Unknown profession: $professionName. Available: ${Profession.selectableProfessions().map { it.displayName }}")
        }

        if (state.characterSheet.profession != Profession.NONE) {
            return ToolOutcome(success = false, error = "Player already has a profession: ${state.characterSheet.profession.displayName}")
        }

        val newSheet = state.characterSheet.chooseProfession(profession)
        val newState = state.updateCharacterSheet(newSheet)
        val events = listOf(
            GameEvent.SystemNotification("Profession selected: ${profession.displayName}")
        )

        val data = buildJsonObject {
            put("profession", JsonPrimitive(profession.displayName))
            put("description", JsonPrimitive(profession.description))
            put("category", JsonPrimitive(profession.category.displayName))
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = events)
    }

    private fun addXP(amount: Long, state: GameState): ToolOutcome {
        val newState = state.gainXP(amount)
        val leveledUp = newState.playerLevel > state.playerLevel
        val events = mutableListOf<GameEvent>()

        events.add(GameEvent.StatChange("xp", state.playerXP.toInt(), newState.playerXP.toInt()))
        if (leveledUp) {
            events.add(GameEvent.SystemNotification("Level up! You are now level ${newState.playerLevel}"))
        }

        val newUnspent = newState.characterSheet.unspentStatPoints
        val oldUnspent = state.characterSheet.unspentStatPoints
        if (newUnspent > oldUnspent) {
            events.add(GameEvent.SystemNotification("Grade advancement! You have ${newUnspent} stat points to allocate."))
        }

        val data = buildJsonObject {
            put("xpGained", JsonPrimitive(amount))
            put("newXP", JsonPrimitive(newState.playerXP))
            put("levelUp", JsonPrimitive(leveledUp))
            put("newLevel", JsonPrimitive(newState.playerLevel))
            put("unspentStatPoints", JsonPrimitive(newUnspent))
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = events)
    }

    private fun allocateStatPoints(
        str: Int, dex: Int, con: Int, int_: Int, wis: Int, cha: Int,
        state: GameState
    ): ToolOutcome {
        val totalSpending = str + dex + con + int_ + wis + cha
        val available = state.characterSheet.unspentStatPoints

        if (totalSpending <= 0) {
            return ToolOutcome(success = false, data = buildJsonObject {
                put("error", JsonPrimitive("Must allocate at least 1 stat point"))
                put("unspentStatPoints", JsonPrimitive(available))
            })
        }

        if (totalSpending > available) {
            return ToolOutcome(success = false, data = buildJsonObject {
                put("error", JsonPrimitive("Not enough stat points. Trying to spend $totalSpending but only $available available."))
                put("unspentStatPoints", JsonPrimitive(available))
            })
        }

        val allocation = Stats(
            strength = str, dexterity = dex, constitution = con,
            intelligence = int_, wisdom = wis, charisma = cha
        )
        val newSheet = state.characterSheet.spendStatPoints(allocation)
        val newState = state.copy(characterSheet = newSheet)

        val events = mutableListOf<GameEvent>()
        val parts = mutableListOf<String>()
        if (str > 0) parts.add("STR +$str")
        if (dex > 0) parts.add("DEX +$dex")
        if (con > 0) parts.add("CON +$con")
        if (int_ > 0) parts.add("INT +$int_")
        if (wis > 0) parts.add("WIS +$wis")
        if (cha > 0) parts.add("CHA +$cha")
        events.add(GameEvent.SystemNotification("Stats allocated: ${parts.joinToString(", ")}. ${newSheet.unspentStatPoints} points remaining."))

        val effective = newSheet.effectiveStats()
        val data = buildJsonObject {
            put("pointsSpent", JsonPrimitive(totalSpending))
            put("remaining", JsonPrimitive(newSheet.unspentStatPoints))
            putJsonObject("newStats") {
                put("strength", JsonPrimitive(effective.strength))
                put("dexterity", JsonPrimitive(effective.dexterity))
                put("constitution", JsonPrimitive(effective.constitution))
                put("intelligence", JsonPrimitive(effective.intelligence))
                put("wisdom", JsonPrimitive(effective.wisdom))
                put("charisma", JsonPrimitive(effective.charisma))
                put("defense", JsonPrimitive(effective.defense))
            }
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = events)
    }

    private fun grantSkill(skillId: String, skillName: String?, description: String?, state: GameState): ToolOutcome {
        // Try to find skill in database first
        val dbSkill = com.rpgenerator.core.skill.SkillDatabase.getSkillById(skillId)
        val skill = if (dbSkill != null) {
            dbSkill
        } else if (skillName != null) {
            // Create a dynamically generated skill
            com.rpgenerator.core.skill.SkillDatabase.createGeneratedSkill(
                name = skillName,
                description = description ?: "A skill granted by the system",
                className = state.characterSheet.playerClass.name
            )
        } else {
            return ToolOutcome(success = false, error = "Skill '$skillId' not found in database. Provide skillName to create a new skill.")
        }

        // Check if player already has this skill (by name)
        if (state.characterSheet.skills.any { it.name == skill.name }) {
            return ToolOutcome(success = false, error = "Player already has skill '${skill.name}'")
        }

        val newSheet = state.characterSheet.copy(skills = state.characterSheet.skills + skill)
        val newState = state.updateCharacterSheet(newSheet)
        val events = listOf(GameEvent.SystemNotification("[Skill Acquired: ${skill.name}] — ${skill.description}"))
        val data = buildJsonObject {
            put("skillId", JsonPrimitive(skill.id))
            put("skillName", JsonPrimitive(skill.name))
            put("description", JsonPrimitive(skill.description))
            put("manaCost", JsonPrimitive(skill.manaCost))
            put("energyCost", JsonPrimitive(skill.energyCost))
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = events)
    }

    private fun completeTutorial(state: GameState): ToolOutcome {
        var newState = state.copy(hasOpeningNarrationPlayed = true)
        val events = mutableListOf<GameEvent>(GameEvent.SystemNotification("Tutorial complete!"))

        // Also complete the tutorial quest if it exists
        val tutorialQuest = newState.activeQuests["quest_survive_tutorial"]
        if (tutorialQuest != null) {
            // Auto-complete any remaining objectives
            for (obj in tutorialQuest.objectives) {
                if (!obj.isComplete()) {
                    newState = newState.updateQuestObjective("quest_survive_tutorial", obj.id, 1)
                }
            }
            // Complete the quest and award rewards
            newState = newState.completeQuest("quest_survive_tutorial")
            events.add(GameEvent.QuestUpdate("quest_survive_tutorial", tutorialQuest.name, QuestStatus.COMPLETED))
        }

        val data = buildJsonObject {
            put("completed", JsonPrimitive(true))
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = events)
    }

    // ═══════════════════════════════════════════════════════════════════
    // WORLD GENERATION
    // ═══════════════════════════════════════════════════════════════════

    private suspend fun spawnNPC(name: String, role: String, locationId: String, state: GameState): ToolOutcome {
        if (name.isBlank()) return ToolOutcome(success = false, error = "NPC name cannot be blank")

        // Check if this is a named peer from WorldSeed
        val seed = state.seedId?.let { WorldSeeds.byId(it) }
        val peer = seed?.tutorial?.namedPeers?.find { peer ->
            peer.name.equals(name, ignoreCase = true) ||
                name.lowercase().contains(peer.name.lowercase()) ||
                peer.name.lowercase().contains(name.lowercase())
        }

        val archetype: NPCArchetype
        val personality: NPCPersonality
        val lore: String
        val greetingContext: String
        var npcVisualPrompt = ""
        var npcVisualDescription = ""

        if (peer != null) {
            // Named peer — populate full personality from WorldSeed
            archetype = NPCArchetype.NAMED_PEER
            personality = NPCPersonality(
                traits = peer.personality.split(",", ";", " and ").map { it.trim() }.filter { it.isNotBlank() },
                speechPattern = peer.dialogueStyle,
                motivations = listOfNotNull(
                    peer.classPriority.takeIf { it.isNotBlank() },
                    peer.classPhilosophy.takeIf { it.isNotBlank() },
                    peer.needFromPlayer.takeIf { it.isNotBlank() }
                ).ifEmpty { listOf(peer.arc) }
            )
            lore = buildString {
                appendLine("${peer.name} — ${peer.className} (${peer.relationship})")
                appendLine("Former life: ${peer.formerLife}")
                if (peer.vulnerability.isNotBlank()) appendLine("Vulnerability: ${peer.vulnerability}")
                if (peer.moment.isNotBlank()) appendLine("Defining moment: ${peer.moment}")
                if (peer.classConflict.isNotBlank()) appendLine("Inner conflict: ${peer.classConflict}")
                appendLine("Arc: ${peer.arc}")
            }
            greetingContext = buildString {
                appendLine("First impression: ${peer.stageOne.ifBlank { peer.firstAppearance }}")
                if (peer.teachesPlayer.isNotBlank()) appendLine("Naturally teaches: ${peer.teachesPlayer}")
                if (peer.sharedActivity.isNotBlank()) appendLine("Bond through: ${peer.sharedActivity}")
            }
        } else {
            // Generic NPC — try AI generation for rich personality + portrait prompt
            val generated = if (npcGenerator != null) {
                try {
                    npcGenerator.generateForRole(
                        role = role,
                        locationId = locationId.ifBlank { state.currentLocation.id },
                        context = "Zone: ${state.currentLocation.name} (${state.currentLocation.biome.name}). Player level: ${state.playerLevel}"
                    )
                } catch (_: Exception) { null }
            } else null

            if (generated != null) {
                // Use AI-generated NPC
                archetype = generated.npc.archetype
                personality = generated.npc.personality
                lore = generated.npc.lore
                greetingContext = generated.npc.greetingContext
            } else {
                // Fallback to minimal NPC
                archetype = when (role.lowercase()) {
                    "merchant", "trader", "shopkeeper" -> NPCArchetype.MERCHANT
                    "quest_giver", "quest giver" -> NPCArchetype.QUEST_GIVER
                    "guard", "soldier" -> NPCArchetype.GUARD
                    "innkeeper", "barkeep" -> NPCArchetype.INNKEEPER
                    "blacksmith" -> NPCArchetype.BLACKSMITH
                    "alchemist" -> NPCArchetype.ALCHEMIST
                    "trainer", "teacher" -> NPCArchetype.TRAINER
                    "noble" -> NPCArchetype.NOBLE
                    "scholar" -> NPCArchetype.SCHOLAR
                    "wanderer", "traveler" -> NPCArchetype.WANDERER
                    else -> NPCArchetype.VILLAGER
                }
                personality = NPCPersonality(
                    traits = listOf("neutral"),
                    speechPattern = "Normal speech",
                    motivations = listOf("Exists in the world")
                )
                lore = ""
                greetingContext = "Spawned NPC"
            }
            npcVisualPrompt = generated?.visualPrompt ?: ""
            npcVisualDescription = generated?.visualDescription ?: ""
        }

        val npcId = "npc_${name.lowercase().replace(" ", "_")}_${currentTimeMillis()}"
        val npc = NPC(
            id = npcId,
            name = peer?.name ?: name, // Use canonical peer name if matched
            archetype = archetype,
            locationId = locationId.ifBlank { state.currentLocation.id },
            personality = personality,
            lore = lore,
            greetingContext = greetingContext,
            visualDescription = npcVisualDescription,
            visualPrompt = npcVisualPrompt
        )

        val newState = state.addNPC(npc)
        val events = listOf(GameEvent.SystemNotification("${npc.name} appeared"))

        val data = buildJsonObject {
            put("npcId", JsonPrimitive(npcId))
            put("name", JsonPrimitive(npc.name))
            put("role", JsonPrimitive(if (peer != null) "named_peer" else role))
            put("location", JsonPrimitive(npc.locationId))
            if (peer != null) {
                put("isPeer", JsonPrimitive(true))
                put("className", JsonPrimitive(peer.className))
                put("relationship", JsonPrimitive(peer.relationship))
            }
            // Visual prompt for server-side portrait generation (non-peer NPCs only)
            if (peer == null && npcVisualPrompt.isNotBlank()) {
                put("visualPrompt", JsonPrimitive(npcVisualPrompt))
                put("imageType", JsonPrimitive("portrait"))
            }
        }
        return ToolOutcome(success = true, data = data, newState = newState, events = events)
    }

    private fun spawnEnemy(name: String, danger: Int, state: GameState): ToolOutcome {
        val data = buildJsonObject {
            put("enemy", JsonPrimitive(name))
            put("danger", JsonPrimitive(danger))
            put("playerLevel", JsonPrimitive(state.playerLevel))
            put("message", JsonPrimitive("$name appears!"))
        }
        return ToolOutcome(success = true, data = data)
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLOT GRAPH
    // ═══════════════════════════════════════════════════════════════════

    private fun createPlotNode(title: String, description: String, threadId: String, beatType: String, triggerLevel: Int, state: GameState): ToolOutcome {
        if (title.isBlank()) return ToolOutcome(success = false, error = "Plot node title cannot be blank")

        val parsedBeatType = try {
            PlotBeatType.valueOf(beatType.uppercase())
        } catch (_: IllegalArgumentException) {
            PlotBeatType.ESCALATION
        }

        val nodeId = "plot_${title.lowercase().replace(Regex("[^a-z0-9]"), "_")}_${currentTimeMillis()}"
        val beat = PlotBeat(
            id = nodeId,
            title = title,
            description = description,
            beatType = parsedBeatType,
            triggerLevel = triggerLevel
        )

        val node = PlotNode(
            id = nodeId,
            beat = beat,
            threadId = threadId.ifBlank { "dynamic" },
            position = GraphPosition(tier = (state.playerLevel / 5) + 1, sequence = 0)
        )

        // Store in game state's plot data (using activeQuests as proxy for now — plot graph is in StoryFoundation)
        val events = listOf(GameEvent.SystemNotification("[Plot] New story beat: $title"))
        val data = buildJsonObject {
            put("nodeId", JsonPrimitive(nodeId))
            put("title", JsonPrimitive(title))
            put("threadId", JsonPrimitive(node.threadId))
            put("beatType", JsonPrimitive(parsedBeatType.name))
        }
        return ToolOutcome(success = true, data = data, events = events)
    }

    private fun completePlotNode(nodeId: String, state: GameState): ToolOutcome {
        val events = listOf(GameEvent.SystemNotification("[Plot] Story beat completed: $nodeId"))
        val data = buildJsonObject {
            put("nodeId", JsonPrimitive(nodeId))
            put("completed", JsonPrimitive(true))
        }
        return ToolOutcome(success = true, data = data, events = events)
    }

    private fun getActivePlotNodes(state: GameState): ToolOutcome {
        val data = buildJsonObject {
            put("playerLevel", JsonPrimitive(state.playerLevel))
            put("location", JsonPrimitive(state.currentLocation.name))
            put("note", JsonPrimitive("Plot graph managed by orchestrator. Use create_plot_node to add dynamic story beats."))
        }
        return ToolOutcome(success = true, data = data)
    }

    // ═══════════════════════════════════════════════════════════════════
    // PLAYER-FACING KNOWLEDGE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Player asks about the world, system, lore, etc.
     * Aggregates info from world seed, location, quests, and game state
     * so the GM can answer in-character.
     */
    private fun askWorld(question: String, state: GameState): ToolOutcome {
        val seed = state.seedId?.let { WorldSeeds.byId(it) }
        val data = buildJsonObject {
            put("question", JsonPrimitive(question))

            // World context
            putJsonObject("world") {
                if (seed != null) {
                    put("name", JsonPrimitive(seed.displayName))
                    put("tagline", JsonPrimitive(seed.tagline))
                    putJsonObject("powerSystem") {
                        put("name", JsonPrimitive(seed.powerSystem.name))
                        put("source", JsonPrimitive(seed.powerSystem.source))
                        put("progression", JsonPrimitive(seed.powerSystem.progression))
                        put("uniqueMechanic", JsonPrimitive(seed.powerSystem.uniqueMechanic))
                        put("limitations", JsonPrimitive(seed.powerSystem.limitations))
                    }
                    putJsonObject("worldState") {
                        put("era", JsonPrimitive(seed.worldState.era))
                        put("civilizationStatus", JsonPrimitive(seed.worldState.civilizationStatus))
                        putJsonArray("threats") { seed.worldState.threats.forEach { add(JsonPrimitive(it)) } }
                        put("atmosphere", JsonPrimitive(seed.worldState.atmosphere))
                    }
                    putJsonArray("tone") { seed.tone.forEach { add(JsonPrimitive(it)) } }
                    putJsonArray("themes") { seed.themes.forEach { add(JsonPrimitive(it)) } }
                    put("systemVoicePersonality", JsonPrimitive(seed.systemVoice.personality))
                } else {
                    put("name", JsonPrimitive(state.worldSettings.worldName))
                    put("concept", JsonPrimitive(state.worldSettings.coreConcept))
                }
            }

            // Player's current context
            putJsonObject("playerContext") {
                put("name", JsonPrimitive(state.playerName))
                put("class", JsonPrimitive(state.characterSheet.playerClass.displayName))
                put("level", JsonPrimitive(state.playerLevel))
                put("grade", JsonPrimitive(state.characterSheet.currentGrade.displayName))
                put("location", JsonPrimitive(state.currentLocation.name))
                put("locationDescription", JsonPrimitive(state.currentLocation.description))
                put("backstory", JsonPrimitive(state.backstory))
            }

            // Available classes (for "what classes exist?" questions)
            putJsonArray("availableClasses") {
                PlayerClass.selectableClasses().forEach { cls ->
                    addJsonObject {
                        put("name", JsonPrimitive(cls.displayName))
                        put("archetype", JsonPrimitive(cls.archetype.displayName))
                        put("description", JsonPrimitive(cls.description))
                    }
                }
            }

            // Grade progression (for "how do I get stronger?" questions)
            putJsonArray("grades") {
                Grade.values().forEach { grade ->
                    addJsonObject {
                        put("name", JsonPrimitive(grade.displayName))
                        put("levelRange", JsonPrimitive("${grade.levelRange.first}-${grade.levelRange.last}"))
                        put("description", JsonPrimitive(grade.description))
                    }
                }
            }

            // Known skills
            putJsonArray("knownSkills") {
                state.characterSheet.skills.forEach { skill ->
                    addJsonObject {
                        put("name", JsonPrimitive(skill.name))
                        put("description", JsonPrimitive(skill.description))
                        put("level", JsonPrimitive(skill.level))
                    }
                }
            }

            // Active quests
            putJsonArray("activeQuests") {
                state.activeQuests.values.forEach { quest ->
                    addJsonObject {
                        put("name", JsonPrimitive(quest.name))
                        put("description", JsonPrimitive(quest.description))
                    }
                }
            }

            put("instruction", JsonPrimitive(
                "The player asked: '$question'. Use the world context above to answer IN CHARACTER " +
                "as the System/Announcer/narrator. Don't dump raw data — weave it into the world's voice. " +
                "If the question relates to game mechanics (classes, leveling, skills), explain them " +
                "through the world's power system, not as game rules."
            ))
        }
        return ToolOutcome(success = true, data = data)
    }

    /**
     * Generate a random character concept — name, backstory, appearance.
     * Useful for players who want inspiration or a quick start.
     */
    private fun generateCharacter(style: String?, state: GameState): ToolOutcome {
        val seed = state.seedId?.let { WorldSeeds.byId(it) }
        val worldName = seed?.displayName ?: state.worldSettings.worldName

        // Provide rich context so the GM can generate a fitting character
        val data = buildJsonObject {
            put("worldName", JsonPrimitive(worldName))
            if (seed != null) {
                put("worldTone", JsonPrimitive(seed.tone.joinToString(", ")))
                put("worldThemes", JsonPrimitive(seed.themes.joinToString(", ")))
                put("worldEra", JsonPrimitive(seed.worldState.era))
                put("civilizationStatus", JsonPrimitive(seed.worldState.civilizationStatus))
                putJsonArray("inspirations") { seed.inspirations.forEach { add(JsonPrimitive(it)) } }
            }
            if (!style.isNullOrBlank()) {
                put("requestedStyle", JsonPrimitive(style))
            }

            // Available classes for reference
            putJsonArray("availableClasses") {
                PlayerClass.selectableClasses().forEach { cls ->
                    addJsonObject {
                        put("name", JsonPrimitive(cls.displayName))
                        put("archetype", JsonPrimitive(cls.archetype.displayName))
                        put("description", JsonPrimitive(cls.description))
                    }
                }
            }

            put("instruction", JsonPrimitive(buildString {
                append("Generate a CHARACTER CONCEPT for the $worldName world. Return a JSON object with: ")
                append("name (fitting the world's tone), ")
                append("backstory (2-3 sentences — who they were BEFORE the story begins, their personality, what drives them), ")
                append("appearance (physical description for portrait generation — build, age, distinctive features, clothing). ")
                if (!style.isNullOrBlank()) {
                    append("The player requested style: '$style'. Honor this. ")
                }
                append("Make it specific and vivid — not generic fantasy. Ground it in this world's themes. ")
                append("The backstory should suggest a class archetype without naming it explicitly.")
            }))
        }
        return ToolOutcome(success = true, data = data)
    }

    // ═══════════════════════════════════════════════════════════════════
    // MULTIMODAL TRIGGERS
    // ═══════════════════════════════════════════════════════════════════

    private fun generateSceneArt(description: String, state: GameState): ToolOutcome {
        val data = buildJsonObject {
            put("description", JsonPrimitive(description))
            if (enableImageGeneration) {
                put("status", JsonPrimitive("generating"))
            } else {
                put("status", JsonPrimitive("skipped"))
                put("reason", JsonPrimitive("Image generation not available in this session"))
            }
        }
        if (!enableImageGeneration) {
            return ToolOutcome(success = true, data = data)
        }
        val event = GameEvent.SceneImage(imageData = ByteArray(0), description = description)
        return ToolOutcome(success = true, data = data, events = listOf(event))
    }

    private fun shiftMusicMood(mood: String, state: GameState): ToolOutcome {
        val event = GameEvent.MusicChange(mood = mood, audioData = null)
        val data = buildJsonObject {
            put("mood", JsonPrimitive(mood))
        }
        return ToolOutcome(success = true, data = data, events = listOf(event))
    }

    // ═══════════════════════════════════════════════════════════════════
    // BESTIARY
    // ═══════════════════════════════════════════════════════════════════

    private fun getBestiary(state: GameState): ToolOutcome {
        if (bestiary == null) {
            return ToolOutcome(success = true, data = buildJsonObject {
                put("message", JsonPrimitive("No bestiary available"))
                putJsonArray("enemies") {}
            })
        }

        val zoneId = state.currentLocation.id
        val enemies = bestiary.getEnemiesForZone(zoneId)
        val boss = bestiary.getBossForZone(zoneId)

        val data = buildJsonObject {
            put("zoneId", JsonPrimitive(zoneId))
            put("zoneName", JsonPrimitive(state.currentLocation.name))
            put("enemyCount", JsonPrimitive(enemies.size))
            putJsonArray("enemies") {
                enemies.filter { !it.isBoss }.forEach { tmpl ->
                    add(buildJsonObject {
                        put("name", JsonPrimitive(tmpl.name))
                        put("levelRange", JsonPrimitive("${tmpl.levelRange.first}-${tmpl.levelRange.last}"))
                        put("danger", JsonPrimitive(tmpl.baseDanger))
                        put("description", JsonPrimitive(tmpl.description))
                        put("lootTier", JsonPrimitive(tmpl.lootTier))
                        tmpl.portraitResource?.let { put("portraitResource", JsonPrimitive(it)) }
                        if (tmpl.immunities.isNotEmpty()) putJsonArray("immunities") {
                            tmpl.immunities.forEach { add(JsonPrimitive(it.name)) }
                        }
                        if (tmpl.vulnerabilities.isNotEmpty()) putJsonArray("vulnerabilities") {
                            tmpl.vulnerabilities.forEach { add(JsonPrimitive(it.name)) }
                        }
                        if (tmpl.resistances.isNotEmpty()) putJsonArray("resistances") {
                            tmpl.resistances.forEach { add(JsonPrimitive(it.name)) }
                        }
                        putJsonArray("abilities") {
                            tmpl.abilities.filter { it.unlockPhase == 1 }.forEach { ab ->
                                add(buildJsonObject {
                                    put("name", JsonPrimitive(ab.name))
                                    put("description", JsonPrimitive(ab.description))
                                    put("damageType", JsonPrimitive(ab.damageType.name))
                                })
                            }
                        }
                    })
                }
            }
            if (boss != null) {
                put("boss", buildJsonObject {
                    put("name", JsonPrimitive(boss.name))
                    put("level", JsonPrimitive(boss.levelRange.last))
                    put("danger", JsonPrimitive(boss.baseDanger))
                    put("description", JsonPrimitive(boss.description))
                    boss.portraitResource?.let { put("portraitResource", JsonPrimitive(it)) }
                    put("phases", JsonPrimitive(boss.bossPhases.size))
                    putJsonArray("phaseDescriptions") {
                        boss.bossPhases.forEach { phase ->
                            add(buildJsonObject {
                                put("phase", JsonPrimitive(phase.phase))
                                put("hpThreshold", JsonPrimitive("${(phase.hpThreshold * 100).toInt()}%"))
                                put("description", JsonPrimitive(phase.description))
                            })
                        }
                    }
                })
            }
        }
        return ToolOutcome(success = true, data = data)
    }

    // ═══════════════════════════════════════════════════════════════════
    // TOOL DEFINITIONS
    // ═══════════════════════════════════════════════════════════════════

    override fun getToolDefinitions(): List<UnifiedToolDef> = listOf(
        // State queries
        UnifiedToolDef("get_player_stats", "Get player level, HP, XP, mana, energy, location, and class"),
        UnifiedToolDef("get_character_sheet", "Get full character sheet with stats, skills, equipment, status effects"),
        UnifiedToolDef("get_inventory", "Get player's inventory items and capacity"),
        UnifiedToolDef("get_location", "Get current location details, features, and connections"),
        UnifiedToolDef("get_connected_locations", "Get locations accessible from current location"),
        UnifiedToolDef("get_npcs_here", "Get NPCs at the player's current location"),
        UnifiedToolDef("get_active_quests", "Get all active quests and their progress"),
        UnifiedToolDef("get_quest_details", "Get detailed info about a specific quest", listOf(
            ToolParam("questId", "string", "ID of the quest")
        )),
        UnifiedToolDef("find_npc", "Find an NPC by name at current location", listOf(
            ToolParam("name", "string", "Name of the NPC to find")
        )),
        UnifiedToolDef("get_npc_shop", "Get shop inventory for an NPC", listOf(
            ToolParam("npcId", "string", "ID of the NPC with a shop")
        )),
        UnifiedToolDef("get_event_summary", "Get summary of game progress"),
        UnifiedToolDef("get_combat_targets", "Get info about potential combat targets at current location"),
        UnifiedToolDef("get_tutorial_state", "Check tutorial progress and class selection status"),
        UnifiedToolDef("get_story_state", "Get current story/narrative state"),
        UnifiedToolDef("get_narration_context", "Get writing guidance for the current scene: tone, NPC voices, what to emphasize, what to avoid. Call this before narrating important moments."),

        // Lore
        UnifiedToolDef("query_lore", "Look up game rules, classes, skills, world info", listOf(
            ToolParam("category", "string", "Category: classes, skills, world, tutorial, biomes, npc_archetypes, quest_templates, loot, progression, system_voice"),
            ToolParam("filter", "string", "Optional filter within category", required = false)
        )),

        // Combat — Multi-round system
        UnifiedToolDef("start_combat", "Start combat with an enemy. Creates the enemy with HP, stats, and abilities based on danger level. Uses bestiary templates when available for lore-accurate enemies. MUST be called before combat_attack.", listOf(
            ToolParam("enemyName", "string", "Name of the enemy (e.g. 'Bone Crawler', 'Caldera Tyrant'). Use get_bestiary to see available enemies."),
            ToolParam("danger", "integer", "Danger level 1-10 (1=rat, 3=goblin, 5=elite, 8+=boss). Scales HP, damage, abilities. Ignored if bestiary match found.", required = false),
            ToolParam("description", "string", "Brief description for narration", required = false),
            ToolParam("zoneId", "string", "Zone ID to look up the enemy in the bestiary (default: current location)", required = false)
        )),
        UnifiedToolDef("get_bestiary", "Get enemies available in the current zone from the bestiary. Shows regular enemies and boss with abilities and descriptions. Use this to know what to spawn."),
        UnifiedToolDef("combat_attack", "Execute one round of basic attack. Player swings, enemy responds. Returns hit/miss, damage, HP for both sides. Call repeatedly for multi-round fights."),
        UnifiedToolDef("combat_use_skill", "Use a skill in combat. Spends resources, applies effects (damage, heal, buff, debuff, DoT), enemy responds. Returns full round results.", listOf(
            ToolParam("skillId", "string", "ID of the skill to use (check readySkills in combat status)")
        )),
        UnifiedToolDef("combat_flee", "Attempt to flee combat. Success based on DEX vs enemy speed. On failure, enemy gets a free attack."),
        UnifiedToolDef("combat_use_item", "Use a consumable item during combat (potion, scroll, etc.). Costs your action — enemy gets to counter-attack.", listOf(
            ToolParam("itemId", "string", "ID of the consumable item to use")
        )),
        UnifiedToolDef("get_combat_status", "Get current combat state: enemy HP/condition, player resources, available skills, status effects, round number."),
        UnifiedToolDef("skill_check", "Perform a non-combat d20 skill check (Investigation, Perception, Persuasion, Stealth, etc.). Returns roll, modifiers, degree of success/failure.", listOf(
            ToolParam("checkType", "string", "Check type: ${SkillCheckType.entries.joinToString(", ") { it.displayName }}"),
            ToolParam("difficulty", "string", "Difficulty: TRIVIAL (DC 5), EASY (DC 8), MODERATE (DC 12), HARD (DC 15), VERY_HARD (DC 18), NEARLY_IMPOSSIBLE (DC 22)")
        )),
        UnifiedToolDef("attack_target", "Legacy: auto-starts combat and resolves one round. Prefer start_combat + combat_attack for tactical fights.", listOf(
            ToolParam("target", "string", "Name of the enemy to attack"),
            ToolParam("danger", "integer", "Danger level 1-10", required = false)
        )),
        UnifiedToolDef("use_skill", "Use a character skill. If in combat, routes to combat_use_skill automatically.", listOf(
            ToolParam("skillId", "string", "ID of the skill to use"),
            ToolParam("target", "string", "Target for the skill", required = false)
        )),

        // Movement
        UnifiedToolDef("move_to_location", "Move player to a location. If the location doesn't exist yet, it will be dynamically created and connected. Use for dungeon floors, new areas, etc.", listOf(
            ToolParam("locationName", "string", "Name of the location to move to (e.g. 'Floor Two - The Gauntlet')")
        )),

        // NPC
        UnifiedToolDef("talk_to_npc", "Start or continue conversation with an NPC", listOf(
            ToolParam("npcName", "string", "Name of NPC to talk to"),
            ToolParam("dialogue", "string", "What the player says", required = false)
        )),
        UnifiedToolDef("purchase_from_shop", "Buy an item from an NPC's shop", listOf(
            ToolParam("npcId", "string", "NPC ID"),
            ToolParam("itemId", "string", "Item to purchase"),
            ToolParam("quantity", "integer", "How many to buy", required = false)
        )),
        UnifiedToolDef("sell_to_shop", "Sell an item to an NPC's shop", listOf(
            ToolParam("npcId", "string", "NPC ID"),
            ToolParam("itemId", "string", "Inventory item ID"),
            ToolParam("quantity", "integer", "How many to sell", required = false)
        )),

        // Items
        UnifiedToolDef("use_item", "Use a consumable item from inventory", listOf(
            ToolParam("itemId", "string", "ID of the item to use")
        )),
        UnifiedToolDef("equip_item", "Equip an item from inventory", listOf(
            ToolParam("itemId", "string", "ID of the item to equip")
        )),
        UnifiedToolDef("add_item", "Give the player a found/received/picked-up item. MUST be called whenever the player gains an item outside of combat or shops.", listOf(
            ToolParam("itemName", "string", "Name of the item"),
            ToolParam("description", "string", "Brief description"),
            ToolParam("quantity", "integer", "Number of items (default 1)", required = false),
            ToolParam("rarity", "string", "COMMON, UNCOMMON, RARE, EPIC, LEGENDARY (default COMMON)", required = false)
        )),
        UnifiedToolDef("add_gold", "Award gold to the player. MUST be called whenever the player gains gold outside of combat.", listOf(
            ToolParam("amount", "integer", "Amount of gold to add")
        )),

        // Quests
        UnifiedToolDef("accept_quest", "Accept a quest", listOf(
            ToolParam("questId", "string", "ID of quest to accept")
        )),
        UnifiedToolDef("update_quest_progress", "Update progress on a quest objective", listOf(
            ToolParam("questId", "string", "Quest ID"),
            ToolParam("objectiveId", "string", "Objective ID"),
            ToolParam("progress", "integer", "New progress value")
        )),
        UnifiedToolDef("complete_quest", "Complete a quest if all objectives are met", listOf(
            ToolParam("questId", "string", "ID of quest to complete")
        )),

        // Character
        UnifiedToolDef("set_player_name", "Set the player character's name", listOf(
            ToolParam("name", "string", "Character name chosen by the player")
        )),
        UnifiedToolDef("set_backstory", "Set or update the player character's backstory", listOf(
            ToolParam("backstory", "string", "The character's backstory, woven from the player's description")
        )),
        UnifiedToolDef("suggest_classes", "Generate 3 unique class options based on the player's backstory and world. Uses AI to create personalized classes with traits, evolution paths, and physical mutations. Call this BEFORE set_class to give the player options.", emptyList()),
        UnifiedToolDef("suggest_skills", "Generate skill options for the player. With no context: generates 4-5 starter skills for current class. With context: generates a custom skill from the player's description.", listOf(
            ToolParam("context", "string", "Optional: player's description of a skill they want. If omitted, generates starter skill options for their class.", required = false)
        )),
        UnifiedToolDef("set_class", "Set the player's class. ANY name works — standard or fully custom. System resolves the closest archetype for stat math. After setting class, call suggest_skills to get skill options for the player to choose from.", listOf(
            ToolParam("className", "string", "Class name — anything goes. Standard names (Slayer, Channeler, Cultivator) or fully custom (Remnant, Blood Weaver, Void Eater, Gourmand). System auto-resolves base archetype for stats."),
            ToolParam("description", "string", "Class description — what this class IS and how it feels. 1-2 sentences.", required = false),
            ToolParam("traits", "string", "Comma-separated class traits. Format: 'Trait Name: description, Trait Name: description'. These are passive bonuses or unique abilities. E.g. 'Night Vision: See in complete darkness, Iron Stomach: Immune to poison from food'", required = false),
            ToolParam("evolutionHints", "string", "Comma-separated hints for how this class could evolve at higher levels. E.g. 'Could become Blood Lord, Could become Crimson Knight'", required = false),
            ToolParam("physicalChanges", "string", "Comma-separated physical mutations/evolutions this class causes. E.g. 'Eyes glow faintly red, Veins darken visibly when using abilities'", required = false)
        )),
        UnifiedToolDef("set_profession", "Set the player's profession (crafting/gathering/utility specialization)", listOf(
            ToolParam("professionName", "string", "Profession name. Options: ${Profession.selectableProfessions().joinToString(", ") { it.displayName }}")
        )),
        UnifiedToolDef("add_xp", "Award XP to the player", listOf(
            ToolParam("amount", "integer", "Amount of XP to award")
        )),
        UnifiedToolDef("allocate_stat_points", "Spend unspent stat points to increase player attributes. Points are earned on grade advancement (D=10, C=20, B=30, A=50, S=100). Player chooses how to distribute.", listOf(
            ToolParam("strength", "integer", "Points to add to STR", required = false),
            ToolParam("dexterity", "integer", "Points to add to DEX", required = false),
            ToolParam("constitution", "integer", "Points to add to CON", required = false),
            ToolParam("intelligence", "integer", "Points to add to INT", required = false),
            ToolParam("wisdom", "integer", "Points to add to WIS", required = false),
            ToolParam("charisma", "integer", "Points to add to CHA", required = false)
        )),
        UnifiedToolDef("grant_skill", "Grant a skill to the player. Use known skill IDs (power_strike, quick_slash, fireball, etc.) or create custom ones with skillName.", listOf(
            ToolParam("skillId", "string", "Skill ID from the skill database, or a unique ID for a new skill"),
            ToolParam("skillName", "string", "Display name for a custom skill (required if skillId not in database)", required = false),
            ToolParam("description", "string", "Description for a custom skill", required = false)
        )),
        UnifiedToolDef("complete_tutorial", "Mark the tutorial as complete"),

        // Plot graph
        UnifiedToolDef("create_plot_node", "Create a dynamic story beat in the plot graph. Use when the player's choices create new narrative threads.", listOf(
            ToolParam("title", "string", "Short title for the story beat"),
            ToolParam("description", "string", "What happens in this beat"),
            ToolParam("threadId", "string", "Plot thread ID (e.g. 'main-story', 'fellowship', 'rebellion')", required = false),
            ToolParam("beatType", "string", "REVELATION, CONFRONTATION, CHOICE, LOSS, VICTORY, BETRAYAL, REUNION, TRANSFORMATION, ESCALATION", required = false),
            ToolParam("triggerLevel", "integer", "Level when this should trigger (default: current level)", required = false)
        )),
        UnifiedToolDef("complete_plot_node", "Mark a plot node as completed", listOf(
            ToolParam("nodeId", "string", "ID of the plot node to complete")
        )),
        UnifiedToolDef("get_active_plot_nodes", "Get currently active and ready plot nodes"),

        // World generation
        UnifiedToolDef("spawn_npc", "Create a new NPC at a location", listOf(
            ToolParam("name", "string", "NPC name"),
            ToolParam("role", "string", "NPC role (merchant, guard, quest_giver, etc.)"),
            ToolParam("locationId", "string", "Location to spawn at (blank = current)", required = false)
        )),
        UnifiedToolDef("spawn_enemy", "Spawn an enemy for combat", listOf(
            ToolParam("name", "string", "Enemy name"),
            ToolParam("danger", "integer", "Danger level", required = false)
        )),

        // Multimodal
        UnifiedToolDef("generate_scene_art", "Generate an image of the current scene", listOf(
            ToolParam("description", "string", "Visual description of the scene")
        )),
        UnifiedToolDef("shift_music_mood", "Change background music mood", listOf(
            ToolParam("mood", "string", "Mood: peaceful, tense, battle, victory, mysterious, dark, epic")
        )),

        // Player-facing knowledge
        UnifiedToolDef("ask_world", "Answer a player's question about the world, system, lore, classes, progression, or mechanics. Returns world context so you can answer in-character.", listOf(
            ToolParam("question", "string", "The player's question about the world or system")
        )),
        UnifiedToolDef("generate_character", "Generate a random character concept (name, backstory, appearance) fitting the current world. Use when the player wants inspiration or a quick-start character.", listOf(
            ToolParam("style", "string", "Optional style hint: 'warrior', 'scholar', 'rogue', 'healer', or a freeform description", required = false)
        )),
    )
}

// ── Argument extraction helpers ──────────────────────────────────────

private fun Map<String, Any?>.str(key: String): String =
    this[key]?.toString() ?: ""

private fun Map<String, Any?>.strOpt(key: String): String? =
    this[key]?.toString()

private fun Map<String, Any?>.int(key: String, default: Int = 0): Int =
    (this[key] as? Number)?.toInt() ?: this[key]?.toString()?.toIntOrNull() ?: default

private fun Map<String, Any?>.long(key: String, default: Long = 0L): Long =
    (this[key] as? Number)?.toLong() ?: this[key]?.toString()?.toLongOrNull() ?: default
