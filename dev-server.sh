#!/bin/bash
# Start RPGenerator server locally for development/testing.
# Reads .env.local for secrets, uses /tmp for data.
#
# Usage:
#   ./dev-server.sh              # default: gemini (gemini-2.5-flash)
#   ./dev-server.sh claude       # claude --print --model claude-opus-4-6
#   ./dev-server.sh codex        # codex --quiet --model codex-5.4
#   ./dev-server.sh gemini       # gemini-2.5-flash (explicit)
#   ./dev-server.sh gemini pro   # gemini-3.1-pro-preview

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Load env vars from .env.local
if [ -f "$SCRIPT_DIR/.env.local" ]; then
    export $(grep -v '^#' "$SCRIPT_DIR/.env.local" | xargs)
else
    echo "Warning: .env.local not found. GOOGLE_API_KEY may not be set."
fi

export DATA_DIR="${DATA_DIR:-/tmp/rpgenerator-data}"
export KTOR_DEVELOPMENT=true
mkdir -p "$DATA_DIR"

# Parse provider arg
PROVIDER="${1:-gemini}"
case "$PROVIDER" in
    claude)
        export LLM_PROVIDER=claude-cli
        export LLM_MODEL="${2:-claude-opus-4-6}"
        ;;
    codex)
        export LLM_PROVIDER=codex-cli
        export LLM_MODEL="${2:-codex-5.4}"
        ;;
    gemini)
        export LLM_PROVIDER=gemini
        case "$2" in
            pro)   export LLM_MODEL="gemini-3.1-pro-preview" ;;
            flash) export LLM_MODEL="gemini-2.5-flash" ;;
            "")    export LLM_MODEL="gemini-2.5-flash" ;;
            *)     export LLM_MODEL="$2" ;;
        esac
        ;;
    *)
        export LLM_PROVIDER="$PROVIDER"
        [ -n "$2" ] && export LLM_MODEL="$2"
        ;;
esac

# Kill any existing server on port 8080
lsof -ti:8080 | xargs kill -9 2>/dev/null || true
sleep 1

echo "Starting RPGenerator dev server..."
echo "  DATA_DIR: $DATA_DIR"
echo "  LLM:      $LLM_PROVIDER ($LLM_MODEL)"
echo "  Port:     8080"
echo ""

exec "$SCRIPT_DIR/gradlew" :server:run --console=plain
