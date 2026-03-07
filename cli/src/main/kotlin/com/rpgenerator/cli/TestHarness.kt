package com.rpgenerator.cli

import com.rpgenerator.core.api.*
import com.rpgenerator.core.gemini.*
import com.rpgenerator.core.persistence.DriverFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Headless test harness for the Gemini game integration.
 *
 * Two modes:
 * 1. MCP Server (--mcp): JSON-RPC over stdio, for Claude Code or any MCP client
 * 2. Interactive CLI (--test): Direct tool calls from command line
 *
 * Usage:
 *   ./gradlew :cli:run --args="--mcp"           # MCP server mode
 *   ./gradlew :cli:run --args="--test"           # Interactive CLI mode
 *   ./gradlew :cli:run --args="--test tool get_player_stats"  # Single tool call
 */
class TestHarness(
    private val llm: LLMInterface
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private var game: Game? = null
    private var toolContract: GeminiTools? = null

    private fun ensureGame(): Game {
        if (game != null) return game!!
        createGame(SystemType.SYSTEM_INTEGRATION, "shattered_grid")
        return game!!
    }

    private fun ensureTools(): GeminiTools {
        if (toolContract != null) return toolContract!!
        ensureGame()
        return toolContract!!
    }

    private fun createGame(systemType: SystemType, seedId: String?) {
        val dataDir = File(System.getProperty("user.home"), ".rpgenerator-test")
        if (!dataDir.exists()) dataDir.mkdirs()
        // Use unique file per session to avoid lock issues
        val dbFile = File(dataDir, "test_${System.currentTimeMillis()}.db")
        val driver = DriverFactory(dbFile.absolutePath).createDriver()
        val client = RPGClient(driver)

        val config = GameConfig(
            systemType = systemType,
            difficulty = Difficulty.NORMAL,
            seedId = seedId
        )

        game = runBlocking { client.startGame(config, llm) }

        // Create tool contract for direct tool testing
        toolContract = GeminiTools.createDefault(gameId = game!!.id, systemType = systemType)
    }

    // ── MCP Server Mode ────────────────────────────────────────────

    fun runMCPServer() {
        val input = System.`in`
        val output = System.out
        val reader = input.bufferedReader()

        while (true) {
            // Read headers until empty line
            var contentLength = -1
            while (true) {
                val headerLine = reader.readLine() ?: return
                if (headerLine.isBlank()) {
                    if (contentLength > 0) break
                    continue
                }
                if (headerLine.lowercase().startsWith("content-length:")) {
                    contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: continue
                }
            }

            // Read the JSON body
            val body = CharArray(contentLength)
            var totalRead = 0
            while (totalRead < contentLength) {
                val n = reader.read(body, totalRead, contentLength - totalRead)
                if (n == -1) return
                totalRead += n
            }
            val message = String(body)

            try {
                val request = json.parseToJsonElement(message).jsonObject
                val response = handleMCPRequest(request)
                if (response != null) {
                    writeMessage(output, json.encodeToString(response))
                }
            } catch (e: Exception) {
                val errorResponse = buildJsonObject {
                    put("jsonrpc", JsonPrimitive("2.0"))
                    put("id", JsonNull)
                    putJsonObject("error") {
                        put("code", JsonPrimitive(-32700))
                        put("message", JsonPrimitive("Parse error: ${e.message}"))
                    }
                }
                writeMessage(output, json.encodeToString(errorResponse))
            }
        }
    }

    private fun writeMessage(output: java.io.OutputStream, jsonStr: String) {
        val bytes = jsonStr.toByteArray(Charsets.UTF_8)
        val header = "Content-Length: ${bytes.size}\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun handleMCPRequest(request: JsonObject): JsonObject? {
        val method = request["method"]?.jsonPrimitive?.content ?: ""
        val id = request["id"]

        return when (method) {
            "initialize" -> {
                val clientVersion = request["params"]?.jsonObject?.get("protocolVersion")
                    ?.jsonPrimitive?.content ?: "2024-11-05"
                mcpResponse(id, buildJsonObject {
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

            "tools/list" -> mcpResponse(id, buildJsonObject {
                putJsonArray("tools") {
                    addMCPTool("create_game",
                        "Create a new game session",
                        mapOf("systemType" to "string", "seedId" to "string"),
                        required = emptyList()
                    )
                    addMCPTool("send_player_input",
                        "Send player input and get game events back",
                        mapOf("input" to "string"),
                        required = listOf("input")
                    )
                    addMCPTool("get_game_state",
                        "Get current game state (player, location, NPCs, quests, inventory)",
                        emptyMap()
                    )

                    // All Gemini tool contract tools
                    addMCPTool("game_get_player_stats", "Get player level, HP, XP, location, class", emptyMap())
                    addMCPTool("game_get_inventory", "Get player inventory", emptyMap())
                    addMCPTool("game_get_active_quests", "Get active quests and progress", emptyMap())
                    addMCPTool("game_get_npcs_here", "Get NPCs at current location", emptyMap())
                    addMCPTool("game_get_location", "Get current location details", emptyMap())
                    addMCPTool("game_get_character_sheet", "Get full character sheet", emptyMap())
                    addMCPTool("game_attack_target", "Attack an enemy",
                        mapOf("target" to "string"), listOf("target"))
                    addMCPTool("game_move_to_location", "Move to a connected location",
                        mapOf("locationName" to "string"), listOf("locationName"))
                    addMCPTool("game_talk_to_npc", "Talk to an NPC",
                        mapOf("npcName" to "string", "dialogue" to "string"), listOf("npcName"))
                    addMCPTool("game_use_item", "Use an inventory item",
                        mapOf("itemId" to "string"), listOf("itemId"))
                    addMCPTool("game_use_skill", "Use a skill",
                        mapOf("skillId" to "string", "target" to "string"), listOf("skillId"))
                    addMCPTool("game_accept_quest", "Accept a quest",
                        mapOf("questId" to "string"), listOf("questId"))
                    addMCPTool("game_complete_quest", "Complete a quest",
                        mapOf("questId" to "string"), listOf("questId"))
                    addMCPTool("game_generate_scene_art", "Generate scene artwork",
                        mapOf("sceneDescription" to "string"), listOf("sceneDescription"))
                    addMCPTool("game_shift_music_mood", "Shift background music mood",
                        mapOf("mood" to "string", "intensity" to "number"), listOf("mood"))
                    addMCPTool("game_generate_location", "Generate a new location",
                        mapOf("description" to "string"), listOf("description"))
                    addMCPTool("game_generate_npc", "Generate a new NPC",
                        mapOf("name" to "string", "role" to "string", "personality" to "string"),
                        listOf("name", "role", "personality"))
                }
            })

            "tools/call" -> {
                val params = request["params"]?.jsonObject
                val toolName = params?.get("name")?.jsonPrimitive?.content ?: ""
                val args = params?.get("arguments")?.jsonObject ?: buildJsonObject {}

                val result = runBlocking { executeTool(toolName, args) }

                mcpResponse(id, buildJsonObject {
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive(json.encodeToString(result)))
                        }
                    }
                })
            }

            "notifications/initialized" -> null  // No response for notifications

            else -> mcpResponse(id, buildJsonObject {
                put("error", JsonPrimitive("Unknown method: $method"))
            })
        }
    }

    private fun JsonArrayBuilder.addMCPTool(
        name: String,
        description: String,
        properties: Map<String, String>,
        required: List<String> = emptyList()
    ) {
        addJsonObject {
            put("name", JsonPrimitive(name))
            put("description", JsonPrimitive(description))
            putJsonObject("inputSchema") {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    properties.forEach { (propName, propType) ->
                        putJsonObject(propName) {
                            put("type", JsonPrimitive(propType))
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

    // ── Interactive CLI Mode ───────────────────────────────────────

    fun runInteractive() {
        println("=== RPGenerator Test Harness ===")
        println("Commands:")
        println("  create [systemType] [seedId]  - Create a new game")
        println("  play <input>                  - Send player input (or just type naturally)")
        println("  state                         - Show game state")
        println("  tool <name> [key=val ...]     - Call a game tool directly")
        println("  tools                         - List all available tools")
        println("  sequence <file>               - Run a test sequence from file")
        println("  e2e                           - Run full end-to-end flow")
        println("  quit                          - Exit")
        println()

        val reader = BufferedReader(InputStreamReader(System.`in`))

        while (true) {
            print("test> ")
            System.out.flush()
            val line = reader.readLine()?.trim() ?: break
            if (line.isBlank()) continue

            try {
                when {
                    line == "quit" || line == "exit" -> break
                    line == "tools" -> listTools()
                    line == "state" -> showState()
                    line == "e2e" -> runE2EFlow()
                    line.startsWith("create") -> handleCreate(line)
                    line.startsWith("play ") -> handlePlay(line.removePrefix("play ").trim())
                    line.startsWith("tool ") -> handleToolCall(line.removePrefix("tool ").trim())
                    line.startsWith("sequence ") -> handleSequence(line.removePrefix("sequence ").trim())
                    else -> handlePlay(line)
                }
            } catch (e: Exception) {
                System.err.println("ERROR: ${e.message}")
            }
        }
    }

    fun runSingleCommand(args: List<String>) {
        when (args.firstOrNull()) {
            "tool" -> {
                if (args.size < 2) { println("Usage: --test tool <name> [key=val]"); return }
                handleToolCall(args.drop(1).joinToString(" "))
            }
            "tools" -> listTools()
            "state" -> showState()
            "play" -> handlePlay(args.drop(1).joinToString(" "))
            "create" -> handleCreate(args.joinToString(" "))
            "sequence" -> {
                if (args.size < 2) { println("Usage: --test sequence <file>"); return }
                handleSequence(args[1])
            }
            "e2e" -> runE2EFlow()
            else -> {
                println("Unknown command: ${args.firstOrNull()}")
                println("Available: tool, tools, state, play, create, sequence, e2e")
            }
        }
    }

    private fun handleCreate(line: String) {
        val parts = line.split(" ").filter { it.isNotBlank() }
        val systemType = parts.getOrNull(1)?.let {
            try { SystemType.valueOf(it.uppercase()) } catch (e: Exception) { null }
        } ?: SystemType.SYSTEM_INTEGRATION
        val seedId = parts.getOrNull(2)

        println("Creating game: systemType=$systemType, seedId=$seedId")
        createGame(systemType, seedId)
        println("Game created: id=${game!!.id}")
        showState()
    }

    private fun handlePlay(input: String) {
        val g = ensureGame()
        println(">>> Player: $input")
        runBlocking {
            g.processInput(input).collect { event -> printGameEvent(event) }
        }
        println()
    }

    private fun handleToolCall(line: String) {
        val parts = line.split(" ")
        val toolName = parts.first()
        val args = buildJsonObject {
            parts.drop(1).forEach { part ->
                if (part.contains("=")) {
                    put(part.substringBefore("="), JsonPrimitive(part.substringAfter("=")))
                }
            }
        }

        println("Calling tool: $toolName($args)")
        val result = runBlocking { executeTool(toolName, args) }
        println(json.encodeToString(result))
    }

    private fun handleSequence(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) { println("File not found: $filePath"); return }

        println("=== Running sequence: $filePath ===")
        file.readLines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEachIndexed
            println("\n--- Step ${index + 1}: $trimmed ---")
            when {
                trimmed.startsWith("create") -> handleCreate(trimmed)
                trimmed.startsWith("tool ") -> handleToolCall(trimmed.removePrefix("tool ").trim())
                trimmed == "state" -> showState()
                trimmed.startsWith("play ") -> handlePlay(trimmed.removePrefix("play ").trim())
                else -> handlePlay(trimmed)
            }
        }
        println("\n=== Sequence complete ===")
    }

    private fun runE2EFlow() {
        println("=== End-to-End Flow ===\n")
        handleCreate("create SYSTEM_INTEGRATION shattered_grid")
        println()
        handlePlay("")
        println()
        handlePlay("I look around carefully")
        println()
        handleToolCall("get_player_stats")
        println()
        handleToolCall("get_location")
        println()
        handleToolCall("get_npcs_here")
        println()
        handlePlay("I attack the nearest enemy")
        println()
        handleToolCall("get_player_stats")
        println()
        handleToolCall("get_inventory")
        println()
        handleToolCall("generate_scene_art sceneDescription=A dark cavern with glowing crystals")
        println()
        handleToolCall("shift_music_mood mood=tense intensity=0.8")
        println()
        println("=== E2E Complete ===")
    }

    private fun listTools() {
        println("Available tools:")
        println("  --- Game Management ---")
        println("  create_game(systemType, seedId)")
        println("  send_player_input(input)")
        println("  get_game_state()")
        println()
        println("  --- Gemini Tool Contract ---")
        val tools = ensureTools()
        tools.getToolDeclarations().forEach { decl ->
            val params = decl.parameters.entries.joinToString(", ") { "${it.key}: ${it.value.type}" }
            println("  ${decl.name}($params) - ${decl.description}")
        }
    }

    private fun showState() {
        val g = game ?: run { println("No game active."); return }
        val state = g.getState()
        println("=== Game State ===")
        println("Player: ${state.playerStats.name} Lv.${state.playerStats.level}")
        println("HP: ${state.playerStats.health}/${state.playerStats.maxHealth}")
        println("XP: ${state.playerStats.experience}/${state.playerStats.experienceToNextLevel}")
        println("Location: ${state.location}")
        println("NPCs: ${state.npcsAtLocation.joinToString { it.name }.ifEmpty { "(none)" }}")
        println("Quests: ${state.activeQuests.joinToString { "${it.name} (${it.status})" }.ifEmpty { "(none)" }}")
        println("Inventory: ${state.inventory.joinToString { "${it.name} x${it.quantity}" }.ifEmpty { "(empty)" }}")
    }

    private suspend fun executeTool(name: String, args: JsonObject): JsonObject {
        when (name) {
            "create_game" -> {
                val st = args["systemType"]?.jsonPrimitive?.contentOrNull?.let {
                    try { SystemType.valueOf(it.uppercase()) } catch (e: Exception) { null }
                } ?: SystemType.SYSTEM_INTEGRATION
                createGame(st, args["seedId"]?.jsonPrimitive?.contentOrNull)
                return buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put("gameId", JsonPrimitive(game?.id ?: ""))
                }
            }
            "send_player_input" -> {
                val g = ensureGame()
                val input = args["input"]?.jsonPrimitive?.content ?: ""
                val events = mutableListOf<JsonObject>()
                g.processInput(input).collect { event ->
                    events.add(gameEventToJson(event))
                    printGameEvent(event)
                }
                return buildJsonObject {
                    put("success", JsonPrimitive(true))
                    putJsonArray("events") { events.forEach { add(it) } }
                }
            }
            "get_game_state" -> {
                val state = ensureGame().getState()
                return buildJsonObject {
                    put("playerName", JsonPrimitive(state.playerStats.name))
                    put("level", JsonPrimitive(state.playerStats.level))
                    put("hp", JsonPrimitive(state.playerStats.health))
                    put("maxHp", JsonPrimitive(state.playerStats.maxHealth))
                    put("xp", JsonPrimitive(state.playerStats.experience))
                    put("location", JsonPrimitive(state.location))
                    putJsonArray("npcs") {
                        state.npcsAtLocation.forEach { npc ->
                            addJsonObject { put("name", JsonPrimitive(npc.name)); put("archetype", JsonPrimitive(npc.archetype)) }
                        }
                    }
                    putJsonArray("quests") {
                        state.activeQuests.forEach { q ->
                            addJsonObject { put("name", JsonPrimitive(q.name)); put("status", JsonPrimitive(q.status.name)) }
                        }
                    }
                    putJsonArray("inventory") {
                        state.inventory.forEach { item ->
                            addJsonObject { put("name", JsonPrimitive(item.name)); put("quantity", JsonPrimitive(item.quantity)) }
                        }
                    }
                }
            }
        }

        // Route game_* tools to the contract
        val toolName = if (name.startsWith("game_")) name.removePrefix("game_") else name
        val tools = ensureTools()

        val call = GeminiToolCall(
            id = "test_${System.currentTimeMillis()}",
            name = toolName,
            arguments = args
        )
        val result = tools.dispatch(call)

        return buildJsonObject {
            put("success", JsonPrimitive(result.success))
            if (result.data != null) put("data", result.data!!)
            if (result.error != null) put("error", JsonPrimitive(result.error!!))
            if (result.gameEvents.isNotEmpty()) {
                putJsonArray("events") {
                    result.gameEvents.forEach { event -> add(gameEventToJson(event)) }
                }
            }
        }
    }

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
        }
    }

    private fun printGameEvent(event: GameEvent) {
        when (event) {
            is GameEvent.NarratorText -> println("  [Narrator] ${event.text}")
            is GameEvent.NPCDialogue -> println("  [${event.npcName}] ${event.text}")
            is GameEvent.CombatLog -> println("  [Combat] ${event.text}")
            is GameEvent.SystemNotification -> println("  [System] ${event.text}")
            is GameEvent.StatChange -> println("  [Stat] ${event.statName}: ${event.oldValue} -> ${event.newValue}")
            is GameEvent.ItemGained -> println("  [Item] +${event.quantity}x ${event.itemName}")
            is GameEvent.QuestUpdate -> println("  [Quest] ${event.questName}: ${event.status}")
            is GameEvent.SceneImage -> println("  [Image] ${event.description}")
            is GameEvent.NarratorAudio -> println("  [Audio] ${event.audioData.size} bytes @ ${event.sampleRate}Hz")
            is GameEvent.MusicChange -> println("  [Music] mood=${event.mood}")
            is GameEvent.NPCPortrait -> println("  [Portrait] ${event.npcName}")
        }
    }
}
