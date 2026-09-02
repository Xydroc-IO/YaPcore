#!/usr/bin/env bash
# Bedrock Geyser-parity smoke against a live YaPcore dual-stack UDP listener.
# Starts nothing — server must already listen (default shared port 25566).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-25566}"
cd "$ROOT/scripts/bench/bots"
if [[ ! -d node_modules/bedrock-protocol ]]; then
  npm install bedrock-protocol --no-fund --no-audit
fi
export HOST PORT
node "$ROOT/scripts/protocol-matrix/bedrock-smoke.mjs" | tee "${BEDROCK_OUT:-$ROOT/build/bedrock-smoke-latest.json}"
