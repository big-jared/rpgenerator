# RPGenerator - Claude Agent Context

## Project Overview

RPGenerator is a Kotlin Multiplatform game engine for AI-powered LitRPG adventures. It orchestrates multiple AI agents (Game Master, Narrator, NPCs, Companion) to create dynamic, voice-first gameplay with unique companion characters per world seed.

## Build & Run

```bash
# Start the dev server (default: Gemini Flash)
./scripts/dev-server.sh

# Start with specific provider
./scripts/dev-server.sh gemini pro    # Gemini Pro
./scripts/dev-server.sh claude        # Claude Code CLI
./scripts/dev-server.sh codex         # Codex CLI

# Play with a companion (requires running server)
./scripts/play_integration.sh    # Hank — System Apocalypse
./scripts/play_tabletop.sh       # Pip — Classic Fantasy
./scripts/play_crawler.sh        # Glitch — Dungeon Crawler
./scripts/play_quiet_life.sh     # Bramble — Cozy Apocalypse

# Compile only
./gradlew :core:compileKotlinJvm
./gradlew :server:compileKotlin

# Build Android APK
./gradlew :composeApp:assembleDebug
```

## Project Structure

```
rpgenerator/
├── core/                          # Multiplatform library (JVM, iOS)
│   └── src/commonMain/kotlin/com/rpgenerator/core/
│       ├── GameImpl.kt            # Game session implementation
│       ├── RPGClientImpl.kt       # Main entry point, game creation/loading
│       ├── agents/                # AI agents
│       │   ├── GameMasterAgent.kt
│       │   ├── GMPromptBuilder.kt # Companion prompt routing
│       │   ├── NarratorAgent.kt
│       │   ├── NPCAgent.kt
│       │   ├── AutonomousNPCAgent.kt
│       │   ├── SystemAgent.kt
│       │   ├── QuestGeneratorAgent.kt
│       │   ├── PlannerAgent.kt
│       │   ├── LocationGeneratorAgent.kt
│       │   └── companions/        # Per-companion personality prompts
│       │       ├── HankCompanion.kt
│       │       ├── PipCompanion.kt
│       │       ├── GlitchCompanion.kt
│       │       ├── BrambleCompanion.kt
│       │       └── ReceptionistCompanion.kt
│       ├── api/                   # Public interfaces
│       │   ├── Game.kt            # Main game interface
│       │   ├── GameEvent.kt       # Event types emitted during gameplay
│       │   ├── GameStateSnapshot.kt # UI-facing state
│       │   └── LLMInterface.kt    # LLM provider abstraction
│       ├── domain/                # Core game entities
│       │   ├── GameState.kt       # Full internal game state
│       │   ├── CharacterSheet.kt  # Player stats, inventory, skills
│       │   ├── NPC.kt             # NPC data model
│       │   ├── Quest.kt           # Quest system
│       │   └── TierSystem.kt      # Class/tier progression (PlayerClass enum)
│       ├── orchestration/
│       │   └── GameOrchestrator.kt # Main game loop, intent routing
│       ├── story/                 # World seeds (per-seed files)
│       │   ├── WorldSeed.kt       # Data classes + WorldSeeds registry
│       │   ├── IntegrationSeed.kt
│       │   ├── TabletopSeed.kt
│       │   ├── CrawlerSeed.kt
│       │   ├── QuietLifeSeed.kt
│       │   └── StoryPlanningService.kt
│       └── persistence/
│           ├── GameRepository.kt  # Save/load game state
│           └── PlotGraphRepository.kt
├── server/                        # Ktor server (REST + MCP)
├── composeApp/                    # Mobile UI (Android, iOS stubs)
├── scripts/                       # Dev server + play scripts
└── .mcp.json                      # MCP client config
```

## Key Architecture Patterns

### Two Client Paths
- **Mobile**: Player ↔ Gemini Live (client-side) ↔ App ↔ Server REST API for tool execution
- **MCP**: Any MCP client (Claude Code, etc.) ↔ Server MCP endpoint (`/mcp`)

### Agent System
- Agents are lazy-initialized (`by lazy`) — only appear when first used
- Each agent has a system prompt and maintains conversation history
- `LoggingLLMInterface` wraps the actual LLM to intercept and log all calls
- Companion prompts live in `core/.../agents/companions/` (one file per companion)

### Game State
- `GameState` (internal) — Full mutable state with all game data
- `GameStateSnapshot` (API) — Immutable snapshot for UI display
- State includes: player stats, location, NPCs by location, quests, inventory

### Event System
- `GameOrchestrator.processInput()` returns `Flow<GameEvent>`
- Events: `NarratorText`, `NPCDialogue`, `SystemNotification`, `QuestStarted`, etc.

### Tool Contract
- `Game.executeTool()` → `UnifiedToolContractImpl` — 35+ game tools
- Server exposes tools via REST (`/api/game/{id}/tool`) and MCP (`/mcp`)
- Tool categories: Lifecycle, Queries, Actions, Generation, Debug

## Common Tasks

### Adding a new agent
1. Create agent class in `core/agents/`
2. Add lazy property in `GameOrchestrator`
3. Agent will auto-appear when first used

### Adding a new companion
1. Create `XxxCompanion.kt` in `core/.../agents/companions/`
2. Add routing in `GMPromptBuilder.kt` (`when (seed?.id)` block)
3. Create world seed file in `core/.../story/`
4. Add to `WorldSeeds` registry in `WorldSeed.kt`
5. Create play script in `scripts/`

### NPC System
- NPCs stored in `GameState.npcsByLocation: Map<String, List<NPC>>`
- `GameState.getNPCsAtCurrentLocation()` returns NPCs at player's location
- NPC resolution uses fuzzy matching + LLM fallback for ambiguous references

## MCP Server

The server exposes game tools via MCP at `http://localhost:8080/mcp` (Streamable HTTP transport).

Claude Code auto-discovers from `.mcp.json` in the project root. Other clients: point at the URL and include the `Mcp-Session-Id` header for session persistence.

## Files to Read First

For understanding the system:
1. `core/.../orchestration/GameOrchestrator.kt` — Main game loop, agent coordination
2. `core/.../api/GameEvent.kt` — All event types
3. `core/.../domain/GameState.kt` — Full state model
4. `server/.../` — Server routes, MCP endpoint

## LLM Integration

```kotlin
interface LLMInterface {
    fun startAgent(systemPrompt: String): AgentStream
}

interface AgentStream {
    suspend fun sendMessage(message: String): Flow<String>
}
```

Supported providers: Gemini, Claude Code CLI, Codex CLI, Mock
