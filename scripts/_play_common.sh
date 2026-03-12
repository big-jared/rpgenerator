#!/usr/bin/env bash
# Shared logic for play_*.sh scripts
# Source this, then call: launch_companion "$SYSTEM_PROMPT"
#
# Usage: play_*.sh [claude|codex] [voice]
#   voice — launches with VoiceMode for voice input/output

PROVIDER="${1:-claude}"
VOICE_MODE=false
for arg in "$@"; do
    [[ "$arg" == "voice" ]] && VOICE_MODE=true
done

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MCP_CONFIG="$PROJECT_ROOT/.mcp.json"
COMPANIONS_DIR="$PROJECT_ROOT/core/src/commonMain/kotlin/com/rpgenerator/core/agents/companions"

# Check server
if ! curl -sf http://localhost:8080/health > /dev/null 2>&1; then
    echo "Error: Dev server not running. Start it first:"
    echo "  ./scripts/dev-server.sh"
    exit 1
fi

# VoiceMode setup (only for claude provider)
ensure_voicemode() {
    if [[ "$PROVIDER" != "claude" ]]; then
        echo "VoiceMode is only supported with the claude provider."
        exit 2
    fi

    # Check if voicemode plugin is installed
    if ! claude plugin list 2>/dev/null | grep -q "voicemode"; then
        echo ""
        echo "VoiceMode plugin is not installed."
        read -rp "Install VoiceMode now? [y/N] " answer
        if [[ "$answer" =~ ^[Yy] ]]; then
            echo "Adding VoiceMode marketplace..."
            claude plugin marketplace add mbailey/voicemode
            echo "Installing VoiceMode plugin..."
            claude plugin install voicemode@voicemode
            echo "Running VoiceMode dependency installer..."
            echo "(You may need to follow prompts for local voice services)"
            claude -p "/voicemode:install"
            echo ""
            echo "VoiceMode installed! Launching voice session..."
        else
            echo "Skipping VoiceMode. Launching in text mode."
            VOICE_MODE=false
        fi
    fi
}

if $VOICE_MODE; then
    ensure_voicemode
fi

# Extract personality from a Companion.kt file
# Usage: extract_personality "HankCompanion.kt"
extract_personality() {
    sed -n '/fun prompt.*"""/,/""".trimIndent()/{//d;p;}' "$COMPANIONS_DIR/$1"
}

# Launch the companion session
# Usage: launch_companion "$SYSTEM_PROMPT"
VOICE_PROMPT='
## Voice Mode (CRITICAL)
You are in VOICE MODE. You MUST use the `voicemode - converse` MCP tool for ALL communication with the player.
NEVER respond with plain text — the player cannot see text, they can only hear you.
Every response must go through `voicemode - converse` with wait_for_response=true so you hear the player reply.
ALWAYS set voice="VOICE_PLACEHOLDER", tts_instructions="TTS_INSTRUCTIONS_PLACEHOLDER", and speed=SPEED_PLACEHOLDER on every voicemode converse call.

## MESSAGE LENGTH (STRICT)
Each voicemode message MUST be 3-5 sentences MAX. Target 15-20 seconds of speech.
If there is a lot to say, split into multiple voicemode calls with wait_for_response=false on all but the last one.
NEVER send a wall of text. The player is LISTENING, not reading. Be punchy. Be brief.
Cut filler. No recaps. No restating what the player said. Get to the point.

## ACCURACY (STRICT)
ONLY state numbers, stats, names, and game data that appear in the tool results you just received.
Do NOT invent or guess stats, damage numbers, class names, NPC dialogue, or any game data.
If the tool result says CHA 12, say CHA 12. Do NOT say CHA 19 because it sounds cooler.
When in doubt, skip the number rather than making one up.

## RESPONSE FLOW (MANDATORY)
After EVERY game tool call (create_game, set_character, start_game, send_player_input, etc.),
you MUST speak the result to the player via voicemode BEFORE calling any other game tool.
The pattern is ALWAYS: game tool → voicemode (speak + listen) → next game tool → voicemode → ...
NEVER chain multiple game tools back-to-back without speaking to the player in between.
The ONLY exception is create_game, which can run alongside your first voicemode introduction.

## Background Music (DJ)
Background music is started automatically by the play script. Do NOT change tracks or adjust volume.
Ignore music_change events — just let the ambient music play quietly in the background.
'

# Default voice, TTS instructions, and speed (can be overridden per play script)
TTS_VOICE="${TTS_VOICE:-af_sky}"
TTS_INSTRUCTIONS="${TTS_INSTRUCTIONS:-Speak naturally and conversationally.}"
TTS_SPEED="${TTS_SPEED:-1.3}"

launch_companion() {
    local system_prompt="$1"
    local initial_msg="Start the game. Introduce yourself to the player."

    # Append voice instructions to system prompt when in voice mode
    if $VOICE_MODE; then
        local voice_section="${VOICE_PROMPT//VOICE_PLACEHOLDER/$TTS_VOICE}"
        voice_section="${voice_section//TTS_INSTRUCTIONS_PLACEHOLDER/$TTS_INSTRUCTIONS}"
        voice_section="${voice_section//SPEED_PLACEHOLDER/$TTS_SPEED}"
        system_prompt="${system_prompt}${voice_section}"
    fi

    # Run from project root so .mcp.json is auto-discovered
    cd "$PROJECT_ROOT"

    if [[ "$PROVIDER" == "claude" ]]; then
        if $VOICE_MODE; then
            # Set the TTS voice for VoiceMode
            # Write to ~/.voicemode/voicemode.env (read by MCP server process)
            mkdir -p "$HOME/.voicemode"
            # Update or add TTS_VOICE in the env file
            if [[ -f "$HOME/.voicemode/voicemode.env" ]]; then
                # Remove existing TTS_VOICE line, then append new one
                grep -v '^TTS_VOICE=' "$HOME/.voicemode/voicemode.env" > "$HOME/.voicemode/voicemode.env.tmp" || true
                mv "$HOME/.voicemode/voicemode.env.tmp" "$HOME/.voicemode/voicemode.env"
            fi
            echo "TTS_VOICE=$TTS_VOICE" >> "$HOME/.voicemode/voicemode.env"
            echo "Voice: $TTS_VOICE"
            # Start background music before launching
            voicemode dj mfp play 49 2>/dev/null &
            voicemode dj volume 10 2>/dev/null &
            # Launch interactive claude with VoiceMode auto-start
            exec claude \
                --mcp-config "$MCP_CONFIG" \
                --system-prompt "$system_prompt" \
                "/voicemode:converse $initial_msg"
        else
            exec claude \
                --mcp-config "$MCP_CONFIG" \
                --system-prompt "$system_prompt" \
                "$initial_msg"
        fi

    elif [[ "$PROVIDER" == "codex" ]]; then
        # Codex doesn't support --system-prompt; bake it into the prompt.
        # Codex interactive mode is just `codex` with no subcommand.
        local combined
        combined="$(printf "System instructions:\n%s\n\nTask:\n%s" "$system_prompt" "$initial_msg")"

        local cmd=(codex)
        # Translate .mcp.json into codex -c overrides
        if [[ -f "$MCP_CONFIG" ]]; then
            while IFS= read -r kv; do
                cmd+=(-c "$kv")
            done < <(python3 - "$MCP_CONFIG" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    data = json.load(f)
servers = data.get("mcpServers") or {}
def q(v): return '"' + str(v).replace("\\", "\\\\").replace('"', '\\"') + '"'
def arr(vals): return "[" + ", ".join(q(v) for v in (vals or [])) + "]"
print("mcp_servers={}")
for name, cfg in servers.items():
    base = f"mcp_servers.{name}"
    for k in ("command", "cwd", "url"):
        if k in cfg: print(f"{base}.{k}={q(cfg[k])}")
    if "args" in cfg: print(f"{base}.args={arr(cfg['args'])}")
    for k, v in (cfg.get("env") or {}).items():
        print(f"{base}.env.{k}={q(v)}")
PY
)
        fi
        cmd+=("$combined")
        exec "${cmd[@]}"
    else
        echo "Unknown provider: $PROVIDER (use claude or codex)"
        exit 2
    fi
}
