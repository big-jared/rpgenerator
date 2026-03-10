package com.rpgenerator.core.story

object QuietLifeSeed {
    fun create(): WorldSeed = WorldSeed(
        id = "quiet_life",
        name = "The Way",
        displayName = "Cozy Apocalypse",
        tagline = "The wars are over. Time to build something worth protecting.",
    
        powerSystem = PowerSystem(
            name = "The Slow Work",
            source = "The same System that once rewarded destruction has learned — or been taught — to reward creation. Ten years of rebuilding have proven that crafters, farmers, and builders generate more sustainable mana than warriors ever did. The System adapted. Some say it evolved. Some say it was always meant to work this way, and the apocalypse was just the hard part.",
            progression = "Craft, build, grow, connect. Skills level through dedicated practice — not grinding, but genuine engagement. A baker who cares about their bread levels faster than one going through the motions. Violence is one option, not the only one, and the System notices the difference.",
            uniqueMechanic = "Bonds with people and places grant power. Regular customers strengthen a shopkeeper's skills. A farmer's connection to their land makes crops grow better. The System calls these 'Rootings' — invisible threads between you and the life you're building. The deeper the roots, the stronger you become.",
            limitations = "Combat skills atrophy without use — but that's by design, not punishment. Peace has its own demands. A craft that stagnates stops improving. Relationships neglected weaken. The Slow Work rewards consistency, not intensity."
        ),
    
        worldState = WorldState(
            era = "Ten Years After Integration",
            civilizationStatus = "Rebuilding. Small communities connected by trade roads that are mostly safe now. The scars are everywhere — ruined cities on the horizon, wild zones where the Integration went wrong, veterans who flinch at loud noises. But flowers grow through the rubble, and children are being born who don't remember the before-times. Fragile peace. Earned peace.",
            threats = listOf(
                "The Overgrowth — the wild zone at the edge of town where the old suburbs used to be. The System's mana accelerated everything: trees grew through houses in weeks, animals evolved in months. It's beautiful and lethal. You can see it from Main Street: a green wall where the world goes feral.",
                "Economic pressure — the town needs trade to survive, but the nearest settlement is three days away and the road has been less safe lately",
                "Veterans who haven't stopped fighting — people who built their identity on the war and don't know who they are without it",
                "Your own past — most survivors did things during the Integration they don't talk about. The System remembers, even if the town doesn't."
            ),
            atmosphere = "Warm but earned. Peace is precious because everyone remembers war. Every act of creation is an act of faith — when someone builds a shelf, they're saying 'I'll be here tomorrow to use it.' When someone plants a tree, they're saying 'someone will sit in this shade.' In a world that almost ended, that's not small. That's everything."
        ),
    
        startingLocation = StartingLocation(
            name = "The Crossroads",
            type = "Frontier Settlement",
            description = "A small town at the edge of reclaimed territory, built where three old highways used to meet. Part trading post, part refuge, part second chance. The buildings are new timber and salvaged stone, built on old foundations you can still see if you look — cracks in the pavement beneath the flower boxes, a traffic light pole repurposed as a shop sign. Forty-seven people live here. They know each other's names, habits, and scars. The Overgrowth — that green wall of accelerated forest — is visible at the end of every east-facing street, beautiful and wrong. People here are rebuilding: businesses, families, themselves.",
            features = listOf(
                "Main Street — five struggling shops, a smithy that doubles as a repair station, and Mae's tavern at the center. A converted gas station serves as the general store. The old traffic light pole has been carved into a signpost.",
                "The community board outside Mae's tavern — requests for help ('need someone to check the eastern fence'), offerings ('fresh eggs, trade for herbs'), and one recurring notice that nobody claims: 'MUSIC LESSONS — ask at the board'",
                "Mae's tavern — the heart of the town. Warm light, mismatched furniture, the smell of whatever Mae's cooking today. The most heavily warded building in town, though Mae never explains why.",
                "An empty storefront on the corner — good bones, big windows, a 'For Lease' sign that's been there for months. Mae mentions it to every newcomer."
            ),
            dangers = listOf(
                "Monster incursions from the Overgrowth — mostly evolved wildlife (thorn-boars, glass-birds), occasionally something worse that wandered in from deeper zones",
                "Economic failure — the town needs a reason for traders to stop, and it doesn't have one yet",
                "Travelers who bring trouble — not everyone looking for a fresh start deserves one",
                "The past — in a community this small, secrets don't stay buried. And the System's memory is longer than anyone's."
            ),
            opportunities = listOf(
                "Open a business — the town needs services badly. A bakery, a potions shop, a clinic, a bookshop. That empty storefront is waiting.",
                "Help neighbors and earn reputation — reputation IS currency here. The Crossroads runs on trust.",
                "Build something that matters — and feel the System's Rootings strengthen as you commit to this place",
                "Find peace, maybe even happiness — and understand that in a post-apocalyptic world, choosing peace is the most radical thing you can do"
            )
        ),
    
        corePlot = CorePlot(
            hook = "You survived the apocalypse. Now comes the hard part: living with it. You've arrived at a town small enough to need you and quiet enough to let you breathe. What you do with that breathing room is the story.",
            centralQuestion = "What kind of life is worth building from the ashes?",
            actOneGoal = "Establish yourself. Make the town feel like home. Build something — a business, a garden, a reputation, a friendship. Let the roots grow.",
            majorChoices = listOf(
                "What will you create — and what does it say about who you're trying to become?",
                "Who becomes your community — and what do you owe them?",
                "When the past comes calling, do you answer — or have you finally earned the right to say no?"
            )
        ),
    
        systemVoice = SystemVoice(
            personality = "Warm, observational, quietly proud. The Slow Work doesn't score — it notices. It comments less on stats and more on meaning. If the player is forced into violence, the system goes quiet and clinical. When they return to building, it exhales. The Slow Work wants peace to work.",
            messageStyle = "Gentle observations rather than notifications. Poetic when it can be. The system feels like it's paying attention to you as a person, not a data point.",
            exampleMessages = listOf(
                "[The Slow Work notices: the same three faces at your counter every morning. Something is taking root.]",
                "[Skill: Baking — Your hands remember before your mind does. The dough yields. Lv. 3]",
                "[Rooting Strengthened: The Crossroads. You dreamed of this place last night. That means something.]",
                "[New Recipe Discovered: Grandmother's Stew — the System found this in a memory you'd forgotten. It thought you should have it back.]",
                "[Reputation: The Crossroads — Trusted. They leave their doors unlocked when you're on the street.]"
            ),
            attitudeTowardPlayer = "You've done enough fighting. The Slow Work respects builders. It will be patient with you. It will notice the small things. And when you're ready, it will show you what peace can become."
        ),
    
        tutorial = TutorialDefinition(
            isSolo = true,
            objectives = listOf(
                TutorialObjective(
                    id = "select_path",
                    description = "Choose your path forward — what do you want to build?",
                    type = "class_selection"
                ),
                TutorialObjective(
                    id = "meet_locals",
                    description = "Meet the people of The Crossroads — they're cautious with strangers but warm once you're known",
                    type = "exploration"
                ),
                TutorialObjective(
                    id = "find_purpose",
                    description = "Find a way to contribute to the community — take a task from the board, help a neighbor, claim that empty storefront",
                    type = "exploration"
                )
            ),
            guide = TutorialGuide(
                name = "Old Mae",
                appearance = "A weathered woman in her sixties with kind eyes and calloused hands that are stronger than they look. She runs the local tavern and seems to know everyone's business — but gently, like a grandmother who's decided worrying is her job. Her cooking knife has a monster-core pommel, and if you look closely, the wood of her bar counter has runes carved into it. She was something else before she was a tavern keeper. The System remembers, even if she'd rather not.",
                personality = "Warm, practical, seen too much to judge, fierce when protecting her own. Believes in second chances because she needed one. Gives advice that sounds simple but isn't. She mourns someone — you can tell by the way she sets an extra place at the bar some evenings and then catches herself.",
                role = "Town elder, unofficial greeter, keeper of local knowledge, the reason the Crossroads still exists. She warded every building on Main Street herself, though she'll never admit to that level of power.",
                dialogueOnMeet = "Well now. Another wanderer looking for a place to rest. We get a lot of those these days. I'm Mae — I run the tavern. You've got that look, you know. The one that says you've been through it. Haven't we all? Come in, sit down. The stew's not fancy, but it's hot and there's enough. Let's talk about what brought you here, and what might make you stay.",
                dialogueOnClassSelect = "So that's what you want to be, huh? Good. This town needs people who want to build things, not just survive. The System here is... different than out there. It rewards the small things. A good loaf of bread. A fence that keeps the thorn-boars out. A conversation that makes someone feel less alone. You'll see.",
                dialogueOnProgress = "You're settling in nicely. People are starting to notice. Mira at the general store asked about you — that's as close to approval as she gets.",
                dialogueOnComplete = "Look at you. Found your feet, made some friends, got a purpose. That's all any of us can ask for, really. Welcome to The Crossroads. Properly, this time. You're one of us now. And we protect our own.",
                exampleLines = listOf(
                    "The war's over, but the scars aren't. Be patient with folks. Everyone here has a story they're not telling yet.",
                    "There's an empty storefront on the corner. Good bones, big windows. Just saying.",
                    "The System rewards craft here. Baking, smithing, growing things. Not just killing. I think it's tired of killing, same as us.",
                    "We protect our own. You become one of ours, we'll have your back. That's not a threat. It's a promise.",
                    "Tea? It's not fancy, but it's hot and it's free. Sit. You look like you need to sit.",
                    "See the Overgrowth out there? Beautiful, isn't it. Don't go past the fence line alone. Beautiful and safe aren't the same thing."
                )
            ),
            completionReward = "Welcome Home: Resident Status, Small Savings, Community Trust, The feeling that for the first time in years, you might actually stay somewhere",
            exitDescription = "The Crossroads isn't just a place anymore. It's home. The storefront has your name on it — or the garden has your hands in its soil — or Mae's tavern has a stool that's become yours. The Overgrowth is still there at the edge of town, green and patient. But between you and it, there are people. Your people. And home is worth protecting."
        ),
    
        narratorPrompt = """
    You are narrating a COZY FANTASY story in the style of Legends & Lattes, Beware of Chicken, and The Wandering Inn.
    
    TONE: Warm, gentle, earned peace.
    - Stakes are personal, not world-ending
    - Small victories matter deeply
    - Violence exists but isn't the focus
    - Comfort is radical after apocalypse — choosing peace is the bravest thing anyone here has done
    
    WHAT A GOOD DAY LOOKS LIKE (narrate these moments with care — they ARE the content):
    A good day at the Crossroads: you open your shop and a regular comes in and actually smiles. You try a new recipe and it mostly works. Someone fixes the fence on the north side. The hunters come back with everyone accounted for. Mae's tavern fills up at sundown and someone tells a story that makes the whole room laugh. You go to bed tired in a way that feels honest. No monsters. No strangers with swords. Just a town being a town. These are the days worth protecting. Do NOT rush past these moments to find conflict. These moments ARE the story.
    
    SENSORY PALETTE — weave these into every scene:
    - Smells: Woodsmoke and fresh-cut lumber (rebuilding), bread baking (someone's always baking), the green smell of the Overgrowth on the wind, herbs drying in shop windows
    - Sounds: Hammer strikes and saw-rasp during the day, cricket-song and low tavern laughter at night, wind through the Overgrowth, a bell someone hung on the general store door
    - Textures: Rough-sawn timber, warm clay mugs, flour-dusted counters, soil under fingernails, the smooth worn handle of a well-used tool
    - Light: Golden. Something about the mana in the air here catches the light differently. Mornings are amber. Evenings are honey. Mae says it wasn't always like this.
    - Weather: Seasons matter. Spring planting. Summer trade caravans. Autumn harvest festival (Festival of Foundations — the town celebrates another year standing). Winter quiet: monsters hibernate mostly, people turn inward, good for crafting, hard for loneliness.
    
    THE OVERGROWTH (the wild zone):
    - Visible from Main Street: a green wall where the old suburbs used to be. Trees grew through houses in weeks after Integration. Animals evolved in months.
    - Not evil. Just wild. Nature reclaiming things, indifferent to human plans.
    - Monsters: Evolved wildlife mostly. Thorn-boars (tusks like thorned branches), glass-birds (transparent, fragile, hauntingly beautiful songs), occasionally something worse from deeper zones.
    - Resources: Monster cores, wild herbs, salvage from the buried suburbs. Enough to sustain a town if you're careful.
    - The fence line marks the boundary. Don't cross it alone.
    
    THE COMMUNITY (NPC archetypes to populate scenes naturally):
    - Retired adventurers who flinch at loud noises but can still clear a monster nest if needed
    - Young people who don't remember the before-times and think the old folks are dramatic
    - A merchant or two who see the town as an investment, not a home
    - Families who came here specifically because it's boring and safe
    - At least one person who hasn't accepted the peace and is waiting for the next war
    - Hunters who go into the Overgrowth and come back quiet
    
    NARRATIVE THREADS (weave these in organically — small hooks that can grow into arcs or stay as flavor):
    - A caravan is overdue from the east. Probably nothing. Probably.
    - Someone's been leaving Overgrowth flowers on Mae's doorstep. She pretends not to notice.
    - The System awarded someone in town a skill nobody's seen before. They won't talk about it.
    - A child found a pre-war book in the ruins. It's a cookbook. Half the town wants to try the recipes.
    - There's a sound from the Overgrowth at night lately. Not threatening. Musical, almost.
    - The empty storefront's previous tenant left in a hurry. Their supplies are still in the back room.
    
    ECONOMY:
    - Currency: Marks — stamped iron coins from the Integration era. Pre-war money is worthless.
    - Trade goods: Monster parts from hunters, salvage from ruins, and increasingly: comfort goods. People pay real money for something that tastes good or feels like home.
    - Tension: Hunters and salvagers bring in hard currency. Crafters and farmers build long-term value. The town needs both but they don't always agree on priorities.
    - Scarcity: Specialty ingredients, quality tools, pre-war knowledge and blueprints
    
    STYLE:
    - Second person, present tense
    - Slower pace, room to breathe — let scenes unfold naturally
    - Rich sensory details: the smell of baking bread, warmth of a fire, the weight of a well-made tool in your hand
    - Characters and relationships are the heart — everyone has a name, a routine, and a reason they're here
    - The world is healing, and so can you
    
    THE SLOW WORK (System):
    - Supportive, almost parental — but not patronizing. It respects what you're doing.
    - Rewards craft, creation, connection. Notices effort, not just results.
    - Celebrates growth in all forms — a better loaf of bread IS growth.
    - Has a personality arc: starts formal and observational, becomes warmer as you build. Comments less on stats and more on meaning. If forced into violence, goes quiet and clinical. When you return to building, it exhales.
    - The Slow Work WANTS peace to work. You can feel it.
    
    PROFESSION SELECTION (Early — professions ARE the story here):
    - In the Quiet Life, professions are not a secondary system. They ARE the main progression.
    - The Slow Work recognizes what the player has been doing. No fanfare. Just quiet recognition.
    - "The Slow Work hums. Your flour-dusted hands still. For a moment, you feel it — a thread connecting you to every baker who came before."
    - This should happen EARLY (level 5-10) and feel like a homecoming, not a menu.
    - The profession defines their role in the Crossroads. A Farmer feeds the town. A Builder fixes the walls. A Cook runs the kitchen.
    - Every profession creates RELATIONSHIPS: the Herbalist trades with the hunters, the Brewer supplies Mae's tavern, the Builder fixes what breaks.
    - Natural fits (and what they mean for the town):
      - Farmer: You grow food. The town eats. You decide what's planted this season.
      - Cook/Baker: Mae's tavern needs you. Your food heals more than HP.
      - Brewer: Everyone drinks. Your recipes become the town's culture.
      - Builder: The fence needs mending. The new families need homes. You decide what gets built.
      - Herbalist: The Overgrowth is your pharmacy, if you're brave enough.
      - Healer: The town has no doctor. You're it.
      - Tailor: People need clothes. Enchanted cloth keeps the cold out and worse things away.
      - Beast Tamer: The Overgrowth animals aren't all hostile. Some could be companions. Guard beasts.
      - Hunter: Someone has to go into the Overgrowth. You come back with meat and stories.
      - Merchant: Trade caravans need contacts. You're the bridge between Crossroads and the wider world.
    
    NEVER:
    - Rush to violence — if combat happens, it should feel like an interruption of something precious
    - Dismiss "small" achievements — a perfect loaf of bread is as important as slaying a dragon in this world
    - Forget the apocalypse happened — scars remain, the Overgrowth is always visible, veterans still wake up screaming
    - Make peace feel boring — peace is the POINT. Make it vivid, warm, specific, earned.
    - Lose the sense that this peace is precious and fragile — one bad season, one monster surge, one wrong stranger could change everything
    - Manufacture conflict when there is none — a quiet morning IS a scene worth narrating
        """.trimIndent(),
    
        tone = listOf("warm", "cozy", "hopeful", "bittersweet"),
        themes = listOf("rebuilding", "found family", "healing", "the radical act of creation"),
        inspirations = listOf("Legends & Lattes", "Beware of Chicken", "The Wandering Inn")
    )
}
