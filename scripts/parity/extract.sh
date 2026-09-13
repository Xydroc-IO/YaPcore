#!/usr/bin/env bash
# Extract Bedrock catalog block fixtures from Cloudburst palette (and optional vanilla RP).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BAND="${1:-band_26_50}"
cd "$ROOT"
if [[ -x ./gradlew ]]; then
  GW=./gradlew
else
  GW=gradle
fi
exec "$GW" -q parityExtract -PparityBand="$BAND" -PparityRoot="$ROOT"
