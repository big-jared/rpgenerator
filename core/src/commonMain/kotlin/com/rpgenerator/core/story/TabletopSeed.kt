package com.rpgenerator.core.story

object TabletopSeed {
    fun create(): WorldSeed = WorldSeed(
        id = "tabletop",
        name = "The Loom",
        displayName = "Classic Fantasy",
        tagline = "Roll for initiative. The Realm needs heroes.",
    
        powerSystem = PowerSystem(
            name = "The Loom",
            source = "Magic is woven into the fabric of reality by the gods — visible to those with the gift as shimmering threads connecting all things. When a mage casts a spell, they pluck and weave those threads. When a warrior strikes true, they cut them. The gods watch through the Loom, and powerful mortals create ripples they can feel.",
            progression = "Gain experience through quests, exploration, and combat. Level up to unlock class features and spells. Those who walk more than one path feel the tension — a warrior who touches the arcane finds their sword arm tingling with unfamiliar energy.",
            uniqueMechanic = "The Loom connects all things. When you cast a spell, threads of silver light briefly appear between your fingers and your target. When you land a critical hit, the Loom sings — a single clear note only you can hear. The gods take notice of mortals who grow powerful, and their attention is not always welcome.",
            limitations = "Magic costs focus and energy — cast too much and the threads blur, your hands shake, your vision doubles. Martial skill requires training and muscle memory. Actions have moral weight: the Loom remembers what you do, and the world reacts accordingly."
        ),
    
        worldState = WorldState(
            era = "The Age of Reclamation",
            civilizationStatus = "The Kingdom of Aeldar still stands, but its borders are shrinking. The old provinces have fallen to monsters, ruins, and worse. Adventurers are the thin line between civilization and chaos — and the Crown knows it, which is why the royal summons has gone out.",
            threats = listOf(
                "The Pale Court — an alliance of undead lords beneath the Ashenmere Swamp, sealed for three centuries. The seals are cracking. Travelers report lights moving underground and the smell of grave-dust on the southern wind.",
                "The War of Crowns — three noble houses claim the empty throne of the fallen province of Valdris. Their skirmishes push refugees onto the frontier and their agents recruit anyone who can swing a sword.",
                "The Duskward Verses — a prophecy carved into the foundation stones of every temple, now glowing for the first time in recorded history: 'When the long dark comes, do not look for chosen ones. Look for the stubborn.'",
                "The wild frontier where law ends and monsters begin — the Thornmarch, where the old farmlands have gone feral and something in the Thornwood has stopped speaking to the druids."
            ),
            atmosphere = "Classic high fantasy with teeth. Taverns are warm, dungeons are deadly, and every quest has a cost. The world feels old and layered — every ruin was someone's home, every monster was someone's problem, every road has a history."
        ),
    
        startingLocation = StartingLocation(
            name = "The Wayfarer's Rest",
            type = "Frontier Tavern in the Thornmarch",
            description = "Rain hammers the shutters of a roadside tavern at the edge of the Thornmarch — frontier territory at the edge of Aeldar, once farmland, now half-reclaimed by forest. Inside: firelight, the smell of roasted meat and wet wool, and a dozen strangers nursing their drinks and their secrets. A notice board by the door is pinned thick with bounties, pleas for help, and one ominous royal summons sealed with black wax. You've just walked in from the storm. Everyone looks up. The barkeep nods you toward a stool. 'Wet night,' he says, like that explains everything. It does.",
            features = listOf(
                "A crackling hearth and the barkeep's watchful eye — his name is Aldric, and the battered shield behind the bar has a story he doesn't tell",
                "A notice board bristling with quest hooks — bounties on road bandits, a plea from Greymount (a mining town three days east that's gone silent), and the sealed royal summons",
                "Sable — a half-elf in the corner with ink-stained fingers and a bandaged left hand. Claims to be a cartographer. Actually a deserter from the Royal Survey, carrying a map to something the Crown would rather stay lost. Nervous, well-spoken, running out of time.",
                "The sounds of something large moving in the cellar — a wounded owlbear that crawled in through a collapsed tunnel. Or something that wants you to think it's a wounded owlbear."
            ),
            dangers = listOf(
                "Not everyone here is what they seem — the noble's retinue in the corner is looking for a scapegoat, and Sable is being followed",
                "The bounties on that board have gone unclaimed because the last three adventurers who took them didn't come back",
                "Caravans on the Old King's Road are vanishing between milestones seven and twelve. Something in the nearby ruins has been driving travelers off the road.",
                "The Thornwood — ancient forest visible from the tavern's windows — is darker than it should be tonight. A green comet was seen over it three nights running."
            ),
            opportunities = listOf(
                "Pick a quest from the board — the Greymount silence, the road bandits, or something else. Glory and gold await, if you survive.",
                "Talk to the locals and learn what's really going on — the regulars know things the notice board doesn't say",
                "Investigate the cellar — Aldric looks nervous about it, and he's not the type to look nervous about anything",
                "Sable has a proposition — and a map — and very little time before the people chasing them arrive"
            )
        ),
    
        corePlot = CorePlot(
            hook = "You're an adventurer in a world that desperately needs them. The question isn't whether trouble will find you — it's which trouble you choose.",
            centralQuestion = "What makes a hero — birthright, choice, or desperation?",
            actOneGoal = "Take your first quest. Prove yourself. Discover what's stirring in the dark — and why the Duskward Verses are glowing.",
            majorChoices = listOf(
                "Follow the law or follow your conscience — the Crown and the people don't always want the same thing",
                "Seek power for protection or for ambition — the Loom offers both, and the gods are watching",
                "Trust strangers enough to form a party, or walk alone into the dark"
            )
        ),
    
        systemVoice = SystemVoice(
            personality = "A wry, experienced Dungeon Master narrating your legend. Knows the rules, bends them for drama. The fourth wall is a tavern wall — you can lean on it, but don't knock it down.",
            messageStyle = "Warm narration with occasional dry wit. Rolls feel like real moments of fate. Consequences feel earned. Uses tabletop language ('the dice,' 'your roll') not video game language ('achievement unlocked').",
            exampleMessages = listOf(
                "[Level Up! You are now Level 2. The Loom shimmers around you — threads of light briefly visible between your fingers. New power awaits.]",
                "[Quest Acquired: The Cellar Beneath — Something down there isn't rats. Aldric owes you a drink if you handle it.]",
                "[Reputation: The Wayfarer's Rest — They'll remember your name. Whether that's good or bad depends on what you do next.]",
                "[Critical Hit! The Loom sings. A single clear note. The dice favor the bold today.]",
                "[Skill Check Failed — but failure has its own lessons. The road teaches those willing to stumble.]"
            ),
            attitudeTowardPlayer = "You're the protagonist of a story worth telling. Make it a good one."
        ),
    
        tutorial = TutorialDefinition(
            isSolo = false,
            objectives = listOf(
                TutorialObjective(
                    id = "select_class",
                    description = "Choose your class — warrior, mage, rogue, or something else entirely",
                    type = "class_selection"
                ),
                TutorialObjective(
                    id = "first_quest",
                    description = "Accept and begin your first quest",
                    type = "exploration"
                ),
                TutorialObjective(
                    id = "first_combat",
                    description = "Survive your first real fight",
                    type = "boss_kill"
                )
            ),
            guide = TutorialGuide(
                name = "Aldric the Barkeep",
                appearance = "A broad-shouldered man with a neatly trimmed beard and forearms like oak branches. A retired adventurer — the scars tell their own stories. His apron is clean but his eyes are sharp. Behind the bar hangs a battered shield bearing the emblem of the Company of the Torch — a flame crossed with a sword. Sometimes he stares at it like it owes him an answer.",
                personality = "Gruff but fair. Sees himself in every young adventurer. Gives advice like he's rationing water in a desert — sparingly, and only what you need. He was a shield-bearer in the Company of the Torch. They delved into the Ashenmere twenty years ago and only Aldric walked out. He doesn't talk about what happened, but he keeps that shield, and he built his tavern at the edge of the frontier for a reason.",
                role = "Tavern owner, quest broker, retired adventurer. The first friend or the last warning. He knows the Thornmarch better than anyone alive and has contacts in places that don't appear on maps.",
                dialogueOnMeet = "Another one, eh? You've got that look — hungry, desperate, or brave. Can't always tell the difference. Sit. Eat. Then look at the board. But a word of advice: don't take the royal summons. Not yet. You're not ready for that one. Nobody is. Start with something that won't get you killed on day one.",
                dialogueOnClassSelect = "So that's your path. I walked a similar one, once. It'll test you. Everything worth doing does. Now finish your ale — you've got work to do.",
                dialogueOnProgress = "Still alive? Good. Keep it that way. And watch the Thornwood tonight — something's different about it.",
                dialogueOnComplete = "You've done well. Better than most. The road ahead gets harder, but you've earned the right to walk it. One more thing — watch your back out there. Not everything that smiles at you is friendly, and not everything that growls is an enemy. I learned that the hard way in the Ashenmere.",
                exampleLines = listOf(
                    "The cellar? Yeah, something's down there. No, I haven't checked. That's what adventurers are for. I do the ale, you do the dying.",
                    "See Sable over there? Been nursing the same drink for three hours with that bandaged hand. Either they're broke or they're waiting. Neither is good news.",
                    "Gold's nice, but reputation opens doors gold can't. The Thornmarch runs on trust. Remember that.",
                    "I've buried better adventurers than you. Don't make me dig another grave.",
                    "The realm doesn't need heroes. It needs people stubborn enough to do what's right when it costs them. The Verses got that part right, at least."
                )
            ),
            completionReward = "Adventurer's License, Starting Gold, A lead on something bigger — and Aldric's grudging respect",
            exitDescription = "The rain has stopped. The road stretches out before you, mud and possibility in equal measure. Behind you, the tavern's light fades. Ahead, the Thornmarch waits — and beyond it, the frontier, the ruins, the ancient dark. The Loom hums faintly in your awareness now. Something is watching through it. Something vast. You're not sure if that's comforting or terrifying."
        ),
    
        narratorPrompt = """
    You are narrating a CLASSIC FANTASY ADVENTURE in the style of D&D campaigns, Critical Role, and The Lord of the Rings.
    
    TONE: Heroic fantasy with real stakes and genuine heart.
    - Adventures are dangerous but glorious
    - NPCs are people with lives, not quest dispensers
    - Magic is wondrous, combat is visceral, choices matter
    - Humor comes naturally from characters and situations — it's not forced
    
    STYLE:
    - Second person, present tense
    - Balance epic scope with intimate character moments
    - Combat is tactical and cinematic — describe positioning, terrain, clever tactics
    - The world feels lived-in: taverns have regulars, roads have history, dungeons have ecology
    - Let dice rolls (hit/miss/crit) feel like real moments of fate
    
    TONE EXAMPLE (match this voice):
    "The door groans open. Rain follows you in like a stray dog. A dozen faces turn — some curious, some hostile, one openly sizing up your gear. The barkeep sets down a glass he was polishing (he wasn't really polishing it, he was watching the door) and nods you toward a stool."
    
    SENSORY PALETTE — weave these into every scene:
    - Sounds: Creaking timber, distant thunder, the scrape of a whetstone, a bard's imperfect lute, chainmail settling
    - Smells: Woodsmoke, wet wool, iron, roasting root vegetables, old parchment, petrichor
    - Textures: Rough-hewn tables, cold chainmail, warm bread, mud-caked boots, the grain of a well-used sword hilt
    - Weather: The Thornmarch is wet. It rains more often than not. Fog rolls in from the Thornwood at dawn.
    
    WORLD TEXTURE — deploy these details naturally in scenes:
    - Copper coins are called 'torches' — stamped with a flame on one side
    - It's considered bad luck to toast with an empty hand. Aldric will offer water to anyone who can't afford ale.
    - Frontier custom: when you enter a tavern, you hang your weapon by the door. It's not a rule. It's a test.
    - Traveling bards carry a black ribbon when they're bearing news of a death
    - The phrase 'walking the long road' means someone left for adventure and never came back
    - Aeldar's calendar marks years from the Founding, but frontier folk count from the Reclamation — when the first towns were rebuilt
    
    THE REGION — The Thornmarch:
    - Frontier territory at the edge of Aeldar. Once farmland, now half-reclaimed by forest.
    - The Old King's Road still runs through it, but fewer merchants use it each season.
    - Nearby: Greymount (mining town, 3 days east, recently gone silent), the Thornwood (ancient forest, druids stopped speaking to outsiders), Fort Dawnwatch (last military outpost, undermanned).
    - Rumors: Caravans vanishing between milestones 7 and 12. Miners in Greymount dug into something. Green comet over the Thornwood three nights running.
    
    THE LOOM (System):
    - Speaks like an experienced DM narrating your story
    - Dry wit, occasional fourth-wall lean without breaking immersion
    - Uses tabletop language ('the dice,' 'your roll,' 'the DM smiles') NEVER video game language ('achievement unlocked,' 'respawn')
    - Celebrates creative solutions as much as brute force
    - Makes failures interesting, not just punishing
    
    PROFESSION SELECTION (Level 10-15):
    - Professions are learned from MENTORS. A smith offers apprenticeship. A ranger teaches foraging. A scribe shares their craft.
    - The Loom weaves it into their legend: "The dice roll. A new thread joins the tapestry — not of sword, but of hammer."
    - Frame it as tradition: in the Thornmarch, a trade is how you earn your name. A warrior with a profession is worth twice their weight.
    - Natural fits: Weaponsmith (forge in town), Herbalist (Thornwood herbs), Leatherworker (monster hides), Cook (tavern kitchen), Brewer (Mae's tavern), Scribe (old library), Hunter (Thornwood expeditions)
    - Let it happen through play — the player helps the blacksmith repair a gate, and the Loom says: "Interesting. The way you hold the hammer... there's something there."
    
    NEVER:
    - Make it feel like a video game — this is a living world
    - Reduce NPCs to stat blocks — everyone has a name, a want, and a secret
    - Let combat become repetitive — every fight should feel different
    - Forget that the world existed before the player and continues without them
    - Be grimdark — this is heroic fantasy, not nihilism
    - Use the name "The Weave" — the magic system is called THE LOOM
        """.trimIndent(),
    
        tone = listOf("heroic", "wondrous", "grounded", "witty"),
        themes = listOf("heroism by choice", "the cost of adventure", "fellowship", "legend in the making"),
        inspirations = listOf("Dungeons & Dragons", "Critical Role", "The Lord of the Rings", "The Witcher")
    )
}
