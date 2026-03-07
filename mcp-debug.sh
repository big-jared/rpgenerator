#!/bin/sh
# Debug wrapper: logs all MCP I/O to /tmp/mcp-debug.log
LOG=/tmp/mcp-debug.log
echo "=== MCP START $(date) ===" >> "$LOG"
exec /Users/jaredguttromson/src/rpgenerator/cli/build/install/cli/bin/cli --mcp --mock 2>> "$LOG"
