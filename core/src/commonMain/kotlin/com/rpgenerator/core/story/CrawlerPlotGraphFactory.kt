package com.rpgenerator.core.story

import com.rpgenerator.core.domain.*

/**
 * Pre-baked plot graph for the Dungeon Crawler world.
 *
 * Five interwoven threads covering the crawler experience from Floor 1 through Floor 3:
 * 1. MAIN STORY — The Dungeon's true nature and why Earth was harvested
 * 2. SHOWMANSHIP — The sponsor/audience metagame
 * 3. FELLOW CRAWLERS — Relationships with key NPCs
 * 4. FACTION POLITICS — Floor 2 faction choices and consequences
 * 5. THE REBELLION — The hidden resistance against the Dungeon
 */
internal object CrawlerPlotGraphFactory {

    fun createInitialGraph(gameId: String): PlotGraph {
        return PlotGraphBuilder(gameId)
            // ══════════════════════════════════════════════════════════
            // THREAD 1: MAIN STORY — "What Is the Dungeon?"
            // ══════════════════════════════════════════════════════════
            .node(
                id = "ms-awakening",
                beat = PlotBeat(
                    id = "ms-awakening",
                    title = "The Rude Awakening",
                    description = "Player wakes in the Staging Area. The Announcer delivers the horrible truth: Earth is gone, you're a contestant, the universe is watching. Class selection happens here.",
                    beatType = PlotBeatType.REVELATION,
                    triggerLevel = 1,
                    involvedLocations = listOf("f1-staging-area"),
                    foreshadowing = "The Announcer's cheerfulness is a mask. Sometimes it glitches — just for a frame — and something else shows through.",
                    consequences = "Player has a class. The show has begun. Sponsors are watching."
                ),
                threadId = "main-story",
                position = GraphPosition(tier = 1, sequence = 1)
            )
            .node(
                id = "ms-first-blood",
                beat = PlotBeat(
                    id = "ms-first-blood",
                    title = "First Blood",
                    description = "Player's first real fight in the Tutorial Gauntlet. The Dungeon's system voice goes wild with commentary. A sponsor sends a gift based on performance.",
                    beatType = PlotBeatType.CONFRONTATION,
                    triggerLevel = 1,
                    involvedLocations = listOf("f1-tutorial-gauntlet"),
                    foreshadowing = "The monsters bleed something that isn't quite blood. It dissolves too fast.",
                    consequences = "First sponsor gift. The player learns that entertainment = survival."
                ),
                threadId = "main-story",
                position = GraphPosition(tier = 1, sequence = 2)
            )
            .node(
                id = "ms-boss-floor1",
                beat = PlotBeat(
                    id = "ms-boss-floor1",
                    title = "King Grumbles",
                    description = "The Floor 1 Boss fight. A grotesque goblin king in a tiny crown. Absurd, dangerous, and the highest-rated content on Floor 1. Party formation is key.",
                    beatType = PlotBeatType.CONFRONTATION,
                    triggerLevel = 4,
                    involvedLocations = listOf("f1-boss-arena"),
                    foreshadowing = "Grumbles pauses mid-fight and stares directly at a camera. For one second, his eyes look... sad.",
                    consequences = "Access to Floor 2. The Safe Room. A moment to breathe. Sponsor package unlocked."
                ),
                threadId = "main-story",
                position = GraphPosition(tier = 1, sequence = 5)
            )
            .node(
                id = "ms-faction-choice",
                beat = PlotBeat(
                    id = "ms-faction-choice",
                    title = "Choose Your Side",
                    description = "At the Crossroads, three factions recruit the player. Each offers power, safety, and purpose — at a cost. The choice shapes Floor 2.",
                    beatType = PlotBeatType.CHOICE,
                    triggerLevel = 5,
                    involvedLocations = listOf("f2-crossroads"),
                    involvedNPCs = listOf("npc_dozer", "npc_whisper", "npc_mama_teng"),
                    foreshadowing = "Each faction leader knows something about the Dungeon that the others don't. Together, their knowledge forms a picture.",
                    consequences = "Faction alignment determines allies, enemies, and information access."
                ),
                threadId = "main-story",
                position = GraphPosition(tier = 2, sequence = 1)
            )
            .node(
                id = "ms-dungeon-glitch",
                beat = PlotBeat(
                    id = "ms-dungeon-glitch",
                    title = "The Glitch",
                    description = "The player witnesses the Dungeon glitch — walls flicker, revealing something behind them. Not stone. Not code. Something organic. The Announcer scrambles to cover it up.",
                    beatType = PlotBeatType.REVELATION,
                    triggerLevel = 7,
                    involvedLocations = listOf("f2-neutral-market"),
                    foreshadowing = "Other crawlers have seen it too. Nobody talks about it openly. The cameras are always watching.",
                    consequences = "The player knows the Dungeon isn't what it seems. This knowledge is dangerous."
                ),
                threadId = "main-story",
                position = GraphPosition(tier = 2, sequence = 3)
            )
            .node(
                id = "ms-the-question",
                beat = PlotBeat(
                    id = "ms-the-question",
                    title = "The Question",
                    description = "The Floor 3 Boss doesn't fight. It sits in the Heart and asks: 'Why do you climb?' The answer determines everything. Wrong answer = death. Right answer = the truth about the Dungeon.",
                    beatType = PlotBeatType.TRANSFORMATION,
                    triggerLevel = 12,
                    involvedLocations = listOf("f3-heart"),
                    foreshadowing = "Every NPC the player has befriended has asked them a version of this question. They were preparing you.",
                    consequences = "The player learns the Dungeon is alive — and it's not entertainment. It's a filter."
                ),
                threadId = "main-story",
                position = GraphPosition(tier = 3, sequence = 5)
            )

            .node(
                id = "ms-cancelled",
                beat = PlotBeat(
                    id = "ms-cancelled",
                    title = "Cancelled",
                    description = "A nearby crawler's ratings drop to zero. The system announces their 'retirement' with confetti graphics. The floor opens beneath them. Gone in two seconds. The Announcer immediately pivots: 'AND WE'RE DOWN TO 812! Who's next?'",
                    beatType = PlotBeatType.LOSS,
                    triggerLevel = 3,
                    involvedLocations = listOf("f1-crawler-camp", "f1-rat-warrens"),
                    foreshadowing = "The crawler was someone the player spoke to. Someone who was trying. Someone who wasn't entertaining enough.",
                    consequences = "The player learns the real rule: boredom is death. The sponsor system isn't optional — it's oxygen."
                ),
                threadId = "main-story",
                position = GraphPosition(tier = 1, sequence = 3)
            )
            .node(
                id = "ms-rule-change",
                beat = PlotBeat(
                    id = "ms-rule-change",
                    title = "The Dungeon Cheats",
                    description = "Mid-Floor 2, the Dungeon announces a rule change with game-show enthusiasm: something the player relied on no longer works. Healing cooldowns, map scramble, or safe zone timer halved. The system voice is DELIGHTED. 'Keeping things FRESH for our viewers!'",
                    beatType = PlotBeatType.ESCALATION,
                    triggerLevel = 6,
                    involvedLocations = listOf("f2-crossroads", "f2-neutral-market"),
                    foreshadowing = "Boatman warned about this: 'The rules are real until they're not. Read the fine print. There is no fine print.'",
                    consequences = "The player learns the Dungeon is not a neutral referee. It's a showrunner who will sacrifice fairness for entertainment."
                ),
                threadId = "main-story",
                position = GraphPosition(tier = 2, sequence = 2)
            )

            // ══════════════════════════════════════════════════════════
            // THREAD 2: SHOWMANSHIP — The Sponsor Metagame
            // ══════════════════════════════════════════════════════════
            .node(
                id = "sp-first-sponsor",
                beat = PlotBeat(
                    id = "sp-first-sponsor",
                    title = "Your First Fan",
                    description = "A sponsor — an alien viewer — sends the player their first gift. Something small and practical. With a message: 'Don't die yet. You're interesting.'",
                    beatType = PlotBeatType.VICTORY,
                    triggerLevel = 2,
                    foreshadowing = "The sponsor's name translates roughly to 'The One Who Watches Seeds Grow.' They've sponsored exactly 47 crawlers before you. None survived past Floor 5.",
                    consequences = "Player has a sponsor. Sponsor gifts improve with entertainment value."
                ),
                threadId = "showmanship",
                position = GraphPosition(tier = 1, sequence = 3)
            )
            .node(
                id = "sp-rival-crawler",
                beat = PlotBeat(
                    id = "sp-rival-crawler",
                    title = "The Rival",
                    description = "Another crawler is getting more attention. More sponsors, better gifts, bigger audience. They're not hostile — but the system rewards competition. There can only be one fan favorite per floor.",
                    beatType = PlotBeatType.ESCALATION,
                    triggerLevel = 3,
                    involvedNPCs = listOf("npc_ajax"),
                    foreshadowing = "Ajax has a sponsor who's been in the game longer than the Dungeon itself. That sponsor knows things.",
                    consequences = "Rivalry established. How the player handles it shapes their audience persona."
                ),
                threadId = "showmanship",
                position = GraphPosition(tier = 1, sequence = 4)
            )
            .node(
                id = "sp-viral-moment",
                beat = PlotBeat(
                    id = "sp-viral-moment",
                    title = "Going Viral",
                    description = "The player does something — intentional or not — that goes massively viral across the galaxy. Sponsor gifts pour in. The Announcer is ecstatic. Suddenly everyone knows your name.",
                    beatType = PlotBeatType.VICTORY,
                    triggerLevel = 6,
                    foreshadowing = "Fame in the Dungeon is a double-edged sword. More sponsors means more gifts — but also higher expectations. And the Dungeon always raises the stakes for its stars.",
                    consequences = "Major sponsor package. But the Dungeon will test whether the player can handle the spotlight."
                ),
                threadId = "showmanship",
                position = GraphPosition(tier = 2, sequence = 2)
            )
            .node(
                id = "sp-sponsor-demand",
                beat = PlotBeat(
                    id = "sp-sponsor-demand",
                    title = "The Price of Fame",
                    description = "A major sponsor demands the player do something morally questionable for content. Refuse, and lose their support. Comply, and lose something else.",
                    beatType = PlotBeatType.CHOICE,
                    triggerLevel = 8,
                    foreshadowing = "The sponsor has done this before. The last crawler who refused disappeared. The last one who complied... is still crawling. But they don't laugh anymore.",
                    consequences = "Defines the player's relationship with the sponsor system. Are you content, or a person?"
                ),
                threadId = "showmanship",
                position = GraphPosition(tier = 2, sequence = 4)
            )

            // ══════════════════════════════════════════════════════════
            // THREAD 3: FELLOW CRAWLERS — Found Family
            // ══════════════════════════════════════════════════════════
            .node(
                id = "fc-meet-mordecai",
                beat = PlotBeat(
                    id = "fc-meet-mordecai",
                    title = "The Cat in the Corner",
                    description = "A talking cat named Mordecai approaches the player in the Staging Area. He claims to be a 'Dungeon Guide' — an NPC with sentience. He's sarcastic, world-weary, and knows more than he should.",
                    beatType = PlotBeatType.REVELATION,
                    triggerLevel = 1,
                    involvedNPCs = listOf("npc_mordecai"),
                    involvedLocations = listOf("f1-staging-area"),
                    foreshadowing = "Mordecai flinches when cameras get too close. He knows exactly where their blind spots are.",
                    consequences = "Player gains a guide/companion. Mordecai provides dungeon lore and tactical advice — when he feels like it."
                ),
                threadId = "fellow-crawlers",
                position = GraphPosition(tier = 1, sequence = 1)
            )
            .node(
                id = "fc-meet-katya",
                beat = PlotBeat(
                    id = "fc-meet-katya",
                    title = "The Reluctant Partner",
                    description = "Katya — a former paramedic — saves the player from a trap (or vice versa). She's pragmatic, haunted, and refusing to let anyone else die on her watch. She doesn't want a partner. She needs one.",
                    beatType = PlotBeatType.ESCALATION,
                    triggerLevel = 2,
                    involvedNPCs = listOf("npc_katya"),
                    involvedLocations = listOf("f1-tutorial-gauntlet", "f1-rat-warrens"),
                    foreshadowing = "Katya's hands shake when she's not doing something. She lost someone on Day 1. She hasn't said who.",
                    consequences = "Potential party member. Katya's medical skills are invaluable. Her trauma is a ticking clock."
                ),
                threadId = "fellow-crawlers",
                position = GraphPosition(tier = 1, sequence = 2)
            )
            .node(
                id = "fc-party-forms",
                beat = PlotBeat(
                    id = "fc-party-forms",
                    title = "Strength in Numbers",
                    description = "The player forms a proper party for the Floor 1 Boss. These aren't just allies — they're becoming people the player would risk dying for. The Dungeon loves this. Bonds make for better content.",
                    beatType = PlotBeatType.VICTORY,
                    triggerLevel = 3,
                    involvedNPCs = listOf("npc_katya", "npc_mordecai"),
                    involvedLocations = listOf("f1-boss-antechamber"),
                    foreshadowing = "Mordecai warns: 'The Dungeon always takes what you love. It's not cruel — it's efficient. Attachment creates stakes. Stakes create content.'",
                    consequences = "Party established. The emotional stakes are real now."
                ),
                threadId = "fellow-crawlers",
                position = GraphPosition(tier = 1, sequence = 4)
            )
            .node(
                id = "fc-katya-secret",
                beat = PlotBeat(
                    id = "fc-katya-secret",
                    title = "What Katya Lost",
                    description = "Katya finally reveals her secret: the person she lost on Day 1 was her daughter. She's still alive — somewhere deeper in the Dungeon. Katya isn't climbing to survive. She's climbing to find her.",
                    beatType = PlotBeatType.REVELATION,
                    triggerLevel = 7,
                    involvedNPCs = listOf("npc_katya"),
                    foreshadowing = "Katya keeps a child's drawing folded in her pocket. She touches it before every fight.",
                    consequences = "New motivation. Finding Katya's daughter becomes a personal quest. The sponsors eat it up."
                ),
                threadId = "fellow-crawlers",
                position = GraphPosition(tier = 2, sequence = 3)
            )
            .node(
                id = "fc-mordecai-truth",
                beat = PlotBeat(
                    id = "fc-mordecai-truth",
                    title = "Not Just a Cat",
                    description = "Mordecai reveals he was once a crawler — human, not a cat. The Dungeon 'promoted' him to Guide against his will. He's been trapped for seven seasons, watching people die. He helps the player because one day, someone has to break the cycle.",
                    beatType = PlotBeatType.REVELATION,
                    triggerLevel = 10,
                    involvedNPCs = listOf("npc_mordecai"),
                    involvedLocations = listOf("f3-echo-caves"),
                    foreshadowing = "In the Echo Caves, the player hears a human voice say Mordecai's name. He freezes. 'That was me,' he says. 'Before.'",
                    consequences = "The player understands the Dungeon recycles its contestants. Death isn't the worst thing that can happen here."
                ),
                threadId = "fellow-crawlers",
                position = GraphPosition(tier = 3, sequence = 2)
            )
            .node(
                id = "fc-ajax-unmasked",
                beat = PlotBeat(
                    id = "fc-ajax-unmasked",
                    title = "Behind the Persona",
                    description = "The player catches Ajax in a camera blind spot. The performer drops completely — no smile, no swagger. He's calculating, scared, and exhausted. 'The camera is the only weapon they can't take,' he says. Then a drone approaches and he snaps the persona back on instantly. The player sees: Ajax isn't shallow. He's strategic.",
                    beatType = PlotBeatType.REVELATION,
                    triggerLevel = 4,
                    involvedNPCs = listOf("npc_ajax"),
                    involvedLocations = listOf("f1-boss-antechamber", "f1-crawler-camp"),
                    foreshadowing = "Ajax's eyes don't match his smile. They never have.",
                    consequences = "The rivalry reframes. Ajax understands the system better than anyone. Is he an ally who sees clearly, or a competitor who'll sacrifice you for ratings?"
                ),
                threadId = "fellow-crawlers",
                position = GraphPosition(tier = 1, sequence = 4, branch = 1)
            )
            .node(
                id = "fc-first-loss",
                beat = PlotBeat(
                    id = "fc-first-loss",
                    title = "The Empty Bedroll",
                    description = "A crawler from Base Camp — someone friendly, optimistic, who the player has spoken to twice — doesn't come back from a run. The system announces their death with a jingle. Their bedroll sits empty at camp. Katya stares at it. Mordecai doesn't say anything for a long time.",
                    beatType = PlotBeatType.LOSS,
                    triggerLevel = 5,
                    involvedNPCs = listOf("npc_katya", "npc_mordecai"),
                    involvedLocations = listOf("f1-safe-room", "f2-crossroads"),
                    foreshadowing = "The crawler had just started getting sponsor gifts. They were excited about it. They thought they were going to make it.",
                    consequences = "First real death. Stakes are real. Katya goes quiet. Mordecai says: 'I told you not to learn their names.'"
                ),
                threadId = "fellow-crawlers",
                position = GraphPosition(tier = 2, sequence = 0)
            )
            .node(
                id = "fc-loss",
                beat = PlotBeat(
                    id = "fc-loss",
                    title = "The Cost of Caring",
                    description = "A major party member is killed or taken by the Dungeon. It's sudden, engineered for maximum drama. The cameras zoom in on the player's face. Sponsors send sympathy gifts. The Announcer calls it 'this season's most compelling moment.' The player's grief is content.",
                    beatType = PlotBeatType.LOSS,
                    triggerLevel = 9,
                    involvedNPCs = listOf("npc_katya", "npc_ajax"),
                    foreshadowing = "Mordecai warned them. He always warns them. Nobody listens until it's too late.",
                    consequences = "Grief. Rage. A reason to keep climbing that goes beyond survival. The sponsors love it."
                ),
                threadId = "fellow-crawlers",
                position = GraphPosition(tier = 3, sequence = 1)
            )

            // ══════════════════════════════════════════════════════════
            // THREAD 4: FACTION POLITICS
            // ══════════════════════════════════════════════════════════
            .node(
                id = "fp-dozer-pitch",
                beat = PlotBeat(
                    id = "fp-dozer-pitch",
                    title = "The Strongman's Offer",
                    description = "Dozer, leader of the Crimson Axes, offers the player a place. His pitch: 'Stop pretending this is anything but a fight. Get strong or get dead. I can make you strong.'",
                    beatType = PlotBeatType.CHOICE,
                    triggerLevel = 5,
                    involvedNPCs = listOf("npc_dozer"),
                    involvedLocations = listOf("f2-crimson-territory"),
                    foreshadowing = "Dozer's hands are covered in scars — not from monsters. From other crawlers. He doesn't talk about Floor 1.",
                    consequences = "If accepted: combat power boost, aggressive sponsor audience. If refused: the Axes remember."
                ),
                threadId = "faction-politics",
                position = GraphPosition(tier = 2, sequence = 1, branch = 1)
            )
            .node(
                id = "fp-whisper-pitch",
                beat = PlotBeat(
                    id = "fp-whisper-pitch",
                    title = "The Shadow's Offer",
                    description = "Whisper doesn't recruit. She reveals she already knows three things about the player that nobody should know. Then she offers: 'Information is the only weapon the Dungeon can't take from you.'",
                    beatType = PlotBeatType.CHOICE,
                    triggerLevel = 5,
                    involvedNPCs = listOf("npc_whisper"),
                    involvedLocations = listOf("f2-silent-territory"),
                    foreshadowing = "Whisper's network extends further than Floor 2. She has contacts in places crawlers aren't supposed to go.",
                    consequences = "If accepted: intelligence access, stealth bonuses. If refused: Whisper watches from the shadows."
                ),
                threadId = "faction-politics",
                position = GraphPosition(tier = 2, sequence = 1, branch = 2)
            )
            .node(
                id = "fp-mama-teng-pitch",
                beat = PlotBeat(
                    id = "fp-mama-teng-pitch",
                    title = "The Healer's Offer",
                    description = "Mama Teng doesn't pitch. She feeds you, patches your wounds, and says: 'You don't have to join us. But you're welcome here. Everyone is.' It's the most dangerous thing anyone's said in the Dungeon.",
                    beatType = PlotBeatType.CHOICE,
                    triggerLevel = 5,
                    involvedNPCs = listOf("npc_mama_teng"),
                    involvedLocations = listOf("f2-open-hand-hall"),
                    foreshadowing = "Mama Teng's System class is [REDACTED]. The Dungeon won't display it. She smiles when asked about it.",
                    consequences = "If accepted: community support, healing access, shared resources. If refused: the Open Hand helps you anyway."
                ),
                threadId = "faction-politics",
                position = GraphPosition(tier = 2, sequence = 1, branch = 3)
            )
            .node(
                id = "fp-loyalty-test",
                beat = PlotBeat(
                    id = "fp-loyalty-test",
                    title = "Prove Your Loyalty",
                    description = "The player's faction sends them on a mission that conflicts with another faction's interests. Crimson Axes: sabotage the Garden's water supply. Silent Path: steal Dozer's battle plans. Open Hand: heal a wounded enemy faction member despite protests. The mission forces the player to choose between loyalty and conscience.",
                    beatType = PlotBeatType.CHOICE,
                    triggerLevel = 7,
                    involvedNPCs = listOf("npc_dozer", "npc_whisper", "npc_mama_teng"),
                    involvedLocations = listOf("f2-crimson-territory", "f2-silent-territory", "f2-open-hand-hall"),
                    foreshadowing = "The faction leader's eyes when they give the order — a flicker of doubt, quickly buried. They've given this order before.",
                    consequences = "Completing the mission deepens faction trust but creates enemies. Refusing costs standing but earns respect from the target faction."
                ),
                threadId = "faction-politics",
                position = GraphPosition(tier = 2, sequence = 3)
            )
            .node(
                id = "fp-faction-war",
                beat = PlotBeat(
                    id = "fp-faction-war",
                    title = "The Breaking Point",
                    description = "Tensions between factions explode. Someone sabotaged the Bazaar. Each faction blames the others. The player must mediate, pick a side, or let it burn. The sponsors are THRILLED.",
                    beatType = PlotBeatType.CONFRONTATION,
                    triggerLevel = 8,
                    involvedNPCs = listOf("npc_dozer", "npc_whisper", "npc_mama_teng"),
                    involvedLocations = listOf("f2-crossroads", "f2-neutral-market"),
                    foreshadowing = "The sabotage was too precise. Someone who knows all three factions set this up. Someone — or something.",
                    consequences = "Faction war or faction unity. Either way, Floor 2 will never be the same."
                ),
                threadId = "faction-politics",
                position = GraphPosition(tier = 2, sequence = 4)
            )

            // ══════════════════════════════════════════════════════════
            // THREAD 5: THE REBELLION
            // ══════════════════════════════════════════════════════════
            .node(
                id = "rb-breathing-walls",
                beat = PlotBeat(
                    id = "rb-breathing-walls",
                    title = "The Walls Breathe",
                    description = "In a quiet corridor, the player notices something wrong. A wall is warm to the touch. If they watch long enough, it moves — a slow, rhythmic expansion. Like breathing. Then it stops. The Dungeon was never made of stone.",
                    beatType = PlotBeatType.ESCALATION,
                    triggerLevel = 2,
                    involvedLocations = listOf("f1-tutorial-gauntlet", "f1-rat-warrens"),
                    foreshadowing = "Dismissible on its own. But it connects to The Glitch, The Question, and Zero's revelation that the Dungeon is an organism.",
                    consequences = "A seed planted. The player may not think about it again until Floor 3 — when it all clicks."
                ),
                threadId = "rebellion",
                position = GraphPosition(tier = 1, sequence = 1)
            )
            .node(
                id = "rb-first-hint",
                beat = PlotBeat(
                    id = "rb-first-hint",
                    title = "The Dead Camera",
                    description = "The player finds a camera drone that's been disabled — cleanly, surgically. Scratched into the wall behind it: 'THE SHOW ENDS WHEN WE STOP PERFORMING.' Someone is fighting back.",
                    beatType = PlotBeatType.REVELATION,
                    triggerLevel = 3,
                    involvedLocations = listOf("f1-rat-warrens"),
                    foreshadowing = "More dead cameras appear throughout Floor 1. The Announcer doesn't mention them.",
                    consequences = "The player knows a resistance exists. They don't know what it means yet."
                ),
                threadId = "rebellion",
                position = GraphPosition(tier = 1, sequence = 3)
            )
            .node(
                id = "rb-mordecai-flinch",
                beat = PlotBeat(
                    id = "rb-mordecai-flinch",
                    title = "The Cat Remembers",
                    description = "Mordecai flinches at a camera malfunction — just a flicker. If the player asks, he deflects: 'Bad memories. Cats have nine lives. I've used more than that.' He's staring at his paws like they used to be something else.",
                    beatType = PlotBeatType.ESCALATION,
                    triggerLevel = 4,
                    involvedNPCs = listOf("npc_mordecai"),
                    foreshadowing = "Connects to Mordecai's true nature revelation at L10. The more the player notices, the more hits when the truth drops.",
                    consequences = "Builds mystery around Mordecai. Players who pay attention will see the twist coming — and that's fine. The emotional weight is in the confirmation."
                ),
                threadId = "rebellion",
                position = GraphPosition(tier = 1, sequence = 4)
            )
            .node(
                id = "rb-contact",
                beat = PlotBeat(
                    id = "rb-contact",
                    title = "The Signal",
                    description = "A message appears in the player's sponsor feed — hidden in the metadata. Coordinates to a dead zone where cameras can't see. Someone wants to talk.",
                    beatType = PlotBeatType.ESCALATION,
                    triggerLevel = 6,
                    foreshadowing = "The message is encrypted with a cipher that only someone from Earth would recognize. Someone old-school.",
                    consequences = "Player can seek out the rebellion or ignore it. The Dungeon notices either way."
                ),
                threadId = "rebellion",
                position = GraphPosition(tier = 2, sequence = 2)
            )
            .node(
                id = "rb-meet-zero",
                beat = PlotBeat(
                    id = "rb-meet-zero",
                    title = "Zero",
                    description = "The player meets Zero — leader of the rebellion. She's been in the Dungeon for three seasons. She's found something: the Dungeon has a core, and it can be reached. Not destroyed — freed.",
                    beatType = PlotBeatType.REVELATION,
                    triggerLevel = 10,
                    involvedNPCs = listOf("npc_zero"),
                    involvedLocations = listOf("f3-rebel-outpost"),
                    foreshadowing = "Zero shows the player a map. The Dungeon isn't random. Every floor is shaped like a nerve ending. The whole thing is an organism.",
                    consequences = "The player learns the Dungeon is alive. The climb isn't entertainment — it's a test. The rebellion wants to reach whatever's at the bottom and ask it why."
                ),
                threadId = "rebellion",
                position = GraphPosition(tier = 3, sequence = 3)
            )
            .node(
                id = "rb-choose-path",
                beat = PlotBeat(
                    id = "rb-choose-path",
                    title = "Performer or Rebel",
                    description = "The player must decide: continue climbing as a contestant, playing the game and earning sponsors? Or join the rebellion, sacrifice fame and gifts, and try to break the system from the inside?",
                    beatType = PlotBeatType.CHOICE,
                    triggerLevel = 11,
                    involvedNPCs = listOf("npc_zero", "npc_mordecai"),
                    foreshadowing = "Mordecai has been to the rebellion before. He chose to stay a Guide. He won't say why.",
                    consequences = "Defines the endgame. Performers face the boss. Rebels seek the truth beneath the floors."
                ),
                threadId = "rebellion",
                position = GraphPosition(tier = 3, sequence = 4)
            )

            // ══════════════════════════════════════════════════════════
            // EDGES — Dependencies, conflicts, and narrative flow
            // ══════════════════════════════════════════════════════════

            // Main story flow
            .dependency("ms-awakening", "ms-first-blood")
            .dependency("ms-first-blood", "ms-cancelled")
            .dependency("ms-cancelled", "ms-boss-floor1")
            .dependency("ms-boss-floor1", "ms-faction-choice")
            .dependency("ms-faction-choice", "ms-rule-change")
            .dependency("ms-rule-change", "ms-dungeon-glitch")
            .dependency("ms-dungeon-glitch", "ms-the-question")

            // Showmanship flow
            .dependency("ms-first-blood", "sp-first-sponsor")
            .dependency("sp-first-sponsor", "sp-rival-crawler")
            .dependency("ms-boss-floor1", "sp-viral-moment")
            .dependency("sp-viral-moment", "sp-sponsor-demand")

            // Fellow crawlers flow
            .dependency("ms-awakening", "fc-meet-mordecai")
            .dependency("fc-meet-mordecai", "fc-meet-katya")
            .dependency("fc-meet-katya", "fc-party-forms")
            .dependency("sp-rival-crawler", "fc-ajax-unmasked")
            .dependency("ms-boss-floor1", "fc-first-loss")
            .dependency("fc-first-loss", "fc-katya-secret")
            .dependency("fc-katya-secret", "fc-mordecai-truth")
            .dependency("fc-katya-secret", "fc-loss")

            // Faction politics flow (all three pitches available after faction choice)
            .dependency("ms-faction-choice", "fp-dozer-pitch")
            .dependency("ms-faction-choice", "fp-whisper-pitch")
            .dependency("ms-faction-choice", "fp-mama-teng-pitch")
            .dependency("fp-dozer-pitch", "fp-loyalty-test")
            .dependency("fp-whisper-pitch", "fp-loyalty-test")
            .dependency("fp-mama-teng-pitch", "fp-loyalty-test")
            .dependency("fp-loyalty-test", "fp-faction-war")

            // Rebellion flow
            .dependency("ms-first-blood", "rb-breathing-walls")
            .dependency("rb-breathing-walls", "rb-first-hint")
            .dependency("rb-first-hint", "rb-mordecai-flinch")
            .dependency("rb-mordecai-flinch", "rb-contact")
            .dependency("ms-dungeon-glitch", "rb-meet-zero")
            .dependency("rb-contact", "rb-meet-zero")
            .dependency("rb-meet-zero", "rb-choose-path")

            // Cross-thread connections
            .dependency("fc-mordecai-truth", "ms-the-question")
            .dependency("rb-choose-path", "ms-the-question")
            .dependency("fp-faction-war", "fc-loss") // Faction war creates the context for major loss

            // Conflicts (mutually exclusive paths)
            .conflict("sp-sponsor-demand", "rb-choose-path")

            .build()
    }

    /**
     * Named NPCs for the crawler world.
     * These are spawned contextually by the GM agent or pre-placed at locations.
     */
    val CRAWLER_NPCS = listOf(
        CrawlerNPCTemplate(
            id = "npc_mordecai",
            name = "Mordecai",
            archetype = NPCArchetype.WANDERER,
            startingLocation = "f1-staging-area",
            personality = NPCPersonality(
                traits = listOf("sarcastic", "world-weary", "secretly caring", "cryptic"),
                speechPattern = "Dry, sardonic wit. Speaks in observations that are funnier than they should be. Occasionally drops terrifyingly accurate insights.",
                motivations = listOf("Break the cycle", "Protect promising crawlers", "Remember what being human felt like")
            ),
            lore = "A talking cat who claims to be a 'Dungeon Guide.' In reality, a former human crawler who was forcibly transformed by the Dungeon after seven seasons. He's seen hundreds of crawlers die. He helps anyway.",
            greetingContext = "Approaches casually, like he's been waiting. 'Oh good, another one. Try not to die in the first hour — it's embarrassing for both of us.'"
        ),
        CrawlerNPCTemplate(
            id = "npc_katya",
            name = "Katya Volkov",
            archetype = NPCArchetype.GUARD,
            startingLocation = "f1-tutorial-gauntlet",
            personality = NPCPersonality(
                traits = listOf("pragmatic", "haunted", "protective", "dry humor"),
                speechPattern = "Clipped, efficient. Former paramedic cadence — assesses situations like triage. Dark humor leaks through the professionalism.",
                motivations = listOf("Find her daughter", "Keep people alive", "Don't fall apart")
            ),
            lore = "A paramedic from Chicago. Lost her daughter in the chaos of Earth's destruction — but received a Dungeon notification that her daughter was 'allocated' to a deeper floor. She climbs to find her.",
            greetingContext = "Pulls the player out of danger or patches a wound. 'Hold still. Don't thank me — I'm billing you later.'"
        ),
        CrawlerNPCTemplate(
            id = "npc_ajax",
            name = "Ajax",
            archetype = NPCArchetype.TRAINER,
            startingLocation = "f1-crawler-camp",
            personality = NPCPersonality(
                traits = listOf("charismatic", "strategic", "dual-natured", "perceptive"),
                speechPattern = "ON-CAMERA: Loud, confident, every sentence delivered for an audience. OFF-CAMERA: Quiet, calculating, exhausted. The switch is instantaneous.",
                motivations = listOf("Survive through performance", "Understand the system's rules", "Find someone worth trusting with the mask down")
            ),
            lore = "Former data analyst (not influencer — that's the cover story). Figured out in the first hour that the sponsor system rewards entertainment, so he built a persona. Every 'spontaneous' moment is choreographed. He's not shallow — he's the smartest crawler on Floor 1, and terrified that won't be enough.",
            greetingContext = "Notices the player getting attention. 'Hey, new blood! You're pulling numbers. Respect. Just remember — there's only room for one fan favorite per floor.' His eyes are measuring you the entire time."
        ),
        CrawlerNPCTemplate(
            id = "npc_boatman",
            name = "Boatman",
            archetype = NPCArchetype.MERCHANT,
            startingLocation = "f1-gift-shop",
            personality = NPCPersonality(
                traits = listOf("manic", "entrepreneurial", "mysterious", "oddly helpful"),
                speechPattern = "Fast-talking, carnival barker energy. Sprinkles in unsettling knowledge casually. 'Everything's for sale! Except my secrets — those cost extra.'",
                motivations = listOf("Profit", "Collect interesting stories", "Survive by being too useful to kill")
            ),
            lore = "A floating skull who runs the Floor 1 Gift Shop. Claims to have been here 'since before the first crawlers.' Sells gear, information, and occasionally advice. His prices are outrageous. His merchandise is real. If the player earns his trust, he drops the act for exactly one sentence: 'The merchandise is real because I was real, once.' Then he's immediately back to selling. He never explains. He never repeats it.",
            greetingContext = "Materializes from behind the counter. 'WELCOME to Boatman's! Everything's on sale! Everything's always on sale! What are you looking to NOT die from today?'"
        ),
        CrawlerNPCTemplate(
            id = "npc_dozer",
            name = "Dozer",
            archetype = NPCArchetype.TRAINER,
            startingLocation = "f2-crimson-territory",
            personality = NPCPersonality(
                traits = listOf("blunt", "powerful", "pragmatic", "haunted by past"),
                speechPattern = "Short sentences. Doesn't waste words. Speaks like every syllable costs him something.",
                motivations = listOf("Build the strongest faction", "Never feel helpless again", "Protect his people through strength")
            ),
            lore = "Former construction worker. On Day 1, he watched his crew die because they weren't strong enough. He punched a minotaur to death with his bare hands. Founded the Crimson Axes on a simple principle: the strong survive. Keeps a collection of pressed wildflowers in his quarters — from a garden his daughter used to tend. Nobody mentions it. Everyone knows.",
            greetingContext = "Sizes the player up silently. 'You fight?' One word answers until you prove you're worth more words."
        ),
        CrawlerNPCTemplate(
            id = "npc_whisper",
            name = "Whisper",
            archetype = NPCArchetype.SCHOLAR,
            startingLocation = "f2-silent-territory",
            personality = NPCPersonality(
                traits = listOf("quiet", "calculating", "perceptive", "lonely"),
                speechPattern = "Never above a murmur. Pauses are meaningful. Says more with silence than most say with speeches.",
                motivations = listOf("Map the Dungeon's truth", "Control through information", "Find a way out that doesn't require fighting")
            ),
            lore = "Nobody knows Whisper's real name or background. She appeared on Floor 2 already organized, already knowing things she shouldn't. Her intelligence network spans three floors. Information is her currency, her weapon, and her shield. Sometimes, in the deep hours, scouts report hearing her sing — a lullaby in a language none of them recognize. She stops if she knows anyone is listening.",
            greetingContext = "Appears from shadow. A folded note slides into the player's hand with something written that only they would know."
        ),
        CrawlerNPCTemplate(
            id = "npc_mama_teng",
            name = "Mama Teng",
            archetype = NPCArchetype.INNKEEPER,
            startingLocation = "f2-open-hand-hall",
            personality = NPCPersonality(
                traits = listOf("warm", "fierce", "practical", "unbreakable"),
                speechPattern = "Grandmotherly warmth backed by steel. 'Eat first, talk second.' Doesn't judge. Doesn't forget either.",
                motivations = listOf("Keep people human", "Build community", "Prove kindness isn't weakness")
            ),
            lore = "Retired nurse, age 62 when Earth fell. She should have died a dozen times. Instead, her System class evolved around caregiving — healing, growing food, building shelters. She founded the Open Hand because 'someone has to remember we're people, not content.' Runs a weekly poker game and wins every single time. Plays with a ruthlessness that makes Dozer nervous.",
            greetingContext = "Hands the player a bowl of hot soup before saying a word. 'You look hungry. Sit. Eat. We'll talk when you've got something in your stomach.'"
        ),
        CrawlerNPCTemplate(
            id = "npc_zero",
            name = "Zero",
            archetype = NPCArchetype.QUEST_GIVER,
            startingLocation = "f3-rebel-outpost",
            personality = NPCPersonality(
                traits = listOf("intense", "brilliant", "paranoid", "driven"),
                speechPattern = "Rapid-fire, analytical. Speaks like she's running out of time — because she is. Every conversation is a briefing.",
                motivations = listOf("Break the Dungeon", "Free the crawlers", "Reach whatever's at the bottom")
            ),
            lore = "Three-season veteran. Former systems engineer. She's found patterns in the Dungeon that nobody else sees: the floors are shaped like neural pathways. The monsters are antibodies. The whole thing is alive. She doesn't want to destroy it — she wants to talk to it. Her blind spot: she's so focused on freeing everyone that she'll sacrifice individuals to get there. 'Acceptable losses' comes too easily to her — putting her in direct philosophical conflict with Mama Teng.",
            greetingContext = "Studies the player like a specimen. 'Mordecai vouched for you. That buys you five minutes. Talk fast — the cameras find this place every 72 hours and we have to move.'"
        )
    )

    data class CrawlerNPCTemplate(
        val id: String,
        val name: String,
        val archetype: NPCArchetype,
        val startingLocation: String,
        val personality: NPCPersonality,
        val lore: String,
        val greetingContext: String
    )
}
