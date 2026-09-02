#!/usr/bin/env bash
# JE Via matrix with resource-pack-on soak (forced pack + Via auto-ack path).
# Server must already listen; prefer resource-pack-enabled=true / forced=true.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-25566}"
export HOST PORT
export RESOURCE_PACK=1
export TIMEOUT_MS="${TIMEOUT_MS:-30000}"
export MATRIX_OUT="${MATRIX_OUT:-$ROOT/build/protocol-matrix-pack-on-latest.json}"
exec "$ROOT/scripts/protocol-matrix/run-matrix.sh"
