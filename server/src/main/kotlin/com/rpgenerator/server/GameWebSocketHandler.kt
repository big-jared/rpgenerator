package com.rpgenerator.server

import com.rpgenerator.core.api.GameEvent
import com.rpgenerator.core.api.GameStateSnapshot
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.Base64
import com.google.genai.types.*
import com.google.genai.types.LiveServerMessage

/**
 * Handles WebSocket communication between the mobile app and Gemini Live API.
 *
 * All game events, narration, player messages, tool results, and images are
 * recorded in the session's FeedStore and pushed to the client as FeedEntry
 * objects. The client is a dumb renderer — the server is the single source
 * of truth for what appears in the feed.
 *
 * Audio gating: only gate on toolCallPending (Gemini rejects input while
 * waiting for tool responses). The Live API handles interruptions natively —
 * no modelSpeaking or audioMuted gates needed.
 */
object GameWebSocketHandler {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun handle(ws: DefaultWebSocketServerSession, session: GameSession) {
        session.toolCallPending = false
        try {
            for (frame in ws.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val msg = json.parseToJsonElement(frame.readText()).jsonObject
                        val type = msg["type"]?.jsonPrimitive?.content ?: continue

                        when (type) {
                            "connect" -> handleConnect(ws, session, msg)
                            "audio" -> if (!session.toolCallPending) handleAudio(session, msg)
                            "text" -> if (!session.toolCallPending) handleText(session, msg)
                            "feed_request" -> {
                                // Client reconnection: replay missed feed entries
                                val afterId = msg["afterId"]?.jsonPrimitive?.longOrNull ?: 0L
                                val entries = session.feedStore.since(afterId)
                                ws.sendFeedSync(entries)
                            }
                            "disconnect" -> {
                                session.disconnect()
                                ws.send("""{"type": "disconnected"}""")
                            }
                        }
                    }
                    is Frame.Binary -> {
                        if (session.toolCallPending) continue
                        val gemini = session.geminiSession ?: continue
                        val params = LiveSendRealtimeInputParameters.builder()
                            .media(Blob.builder().mimeType("audio/pcm").data(frame.data))
                            .build()
                        try {
                            gemini.sendRealtimeInput(params)
                        } catch (e: Exception) {
                            // Gemini rejects realtime input during the latency window
                            // between server-side tool call and our callback receiving it.
                            // Gate further input and swallow the error.
                            session.toolCallPending = true
                            println("GameWSHandler: sendRealtimeInput rejected (binary), gating input: ${e.message}")
                        }
                    }
                    else -> {}
                }
            }
        } finally {
            session.disconnect()
            GameSessionManager.removeSession(session.id)
        }
    }

    private suspend fun handleConnect(
        ws: DefaultWebSocketServerSession,
        session: GameSession,
        msg: JsonObject
    ) {
        try {
            // Use the companion's configured voice for this world seed,
            // falling back to the client-requested voice or default "Kore"
            val companionVoice = session.game.getCompanionVoice()
            val voiceName = if (companionVoice.isNotBlank()) companionVoice
                else msg["voiceName"]?.jsonPrimitive?.content ?: "Kore"
            var prompt = session.game.getSystemPrompt()

            // Opening narration is passed via the connect message from the client
            // (generated during loading screen via REST API)
            val openingNarration = msg["openingNarration"]?.jsonPrimitive?.content
            if (!openingNarration.isNullOrBlank()) {
                prompt += "\n\n## OPENING NARRATION (read this aloud NOW)\n$openingNarration"
                println("GameWSHandler: Appended opening narration (${openingNarration.length} chars) from client")
            }

            println("GameWSHandler: Connecting with voice=$voiceName, prompt (${prompt.length} chars)")
            session.connectToGemini(voiceName = voiceName, systemPrompt = prompt)

            // Serialize all Gemini callbacks through a channel to preserve message ordering.
            // NOTE: gemini.receive() is ASYNC — it registers a callback and returns immediately.
            // Do NOT put it in a blocking loop.
            val messageChannel = Channel<LiveServerMessage>(Channel.UNLIMITED)

            session.scope.launch {
                for (msg in messageChannel) {
                    handleGeminiMessage(ws, session, msg)
                }
            }

            val gemini = session.geminiSession!!
            println("GameWSHandler: Registering Gemini receive callback")
            gemini.receive { message ->
                if (message.toolCall().isPresent) {
                    session.toolCallPending = true
                }
                messageChannel.trySend(message)
            }
            println("GameWSHandler: Gemini receive callback registered")

            // Start Lyria music streaming
            val apiKey = System.getenv("GOOGLE_API_KEY") ?: ""
            if (apiKey.isNotEmpty()) {
                try {
                    val musicChannel = Channel<ByteArray>(Channel.UNLIMITED)
                    session.scope.launch {
                        for (chunk in musicChannel) {
                            try {
                                ws.send(Frame.Binary(true, byteArrayOf(0x02) + chunk))
                            } catch (_: Exception) { break }
                        }
                    }
                    val music = LyriaMusicService(apiKey) { chunk ->
                        musicChannel.trySend(chunk)
                    }
                    session.lyriaMusicService = music
                    music.connect()
                    music.setMood("peaceful")
                } catch (e: Exception) {
                    println("GameWSHandler: Lyria music failed to start: ${e.message}")
                }
            }

            // Push existing feed entries so the client has full context on connect/reconnect
            val existingFeed = session.feedStore.all()
            if (existingFeed.isNotEmpty()) {
                println("GameWSHandler: Pushing ${existingFeed.size} existing feed entries on connect")
                ws.sendFeedSync(existingFeed)
            }

            ws.send("""{"type": "connected"}""")

            // Opening feed events were already sent to client during loading via REST
        } catch (e: Exception) {
            ws.send("""{"type": "error", "message": "${e.message?.replace("\"", "\\\"")}"}""")
        }
    }

    private suspend fun handleAudio(session: GameSession, msg: JsonObject) {
        val gemini = session.geminiSession ?: return
        val b64 = msg["data"]?.jsonPrimitive?.content ?: return
        val pcm = Base64.getDecoder().decode(b64)

        val params = LiveSendRealtimeInputParameters.builder()
            .media(Blob.builder().mimeType("audio/pcm").data(pcm))
            .build()
        try {
            gemini.sendRealtimeInput(params)
        } catch (e: Exception) {
            session.toolCallPending = true
            println("GameWSHandler: sendRealtimeInput rejected (json audio), gating input: ${e.message}")
        }
    }

    private suspend fun handleText(session: GameSession, msg: JsonObject) {
        val gemini = session.geminiSession ?: return
        val text = msg["content"]?.jsonPrimitive?.content ?: return
        println("GameWSHandler: handleText: ${text.take(80)}")

        val params = LiveSendClientContentParameters.builder()
            .turnComplete(true)
            .turns(
                Content.builder()
                    .role("user")
                    .parts(listOf(Part.builder().text(text).build()))
                    .build()
            )
            .build()
        gemini.sendClientContent(params)
    }

    private suspend fun handleGeminiMessage(
        ws: DefaultWebSocketServerSession,
        session: GameSession,
        message: LiveServerMessage
    ) {
        val feed = session.feedStore
        val hasToolCall = message.toolCall().isPresent
        val hasTurnComplete = message.serverContent().orElse(null)?.turnComplete()?.orElse(false) ?: false
        if (hasToolCall || hasTurnComplete) {
            println("GameWSHandler: Gemini message — toolCall=$hasToolCall turnComplete=$hasTurnComplete")
        }

        // Handle tool calls
        val toolCall = message.toolCall().orElse(null)
        if (toolCall != null) {
            val funcNames = toolCall.functionCalls().orElse(emptyList()).mapNotNull { it.name().orElse(null) }
            println("GameWSHandler: TOOL CALL received: $funcNames")
            session.toolCallPending = true
            // Flush narration and companion asides before tool execution
            feed.flushNarration()?.let { ws.sendFeedEntry(it) }
            feed.flushCompanion()?.let { ws.sendFeedEntry(it) }
            try {
                handleToolCall(ws, session, toolCall)
                println("GameWSHandler: Tool call complete, toolCallPending=false, resuming message processing")
            } finally {
                session.toolCallPending = false
            }
        }

        // Handle server content (text + audio)
        val content = message.serverContent().orElse(null) ?: return

        if (content.interrupted().orElse(false)) {
            // Flush any partial narration/companion before interruption
            feed.flushNarration()?.let { ws.sendFeedEntry(it) }
            feed.flushCompanion()?.let { ws.sendFeedEntry(it) }
            ws.send("""{"type": "interrupted"}""")
        }

        val modelTurn = content.modelTurn().orElse(null)
        if (modelTurn != null) {
            val parts = modelTurn.parts().orElse(emptyList())
            for (part in parts) {
                val text = part.text().orElse(null)
                if (text != null) {
                    val cleaned = text.replace(Regex("<ctrl\\d+>"), "").trim()
                    if (cleaned.isNotEmpty() && !cleaned.startsWith("**")) {
                        // Route to narration or companion aside based on whether
                        // we're still reading back engine narration
                        val narrative = session.lastNarrativeText
                        if (narrative != null && !session.narrativeConsumed) {
                            // Still reading engine narration — accumulate as narration
                            feed.appendNarration(cleaned)
                            // Check if we've consumed most of the narrative
                            val narrationSoFar = feed.peekNarration()
                            if (narrationSoFar.length >= narrative.length * 0.7) {
                                session.narrativeConsumed = true
                            }
                        } else {
                            // No pending narration or already consumed — this is companion aside
                            feed.appendCompanion(cleaned)
                        }
                        // Send raw text for subtitles regardless
                        ws.send("""{"type": "text", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(cleaned))}}""")
                    }
                }

                val audioBytes = part.inlineData().orElse(null)?.data()?.orElse(null)
                if (audioBytes != null) {
                    ws.send(Frame.Binary(true, audioBytes))
                }
            }
        }


        // Input transcription — player speech
        val inputText = content.inputTranscription().orElse(null)?.text()?.orElse(null)
        if (inputText != null) {
            // Flush any pending narration when player starts speaking
            feed.flushNarration()?.let { ws.sendFeedEntry(it) }

            // Accumulate player speech
            feed.appendPlayerSpeech(inputText)

            ws.send("""{"type": "transcript", "role": "user", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(inputText))}}""")

        }

        // Output transcription — just for subtitles, not the feed
        val outputText = content.outputTranscription().orElse(null)?.text()?.orElse(null)
        if (outputText != null) {
            val cleaned = outputText.replace(Regex("<ctrl\\d+>"), "").trim()
            if (cleaned.isNotEmpty()) {
                ws.send("""{"type": "transcript", "role": "model", "content": ${json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(cleaned))}}""")
            }
        }

        if (content.turnComplete().orElse(false)) {
            // Flush player speech, narration, and companion asides on turn complete
            feed.flushPlayerSpeech()?.let { ws.sendFeedEntry(it) }
            feed.flushNarration()?.let { ws.sendFeedEntry(it) }
            // Determine companion name from world seed
            val companionName = when (session.game.getCompanionVoice()) {
                "Orus" -> "Hank"
                "Puck" -> "Pip"
                "Fenrir" -> "Glitch"
                "Kore" -> "Bramble"
                else -> "Companion"
            }
            feed.flushCompanion(companionName)?.let { ws.sendFeedEntry(it) }
            // Reset narrative tracking for next turn
            session.lastNarrativeText = null
            session.narrativeConsumed = false
            ws.send("""{"type": "turn_complete"}""")
        }
    }

    private suspend fun handleToolCall(
        ws: DefaultWebSocketServerSession,
        session: GameSession,
        toolCall: LiveServerToolCall
    ) {
        val feed = session.feedStore
        val functionCalls = toolCall.functionCalls().orElse(emptyList())
        val responses = mutableListOf<FunctionResponse>()

        for (fc in functionCalls) {
            val name = fc.name().orElse("")
            val id = fc.id().orElse(name)
            val args = fc.args().orElse(emptyMap())

            // Emit tool_call feed entry
            val toolCallEntry = feed.append("tool_call", name, buildJsonObject {
                args.forEach { (k, v) -> put(k, JsonPrimitive(v.toString())) }
            })
            ws.sendFeedEntry(toolCallEntry)

            val resultData: JsonObject = try {
                if (name == "send_player_input") {
                    val input = args["input"]?.toString() ?: ""
                    val events = session.game.processInput(input).toList()

                    for (event in events) {
                        emitGameEventFeed(ws, session, event)
                    }

                    try {
                        val stateSnapshot = session.game.getState()
                        val stateJson = json.encodeToString(GameStateSnapshot.serializer(), stateSnapshot)
                        ws.send("""{"type": "state_update", "state": $stateJson}""")
                    } catch (_: Exception) {}

                    // Build human-readable summary for Gemini (not raw JSON)
                    val narrative = buildList {
                        for (event in events) {
                            when (event) {
                                is GameEvent.NarratorText -> add(event.text)
                                is GameEvent.NPCDialogue -> add("${event.npcName} says: ${event.text}")
                                is GameEvent.SystemNotification -> add("[System] ${event.text}")
                                is GameEvent.CombatLog -> add("[Combat] ${event.text}")
                                is GameEvent.StatChange -> add("${event.statName}: ${event.oldValue} → ${event.newValue}")
                                is GameEvent.ItemGained -> add("Gained item: ${event.itemName} x${event.quantity}")
                                is GameEvent.QuestUpdate -> add("Quest '${event.questName}': ${event.status}")
                                is GameEvent.SceneImage -> add("[Scene Art: ${event.description}]")
                                else -> {} // skip audio-only events
                            }
                        }
                    }.joinToString("\n")

                    // Store narrative for companion aside detection
                    session.lastNarrativeText = narrative
                    session.narrativeConsumed = false

                    // Auto-save after each player turn so resume works
                    try { session.game.save() } catch (_: Exception) {}

                    buildJsonObject {
                        put("success", JsonPrimitive(true))
                        put("narrative", JsonPrimitive(narrative.ifEmpty { "Action processed." }))
                    }
                } else if (name == "generate_portrait") {
                    val appearance = (args["appearance"] ?: args["description"] ?: "").toString()
                    val charName = (args["characterName"] ?: "").toString()
                    try {
                        val imgResult = session.imageService.generatePortrait(
                            PortraitRequest(name = charName, appearance = appearance)
                        )
                        if (imgResult is ImageResult.Success) {
                            val b64 = Base64.getEncoder().encodeToString(imgResult.imageData)
                            // Emit as scene_image feed entry
                            val imgEntry = feed.append("scene_image", null, buildJsonObject {
                                put("data", JsonPrimitive(b64))
                                put("mimeType", JsonPrimitive(imgResult.mimeType))
                                put("description", JsonPrimitive("Portrait of $charName"))
                            })
                            ws.sendFeedEntry(imgEntry)
                            // Also send legacy scene_image for backward compat
                            ws.send("""{"type": "scene_image", "data": "$b64", "mimeType": "${imgResult.mimeType}"}""")
                            buildJsonObject {
                                put("success", JsonPrimitive(true))
                                put("description", JsonPrimitive("Portrait of $charName"))
                            }
                        } else {
                            buildJsonObject {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Portrait generation failed"))
                            }
                        }
                    } catch (e: Exception) {
                        buildJsonObject {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Portrait generation error: ${e.message}"))
                        }
                    }
                } else {
                    val argsMap: Map<String, Any?> = args.mapValues { (_, value) ->
                        when (value) {
                            is String -> value
                            is Number -> value
                            is Boolean -> value
                            else -> value.toString()
                        }
                    }

                    val result = session.game.executeTool(name, argsMap)

                    for (event in result.events) {
                        emitGameEventFeed(ws, session, event)
                    }

                    // Emit tool_result feed entry
                    val toolResultEntry = feed.append("tool_result", name, buildJsonObject {
                        put("success", JsonPrimitive(result.success))
                        result.data.let { put("data", it) }
                        result.error?.let { put("error", JsonPrimitive(it)) }
                    })
                    ws.sendFeedEntry(toolResultEntry)

                    if (name == "shift_music_mood") {
                        val mood = argsMap["mood"]?.toString() ?: "peaceful"
                        session.lyriaMusicService?.setMood(mood)
                        val moodEntry = feed.append("music_change", mood)
                        ws.sendFeedEntry(moodEntry)
                    }

                    buildJsonObject {
                        put("success", JsonPrimitive(result.success))
                        result.data.let { put("data", it) }
                        result.error?.let { put("error", JsonPrimitive(it)) }
                    }
                }
            } catch (e: Exception) {
                println("GameWSHandler: Tool '$name' failed: ${e.message}")
                e.printStackTrace()
                val errorEntry = feed.append("system", "Tool '$name' failed: ${e.message}")
                ws.sendFeedEntry(errorEntry)
                buildJsonObject {
                    put("success", JsonPrimitive(false))
                    put("error", JsonPrimitive(e.message ?: "Unknown error"))
                }
            }

            responses.add(
                FunctionResponse.builder()
                    .id(id)
                    .name(name)
                    .response(mapOf<String, Any>("result" to resultData.toString()))
                    .build()
            )
        }

        if (responses.isNotEmpty()) {
            val responseNames = responses.mapNotNull { it.name().orElse(null) }
            println("GameWSHandler: Sending ${responses.size} tool response(s) back to Gemini: $responseNames")
            val params = LiveSendToolResponseParameters.builder()
                .functionResponses(responses)
                .build()
            try {
                session.geminiSession?.sendToolResponse(params)
            } catch (e: Exception) {
                println("GameWSHandler: Failed to send tool response to Gemini: ${e.message}")
            }
        }
    }

    /**
     * Convert a GameEvent into one or more FeedEntry objects and push to the client.
     */
    private suspend fun emitGameEventFeed(
        ws: DefaultWebSocketServerSession,
        session: GameSession,
        event: GameEvent
    ) {
        val feed = session.feedStore

        when (event) {
            is GameEvent.NarratorText -> {
                val entry = feed.append("narration", event.text)
                ws.sendFeedEntry(entry)
            }
            is GameEvent.NPCDialogue -> {
                val entry = feed.append("npc_dialogue", event.text, buildJsonObject {
                    put("npcName", JsonPrimitive(event.npcName))
                })
                ws.sendFeedEntry(entry)
            }
            is GameEvent.SystemNotification -> {
                val entry = feed.append("system", event.text)
                ws.sendFeedEntry(entry)
            }
            is GameEvent.CombatLog -> {
                val entry = feed.append("combat_action", event.text)
                ws.sendFeedEntry(entry)
            }
            is GameEvent.StatChange -> {
                val entry = feed.append("stat_change", "${event.statName}: ${event.oldValue} → ${event.newValue}", buildJsonObject {
                    put("statName", JsonPrimitive(event.statName))
                    put("oldValue", JsonPrimitive(event.oldValue))
                    put("newValue", JsonPrimitive(event.newValue))
                })
                ws.sendFeedEntry(entry)
            }
            is GameEvent.ItemGained -> {
                val entry = feed.append("item_gained", event.itemName, buildJsonObject {
                    put("itemName", JsonPrimitive(event.itemName))
                    put("quantity", JsonPrimitive(event.quantity))
                })
                ws.sendFeedEntry(entry)
            }
            is GameEvent.QuestUpdate -> {
                val entry = feed.append("quest_update", event.questName, buildJsonObject {
                    put("questName", JsonPrimitive(event.questName))
                    put("status", JsonPrimitive(event.status.name))
                })
                ws.sendFeedEntry(entry)
            }
            is GameEvent.SceneImage -> {
                // Store image in session cache and send URL (not inline base64 — too large for WebSocket)
                suspend fun storeAndSendImageUrl(imageData: ByteArray, mimeType: String, description: String) {
                    val imageId = java.util.UUID.randomUUID().toString().take(8)
                    session.images[imageId] = Pair(imageData, mimeType)
                    val imageUrl = "/api/game/${session.id}/image/$imageId"
                    println("GameWSHandler: Scene image stored as $imageId (${imageData.size} bytes), url=$imageUrl")
                    val entry = feed.append("scene_image", null, buildJsonObject {
                        put("imageUrl", JsonPrimitive(imageUrl))
                        put("mimeType", JsonPrimitive(mimeType))
                        put("description", JsonPrimitive(description.take(200)))
                    })
                    ws.sendFeedEntry(entry)
                    ws.send("""{"type": "scene_image", "imageUrl": "$imageUrl", "mimeType": "$mimeType"}""")
                }

                if (event.imageData.isNotEmpty()) {
                    println("GameWSHandler: SceneImage with inline data (${event.imageData.size} bytes)")
                    storeAndSendImageUrl(event.imageData, "image/png", event.description)
                } else if (event.description.isNotBlank()) {
                    println("GameWSHandler: SceneImage — generating server-side (${event.description.take(80)}...)")
                    try {
                        val state = session.game.getState()
                        val imgResult = session.imageService.generateSceneArt(
                            SceneArtRequest(
                                locationName = state.location,
                                description = event.description.take(500)
                            )
                        )
                        if (imgResult is ImageResult.Success) {
                            storeAndSendImageUrl(imgResult.imageData, imgResult.mimeType, event.description)
                        } else if (imgResult is ImageResult.Failure) {
                            println("GameWSHandler: Scene art generation failed: ${imgResult.error}")
                        }
                    } catch (e: Exception) {
                        println("GameWSHandler: Scene art generation failed: ${e.message}")
                    }
                }
            }
            is GameEvent.NPCPortrait -> {
                if (event.imageData.isNotEmpty()) {
                    val b64 = Base64.getEncoder().encodeToString(event.imageData)
                    val entry = feed.append("npc_portrait", null, buildJsonObject {
                        put("npcName", JsonPrimitive(event.npcName))
                        put("data", JsonPrimitive(b64))
                    })
                    ws.sendFeedEntry(entry)
                }
            }
            is GameEvent.MusicChange -> {
                val entry = feed.append("music_change", event.mood)
                ws.sendFeedEntry(entry)
            }
            is GameEvent.ToolCallResults -> {
                for (result in event.results) {
                    val entry = feed.append("tool_result", result.toolName, buildJsonObject {
                        put("success", JsonPrimitive(result.success))
                        try {
                            put("data", json.parseToJsonElement(result.data))
                        } catch (_: Exception) {
                            put("data", JsonPrimitive(result.data))
                        }
                    })
                    ws.sendFeedEntry(entry)
                }
            }
            is GameEvent.NarratorAudio -> {} // Audio handled separately via binary frames
            is GameEvent.ItemIconGenerated -> {} // Not needed in feed
        }
    }
}
