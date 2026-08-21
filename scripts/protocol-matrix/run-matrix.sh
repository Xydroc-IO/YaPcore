#!/usr/bin/env bash
# Run JE protocol client matrix against a live YaPcore/Paper instance.
# Starts nothing by itself — server must already listen on PORT (default 25565).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-25566}"
cd "$ROOT/scripts/bench/bots"
if [[ ! -d node_modules/minecraft-protocol ]]; then
  npm install minecraft-protocol minecraft-data --no-fund --no-audit
fi
export HOST PORT
node "$ROOT/scripts/protocol-matrix/join-matrix.mjs" | tee "${MATRIX_OUT:-$ROOT/build/protocol-matrix-latest.json}"
