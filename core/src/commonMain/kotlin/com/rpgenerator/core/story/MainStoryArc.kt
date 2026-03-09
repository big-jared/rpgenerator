package com.rpgenerator.core.story

/**
 * The main story progression - hardcoded narrative beats that trigger at specific points.
 * These provide structure and direction to the player's journey.
 *
 * Structure:
 *   ACT 1: THE TUTORIAL (Levels 1-25, E-Grade) — Tutorial dimension with other integrated humans
 *   ACT 2: RETURN TO EARTH (Levels 25-40) — Incursion zones from other integrated worlds
 *   ACT 3: THE INCURSION WAR (Levels 40+) — Full-scale multiverse conflict
 */
internal object MainStoryArc {

    data class StoryBeat(
        val id: String,
        val title: String,
        val act: Int,
        val triggerLevel: Int,
        val triggerLocation: String?,
        val narration: String,
        val consequences: Map<String, String> = emptyMap()
    )

    data class MainQuest(
        val id: String,
        val title: String,
        val act: Int,
        val levelRange: IntRange,
        val description: String,
        val objectives: List<String>,
        val reward: String
    )

    // ════════════════════════════════════════════════════════════════
    // ACT 1: THE TUTORIAL (Levels 1-25, E-Grade)
    // The entire tutorial takes place within E-Grade (levels 1-25).
    // Milestones at levels 5, 10, 15, 20, 25 unlock new zones and events.
    // Grade advancement (E→D) happens at level 26 when the tutorial ends.
    // ════════════════════════════════════════════════════════════════

    val TUTORIAL_STORY_BEATS = listOf(
        StoryBeat(
            id = "beat_integration",
            title = "Integration",
            act = 1,
            triggerLevel = 1,
            triggerLocation = "void",
            narration = """
                Tuesday. You were in the middle of a completely ordinary day — and then the sky
                split open. White light. A sound like every frequency at once. Your body seized.
                The world dissolved.

                You wake in a featureless white void. No ground, no sky — just solid nothing
                beneath your feet and silence that has weight. A pillar of blue-white light
                pulses in front of you. Text scrolls across it in a script that shifts between
                languages you know and ones you've never seen.

                [INTEGRATION COMPLETE. SPECIES: HUMAN. DESIGNATION: PENDING.]
                [CLASS SELECTION MANDATORY. SELECT NOW.]

                No explanation. No greeting. Just a list of classes and a timer counting down.
                Whatever this is, it doesn't care if you're ready.
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_the_drop",
            title = "The Drop",
            act = 1,
            triggerLevel = 1,
            triggerLocation = "tutorial_crucible",
            narration = """
                The void shatters. You fall — not far, but hard. Real ground. Real air. A sky
                that's too purple and has two suns.

                You're in a massive stone courtyard surrounded by impossible architecture — towers
                that bend at wrong angles, bridges to nowhere, walls covered in glowing glyphs.
                And you're not alone. Dozens of people are picking themselves up off the ground,
                all wearing the same stunned expression. Some are crying. Some are already
                checking their status screens. One person is laughing, which is somehow the most
                unsettling reaction.

                A leaderboard materializes in the sky above the courtyard. Your name is on it.
                Everyone's name is on it. Right now, you're all tied at Level 1, E-Grade, zero
                Tutorial Points.

                That won't last.

                [TUTORIAL ZONE ACTIVE. PARTICIPANTS: 1,247. DURATION: INDEFINITE.]
                [OBJECTIVE: REACH LEVEL 25. COMPLETE THE TUTORIAL. SURVIVE.]
                [TUTORIAL POINTS AWARDED FOR: KILLS, BOSS CLEARS, ACHIEVEMENTS, MILESTONES.]
                [TUTORIAL POINTS REDEEMABLE AT CONCLUSION FOR EQUIPMENT, SKILLS, AND RESOURCES.]
                [BEGIN.]
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_level_5",
            title = "Level 5 — New Zones",
            act = 1,
            triggerLevel = 5,
            triggerLocation = null,
            narration = """
                [MILESTONE: LEVEL 5. NEW ZONES UNLOCKED. NEW BOSSES AVAILABLE.]

                The leaderboard updates. You've hit Level 5 — but looking at the numbers,
                40% of the participants haven't. Some never will.

                Level 5 opens the deeper regions: the Ashlands, the Drowned Shelf, and the
                monster nests that low-level participants learn to avoid. The Tutorial Point
                rewards scale up, but so does everything trying to kill you.

                You've also started to notice something: the people you arrived with are
                diverging. Some are forming groups, pooling resources, watching each other's
                backs. Others are going solo, climbing fast and hard, treating every other
                participant as competition. A few have started fighting each other for grinding
                spots.

                The System doesn't intervene. The System watches.
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_level_10",
            title = "Level 10 — The Culling",
            act = 1,
            triggerLevel = 10,
            triggerLocation = null,
            narration = """
                [MILESTONE: LEVEL 10. TUTORIAL EVENT TRIGGERED.]
                [SYSTEM EVENT: THE CULLING — Boss monsters have been released across all zones.
                Kill or be killed. Bonus TP for confirmed boss kills. Duration: 72 hours.]

                The System just changed the rules. Boss monsters are loose — not in their lairs
                waiting to be found, but actively hunting. Low-level participants are dying
                in droves. The leaderboard updates in real-time, and names are disappearing
                faster than you can read them.

                This is the first time the tutorial has felt truly dangerous. Not "hard
                encounter" dangerous — "the System is testing who deserves to keep going"
                dangerous.

                [PARTICIPANTS REMAINING: 847.]
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_level_15",
            title = "Level 15 — The Spire",
            act = 1,
            triggerLevel = 15,
            triggerLocation = null,
            narration = """
                [MILESTONE: LEVEL 15. THE SPIRE IS NOW ACCESSIBLE.]

                Level 15. The top 15% of surviving participants. The System starts treating you
                differently — notifications are more detailed, rewards are richer, and the
                leaderboard now shows your exact ranking instead of just a tier.

                The Spire has appeared at the center of the tutorial dimension. A tower of black
                stone and crystal that wasn't there yesterday, stretching impossibly high into
                the purple sky. The strongest participants are already heading toward it.

                The Spire is where the real bosses are. Floor bosses, each one harder than the
                last. The Tutorial Point rewards for clearing Spire floors are massive — but
                the death rate is worse.

                You also learn something unsettling: the tutorial doesn't have a time limit.
                It has a PARTICIPANT limit. When enough people hit Level 25, the tutorial ends
                for EVERYONE. Ready or not.

                [PARTICIPANTS REMAINING: 412.]
                [LEVEL 25 THRESHOLD: 0/50. TUTORIAL ENDS WHEN 50 PARTICIPANTS HIT LEVEL 25.]
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_level_20",
            title = "Level 20 — The Summit",
            act = 1,
            triggerLevel = 20,
            triggerLocation = null,
            narration = """
                [MILESTONE: LEVEL 20. TITLE AWARDED: "APEX CANDIDATE."]
                [SPIRE UPPER FLOORS UNLOCKED. FINAL BOSS: AVAILABLE.]

                Level 20. The elite. You can count on two hands the number of other participants
                at this level. The leaderboard has become personal — you know these names,
                you've fought alongside or against all of them.

                The System has started issuing you personal challenges. Hidden achievements.
                Unique skill trials. Things it doesn't offer to lower-level participants. It's
                investing in you now, and that investment feels like being watched through a
                microscope.

                The Spire's upper floors are a gauntlet. Each floor is a different environment —
                frozen wastes, volcanic chambers, reality-warped corridors where physics breaks.
                At the top: the Apex Boss. No one has beaten it yet.

                The Tutorial Point shop has also updated. Level 20 unlocks the premium tier:
                legendary equipment, rare skills, and one item simply labeled [REDACTED] that
                costs more TP than anyone has earned.

                [PARTICIPANTS REMAINING: 203.]
                [LEVEL 25 THRESHOLD: 12/50.]
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_level_25",
            title = "Level 25 — Tutorial Complete",
            act = 1,
            triggerLevel = 25,
            triggerLocation = null,
            narration = """
                [MILESTONE: LEVEL 25. E-GRADE COMPLETE.]
                [TITLE AWARDED: "INTEGRATION SURVIVOR."]
                [TUTORIAL PERFORMANCE RATING: CALCULATING...]

                Level 25. E-Grade complete. You've done it.

                The System's notification is longer than anything it's ever sent you. For the
                first time, it almost sounds... impressed. Almost.

                [TUTORIAL POINT TOTAL: {tp_total}. RANKING: {rank_position}/{total_participants}.]
                [TUTORIAL POINT EXCHANGE NOW OPEN. SELECT YOUR LOADOUT FOR REINTEGRATION.]
                [NOTE: All unspent Tutorial Points will be converted to base currency at a
                ratio of 10:1. Spend wisely. You will not have this opportunity again.]

                The shop unfolds before you — equipment, skills, resources, information packets
                about Earth's current state. Everything has a price. You can't afford everything.
                Choose what matters.

                When you're done, the sky cracks. The tutorial dimension is ending. You're going
                back to Earth. But Earth isn't what you left.

                [GRADE ADVANCEMENT PENDING: E-GRADE → D-GRADE.]
                [REINTEGRATION IN PROGRESS. DESTINATION: EARTH. STATUS: ACTIVE INCURSION ZONES
                DETECTED. 47 DIMENSIONAL RIFTS. 12 HOSTILE SPECIES. GOOD LUCK.]
            """.trimIndent()
        )
    )

    val ACT_1_MAIN_QUESTS = listOf(
        // Tutorial quest is created by GameOrchestrator.createTutorialQuest() as "quest_survive_tutorial"
        // Do NOT define a duplicate here.

        MainQuest(
            id = "quest_early_survival",
            title = "Early Survival",
            act = 1,
            levelRange = 1..5,
            description = "Learn the basics, find allies, and reach Level 5",
            objectives = listOf(
                "Reach Level 5",
                "Defeat a zone boss",
                "Earn your first achievement title",
                "Form or join a group"
            ),
            reward = "New zones unlocked, Greenreach Boss unlocked"
        ),

        MainQuest(
            id = "quest_proving_grounds",
            title = "Proving Grounds",
            act = 1,
            levelRange = 5..10,
            description = "Push deeper into the tutorial zones and prove you belong",
            objectives = listOf(
                "Reach Level 10",
                "Clear a zone boss",
                "Earn 500 Tutorial Points",
                "Survive the Culling event"
            ),
            reward = "Ashlands and Drowned Shelf access"
        ),

        MainQuest(
            id = "quest_the_climb",
            title = "The Climb",
            act = 1,
            levelRange = 10..20,
            description = "Fight through the upper levels and reach the Spire",
            objectives = listOf(
                "Reach Level 20",
                "Clear at least 3 Spire floors",
                "Earn a rare achievement title",
                "Accumulate 2,000 Tutorial Points"
            ),
            reward = "Spire Upper Floors unlocked, Premium shop tier"
        ),

        MainQuest(
            id = "quest_apex",
            title = "The Apex",
            act = 1,
            levelRange = 20..25,
            description = "Reach Level 25, defeat the Apex Boss, and prepare for reintegration",
            objectives = listOf(
                "Reach Level 25",
                "Defeat the Apex Boss at the top of the Spire",
                "Spend Tutorial Points on your reintegration loadout",
                "Complete the tutorial"
            ),
            reward = "Tutorial completion, E→D Grade advancement, Reintegration to Earth with purchased gear"
        )
    )

    // ════════════════════════════════════════════════════════════════
    // ACT 2: RETURN TO EARTH (D-Grade, Levels 26-100)
    // ════════════════════════════════════════════════════════════════

    val ACT_2_STORY_BEATS = listOf(
        StoryBeat(
            id = "beat_reintegration",
            title = "Reintegration",
            act = 2,
            triggerLevel = 26,
            triggerLocation = "earth_landing",
            narration = """
                The tutorial dimension collapses around you. Colors blur. Gravity inverts. Then —

                Real air. Real sunlight. Real ground beneath you.

                But Earth has changed. The sky has seams — visible cracks where dimensions
                bleed through. The air tastes like copper and something alien. Mana. You can
                feel it saturating everything.

                You're in what used to be a city. You recognize the bones of it — streets,
                buildings, a freeway overpass. But dimensional rifts have torn through it like
                wounds. Through the nearest rift, you can see another world: red sky, black
                sand, things moving. An Incursion Zone.

                Other tutorial survivors are appearing around you, blinking in the real sunlight.
                Some are people you fought beside. Some you fought against. All of you are
                armed with tutorial-bought gear and six months of combat experience.

                [REINTEGRATION COMPLETE. EARTH STATUS: COMPROMISED.]
                [47 ACTIVE INCURSION ZONES DETECTED. HOSTILE SPECIES: 12.]
                [LOCAL HUMAN POPULATION: SURVIVING. SCATTERED. UNINTEGRATED CIVILIANS PRESENT.]
                [PRIORITY: SECURE PERIMETER. CLEAR INCURSION ZONES. PROTECT SURVIVORS.]
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_first_incursion",
            title = "First Incursion",
            act = 2,
            triggerLevel = 40,
            triggerLocation = null,
            narration = """
                You've cleared your first Incursion Zone — a rift to a world of fungal forests
                and spore-creatures that dissolved anything organic. It took a team of eight
                and you lost two people. Real people. Not tutorial constructs.

                Closing the rift stabilized a twelve-block radius. Survivors emerged from
                basements and barricades — unintegrated civilians who spent the tutorial period
                just trying not to die. They look at you like you're either a savior or another
                threat. Fair enough.

                But here's what the System didn't tell you: the rifts aren't random. They're
                connected. Close one and the pressure shifts to others. Some rifts are getting
                STRONGER. And through a few of them, you've seen something worse than monsters:
                organized forces. Armies from worlds that completed their Integration long ago.

                Earth isn't just broken. Earth is being invaded.
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_not_alone",
            title = "We're Not Alone",
            act = 2,
            triggerLevel = 60,
            triggerLocation = null,
            narration = """
                Contact. Through an Incursion Zone that connects to a desert world, you've
                encountered your first alien integrated beings. Not monsters — people. A species
                the System calls "Keth" — tall, angular, skin like burnished copper. They've
                been integrated for three generations. Their lowest-ranked warriors are C-Grade.

                They're not here to invade. They're here because the rifts go both ways, and
                something is pushing through from THEIR world too. Something neither of you
                created.

                The Keth commander offers a choice: fight them for territory, or cooperate
                against whatever is driving the rifts open.

                [FIRST CONTACT PROTOCOL: NOT FOUND. The System does not mediate between
                integrated species. Negotiate or fight at your discretion.]
            """.trimIndent(),
            consequences = mapOf(
                "keth_contact" to "true",
                "alliance_or_war" to "player_choice"
            )
        ),

        StoryBeat(
            id = "beat_the_source",
            title = "The Source",
            act = 2,
            triggerLevel = 85,
            triggerLocation = null,
            narration = """
                You've been mapping the Incursion Zones. The rifts aren't random — they form
                a pattern. Concentric rings, converging on a single point. And at that point,
                the biggest rift of all: a tear in reality the size of a stadium, pulsing with
                energy that makes your D-Grade senses scream.

                Through it, you can see a world that isn't a world. It's a structure. Artificial.
                Built by something that treats dimensions like building materials. The System
                has a designation for it, but the designation is [RESTRICTED].

                Whatever is on the other side of that rift isn't just integrated. It's something
                that the System itself seems afraid of. And it's noticed Earth.

                [WARNING: MEGA-INCURSION EVENT PROBABILITY: 94.7%.]
                [ESTIMATED TIME TO CONVERGENCE: 30 DAYS.]
                [RECOMMENDATION: PREPARE.]
            """.trimIndent()
        )
    )

    val ACT_2_MAIN_QUESTS = listOf(
        MainQuest(
            id = "quest_reclaim_earth",
            title = "Reclamation",
            act = 2,
            levelRange = 26..50,
            description = "Return to Earth, find survivors, and start clearing Incursion Zones",
            objectives = listOf(
                "Establish a base of operations",
                "Clear your first Incursion Zone",
                "Rescue or recruit civilian survivors",
                "Reach Level 50"
            ),
            reward = "Settlement established, faction reputation, zone resources"
        ),

        MainQuest(
            id = "quest_close_the_rifts",
            title = "Rift Hunter",
            act = 2,
            levelRange = 50..100,
            description = "Close Incursion Zones, make contact with alien species, and find the source",
            objectives = listOf(
                "Clear 5 Incursion Zones",
                "Make contact with an alien integrated species",
                "Discover the rift convergence pattern",
                "Reach Level 100 and evolve to C-Grade"
            ),
            reward = "Alliance or territory, convergence intel, legendary equipment"
        )
    )

    // ════════════════════════════════════════════════════════════════
    // ACT 3: THE INCURSION WAR (C-Grade+, Levels 101+)
    // ════════════════════════════════════════════════════════════════

    val ACT_3_STORY_BEATS = listOf(
        StoryBeat(
            id = "beat_convergence",
            title = "Convergence",
            act = 3,
            triggerLevel = 101,
            triggerLocation = null,
            narration = """
                [SYSTEM ANNOUNCEMENT: CONVERGENCE EVENT INITIATED.]
                [ALL INTEGRATED SPECIES ON CONNECTED WORLDS: MOBILIZE OR PERISH.]

                The mega-rift tears open. Through it pours an army from the artificial
                world — beings that don't operate on the Integration system. They don't
                have levels. They don't have ranks. They have something older and worse.

                Every Incursion Zone on Earth activates simultaneously. It's not an invasion —
                it's a purge. And not just Earth: the Keth are under attack too, and every
                other integrated world connected by the rifts.

                The System, for the first time, seems to be on YOUR side. Notifications flood
                in: emergency rank-ups, temporary power boosts, unlocked skills you didn't
                know existed. The System is arming its investments.

                This is what Integration was for. This is why the System needed strong beings
                across thousands of worlds. Not for each other.

                For whatever is coming through that rift.
            """.trimIndent()
        ),

        StoryBeat(
            id = "beat_the_war",
            title = "The War Beyond",
            act = 3,
            triggerLevel = 150,
            triggerLocation = null,
            narration = """
                The war has been raging for weeks. Human settlements have fallen and been
                retaken. Allied species fight alongside you — Keth warriors, crystalline
                beings from a world of living glass, insectoid engineers who build fortifications
                in hours.

                You've learned what the System is: a weapon. Built by a civilization that lost
                their war against the rift-entities and created the Integration system as a
                last resort — a way to mass-produce warriors across every world they could
                reach. The original builders are long dead. The System runs on autopilot,
                integrating world after world, building armies for a war its creators already
                lost.

                But maybe that's the point. The builders lost because they fought alone.
                The System ensures no one fights alone.

                The mega-rift is getting larger. Whatever's on the other side is getting closer.
                The final battle is coming, and your rank — your power — your choices — will
                decide if Earth survives it.
            """.trimIndent()
        )
    )

    val ACT_3_MAIN_QUESTS = listOf(
        MainQuest(
            id = "quest_convergence",
            title = "Convergence",
            act = 3,
            levelRange = 101..200,
            description = "Face the mega-incursion and fight for Earth's survival",
            objectives = listOf(
                "Defend your settlement against the convergence assault",
                "Form alliances with other integrated species",
                "Push into the mega-rift",
                "Reach Level 200 and evolve to B-Grade"
            ),
            reward = "Legendary rank, multiverse reputation"
        )
    )

    // ════════════════════════════════════════════════════════════════
    // TUTORIAL ACHIEVEMENTS & TITLES
    // ════════════════════════════════════════════════════════════════

    data class Achievement(
        val id: String,
        val title: String,
        val description: String,
        val tpReward: Int,
        val rarity: String // "common", "uncommon", "rare", "epic", "legendary"
    )

    val TUTORIAL_ACHIEVEMENTS = listOf(
        Achievement("first_blood", "First Blood", "Kill your first monster", 10, "common"),
        Achievement("ten_kills", "Getting Started", "Kill 10 monsters", 25, "common"),
        Achievement("hundred_kills", "Centurion", "Kill 100 monsters", 100, "uncommon"),
        Achievement("thousand_kills", "Slaughter Machine", "Kill 1,000 monsters", 500, "rare"),
        Achievement("first_boss", "Boss Slayer", "Defeat your first boss", 50, "uncommon"),
        Achievement("all_zone_bosses", "Apex Predator", "Defeat all zone bosses", 500, "epic"),
        Achievement("spire_floor_1", "Spire Initiate", "Clear Spire Floor 1", 75, "uncommon"),
        Achievement("spire_floor_5", "Spire Veteran", "Clear Spire Floor 5", 200, "rare"),
        Achievement("spire_floor_10", "Spire Conqueror", "Clear Spire Floor 10", 500, "epic"),
        Achievement("apex_boss", "Apex", "Defeat the Apex Boss", 1000, "legendary"),
        Achievement("solo_boss", "Lone Wolf", "Defeat a boss solo", 150, "rare"),
        Achievement("group_clear", "Pack Hunter", "Clear a zone with a full group", 50, "uncommon"),
        Achievement("no_deaths", "Unbroken", "Reach Level 10 without dying", 300, "rare"),
        Achievement("speed_level_5", "Fast Learner", "Reach Level 5 in under 48 hours", 200, "rare"),
        Achievement("speed_level_25", "Tutorial Speedrun", "Complete the tutorial first among all participants", 2000, "legendary"),
        Achievement("hidden_area", "Explorer", "Discover a hidden area", 100, "uncommon"),
        Achievement("all_hidden", "Cartographer", "Discover all hidden areas", 750, "epic"),
        Achievement("pvp_victory", "Contested", "Win a PvP encounter", 50, "uncommon"),
        Achievement("rank_1", "Number One", "Hold the #1 leaderboard position", 500, "epic"),
        Achievement("save_another", "Guardian", "Save another participant from death", 75, "uncommon"),
        Achievement("crit_streak", "On Fire", "Land 5 critical hits in a row", 100, "rare")
    )

    // ════════════════════════════════════════════════════════════════
    // TUTORIAL POINT SHOP
    // ════════════════════════════════════════════════════════════════

    data class ShopItem(
        val id: String,
        val name: String,
        val description: String,
        val cost: Int,
        val levelRequired: Int,
        val category: String // "weapon", "armor", "skill", "consumable", "intel", "special"
    )

    val TUTORIAL_SHOP_CATEGORIES = listOf(
        "Weapons — from basic steel to enchanted blades",
        "Armor — protection scaled to your budget",
        "Skills — skill books that grant abilities instantly",
        "Consumables — potions, scrolls, and one-use items for the early days on Earth",
        "Intel — information about Earth's current state, rift locations, survivor settlements",
        "Special — items with unique effects, some with [REDACTED] descriptions"
    )

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    fun getStoryBeatForLevel(level: Int): StoryBeat? {
        val allBeats = TUTORIAL_STORY_BEATS + ACT_2_STORY_BEATS + ACT_3_STORY_BEATS
        return allBeats.firstOrNull { it.triggerLevel == level }
    }

    fun getMainQuestForLevel(level: Int): MainQuest? {
        val allQuests = ACT_1_MAIN_QUESTS + ACT_2_MAIN_QUESTS + ACT_3_MAIN_QUESTS
        return allQuests.firstOrNull { level in it.levelRange }
    }

    fun getStoryBeatsForAct(act: Int): List<StoryBeat> {
        return when (act) {
            1 -> TUTORIAL_STORY_BEATS
            2 -> ACT_2_STORY_BEATS
            3 -> ACT_3_STORY_BEATS
            else -> emptyList()
        }
    }

    fun getCurrentAct(level: Int): Int {
        return when {
            level <= 25 -> 1
            level <= 100 -> 2
            else -> 3
        }
    }
}
