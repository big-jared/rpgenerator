package com.rpgenerator.server

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generates images using Gemini's native multimodal image generation.
 * Uses gemini-2.5-flash-preview-native-audio-thinking with responseModalities: [TEXT, IMAGE]
 * for interleaved text+image output — a key requirement for Creative Storyteller track.
 *
 * Handles portrait generation, scene art, and item illustrations.
 * Uses carefully crafted prompts to maintain visual consistency
 * across a game session (consistent art style, character appearance).
 */
class ImageGenerationService(
    private val client: Client,
    private val model: String = "gemini-2.5-flash-image"
) {
    /**
     * Generate a character portrait.
     * Returns image bytes or null if generation fails.
     */
    suspend fun generatePortrait(request: PortraitRequest): ImageResult {
        val prompt = buildPortraitPrompt(request)
        return generateImage(prompt)
    }

    /**
     * Generate scene art for the current game moment.
     * Returns image bytes or null if generation fails.
     */
    suspend fun generateSceneArt(request: SceneArtRequest): ImageResult {
        val prompt = buildScenePrompt(request)
        return generateImage(prompt)
    }

    /**
     * Generate an item illustration.
     */
    suspend fun generateItemArt(request: ItemArtRequest, iconSize: Boolean = false): ImageResult {
        val prompt = buildItemPrompt(request)
        return generateImage(prompt)
    }

    /**
     * Core generation method — sends prompt to Gemini with TEXT+IMAGE modalities,
     * extracts the inline image from the response.
     */
    private suspend fun generateImage(prompt: String): ImageResult {
        return try {
            val config = GenerateContentConfig.builder()
                .responseModalities("TEXT", "IMAGE")
                .build()

            val contents = listOf(
                Content.builder()
                    .role("user")
                    .parts(listOf(Part.builder().text(prompt).build()))
                    .build()
            )

            val response = withContext(Dispatchers.IO) {
                client.models.generateContent(model, contents, config)
            }

            // Extract image from response parts
            val parts = response.candidates().orElse(emptyList()).firstOrNull()
                ?.content()?.orElse(null)?.parts()?.orElse(emptyList()) ?: emptyList()

            var imageBytes: ByteArray? = null
            var mimeType = "image/png"

            for (part in parts) {
                val inlineData = part.inlineData().orElse(null)
                if (inlineData != null) {
                    imageBytes = inlineData.data().orElse(null)
                    mimeType = inlineData.mimeType().orElse("image/png")
                    if (imageBytes != null) break
                }
            }

            if (imageBytes != null) {
                ImageResult.Success(
                    imageData = imageBytes,
                    mimeType = mimeType,
                    prompt = prompt
                )
            } else {
                ImageResult.Failure("Gemini returned no image data in response", prompt)
            }
        } catch (e: Exception) {
            ImageResult.Failure("Image generation failed: ${e.message}", prompt)
        }
    }

    // ── Prompt Engineering ────────────────────────────────────────────

    private fun buildPortraitPrompt(req: PortraitRequest): String = buildString {
        append("Generate an image: ")
        append(STYLE_PREFIX)

        // Framing
        append(when (req.framing) {
            PortraitFraming.BUST -> "portrait from chest up, "
            PortraitFraming.FULL_BODY -> "full body standing pose, "
            PortraitFraming.CLOSE_UP -> "dramatic close-up of face, "
        })

        // Class archetype (not the name — avoids text generation)
        if (req.characterClass != null) {
            append("a ${req.characterClass}, ")
        } else {
            append("a fantasy adventurer, ")
        }

        // Appearance
        if (req.appearance != null) {
            append("${req.appearance}. ")
        }

        // Equipment/class flavor
        if (req.equipment != null) {
            append("Wearing ${req.equipment}. ")
        }

        // Mood/expression
        val mood = req.mood ?: "determined"
        append("${mood} expression. ")

        // Tier/power level visual cues
        if (req.powerTier != null) {
            append(getPowerTierVisuals(req.powerTier))
        }

        // System-specific flavor
        if (req.systemType != null) {
            append(getSystemVisuals(req.systemType))
        }

        append(QUALITY_SUFFIX)
    }

    private fun buildScenePrompt(req: SceneArtRequest): String = buildString {
        append("Generate an image: ")
        append(STYLE_PREFIX)
        append("Wide establishing shot, ")

        // Location
        append("${req.locationName}. ")

        // Scene description
        append("${req.description}. ")

        // Atmosphere
        if (req.mood != null) {
            append("Atmosphere: ${req.mood}. ")
        }

        // Time of day
        if (req.timeOfDay != null) {
            append("${req.timeOfDay} lighting. ")
        }

        // Weather
        if (req.weather != null) {
            append("Weather: ${req.weather}. ")
        }

        // System-specific environmental effects
        if (req.systemType != null) {
            append(getSystemEnvironment(req.systemType))
        }

        // Action/narrative moment
        if (req.narrativeMoment != null) {
            append("${req.narrativeMoment}. ")
        }

        append(QUALITY_SUFFIX)
    }

    private fun buildItemPrompt(req: ItemArtRequest): String = buildString {
        append("Generate an image: ")
        append("Square fantasy RPG inventory icon, oil painting style, detailed brushwork, fantasy RPG UI aesthetic. ")
        append("${req.name}: ${req.description}. ")

        if (req.rarity != null) {
            append(getRarityVisuals(req.rarity))
        }

        append("Dark vignette background, warm tones, painterly rendering, centered composition, ")
        append("no text, no words, no letters, no writing. ")
        append(QUALITY_SUFFIX)
    }

    // ── Visual Language by Game System ─────────────────────────────────

    private fun getSystemVisuals(systemType: String): String = when (systemType.uppercase()) {
        "SYSTEM_INTEGRATION" -> "Faint holographic UI elements floating near the character, blue system windows, digital runes glowing on skin. "
        "CULTIVATION_PATH" -> "Spiritual energy aura, qi flowing visibly around the body, traditional cultivator robes with celestial motifs. "
        "DEATH_LOOP" -> "Subtle temporal distortion effects, faint afterimages, a haunted but determined look. "
        "DUNGEON_DELVE" -> "Torchlit underground atmosphere, dungeon gear, worn leather and steel. "
        "ARCANE_ACADEMY" -> "Magical sigils orbiting the character, academy robes with glowing trim, floating spellbook. "
        "TABLETOP_CLASSIC" -> "Classic high fantasy aesthetic, detailed armor and weaponry, heroic proportions. "
        "EPIC_JOURNEY" -> "Weathered traveler aesthetic, sweeping landscape behind, fellowship imagery. "
        "HERO_AWAKENING" -> "Energy crackling around the body, costume in mid-transformation, power emanating outward. "
        else -> ""
    }

    private fun getSystemEnvironment(systemType: String): String = when (systemType.uppercase()) {
        "SYSTEM_INTEGRATION" -> "Holographic system panels floating in the environment, blue grid lines overlaying reality. "
        "CULTIVATION_PATH" -> "Spiritual energy mist, jade formations, ancient temples on mountain peaks. "
        "DEATH_LOOP" -> "Subtle temporal fractures in the sky, déjà vu visual echoes. "
        "DUNGEON_DELVE" -> "Ancient stone corridors, flickering torchlight, mysterious carved runes on walls. "
        "ARCANE_ACADEMY" -> "Floating magical platforms, crystalline architecture, aurora-like magical currents in the sky. "
        "TABLETOP_CLASSIC" -> "Classic fantasy landscape, rolling hills, medieval settlements, dragon-soared skies. "
        "EPIC_JOURNEY" -> "Sweeping vistas, ancient roads, fellowship campfire warmth vs wilderness danger. "
        "HERO_AWAKENING" -> "Urban/modern environment being transformed by supernatural energy emergence. "
        else -> ""
    }

    private fun getPowerTierVisuals(tier: String): String = when (tier.uppercase()) {
        "E_GRADE", "D_GRADE" -> "Subtle, nascent power. Faint glow in the eyes. "
        "C_GRADE" -> "Visible energy aura, confident stance, clearly supernatural. "
        "B_GRADE" -> "Powerful aura distorting the air, intense eyes, battle-hardened. "
        "A_GRADE" -> "Overwhelming presence, reality warping slightly around them, legendary warrior. "
        "S_GRADE" -> "Godlike power radiating outward, floating slightly, the environment itself reacts to their presence. "
        else -> ""
    }

    private fun getRarityVisuals(rarity: String): String = when (rarity.uppercase()) {
        "COMMON" -> "Simple, functional appearance. Muted colors. "
        "UNCOMMON" -> "Slight magical shimmer, green-tinted glow. "
        "RARE" -> "Blue magical aura, intricate engravings, clearly enchanted. "
        "EPIC" -> "Purple ethereal glow, ornate design, powerful magical emanation. "
        "LEGENDARY" -> "Golden radiance, ancient and ornate design, legendary craftsmanship, reality-bending visual effects. "
        "MYTHIC" -> "Reality-warping presence, impossible geometry, colors that shouldn't exist, divine craftsmanship. "
        else -> ""
    }

    companion object {
        // Consistent art style anchor — keeps all images in the same visual family
        private const val STYLE_PREFIX =
            "Digital painting, fantasy concept art style, rich colors, dramatic cinematic lighting, " +
            "no text, no words, no letters, no writing, no labels, no captions, "

        private const val QUALITY_SUFFIX =
            "Sharp focus, professional illustration quality, painterly brushwork, " +
            "rich color palette, volumetric lighting, dark moody background."
    }
}

// ── Request/Response Types ────────────────────────────────────────────

data class PortraitRequest(
    val name: String,
    val appearance: String? = null,
    val characterClass: String? = null,
    val equipment: String? = null,
    val mood: String? = null,
    val powerTier: String? = null,
    val systemType: String? = null,
    val framing: PortraitFraming = PortraitFraming.BUST
)

enum class PortraitFraming {
    BUST,
    FULL_BODY,
    CLOSE_UP
}

data class SceneArtRequest(
    val locationName: String,
    val description: String,
    val mood: String? = null,
    val timeOfDay: String? = null,
    val weather: String? = null,
    val systemType: String? = null,
    val narrativeMoment: String? = null
)

data class ItemArtRequest(
    val name: String,
    val description: String,
    val rarity: String? = null
)

sealed class ImageResult {
    data class Success(
        val imageData: ByteArray,
        val mimeType: String,
        val prompt: String
    ) : ImageResult()

    data class Failure(
        val error: String,
        val prompt: String
    ) : ImageResult()
}
