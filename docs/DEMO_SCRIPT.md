**[Screen: Title card / app splash screen]**

What if you could live inside a book? Not read one — live one, your voice, your decisions, your story.

RPGenerator is a voice-first LitRPG engine powered by Gemini. You speak, the world responds — with voice, art, and music. No text boxes, no menus. Just you, your voice, a companion, and a world that's never been told before.

If you're unfamiliar with the LITRPG genre, it's a type of role-playing where the game world is represented by text, and the game mechanics are tracked using numbers and statistics. Think Dungeons & Dragons meets a choose your own adventure book.

RPGenerator is built on a multi-agent architecture where specialized AI agents — a Game Master, Narrator, NPC agents, quest and location generators, even plot architects — collaborate in real-time to create a story just for you.

Let me show you how it works.

---

## ACT 2: Architecture Diagram (0:30–1:15)

**[Screen: Architecture diagram (full screen)]**

> "Here's the system."

**[Point to / highlight sections as you speak:]**

-- core module first kmp. 

-- clients  -- mcp server testing

-- clieints -- websockets mobile desgined for walking audio voice only 

-- talk about the different agents and multimodal output

---

## ACT 3: Claude Code Testing Session (1:15–2:15)

**[Screen: Terminal — Claude Code with MCP tools]**

> "Before the live demo, let me show you the developer experience. Claude Code connects to our game server via MCP and can play the game using tools."

- Create a game: call `create_game` → `set_character` → `start_game`
- Show the game responding — narrator text, location description
- Send player input: `send_player_input` with "look around"
- Show `get_game_state` — demonstrate the full state model
- Move somewhere: `game_move_to_location`
- Quick combat: `game_attack_target` or encounter

> "Every tool the voice companion uses is the same API Claude Code uses here. 35+ tools — movement, combat, dialogue, inventory, quest management, world generation."

### QA Testing with Agents (brief mention ~15s)

> "We also use Claude Code agents for QA. We have test scripts — happy path, breaker path, confused player — that exercise the game automatically. The agents play through scenarios, verify state consistency, and catch regressions. It's an AI testing an AI."

**[Optional: flash the qa_tests/ directory or a test result briefly]**

---

## ACT 4: Live Voice Demo (2:15–3:40)

**[Screen: Phone — app in hand or on screen capture]**

> "Now the real thing. Voice to voice."

### 4a. Onboarding (2:15–2:40)
- Open app, sign in, select Integration world (Hank)
- Gemini receptionist speaks — guides character creation
- Respond naturally with voice: name, backstory
- Show transcript scrolling in real-time

> "Character creation is a conversation. No forms."

### 4b. Meeting the Companion (2:40–3:00)
- Game starts — Hank introduces himself
- Let Hank's personality shine (sarcasm, Brooklyn accent, "listen kid...")
- Scene art appears for starting location
- Music begins playing

> "That's Hank. He treats every dungeon like a plumbing job."

### 4c. Playing to Level 2 (3:00–3:35)
- Explore: "What's around here?"
- Move: "Let's check out that forest"
- New scene art generates, music shifts
- Fight a monster — voice commands: "Attack!"
- Combat HUD appears (health bars)
- Level up! System notification, stat changes
- Swipe up HUD to show stats, inventory

> "Level 2. New stats, new abilities, new story — all from a conversation."

---

## ACT 5: Close (3:35–3:55)

**[Screen: Quick montage of all 4 companions / worlds, or return to title]**

> "Four worlds. Four companions. Infinite stories. Every session is unique — the agents remember, adapt, and build on what came before."

> "RPGenerator. A living audiobook, powered by Gemini."

**[Screen: Title card — project name, GitHub URL, team]**

---

## TIMING BREAKDOWN

| Act | Content | Duration |
|-----|---------|----------|
| 1 | Pitch — problem, solution, one-liner | 30s |
| 2 | Architecture diagram walkthrough | 45s |
| 3 | Claude Code MCP testing + QA mention | 60s |
| 4 | Live voice demo — onboard, meet Hank, level 2 | 85s |
| 5 | Close — vision, companion montage | 20s |
| **Total** | | **4:00** |

## JUDGING CRITERIA COVERAGE

| Criteria (Weight) | Where We Hit It |
|--------------------|----------------|
| **Innovation & Multimodal UX (40%)** | Voice-only onboarding, companion personality, scene art + music + voice simultaneously, no text boxes |
| **Technical Implementation (30%)** | Architecture diagram, multi-agent system, 35+ tools, Claude Code MCP testing, QA agents |
| **Demo & Presentation (30%)** | Clear pitch, legible architecture, live voice demo, Cloud Run deployment |

## BACKUP PLANS

- **Gemini slow/down:** Pre-record the voice segments, narrate over them
- **Audio choppy:** Fall back to MCP demo (Claude Code terminal is compelling on its own)
- **Scene art fails:** Have pre-generated images as static backgrounds
- **Can't reach level 2:** Show combat + XP gain, mention level 2 is moments away
- **Server down:** Run against local server, show Cloud Run deployment proof separately

## BONUS POINTS

- [ ] Blog/video about the build with #GeminiLiveAgentChallenge
- [ ] Infrastructure-as-code: Dockerfile + Cloud Run deploy script
- [ ] Google Developer Group membership link
