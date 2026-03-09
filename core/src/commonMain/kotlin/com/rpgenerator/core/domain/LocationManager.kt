package com.rpgenerator.core.domain

import com.rpgenerator.core.api.SystemType

internal class LocationManager {
    private val zones = mutableMapOf<String, Zone>()
    private val templateLocations = mutableMapOf<String, Location>()

    fun loadLocations(systemType: SystemType) {
        when (systemType) {
            SystemType.SYSTEM_INTEGRATION -> loadSystemIntegrationLocations()
            SystemType.DUNGEON_DELVE -> loadDungeonCrawlerLocations()
            else -> loadDefaultLocations()
        }
    }

    fun getLocation(locationId: String, gameState: GameState): Location? {
        return gameState.customLocations[locationId]
            ?: templateLocations[locationId]
    }

    fun getTemplateLocation(locationId: String): Location? {
        return templateLocations[locationId]
    }

    fun getZone(zoneId: String): Zone? {
        return zones[zoneId]
    }

    fun getConnectedLocations(locationId: String, gameState: GameState): List<Location> {
        val location = getLocation(locationId, gameState) ?: return emptyList()
        return location.connections.mapNotNull {
            getLocation(it, gameState)
        }
    }

    fun getAvailableLocations(gameState: GameState): List<Location> {
        val discovered = gameState.discoveredTemplateLocations.mapNotNull {
            templateLocations[it]
        }
        val custom = gameState.customLocations.values.toList()
        return discovered + custom
    }

    fun getStartingLocation(systemType: SystemType): Location {
        return when (systemType) {
            SystemType.SYSTEM_INTEGRATION -> templateLocations["tutorial-grove"]
                ?: error("Tutorial grove not found")
            else -> templateLocations.values.first()
        }
    }

    private fun loadSystemIntegrationLocations() {
        addZone(Zone(
            id = "tutorial-zone",
            name = "Tutorial Zone",
            recommendedLevel = 1,
            description = "A protected area where newly integrated humans learn to survive",
            lore = "When the System integrated Earth, it created safe zones for the unprepared masses. Most didn't make it past the first wave."
        ))

        addZone(Zone(
            id = "verdant-wilderness",
            name = "Verdant Wilderness",
            recommendedLevel = 5,
            description = "Dense forests teeming with mutated beasts and rare herbs",
            lore = "The System's energy warped Earth's ecosystems. What were once ordinary animals became deadly predators, and plants gained strange properties."
        ))

        addZone(Zone(
            id = "contested-outpost",
            name = "Contested Outpost",
            recommendedLevel = 10,
            description = "A human settlement constantly under threat from beast hordes",
            lore = "Survivors banded together, building walls and organizing parties. But the beasts grow stronger each day."
        ))

        addZone(Zone(
            id = "nexus-shard",
            name = "Dimensional Nexus Shard",
            recommendedLevel = 15,
            description = "A fracture in reality where cosmic energies pool",
            lore = "The System integration didn't just change Earth - it punched holes to other dimensions. Power flows through these rifts, but so do horrors."
        ))

        addLocation(Location(
            id = "tutorial-grove",
            name = "Sanctified Grove",
            zoneId = "tutorial-zone",
            biome = Biome.TUTORIAL_ZONE,
            description = "A peaceful clearing surrounded by shimmering barriers. The air hums with protective energy.",
            danger = 1,
            connections = listOf("darkwood-path"),
            features = listOf("system_obelisk", "healing_spring"),
            lore = "The System placed these groves across Earth as starting points. Their barriers last one week - then you're on your own."
        ))

        addLocation(Location(
            id = "darkwood-path",
            name = "Darkwood Path",
            zoneId = "tutorial-zone",
            biome = Biome.FOREST,
            description = "A winding trail through dense forest. Shadows move between the trees.",
            danger = 2,
            connections = listOf("tutorial-grove", "goblin-camp", "hidden-glade"),
            features = listOf("beast_tracks", "abandoned_corpses"),
            lore = "The path to the outside world. Many who took it never returned."
        ))

        addLocation(Location(
            id = "goblin-camp",
            name = "Goblin War Camp",
            zoneId = "tutorial-zone",
            biome = Biome.SETTLEMENT,
            description = "Crude tents and weapon racks surround a blazing fire. The stench of blood fills the air.",
            danger = 3,
            connections = listOf("darkwood-path"),
            features = listOf("weapon_cache", "goblin_chieftain"),
            lore = "The System didn't just enhance Earth's creatures - it spawned entirely new species. Goblins were among the first."
        ))

        addLocation(Location(
            id = "hidden-glade",
            name = "Mystic Glade",
            zoneId = "tutorial-zone",
            biome = Biome.FOREST,
            description = "A serene clearing where crystalline flowers bloom. The air shimmers with mana.",
            danger = 1,
            connections = listOf("darkwood-path", "ancient-ruins"),
            features = listOf("mana_flowers", "spirit_wisps"),
            lore = "Places where Earth's original magic mingles with System energy. Valuable, but contested."
        ))

        addLocation(Location(
            id = "ancient-ruins",
            name = "Pre-System Ruins",
            zoneId = "verdant-wilderness",
            biome = Biome.RUINS,
            description = "Collapsed skyscrapers now overgrown with luminescent vines. Nature reclaimed civilization in days.",
            danger = 5,
            connections = listOf("hidden-glade", "beast-den", "outpost-gates"),
            features = listOf("scavengeable_tech", "mutant_nest"),
            lore = "A city that fell in the first hours. The corpses are long gone, but their equipment remains."
        ))

        addLocation(Location(
            id = "beast-den",
            name = "Crimson Beast Den",
            zoneId = "verdant-wilderness",
            biome = Biome.CAVE,
            description = "A network of caves stained red from countless kills. Bones litter the entrance.",
            danger = 7,
            connections = listOf("ancient-ruins"),
            features = listOf("alpha_beast", "bone_pile", "rare_catalyst"),
            lore = "Home to a pack of Class E beasts. Their alpha guards a Dao shard."
        ))

        addLocation(Location(
            id = "outpost-gates",
            name = "Sanctuary Outpost Gates",
            zoneId = "contested-outpost",
            biome = Biome.SETTLEMENT,
            description = "Reinforced walls built from scrap metal and concrete. Guards eye you warily from watchtowers.",
            danger = 3,
            connections = listOf("ancient-ruins", "outpost-market", "training-grounds"),
            features = listOf("guard_post", "quest_board"),
            lore = "The largest surviving human settlement in the region. Population: 847. Yesterday it was 851."
        ))

        addLocation(Location(
            id = "outpost-market",
            name = "Black Market",
            zoneId = "contested-outpost",
            biome = Biome.SETTLEMENT,
            description = "Makeshift stalls selling everything from System crystals to scavenged rations. Desperate merchants hawk their wares.",
            danger = 1,
            connections = listOf("outpost-gates"),
            features = listOf("merchant_npc", "rare_items", "information_broker"),
            lore = "Credits are worthless. Nexus coins and beast cores are the new currency."
        ))

        addLocation(Location(
            id = "training-grounds",
            name = "Combat Training Yard",
            zoneId = "contested-outpost",
            biome = Biome.SETTLEMENT,
            description = "An open area where survivors practice with weapons. The clash of steel rings out constantly.",
            danger = 2,
            connections = listOf("outpost-gates", "nexus-rift"),
            features = listOf("training_dummies", "instructor_npc", "sparring_arena"),
            lore = "Everyone fights now. There are no civilians anymore."
        ))

        addLocation(Location(
            id = "nexus-rift",
            name = "Pulsing Nexus Rift",
            zoneId = "nexus-shard",
            biome = Biome.COSMIC_VOID,
            description = "Reality tears open, revealing an impossible vista of swirling galaxies. Cosmic energy pours through.",
            danger = 15,
            connections = listOf("training-grounds"),
            features = listOf("dimensional_portal", "cosmic_horror", "dao_fragment"),
            lore = "A gateway to the wider multiverse. Step through at your own risk."
        ))
    }

    private fun loadDungeonCrawlerLocations() {
        // ── Floor 1 — The Welcome Mat ───────────────────────────────
        addZone(Zone(
            id = "floor-1",
            name = "Floor 1 — The Welcome Mat",
            recommendedLevel = 1,
            description = "The entry level of the World Dungeon. Garishly lit, camera drones everywhere, sponsors watching.",
            lore = "Designed for maximum entertainment value. The Dungeon eases you in — then pulls the rug."
        ))

        addLocation(Location(
            id = "f1-staging-area",
            name = "The Staging Area",
            zoneId = "floor-1",
            biome = Biome.DUNGEON,
            description = "A massive cavern lit by floating neon signs advertising alien products. Dazed crawlers mill around. Camera drones hover at eye level. A giant scoreboard shows viewer counts.",
            danger = 1,
            connections = listOf("f1-gift-shop", "f1-tutorial-gauntlet", "f1-crawler-camp"),
            features = listOf("sponsor_scoreboard", "camera_drones", "tutorial_box", "crawler_orientation"),
            lore = "Where every crawler's season begins. The Dungeon gives you ten minutes to get your bearings. Then the clock starts."
        ))

        addLocation(Location(
            id = "f1-gift-shop",
            name = "Boatman's Gift Shop",
            zoneId = "floor-1",
            biome = Biome.DUNGEON,
            description = "A cramped shop crammed with 'survival essentials' at outrageous prices. A floating skull named Boatman runs the register with manic enthusiasm.",
            danger = 0,
            connections = listOf("f1-staging-area"),
            features = listOf("merchant_npc", "overpriced_gear", "sponsor_coupons", "mystery_boxes"),
            lore = "Boatman claims he's been running this shop since 'before the first crawler drew breath.' He accepts all currencies, including teeth."
        ))

        addLocation(Location(
            id = "f1-tutorial-gauntlet",
            name = "The Tutorial Gauntlet",
            zoneId = "floor-1",
            biome = Biome.DUNGEON,
            description = "A corridor of increasingly nasty traps and low-level monsters, designed to teach crawlers the basics. Floating text helpfully explains each way you might die.",
            danger = 3,
            connections = listOf("f1-staging-area", "f1-rat-warrens", "f1-sponsor-shrine"),
            features = listOf("training_traps", "tutorial_monsters", "floating_tips", "loot_caches"),
            lore = "The Dungeon's idea of training: throw you at problems and see what sticks. Literally."
        ))

        addLocation(Location(
            id = "f1-crawler-camp",
            name = "Crawler Base Camp",
            zoneId = "floor-1",
            biome = Biome.SETTLEMENT,
            description = "A makeshift camp where crawlers rest between runs. Campfires, bedrolls, and the sound of people processing trauma through dark humor.",
            danger = 0,
            connections = listOf("f1-staging-area", "f1-sponsor-shrine"),
            features = listOf("campfire", "fellow_crawlers", "trade_corner", "rumor_board"),
            lore = "Rule one of Crawler Camp: don't get attached. Rule two: everyone breaks rule one."
        ))

        addLocation(Location(
            id = "f1-rat-warrens",
            name = "The Rat Warrens",
            zoneId = "floor-1",
            biome = Biome.CAVE,
            description = "Tunnels infested with mutated rats the size of dogs. They hunt in packs and they're smarter than they look. The chat finds rat deaths hilarious.",
            danger = 4,
            connections = listOf("f1-tutorial-gauntlet", "f1-boss-antechamber"),
            features = listOf("rat_packs", "hidden_passages", "loot_nests", "rat_king_lair"),
            lore = "The Rat King is a Floor 1 legend. Some crawlers have made it their personal mission to dethrone him. Most become rat food."
        ))

        addLocation(Location(
            id = "f1-sponsor-shrine",
            name = "The Sponsor Shrine",
            zoneId = "floor-1",
            biome = Biome.DUNGEON,
            description = "A gaudy altar where crawlers can appeal to sponsors. Holographic screens show viewer counts, trending moments, and gift notifications. It reeks of desperation and alien perfume.",
            danger = 1,
            connections = listOf("f1-tutorial-gauntlet", "f1-crawler-camp", "f1-boss-antechamber"),
            features = listOf("sponsor_altar", "trending_board", "gift_box_terminal", "highlight_reel"),
            lore = "Sponsors are alien beings who watch the Dungeon for entertainment. They gift items to crawlers they find entertaining. The currency is attention."
        ))

        addLocation(Location(
            id = "f1-boss-antechamber",
            name = "Boss Antechamber",
            zoneId = "floor-1",
            biome = Biome.DUNGEON,
            description = "A grand hall with a massive iron door at the far end. The words 'BOSS FIGHT — SPONSORED BY MURDERMAX ENERGY DRINK' glow above it. Other crawlers eye each other nervously.",
            danger = 2,
            connections = listOf("f1-rat-warrens", "f1-sponsor-shrine", "f1-boss-arena"),
            features = listOf("boss_door", "pre_fight_vendor", "party_formation", "spectator_stands"),
            lore = "The Antechamber is where alliances form and break. Everyone needs a party for the boss. Trust is expensive."
        ))

        addLocation(Location(
            id = "f1-boss-arena",
            name = "The Colosseum of Woe",
            zoneId = "floor-1",
            biome = Biome.DUNGEON,
            description = "A circular arena with floating cameras and holographic audience seats filled with alien spectators. The Floor 1 Boss — a grotesque, oversized goblin in a tiny crown — waits at the center.",
            danger = 6,
            connections = listOf("f1-boss-antechamber", "f1-safe-room"),
            features = listOf("floor_boss", "audience_stands", "sponsor_drops", "achievement_pillar"),
            lore = "King Grumbles, the Floor 1 Boss, has killed more crawlers than any other monster in the Dungeon. He's also the most meme'd."
        ))

        addLocation(Location(
            id = "f1-safe-room",
            name = "Floor 1 Safe Room",
            zoneId = "floor-1",
            biome = Biome.SETTLEMENT,
            description = "A deceptively cozy room that appears after the boss dies. Hot food, soft beds, a working shower. The Dungeon's cruelest trick: reminding you what comfort feels like.",
            danger = 0,
            connections = listOf("f1-boss-arena", "f2-crossroads"),
            features = listOf("rest_point", "loot_summary", "floor_stats", "stairway_down"),
            lore = "The Safe Room lasts 24 hours. After that, the Dungeon kicks you to Floor 2 whether you're ready or not."
        ))

        // ── Floor 2 — The Proving Grounds ───────────────────────────
        addZone(Zone(
            id = "floor-2",
            name = "Floor 2 — The Proving Grounds",
            recommendedLevel = 5,
            description = "Where the Dungeon stops pretending to be fair. Faction territories, harder monsters, real consequences.",
            lore = "Floor 2 is where crawlers die for real. The tutorial's over. The audience wants blood."
        ))

        addLocation(Location(
            id = "f2-crossroads",
            name = "The Crossroads",
            zoneId = "floor-2",
            biome = Biome.DUNGEON,
            description = "A vast underground intersection where three faction territories meet. Banners hang from the ceiling: the Crimson Axes (strength), the Silent Path (stealth), and the Open Hand (cooperation).",
            danger = 3,
            connections = listOf("f1-safe-room", "f2-crimson-territory", "f2-silent-territory", "f2-open-hand-hall", "f2-neutral-market"),
            features = listOf("faction_recruiters", "territory_map", "bounty_board", "faction_banners"),
            lore = "Every crawler must eventually choose a faction — or face Floor 2 alone. The Dungeon rewards loyalty. And punishes it."
        ))

        addLocation(Location(
            id = "f2-crimson-territory",
            name = "Crimson Axes Territory",
            zoneId = "floor-2",
            biome = Biome.DUNGEON,
            description = "A fortified zone controlled by the Crimson Axes — combat-focused crawlers who believe strength is the only answer. Training pits ring with the clash of weapons.",
            danger = 5,
            connections = listOf("f2-crossroads", "f2-fighting-pits"),
            features = listOf("training_pits", "weapon_forge", "challenge_arena", "war_council"),
            lore = "Founded by Dozer, a former construction worker who punched a minotaur to death on Floor 3. The Axes respect only one thing: results."
        ))

        addLocation(Location(
            id = "f2-silent-territory",
            name = "Silent Path Hideout",
            zoneId = "floor-2",
            biome = Biome.CAVE,
            description = "A network of hidden passages and concealed rooms. The Silent Path operates in the shadows — scouts, assassins, information brokers. You don't find them; they find you.",
            danger = 4,
            connections = listOf("f2-crossroads", "f2-intelligence-room"),
            features = listOf("hidden_entrance", "dead_drops", "shadow_training", "intel_network"),
            lore = "Led by Whisper, a woman who hasn't spoken above a murmur since Floor 1. Her information network spans three floors. Knowing things is worth more than hitting things."
        ))

        addLocation(Location(
            id = "f2-open-hand-hall",
            name = "Open Hand Community Hall",
            zoneId = "floor-2",
            biome = Biome.SETTLEMENT,
            description = "A warm, welcoming space where the Open Hand faction shares resources and knowledge. Potluck dinners, skill-sharing workshops, and a disturbing amount of optimism for people trapped in a death game.",
            danger = 1,
            connections = listOf("f2-crossroads", "f2-community-garden"),
            features = listOf("communal_kitchen", "skill_workshops", "healing_station", "morale_board"),
            lore = "Founded by Mama Teng, a retired nurse who decided that if humanity was going to die, they'd die helping each other. The other factions call them naive. They're still alive."
        ))

        addLocation(Location(
            id = "f2-neutral-market",
            name = "The Bazaar",
            zoneId = "floor-2",
            biome = Biome.SETTLEMENT,
            description = "A neutral trading zone where all factions do business. Merchant crawlers hawk gear, information, and questionable potions. The Dungeon guarantees a no-violence zone — mostly.",
            danger = 1,
            connections = listOf("f2-crossroads", "f2-boss-gate"),
            features = listOf("gear_merchants", "potion_sellers", "info_brokers", "betting_parlor"),
            lore = "The Bazaar runs on one rule: everyone's money is good here. Cross someone in the Bazaar, and every merchant on every floor hears about it."
        ))

        addLocation(Location(
            id = "f2-fighting-pits",
            name = "The Fighting Pits",
            zoneId = "floor-2",
            biome = Biome.DUNGEON,
            description = "Where crawlers fight monsters — and each other — for glory, loot, and sponsor attention. The stands are always packed. Blood washes off the stone surprisingly well.",
            danger = 7,
            connections = listOf("f2-crimson-territory", "f2-boss-gate"),
            features = listOf("pit_fights", "monster_arena", "champion_board", "sponsor_skybox"),
            lore = "The Pits are the single highest-rated content source on Floor 2. Sponsors pay triple for ringside views."
        ))

        addLocation(Location(
            id = "f2-intelligence-room",
            name = "The Whispering Room",
            zoneId = "floor-2",
            biome = Biome.CAVE,
            description = "The nerve center of the Silent Path. Maps cover every wall. Scouts report in through hidden passages. Someone always knows something you need.",
            danger = 2,
            connections = listOf("f2-silent-territory", "f2-boss-gate"),
            features = listOf("floor_maps", "scout_reports", "secret_passages", "boss_intel"),
            lore = "Whisper has mapped every trap, every patrol route, every safe passage on Floor 2. That information has a price."
        ))

        addLocation(Location(
            id = "f2-community-garden",
            name = "The Underground Garden",
            zoneId = "floor-2",
            biome = Biome.JUNGLE,
            description = "A cavern where the Open Hand grows food using bioluminescent plants. It shouldn't work, but Mama Teng's green thumb is apparently System-enhanced. The air smells like basil and hope.",
            danger = 1,
            connections = listOf("f2-open-hand-hall", "f2-boss-gate"),
            features = listOf("herb_garden", "food_supply", "alchemy_station", "gathering_quests"),
            lore = "The Garden feeds three factions. Mama Teng gives food to anyone who asks. 'Hungry people make bad decisions,' she says."
        ))

        addLocation(Location(
            id = "f2-boss-gate",
            name = "The Gate of Trials",
            zoneId = "floor-2",
            biome = Biome.DUNGEON,
            description = "A massive gate inscribed with shifting runes. The Floor 2 Boss requires a party of at least three. The gate won't open for less. The Dungeon loves a team dynamic.",
            danger = 3,
            connections = listOf("f2-neutral-market", "f2-fighting-pits", "f2-intelligence-room", "f2-community-garden"),
            features = listOf("boss_gate", "party_check", "final_prep_area", "spectator_portal"),
            lore = "The Floor 2 Boss changes every season. Past bosses include a hydra, a puzzle golem, and once — a very angry accountant. The Dungeon has a sense of humor."
        ))

        // ── Floor 3 — The Deep ──────────────────────────────────────
        addZone(Zone(
            id = "floor-3",
            name = "Floor 3 — The Deep",
            recommendedLevel = 10,
            description = "Where the Dungeon gets serious. Darkness, environmental hazards, and monsters that learn from your tactics.",
            lore = "Floor 3 is where the Dungeon tests whether you deserve to keep going. Most don't."
        ))

        addLocation(Location(
            id = "f3-descent",
            name = "The Descent",
            zoneId = "floor-3",
            biome = Biome.CAVE,
            description = "A spiraling staircase carved into living rock. The temperature drops. The lights dim. Camera drones switch to night vision. Welcome to the deep.",
            danger = 6,
            connections = listOf("f3-echo-caves", "f3-fungal-forest"),
            features = listOf("environmental_hazards", "darkness_mechanic", "adaptive_monsters", "echo_traps"),
            lore = "The Deep is where the Dungeon's true nature shows. It's not just a game. It's alive. And it's been watching you learn."
        ))

        addLocation(Location(
            id = "f3-echo-caves",
            name = "The Echo Caves",
            zoneId = "floor-3",
            biome = Biome.CAVE,
            description = "Caves that replay sounds from the past — screams, laughter, the last words of dead crawlers. The monsters here hunt by sound. Silence is survival.",
            danger = 8,
            connections = listOf("f3-descent", "f3-rebel-outpost"),
            features = listOf("sound_traps", "echo_monsters", "silence_zones", "ghost_whispers"),
            lore = "Some crawlers hear their own voices echoed back. The Dungeon remembers everything."
        ))

        addLocation(Location(
            id = "f3-fungal-forest",
            name = "The Fungal Forest",
            zoneId = "floor-3",
            biome = Biome.JUNGLE,
            description = "A cavern filled with massive bioluminescent mushrooms. Beautiful and deadly — the spores cause hallucinations, the caps are carnivorous, and something ancient lives at the center.",
            danger = 9,
            connections = listOf("f3-descent", "f3-rebel-outpost"),
            features = listOf("hallucinogenic_spores", "carnivorous_fungi", "rare_ingredients", "hidden_shrine"),
            lore = "The Fungal Forest predates the Dungeon. Even the sponsors don't know what grew it. That makes them nervous."
        ))

        addLocation(Location(
            id = "f3-rebel-outpost",
            name = "The Rebellion",
            zoneId = "floor-3",
            biome = Biome.SETTLEMENT,
            description = "A hidden base where crawlers who've rejected the game plan their resistance. They've found ways to disable cameras, block sponsor gifts, and communicate without the Dungeon listening. Maybe.",
            danger = 2,
            connections = listOf("f3-echo-caves", "f3-fungal-forest", "f3-heart"),
            features = listOf("camera_jammers", "rebel_leader", "resistance_plans", "forbidden_knowledge"),
            lore = "Led by 'Zero' — a crawler who claims to have found proof that the Dungeon can be broken from the inside. The sponsors want her dead. The Dungeon... seems amused."
        ))

        addLocation(Location(
            id = "f3-heart",
            name = "The Heart of Floor 3",
            zoneId = "floor-3",
            biome = Biome.COSMIC_VOID,
            description = "The center of Floor 3, where reality thins. The walls breathe. The floor pulses. The Floor 3 Boss doesn't fight you — it asks you a question. Get it wrong, and you never leave.",
            danger = 12,
            connections = listOf("f3-rebel-outpost"),
            features = listOf("floor_boss_3", "reality_thin_point", "the_question", "truth_or_death"),
            lore = "The Heart is where crawlers learn the first real truth about the Dungeon: it's not just entertainment. It's a test. For what, no one knows."
        ))
    }

    private fun loadDefaultLocations() {
        addZone(Zone(
            id = "starting-zone",
            name = "Starting Area",
            recommendedLevel = 1,
            description = "Where your journey begins",
            lore = "Every story starts somewhere."
        ))

        addLocation(Location(
            id = "start",
            name = "Starting Location",
            zoneId = "starting-zone",
            biome = Biome.FOREST,
            description = "A generic starting area",
            danger = 1,
            connections = emptyList(),
            features = emptyList(),
            lore = "The beginning of your adventure."
        ))
    }

    private fun addZone(zone: Zone) {
        zones[zone.id] = zone
    }

    private fun addLocation(location: Location) {
        templateLocations[location.id] = location
    }
}
