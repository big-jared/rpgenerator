package com.rpgenerator.core.domain

import com.rpgenerator.core.skill.DamageType

/**
 * Bestiary for the Integration seed (System Apocalypse / tutorial dimension).
 * Covers all zones from ZoneProgression: Greenreach, Ashlands, Drowned Shelf, Spire, and The Hollow.
 */
internal object IntegrationBestiary : Bestiary {

    // ════════════════════════════════════════════════════════════════
    // GREENREACH (tutorial_greenreach) — Levels 1-8
    // ════════════════════════════════════════════════════════════════

    private val BONE_CRAWLER = EnemyTemplate(
        id = "greenreach_bone_crawler",
        name = "Bone Crawler",
        zoneId = "tutorial_greenreach",
        levelRange = 1..3,
        baseDanger = 2,
        description = "A spider-like creature with a body of fused bone-bark, nearly invisible against the pale trees of the Greenreach. Its blue eyes glow faintly in the canopy shadows.",
        visualDescription = "Spider-like creature with bone-bark exoskeleton, pale white-grey coloring, six legs with barbed tips, faintly glowing blue compound eyes. Camouflaged against bone-white tree trunks.",
        abilities = listOf(
            EnemyAbilityTemplate(
                name = "Web Spit",
                bonusDamage = 1,
                cooldown = 3,
                description = "Spits a glob of sticky webbing that slows the target",
                damageType = DamageType.POISON
            ),
            EnemyAbilityTemplate(
                name = "Ambush Lunge",
                bonusDamage = 3,
                cooldown = 0,
                description = "Lunges from concealment with a burst of speed"
            )
        ),
        lootTier = "normal",
        vulnerabilities = setOf(DamageType.FIRE),
        resistances = setOf(DamageType.POISON),
        portraitResource = "monster_greenreach_bone_crawler"
    )

    private val CANOPY_STALKER = EnemyTemplate(
        id = "greenreach_canopy_stalker",
        name = "Canopy Stalker",
        zoneId = "tutorial_greenreach",
        levelRange = 3..5,
        baseDanger = 3,
        description = "An arboreal ambush predator that clings to the underside of branches, dropping onto prey with razor-sharp limbs. By the time you see it, it's already falling.",
        visualDescription = "Lean reptilian predator with elongated limbs ending in hooked claws, mottled blue-green scales, a whip-like barbed tail. Crouched on a tree branch mid-drop, limbs spread wide.",
        abilities = listOf(
            EnemyAbilityTemplate(
                name = "Drop Attack",
                bonusDamage = 5,
                cooldown = 0,
                description = "Drops from above for a devastating first strike"
            ),
            EnemyAbilityTemplate(
                name = "Barbed Tail Lash",
                bonusDamage = 2,
                cooldown = 3,
                description = "Whips its barbed tail, leaving a bleeding wound",
                damageType = DamageType.POISON // bleed
            )
        ),
        lootTier = "normal",
        vulnerabilities = setOf(DamageType.ICE),
        portraitResource = "monster_greenreach_canopy_stalker"
    )

    private val THORNBACK = EnemyTemplate(
        id = "greenreach_thornback",
        name = "Thornback",
        zoneId = "tutorial_greenreach",
        levelRange = 5..8,
        baseDanger = 4,
        description = "A heavily armored beast covered in thorn-like protrusions. It charges through the undergrowth like a living battering ram, scattering bone-bark splinters in its wake.",
        visualDescription = "Rhinoceros-sized quadruped covered in thorn-like bone spikes, thick grey-green hide, a massive horn of fused thorns on its snout. Muscular, low to the ground, built for charging.",
        abilities = listOf(
            EnemyAbilityTemplate(
                name = "Gore Charge",
                bonusDamage = 6,
                cooldown = 3,
                description = "Charges headlong into the target with devastating force"
            ),
            EnemyAbilityTemplate(
                name = "Thorn Spray",
                bonusDamage = 3,
                cooldown = 4,
                description = "Shakes violently, launching thorns in all directions"
            ),
            EnemyAbilityTemplate(
                name = "Hunker Down",
                bonusDamage = 0,
                cooldown = 5,
                description = "Hunkers behind its armored shell, greatly increasing defense"
            )
        ),
        lootTier = "normal",
        xpMultiplier = 1.2,
        vulnerabilities = setOf(DamageType.LIGHTNING),
        resistances = setOf(DamageType.PHYSICAL),
        portraitResource = "monster_greenreach_thornback"
    )

    private val HOLLOW_MATRIARCH = EnemyTemplate(
        id = "greenreach_hollow_matriarch",
        name = "Hollow Matriarch",
        zoneId = "tutorial_greenreach",
        levelRange = 8..8,
        baseDanger = 8,
        description = "A giant spider-creature fused with the oldest tree in the Greenreach. Her body is made of the same bone-bark as the trees — she's been here so long the forest grew around her. When she moves, the canopy moves with her.",
        visualDescription = "Massive spider merged with an ancient tree, bone-bark legs the size of tree trunks, glowing blue eyes clustered across a gnarled wooden head. Web-draped canopy shudders around her body.",
        abilities = listOf(
            // Phase 1 abilities
            EnemyAbilityTemplate(
                name = "Summon Bone Crawlers",
                bonusDamage = 0,
                cooldown = 4,
                description = "Calls forth a wave of Bone Crawlers from the canopy",
                unlockPhase = 1
            ),
            EnemyAbilityTemplate(
                name = "Web Trap",
                bonusDamage = 2,
                cooldown = 3,
                description = "Launches a massive web that immobilizes the target",
                unlockPhase = 1
            ),
            EnemyAbilityTemplate(
                name = "Mandible Crush",
                bonusDamage = 8,
                cooldown = 2,
                description = "Snaps her enormous mandibles shut on the target",
                unlockPhase = 1
            ),
            // Phase 2 abilities
            EnemyAbilityTemplate(
                name = "Skittering Rush",
                bonusDamage = 6,
                cooldown = 3,
                description = "Detached and mobile, she rushes forward in a blur of legs",
                unlockPhase = 2
            ),
            // Phase 3 abilities
            EnemyAbilityTemplate(
                name = "Root Eruption",
                bonusDamage = 7,
                cooldown = 2,
                description = "The tree's roots erupt from the ground beneath the target",
                unlockPhase = 3
            ),
            EnemyAbilityTemplate(
                name = "Canopy Collapse",
                bonusDamage = 12,
                cooldown = 5,
                description = "The entire canopy crashes down in a devastating area attack",
                unlockPhase = 3
            )
        ),
        isBoss = true,
        bossPhases = listOf(
            BossPhase(
                phase = 1,
                hpThreshold = 1.0,
                description = "The Matriarch is fused with her tree, summoning crawlers and trapping prey in webs."
            ),
            BossPhase(
                phase = 2,
                hpThreshold = 0.75,
                description = "With a horrible tearing sound, the Matriarch rips free from her tree. She's faster now, skittering across the clearing.",
                additionalAbilities = listOf(
                    EnemyAbilityTemplate("Skittering Rush", 6, 3, "Detached and mobile, she rushes forward in a blur of legs", unlockPhase = 2)
                ),
                statMultiplier = 1.3
            ),
            BossPhase(
                phase = 3,
                hpThreshold = 0.40,
                description = "The tree itself comes alive, roots erupting from the earth, the canopy thrashing overhead. The Matriarch and tree fight as one.",
                additionalAbilities = listOf(
                    EnemyAbilityTemplate("Root Eruption", 7, 2, "The tree's roots erupt from the ground beneath the target", unlockPhase = 3),
                    EnemyAbilityTemplate("Canopy Collapse", 12, 5, "The entire canopy crashes down in a devastating area attack", unlockPhase = 3)
                ),
                statMultiplier = 1.5
            )
        ),
        lootTier = "boss",
        xpMultiplier = 3.0,
        vulnerabilities = setOf(DamageType.FIRE),
        resistances = setOf(DamageType.POISON, DamageType.DARK),
        portraitResource = "monster_greenreach_hollow_matriarch"
    )

    // ════════════════════════════════════════════════════════════════
    // ASHLANDS (tutorial_ashlands) — Levels 5-15
    // ════════════════════════════════════════════════════════════════

    private val SLAG_BEETLE = EnemyTemplate(
        id = "ashlands_slag_beetle",
        name = "Slag Beetle",
        zoneId = "tutorial_ashlands",
        levelRange = 5..8,
        baseDanger = 3,
        description = "An obsidian-shelled beetle the size of a car, slow but devastatingly powerful. Its mandibles shear through stone like paper, and its shell deflects most attacks.",
        visualDescription = "Car-sized beetle with glossy black obsidian shell, glowing orange cracks between plates, massive serrated mandibles dripping with molten slag. Stubby legs, hulking frame.",
        abilities = listOf(
            EnemyAbilityTemplate(
                name = "Mandible Shear",
                bonusDamage = 6,
                cooldown = 2,
                description = "Clamps its massive mandibles shut with stone-shearing force"
            ),
            EnemyAbilityTemplate(
                name = "Shell Deflect",
                bonusDamage = 0,
                cooldown = 4,
                description = "Angles its obsidian shell to deflect the next incoming attack"
            )
        ),
        lootTier = "normal",
        immunities = setOf(DamageType.FIRE),
        vulnerabilities = setOf(DamageType.ICE),
        resistances = setOf(DamageType.PHYSICAL),
        portraitResource = "monster_ashlands_slag_beetle"
    )

    private val MAGMA_SERPENT = EnemyTemplate(
        id = "ashlands_magma_serpent",
        name = "Magma Serpent",
        zoneId = "tutorial_ashlands",
        levelRange = 7..11,
        baseDanger = 4,
        description = "A serpent made of living magma that flows between cracks in the volcanic rock. Fire cannot harm it, and its body burns anything foolish enough to strike it in melee.",
        visualDescription = "Serpentine creature of flowing molten rock, bright orange-red magma body with a darker cooled crust that cracks and reforms. Glowing yellow eyes, heat shimmer radiating outward.",
        abilities = listOf(
            EnemyAbilityTemplate(
                name = "Lava Spit",
                bonusDamage = 4,
                cooldown = 2,
                description = "Spits a glob of molten rock at range",
                damageType = DamageType.FIRE
            ),
            EnemyAbilityTemplate(
                name = "Constrict",
                bonusDamage = 5,
                cooldown = 4,
                description = "Wraps around the target, dealing sustained crushing and burn damage",
                damageType = DamageType.FIRE
            ),
            EnemyAbilityTemplate(
                name = "Molten Body",
                bonusDamage = 2,
                cooldown = 0,
                description = "Its body of living magma damages anyone who strikes it in melee",
                damageType = DamageType.FIRE
            )
        ),
        lootTier = "normal",
        xpMultiplier = 1.2,
        immunities = setOf(DamageType.FIRE),
        vulnerabilities = setOf(DamageType.ICE),
        portraitResource = "monster_ashlands_magma_serpent"
    )

    private val EMBERWRAITH = EnemyTemplate(
        id = "ashlands_emberwraith",
        name = "Emberwraith",
        zoneId = "tutorial_ashlands",
        levelRange = 9..13,
        baseDanger = 5,
        description = "Invisible until the moment it strikes, an Emberwraith appears as nothing more than a heat shimmer until it materializes in a burst of flame. The fire doesn't stop after the first hit.",
        visualDescription = "Semi-transparent humanoid figure made of wavering heat and embers, flickering in and out of visibility. When visible: skeletal frame wreathed in orange-white flames, hollow burning eye sockets.",
        abilities = listOf(
            EnemyAbilityTemplate(
                name = "Phase Strike",
                bonusDamage = 5,
                cooldown = 0,
                description = "Materializes from invisibility and strikes in a burst of flame",
                damageType = DamageType.FIRE
            ),
            EnemyAbilityTemplate(
                name = "Immolation Aura",
                bonusDamage = 3,
                cooldown = 3,
                description = "Radiates intense heat, burning everything nearby over time",
                damageType = DamageType.FIRE
            ),
            EnemyAbilityTemplate(
                name = "Fade",
                bonusDamage = 0,
                cooldown = 4,
                description = "Becomes untargetable for one turn, fading back into heat shimmer"
            )
        ),
        lootTier = "elite",
        xpMultiplier = 1.3,
        immunities = setOf(DamageType.FIRE, DamageType.PHYSICAL),
        vulnerabilities = setOf(DamageType.ICE, DamageType.HOLY),
        portraitResource = "monster_ashlands_emberwraith"
    )

    private val CALDERA_TYRANT = EnemyTemplate(
        id = "ashlands_caldera_tyrant",
        name = "Caldera Tyrant",
        zoneId = "tutorial_ashlands",
        levelRange = 13..13,
        baseDanger = 9,
        description = "A creature of living stone and magma that dwells in the central caldera. When it roars, the ground splits and lava fountains erupt. It's slow, but every hit is devastating and it regenerates by consuming the lava around it.",
        visualDescription = "Towering humanoid of volcanic rock and flowing magma, molten lava pouring from cracks in its stone body. Crown of obsidian horns, fists like boulders trailing fire. The caldera glows behind it.",
        abilities = listOf(
            // Phase 1
            EnemyAbilityTemplate(
                name = "Magma Boulder",
                bonusDamage = 7,
                cooldown = 3,
                description = "Hurls a massive boulder of cooling magma at range",
                unlockPhase = 1,
                damageType = DamageType.FIRE
            ),
            EnemyAbilityTemplate(
                name = "Ground Pound",
                bonusDamage = 6,
                cooldown = 2,
                description = "Slams fists into the ground, sending a shockwave outward",
                unlockPhase = 1
            ),
            EnemyAbilityTemplate(
                name = "Lava Vein",
                bonusDamage = 0,
                cooldown = 4,
                description = "Draws lava from the caldera to regenerate health",
                unlockPhase = 1,
                damageType = DamageType.FIRE
            ),
            // Phase 2
            EnemyAbilityTemplate(
                name = "Eruption",
                bonusDamage = 9,
                cooldown = 3,
                description = "The arena erupts with geysers of lava",
                unlockPhase = 2,
                damageType = DamageType.FIRE
            ),
            // Phase 3
            EnemyAbilityTemplate(
                name = "Meltdown",
                bonusDamage = 4,
                cooldown = 0,
                description = "Radiates a continuous aura of devastating heat as its body destabilizes",
                unlockPhase = 3,
                damageType = DamageType.FIRE
            ),
            EnemyAbilityTemplate(
                name = "Desperate Slam",
                bonusDamage = 15,
                cooldown = 2,
                description = "A massive overhead slam with the force of a volcanic eruption",
                unlockPhase = 3,
                damageType = DamageType.FIRE
            )
        ),
        isBoss = true,
        bossPhases = listOf(
            BossPhase(
                phase = 1,
                hpThreshold = 1.0,
                description = "The Caldera Tyrant stands in its domain, hurling magma and shaking the earth with each step."
            ),
            BossPhase(
                phase = 2,
                hpThreshold = 0.60,
                description = "The lava in the arena begins to rise. The Tyrant draws power from the caldera itself, eruptions rocking the battlefield.",
                additionalAbilities = listOf(
                    EnemyAbilityTemplate("Eruption", 9, 3, "The arena erupts with geysers of lava", unlockPhase = 2)
                ),
                statMultiplier = 1.3
            ),
            BossPhase(
                phase = 3,
                hpThreshold = 0.25,
                description = "The Tyrant enters a berserk rage. Its body cracks and destabilizes, pouring continuous heat. Every attack is a desperate, devastating blow.",
                additionalAbilities = listOf(
                    EnemyAbilityTemplate("Meltdown", 4, 0, "Radiates a continuous aura of devastating heat as its body destabilizes", unlockPhase = 3),
                    EnemyAbilityTemplate("Desperate Slam", 15, 2, "A massive overhead slam with the force of a volcanic eruption", unlockPhase = 3)
                ),
                statMultiplier = 1.6
            )
        ),
        lootTier = "boss",
        xpMultiplier = 3.5,
        immunities = setOf(DamageType.FIRE),
        vulnerabilities = setOf(DamageType.ICE),
        resistances = setOf(DamageType.PHYSICAL),
        portraitResource = "monster_ashlands_caldera_tyrant"
    )

    // ════════════════════════════════════════════════════════════════
    // DROWNED SHELF (tutorial_drowned_shelf) — Levels 8-18
    // ════════════════════════════════════════════════════════════════

    private val REEF_RAZOR = EnemyTemplate(
        id = "drowned_reef_razor",
        name = "Reef Razor",
        zoneId = "tutorial_drowned_shelf",
        levelRange = 8..11,
        baseDanger = 3,
        description = "Fast razor-edged fish that dart through the shallows in swarms. Individually weak, but they enter a frenzy at the scent of blood, striking again and again.",
        visualDescription = "Sleek predatory fish with blade-like fins and razor-sharp gill plates, iridescent silver-blue scales, rows of needle teeth. Moves in tight coordinated schools.",
        abilities = listOf(
            EnemyAbilityTemplate(
                name = "Razor Pass",
                bonusDamage = 2,
                cooldown = 0,
                description = "Darts past the target with razor-sharp fins extended"
            ),
            EnemyAbilityTemplate(
                name = "Frenzy Swarm",
                bonusDamage = 6,
                cooldown = 3,
                description = "The swarm enters a feeding frenzy, striking multiple times in rapid succession"
            )
        ),
        lootTier = "normal",
        vulnerabilities = setOf(DamageType.LIGHTNING),
        resistances = setOf(DamageType.ICE),
        portraitResource = "monster_drowned_reef_razor"
    )

    private val ABYSSAL_REACHER = EnemyTemplate(
        id = "drowned_abyssal_reacher",
        name = "Abyssal Reacher",
        zoneId = "tutorial_drowned_shelf",
        levelRange = 10..14,
        baseDanger = 5,
        description = "A tentacled horror that lurks in tidal pools, waiting for prey to pass overhead. Responsible for more participant deaths than any other non-boss monster in the tutorial.",
        visualDescription = "Mass of dark purple-black tentacles emerging from a tidal pool, each lined with suckers and bioluminescent lures. A single massive eye visible beneath the water's surface.",
        abilities = listOf(
            EnemyAbilityTemplate(
                name = "Tentacle Grab",
                bonusDamage = 5,
                cooldown = 3,
                description = "Seizes the target with a tentacle, immobilizing and crushing"
            ),
            EnemyAbilityTemplate(
                name = "Ink Cloud",
                bonusDamage = 0,
                cooldown = 4,
                description = "Releases a cloud of ink that blinds and disorients, increasing miss chance",
                damageType = DamageType.DARK
            ),
            EnemyAbilityTemplate(
                name = "Drag Under",
                bonusDamage = 8,
                cooldown = 5,
                description = "Drags the target beneath the water for a devastating sustained attack",
                damageType = DamageType.DARK
            )
        ),
        lootTier = "elite",
        xpMultiplier = 1.3,
        vulnerabilities = setOf(DamageType.LIGHTNING, DamageType.FIRE),
        resistances = setOf(DamageType.ICE, DamageType.DARK),
        portraitResource = "monster_drowned_abyssal_reacher"
    )

    private val TIDEWALKER = EnemyTemplate(
        id = "drowned_tidewalker",
        name = "Tidewalker",
        zoneId = "tutorial_drowned_shelf",
        levelRange = 12..16,
        baseDanger = 5,
        description = "A humanoid crustacean that fights with terrifying coordination. Tidewalkers use both crushing and razor claws, and they rally themselves for sustained engagements.",
        visualDescription = "Humanoid crustacean standing upright, barnacle-encrusted carapace of blue-grey chitin, one massive crusher claw and one serrated razor claw. Compound eyes, antennae twitching.",
        abilities = listOf(
            EnemyAbilityTemplate(
                name = "Crushing Claw",
                bonusDamage = 7,
                cooldown = 2,
                description = "Brings the massive crusher claw down with bone-breaking force"
            ),
            EnemyAbilityTemplate(
                name = "Razor Claw",
                bonusDamage = 3,
                cooldown = 1,
                description = "A rapid slash with the serrated claw, leaving a bleeding wound"
            ),
            EnemyAbilityTemplate(
                name = "Rally",
                bonusDamage = 0,
                cooldown = 5,
                description = "Clicks and rattles its carapace, buffing its own attack and defense"
            )
        ),
        lootTier = "elite",
        xpMultiplier = 1.2,
        vulnerabilities = setOf(DamageType.LIGHTNING),
        resistances = setOf(DamageType.PHYSICAL, DamageType.ICE),
        portraitResource = "monster_drowned_tidewalker"
    )

    private val LEVIATHANS_MAW = EnemyTemplate(
        id = "drowned_leviathans_maw",
        name = "Leviathan's Maw",
        zoneId = "tutorial_drowned_shelf",
        levelRange = 16..16,
        baseDanger = 9,
        description = "Not a creature with a mouth — the entire trench IS the mouth. A colossal organism that lines the walls of the deepest trench, its 'teeth' are coral formations that close when prey enters.",
        visualDescription = "A living trench: walls of fleshy tissue lined with coral-tooth formations, massive tentacles extending from the walls, bioluminescent lures dangling from the ceiling. The gullet glows deep red below.",
        abilities = listOf(
            // Phase 1
            EnemyAbilityTemplate(
                name = "Tentacle Lash",
                bonusDamage = 6,
                cooldown = 2,
                description = "A massive tentacle sweeps across the trench",
                unlockPhase = 1
            ),
            EnemyAbilityTemplate(
                name = "Current Pull",
                bonusDamage = 3,
                cooldown = 3,
                description = "Generates a powerful current pulling the target toward the gullet",
                unlockPhase = 1
            ),
            EnemyAbilityTemplate(
                name = "Tooth Snap",
                bonusDamage = 8,
                cooldown = 4,
                description = "The coral-teeth formations snap shut around the target",
                unlockPhase = 1
            ),
            // Phase 2
            EnemyAbilityTemplate(
                name = "Trench Spasm",
                bonusDamage = 9,
                cooldown = 3,
                description = "The trench walls convulse, crushing everything inside",
                unlockPhase = 2
            ),
            EnemyAbilityTemplate(
                name = "Ink Flood",
                bonusDamage = 0,
                cooldown = 4,
                description = "Floods the trench with ink, drastically reducing visibility",
                unlockPhase = 2
            ),
            // Phase 3
            EnemyAbilityTemplate(
                name = "Swallow",
                bonusDamage = 20,
                cooldown = 6,
                description = "Attempts to swallow the target whole — devastating if they're immobilized",
                unlockPhase = 3
            ),
            EnemyAbilityTemplate(
                name = "Death Throes",
                bonusDamage = 5,
                cooldown = 0,
                description = "The dying organism thrashes continuously, dealing constant area damage",
                unlockPhase = 3
            )
        ),
        isBoss = true,
        bossPhases = listOf(
            BossPhase(
                phase = 1,
                hpThreshold = 1.0,
                description = "The Leviathan's Maw stirs. Tentacles lash out from the trench walls, and the current pulls you inexorably toward the gullet."
            ),
            BossPhase(
                phase = 2,
                hpThreshold = 0.70,
                description = "The trench convulses. Nerve clusters glow along the walls — destroying them is the only way to weaken the creature. Ink floods the water.",
                additionalAbilities = listOf(
                    EnemyAbilityTemplate("Trench Spasm", 9, 3, "The trench walls convulse, crushing everything inside", unlockPhase = 2),
                    EnemyAbilityTemplate("Ink Flood", 0, 4, "Floods the trench with ink, drastically reducing visibility", unlockPhase = 2)
                ),
                statMultiplier = 1.3
            ),
            BossPhase(
                phase = 3,
                hpThreshold = 0.35,
                description = "The Maw is dying but desperate. The gullet gapes wide, the current intensifies, and the organism thrashes in its death throes.",
                additionalAbilities = listOf(
                    EnemyAbilityTemplate("Swallow", 20, 6, "Attempts to swallow the target whole — devastating if they're immobilized", unlockPhase = 3),
                    EnemyAbilityTemplate("Death Throes", 5, 0, "The dying organism thrashes continuously, dealing constant area damage", unlockPhase = 3)
                ),
                statMultiplier = 1.5
            )
        ),
        lootTier = "boss",
        xpMultiplier = 3.5,
        vulnerabilities = setOf(DamageType.LIGHTNING, DamageType.FIRE),
        resistances = setOf(DamageType.ICE, DamageType.PHYSICAL),
        portraitResource = "monster_drowned_leviathans_maw"
    )

    // ════════════════════════════════════════════════════════════════
    // SPIRE (tutorial_spire) — Levels 15-25
    // ════════════════════════════════════════════════════════════════

    private val SPIRE_GUARDIAN = EnemyTemplate(
        id = "spire_guardian",
        name = "Spire Guardian",
        zoneId = "tutorial_spire",
        levelRange = 15..24,
        baseDanger = 7,
        description = "Each floor of the Spire has a unique Guardian — a crystalline construct adapted to the floor's environment. They analyze and counter the challenger's tactics.",
        visualDescription = "Humanoid crystalline construct of dark stone and glowing crystal veins, shifting form that adapts mid-combat. Faceless head with a single pulsing crystal core. Floor environment warps around it.",
        abilities = listOf(
            EnemyAbilityTemplate(
                name = "Adaptive Strike",
                bonusDamage = 7,
                cooldown = 2,
                description = "Strikes with an attack tuned to the challenger's weakest defense",
                damageType = DamageType.MAGICAL
            ),
            EnemyAbilityTemplate(
                name = "Crystal Barrier",
                bonusDamage = 0,
                cooldown = 4,
                description = "Raises a crystalline barrier that absorbs incoming damage"
            ),
            EnemyAbilityTemplate(
                name = "Floor Hazard",
                bonusDamage = 5,
                cooldown = 3,
                description = "Triggers the floor's environmental hazard against the challenger",
                damageType = DamageType.MAGICAL
            )
        ),
        lootTier = "elite",
        xpMultiplier = 2.0,
        vulnerabilities = setOf(DamageType.PHYSICAL),
        resistances = setOf(DamageType.MAGICAL),
        portraitResource = "monster_spire_guardian"
    )

    private val THE_APEX = EnemyTemplate(
        id = "spire_the_apex",
        name = "The Apex",
        zoneId = "tutorial_spire",
        levelRange = 25..25,
        baseDanger = 10,
        description = "A humanoid figure made of the same crystal as the Spire itself. It doesn't speak. It doesn't have a face. But it fights like it's studied every participant who ever entered the tutorial — because it has.",
        visualDescription = "Tall humanoid figure of pure black crystal with prismatic veins of light, faceless smooth head, proportions slightly wrong — too long, too precise. Stands on a platform in an endless void.",
        abilities = listOf(
            // Phase 1 — mirrors player, MAGICAL
            EnemyAbilityTemplate(
                name = "Mirror Strike",
                bonusDamage = 8,
                cooldown = 2,
                description = "Copies the player's last attack and reflects it back",
                unlockPhase = 1,
                damageType = DamageType.MAGICAL
            ),
            EnemyAbilityTemplate(
                name = "Crystal Shield",
                bonusDamage = 0,
                cooldown = 3,
                description = "Generates a crystalline barrier that absorbs a burst of damage",
                unlockPhase = 1
            ),
            EnemyAbilityTemplate(
                name = "Analyze",
                bonusDamage = 0,
                cooldown = 5,
                description = "Studies the player's class and patterns, buffing itself accordingly",
                unlockPhase = 1
            ),
            // Phase 2 — multi-class, mixed elemental
            EnemyAbilityTemplate(
                name = "Prismatic Beam",
                bonusDamage = 12,
                cooldown = 3,
                description = "Fires a devastating beam of concentrated prismatic energy",
                unlockPhase = 2,
                damageType = DamageType.MAGICAL
            ),
            EnemyAbilityTemplate(
                name = "Shadow Step",
                bonusDamage = 7,
                cooldown = 2,
                description = "Teleports behind the target and strikes from the blind spot",
                unlockPhase = 2,
                damageType = DamageType.DARK
            ),
            // Phase 3 — reality-breaking, TRUE damage
            EnemyAbilityTemplate(
                name = "Void Rend",
                bonusDamage = 14,
                cooldown = 2,
                description = "Tears at the fabric of space — true damage that ignores all defenses",
                unlockPhase = 3,
                damageType = DamageType.TRUE
            ),
            EnemyAbilityTemplate(
                name = "Reality Fracture",
                bonusDamage = 10,
                cooldown = 4,
                description = "Shatters the arena into fragments, dealing area damage and debuffing",
                unlockPhase = 3,
                damageType = DamageType.MAGICAL
            ),
            // Phase 4 — perfect form, TRUE damage
            EnemyAbilityTemplate(
                name = "Apex Protocol",
                bonusDamage = 15,
                cooldown = 3,
                description = "Copies the player's most powerful ability and uses it with perfect execution",
                unlockPhase = 4,
                damageType = DamageType.TRUE
            ),
            EnemyAbilityTemplate(
                name = "Final Convergence",
                bonusDamage = 25,
                cooldown = 6,
                description = "Channels all accumulated data into one massive strike of true damage",
                unlockPhase = 4,
                damageType = DamageType.TRUE
            )
        ),
        isBoss = true,
        bossPhases = listOf(
            BossPhase(
                phase = 1,
                hpThreshold = 1.0,
                description = "The Apex mirrors your stance. Every move you make, it studies. Every pattern, cataloged."
            ),
            BossPhase(
                phase = 2,
                hpThreshold = 0.80,
                description = "The Apex shifts form, adopting abilities from multiple combat disciplines. Prismatic light dances across its crystal body.",
                additionalAbilities = listOf(
                    EnemyAbilityTemplate("Prismatic Beam", 12, 3, "Fires a devastating beam of concentrated prismatic energy", unlockPhase = 2),
                    EnemyAbilityTemplate("Shadow Step", 7, 2, "Teleports behind the target and strikes from the blind spot", unlockPhase = 2)
                ),
                statMultiplier = 1.4
            ),
            BossPhase(
                phase = 3,
                hpThreshold = 0.50,
                description = "The Apex uses abilities you've never seen. The void bleeds through cracks in reality around it.",
                additionalAbilities = listOf(
                    EnemyAbilityTemplate("Void Rend", 14, 2, "Tears at the fabric of space — true damage that ignores all defenses", unlockPhase = 3),
                    EnemyAbilityTemplate("Reality Fracture", 10, 4, "Shatters the arena into fragments, dealing area damage and debuffing", unlockPhase = 3)
                ),
                statMultiplier = 1.7
            ),
            BossPhase(
                phase = 4,
                hpThreshold = 0.20,
                description = "The Apex achieves its perfect form. It moves with absolute precision, wielding your own techniques and unknowable power in equal measure.",
                additionalAbilities = listOf(
                    EnemyAbilityTemplate("Apex Protocol", 15, 3, "Copies the player's most powerful ability and uses it with perfect execution", unlockPhase = 4),
                    EnemyAbilityTemplate("Final Convergence", 25, 6, "Channels all accumulated data into one massive strike of true damage", unlockPhase = 4)
                ),
                statMultiplier = 2.0
            )
        ),
        lootTier = "boss",
        xpMultiplier = 5.0,
        // The Apex adapts — resistant to magic and elements, but raw physical force can crack crystal
        resistances = setOf(DamageType.MAGICAL, DamageType.FIRE, DamageType.ICE, DamageType.LIGHTNING, DamageType.DARK),
        portraitResource = "monster_spire_the_apex"
    )

    // ════════════════════════════════════════════════════════════════
    // HIDDEN — THE HOLLOW (tutorial_hollow) — Levels 6-10
    // ════════════════════════════════════════════════════════════════

    private val SPORE_LURKER = EnemyTemplate(
        id = "hollow_spore_lurker",
        name = "Spore Lurker",
        zoneId = "tutorial_hollow",
        levelRange = 6..10,
        baseDanger = 3,
        description = "A fungal creature that drifts through the mushroom forests of the Hollow, releasing hallucinogenic spore clouds. Its mycelium network snares the unwary, and its death is as dangerous as its life.",
        visualDescription = "Amorphous fungal mass with a cluster of bioluminescent mushroom caps as a 'head', trailing tendrils of mycelium, releasing clouds of iridescent spores. Faintly pulsing purple and green glow.",
        abilities = listOf(
            EnemyAbilityTemplate(
                name = "Spore Cloud",
                bonusDamage = 2,
                cooldown = 3,
                description = "Releases a hallucinogenic cloud that confuses and disorients",
                damageType = DamageType.POISON
            ),
            EnemyAbilityTemplate(
                name = "Mycelium Snare",
                bonusDamage = 1,
                cooldown = 4,
                description = "Mycelium threads erupt from the ground to root the target in place"
            ),
            EnemyAbilityTemplate(
                name = "Toxic Burst",
                bonusDamage = 6,
                cooldown = 0,
                description = "On death, releases a burst of toxic spores that poison nearby targets",
                damageType = DamageType.POISON
            )
        ),
        lootTier = "normal",
        xpMultiplier = 1.1,
        immunities = setOf(DamageType.POISON),
        vulnerabilities = setOf(DamageType.FIRE),
        portraitResource = "monster_hollow_spore_lurker"
    )

    // ════════════════════════════════════════════════════════════════
    // ALL TEMPLATES + BESTIARY INTERFACE
    // ════════════════════════════════════════════════════════════════

    private val ALL_ENEMIES: List<EnemyTemplate> = listOf(
        // Greenreach
        BONE_CRAWLER, CANOPY_STALKER, THORNBACK, HOLLOW_MATRIARCH,
        // Ashlands
        SLAG_BEETLE, MAGMA_SERPENT, EMBERWRAITH, CALDERA_TYRANT,
        // Drowned Shelf
        REEF_RAZOR, ABYSSAL_REACHER, TIDEWALKER, LEVIATHANS_MAW,
        // Spire
        SPIRE_GUARDIAN, THE_APEX,
        // Hidden — The Hollow
        SPORE_LURKER
    )

    override fun findEnemy(name: String, zoneId: String?): EnemyTemplate? {
        val normalized = name.lowercase().trim()
        // Exact match first
        val exactMatch = ALL_ENEMIES.firstOrNull { it.name.lowercase() == normalized }
        if (exactMatch != null) {
            return if (zoneId == null) exactMatch else {
                // Prefer zone-specific match
                ALL_ENEMIES.firstOrNull { it.name.lowercase() == normalized && it.zoneId == zoneId }
                    ?: exactMatch
            }
        }
        // Fuzzy match: check if the search term is contained in the enemy name or vice versa
        val fuzzyMatches = ALL_ENEMIES.filter {
            it.name.lowercase().contains(normalized) || normalized.contains(it.name.lowercase())
        }
        return if (zoneId != null) {
            fuzzyMatches.firstOrNull { it.zoneId == zoneId } ?: fuzzyMatches.firstOrNull()
        } else {
            fuzzyMatches.firstOrNull()
        }
    }

    override fun getEnemiesForZone(zoneId: String): List<EnemyTemplate> {
        return ALL_ENEMIES.filter { it.zoneId == zoneId }
    }

    override fun getBossForZone(zoneId: String): EnemyTemplate? {
        return ALL_ENEMIES.firstOrNull { it.zoneId == zoneId && it.isBoss }
    }
}
