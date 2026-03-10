#!/usr/bin/env bash
set -euo pipefail

# RPGenerator QA Test Runner
# Usage: ./qa_tests/run_tests.sh [options] [happy|breaker|confused|all]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROMPTS_DIR="$SCRIPT_DIR/prompts"
REPORTS_DIR="$SCRIPT_DIR/bug_reports"
MCP_CONFIG="$PROJECT_ROOT/.mcp.json"

provider="${AGENT_CLI_PROVIDER:-auto}"
model=""
max_turns="50"
dangerous=0
verbose=0
json_stream=0
test_name="all"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --provider)   provider="${2:-}"; shift 2 ;;
    --model)      model="${2:-}"; shift 2 ;;
    --max-turns)  max_turns="${2:-}"; shift 2 ;;
    --dangerous)  dangerous=1; shift ;;
    --verbose)    verbose=1; shift ;;
    --json-stream) json_stream=1; shift ;;
    happy|breaker|confused|all)
      test_name="$1"; shift ;;
    -h|--help)
      cat <<'EOF'
Usage: ./qa_tests/run_tests.sh [options] [test]

Tests:
  happy       Normal gameplay — verify features work
  breaker     Edge cases & adversarial inputs
  confused    Confused player simulation
  all         Run all three (default)

Options:
  --provider auto|claude|codex   CLI to use (default: auto-detect)
  --model MODEL                  Model override
  --max-turns N                  Max agent turns (default: 50)
  --dangerous                    Skip permission prompts
  --verbose                      Verbose output
  --json-stream                  Stream JSON output
  -h, --help                     Show this help
EOF
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

# ── Auto-detect provider ──────────────────────────────────────────
if [[ "$provider" == "auto" ]]; then
  if command -v claude >/dev/null 2>&1; then
    provider="claude"
  elif command -v codex >/dev/null 2>&1; then
    provider="codex"
  else
    echo "ERROR: Neither 'claude' nor 'codex' is installed" >&2
    exit 127
  fi
fi

# ── Check server is running ───────────────────────────────────────
if ! curl -sf http://localhost:8080/health > /dev/null 2>&1; then
  echo "ERROR: Dev server not running on :8080"
  echo "Start it first: ./scripts/dev-server.sh"
  exit 1
fi

# ── Build test list ───────────────────────────────────────────────
declare -a TESTS
case "$test_name" in
  all)      TESTS=("happy_path" "breaker_path" "confused_path") ;;
  happy)    TESTS=("happy_path") ;;
  breaker)  TESTS=("breaker_path") ;;
  confused) TESTS=("confused_path") ;;
  *)        echo "Unknown test: $test_name"; exit 2 ;;
esac

mkdir -p "$REPORTS_DIR"
BUGS_BEFORE=$(find "$REPORTS_DIR" -name '*.json' 2>/dev/null | wc -l | tr -d ' ')

echo "═══════════════════════════════════════════"
echo "  RPGenerator QA Test Runner"
echo "═══════════════════════════════════════════"
echo "  Provider:  $provider"
echo "  Tests:     ${TESTS[*]}"
echo "  Max turns: $max_turns"
echo "  Reports:   $REPORTS_DIR"
echo "  Bugs before: $BUGS_BEFORE"
echo "═══════════════════════════════════════════"
echo ""

# ── Run agent (shared logic) ─────────────────────────────────────
run_agent() {
  local system_prompt_file="$1"
  local task_prompt="$2"
  local system_prompt
  system_prompt="$(cat "$system_prompt_file")"

  if [[ "$provider" == "claude" ]]; then
    local cmd=(claude --print)
    if [[ "$json_stream" -eq 1 ]]; then
      cmd+=(--output-format stream-json)
    fi
    if [[ "$verbose" -eq 1 ]]; then
      cmd+=(--verbose)
    fi
    if [[ -n "$max_turns" ]]; then
      cmd+=(--max-turns "$max_turns")
    fi
    if [[ -n "$model" ]]; then
      cmd+=(--model "$model")
    fi
    if [[ "$dangerous" -eq 1 ]]; then
      cmd+=(--dangerously-skip-permissions)
    fi
    cmd+=(--allowedTools "mcp__rpgenerator__*")
    cmd+=(--mcp-config "$MCP_CONFIG")
    cmd+=(--system-prompt "$system_prompt")
    cmd+=(-p "$task_prompt")
    "${cmd[@]}"

  elif [[ "$provider" == "codex" ]]; then
    local codex_runner=()
    if command -v codex >/dev/null 2>&1; then
      codex_runner=(codex)
    elif [[ -n "${CODEX_CLI_PATH:-}" && -x "${CODEX_CLI_PATH:-}" ]]; then
      codex_runner=("$CODEX_CLI_PATH")
    elif command -v npx >/dev/null 2>&1; then
      codex_runner=(npx -y @openai/codex@latest)
    else
      echo "ERROR: codex provider selected but no codex executable found." >&2
      exit 127
    fi

    local codex_model="${model:-${CODEX_MODEL:-gpt-5.1}}"

    local cmd=("${codex_runner[@]}" exec)
    if [[ "$json_stream" -eq 1 ]]; then
      cmd+=(--json)
    fi
    cmd+=(--model "$codex_model")
    if [[ "$dangerous" -eq 1 ]]; then
      cmd+=(--dangerously-bypass-approvals-and-sandbox)
    fi

    # Translate .mcp.json into codex -c overrides
    if [[ -f "$MCP_CONFIG" ]]; then
      while IFS= read -r kv; do
        cmd+=(-c "$kv")
      done < <(python3 - "$MCP_CONFIG" <<'PY'
import json, sys
with open(sys.argv[1], "r") as f:
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

    # Codex doesn't support --system-prompt, so bake it into the prompt
    local combined_prompt
    combined_prompt="$(printf "System instructions:\n%s\n\nTask:\n%s" "$system_prompt" "$task_prompt")"
    cmd+=("$combined_prompt")
    "${cmd[@]}"
  else
    echo "Unsupported provider: $provider" >&2
    exit 2
  fi
}

# ── Execute tests ─────────────────────────────────────────────────
TASK_PROMPT="You are a QA tester. Follow the test plan in your system prompt precisely. Start testing now. Work through every phase methodically. Call report_bug for every issue you find — no matter how small. When all phases are complete, output your final summary."

for t in "${TESTS[@]}"; do
  PROMPT_FILE="$PROMPTS_DIR/${t}.md"
  if [[ ! -f "$PROMPT_FILE" ]]; then
    echo "SKIP: $PROMPT_FILE not found"
    continue
  fi

  echo "───────────────────────────────────────────"
  echo "  Running: $t"
  echo "───────────────────────────────────────────"
  echo ""

  run_agent "$PROMPT_FILE" "$TASK_PROMPT"

  echo ""
  echo "  Completed: $t"
  echo ""
done

# ── Summary ───────────────────────────────────────────────────────
BUGS_AFTER=$(find "$REPORTS_DIR" -name '*.json' 2>/dev/null | wc -l | tr -d ' ')
NEW_BUGS=$((BUGS_AFTER - BUGS_BEFORE))

echo "═══════════════════════════════════════════"
echo "  QA Run Complete"
echo "═══════════════════════════════════════════"
echo "  New bugs filed: $NEW_BUGS"
echo "  Total bug reports: $BUGS_AFTER"
echo ""

if [[ $NEW_BUGS -gt 0 ]]; then
  echo "  New bug reports:"
  find "$REPORTS_DIR" -name '*.json' -newer "$SCRIPT_DIR/run_tests.sh" 2>/dev/null | sort -r | head -n "$NEW_BUGS" | while read -r f; do
    TITLE=$(python3 -c "import json; print(json.load(open('$f'))['title'])" 2>/dev/null || echo "???")
    SEV=$(python3 -c "import json; print(json.load(open('$f'))['severity'])" 2>/dev/null || echo "???")
    echo "    [$SEV] $TITLE"
  done
  echo ""
fi

echo "  Bug reports: $REPORTS_DIR"
echo "═══════════════════════════════════════════"
