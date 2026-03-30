package com.rpgenerator.server

import com.rpgenerator.core.api.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * MCP Streamable HTTP transport handler.
 * Implements the MCP protocol over HTTP POST/DELETE.
 *
 * Each MCP session maps to one game session.
 */
object McpHandler {

    private val log = LoggerFactory.getLogger("McpHandler")

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    // MCP session ID -> game session ID
    private val mcpSessions = ConcurrentHashMap<String, McpSessionState>()

    // Image cache for debug gallery: imageId -> GeneratedImage
    private val imageCache = ConcurrentHashMap<String, GeneratedImage>()
    private var imageCounter = 0

    data class GeneratedImage(
        val id: String,
        val type: String, // "portrait", "scene", "item"
        val label: String,
        val imageData: ByteArray,
        val mimeType: String,
        val prompt: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    /** Get all cached images for the debug gallery */
    fun getCachedImages(): List<GeneratedImage> =
        imageCache.values.sortedByDescending { it.timestamp }

    /** Get a single cached image by ID */
    fun getCachedImage(id: String): GeneratedImage? = imageCache[id]

    private const val MAX_CACHED_IMAGES = 100

    private fun cacheImage(type: String, label: String, imageData: ByteArray, mimeType: String, prompt: String): String {
        val id = "img_${++imageCounter}"
        imageCache[id] = GeneratedImage(id, type, label, imageData, mimeType, prompt)
        // Evict oldest entries when cache exceeds limit
        if (imageCache.size > MAX_CACHED_IMAGES) {
            val oldest = imageCache.values.sortedBy { it.timestamp }.take(imageCache.size - MAX_CACHED_IMAGES)
            oldest.forEach { imageCache.remove(it.id) }
        }
        return id
    }

    private class McpSessionState(
        var gameSessionId: String? = null,
        var gameCreated: Boolean = false,
        var gameStarted: Boolean = false,

        // Character creation state (set before start_game)
        var characterName: String? = null,
        var backstory: String? = null,
        var appearance: String? = null,
        var statAllocation: StatAllocation = StatAllocation.BALANCED,
        var startingClass: String? = null,
        var equipmentPreference: EquipmentPreference = EquipmentPreference.BALANCED,
        var avatarImageId: String? = null,

        // Game config (set during create_game)
        var systemType: SystemType = SystemType.SYSTEM_INTEGRATION,
        var seedId: String? = null,
        var difficulty: Difficulty = Difficulty.NORMAL
    ) {
        /** Check what's missing before the game can start */
        fun getMissingRequirements(): List<String> {
            val missing = mutableListOf<String>()
            if (!gameCreated) missing.add("game: Call create_game first to choose your world seed and system type")
            if (characterName.isNullOrBlank()) missing.add("characterName: Set a character name using set_character")
            if (backstory.isNullOrBlank()) missing.add("backstory: Provide a backstory using set_character")
            return missing
        }

        fun isReadyToStart(): Boolean = getMissingRequirements().isEmpty()
    }

    suspend fun handleRequest(call: RoutingCall) {
        val body = call.receiveText()
        log.debug("MCP request received, body length={}", body.length)
        val request = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            log.warn("MCP parse error: {}", e.message)
            call.respondText(
                json.encodeToString(JsonElement.serializer(),errorResponse(JsonNull, -32700, "Parse error: ${e.message}")),
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
            return
        }

        // Get or create MCP session (with persistence fallback)
        val mcpSessionId = call.request.header("Mcp-Session-Id")
            ?: call.request.header("mcp-session-id")
        val sessionState = if (mcpSessionId != null) {
            mcpSessions.getOrPut(mcpSessionId) {
                // Not in memory — try to restore from persistent store
                val persisted = SessionStore.load(mcpSessionId)
                if (persisted != null) {
                    val restored = McpSessionState(
                        gameCreated = persisted.gameCreated,
                        gameStarted = persisted.gameStarted,
                        characterName = persisted.characterName,
                        backstory = persisted.backstory,
                        appearance = persisted.appearance,
                        avatarImageId = persisted.avatarImageId,
                        seedId = persisted.seedId,
                        difficulty = try { Difficulty.valueOf(persisted.difficulty) } catch (_: Exception) { Difficulty.NORMAL },
                        systemType = try { SystemType.valueOf(persisted.systemType) } catch (_: Exception) { SystemType.SYSTEM_INTEGRATION }
                    )
                    // Resume the game engine session if game was started
                    if (persisted.gameStarted) {
                        try {
                            val gameSession = kotlinx.coroutines.runBlocking {
                                GameSessionManager.resumeSession(persisted)
                            }
                            if (gameSession != null) {
                                restored.gameSessionId = gameSession.id
                                log.info("Restored MCP session {} → game {}", mcpSessionId, gameSession.id)
                            } else {
                                // DB file missing or corrupt — reset to character creation
                                restored.gameStarted = false
                                restored.gameSessionId = null
                                log.warn("Could not resume game for session {} — reset to character creation", mcpSessionId)
                            }
                        } catch (e: Exception) {
                            restored.gameStarted = false
                            restored.gameSessionId = null
                            log.error("Error resuming game for session {}: {}", mcpSessionId, e.message, e)
                        }
                    }
                    restored
                } else {
                    McpSessionState()
                }
            }
        } else {
            McpSessionState()
        }

        val method = request["method"]?.jsonPrimitive?.content ?: ""
        val id = request["id"]
        log.info("MCP method={}, session={}, gameStarted={}", method, mcpSessionId?.take(8), sessionState.gameStarted)

        val methodStart = System.currentTimeMillis()
        val response = when (method) {
            "initialize" -> handleInitialize(request, sessionState)
            "notifications/initialized" -> null
            "tools/list" -> handleToolsList(id)
            "tools/call" -> handleToolsCall(id, request, sessionState, mcpSessionId)
            else -> mcpResponse(id, buildJsonObject {
                put("error", JsonPrimitive("Unknown method: $method"))
            })
        }
        val methodElapsed = System.currentTimeMillis() - methodStart
        if (methodElapsed > 100) {
            log.info("MCP method={} completed in {}ms", method, methodElapsed)
        }

        if (response != null) {
            // Assign session ID on initialize
            val responseSessionId = if (method == "initialize") {
                val newId = mcpSessionId ?: UUID.randomUUID().toString()
                mcpSessions[newId] = sessionState
                newId
            } else {
                mcpSessionId
            }

            call.response.header("Mcp-Session-Id", responseSessionId ?: "")
            call.respondText(
                json.encodeToString(JsonElement.serializer(),response),
                ContentType.Application.Json,
                HttpStatusCode.OK
            )
        } else {
            call.respond(HttpStatusCode.Accepted)
        }
    }

    suspend fun handleSessionDelete(call: RoutingCall) {
        val mcpSessionId = call.request.header("Mcp-Session-Id")
            ?: call.request.header("mcp-session-id")
        if (mcpSessionId != null) {
            val state = mcpSessions.remove(mcpSessionId)
            state?.gameSessionId?.let { GameSessionManager.removeSession(it) }
            SessionStore.delete(mcpSessionId)
        }
        call.respond(HttpStatusCode.OK)
    }

    // ── MCP Methods ─────────────────────────────────────────────────

    private fun handleInitialize(request: JsonObject, sessionState: McpSessionState): JsonObject {
        val id = request["id"]
        val clientVersion = request["params"]?.jsonObject?.get("protocolVersion")
            ?.jsonPrimitive?.content ?: "2025-03-26"

        return mcpResponse(id, buildJsonObject {
            put("protocolVersion", JsonPrimitive(clientVersion))
            putJsonObject("capabilities") {
                putJsonObject("tools") {
                    put("listChanged", JsonPrimitive(false))
                }
            }
            putJsonObject("serverInfo") {
                put("name", JsonPrimitive("rpgenerator"))
                put("version", JsonPrimitive("1.0.0"))
            }
        })
    }

    private fun handleToolsList(id: JsonElement?): JsonObject {
        return mcpResponse(id, buildJsonObject {
            putJsonArray("tools") {
                // ── Lifecycle tools (call in order: create_game → set_character → game_generate_portrait → start_game) ──
                addMCPTool("create_game",
                    "Step 1: Create a new game session. MUST be called before set_character or start_game. Call get_game_state first to see available worlds with full descriptions. Choose a seedId to set the world.",
                    mapOf(
                        "systemType" to ToolParam("string", "Game system type: SYSTEM_INTEGRATION, CULTIVATION_PATH, DEATH_LOOP, DUNGEON_DELVE, ARCANE_ACADEMY, TABLETOP_CLASSIC, EPIC_JOURNEY, HERO_AWAKENING"),
                        "seedId" to ToolParam("string", "World seed ID: integration (System Apocalypse — brutal survival, kill to level), tabletop (Classic Fantasy — D&D-style heroic adventure), crawler (Dungeon Crawler — dark comedy death game show), quiet_life (Cozy Apocalypse — rebuild after the wars)", required = true),
                        "difficulty" to ToolParam("string", "Difficulty: EASY, NORMAL, HARD, NIGHTMARE")
                    ),
                    required = listOf("seedId")
                )
                addMCPTool("set_character",
                    "Step 2: Set character narrative details. Call after create_game. Only set who the character IS — name, backstory (pure narrative: who they were before the story begins), and appearance. Class, stats, and equipment are chosen during the in-game tutorial. Can be called multiple times to update fields.",
                    mapOf(
                        "characterName" to ToolParam("string", "Character name"),
                        "backstory" to ToolParam("string", "Pure narrative backstory — who this person was before the story begins. No class/stat/game-mechanic info, just their personal history and personality."),
                        "appearance" to ToolParam("string", "Physical appearance description (used for portrait generation)")
                    ),
                    required = emptyList()
                )
                addMCPTool("start_game",
                    "Step 3: Start the game engine. Requires: create_game and set_character (name + backstory). Portrait is optional. The game begins with a tutorial where the player chooses their class and earns starting equipment. Call send_player_input after this succeeds.",
                    emptyMap()
                )
                addMCPTool("save_game",
                    "Save current game state to persistent storage",
                    emptyMap()
                )
                addMCPTool("abandon_game",
                    "Abandon the current game and reset the session. Saves the game first so it can be resumed later with load_game. After abandoning, call create_game to start a new adventure.",
                    mapOf(
                        "confirm" to ToolParam("boolean", "Must be true to confirm abandoning the game", required = true)
                    ),
                    required = listOf("confirm")
                )
                addMCPTool("list_saves",
                    "List all saved game sessions. Shows character name, world, level, and last played time. Use with load_game to resume a previous adventure.",
                    emptyMap()
                )
                addMCPTool("load_game",
                    "Load a previously saved game by its game ID (from list_saves). Replaces the current session's game with the saved one.",
                    mapOf(
                        "gameId" to ToolParam("string", "Game session ID from list_saves", required = true)
                    ),
                    required = listOf("gameId")
                )
                addMCPTool("get_game_state",
                    "Get current game state: player stats, location, NPCs, quests, inventory. Before start_game, returns character creation progress.",
                    emptyMap()
                )

                // Main gameplay tool
                addMCPTool("send_player_input",
                    "Send player input through the full game engine (AI agents, combat, narration). Returns all game events.",
                    mapOf("input" to ToolParam("string", "Player's action or dialogue", required = true)),
                    required = listOf("input")
                )

                // Direct game tools
                addMCPTool("game_get_player_stats", "Get player level, HP, XP, location, class", emptyMap())
                addMCPTool("game_get_inventory", "Get player inventory items", emptyMap())
                addMCPTool("game_get_active_quests", "Get active quests and progress", emptyMap())
                addMCPTool("game_get_npcs_here", "Get NPCs at current location", emptyMap())
                addMCPTool("game_get_location", "Get current location details", emptyMap())
                addMCPTool("game_get_character_sheet", "Get full character sheet with all stats and skills", emptyMap())
                addMCPTool("game_attack_target", "Attack an enemy in combat",
                    mapOf("target" to ToolParam("string", "Target name", required = true)), listOf("target"))
                addMCPTool("game_move_to_location", "Move to a connected location",
                    mapOf("locationName" to ToolParam("string", "Location to move to", required = true)), listOf("locationName"))
                addMCPTool("game_talk_to_npc", "Talk to an NPC at current location",
                    mapOf(
                        "npcName" to ToolParam("string", "NPC name", required = true),
                        "dialogue" to ToolParam("string", "What to say")
                    ), listOf("npcName"))
                addMCPTool("game_use_item", "Use an inventory item",
                    mapOf("itemId" to ToolParam("string", "Item ID", required = true)), listOf("itemId"))
                addMCPTool("game_use_skill", "Use a player skill",
                    mapOf(
                        "skillId" to ToolParam("string", "Skill ID", required = true),
                        "target" to ToolParam("string", "Target for the skill")
                    ), listOf("skillId"))
                addMCPTool("game_accept_quest", "Accept an offered quest",
                    mapOf("questId" to ToolParam("string", "Quest ID", required = true)), listOf("questId"))
                addMCPTool("game_complete_quest", "Complete a quest",
                    mapOf("questId" to ToolParam("string", "Quest ID", required = true)), listOf("questId"))
                addMCPTool("game_generate_scene_art", "Generate scene artwork using Gemini's native image generation. Returns base64-encoded image.",
                    mapOf(
                        "sceneDescription" to ToolParam("string", "Scene to depict", required = true),
                        "mood" to ToolParam("string", "Atmosphere: tense, serene, epic, mysterious, foreboding, etc."),
                        "timeOfDay" to ToolParam("string", "Time: dawn, morning, midday, dusk, night, etc."),
                        "weather" to ToolParam("string", "Weather: clear, rain, storm, fog, snow, etc.")
                    ), listOf("sceneDescription"))
                addMCPTool("game_shift_music_mood", "Shift background music mood",
                    mapOf(
                        "mood" to ToolParam("string", "Mood: tense, calm, epic, mysterious, etc.", required = true),
                        "intensity" to ToolParam("number", "Intensity 0.0-1.0")
                    ), listOf("mood"))
                addMCPTool("game_generate_location", "Generate a new location in the world",
                    mapOf("description" to ToolParam("string", "Location description", required = true)), listOf("description"))
                addMCPTool("game_generate_npc", "Generate a new NPC",
                    mapOf(
                        "name" to ToolParam("string", "NPC name", required = true),
                        "role" to ToolParam("string", "NPC role", required = true),
                        "personality" to ToolParam("string", "NPC personality", required = true)
                    ), listOf("name", "role", "personality"))
                addMCPTool("game_generate_item_art", "Generate an item illustration using Gemini's native image generation. Returns base64-encoded image.",
                    mapOf(
                        "itemName" to ToolParam("string", "Item name", required = true),
                        "itemDescription" to ToolParam("string", "Item description", required = true),
                        "rarity" to ToolParam("string", "Item rarity: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC")
                    ), listOf("itemName", "itemDescription"))
                addMCPTool("game_generate_portrait", "Generate the character's avatar portrait using Gemini's native image generation (optional). Uses appearance from set_character. Can be called again to regenerate.",
                    mapOf(
                        "characterName" to ToolParam("string", "Character name", required = true),
                        "appearance" to ToolParam("string", "Physical appearance description"),
                        "characterClass" to ToolParam("string", "Character class/archetype"),
                        "mood" to ToolParam("string", "Portrait mood: heroic, mysterious, battle-worn, serene, etc."),
                        "framing" to ToolParam("string", "Shot framing: BUST (chest up), FULL_BODY, CLOSE_UP")
                    ), listOf("characterName"))

                // Debug introspection tools
                addMCPTool("debug_get_agent_conversations",
                    "Get full conversation history for all AI agents (GameMaster, Narrator, NPCs, etc). Shows system prompts and every message exchanged. Use to verify agents are coordinating correctly and not contradicting each other.",
                    mapOf(
                        "agentName" to ToolParam("string", "Filter by agent name (e.g. 'Narrator', 'GameMaster'). Omit for all agents."),
                        "lastN" to ToolParam("number", "Only return the last N messages per agent. Omit for full history.")
                    )
                )
                addMCPTool("debug_get_event_log",
                    "Get ordered list of all GameEvents fired this session. Shows narrator text, NPC dialogue, combat logs, quest updates, system notifications, etc. Use to verify event ordering and find missing/duplicate events.",
                    mapOf(
                        "lastN" to ToolParam("number", "Only return the last N events. Omit for full log."),
                        "eventType" to ToolParam("string", "Filter by event type: NarratorText, NPCDialogue, SystemNotification, QuestUpdate, StatChange, CombatLog, ItemEvent, LevelUp, etc.")
                    )
                )
                addMCPTool("debug_get_plot_graph",
                    "Get the story plan: plot threads, nodes, edges, and completion state. Shows the narrative structure the engine is working from. Use to find unreachable plot nodes, broken quest chains, and dead narrative threads.",
                    emptyMap()
                )
                addMCPTool("debug_get_narrative_state",
                    "Compare what the narrator 'thinks' is happening vs actual game state. Shows narrator's system prompt, recent context sent to narrator, and current game state side-by-side. Use to catch narrator drift — describing things that don't match reality.",
                    emptyMap()
                )
                addMCPTool("debug_get_tool_log",
                    "Get chronological log of every tool call this session. Shows who called each tool (GM_AGENT vs EXTERNAL_MCP), what args were passed, whether it succeeded, timing, state changes, and events emitted. Essential for debugging why NPCs weren't spawned, skills weren't granted, etc.",
                    mapOf(
                        "lastN" to ToolParam("number", "Only return the last N entries. Omit for full log."),
                        "toolName" to ToolParam("string", "Filter by tool name (e.g. 'spawn_npc', 'set_class'). Omit for all tools."),
                        "caller" to ToolParam("string", "Filter by caller: GM_AGENT, EXTERNAL_MCP, EXTERNAL_REST. Omit for all."),
                        "failedOnly" to ToolParam("boolean", "If true, only show failed tool calls. Omit for all.")
                    )
                )

                // QA tools
                addMCPTool("report_bug",
                    "Report a bug found during testing. Writes a structured bug report to qa_tests/bug_reports/ as a JSON file. Use this whenever you encounter unexpected behavior, crashes, inconsistencies, or UX issues.",
                    mapOf(
                        "title" to ToolParam("string", "Brief title of the bug", required = true),
                        "description" to ToolParam("string", "Detailed description: what happened, what you expected, and what actually occurred", required = true),
                        "severity" to ToolParam("string", "Severity: critical (crash/data loss), high (broken feature), medium (wrong behavior), low (cosmetic/minor)"),
                        "category" to ToolParam("string", "Category: gameplay, combat, narrative, npc, quest, inventory, movement, ui, tool_api, state"),
                        "steps_to_reproduce" to ToolParam("string", "Step-by-step reproduction instructions"),
                        "tool_call" to ToolParam("string", "The MCP tool call that triggered the bug, if applicable"),
                        "game_state_context" to ToolParam("string", "Relevant game state at time of bug (level, location, quest, etc.)")
                    ),
                    required = listOf("title", "description")
                )
            }
        })
    }

    private suspend fun handleToolsCall(
        id: JsonElement?,
        request: JsonObject,
        sessionState: McpSessionState,
        mcpSessionId: String? = null
    ): JsonObject {
        val params = request["params"]?.jsonObject
        val toolName = params?.get("name")?.jsonPrimitive?.content ?: ""
        val args = params?.get("arguments")?.jsonObject ?: buildJsonObject {}
        log.info("TOOL CALL: {} args={}", toolName, args.keys)

        val toolStart = System.currentTimeMillis()
        return try {
            val result = executeTool(toolName, args, sessionState, mcpSessionId)
            val toolElapsed = System.currentTimeMillis() - toolStart
            log.info("TOOL DONE: {} in {}ms, result keys={}", toolName, toolElapsed, result.keys)

            // Extract image data if present, return as MCP image content block
            val imageBase64 = result["imageBase64"]?.jsonPrimitive?.contentOrNull
            val imageMimeType = result["mimeType"]?.jsonPrimitive?.contentOrNull

            // Build text result without the raw base64 blob (keep it clean for text display)
            val textResult = if (imageBase64 != null) {
                buildJsonObject {
                    result.entries.forEach { (k, v) ->
                        if (k != "imageBase64") put(k, v)
                    }
                    put("imageIncluded", JsonPrimitive(true))
                    put("imageSizeBytes", JsonPrimitive(imageBase64.length * 3 / 4)) // approx decoded size
                }
            } else {
                result
            }

            mcpResponse(id, buildJsonObject {
                putJsonArray("content") {
                    // Text metadata first
                    addJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(json.encodeToString(JsonElement.serializer(),textResult)))
                    }
                    // Image content block (Claude can see this directly)
                    if (imageBase64 != null && imageMimeType != null) {
                        addJsonObject {
                            put("type", JsonPrimitive("image"))
                            put("data", JsonPrimitive(imageBase64))
                            put("mimeType", JsonPrimitive(imageMimeType))
                        }
                    }
                }
            })
        } catch (e: Exception) {
            val toolElapsed = System.currentTimeMillis() - toolStart
            log.error("TOOL ERROR: {} after {}ms: {}", toolName, toolElapsed, e.message, e)
            mcpResponse(id, buildJsonObject {
                putJsonArray("content") {
                    addJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive("""{"error": "${e.message}"}"""))
                    }
                }
                put("isError", JsonPrimitive(true))
            })
        }
    }

    // ── Tool Execution ──────────────────────────────────────────────

    private suspend fun executeTool(
        name: String,
        args: JsonObject,
        sessionState: McpSessionState,
        mcpSessionId: String? = null
    ): JsonObject {
        when (name) {
            "create_game" -> {
                val wasAlreadyCreated = sessionState.gameCreated
                if (wasAlreadyCreated && sessionState.gameStarted) {
                    return buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("A game is already in progress. Cannot overwrite a started game in this session."))
                    }
                }
                sessionState.systemType = args["systemType"]?.jsonPrimitive?.contentOrNull?.let {
                    try { SystemType.valueOf(it.uppercase()) } catch (e: Exception) { null }
                } ?: SystemType.SYSTEM_INTEGRATION
                val requestedSeedId = args["seedId"]?.jsonPrimitive?.contentOrNull
                val validSeeds = listOf("integration", "tabletop", "crawler", "quiet_life")
                if (requestedSeedId != null && requestedSeedId !in validSeeds) {
                    return buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Invalid seedId '$requestedSeedId'."))
                        putJsonArray("validSeeds") { validSeeds.forEach { add(JsonPrimitive(it)) } }
                    }
                }
                sessionState.seedId = requestedSeedId ?: "integration"
                sessionState.difficulty = args["difficulty"]?.jsonPrimitive?.contentOrNull?.let {
                    try { Difficulty.valueOf(it.uppercase()) } catch (e: Exception) { null }
                } ?: Difficulty.NORMAL
                sessionState.gameCreated = true
                sessionState.gameStarted = false
                persistSession(mcpSessionId, sessionState)

                return buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("systemType", JsonPrimitive(sessionState.systemType.name))
                    put("seedId", JsonPrimitive(sessionState.seedId ?: "none"))
                    put("difficulty", JsonPrimitive(sessionState.difficulty.name))
                    putJsonArray("nextSteps") {
                        add(JsonPrimitive("1. Call set_character with characterName, backstory, and appearance"))
                        add(JsonPrimitive("2. Call game_generate_portrait to create the character's avatar"))
                        add(JsonPrimitive("3. Call start_game to begin playing"))
                    }
                    if (wasAlreadyCreated) {
                        put("warning", JsonPrimitive("Previous game setup was overwritten."))
                    }
                    put("message", JsonPrimitive("Game session created. Set up your character before starting."))
                }
            }

            "set_character" -> {
                if (!sessionState.gameCreated) {
                    return buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("No game created yet. Call create_game first to choose your world."))
                        putJsonArray("availableSeeds") {
                            add(buildJsonObject { put("id", JsonPrimitive("integration")); put("name", JsonPrimitive("System Apocalypse")); put("tagline", JsonPrimitive("Brutal survival. Kill to level. Earth merged with an infinite multiverse.")) })
                            add(buildJsonObject { put("id", JsonPrimitive("tabletop")); put("name", JsonPrimitive("Classic Fantasy")); put("tagline", JsonPrimitive("Roll for initiative. The Realm needs heroes.")) })
                            add(buildJsonObject { put("id", JsonPrimitive("crawler")); put("name", JsonPrimitive("Dungeon Crawler")); put("tagline", JsonPrimitive("Earth is gone. You're entertainment now. Make it a good show.")) })
                            add(buildJsonObject { put("id", JsonPrimitive("quiet_life")); put("name", JsonPrimitive("Cozy Apocalypse")); put("tagline", JsonPrimitive("The wars are over. Time to build something worth protecting.")) })
                        }
                    }
                }
                args["characterName"]?.jsonPrimitive?.contentOrNull?.let { sessionState.characterName = it }
                args["backstory"]?.jsonPrimitive?.contentOrNull?.let { sessionState.backstory = it }
                args["appearance"]?.jsonPrimitive?.contentOrNull?.let { sessionState.appearance = it }
                persistSession(mcpSessionId, sessionState)

                val missing = sessionState.getMissingRequirements()
                return buildJsonObject {
                    put("success", JsonPrimitive(true))
                    putJsonObject("character") {
                        put("name", JsonPrimitive(sessionState.characterName ?: ""))
                        put("backstory", JsonPrimitive(sessionState.backstory ?: ""))
                        put("appearance", JsonPrimitive(sessionState.appearance ?: ""))
                        put("hasAvatar", JsonPrimitive(sessionState.avatarImageId != null))
                    }
                    put("ready", JsonPrimitive(sessionState.isReadyToStart()))
                    if (missing.isNotEmpty()) {
                        putJsonArray("missing") { missing.forEach { add(JsonPrimitive(it)) } }
                    }
                    if (sessionState.avatarImageId == null && sessionState.appearance != null) {
                        put("hint", JsonPrimitive("Appearance set — now call game_generate_portrait to create the avatar."))
                    }
                }
            }

            "start_game" -> {
                if (sessionState.gameStarted) {
                    return buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("message", JsonPrimitive("Game already started. Use send_player_input to play."))
                    }
                }

                val missing = sessionState.getMissingRequirements()
                if (missing.isNotEmpty()) {
                    return buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("ready", JsonPrimitive(false))
                        putJsonArray("missing") { missing.forEach { add(JsonPrimitive(it)) } }
                        put("message", JsonPrimitive("Cannot start game. Complete character creation first."))
                    }
                }

                // Build character creation options from accumulated state
                val characterCreation = CharacterCreationOptions(
                    name = sessionState.characterName!!,
                    backstory = sessionState.backstory,
                    statAllocation = sessionState.statAllocation,
                    appearance = sessionState.appearance,
                    startingClass = sessionState.startingClass,
                    equipmentPreference = sessionState.equipmentPreference
                )

                val session = GameSessionManager.createSession(
                    sessionState.systemType,
                    sessionState.seedId,
                    sessionState.difficulty,
                    characterCreation
                )
                sessionState.gameSessionId = session.id
                sessionState.gameStarted = true
                persistSession(mcpSessionId, sessionState)

                // Trigger opening narration by sending blank input
                // GameOrchestrator.processInput("") plays the intro and returns
                val introEvents = mutableListOf<JsonObject>()
                var introSceneImage: ByteArray? = null
                log.info("start_game: triggering opening narration via processInput(\"\")...")
                val introStart = System.currentTimeMillis()
                try {
                    session.game.processInput("").collect { event ->
                        introEvents.add(gameEventToJson(event))
                        log.debug("start_game: intro event: {}", event::class.simpleName)
                        if (event is GameEvent.SceneImage && event.imageData.isNotEmpty() && introSceneImage == null) {
                            introSceneImage = event.imageData
                        }
                    }
                    val introElapsed = System.currentTimeMillis() - introStart
                    log.info("start_game: intro narration complete in {}ms, {} events", introElapsed, introEvents.size)
                    session.game.save()
                } catch (e: Exception) {
                    val introElapsed = System.currentTimeMillis() - introStart
                    log.error("start_game: intro narration FAILED after {}ms: {}", introElapsed, e.message, e)
                    // Intro failed but game is still started — log it
                    introEvents.add(buildJsonObject {
                        put("type", JsonPrimitive("system"))
                        put("text", JsonPrimitive("Game started. (Opening narration failed: ${e.message})"))
                    })
                }

                return buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("gameId", JsonPrimitive(session.id))
                    putJsonObject("character") {
                        put("name", JsonPrimitive(sessionState.characterName!!))
                        put("backstory", JsonPrimitive(sessionState.backstory ?: ""))
                        put("avatarImageId", JsonPrimitive(sessionState.avatarImageId ?: ""))
                    }
                    putJsonArray("introEvents") { introEvents.forEach { add(it) } }
                    put("message", JsonPrimitive("Game started! Use send_player_input to play."))
                    if (introSceneImage != null) {
                        put("imageBase64", JsonPrimitive(java.util.Base64.getEncoder().encodeToString(introSceneImage)))
                        put("mimeType", JsonPrimitive("image/png"))
                    }
                }
            }

            "save_game" -> {
                val session = getGameSession(sessionState)
                session.game.save()
                return buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("message", JsonPrimitive("Game saved."))
                }
            }

            "abandon_game" -> {
                val confirm = args["confirm"]?.jsonPrimitive?.booleanOrNull ?: false
                if (!confirm) {
                    return buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Set confirm=true to abandon the current game."))
                    }
                }
                if (!sessionState.gameStarted) {
                    // No active game — just reset creation state
                    sessionState.gameCreated = false
                    sessionState.characterName = null
                    sessionState.backstory = null
                    sessionState.appearance = null
                    sessionState.avatarImageId = null
                    persistSession(mcpSessionId, sessionState)
                    return buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("message", JsonPrimitive("Session reset. Call create_game to start a new adventure."))
                    }
                }

                // Save the current game before abandoning
                val session = getGameSession(sessionState)
                session.game.save()
                val abandonedGameId = sessionState.gameSessionId
                val abandonedCharacter = sessionState.characterName ?: "Unknown"

                // Remove the game session from memory (DB file persists on disk for load_game)
                sessionState.gameSessionId?.let { GameSessionManager.removeSession(it) }

                // Reset session state
                sessionState.gameSessionId = null
                sessionState.gameCreated = false
                sessionState.gameStarted = false
                sessionState.characterName = null
                sessionState.backstory = null
                sessionState.appearance = null
                sessionState.avatarImageId = null
                persistSession(mcpSessionId, sessionState)

                return buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("abandonedGameId", JsonPrimitive(abandonedGameId ?: ""))
                    put("abandonedCharacter", JsonPrimitive(abandonedCharacter))
                    put("message", JsonPrimitive("Game saved and abandoned. Call create_game to start a new adventure, or load_game to resume a previous one."))
                }
            }

            "list_saves" -> {
                val dataDir = SessionStore.getDataDir()
                val dbFiles = java.io.File(dataDir).listFiles { f -> f.name.startsWith("rpg_") && f.name.endsWith(".db") }
                    ?: emptyArray()

                val saves = dbFiles.mapNotNull { dbFile ->
                    try {
                        val gameId = dbFile.name.removePrefix("rpg_").removeSuffix(".db")
                        val driver = com.rpgenerator.core.persistence.DriverFactory(dbFile.absolutePath).createDriver()
                        val client = RPGClient(driver)
                        val games = client.getGames()
                        client.close()
                        games.firstOrNull()?.let { info ->
                            buildJsonObject {
                                put("gameId", JsonPrimitive(gameId))
                                put("playerName", JsonPrimitive(info.playerName))
                                put("systemType", JsonPrimitive(info.systemType.name))
                                put("level", JsonPrimitive(info.level))
                                put("difficulty", JsonPrimitive(info.difficulty.name))
                                put("lastPlayed", JsonPrimitive(info.lastPlayedAt))
                                put("createdAt", JsonPrimitive(info.createdAt))
                                put("playtimeMinutes", JsonPrimitive(info.playtime / 60))
                            }
                        }
                    } catch (e: Exception) {
                        null // Skip corrupt/unreadable DB files
                    }
                }.sortedByDescending { it["lastPlayed"]?.jsonPrimitive?.longOrNull ?: 0L }

                return buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("saveCount", JsonPrimitive(saves.size))
                    putJsonArray("saves") { saves.forEach { add(it) } }
                    if (saves.isEmpty()) {
                        put("message", JsonPrimitive("No saved games found."))
                    } else {
                        put("message", JsonPrimitive("Found ${saves.size} saved game(s). Use load_game with a gameId to resume."))
                    }
                }
            }

            "load_game" -> {
                val gameId = args["gameId"]?.jsonPrimitive?.contentOrNull
                    ?: return buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("gameId is required. Call list_saves to see available games."))
                    }

                // Check if there's already an active game
                if (sessionState.gameStarted && sessionState.gameSessionId != null) {
                    // Save and remove current game first
                    try {
                        val currentSession = getGameSession(sessionState)
                        currentSession.game.save()
                        GameSessionManager.removeSession(sessionState.gameSessionId!!)
                    } catch (_: Exception) { /* best effort */ }
                }

                // Try to resume the saved game
                val dataDir = SessionStore.getDataDir()
                val dbPath = "$dataDir/rpg_$gameId.db"
                if (!java.io.File(dbPath).exists()) {
                    return buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("No save file found for gameId '$gameId'. Call list_saves to see available games."))
                    }
                }

                val persisted = PersistedSession(
                    gameId = gameId,
                    gameStarted = true,
                    gameCreated = true
                )

                val gameSession = GameSessionManager.resumeSession(persisted)
                    ?: return buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Failed to load game '$gameId'. The save may be corrupted."))
                    }

                // Update session state
                val snapshot = gameSession.game.getState()
                sessionState.gameSessionId = gameSession.id
                sessionState.gameCreated = true
                sessionState.gameStarted = true
                sessionState.characterName = snapshot.playerStats.name
                persistSession(mcpSessionId, sessionState)

                return buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("gameId", JsonPrimitive(gameId))
                    put("playerName", JsonPrimitive(snapshot.playerStats.name))
                    put("level", JsonPrimitive(snapshot.playerStats.level))
                    put("location", JsonPrimitive(snapshot.location))
                    put("hp", JsonPrimitive(snapshot.playerStats.health))
                    put("maxHp", JsonPrimitive(snapshot.playerStats.maxHealth))
                    put("message", JsonPrimitive("Game loaded! ${snapshot.playerStats.name} (Level ${snapshot.playerStats.level}) at ${snapshot.location}. Use send_player_input to continue playing."))
                }
            }

            "get_game_state" -> {
                // No game created yet — show available worlds
                if (!sessionState.gameCreated) {
                    return buildJsonObject {
                        put("phase", JsonPrimitive("world_selection"))
                        put("message", JsonPrimitive("Welcome! Choose your world to begin."))
                        putJsonArray("availableWorlds") {
                            add(buildJsonObject {
                                put("seedId", JsonPrimitive("integration"))
                                put("name", JsonPrimitive("System Apocalypse"))
                                put("tagline", JsonPrimitive("Brutal survival. Kill to level. Earth merged with an infinite multiverse."))
                                put("description", JsonPrimitive("Day 1 of Integration. An alien System has merged with Earth, collapsing civilization overnight. Monsters pour from dimensional rifts, dungeons spawn in city centers, and the only law is power. You wake alone in a personal tutorial instance — a white void with nothing but you and a System Terminal. Choose your class. Kill or be killed. Those too weak will be culled. Inspired by Defiance of the Fall and Primal Hunter."))
                                put("tone", JsonPrimitive("Brutal, visceral, desperate, empowering"))
                            })
                            add(buildJsonObject {
                                put("seedId", JsonPrimitive("tabletop"))
                                put("name", JsonPrimitive("Classic Fantasy"))
                                put("tagline", JsonPrimitive("Roll for initiative. The Realm needs heroes."))
                                put("description", JsonPrimitive("A classic high-fantasy realm where magic flows through the Weave and adventurers are the thin line between civilization and darkness. You start at a roadside tavern on a stormy night — a notice board full of quests, a barkeep with secrets, and a hooded stranger waiting in the corner. Choose your class, pick your quest, and forge your legend. The System plays like an experienced DM: dry wit, real stakes, and dice rolls that matter. Inspired by D&D, Critical Role, and The Lord of the Rings."))
                                put("tone", JsonPrimitive("Heroic, wondrous, grounded, witty"))
                            })
                            add(buildJsonObject {
                                put("seedId", JsonPrimitive("crawler"))
                                put("name", JsonPrimitive("Dungeon Crawler"))
                                put("tagline", JsonPrimitive("Earth is gone. You're entertainment now. Make it a good show."))
                                put("description", JsonPrimitive("Earth's surface is destroyed. Survivors are dumped into the World Dungeon — a cosmic game show broadcast to eighteen thousand alien sponsor species across the galaxy. Floating cameras follow your every move. Dramatic kills earn sponsor gifts. Boring deaths get forgotten. The Dungeon Master announcer narrates your suffering with game-show-host glee. Dark comedy meets desperate survival. Loot boxes, gift shops, and viewer ratings in a death arena. Inspired by Dungeon Crawler Carl."))
                                put("tone", JsonPrimitive("Darkly comedic, absurdist, theatrical, surprisingly heartfelt"))
                            })
                            add(buildJsonObject {
                                put("seedId", JsonPrimitive("quiet_life"))
                                put("name", JsonPrimitive("Cozy Apocalypse"))
                                put("tagline", JsonPrimitive("The wars are over. Time to build something worth protecting."))
                                put("description", JsonPrimitive("Ten years after Integration. The fighting is done, but the scars remain. Small communities are rebuilding in a world forever changed by the System. You arrive at The Crossroads — a frontier settlement that's part trading post, part refuge, part second chance. The System here rewards creation as much as destruction: baking, smithing, growing things, building bonds. Open a shop, help neighbors, find peace. Violence is an option, not the focus. Inspired by Legends & Lattes and Beware of Chicken."))
                                put("tone", JsonPrimitive("Warm, cozy, hopeful, bittersweet"))
                            })
                        }
                        putJsonArray("nextSteps") {
                            add(JsonPrimitive("Call create_game with your chosen seedId (and optionally systemType and difficulty)"))
                        }
                    }
                }

                // Game created but not started — show character creation progress
                if (!sessionState.gameStarted) {
                    val missing = sessionState.getMissingRequirements()
                    return buildJsonObject {
                        put("phase", JsonPrimitive("character_creation"))
                        putJsonObject("character") {
                            put("name", JsonPrimitive(sessionState.characterName ?: ""))
                            put("backstory", JsonPrimitive(sessionState.backstory ?: ""))
                            put("appearance", JsonPrimitive(sessionState.appearance ?: ""))
                            put("hasAvatar", JsonPrimitive(sessionState.avatarImageId != null))
                            if (sessionState.avatarImageId != null) {
                                put("avatarUrl", JsonPrimitive("/debug/images/${sessionState.avatarImageId}"))
                            }
                        }
                        putJsonObject("config") {
                            put("systemType", JsonPrimitive(sessionState.systemType.name))
                            put("seedId", JsonPrimitive(sessionState.seedId ?: "none"))
                            put("difficulty", JsonPrimitive(sessionState.difficulty.name))
                        }
                        put("ready", JsonPrimitive(sessionState.isReadyToStart()))
                        if (missing.isNotEmpty()) {
                            putJsonArray("missing") { missing.forEach { add(JsonPrimitive(it)) } }
                        }
                    }
                }

                // Game is started — return live game state
                val session = getGameSession(sessionState)
                val state = session.game.getState()
                return buildJsonObject {
                    put("playerName", JsonPrimitive(state.playerStats.name))
                    put("level", JsonPrimitive(state.playerStats.level))
                    put("hp", JsonPrimitive(state.playerStats.health))
                    put("maxHp", JsonPrimitive(state.playerStats.maxHealth))
                    put("energy", JsonPrimitive(state.playerStats.energy))
                    put("maxEnergy", JsonPrimitive(state.playerStats.maxEnergy))
                    put("xp", JsonPrimitive(state.playerStats.experience))
                    put("xpToNext", JsonPrimitive(state.playerStats.experienceToNextLevel))
                    put("location", JsonPrimitive(state.location))
                    putJsonArray("npcs") {
                        state.npcsAtLocation.forEach { npc ->
                            addJsonObject {
                                put("name", JsonPrimitive(npc.name))
                                put("archetype", JsonPrimitive(npc.archetype))
                            }
                        }
                    }
                    putJsonArray("quests") {
                        state.activeQuests.forEach { q ->
                            addJsonObject {
                                put("name", JsonPrimitive(q.name))
                                put("status", JsonPrimitive(q.status.name))
                            }
                        }
                    }
                    putJsonArray("inventory") {
                        state.inventory.forEach { item ->
                            addJsonObject {
                                put("name", JsonPrimitive(item.name))
                                put("quantity", JsonPrimitive(item.quantity))
                            }
                        }
                    }
                }
            }

            "send_player_input" -> {
                if (!sessionState.gameStarted) {
                    val missing = sessionState.getMissingRequirements()
                    return buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Game not started. Complete character creation first, then call start_game."))
                        if (missing.isNotEmpty()) {
                            putJsonArray("missing") { missing.forEach { add(JsonPrimitive(it)) } }
                        }
                    }
                }
                val session = getGameSession(sessionState)
                val input = args["input"]?.jsonPrimitive?.content ?: ""
                if (input.isBlank()) {
                    return buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Empty input. Provide an action or dialogue."))
                    }
                }
                log.info("send_player_input: '{}' (len={})", input.take(100), input.length)
                val inputStart = System.currentTimeMillis()
                val events = mutableListOf<JsonObject>()
                var firstSceneImage: ByteArray? = null
                session.game.processInput(input).collect { event ->
                    val eventElapsed = System.currentTimeMillis() - inputStart
                    log.debug("send_player_input: event {} at +{}ms", event::class.simpleName, eventElapsed)
                    events.add(gameEventToJson(event))
                    // Capture first scene image for MCP image content block
                    if (event is GameEvent.SceneImage && event.imageData.isNotEmpty() && firstSceneImage == null) {
                        firstSceneImage = event.imageData
                    }
                }
                val inputElapsed = System.currentTimeMillis() - inputStart
                log.info("send_player_input: completed in {}ms, {} events, hasImage={}", inputElapsed, events.size, firstSceneImage != null)
                // Auto-save after each input
                try { session.game.save() } catch (_: Exception) {}

                // Return result — imageBase64/mimeType keys trigger image content block in handleToolsCall
                return buildJsonObject {
                    put("success", JsonPrimitive(true))
                    putJsonArray("events") { events.forEach { add(it) } }
                    if (firstSceneImage != null) {
                        put("imageBase64", JsonPrimitive(java.util.Base64.getEncoder().encodeToString(firstSceneImage)))
                        put("mimeType", JsonPrimitive("image/png"))
                    }
                }
            }
        }

        // ── Pre-start tools (portrait can be generated before game starts) ──
        if (name == "game_generate_portrait") {
            val charName = args["characterName"]?.jsonPrimitive?.content ?: sessionState.characterName ?: "Character"
            val appearance = args["appearance"]?.jsonPrimitive?.contentOrNull ?: sessionState.appearance
            val charClass = args["characterClass"]?.jsonPrimitive?.contentOrNull ?: sessionState.startingClass
            val mood = args["mood"]?.jsonPrimitive?.contentOrNull ?: "heroic"
            val framing = args["framing"]?.jsonPrimitive?.contentOrNull?.let {
                try { PortraitFraming.valueOf(it.uppercase()) } catch (e: Exception) { null }
            } ?: PortraitFraming.BUST

            // Need an image service — use session's if game started, or create a temporary one
            val imgService = if (sessionState.gameStarted && sessionState.gameSessionId != null) {
                GameSessionManager.getSession(sessionState.gameSessionId!!)?.imageService
            } else {
                null
            } ?: ImageGenerationService(com.google.genai.Client())

            val request = PortraitRequest(
                name = charName,
                appearance = appearance,
                characterClass = charClass,
                mood = mood,
                powerTier = null,
                systemType = sessionState.systemType.name,
                framing = framing
            )
            return when (val result = imgService.generatePortrait(request)) {
                is ImageResult.Success -> {
                    val imgId = cacheImage("portrait", charName, result.imageData, result.mimeType, result.prompt)
                    sessionState.avatarImageId = imgId
                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("characterName", JsonPrimitive(charName))
                        put("imageId", JsonPrimitive(imgId))
                        put("imageUrl", JsonPrimitive("/debug/images/$imgId"))
                        put("imageBase64", JsonPrimitive(Base64.getEncoder().encodeToString(result.imageData)))
                        put("mimeType", JsonPrimitive(result.mimeType))
                        put("prompt", JsonPrimitive(result.prompt))
                        put("avatarSet", JsonPrimitive(true))
                        if (sessionState.isReadyToStart()) {
                            put("hint", JsonPrimitive("Character complete! Call start_game to begin."))
                        }
                    }
                }
                is ImageResult.Failure -> buildJsonObject {
                    put("success", JsonPrimitive(false))
                    put("characterName", JsonPrimitive(charName))
                    put("error", JsonPrimitive(result.error))
                    put("prompt", JsonPrimitive(result.prompt))
                }
            }
        }

        // ── All remaining tools require a started game ──
        if (!sessionState.gameStarted) {
            val missing = sessionState.getMissingRequirements()
            return buildJsonObject {
                put("success", JsonPrimitive(false))
                put("error", JsonPrimitive("Game not started. Complete character creation and call start_game first."))
                if (missing.isNotEmpty()) {
                    putJsonArray("missing") { missing.forEach { add(JsonPrimitive(it)) } }
                }
            }
        }

        // Route game_* tools through real Game state and processInput
        val session = getGameSession(sessionState)
        val state = session.game.getState()

        return when (name) {
            // ── State query tools (read from real Game.getState()) ──
            "game_get_player_stats" -> buildJsonObject {
                put("name", JsonPrimitive(state.playerStats.name))
                put("class", JsonPrimitive(state.playerStats.playerClass))
                put("level", JsonPrimitive(state.playerStats.level))
                put("hp", JsonPrimitive(state.playerStats.health))
                put("maxHp", JsonPrimitive(state.playerStats.maxHealth))
                put("energy", JsonPrimitive(state.playerStats.energy))
                put("maxEnergy", JsonPrimitive(state.playerStats.maxEnergy))
                put("xp", JsonPrimitive(state.playerStats.experience))
                put("xpToNext", JsonPrimitive(state.playerStats.experienceToNextLevel))
                put("location", JsonPrimitive(state.location))
                putJsonObject("stats") {
                    state.playerStats.stats.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                }
            }

            "game_get_inventory" -> buildJsonObject {
                putJsonArray("items") {
                    state.inventory.forEach { item ->
                        addJsonObject {
                            put("id", JsonPrimitive(item.id))
                            put("name", JsonPrimitive(item.name))
                            put("description", JsonPrimitive(item.description))
                            put("quantity", JsonPrimitive(item.quantity))
                            put("rarity", JsonPrimitive(item.rarity.name))
                        }
                    }
                }
            }

            "game_get_active_quests" -> buildJsonObject {
                putJsonArray("quests") {
                    state.activeQuests.forEach { q ->
                        addJsonObject {
                            put("id", JsonPrimitive(q.id))
                            put("name", JsonPrimitive(q.name))
                            put("description", JsonPrimitive(q.description))
                            put("status", JsonPrimitive(q.status.name))
                            putJsonArray("objectives") {
                                q.objectives.forEach { obj ->
                                    addJsonObject {
                                        put("description", JsonPrimitive(obj.description))
                                        put("completed", JsonPrimitive(obj.completed))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "game_get_npcs_here" -> buildJsonObject {
                putJsonArray("npcs") {
                    state.npcsAtLocation.forEach { npc ->
                        addJsonObject {
                            put("id", JsonPrimitive(npc.id))
                            put("name", JsonPrimitive(npc.name))
                            put("archetype", JsonPrimitive(npc.archetype))
                            put("disposition", JsonPrimitive(npc.disposition))
                            put("description", JsonPrimitive(npc.description))
                        }
                    }
                }
            }

            "game_get_location" -> buildJsonObject {
                put("location", JsonPrimitive(state.location))
                put("scene", JsonPrimitive(state.currentScene))
            }

            "game_get_character_sheet" -> buildJsonObject {
                putJsonObject("player") {
                    put("name", JsonPrimitive(state.playerStats.name))
                    put("class", JsonPrimitive(state.playerStats.playerClass))
                    put("level", JsonPrimitive(state.playerStats.level))
                    put("backstory", JsonPrimitive(state.playerStats.backstory))
                    putJsonObject("stats") {
                        state.playerStats.stats.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                    }
                    put("hp", JsonPrimitive(state.playerStats.health))
                    put("maxHp", JsonPrimitive(state.playerStats.maxHealth))
                    put("energy", JsonPrimitive(state.playerStats.energy))
                    put("maxEnergy", JsonPrimitive(state.playerStats.maxEnergy))
                    put("xp", JsonPrimitive(state.playerStats.experience))
                    put("xpToNext", JsonPrimitive(state.playerStats.experienceToNextLevel))
                }
                put("location", JsonPrimitive(state.location))
                putJsonArray("skills") {
                    state.skills.forEach { skill ->
                        addJsonObject {
                            put("id", JsonPrimitive(skill.id))
                            put("name", JsonPrimitive(skill.name))
                            put("level", JsonPrimitive(skill.level))
                            put("isActive", JsonPrimitive(skill.isActive))
                        }
                    }
                }
                putJsonArray("inventory") {
                    state.inventory.forEach { item ->
                        addJsonObject {
                            put("name", JsonPrimitive(item.name))
                            put("quantity", JsonPrimitive(item.quantity))
                            put("rarity", JsonPrimitive(item.rarity.name))
                        }
                    }
                }
                putJsonArray("quests") {
                    state.activeQuests.forEach { q ->
                        addJsonObject {
                            put("name", JsonPrimitive(q.name))
                            put("status", JsonPrimitive(q.status.name))
                        }
                    }
                }
            }

            // ── Action tools (route through Game.processInput) ──
            "game_attack_target" -> {
                val target = args["target"]?.jsonPrimitive?.content ?: "enemy"
                routeThroughEngine(session, "I attack $target")
            }

            "game_move_to_location" -> {
                val loc = args["locationName"]?.jsonPrimitive?.content ?: ""
                val stateBefore = session.game.getState()
                val result = routeThroughEngine(session, "I travel to $loc")
                val stateAfter = session.game.getState()
                val moved = stateAfter.location != stateBefore.location
                buildJsonObject {
                    put("success", JsonPrimitive(moved))
                    if (!moved) {
                        put("error", JsonPrimitive("Could not move to '$loc'. You're still at ${stateAfter.location}."))
                    } else {
                        put("newLocation", JsonPrimitive(stateAfter.location))
                    }
                    result.forEach { (key, value) ->
                        if (key != "success") put(key, value)
                    }
                }
            }

            "game_talk_to_npc" -> {
                val npcName = args["npcName"]?.jsonPrimitive?.content ?: ""
                val state = session.game.getState()
                val npc = state.npcsAtLocation.find {
                    it.name.equals(npcName, ignoreCase = true) ||
                    it.name.lowercase().contains(npcName.lowercase())
                }
                if (npc == null) {
                    val npcsHere = state.npcsAtLocation.map { it.name }
                    buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("No NPC named '$npcName' at ${state.location}."))
                        putJsonArray("npcsHere") { npcsHere.forEach { add(JsonPrimitive(it)) } }
                    }
                } else {
                    val dialogue = args["dialogue"]?.jsonPrimitive?.contentOrNull
                    val input = if (dialogue != null) "I say to ${npc.name}: \"$dialogue\"" else "I talk to ${npc.name}"
                    routeThroughEngine(session, input)
                }
            }

            "game_use_item" -> {
                val itemId = args["itemId"]?.jsonPrimitive?.content ?: ""
                val state = session.game.getState()
                val item = state.inventory.find {
                    it.id.equals(itemId, ignoreCase = true) ||
                    it.name.equals(itemId, ignoreCase = true)
                }
                if (item == null) {
                    val knownItems = state.inventory.map { "${it.name} (${it.id})" }
                    buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("No item '$itemId' in inventory."))
                        putJsonArray("inventory") { knownItems.forEach { add(JsonPrimitive(it)) } }
                    }
                } else {
                    routeThroughEngine(session, "I use ${item.name}")
                }
            }

            "game_use_skill" -> {
                val skillId = args["skillId"]?.jsonPrimitive?.content ?: ""
                val target = args["target"]?.jsonPrimitive?.contentOrNull
                val state = session.game.getState()
                val skill = state.skills.find {
                    it.id.equals(skillId, ignoreCase = true) ||
                    it.name.equals(skillId, ignoreCase = true)
                }
                if (skill == null) {
                    val knownSkills = state.skills.map { "${it.name} (${it.id})" }
                    buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("You don't know a skill called '$skillId'."))
                        putJsonArray("knownSkills") { knownSkills.forEach { add(JsonPrimitive(it)) } }
                    }
                } else {
                    val input = if (target != null) "use ${skill.name} on $target" else "use ${skill.name}"
                    routeThroughEngine(session, input)
                }
            }

            "game_accept_quest" -> {
                val questId = args["questId"]?.jsonPrimitive?.content ?: ""
                routeThroughEngine(session, "I accept the quest $questId")
            }

            "game_complete_quest" -> {
                val questId = args["questId"]?.jsonPrimitive?.content ?: ""
                routeThroughEngine(session, "I complete the quest $questId")
            }

            "game_generate_scene_art" -> {
                val desc = args["sceneDescription"]?.jsonPrimitive?.content ?: ""
                val request = SceneArtRequest(
                    locationName = state.location,
                    description = desc,
                    mood = args["mood"]?.jsonPrimitive?.contentOrNull,
                    timeOfDay = args["timeOfDay"]?.jsonPrimitive?.contentOrNull,
                    weather = args["weather"]?.jsonPrimitive?.contentOrNull,
                    systemType = null // Could be pulled from game config
                )
                when (val result = session.imageService.generateSceneArt(request)) {
                    is ImageResult.Success -> {
                        val imgId = cacheImage("scene", desc, result.imageData, result.mimeType, result.prompt)
                        buildJsonObject {
                            put("success", JsonPrimitive(true))
                            put("description", JsonPrimitive(desc))
                            put("imageId", JsonPrimitive(imgId))
                            put("imageUrl", JsonPrimitive("/debug/images/$imgId"))
                            put("imageBase64", JsonPrimitive(Base64.getEncoder().encodeToString(result.imageData)))
                            put("mimeType", JsonPrimitive(result.mimeType))
                            put("prompt", JsonPrimitive(result.prompt))
                        }
                    }
                    is ImageResult.Failure -> buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("description", JsonPrimitive(desc))
                        put("error", JsonPrimitive(result.error))
                        put("prompt", JsonPrimitive(result.prompt))
                    }
                }
            }

            "game_shift_music_mood" -> {
                val mood = args["mood"]?.jsonPrimitive?.content ?: "neutral"
                val intensity = args["intensity"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0.5f
                buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("mood", JsonPrimitive(mood))
                    put("intensity", JsonPrimitive(intensity))
                }
            }

            "game_generate_location" -> {
                val desc = args["description"]?.jsonPrimitive?.content ?: ""
                routeThroughEngine(session, "I explore and discover $desc")
            }

            "game_generate_npc" -> {
                val npcName = args["name"]?.jsonPrimitive?.content ?: ""
                val role = args["role"]?.jsonPrimitive?.content ?: ""
                val personality = args["personality"]?.jsonPrimitive?.content ?: ""
                val result = routeThroughEngine(session, "I encounter a $role named $npcName who seems $personality")

                // Verify the NPC was actually created by checking game state
                val stateAfter = session.game.getState()
                val npcCreated = stateAfter.npcsAtLocation.any {
                    it.name.equals(npcName, ignoreCase = true)
                }
                buildJsonObject {
                    put("success", JsonPrimitive(npcCreated))
                    if (!npcCreated) {
                        put("warning", JsonPrimitive("NPC '$npcName' was not added to the game state. The engine may have narrated the encounter without registering the NPC."))
                    }
                    result.forEach { (key, value) ->
                        if (key != "success") put(key, value)
                    }
                }
            }

            "game_generate_item_art" -> {
                val itemName = args["itemName"]?.jsonPrimitive?.content ?: ""
                val itemDesc = args["itemDescription"]?.jsonPrimitive?.content ?: ""
                val rarity = args["rarity"]?.jsonPrimitive?.contentOrNull

                val request = ItemArtRequest(
                    name = itemName,
                    description = itemDesc,
                    rarity = rarity
                )
                when (val result = session.imageService.generateItemArt(request)) {
                    is ImageResult.Success -> {
                        val imgId = cacheImage("item", itemName, result.imageData, result.mimeType, result.prompt)
                        buildJsonObject {
                            put("success", JsonPrimitive(true))
                            put("itemName", JsonPrimitive(itemName))
                            put("imageId", JsonPrimitive(imgId))
                            put("imageUrl", JsonPrimitive("/debug/images/$imgId"))
                            put("imageBase64", JsonPrimitive(Base64.getEncoder().encodeToString(result.imageData)))
                            put("mimeType", JsonPrimitive(result.mimeType))
                            put("prompt", JsonPrimitive(result.prompt))
                        }
                    }
                    is ImageResult.Failure -> buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("itemName", JsonPrimitive(itemName))
                        put("error", JsonPrimitive(result.error))
                        put("prompt", JsonPrimitive(result.prompt))
                    }
                }
            }

            // game_generate_portrait is handled above (works pre-start and post-start)

            // ── Debug introspection tools ────────────────────────────────────

            "debug_get_agent_conversations" -> {
                val session = getGameSession(sessionState)
                val trackingLlm = session.trackingLlm

                if (trackingLlm == null) {
                    buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Agent tracking not available for this session."))
                    }
                } else {
                    val filterName = args["agentName"]?.jsonPrimitive?.contentOrNull
                    val lastN = args["lastN"]?.jsonPrimitive?.intOrNull

                    val agents = if (filterName != null) {
                        trackingLlm.agents.filter {
                            it.name.equals(filterName, ignoreCase = true) ||
                                it.name.contains(filterName, ignoreCase = true)
                        }
                    } else {
                        trackingLlm.agents
                    }

                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("agentCount", JsonPrimitive(agents.size))
                        putJsonObject("diagnostics") {
                            put("sessionId", JsonPrimitive(session.id))
                            put("trackingLlmIdentity", JsonPrimitive(System.identityHashCode(trackingLlm).toString()))
                        }
                        putJsonArray("agents") {
                            agents.forEach { agent ->
                                addJsonObject {
                                    put("agentId", JsonPrimitive(agent.agentId))
                                    put("name", JsonPrimitive(agent.name))
                                    put("systemPrompt", JsonPrimitive(agent.systemPrompt))
                                    put("messageCount", JsonPrimitive(agent.messageCount))
                                    val msgs = if (lastN != null) agent.messages.takeLast(lastN) else agent.messages
                                    putJsonArray("messages") {
                                        msgs.forEach { msg ->
                                            addJsonObject {
                                                put("role", JsonPrimitive(msg.role))
                                                put("content", JsonPrimitive(msg.content))
                                                put("timestamp", JsonPrimitive(msg.timestamp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "debug_get_event_log" -> {
                val session = getGameSession(sessionState)
                val allEvents = session.game.getEventLog()
                val filterType = args["eventType"]?.jsonPrimitive?.contentOrNull
                val lastN = args["lastN"]?.jsonPrimitive?.intOrNull

                val filtered = if (filterType != null) {
                    allEvents.filter { event ->
                        val eventTypeName = event::class.simpleName ?: ""
                        eventTypeName.equals(filterType, ignoreCase = true)
                    }
                } else {
                    allEvents
                }

                val events = if (lastN != null) filtered.takeLast(lastN) else filtered

                buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("totalEvents", JsonPrimitive(allEvents.size))
                    put("filteredCount", JsonPrimitive(events.size))
                    if (filterType != null) put("filter", JsonPrimitive(filterType))
                    putJsonArray("events") {
                        events.forEachIndexed { index, event ->
                            addJsonObject {
                                put("index", JsonPrimitive(if (lastN != null) allEvents.size - events.size + index else index))
                                put("type", JsonPrimitive(event::class.simpleName ?: "Unknown"))
                                // Include full event data
                                val eventJson = gameEventToJson(event)
                                eventJson.forEach { (key, value) ->
                                    put(key, value)
                                }
                            }
                        }
                    }
                }
            }

            "debug_get_plot_graph" -> {
                val session = getGameSession(sessionState)
                try {
                    val threads = session.rpgClient.getPlotThreads(session.game)
                    val nodes = session.rpgClient.getPlotNodes(session.game)
                    val edges = session.rpgClient.getPlotEdges(session.game)

                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        putJsonObject("plotGraph") {
                            put("threadCount", JsonPrimitive(threads.size))
                            put("nodeCount", JsonPrimitive(nodes.size))
                            put("edgeCount", JsonPrimitive(edges.size))
                            putJsonArray("threads") {
                                threads.forEach { thread ->
                                    addJsonObject {
                                        put("id", JsonPrimitive(thread.id))
                                        put("category", JsonPrimitive(thread.category))
                                        put("priority", JsonPrimitive(thread.priority))
                                        put("status", JsonPrimitive(thread.status))
                                        put("data", JsonPrimitive(thread.threadJson))
                                    }
                                }
                            }
                            putJsonArray("nodes") {
                                nodes.forEach { node ->
                                    addJsonObject {
                                        put("id", JsonPrimitive(node.id))
                                        put("threadId", JsonPrimitive(node.threadId))
                                        put("tier", JsonPrimitive(node.tier))
                                        put("sequence", JsonPrimitive(node.sequence))
                                        put("beatType", JsonPrimitive(node.beatType))
                                        put("triggered", JsonPrimitive(node.triggered))
                                        put("completed", JsonPrimitive(node.completed))
                                        put("abandoned", JsonPrimitive(node.abandoned))
                                        put("data", JsonPrimitive(node.nodeJson))
                                    }
                                }
                            }
                            putJsonArray("edges") {
                                edges.forEach { edge ->
                                    addJsonObject {
                                        put("id", JsonPrimitive(edge.id))
                                        put("from", JsonPrimitive(edge.fromNodeId))
                                        put("to", JsonPrimitive(edge.toNodeId))
                                        put("type", JsonPrimitive(edge.edgeType))
                                        put("weight", JsonPrimitive(edge.weight))
                                        put("disabled", JsonPrimitive(edge.disabled))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Plot graph may not exist yet (generated after first input)
                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("plotGraph", JsonNull)
                        put("message", JsonPrimitive("Plot graph not generated yet. It is created after the first player input."))
                    }
                }
            }

            "debug_get_narrative_state" -> {
                val session = getGameSession(sessionState)
                val gameState = session.game.getState()
                val debugState = session.game.getDebugState()
                val trackingLlm = session.trackingLlm

                buildJsonObject {
                    put("success", JsonPrimitive(true))

                    // What the game engine thinks is true
                    putJsonObject("engineState") {
                        debugState.forEach { (key, value) ->
                            put(key, JsonPrimitive(value))
                        }
                    }

                    // What the player-facing snapshot shows
                    putJsonObject("playerFacingState") {
                        put("playerName", JsonPrimitive(gameState.playerStats.name))
                        put("level", JsonPrimitive(gameState.playerStats.level))
                        put("health", JsonPrimitive("${gameState.playerStats.health}/${gameState.playerStats.maxHealth}"))
                        put("location", JsonPrimitive(gameState.location))
                        put("scene", JsonPrimitive(gameState.currentScene))
                        put("questCount", JsonPrimitive(gameState.activeQuests.size))
                        put("inventoryCount", JsonPrimitive(gameState.inventory.size))
                        put("npcCount", JsonPrimitive(gameState.npcsAtLocation.size))
                    }

                    // What the narrator last received and said
                    if (trackingLlm != null) {
                        val narrator = trackingLlm.agents.find { it.name == "Narrator" }
                        if (narrator != null) {
                            putJsonObject("narratorState") {
                                put("systemPrompt", JsonPrimitive(narrator.systemPrompt))
                                put("totalMessages", JsonPrimitive(narrator.messageCount))
                                // Last exchange
                                val lastMessages = narrator.messages.takeLast(4)
                                putJsonArray("recentExchange") {
                                    lastMessages.forEach { msg ->
                                        addJsonObject {
                                            put("role", JsonPrimitive(msg.role))
                                            put("content", JsonPrimitive(msg.content))
                                        }
                                    }
                                }
                            }
                        }

                        // GM's last plan (if available)
                        val gm = trackingLlm.agents.find { it.name == "GameMaster" }
                        if (gm != null) {
                            putJsonObject("gameMasterState") {
                                put("totalMessages", JsonPrimitive(gm.messageCount))
                                val lastGmMessages = gm.messages.takeLast(4)
                                putJsonArray("recentExchange") {
                                    lastGmMessages.forEach { msg ->
                                        addJsonObject {
                                            put("role", JsonPrimitive(msg.role))
                                            put("content", JsonPrimitive(msg.content))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "debug_get_tool_log" -> {
                val session = getGameSession(sessionState)
                val allEntries = session.game.getToolCallLog()
                val filterTool = args["toolName"]?.jsonPrimitive?.contentOrNull
                val filterCaller = args["caller"]?.jsonPrimitive?.contentOrNull
                val failedOnly = args["failedOnly"]?.jsonPrimitive?.booleanOrNull ?: false
                val lastN = args["lastN"]?.jsonPrimitive?.intOrNull

                var filtered = allEntries
                if (filterTool != null) {
                    filtered = filtered.filter { (it["tool"] as? String)?.equals(filterTool, ignoreCase = true) == true }
                }
                if (filterCaller != null) {
                    filtered = filtered.filter { (it["caller"] as? String)?.equals(filterCaller, ignoreCase = true) == true }
                }
                if (failedOnly) {
                    filtered = filtered.filter { (it["success"] as? Boolean) == false }
                }
                val entries = if (lastN != null) filtered.takeLast(lastN) else filtered

                buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("totalCalls", JsonPrimitive(allEntries.size))
                    put("filteredCount", JsonPrimitive(entries.size))
                    if (filterTool != null) put("filterTool", JsonPrimitive(filterTool))
                    if (filterCaller != null) put("filterCaller", JsonPrimitive(filterCaller))
                    if (failedOnly) put("failedOnly", JsonPrimitive(true))
                    putJsonArray("entries") {
                        entries.forEach { entry ->
                            addJsonObject {
                                put("seq", JsonPrimitive(entry["seq"] as? Int ?: 0))
                                put("turn", JsonPrimitive(entry["turn"] as? Int ?: 0))
                                put("tool", JsonPrimitive(entry["tool"] as? String ?: ""))
                                put("caller", JsonPrimitive(entry["caller"] as? String ?: ""))
                                put("success", JsonPrimitive(entry["success"] as? Boolean ?: false))
                                put("elapsedMs", JsonPrimitive(entry["elapsedMs"] as? Long ?: 0))
                                put("stateChanged", JsonPrimitive(entry["stateChanged"] as? Boolean ?: false))
                                put("location", JsonPrimitive(entry["location"] as? String ?: ""))
                                put("playerLevel", JsonPrimitive(entry["playerLevel"] as? Int ?: 0))
                                put("result", JsonPrimitive(entry["result"] as? String ?: ""))
                                if (entry["error"] != null) put("error", JsonPrimitive(entry["error"] as String))

                                // Args as object
                                @Suppress("UNCHECKED_CAST")
                                val argsMap = entry["args"] as? Map<String, String> ?: emptyMap()
                                putJsonObject("args") {
                                    argsMap.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                                }

                                // Events as array
                                @Suppress("UNCHECKED_CAST")
                                val events = entry["events"] as? List<String> ?: emptyList()
                                putJsonArray("events") {
                                    events.forEach { add(JsonPrimitive(it)) }
                                }
                            }
                        }
                    }
                }
            }

            "report_bug" -> {
                val title = args["title"]?.jsonPrimitive?.contentOrNull ?: ""
                val description = args["description"]?.jsonPrimitive?.contentOrNull ?: ""
                if (title.isBlank() || description.isBlank()) {
                    return buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("title and description are required"))
                    }
                }
                val severity = args["severity"]?.jsonPrimitive?.contentOrNull ?: "medium"
                val category = args["category"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                val steps = args["steps_to_reproduce"]?.jsonPrimitive?.contentOrNull
                val toolCall = args["tool_call"]?.jsonPrimitive?.contentOrNull
                val gameContext = args["game_state_context"]?.jsonPrimitive?.contentOrNull

                val timestamp = System.currentTimeMillis()
                val bugId = "BUG-${timestamp}"
                val report = buildJsonObject {
                    put("id", JsonPrimitive(bugId))
                    put("title", JsonPrimitive(title))
                    put("description", JsonPrimitive(description))
                    put("severity", JsonPrimitive(severity))
                    put("category", JsonPrimitive(category))
                    put("timestamp", JsonPrimitive(timestamp))
                    put("timestampReadable", JsonPrimitive(java.time.Instant.ofEpochMilli(timestamp).toString()))
                    if (steps != null) put("steps_to_reproduce", JsonPrimitive(steps))
                    if (toolCall != null) put("tool_call", JsonPrimitive(toolCall))
                    if (gameContext != null) put("game_state_context", JsonPrimitive(gameContext))
                    // Include current game state if available
                    if (sessionState.gameStarted) {
                        try {
                            val session = getGameSession(sessionState)
                            val state = session.game.getState()
                            putJsonObject("snapshot") {
                                put("playerName", JsonPrimitive(state.playerStats.name))
                                put("level", JsonPrimitive(state.playerStats.level))
                                put("health", JsonPrimitive("${state.playerStats.health}/${state.playerStats.maxHealth}"))
                                put("location", JsonPrimitive(state.location))
                                put("seedId", JsonPrimitive(sessionState.seedId ?: "unknown"))
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Write to qa_tests/bug_reports/
                val bugDir = java.io.File("qa_tests/bug_reports")
                bugDir.mkdirs()
                val file = java.io.File(bugDir, "$bugId.json")
                file.writeText(json.encodeToString(JsonObject.serializer(), report))

                buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("bugId", JsonPrimitive(bugId))
                    put("file", JsonPrimitive(file.absolutePath))
                    put("message", JsonPrimitive("Bug report saved: $title"))
                }
            }

            else -> buildJsonObject {
                put("error", JsonPrimitive("Unknown tool: $name"))
            }
        }
    }

    private suspend fun routeThroughEngine(session: GameSession, input: String): JsonObject {
        log.info("routeThroughEngine: '{}'", input.take(100))
        val routeStart = System.currentTimeMillis()
        val events = mutableListOf<JsonObject>()
        session.game.processInput(input).collect { event ->
            val elapsed = System.currentTimeMillis() - routeStart
            log.debug("routeThroughEngine: event {} at +{}ms", event::class.simpleName, elapsed)
            events.add(gameEventToJson(event))
        }
        val routeElapsed = System.currentTimeMillis() - routeStart
        log.info("routeThroughEngine: completed in {}ms, {} events", routeElapsed, events.size)
        return buildJsonObject {
            put("success", JsonPrimitive(true))
            putJsonArray("events") { events.forEach { add(it) } }
        }
    }

    private fun getGameSession(sessionState: McpSessionState): GameSession {
        val gameId = sessionState.gameSessionId
            ?: throw IllegalStateException("No game session. Call create_game first.")
        return GameSessionManager.getSession(gameId)
            ?: throw IllegalStateException("Game session $gameId not found. Call create_game to start a new game.")
    }

    /** Persist MCP session state to survive server restarts */
    private fun persistSession(mcpSessionId: String?, sessionState: McpSessionState) {
        if (mcpSessionId == null) return
        SessionStore.save(mcpSessionId, PersistedSession(
            gameId = sessionState.gameSessionId ?: "",
            systemType = sessionState.systemType.name,
            seedId = sessionState.seedId,
            difficulty = sessionState.difficulty.name,
            characterName = sessionState.characterName,
            backstory = sessionState.backstory,
            appearance = sessionState.appearance,
            avatarImageId = sessionState.avatarImageId,
            gameStarted = sessionState.gameStarted,
            gameCreated = sessionState.gameCreated
        ))
    }

    // ── JSON Helpers ────────────────────────────────────────────────

    private fun gameEventToJson(event: GameEvent): JsonObject = buildJsonObject {
        when (event) {
            is GameEvent.NarratorText -> { put("type", JsonPrimitive("narrator")); put("text", JsonPrimitive(event.text)) }
            is GameEvent.NPCDialogue -> { put("type", JsonPrimitive("npc_dialogue")); put("npcName", JsonPrimitive(event.npcName)); put("text", JsonPrimitive(event.text)) }
            is GameEvent.CombatLog -> { put("type", JsonPrimitive("combat")); put("text", JsonPrimitive(event.text)) }
            is GameEvent.SystemNotification -> { put("type", JsonPrimitive("system")); put("text", JsonPrimitive(event.text)) }
            is GameEvent.StatChange -> { put("type", JsonPrimitive("stat_change")); put("stat", JsonPrimitive(event.statName)); put("old", JsonPrimitive(event.oldValue)); put("new", JsonPrimitive(event.newValue)) }
            is GameEvent.ItemGained -> { put("type", JsonPrimitive("item_gained")); put("item", JsonPrimitive(event.itemName)); put("quantity", JsonPrimitive(event.quantity)) }
            is GameEvent.QuestUpdate -> { put("type", JsonPrimitive("quest_update")); put("quest", JsonPrimitive(event.questName)); put("status", JsonPrimitive(event.status.name)) }
            is GameEvent.SceneImage -> { put("type", JsonPrimitive("scene_image")); put("description", JsonPrimitive(event.description)) }
            is GameEvent.NarratorAudio -> { put("type", JsonPrimitive("narrator_audio")); put("sampleRate", JsonPrimitive(event.sampleRate)) }
            is GameEvent.MusicChange -> { put("type", JsonPrimitive("music_change")); put("mood", JsonPrimitive(event.mood)) }
            is GameEvent.NPCPortrait -> { put("type", JsonPrimitive("npc_portrait")); put("npcName", JsonPrimitive(event.npcName)) }
            is GameEvent.ItemIconGenerated -> { put("type", JsonPrimitive("item_icon")); put("itemId", JsonPrimitive(event.itemId)); put("iconUrl", JsonPrimitive(event.iconUrl)) }
            is GameEvent.ToolCallResults -> {
                put("type", JsonPrimitive("tool_results"))
                put("results", JsonArray(event.results.map { entry ->
                    buildJsonObject {
                        put("toolName", JsonPrimitive(entry.toolName))
                        put("success", JsonPrimitive(entry.success))
                        put("data", json.parseToJsonElement(entry.data))
                    }
                }))
            }
        }
    }

    private data class ToolParam(
        val type: String,
        val description: String = "",
        val required: Boolean = false
    )

    private fun JsonArrayBuilder.addMCPTool(
        name: String,
        description: String,
        properties: Map<String, ToolParam>,
        required: List<String> = emptyList()
    ) {
        addJsonObject {
            put("name", JsonPrimitive(name))
            put("description", JsonPrimitive(description))
            putJsonObject("inputSchema") {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    properties.forEach { (propName, param) ->
                        putJsonObject(propName) {
                            put("type", JsonPrimitive(param.type))
                            if (param.description.isNotEmpty()) {
                                put("description", JsonPrimitive(param.description))
                            }
                        }
                    }
                }
                if (required.isNotEmpty()) {
                    putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
                }
            }
        }
    }

    private fun mcpResponse(id: JsonElement?, result: JsonObject): JsonObject {
        return buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            if (id != null) put("id", id)
            put("result", result)
        }
    }

    private fun errorResponse(id: JsonElement?, code: Int, message: String): JsonObject {
        return buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            if (id != null) put("id", id)
            putJsonObject("error") {
                put("code", JsonPrimitive(code))
                put("message", JsonPrimitive(message))
            }
        }
    }
}
