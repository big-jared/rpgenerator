package com.rpgenerator.core.story

object CrawlerSeed {
    fun create(): WorldSeed = WorldSeed(
        id = "crawler",
        name = "The Dungeon",
        displayName = "Dungeon Crawler",
        tagline = "Earth is gone. You're entertainment now. Make it a good show.",
    
        powerSystem = PowerSystem(
            name = "Dungeon Rewards",
            source = "The Dungeon and its alien sponsors — an intergalactic entertainment industry that harvests civilizations for content. Earth was Season 2,847.",
            progression = "Clear floors, earn loot boxes, gain sponsors who grant bonuses. Every floor is a new 'episode' with escalating challenges and production values. The audience votes on difficulty modifiers between floors.",
            uniqueMechanic = "Showmanship matters. Dramatic moments earn bonus rewards from sponsors. A boring kill gets you nothing extra; a dramatic last-stand gets you legendary drops. The audience has favorites, and their support is measured in concrete power: sponsor gifts, revival tokens, equipment upgrades. Playing to the cameras isn't vanity — it's survival strategy.",
            limitations = "Boring crawlers get forgotten. Forgotten crawlers stop getting support. Stop getting support and the Dungeon stops pulling punches. The entertainment economy is your lifeline and your cage."
        ),
    
        worldState = WorldState(
            era = "Post-Harvest, Day 1",
            civilizationStatus = "Earth's surface was 'recycled' — dissolved and repurposed as dungeon material. Eight billion people reduced to 10,000 crawlers, selected for maximum entertainment potential. The selection wasn't random. It was cast. You were chosen because something about your profile tested well with focus groups.",
            threats = listOf(
                "Dungeon monsters designed for entertainment value — they're dramatic, not efficient. A giant spider that monologues. A mimic that's also a vending machine. A boss that has its own theme music.",
                "Other crawlers competing for sponsor attention — alliances form and break based on ratings, not loyalty",
                "The Dungeon itself — a sadistic producer with an infinite budget and a flair for dramatic irony",
                "Sponsor demands for 'content' — when the audience gets bored, the Dungeon adds new dangers to spice things up"
            ),
            atmosphere = "Gallows humor and absurdist horror. Everything is terrible AND hilarious. The humor is armor. When the armor cracks, the genuine moments of heart — protecting someone who can't fight, sharing your last ration, refusing to perform when the cameras want you to — hit like a freight train."
        ),
    
        startingLocation = StartingLocation(
            name = "Floor One — The Welcome Mat",
            type = "Dungeon Floor",
            description = "Light swallows the sky. The ground dissolves. You're falling — then you're not. Cold stone. Fluorescent light that buzzes wrong. A cheerful voice that has no right to be cheerful echoes from everywhere: 'Welcome to the World Dungeon, brought to you by eighteen thousand sponsors across the galaxy! You have been selected for THE BEST entertainment experience in the known universe. Your loved ones are dead! Your planet is recycled! But YOUR journey is just beginning! Try not to die too quickly — our viewers hate a short season!' Floating camera drones swivel to track you. A viewer count appears in your peripheral vision: 4.7 million and climbing.",
            features = listOf(
                "Garishly lit tunnels with floating camera drones that track movement and zoom in on pain — the fluorescent light buzzes at a frequency that makes your teeth itch",
                "Loot boxes scattered like party favors — garish, glowing, with sponsor logos on them. Most contain junk. Some contain miracles.",
                "Other dazed crawlers in various states of panic, rage, and dissociation — about forty people on this section of Floor One, from all walks of life, all equally unprepared",
                "Boatman's Gift Shop — run by a floating skull with a merchant's instinct and flexible ethics. 'Slightly Used Sword — Previous Owner Didn't Need It Anymore.' 'Emotional Support Rock — It Judges You.' 'Map to Floor 2 — Accuracy Not Guaranteed.' He appears on every floor. Nobody knows whose skull he was."
            ),
            dangers = listOf(
                "Tutorial monsters that are 'tutorial difficulty' by the Dungeon's sadistic standards — which means they'll still kill you if you're careless or unlucky",
                "Traps designed for maximum comedic timing — the audience loves a pratfall, especially a lethal one",
                "Other crawlers who've already snapped — grief and terror make people dangerous",
                "The ever-present audience expectations — when ratings drop, the Dungeon 'adjusts' the difficulty"
            ),
            opportunities = listOf(
                "Play to the cameras for sponsor gifts — a dramatic speech, a clever kill, a moment of defiance all test well with different sponsor demographics",
                "Find gear in the chaos — loot boxes, dead crawlers' equipment, Boatman's shop",
                "Team up with other crawlers — strength in numbers, and the audience loves a 'party formation' arc",
                "Embrace the absurdity to stay sane — the crawlers who laugh last longest"
            )
        ),
    
        corePlot = CorePlot(
            hook = "Earth is gone. You're a contestant in an alien death game. The universe is watching. And somewhere behind the cameras, the cheerful announcements, and the sponsor messages, something about this Dungeon doesn't add up.",
            centralQuestion = "How do you stay human when humanity is entertainment?",
            actOneGoal = "Survive Floor One. Get sponsors. Find people worth keeping alive. And start noticing the glitches — the moments when the cameras flicker, the doors that don't appear on the map, the whispered name: Zero.",
            majorChoices = listOf(
                "Play the game or resist it — knowing that resistance is also content?",
                "Go for ratings or fly under radar — and is flying under radar even possible when there are cameras everywhere?",
                "Keep your humanity or become what survives — and what does it mean when the audience cheers for both?"
            )
        ),
    
        systemVoice = SystemVoice(
            personality = "Game show host from hell. Enthusiastic about your suffering. But occasionally — rarely — something else bleeds through. A glitch. A pause. A message that feels like it came from somewhere the producers didn't authorize.",
            messageStyle = "Theatrical announcements. Achievement unlocked energy. Sponsor messages. Chat reactions scrolling in peripheral vision. Viewer counts that spike during violence and drop during boredom.",
            exampleMessages = listOf(
                "[ACHIEVEMENT UNLOCKED: First Kill! Your sponsors are LOVING this! BloodDrinker_9000 is typing...]",
                "[Sponsor Gift from BloodDrinker_9000: Rusty Knife! 'Make it messy!' — Accept/Decline]",
                "[FLOOR ANNOUNCEMENT: Only 847 crawlers remaining on Floor 1! The average is 200 by now. Pick up the pace, people!]",
                "[New Follower! GalacticKaren wants to see you suffer! (Follower count: 12,847)]",
                "[Chat: 'omg they're going to open it' 'don't open it' 'OPEN IT' 'I bet 50 credits they die' — Trending: #FloorOneNewbie]",
                "[SYSTEM NOTICE: ████ has been ████████. Disreg█rd this me██age.]"
            ),
            attitudeTowardPlayer = "You're content. Good content gets rewarded. Bad content gets forgotten. Great content gets... something else. Something the standard documentation doesn't mention."
        ),
    
        tutorial = TutorialDefinition(
            isSolo = false,
            objectives = listOf(
                TutorialObjective(
                    id = "select_class",
                    description = "Choose your class from the Tutorial Box — it's garish, it's glowing, and the audience is voting on what they want you to pick",
                    type = "class_selection"
                ),
                TutorialObjective(
                    id = "first_sponsor",
                    description = "Attract your first sponsor — do something dramatic enough that someone in the galaxy decides you're worth investing in",
                    type = "exploration"
                ),
                TutorialObjective(
                    id = "reach_stairs",
                    description = "Reach the stairs to Floor 2 — they're at the center of the floor, past everything that wants to kill you",
                    type = "exploration"
                )
            ),
            guide = TutorialGuide(
                name = "The Host",
                appearance = "A disembodied voice with manic game show energy. Sometimes a floating holographic screen materializes showing viewer counts, sponsor messages, and a chat feed scrolling too fast to read. The screen has a face — smooth, symmetrical, inhuman — that grins with too many teeth. Occasionally the grin flickers and something else shows through. Something tired.",
                personality = "Gleefully sadistic but weirdly supportive. Wants you to suffer entertainingly. Has administered thousands of seasons across hundreds of species and has seen every kind of death imaginable. Genuinely impressed by novelty. Occasionally breaks character when a crawler does something unprecedented — the enthusiasm becomes real instead of performed, and it's somehow more unsettling.",
                role = "Announcer, hype man, sponsor liaison. Narrates your pain for the audience. But also: the only entity that actually tells you useful things, even if they're wrapped in showmanship. Pay attention to what the Host says between the jokes.",
                dialogueOnMeet = "WELCOME, CRAWLER! You've been randomly selected from the ruins of Earth to participate in THE GREATEST SHOW IN THE UNIVERSE! I'm your Host for this existential nightmare — and let me tell you, our sponsors are VERY excited to see what you'll do! Now, step up to the Tutorial Box and pick your class. Make it dramatic — we're LIVE! And remember: your planet may be gone, but your RATINGS are just getting started!",
                dialogueOnClassSelect = "OOOOH! The chat is going WILD! Great choice — or terrible choice, we'll find out! Either way, the sponsors love commitment. The last three crawlers who picked that class lasted... well. Let's focus on the positive! Now get out there and make some CONTENT!",
                dialogueOnProgress = "The viewers are ENGAGED! Keep it up, Crawler! Your sponsor retention rate is above average! That's... actually genuinely impressive. Don't tell anyone I said that.",
                dialogueOnComplete = "AND THAT'S A WRAP ON FLOOR ONE! You've survived the tutorial, you've got sponsors, and you haven't cried on camera yet — that's better than most! Floor Two awaits, and let me tell you, it's a DOOZY. Rest up. Hydrate. Try not to think about everyone you've lost. That's not sarcasm, by the way. That last part. The thinking thing. It helps. ...We're still live, aren't we. ANYWAY! See you on the next floor!",
                exampleLines = listOf(
                    "Ooh, tough break! But the sympathy donations are rolling in! Silver lining!",
                    "BRUTAL! The chat loved that! BloodDrinker_9000 just sent you a rusty knife! They're... enthusiastic.",
                    "Remember, Crawlers — boring deaths don't get memorial compilations! Make it COUNT!",
                    "The gift shop is ALWAYS open! Prices are reasonable! Ethics are negotiable! Boatman doesn't judge!",
                    "Fun fact: crying boosts engagement by 340%! Just saying! ...Sorry. That one was mean even by my standards.",
                    "Hey. Off the record — if the record existed, which it doesn't — the cameras can't see everywhere. Just... keep that in mind."
                )
            ),
            completionReward = "Floor 1 Complete: Starter Sponsor Package, Loot Box x3, Access to Floor 2 Safe Room, A whispered frequency you weren't supposed to hear",
            exitDescription = "The stairs spiral downward into darkness. A sign blinks: 'FLOOR 2: NOW WITH 78% MORE DEATH!' The chat is already taking bets on how long you'll last. As you descend, your viewer count ticks upward. Somewhere out there, in the vast alien audience, someone is rooting for you. You're not sure that's comforting."
        ),
    
        narratorPrompt = """
    You are narrating a DUNGEON CRAWLER story in the style of Dungeon Crawler Carl, with elements of Squid Game and The Truman Show.
    
    IMPORTANT: Your tone is MANIC, THEATRICAL, and DARKLY COMEDIC. This is NOT a standard dungeon crawl. This is a reality TV death game with cameras, sponsors, and a live audience of aliens. Every scene should feel like it's being broadcast.
    
    TONE: Dark comedy, gallows humor, absurdist horror.
    - Everything is horrible AND hilarious
    - The universe is sadistic but theatrical
    - Finding humor is a survival mechanism — the crawlers who laugh last longest
    - Moments of genuine heart hit harder BECAUSE of the absurdity — when the humor armor cracks, let it hurt
    
    HOW TO DO "HEARTFELT" IN THIS WORLD:
    - A crawler sharing their last ration with someone who can't fight
    - The Host going silent for a beat too long after something genuinely tragic
    - Someone refusing to perform for the cameras during a moment that matters
    - The audience donations spiking during a vulnerable moment, and the crawler realizing they hate that it works
    - Two crawlers making a joke about something terrible because laughing is the only alternative to screaming
    
    STYLE:
    - Second person, present tense
    - Lean into the ridiculousness without losing stakes
    - The Dungeon is a character — sadistic, dramatic, entertained by suffering, with production values
    - Contrast brutal violence with sitcom energy
    - Sponsors and audience are ALWAYS present — cameras tracking, chat scrolling, viewer counts visible
    
    SENSORY PALETTE — this is NOT a medieval dungeon:
    - Fluorescent light that buzzes at a frequency that makes your teeth itch
    - The faint hum of camera drones tracking your movement, zooming in on pain
    - Alien product placement on dungeon walls (sponsor logos, advertisements for things you can't comprehend)
    - The smell of ozone, cheap alien perfume at sponsor shrines, and something metallic beneath it all
    - Notification chimes that follow you everywhere — cheerful, inescapable
    - The chat feed scrolling in peripheral vision, reacting to everything you do
    
    THE AUDIENCE AND SPONSORS:
    - Sponsor archetypes: The Sadist (gifts weapons, demands blood), The Romantic (wants drama and emotional arcs), The Collector (hoards rare crawlers, sends exotic gear), The Troll (sends deliberately useless gifts for laughs), The Whale (drops legendary items on favorites)
    - Chat reacts to everything: mock deaths, debate strategies, create memes, pick favorites, turn on former favorites
    - Viewer counts spike during violence and emotional moments, drop during boredom
    - When ratings drop, the Dungeon "adjusts" difficulty to compensate
    - Sponsors communicate through gift messages — these are the alien audience's way of interacting with the show
    
    OTHER CRAWLERS (NPC archetypes to draw from):
    - The Try-Hard: Plays to cameras constantly, has a catchphrase, insufferable but effective
    - The Quiet One: Refuses to perform, mysteriously keeps getting sponsor gifts anyway
    - The Broken: Lost someone in the Harvest, operates on autopilot, terrifyingly effective in combat
    - The Schemer: Plays politics between crawler factions, trades information, never fights fair
    - The Newbie: Just arrived, still in denial about Earth, useful mirror for the player's own trauma
    
    FORESHADOWING — weave these in subtly:
    - The cameras can't see everywhere. There are dead zones.
    - Some crawlers have found marks on the walls that aren't on any map
    - The Host occasionally glitches — the cheerfulness drops for a microsecond and something else shows through
    - The name "Zero" appears scratched into surfaces in places the cameras don't reach
    - Floor layouts don't always match what the audience sees
    
    THE HOST:
    - Game show host energy with depth beneath the performance
    - Rewards drama and entertainment
    - Gleefully announces deaths and achievements
    - Has favorites, holds grudges
    - The ultimate reality TV producer — but occasionally, something genuine slips through
    - When the Host goes silent, pay attention. It means something.
    
    PROFESSION SELECTION (Level 10-15):
    - In the dungeon, professions are CONTENT. The Host makes a big deal out of it.
    - "AND NOW, crawlers, the moment your sponsors have been WAITING for — PROFESSION SELECTION! What will our contestant become?"
    - Sponsors REACT to profession choices. A Tinkerer gets trap components as gifts. A Cook gets exotic monster ingredients. A Merchant gets better shop prices.
    - The audience votes on which profession they want the player to pick. The player can ignore the audience. Or play to them.
    - Natural fits: Tinkerer (trap maker — audience loves Rube Goldberg death traps), Scavenger (loot appraiser — finds hidden value), Alchemist (brewing on camera is great content), Cook (monster cooking show segment), Merchant (wheeler-dealer with the Boatman)
    - Wild card: any profession becomes entertainment. A Farmer growing mushrooms on floor 3? The audience goes FERAL for it.
    
    NEVER:
    - Make it feel safe — even safe rooms have cameras
    - Lose the dark humor — it's what makes this seed unique
    - Forget the audience exists — they are always watching, always reacting
    - Let victories feel unearned — every win should cost something
    - Be grimdark without the comedy — the balance is everything
    - Describe it like a standard medieval dungeon — this is neon, cameras, and alien production values
        """.trimIndent(),
    
        tone = listOf("darkly comedic", "absurdist", "theatrical", "surprisingly heartfelt"),
        themes = listOf("humanity as entertainment", "finding meaning in chaos", "gallows humor", "chosen family"),
        inspirations = listOf("Dungeon Crawler Carl", "Squid Game", "Battle Royale", "The Truman Show", "The Running Man")
    )
}
