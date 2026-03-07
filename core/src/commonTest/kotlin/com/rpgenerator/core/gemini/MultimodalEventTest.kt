package com.rpgenerator.core.gemini

import app.cash.turbine.test
import com.rpgenerator.core.api.GameEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for multimodal game events — verifying that images, music, and audio
 * flow correctly through the event system and can be rendered by the UI.
 *
 * These define the contract for how the Compose UI will consume multimodal content.
 */
class MultimodalEventTest {

    // ── New Event Types ────────────────────────────────────────────

    @Test
    fun `SceneImage event carries image data`() {
        val event = GameEvent.SceneImage(
            imageData = ByteArray(1024) { it.toByte() },
            description = "A dark forest with twisted trees"
        )

        assertTrue(event.imageData.isNotEmpty())
        assertEquals("A dark forest with twisted trees", event.description)
    }

    @Test
    fun `MusicChange event carries mood and optional audio`() {
        val event = GameEvent.MusicChange(
            mood = "tense",
            audioData = ByteArray(4800)
        )

        assertEquals("tense", event.mood)
        assertNotNull(event.audioData)
    }

    @Test
    fun `MusicChange event works without audio data`() {
        val event = GameEvent.MusicChange(
            mood = "peaceful",
            audioData = null
        )

        assertEquals("peaceful", event.mood)
        assertNull(event.audioData)
    }

    @Test
    fun `NarratorAudio event carries PCM data`() {
        val event = GameEvent.NarratorAudio(
            audioData = ByteArray(9600),
            sampleRate = 24000
        )

        assertEquals(9600, event.audioData.size)
        assertEquals(24000, event.sampleRate)
    }

    @Test
    fun `NPCPortrait event carries portrait for specific NPC`() {
        val event = GameEvent.NPCPortrait(
            npcId = "npc_grimjaw",
            npcName = "Grimjaw",
            imageData = ByteArray(512)
        )

        assertEquals("npc_grimjaw", event.npcId)
        assertEquals("Grimjaw", event.npcName)
        assertTrue(event.imageData.isNotEmpty())
    }

    // ── Event Stream Ordering ──────────────────────────────────────

    @Test
    fun `multimodal events interleave with narrative events`() = runTest {
        val events = flow {
            // Narrator describes scene → scene art appears → narration continues
            emit(GameEvent.NarratorText("You enter the ancient temple..."))
            emit(GameEvent.SceneImage(
                imageData = ByteArray(100),
                description = "Ancient temple entrance"
            ))
            emit(GameEvent.MusicChange(mood = "mysterious", audioData = null))
            emit(GameEvent.NarratorText("Torches flicker along the walls."))
            emit(GameEvent.NPCDialogue(
                npcId = "guardian",
                npcName = "Temple Guardian",
                text = "Who dares enter?"
            ))
            emit(GameEvent.NPCPortrait(
                npcId = "guardian",
                npcName = "Temple Guardian",
                imageData = ByteArray(200)
            ))
        }

        events.test {
            // Verify the interleaved order
            assertTrue(awaitItem() is GameEvent.NarratorText)
            assertTrue(awaitItem() is GameEvent.SceneImage)
            assertTrue(awaitItem() is GameEvent.MusicChange)
            assertTrue(awaitItem() is GameEvent.NarratorText)
            assertTrue(awaitItem() is GameEvent.NPCDialogue)
            assertTrue(awaitItem() is GameEvent.NPCPortrait)
            awaitComplete()
        }
    }

    @Test
    fun `combat sequence produces multimodal events`() = runTest {
        val events = flow {
            emit(GameEvent.MusicChange(mood = "battle", audioData = null))
            emit(GameEvent.NarratorText("The goblin lunges at you!"))
            emit(GameEvent.SceneImage(
                imageData = ByteArray(100),
                description = "Goblin attacking"
            ))
            emit(GameEvent.CombatLog("You swing your sword for 15 damage"))
            emit(GameEvent.StatChange("xp", 0, 25))
            emit(GameEvent.NarratorText("The goblin falls."))
            emit(GameEvent.SceneImage(
                imageData = ByteArray(100),
                description = "Defeated goblin"
            ))
            emit(GameEvent.MusicChange(mood = "victory", audioData = null))
        }

        events.test {
            val firstEvent = awaitItem()
            assertTrue(firstEvent is GameEvent.MusicChange)
            assertEquals("battle", (firstEvent as GameEvent.MusicChange).mood)

            // Consume the combat sequence
            assertTrue(awaitItem() is GameEvent.NarratorText)
            assertTrue(awaitItem() is GameEvent.SceneImage)
            assertTrue(awaitItem() is GameEvent.CombatLog)
            assertTrue(awaitItem() is GameEvent.StatChange)
            assertTrue(awaitItem() is GameEvent.NarratorText)
            assertTrue(awaitItem() is GameEvent.SceneImage)

            val lastEvent = awaitItem()
            assertTrue(lastEvent is GameEvent.MusicChange)
            assertEquals("victory", (lastEvent as GameEvent.MusicChange).mood)

            awaitComplete()
        }
    }

    // ── Gemini Output → GameEvent Mapping ──────────────────────────

    @Test
    fun `GeminiOutput maps to GameEvents correctly`() {
        val geminiOutputs = listOf(
            GeminiOutput.Text("You see a merchant."),
            GeminiOutput.Image(ByteArray(100), "image/png", "Merchant stall"),
            GeminiOutput.Audio(ByteArray(4800), 24000)
        )

        val gameEvents = geminiOutputs.map { output ->
            when (output) {
                is GeminiOutput.Text -> GameEvent.NarratorText(output.content)
                is GeminiOutput.Image -> GameEvent.SceneImage(
                    imageData = output.imageData,
                    description = output.description
                )
                is GeminiOutput.Audio -> GameEvent.NarratorAudio(
                    audioData = output.pcmData,
                    sampleRate = output.sampleRate
                )
                is GeminiOutput.ToolCallRequest -> null
                is GeminiOutput.TurnComplete -> null
            }
        }.filterNotNull()

        assertEquals(3, gameEvents.size)
        assertTrue(gameEvents[0] is GameEvent.NarratorText)
        assertTrue(gameEvents[1] is GameEvent.SceneImage)
        assertTrue(gameEvents[2] is GameEvent.NarratorAudio)
    }

    // ── UI Rendering Contract ──────────────────────────────────────

    @Test
    fun `scene image event has data UI can render`() {
        val event = GameEvent.SceneImage(
            imageData = ByteArray(1024) { (it % 256).toByte() },
            description = "Forest clearing at dawn"
        )

        // UI needs: non-empty image data + description for accessibility
        assertTrue(event.imageData.size > 0, "Image data must not be empty")
        assertTrue(event.description.isNotBlank(), "Description needed for accessibility")
    }

    @Test
    fun `audio event has correct format for playback`() {
        val event = GameEvent.NarratorAudio(
            audioData = ByteArray(9600),
            sampleRate = 24000
        )

        // Gemini outputs 24kHz audio
        assertEquals(24000, event.sampleRate)
        assertTrue(event.audioData.isNotEmpty())
    }
}
