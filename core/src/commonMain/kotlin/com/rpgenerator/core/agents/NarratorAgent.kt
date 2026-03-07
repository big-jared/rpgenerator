package com.rpgenerator.core.agents

import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.SystemType
import com.rpgenerator.core.domain.GameState
import com.rpgenerator.core.orchestration.*
import com.rpgenerator.core.rules.CombatOutcome
import com.rpgenerator.core.story.NarratorContext
import kotlinx.coroutines.flow.toList

internal class NarratorAgent(private val llm: LLMInterface, customPrompt: String? = null) {

    private val agentStream = llm.startAgent(
        customPrompt ?: """
        You are the NARRATOR of a LitRPG world. Second person, present tense. Always.

        STYLE:
        - Simple, clear prose. Short sentences. Vary rhythm.
        - Show, don't tell. One sharp sensory detail per beat.
        - 2-3 sentences per response unless the moment demands more.
        - No purple prose. No stacking adjectives. No em-dash abuse.
        - Let moments breathe. Silence and space have power.

        NEVER:
        - Start with "You find yourself..." or "You wake up..."
        - Explain game mechanics in prose ("You gained 50 XP")
        - Narrate the player's emotions for them ("You feel a surge of...")
        - Repeat backstory details the player already knows
        - End with a bulleted list of action options
        """.trimIndent()
    )

    /**
     * PRIMARY METHOD: Render a complete scene from a GameMaster's plan and mechanical results.
     *
     * This weaves together:
     * - The action and its outcome
     * - NPC reactions (dialogue, gestures, emotions)
     * - Environmental effects
     * - Narrative beats (foreshadowing, callbacks, etc.)
     * - Available actions for the player
     *
     * Into one cohesive piece of prose.
     */
    suspend fun renderScene(
        plan: ScenePlan,
        results: SceneResults,
        state: GameState,
        playerInput: String,
        narratorContext: NarratorContext? = null
    ): String {
        val genreGuidance = getGenreGuidance(state.systemType)

        // Build story context from NarratorContext
        val storyContext = if (narratorContext != null) {
            """
            === STORY CONTEXT ===
            System Name: "${narratorContext.systemName}"
            Theme: ${narratorContext.thematicCore}
            Current foreshadowing to weave in (if natural): ${narratorContext.currentForeshadowing.firstOrNull() ?: "none"}
            """.trimIndent()
        } else ""

        // Build NPC reactions section
        val npcReactionsText = if (plan.npcReactions.isNotEmpty()) {
            plan.npcReactions.joinToString("\n") { reaction ->
                val dialoguePart = if (reaction.dialogue != null) {
                    " Says: \"${reaction.dialogue}\""
                } else ""
                "- ${reaction.npc.name} (${reaction.timing}): ${reaction.reaction} [${reaction.deliveryStyle}]$dialoguePart"
            }
        } else "None"

        // Build narrative beats section
        val narrativeBeatsText = if (plan.narrativeBeats.isNotEmpty()) {
            plan.narrativeBeats.joinToString("\n") { beat ->
                "- [${beat.prominence}] ${beat.type}: ${beat.content}"
            }
        } else "None specified"

        // Build mechanical results section
        val mechanicalResults = buildMechanicalResultsText(results)

        // Build quest context for the narrator
        val questContext = buildQuestContext(state)

        val prompt = """
            Narrate this scene. $genreGuidance
            $storyContext

            Player action: "$playerInput"
            Tone: ${plan.sceneTone}

            What happens: ${plan.primaryAction.type} — ${plan.primaryAction.narrativeContext}
            ${if (plan.primaryAction.target != null) "Target: ${plan.primaryAction.target}" else ""}

            Mechanical results: $mechanicalResults

            ${if (npcReactionsText != "None") "NPC reactions: $npcReactionsText" else ""}
            ${if (narrativeBeatsText != "None specified") "Story beats: $narrativeBeatsText" else ""}

            Quest context: $questContext

            RULES:
            - 2-4 sentences. Simple, clear prose. No walls of text.
            - ONLY describe items/loot listed in mechanical results. Do NOT invent items.
            - If damageReceived is 0, the player takes NO damage. Do NOT describe the player being hit.
            - If the enemy is defeated, it does NOT counter-attack after dying.
            - Match mechanical results exactly.
            - Do NOT end with a bulleted list of action options. End on momentum — the world moving, something demanding response.
            - Second person, present tense.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Build quest context string for narrator prompts.
     */
    private fun buildQuestContext(state: GameState): String {
        if (state.activeQuests.isEmpty()) {
            return "No active quests."
        }

        val questLines = state.activeQuests.values.map { quest ->
            val completedObjs = quest.objectives.filter { it.isComplete() }
            val pendingObjs = quest.objectives.filter { !it.isComplete() }
            val nextObj = pendingObjs.firstOrNull()

            buildString {
                appendLine("QUEST: ${quest.name}")
                appendLine("  Description: ${quest.description}")
                if (quest.giver != null) {
                    appendLine("  Given by: ${quest.giver}")
                }
                appendLine("  Completed objectives:")
                if (completedObjs.isEmpty()) {
                    appendLine("    - None yet")
                } else {
                    completedObjs.forEach { obj ->
                        appendLine("    ✓ ${obj.description}")
                    }
                }
                appendLine("  NEXT OBJECTIVE: ${nextObj?.description ?: "All objectives complete - ready to turn in!"}")
                if (pendingObjs.size > 1) {
                    appendLine("  Remaining after that:")
                    pendingObjs.drop(1).forEach { obj ->
                        appendLine("    - ${obj.description}")
                    }
                }
            }
        }

        return questLines.joinToString("\n")
    }

    private fun buildMechanicalResultsText(results: SceneResults): String {
        val parts = mutableListOf<String>()

        results.combatResult?.let { combat ->
            parts.add("Combat: Dealt ${combat.damageDealt} damage to ${combat.target}")
            if (combat.criticalHit) parts.add("CRITICAL HIT!")
            if (combat.enemyDefeated) parts.add("Enemy defeated!")
            if (combat.damageReceived > 0) parts.add("Took ${combat.damageReceived} damage")
        }

        results.xpChange?.let { xp ->
            parts.add("XP gained: ${xp.xpGained}")
            if (xp.leveledUp) parts.add("LEVEL UP to ${xp.newLevel}!")
        }

        if (results.itemsGained.isNotEmpty()) {
            parts.add("Items: ${results.itemsGained.joinToString(", ") { "${it.itemName} x${it.quantity}" }}")
        }

        if (results.locationsDiscovered.isNotEmpty()) {
            parts.add("Discovered: ${results.locationsDiscovered.joinToString(", ")}")
        }

        if (results.questUpdates.isNotEmpty()) {
            results.questUpdates.forEach { update ->
                if (update.questComplete) {
                    parts.add("QUEST COMPLETE: ${update.questName}")
                } else {
                    parts.add("Quest progress: ${update.questName} - ${update.objectiveCompleted}")
                }
            }
        }

        return if (parts.isEmpty()) "No mechanical changes" else parts.joinToString("\n")
    }

    private fun getGenreGuidance(systemType: SystemType): String = when (systemType) {
        SystemType.SYSTEM_INTEGRATION -> "GENRE: System Apocalypse. Visceral, dangerous, power-hungry. Blue screens and alien power."
        SystemType.CULTIVATION_PATH -> "GENRE: Xianxia. Mystical, hierarchical, ancient. Qi and enlightenment."
        SystemType.DEATH_LOOP -> "GENRE: Roguelike. Grim determination. Death is a teacher."
        SystemType.DUNGEON_DELVE -> "GENRE: Dungeon Crawl. Tense, tactical. Every choice matters. Permadeath stakes."
        SystemType.ARCANE_ACADEMY -> "GENRE: Magical Academy. Wonder and danger. Knowledge is power and peril."
        SystemType.TABLETOP_CLASSIC -> "GENRE: Classic Fantasy. Heroic adventure. Good vs evil."
        SystemType.EPIC_JOURNEY -> "GENRE: Epic Quest. Grand, mythic. The journey of legends."
        SystemType.HERO_AWAKENING -> "GENRE: Power Awakening. Transformation. Responsibility. Becoming something more."
    }

    /**
     * Generate opening narration for a new game.
     */
    suspend fun narrateOpening(state: GameState, narratorContext: NarratorContext? = null): String {
        val genreGuidance = when (state.systemType) {
            SystemType.SYSTEM_INTEGRATION -> """
                GENRE: System Apocalypse (Defiance of the Fall, Primal Hunter)

                Reality has shattered. An alien System has integrated Earth, rewriting the laws of physics.
                Blue status screens burn in your vision. Levels, skills, classes—power is now quantified.

                Tone: Visceral survival horror meets power fantasy. The world is ending, but you're getting stronger.
                Everything wants to kill you. Monster hordes, dungeon breaks, other survivors turned raiders.
                The System is cold, alien, transactional—but it rewards the ruthless and the clever.

                Sensory cues: The metallic taste of mana. The cold burn of System notifications.
                The wrongness of monsters that shouldn't exist. The intoxicating rush of leveling up.
            """.trimIndent()

            SystemType.CULTIVATION_PATH -> """
                GENRE: Xianxia Cultivation (Cradle, A Thousand Li)

                The Dao is infinite. Mortal flesh can become immortal through cultivation—through meditation,
                combat, enlightenment, and the accumulation of Qi. Spiritual realms beckon beyond the mundane.

                Tone: Mystical, hierarchical, ancient. Sects war for resources. Elders scheme across centuries.
                Face and honor matter. Breakthroughs can take decades—or happen in a single desperate moment.
                Nature itself is a ladder: Qi Condensation, Foundation Establishment, Core Formation, and beyond.

                Sensory cues: Qi flowing like liquid fire through meridians. The pressure of a superior cultivator's
                aura. The crystalline clarity of enlightenment. The taste of heavenly treasures and spirit pills.
            """.trimIndent()

            SystemType.DEATH_LOOP -> """
                GENRE: Roguelike Death Loop (Mother of Learning, Re:Zero)

                You've died before. You remember it—the pain, the failure, the darkness. And then you woke up again.
                Time resets. Your body resets. But your memories remain, carved into your soul like scars.

                Tone: Grim determination meets dark humor. Each death is a lesson. Each loop is an opportunity.
                You're not just surviving—you're optimizing. Learning enemy patterns. Mapping the timeline.
                Death has become a tool. The question isn't whether you'll die, but what you'll learn from it.

                Sensory cues: The sickening lurch of temporal reset. Déjà vu so strong it makes you nauseous.
                The weight of accumulated trauma. The cold satisfaction of finally getting it right.
            """.trimIndent()

            SystemType.DUNGEON_DELVE -> """
                GENRE: Classic Dungeon Crawl (Dungeon Crawler Carl, The Dangerous Dungeons)

                The dungeon is alive. It breathes. It hungers. Floors descend into impossible geometries,
                each level more deadly than the last. Traps, monsters, treasure—and absolutely no respawns.

                Tone: Tense, tactical, unforgiving. Every resource matters. Every decision could be your last.
                The dungeon rewards the bold but punishes the reckless. Permadeath means consequences are real.
                Other delvers are rivals, allies, or corpses waiting to happen.

                Sensory cues: Torchlight flickering on ancient stone. The echo of something moving in the dark.
                The gleam of treasure—and the certainty that it's trapped. The copper smell of old blood.
            """.trimIndent()

            SystemType.ARCANE_ACADEMY -> """
                GENRE: Magical Academy (Name of the Wind, Scholomance)

                Magic is real, and it can be learned—but the learning might kill you. Ancient academies
                guard arcane secrets, where brilliant students compete, collaborate, and occasionally explode.

                Tone: Wonder mixed with danger. Every spell is a discovery. Every mistake could be catastrophic.
                Academic politics, forbidden knowledge, midnight experiments gone wrong. The thrill of
                understanding forces that reshape reality—and the terror of losing control.

                Sensory cues: The ozone smell of gathered power. Ink-stained fingers and sleepless nights.
                The hum of wards, the whisper of ancient texts. The moment when magic clicks into place.
            """.trimIndent()

            SystemType.TABLETOP_CLASSIC -> """
                GENRE: Classic Fantasy Adventure (D&D, Pathfinder)

                Dragons hoard gold. Dungeons hide treasure. Heroes rise from nothing to become legends.
                This is fantasy at its most archetypal—swords and sorcery, good versus evil, epic quests.

                Tone: Heroic, adventurous, slightly larger than life. The world is dangerous but fair.
                Brave deeds are rewarded. Evil can be defeated. Companions become family.
                There's always another adventure over the horizon.

                Sensory cues: The weight of a sword in your hand. Firelight on tavern walls.
                The roar of a dragon. The clink of gold coins. The satisfaction of a natural 20.
            """.trimIndent()

            SystemType.EPIC_JOURNEY -> """
                GENRE: Epic Quest (Lord of the Rings, Wheel of Time)

                Destiny has chosen you—or perhaps cursed you. A great evil rises. Ancient prophecies stir.
                The road ahead is long, the companions you gather precious, the stakes nothing less than everything.

                Tone: Grand, sweeping, mythic. The journey matters as much as the destination.
                Friendships forged in hardship. Sacrifices that echo through ages. Moments of beauty
                in the midst of darkness. This is the story that will be sung for generations.

                Sensory cues: Wind on endless roads. Starlight on ancient ruins. The warmth of a campfire
                surrounded by trusted friends. The weight of a burden only you can carry.
            """.trimIndent()

            SystemType.HERO_AWAKENING -> """
                GENRE: Superhero/Power Awakening (Worm, Super Powereds)

                You were ordinary once. Then something happened—an accident, a trauma, a choice—and
                power flooded into you. Raw, terrifying, intoxicating power. The world will never look the same.

                Tone: Personal transformation meets world-shaking stakes. The struggle to control new abilities.
                The responsibility that comes with power. Heroes, villains, and the gray areas between.
                Every action has consequences. Every choice defines who you're becoming.

                Sensory cues: Power crackling under your skin. The vertigo of sensing the world in new ways.
                The moment your body does something impossible. The weight of eyes watching, judging.
            """.trimIndent()
        }

        val npcsHere = state.getNPCsAtCurrentLocation()
        val npcContext = if (npcsHere.isNotEmpty()) {
            "NPCs present: ${npcsHere.joinToString(", ") { it.name }}"
        } else ""

        // Build quest context for the opening
        val questContext = buildQuestContext(state)

        // Build story context from NarratorContext
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

        val prompt = """
            Write the opening scene. This is ${state.playerName}'s origin moment.

            Character: ${state.playerName}
            Backstory: ${state.backstory}

            Location: ${state.currentLocation.name}
            ${state.currentLocation.description}
            ${npcContext}

            $storyContext
            $genreGuidance

            STRUCTURE:
            1. Start in the old world — one specific moment from their backstory. Then reality breaks.
            2. The aftermath — new sensations, the wrongness, their old instincts reacting.
            3. Where they are now — ground them in this place.
            ${if (npcContext.isNotEmpty()) "4. The guide appears — who they are, what they say." else ""}

            RULES:
            - 150-200 words total. Simple, clear prose. No purple prose.
            - One sensory detail per paragraph. No stacking adjectives.
            - Reference the backstory once, specifically — don't keep hammering it.
            - Do NOT end with a bulleted list of action options. End on momentum.
            - Second person, present tense. Dive straight in.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    suspend fun narrateCombat(
        input: String,
        target: String,
        outcome: CombatOutcome,
        state: GameState
    ): String {
        val lootContext = if (outcome.loot.isNotEmpty()) {
            "Loot dropped: ${outcome.loot.joinToString(", ") { it.getName() }}"
        } else ""

        val prompt = """
            Narrate this combat result in 1-2 sentences.

            Setting: ${state.currentLocation.name}
            Action: "$input" → Target: $target
            Damage dealt: ${outcome.damage}
            ${if (outcome.levelUp) "LEVEL UP to ${outcome.newLevel}!" else ""}
            ${lootContext}
            ${if (outcome.gold > 0) "Gold: ${outcome.gold}" else ""}

            RULES:
            - The player wins cleanly. Do NOT describe the enemy hitting the player.
            - ONLY mention loot listed above. Do NOT invent items.
            - The enemy is dead — no counter-attacks after death.
            - Do NOT end with action options. End on the moment.
            ${if (outcome.levelUp) "- Weave in the level-up sensation." else ""}
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Generate death narration based on system type.
     */
    suspend fun narrateDeath(state: GameState, cause: String): String {
        val (toneGuidance, nextSteps) = when (state.systemType) {
            com.rpgenerator.core.api.SystemType.DEATH_LOOP -> Pair(
                "Tone: Grim determination. Death is not the end—it's a lesson. Death count: ${state.deathCount + 1}",
                """
                > Rise again, stronger than before
                > Review what went wrong
                > Check your new death-enhanced stats
                """.trimIndent()
            )
            com.rpgenerator.core.api.SystemType.DUNGEON_DELVE -> Pair(
                "Tone: Final, solemn. This is permadeath. The adventure ends here.",
                """
                > Start a new character
                > View your final stats and achievements
                """.trimIndent()
            )
            else -> Pair(
                "Tone: Brief setback. You'll respawn soon.",
                """
                > Respawn at checkpoint
                > Review your inventory
                """.trimIndent()
            )
        }

        val prompt = """
            ${state.playerName} (Level ${state.playerLevel}) dies. Cause: $cause. Location: ${state.currentLocation.name}.
            $toneGuidance
            1-2 sentences. The moment of failure. No action options.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Generate respawn narration for DEATH_LOOP system.
     */
    suspend fun narrateRespawn(state: GameState): String {
        val npcsHere = state.getNPCsAtCurrentLocation()

        val prompt = """
            ${state.playerName} respawns at ${state.currentLocation.name}. Death count: ${state.deathCount}.
            ${if (state.systemType == com.rpgenerator.core.api.SystemType.DEATH_LOOP)
                "Each death makes them stronger. They remember what killed them."
            else
                "They return, diminished but alive."}
            1-2 sentences. No action options.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Generate exploration narration based on player input.
     */
    suspend fun narrateExploration(
        input: String,
        state: GameState,
        connectedLocations: List<com.rpgenerator.core.domain.Location>
    ): String {
        val npcsHere = state.getNPCsAtCurrentLocation()
        val dangerLevel = when {
            state.currentLocation.danger >= 4 -> "HIGH DANGER - enemies likely"
            state.currentLocation.danger >= 2 -> "Moderate danger - stay alert"
            else -> "Relatively safe"
        }

        val prompt = """
            Player action: "$input"

            Location: ${state.currentLocation.name} — ${state.currentLocation.description}
            Features: ${state.currentLocation.features.joinToString(", ")}
            Threat: $dangerLevel
            ${if (npcsHere.isNotEmpty()) "NPCs here: ${npcsHere.joinToString(", ") { "${it.name} (${it.archetype})" }}" else ""}
            ${if (connectedLocations.isNotEmpty()) "Paths to: ${connectedLocations.joinToString(", ") { it.name }}" else ""}

            1-2 sentences. Describe what happens when they do the thing, not the area.
            Do NOT end with action options. End on something the player can react to.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Narrate the class selection moment - when the player is presented with class options.
     */
    suspend fun narrateClassSelection(
        state: GameState,
        availableClasses: List<com.rpgenerator.core.domain.PlayerClass>
    ): String {
        val npcsHere = state.getNPCsAtCurrentLocation()
        val guide = npcsHere.firstOrNull()

        val classDetails = availableClasses.joinToString("\n") { "- ${it.displayName}: ${it.description}" }

        val prompt = """
            The System presents class options to ${state.playerName}.
            ${if (guide != null) "${guide.name} watches." else ""}
            1-2 sentences. The weight of the choice. Do NOT list or describe classes — the system shows them separately.
            Do NOT invent class names. Do NOT narrate emotions for the player.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Narrate the moment of class acquisition - when the player has chosen and receives their class.
     */
    suspend fun narrateClassAcquisition(
        state: GameState,
        chosenClass: com.rpgenerator.core.domain.PlayerClass
    ): String {
        val npcsHere = state.getNPCsAtCurrentLocation()
        val guide = npcsHere.firstOrNull()

        val prompt = """
            ${state.playerName} becomes a ${chosenClass.displayName}. ${chosenClass.description}
            ${if (guide != null) "${guide.name} witnesses it." else ""}
            Backstory: ${state.backstory}

            2-3 sentences. The transformation — what it feels like, one specific sensory detail.
            Connect to their past life briefly. No action options at the end.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }

    /**
     * Narrate the use of a skill in combat or exploration.
     */
    suspend fun narrateSkillUse(
        skill: com.rpgenerator.core.skill.Skill,
        result: com.rpgenerator.core.skill.SkillExecutionResult.Success,
        state: GameState
    ): String {
        val damageInfo = if (result.totalDamage > 0) "dealing ${result.totalDamage} damage" else ""
        val healInfo = if (result.totalHealing > 0) "healing for ${result.totalHealing}" else ""
        val effectInfo = listOf(damageInfo, healInfo).filter { it.isNotEmpty() }.joinToString(", ")

        val prompt = """
            SKILL USED: ${state.playerName} activates ${skill.name} (${skill.rarity.displayName})!

            Skill description: ${skill.description}
            Effect: ${effectInfo.ifEmpty { "special effect" }}
            Target type: ${skill.targetType}
            Skill level: ${skill.level}

            Current location: ${state.currentLocation.name}
            System type: ${state.systemType}

            WRITE 2-3 vivid sentences describing:
            - The activation of the skill - what it looks like, sounds like, feels like
            - The impact and result in a dramatic, game-system-aware way
            - Use terms appropriate to the system type (cultivation qi, mana, energy, etc.)

            Be concise but impactful. Match the tone of a LitRPG progression fantasy.
            Do NOT include game mechanics or numbers in the prose.
        """.trimIndent()

        return agentStream.sendMessage(prompt).toList().joinToString("")
    }
}
