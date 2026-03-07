package com.rpgenerator.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

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
                val session = GameSessionManager.createSession()
                call.respondText(
                    """{"sessionId": "${session.id}"}""",
                    ContentType.Application.Json
                )
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
