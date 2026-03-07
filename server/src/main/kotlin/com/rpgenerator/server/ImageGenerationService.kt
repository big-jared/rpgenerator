package com.rpgenerator.server

import com.google.genai.Client
import com.google.genai.types.GenerateImagesConfig
import com.google.genai.types.PersonGeneration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generates images using Gemini's Imagen API.
 * Handles portrait generation, scene art, and item illustrations.
 *
 * Uses carefully crafted prompts to maintain visual consistency
 * across a game session (consistent art style, character appearance).
 */
class ImageGenerationService(
    private val client: Client,
    private val model: String = "imagen-4.0-fast-generate-001"
) {
    /**
     * Generate a character portrait.
     * Returns image bytes (PNG) or null if generation fails.
     */
    suspend fun generatePortrait(request: PortraitRequest): ImageResult {
        val prompt = buildPortraitPrompt(request)
        return generateImage(
            prompt = prompt,
            negativePrompt = PORTRAIT_NEGATIVE,
            aspectRatio = "1:1"
        )
    }

    /**
     * Generate scene art for the current game moment.
     * Returns image bytes (PNG) or null if generation fails.
     */
    suspend fun generateSceneArt(request: SceneArtRequest): ImageResult {
        val prompt = buildScenePrompt(request)
        return generateImage(
            prompt = prompt,
            negativePrompt = SCENE_NEGATIVE,
            aspectRatio = "16:9"
        )
    }

    /**
     * Generate an item illustration.
     */
    suspend fun generateItemArt(request: ItemArtRequest): ImageResult {
        val prompt = buildItemPrompt(request)
        return generateImage(
            prompt = prompt,
            negativePrompt = ITEM_NEGATIVE,
            aspectRatio = "1:1"
        )
    }

    private suspend fun generateImage(
        prompt: String,
        negativePrompt: String,
        aspectRatio: String
    ): ImageResult {
        return try {
            val config = GenerateImagesConfig.builder()
                .numberOfImages(1)
                .aspectRatio(aspectRatio)
                .personGeneration(PersonGeneration.Known.ALLOW_ADULT)
                .outputMimeType("image/png")
                .build()

            val response = withContext(Dispatchers.IO) {
                client.models.generateImages(model, prompt, config)
            }

            val images = response.images()
            if (images != null && images.isNotEmpty()) {
                val imageBytes = images[0].imageBytes()
                if (imageBytes.isPresent) {
                    ImageResult.Success(
                        imageData = imageBytes.get(),
                        mimeType = "image/png",
                        prompt = prompt
                    )
                } else {
                    ImageResult.Failure("Image generated but no bytes returned", prompt)
                }
            } else {
                ImageResult.Failure("No images in response (may have been filtered)", prompt)
            }
        } catch (e: Exception) {
            ImageResult.Failure("Image generation failed: ${e.message}", prompt)
        }
    }

    // ── Prompt Engineering ────────────────────────────────────────────

    private fun buildPortraitPrompt(req: PortraitRequest): String = buildString {
        append(STYLE_PREFIX)
        append("Character portrait, ")

        // Framing
        append(when (req.framing) {
            PortraitFraming.BUST -> "bust shot from chest up, "
            PortraitFraming.FULL_BODY -> "full body standing pose, "
            PortraitFraming.CLOSE_UP -> "dramatic close-up face shot, "
        })

        // Character identity
        append("of ${req.name}")
        if (req.characterClass != null) {
            append(", a ${req.characterClass}")
        }
        append(". ")

        // Appearance
        if (req.appearance != null) {
            append("${req.appearance}. ")
        }

        // Equipment/class flavor
        if (req.equipment != null) {
            append("Wearing/carrying: ${req.equipment}. ")
        }

        // Mood/expression
        val mood = req.mood ?: "determined"
        append("Expression: $mood. ")

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
        append(STYLE_PREFIX)
        append("Item illustration on dark background, ")
        append("${req.name}. ")
        append("${req.description}. ")

        if (req.rarity != null) {
            append(getRarityVisuals(req.rarity))
        }

        append("Clean item card style, centered composition. ")
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
            "Digital fantasy art, painterly style with rich colors and dramatic lighting, " +
            "LitRPG game illustration, high detail, "

        private const val QUALITY_SUFFIX =
            "Masterful composition, professional fantasy game art quality, " +
            "rich color palette, dramatic lighting."

        private const val PORTRAIT_NEGATIVE =
            "photo, photograph, realistic photo, selfie, " +
            "blurry, low quality, deformed, extra limbs, " +
            "text, watermark, signature, frame, border, " +
            "nsfw, nude, gore, blood splatter"

        private const val SCENE_NEGATIVE =
            "photo, photograph, realistic photo, " +
            "blurry, low quality, deformed, " +
            "text, watermark, signature, UI elements, " +
            "nsfw, gore"

        private const val ITEM_NEGATIVE =
            "photo, photograph, hands holding item, person, character, " +
            "blurry, low quality, text, watermark, " +
            "cluttered background, multiple items"
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
