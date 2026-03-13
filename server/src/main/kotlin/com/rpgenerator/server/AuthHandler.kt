package com.rpgenerator.server

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * OAuth 2.0 Authorization Server for RPGenerator MCP.
 *
 * Implements the MCP OAuth flow:
 * 1. Claude Code hits /mcp → gets 401
 * 2. Fetches /.well-known/oauth-authorization-server → gets endpoints
 * 3. Registers via POST /oauth/register (dynamic client registration)
 * 4. Opens browser to GET /oauth/authorize → user signs in with Google
 * 5. Google callback → server issues auth code → redirects to client
 * 6. Client exchanges code via POST /oauth/token → gets access_token
 * 7. Client uses Bearer token for all MCP requests
 */
object AuthHandler {

    private val googleClientId: String by lazy {
        System.getenv("GOOGLE_OAUTH_CLIENT_ID")
            ?: error("GOOGLE_OAUTH_CLIENT_ID environment variable not set")
    }

    private val googleVerifier: GoogleIdTokenVerifier by lazy {
        GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(listOf(googleClientId))
            .build()
    }

    // Firebase Admin SDK — initialized once, uses Application Default Credentials on Cloud Run
    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(System.getenv("GOOGLE_CLOUD_PROJECT") ?: "rpgenerator-f89d6")
                    .build())
            }
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            println("AuthHandler: Firebase Admin SDK init failed: ${e.message}")
            null
        }
    }

    /**
     * Verify a token as a Firebase ID token. Returns user info or null.
     */
    private fun verifyFirebaseToken(token: String): AuthenticatedUser? {
        return try {
            val decoded: FirebaseToken = firebaseAuth?.verifyIdToken(token) ?: return null
            AuthenticatedUser(
                email = decoded.email ?: "",
                name = decoded.name ?: decoded.email ?: "",
                pictureUrl = decoded.picture,
                googleId = decoded.uid,
                token = token
            )
        } catch (_: Exception) {
            null
        }
    }

    // Bearer token → authenticated user
    private val sessions = ConcurrentHashMap<String, AuthenticatedUser>()

    // Authorization code → pending auth (short-lived, exchanged for access token)
    private val pendingAuthCodes = ConcurrentHashMap<String, PendingAuth>()

    // Dynamic client registrations
    private val registeredClients = ConcurrentHashMap<String, RegisteredClient>()

    // Pending OAuth flows (state → flow params)
    private val pendingFlows = ConcurrentHashMap<String, OAuthFlowState>()

    data class AuthenticatedUser(
        val email: String,
        val name: String,
        val pictureUrl: String?,
        val googleId: String,
        val token: String,
        val createdAt: Long = System.currentTimeMillis()
    )

    private data class PendingAuth(
        val code: String,
        val user: AuthenticatedUser,
        val clientId: String,
        val redirectUri: String,
        val codeChallenge: String?,
        val createdAt: Long = System.currentTimeMillis()
    )

    private data class RegisteredClient(
        val clientId: String,
        val clientSecret: String,
        val redirectUris: List<String>,
        val clientName: String
    )

    private data class OAuthFlowState(
        val state: String,
        val clientId: String,
        val redirectUri: String,
        val codeChallenge: String?,
        val codeChallengeMethod: String?
    )

    // ── OAuth Metadata ──────────────────────────────────────────────

    fun getServerMetadata(baseUrl: String): String {
        return """
{
    "issuer": "$baseUrl",
    "authorization_endpoint": "$baseUrl/oauth/authorize",
    "token_endpoint": "$baseUrl/oauth/token",
    "registration_endpoint": "$baseUrl/oauth/register",
    "response_types_supported": ["code"],
    "grant_types_supported": ["authorization_code"],
    "token_endpoint_auth_methods_supported": ["client_secret_post", "none"],
    "code_challenge_methods_supported": ["S256", "plain"]
}
""".trimIndent()
    }

    // ── Dynamic Client Registration ─────────────────────────────────

    suspend fun handleClientRegistration(call: RoutingCall) {
        val body = call.receiveText()
        val json = try {
            kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
        } catch (e: Exception) {
            call.respondText("""{"error": "invalid_request"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            return
        }

        val clientName = json["client_name"]?.let {
            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
        } ?: "unknown"

        val redirectUris = json["redirect_uris"]?.let { element ->
            (element as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
            }
        } ?: emptyList()

        val clientId = "rpg_${UUID.randomUUID().toString().take(16)}"
        val clientSecret = UUID.randomUUID().toString()

        val client = RegisteredClient(
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUris = redirectUris,
            clientName = clientName
        )
        registeredClients[clientId] = client

        call.respondText(
            """{"client_id":"$clientId","client_secret":"$clientSecret","client_name":"$clientName","redirect_uris":${redirectUris.joinToString(",", "[", "]") { "\"$it\"" }}}""",
            ContentType.Application.Json,
            HttpStatusCode.Created
        )
    }

    // ── Authorization Endpoint ──────────────────────────────────────

    suspend fun handleAuthorize(call: RoutingCall) {
        val clientId = call.request.queryParameters["client_id"] ?: ""
        val redirectUri = call.request.queryParameters["redirect_uri"] ?: ""
        val state = call.request.queryParameters["state"] ?: ""
        val codeChallenge = call.request.queryParameters["code_challenge"]
        val codeChallengeMethod = call.request.queryParameters["code_challenge_method"]

        // Store the OAuth flow state
        pendingFlows[state] = OAuthFlowState(
            state = state,
            clientId = clientId,
            redirectUri = redirectUri,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod
        )

        // Show Google Sign-In page that will complete the OAuth flow
        call.respondText(getAuthorizePage(state), ContentType.Text.Html)
    }

    // ── Google Sign-In Callback (from the authorize page) ───────────

    suspend fun handleGoogleCallback(call: RoutingCall) {
        val body = call.receiveText()
        val json = try {
            kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
        } catch (e: Exception) {
            call.respondText("""{"error": "invalid_request"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            return
        }

        val idTokenString = (json["idToken"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val state = (json["state"] as? kotlinx.serialization.json.JsonPrimitive)?.content

        if (idTokenString == null || state == null) {
            call.respondText("""{"error": "Missing idToken or state"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            return
        }

        val flow = pendingFlows.remove(state)
        if (flow == null) {
            call.respondText("""{"error": "Invalid or expired state"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            return
        }

        // Verify Google ID token
        val idToken = try { googleVerifier.verify(idTokenString) } catch (e: Exception) { null }
        if (idToken == null) {
            call.respondText("""{"error": "Invalid Google token"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
            return
        }

        val payload = idToken.payload
        val email = payload.email
        val name = payload["name"] as? String ?: email
        val picture = payload["picture"] as? String
        val googleId = payload.subject

        // Create or reuse user session
        val accessToken = UUID.randomUUID().toString()
        val user = AuthenticatedUser(
            email = email,
            name = name,
            pictureUrl = picture,
            googleId = googleId,
            token = accessToken
        )
        sessions[accessToken] = user

        // Generate authorization code
        val authCode = UUID.randomUUID().toString()
        pendingAuthCodes[authCode] = PendingAuth(
            code = authCode,
            user = user,
            clientId = flow.clientId,
            redirectUri = flow.redirectUri,
            codeChallenge = flow.codeChallenge
        )

        // Return redirect URL for the browser to follow
        val redirectUrl = "${flow.redirectUri}?code=$authCode&state=$state"
        call.respondText(
            """{"redirect": "$redirectUrl"}""",
            ContentType.Application.Json
        )
    }

    // ── Token Endpoint ──────────────────────────────────────────────

    suspend fun handleTokenExchange(call: RoutingCall) {
        val params = call.receiveText()
        val formParams = params.split("&").associate {
            val (key, value) = it.split("=", limit = 2)
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

        val grantType = formParams["grant_type"]
        val code = formParams["code"]
        val codeVerifier = formParams["code_verifier"]

        if (grantType != "authorization_code" || code == null) {
            call.respondText(
                """{"error": "invalid_grant"}""",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest
            )
            return
        }

        val pending = pendingAuthCodes.remove(code)
        if (pending == null) {
            call.respondText(
                """{"error": "invalid_grant", "error_description": "Code expired or already used"}""",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest
            )
            return
        }

        // Verify PKCE code_verifier if code_challenge was provided
        if (pending.codeChallenge != null && codeVerifier != null) {
            val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray())
            val computed = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
            if (computed != pending.codeChallenge) {
                call.respondText(
                    """{"error": "invalid_grant", "error_description": "PKCE verification failed"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.BadRequest
                )
                return
            }
        }

        call.respondText(
            """{"access_token": "${pending.user.token}", "token_type": "bearer", "scope": "mcp"}""",
            ContentType.Application.Json
        )
    }

    // ── Request Verification ────────────────────────────────────────

    private val workerSharedSecret: String? by lazy {
        System.getenv("WORKER_SHARED_SECRET")
    }

    /**
     * Verify a request. Accepts either:
     * 1. X-Worker-Secret + X-User-Id + X-User-Email headers (from Cloudflare Worker proxy)
     * 2. Bearer token (from direct /auth page sign-in)
     */
    fun verifyRequest(call: RoutingCall): AuthenticatedUser? {
        // Check for Worker-proxied identity headers (requires matching shared secret)
        val secret = call.request.headers["X-Worker-Secret"]
        val userId = call.request.headers["X-User-Id"]
        val userEmail = call.request.headers["X-User-Email"]
        if (secret != null && userId != null && userEmail != null
            && workerSharedSecret != null && secret == workerSharedSecret) {
            return AuthenticatedUser(
                email = userEmail,
                name = userEmail,
                pictureUrl = null,
                googleId = userId,
                token = "worker-proxied"
            )
        }

        // Fall back to bearer token
        val authHeader = call.request.header(HttpHeaders.Authorization) ?: return null
        if (!authHeader.startsWith("Bearer ", ignoreCase = true)) return null
        val token = authHeader.removePrefix("Bearer ").removePrefix("bearer ").trim()

        // Check in-memory sessions first (tokens from /auth/token exchange)
        sessions[token]?.let { return it }

        // Try verifying as a raw Google ID token
        try {
            val idToken = googleVerifier.verify(token)
            if (idToken != null) {
                val payload = idToken.payload
                return AuthenticatedUser(
                    email = payload.email,
                    name = (payload["name"] as? String) ?: payload.email,
                    pictureUrl = payload["picture"] as? String,
                    googleId = payload.subject,
                    token = token
                )
            }
        } catch (_: Exception) {}

        // Try verifying as a Firebase ID token (mobile apps send these)
        return verifyFirebaseToken(token)
    }

    fun isValidToken(token: String): Boolean {
        if (sessions.containsKey(token)) return true
        // Try Google ID token
        try { if (googleVerifier.verify(token) != null) return true } catch (_: Exception) {}
        // Try Firebase ID token
        return verifyFirebaseToken(token) != null
    }

    fun isEnabled(): Boolean {
        return System.getenv("GOOGLE_OAUTH_CLIENT_ID") != null
    }

    // ── Manual Auth Page (fallback / direct use) ────────────────────

    fun getLoginPageHtml(): String {
        val oauthClientId = if (isEnabled()) googleClientId else "NOT_CONFIGURED"
        return """
<!DOCTYPE html>
<html><head>
<title>RPGenerator - Sign In</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { background: #0a0a0f; color: #e0e0e0; font-family: 'Segoe UI', system-ui, sans-serif;
         display: flex; justify-content: center; align-items: center; min-height: 100vh; }
  .card { background: #12121f; border: 1px solid #222; border-radius: 12px; padding: 40px;
          max-width: 440px; width: 90%; text-align: center; }
  h1 { color: #7c4dff; font-size: 1.8em; margin-bottom: 8px; }
  .subtitle { color: #666; margin-bottom: 32px; }
  .token-box { display: none; margin-top: 24px; text-align: left; }
  .token-box label { color: #aaa; font-size: 0.85em; display: block; margin-bottom: 6px; }
  .token-box .token { background: #1a1a2e; border: 1px solid #333; border-radius: 6px; padding: 12px;
                       font-family: monospace; font-size: 0.8em; word-break: break-all; color: #7c4dff;
                       cursor: pointer; }
  .token-box .mcp-config { background: #1a1a2e; border: 1px solid #333; border-radius: 6px; padding: 12px;
                            font-family: monospace; font-size: 0.7em; white-space: pre; overflow-x: auto;
                            color: #ccc; margin-top: 16px; }
  .welcome { color: #7c4dff; margin-bottom: 12px; }
  .copied { color: #4caf50; font-size: 0.85em; margin-top: 8px; }
  .error { color: #f44336; margin-top: 12px; }
</style>
<script src="https://accounts.google.com/gsi/client" async></script>
</head><body>
<div class="card">
  <h1>RPGenerator</h1>
  <p class="subtitle">Sign in to play</p>
  <div id="signin-area">
    <div id="g_id_onload" data-client_id="$oauthClientId"
         data-callback="handleCredentialResponse" data-auto_prompt="false"></div>
    <div class="g_id_signin" data-type="standard" data-size="large"
         data-theme="filled_black" data-text="sign_in_with" data-shape="rectangular"></div>
  </div>
  <div id="error" class="error"></div>
  <div id="token-area" class="token-box">
    <p class="welcome" id="welcome"></p>
    <label>Your Bearer Token:</label>
    <div class="token" id="token" onclick="copyToken()"></div>
    <div class="copied" id="copied" style="display:none">Copied!</div>
    <label style="margin-top: 16px;">MCP config (.mcp.json):</label>
    <div class="mcp-config" id="mcp-config"></div>
  </div>
</div>
<script>
function handleCredentialResponse(response) {
  fetch('/auth/token', {
    method: 'POST',
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: 'grant_type=google_id_token&id_token=' + encodeURIComponent(response.credential)
  }).then(r => r.json()).then(data => {
    if (data.error) { document.getElementById('error').textContent = data.error; return; }
    document.getElementById('signin-area').style.display = 'none';
    document.getElementById('token-area').style.display = 'block';
    document.getElementById('welcome').textContent = 'Welcome, ' + (data.name || data.email) + '!';
    document.getElementById('token').textContent = data.access_token;
    document.getElementById('mcp-config').textContent = JSON.stringify({
      mcpServers: { rpgenerator: { type: "http", url: location.origin + "/mcp",
        headers: { Authorization: "Bearer " + data.access_token } } }
    }, null, 2);
  }).catch(err => { document.getElementById('error').textContent = err.message; });
}
function copyToken() {
  navigator.clipboard.writeText(document.getElementById('token').textContent).then(() => {
    document.getElementById('copied').style.display = 'block';
    setTimeout(() => document.getElementById('copied').style.display = 'none', 2000);
  });
}
</script>
</body></html>
""".trimIndent()
    }

    // ── OAuth Authorize Page (for MCP OAuth flow) ───────────────────

    private fun getAuthorizePage(state: String): String {
        val oauthClientId = googleClientId
        return """
<!DOCTYPE html>
<html><head>
<title>RPGenerator - Authorize</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { background: #0a0a0f; color: #e0e0e0; font-family: 'Segoe UI', system-ui, sans-serif;
         display: flex; justify-content: center; align-items: center; min-height: 100vh; }
  .card { background: #12121f; border: 1px solid #222; border-radius: 12px; padding: 40px;
          max-width: 440px; width: 90%; text-align: center; }
  h1 { color: #7c4dff; font-size: 1.6em; margin-bottom: 8px; }
  .subtitle { color: #666; margin-bottom: 24px; }
  .status { color: #4caf50; margin-top: 16px; }
  .error { color: #f44336; margin-top: 12px; }
</style>
<script src="https://accounts.google.com/gsi/client" async></script>
</head><body>
<div class="card">
  <h1>RPGenerator</h1>
  <p class="subtitle">Sign in to authorize access</p>
  <div id="signin-area">
    <div id="g_id_onload" data-client_id="$oauthClientId"
         data-callback="handleCredentialResponse" data-auto_prompt="false"></div>
    <div class="g_id_signin" data-type="standard" data-size="large"
         data-theme="filled_black" data-text="sign_in_with" data-shape="rectangular"></div>
  </div>
  <div id="status" class="status" style="display:none">Authorized! Redirecting...</div>
  <div id="error" class="error"></div>
</div>
<script>
function handleCredentialResponse(response) {
  document.getElementById('signin-area').style.display = 'none';
  document.getElementById('status').style.display = 'block';
  document.getElementById('status').textContent = 'Verifying...';

  fetch('/oauth/callback', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({idToken: response.credential, state: "$state"})
  }).then(r => r.json()).then(data => {
    if (data.error) {
      document.getElementById('status').style.display = 'none';
      document.getElementById('error').textContent = data.error;
      return;
    }
    document.getElementById('status').textContent = 'Authorized! Redirecting...';
    window.location.href = data.redirect;
  }).catch(err => {
    document.getElementById('status').style.display = 'none';
    document.getElementById('error').textContent = err.message;
  });
}
</script>
</body></html>
""".trimIndent()
    }

    // ── Direct token exchange (for /auth page) ──────────────────────

    suspend fun handleDirectTokenExchange(call: RoutingCall) {
        val params = call.receiveText()
        val formParams = params.split("&").associate {
            val parts = it.split("=", limit = 2)
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
        }

        val grantType = formParams["grant_type"]

        if (grantType == "google_id_token") {
            // Direct Google ID token → access token (for /auth page)
            val idTokenString = formParams["id_token"] ?: run {
                call.respondText("""{"error": "missing id_token"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return
            }

            val idToken = try { googleVerifier.verify(idTokenString) } catch (e: Exception) { null }
            if (idToken == null) {
                call.respondText("""{"error": "invalid_token"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                return
            }

            val payload = idToken.payload
            val existingUser = sessions.values.find { it.googleId == payload.subject }
            if (existingUser != null) {
                call.respondText(
                    """{"access_token":"${existingUser.token}","token_type":"bearer","name":"${existingUser.name.replace("\"", "\\\"")}","email":"${existingUser.email}"}""",
                    ContentType.Application.Json
                )
                return
            }

            val token = UUID.randomUUID().toString()
            val user = AuthenticatedUser(
                email = payload.email,
                name = (payload["name"] as? String) ?: payload.email,
                pictureUrl = payload["picture"] as? String,
                googleId = payload.subject,
                token = token
            )
            sessions[token] = user

            call.respondText(
                """{"access_token":"$token","token_type":"bearer","name":"${user.name.replace("\"", "\\\"")}","email":"${user.email}"}""",
                ContentType.Application.Json
            )
        } else if (grantType == "authorization_code") {
            // Standard OAuth code exchange (for MCP OAuth flow)
            handleTokenExchange(call)
        } else {
            call.respondText("""{"error": "unsupported_grant_type"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
        }
    }

    private object URLDecoder {
        fun decode(s: String, charset: String): String = java.net.URLDecoder.decode(s, charset)
    }
}
