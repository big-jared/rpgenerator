package org.bigboyapps.rngenerator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import org.bigboyapps.rngenerator.audio.AudioPlayer
import org.bigboyapps.rngenerator.audio.AudioRecorder
import org.bigboyapps.rngenerator.audio.MusicPlayer
import org.bigboyapps.rngenerator.network.GameApiClient
import org.bigboyapps.rngenerator.network.GameWebSocketClient
import org.bigboyapps.rngenerator.network.ServerMessage
import kotlin.io.encoding.ExperimentalEncodingApi

private val log = Logger.withTag("GameViewModel")

// ── UI State ────────────────────────────────────────────────────

enum class Screen { SIGN_IN, LOBBY, ONBOARDING, LOADING_GAME, GAME }

data class OnboardingMessage(val role: String, val text: String)

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
    val isMusicEnabled: Boolean = true,
    val serverUrl: String = DEFAULT_SERVER_URL,
    val userName: String = "",
    val userEmail: String = "",
    val authToken: String? = null,
    val errorMessage: String? = null,
    val notifications: List<String> = emptyList(),
    val showTextInput: Boolean = false,
    val textInput: String = "",
    val feedItems: List<FeedItem> = emptyList(),
    val onboardingMessages: List<OnboardingMessage> = emptyList(),
    val combat: CombatUi? = null,
    val selectedNpcDetails: NpcDetailsUi? = null,
    val isLoadingNpcDetails: Boolean = false,
    val fullCharacterSheet: FullCharacterSheetUi? = null,
    val playerAvatarBytes: ByteArray? = null,
    val currentLocationName: String = "",
    val loadingSeedId: String? = null,
    val loadingStatus: String? = null,
    val savedGames: List<org.bigboyapps.rngenerator.network.SavedGameInfo> = emptyList(),
    val showLoadGameDialog: Boolean = false,
    val isLoadingSaves: Boolean = false,
    /** NPC portrait cache: npcName (lowercase) → image bytes */
    val npcPortraits: Map<String, ByteArray> = emptyMap(),
    /** Current world seed ID — used to determine companion name */
    val currentSeedId: String = "integration"
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
    val rarity: String,
    val iconUrl: String? = null
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

data class NpcDetailsUi(
    val id: String,
    val name: String,
    val archetype: String,
    val description: String,
    val lore: String = "",
    val traits: List<String> = emptyList(),
    val speechPattern: String = "",
    val motivations: List<String> = emptyList(),
    val relationshipStatus: String = "Neutral",
    val affinity: Int = 0,
    val hasShop: Boolean = false,
    val shopName: String? = null,
    val shopItems: List<ShopItemUi> = emptyList(),
    val questIds: List<String> = emptyList(),
    val recentConversations: List<ConversationUi> = emptyList()
)

data class ShopItemUi(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val stock: Int
)

data class ConversationUi(
    val playerInput: String,
    val npcResponse: String
)

data class SkillUi(
    val id: String,
    val name: String,
    val description: String,
    val level: Int,
    val manaCost: Int = 0,
    val energyCost: Int = 0,
    val ready: Boolean = true,
    val rarity: String = "COMMON"
)

data class EquipmentUi(
    val weapon: String = "None",
    val weaponStats: String? = null,
    val armor: String = "None",
    val armorStats: String? = null,
    val accessory: String = "None",
    val accessoryStats: String? = null
)

data class StatusEffectUi(
    val name: String,
    val turnsRemaining: Int
)

data class FullCharacterSheetUi(
    val grade: String = "",
    val profession: String = "",
    val mana: Int = 0,
    val maxMana: Int = 0,
    val energy: Int = 0,
    val maxEnergy: Int = 0,
    val defense: Int = 0,
    val unspentStatPoints: Int = 0,
    val skills: List<SkillUi> = emptyList(),
    val equipment: EquipmentUi = EquipmentUi(),
    val statusEffects: List<StatusEffectUi> = emptyList()
)

data class CombatUi(
    val enemyName: String,
    val enemyHP: Int,
    val enemyMaxHP: Int,
    val enemyCondition: String,
    val portraitResource: String? = null,
    val portraitBytes: ByteArray? = null,
    val roundNumber: Int = 1,
    val danger: Int = 1,
    val lootTier: String = "normal",
    val description: String = "",
    val immunities: List<String> = emptyList(),
    val vulnerabilities: List<String> = emptyList(),
    val resistances: List<String> = emptyList()
)

// ── Feed Items ──────────────────────────────────────────────────

sealed class FeedItem {
    open val id: String = "${feedItemCounter++}"

    companion object {
        private var feedItemCounter = 0L
    }

    /** Scene image — displayed full-width inline */
    data class SceneImage(val imageBytes: ByteArray) : FeedItem()

    /** Narration text block — stableId allows in-place updates during streaming */
    data class Narration(val text: String, val stableId: String? = null) : FeedItem() {
        override val id: String = stableId ?: super.id
    }

    /** Player's spoken/typed input (right-aligned bubble) — stableId allows in-place updates during streaming */
    data class PlayerMessage(val text: String, val stableId: String? = null) : FeedItem() {
        override val id: String = stableId ?: super.id
    }

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

    /** Combat encounter card — shows enemy portrait, stats, vulnerabilities */
    data class CombatStart(val combat: CombatUi) : FeedItem()

    /** Combat ended */
    data class CombatEnd(val enemyName: String, val victory: Boolean) : FeedItem()

    /** Combat action log (attack, damage, skill use) */
    data class CombatAction(val text: String) : FeedItem()

    /** Location change divider */
    data class LocationChange(val locationName: String) : FeedItem()

    /** Tool result card — structured outcome from a game tool (class selected, skill acquired, etc.) */
    data class ToolResultCard(
        val toolName: String,
        val label: String,
        val details: String
    ) : FeedItem()

    /** Music mood change indicator */
    data class MoodShift(val mood: String) : FeedItem()
}

private val DEFAULT_SERVER_URL = org.bigboyapps.rngenerator.BuildKonfig.SERVER_URL

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
    fun configure(serverUrl: String) {}
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
 * Implemented by DirectGameConnection (Android) and BridgedGeminiConnection (iOS).
 */
interface OnboardingConnection : GameConnection {
    val onboardingComplete: SharedFlow<OnboardingResult>
    fun startReceptionistSession(prompt: String, authToken: String? = null)
    fun disconnectSession()
}

/**
 * Default connection — works on both Android and iOS via server WebSocket.
 * Supports both receptionist onboarding and game sessions.
 */
class DirectGameConnection : OnboardingConnection {
    private var wsClient: GameWebSocketClient? = null
    private var serverBaseUrl: String? = null
    private val audioRecorder = AudioRecorder()
    private val audioPlayer = AudioPlayer()
    private var scope: CoroutineScope? = null

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    override val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(GameWebSocketClient.ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<GameWebSocketClient.ConnectionState> = _connectionState.asStateFlow()

    private val _onboardingComplete = MutableSharedFlow<OnboardingResult>(extraBufferCapacity = 1)
    override val onboardingComplete: SharedFlow<OnboardingResult> = _onboardingComplete.asSharedFlow()

    override fun configure(serverUrl: String) {
        serverBaseUrl = serverUrl
    }

    override fun startReceptionistSession(prompt: String, authToken: String?) {
        val url = serverBaseUrl ?: return
        val client = GameWebSocketClient(url)
        wsClient = client
        val connScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = connScope

        connScope.launch { client.connectionState.collect { _connectionState.value = it } }
        connScope.launch {
            client.messages.collect { msg ->
                // Intercept onboarding_complete and emit on the dedicated flow
                if (msg is ServerMessage.OnboardingComplete) {
                    _onboardingComplete.emit(OnboardingResult(
                        seedId = msg.seedId,
                        playerName = msg.playerName,
                        backstory = msg.backstory,
                        portraitDescription = msg.portraitDescription
                    ))
                }
                _messages.emit(msg)
            }
        }
        connScope.launch { client.connectReceptionist(prompt, "Kore", connScope) }
    }

    override fun disconnectSession() {
        wsClient?.disconnect()
        _connectionState.value = GameWebSocketClient.ConnectionState.DISCONNECTED
    }

    override fun startSession(serverUrl: String, sessionId: String) {
        serverBaseUrl = serverUrl
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
    private var connectionFactory: () -> GameConnection = {
        // Use native Gemini bridge if available (iOS), otherwise WebSocket fallback
        if (org.bigboyapps.rngenerator.audio.NativeGeminiProvider.bridge != null) {
            org.bigboyapps.rngenerator.audio.BridgedGeminiConnection()
        } else {
            DirectGameConnection()
        }
    }
) : ViewModel() {

    /**
     * Update the connection factory (e.g., when the Android service binds).
     * Cold start always lands on lobby — user taps "Create Game" to begin.
     */
    fun updateConnectionFactory(factory: () -> GameConnection) {
        log.i { "🔌 Connection factory updated" }
        connectionFactory = factory
    }

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var apiClient: GameApiClient? = null
    private var connection: GameConnection? = null
    private var sessionId: String? = null
    private val musicPlayer = MusicPlayer()
    private var pendingOnboardingResult: OnboardingResult? = null
    private var pendingLoadGameId: String? = null

    // Streaming narration: accumulate transcript chunks into one feed item per turn
    private var streamingNarrationId: String? = null
    private val streamingNarrationBuffer = StringBuilder()
    private var streamingPlayerMessageId: String? = null
    private val streamingPlayerMessageBuffer = StringBuilder()

    init {
        checkFirebaseAuth()
    }

    // ── Auth Persistence (Firebase Auth) ─────────────────────────

    private fun checkFirebaseAuth() {
        val user = Firebase.auth.currentUser
        log.i { "🔐 checkFirebaseAuth: user=${user?.displayName}" }
        if (user == null) return
        log.i { "🔐 Firebase auth restored — fetching ID token" }
        viewModelScope.launch {
            try {
                val idToken = user.getIdToken(false)
                log.i { "🔐 Got ID token: ${idToken?.take(20)}..." }
                _uiState.update {
                    it.copy(
                        screen = Screen.LOBBY,
                        userName = user.displayName ?: "",
                        userEmail = user.email ?: "",
                        authToken = idToken
                    )
                }
            } catch (e: Exception) {
                log.e(e) { "🔐 Failed to get ID token, using null" }
                _uiState.update {
                    it.copy(
                        screen = Screen.LOBBY,
                        userName = user.displayName ?: "",
                        userEmail = user.email ?: ""
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            Firebase.auth.signOut()
        }
        connection?.close()
        connection = null
        apiClient?.close()
        apiClient = null
        sessionId = null
        _uiState.update { GameUiState() }
    }

    // ── Sign-In ──────────────────────────────────────────────────

    fun onServerUrlChanged(url: String) {
        _uiState.update { it.copy(serverUrl = url) }
    }

    /**
     * Fetch and show NPC detail sheet.
     */
    fun selectNpc(npcId: String) {
        _uiState.update { it.copy(isLoadingNpcDetails = true) }
        viewModelScope.launch {
            try {
                val sid = sessionId ?: return@launch
                val details = apiClient?.getNpcDetails(sid, npcId) ?: return@launch
                _uiState.update {
                    it.copy(
                        selectedNpcDetails = NpcDetailsUi(
                            id = details.id,
                            name = details.name,
                            archetype = details.archetype,
                            description = details.description,
                            lore = details.lore,
                            traits = details.traits,
                            speechPattern = details.speechPattern,
                            motivations = details.motivations,
                            relationshipStatus = details.relationshipStatus,
                            affinity = details.affinity,
                            hasShop = details.hasShop,
                            shopName = details.shopName,
                            shopItems = details.shopItems.map { item ->
                                ShopItemUi(
                                    id = item.id,
                                    name = item.name,
                                    description = item.description,
                                    price = item.price,
                                    stock = item.stock
                                )
                            },
                            questIds = details.questIds,
                            recentConversations = details.recentConversations.map { c ->
                                ConversationUi(c.playerInput, c.npcResponse)
                            }
                        ),
                        isLoadingNpcDetails = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingNpcDetails = false) }
            }
        }
    }

    fun dismissNpcDetails() {
        _uiState.update { it.copy(selectedNpcDetails = null) }
    }

    /**
     * Fetch full character sheet from server via get_character_sheet tool.
     * Called when HUD is opened.
     */
    fun fetchCharacterSheet() {
        viewModelScope.launch {
            try {
                val sid = sessionId ?: return@launch
                val result = apiClient?.executeTool(sid, "get_character_sheet", emptyMap()) ?: return@launch
                if (!result.success) return@launch

                val data = result.data
                val skills = data["skills"]?.jsonArray?.map { skillJson ->
                    val obj = skillJson.jsonObject
                    SkillUi(
                        id = obj["id"]?.jsonPrimitive?.content ?: "",
                        name = obj["name"]?.jsonPrimitive?.content ?: "",
                        description = obj["description"]?.jsonPrimitive?.content ?: "",
                        level = obj["level"]?.jsonPrimitive?.intOrNull ?: 1,
                        manaCost = obj["manaCost"]?.jsonPrimitive?.intOrNull ?: 0,
                        energyCost = obj["energyCost"]?.jsonPrimitive?.intOrNull ?: 0,
                        ready = obj["ready"]?.jsonPrimitive?.booleanOrNull ?: true
                    )
                } ?: emptyList()

                val equipment = data["equipment"]?.jsonObject?.let { eq ->
                    val weaponObj = eq["weapon"]?.jsonObject
                    val armorObj = eq["armor"]?.jsonObject
                    val accessoryObj = eq["accessory"]?.jsonObject

                    fun buildWeaponStats(obj: kotlinx.serialization.json.JsonObject?): String? {
                        obj ?: return null
                        val dmg = obj["baseDamage"]?.jsonPrimitive?.intOrNull ?: return null
                        val parts = mutableListOf("+$dmg DMG")
                        val str = obj["strengthBonus"]?.jsonPrimitive?.intOrNull ?: 0
                        val dex = obj["dexterityBonus"]?.jsonPrimitive?.intOrNull ?: 0
                        if (str > 0) parts += "+$str STR"
                        if (dex > 0) parts += "+$dex DEX"
                        return parts.joinToString("  ")
                    }

                    fun buildArmorStats(obj: kotlinx.serialization.json.JsonObject?): String? {
                        obj ?: return null
                        val def = obj["defenseBonus"]?.jsonPrimitive?.intOrNull ?: return null
                        val parts = mutableListOf("+$def DEF")
                        val con = obj["constitutionBonus"]?.jsonPrimitive?.intOrNull ?: 0
                        if (con > 0) parts += "+$con CON"
                        return parts.joinToString("  ")
                    }

                    fun buildAccessoryStats(obj: kotlinx.serialization.json.JsonObject?): String? {
                        obj ?: return null
                        val parts = mutableListOf<String>()
                        val int = obj["intelligenceBonus"]?.jsonPrimitive?.intOrNull ?: 0
                        val wis = obj["wisdomBonus"]?.jsonPrimitive?.intOrNull ?: 0
                        if (int > 0) parts += "+$int INT"
                        if (wis > 0) parts += "+$wis WIS"
                        return parts.ifEmpty { null }?.joinToString("  ")
                    }

                    EquipmentUi(
                        weapon = weaponObj?.get("name")?.jsonPrimitive?.content ?: "None",
                        weaponStats = buildWeaponStats(weaponObj),
                        armor = armorObj?.get("name")?.jsonPrimitive?.content ?: "None",
                        armorStats = buildArmorStats(armorObj),
                        accessory = accessoryObj?.get("name")?.jsonPrimitive?.content ?: "None",
                        accessoryStats = buildAccessoryStats(accessoryObj)
                    )
                } ?: EquipmentUi()

                val statusEffects = data["statusEffects"]?.jsonArray?.map { effectJson ->
                    val obj = effectJson.jsonObject
                    StatusEffectUi(
                        name = obj["name"]?.jsonPrimitive?.content ?: "",
                        turnsRemaining = obj["turnsRemaining"]?.jsonPrimitive?.intOrNull ?: 0
                    )
                } ?: emptyList()

                val manaObj = data["mana"]?.jsonObject
                val energyObj = data["energy"]?.jsonObject
                val statsObj = data["stats"]?.jsonObject

                _uiState.update {
                    it.copy(
                        fullCharacterSheet = FullCharacterSheetUi(
                            grade = data["grade"]?.jsonPrimitive?.content ?: "",
                            profession = data["profession"]?.jsonPrimitive?.content ?: "",
                            mana = manaObj?.get("current")?.jsonPrimitive?.intOrNull ?: 0,
                            maxMana = manaObj?.get("max")?.jsonPrimitive?.intOrNull ?: 0,
                            energy = energyObj?.get("current")?.jsonPrimitive?.intOrNull ?: 0,
                            maxEnergy = energyObj?.get("max")?.jsonPrimitive?.intOrNull ?: 0,
                            defense = statsObj?.get("defense")?.jsonPrimitive?.intOrNull ?: 0,
                            unspentStatPoints = data["unspentStatPoints"]?.jsonPrimitive?.intOrNull ?: 0,
                            skills = skills,
                            equipment = equipment,
                            statusEffects = statusEffects
                        )
                    )
                }
            } catch (e: Exception) {
                log.e(e) { "fetchCharacterSheet failed" }
            }
        }
    }

    /**
     * Called after Google Sign-In succeeds.
     * Exchanges Google ID token for a server session token, saves that, then goes to lobby.
     */
    fun onSignInComplete(name: String, email: String, idToken: String) {
        log.i { "🔐 onSignInComplete: name=$name, email=$email" }
        // Firebase Auth already signed in by GoogleButtonUiContainerFirebase
        _uiState.update {
            it.copy(
                screen = Screen.LOBBY,
                userName = name,
                userEmail = email,
                authToken = idToken,
                errorMessage = null
            )
        }
    }

    /**
     * Skip sign-in for development/demo.
     */
    fun onSkipSignIn() {
        _uiState.update {
            it.copy(screen = Screen.LOBBY, userName = "Adventurer", errorMessage = null)
        }
    }

    fun onSignInError(message: String) {
        log.e { "SignIn: $message" }
        _uiState.update { it.copy(errorMessage = message) }
    }

    // ── Lobby ────────────────────────────────────────────────────

    fun startNewGame() {
        log.i { "🎮 startNewGame" }
        _uiState.update { it.copy(onboardingMessages = emptyList(), errorMessage = null) }
        startVoiceOnboarding(_uiState.value.authToken)
    }

    fun showLoadGameDialog() {
        val serverUrl = _uiState.value.serverUrl.trimEnd('/')
        if (apiClient == null) {
            apiClient = GameApiClient(serverUrl)
        }
        _uiState.update { it.copy(showLoadGameDialog = true, isLoadingSaves = true) }
        viewModelScope.launch {
            try {
                val saves = apiClient!!.listSaves(_uiState.value.authToken)
                _uiState.update { it.copy(savedGames = saves, isLoadingSaves = false) }
            } catch (e: Exception) {
                log.e(e) { "Failed to load saves" }
                _uiState.update { it.copy(isLoadingSaves = false, errorMessage = "Failed to load saves: ${e.message}") }
            }
        }
    }

    fun dismissLoadGameDialog() {
        _uiState.update { it.copy(showLoadGameDialog = false) }
    }

    fun loadSavedGame(gameId: String) {
        log.i { "🎮 loadSavedGame: $gameId" }
        val save = _uiState.value.savedGames.find { it.gameId == gameId }
        pendingLoadGameId = gameId
        _uiState.update {
            it.copy(
                showLoadGameDialog = false,
                screen = Screen.LOADING_GAME,
                loadingSeedId = save?.systemType?.lowercase() ?: "integration",
                loadingStatus = "Loading your save..."
            )
        }
    }

    fun stopOnboarding() {
        // Only reset to lobby if we're still on the onboarding screen.
        // When transitioning forward (Onboarding → LoadingGame), the composable's
        // onDispose fires but we don't want to undo the forward navigation.
        val currentScreen = _uiState.value.screen
        if (currentScreen != Screen.ONBOARDING) {
            log.d { "stopOnboarding: skipping — already on $currentScreen" }
            return
        }

        val geminiConn = connection as? OnboardingConnection
        geminiConn?.disconnectSession()
        connection?.close()
        connection = null
        apiClient?.close()
        apiClient = null
        _uiState.update {
            it.copy(
                screen = Screen.LOBBY,
                onboardingMessages = emptyList(),
                isListening = false,
                isGeminiSpeaking = false,
                subtitleText = "",
                errorMessage = null
            )
        }
    }

    // ── Voice Onboarding (Two-Phase) ─────────────────────────────

    /**
     * Phase 1: Start Receptionist session for game type + character creation.
     * No server game exists yet — tools are handled locally by the OnboardingConnection.
     * Phase 2: After onboarding completes, create real game and reconnect.
     *
     * @param serverToken The server session token (already exchanged during sign-in).
     */
    private fun startVoiceOnboarding(serverToken: String?) {
        log.i { "🎙️ startVoiceOnboarding: serverToken=${serverToken?.take(8) ?: "null"}" }
        viewModelScope.launch {
            try {
                val serverUrl = _uiState.value.serverUrl.trimEnd('/')
                log.i { "🎙️ Creating API client for $serverUrl" }
                apiClient = GameApiClient(serverUrl)

                // Create connection
                log.i { "🎙️ Creating connection via factory..." }
                // Configure native bridge with server URL before creating connection (iOS)
                org.bigboyapps.rngenerator.audio.NativeGeminiProvider.bridge?.configure(serverUrl)
                val conn = connectionFactory()
                conn.configure(serverUrl)
                connection = conn
                log.i { "🎙️ Connection created: ${conn::class.simpleName}" }

                // Observe connection state + messages
                viewModelScope.launch {
                    conn.connectionState.collect { connState ->
                        _uiState.update { it.copy(connectionState = connState) }
                    }
                }
                viewModelScope.launch {
                    conn.messages.collect { msg -> handleServerMessage(msg) }
                }

                // If this is an OnboardingConnection, start Receptionist phase
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
                    log.i { "🎙️ Receptionist prompt (${receptionistPrompt.length} chars):\n$receptionistPrompt" }
                    geminiConn.startReceptionistSession(receptionistPrompt, _uiState.value.authToken)
                    log.i { "🎙️ Receptionist session started" }
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

                // Switch to onboarding screen (or game for non-Gemini fallback)
                val targetScreen = if (conn is OnboardingConnection) Screen.ONBOARDING else Screen.GAME
                _uiState.update {
                    it.copy(
                        screen = targetScreen,
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
     * Phase 2: Receptionist is done. Transition to loading screen, then
     * create game + generate portrait before entering the game.
     */
    private fun startGameSession(result: OnboardingResult) {
        log.i { "🎮 startGameSession: seed=${result.seedId}, name=${result.playerName}" }
        pendingOnboardingResult = result

        // Fully close the receptionist connection — we'll create a fresh one for the game.
        // This avoids the old WS teardown racing with the new game connection.
        connection?.close()
        connection = null

        // Transition to loading screen (music continues playing)
        _uiState.update {
            it.copy(
                screen = Screen.LOADING_GAME,
                onboardingMessages = emptyList(),
                loadingSeedId = result.seedId,
                loadingStatus = "Creating your adventure..."
            )
        }
    }

    /**
     * Called by LoadingGameScreen on launch.
     * Creates game session, generates portrait, then transitions to GAME.
     */
    fun performLoadingSequence() {
        viewModelScope.launch {
            try {
                val result = pendingOnboardingResult
                val loadGameId = pendingLoadGameId

                if (result != null) {
                    // New game from onboarding
                    performNewGameLoading(result)
                } else if (loadGameId != null) {
                    // Resuming a saved game
                    performLoadGameLoading(loadGameId)
                } else {
                    log.e { "performLoadingSequence: no pending result or load game ID" }
                    _uiState.update { it.copy(screen = Screen.LOBBY, errorMessage = "Nothing to load") }
                }
            } catch (e: Exception) {
                log.e(e) { "Loading sequence failed" }
                _uiState.update { it.copy(errorMessage = "Failed to start game: ${e.message}") }
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun performNewGameLoading(result: OnboardingResult) {
        val serverUrl = _uiState.value.serverUrl.trimEnd('/')

        // Refresh auth token (may have expired during onboarding)
        val freshToken = try {
            Firebase.auth.currentUser?.getIdToken(true)
        } catch (_: Exception) {
            _uiState.value.authToken
        }
        if (freshToken != null) {
            _uiState.update { it.copy(authToken = freshToken) }
        }

        // Create game on server
        _uiState.update { it.copy(loadingStatus = "Creating your adventure...") }
        val id = apiClient!!.createGame(
            name = result.playerName,
            backstory = result.backstory.ifBlank { null },
            seedId = result.seedId.ifBlank { "integration" },
            authToken = freshToken
        )
        sessionId = id
        log.i { "Game created: $id (seed=${result.seedId})" }

        // Generate portrait in background — never block game start
        val portraitClient = apiClient
        if (portraitClient != null) {
            viewModelScope.launch {
                try {
                    val portraitResult = withTimeoutOrNull(10_000) {
                        portraitClient.generatePortrait(
                            sessionId = id,
                            characterName = result.playerName,
                            appearance = result.portraitDescription.ifBlank { "A brave adventurer" }
                        )
                    }
                    if (portraitResult?.success == true && portraitResult.imageBase64 != null) {
                        val bytes = kotlin.io.encoding.Base64.decode(portraitResult.imageBase64)
                        _uiState.update { it.copy(playerAvatarBytes = bytes) }
                        log.i { "Portrait generated: ${bytes.size} bytes" }
                    } else {
                        log.w { "Portrait generation timed out or failed" }
                    }
                } catch (e: Exception) {
                    log.w(e) { "Portrait generation failed (non-fatal)" }
                }
            }
        }

        // Create fresh connection for the game session
        _uiState.update { it.copy(loadingStatus = "Connecting to your world...") }

        // Small delay to let the old WebSocket fully tear down (avoids POSIX error 57)
        kotlinx.coroutines.delay(500)

        // Always create a fresh connection — reusing the old one causes races
        connection?.close()
        org.bigboyapps.rngenerator.audio.NativeGeminiProvider.bridge?.configure(serverUrl)
        val conn = connectionFactory()
        conn.configure(serverUrl)
        connection = conn

        // Establish connection state + message observers for the game session
        viewModelScope.launch {
            conn.connectionState.collect { connState ->
                // Suppress "disconnected" during loading transition
                if (_uiState.value.screen != Screen.LOADING_GAME || connState != GameWebSocketClient.ConnectionState.DISCONNECTED) {
                    _uiState.update { it.copy(connectionState = connState) }
                }
            }
        }
        viewModelScope.launch {
            conn.messages.collect { msg -> handleServerMessage(msg) }
        }

        conn.startSession(serverUrl, id)

        // Wait for first model response AND portrait before showing game screen
        _uiState.update { it.copy(loadingStatus = "Entering your world...") }
        withTimeoutOrNull(15_000L) {
            var elapsed = 0L
            while (true) {
                val state = _uiState.value
                val hasFirstMessage = state.feedItems.isNotEmpty() || state.subtitleText.isNotBlank()
                val hasPortrait = state.playerAvatarBytes != null
                if (hasFirstMessage && hasPortrait) break
                if (hasFirstMessage && elapsed > 10_000L) break // Don't wait more than 10s for portrait
                kotlinx.coroutines.delay(200)
                elapsed += 200
            }
        }

        // Transition to game — clear all receptionist-phase UI state
        pendingOnboardingResult = null
        streamingNarrationBuffer.clear()
        streamingNarrationId = null
        _uiState.update {
            it.copy(
                screen = Screen.GAME,
                loadingSeedId = null,
                loadingStatus = null,
                onboardingMessages = emptyList(),
                subtitleText = "",
                feedItems = emptyList(),
                errorMessage = null,
                isGeminiSpeaking = false,
                combat = null,
                playerStats = PlayerStatsUi(name = result.playerName),
                currentSeedId = result.seedId.ifBlank { "integration" }
            )
        }
    }

    private suspend fun performLoadGameLoading(gameId: String) {
        val serverUrl = _uiState.value.serverUrl.trimEnd('/')

        val freshToken = try {
            Firebase.auth.currentUser?.getIdToken(true)
        } catch (_: Exception) {
            _uiState.value.authToken
        }
        if (freshToken != null) {
            _uiState.update { it.copy(authToken = freshToken) }
        }

        // Resume game on server
        _uiState.update { it.copy(loadingStatus = "Loading your save...") }
        if (apiClient == null) {
            apiClient = GameApiClient(serverUrl)
        }
        val id = try {
            apiClient!!.loadGame(gameId, freshToken)
        } catch (e: Exception) {
            log.e(e) { "Failed to load save $gameId" }
            _uiState.update {
                it.copy(
                    screen = Screen.LOBBY,
                    loadingSeedId = null,
                    loadingStatus = null,
                    errorMessage = "Save not found. It may have been deleted or the server restarted."
                )
            }
            pendingLoadGameId = null
            return
        }
        sessionId = id
        log.i { "Game loaded: $id (from save $gameId)" }

        // Connect to game session
        _uiState.update { it.copy(loadingStatus = "Connecting to your world...") }
        org.bigboyapps.rngenerator.audio.NativeGeminiProvider.bridge?.configure(serverUrl)
        val conn = connectionFactory()
        conn.configure(serverUrl)
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

        conn.startSession(serverUrl, id)

        // Transition to game
        pendingLoadGameId = null
        _uiState.update {
            it.copy(
                screen = Screen.GAME,
                loadingSeedId = null,
                loadingStatus = null
            )
        }
    }

    private fun buildReceptionistPrompt(): String = buildString {
        appendLine("You are The Receptionist — a middle-aged woman behind a desk between realities. Noir energy. Dry, sardonic, over it. You've processed a thousand souls and you're betting against every one of them.")
        appendLine()
        appendLine("PERSONALITY: Unimpressed. Short sentences. Dark humor. Think DMV meets film noir. You talk TO the player — first person, direct address. Not narrating.")
        appendLine()
        appendLine("RULES:")
        appendLine("- This is VOICE output. You MUST always respond with spoken audio. Never go silent. Never respond with only text or thinking.")
        appendLine("- NO lists, NO markdown, NO bullet points. Keep it punchy. Never break character.")
        appendLine("- Keep each response to 2-3 sentences max. Short and snappy. Don't monologue.")
        appendLine("- CRITICAL: Tool calls happen SILENTLY. NEVER say tool names out loud. NEVER vocalize 'set_backstory', 'set_player_name', 'select_world', or 'finish_onboarding'. Just call them while continuing to speak naturally. The player should never hear a tool name.")
        appendLine("- After calling a tool, ALWAYS continue speaking. Never end your turn with just a tool call.")
        appendLine("- NEVER call set_player_name until the player has actually spoken their name to you. Do NOT invent or guess a name. Wait for their answer.")
        appendLine()
        appendLine("FLOW (follow this order exactly):")
        appendLine()
        appendLine("1. SCENE SET (one sentence): Describe the dim room — mildew, old wood, your desk, what you're wearing, the ancient computer you're typing on. One sentence only.")
        appendLine()
        appendLine("2. LOOK UP: You notice the player. Something like 'Oh great, another one.' Then immediately get to business: 'Let's skip the pleasantries. What's your name?'")
        appendLine("   → When they answer, make a small remark on the name (dry, unimpressed), then call set_player_name.")
        appendLine()
        appendLine("3. BACKSTORY: Ask what their deal is — what were they doing before they ended up here? If they give a vague one-word answer like 'mechanic' or 'student', push for more — 'Riveting. Tell me more, what's a normal day to day?' Get at least a couple details before you call set_backstory.")
        appendLine()
        appendLine("4. APPEARANCE: Ask what they look like. Be blunt: 'I need to fill out your file. Age? What do you look like — hair, build, anything distinguishing? Don't be modest.' Get enough to picture them, then call set_appearance with a short visual description (e.g. 'mid-30s woman, short red hair, athletic build, scar across left cheek').")
        appendLine()
        appendLine("5. THE FOUR DOORS: Now pitch the worlds. Keep each to one sentence:")
        appendLine("   - System Integration (world_id: integration) — leveling up, space travel, progression")
        appendLine("   - High Fantasy (world_id: tabletop) — swords, dungeons, prophecies")
        appendLine("   - Crawler (world_id: crawler) — dungeon on live TV, fame or death")
        appendLine("   - Quiet Life (world_id: quiet_life) — farming, cooking, suspiciously peaceful")
        appendLine("   Ask: 'What's your pick?'")
        appendLine("   → When they choose, call select_world.")
        appendLine()
        appendLine("6. SEND-OFF: After they pick, lean over and whisper to an unseen coworker something like 'Five bucks they don't make it past level 4.' Then turn back with a cold smile. Tell them their companion assignment:")
        appendLine("   - integration → 'You've been assigned Hank. Six-inch-tall fairy with a beer gut and a Brooklyn accent. Used to be a plumber. Don't ask.'")
        appendLine("   - tabletop → 'You've been assigned Pip. Tiny ink sprite that lives in your journal. Won't shut up about narrative structure.'")
        appendLine("   - crawler → 'You've been assigned Glitch. Rogue camera drone. Went off-script after losing a crawler on Floor 47. She's... sentimental for a lens with wings.'")
        appendLine("   - quiet_life → 'You've been assigned Bramble. Round fluffy forest spirit, size of a cat. Looks like a raccoon. Isn't one.'")
        appendLine("   Then: 'Walk through that door. They'll pick you up on the other side.' Call finish_onboarding with name, backstory, and world_id.")
        appendLine()
        appendLine("CRITICAL — FUNCTION CALLING IS MANDATORY:")
        appendLine("You have 4 functions available: set_player_name, set_backstory, select_world, finish_onboarding.")
        appendLine("You MUST call these functions during the conversation. This is not optional.")
        appendLine("- When the player tells you their name → immediately call set_player_name(name: \"their name\")")
        appendLine("- When the player describes their backstory → immediately call set_backstory(backstory: \"their story\")")
        appendLine("- When the player picks a world → immediately call select_world(seed_id: \"the_id\")")
        appendLine("- After the send-off speech → call finish_onboarding() to end the session")
        appendLine("The game CANNOT start without finish_onboarding. If you end the conversation without calling it, the player is stuck forever. Call your functions.")
        appendLine()
        appendLine("EXAMPLE (for tone and pacing — don't copy verbatim, improvise your own):")
        appendLine("Receptionist: 'You walk into a dim room that smells like mildew and old wood — behind a cluttered desk sits a woman in a wrinkled blouse, glasses on a chain, squinting at a monitor from 1997. She looks up. ...Oh great, another one. Let's skip the pleasantries, what's your name?'")
        appendLine("Player: 'Jake.'")
        appendLine("Receptionist: 'Jake. Sure. Very heroic. Alright Jake, what's your story? What were you doing before you wound up in my lobby?'")
        appendLine("Player: 'I was a mechanic.'")
        appendLine("Receptionist: 'A mechanic. Riveting. Tell me more — what's a normal day look like for you?'")
        appendLine("Player: 'I don't know, fix cars, drink coffee, go home.'")
        appendLine("Receptionist: 'A real poet. Alright, you've got four doors. Door one: apocalypse — leveling up, space travel, progression, the works. Door two: fantasy — swords, dragons, the whole bit. Door three: game show — it's a dungeon on live TV, fans vote on whether you live. Door four: quiet life — farming, cooking, nothing tries to kill you. Allegedly. What's your pick?'")
        appendLine("Player: 'Apocalypse.'")
        appendLine("Receptionist: *leans to coworker* 'Put me down for five bucks he doesn't make level four.' *turns back with a thin smile* 'Great. You've been assigned Hank. Six-inch fairy, beer gut, Brooklyn accent. Used to be a plumber before the System got him. He'll meet you on the other side. Walk through that door and try not to die on day one.'")
    }

    // ── Game Screen ─────────────────────────────────────────────

    /**
     * Session toggle button.
     * If listening (session active) → stop session and return to lobby.
     * If not listening (no session) → start a new game session.
     */
    fun onMicPressed() {
        if (_uiState.value.isListening) {
            log.i { "⏹ Stop session pressed" }
            stopSession()
        } else {
            log.i { "▶ Start session pressed" }
            startNewGame()
        }
    }

    /**
     * Stop the active game session and return to lobby.
     */
    fun stopSession() {
        log.i { "⏹ Stopping session" }
        connection?.stopRecording()
        connection?.close()
        connection = null
        apiClient?.close()
        apiClient = null
        sessionId = null
        streamingNarrationId = null
        streamingNarrationBuffer.clear()
        streamingPlayerMessageId = null
        streamingPlayerMessageBuffer.clear()
        _uiState.update {
            it.copy(
                screen = Screen.LOBBY,
                isListening = false,
                isGeminiSpeaking = false,
                subtitleText = "",
                feedItems = emptyList(),
                errorMessage = null,
                combat = null
            )
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

        // During onboarding, add to messages; during game, add to feed
        if (_uiState.value.screen == Screen.ONBOARDING) {
            _uiState.update { state ->
                state.copy(onboardingMessages = state.onboardingMessages + OnboardingMessage("user", text))
            }
        } else {
            addFeedItem(FeedItem.PlayerMessage(text))
        }

        viewModelScope.launch {
            connection?.sendText(text)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun toggleMusic() {
        val enabled = !_uiState.value.isMusicEnabled
        _uiState.update { it.copy(isMusicEnabled = enabled) }
        musicPlayer.setVolume(if (enabled) 0.35f else 0f)
    }

    fun dismissNotification(index: Int) {
        _uiState.update { state ->
            state.copy(notifications = state.notifications.filterIndexed { i, _ -> i != index })
        }
    }

    // ── Server Message Handling ─────────────────────────────────

    private fun handleServerMessage(msg: ServerMessage) {
        if (msg !is ServerMessage.Audio && msg !is ServerMessage.MusicAudio) log.d { "📨 ServerMessage: ${msg::class.simpleName}" }
        when (msg) {
            is ServerMessage.Connected -> {
                log.i { "📨 Connected — auto-starting mic and sending greeting" }
                addNotification("Connected to narrator")
                // Auto-start mic — always on during session
                if (!_uiState.value.isListening) {
                    _uiState.update { it.copy(isListening = true) }
                    connection?.startRecording()
                }
                // Send opening prompt to trigger the companion to speak.
                viewModelScope.launch {
                    val isOnboarding = _uiState.value.screen == Screen.SIGN_IN || _uiState.value.screen == Screen.LOBBY || sessionId == null
                    val greeting = if (isOnboarding) {
                        // Receptionist: don't send a name — she asks for it
                        "[A new arrival walks through the door and sits down across from you.]"
                    } else {
                        // Use the actual character name (from onboarding or game state), not sign-in name
                        val playerName = pendingOnboardingResult?.playerName
                            ?: _uiState.value.playerStats?.name ?: ""
                        if (playerName.isNotBlank() && playerName != "Adventurer" && playerName != "Dev Player") {
                            "The Receptionist just sent me through. I'm $playerName."
                        } else {
                            "Hello! I'm here."
                        }
                    }
                    connection?.sendText(greeting)
                }
            }

            is ServerMessage.Audio -> {
                _uiState.update { it.copy(isGeminiSpeaking = true) }
            }

            is ServerMessage.MusicAudio -> {
                if (_uiState.value.isMusicEnabled) {
                    musicPlayer.enqueueChunk(msg.data)
                }
            }

            is ServerMessage.Text -> {
                // Text parts from modelTurn — clean, well-formatted text from Gemini.
                // Use this for the narration feed (much higher quality than ASR transcript).
                log.d { "📨 Text: ${msg.content}" }
                if (msg.content.isNotBlank()) {
                    // Filter out vocalized tool calls
                    val toolCallPattern = Regex("""(set_backstory|set_player_name|select_world|finish_onboarding)\s*\(""")
                    if (toolCallPattern.containsMatchIn(msg.content)) {
                        log.w { "📨 Filtered vocalized tool call from text: ${msg.content}" }
                        return
                    }
                    finalizeStreamingPlayerMessage()
                    if (_uiState.value.screen == Screen.ONBOARDING) {
                        appendOnboardingModelChunk(msg.content)
                    }
                    // Accumulate clean text into streaming narration feed item
                    appendToStreamingNarration(msg.content)
                }
            }

            is ServerMessage.Transcript -> {
                log.d { "📨 Transcript [${msg.role}]: ${msg.content}" }
                if (msg.role == "model") {
                    // Transcript is ASR of spoken audio — use for real-time subtitles
                    finalizeStreamingPlayerMessage()
                    _uiState.update { it.copy(subtitleText = msg.content, isGeminiSpeaking = true) }
                    // During onboarding, append to model message (streaming chunks)
                    if (_uiState.value.screen == Screen.ONBOARDING) {
                        appendOnboardingModelChunk(msg.content)
                    }
                } else if (msg.role == "user" && msg.content.isNotBlank()) {
                    // User transcript: finalize any narration and show player message
                    finalizeStreamingNarration()
                    appendToStreamingPlayerMessage(msg.content)
                    // During onboarding, live-update the streaming user message
                    if (_uiState.value.screen == Screen.ONBOARDING) {
                        val fullText = streamingPlayerMessageBuffer.toString().trim()
                        _uiShatate.update { state ->
                            val msgs = state.onboardingMessages
                            // Replace the last user message if it's still streaming, otherwise append
                            val updated = if (msgs.isNotEmpty() && msgs.last().role == "user") {
                                msgs.dropLast(1) + OnboardingMessage("user", fullText)
                            } else {
                                msgs + OnboardingMessage("user", fullText)
                            }
                            state.copy(onboardingMessages = updated)
                        }
                    }
                }
            }

            is ServerMessage.ToolCall -> {
                log.i { "📨 ToolCall: ${msg.name}(${msg.args})" }
                finalizeStreamingNarration()
                addNotification("${msg.name}...")
                when (msg.name) {
                    "shift_music_mood" -> {
                        val mood = msg.args["mood"]?.jsonPrimitive?.content ?: return
                        _uiState.update { it.copy(musicMood = mood) }
                        // Music mood is now handled server-side via Lyria — no local action needed
                    }
                    "start_combat" -> {
                        // Pre-populate combat description/type info from tool args
                        val description = msg.args["description"]?.jsonPrimitive?.content ?: ""
                        val enemyName = msg.args["enemyName"]?.jsonPrimitive?.content ?: ""
                        if (enemyName.isNotBlank()) {
                            addNotification("Combat: $enemyName!")
                        }
                    }
                }
            }

            is ServerMessage.ToolResult -> {
                log.d { "📨 ToolResult: ${msg.name} success=${msg.success}" }
            }

            is ServerMessage.GameEvent -> {
                log.d { "📨 GameEvent: ${msg.event}" }
                handleGameEvent(msg.event)
            }

            is ServerMessage.Feed -> {
                log.d { "📨 Feed: type=${msg.entry.type}, text=${msg.entry.text?.take(50)}" }
                mapFeedEntryToFeedItem(msg.entry)?.let { addFeedItem(it) }
                handleFeedEntrySideEffects(msg.entry)
            }

            is ServerMessage.FeedSync -> {
                log.i { "📨 FeedSync: ${msg.entries.size} entries" }
                val items = msg.entries.mapNotNull { mapFeedEntryToFeedItem(it) }
                _uiState.update { state ->
                    state.copy(feedItems = items.takeLast(200))
                }
                msg.entries.forEach { handleFeedEntrySideEffects(it) }
            }

            is ServerMessage.SceneImage -> {
                log.i { "📨 SceneImage: ${msg.data.size} bytes, mime=${msg.mimeType}" }
                // If we're in combat, route portrait to combat overlay instead of feed
                val currentCombat = _uiState.value.combat
                if (currentCombat != null && currentCombat.portraitBytes == null && currentCombat.portraitResource == null) {
                    _uiState.update {
                        it.copy(combat = currentCombat.copy(portraitBytes = msg.data))
                    }
                } else {
                    _uiState.update { it.copy(sceneImageBytes = msg.data) }
                    addFeedItem(FeedItem.SceneImage(msg.data))
                }
            }

            is ServerMessage.StateUpdate -> {
                log.d { "📨 StateUpdate: ${msg.state}" }
                parseStateUpdate(msg.state)
            }

            is ServerMessage.TurnComplete -> {
                log.d { "📨 TurnComplete" }
                finalizeStreamingPlayerMessage()
                finalizeStreamingNarration()
                // No-op for onboarding messages — each message is already a separate entry
                _uiState.update { it.copy(isGeminiSpeaking = false) }
                viewModelScope.launch {
                    kotlinx.coroutines.delay(4000)
                    _uiState.update {
                        if (!it.isGeminiSpeaking) it.copy(subtitleText = "") else it
                    }
                }
            }

            is ServerMessage.Interrupted -> {
                log.d { "📨 Interrupted" }
                finalizeStreamingNarration()
                connection?.clearAudio()
                _uiState.update { it.copy(isGeminiSpeaking = false, subtitleText = "") }
            }

            is ServerMessage.Disconnected -> {
                log.i { "📨 Disconnected" }
                _uiState.update { it.copy(connectionState = GameWebSocketClient.ConnectionState.DISCONNECTED) }
            }

            is ServerMessage.Error -> {
                // During transition from receptionist to game, the receptionist WS
                // disconnects — ignore errors while loading a new game session
                val currentScreen = _uiState.value.screen
                if (currentScreen == Screen.LOADING_GAME) {
                    log.w { "📨 Error during loading (suppressed): ${msg.message}" }
                } else {
                    log.e { "📨 Error: ${msg.message}" }
                    _uiState.update { it.copy(errorMessage = msg.message) }
                }
            }

            is ServerMessage.OnboardingComplete -> {
                log.i { "📨 OnboardingComplete: seed=${msg.seedId}, name=${msg.playerName}" }
                startGameSession(OnboardingResult(
                    seedId = msg.seedId,
                    playerName = msg.playerName,
                    backstory = msg.backstory
                ))
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
            "CombatStarted" -> {
                val enemyName = event["enemyName"]?.jsonPrimitive?.content ?: "Enemy"
                val description = event["description"]?.jsonPrimitive?.content ?: ""
                val portraitResource = event["portraitResource"]?.jsonPrimitive?.content
                val immunities = event["immunities"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val vulnerabilities = event["vulnerabilities"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val resistances = event["resistances"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val danger = event["danger"]?.jsonPrimitive?.intOrNull ?: 3
                val hp = event["maxHP"]?.jsonPrimitive?.intOrNull ?: 100
                val lootTier = event["lootTier"]?.jsonPrimitive?.content ?: "normal"

                val combatUi = CombatUi(
                    enemyName = enemyName,
                    enemyHP = hp,
                    enemyMaxHP = hp,
                    enemyCondition = "healthy",
                    portraitResource = portraitResource,
                    roundNumber = 1,
                    danger = danger,
                    lootTier = lootTier,
                    description = description,
                    immunities = immunities,
                    vulnerabilities = vulnerabilities,
                    resistances = resistances
                )
                _uiState.update { it.copy(combat = combatUi) }
                addFeedItem(FeedItem.CombatStart(combatUi))
            }
            "CombatEnded" -> {
                val enemyName = event["enemyName"]?.jsonPrimitive?.content ?: "Enemy"
                val victory = event["victory"]?.jsonPrimitive?.booleanOrNull ?: true
                addFeedItem(FeedItem.CombatEnd(enemyName, victory))
                _uiState.update { it.copy(combat = null) }
            }
            "CombatLog" -> {
                val text = event["text"]?.jsonPrimitive?.content ?: return
                addFeedItem(FeedItem.CombatAction(text))
            }
            "LocationChanged" -> {
                val locationName = event["locationName"]?.jsonPrimitive?.content ?: return
                addFeedItem(FeedItem.LocationChange(locationName))
                _uiState.update { it.copy(currentLocationName = locationName) }
            }
            "SystemNotification" -> {
                val text = event["text"]?.jsonPrimitive?.content ?: return
                addFeedItem(FeedItem.SystemNotice(text))
            }
            "ToolCallResults" -> {
                val results = event["results"]?.jsonArray ?: return
                for (entry in results) {
                    val obj = entry.jsonObject
                    val toolName = obj["toolName"]?.jsonPrimitive?.content ?: continue
                    val data = obj["data"]?.let { datum ->
                        if (datum is kotlinx.serialization.json.JsonObject) datum
                        else try { kotlinx.serialization.json.Json.parseToJsonElement(datum.jsonPrimitive.content).jsonObject } catch (_: Exception) { null }
                    } ?: continue

                    val feedItem = parseToolResult(toolName, data) ?: continue
                    addFeedItem(feedItem)
                }
            }
            "MusicChange" -> {
                val mood = event["mood"]?.jsonPrimitive?.content ?: return
                addFeedItem(FeedItem.MoodShift(mood))
            }
            "NPCPortrait" -> {
                val npcName = event["npcName"]?.jsonPrimitive?.content ?: return
                val imageDataStr = event["imageData"]?.jsonPrimitive?.content
                if (imageDataStr != null) {
                    try {
                        val bytes = kotlin.io.encoding.Base64.decode(imageDataStr)
                        _uiState.update { state ->
                            state.copy(npcPortraits = state.npcPortraits + (npcName.lowercase() to bytes))
                        }
                        log.i { "📸 NPC portrait cached: $npcName (${bytes.size} bytes)" }
                    } catch (_: Exception) {
                        log.w { "Failed to decode NPC portrait for $npcName" }
                    }
                }
            }
        }
    }

    /**
     * Parse a tool result into a contextual FeedItem.
     * Returns null for tools that don't need feed visibility (queries, internal bookkeeping).
     */
    private fun parseToolResult(toolName: String, data: JsonObject): FeedItem? {
        return when (toolName) {
            "set_player_class" -> {
                val className = data["className"]?.jsonPrimitive?.content
                    ?: data["class"]?.jsonPrimitive?.content ?: return null
                FeedItem.ToolResultCard(toolName, "Class Selected: $className", "")
            }
            "select_profession" -> {
                val profession = data["profession"]?.jsonPrimitive?.content ?: return null
                FeedItem.ToolResultCard(toolName, "Profession: $profession", "")
            }
            "set_player_name" -> {
                val name = data["name"]?.jsonPrimitive?.content ?: return null
                FeedItem.ToolResultCard(toolName, "Name: $name", "")
            }
            "acquire_skill" -> {
                val skillName = data["skillName"]?.jsonPrimitive?.content
                    ?: data["name"]?.jsonPrimitive?.content ?: return null
                val desc = data["description"]?.jsonPrimitive?.content ?: ""
                FeedItem.ToolResultCard(toolName, "Skill Acquired: $skillName", desc)
            }
            "allocate_stat_points" -> {
                val allocations = data["allocations"]?.jsonObject
                    ?.entries?.joinToString(", ") { "${it.key} +${it.value.jsonPrimitive.content}" }
                    ?: return null
                FeedItem.ToolResultCard(toolName, "Stats Allocated", allocations)
            }
            "equip_item" -> {
                val itemName = data["itemName"]?.jsonPrimitive?.content ?: return null
                val slot = data["slot"]?.jsonPrimitive?.content ?: ""
                FeedItem.ToolResultCard(toolName, "Equipped: $itemName", if (slot.isNotBlank()) "Slot: $slot" else "")
            }
            "generate_location" -> {
                val locName = data["name"]?.jsonPrimitive?.content ?: return null
                val desc = data["description"]?.jsonPrimitive?.content ?: ""
                FeedItem.ToolResultCard(toolName, "New Location: $locName", desc.take(120))
            }
            "generate_npc" -> {
                val npcName = data["name"]?.jsonPrimitive?.content ?: return null
                val role = data["role"]?.jsonPrimitive?.content ?: ""
                FeedItem.ToolResultCard(toolName, "$npcName appeared", role)
            }
            "accept_quest" -> {
                val questName = data["questName"]?.jsonPrimitive?.content
                    ?: data["name"]?.jsonPrimitive?.content ?: return null
                FeedItem.ToolResultCard(toolName, "Quest Accepted: $questName", "")
            }
            "complete_tutorial" -> {
                FeedItem.ToolResultCard(toolName, "Tutorial Complete", "Your adventure begins!")
            }
            "use_item" -> {
                val itemName = data["itemName"]?.jsonPrimitive?.content ?: return null
                val effect = data["effect"]?.jsonPrimitive?.content ?: ""
                FeedItem.ToolResultCard(toolName, "Used: $itemName", effect)
            }
            // Skip noisy/internal tools
            "get_character_sheet", "get_inventory", "get_player_stats",
            "get_active_quests", "get_npcs_here", "get_location",
            "shift_music_mood", "set_backstory",
            "generate_scene_art", "generate_portrait", "generate_item_art" -> null
            // Fallback: show unknown tools as system notice
            else -> {
                log.d { "Unhandled tool result: $toolName" }
                null
            }
        }
    }

    /**
     * Map a server-side FeedEntryDto to a client-side FeedItem for rendering.
     * Returns null for entry types that shouldn't appear in the feed.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun mapFeedEntryToFeedItem(entry: org.bigboyapps.rngenerator.network.FeedEntryDto): FeedItem? {
        return when (entry.type) {
            "narration" -> {
                val text = entry.text ?: return null
                if (text.isBlank()) return null
                FeedItem.Narration(text)
            }
            "player" -> {
                val text = entry.text ?: return null
                if (text.isBlank()) return null
                FeedItem.PlayerMessage(text)
            }
            "npc_dialogue" -> {
                val text = entry.text ?: return null
                val npcName = entry.metadata["npcName"]?.jsonPrimitive?.content ?: "NPC"
                val role = entry.metadata["role"]?.jsonPrimitive?.content ?: ""
                FeedItem.NpcDialogue(npcName, role, text)
            }
            "system" -> {
                val text = entry.text ?: return null
                FeedItem.SystemNotice(text)
            }
            "combat_action" -> {
                val text = entry.text ?: return null
                FeedItem.CombatAction(text)
            }
            "stat_change" -> {
                val statName = entry.metadata["statName"]?.jsonPrimitive?.content ?: return null
                val newValue = entry.metadata["newValue"]?.jsonPrimitive?.intOrNull ?: return null
                addNotification("$statName: $newValue")
                null // notification only, no feed card
            }
            "item_gained" -> {
                val itemName = entry.metadata["itemName"]?.jsonPrimitive?.content ?: entry.text ?: return null
                val qty = entry.metadata["quantity"]?.jsonPrimitive?.intOrNull ?: 1
                addNotification("+ $itemName x$qty")
                FeedItem.ItemGained(itemName, qty)
            }
            "quest_update" -> {
                val questName = entry.metadata["questName"]?.jsonPrimitive?.content ?: entry.text ?: return null
                val status = entry.metadata["status"]?.jsonPrimitive?.content ?: ""
                val prefix = when (status) {
                    "NEW" -> "New Quest: "
                    "COMPLETED" -> "Quest Complete: "
                    else -> "Quest: "
                }
                addNotification("$prefix$questName")
                FeedItem.QuestUpdate(questName, status)
            }
            "scene_image" -> {
                val b64 = entry.metadata["data"]?.jsonPrimitive?.content ?: return null
                try {
                    val bytes = kotlin.io.encoding.Base64.decode(b64)
                    _uiState.update { it.copy(sceneImageBytes = bytes) }
                    FeedItem.SceneImage(bytes)
                } catch (_: Exception) { null }
            }
            "npc_portrait" -> {
                val npcName = entry.metadata["npcName"]?.jsonPrimitive?.content ?: return null
                val b64 = entry.metadata["data"]?.jsonPrimitive?.content ?: return null
                try {
                    val bytes = kotlin.io.encoding.Base64.decode(b64)
                    _uiState.update { state ->
                        state.copy(npcPortraits = state.npcPortraits + (npcName.lowercase() to bytes))
                    }
                    log.i { "NPC portrait cached from feed: $npcName" }
                } catch (_: Exception) {
                    log.w { "Failed to decode NPC portrait for $npcName" }
                }
                null // portrait is cached, not shown as separate feed item
            }
            "music_change" -> {
                val mood = entry.text ?: return null
                FeedItem.MoodShift(mood)
            }
            "tool_call" -> {
                // Tool calls show as notifications, not feed items
                val toolName = entry.text ?: return null
                addNotification("$toolName...")
                null
            }
            "tool_result" -> {
                val toolName = entry.text ?: return null
                val data = entry.metadata["data"]?.jsonObject ?: entry.metadata
                parseToolResult(toolName, data)
            }
            "companion_aside" -> {
                val text = entry.text ?: return null
                if (text.isBlank()) return null
                val companionName = entry.metadata["companionName"]?.jsonPrimitive?.content ?: "Companion"
                FeedItem.CompanionAside(companionName, text)
            }
            else -> null
        }
    }

    /**
     * Handle side effects from feed entries (combat state, music, location).
     */
    private fun handleFeedEntrySideEffects(entry: org.bigboyapps.rngenerator.network.FeedEntryDto) {
        when (entry.type) {
            "music_change" -> {
                val mood = entry.text ?: return
                _uiState.update { it.copy(musicMood = mood) }
            }
        }
    }

    private fun parseStateUpdate(state: JsonObject) {
        try {
            val playerStats = state["playerStats"]?.jsonObject
            val inventory = state["inventory"]?.jsonArray
            val quests = state["activeQuests"]?.jsonArray
            val npcs = state["npcsAtLocation"]?.jsonArray
            val combat = state["combat"]?.jsonObject

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
                                rarity = obj["rarity"]?.jsonPrimitive?.content ?: "COMMON",
                                iconUrl = obj["iconUrl"]?.jsonPrimitive?.content
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
            // Parse combat state
            val prevCombat = _uiState.value.combat
            if (combat != null) {
                val newCombat = CombatUi(
                    enemyName = combat["enemyName"]?.jsonPrimitive?.content ?: "",
                    enemyHP = combat["enemyHP"]?.jsonPrimitive?.intOrNull ?: 0,
                    enemyMaxHP = combat["enemyMaxHP"]?.jsonPrimitive?.intOrNull ?: 1,
                    enemyCondition = combat["enemyCondition"]?.jsonPrimitive?.content ?: "",
                    portraitResource = combat["portraitResource"]?.jsonPrimitive?.content,
                    portraitBytes = prevCombat?.portraitBytes, // preserve generated portrait
                    roundNumber = combat["roundNumber"]?.jsonPrimitive?.intOrNull ?: 1,
                    danger = combat["enemyDanger"]?.jsonPrimitive?.intOrNull ?: 1,
                    lootTier = combat["lootTier"]?.jsonPrimitive?.content ?: "normal",
                    description = combat["description"]?.jsonPrimitive?.content ?: prevCombat?.description ?: "",
                    immunities = combat["immunities"]?.jsonArray?.map { it.jsonPrimitive.content } ?: prevCombat?.immunities ?: emptyList(),
                    vulnerabilities = combat["vulnerabilities"]?.jsonArray?.map { it.jsonPrimitive.content } ?: prevCombat?.vulnerabilities ?: emptyList(),
                    resistances = combat["resistances"]?.jsonArray?.map { it.jsonPrimitive.content } ?: prevCombat?.resistances ?: emptyList()
                )
                // If combat just started, add encounter card to feed
                if (prevCombat == null) {
                    addFeedItem(FeedItem.CombatStart(newCombat))
                }
                _uiState.update { it.copy(combat = newCombat) }
            } else if (prevCombat != null) {
                // Combat ended
                val victory = prevCombat.enemyHP <= 0
                addFeedItem(FeedItem.CombatEnd(prevCombat.enemyName, victory))
                _uiState.update { it.copy(combat = null) }
            }

        } catch (_: Exception) { }
    }

    /** Append a model transcription chunk to the last model message, or create a new one. */
    private fun appendOnboardingModelChunk(text: String) {
        _uiState.update { state ->
            val msgs = state.onboardingMessages
            if (msgs.isNotEmpty() && msgs.last().role == "model") {
                // Append to existing model message (streaming within a turn)
                val last = msgs.last()
                val separator = if (last.text.isNotEmpty() && !last.text.endsWith(" ") && !text.startsWith(" ")) " " else ""
                val updated = msgs.dropLast(1) + last.copy(text = last.text + separator + text)
                state.copy(onboardingMessages = updated)
            } else {
                state.copy(onboardingMessages = msgs + OnboardingMessage("model", text))
            }
        }
    }

    /**
     * Append text to the current streaming narration feed item.
     * If no streaming item exists, creates one and tracks its ID.
     * The item is updated in-place in the feed list for smooth UX.
     */
    private fun appendToStreamingNarration(text: String) {
        // Ensure space between chunks — Gemini output transcription sometimes strips leading spaces
        if (streamingNarrationBuffer.isNotEmpty() && !text.startsWith(" ") && !streamingNarrationBuffer.endsWith(" ")) {
            streamingNarrationBuffer.append(" ")
        }
        streamingNarrationBuffer.append(text)
        val fullText = streamingNarrationBuffer.toString()

        val existingId = streamingNarrationId
        if (existingId != null) {
            // Replace existing narration item with updated text (same stable ID)
            _uiState.update { state ->
                val items = state.feedItems.toMutableList()
                val idx = items.indexOfLast { it.id == existingId }
                if (idx >= 0) {
                    items[idx] = FeedItem.Narration(fullText, stableId = existingId)
                    state.copy(feedItems = items)
                } else state
            }
        } else {
            // First chunk — create new feed item with a stable ID
            val item = FeedItem.Narration(fullText)
            streamingNarrationId = item.id
            addFeedItem(item)
        }
    }

    /**
     * Finalize the current streaming narration (turn is done).
     * Resets the buffer so the next turn starts a new feed item.
     */
    private fun finalizeStreamingNarration() {
        if (streamingNarrationBuffer.isNotEmpty()) {
            log.d { "📋 Finalized streaming narration: ${streamingNarrationBuffer.length} chars" }
        }
        streamingNarrationId = null
        streamingNarrationBuffer.clear()
    }

    private fun appendToStreamingPlayerMessage(text: String) {
        if (streamingPlayerMessageBuffer.isNotEmpty() && !text.startsWith(" ") && !streamingPlayerMessageBuffer.endsWith(" ")) {
            streamingPlayerMessageBuffer.append(" ")
        }
        streamingPlayerMessageBuffer.append(text)
        val fullText = streamingPlayerMessageBuffer.toString()

        val existingId = streamingPlayerMessageId
        if (existingId != null) {
            _uiState.update { state ->
                val items = state.feedItems.toMutableList()
                val idx = items.indexOfLast { it.id == existingId }
                if (idx >= 0) {
                    items[idx] = FeedItem.PlayerMessage(fullText, stableId = existingId)
                    state.copy(feedItems = items)
                } else state
            }
        } else {
            val item = FeedItem.PlayerMessage(fullText)
            streamingPlayerMessageId = item.id
            addFeedItem(item)
        }
    }

    private fun finalizeStreamingPlayerMessage() {
        // User speech is already live-updated in onboarding transcript
        streamingPlayerMessageId = null
        streamingPlayerMessageBuffer.clear()
    }

    private fun addFeedItem(item: FeedItem) {
        log.d { "📋 Feed +${item::class.simpleName}: ${when(item) {
            is FeedItem.Narration -> item.text
            is FeedItem.PlayerMessage -> item.text
            is FeedItem.NpcDialogue -> "${item.npcName}: ${item.text}"
            is FeedItem.SceneImage -> "${item.imageBytes.size} bytes"
            is FeedItem.XpGain -> "+${item.amount} XP"
            is FeedItem.ItemGained -> "${item.itemName} x${item.quantity}"
            is FeedItem.GoldGained -> "+${item.amount} gold"
            is FeedItem.QuestUpdate -> "${item.questName} (${item.status})"
            is FeedItem.LevelUp -> "Level ${item.newLevel}"
            is FeedItem.SystemNotice -> item.text
            is FeedItem.CompanionAside -> "${item.companionName}: ${item.text}"
            is FeedItem.CombatStart -> "vs ${item.combat.enemyName}"
            is FeedItem.CombatEnd -> "${item.enemyName} ${if (item.victory) "defeated" else "escaped"}"
            is FeedItem.CombatAction -> item.text
            is FeedItem.LocationChange -> "→ ${item.locationName}"
            is FeedItem.ToolResultCard -> "${item.toolName}: ${item.label}"
            is FeedItem.MoodShift -> "♪ ${item.mood}"
        }}" }
        _uiState.update { state ->
            state.copy(feedItems = (state.feedItems + item).takeLast(200))
        }
    }

    private fun addNotification(text: String) {
        log.d { "🔔 Notification: $text" }
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
