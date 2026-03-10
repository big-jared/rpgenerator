package com.rpgenerator.core.story

import kotlinx.serialization.Serializable

/**
 * WorldSeed - Complete definition of a game world's flavor, rules, and starting state.
 *
 * Seeds are hardcoded world templates inspired by popular LitRPG subgenres.
 * They define everything the narrator needs to create a cohesive experience.
 */
@Serializable
data class WorldSeed(
    val id: String,
    val name: String,
    val displayName: String,
    val tagline: String,

    val powerSystem: PowerSystem,
    val worldState: WorldState,
    val startingLocation: StartingLocation,
    val corePlot: CorePlot,
    val systemVoice: SystemVoice,
    val tutorial: TutorialDefinition,

    val narratorPrompt: String,
    val tone: List<String>,
    val themes: List<String>,
    val inspirations: List<String>
)

@Serializable
data class PowerSystem(
    val name: String,
    val source: String,
    val progression: String,
    val uniqueMechanic: String,
    val limitations: String
)

@Serializable
data class WorldState(
    val era: String,
    val civilizationStatus: String,
    val threats: List<String>,
    val atmosphere: String
)

@Serializable
data class StartingLocation(
    val name: String,
    val type: String,
    val description: String,
    val features: List<String>,
    val dangers: List<String>,
    val opportunities: List<String>
)

@Serializable
data class CorePlot(
    val hook: String,
    val centralQuestion: String,
    val actOneGoal: String,
    val majorChoices: List<String>
)

@Serializable
data class SystemVoice(
    val personality: String,
    val messageStyle: String,
    val exampleMessages: List<String>,
    val attitudeTowardPlayer: String
)

@Serializable
data class TutorialDefinition(
    val isSolo: Boolean,
    val objectives: List<TutorialObjective>,
    val guide: TutorialGuide?,
    val completionReward: String,
    val exitDescription: String,
    val namedPeers: List<TutorialPeer> = emptyList()
)

/**
 * A named peer — another human ripped from Earth the same day the player was.
 *
 * These are the HEART of the tutorial experience. Each peer:
 * - Has a class that defines HOW they approach the tutorial (not just combat style)
 * - Teaches the player something about the game through relationship, not exposition
 * - Has emotional needs and vulnerabilities that create real bonds
 * - Progresses through relationship stages based on player interaction
 *
 * The GM MUST prioritize peer interactions over solo grinding.
 * Every 2-3 combat encounters should include a peer moment.
 */
@Serializable
data class TutorialPeer(
    val id: String,
    val name: String,
    val className: String,
    val formerLife: String,
    val personality: String,
    val combatStyle: String,
    val relationship: String, // rival, ally, wildcard, threat
    val faction: String?,
    val firstAppearance: String,
    val arc: String,
    val dialogueStyle: String,
    val exampleLines: List<String>,

    // ── CLASS-DRIVEN PRIORITIES ──
    // How their class shapes their worldview and tutorial strategy
    val classPriority: String = "", // what they optimize for based on class
    val classPhilosophy: String = "", // how their class reflects who they ARE
    val classConflict: String = "", // tension between their class and their humanity

    // ── TEACHING MOMENTS ──
    // What the player learns from this relationship (game mechanics through story)
    val teachesPlayer: String = "", // game concept they naturally introduce
    val sharedActivity: String = "", // what you DO together (not just talk)

    // ── RELATIONSHIP STAGES ──
    // How the relationship evolves — the GM should track and progress these
    val stageOne: String = "", // first meeting — impression, hook
    val stageTwo: String = "", // building trust — shared danger or discovery
    val stageThree: String = "", // deepening — vulnerability, real conversation
    val stageFour: String = "", // tested — conflict or crisis that defines the bond

    // ── EMOTIONAL HOOKS ──
    // What makes you CARE about this person
    val vulnerability: String = "", // the thing they're afraid of or struggling with
    val moment: String = "", // a small, human moment that reveals who they really are
    val needFromPlayer: String = "" // what they need that only the player can provide
)

@Serializable
data class TutorialObjective(
    val id: String,
    val description: String,
    val type: String, // "class_selection", "kill_count", "reach_level", "boss_kill", "exploration"
    val target: Int = 1 // For kill_count or reach_level
)

@Serializable
data class TutorialGuide(
    val name: String,
    val appearance: String,
    val personality: String,
    val role: String,
    val dialogueOnMeet: String,
    val dialogueOnClassSelect: String,
    val dialogueOnProgress: String,
    val dialogueOnComplete: String,
    val exampleLines: List<String>
)

/**
 * All available world seeds.
 */
object WorldSeeds {
    val INTEGRATION = IntegrationSeed.create()
    val TABLETOP = TabletopSeed.create()
    val CRAWLER = CrawlerSeed.create()
    val QUIET_LIFE = QuietLifeSeed.create()

    /**
     * Get all available seeds.
     */
    fun all(): List<WorldSeed> = listOf(INTEGRATION, TABLETOP, CRAWLER, QUIET_LIFE)

    /**
     * Get a seed by ID.
     */
    fun byId(id: String): WorldSeed? = all().find { it.id == id }

    /**
     * Get a random seed.
     */
    fun random(): WorldSeed = all().random()
}
