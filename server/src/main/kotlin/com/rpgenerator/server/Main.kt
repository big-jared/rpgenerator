package com.rpgenerator.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import com.rpgenerator.core.api.CharacterCreationOptions
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration.Companion.seconds

@kotlinx.serialization.Serializable
data class CreateGameRequest(
    val name: String? = null,
    val backstory: String? = null,
    val systemType: String? = null,
    val seedId: String? = null
)

@kotlinx.serialization.Serializable
data class ToolExecutionRequest(
    val name: String,
    val args: Map<String, String> = emptyMap()
)

@kotlinx.serialization.Serializable
data class ToolExecutionResponse(
    val success: Boolean,
    val data: JsonObject = JsonObject(emptyMap()),
    val events: List<JsonObject> = emptyList(),
    val error: String? = null,
    val imageBase64: String? = null,
    val imageMimeType: String? = null
)

private fun buildImageGalleryHtml(images: List<McpHandler.GeneratedImage>): String = """
<!DOCTYPE html>
<html><head>
<title>RPGenerator - Image Gallery</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { background: #0a0a0f; color: #e0e0e0; font-family: 'Segoe UI', system-ui, sans-serif; padding: 20px; }
  h1 { text-align: center; color: #7c4dff; margin-bottom: 8px; font-size: 1.8em; }
  .subtitle { text-align: center; color: #666; margin-bottom: 24px; }
  .filters { text-align: center; margin-bottom: 20px; }
  .filters button { background: #1a1a2e; color: #aaa; border: 1px solid #333; padding: 6px 16px; margin: 0 4px; border-radius: 4px; cursor: pointer; }
  .filters button.active, .filters button:hover { background: #7c4dff; color: white; border-color: #7c4dff; }
  .gallery { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 20px; max-width: 1400px; margin: 0 auto; }
  .card { background: #12121f; border: 1px solid #222; border-radius: 8px; overflow: hidden; transition: transform 0.2s; }
  .card:hover { transform: translateY(-4px); border-color: #7c4dff; }
  .card img { width: 100%; display: block; cursor: pointer; }
  .card .meta { padding: 12px; }
  .card .type { display: inline-block; padding: 2px 8px; border-radius: 3px; font-size: 0.75em; font-weight: 600; text-transform: uppercase; margin-bottom: 6px; }
  .type-portrait { background: #1b5e20; color: #a5d6a7; }
  .type-scene { background: #0d47a1; color: #90caf9; }
  .type-item { background: #e65100; color: #ffcc80; }
  .card .label { font-weight: 600; font-size: 1.05em; margin-bottom: 4px; }
  .card .prompt { color: #777; font-size: 0.8em; line-height: 1.4; max-height: 3.6em; overflow: hidden; cursor: pointer; }
  .card .prompt.expanded { max-height: none; }
  .card .time { color: #555; font-size: 0.7em; margin-top: 6px; }
  .empty { text-align: center; color: #555; padding: 60px; font-size: 1.1em; }
  .modal { display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.9); z-index: 100; justify-content: center; align-items: center; }
  .modal.show { display: flex; }
  .modal img { max-width: 90%; max-height: 90%; object-fit: contain; }
  .modal .close { position: absolute; top: 20px; right: 30px; color: white; font-size: 30px; cursor: pointer; }
</style>
</head><body>
<h1>Image Gallery</h1>
<p class="subtitle">${images.size} images generated this session</p>
<div class="filters">
  <button class="active" onclick="filter('all')">All</button>
  <button onclick="filter('portrait')">Portraits</button>
  <button onclick="filter('scene')">Scenes</button>
  <button onclick="filter('item')">Items</button>
</div>
<div class="gallery" id="gallery">
${if (images.isEmpty()) """<div class="empty">No images generated yet.<br>Use game_generate_portrait, game_generate_scene_art, or game_generate_item_art via MCP.</div>""" else images.joinToString("\n") { img -> """
  <div class="card" data-type="${img.type}">
    <img src="/debug/images/${img.id}" alt="${img.label}" onclick="openModal(this.src)">
    <div class="meta">
      <span class="type type-${img.type}">${img.type}</span>
      <div class="label">${img.label}</div>
      <div class="prompt" onclick="this.classList.toggle('expanded')">${img.prompt.replace("\"", "&quot;").replace("<", "&lt;")}</div>
      <div class="time">${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(img.timestamp))}</div>
    </div>
  </div>"""}}
</div>
<div class="modal" id="modal" onclick="this.classList.remove('show')">
  <span class="close">&times;</span>
  <img id="modalImg">
</div>
<script>
function filter(type) {
  document.querySelectorAll('.filters button').forEach(b => b.classList.remove('active'));
  event.target.classList.add('active');
  document.querySelectorAll('.card').forEach(c => {
    c.style.display = (type === 'all' || c.dataset.type === type) ? '' : 'none';
  });
}
function openModal(src) {
  document.getElementById('modalImg').src = src;
  document.getElementById('modal').classList.add('show');
}
// Auto-refresh every 10s
setInterval(() => fetch('/debug/gallery/json').then(r=>r.json()).then(imgs => {
  if (imgs.length !== ${images.size}) location.reload();
}), 10000);
</script>
</body></html>
""".trimIndent()

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    println("Starting RPGenerator server on port $port")

    val isDev = System.getenv("KTOR_DEVELOPMENT")?.toBoolean() ?: false
    if (isDev) println("Development mode ON")

    // Log LLM config
    val llmProvider = System.getenv("LLM_PROVIDER") ?: "gemini"
    val llmModel = System.getenv("LLM_MODEL") ?: "(default)"
    println("LLM provider: $llmProvider, model: $llmModel")

    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }

        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Options)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader("Mcp-Session-Id")
            exposeHeader("Mcp-Session-Id")
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respondText(
                    text = """{"error": "${cause.message?.replace("\"", "\\\"") ?: "Unknown error"}"}""",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.InternalServerError
                )
            }
        }

        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 60.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        val authEnabled = AuthHandler.isEnabled()
        if (authEnabled) {
            println("Auth enabled (GOOGLE_OAUTH_CLIENT_ID set)")
        } else {
            println("Auth disabled (no GOOGLE_OAUTH_CLIENT_ID) — all routes are open")
        }

        routing {
            // ── Public routes (no auth) ────────────────────────────────

            // Health check for Cloud Run
            get("/health") {
                call.respondText("""{"status": "ok"}""", ContentType.Application.Json)
            }

            // ── OAuth 2.0 endpoints (MCP OAuth flow) ────────────────
            get("/.well-known/oauth-authorization-server") {
                val scheme = call.request.headers["X-Forwarded-Proto"] ?: "https"
                val host = call.request.headers["Host"] ?: "localhost:8080"
                val url = "$scheme://$host"
                call.respondText(AuthHandler.getServerMetadata(url), ContentType.Application.Json)
            }

            post("/oauth/register") {
                AuthHandler.handleClientRegistration(call)
            }

            get("/oauth/authorize") {
                AuthHandler.handleAuthorize(call)
            }

            post("/oauth/callback") {
                AuthHandler.handleGoogleCallback(call)
            }

            post("/oauth/token") {
                AuthHandler.handleDirectTokenExchange(call)
            }

            // Auth: Sign-in page (manual flow)
            get("/auth") {
                call.respondText(AuthHandler.getLoginPageHtml(), ContentType.Text.Html)
            }

            // Auth: Direct token exchange (for /auth page)
            post("/auth/token") {
                AuthHandler.handleDirectTokenExchange(call)
            }

            // ── Protected routes (require auth when enabled) ───────────

            // MCP Streamable HTTP endpoint
            post("/mcp") {
                if (authEnabled && AuthHandler.verifyRequest(call) == null) {
                    call.respondText(
                        """{"jsonrpc":"2.0","error":{"code":-32000,"message":"Unauthorized. Get a token at /auth"}}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Unauthorized
                    )
                    return@post
                }
                McpHandler.handleRequest(call)
            }

            delete("/mcp") {
                if (authEnabled && AuthHandler.verifyRequest(call) == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@delete
                }
                McpHandler.handleSessionDelete(call)
            }

            // Create a new game session and return session ID (REST API)
            post("/api/game/create") {
                if (authEnabled && AuthHandler.verifyRequest(call) == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val body = try {
                    call.receive<CreateGameRequest>()
                } catch (_: Exception) {
                    CreateGameRequest()
                }
                val session = GameSessionManager.createSession(
                    seedId = body.seedId ?: "integration",
                    characterCreation = CharacterCreationOptions(
                        name = body.name ?: "Adventurer",
                        backstory = body.backstory
                    )
                )
                call.respondText(
                    """{"sessionId": "${session.id}"}""",
                    ContentType.Application.Json
                )
            }

            // Get game state for a session
            get("/api/game/{sessionId}/state") {
                if (authEnabled && AuthHandler.verifyRequest(call) == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                val sessionId = call.parameters["sessionId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val session = GameSessionManager.getSession(sessionId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                val state = session.game.getState()
                // Inject icon URLs for items that have generated icons
                val scheme = call.request.headers["X-Forwarded-Proto"] ?: "http"
                val host = call.request.headers["Host"] ?: "localhost:8080"
                val baseUrl = "$scheme://$host"
                val stateWithIcons = state.copy(
                    inventory = state.inventory.map { item ->
                        if (session.itemIcons.containsKey(item.id)) {
                            item.copy(iconUrl = "$baseUrl/api/game/$sessionId/icon/${item.id}")
                        } else item
                    }
                )
                call.respond(stateWithIcons)
            }

            // Execute a tool on a game session
            post("/api/game/{sessionId}/tool") {
                if (authEnabled && AuthHandler.verifyRequest(call) == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val sessionId = call.parameters["sessionId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                val session = GameSessionManager.getSession(sessionId)
                    ?: return@post call.respond(HttpStatusCode.NotFound)

                val body = call.receive<ToolExecutionRequest>()
                val argsMap: Map<String, Any?> = body.args
                val result = session.game.executeTool(body.name, argsMap)

                // Generic visualPrompt interception — any tool can include a visualPrompt
                // in its result data, and the server generates the appropriate image type.
                var imageBase64: String? = null
                var imageMimeType: String? = null

                if (result.success) {
                    val visualPrompt = result.data["visualPrompt"]?.let { (it as? JsonPrimitive)?.content }
                    val imageType = result.data["imageType"]?.let { (it as? JsonPrimitive)?.content }

                    if (visualPrompt != null) {
                        try {
                            val imgResult = when (imageType) {
                                "portrait" -> session.imageService.generatePortrait(
                                    PortraitRequest(name = body.name, appearance = visualPrompt)
                                )
                                "scene" -> session.imageService.generateSceneArt(
                                    SceneArtRequest(locationName = "", description = visualPrompt)
                                )
                                "item" -> session.imageService.generateItemArt(
                                    ItemArtRequest(name = "", description = visualPrompt)
                                )
                                else -> null
                            }
                            if (imgResult is ImageResult.Success) {
                                imageBase64 = java.util.Base64.getEncoder().encodeToString(imgResult.imageData)
                                imageMimeType = imgResult.mimeType
                            }
                        } catch (e: Exception) {
                            // Image generation failed — non-fatal, respond without image
                        }
                    } else if (body.name == "generate_scene_art") {
                        // Legacy path: explicit generate_scene_art tool
                        try {
                            val description = body.args["description"] ?: ""
                            val state = session.game.getState()
                            val imgResult = session.imageService.generateSceneArt(
                                SceneArtRequest(
                                    locationName = state.location,
                                    description = description
                                )
                            )
                            if (imgResult is ImageResult.Success) {
                                imageBase64 = java.util.Base64.getEncoder().encodeToString(imgResult.imageData)
                                imageMimeType = imgResult.mimeType
                            }
                        } catch (_: Exception) {}
                    } else if (body.name == "generate_portrait") {
                        // Legacy path: explicit generate_portrait tool
                        try {
                            val description = body.args["description"] ?: ""
                            val imgResult = session.imageService.generatePortrait(
                                PortraitRequest(name = "", appearance = description)
                            )
                            if (imgResult is ImageResult.Success) {
                                imageBase64 = java.util.Base64.getEncoder().encodeToString(imgResult.imageData)
                                imageMimeType = imgResult.mimeType
                            }
                        } catch (_: Exception) {}
                    }

                    // For add_item without a visualPrompt, async-generate a 128px item icon (legacy path)
                    if (body.name == "add_item" && imageType != "item") {
                        val itemName = body.args["itemName"] ?: ""
                        val itemDesc = body.args["description"] ?: "Found item"
                        val itemRarity = body.args["rarity"] ?: "COMMON"
                        val itemId = result.data["itemId"]?.let { (it as? JsonPrimitive)?.content }
                        if (itemId != null && itemName.isNotBlank()) {
                            session.scope.launch {
                                try {
                                    val imgResult = session.imageService.generateItemArt(
                                        ItemArtRequest(name = itemName, description = itemDesc, rarity = itemRarity),
                                        iconSize = true
                                    )
                                    if (imgResult is ImageResult.Success) {
                                        session.itemIcons[itemId] = Pair(imgResult.imageData, imgResult.mimeType)
                                        println("Generated icon for item $itemId ($itemName)")
                                    }
                                } catch (e: Exception) {
                                    println("Item icon generation failed for $itemName: ${e.message}")
                                }
                            }
                        }
                    }
                }

                call.respond(ToolExecutionResponse(
                    success = result.success,
                    data = result.data,
                    events = result.events.map { event ->
                        Json.encodeToJsonElement(com.rpgenerator.core.api.GameEvent.serializer(), event)
                            .jsonObject
                    },
                    error = result.error,
                    imageBase64 = imageBase64,
                    imageMimeType = imageMimeType
                ))
            }

            // Get NPC details
            get("/api/game/{sessionId}/npc/{npcId}") {
                if (authEnabled && AuthHandler.verifyRequest(call) == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                val sessionId = call.parameters["sessionId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val npcId = call.parameters["npcId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val session = GameSessionManager.getSession(sessionId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                val details = session.game.getNpcDetails(npcId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(details)
            }

            // Serve item icon images
            get("/api/game/{sessionId}/icon/{itemId}") {
                val sessionId = call.parameters["sessionId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val itemId = call.parameters["itemId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val session = GameSessionManager.getSession(sessionId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                val icon = session.itemIcons[itemId]
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respondBytes(icon.first, ContentType.parse(icon.second))
            }

            // List all saved games (scan DB files)
            get("/api/saves") {
                if (authEnabled && AuthHandler.verifyRequest(call) == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                val dataDir = SessionStore.getDataDir()
                val saves = java.io.File(dataDir).listFiles { f -> f.name.startsWith("rpg_") && f.name.endsWith(".db") }
                    ?.mapNotNull { file ->
                        val gameId = file.nameWithoutExtension.removePrefix("rpg_")
                        try {
                            val driver = com.rpgenerator.core.persistence.DriverFactory(file.absolutePath).createDriver()
                            val client = com.rpgenerator.core.api.RPGClient(driver)
                            val games = client.getGames()
                            client.close()
                            games.firstOrNull()?.let { info ->
                                JsonObject(mapOf(
                                    "gameId" to JsonPrimitive(gameId),
                                    "playerName" to JsonPrimitive(info.playerName),
                                    "systemType" to JsonPrimitive(info.systemType.name),
                                    "level" to JsonPrimitive(info.level),
                                    "lastPlayed" to JsonPrimitive(file.lastModified())
                                ))
                            }
                        } catch (_: Exception) { null }
                    }
                    ?.sortedByDescending { it["lastPlayed"]?.let { v -> (v as? JsonPrimitive)?.content?.toLongOrNull() } ?: 0L }
                    ?: emptyList()
                call.respondText(
                    Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(JsonObject.serializer()), saves),
                    ContentType.Application.Json
                )
            }

            // Load (resume) a saved game by gameId → returns a new session ID
            post("/api/game/load") {
                if (authEnabled && AuthHandler.verifyRequest(call) == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val body = call.receiveText()
                val parsed = Json.decodeFromString<JsonObject>(body)
                val gameId = parsed["gameId"]?.let { (it as? JsonPrimitive)?.content }
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                val persisted = PersistedSession(
                    gameId = gameId,
                    gameStarted = true,
                    gameCreated = true
                )
                val session = GameSessionManager.resumeSession(persisted)
                    ?: return@post call.respondText(
                        """{"error": "Save not found"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.NotFound
                    )
                call.respondText(
                    """{"sessionId": "${session.id}"}""",
                    ContentType.Application.Json
                )
            }

            // Save current game state
            post("/api/game/{sessionId}/save") {
                if (authEnabled && AuthHandler.verifyRequest(call) == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val sessionId = call.parameters["sessionId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                val session = GameSessionManager.getSession(sessionId)
                    ?: return@post call.respond(HttpStatusCode.NotFound)
                session.game.save()
                call.respondText("""{"success": true}""", ContentType.Application.Json)
            }

            // ── Internal: Ephemeral MCP bridge for CliProcessLLM tool calls ──
            post("/internal/mcp/{token}") {
                val token = call.parameters["token"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
                val body = call.receiveText()
                val (responseBody, statusCode) = EphemeralMcpBridge.handleRequest(token, body)
                if (responseBody != null) {
                    call.respondText(responseBody, ContentType.Application.Json, HttpStatusCode.fromValue(statusCode))
                } else {
                    call.respond(HttpStatusCode.Accepted)
                }
            }

            // ── Debug Image Gallery ──────────────────────────────────

            get("/debug/images/{imageId}") {
                val imageId = call.parameters["imageId"] ?: return@get call.respond(HttpStatusCode.NotFound)
                val image = McpHandler.getCachedImage(imageId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respondBytes(image.imageData, ContentType.parse(image.mimeType))
            }

            get("/debug/gallery") {
                if (authEnabled && AuthHandler.verifyRequest(call) == null) {
                    call.respondRedirect("/auth")
                    return@get
                }
                val images = McpHandler.getCachedImages()
                val html = buildImageGalleryHtml(images)
                call.respondText(html, ContentType.Text.Html)
            }

            get("/debug/gallery/json") {
                if (authEnabled && AuthHandler.verifyRequest(call) == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                val images = McpHandler.getCachedImages()
                val json = buildString {
                    append("[")
                    images.forEachIndexed { i, img ->
                        if (i > 0) append(",")
                        append("""{"id":"${img.id}","type":"${img.type}","label":"${img.label.replace("\"", "\\\"")}","url":"/debug/images/${img.id}","prompt":"${img.prompt.replace("\"", "\\\"").replace("\n", "\\n")}","timestamp":${img.timestamp}}""")
                    }
                    append("]")
                }
                call.respondText(json, ContentType.Application.Json)
            }

            // WebSocket for receptionist onboarding — no game engine needed
            webSocket("/ws/receptionist") {
                if (authEnabled) {
                    val token = call.request.queryParameters["token"]
                        ?: call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
                    if (token == null || !AuthHandler.isValidToken(token)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                        return@webSocket
                    }
                }
                ReceptionistWebSocketHandler.handle(this)
            }

            // WebSocket for game session — mobile app connects here
            webSocket("/ws/game/{sessionId}") {
                // WebSocket auth via query param (browsers can't set headers on WS upgrade)
                if (authEnabled) {
                    val token = call.request.queryParameters["token"]
                        ?: call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
                    if (token == null || !AuthHandler.isValidToken(token)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                        return@webSocket
                    }
                }

                val sessionId = call.parameters["sessionId"]
                    ?: return@webSocket close(
                        CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing sessionId")
                    )

                val gameSession = GameSessionManager.getSession(sessionId)
                    ?: return@webSocket close(
                        CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid sessionId")
                    )

                GameWebSocketHandler.handle(this, gameSession)
            }
        }
    }.start(wait = true)
}
