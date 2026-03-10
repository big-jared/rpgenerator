# RPGenerator — AI-Powered LitRPG with Voice Companions

RPGenerator is a game engine that orchestrates multiple AI agents to create dynamic, voice-first LitRPG adventures. Each world comes with a unique companion character who guides the player through the story — a grumpy Brooklyn fairy, an excitable ink sprite, a paranoid camera drone, or a cozy forest spirit.

The engine handles agent orchestration, game state, combat, quests, and narration. You talk to your companion, and the world responds.

> **See it in action:** [Playtest transcript — Hank (System Apocalypse)](docs/PLAYTEST_INTEGRATION_1.md)

## How It Works

```
┌─────────────────────┐
│  Google Gemini Live  │
│  (voice + audio)     │
└──────────┬──────────┘
           │ audio ↕ tool calls ↕
┌──────────┴──────────────┐       ┌──────────────────────────────┐
│   Mobile App (Android)  │       │   Any MCP Client             │
│                         │       │   (Claude Code, custom, etc.) │
│   Mic/Speaker ↔ Gemini  │       │                              │
│   Tool results ↔ Server │       │   Companion prompt           │
│                         │       │   (Hank/Pip/Glitch/Bramble)  │
└──────────────┬──────────┘       └──────────────┬───────────────┘
               │ REST (tool exec)                │ MCP (HTTP)
               │                                 │
┌──────────────▼─────────────────────────────────▼───────────────┐
│                    RPGenerator Server (:8080)                      │
│                                                                   │
│   REST API              MCP Endpoint                              │
│   /api/game/*/tool      /mcp                                      │
│                                                                   │
│   ┌───────────────────────────────────────────────────────────┐   │
│   │                    RPGenerator Core                       │   │
│   │   Game Master ─── Narrator ─── System Agent               │   │
│   │   NPC Agents ─── Quest Gen ─── Planner                    │   │
│   │   Location Gen ─── Autonomous NPCs                        │   │
│   │                                                           │   │
│   │   GameState ─── Combat ─── Persistence                    │   │
│   └───────────────────────────────────────────────────────────┘   │
│                            │                                      │
│                       LLM Provider                                │
│                 (Gemini / Claude / Codex)                          │
└───────────────────────────────────────────────────────────────────┘
```

Two client paths:
- **Mobile**: Player ↔ Gemini Live (Google) ↔ App ↔ Server REST API for tool execution
- **MCP**: Any MCP client (Claude Code, custom apps, etc.) ↔ Server MCP endpoint for tool execution

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

For Gemini, create a `.env.local` at the project root:

```bash
GOOGLE_API_KEY=your_key_here
```

Get a key from [Google AI Studio](https://aistudio.google.com/apikey).

Claude and Codex run through their respective CLIs — no API key needed, just an active subscription.

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

The Android app connects directly to Gemini Live for voice and to the dev server for game state.

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
└── .mcp.json               # MCP client config (auto-discovered by Claude Code)
```

See [`CLAUDE.md`](CLAUDE.md) for full architecture details, agent system documentation, and common development tasks.

## License

MIT License — see LICENSE file for details.
