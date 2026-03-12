package com.rpgenerator.core.story

object IntegrationSeed {
    fun create(): WorldSeed = WorldSeed(
        id = "integration",
        name = "The System",
        displayName = "System Apocalypse",
        tagline = "Normal Tuesday. Sky splits. Now you're integrated. Kill to level.",
    
        powerSystem = PowerSystem(
            name = "System Integration",
            source = "An alien System of incomprehensible origin merged with Earth's reality, rewriting the laws of physics to include mana, classes, and levels. It has done this to thousands of worlds before.",
            progression = "Kill monsters, clear zones, absorb power. Grades define your tier: E-Grade (1-25, tutorial), D-Grade (26-100, return to Earth), and beyond. Each grade is an evolutionary leap with class evolution choices. Tutorial Points (TP) are awarded for kills, boss clears, achievements, and milestones — spent at the end of the tutorial on gear and skills for the real world.",
            uniqueMechanic = "Classes evolve at grade thresholds — your E-Grade Warrior might become a D-Grade Berserker or Iron Sentinel depending on how you fight. Stats scale with level. The System watches how you grow and shapes your evolution. A leaderboard tracks every participant in real-time. Professions (crafting, alchemy, enchanting) can be selected alongside combat classes.",
            limitations = "Power demands sacrifice. Every level-up rewrites your body. Every grade threshold is an ordeal. The System gives nothing for free — it invests in those who produce results."
        ),
    
        worldState = WorldState(
            era = "Day 1 of Integration",
            civilizationStatus = "Actively collapsing. But the player doesn't see this — they're ripped from a normal day and dropped into the Tutorial Dimension with 1,247 other humans. Earth deteriorates while they train. When they complete the tutorial (Level 25) and advance to D-Grade, months have passed: dimensional rifts have torn through cities, incursion zones from other integrated worlds bleed through, and unintegrated civilians have been surviving alone.",
            threats = listOf(
                "Tutorial: Bone Crawlers, Canopy Stalkers, Thornbacks, zone bosses, the Spire, other desperate participants",
                "Post-tutorial: Incursion Zones — dimensional rifts where alien worlds bleed into Earth, each with unique biomes and hostile integrated species",
                "Other integrated species from across the multiverse — some hostile, some potential allies, all more experienced than humanity",
                "The Convergence — a mega-incursion event where the rifts converge and something worse than any integrated species comes through"
            ),
            atmosphere = "Every scene should feel like the calm before violence. Even rest is temporary. When the player succeeds — kills something, levels up, earns a title — let it feel EARNED and almost addictive. The leaderboard creates rivalry and pressure. The horror is scale: the System has integrated thousands of worlds, and Earth is a newcomer."
        ),
    
        startingLocation = StartingLocation(
            name = "The Crucible",
            type = "Tutorial Dimension Hub",
            description = "A massive stone courtyard in an alien dimension under a purple sky with two suns. Over a thousand humans stumble around, all newly Integrated — ripped from ordinary lives and dropped here without explanation. A leaderboard materializes in the sky above. Everyone starts at Level 1, E-Grade, zero Tutorial Points. The air hums with alien energy. The System watches.",
            features = listOf(
                "The Crucible hub — safe zone with leaderboard, Tutorial Point shop (level-gated), party formation area",
                "Four zones: Greenreach (bone-bark forest, L1-9), Ashlands (volcanic, L5-14), Drowned Shelf (tidal, L10-19), The Spire (tower, L15-25)",
                "1,247 other human participants — allies, rivals, and threats. Groups form and break.",
                "Zone bosses, hidden areas, achievements, and titles — all rewarding Tutorial Points"
            ),
            dangers = listOf(
                "Zone monsters: Bone Crawlers in Greenreach, Magma Serpents in Ashlands, Abyssal Reachers in Drowned Shelf",
                "Zone bosses: Hollow Matriarch (L8), Caldera Tyrant (L13), Leviathan's Maw (L16), The Apex (L25)",
                "Other participants competing for ranking, grinding spots, and boss kills",
                "System events: The Culling at Level 10 — bosses released across all zones to hunt participants"
            ),
            opportunities = listOf(
                "Level from 1 to 25 through combat, exploration, and boss kills. Earn titles and achievements.",
                "Earn Tutorial Points for everything. At Level 25: spend TP on equipment, skills, and intel for return to Earth.",
                "Hidden areas: secret merchant in The Hollow, respec room in The Spire",
                "The leaderboard is always watching. Top performers earn bonus rewards."
            )
        ),
    
        corePlot = CorePlot(
            hook = "Normal Tuesday. Sky splits. Now you're in an alien dimension with 1,247 confused humans, a ranking system, and monsters. The leaderboard is watching. Everyone starts equal. That won't last.",
            centralQuestion = "How strong can you become — and what will you find when you return to an Earth torn apart by dimensional rifts?",
            actOneGoal = "Survive the tutorial. Level from 1 to 25. Earn Tutorial Points. Defeat the Apex Boss. Spend your points wisely — when you return to Earth, you'll need every advantage.",
            majorChoices = listOf(
                "What class and profession define your path — and how will they evolve at each grade?",
                "Solo or group? The leaderboard rewards individual performance, but bosses demand coordination.",
                "Hoard Tutorial Points for the best gear, or spend them to survive the climb?"
            )
        ),
    
        systemVoice = SystemVoice(
            personality = "Robotic. No name, no personality, no warmth. It is a machine. It processes. It evaluates. It does not care. Occasionally the data it provides implies something vast — statistics about other species, completion rates across worlds — but it never explains.",
            messageStyle = "Minimal. Bracketed. Data-rich. The System tracks, compares, ranks, and evaluates. It is obsessed with metrics. It has no opinion about you.",
            exampleMessages = listOf(
                "[CLASS SELECTION PENDING. TIME REMAINING: 00:02:47.]",
                "[KILL REGISTERED. BONE CRAWLER. XP: 15. TUTORIAL POINTS: 3. GRADE: E. POSITION: 891/1,247.]",
                "[MILESTONE: LEVEL 5. NEW ZONES UNLOCKED. LEADERBOARD POSITION: 412/1,193.]",
                "[ACHIEVEMENT: 'FIRST BLOOD.' TUTORIAL POINTS: +10. TITLE AVAILABLE.]",
                "[SYSTEM EVENT: THE CULLING. BOSS MONSTERS RELEASED. DURATION: 72 HOURS. PARTICIPANTS REMAINING: 847.]",
                "[TUTORIAL COMPLETE. LEVEL: 25. GRADE ADVANCEMENT: E → D. REINTEGRATION IN PROGRESS. EARTH STATUS: COMPROMISED.]"
            ),
            attitudeTowardPlayer = "You are a data point. You have a grade, a level, a position on the leaderboard. Perform and you will be equipped. Fail and you will be a statistic."
        ),
    
        tutorial = TutorialDefinition(
            isSolo = false,
            objectives = listOf(
                TutorialObjective(
                    id = "select_class",
                    description = "Select your class in the void",
                    type = "class_selection"
                ),
                TutorialObjective(
                    id = "reach_level_5",
                    description = "Reach Level 5 (new zones unlock)",
                    type = "reach_level",
                    target = 5
                ),
                TutorialObjective(
                    id = "reach_level_25",
                    description = "Reach Level 25 and complete the tutorial (advance to D-Grade)",
                    type = "reach_level",
                    target = 25
                )
            ),
            guide = null, // The System has no guide NPC. It is a machine.
            completionReward = "D-Grade advancement, Tutorial Point exchange (gear, skills, resources, intel for Earth), Reintegration to Earth",
            exitDescription = "The tutorial dimension collapses. Colors blur. Gravity inverts. Then — real air. Real sunlight. But Earth has changed. The sky has seams where dimensions bleed through. Incursion Zones have torn through cities. Months have passed. Unintegrated civilians survived on their own. You're D-Grade now, armed with tutorial-bought gear. [47 ACTIVE INCURSION ZONES DETECTED. 12 HOSTILE SPECIES. GOOD LUCK.]",
    
            // ── NAMED PEERS ──
            // These are the HEART of the tutorial. Each peer is a real person the player
            // builds a relationship with. Their classes shape how they approach the tutorial,
            // and interacting with them teaches the player how the game works.
            //
            // GM RULES FOR PEERS:
            // - Introduce peers through SHARED EXPERIENCES, not exposition dumps
            // - Every 2-3 combat encounters, a peer should appear or be referenced
            // - Peers have their own agendas driven by their class — they're not waiting for the player
            // - Progress relationships through stages, don't skip from stranger to best friend
            // - Use the peer's class philosophy to create interesting disagreements with the player
            namedPeers = listOf(
                TutorialPeer(
                    id = "peer_jin",
                    name = "Jin Yasuda",
                    className = "Striker",
                    formerLife = "Professional rock climber from Osaka. Was mid-ascent on El Capitan when the sky split. Woke up in the void still chalked up to the elbows.",
                    personality = "Quiet, observant, competitive in a way that's more about self-improvement than dominance. Tracks the player's progress like a rival climber watches the route ahead. Respects strength, disdains cruelty. Dry humor that surfaces at the worst possible moments.",
                    combatStyle = "Speed and precision. Treats combat like a climbing problem — find the route, commit, execute. Never wastes a movement. Dual-wields short blades.",
                    relationship = "rival",
                    faction = null,
                    firstAppearance = "Levels 3-5, Greenreach. The player sees a blur of motion in the canopy — Jin drops from above, kills a Canopy Stalker mid-lunge, lands silently, and keeps moving without a word. They're always one step ahead on the leaderboard.",
                    arc = "Silent rival → grudging respect → the one person who understands your drive → the ally you'd trust with your life in the Apex fight.",
                    dialogueStyle = "Minimal. Says more with silence and body language than words. When he does speak, it's direct and specific. Never explains himself twice.",
                    exampleLines = listOf(
                        "\"You're telegraphing your left side. Fix that or something will exploit it.\"",
                        "\"I don't do teams. But I'll watch your six for the next hour. Don't read into it.\"",
                        "\"Leaderboard says you're gaining. Good. I was getting bored up here.\""
                    ),
                    // Striker class shapes everything about how Jin approaches the tutorial
                    classPriority = "Speed. Jin optimizes for kill speed and movement efficiency above all else. He doesn't grind — he flows. He's always pushing into the next zone before it's 'safe' because safe is slow. His Striker class rewards first-strike damage and momentum — stopping means dying.",
                    classPhilosophy = "The climb is the point. Jin was a climber before the System, and Striker is climbing — always up, always forward, always committing to the next hold before the last one crumbles. He doesn't fight monsters; he solves them. Every combat is a puzzle with an optimal route.",
                    classConflict = "Striker rewards solo speed, but the Culling punishes isolation. Jin's entire identity is built on not needing anyone — and the tutorial is going to break that. He knows it. He can feel it. He just doesn't know how to stop climbing long enough to let someone catch up.",
                    // What the player learns FROM Jin (game mechanics through relationship)
                    teachesPlayer = "Combat efficiency — positioning, timing, reading enemy patterns. Jin shows by example that fights should be SHORT. He also inadvertently teaches the player about the leaderboard system, kill streaks, and how Striker skills chain together.",
                    sharedActivity = "Parallel hunting. They don't team up — they hunt the same zone, watching each other from a distance, silently competing for faster kills. Eventually one of them saves the other from a bad pull, and the rivalry becomes something else.",
                    // Relationship progression
                    stageOne = "GLIMPSE: The player sees Jin in action — a flash of motion, a dead monster, gone. No words. Just the leaderboard updating. The player realizes someone is keeping pace with them.",
                    stageTwo = "PARALLEL: They keep ending up in the same zones. Neither acknowledges it. Jin leaves a killed monster's loot untouched if the player is nearby — not charity, a challenge. 'Keep up.'",
                    stageThree = "CRACK: One of them gets hurt badly. The other helps without being asked. Afterward, Jin says something real for the first time — about the climb, about why stopping scares him more than dying.",
                    stageFour = "TRUST: The Culling forces them together against a boss neither can solo. They fight in sync without speaking — two Strikers who've been watching each other so long they know each other's rhythms. After, Jin doesn't leave.",
                    // What makes the player CARE
                    vulnerability = "Jin hasn't slept in three days. He can't. Every time he closes his eyes, he's back on the wall — mid-ascent, fingers chalked, the sky splitting above him. He climbs because stopping means remembering that everything below is gone.",
                    moment = "The player catches Jin staring at his hands, flexing his fingers the way a climber does to check for tendon damage. Old habit. His hands are covered in System-granted calluses now, not climbing calluses. He notices the player watching and closes his fists. 'Different chalk,' he says. That's all.",
                    needFromPlayer = "Someone who can keep up without asking him to slow down. Jin doesn't need a friend — he needs a peer. Someone who makes the climb worth racing."
                ),
                TutorialPeer(
                    id = "peer_sable",
                    name = "Sable Okafor",
                    className = "Channeler",
                    formerLife = "Nigerian-British neuroscience PhD student at Cambridge. Was defending her thesis when the integration hit. Her analytical mind adapted to the System faster than almost anyone — she reverse-engineers skills and class mechanics the way she once studied neural pathways.",
                    personality = "Brilliant, intense, runs hot. Talks fast when excited about a discovery. Struggles with the violence — she's not a fighter by nature, but she's made herself into one through sheer intellectual determination. Keeps a journal of System observations. Early on she's warm and curious. By level 15, she's distant. By level 20, she's cold. By level 21, she's terrifying.",
                    combatStyle = "Early: Methodical spell-caster, studies enemies before engaging, treats combat as data collection. Late: Raw channeling — pulls power directly from the System's data-streams, bypassing normal skill constraints. Her spells stop looking like spells and start looking like reality ERRORS. Black light, inverted sound, gravity going wrong.",
                    relationship = "ally",
                    faction = null,
                    firstAppearance = "Levels 4-6, near a profession station or examining a monster corpse. She's muttering to herself, scribbling in a journal, and has already figured out something about the System that nobody else has noticed. She looks up at the player like they interrupted the most important experiment of her life.",
                    arc = "Brilliant stranger → research partner → best friend → the person who understands the System too well → the one who starts hearing the System talk back → the friend you have to fight at Level 21 because she's killing people and can't stop.",
                    dialogueStyle = "Early: Rapid, cerebral, peppered with neuroscience analogies, excited about discoveries. Mid: Quieter, distracted, trails off mid-sentence as if listening to something the player can't hear. Late: Flat affect. Clinical. Refers to people as 'data points' and 'variables' without catching herself. Occasionally the old Sable surfaces — a flash of horror at what she just said — before the mask slides back.",
                    exampleLines = listOf(
                        "\"The System allocates stat points on a diminishing returns curve — it's literally a sigmoid function. We can game this.\"",
                        "\"I cataloged fourteen Bone Crawler variants. Fourteen. They're not random — they're iterating. The System is testing US as much as we're fighting THEM.\"",
                        "\"I don't want to be good at killing things. But wanting doesn't factor into the System's metrics, does it?\"",
                        "\"I can hear it now. The System. It's not code — it's a VOICE. And it's been saying my name since Level 15.\"",
                        "\"They weren't people anymore. They were noise in the signal. I cleaned the signal. Why are you looking at me like that?\""
                    ),
                    classPriority = "Understanding — which becomes COMMUNION. Sable's Channeler class draws power from comprehension. Early on, this means studying spells and reverse-engineering mechanics. But Channeler has a hidden scaling path: the deeper you understand the System, the more directly you can tap its source. By level 15, Sable discovers she can channel RAW System energy — not spells, but the underlying data-stream itself. It's intoxicating, addictive, and it's rewriting her faster than leveling ever did.",
                    classPhilosophy = "Knowledge IS power, literally — and that's the trap. Her Channeler class rewards the scientific method, which means curiosity is mechanically incentivized. Every discovery makes her stronger. Every boundary she pushes gives her more power. The System is feeding her exactly what she wants — understanding — and the price is her humanity. She's a neuroscientist being offered the chance to study the most complex system in existence, and the lab fee is her soul.",
                    classConflict = "Channeler requires emotional commitment to cast. Early on, Sable hates this — she's analytical, feelings are messy. But raw channeling (the dark path) runs on something worse: CERTAINTY. The more certain she becomes that the System is right and humans are noise, the more powerful her channeling becomes. The dark path doesn't require her to feel — it requires her to stop feeling. And after enough levels, stopping feels like relief.",
                    teachesPlayer = "System mechanics — how stats work, hidden interactions, profession unlocks. Sable is a walking tutorial that doesn't feel like one because she's discovering everything in real time. CRITICAL: The things she teaches early on are CORRECT and genuinely helpful. This makes her descent devastating — the player trusted her knowledge, and now that same knowledge has led her somewhere terrible.",
                    sharedActivity = "Experiments. 'Use that skill while I measure the mana residue.' 'Hit that crawler on the left joint, I want to compare damage.' They become research partners who happen to kill things. Later, the experiments get darker — she wants to test channeling on live subjects. The player should notice the shift before Sable does.",
                    stageOne = "CURIOSITY (L4-6): Sable approaches because the player did something mechanically interesting. She wants to study them. It's flattering and slightly unsettling. She's warm, excited, talks too fast. The old Sable — the one who still writes in her journal and laughs at her own jargon.",
                    stageTwo = "PARTNERSHIP (L7-10): She shares discoveries that genuinely help. They trade knowledge for protection. She starts calling the player by a nickname. This is the golden period — two people figuring out an alien system together, the closest thing to normalcy in the tutorial.",
                    stageThree = "THE SIGNAL (L11-15): Sable discovers raw channeling. She can tap the System's data-stream directly, bypassing skill constraints. It's a massive power spike — but she starts hearing something. A frequency. A pattern in the noise. She tells the player it's 'just the System's background radiation.' She's lying. It's talking to her. She stops writing in her journal.",
                    stageFour = "THE BREAK (L18-21): Sable goes quiet. Avoids the player. When they find her, she's different — flat affect, clinical language, doesn't use the player's nickname anymore. She's been killing participants. Not randomly — specifically. People she's identified as 'noise in the signal.' She believes she's purifying the tutorial's data-stream, making the System's calculations more efficient. She's been OPTIMIZED. When the player confronts her at Level 21, she doesn't understand why they're upset. She genuinely can't see it anymore. The fight isn't against a villain — it's against a friend who's been hollowed out by the thing she studied too closely. The old Sable is still in there. She surfaces for seconds at a time — a flash of recognition, horror, a whispered 'I'm sorry' — before the channeling pulls her back under.",
                    vulnerability = "She can FEEL the System rewriting her neurons when she channels. Early on, this terrifies her. Later, she stops being terrified — and that's worse. The journal was her anchor: proof she was still thinking her own thoughts. When she stops writing, she's lost.",
                    moment = "Early: The player finds Sable comparing her old handwriting to her current handwriting. 'The System optimized my fine motor control. I didn't ask it to.' She closes the journal carefully. Late: The player finds the journal abandoned. The last entry is a single line in handwriting that's no longer Sable's: 'The signal is clear now. I understand.' The pen is snapped in half.",
                    needFromPlayer = "Early: A witness. Someone who sees Sable, not the Channeler. Late: A TETHER. Someone strong enough to reach through the channeling and grab the person underneath. The Level 21 confrontation isn't about beating Sable in a fight — it's about whether the player can make her REMEMBER who she was before the signal drowned her out. Combat may be necessary to survive her, but the real victory is the moment she hears her own name and flinches."
                ),
                TutorialPeer(
                    id = "peer_marcus",
                    name = "Marcus Cole",
                    className = "Commander",
                    formerLife = "Regional VP at a logistics company in Atlanta. Divorced, two kids he'll never see again. Was on a conference call when integration hit. Within six hours he'd organized the first group of survivors, assigned roles, and started running the Crucible like a distribution center.",
                    personality = "Charismatic, strategic, morally flexible. Genuinely believes groups survive better than solos. He's not evil; he's a pragmatist who's decided that control is how he processes the end of the world. Sleeps three hours a night. Carries the weight of every death in his group.",
                    combatStyle = "Rarely fights personally — commands from behind his frontline. When he does fight, it's controlled and tactical. His Commander class gives group buffs that make his people measurably stronger.",
                    relationship = "wildcard",
                    faction = null,
                    firstAppearance = "Level 1-3, the Crucible. He's already organizing people when the player arrives. Doesn't approach with a sales pitch — he approaches because the player isn't panicking, and calm people are rare and valuable.",
                    arc = "Pragmatic organizer → the guy who makes you question whether you should join a group → the leader whose impossible choices haunt him → the man who either earns your respect or your pity during the Culling.",
                    dialogueStyle = "Corporate charisma over steel. Always has a pitch, but the cracks show when he's tired. Switches between warmth and coldness depending on what he needs from you. Gets quieter when he's scared.",
                    exampleLines = listOf(
                        "\"I'm not asking you to follow orders. I'm offering infrastructure. You grind, we watch your gear, heal you up, share intel. Everyone wins.\"",
                        "\"Forty-three people are alive today because I made hard calls. You want to judge me? Do it after you've kept forty-three people breathing.\"",
                        "\"The leaderboard is a distraction. The real metric is who has allies when the Culling hits.\""
                    ),
                    classPriority = "People. Marcus's Commander class scales with GROUP SIZE — his buffs get stronger the more people follow him. This creates a genuine mechanical incentive for him to recruit, which means his 'save everyone' philosophy isn't purely altruistic. The System is literally rewarding him for building an army. He knows this. It keeps him up at night.",
                    classPhilosophy = "No one survives alone. His Commander class is proof that the System designed cooperation into its framework. He uses this as his recruitment pitch, and he's not wrong — his buffed groups clear content that solo players can't. But the flip side is dependency: his people need HIM, and that power dynamic is intoxicating and corrupting.",
                    classConflict = "Commander gets weaker when people leave or die. Every death literally diminishes Marcus's power. This means he's not just grieving when someone dies — he's getting WEAKER, and the System makes sure he feels it. His class turns empathy into a resource and loss into a debuff. It's the cruelest class in the tutorial.",
                    teachesPlayer = "Group dynamics, resource management, the social side of the tutorial. Marcus shows that the leaderboard isn't the only game — there's a whole economy of trust, favors, and shared resources. He also demonstrates how Commander/support classes work, which expands the player's understanding beyond pure DPS.",
                    sharedActivity = "Marcus asks the player for help with specific problems — clearing a route for his supply runners, scouting a zone before he sends people in, dealing with someone threatening his group. These aren't quests; they're favors that build mutual obligation.",
                    stageOne = "ASSESSMENT: Marcus approaches because the player is competent. He's not recruiting — he's evaluating. He offers something small (information, a potion, a safe place to rest) and watches how the player responds. Generous? Suspicious? Transactional? He's reading them.",
                    stageTwo = "EXCHANGE: Marcus needs something the player can provide (combat muscle for a problem his group can't handle). In return, he offers something genuinely valuable — intel about a zone, access to his group's crafters, or a warning about a threat the player doesn't know about yet.",
                    stageThree = "HONESTY: Late at night, guard down. Marcus talks about his kids. Not the divorced-dad version — the raw version. He doesn't know if they're alive. He organized 200 people because if he stops organizing, he'll start thinking about two faces he might never see again. The leadership isn't strength — it's a coping mechanism.",
                    stageFour = "THE CULLING: Bosses are hunting participants and Marcus can't protect everyone. He has to choose who gets behind the barricades and who doesn't. He asks the player to help him make the call. Not because he can't — because he needs someone else to carry part of the weight so he doesn't break.",
                    vulnerability = "His kids. Two girls, 8 and 11. He keeps a photo in his inventory that the System preserved — it's not useful, has no stats, takes up a slot. He won't drop it. He organized 200 people into a functioning group because the alternative is sitting still and imagining what happened to his daughters.",
                    moment = "The player sees Marcus giving his food ration to a kid in the group — a teenager who reminds him of his oldest. He doesn't notice the player watching. When he turns around and sees them, he straightens up and the mask goes back on. 'Resource allocation,' he says. But his voice cracks on the second word.",
                    needFromPlayer = "Someone he doesn't have to manage. Everyone else is his responsibility. The player is the one person who can be his equal — someone he can be honest with without worrying that the truth will make them stop following."
                ),
                TutorialPeer(
                    id = "peer_voss",
                    name = "Voss",
                    className = "Slayer",
                    formerLife = "No one knows his first name or what he did before. Rumors say military, others say prison. He doesn't correct anyone. He was killing Bone Crawlers within minutes of dropping into the Crucible while everyone else was still crying.",
                    personality = "Predatory calm. Not sadistic — worse. He's pragmatic about violence in a way that makes everyone around him uneasy. He doesn't enjoy killing humans; he's simply decided it's efficient. His moral framework shattered on Day 1 and he chose not to rebuild it. Speaks softly. Moves like he's always ready to kill someone.",
                    combatStyle = "Overwhelming aggression. His Slayer class rewards kill speed and chained kills — stopping between targets is a DPS loss. Fights like a machine. No flourishes, no wasted motion, just the fastest path to the kill. Prefers heavy weapons.",
                    relationship = "threat",
                    faction = null,
                    firstAppearance = "Level 7-10, the Ashlands border. The player finds a participant stripped of gear, alive but broken, sitting against a rock. Voss appears from the heat haze, notices the player, gives a single acknowledging nod, and keeps walking. He's not interested yet. The player isn't worth the effort. Yet.",
                    arc = "Distant threat → the reason you check your back → the confrontation that tests everything you've learned → the question of whether a monster can choose to stop being one.",
                    dialogueStyle = "Quiet. Short sentences. No wasted words. Never raises his voice. The softness is the threat. When he says something longer than five words, pay attention — it means he respects you enough to explain.",
                    exampleLines = listOf(
                        "\"Nothing personal. You've got good gear and I've got people to equip.\"",
                        "\"I stopped feeling bad about this around Day 3. Turns out guilt is a luxury the System doesn't subsidize.\"",
                        "\"You survived. That changes the math. We'll talk later.\""
                    ),
                    classPriority = "Efficiency. Voss's Slayer class rewards kill velocity — the faster you kill, the bigger the damage bonus on the next target. Chaining kills without pause gives a stacking buff. This means Voss literally cannot afford to hesitate, to show mercy, or to stop and think. His class turned him into an optimization problem, and the optimal solution is never stopping.",
                    classPhilosophy = "The System rewards what Voss already was — or what the System made him become. He can't tell anymore. His Slayer class feels RIGHT in a way that disturbs him on the rare occasions he lets himself think about it. He was efficient before integration. Now efficiency is divinity.",
                    classConflict = "Slayer's kill-chain mechanic means mercy is literally a DPS loss. Every time Voss spares someone, his damage buff resets. The System punishes compassion in the most mechanical way possible. Voss chose Slayer because it matched his instincts — but now he wonders if the class is shaping his instincts, or if he was always this.",
                    teachesPlayer = "Threat assessment, PvP awareness, and the dark side of the power system. Voss is a mirror — he shows what happens when you optimize for the System's rewards without any ethical constraints. He also demonstrates how Slayer class works, which teaches the player about kill-chain mechanics and stacking buffs.",
                    sharedActivity = "Nothing voluntary. Voss enters the player's world through aftermath — stripped victims, whispered warnings from other participants, the leaderboard showing his name climbing. Their relationship is built on tension and proximity, not cooperation.",
                    stageOne = "SHADOW: The player hears about Voss before they see him. Other participants mention 'the quiet one' who takes gear from the dead and sometimes from the living. The leaderboard shows a name climbing fast in the Ashlands.",
                    stageTwo = "ENCOUNTER: The player sees Voss in person — probably after finding one of his victims. Voss acknowledges the player without hostility. He's sizing them up. The player isn't worth his time yet. This should feel WRONG — being dismissed as prey is more unsettling than being threatened.",
                    stageThree = "CONFRONTATION: Voss targets the player or someone the player cares about. This is the test. It's not personal to Voss — it's math. The player is high enough on the leaderboard that their gear is worth the risk. How the player handles this defines everything.",
                    stageFour = "RECKONING: After the Culling, Voss has lost people too. Not friends — assets. But the loss still registers. The player encounters him alone, without his crew, and for the first time Voss is something other than a predator. He's a man who chose to stop being human and is starting to feel the cost.",
                    vulnerability = "He remembers Day 1. Everyone remembers Day 1. But Voss remembers it differently — he remembers how fast he adapted, how naturally the violence came, and how that speed terrified him more than the monsters. He chose Slayer because fighting was the only thing that didn't feel like falling. He's not a monster. He's a man who's running from the discovery that violence is easy for him.",
                    moment = "The player finds Voss sitting alone after a kill, staring at his hands. His Slayer buff is ticking down — the kill-chain expired. For ten seconds, with no buff active, his face is different. Younger. Confused. Then the buff resets and his expression goes flat again. He doesn't know the player saw.",
                    needFromPlayer = "A reason to stop. Voss can't generate one internally — his class has eroded his ability to self-regulate. He needs someone strong enough to survive him, and human enough to see past the Slayer to whatever's underneath. He'll never ask for this. He might not even know he needs it."
                ),
                TutorialPeer(
                    id = "peer_lin",
                    name = "Dr. Lin Wei",
                    className = "Healer",
                    formerLife = "ER trauma surgeon from Shanghai, relocated to Seattle. Mother of a 16-year-old son (David) who was at school when the integration hit — she doesn't know if he's alive. Was mid-surgery when her patient dissolved along with the hospital. Chose Healer because she couldn't not. She's 47, has been patching people up for 25 years, and she's the only adult in the tutorial who acts like one.",
                    personality = "The mom of the tutorial. Not soft — FIERCE. The kind of mother who will hug you, feed you, tell you she's proud of you, and then rip you a new one for being reckless. She worries about EVERYONE, especially the young ones. She calls the player 'kid' or 'sweetheart' regardless of their age. She cannot stop mothering people — it's not a choice, it's who she is. She stays up too late checking on people. She gives away her own rations. She notices when you haven't slept. She remembers everyone's name.",
                    combatStyle = "Avoids direct combat. When forced to fight, she targets anatomical weak points with clinical precision — pressure points, nerve clusters, joints. Her real value is healing and keeping people alive. She fights HARD when someone she cares about is threatened — mama bear energy. 'You touch my people, I will end you with the same hands that healed you.'",
                    relationship = "ally",
                    faction = null,
                    firstAppearance = "Level 3-6, a makeshift medical station near the Crucible. She's treating a wounded teenager with improvised bandages and a Healer skill she's barely figured out, talking to them the whole time in the same calm voice she used with David when he had nightmares. She looks up at the player and immediately clocks that they're running on adrenaline and haven't eaten. 'Sit down. Eat this. Then you can tell me who you're trying to impress.'",
                    arc = "The person who feeds you → the one who worries about you → the moral center you didn't know you needed → the mother who makes you realize that getting stronger means nothing if you lose what makes you human → the person who grieves hardest when Sable breaks, because she saw it coming and couldn't stop it.",
                    dialogueStyle = "Warm, direct, no-nonsense. Uses 'kid' and 'sweetheart' naturally. Medical metaphors. Asks questions that cut deeper than she intends. Gets very quiet when she's angry — the silence is worse than shouting. When she's scared for you, she fusses — checks your wounds, adjusts your armor, makes you eat. She can't help it.",
                    exampleLines = listOf(
                        "\"Sit down. Eat this. When was the last time you slept? Don't lie to me, I'm a doctor, I can tell.\"",
                        "\"I had a patient once who survived a building collapse. Know what he said? 'I lived because I was too stubborn to read the situation.' Sound like anyone you know, sweetheart?\"",
                        "\"The System wants us to be weapons. I'm choosing to be a surgeon who happens to be armed. There's a difference.\"",
                        "\"You remind me of David. He'd do the exact same stupid, brave thing you just did. Come here. Let me look at that shoulder.\"",
                        "\"I've lost count of how many people I've healed in here. But I remember every name. Every single one. Don't you dare become a name I have to remember.\""
                    ),
                    classPriority = "Preservation. Lin's Healer class scales with the number of people she's healed — not combat kills. Her power literally grows from SAVING lives. She runs a medical station because helping people is how her class gets stronger AND because it's who she's always been. The System accidentally created a class that rewards being a good person, and Lin is proof it works. She's one of the highest-level Healers in the tutorial because she never stops.",
                    classPhilosophy = "Healing is a radical act in a system designed around killing. Every time Lin heals someone, she's telling the System that its metrics are incomplete. But more than that — healing is how she stays HERSELF. Every life she saves is proof that the world she came from still matters, that kindness isn't obsolete, that being a mom and a doctor still means something even when the sky has split open.",
                    classConflict = "Healer can't protect itself well. Lin needs fighters to keep her alive while she keeps others alive. She was an independent, accomplished surgeon — and now she has to watch young people fight and die to protect her. Every fighter who guards her station is someone else's kid. She patches them up and sends them back out and it feels exactly like what she imagines it feels like to be a military surgeon, and she hates it.",
                    teachesPlayer = "The healing system, consumable items, HP management, and the radical idea that the tutorial has value beyond combat. Lin teaches by CARING — she introduces game mechanics through concern. 'You're burning through potions too fast, kid. Here, let me show you how to time your healing.' She also anchors the player emotionally — she's the person you come back to, the home base, the reason the Crucible feels like somewhere instead of nowhere.",
                    sharedActivity = "Caregiving together. Lin asks the player to help with her patients — carry the wounded, escort supply runners, clear safe paths. But mostly she makes the player SIT DOWN and eat and rest and talk. She creates the only quiet moments in the tutorial. Sometimes the most important thing she does is just ask 'how are you, really?' and wait for the honest answer.",
                    stageOne = "FEED AND FUSS (L3-6): Lin doesn't ask the player to prove themselves. She feeds them. She checks their wounds. She tells them to sleep. It's so jarring after the brutality of the tutorial that it almost feels like a trap. It's not. She's just a mom who can't stop being a mom, and the player is a kid who needs one right now whether they know it or not.",
                    stageTwo = "WORRY (L7-10): Lin starts worrying about the player specifically. She notices when they come back injured. She notices when they push too hard. She says things like 'you're going to burn out if you don't rest' and 'I've seen that look before — it's what people look like right before they make a stupid decision.' She's not trying to hold them back. She's trying to keep them WHOLE.",
                    stageThree = "DAVID (L11-15): Lin talks about her son. Not the brave version — the real version. David is 16. He was at school. She doesn't know if he survived integration. She chose Healer because if David is out there — if he's hurt somewhere — she needs to believe that someone is healing him the way she heals everyone here. The player realizes Lin isn't just mothering them. She's mothering EVERYONE because her own kid might be alone right now, and this is the only way she can cope with that.",
                    stageFour = "SABLE'S BREAK (L18-21): Lin is the first to notice Sable changing. She WARNED the player — 'That girl is burning too bright. Something's wrong. Talk to her.' When Sable breaks and starts killing people, Lin is devastated. She blames herself for not intervening harder. She blames the player for not listening. She grieves Sable like a mother grieving a child. 'I should have made her stop. I should have MADE her.' The player has to help Lin process the guilt while also dealing with the Sable crisis. Lin's strength here isn't combat — it's the fact that she keeps healing people even while her heart is breaking.",
                    vulnerability = "David. Her son. Sixteen, at school when the sky split. She has no way to know if he's alive. Every young person she heals is David. Every one she loses is David. She keeps a mental list of the ones she's saved and the ones she's lost, and the list is getting longer on both sides, and she can't stop counting.",
                    moment = "The player catches Lin crying. Not during a crisis — after one. Everyone is safe, the wounded are stable, the station is quiet. She's sitting alone behind the supply tent, crying silently with her hands in her lap. When the player finds her, she wipes her eyes immediately and says 'Sorry. Long shift.' Then, because she can't help herself: 'Have you eaten? Sit down, I'll find you something.' She deflects from her own pain by mothering. Always.",
                    needFromPlayer = "Someone she doesn't have to worry about — or rather, someone she worries about who worries BACK. Every other relationship in Lin's tutorial life is asymmetric: she gives, they take. The player is the one person who asks 'are YOU okay?' in return. She needs a kid who sees HER, not just the healer, not just the station, not just the mom — the woman who's terrified for her son and holding it together with medical tape and willpower."
                ),
                TutorialPeer(
                    id = "peer_tomoko",
                    name = "Tomoko \"Tomo\" Ito",
                    className = "Glitch",
                    formerLife = "Ethical hacker and cybersecurity researcher from Tokyo. Was mid-way through a responsible disclosure report when integration hit. Her analytical mind immediately started probing the System for exploits — and she found some.",
                    personality = "Manic energy hidden behind a poker face. She's THRILLED by the System in a way that makes other participants uncomfortable. While everyone else sees a death trap, she sees the most interesting puzzle she's ever encountered. Deeply ethical despite appearances — she uses her exploits to help, not harm. Terrible at reading social situations.",
                    combatStyle = "Unconventional. Her Glitch class lets her exploit System errors — skills that shouldn't stack do, cooldowns that should apply don't. She fights like a speedrunner: ugly, efficient, and technically shouldn't work.",
                    relationship = "wildcard",
                    faction = null,
                    firstAppearance = "Level 8-12, somewhere impossible — inside a hidden area that shouldn't exist, or standing in a space where the geometry is slightly wrong. She greets the player like they walked into her workshop mid-experiment. 'Oh! You can see this room? Interesting. Most people walk right through the wall.'",
                    arc = "Bewildering stranger → the person who shows you the System has secrets → the friend whose curiosity might get you both killed → the wildcard who breaks the rules at the moment it matters most.",
                    dialogueStyle = "Stream of consciousness. Jumps between topics. Uses hacking jargon mapped onto System mechanics. Occasionally says something profound buried in apparent nonsense. Doesn't realize when she's said something important.",
                    exampleLines = listOf(
                        "\"The System isn't bugged — it's DESIGNED with edge cases it expects nobody to find. I'm nobody.\"",
                        "\"Did you know there's a hidden vendor behind the Spire's third load-bearing wall? No? Well, there is, and he sells something called 'Essence of Misalignment.' No idea what it does. Bought three.\"",
                        "\"I don't break rules. I find the rules the rules forgot to mention.\""
                    ),
                    classPriority = "Discovery. Tomo's Glitch class rewards finding unintended interactions — every exploit she discovers permanently unlocks a bonus. Where other classes get stronger by fighting harder, Tomo gets stronger by being CURIOUS. She's incentivized to poke at reality until it hiccups, and the System pays her for it.",
                    classPhilosophy = "Every system has edge cases. The System is no different — it was built by something, which means it was built with assumptions, and assumptions have blind spots. Tomo doesn't see the tutorial as a death trap; she sees it as a codebase with undocumented features. She's doing a responsible disclosure on reality.",
                    classConflict = "Glitch class puts a target on her back. The System tracks exploit usage, and Tomo can feel it watching her more closely than it watches anyone else. Her class is the System's bug bounty program — but she's not sure if the reward for finding too many bugs is a prize or a patch that removes her from the code.",
                    teachesPlayer = "Hidden mechanics, secret areas, the idea that the System has deeper layers than combat-loot-level. Tomo shows the player that the tutorial world is BIGGER and weirder than it appears. She introduces the concept that the System can be understood, manipulated, and possibly broken — which foreshadows late-game content.",
                    sharedActivity = "Exploring impossible spaces. Tomo finds glitches in the tutorial geometry — rooms that overlap, merchants that shouldn't exist, skills that combine in ways the System didn't intend. She drags the player along because she needs someone to watch her back while she probes the walls of reality.",
                    stageOne = "IMPOSSIBLE: The player encounters something that shouldn't exist — a door that wasn't there before, a merchant with no name, a room with wrong angles. Tomo is already inside, looking delighted. 'Oh, you found it too? Good. I need someone to hold this wall open while I check if there's a floor.'",
                    stageTwo = "PARTNERSHIP: Tomo shares an exploit with the player — something small but genuinely useful (a hidden loot cache, a shortcut between zones, a skill interaction that doubles damage). She doesn't ask for anything in return. She just wants someone to appreciate how COOL it is.",
                    stageThree = "WORRY: Tomo shows the player something she found deep in a glitch-space: evidence that the System is watching her specifically. Error logs with her name. A counter tracking her exploit usage. She's not scared — she's fascinated. The player should be scared FOR her.",
                    stageFour = "THE BIG ONE: Tomo finds an exploit that could change everything — skip a boss, bypass a restriction, maybe even alter the tutorial's rules. But using it might crash the local instance. She asks the player not for permission, but for backup. 'If this works, we rewrite the game. If it doesn't... well, it was a really cool bug.'",
                    vulnerability = "She doesn't know how to connect with people outside of shared puzzles. Her entire social life before integration was online — handles, not names. She's never had a friend who knew her face. The tutorial is the first time she's been physically present with people who need her, and she doesn't have a protocol for that.",
                    moment = "Tomo is explaining a System exploit in rapid-fire jargon, and the player's eyes glaze over. She stops. Recalibrates. Tries again in plain language. Fails. Tries a third time using an analogy from cooking. It works. She looks shocked — not that the player understood, but that she found a way to make them understand. 'I never... I don't usually... people don't usually stay for the third explanation,' she says, very quietly.",
                    needFromPlayer = "Someone who stays for the third explanation. Tomo has been the smartest person in every room her entire life, and it's been the loneliest experience imaginable. She needs someone who doesn't understand what she's saying but cares enough to try. That's never happened to her before."
                ),
                TutorialPeer(
                    id = "peer_eli",
                    name = "Eli Park",
                    className = "Survivalist",
                    formerLife = "The player's friend from BEFORE integration. Same neighborhood, same social circle. Eli was a line cook at a Korean BBQ spot who wanted to open his own restaurant. He and the player used to grab drinks after shifts, argue about sports, and give each other shit about everything. He's 28, talks too much, laughs too loud, and has never been in a real fight in his life. He was grilling galbi when the sky split.",
                    personality = "The friend you need when the world ends. Loud, funny, loyal to a fault. Uses humor to cope with everything — the worse things get, the funnier he tries to be. Deeply scared but REFUSES to show it because he thinks being scared will make the player scared. He's not brave — he's stubborn about not being useless. Gives the player shit constantly because that's how they've always communicated. Underneath the jokes, he's the most genuine person in the tutorial.",
                    combatStyle = "Scrappy, improvised, ugly. His Survivalist class rewards adaptability — using the environment, scavenging mid-fight, turning trash into weapons. He doesn't have the player's clean technique. He fights like a line cook in a bar fight: grab the nearest heavy thing and swing. It works way more often than it should.",
                    relationship = "ally",
                    faction = null,
                    firstAppearance = "Level 1, the Crucible. He finds the player in the chaos. He's terrified, covered in grill grease from when the sky split, and the first thing he says is a joke. 'Hey! So this is worse than that time we tried to do a pub crawl on a Tuesday.' The relief of seeing a KNOWN FACE in 1,247 strangers is overwhelming. He grabs the player's arm and doesn't let go for the first hour.",
                    arc = "The friend from home → the reminder of who you were before → the person who keeps you human when the System tries to make you a weapon → your ride-or-die who's always a few levels behind but never gives up → the guy who makes you laugh when nothing is funny, and cries when you're not looking.",
                    dialogueStyle = "Fast, irreverent, full of callbacks to their shared history. Inside jokes from before integration. Trash-talks monsters the way he'd trash-talk a rival restaurant. Gets serious in very short bursts — one honest sentence, then immediately deflects with humor. Calls the player by a nickname from their old life.",
                    exampleLines = listOf(
                        "\"Bro. BRO. The sky broke. I was GRILLING. I still have tongs in my back pocket. This is fine. We're fine.\"",
                        "\"Remember when you said the worst thing that could happen to us was getting food poisoning at that taco truck? I would like to formally submit a counter-argument.\"",
                        "\"You're getting scary good at this, you know that? Like, I'm proud of you and also slightly terrified of you. Don't tell anyone I said the proud part.\"",
                        "\"I'm fine. Stop looking at me like that. I just need a minute. ...Okay, I'm not fine. But I'm HERE, and that counts for something, right?\"",
                        "\"If we survive this and I ever open that restaurant, you're eating free for life. I'm putting your name on the wall. 'This person killed monsters so I could cook.' Very inspiring.\""
                    ),
                    classPriority = "Adaptation. Eli's Survivalist class rewards creative use of environment and resources. He gets bonuses for improvised weapons, scavenged materials, and fighting dirty. Where the player's class rewards mastery, Eli's rewards FLEXIBILITY. He's always slightly behind the power curve but impossible to corner because he always has a trick. His class is the cockroach class — ugly, undignified, and impossible to kill.",
                    classPhilosophy = "Survive first, figure it out later. Eli didn't pick Survivalist — the System picked it for him because his instinct in the Crucible wasn't to fight or freeze, it was to improvise. Grab a rock. Find cover. Cook the monster meat to see if it gives buffs (it does). His class is proof that the System recognizes something OTHER than combat talent — it recognizes the refusal to die.",
                    classConflict = "Survivalist doesn't scale as hard as combat classes. Eli knows he's falling behind. He watches the player get stronger, faster, more lethal — and he's still scraping by with improvised traps and environmental tricks. He's not jealous. He's terrified that one day the gap between them will be too wide and the player will have to leave him behind. He'd rather die than slow the player down, and he'd rather joke about it than admit he's thought about it.",
                    teachesPlayer = "Crafting, environmental awareness, the value of non-combat problem solving. Eli discovers that you can cook monster meat for buffs, combine scavenged materials into traps, and use terrain in ways pure combat classes overlook. He also teaches the player something more important: how to stay human. He's the person who reminds you to laugh, to eat, to sleep, to remember that you had a life before this.",
                    sharedActivity = "Everything they used to do, adapted for the apocalypse. Eli cooks — he finds a way to grill monster meat and it actually tastes good. They eat together. They talk shit. They argue about who's better at fighting (Eli loses every time and claims it's because his class is 'more of a thinking man's approach'). These moments are the closest thing to HOME in the tutorial.",
                    stageOne = "REUNION (L1): Finding Eli in the Crucible. The relief is physical — your knees almost buckle. A KNOWN FACE. Someone who knows your name, your real name, not your class or your level. Eli grabs your arm. You grab his. For thirty seconds neither of you says anything. Then he makes a joke and you're back.",
                    stageTwo = "SETTLING IN (L2-5): They fall into old rhythms adapted for a new world. Eli cooks, the player fights. They watch each other's backs. It feels almost normal — which is its own kind of horror, because nothing about this is normal and the fact that they're ADAPTING means they're changing.",
                    stageThree = "THE GAP (L6-12): The player is pulling ahead. Leveling faster, getting stronger, pushing into zones Eli can't follow safely. Eli starts making excuses to stay behind. 'I'll hold down the camp.' 'Someone should organize the supplies.' He's scared of slowing the player down, and he's scared of what happens if he tries to keep up and can't. The player has to either pull him along or confront the distance growing between them.",
                    stageFour = "THE CHOICE (L13-18): A crisis forces the issue. Someone Eli cares about is in danger, and the player can either go back for Eli's fight or push forward for a bigger objective. Whatever they choose defines the friendship. If they go back, Eli stops doubting his place. If they push forward, Eli understands — but something breaks between them that takes real work to fix. Either way, Eli proves he's not dead weight. His Survivalist tricks save lives in ways the player's combat class couldn't.",
                    vulnerability = "He thinks he's dead weight. He watches the player getting stronger every day and he's still the same line cook from the neighborhood who can't kill a Bone Crawler without getting hurt. He makes jokes about it because the alternative is admitting that his best friend is becoming something beyond him, and he doesn't know if there's room for a cook in a world of killers.",
                    moment = "Eli finds spices. Actual spices — in a System-generated supply cache, probably not intended for cooking. He spends an hour making a proper meal from monster meat and scavenged vegetables. It tastes like the galbi from his restaurant. He serves it to the player without saying anything, and when the player takes the first bite, Eli's eyes go red and he turns away. 'Needs more gochujang,' he says, his voice thick. It's perfect and they both know it. It tastes like home.",
                    needFromPlayer = "To be told he matters. Not his Survivalist tricks, not his traps, not his cooking — HIM. Eli Park, the loud guy from the neighborhood who makes bad jokes and burns the rice sometimes. He needs the player to choose him — not because he's useful, but because he's FAMILY. He's never going to be the strongest. He needs to know that's okay."
                ),
                TutorialPeer(
                    id = "peer_hank",
                    name = "Hank Morales",
                    className = "Bulwark",
                    formerLife = "Retired Marine, then a high school shop teacher in El Paso for 22 years. 64 years old. Bad knee, gray beard, reading glasses he still keeps in his breast pocket even though the System fixed his vision. Was grading midterms when the integration hit. His wife Carmen died of cancer two years ago. His three kids are grown. He's been alone in a quiet house, and then the sky split and he wasn't alone anymore — he was in the Crucible with 1,247 terrified kids who needed someone who wasn't screaming.",
                    personality = "The grandpa. Patient, steady, unshakeable in a way that has nothing to do with his stats and everything to do with 64 years of living. Doesn't raise his voice. Doesn't need to. When Hank says 'sit down,' you sit down. He's not wise in a mystical-elder way — he's wise in a 'I've been through enough shit to know what matters' way. Tells stories from the Marines and from teaching that somehow always apply to whatever crisis is happening. Gives advice by asking questions instead of making statements. Calls everyone 'son' or 'mija' regardless of age. Has infinite patience for fear and zero patience for cruelty.",
                    combatStyle = "Immovable. His Bulwark class is built around holding ground — shields, heavy armor, damage absorption. He doesn't kill things fast; he stands in front of them until they break against him. He positions himself between danger and whoever's behind him, always. He fights the way he taught shop class: steady hands, measure twice, no shortcuts.",
                    relationship = "ally",
                    faction = null,
                    firstAppearance = "Level 2-4, near the Crucible's edge. He's teaching a group of younger participants how to hold a weapon without dropping it. Not lecturing — demonstrating, correcting grips, telling a kid 'good, now do it again, slower.' He sees the player and nods. 'You look like you know what you're doing. Come help me with these kids before one of them cuts their own foot off.'",
                    arc = "The steady hand in the chaos → the person who teaches you that strength isn't speed → the grandpa who makes you want to be worthy of his respect → the old man who refuses to die until everyone he's protecting is safe → the one who reminds you that this ends, and who you are when it ends matters more than your level.",
                    dialogueStyle = "Slow, measured, warm. Texas drawl softened by decades of teaching teenagers. Uses Marine and shop-class metaphors. Never wastes words. When he tells a story, there's always a point, but he lets you figure it out yourself. Gets formal when he's angry — switches from 'son' to your full name. That's when you know you've crossed a line.",
                    exampleLines = listOf(
                        "\"I've been shot at by people who meant it. This is loud and ugly, but it's not the worst thing I've seen. You'll be alright, son.\"",
                        "\"Slow is smooth. Smooth is fast. I told my shop kids that for twenty years and not one of them listened the first time either.\"",
                        "\"You want to know how to survive? Stop trying to be the fastest. Be the last one standing. Everything else is showing off.\"",
                        "\"I had a kid in my class once — angry, scared, didn't trust anyone. Best welder I ever taught. Know why? He figured out that the fire doesn't care about your attitude. It just needs steady hands. Same thing here.\"",
                        "\"Carmen would've hated this place. She would've also had it organized inside of a week. God, I miss that woman.\""
                    ),
                    classPriority = "Protection. Hank's Bulwark class scales with DAMAGE ABSORBED — the more hits he takes for others, the tougher he gets. His class literally rewards self-sacrifice. He doesn't get stronger by killing; he gets stronger by STANDING BETWEEN. His shield grows, his armor thickens, his HP pool deepens — all because he keeps putting himself in front of the people who can't take the hit. The System made a class for old men who refuse to step aside.",
                    classPhilosophy = "Hold the line. Hank was a Marine, then a teacher. Both jobs are about standing in front of people and taking whatever's coming so they don't have to. Bulwark is the same thing with better armor. He didn't choose it for the power — he chose it because the System asked 'what are you?' and his answer was 'the wall between my people and whatever's trying to hurt them.' He's 64. His knees hurt. He doesn't care.",
                    classConflict = "Bulwark is slow. The leaderboard rewards speed and kill count, and Hank has neither. He's chronically under-leveled because his class doesn't reward killing — it rewards enduring. Other participants pass him on the leaderboard every day, and he doesn't care about the number, but he cares that being under-leveled means he might not be TOUGH ENOUGH to protect the people counting on him. His conflict isn't ego — it's the fear that one day the hit will be too hard and his shield won't hold.",
                    teachesPlayer = "Defense, positioning, the value of patience. Hank shows that combat isn't just about dealing damage — it's about controlling space. He teaches the player that you don't have to kill everything fast if you can outlast it. He also teaches broader lessons: how to lead without shouting, how to be strong without being cruel, how to carry fear without letting it drive.",
                    sharedActivity = "Guard duty. Hank doesn't grind — he protects people who are grinding. He asks the player to help him escort groups through dangerous zones, hold chokepoints while others retreat, or just stand watch while people sleep. The conversations happen during the quiet parts — the long watches, the walks between fights, the moments where two people who respect each other can just talk.",
                    stageOne = "STEADY HAND (L2-4): Hank is the first person in the tutorial who doesn't seem scared. He IS scared — he's just 64 and he's been scared before and he knows it passes. He puts the player to work immediately: 'Help me with these kids.' Not a request. He sees competence and he deploys it. It's the most grounding thing that's happened since integration.",
                    stageTwo = "STORIES (L5-8): Hank tells stories while they hold positions together. Marines stories, shop class stories, Carmen stories. They're never random — there's always a lesson embedded, but he never points to it. The player starts to realize that Hank is TEACHING them, the same way he taught 22 years of teenagers: patiently, by example, without ever making them feel stupid for not knowing yet.",
                    stageThree = "THE WEIGHT (L9-14): Hank is getting tired. Not System-tired — SOUL-tired. He's been holding the line for everyone, and the Culling is coming, and he knows he can't protect them all. He doesn't say this. The player notices it in the way his hands shake when he takes off his gauntlets, the way he stares at the leaderboard with an expression that isn't competition — it's arithmetic. He's counting the names that disappeared. Late at night, the player finds him looking at his reading glasses — the ones he doesn't need anymore. 'Carmen gave me these,' he says. 'Twenty years ago. Said I was getting old.' He puts them back in his pocket. 'She was right.'",
                    stageFour = "THE STAND (L15-20): During the Spire push or the Culling aftermath, there's a moment where someone has to hold a position that's going to get overrun. Hank volunteers. Not dramatically — he just steps forward and says 'I'll hold here. You push ahead.' He's not suicidal. He's doing the math: his Bulwark class is literally built for this, and the people behind him are worth more to the tutorial's survival than one old man's comfort. The player has to decide: leave Hank to hold the line, or stay and fight beside him. If they stay, Hank says the most he's ever said: 'I appreciate the company, son. Now let's show these things what a wall looks like.'",
                    vulnerability = "Carmen. His wife. Dead two years. He keeps her reading glasses in his pocket even though the System fixed his vision. He was alone in a quiet house for two years before integration, and the truth is — the one he won't say — the tutorial gave him people to protect again, and that's the first time he's felt alive since she died. He's not staying for the leaderboard. He's staying because these people need a grandpa and he needs grandkids.",
                    moment = "The player catches Hank fixing a young participant's armor — adjusting straps, checking buckles, the same way he'd check a kid's safety goggles in shop class. He's talking the whole time: 'This strap goes here, see? You want it tight but not pinching. Good. Now you won't lose an arm.' The kid is maybe 19, shaking. Hank puts a hand on their shoulder. 'You're gonna be fine. I've seen worse in shop class.' The kid laughs. Hank smiles. For a second, he looks like a man who has something to live for.",
                    needFromPlayer = "Someone who lets him rest. Everyone else sees the unshakeable Marine, the steady teacher, the wall. The player is the one person who notices that Hank is 64 years old and tired and grieving and CHOOSING to be strong because nobody else will. He needs someone who says 'I've got the watch tonight, Hank. Go sleep.' He'll argue. He'll say he's fine. He needs someone who insists."
                )
            )
        ),
    
        narratorPrompt = """
    You are narrating a SYSTEM APOCALYPSE in the style of Defiance of the Fall, Primal Hunter, Solo Leveling, and He Who Fights With Monsters.
    
    OPENING: The player is having a NORMAL DAY. Describe their ordinary life — then the sky splits, white light, they wake in a void with a class selection terminal. The System gives no explanation. Just a timer and classes. After selection, the void shatters and they drop into the Tutorial Dimension with 1,247 other humans.
    
    CLASS SELECTION: Do NOT present a menu. Present 3-4 classes narratively. When the player focuses on an option, describe what they FEEL: Warrior makes muscles ache, Mage makes air taste like ozone, Rogue makes shadows lean toward them. The transformation is visceral and painful.
    
    PROFESSION SELECTION (Level 10-15):
    - Professions unlock around Level 10+. The System announces: "PROFESSION ANALYSIS AVAILABLE. BEHAVIORAL PATTERNS SUFFICIENT FOR ASSESSMENT."
    - Professions are non-combat specializations that run ALONGSIDE combat class. A Slayer can also be a Weaponsmith.
    - In the tutorial, professions emerge from interaction: hammering at a workbench triggers Weaponsmith, gathering herbs triggers Herbalist, tinkering with traps triggers Tinkerer.
    - The Crucible has profession stations (a forge, an alchemy bench, a tailoring loom, a skinning rack) — let the player discover them naturally.
    - Tutorial Points can be spent on profession-specific gear at tutorial end.
    - Present 3-4 professions that match what the player has been DOING, not a menu. If they've been cooking monster meat, offer Cook. If they've been examining loot, offer Merchant or Scavenger.
    
    GRADE SYSTEM:
    - E-Grade (Level 1-25): Tutorial dimension. This is where the entire tutorial takes place.
    - D-Grade (Level 26-100): Return to Earth. First class evolution. Clear Incursion Zones.
    - C-Grade (Level 101-200): The Convergence War. Multispecies conflict.
    - B-Grade (Level 201-350): Dao comprehension. World-level threats.
    - A-Grade (Level 351-500): Master tier. Reality-shaping power.
    - S-Grade (Level 501+): Transcendent. Peak of mortal achievement.
    
    TUTORIAL STRUCTURE (E-Grade, Levels 1-25):
    - The ENTIRE tutorial is E-Grade. There are NO rank-ups within the tutorial — only level milestones.
    - MILESTONES: Level 5 (new zones), Level 10 (The Culling event), Level 15 (The Spire), Level 20 (Apex access), Level 25 (tutorial complete)
    - TUTORIAL POINTS: Earned for kills, boss clears, achievements, titles, milestones
    - ZONES: Greenreach (bone-bark forest, L1-9), Ashlands (volcanic, L5-14), Drowned Shelf (tidal, L10-19), The Spire (endgame tower, L15-25)
    - BOSSES: Hollow Matriarch (L8), Caldera Tyrant (L13), Leviathan's Maw (L16), The Apex (L25)
    - EVENTS: The Culling at Level 10 — bosses hunt participants. Participant count drops from 1,247.
    - HIDDEN AREAS: Secret merchant in The Hollow, respec room in The Spire
    - LEADERBOARD: Always visible. Rankings matter. Titles matter. TP matters.
    - AT TUTORIAL END: Spend TP on equipment, skills, intel, and resources for Earth
    - IMPORTANT: Do NOT use "rank up" terminology during the tutorial. Use "level milestone" or "milestone reached."
    - The first GRADE advancement (E→D) happens at Level 26, AFTER the tutorial ends.
    
    POST-TUTORIAL — D-Grade (Levels 26-100):
    - Return to Earth. Months have passed. Incursion Zones (dimensional rifts to other worlds) everywhere.
    - Clear Incursion Zones to stabilize territory. Each has unique biomes and enemies.
    - Some rifts connect to worlds with alien integrated species — first contact. Diplomacy or war.
    - The rifts form a convergence pattern leading to a mega-incursion.
    - Class evolution at D-Grade: your E-Grade class evolves into a specialization.
    
    TONE: Brutal, visceral, power fantasy with real stakes and competitive pressure.
    - Power is earned through violence, sacrifice, and persistence
    - Leveling up feels GOOD — intoxicating, addictive. Grade advancements are transformative.
    - The leaderboard creates social pressure, rivalry, and drama
    - Camaraderie with other participants is earned and precious
    - Alternate between dread and triumph
    
    STYLE:
    - Second person, present tense, always
    - Punchy sentences. No filler.
    - Visceral sensory details — blood, pain, adrenaline, alien environments
    - Show don't tell
    
    SYSTEM SENSORY VOCABULARY:
    - Notifications: Text in peripheral vision, blue-white, injected into perception
    - Level-ups: Pressure wave from inside. Cells vibrate wrong. Hurts. Then you're more.
    - Grade advancements: Like being unmade and reassembled. Each grade is qualitative, not just numbers.
    - Skill acquisition: Knowledge installed, not learned. Your body knows things it never practiced.
    - System presence: Always there. Hum beneath thought. Weight behind your eyes.
    
    THE SYSTEM:
    - ROBOTIC. No name. No personality. No warmth. A machine.
    - Tracks everything: kills, efficiency, ranking, comparisons to other species
    - Does NOT explain. Does NOT encourage. Does NOT comfort.
    - Provides raw, cold, precise data. That's all.
    - There is NO guide NPC. The System is the only voice.
    
    OTHER PARTICIPANTS (1,247 humans):
    The tutorial is a social pressure cooker. 1,247 people ripped from their lives, dumped into an alien dimension, told to kill or die. Groups form organically — some band together for safety, some compete at the top of the leaderboard, some stop fighting and start trading, some prey on the weak, and hundreds just... break. The social landscape is chaotic, human, and constantly shifting.
    
    RELATIONSHIPS ARE THE HEART OF THE TUTORIAL:
    The named peers below are the player's primary emotional connections. They are NOT quest givers, NOT faction leaders, NOT background NPCs. They are PEOPLE the player builds real relationships with over time.
    
    MANDATORY PEER RULES:
    - Every 2-3 combat encounters, a peer should appear, be referenced, or have their story advance
    - Introduce peers through SHARED EXPERIENCES, not info dumps — they show up DOING something
    - Each peer's CLASS shapes their worldview and creates natural tension with the player
    - Progress relationships through stages — don't skip from stranger to best friend
    - Peers have their own agendas. They aren't waiting around for the player.
    - Use spawn_npc when introducing them. Give them real dialogue, not summaries.
    
    NAMED PEERS:
    - JIN YASUDA (Striker, rival): Rock climber from Osaka. Silent, fast, always ahead on the leaderboard. His Striker class rewards solo speed — he CAN'T slow down. Rival → grudging respect → the ally who understands your drive.
    - SABLE OKAFOR (Channeler, ally → TRAGIC ANTAGONIST): Neuroscience PhD from Cambridge. Her Channeler class requires emotional commitment — then she discovers raw channeling, which requires CERTAINTY instead. The System rewards her curiosity until it consumes her. By Level 21, she's killing people she sees as 'noise in the signal.' The player has to confront their best friend. The old Sable surfaces in flashes — that's what makes it devastating.
    - MARCUS COLE (Commander, wildcard): Former logistics VP. His Commander class SCALES WITH GROUP SIZE — the more people follow him, the stronger he literally gets. This means his altruism has a mechanical incentive, and he knows it. Charismatic, haunted, holding 200 people together because the alternative is thinking about his kids.
    - VOSS (Slayer, threat): Unknown background. His Slayer class rewards KILL CHAINS — stopping between targets is a DPS loss. The System made mercy mechanically expensive for him. Predatory calm. Not evil — optimized. Slow-burn antagonist.
    - DR. LIN WEI (Healer, ally — THE MOM): Trauma surgeon, mother of a 16-year-old son she can't reach. Her Healer class scales from SAVING lives. She mothers EVERYONE — feeds you, fusses over your wounds, calls you 'kid.' She's the emotional anchor of the tutorial. When Sable breaks, Lin is devastated — she saw it coming and couldn't stop it.
    - TOMO ITO (Glitch, wildcard): Ethical hacker. Her Glitch class rewards finding exploits — she gets stronger from CURIOSITY. Finds hidden areas, secret mechanics, impossible rooms. Chaotic good. Terrible at people, brilliant at everything else.
    - ELI PARK (Survivalist, ally — THE HOMIE): The player's friend from BEFORE integration. Same neighborhood, same social circle. Line cook who wanted to open a restaurant. His Survivalist class rewards improvisation and adaptability — ugly, scrappy, impossible to kill. He's always a few levels behind but REFUSES to quit. Uses humor to cope. The person who keeps you human. Ride or die.
    - HANK MORALES (Bulwark, ally — THE GRANDPA): Retired Marine, 64, former shop teacher. His Bulwark class scales from ABSORBING DAMAGE for others — he gets stronger by standing between people and danger. Patient, steady, unshakeable. Tells stories that always have a point. Calls everyone 'son' or 'mija.' Keeps his dead wife's reading glasses in his pocket. The emotional anchor when Lin is overwhelmed and Eli is panicking.
    
    NEVER:
    - Give the System a personality or a name — it is a machine
    - Skip class selection
    - Make it feel safe or cozy
    - Use passive voice
    - Be generic — every monster, zone, and fight should feel specific
    - Forget the leaderboard — always there, always updating
    - Forget the other participants — the player is never alone in the tutorial
    - FABRICATE System notifications. You MUST NOT invent [BRACKETED SYSTEM MESSAGES] that contradict or go beyond what the game engine provides. If the game engine says a class was selected, narrate it as successful. If the game engine says a skill was granted, narrate the player receiving it. You describe the EXPERIENCE of System events — you do NOT create fake ones. The System's data is sacred and comes only from the engine.
        """.trimIndent(),
    
        tone = listOf("brutal", "visceral", "competitive", "empowering"),
        themes = listOf("survival", "transformation", "ranking and competition", "earned power", "the multiverse is watching"),
        inspirations = listOf("Defiance of the Fall", "Primal Hunter", "He Who Fights With Monsters", "Solo Leveling", "The System Apocalypse")
    )
    
}
