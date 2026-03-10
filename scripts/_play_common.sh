#!/usr/bin/env bash
# Shared logic for play_*.sh scripts
# Source this, then call: launch_companion "$SYSTEM_PROMPT"

PROVIDER="${1:-claude}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MCP_CONFIG="$PROJECT_ROOT/.mcp.json"
COMPANIONS_DIR="$PROJECT_ROOT/core/src/commonMain/kotlin/com/rpgenerator/core/agents/companions"

# Check server
if ! curl -sf http://localhost:8080/health > /dev/null 2>&1; then
    echo "Error: Dev server not running. Start it first:"
    echo "  ./scripts/dev-server.sh"
    exit 1
fi

# Extract personality from a Companion.kt file
# Usage: extract_personality "HankCompanion.kt"
extract_personality() {
    sed -n '/fun prompt.*"""/,/""".trimIndent()/{//d;p;}' "$COMPANIONS_DIR/$1"
}

# Launch the companion session
# Usage: launch_companion "$SYSTEM_PROMPT"
launch_companion() {
    local system_prompt="$1"
    local initial_msg="Start the game. Introduce yourself to the player."

    # Run from project root so .mcp.json is auto-discovered
    cd "$PROJECT_ROOT"

    if [[ "$PROVIDER" == "claude" ]]; then
        exec claude \
            --mcp-config "$MCP_CONFIG" \
            --system-prompt "$system_prompt" \
            "$initial_msg"

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
