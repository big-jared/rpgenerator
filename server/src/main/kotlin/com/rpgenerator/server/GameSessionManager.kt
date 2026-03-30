package com.rpgenerator.server

import com.google.genai.Client
import com.google.genai.types.*
import com.rpgenerator.core.api.*
import com.rpgenerator.core.persistence.DriverFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.google.genai.AsyncSession as GeminiAsyncSession
import com.google.genai.types.VoiceConfig as GeminiVoiceConfig

/**
 * Manages active game sessions with real game engine.
 * Each session has a full RPGClient, Game, and optional Gemini Live connection.
 *
 * Game DBs are stored at DATA_DIR/rpg_{id}.db (persistent across restarts).
 * Sessions are restored on-demand via resumeSession().
 */
object GameSessionManager {

    private val sessions = ConcurrentHashMap<String, GameSession>()
    private val geminiClient: Client by lazy { Client() }
    private val dataDir: String get() = SessionStore.getDataDir()
    internal val GEMINI_LIVE_MODEL = System.getenv("GEMINI_LIVE_MODEL") ?: "gemini-2.5-flash-native-audio-preview-12-2025"

    /**
     * Create a brand new game session.
     */
    suspend fun createSession(
        systemType: SystemType = SystemType.SYSTEM_INTEGRATION,
        seedId: String? = "integration",
        difficulty: Difficulty = Difficulty.NORMAL,
        characterCreation: CharacterCreationOptions = CharacterCreationOptions()
    ): GameSession {
        val id = UUID.randomUUID().toString().take(8)
        File(dataDir).mkdirs()
        val dbPath = "$dataDir/rpg_$id.db"
        val driver = DriverFactory(dbPath).createDriver()
        val client = RPGClient(driver)
        val baseLlm = LLMFactory.create()
        val trackingLlm = TrackingLLMInterface(baseLlm)

        val config = GameConfig(
            systemType = systemType,
            difficulty = difficulty,
            seedId = seedId,
            characterCreation = characterCreation
        )

        val game = client.startGame(config, trackingLlm)

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val session = GameSession(
            id = id,
            rpgClient = client,
            game = game,
            geminiClient = geminiClient,
            scope = scope,
            trackingLlm = trackingLlm
        )
        sessions[id] = session
        return session
    }

    /**
     * Resume a game session from persisted state.
     * Reloads the game from its SQLite DB.
     */
    suspend fun resumeSession(persisted: PersistedSession): GameSession? {
        val id = persisted.gameId
        // Already in memory?
        sessions[id]?.let { return it }

        val dbPath = "$dataDir/rpg_$id.db"
        if (!File(dbPath).exists()) {
            println("Cannot resume session $id: DB file not found at $dbPath")
            return null
        }

        return try {
            println("resumeSession($id): Opening DB at $dbPath")
            val driver = DriverFactory(dbPath).createDriver()
            val client = RPGClient(driver)
            val baseLlm = LLMFactory.create()
            val trackingLlm = TrackingLLMInterface(baseLlm)

            val systemType = try { SystemType.valueOf(persisted.systemType) } catch (_: Exception) { SystemType.SYSTEM_INTEGRATION }
            val difficulty = try { Difficulty.valueOf(persisted.difficulty) } catch (_: Exception) { Difficulty.NORMAL }

            // Each session DB has exactly one game — grab it
            val savedGames = client.getGames()
            println("resumeSession($id): getGames() returned ${savedGames.size} game(s)")
            val gameInfo = savedGames.firstOrNull()

            if (gameInfo == null) {
                println("Cannot resume session $id: no saved game found in DB")
                client.close()
                return null
            }

            println("resumeSession($id): Resuming game ${gameInfo.id} (player=${gameInfo.playerName}, level=${gameInfo.level})")
            val game = client.resumeGame(gameInfo, trackingLlm)
            println("resumeSession($id): Game resumed successfully")

            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val session = GameSession(
                id = id,
                rpgClient = client,
                game = game,
                geminiClient = geminiClient,
                scope = scope,
                trackingLlm = trackingLlm
            )
            // Seed the FeedStore with resume events so the client can
            // pre-populate the feed via GET /events before connecting WebSocket.
            val resumeEvents = game.getResumeEvents()
            if (resumeEvents.isNotEmpty()) {
                session.feedStore.seedFromEvents(resumeEvents)
                println("resumeSession($id): Seeded feed with ${resumeEvents.size} persisted events")
            }

            sessions[id] = session
            println("Resumed session $id from disk")
            session
        } catch (e: Exception) {
            println("Failed to resume session $id: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun getSession(id: String): GameSession? = sessions[id]

    fun removeSession(id: String) {
        sessions.remove(id)?.close()
    }
}

/**
 * A single game session with real game engine and optional Gemini Live API connection.
 */
class GameSession(
    val id: String,
    val rpgClient: RPGClient,
    val game: Game,
    val geminiClient: Client,
    val scope: CoroutineScope,
    val trackingLlm: TrackingLLMInterface? = null,
    val imageService: ImageGenerationService = ImageGenerationService(geminiClient),
    val feedStore: FeedStore = FeedStore()
) {
    var geminiSession: GeminiAsyncSession? = null
        private set

    var connected = false
        private set

    /** Lyria RealTime music streaming — created when WebSocket client connects. */
    var lyriaMusicService: LyriaMusicService? = null

    /** Cached item icon images: itemId → (imageBytes, mimeType) */
    val itemIcons = ConcurrentHashMap<String, Pair<ByteArray, String>>()

    /** Cached scene/portrait images: imageId → (imageBytes, mimeType) */
    val images = ConcurrentHashMap<String, Pair<ByteArray, String>>()

    /** Gate ALL realtime input while a Gemini tool call is pending (prevents 1008). */
    @Volatile var toolCallPending = false

    /** Last narrative text returned by send_player_input — used to distinguish narration readback from companion asides */
    @Volatile var lastNarrativeText: String? = null

    /** True once the companion has finished reading the engine narration and is now speaking as itself */
    @Volatile var narrativeConsumed = false


    /**
     * Connect to Gemini Live API for voice narration.
     */
    suspend fun connectToGemini(
        voiceName: String = "Kore",
        systemPrompt: String? = null
    ) {
        // Voice companion gets a small curated tool set — NOT the full 56-tool
        // game engine contract. The companion relays player intent through
        // send_player_input → orchestrator/GM, which handles all game mechanics.
        // Query tools let the companion answer quick questions without a full
        // orchestrator round-trip. Keeping this small prevents 1008 errors on
        // the native audio model.
        val toolDeclarations = listOf(
            FunctionDeclaration.builder()
                .name("send_player_input")
                .description("Send the player's action or dialogue to the game engine. The game master will handle combat, movement, NPC interaction, quests, and everything else. Returns narrative events describing what happened. Use this for ANY player action.")
                .parameters(Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(mapOf(
                        "input" to Schema.builder()
                            .type(Type.Known.STRING)
                            .description("What the player said or wants to do, in natural language")
                            .build()
                    ))
                    .required(listOf("input"))
                    .build()
                )
                .build(),
            FunctionDeclaration.builder()
                .name("get_player_stats")
                .description("Get the player's current stats: level, HP, XP, mana, energy, location, and class.")
                .build(),
            FunctionDeclaration.builder()
                .name("get_inventory")
                .description("Get the player's inventory items and capacity.")
                .build(),
            FunctionDeclaration.builder()
                .name("get_active_quests")
                .description("Get all active quests and their progress.")
                .build(),
            FunctionDeclaration.builder()
                .name("get_location")
                .description("Get current location details, features, and connections to other areas.")
                .build(),
            FunctionDeclaration.builder()
                .name("shift_music_mood")
                .description("Change the background music mood to match the scene.")
                .parameters(Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(mapOf(
                        "mood" to Schema.builder()
                            .type(Type.Known.STRING)
                            .description("Mood: peaceful, tense, battle, victory, mysterious, dark, epic")
                            .build()
                    ))
                    .required(listOf("mood"))
                    .build()
                )
                .build()
        )

        val prompt = systemPrompt ?: game.getSystemPrompt()

        val config = LiveConnectConfig.builder()
            .responseModalities(Modality.Known.AUDIO)
            .systemInstruction(
                Content.builder()
                    .parts(listOf(Part.builder().text(prompt).build()))
                    .build()
            )
            .tools(listOf(
                Tool.builder()
                    .functionDeclarations(toolDeclarations)
                    .build()
            ))
            .speechConfig(
                SpeechConfig.builder()
                    .voiceConfig(
                        GeminiVoiceConfig.builder()
                            .prebuiltVoiceConfig(
                                PrebuiltVoiceConfig.builder()
                                    .voiceName(voiceName)
                            )
                    )
            )
            .inputAudioTranscription(AudioTranscriptionConfig.builder().build())
            .outputAudioTranscription(AudioTranscriptionConfig.builder().build())
            .build()

        geminiSession = geminiClient.async.live.connect(GameSessionManager.GEMINI_LIVE_MODEL, config).get()
        connected = true
    }

    fun disconnect() {
        geminiSession?.close()
        geminiSession = null
        lyriaMusicService?.close()
        lyriaMusicService = null
        connected = false
    }

    fun close() {
        disconnect()
        rpgClient.close()
    }
}
