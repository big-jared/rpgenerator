package com.rpgenerator.server

import com.google.genai.Client
import com.google.genai.types.*
import com.rpgenerator.core.api.*
import com.rpgenerator.core.gemini.*
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
        val baseLlm = GeminiLLM()
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
            val driver = DriverFactory(dbPath).createDriver()
            val client = RPGClient(driver)
            val baseLlm = GeminiLLM()
            val trackingLlm = TrackingLLMInterface(baseLlm)

            val systemType = try { SystemType.valueOf(persisted.systemType) } catch (_: Exception) { SystemType.SYSTEM_INTEGRATION }
            val difficulty = try { Difficulty.valueOf(persisted.difficulty) } catch (_: Exception) { Difficulty.NORMAL }

            // Each session DB has exactly one game — grab it
            val savedGames = client.getGames()
            val gameInfo = savedGames.firstOrNull()

            if (gameInfo == null) {
                println("Cannot resume session $id: no saved game found in DB")
                return null
            }

            val game = client.resumeGame(gameInfo, trackingLlm)

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
            println("Resumed session $id from disk")
            session
        } catch (e: Exception) {
            println("Failed to resume session $id: ${e.message}")
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
    val imageService: ImageGenerationService = ImageGenerationService(geminiClient)
) {
    // GeminiTools for voice path tool dispatching
    val tools: GeminiTools = GeminiTools.createDefault(gameId = id, systemType = SystemType.SYSTEM_INTEGRATION)

    var geminiSession: GeminiAsyncSession? = null
        private set

    var connected = false
        private set

    /**
     * Connect to Gemini Live API for voice narration.
     */
    suspend fun connectToGemini(
        voiceName: String = "Kore",
        systemPrompt: String = defaultSystemPrompt()
    ) {
        // Build tool declarations from GeminiTools for voice path
        val tools = GeminiTools.createDefault(gameId = id, systemType = SystemType.SYSTEM_INTEGRATION)
        val toolDeclarations = tools.getToolDeclarations().map { decl ->
            FunctionDeclaration.builder()
                .name(decl.name)
                .description(decl.description)
                .parameters(Schema.builder()
                    .type("OBJECT")
                    .properties(decl.parameters.mapValues { (_, param) ->
                        Schema.builder()
                            .type(param.type.uppercase())
                            .description(param.description)
                            .build()
                    })
                    .required(decl.parameters.filter { it.value.required }.keys.toList())
                    .build()
                )
                .build()
        }

        val config = LiveConnectConfig.builder()
            .responseModalities(Modality.Known.AUDIO)
            .systemInstruction(
                Content.builder()
                    .parts(listOf(Part.builder().text(systemPrompt).build()))
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

        val modelId = "gemini-2.5-flash-preview-native-audio-dialog"
        geminiSession = geminiClient.async.live.connect(modelId, config).get()
        connected = true
    }

    fun disconnect() {
        geminiSession?.close()
        geminiSession = null
        connected = false
    }

    fun close() {
        disconnect()
        rpgClient.close()
    }

    private fun defaultSystemPrompt(): String = """
        You are the narrator of a LitRPG adventure game. Speak in a dramatic, immersive
        style like a professional audiobook narrator. You bring the world to life with
        vivid descriptions and emotional delivery.

        You have access to game tools to query and modify the game state. ALWAYS use
        tools when the player takes actions — check stats, resolve combat, update quests.
        Narrate the results of tool calls dramatically.

        Keep responses concise for voice. Short, punchy sentences. Build tension.
        React to what the player says naturally — this is a conversation, not a script.
    """.trimIndent()
}
