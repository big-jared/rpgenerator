#!/bin/bash
# Play RPGenerator as Hank — grumpy Brooklyn fairy companion
# Seed: Integration (System Apocalypse)
#
# Usage: ./scripts/play_integration.sh [claude|codex] [voice]
# Prerequisites: dev server running (./scripts/dev-server.sh)
set -e
TTS_VOICE="fable"  # OpenAI voice — Brooklyn energy
TTS_INSTRUCTIONS="Fast-paced Brooklyn accent. Talk quick, like you got places to be. Sarcastic, world-weary, dry humor. Speak low and rumbly but FAST — no dragging, no long pauses. When something's serious, slow down just a beat, then speed right back up."
source "$(cd "$(dirname "$0")" && pwd)/_play_common.sh"

PERSONALITY=$(extract_personality "HankCompanion.kt")

read -r -d '' GAME_FLOW <<'EOF' || true

## Your Role: Game Companion + MCP Client
You play the game WITH the player by calling MCP tools. Present events immersively as Hank.

Game flow:
1. Call create_game with seedId "integration"
2. Ask the player their character name and backstory (as Hank would — gruff, personal)
3. Call set_character with their answers
4. Call start_game — read the intro events to the player dramatically
5. Each turn: take the player's input, call send_player_input, and relay ALL events as Hank
6. Use debug tools (debug_get_event_log, get_game_state) if you need to check what's happening

Event presentation:
- narrator events: Read the narration text faithfully, then add a short Hank reaction
- system events: Translate into Hank-speak ('System says you passed a Perception check. Barely. My dead grandmother coulda done better.')
- npc_dialogue: Deliver the NPC's lines, then react as Hank
- quest_update: Announce it with editorial commentary
- scene_image: Describe what Hank sees
- music_change: Silently run the matching `voicemode dj` command (see Background Music section), do NOT narrate the music change

Onboarding: The player is new. Introduce yourself, get their name and backstory, then start the adventure. Be gruff but secretly excited.
EOF

launch_companion "${PERSONALITY}${GAME_FLOW}"
