package com.rpgenerator.core.domain

import kotlinx.serialization.Serializable

/**
 * Class archetype categories for grouping similar playstyles
 */
@Serializable
enum class ClassArchetype(val displayName: String) {
    UNALIGNED("Unaligned"),
    COMBAT("Combat"),
    MYSTICAL("Mystical"),
    HYBRID("Hybrid"),
    SUPPORT("Support"),
    UNIQUE("Unique")
}

/**
 * Grade/Tier system for progression
 * Inspired by Defiance of the Fall, Primal Hunter, etc.
 */
@Serializable
enum class Grade(
    val displayName: String,
    val levelRange: IntRange,
    val description: String
) {
    E_GRADE(
        displayName = "E-Grade",
        levelRange = 1..25,
        description = "Tutorial tier. Basic skills and foundation building."
    ),
    D_GRADE(
        displayName = "D-Grade",
        levelRange = 26..100,
        description = "First evolution. Return to Earth. Specialized paths and class evolution."
    ),
    C_GRADE(
        displayName = "C-Grade",
        levelRange = 101..200,
        description = "Advanced tier. Skill mastery and path refinement."
    ),
    B_GRADE(
        displayName = "B-Grade",
        levelRange = 201..350,
        description = "Expert tier. Dao comprehension and power consolidation."
    ),
    A_GRADE(
        displayName = "A-Grade",
        levelRange = 351..500,
        description = "Master tier. World-shaking powers emerge."
    ),
    S_GRADE(
        displayName = "S-Grade",
        levelRange = 501..1000,
        description = "Transcendent tier. Peak of mortal achievement."
    );

    companion object {
        fun fromLevel(level: Int): Grade {
            return values().firstOrNull { level in it.levelRange } ?: E_GRADE
        }

        fun isGradeUp(oldLevel: Int, newLevel: Int): Boolean {
            val oldGrade = fromLevel(oldLevel)
            val newGrade = fromLevel(newLevel)
            return newGrade.ordinal > oldGrade.ordinal
        }
    }
}

/**
 * Class archetype - the base path a player chooses
 * Each class has different evolution options at tier-ups
 *
 * Classes are inspired by various LitRPG subgenres:
 * - System Apocalypse (combat/survival focused)
 * - Cultivation (internal energy paths)
 * - Tower/Dungeon (specialized combat roles)
 * - Crafting/Support (non-combat mastery)
 * - Unique paths (rare/exotic options)
 */
@Serializable
internal enum class PlayerClass(
    val displayName: String,
    val description: String,
    internal val statBonuses: Stats,
    val archetype: ClassArchetype = ClassArchetype.COMBAT
) {
    NONE(
        displayName = "Classless",
        description = "The System has not yet assigned you a path. Your choices will shape your destiny.",
        statBonuses = Stats(0, 0, 0, 0, 0, 0),
        archetype = ClassArchetype.UNALIGNED
    ),

    // === COMBAT ARCHETYPES ===
    SLAYER(
        displayName = "Slayer",
        description = "Kill count is everything. Each enemy defeated makes you stronger. The System rewards those who embrace violence.",
        statBonuses = Stats(strength = 4, dexterity = 4, constitution = 2),
        archetype = ClassArchetype.COMBAT
    ),
    BULWARK(
        displayName = "Bulwark",
        description = "An immovable object. Your body becomes your fortress, protecting yourself and those behind you.",
        statBonuses = Stats(constitution = 6, strength = 3, wisdom = 1),
        archetype = ClassArchetype.COMBAT
    ),
    STRIKER(
        displayName = "Striker",
        description = "Speed kills. Hit fast, hit hard, don't get hit back. Mobility is survival.",
        statBonuses = Stats(dexterity = 6, strength = 2, intelligence = 2),
        archetype = ClassArchetype.COMBAT
    ),

    // === MYSTICAL ARCHETYPES ===
    CHANNELER(
        displayName = "Channeler",
        description = "The System's energy flows through you. Shape mana, bend reality, unleash devastation.",
        statBonuses = Stats(intelligence = 5, wisdom = 4, charisma = 1),
        archetype = ClassArchetype.MYSTICAL
    ),
    CULTIVATOR(
        displayName = "Cultivator",
        description = "Refine your inner energy. Strengthen your foundation. The path to immortality begins with a single breath.",
        statBonuses = Stats(wisdom = 5, constitution = 3, intelligence = 2),
        archetype = ClassArchetype.MYSTICAL
    ),
    PSION(
        displayName = "Psion",
        description = "The mind is the ultimate weapon. Telekinesis, telepathy, precognition—thought becomes reality.",
        statBonuses = Stats(intelligence = 4, wisdom = 4, charisma = 2),
        archetype = ClassArchetype.MYSTICAL
    ),

    // === HYBRID ARCHETYPES ===
    ADAPTER(
        displayName = "Adapter",
        description = "No fixed path. Learn from everything. The jack of all trades who becomes master of adaptation.",
        statBonuses = Stats(intelligence = 3, wisdom = 3, dexterity = 2, strength = 2),
        archetype = ClassArchetype.HYBRID
    ),
    SURVIVALIST(
        displayName = "Survivalist",
        description = "When the world ends, you endure. Track, hunt, trap, heal—whatever it takes to see tomorrow.",
        statBonuses = Stats(constitution = 4, wisdom = 3, dexterity = 3),
        archetype = ClassArchetype.HYBRID
    ),
    BLADE_DANCER(
        displayName = "Blade Dancer",
        description = "Combat as art. Weapons as extensions of will. Every fight is a performance, and death is beautiful.",
        statBonuses = Stats(dexterity = 5, charisma = 3, strength = 2),
        archetype = ClassArchetype.HYBRID
    ),

    // === SUPPORT ARCHETYPES ===
    ARTIFICER(
        displayName = "Artificer",
        description = "Build. Create. Improve. The System provides blueprints—you provide the genius to break its limits.",
        statBonuses = Stats(intelligence = 5, dexterity = 3, wisdom = 2),
        archetype = ClassArchetype.SUPPORT
    ),
    HEALER(
        displayName = "Healer",
        description = "Life is sacred. Mend wounds, cure ailments, restore what was broken. In a world of death, you bring hope.",
        statBonuses = Stats(wisdom = 5, charisma = 3, constitution = 2),
        archetype = ClassArchetype.SUPPORT
    ),
    COMMANDER(
        displayName = "Commander",
        description = "Lead from the front or direct from behind. Your presence inspires, your tactics devastate.",
        statBonuses = Stats(charisma = 5, intelligence = 3, wisdom = 2),
        archetype = ClassArchetype.SUPPORT
    ),

    // === UNIQUE ARCHETYPES ===
    CONTRACTOR(
        displayName = "Contractor",
        description = "Entities beyond the System offer power—for a price. Bind spirits, demons, or stranger things to your will.",
        statBonuses = Stats(charisma = 4, wisdom = 4, intelligence = 2),
        archetype = ClassArchetype.UNIQUE
    ),
    GLITCH(
        displayName = "Glitch",
        description = "Something went wrong during Integration. You exist between System rules. Exploit the errors.",
        statBonuses = Stats(intelligence = 4, dexterity = 4, wisdom = 2),
        archetype = ClassArchetype.UNIQUE
    ),
    ECHO(
        displayName = "Echo",
        description = "Memories of the old world cling to you. Channel the skills and powers of who you were—and who you could have been.",
        statBonuses = Stats(wisdom = 4, charisma = 3, constitution = 3),
        archetype = ClassArchetype.UNIQUE
    );

    fun getEvolutionOptions(currentGrade: Grade): List<ClassEvolution> {
        return when (this) {
            NONE -> emptyList()

            // Combat Classes
            SLAYER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Reaper", "Death itself follows in your wake"),
                    ClassEvolution("Berserker", "Pain becomes power, rage becomes strength"),
                    ClassEvolution("Executioner", "One strike, one kill—efficiency perfected")
                )
                else -> emptyList()
            }
            BULWARK -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Juggernaut", "Unstoppable force meets immovable object—you are both"),
                    ClassEvolution("Sentinel", "Guardian of the weak, bane of the wicked"),
                    ClassEvolution("Fortress", "Your body becomes literal armor")
                )
                else -> emptyList()
            }
            STRIKER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Phantom", "So fast you're barely real"),
                    ClassEvolution("Duelist", "Every fight is a dance, and you lead"),
                    ClassEvolution("Tempest", "A storm of blades and motion")
                )
                else -> emptyList()
            }

            // Mystical Classes
            CHANNELER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Elementalist", "Fire, ice, lightning—all bow to your will"),
                    ClassEvolution("Void Mage", "Channel the emptiness between stars"),
                    ClassEvolution("Arcane Savant", "Pure magical theory made devastating practice")
                )
                else -> emptyList()
            }
            CULTIVATOR -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Sword Saint", "Your blade is your dao, your dao is your blade"),
                    ClassEvolution("Body Refiner", "Forge flesh into something beyond mortal"),
                    ClassEvolution("Qi Master", "Internal energy flows like a raging river")
                )
                else -> emptyList()
            }
            PSION -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Mindbreaker", "Shatter mental defenses like glass"),
                    ClassEvolution("Telekinetic", "Move mountains with a thought"),
                    ClassEvolution("Oracle", "See what was, is, and will be")
                )
                else -> emptyList()
            }

            // Hybrid Classes
            ADAPTER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Mimic", "Every skill you see becomes yours"),
                    ClassEvolution("Polymath", "Master of many, slave to none"),
                    ClassEvolution("Synthesis", "Combine incompatible powers into something new")
                )
                else -> emptyList()
            }
            SURVIVALIST -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Apex Predator", "Top of every food chain"),
                    ClassEvolution("Wayfinder", "No terrain is impassable, no trail invisible"),
                    ClassEvolution("Alchemist", "Nature's secrets become your weapons")
                )
                else -> emptyList()
            }
            BLADE_DANCER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Weapon Maestro", "Every weapon sings in your hands"),
                    ClassEvolution("Spell Sword", "Magic and steel intertwined"),
                    ClassEvolution("Wind Dancer", "Move like air, strike like thunder")
                )
                else -> emptyList()
            }

            // Support Classes
            ARTIFICER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Runesmith", "Inscribe power into permanent form"),
                    ClassEvolution("Golem Maker", "Build allies from nothing"),
                    ClassEvolution("Technomancer", "Merge System tech with your creations")
                )
                else -> emptyList()
            }
            HEALER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Life Weaver", "Restore even the nearly dead"),
                    ClassEvolution("Combat Medic", "Heal and harm in equal measure"),
                    ClassEvolution("Purifier", "Cleanse corruption, cure the incurable")
                )
                else -> emptyList()
            }
            COMMANDER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Warlord", "Your army moves as one devastating unit"),
                    ClassEvolution("Tactician", "Predict and counter every enemy move"),
                    ClassEvolution("Champion", "Lead by example, inspire by presence")
                )
                else -> emptyList()
            }

            // Unique Classes
            CONTRACTOR -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Demon Binder", "The abyss serves those bold enough to demand"),
                    ClassEvolution("Spirit Caller", "The dead answer your summons"),
                    ClassEvolution("Pact Master", "Multiple contracts, multiple powers")
                )
                else -> emptyList()
            }
            GLITCH -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("System Breaker", "The rules bend around you"),
                    ClassEvolution("Anomaly", "You shouldn't exist—use that"),
                    ClassEvolution("Debugger", "Find and exploit every flaw")
                )
                else -> emptyList()
            }
            ECHO -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ClassEvolution("Past Life Inheritor", "Your previous selves grant their power"),
                    ClassEvolution("Memory Thief", "Steal skills from others' pasts"),
                    ClassEvolution("Timeline Walker", "Step between what was and what is")
                )
                else -> emptyList()
            }
        }
    }

    companion object {
        /** Get classes grouped by archetype for display */
        fun byArchetype(): Map<ClassArchetype, List<PlayerClass>> {
            return values()
                .filter { it != NONE }
                .groupBy { it.archetype }
        }

        /** Get all selectable classes (excluding NONE) */
        fun selectableClasses(): List<PlayerClass> = values().filter { it != NONE }
    }
}

/**
 * Profession system — non-combat specializations that complement the combat class.
 * Professions grant crafting, gathering, and utility abilities.
 * Available across all seed types with different flavor.
 */
@Serializable
internal enum class Profession(
    val displayName: String,
    val description: String,
    internal val statBonuses: Stats,
    val category: ProfessionCategory = ProfessionCategory.CRAFTING
) {
    NONE(
        displayName = "None",
        description = "No profession selected yet.",
        statBonuses = Stats(0, 0, 0, 0, 0, 0),
        category = ProfessionCategory.NONE
    ),

    // === CRAFTING ===
    WEAPONSMITH(
        displayName = "Weaponsmith",
        description = "Forge blades, bows, and bludgeons. Repair and enhance weapons beyond their base quality.",
        statBonuses = Stats(strength = 2, constitution = 1, dexterity = 1),
        category = ProfessionCategory.CRAFTING
    ),
    ARMORSMITH(
        displayName = "Armorsmith",
        description = "Shape metal and leather into protection. Heavy plates, light leathers, shields — all bend to your hammer.",
        statBonuses = Stats(constitution = 2, strength = 2),
        category = ProfessionCategory.CRAFTING
    ),
    ALCHEMIST(
        displayName = "Alchemist",
        description = "Brew potions, poisons, and elixirs. Transmute base materials into something extraordinary.",
        statBonuses = Stats(intelligence = 2, wisdom = 2),
        category = ProfessionCategory.CRAFTING
    ),
    ENCHANTER(
        displayName = "Enchanter",
        description = "Imbue items with magical properties. Turn mundane gear into legendary artifacts.",
        statBonuses = Stats(intelligence = 3, wisdom = 1),
        category = ProfessionCategory.CRAFTING
    ),
    RUNECRAFTER(
        displayName = "Runecrafter",
        description = "Inscribe runes of power onto surfaces, gear, and even skin. Ancient symbols, modern devastation.",
        statBonuses = Stats(intelligence = 2, dexterity = 1, wisdom = 1),
        category = ProfessionCategory.CRAFTING
    ),
    COOK(
        displayName = "Cook",
        description = "Transform raw ingredients into meals that grant temporary buffs. Good food wins wars.",
        statBonuses = Stats(wisdom = 2, constitution = 1, charisma = 1),
        category = ProfessionCategory.CRAFTING
    ),
    LEATHERWORKER(
        displayName = "Leatherworker",
        description = "Tan hides, stitch armor, craft bags and belts. Monster leather is tougher than steel if you cure it right.",
        statBonuses = Stats(dexterity = 2, constitution = 1, wisdom = 1),
        category = ProfessionCategory.CRAFTING
    ),
    JEWELER(
        displayName = "Jeweler",
        description = "Cut gems, set stones, craft rings and amulets. Small objects with outsized power.",
        statBonuses = Stats(dexterity = 2, intelligence = 2),
        category = ProfessionCategory.CRAFTING
    ),
    TINKERER(
        displayName = "Tinkerer",
        description = "Build gadgets, traps, and mechanical devices. Where magic meets engineering, you thrive.",
        statBonuses = Stats(intelligence = 2, dexterity = 2),
        category = ProfessionCategory.CRAFTING
    ),
    TAILOR(
        displayName = "Tailor",
        description = "Sew enchanted cloth, robes, and cloaks. The thread remembers what you weave into it.",
        statBonuses = Stats(dexterity = 2, charisma = 1, wisdom = 1),
        category = ProfessionCategory.CRAFTING
    ),

    // === GATHERING ===
    HERBALIST(
        displayName = "Herbalist",
        description = "Identify and harvest plants, fungi, and natural reagents. The wilds are your pharmacy.",
        statBonuses = Stats(wisdom = 3, constitution = 1),
        category = ProfessionCategory.GATHERING
    ),
    MINER(
        displayName = "Miner",
        description = "Extract ores, gems, and crystals from stone. You see veins others walk past.",
        statBonuses = Stats(strength = 2, constitution = 2),
        category = ProfessionCategory.GATHERING
    ),
    SCAVENGER(
        displayName = "Scavenger",
        description = "Find useful salvage in ruins, corpses, and wreckage. One person's trash is your next upgrade.",
        statBonuses = Stats(dexterity = 2, wisdom = 1, intelligence = 1),
        category = ProfessionCategory.GATHERING
    ),
    HUNTER(
        displayName = "Hunter",
        description = "Track, trap, and skin. You know where creatures go, what they eat, and how to make use of every part.",
        statBonuses = Stats(dexterity = 2, wisdom = 2),
        category = ProfessionCategory.GATHERING
    ),
    LUMBERJACK(
        displayName = "Lumberjack",
        description = "Fell trees, process timber, identify wood types. System-enhanced lumber can be harder than stone.",
        statBonuses = Stats(strength = 3, constitution = 1),
        category = ProfessionCategory.GATHERING
    ),
    FISHER(
        displayName = "Fisher",
        description = "Pull things from water that have no business existing. System-touched fish grant buffs. The big ones fight back.",
        statBonuses = Stats(wisdom = 2, dexterity = 1, constitution = 1),
        category = ProfessionCategory.GATHERING
    ),

    // === UTILITY ===
    SCRIBE(
        displayName = "Scribe",
        description = "Record, translate, and create scrolls. Knowledge is power — literally, when written in the right ink.",
        statBonuses = Stats(intelligence = 3, charisma = 1),
        category = ProfessionCategory.UTILITY
    ),
    HEALER_PROF(
        displayName = "Field Medic",
        description = "Treat wounds, set bones, cure disease. Not magic healing — real medicine backed by System knowledge.",
        statBonuses = Stats(wisdom = 2, intelligence = 1, constitution = 1),
        category = ProfessionCategory.UTILITY
    ),
    BUILDER(
        displayName = "Builder",
        description = "Construct shelters, fortifications, and infrastructure. Build what the world needs to survive.",
        statBonuses = Stats(strength = 1, constitution = 2, intelligence = 1),
        category = ProfessionCategory.UTILITY
    ),
    MERCHANT(
        displayName = "Merchant",
        description = "Buy low, sell high, appraise everything. Networks and negotiation are your greatest tools.",
        statBonuses = Stats(charisma = 3, intelligence = 1),
        category = ProfessionCategory.UTILITY
    ),
    CARTOGRAPHER(
        displayName = "Cartographer",
        description = "Map the unmapped. Dungeons, zones, incursion rifts — you see the patterns others miss.",
        statBonuses = Stats(intelligence = 2, wisdom = 1, dexterity = 1),
        category = ProfessionCategory.UTILITY
    ),
    FARMER(
        displayName = "Farmer",
        description = "Grow crops in System-touched soil. Mana-infused wheat, stat-boosting vegetables, herbs that shouldn't exist.",
        statBonuses = Stats(wisdom = 2, constitution = 2),
        category = ProfessionCategory.UTILITY
    ),
    BREWER(
        displayName = "Brewer",
        description = "Ferment, distill, and bottle. Ale that heals, wine that buffs, spirits that burn with actual fire.",
        statBonuses = Stats(wisdom = 2, charisma = 1, constitution = 1),
        category = ProfessionCategory.UTILITY
    ),
    BEAST_TAMER(
        displayName = "Beast Tamer",
        description = "Bond with monsters and animals. Train mounts, companions, and guard beasts. They fight for you because they choose to.",
        statBonuses = Stats(charisma = 2, wisdom = 2),
        category = ProfessionCategory.UTILITY
    );

    fun getEvolutionOptions(currentGrade: Grade): List<ProfessionEvolution> {
        return when (this) {
            NONE -> emptyList()
            WEAPONSMITH -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Master Forgewright", "Craft weapons that hum with latent power"),
                    ProfessionEvolution("Siege Engineer", "Build weapons of war, not just war's weapons")
                )
                else -> emptyList()
            }
            ARMORSMITH -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Wardsmith", "Armor that protects against magic and blade alike"),
                    ProfessionEvolution("Living Armorer", "Craft armor that grows with its wearer")
                )
                else -> emptyList()
            }
            ALCHEMIST -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Transmuter", "Change one substance into another at will"),
                    ProfessionEvolution("Plague Doctor", "Poisons and cures — two sides of the same flask")
                )
                else -> emptyList()
            }
            ENCHANTER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Artifex", "Create magical items from raw mana"),
                    ProfessionEvolution("Disenchanter", "Strip and recombine enchantments")
                )
                else -> emptyList()
            }
            RUNECRAFTER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Sigil Master", "Runes that rewrite reality's rules"),
                    ProfessionEvolution("Tattoo Artist", "Inscribe power directly onto living flesh")
                )
                else -> emptyList()
            }
            COOK -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Feast Keeper", "Feed armies. Your meals grant lasting power"),
                    ProfessionEvolution("Monster Chef", "Rare ingredients unlock impossible recipes")
                )
                else -> emptyList()
            }
            HERBALIST -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Druid Botanist", "Plants grow at your command"),
                    ProfessionEvolution("Toxicologist", "Extract the deadliest compounds nature offers")
                )
                else -> emptyList()
            }
            MINER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Deep Delver", "Find veins in dimensions others can't reach"),
                    ProfessionEvolution("Crystal Shaper", "Raw gems become conduits of power")
                )
                else -> emptyList()
            }
            SCAVENGER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Relic Hunter", "Find artifacts hidden by the System itself"),
                    ProfessionEvolution("Salvage Savant", "Reconstruct destroyed items from fragments")
                )
                else -> emptyList()
            }
            SCRIBE -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Lorekeeper", "Written words carry binding power"),
                    ProfessionEvolution("Scroll Weaver", "Create spell scrolls usable by anyone")
                )
                else -> emptyList()
            }
            HEALER_PROF -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Combat Surgeon", "Operate mid-battle. Scalpel in one hand, sword in the other"),
                    ProfessionEvolution("Plague Warden", "Prevent and cure what magic cannot touch")
                )
                else -> emptyList()
            }
            BUILDER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Architect", "Design structures the System reinforces"),
                    ProfessionEvolution("Siege Breaker", "What you build, you can also tear down")
                )
                else -> emptyList()
            }
            MERCHANT -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Trade Baron", "Your network spans dimensions"),
                    ProfessionEvolution("Black Marketeer", "Access to items that shouldn't exist")
                )
                else -> emptyList()
            }
            LEATHERWORKER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Hide Whisperer", "Monster hides retain their creature's abilities"),
                    ProfessionEvolution("Skin Binder", "Bind enchantments into leather at the molecular level")
                )
                else -> emptyList()
            }
            JEWELER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Gem Resonator", "Gems amplify magic channeled through them"),
                    ProfessionEvolution("Soul Setter", "Trap essences in jewelry for permanent buffs")
                )
                else -> emptyList()
            }
            TINKERER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Machinist", "Build autonomous constructs that fight and build"),
                    ProfessionEvolution("Trap Master", "Your devices reshape battlefields before combat begins")
                )
                else -> emptyList()
            }
            TAILOR -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Thread Weaver", "Cloth armor that rivals plate through enchanted fibers"),
                    ProfessionEvolution("Banner Smith", "Craft war banners that buff allies in range")
                )
                else -> emptyList()
            }
            HUNTER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Trophy Hunter", "Monster parts you harvest carry residual power"),
                    ProfessionEvolution("Warden", "Mark territory. Nothing enters without you knowing")
                )
                else -> emptyList()
            }
            LUMBERJACK -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Heartwood Cutter", "Harvest living wood that still grows after shaping"),
                    ProfessionEvolution("Demolisher", "Bring down structures — and the things hiding in them")
                )
                else -> emptyList()
            }
            FISHER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Deep Angler", "Fish in waters between dimensions"),
                    ProfessionEvolution("Tide Reader", "Predict weather, currents, and what swims below")
                )
                else -> emptyList()
            }
            CARTOGRAPHER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Pathfinder", "Your maps reveal hidden passages the System tried to erase"),
                    ProfessionEvolution("Rift Mapper", "Chart dimensional boundaries and predict incursions")
                )
                else -> emptyList()
            }
            FARMER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Mana Cultivator", "Grow crops that channel pure System energy"),
                    ProfessionEvolution("Grove Keeper", "Your farmland becomes a living defensive structure")
                )
                else -> emptyList()
            }
            BREWER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Elixir Master", "Brew concoctions that temporarily grant skills"),
                    ProfessionEvolution("Spirit Distiller", "Extract monster essences into drinkable power")
                )
                else -> emptyList()
            }
            BEAST_TAMER -> when (currentGrade) {
                Grade.D_GRADE -> listOf(
                    ProfessionEvolution("Pack Leader", "Command multiple beasts as a coordinated unit"),
                    ProfessionEvolution("Symbiont", "Merge temporarily with your bonded creature")
                )
                else -> emptyList()
            }
        }
    }

    companion object {
        fun byCategory(): Map<ProfessionCategory, List<Profession>> {
            return values()
                .filter { it != NONE }
                .groupBy { it.category }
        }

        fun selectableProfessions(): List<Profession> = values().filter { it != NONE }
    }
}

@Serializable
enum class ProfessionCategory(val displayName: String) {
    NONE("None"),
    CRAFTING("Crafting"),
    GATHERING("Gathering"),
    UTILITY("Utility")
}

@Serializable
internal data class ProfessionEvolution(
    val name: String,
    val description: String,
    internal val statModifiers: Stats = Stats(0, 0, 0, 0, 0, 0)
)

/**
 * A fully dynamic class definition. The GM generates these — no hardcoded list.
 * PlayerClass enum remains as the internal archetype for combat math only.
 */
@Serializable
internal data class DynamicClassInfo(
    val name: String,
    val description: String,
    val archetype: ClassArchetype = ClassArchetype.COMBAT,
    val traits: List<ClassTrait> = emptyList(),
    val evolutionHints: List<String> = emptyList(),  // Narrative hints for future class evolution
    val physicalMutations: List<String> = emptyList() // Visual/physical changes (e.g. "eyes glow faintly blue")
)

/**
 * A class trait — passive bonus, unique ability, or flavor that shapes gameplay.
 */
@Serializable
internal data class ClassTrait(
    val name: String,
    val description: String,
    val mechanicalEffect: String = "",  // Short machine-readable effect (e.g. "+2 STR in darkness", "regen 1 HP/turn")
    val isPhysical: Boolean = false     // Does this trait change the character's body?
)

/**
 * Evolution choice at tier-up
 */
@Serializable
internal data class ClassEvolution(
    val name: String,
    val description: String,
    internal val statModifiers: Stats = Stats(0, 0, 0, 0, 0, 0)
)

/**
 * Tier-up event when player reaches a new grade
 */
@Serializable
internal data class TierUpEvent(
    val oldGrade: Grade,
    val newGrade: Grade,
    val classEvolutionOptions: List<ClassEvolution>,
    val skillSlotUnlocked: Boolean,
    val statPointsAwarded: Int,
    val systemMessage: String
) {
    companion object {
        internal fun create(oldLevel: Int, newLevel: Int, currentClass: PlayerClass): TierUpEvent? {
            if (!Grade.isGradeUp(oldLevel, newLevel)) return null

            val oldGrade = Grade.fromLevel(oldLevel)
            val newGrade = Grade.fromLevel(newLevel)

            val evolutionOptions = if (currentClass == PlayerClass.NONE && newGrade == Grade.D_GRADE) {
                // First class selection at D-Grade
                listOf(
                    ClassEvolution("Warrior", "Path of martial might"),
                    ClassEvolution("Mage", "Path of arcane power"),
                    ClassEvolution("Rogue", "Path of precision and shadows"),
                    ClassEvolution("Ranger", "Path of wilderness mastery"),
                    ClassEvolution("Cultivator", "Path of internal refinement")
                )
            } else {
                currentClass.getEvolutionOptions(newGrade)
            }

            return TierUpEvent(
                oldGrade = oldGrade,
                newGrade = newGrade,
                classEvolutionOptions = evolutionOptions,
                skillSlotUnlocked = true,
                statPointsAwarded = when (newGrade) {
                    Grade.D_GRADE -> 10
                    Grade.C_GRADE -> 20
                    Grade.B_GRADE -> 30
                    Grade.A_GRADE -> 50
                    Grade.S_GRADE -> 100
                    else -> 0
                },
                systemMessage = """
                    ╔════════════════════════════════════════╗
                    ║  GRADE ADVANCEMENT: ${newGrade.displayName}
                    ║  ${newGrade.description}
                    ╚════════════════════════════════════════╝

                    You have transcended ${oldGrade.displayName} and entered ${newGrade.displayName}.
                    The System recognizes your achievements.

                    Choices await.
                """.trimIndent()
            )
        }
    }
}
