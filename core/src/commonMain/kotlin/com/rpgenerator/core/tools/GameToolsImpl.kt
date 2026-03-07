package com.rpgenerator.core.tools

import com.rpgenerator.core.agents.LocationGeneratorAgent
import com.rpgenerator.core.agents.QuestGeneratorAgent
import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.domain.*
import com.rpgenerator.core.orchestration.Intent
import com.rpgenerator.core.rules.RulesEngine
import com.rpgenerator.core.util.currentTimeMillis
import kotlinx.coroutines.flow.toList

internal class GameToolsImpl(
    private val locationManager: LocationManager,
    private val rulesEngine: RulesEngine,
    private val locationGenerator: LocationGeneratorAgent,
    private val questGenerator: QuestGeneratorAgent,
    private val llm: LLMInterface? = null,
    private val eventLog: MutableList<GameEvent> = mutableListOf()
) : GameTools {

    override fun getPlayerStatus(state: GameState): PlayerStatusResult {
        return PlayerStatusResult(
            level = state.playerLevel,
            xp = state.playerXP,
            xpToNextLevel = (state.playerLevel + 1) * 100L,
            locationName = state.currentLocation.name,
            locationDanger = state.currentLocation.danger
        )
    }

    override fun getCurrentLocation(state: GameState): LocationDetails {
        // Get connected locations using current location directly
        val connected = state.currentLocation.connections.mapNotNull { connectionId ->
            locationManager.getLocation(connectionId, state)
        }

        return LocationDetails(
            id = state.currentLocation.id,
            name = state.currentLocation.name,
            description = state.currentLocation.description,
            danger = state.currentLocation.danger,
            features = state.currentLocation.features,
            lore = state.currentLocation.lore,
            connectedLocationNames = connected.map { it.name }
        )
    }

    override fun searchEventLog(
        state: GameState,
        query: String?,
        categories: List<com.rpgenerator.core.events.EventCategory>?,
        npcId: String?,
        locationId: String?,
        questId: String?,
        limit: Int
    ): List<com.rpgenerator.core.events.EventMetadata> {
        // Simple in-memory search - convert GameEvents to EventMetadata
        val filtered = eventLog.filter { event ->
            val metadata = com.rpgenerator.core.events.EventMetadata.fromGameEvent(event, state.gameId)

            // Filter by query
            val matchesQuery = query == null || metadata.searchableText.contains(query, ignoreCase = true)

            // Filter by categories
            val matchesCategory = categories == null || categories.contains(metadata.category)

            // Filter by NPC
            val matchesNpc = npcId == null || metadata.npcId == npcId

            // Filter by location
            val matchesLocation = locationId == null || metadata.locationId == locationId

            // Filter by quest
            val matchesQuest = questId == null || metadata.questId == questId

            matchesQuery && matchesCategory && matchesNpc && matchesLocation && matchesQuest
        }

        return filtered.takeLast(limit).map { event ->
            com.rpgenerator.core.events.EventMetadata.fromGameEvent(event, state.gameId)
        }
    }

    override fun getEventSummary(state: GameState, maxEvents: Int): com.rpgenerator.core.tools.EventSummaryResult {
        val recentEvents = eventLog.takeLast(maxEvents).map { event ->
            com.rpgenerator.core.events.EventMetadata.fromGameEvent(event, state.gameId)
        }

        val categoryCounts = recentEvents.groupBy { it.category.name }
            .mapValues { it.value.size.toLong() }

        val highlights = recentEvents
            .filter { it.importance != com.rpgenerator.core.events.EventImportance.LOW }
            .takeLast(5)
            .map { metadata ->
                com.rpgenerator.core.tools.EventHighlight(
                    category = metadata.category.name,
                    importance = metadata.importance.name,
                    text = metadata.searchableText,
                    timestamp = metadata.timestamp
                )
            }

        val summary = "Recent activity: ${recentEvents.size} events across ${categoryCounts.size} categories"

        return com.rpgenerator.core.tools.EventSummaryResult(
            totalEvents = eventLog.size.toLong(),
            categoryCounts = categoryCounts,
            recentHighlights = highlights,
            summary = summary
        )
    }

    override suspend fun analyzeIntent(
        input: String,
        state: GameState,
        recentEvents: List<GameEvent>
    ): IntentAnalysis {
        val llmInstance = llm ?: return fallbackIntent(input)

        val knownSkills = state.characterSheet.skills.joinToString(", ") { it.name }.ifEmpty { "none" }
        val hasClass = state.characterSheet.playerClass != PlayerClass.NONE
        val recentContext = recentEvents.takeLast(3).joinToString("; ") {
            when (it) {
                is GameEvent.NarratorText -> "Narration: ${it.text.take(80)}..."
                is GameEvent.SystemNotification -> "System: ${it.text.take(80)}"
                else -> it.toString().take(80)
            }
        }

        val prompt = """
Classify this player input into exactly one intent. Respond in EXACTLY this format, nothing else:
INTENT: <intent>
TARGET: <target or NONE>
REASONING: <brief reason>
SHOULD_GENERATE_LOCATION: <true or false>

Player input: "$input"

Game context:
- Location: ${state.currentLocation.name}
- Has class: $hasClass (${if (hasClass) state.characterSheet.playerClass.displayName else "NONE"})
- Known skills: $knownSkills
- Recent events: $recentContext

Valid intents (pick ONE):
- COMBAT: Player wants to fight, attack, or engage an enemy. TARGET = enemy name.
- NPC_DIALOGUE: Player wants to talk to, ask, or interact with a character. TARGET = NPC name.
- EXPLORATION: Player wants to look around, move, search, examine, or interact with the environment. Set SHOULD_GENERATE_LOCATION=true if they're actively searching/exploring new areas.
- CLASS_SELECTION: Player is choosing, asking about, or naming a class. Includes custom class names. Use this if they have no class and are responding to a class selection prompt.
- USE_SKILL: Player wants to use/cast/activate a specific known skill. TARGET = skill name. Only if it matches a known skill.
- SKILL_MENU: Player wants to VIEW or LIST their skills/abilities (not use them).
- SKILL_EVOLUTION: Player wants to evolve or upgrade a skill.
- SKILL_FUSION: Player wants to fuse/combine skills.
- SYSTEM_QUERY: Player wants to check their stats, status, or level — a quick info query.
- STATUS_MENU: Player wants a detailed character sheet view.
- INVENTORY_MENU: Player wants to view or manage their inventory/items/bag.
- QUEST_ACTION: Player is interacting with the quest system (checking quests, accepting, turning in).

Important: Match the INTENT to what the player actually wants to DO, not individual words. "I want to test my abilities on something" = EXPLORATION or COMBAT, not SKILL_MENU.
""".trim()

        val agent = llmInstance.startAgent(
            "You are an intent classifier for a LitRPG game. Respond ONLY in the exact format requested. Be concise."
        )
        val response = agent.sendMessage(prompt).toList().joinToString("")
        return parseIntentResponse(response)
    }

    private fun parseIntentResponse(response: String): IntentAnalysis {
        val lines = response.trim().lines()
        val intentLine = lines.find { it.startsWith("INTENT:") }
            ?: return IntentAnalysis(Intent.EXPLORATION, reasoning = "Failed to parse LLM response")

        val intentStr = intentLine.substringAfter(":").trim()
        val targetStr = lines.find { it.startsWith("TARGET:") }
            ?.substringAfter(":")?.trim()?.let { if (it == "NONE" || it.isBlank()) null else it }
        val reasoning = lines.find { it.startsWith("REASONING:") }
            ?.substringAfter(":")?.trim() ?: "LLM classified"
        val shouldGenerate = lines.find { it.startsWith("SHOULD_GENERATE_LOCATION:") }
            ?.substringAfter(":")?.trim()?.equals("true", ignoreCase = true) ?: false

        val intent = try {
            Intent.valueOf(intentStr)
        } catch (e: Exception) {
            Intent.EXPLORATION
        }

        return IntentAnalysis(
            intent = intent,
            target = targetStr,
            reasoning = reasoning,
            shouldGenerateLocation = shouldGenerate,
            locationGenerationContext = if (shouldGenerate) targetStr else null
        )
    }

    private fun fallbackIntent(input: String): IntentAnalysis {
        // Minimal fallback when no LLM is available (e.g. tests with mock that doesn't provide LLM)
        return IntentAnalysis(
            intent = Intent.EXPLORATION,
            reasoning = "No LLM available, defaulting to exploration"
        )
    }

    override fun resolveCombat(target: String, state: GameState): CombatResolution {
        val outcome = rulesEngine.calculateCombatOutcome(target, state)

        return CombatResolution(
            success = true,
            damage = outcome.damage,
            xpGained = outcome.xpGain,
            levelUp = outcome.levelUp,
            newLevel = outcome.newLevel,
            targetDefeated = true,
            loot = outcome.loot,
            gold = outcome.gold
        )
    }

    override fun validateAction(
        intent: Intent,
        target: String?,
        state: GameState
    ): ActionValidation {
        return when (intent) {
            Intent.COMBAT -> {
                if (target == null) {
                    ActionValidation(false, "No combat target specified")
                } else if (state.currentLocation.danger > state.playerLevel + 5) {
                    ActionValidation(false, "Location far too dangerous for player level")
                } else {
                    ActionValidation(true)
                }
            }
            else -> ActionValidation(true)
        }
    }

    override suspend fun generateLocation(
        parentLocation: Location,
        discoveryContext: String,
        state: GameState
    ): Location? {
        return locationGenerator.generateLocation(parentLocation, discoveryContext, state)
    }

    override fun getConnectedLocations(state: GameState): List<Location> {
        // Use current location directly to get its connections, rather than looking it up by ID
        // This ensures we use the latest version of the location including any runtime updates
        return state.currentLocation.connections.mapNotNull { connectionId ->
            locationManager.getLocation(connectionId, state)
        }
    }

    override fun getCharacterSheet(state: GameState): CharacterSheetDetails {
        val sheet = state.characterSheet
        val effective = sheet.effectiveStats()

        return CharacterSheetDetails(
            level = sheet.level,
            xp = sheet.xp,
            xpToNextLevel = sheet.xpToNextLevel(),
            baseStats = sheet.baseStats,
            effectiveStats = effective,
            currentHP = sheet.resources.currentHP,
            maxHP = sheet.resources.maxHP,
            currentMana = sheet.resources.currentMana,
            maxMana = sheet.resources.maxMana,
            currentEnergy = sheet.resources.currentEnergy,
            maxEnergy = sheet.resources.maxEnergy,
            skills = sheet.skills.map { skill ->
                SkillInfo(
                    id = skill.id,
                    name = skill.name,
                    description = skill.description,
                    manaCost = skill.manaCost,
                    energyCost = skill.energyCost,
                    level = skill.level
                )
            },
            equipment = EquipmentInfo(
                weapon = sheet.equipment.weapon?.name,
                armor = sheet.equipment.armor?.name,
                accessory = sheet.equipment.accessory?.name
            ),
            statusEffects = sheet.statusEffects.map { effect ->
                StatusEffectInfo(
                    name = effect.name,
                    description = effect.description,
                    turnsRemaining = effect.duration
                )
            }
        )
    }

    override fun getEffectiveStats(state: GameState): Stats {
        return state.characterSheet.effectiveStats()
    }

    override fun equipItem(itemId: String, state: GameState): EquipmentResult {
        val item = state.characterSheet.inventory.items[itemId]

        if (item == null) {
            return EquipmentResult(
                success = false,
                message = "Item not found in inventory",
                equipped = null
            )
        }

        // For POC, we'll need to expand this to convert inventory items to equipment
        // For now, just return not implemented
        return EquipmentResult(
            success = false,
            message = "Equipment system not yet fully implemented",
            equipped = null
        )
    }

    override fun useItem(itemId: String, quantity: Int, state: GameState): ItemUseResult {
        if (!state.characterSheet.inventory.hasItem(itemId, quantity)) {
            return ItemUseResult(
                success = false,
                message = "Insufficient quantity of item",
                effect = null
            )
        }

        val item = state.characterSheet.inventory.items[itemId]
            ?: return ItemUseResult(
                success = false,
                message = "Item not found",
                effect = null
            )

        // For POC, basic consumable logic
        if (item.type == ItemType.CONSUMABLE) {
            return ItemUseResult(
                success = true,
                message = "Used ${item.name}",
                effect = "Item use effects not yet implemented"
            )
        }

        return ItemUseResult(
            success = false,
            message = "Item cannot be used",
            effect = null
        )
    }

    override fun getInventory(state: GameState): InventoryDetails {
        val inventory = state.characterSheet.inventory

        return InventoryDetails(
            items = inventory.items.values.map { item ->
                InventoryItemInfo(
                    id = item.id,
                    name = item.name,
                    description = item.description,
                    type = item.type.name,
                    quantity = item.quantity
                )
            },
            usedSlots = inventory.items.size,
            maxSlots = inventory.maxSlots
        )
    }

    override fun getNPCsAtLocation(state: GameState): List<NPCInfo> {
        return state.getNPCsAtCurrentLocation().map { npc ->
            val relationship = npc.getRelationship(state.gameId)
            NPCInfo(
                id = npc.id,
                name = npc.name,
                archetype = npc.archetype.name,
                hasShop = npc.shop != null,
                hasQuests = npc.questIds.isNotEmpty(),
                relationshipStatus = relationship.getStatus().name
            )
        }
    }

    override fun findNPCByName(name: String, state: GameState): NPCDetails? {
        val npc = state.findNPCByName(name) ?: return null
        val relationship = npc.getRelationship(state.gameId)

        return NPCDetails(
            id = npc.id,
            name = npc.name,
            archetype = npc.archetype.name,
            personality = "${npc.personality.traits.joinToString(", ")} - ${npc.personality.speechPattern}",
            hasShop = npc.shop != null,
            hasQuests = npc.questIds.isNotEmpty(),
            relationship = RelationshipInfo(
                affinity = relationship.affinity,
                status = relationship.getStatus().name
            ),
            recentConversations = npc.getRecentConversations(3).map { entry ->
                "Player: ${entry.playerInput}\n${npc.name}: ${entry.npcResponse}"
            }
        )
    }

    override fun getNPCShop(npcId: String, state: GameState): ShopDetails? {
        val npc = state.findNPC(npcId) ?: return null
        val shop = npc.shop ?: return null

        return ShopDetails(
            shopName = shop.name,
            npcName = npc.name,
            items = shop.inventory.map { item ->
                ShopItemInfo(
                    id = item.id,
                    name = item.name,
                    description = item.description,
                    price = item.price,
                    stock = item.stock,
                    requiredLevel = item.requiredLevel,
                    requiredRelationship = item.requiredRelationship
                )
            },
            currency = shop.currency
        )
    }

    override fun purchaseFromShop(
        npcId: String,
        itemId: String,
        quantity: Int,
        state: GameState
    ): ShopTransactionResult {
        val npc = state.findNPC(npcId)
            ?: return ShopTransactionResult(false, "NPC not found")

        val shop = npc.shop
            ?: return ShopTransactionResult(false, "This NPC doesn't have a shop")

        val item = shop.getItem(itemId)
            ?: return ShopTransactionResult(false, "Item not found in shop")

        // Check level requirement
        if (item.requiredLevel > state.playerLevel) {
            return ShopTransactionResult(
                false,
                "You need to be level ${item.requiredLevel} to purchase this item"
            )
        }

        // Check relationship requirement
        val relationship = npc.getRelationship(state.gameId)
        if (relationship.affinity < item.requiredRelationship) {
            return ShopTransactionResult(
                false,
                "You need a better relationship with ${npc.name} to purchase this item"
            )
        }

        // Check stock
        if (item.stock >= 0 && item.stock < quantity) {
            return ShopTransactionResult(
                false,
                "Only ${item.stock} in stock"
            )
        }

        // For now, we'll return success without actually implementing gold/currency system
        // This would need to be extended when currency is added to CharacterSheet
        val totalCost = item.price * quantity

        return ShopTransactionResult(
            success = true,
            message = "Successfully purchased ${item.name} x$quantity for $totalCost ${shop.currency}",
            goldChange = -totalCost,
            itemReceived = item.name
        )
    }

    override fun sellToShop(
        npcId: String,
        inventoryItemId: String,
        quantity: Int,
        state: GameState
    ): ShopTransactionResult {
        val npc = state.findNPC(npcId)
            ?: return ShopTransactionResult(false, "NPC not found")

        val shop = npc.shop
            ?: return ShopTransactionResult(false, "This NPC doesn't have a shop")

        val playerItem = state.characterSheet.inventory.items[inventoryItemId]
            ?: return ShopTransactionResult(false, "You don't have this item")

        if (playerItem.quantity < quantity) {
            return ShopTransactionResult(
                false,
                "You only have ${playerItem.quantity} of this item"
            )
        }

        // Calculate sell value (typically a percentage of shop price)
        // For now, use a default value since we don't have shop pricing for player items
        val sellValue = 10 * quantity // Placeholder value

        return ShopTransactionResult(
            success = true,
            message = "Sold ${playerItem.name} x$quantity for $sellValue ${shop.currency}",
            goldChange = sellValue
        )
    }

    override fun getToolDefinitions(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                name = "get_player_status",
                description = "Get current player level, XP, and location info",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "get_current_location",
                description = "Get detailed information about the current location",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "search_event_log",
                description = "Search through past game events for context",
                parameters = mapOf(
                    "query" to ParameterDefinition(
                        type = "string",
                        description = "Search term to filter events",
                        required = false
                    ),
                    "limit" to ParameterDefinition(
                        type = "integer",
                        description = "Maximum number of events to return",
                        required = false
                    )
                )
            ),
            ToolDefinition(
                name = "analyze_intent",
                description = "Determine what the player is trying to do",
                parameters = mapOf(
                    "input" to ParameterDefinition(
                        type = "string",
                        description = "Player's input text",
                        required = true
                    )
                )
            ),
            ToolDefinition(
                name = "resolve_combat",
                description = "Calculate combat outcome",
                parameters = mapOf(
                    "target" to ParameterDefinition(
                        type = "string",
                        description = "Enemy being attacked",
                        required = true
                    )
                )
            ),
            ToolDefinition(
                name = "get_character_sheet",
                description = "Get complete character sheet including stats, equipment, skills, and status effects",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "get_effective_stats",
                description = "Get character stats including all bonuses from equipment and status effects",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "get_inventory",
                description = "Get player's inventory with all items",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "equip_item",
                description = "Equip an item from inventory",
                parameters = mapOf(
                    "itemId" to ParameterDefinition(
                        type = "string",
                        description = "ID of the item to equip",
                        required = true
                    )
                )
            ),
            ToolDefinition(
                name = "use_item",
                description = "Use a consumable item from inventory",
                parameters = mapOf(
                    "itemId" to ParameterDefinition(
                        type = "string",
                        description = "ID of the item to use",
                        required = true
                    ),
                    "quantity" to ParameterDefinition(
                        type = "integer",
                        description = "Quantity to use",
                        required = false
                    )
                )
            )
        )
    }

    // Quest tool implementations
    override fun getActiveQuests(state: GameState): List<QuestInfo> {
        return state.activeQuests.values.map { quest ->
            val totalObjectives = quest.objectives.size
            val completedObjectives = quest.objectives.count { it.isComplete() }
            val completionPercentage = if (totalObjectives > 0) {
                (completedObjectives * 100) / totalObjectives
            } else 0

            QuestInfo(
                id = quest.id,
                name = quest.name,
                type = quest.type.name,
                status = quest.status.name,
                completionPercentage = completionPercentage
            )
        }
    }

    override fun getQuestDetails(questId: String, state: GameState): QuestDetails? {
        val quest = state.activeQuests[questId] ?: return null

        return QuestDetails(
            id = quest.id,
            name = quest.name,
            description = quest.description,
            type = quest.type.name,
            status = quest.status.name,
            objectives = quest.objectives.map { obj ->
                QuestObjectiveInfo(
                    description = obj.progressDescription(),
                    currentProgress = obj.currentProgress,
                    targetProgress = obj.targetProgress,
                    isComplete = obj.isComplete()
                )
            },
            rewards = QuestRewardInfo(
                xp = quest.rewards.xp,
                items = quest.rewards.items.map { it.name },
                gold = quest.rewards.gold,
                unlocksLocations = quest.rewards.unlockedLocationIds
            ),
            canComplete = quest.isComplete()
        )
    }

    override suspend fun generateNPC(
        name: String,
        role: String,
        locationId: String,
        personalityTraits: List<String>,
        backstory: String,
        motivations: List<String>,
        relationshipToPlayer: String
    ): NPCGenerationResult {
        // Validate inputs
        if (name.isBlank()) {
            return NPCGenerationResult(
                success = false,
                npc = null,
                message = "NPC name cannot be blank"
            )
        }

        if (locationId.isBlank()) {
            return NPCGenerationResult(
                success = false,
                npc = null,
                message = "Location ID cannot be blank"
            )
        }

        // Map role to archetype
        val archetype = when (role.lowercase()) {
            "merchant", "trader", "shopkeeper" -> NPCArchetype.MERCHANT
            "quest_giver", "quest giver", "questgiver" -> NPCArchetype.QUEST_GIVER
            "guard", "soldier", "defender" -> NPCArchetype.GUARD
            "innkeeper", "barkeep" -> NPCArchetype.INNKEEPER
            "blacksmith", "weaponsmith", "armorer" -> NPCArchetype.BLACKSMITH
            "alchemist", "potionmaker" -> NPCArchetype.ALCHEMIST
            "trainer", "teacher", "instructor" -> NPCArchetype.TRAINER
            "noble", "lord", "lady" -> NPCArchetype.NOBLE
            "scholar", "researcher", "sage" -> NPCArchetype.SCHOLAR
            "wanderer", "traveler", "stranger", "rival", "ally" -> NPCArchetype.WANDERER
            else -> NPCArchetype.VILLAGER
        }

        // Create personality object
        val personality = NPCPersonality(
            traits = personalityTraits.ifEmpty { listOf("neutral", "ordinary") },
            speechPattern = "Normal speech pattern",
            motivations = motivations.ifEmpty { listOf("Survive in the new world") }
        )

        // Generate unique ID
        val npcId = "npc_gen_${locationId}_${role}_${currentTimeMillis()}"

        // Create NPC (returned for orchestrator to add to state)
        val npc = NPC(
            id = npcId,
            name = name,
            archetype = archetype,
            locationId = locationId,
            personality = personality,
            lore = backstory,
            greetingContext = "Dynamically generated NPC"
        )

        return NPCGenerationResult(
            success = true,
            npc = npc,
            message = "$name has been created as a $role at $locationId"
        )
    }

    override suspend fun generateQuest(
        state: GameState,
        questType: String?,
        context: String?
    ): Quest? {
        val type = questType?.let { typeStr ->
            try {
                QuestType.valueOf(typeStr.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        return questGenerator.generateQuest(state, type, context)
    }

    override fun checkQuestProgress(state: GameState): List<QuestProgressUpdate> {
        // This method is used to check if any quest objectives can be auto-completed
        // based on current game state. For now, return empty list.
        // This will be called by GameOrchestrator after each action.
        return emptyList()
    }

    fun logEvent(event: GameEvent) {
        eventLog.add(event)
    }

}
