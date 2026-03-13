# RPGenerator — AI-Powered LitRPG with Voice Companions

RPGenerator is a game engine that orchestrates multiple AI agents to create dynamic, voice-first LitRPG adventures. Each world comes with a unique companion character who guides the player through the story — a grumpy Brooklyn fairy, an excitable ink sprite, a paranoid camera drone, or a cozy forest spirit.

The engine handles agent orchestration, game state, combat, quests, and narration. You talk to your companion, and the world responds.

> **See it in action:** [Playtest transcript — Hank (System Apocalypse)](docs/PLAYTEST_INTEGRATION_1.md)

## How It Works

```
CLIENTS                         SERVER (:8080)                   EXTERNAL

                               +---------------+     +-----------+
+--------------+  WS (audio)   |               |---->|           |
| Mobile App   |-------------->|   WebSocket   |<----| Gemini    |
| Android/iOS  |<--------------| Handler       |     | Live API  |
|              |  events,state |               |     +-----------+
| mic -> server|  audio,images | owns session  |
| server -> spk|               | gates audio   |     +-----------+
+--------------+               | during tools  |     | Gemini    |
                               +-------+-------+     | Flash     |
+--------------+                       |              | Image     |
| MCP Client   |  POST /mcp   +-------+-------+     | (native   |
| (Claude Code)|------------->| Session       |     | multi-    |
|              |<--------------| Manager       |     | modal)    |
+--------------+  JSON-RPC    +-------+-------+     +-----------+
                                      |                    ^
                              +-------+-------+            |
                              |  Image Svc   |------------+
                              +-------+-------+
                                      |
                                      v
                              +---------------+
                              |   Core Lib    |
                              +---------------+


CORE LIBRARY (Kotlin Multiplatform)

+--------------------------------------------------------------------+
|                                                                    |
|  GameImpl (session wrapper, persistence, public API)               |
|      |                                                             |
|      +---> UnifiedToolContractImpl (35+ stateless tools)           |
|      |       Queries:  stats, inventory, npcs, quests, location    |
|      |       Actions:  move, talk, attack, equip, use_item         |
|      |       Quests:   accept, update, complete                    |
|      |       Gen:      scene_art, portrait, item_art               |
|      |                                                             |
|      +---> GameOrchestrator (main game loop)                       |
|              |                                                     |
|              +-- Phase 1+2: decideAgent --> tool calls --> state    |
|              |                                                     |
|              +-- Phase 3:   narrateAgent -> prose -> GameEvent     |
|                  (skipped for CHARACTER_SETUP and QUERY turns,     |
|                   only runs for action / exploration / combat)     |
|                                                                    |
|  Agents (lazy-init)          World Seeds + Companions              |
|  +- decideAgent              +- IntegrationSeed -> Hank            |
|  +- narrateAgent             +- TabletopSeed   -> Pip              |
|  +- npcAgent                 +- CrawlerSeed    -> Glitch           |
|  +- questGenerator           +- QuietLifeSeed  -> Bramble          |
|  +- locationGenerator                                              |
|  +- storyPlanning           GameState (mutable internal)           |
|                              +- PlayerStats (hp, mp, xp, level)    |
|  LLMInterface                +- Location graph                     |
|  +- Gemini Flash             +- NPCs by location                   |
|  +- Claude CLI               +- Inventory + equipment              |
|  +- Codex CLI                +- Active quests                      |
|  +- Mock                     +- Combat state                       |
|                                                                    |
|  GameEvent types (emitted as Flow)                                 |
|  +- NarratorText, NPCDialogue, CombatLog, SystemNotification      |
|  +- StatChange, ItemGained, QuestUpdate                            |
|  +- SceneImage, NPCPortrait, NarratorAudio, MusicChange           |
|                                                                    |
+--------------------------------------------------------------------+


AUDIO PATH

Mic --> App --> Server WS --> Gemini Live --> Server WS --> App --> Speaker
           PCM 16kHz              |              PCM 24kHz
                                  v
                           tool calls exec'd
                           on server (gated)
```

Two client paths — both go through the server:
- **Mobile**: Mic -> Server WebSocket -> Gemini Live API -> Server -> Speaker (server owns the Gemini session and gates audio while executing tool calls)
- **MCP**: Any MCP client (Claude Code, etc.) -> Server MCP endpoint (`/mcp`) -> Core engine

### Agents

The engine spawns specialized AI agents as needed. Each maintains its own conversation history and system prompt.

| Agent | Role | Prompt |
|-------|------|--------|
| **Game Master** | Intent routing — decides what happens when the player acts | [`GMPromptBuilder.kt`](core/src/commonMain/kotlin/com/rpgenerator/core/agents/GMPromptBuilder.kt) |
| **Narrator** | Second-person prose, show-don't-tell, pacing control | [`NarratorAgent.kt`](core/src/commonMain/kotlin/com/rpgenerator/core/agents/NarratorAgent.kt) |
| **System** | Clinical voice of the System — tier/grade progression, notifications | [`SystemAgent.kt`](core/src/commonMain/kotlin/com/rpgenerator/core/agents/SystemAgent.kt) |
| **NPC** | Dynamic dialogue with dedicated streams per named NPC | [`NPCAgent.kt`](core/src/commonMain/kotlin/com/rpgenerator/core/agents/NPCAgent.kt) |
| **Autonomous NPC** | NPCs act independently — move, react, pursue goals without player input | [`AutonomousNPCAgent.kt`](core/src/commonMain/kotlin/com/rpgenerator/core/agents/AutonomousNPCAgent.kt) |
| **Quest Generator** | Contextual quests fitting player level and location | [`QuestGeneratorAgent.kt`](core/src/commonMain/kotlin/com/rpgenerator/core/agents/QuestGeneratorAgent.kt) |
| **Planner** | Async long-term plot architect — foreshadowing, arc planning 50-100 levels ahead | [`PlannerAgent.kt`](core/src/commonMain/kotlin/com/rpgenerator/core/agents/PlannerAgent.kt) |
| **Location Generator** | Creates immersive locations on discovery with biome, features, lore | [`LocationGeneratorAgent.kt`](core/src/commonMain/kotlin/com/rpgenerator/core/agents/LocationGeneratorAgent.kt) |
| **Companion** | Voice personality — the player's guide and emotional anchor | [`companions/`](core/src/commonMain/kotlin/com/rpgenerator/core/agents/companions/) |

Agents are lazy-initialized and only appear in the debug UI when first used. A `LoggingLLMInterface` wrapper intercepts all LLM calls for the debug dashboard.

## World Seeds

Each seed defines a complete world: power system, lore, tutorial structure, named NPCs, and a companion character.

| Seed | World | Companion | Tagline | Source |
|------|-------|-----------|---------|--------|
| `integration` | System Apocalypse | [**Hank**](core/src/commonMain/kotlin/com/rpgenerator/core/agents/companions/HankCompanion.kt) — grumpy Brooklyn fairy | *Normal Tuesday. Sky splits. Now you're integrated. Kill to level.* | [`IntegrationSeed.kt`](core/src/commonMain/kotlin/com/rpgenerator/core/story/IntegrationSeed.kt) |
| `tabletop` | Classic Fantasy | [**Pip**](core/src/commonMain/kotlin/com/rpgenerator/core/agents/companions/PipCompanion.kt) — enchanted ink sprite | *Roll for initiative. The Realm needs heroes.* | [`TabletopSeed.kt`](core/src/commonMain/kotlin/com/rpgenerator/core/story/TabletopSeed.kt) |
| `crawler` | Dungeon Crawler | [**Glitch**](core/src/commonMain/kotlin/com/rpgenerator/core/agents/companions/GlitchCompanion.kt) — rogue camera drone | *Earth is gone. You're entertainment now. Make it a good show.* | [`CrawlerSeed.kt`](core/src/commonMain/kotlin/com/rpgenerator/core/story/CrawlerSeed.kt) |
| `quiet_life` | Cozy Apocalypse | [**Bramble**](core/src/commonMain/kotlin/com/rpgenerator/core/agents/companions/BrambleCompanion.kt) — fluffy forest spirit | *The wars are over. Time to build something worth protecting.* | [`QuietLifeSeed.kt`](core/src/commonMain/kotlin/com/rpgenerator/core/story/QuietLifeSeed.kt) |

## Quick Start

### 1. Start the Dev Server

```bash
./scripts/dev-server.sh [provider] [model]
```

| Command | Provider | Model | Requires |
|---------|----------|-------|----------|
| `./scripts/dev-server.sh` | Gemini | gemini-2.5-flash | `GOOGLE_API_KEY` in `.env.local` |
| `./scripts/dev-server.sh gemini pro` | Gemini | gemini-3.1-pro-preview | `GOOGLE_API_KEY` in `.env.local` |
| `./scripts/dev-server.sh claude` | Claude Code CLI | claude-opus-4-6 | Claude Pro subscription |
| `./scripts/dev-server.sh codex` | Codex CLI | codex-5.4 | Codex CLI installed |

Copy the template and add your key:

```bash
cp .env.local.template .env.local
# Edit .env.local and set GOOGLE_API_KEY
```

Get a key from [Google AI Studio](https://aistudio.google.com/apikey). See `.env.local.template` for all config options (auth, server URL, LLM provider).

Claude and Codex run through their respective CLIs — no API key needed, just an active subscription.

> **Recommended:** Use Gemini for the server backend. Claude/Codex CLI providers shell out to a new process for every agent call (narrator, GM, NPCs), which is significantly slower than Gemini's direct API. Use `./scripts/dev-server.sh` (Gemini) for the server, and Claude Code or Codex as the companion client via the play scripts below.

### 2. Play (Claude Code Companion)

With the dev server running, launch a companion session:

```bash
./scripts/play_integration.sh    # Hank — System Apocalypse
./scripts/play_tabletop.sh       # Pip — Classic Fantasy
./scripts/play_crawler.sh        # Glitch — Dungeon Crawler
./scripts/play_quiet_life.sh     # Bramble — Cozy Apocalypse
```

Each script launches Claude Code with the companion's full personality as a system prompt. The companion uses MCP tools to drive the game engine — creating the session, running combat, generating locations, and narrating events in character.

### 3. MCP Setup

The server exposes 35+ game tools via MCP at `http://localhost:8080/mcp`.

**Claude Code** auto-discovers this config from `.mcp.json` in the project root:

```json
{
  "mcpServers": {
    "rpgenerator": {
      "type": "http",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

**Other MCP clients:** point your client at `http://localhost:8080/mcp` (Streamable HTTP transport). The server returns a `Mcp-Session-Id` header for session persistence — include it in subsequent requests.

Available tool categories:
- **Lifecycle:** `create_game`, `set_character`, `start_game`, `save_game`
- **Queries:** `get_game_state`, `game_get_character_sheet`, `game_get_inventory`, `game_get_active_quests`, `game_get_npcs_here`
- **Actions:** `send_player_input`, `game_attack_target`, `game_move_to_location`, `game_talk_to_npc`, `game_use_item`, `game_use_skill`
- **Generation:** `game_generate_scene_art`, `game_generate_portrait`, `game_generate_npc`, `game_generate_location`
- **Debug:** `debug_get_agent_conversations`, `debug_get_event_log`, `debug_get_plot_graph`

### 4. Mobile App (Android)

The Android app connects to the dev server, which proxies audio to/from Gemini Live and handles all game state.

**Setup:**

1. Add `GOOGLE_API_KEY` to `.env.local` (same key as the server)
2. Start the dev server: `./scripts/dev-server.sh`

**Build & install:**

```bash
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:installDebug
```

Requires Android SDK and a device/emulator with Google Play Services. The app uses a foreground service with microphone access for the Gemini Live voice session.

## Tech Stack

- **Kotlin Multiplatform** (JVM, iOS, Android)
- **Gemini SDK** (`com.google.genai:google-genai:1.41.0`) — Live API for voice, text for agents
- **Ktor** — Server (Netty), client (OkHttp on Android)
- **Compose Multiplatform** — Mobile UI (Android, iOS stubs)
- **SQLDelight** — Game state persistence
- **Kotlinx Coroutines & Serialization**

## Project Structure

```
rpgenerator/
├── core/                   # Multiplatform library (JVM, iOS)
│   └── src/commonMain/
│       ├── agents/         # AI agents (GM, Narrator, NPC, System, Planner, etc.)
│       ├── api/            # Public interfaces (Game, GameEvent, LLMInterface)
│       ├── domain/         # Game entities (CharacterSheet, NPC, Quest, GameState)
│       ├── orchestration/  # Main game loop, intent routing
│       ├── story/          # World seeds, plot planning
│       └── persistence/    # Save/load via SQLDelight
├── server/                 # Ktor server + MCP endpoint
├── composeApp/             # Mobile UI (Android, iOS stubs)
├── scripts/
│   ├── dev-server.sh       # Start dev server
│   ├── play_integration.sh # Launch companion session — Hank
│   ├── play_tabletop.sh    # Launch companion session — Pip
│   ├── play_crawler.sh     # Launch companion session — Glitch
│   └── play_quiet_life.sh  # Launch companion session — Bramble
├── qa_tests/
│   ├── run_tests.sh        # QA test runner (claude or codex)
│   ├── prompts/            # Test scenario prompts
│   │   ├── happy_path.md   # Normal gameplay verification
│   │   ├── breaker_path.md # Edge cases & adversarial inputs
│   │   └── confused_path.md# Confused player simulation
│   └── bug_reports/        # Bug reports filed via report_bug MCP tool
└── .mcp.json               # MCP client config (auto-discovered by Claude Code)
```

See [`CLAUDE.md`](CLAUDE.md) for full architecture details, agent system documentation, and common development tasks.

## License

MIT License — see LICENSE file for details.
