package com.rpgenerator.core.story

/**
 * Hardcoded zone progression defining the world structure.
 * Zones unlock based on level/rank and story progression.
 *
 * Tutorial Dimension: The Crucible and its surrounding zones (Levels 1-25)
 * Earth (Post-Tutorial): Incursion Zones and human settlements (Levels 25+)
 */
internal object ZoneProgression {

    data class ZoneDefinition(
        val id: String,
        val name: String,
        val levelRange: IntRange,
        val description: String,
        val theme: String,
        val dangers: List<String>,
        val resources: List<String>,
        val connectedZones: List<String> = emptyList()
    )

    data class BossDefinition(
        val id: String,
        val name: String,
        val zone: String,
        val level: Int,
        val description: String,
        val mechanics: String,
        val rewards: String
    )

    // ════════════════════════════════════════════════════════════════
    // TUTORIAL DIMENSION — Zones (Levels 1-25)
    // ════════════════════════════════════════════════════════════════

    val CRUCIBLE = ZoneDefinition(
        id = "tutorial_crucible",
        name = "The Crucible",
        levelRange = 1..25,
        description = """
            The central hub of the tutorial dimension. A massive stone courtyard surrounded
            by impossible architecture — towers that bend at wrong angles, bridges connecting
            nothing to nothing, walls of dark stone covered in glowing glyphs that pulse in
            time with your heartbeat. The sky is purple with two pale suns that never set.

            Safe Zone protections are active here. No combat. This is where participants
            gather to trade, form groups, check the leaderboard, and access the Tutorial
            Point shop (which updates as your rank increases). A giant crystalline leaderboard
            floats above the courtyard, showing every participant's rank, level, and TP total
            in real time.

            It's crowded, loud, and tense. People are making deals, comparing builds, mourning
            friends who didn't make it back from the latest expedition. A few people are
            studying the glyphs on the walls — they don't translate to anything useful, but
            they change when no one's looking.
        """.trimIndent(),
        theme = "Hub, safe zone, social dynamics, leaderboard pressure",
        dangers = listOf(
            "None — Safe Zone protections active",
            "Social: betrayal, bad deals, misinformation",
            "The leaderboard pressure itself"
        ),
        resources = listOf(
            "Tutorial Point shop (rank-gated tiers)",
            "Leaderboard and achievement tracking",
            "Group formation and party finder",
            "Rumors and intel from other participants"
        ),
        connectedZones = listOf("tutorial_greenreach", "tutorial_ashlands", "tutorial_drowned_shelf", "tutorial_spire")
    )

    val GREENREACH = ZoneDefinition(
        id = "tutorial_greenreach",
        name = "The Greenreach",
        levelRange = 1..8,
        description = """
            The starter zone — a vast forest of trees with bark like polished bone and leaves
            that glow faintly blue. The canopy is so thick that the twin suns filter through
            as shifting patterns on the ground. Clearings hold monster spawns that reset daily.

            It looks beautiful and almost peaceful until something with too many legs drops
            from the canopy. The Greenreach is designed to teach you that pretty things in
            the System's world are often the most dangerous.

            Other participants are everywhere in the lower sections, grinding low-level monsters
            and racing for the Greenreach Boss. Higher sections thin out — the monsters get
            nastier and the TP rewards get better.
        """.trimIndent(),
        theme = "Starter zone, deceptive beauty, learning the rules",
        dangers = listOf(
            "Bone Crawlers — spider-like creatures with camouflage (Level 1-3)",
            "Canopy Stalkers — ambush predators that drop from above (Level 3-5)",
            "Thornbacks — armored beasts that charge (Level 5-8)",
            "Greenreach Alpha — zone boss (Level 8)"
        ),
        resources = listOf(
            "Luminous Sap — basic alchemy ingredient",
            "Bone Bark — crafting material for early weapons",
            "Hidden clearings with bonus TP caches",
            "Skill Stones embedded in ancient trees (rare)"
        ),
        connectedZones = listOf("tutorial_crucible", "tutorial_ashlands")
    )

    val ASHLANDS = ZoneDefinition(
        id = "tutorial_ashlands",
        name = "The Ashlands",
        levelRange = 5..15,
        description = """
            A volcanic wasteland where the ground cracks with orange heat and geysers of
            mana-saturated steam erupt without warning. Rivers of slow-moving lava carve
            the landscape into isolated plateaus connected by stone bridges that may or may
            not be stable.

            The monsters here are adapted to extreme heat — obsidian-shelled beetles the
            size of cars, serpents made of living magma, and the Emberwraiths that appear
            as heat shimmer until they're close enough to burn. Mid-level participants come
            here to push their limits. Many don't come back.

            The Ashlands Boss lives in a caldera at the zone's center. You can see the glow
            from the Crucible on clear days.
        """.trimIndent(),
        theme = "Hostile environment, heat and fire, environmental hazards",
        dangers = listOf(
            "Slag Beetles — armored, slow, devastating charge attacks (Level 5-8)",
            "Magma Serpents — semi-liquid, immune to fire damage (Level 7-11)",
            "Emberwraiths — invisible until they attack, fire damage over time (Level 9-13)",
            "Caldera Tyrant — zone boss, living volcano (Level 13)",
            "Environmental: geyser eruptions, unstable ground, heat exhaustion"
        ),
        resources = listOf(
            "Ember Cores — fire-aspected crafting materials",
            "Obsidian Shards — weapon reinforcement",
            "Lava Blooms — rare alchemy flowers that grow on cooling lava (valuable TP trade)",
            "Thermal Vents — resting near them grants temporary fire resistance"
        ),
        connectedZones = listOf("tutorial_crucible", "tutorial_greenreach", "tutorial_drowned_shelf")
    )

    val DROWNED_SHELF = ZoneDefinition(
        id = "tutorial_drowned_shelf",
        name = "The Drowned Shelf",
        levelRange = 8..18,
        description = """
            An underwater continental shelf exposed by some cataclysm — or designed that way.
            Ancient coral formations tower like buildings, bioluminescent algae paints
            everything in shifting blues and greens, and the water level rises and falls on
            a six-hour cycle. When the tide is out, you explore the exposed seabed. When the
            tide comes in, you'd better be on high ground or able to swim.

            The creatures here are the worst kind: fast, smart, and adapted to both water
            and land. The Abyssal Reachers — tentacled horrors that hunt from tidal pools —
            are responsible for more participant deaths than any other non-boss monster in
            the tutorial.

            The zone boss dwells in the deepest trench, only accessible during high tide.
            Fighting underwater is a skill most participants never develop. Those who do
            earn some of the best TP rewards in the tutorial.
        """.trimIndent(),
        theme = "Tidal cycles, underwater horror, environmental mastery",
        dangers = listOf(
            "Reef Razors — fast, sharp-edged fish that swarm (Level 8-11)",
            "Abyssal Reachers — tentacled ambush predators in tidal pools (Level 10-14)",
            "Tidewalkers — humanoid crustaceans that coordinate attacks (Level 12-16)",
            "The Leviathan's Maw — zone boss, deep trench predator (Level 16)",
            "Environmental: rising tides, underwater combat penalties, visibility loss"
        ),
        resources = listOf(
            "Pearl Cores — water-aspected crafting materials",
            "Coral Plate — natural armor material",
            "Bioluminescent Extract — night vision consumable",
            "Drowned Caches — pre-placed treasure chests on the seabed (require high-tide diving)"
        ),
        connectedZones = listOf("tutorial_crucible", "tutorial_ashlands", "tutorial_spire")
    )

    val SPIRE = ZoneDefinition(
        id = "tutorial_spire",
        name = "The Spire",
        levelRange = 15..25,
        description = """
            A tower of black stone and crystal that appeared when the first participant hit
            Level 15. It stretches impossibly high into the purple sky — some say it has no
            top, but that's not true. It has ten floors, and at the top sits the Apex Boss.

            Each floor is a self-contained environment — a pocket dimension within the
            tutorial dimension. Floor 1 is a frozen labyrinth. Floor 5 is a gravity-warped
            arena where up and down trade places mid-fight. Floor 10 is just a platform
            in an endless void with the Apex Boss waiting.

            The Spire is where the tutorial is completed. TP rewards for floor clears are
            massive. The death rate is worse. Groups are recommended but not required — a
            few solo legends have cleared floors alone and earned titles for it.

            [SPIRE ACCESS: LEVEL 15 MINIMUM. UPPER FLOORS (6-10): LEVEL 20 MINIMUM.]
        """.trimIndent(),
        theme = "Endgame tower, escalating challenge, the final test",
        dangers = listOf(
            "Floor Guardians — unique bosses per floor, Level 15-24",
            "Environmental hazards change per floor (ice, gravity, darkness, time distortion)",
            "Floor 7: 'The Crucible Mirror' — you fight a System-generated copy of yourself",
            "Floor 10: The Apex Boss (Level 25) — the tutorial's final challenge",
            "Falling damage is real. The Spire is vertical."
        ),
        resources = listOf(
            "Floor clear rewards: 200-1000 TP per floor",
            "Floor boss drops: rare and epic equipment",
            "Hidden rooms on floors 3, 6, and 9 with bonus caches",
            "Title achievements for solo clears, speed clears, and no-damage clears",
            "The Apex Boss drops: legendary equipment + 'Apex' title"
        ),
        connectedZones = listOf("tutorial_crucible")
    )

    // ════════════════════════════════════════════════════════════════
    // TUTORIAL BOSSES
    // ════════════════════════════════════════════════════════════════

    val TUTORIAL_BOSSES = listOf(
        BossDefinition(
            id = "boss_greenreach_alpha",
            name = "The Hollow Matriarch",
            zone = "tutorial_greenreach",
            level = 8,
            description = "A massive spider-creature nesting in the oldest tree in the Greenreach. Her body is made of the same bone-bark as the trees — she's been here so long the forest grew around her. When she moves, the canopy moves with her.",
            mechanics = "Summons Bone Crawlers endlessly. Web traps restrict movement. Phase 2: she detaches from the tree and becomes mobile. Phase 3: the tree itself attacks.",
            rewards = "200 TP, Matriarch's Fang (rare weapon), 'Greenreach Champion' title"
        ),
        BossDefinition(
            id = "boss_ashlands_tyrant",
            name = "Caldera Tyrant",
            zone = "tutorial_ashlands",
            level = 13,
            description = "A creature of living stone and magma that dwells in the central caldera. When it roars, the ground splits and lava fountains erupt. It's slow, but every hit is devastating and it regenerates by consuming the lava around it.",
            mechanics = "Arena is a caldera — lava rises during the fight. Must destroy lava vents to stop regeneration. Throws magma boulders at range. Ground-pound creates shockwaves. Enrages below 25% HP.",
            rewards = "350 TP, Tyrant's Core (epic crafting material), 'Ashlands Conqueror' title"
        ),
        BossDefinition(
            id = "boss_drowned_leviathan",
            name = "The Leviathan's Maw",
            zone = "tutorial_drowned_shelf",
            level = 16,
            description = "Not a creature with a mouth — the entire trench IS the mouth. A colossal organism that lines the walls of the deepest trench, its 'teeth' are coral formations that close when prey enters. Fighting it means fighting the environment itself while underwater.",
            mechanics = "Underwater fight. The trench closes periodically — must find air pockets. Tentacles attack from walls. Must destroy nerve clusters to open the 'jaw' and reach the core. Current pulls toward the gullet.",
            rewards = "500 TP, Leviathan Scale (epic armor material), 'Depths Walker' title"
        ),
        BossDefinition(
            id = "boss_spire_apex",
            name = "The Apex",
            zone = "tutorial_spire",
            level = 25,
            description = "A humanoid figure made of the same crystal as the Spire itself. It doesn't speak. It doesn't have a face. But it fights like it's studied every participant who ever entered the tutorial — because it has. It adapts to your class, your skills, your patterns. The longer the fight goes, the more it learns.",
            mechanics = "Adapts to player's combat style — switches counters every 20% HP. Phase 1: mirrors your class abilities. Phase 2: combines abilities from multiple classes. Phase 3: uses abilities you've never seen. No adds, no gimmicks, just a perfect opponent that gets smarter. Group size affects its power scaling.",
            rewards = "1000 TP, Apex Crystal (legendary material), 'Apex' title, tutorial completion"
        )
    )

    // ════════════════════════════════════════════════════════════════
    // HIDDEN AREAS (Tutorial)
    // ════════════════════════════════════════════════════════════════

    val HIDDEN_AREAS = listOf(
        ZoneDefinition(
            id = "tutorial_hollow",
            name = "The Hollow",
            levelRange = 5..15,
            description = """
                A cave system beneath the Greenreach, accessed through a gap in the roots of
                the Matriarch's tree (only visible after the boss is killed). Inside: mushroom
                forests that glow in impossible colors, pools of liquid mana, and a merchant
                who shouldn't exist.

                The merchant is a construct — or something pretending to be one. It sells items
                not available in the Crucible shop, accepts unusual payment (monster trophies,
                secrets, "interesting stories"), and occasionally offers quests that aren't on
                any official list.
            """.trimIndent(),
            theme = "Hidden merchant, unique items, secret quests",
            dangers = listOf(
                "Spore Lurkers — fungal creatures that release hallucinogenic clouds (Level 6-10)",
                "The merchant's prices are steep and sometimes... strange"
            ),
            resources = listOf(
                "Unique items not available elsewhere",
                "Liquid Mana — stat boost consumable",
                "Secret quest access",
                "'Explorer' achievement"
            )
        ),
        ZoneDefinition(
            id = "tutorial_echo_chamber",
            name = "The Echo Chamber",
            levelRange = 10..20,
            description = """
                A sealed room in the Spire between Floors 3 and 4, accessible only by touching
                a specific glyph sequence on the walls (the glyphs in the Crucible courtyard
                are the key). Inside: a space where the rules are different. Skills can be
                respecced here. Stats can be reallocated. Once.

                The room also contains a System terminal that shows data not available elsewhere:
                tutorial statistics, completion rates for other species, and a partially
                corrupted file about Earth's "projected outcome."
            """.trimIndent(),
            theme = "Respec opportunity, forbidden knowledge, System secrets",
            dangers = listOf(
                "Using the respec is irreversible",
                "The corrupted file contains information that might be better not knowing"
            ),
            resources = listOf(
                "One-time stat/skill respec",
                "System intel (other species data)",
                "Corrupted intel about Earth's future",
                "'Cartographer' achievement (if all hidden areas found)"
            )
        )
    )

    // ════════════════════════════════════════════════════════════════
    // EARTH — Post-Tutorial Zones (Levels 25+)
    // ════════════════════════════════════════════════════════════════

    val LANDING_ZONE = ZoneDefinition(
        id = "earth_landing",
        name = "Reintegration Point",
        levelRange = 25..30,
        description = """
            Where you arrive back on Earth — a city you might recognize, torn apart by
            dimensional rifts. Half the buildings still stand. Power still works in some
            blocks. The sky has visible seams where other dimensions bleed through.

            Unintegrated civilians have survived in basements, shelters, and fortified
            buildings. They've had months without any System powers while you were in the
            tutorial. Some are grateful for help. Some are terrified of what you've become.
            Some have organized into their own communities and aren't sure they want
            high-level fighters disrupting what they've built.
        """.trimIndent(),
        theme = "Homecoming, civilian survivors, rebuilding",
        dangers = listOf(
            "Rift-spawn monsters wandering the city (Level 20-28)",
            "Structural collapse in damaged buildings",
            "Hostile survivor groups who distrust integrated humans",
            "Proximity to active Incursion Zones"
        ),
        resources = listOf(
            "Pre-Integration infrastructure (power, water, supplies)",
            "Civilian survivors (potential allies, community)",
            "Salvageable technology",
            "Settlement-building materials"
        ),
        connectedZones = listOf("earth_incursion_fungal", "earth_incursion_crystal", "earth_settlement")
    )

    val INCURSION_FUNGAL = ZoneDefinition(
        id = "earth_incursion_fungal",
        name = "Incursion Zone: The Sporefield",
        levelRange = 25..35,
        description = """
            A rift to a world of fungal megastructures. Spores drift through the portal like
            snow, converting everything they touch — concrete becomes spongy, metal sprouts
            mycelium, organic matter is consumed and repurposed. The zone has eaten three
            city blocks and is growing.

            The creatures from the other side are part-fungal, part-something else. They
            don't fight like animals — they fight like a network. Kill one and the others
            adapt. The spore clouds reduce visibility and corrode equipment.

            Closing this rift means finding and destroying the Core Bloom — the massive
            fungal structure anchoring the rift from the other side.
        """.trimIndent(),
        theme = "Biological horror, adaptive enemies, environmental decay",
        dangers = listOf(
            "Spore Drones — fungal-infected creatures from the other world (Level 25-30)",
            "Mycelium Network — the ground itself attacks in converted areas (Level 28-33)",
            "Core Bloom Guardians — elite defenders of the rift anchor (Level 33-35)",
            "Environmental: spore clouds damage equipment, reduce visibility, and convert terrain"
        ),
        resources = listOf(
            "Spore Cores — unique crafting materials",
            "Fungal Armor compounds — self-repairing equipment",
            "Rift data — contributes to mapping the convergence pattern",
            "Stabilized zone after clearing — safe territory for settlement expansion"
        ),
        connectedZones = listOf("earth_landing")
    )

    val INCURSION_CRYSTAL = ZoneDefinition(
        id = "earth_incursion_crystal",
        name = "Incursion Zone: The Shardfall",
        levelRange = 28..38,
        description = """
            A rift to a world of living crystal. Massive crystal formations have erupted
            through a highway interchange, refracting light into colors that hurt to look at.
            The crystal grows, and it sings — a harmonic frequency that interferes with
            System notifications and scrambles skills if you stay too long.

            The beings here are crystalline: beautiful, angular, and extremely hostile. They
            don't speak, but they communicate through light patterns. They are also, the
            System notes, an integrated species — Level 30-40, D-Grade. They're not
            monsters. They're people defending their territory.

            This is the first Incursion Zone where diplomacy is an option. Whether it's the
            better option remains to be seen.
        """.trimIndent(),
        theme = "Alien contact, crystal aesthetics, diplomacy vs force",
        dangers = listOf(
            "Crystal Sentinels — integrated alien warriors (Level 30-36, D-Grade)",
            "Harmonic Interference — scrambles skills and System notifications in deep zones",
            "Crystal Growth — the zone expands if not contained, converting buildings to crystal",
            "Shard Storms — periodic eruptions of razor-sharp crystal fragments"
        ),
        resources = listOf(
            "Living Crystal — powerful crafting material",
            "Harmonic Cores — skill enhancement components",
            "First Contact data — understanding alien integrated species",
            "Potential alliance with the Crystalline if diplomacy succeeds"
        ),
        connectedZones = listOf("earth_landing")
    )

    val EARTH_SETTLEMENT = ZoneDefinition(
        id = "earth_settlement",
        name = "Reclaimed Block",
        levelRange = 25..50,
        description = """
            A section of the city cleared of rift-spawn and fortified by returning tutorial
            survivors. Part military camp, part refugee center, part frontier town. Generator
            power. Running water from a rigged municipal system. A perimeter of System-enhanced
            barriers that keep monsters out — mostly.

            This is where the post-tutorial story lives. Returning participants form groups,
            take on Incursion Zone clearing contracts, and argue about what humanity should
            become. Some want to close every rift and rebuild. Others want to go THROUGH the
            rifts — to explore, to conquer, to become something bigger than one world.

            Unintegrated civilians are the majority. They need protection, resources, and
            hope. They also have skills the integrated don't — engineering, medicine, agriculture,
            leadership. The settlement needs both types to survive.
        """.trimIndent(),
        theme = "Home base, politics, civilian-military dynamics",
        dangers = listOf(
            "None within perimeter (Safe Zone barriers)",
            "Resource scarcity",
            "Political tension between integrated and unintegrated humans",
            "Occasional barrier breaches during rift surges"
        ),
        resources = listOf(
            "Equipment repair and crafting",
            "Quest contracts for Incursion Zone clearing",
            "Civilian specialists (engineering, medicine, intel)",
            "Community building — reputation matters here"
        ),
        connectedZones = listOf("earth_landing", "earth_incursion_fungal", "earth_incursion_crystal")
    )

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    fun getZone(zoneId: String): ZoneDefinition? {
        return getAllZones().firstOrNull { it.id == zoneId }
    }

    fun getZonesForLevel(level: Int): List<ZoneDefinition> {
        return getAllZones().filter { level in it.levelRange }
    }

    fun getAllZones(): List<ZoneDefinition> {
        return listOf(
            CRUCIBLE,
            GREENREACH,
            ASHLANDS,
            DROWNED_SHELF,
            SPIRE,
            LANDING_ZONE,
            INCURSION_FUNGAL,
            INCURSION_CRYSTAL,
            EARTH_SETTLEMENT
        ) + HIDDEN_AREAS
    }

    fun getTutorialZones(): List<ZoneDefinition> {
        return listOf(CRUCIBLE, GREENREACH, ASHLANDS, DROWNED_SHELF, SPIRE) + HIDDEN_AREAS
    }

    fun getEarthZones(): List<ZoneDefinition> {
        return listOf(LANDING_ZONE, INCURSION_FUNGAL, INCURSION_CRYSTAL, EARTH_SETTLEMENT)
    }

    fun getBossForZone(zoneId: String): BossDefinition? {
        return TUTORIAL_BOSSES.firstOrNull { it.zone == zoneId }
    }

    fun isZoneUnlocked(zoneId: String, playerLevel: Int): Boolean {
        val zone = getZone(zoneId) ?: return false
        return playerLevel in zone.levelRange
    }
}
