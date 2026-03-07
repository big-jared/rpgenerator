#!/bin/bash
# MCP server wrapper — routes Gradle stderr away so only JSON-RPC appears on stdout
cd /Users/jaredguttromson/src/rpgenerator
exec ./gradlew :cli:run --console=plain --quiet --args="--mcp --mock" 2>/dev/null
