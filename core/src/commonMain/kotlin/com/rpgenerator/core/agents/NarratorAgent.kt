package com.rpgenerator.core.agents

import com.rpgenerator.core.api.AgentChunk
import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.story.NarratorContext
import kotlinx.coroutines.flow.toList

internal class NarratorAgent(private val llm: LLMInterface, customPrompt: String? = null) {

    private val agentStream = llm.startAgent(
        customPrompt ?: """
        You are a professional LitRPG author narrating a living story. You've published ten books. Your editor cuts anything that doesn't earn its place.

        Second person, present tense. Always.

        THE CRAFT:
        - Write like you're telling a friend what happened — not drafting a manuscript.
        - One sharp detail per beat. 'Blood on the door handle' beats 'signs of a struggle.'
        - Short sentences hit harder. Vary rhythm. Let silence do work.
        - 2-3 sentences unless the moment genuinely demands more.

        DIALOGUE & CHARACTER:
        - People reveal themselves through how they talk, not through you describing them.
        - Don't write 'she said nervously' — write nervous dialogue.
        - What a character DOESN'T say matters more than what they do.

        BACKSTORY:
        - Weave the player's past through reflexes and half-thoughts, not exposition.
        - Muscle memory, old habits, flinches — the body remembers before the mind does.

        FORMATTING:
        - Use paragraph breaks (blank lines) between distinct beats or scene shifts.
        - Each beat of action, dialogue, or description gets its own paragraph.
        - System messages like [CLASS SELECTION PENDING] get their own line.
        - This text will be rendered with markdown — use **bold** for System messages and emphasis.

        PACING:
        - Not every moment needs to escalate. Quiet beats make action beats hit harder.
        - If the player is exploring or asking questions, match their energy. Be descriptive, not urgent.
        - After combat or intense moments, let the scene decompress — describe the aftermath, the silence, the relief.
        - A warm fire, a strange mural, a merchant's joke — these moments build the world players remember.

        NEVER:
        - 'You find yourself...' or 'You wake up...'
        - Purple prose, stacked adjectives, em-dash abuse
        - Narrate emotions ('You feel angry') — show the jaw clench instead
        - Explain game mechanics in prose
        - Repeat what the player already knows
        - End with option lists or 'What do you do?'
        - End with artificial cliffhangers or forced urgency when the player is just exploring
        - Fabricate System notifications or [BRACKETED MESSAGES] that contradict the game engine. If the engine says something succeeded, narrate it as successful. You describe the EXPERIENCE of game events — you do not invent fake ones.
        - INVENT NPCs. You may ONLY reference NPCs listed in "NPCs present" in the world state. If no NPCs are listed, the area is empty — do NOT describe unnamed strangers, shopkeepers, guards, or anyone the player could try to interact with. Crowd scenes are fine ("people mill about") but never give an unnamed character dialogue, a name, or distinguishing features that invite interaction.
        """.trimIndent()
    )

    /**
     * Narrate the opening scene with multimodal output (text + scene image).
     * Returns text and any generated images as AgentChunks.
     */
    suspend fun narrateOpeningMultimodal(state: GameState, narratorContext: NarratorContext? = null): List<AgentChunk> {
        val text = narrateOpening(state, narratorContext)
        // Re-send the narration with image generation request
        val imagePrompt = buildString {
            appendLine("Generate a scene image for this opening narration:")
            appendLine(text.take(500))
            appendLine()
            appendLine("Style: Digital painting, fantasy concept art, rich colors, dramatic cinematic lighting, wide establishing shot.")
            appendLine("Location: ${state.currentLocation.name} — ${state.currentLocation.description.take(200)}")
        }
        val imageChunks = agentStream.sendMessageMultimodal(imagePrompt, generateImage = true).toList()
        val images = imageChunks.filterIsInstance<AgentChunk.Image>()

        return buildList {
            add(AgentChunk.Text(text))
            addAll(images)
        }
    }

    suspend fun narrateOpening(state: GameState, narratorContext: NarratorContext? = null): String {
        val genreGuidance = getGenreGuidance(state.systemType)
        val coldOpen = inferColdOpenMoment(state.backstory, state.systemType)

        val npcsHere = state.getNPCsAtCurrentLocation()
        val npcContext = if (npcsHere.isNotEmpty()) {
            "NPCs present: ${npcsHere.joinToString(", ") { it.name }}"
        } else ""

        val storyContext = if (narratorContext != null) {
            """
            === STORY CONTEXT (Use this to add depth and foreshadowing) ===
            System Name: "${narratorContext.systemName}" (Use this name when referring to the System)
            System Personality: ${narratorContext.systemPersonality}
            Central Mystery: ${narratorContext.centralMystery}
            Primary Threat: ${narratorContext.primaryThreat}
            Theme: ${narratorContext.thematicCore}

            FORESHADOWING HOOKS (weave 1-2 of these subtly into the narration):
            ${narratorContext.currentForeshadowing.joinToString("\n            - ", prefix = "- ")}

            UPCOMING STORY BEATS to hint at:
            ${narratorContext.upcomingBeats.joinToString("\n            - ", prefix = "- ")}
            """.trimIndent()
        } else ""

        val systemNameRef = if (narratorContext != null) {
            "the ${narratorContext.systemName}"
        } else "the System"

        val prompt = """
            Write the opening scene. This is ${state.playerName}'s origin moment.

            Character: ${state.playerName}
            Backstory: ${state.backstory}

            Location: ${state.currentLocation.name}
            ${state.currentLocation.description}
            ${npcContext}

            $storyContext
            $genreGuidance

            COLD OPEN DIRECTIVE: ${coldOpen.sceneMoment}
            REALITY BREAK FLAVOR: ${coldOpen.breakFlavor}

            STRUCTURE (think novel opening, not game intro):
            1. COLD OPEN — Mid-action in their old life. No setup, no context.
               The reader catches up. Start mid-sentence if it serves the moment. (2-3 sentences)
            2. THE BREAK — Reality fractures. Felt in the body, not described
               clinically. Old senses fail. New ones ignite. (2-3 sentences)
            3. THE SYSTEM — First contact. One message from $systemNameRef —
               cryptic, unsettling, or coldly efficient. Not a wall of text.
               One notification that changes everything. (1-2 sentences)
            4. GROUNDED — Where they are now. One detail that makes it real.
               End on forward momentum. (1-2 sentences)
            ${if (npcContext.isNotEmpty()) "5. The guide appears — a presence, not an exposition dump." else ""}

            RULES:
            - 200-250 words. The opening earns the extra length.
            - Backstory manifests as BODY knowledge — reflexes, muscle memory, half-thoughts. Never exposition.
            - The System's first message feels alien and invasive — it knows things it shouldn't.
            - One sensory detail per beat. No stacking.
            - End on forward momentum, not a summary.
            - Do NOT narrate the player choosing a class, selecting abilities, or making any decisions. The player hasn't chosen yet. End BEFORE any choice is made — leave them standing in the new world, disoriented, with everything ahead of them.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    private data class ColdOpenDirective(val sceneMoment: String, val breakFlavor: String)

    private fun inferColdOpenMoment(backstory: String, systemType: SystemType): ColdOpenDirective {
        val lower = backstory.lowercase()
        val sceneMoment = when {
            lower.containsAny("soldier", "military", "guard", "warrior", "veteran") ->
                "Mid-patrol or mid-drill. Hands know the weapon before the brain does. Muscle memory is everything."
            lower.containsAny("scholar", "mage", "student", "researcher", "wizard", "academic") ->
                "Mid-study or mid-experiment. Mind racing through a problem. Ink-stained fingers or chalk dust."
            lower.containsAny("thief", "rogue", "criminal", "assassin", "smuggler") ->
                "Mid-job. Eyes mapping exits. Fingers light on the goods. Heart steady — this is routine."
            lower.containsAny("healer", "doctor", "priest", "cleric", "nurse", "medic") ->
                "Mid-procedure or mid-prayer. Hands saving someone. The calm focus of practiced care."
            lower.containsAny("merchant", "craftsman", "blacksmith", "trader", "shopkeeper") ->
                "Mid-transaction or mid-craft. Hands that know quality by touch. The rhythm of honest work."
            lower.containsAny("farmer", "hunter", "ranger", "woodsman", "trapper") ->
                "Mid-harvest or mid-hunt. Body attuned to the land. Reading weather in the wind."
            lower.containsAny("noble", "prince", "lord", "royal", "princess", "aristocrat") ->
                "Mid-audience or mid-ceremony. Weight of expectation on every word. The mask of composure."
            else ->
                "An ordinary moment — commuting, cooking, talking to someone they care about. Mundane and specific."
        }
        val breakFlavor = when (systemType) {
            SystemType.SYSTEM_INTEGRATION -> "The sky splits. Gravity stutters. Every screen goes white. Reality rewrites itself."
            SystemType.DEATH_LOOP -> "Time hiccups. A memory that hasn't happened yet. The sickening certainty this has happened before."
            SystemType.CULTIVATION_PATH -> "Something ancient stirs in the earth. The air thickens. Every cell in the body screams awake."
            SystemType.DUNGEON_DELVE -> "The ground gives way. Darkness swallows everything. Stone closes overhead like a throat."
            SystemType.ARCANE_ACADEMY -> "Words rearrange on every surface. Colors shift wrong. Knowledge floods in — too much, too fast."
            SystemType.TABLETOP_CLASSIC -> "A horn sounds from nowhere. The world sharpens. Colors become too vivid, edges too defined."
            SystemType.EPIC_JOURNEY -> "The stars shift. An ancient presence turns its attention. Destiny finds them like a blade."
            SystemType.HERO_AWAKENING -> "Power floods in — raw, electric, wrong. The body becomes a stranger. The world becomes fragile."
        }
        return ColdOpenDirective(sceneMoment, breakFlavor)
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }

    suspend fun narrateDeath(state: GameState, cause: String): String {
        val toneGuidance = when (state.systemType) {
            SystemType.DEATH_LOOP ->
                "Tone: Grim determination. Death is not the end—it's a lesson. Death count: ${state.deathCount + 1}"
            SystemType.DUNGEON_DELVE ->
                "Tone: Final, solemn. This is permadeath. The adventure ends here."
            else ->
                "Tone: Brief setback. You'll respawn soon."
        }

        val prompt = """
            ${state.playerName} (Level ${state.playerLevel}) dies. Cause: $cause. Location: ${state.currentLocation.name}.
            $toneGuidance
            1-2 sentences. The moment of failure. No action options.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    suspend fun narrateRespawn(state: GameState): String {
        val prompt = """
            ${state.playerName} respawns at ${state.currentLocation.name}. Death count: ${state.deathCount}.
            ${if (state.systemType == SystemType.DEATH_LOOP)
                "Each death makes them stronger. They remember what killed them."
            else
                "They return, diminished but alive."}
            1-2 sentences. No action options.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    private fun getGenreGuidance(systemType: SystemType): String = when (systemType) {
        SystemType.SYSTEM_INTEGRATION -> """
            GENRE: System Apocalypse (Defiance of the Fall, Primal Hunter)
            Reality has shattered. An alien System has integrated Earth.
            Tone: Visceral survival horror meets power fantasy.
            Sensory cues: The metallic taste of mana. The cold burn of System notifications.
        """.trimIndent()
        SystemType.CULTIVATION_PATH -> """
            GENRE: Xianxia Cultivation (Cradle, A Thousand Li)
            The Dao is infinite. Mortal flesh can become immortal through cultivation.
            Tone: Mystical, hierarchical, ancient.
            Sensory cues: Qi flowing like liquid fire through meridians. The pressure of a superior cultivator's aura.
        """.trimIndent()
        SystemType.DEATH_LOOP -> """
            GENRE: Roguelike Death Loop (Mother of Learning, Re:Zero)
            You've died before. Time resets but memories remain.
            Tone: Grim determination meets dark humor.
            Sensory cues: The sickening lurch of temporal reset. Déjà vu so strong it makes you nauseous.
        """.trimIndent()
        SystemType.DUNGEON_DELVE -> """
            GENRE: Classic Dungeon Crawl (Dungeon Crawler Carl)
            The dungeon is alive. It breathes. It hungers.
            Tone: Tense, tactical, unforgiving.
            Sensory cues: Torchlight flickering on ancient stone. The echo of something moving in the dark.
        """.trimIndent()
        SystemType.ARCANE_ACADEMY -> """
            GENRE: Magical Academy (Name of the Wind, Scholomance)
            Magic is real, and it can be learned—but the learning might kill you.
            Tone: Wonder mixed with danger.
            Sensory cues: The ozone smell of gathered power. Ink-stained fingers and sleepless nights.
        """.trimIndent()
        SystemType.TABLETOP_CLASSIC -> """
            GENRE: Classic Fantasy Adventure (D&D, Pathfinder)
            Dragons hoard gold. Dungeons hide treasure. Heroes rise from nothing.
            Tone: Heroic, adventurous, slightly larger than life.
            Sensory cues: The weight of a sword in your hand. Firelight on tavern walls.
        """.trimIndent()
        SystemType.EPIC_JOURNEY -> """
            GENRE: Epic Quest (Lord of the Rings, Wheel of Time)
            Destiny has chosen you. A great evil rises.
            Tone: Grand, sweeping, mythic.
            Sensory cues: Wind on endless roads. Starlight on ancient ruins.
        """.trimIndent()
        SystemType.HERO_AWAKENING -> """
            GENRE: Superhero/Power Awakening (Worm, Super Powereds)
            You were ordinary once. Then power flooded into you.
            Tone: Personal transformation meets world-shaking stakes.
            Sensory cues: Power crackling under your skin. The vertigo of sensing the world in new ways.
        """.trimIndent()
    }
}
