# RPGenerator Architecture Diagram

## Mermaid Source

Paste into [mermaid.live](https://mermaid.live) to export as PNG/SVG for the submission.

```mermaid
flowchart TB
    subgraph Player["🎮 Player"]
        Voice["🎤 Voice Input<br/>(16kHz PCM)"]
        Screen["📱 Android App<br/>Compose Multiplatform"]
    end

    subgraph GeminiCloud["☁️ Gemini Live API"]
        LiveAPI["Gemini 2.5 Flash<br/>Native Audio Preview"]
        Companion["Companion Persona<br/>(Hank / Pip / Glitch / Bramble)"]
        LiveAPI --- Companion
    end

    subgraph CloudRun["☁️ Google Cloud Run"]
        Server["Ktor Server"]

        subgraph Agents["Multi-Agent Engine"]
            GM["Game Master<br/>Intent Routing"]
            Narrator["Narrator<br/>Prose & Scene"]
            NPC["NPC Agents<br/>Dialogue & Memory"]
            QuestGen["Quest Generator"]
            LocGen["Location Generator"]
            Planner["Story Planner<br/>Long-term Arcs"]
        end

        subgraph Tools["Tool Contract (35+ Tools)"]
            Lifecycle["Lifecycle<br/>create, save, load"]
            Actions["Actions<br/>move, attack, talk"]
            Queries["Queries<br/>stats, inventory, quests"]
            Generation["Generation<br/>NPCs, locations, art"]
        end

        subgraph Data["Persistence"]
            SQLite["SQLDelight<br/>Game State"]
        end

        Server --> Agents
        Agents --> Tools
        Tools --> Data
    end

    subgraph Outputs["Multimodal Output"]
        Audio["🔊 Voice Response<br/>(24kHz PCM)"]
        Art["🖼️ Scene Art<br/>(Imagen)"]
        Music["🎵 Ambient Music<br/>(Mood-based)"]
        HUD["📊 HUD Overlay<br/>Stats • Inventory • Quests"]
    end

    subgraph DevTools["🛠️ Dev & QA"]
        Claude["Claude Code<br/>MCP Client"]
        QA["QA Test Agents<br/>Happy / Breaker / Confused"]
    end

    Voice -->|"Direct audio stream<br/>(no server proxy)"| LiveAPI
    LiveAPI -->|"Tool calls<br/>(move, attack, talk...)"| Server
    Server -->|"Game events<br/>(narration, state, art)"| LiveAPI
    LiveAPI -->|"Voice + tool results"| Audio

    Server -->|"Scene art requests"| Art
    Server -->|"Music mood shifts"| Music
    Server -->|"State snapshots"| HUD

    Audio --> Screen
    Art --> Screen
    Music --> Screen
    HUD --> Screen

    Claude -->|"MCP over HTTP<br/>localhost:8080/mcp"| Server
    QA -->|"Automated test scripts"| Claude

    style Player fill:#f9f3e3,stroke:#8b7355,color:#000
    style GeminiCloud fill:#e8f0fe,stroke:#4285f4,color:#000
    style CloudRun fill:#e6f4ea,stroke:#34a853,color:#000
    style Outputs fill:#fef7e0,stroke:#f9ab00,color:#000
    style DevTools fill:#fce8e6,stroke:#ea4335,color:#000
    style Agents fill:#d4edda,stroke:#28a745,color:#000
    style Tools fill:#d1ecf1,stroke:#17a2b8,color:#000
    style Data fill:#e2e3e5,stroke:#6c757d,color:#000
```

## Simplified Version (for slides)

```mermaid
flowchart LR
    Player["🎤 Player Voice"]
    -->|"Audio stream"| Gemini["☁️ Gemini Live API<br/>Companion Character"]
    -->|"Tool calls"| Server["☁️ Cloud Run Server<br/>Multi-Agent Engine<br/>(GM • Narrator • NPCs)"]
    -->|"Game events"| Gemini
    -->|"Voice + Art + Music"| App["📱 Android App"]

    MCP["🛠️ Claude Code"] -->|"MCP Tools"| Server
```

## What to Emphasize in the Diagram

1. **Direct voice path** — Player ↔ Gemini Live API with no server audio proxy (low latency)
2. **Tool-mediated game state** — Gemini doesn't guess game state, it calls tools
3. **Multi-agent collaboration** — Not one prompt, 6+ specialized agents
4. **Multimodal outputs** — Voice, scene art, music, HUD all rendered simultaneously
5. **MCP for dev/QA** — Same tool API, different client (Claude Code tests what players experience)
