package com.rpgenerator.core.gemini

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Manages a Gemini Live API session for gameplay.
 * This is the main interface between the game engine and Gemini's voice/multimodal capabilities.
 *
 * Lifecycle: connect() → sendAudio/sendText → outputStream → disconnect()
 */
interface GeminiGameSession {

    /** Current session state */
    val state: SessionState

    /** Stream of all outputs from Gemini (text, audio, images, tool calls) */
    val outputStream: SharedFlow<GeminiOutput>

    /**
     * Connect to Gemini Live API with game context.
     * Sets up the system prompt, tool declarations, and voice config.
     */
    suspend fun connect(config: GeminiSessionConfig)

    /**
     * Send audio from the player's microphone.
     * PCM 16-bit, 16kHz, mono.
     */
    suspend fun sendAudio(pcmData: ByteArray)

    /**
     * Send text input (for testing, accessibility, or keyboard fallback).
     * Gemini processes this the same as voice — responds with voice + multimodal.
     */
    suspend fun sendText(text: String)

    /**
     * Send tool results back to Gemini after executing tool calls.
     */
    suspend fun sendToolResults(results: List<Pair<String, ToolResult>>)

    /**
     * Update the session context (e.g., after game state changes).
     * Allows refreshing the system prompt mid-session without reconnecting.
     */
    suspend fun updateContext(context: GameContext)

    /**
     * Disconnect from the Live API.
     */
    suspend fun disconnect()
}

enum class SessionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Configuration for starting a Gemini Live session.
 */
data class GeminiSessionConfig(
    val modelId: String = "gemini-2.5-flash-native-audio-preview-12-2025",
    val systemPrompt: String,
    val tools: List<GeminiToolDeclaration>,
    val voiceConfig: VoiceConfig = VoiceConfig(),
    val gameContext: GameContext
)

/**
 * Voice configuration for the narrator.
 */
data class VoiceConfig(
    val voiceName: String = "Kore",  // Default narrator voice
    val speakingRate: Float = 1.0f,
    val pitch: Float = 0.0f
)

/**
 * Current game context sent to Gemini for grounded responses.
 */
data class GameContext(
    val playerName: String,
    val playerLevel: Int,
    val playerClass: String?,
    val currentLocation: String,
    val locationDescription: String,
    val npcsPresent: List<String>,
    val activeQuests: List<String>,
    val recentEvents: List<String>,
    val worldSeedDescription: String? = null,
    val narratorStyle: String? = null
)

/**
 * Tool declaration sent to Gemini during session setup.
 * Maps to Gemini's FunctionDeclaration format.
 */
data class GeminiToolDeclaration(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameter>,
    val behavior: ToolBehavior = ToolBehavior.DEFAULT
)

data class ToolParameter(
    val type: String,  // "string", "integer", "number", "boolean"
    val description: String,
    val required: Boolean = true,
    val enum: List<String>? = null
)

/**
 * How tool execution interacts with Gemini's audio output.
 */
enum class ToolBehavior {
    /** Default — Gemini pauses audio while tool executes */
    DEFAULT,
    /** Non-blocking — tool runs in background, interrupts when done */
    NON_BLOCKING_INTERRUPT,
    /** Non-blocking — results delivered after current speech */
    NON_BLOCKING_WHEN_IDLE,
    /** Non-blocking — results absorbed silently */
    NON_BLOCKING_SILENT
}
