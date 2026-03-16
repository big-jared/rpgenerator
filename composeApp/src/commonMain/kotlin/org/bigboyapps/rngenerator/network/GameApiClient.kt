package org.bigboyapps.rngenerator.network

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val log = Logger.withTag("GameApiClient")

/**
 * REST client for game server API.
 */
class GameApiClient(
    private val baseUrl: String
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    /**
     * Exchange a Google ID token for a server access token.
     */
    suspend fun exchangeToken(googleIdToken: String): String {
        val response = client.post("$baseUrl/auth/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("grant_type=google_id_token&id_token=${googleIdToken}")
        }
        checkResponse(response, "exchangeToken")
        val body = response.bodyAsText()
        val parsed = json.decodeFromString<TokenResponse>(body)
        return parsed.token
    }

    /**
     * Create a new game session on the server.
     * Returns the session ID.
     */
    suspend fun createGame(
        name: String? = null,
        backstory: String? = null,
        seedId: String? = null,
        authToken: String? = null
    ): String {
        log.i { "🌐 createGame: name=$name, seedId=$seedId, hasAuth=${authToken != null}" }
        val response = client.post("$baseUrl/api/game/create") {
            contentType(ContentType.Application.Json)
            if (authToken != null) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }
            setBody(CreateGameRequest(name = name, backstory = backstory, seedId = seedId))
        }
        checkResponse(response, "createGame")
        val body = response.bodyAsText()
        log.d { "🌐 createGame response: $body" }
        val parsed = json.decodeFromString<CreateGameResponse>(body)
        log.i { "🌐 createGame success: sessionId=${parsed.sessionId}" }
        return parsed.sessionId
    }

    /**
     * Get current game state snapshot.
     */
    suspend fun getGameState(sessionId: String): JsonObject {
        log.d { "🌐 getGameState: sessionId=$sessionId" }
        val response = client.get("$baseUrl/api/game/$sessionId/state")
        checkResponse(response, "getGameState")
        val body = response.bodyAsText()
        log.d { "🌐 getGameState response: ${body}..." }
        return json.decodeFromString(body)
    }

    /**
     * Execute a tool on the server and return the result.
     */
    suspend fun executeTool(sessionId: String, name: String, args: Map<String, String>): ToolExecutionResult {
        log.i { "🌐 executeTool: $name($args) sessionId=$sessionId" }
        val response = client.post("$baseUrl/api/game/$sessionId/tool") {
            contentType(ContentType.Application.Json)
            setBody(ToolExecutionRequest(name = name, args = args))
        }
        checkResponse(response, "executeTool($name)")
        val body = response.bodyAsText()
        log.d { "🌐 executeTool response: $body" }
        return json.decodeFromString(body)
    }

    /**
     * Get detailed NPC info for the NPC detail sheet.
     */
    suspend fun getNpcDetails(sessionId: String, npcId: String): NpcDetailsDto {
        val response = client.get("$baseUrl/api/game/$sessionId/npc/$npcId")
        checkResponse(response, "getNpcDetails($npcId)")
        val body = response.bodyAsText()
        return json.decodeFromString(body)
    }

    /**
     * List all saved games on the server.
     */
    suspend fun listSaves(authToken: String? = null): List<SavedGameInfo> {
        log.i { "🌐 listSaves" }
        val response = client.get("$baseUrl/api/saves") {
            if (authToken != null) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }
        }
        checkResponse(response, "listSaves")
        val body = response.bodyAsText()
        log.d { "🌐 listSaves response: $body" }
        return json.decodeFromString(body)
    }

    /**
     * Load (resume) a saved game. Returns the new session ID.
     */
    suspend fun loadGame(gameId: String, authToken: String? = null): String {
        log.i { "🌐 loadGame: gameId=$gameId" }
        val response = client.post("$baseUrl/api/game/load") {
            contentType(ContentType.Application.Json)
            if (authToken != null) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }
            setBody("""{"gameId": "$gameId"}""")
        }
        checkResponse(response, "loadGame")
        val body = response.bodyAsText()
        log.d { "🌐 loadGame response: $body" }
        val parsed = json.decodeFromString<CreateGameResponse>(body)
        return parsed.sessionId
    }

    /**
     * Save the current game state.
     */
    suspend fun saveGame(sessionId: String, authToken: String? = null) {
        log.i { "🌐 saveGame: sessionId=$sessionId" }
        val response = client.post("$baseUrl/api/game/$sessionId/save") {
            if (authToken != null) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }
        }
        checkResponse(response, "saveGame")
    }

    /**
     * Get recent feed entries for pre-populating the feed on load/resume.
     */
    suspend fun fetchEvents(sessionId: String, limit: Int = 50, authToken: String? = null): List<FeedEntryDto> {
        log.d { "🌐 fetchEvents: sessionId=$sessionId, limit=$limit" }
        val response = client.get("$baseUrl/api/game/$sessionId/events?limit=$limit") {
            if (authToken != null) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }
        }
        checkResponse(response, "fetchEvents")
        val body = response.bodyAsText()
        return json.decodeFromString(body)
    }

    /**
     * Generate player portrait via the tool execution endpoint.
     */
    suspend fun generatePortrait(
        sessionId: String,
        characterName: String,
        appearance: String,
        characterClass: String? = null
    ): ToolExecutionResult {
        return executeTool(sessionId, "generate_portrait", buildMap {
            put("characterName", characterName)
            put("appearance", appearance)
            if (characterClass != null) put("characterClass", characterClass)
            put("mood", "heroic")
            put("framing", "BUST")
        })
    }

    private suspend fun checkResponse(response: HttpResponse, context: String) {
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw RuntimeException("$context failed: HTTP ${response.status.value} — ${body.take(200).ifEmpty { "empty response" }}")
        }
    }

    fun close() {
        client.close()
    }
}
