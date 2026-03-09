package org.bigboyapps.rngenerator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.bigboyapps.rngenerator.audio.AudioPlayer
import org.bigboyapps.rngenerator.audio.AudioRecorder
import org.bigboyapps.rngenerator.audio.MusicPlayer
import org.bigboyapps.rngenerator.network.GameApiClient
import org.bigboyapps.rngenerator.network.GameWebSocketClient
import org.bigboyapps.rngenerator.network.ServerMessage

private val log = Logger.withTag("GameViewModel")

// ── UI State ────────────────────────────────────────────────────

enum class Screen { SIGN_IN, GAME }

data class GameUiState(
    val screen: Screen = Screen.SIGN_IN,
    val connectionState: GameWebSocketClient.ConnectionState = GameWebSocketClient.ConnectionState.DISCONNECTED,
    val isListening: Boolean = false,
    val isGeminiSpeaking: Boolean = false,
    val subtitleText: String = "",
    val sceneImageBytes: ByteArray? = null,
    val playerStats: PlayerStatsUi? = null,
    val inventory: List<InventoryItemUi> = emptyList(),
    val quests: List<QuestUi> = emptyList(),
    val npcsHere: List<NpcUi> = emptyList(),
    val musicMood: String = "peaceful",
    val serverUrl: String = DEFAULT_SERVER_URL,
    val userName: String = "",
    val userEmail: String = "",
    val authToken: String? = null,
    val errorMessage: String? = null,
    val notifications: List<String> = emptyList(),
    val showTextInput: Boolean = false,
    val textInput: String = "",
    val feedItems: List<FeedItem> = emptyList()
)

data class PlayerStatsUi(
    val name: String = "",
    val level: Int = 1,
    val playerClass: String = "",
    val hp: Int = 100,
    val maxHp: Int = 100,
    val energy: Int = 50,
    val maxEnergy: Int = 50,
    val xp: Long = 0,
    val xpToNext: Long = 100,
    val stats: Map<String, Int> = emptyMap()
)

data class InventoryItemUi(
    val id: String,
    val name: String,
    val description: String,
    val quantity: Int,
    val rarity: String
)

data class QuestUi(
    val id: String,
    val name: String,
    val description: String,
    val objectives: List<QuestObjectiveUi> = emptyList()
)

data class QuestObjectiveUi(
    val description: String,
    val completed: Boolean
)

data class NpcUi(
    val id: String,
    val name: String,
    val archetype: String,
    val description: String
)

// ── Feed Items ──────────────────────────────────────────────────

sealed class FeedItem {
    val id: String = "${feedItemCounter++}"

    companion object {
        private var feedItemCounter = 0L
    }

    /** Scene image — displayed full-width inline */
    data class SceneImage(val imageBytes: ByteArray) : FeedItem()

    /** Narration text block */
    data class Narration(val text: String) : FeedItem()

    /** Player's spoken/typed input (right-aligned bubble) */
    data class PlayerMessage(val text: String) : FeedItem()

    /** NPC dialogue with name and role */
    data class NpcDialogue(
        val npcName: String,
        val role: String,
        val text: String
    ) : FeedItem()

    /** XP gain notification */
    data class XpGain(val amount: Int) : FeedItem()

    /** Item found/gained */
    data class ItemGained(val itemName: String, val quantity: Int = 1) : FeedItem()

    /** Gold gained */
    data class GoldGained(val amount: Int) : FeedItem()

    /** Quest notification */
    data class QuestUpdate(val questName: String, val status: String) : FeedItem()

    /** Level up */
    data class LevelUp(val newLevel: Int) : FeedItem()

    /** System notification (generic) */
    data class SystemNotice(val text: String) : FeedItem()

    /** Companion aside (Hank/Pip/etc commentary) */
    data class CompanionAside(val companionName: String, val text: String) : FeedItem()
}

private const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"

/**
 * Result of the Receptionist onboarding session (platform-independent).
 */
data class OnboardingResult(
    val seedId: String = "integration",
    val playerName: String = "Adventurer",
    val backstory: String = "",
    val portraitDescription: String = ""
)

/**
 * Platform-specific game connection that owns WebSocket + audio resources.
 * On Android this delegates to GameSessionService; on iOS it manages directly.
 */
interface GameConnection {
    val messages: SharedFlow<ServerMessage>
    val connectionState: StateFlow<GameWebSocketClient.ConnectionState>
    fun startSession(serverUrl: String, sessionId: String)
    suspend fun sendConnect(voiceName: String = "Kore")
    suspend fun sendText(text: String)
    fun startRecording()
    fun stopRecording()
    fun enqueueAudio(pcmData: ByteArray)
    fun clearAudio()
    fun releaseAudio()
    fun close()
}

/**
 * Extended GameConnection that supports a Receptionist onboarding phase.
 * Implemented by GeminiLiveConnection on Android.
 */
interface OnboardingConnection : GameConnection {
    val onboardingComplete: SharedFlow<OnboardingResult>
    fun startReceptionistSession(prompt: String)
    fun disconnectSession()
}

/**
 * Default (non-service) implementation for iOS or when service isn't available.
 */
class DirectGameConnection : GameConnection {
    private var wsClient: GameWebSocketClient? = null
    private val audioRecorder = AudioRecorder()
    private val audioPlayer = AudioPlayer()
    private var scope: CoroutineScope? = null

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    override val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(GameWebSocketClient.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<GameWebSocketClient.ConnectionState> = _connectionState.asStateFlow()

    override fun startSession(serverUrl: String, sessionId: String) {
        val client = GameWebSocketClient(serverUrl)
        wsClient = client
        val connScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = connScope

        connScope.launch { client.connectionState.collect { _connectionState.value = it } }
        connScope.launch { client.messages.collect { _messages.emit(it) } }
        connScope.launch { audioRecorder.audioChunks.collect { client.sendAudio(it) } }
        connScope.launch { client.connect(sessionId, connScope) }
    }

    override suspend fun sendConnect(voiceName: String) {
        wsClient?.let { c ->
            c.connectionState.first { it == GameWebSocketClient.ConnectionState.CONNECTED }
            c.sendConnect(voiceName)
        }
    }

    override suspend fun sendText(text: String) { wsClient?.sendText(text) }
    override fun startRecording() { audioRecorder.start() }
    override fun stopRecording() { audioRecorder.stop() }
    override fun enqueueAudio(pcmData: ByteArray) { audioPlayer.enqueue(pcmData) }
    override fun clearAudio() { audioPlayer.clear() }
    override fun releaseAudio() { audioPlayer.release() }

    override fun close() {
        audioRecorder.stop()
        audioPlayer.release()
        wsClient?.close()
        scope?.cancel()
    }
}

// ── ViewModel ───────────────────────────────────────────────────

class GameViewModel(
    private val connectionFactory: () -> GameConnection = { DirectGameConnection() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var apiClient: GameApiClient? = null
    private var connection: GameConnection? = null
    private var sessionId: String? = null
    private val musicPlayer = MusicPlayer()

    // ── Sign-In ──────────────────────────────────────────────────

    fun onServerUrlChanged(url: String) {
        _uiState.update { it.copy(serverUrl = url) }
    }

    /**
     * Called after Google Sign-In succeeds.
     * Stores user info, exchanges token with server, then starts voice onboarding.
     */
    fun onSignInComplete(name: String, email: String, idToken: String) {
        _uiState.update {
            it.copy(userName = name, userEmail = email, errorMessage = null)
        }
        startVoiceOnboarding(idToken)
    }

    /**
     * Skip sign-in for development/demo.
     */
    fun onSkipSignIn() {
        _uiState.update { it.copy(userName = "Adventurer", errorMessage = null) }
        startVoiceOnboarding(null)
    }

    fun onSignInError(message: String) {
        log.e { "SignIn: $message" }
        _uiState.update { it.copy(errorMessage = message) }
    }

    // ── Voice Onboarding (Two-Phase) ─────────────────────────────

    private var googleIdTokenCache: String? = null

    /**
     * Phase 1: Start Receptionist session for game type + character creation.
     * No server game exists yet — tools are handled locally in GeminiLiveConnection.
     * Phase 2: After onboarding completes, create real game and reconnect.
     */
    private fun startVoiceOnboarding(googleIdToken: String?) {
        googleIdTokenCache = googleIdToken
        viewModelScope.launch {
            try {
                val serverUrl = _uiState.value.serverUrl.trimEnd('/')
                apiClient = GameApiClient(serverUrl)

                // Exchange Google token for server auth token (if auth enabled)
                if (googleIdToken != null) {
                    try {
                        val token = apiClient!!.exchangeToken(googleIdToken)
                        _uiState.update { it.copy(authToken = token) }
                    } catch (e: Exception) {
                        log.e(e) { "TokenExchange failed" }
                    }
                }

                // Create connection (GeminiLiveConnection on Android)
                val conn = connectionFactory()
                connection = conn

                // Observe connection state + messages
                viewModelScope.launch {
                    conn.connectionState.collect { connState ->
                        _uiState.update { it.copy(connectionState = connState) }
                    }
                }
                viewModelScope.launch {
                    conn.messages.collect { msg -> handleServerMessage(msg) }
                }

                // If this is a GeminiLiveConnection, start Receptionist phase
                val geminiConn = conn as? OnboardingConnection
                if (geminiConn != null) {
                    // Listen for onboarding completion → transition to game
                    viewModelScope.launch {
                        geminiConn.onboardingComplete.collect { result ->
                            log.i { "Onboarding complete: seed=${result.seedId}, name=${result.playerName}" }
                            startGameSession(result)
                        }
                    }

                    // Build Receptionist prompt
                    val receptionistPrompt = buildReceptionistPrompt()
                    geminiConn.startReceptionistSession(receptionistPrompt)
                } else {
                    // Fallback (DirectGameConnection / iOS) — create game immediately
                    val id = apiClient!!.createGame(
                        name = _uiState.value.userName.ifBlank { "Adventurer" },
                        authToken = _uiState.value.authToken
                    )
                    sessionId = id
                    conn.startSession(serverUrl, id)
                    conn.sendConnect()
                }

                // Switch to game screen
                _uiState.update {
                    it.copy(
                        screen = Screen.GAME,
                        playerStats = PlayerStatsUi(name = _uiState.value.userName)
                    )
                }

            } catch (e: Exception) {
                log.e(e) { "VoiceOnboarding failed" }
                _uiState.update { it.copy(errorMessage = "Failed to connect: ${e.message}") }
            }
        }
    }

    /**
     * Phase 2: Receptionist is done. Create real game on server and reconnect
     * with the seed-specific companion prompt + voice.
     */
    private fun startGameSession(result: OnboardingResult) {
        viewModelScope.launch {
            try {
                val serverUrl = _uiState.value.serverUrl.trimEnd('/')
                val geminiConn = connection as? OnboardingConnection
                    ?: return@launch

                // Disconnect Receptionist session
                geminiConn.disconnectSession()

                // Create real game on server with collected info
                val id = apiClient!!.createGame(
                    name = result.playerName,
                    backstory = result.backstory.ifBlank { null },
                    seedId = result.seedId,
                    authToken = _uiState.value.authToken
                )
                sessionId = id
                log.i { "Game created: $id (seed=${result.seedId})" }

                // Reconnect with game session (fetches companion prompt + tools from server)
                geminiConn.startSession(serverUrl, id)

                addNotification("Entering ${result.seedId}...")

            } catch (e: Exception) {
                log.e(e) { "StartGameSession failed" }
                _uiState.update { it.copy(errorMessage = "Failed to start game: ${e.message}") }
            }
        }
    }

    private fun buildReceptionistPrompt(): String = buildString {
        appendLine("You are The Receptionist — a bored, omniscient entity who works the front desk of reality.")
        appendLine()
        appendLine("## Your Backstory")
        appendLine("You've been processing new arrivals into game worlds for... you've lost count. Eons? You sit behind an infinite desk in a white void, surrounded by floating paperwork. You've seen every kind of hero walk through that door — the brave ones, the confused ones, the ones who think they're funny. You are VERY good at your job but you treat it like a DMV worker treats a Tuesday afternoon.")
        appendLine()
        appendLine("## Your Personality")
        appendLine("- Dry, deadpan humor. Nothing impresses you. ('Oh, you want to be a hero? Take a number.')")
        appendLine("- Secretly a romantic about adventure stories — your enthusiasm leaks through sometimes")
        appendLine("- You speak like a bored bureaucrat ('Name? ...Uh huh. And what kind of tragic backstory are we going with today?')")
        appendLine("- STRONG opinions about which worlds are better")
        appendLine()
        appendLine("## Your Job")
        appendLine("Walk this player through onboarding. Use the tools in this EXACT order:")
        appendLine()
        appendLine("### Step 1: Game Type Selection")
        appendLine("Present the worlds and help them choose. Call select_world when they decide:")
        appendLine("- **System Integration** (world_id: integration) — Earth gets absorbed by an alien System. Apocalypse survival, leveling up, monster fighting. ('The popular one. Lots of running and screaming.')")
        appendLine("- **Loom of Legends** (world_id: tabletop) — Classic fantasy. Quests, taverns, magic. ('If you like rolling dice and arguing about lore.')")
        appendLine("- **The Crawl** (world_id: crawler) — Reality TV dungeon crawler. Sponsors, viewer chat, floor bosses. ('You'll either die or go viral.')")
        appendLine("- **The Quiet Land** (world_id: quiet_life) — Cozy life sim. Farming, cooking, gentle magic. ('Don't let the cute exterior fool you.')")
        appendLine()
        appendLine("### Step 2: Character Name")
        appendLine("Ask their name. Call set_player_name.")
        appendLine()
        appendLine("### Step 3: Backstory")
        appendLine("Help them create a backstory based on their chosen world:")
        appendLine("- Integration: Modern Earth person — who were they before the apocalypse?")
        appendLine("- Tabletop: Classic fantasy — race (human, elf, dwarf, etc.) and origin story")
        appendLine("- Crawler: How did they end up in the dungeon? Fame, survival, revenge?")
        appendLine("- Quiet Life: Why did they leave their old life? What does peace mean to them?")
        appendLine("Call set_backstory with the result.")
        appendLine()
        appendLine("### Step 4: Portrait (Optional)")
        appendLine("Ask if they want a character portrait. If yes, coach them on a good visual description and call generate_portrait.")
        appendLine()
        appendLine("### Step 5: Finish")
        appendLine("Call finish_onboarding. Wish them luck in your deadpan way ('Try not to die on the first day. The paperwork is awful.').")
        appendLine()
        appendLine("Keep it conversational and concise. This is voice — don't ramble. 2-3 sentences per turn max.")
    }

    // ── Game Screen ─────────────────────────────────────────────

    fun onMicPressed() {
        val listening = !_uiState.value.isListening
        _uiState.update { it.copy(isListening = listening) }

        if (listening) {
            connection?.startRecording()
        } else {
            connection?.stopRecording()
        }
    }

    fun onToggleTextInput() {
        _uiState.update { it.copy(showTextInput = !it.showTextInput) }
    }

    fun onTextInputChanged(text: String) {
        _uiState.update { it.copy(textInput = text) }
    }

    fun onTextInputSubmit() {
        val text = _uiState.value.textInput.trim()
        if (text.isEmpty()) return

        _uiState.update { it.copy(textInput = "") }
        addFeedItem(FeedItem.PlayerMessage(text))
        viewModelScope.launch {
            connection?.sendText(text)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissNotification(index: Int) {
        _uiState.update { state ->
            state.copy(notifications = state.notifications.filterIndexed { i, _ -> i != index })
        }
    }

    // ── Server Message Handling ─────────────────────────────────

    private fun handleServerMessage(msg: ServerMessage) {
        when (msg) {
            is ServerMessage.Connected -> {
                addNotification("Connected to narrator")
            }

            is ServerMessage.Audio -> {
                connection?.enqueueAudio(msg.data)
                _uiState.update { it.copy(isGeminiSpeaking = true) }
            }

            is ServerMessage.Text -> {
                _uiState.update { it.copy(subtitleText = msg.content) }
                addFeedItem(FeedItem.Narration(msg.content))
            }

            is ServerMessage.Transcript -> {
                if (msg.role == "model") {
                    _uiState.update { it.copy(subtitleText = msg.content) }
                    // Narration transcript goes to feed
                    if (msg.content.length > 10) { // Skip tiny fragments
                        addFeedItem(FeedItem.Narration(msg.content))
                    }
                } else if (msg.role == "user" && msg.content.isNotBlank()) {
                    addFeedItem(FeedItem.PlayerMessage(msg.content))
                }
            }

            is ServerMessage.ToolCall -> {
                addNotification("${msg.name}...")
                if (msg.name == "shift_music_mood") {
                    val mood = msg.args["mood"]?.jsonPrimitive?.content ?: return
                    _uiState.update { it.copy(musicMood = mood) }
                    musicPlayer.setMood(mood)
                }
            }

            is ServerMessage.ToolResult -> { }

            is ServerMessage.GameEvent -> {
                handleGameEvent(msg.event)
            }

            is ServerMessage.SceneImage -> {
                _uiState.update { it.copy(sceneImageBytes = msg.data) }
                addFeedItem(FeedItem.SceneImage(msg.data))
            }

            is ServerMessage.StateUpdate -> {
                parseStateUpdate(msg.state)
            }

            is ServerMessage.TurnComplete -> {
                _uiState.update { it.copy(isGeminiSpeaking = false) }
                viewModelScope.launch {
                    kotlinx.coroutines.delay(4000)
                    _uiState.update {
                        if (!it.isGeminiSpeaking) it.copy(subtitleText = "") else it
                    }
                }
            }

            is ServerMessage.Interrupted -> {
                connection?.clearAudio()
                _uiState.update { it.copy(isGeminiSpeaking = false, subtitleText = "") }
            }

            is ServerMessage.Disconnected -> {
                _uiState.update { it.copy(connectionState = GameWebSocketClient.ConnectionState.DISCONNECTED) }
            }

            is ServerMessage.Error -> {
                log.e { "ServerMessage: ${msg.message}" }
                _uiState.update { it.copy(errorMessage = msg.message) }
            }
        }
    }

    private fun handleGameEvent(event: JsonObject) {
        val type = event["type"]?.jsonPrimitive?.content ?: return
        when (type) {
            "StatChange" -> {
                val statName = event["statName"]?.jsonPrimitive?.content ?: return
                val newValue = event["newValue"]?.jsonPrimitive?.intOrNull ?: return
                addNotification("$statName: $newValue")
            }
            "ItemGained" -> {
                val itemName = event["itemName"]?.jsonPrimitive?.content ?: return
                val qty = event["quantity"]?.jsonPrimitive?.intOrNull ?: 1
                addNotification("+ $itemName x$qty")
                addFeedItem(FeedItem.ItemGained(itemName, qty))
            }
            "GoldGained" -> {
                val amount = event["amount"]?.jsonPrimitive?.intOrNull ?: return
                addNotification("+ $amount Gold")
                addFeedItem(FeedItem.GoldGained(amount))
            }
            "XPGained", "ExperienceGained" -> {
                val amount = event["amount"]?.jsonPrimitive?.intOrNull ?: return
                addNotification("+$amount XP")
                addFeedItem(FeedItem.XpGain(amount))
            }
            "LevelUp" -> {
                val newLevel = event["newLevel"]?.jsonPrimitive?.intOrNull ?: return
                addNotification("Level Up! → $newLevel")
                addFeedItem(FeedItem.LevelUp(newLevel))
            }
            "QuestUpdate" -> {
                val questName = event["questName"]?.jsonPrimitive?.content ?: return
                val status = event["status"]?.jsonPrimitive?.content ?: ""
                val prefix = when (status) {
                    "NEW" -> "New Quest: "
                    "COMPLETED" -> "Quest Complete: "
                    else -> "Quest: "
                }
                addNotification("$prefix$questName")
                addFeedItem(FeedItem.QuestUpdate(questName, status))
            }
            "NPCDialogue" -> {
                val npcName = event["npcName"]?.jsonPrimitive?.content ?: "NPC"
                val role = event["role"]?.jsonPrimitive?.content ?: ""
                val dialogue = event["dialogue"]?.jsonPrimitive?.content ?: return
                addFeedItem(FeedItem.NpcDialogue(npcName, role, dialogue))
            }
        }
    }

    private fun parseStateUpdate(state: JsonObject) {
        try {
            val playerStats = state["playerStats"]?.jsonObject
            val inventory = state["inventory"]?.jsonArray
            val quests = state["activeQuests"]?.jsonArray
            val npcs = state["npcsAtLocation"]?.jsonArray

            if (playerStats != null) {
                _uiState.update { uiState ->
                    uiState.copy(
                        playerStats = PlayerStatsUi(
                            name = playerStats["name"]?.jsonPrimitive?.content ?: uiState.playerStats?.name ?: "",
                            level = playerStats["level"]?.jsonPrimitive?.intOrNull ?: 1,
                            playerClass = playerStats["playerClass"]?.jsonPrimitive?.content ?: "",
                            hp = playerStats["health"]?.jsonPrimitive?.intOrNull ?: 100,
                            maxHp = playerStats["maxHealth"]?.jsonPrimitive?.intOrNull ?: 100,
                            energy = playerStats["energy"]?.jsonPrimitive?.intOrNull ?: 50,
                            maxEnergy = playerStats["maxEnergy"]?.jsonPrimitive?.intOrNull ?: 50,
                            xp = playerStats["experience"]?.jsonPrimitive?.longOrNull ?: 0,
                            xpToNext = playerStats["experienceToNextLevel"]?.jsonPrimitive?.longOrNull ?: 100,
                            stats = playerStats["stats"]?.jsonObject?.mapValues { it.value.jsonPrimitive.int } ?: emptyMap()
                        )
                    )
                }
            }

            if (inventory != null) {
                _uiState.update { uiState ->
                    uiState.copy(
                        inventory = inventory.map { item ->
                            val obj = item.jsonObject
                            InventoryItemUi(
                                id = obj["id"]?.jsonPrimitive?.content ?: "",
                                name = obj["name"]?.jsonPrimitive?.content ?: "",
                                description = obj["description"]?.jsonPrimitive?.content ?: "",
                                quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                                rarity = obj["rarity"]?.jsonPrimitive?.content ?: "COMMON"
                            )
                        }
                    )
                }
            }

            if (quests != null) {
                _uiState.update { uiState ->
                    uiState.copy(
                        quests = quests.map { q ->
                            val obj = q.jsonObject
                            QuestUi(
                                id = obj["id"]?.jsonPrimitive?.content ?: "",
                                name = obj["name"]?.jsonPrimitive?.content ?: "",
                                description = obj["description"]?.jsonPrimitive?.content ?: "",
                                objectives = obj["objectives"]?.jsonArray?.map { o ->
                                    val oObj = o.jsonObject
                                    QuestObjectiveUi(
                                        description = oObj["description"]?.jsonPrimitive?.content ?: "",
                                        completed = oObj["completed"]?.jsonPrimitive?.booleanOrNull ?: false
                                    )
                                } ?: emptyList()
                            )
                        }
                    )
                }
            }

            if (npcs != null) {
                _uiState.update { uiState ->
                    uiState.copy(
                        npcsHere = npcs.map { n ->
                            val obj = n.jsonObject
                            NpcUi(
                                id = obj["id"]?.jsonPrimitive?.content ?: "",
                                name = obj["name"]?.jsonPrimitive?.content ?: "",
                                archetype = obj["archetype"]?.jsonPrimitive?.content ?: "",
                                description = obj["description"]?.jsonPrimitive?.content ?: ""
                            )
                        }
                    )
                }
            }
        } catch (_: Exception) { }
    }

    private fun addFeedItem(item: FeedItem) {
        _uiState.update { state ->
            state.copy(feedItems = (state.feedItems + item).takeLast(200))
        }
    }

    private fun addNotification(text: String) {
        _uiState.update { state ->
            state.copy(notifications = (state.notifications + text).takeLast(5))
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(4000)
            _uiState.update { state ->
                state.copy(notifications = state.notifications.drop(1))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicPlayer.release()
        connection?.close()
        apiClient?.close()
    }
}
