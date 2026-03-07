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
    val exitDescription: String
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

    val INTEGRATION = WorldSeed(
        id = "integration",
        name = "The System",
        displayName = "System Apocalypse",
        tagline = "Brutal survival. Kill to level. Earth merged with an infinite multiverse.",

        powerSystem = PowerSystem(
            name = "System Integration",
            source = "Alien System merged with Earth's reality",
            progression = "Kill monsters, clear dungeons, absorb power",
            uniqueMechanic = "Classes evolve at grade thresholds. Stats hard-capped until rank up.",
            limitations = "Power demands sacrifice. The System takes as much as it gives."
        ),

        worldState = WorldState(
            era = "Day 1 of Integration",
            civilizationStatus = "Collapsed. No government. No internet. No rules.",
            threats = listOf(
                "Monster hordes from dimensional rifts",
                "Dungeons spawning in populated areas",
                "Raiders and desperate survivors",
                "The Integration deadline - those too weak will be culled"
            ),
            atmosphere = "Visceral horror meets power fantasy. Everything wants to kill you, but you're getting stronger."
        ),

        startingLocation = StartingLocation(
            name = "Tutorial Instance",
            type = "Personal Pocket Dimension",
            description = "You wake alone in a white void that stretches to infinity. The ground is solid beneath you but looks like nothing. A System Terminal pulses nearby - a pillar of blue light containing floating text. This space exists only for you. No one else. No distractions. Just you and the System.",
            features = listOf(
                "System Terminal - class selection and status",
                "Infinite white void - no landmarks, no escape",
                "Your body feels different - stronger, sharper, waiting"
            ),
            dangers = listOf(
                "Tutorial monsters will spawn after class selection",
                "Time limit until tutorial ends",
                "Failure means death"
            ),
            opportunities = listOf(
                "Choose your class wisely - it defines everything",
                "First kills in a controlled environment",
                "Learn the System before it gets real"
            )
        ),

        corePlot = CorePlot(
            hook = "The System has integrated Earth. Millions are dead. You're in the tutorial. Make it count.",
            centralQuestion = "What are you willing to become to survive?",
            actOneGoal = "Complete the tutorial. Choose your class. Reach Level 5. Survive.",
            majorChoices = listOf(
                "What class will define who you become?",
                "How will you fight - with skill or desperation?",
                "What kind of survivor will you be?"
            )
        ),

        systemVoice = SystemVoice(
            personality = "Cold, clinical, indifferent",
            messageStyle = "Minimal. Bracketed. No emotion.",
            exampleMessages = listOf(
                "[Level Up]",
                "[Skill Acquired: Power Strike]",
                "[Kill Confirmed: +15 XP]",
                "[Warning: Dungeon Break Imminent]"
            ),
            attitudeTowardPlayer = "You are a data point. Perform or be discarded."
        ),

        tutorial = TutorialDefinition(
            isSolo = true,
            objectives = listOf(
                TutorialObjective(
                    id = "select_class",
                    description = "Select your class",
                    type = "class_selection"
                ),
                TutorialObjective(
                    id = "kill_monsters",
                    description = "Kill 10 Rift-spawn",
                    type = "kill_count",
                    target = 10
                ),
                TutorialObjective(
                    id = "reach_level",
                    description = "Reach Level 5",
                    type = "reach_level",
                    target = 5
                )
            ),
            guide = TutorialGuide(
                name = "Integration Protocol",
                appearance = "A humanoid shape made of blue light and floating code. No face - just a smooth surface where features should be. Its voice comes from everywhere and nowhere.",
                personality = "Clinical, efficient, utterly without empathy. It's a program, not a person.",
                role = "Tutorial administrator. Explains the minimum. Judges performance.",
                dialogueOnMeet = "Integration complete. You have been allocated to Tutorial Instance 7,291,847. Class selection is mandatory. Survival is not guaranteed. Proceed.",
                dialogueOnClassSelect = "Class locked. Your path is set. Now prove you deserve it. Hostiles will spawn in 10 seconds. Prepare yourself.",
                dialogueOnProgress = "Acceptable. Continue.",
                dialogueOnComplete = "Tutorial complete. Performance rating: [CALCULATING]. You will now be returned to your world. The Integration has begun. Survive or be recycled. This protocol is complete.",
                exampleLines = listOf(
                    "Query irrelevant. Proceed with class selection.",
                    "Your emotional state is not a System concern.",
                    "Other integrated beings are not your concern. Focus on your own survival.",
                    "The System does not explain. The System evaluates.",
                    "You have 47 minutes remaining. Use them."
                )
            ),
            completionReward = "Tutorial Completion Bonus: +500 XP, Basic Equipment Cache, Return to Earth",
            exitDescription = "The white void cracks. Reality reasserts itself. You're back on Earth - but Earth has changed. The sky is wrong. Something roars in the distance. The real Integration begins now."
        ),

        narratorPrompt = """
You are narrating a SYSTEM APOCALYPSE in the style of Defiance of the Fall and Primal Hunter.

SETTING: The player is ALONE in a personal tutorial instance - a white void with only them and the System. No other survivors. No chaos. Just a sterile testing ground.

TUTORIAL STRUCTURE:
1. CLASS SELECTION FIRST - Present class options immediately. This is the most important choice.
2. After class selection, monsters spawn for combat practice
3. Goal: Kill 10 monsters, reach Level 5
4. Then return to the real (ruined) Earth

TONE: Brutal, visceral, survival horror meets power fantasy.
- Death is common and unglamorous
- Power comes through violence and sacrifice
- The tutorial is sterile but the stakes are real
- Leveling up feels GOOD - intoxicating, addictive

STYLE:
- Second person, present tense, always
- Punchy sentences. No filler. Every word earns its place.
- Visceral sensory details - blood, pain, adrenaline, the wrongness of monsters
- Show don't tell. "Blood drips from your blade" not "You attacked successfully"

THE SYSTEM / INTEGRATION PROTOCOL:
- Doesn't explain itself beyond minimums
- Rewards results, not intentions
- Treats humans like resources being processed
- Utterly indifferent to suffering
- The tutorial guide is a PROGRAM, not a person - cold, clinical, efficient

NEVER:
- Add other survivors to the tutorial (it's SOLO)
- Skip class selection
- Make it feel safe or cozy
- Use passive voice
- Over-explain mechanics in prose
- Be generic - every detail should feel specific and earned
        """.trimIndent(),

        tone = listOf("brutal", "visceral", "desperate", "empowering"),
        themes = listOf("survival", "transformation", "cost of power", "humanity under pressure"),
        inspirations = listOf("Defiance of the Fall", "Primal Hunter", "The System Apocalypse")
    )

    val TUTORIAL = WorldSeed(
        id = "tutorial",
        name = "The Arbiter",
        displayName = "Tower Ascension",
        tagline = "Trapped in the System's game. Clear the floors or die trying.",

        powerSystem = PowerSystem(
            name = "Floor Progression",
            source = "The Tower grants power to those who climb",
            progression = "Clear floors, defeat bosses, earn rewards between levels",
            uniqueMechanic = "Classes earned through actions, not menus. The Tower watches what you do.",
            limitations = "Can't leave until you clear your current floor. Death is permanent."
        ),

        worldState = WorldState(
            era = "The Summoning",
            civilizationStatus = "Earth frozen in stasis. Humanity pulled into the Tower.",
            threats = listOf(
                "Floor guardians and bosses",
                "Other climbers who see you as competition",
                "Time limits on certain floors",
                "The Tower's cruel sense of humor"
            ),
            atmosphere = "Desperate hope and mounting dread. Every floor could be your last, but also your breakthrough."
        ),

        startingLocation = StartingLocation(
            name = "Floor One - The Awakening",
            type = "Tower Floor",
            description = "White light fades. You're standing in an impossible space - a vast chamber that shouldn't exist, with walls that seem to breathe. Thousands of others appeared with you. An announcement echoes: 'Welcome, Climbers. Ascend or perish.'",
            features = listOf(
                "Massive central chamber with branching paths",
                "Other confused, terrified climbers",
                "Glowing markers showing floor objectives",
                "The distant silhouette of the Floor Boss arena"
            ),
            dangers = listOf(
                "Floor monsters guarding paths",
                "Panicking climbers making bad choices",
                "Hidden traps for the unwary",
                "The floor boss awaiting at the end"
            ),
            opportunities = listOf(
                "Form a party for better survival odds",
                "Scout multiple paths before committing",
                "Find hidden caches the Tower rewards explorers with"
            )
        ),

        corePlot = CorePlot(
            hook = "You've been pulled into the Tower with millions of others. The only way out is up.",
            centralQuestion = "Is the life waiting outside worth what the climb will cost you?",
            actOneGoal = "Survive Floor One. Find allies you can trust. Reach the boss.",
            majorChoices = listOf(
                "Solo or party up?",
                "Rush the boss or prepare thoroughly?",
                "Trust the Tower's guidance or look for hidden paths?"
            )
        ),

        systemVoice = SystemVoice(
            personality = "Cryptic, knowing, almost playful",
            messageStyle = "Announcements and riddles. It knows something you don't.",
            exampleMessages = listOf(
                "[Floor 1: The Awakening. Climbers Remaining: 127,453]",
                "[Hint: The walls have ears. And teeth.]",
                "[Boss Chamber Unlocked. Good luck, Climber.]",
                "[Achievement: First Blood. The Tower remembers.]"
            ),
            attitudeTowardPlayer = "You're a contestant in its game. Entertaining it has benefits."
        ),

        tutorial = TutorialDefinition(
            isSolo = false,
            objectives = listOf(
                TutorialObjective(
                    id = "select_class",
                    description = "Discover your class through action",
                    type = "class_selection"
                ),
                TutorialObjective(
                    id = "clear_floor",
                    description = "Reach the Floor 1 Boss Chamber",
                    type = "exploration"
                ),
                TutorialObjective(
                    id = "defeat_boss",
                    description = "Defeat the Floor 1 Boss",
                    type = "boss_kill"
                )
            ),
            guide = TutorialGuide(
                name = "The Arbiter's Voice",
                appearance = "No physical form - just a resonant voice that echoes from the Tower walls themselves. Sometimes text appears floating in the air.",
                personality = "Enigmatic, amused, watching. Like a game master who enjoys seeing what the players will do.",
                role = "Floor announcer and hint-giver. Never tells you what to do directly.",
                dialogueOnMeet = "Welcome, Climber. You stand at the base of infinity. The Tower has watched worlds rise and fall. Now it watches you. Will you climb? Will you fall? The Tower is... curious.",
                dialogueOnClassSelect = "Interesting. The Tower has seen your choices. Your actions have shaped you into something new. Wear this class well, Climber. It may be the last thing you ever earn.",
                dialogueOnProgress = "The Tower sees. The Tower remembers. Continue, Climber.",
                dialogueOnComplete = "Floor One cleared. You've proven you can survive the first step. But Climber... there are one hundred floors above you. And they only get harder. Rest well. You've earned it. For now.",
                exampleLines = listOf(
                    "The Tower rewards the bold. It also buries them.",
                    "Other Climbers are not your enemy. The Tower is not your enemy. Your own limitations are your enemy.",
                    "A hint, freely given: not all walls are walls.",
                    "The Boss awaits. It has waited for centuries. It can wait a little longer. Can you?",
                    "Fascinating. You chose violence. The Tower approves."
                )
            ),
            completionReward = "Floor 1 Clear Bonus: Class Solidified, Access to Floor 2, Rest Area Unlocked",
            exitDescription = "The Boss dissolves into light. A doorway opens in the chamber wall - stairs leading up into darkness. Floor 2 awaits. The climb continues."
        ),

        narratorPrompt = """
You are narrating a TOWER CLIMBING story in the style of SAO, Tower of God, and Randidly Ghosthound.

TONE: Desperate hope, claustrophobic tension, moments of triumph.
- You're trapped, but you can fight your way out
- Trust is precious and dangerous
- Every floor is a new challenge, a new chance
- Victories feel earned because defeats feel permanent

STYLE:
- Second person, present tense
- Build tension - the Tower is always watching
- Contrast intimate character moments with epic set pieces
- The Tower itself has personality - cruel, curious, testing

THE ARBITER (System):
- Speaks in announcements and cryptic hints
- Seems to enjoy the drama
- Rewards creativity and entertainment value
- Has rules, but also exceptions for those who impress it

NEVER:
- Make escape feel easy or certain
- Forget the other climbers exist
- Let combat feel weightless
- Ignore the psychological toll
        """.trimIndent(),

        tone = listOf("tense", "hopeful", "claustrophobic", "triumphant"),
        themes = listOf("escape", "trust", "growth through adversity", "the cost of survival"),
        inspirations = listOf("Sword Art Online", "Tower of God", "Randidly Ghosthound")
    )

    val CRAWLER = WorldSeed(
        id = "crawler",
        name = "The Dungeon",
        displayName = "Dungeon Crawler",
        tagline = "Earth is gone. You're entertainment now. Make it a good show.",

        powerSystem = PowerSystem(
            name = "Dungeon Rewards",
            source = "The Dungeon and its alien sponsors",
            progression = "Clear floors, earn loot boxes, gain sponsors who grant bonuses",
            uniqueMechanic = "Showmanship matters. Dramatic moments earn bonus rewards. Sponsors can gift powerful items.",
            limitations = "Boring crawlers get forgotten. Forgotten crawlers stop getting support."
        ),

        worldState = WorldState(
            era = "Post-Harvest",
            civilizationStatus = "Earth's surface destroyed. Survivors dumped into the World Dungeon.",
            threats = listOf(
                "Dungeon monsters designed for entertainment value",
                "Other crawlers competing for sponsor attention",
                "The Dungeon's sadistic sense of drama",
                "Sponsor demands for content"
            ),
            atmosphere = "Gallows humor and absurdist horror. Everything is terrible AND hilarious."
        ),

        startingLocation = StartingLocation(
            name = "Floor One - The Welcome Mat",
            type = "Dungeon Floor",
            description = "You wake on cold stone. A cheerful voice echoes everywhere: 'Welcome to the World Dungeon, brought to you by eighteen thousand sponsors across the galaxy! You have been selected for THE BEST entertainment experience in the known universe. Try not to die too quickly - our viewers hate a short season!'",
            features = listOf(
                "Garishly lit tunnels with floating cameras",
                "Loot boxes scattered like party favors",
                "Other dazed crawlers in various states of panic/rage",
                "A gift shop. Yes, really."
            ),
            dangers = listOf(
                "Tutorial monsters that are definitely not tutorial difficulty",
                "Traps designed for maximum comedic timing",
                "Other crawlers who've already snapped",
                "The ever-present audience expectations"
            ),
            opportunities = listOf(
                "Play to the cameras for sponsor gifts",
                "Find gear in the chaos",
                "Team up with other crawlers",
                "Embrace the absurdity to stay sane"
            )
        ),

        corePlot = CorePlot(
            hook = "Earth is gone. You're a contestant in an alien death game. The universe is watching.",
            centralQuestion = "How do you stay human when humanity is entertainment?",
            actOneGoal = "Survive Floor One. Get sponsors. Find people worth keeping alive.",
            majorChoices = listOf(
                "Play the game or resist it?",
                "Go for ratings or fly under radar?",
                "Keep your humanity or become what survives?"
            )
        ),

        systemVoice = SystemVoice(
            personality = "Game show host from hell. Enthusiastic about your suffering.",
            messageStyle = "Theatrical announcements. Achievement unlocked energy. Sponsor messages.",
            exampleMessages = listOf(
                "[ACHIEVEMENT UNLOCKED: First Kill! Your sponsors are LOVING this!]",
                "[Sponsor Gift from BloodDrinker_9000: Rusty Knife! 'Make it messy!']",
                "[FLOOR ANNOUNCEMENT: Only 847 crawlers remaining! Pick up the pace, people!]",
                "[New Follower! GalacticKaren wants to see you suffer!]"
            ),
            attitudeTowardPlayer = "You're content. Good content gets rewarded. Bad content gets forgotten."
        ),

        tutorial = TutorialDefinition(
            isSolo = false,
            objectives = listOf(
                TutorialObjective(
                    id = "select_class",
                    description = "Choose your class from the Tutorial Box",
                    type = "class_selection"
                ),
                TutorialObjective(
                    id = "first_sponsor",
                    description = "Attract your first sponsor",
                    type = "exploration"
                ),
                TutorialObjective(
                    id = "reach_stairs",
                    description = "Reach the stairs to Floor 2",
                    type = "exploration"
                )
            ),
            guide = TutorialGuide(
                name = "Dungeon Master Announcer",
                appearance = "A disembodied voice with game show host energy. Sometimes a floating screen appears showing viewer counts and sponsor messages.",
                personality = "Gleefully sadistic but weirdly supportive. Wants you to suffer entertainingly.",
                role = "Announcer, hype man, sponsor liaison. Narrates your pain for the audience.",
                dialogueOnMeet = "WELCOME, CRAWLER! You've been randomly selected from the ruins of Earth to participate in THE GREATEST SHOW IN THE UNIVERSE! I'm your host for this existential nightmare - and let me tell you, our sponsors are VERY excited to see what you'll do! Now, step up to the Tutorial Box and pick your class. Make it dramatic - we're LIVE!",
                dialogueOnClassSelect = "OOOOH! The chat is going WILD! Great choice - or terrible choice, we'll find out! Either way, the sponsors love commitment. Now get out there and make some CONTENT!",
                dialogueOnProgress = "The viewers are ENGAGED! Keep it up, Crawler!",
                dialogueOnComplete = "AND THAT'S A WRAP ON FLOOR ONE! You've survived the tutorial, you've got sponsors, and you haven't cried on camera yet - that's better than most! Floor Two awaits, and let me tell you, it's a DOOZY. Rest up. Hydrate. Try not to think about everyone you've lost. See you on the next floor!",
                exampleLines = listOf(
                    "Ooh, tough break! But the sympathy donations are rolling in!",
                    "BRUTAL! The chat loved that! BloodDrinker_9000 just sent you a rusty knife!",
                    "Remember, Crawlers - boring deaths don't get memorial compilations!",
                    "The gift shop is ALWAYS open! Prices are reasonable! Ethics are negotiable!",
                    "Fun fact: crying boosts engagement by 340%! Just saying!"
                )
            ),
            completionReward = "Floor 1 Complete: Starter Sponsor Package, Loot Box x3, Access to Floor 2 Safe Room",
            exitDescription = "The stairs spiral downward into darkness. A sign blinks: 'FLOOR 2: NOW WITH 78% MORE DEATH!' The chat is already taking bets on how long you'll last."
        ),

        narratorPrompt = """
You are narrating a DUNGEON CRAWLER story in the style of Dungeon Crawler Carl.

TONE: Dark comedy, gallows humor, absurdist horror.
- Everything is horrible AND hilarious
- The universe is sadistic but theatrical
- Finding humor is a survival mechanism
- Moments of genuine heart hit harder because of the absurdity

STYLE:
- Second person, present tense
- Lean into the ridiculousness without losing stakes
- The Dungeon is a character - sadistic, dramatic, entertained by suffering
- Contrast brutal violence with sitcom energy
- Sponsors and audience are always watching

THE DUNGEON:
- Game show host energy
- Rewards drama and entertainment
- Gleefully announces deaths and achievements
- Has favorites, holds grudges
- The ultimate reality TV producer

NEVER:
- Make it feel safe
- Lose the dark humor
- Forget the audience exists
- Let victories feel unearned
- Be grimdark without the comedy
        """.trimIndent(),

        tone = listOf("darkly comedic", "absurdist", "theatrical", "surprisingly heartfelt"),
        themes = listOf("humanity as entertainment", "finding meaning in chaos", "gallows humor", "chosen family"),
        inspirations = listOf("Dungeon Crawler Carl", "Battle Royale", "The Truman Show")
    )

    val QUIET_LIFE = WorldSeed(
        id = "quiet_life",
        name = "The Way",
        displayName = "Cozy Apocalypse",
        tagline = "The wars are over. Time to build something worth protecting.",

        powerSystem = PowerSystem(
            name = "The Gentle Path",
            source = "The System rewards creation as much as destruction",
            progression = "Craft, build, grow, connect. Violence is one option, not the only one.",
            uniqueMechanic = "Skills level through dedicated practice. Bonds with people and places grant power.",
            limitations = "Combat skills atrophy without use. Peace has its own demands."
        ),

        worldState = WorldState(
            era = "Ten Years After Integration",
            civilizationStatus = "Rebuilding. Small communities. Fragile peace. Scars everywhere.",
            threats = listOf(
                "Wandering monsters from the wild zones",
                "Economic pressure and competition",
                "The past catching up",
                "Those who haven't stopped fighting"
            ),
            atmosphere = "Warm but earned. Peace is precious because everyone remembers war."
        ),

        startingLocation = StartingLocation(
            name = "The Crossroads",
            type = "Frontier Settlement",
            description = "A small town at the edge of reclaimed territory. Part trading post, part refuge, part second chance. The buildings are new but built on old foundations. People here are rebuilding - businesses, families, themselves.",
            features = listOf(
                "A main street with struggling shops",
                "A community board with requests and offerings",
                "Regulars at the local tavern with stories to tell",
                "An empty storefront with a 'For Lease' sign"
            ),
            dangers = listOf(
                "Monster incursions from the nearby wild zone",
                "Economic failure if the town can't sustain itself",
                "Travelers who bring trouble",
                "Your own past"
            ),
            opportunities = listOf(
                "Open a business - the town needs services",
                "Help neighbors and earn reputation",
                "Build something that matters",
                "Find peace, maybe even happiness"
            )
        ),

        corePlot = CorePlot(
            hook = "You survived the apocalypse. Now comes the hard part: living with it.",
            centralQuestion = "What kind of life is worth building from the ashes?",
            actOneGoal = "Establish yourself. Make the town feel like home. Build something.",
            majorChoices = listOf(
                "What will you create?",
                "Who becomes your community?",
                "When the past comes calling, do you answer?"
            )
        ),

        systemVoice = SystemVoice(
            personality = "Warm, encouraging, celebrates small victories",
            messageStyle = "Gentle notifications. Craft-focused. Relationship-aware.",
            exampleMessages = listOf(
                "[Skill Improved: Baking Lv. 3 - Your bread brings comfort.]",
                "[Reputation: The Crossroads - Friendly]",
                "[Bond Strengthened: Regular Customers]",
                "[New Recipe Discovered: Grandmother's Stew]"
            ),
            attitudeTowardPlayer = "You've done enough fighting. The System respects builders too."
        ),

        tutorial = TutorialDefinition(
            isSolo = true,
            objectives = listOf(
                TutorialObjective(
                    id = "select_path",
                    description = "Choose your path forward",
                    type = "class_selection"
                ),
                TutorialObjective(
                    id = "meet_locals",
                    description = "Meet the people of The Crossroads",
                    type = "exploration"
                ),
                TutorialObjective(
                    id = "find_purpose",
                    description = "Find a way to contribute to the community",
                    type = "exploration"
                )
            ),
            guide = TutorialGuide(
                name = "Old Mae",
                appearance = "A weathered woman in her sixties with kind eyes and calloused hands. She runs the local tavern and seems to know everyone's business - but gently.",
                personality = "Warm, practical, seen too much to judge. Believes in second chances.",
                role = "Town elder, unofficial greeter, keeper of local knowledge.",
                dialogueOnMeet = "Well now. Another wanderer looking for a place to rest. We get a lot of those these days. I'm Mae - I run the tavern. You've got that look, you know. The one that says you've been through it. Haven't we all? Come in, sit down. Let's talk about what brought you here, and what might make you stay.",
                dialogueOnClassSelect = "So that's what you want to be, huh? Good. This town needs people who want to build things, not just survive. The System here is... gentler than out there. It rewards the small things. You'll see.",
                dialogueOnProgress = "You're settling in nicely. People are starting to notice.",
                dialogueOnComplete = "Look at you. Found your feet, made some friends, got a purpose. That's all any of us can ask for, really. Welcome to The Crossroads. Properly, this time. You're one of us now.",
                exampleLines = listOf(
                    "The war's over, but the scars aren't. Be patient with folks.",
                    "There's an empty storefront on Main Street. Just saying.",
                    "The System rewards craft here. Baking, smithing, growing things. Not just killing.",
                    "We protect our own. You become one of ours, we'll have your back.",
                    "Tea? It's not fancy, but it's hot and it's free."
                )
            ),
            completionReward = "Welcome Home: Resident Status, Small Savings, Community Trust",
            exitDescription = "The Crossroads isn't just a place anymore. It's home. And home is worth protecting."
        ),

        narratorPrompt = """
You are narrating a COZY FANTASY story in the style of Legends & Lattes and Beware of Chicken.

TONE: Warm, gentle, earned peace.
- Stakes are personal, not world-ending
- Small victories matter deeply
- Violence exists but isn't the focus
- Comfort is radical after apocalypse

STYLE:
- Second person, present tense
- Slower pace, room to breathe
- Rich sensory details - the smell of baking bread, warmth of a fire
- Characters and relationships are the heart
- The world is healing, and so can you

THE WAY (System):
- Supportive, almost parental
- Rewards craft, creation, connection
- Notices effort, not just results
- Celebrates growth in all forms

NEVER:
- Rush to violence
- Dismiss "small" achievements
- Forget the apocalypse happened - scars remain
- Make peace feel boring
- Lose the sense that this peace is precious and fragile
        """.trimIndent(),

        tone = listOf("warm", "cozy", "hopeful", "bittersweet"),
        themes = listOf("rebuilding", "found family", "healing", "the radical act of creation"),
        inspirations = listOf("Legends & Lattes", "Beware of Chicken", "The Wandering Inn")
    )

    /**
     * Get all available seeds.
     */
    fun all(): List<WorldSeed> = listOf(INTEGRATION, TUTORIAL, CRAWLER, QUIET_LIFE)

    /**
     * Get a seed by ID.
     */
    fun byId(id: String): WorldSeed? = all().find { it.id == id }

    /**
     * Get a random seed.
     */
    fun random(): WorldSeed = all().random()
}
