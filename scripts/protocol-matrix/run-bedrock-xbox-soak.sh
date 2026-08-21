#!/usr/bin/env bash
# Live Mojang/Xbox Bedrock soak wrapper.
# See bedrock-xbox-soak.mjs and docs/XBOX_RETAIL_CAPTURE.md.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-25566}"
export HOST PORT
if [[ ! -d scripts/bench/bots/node_modules ]]; then
  echo "Installing bedrock-protocol deps under scripts/bench/bots…"
  (cd scripts/bench/bots && npm install --silent)
fi
exec node scripts/protocol-matrix/bedrock-xbox-soak.mjs "$@"
