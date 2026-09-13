#!/usr/bin/env bash
# Convert Bedrock fixtures → converted/ + provenance manifest (deterministic).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BAND="${1:-band_26_50}"
cd "$ROOT"
if [[ -x ./gradlew ]]; then
  GW=./gradlew
else
  GW=gradle
fi
exec "$GW" -q parityConvert -PparityBand="$BAND" -PparityRoot="$ROOT"
