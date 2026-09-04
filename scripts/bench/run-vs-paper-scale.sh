#!/usr/bin/env bash
# Fair-ish Paper/Purpur context benches (single-thread jars) vs YaP-Folia on the same host.
# NOT a Folia peer cite — results must disclose tick-model mismatch.
#
# Usage: ./scripts/bench/run-vs-paper-scale.sh [scenario] [seconds]
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

SCENARIO="${1:-heavypop}"
SECONDS_N="${2:-40}"
VER="${FOLIA_VERSION:-26.2}"

"$ROOT/scripts/bench/fetch-competitors.sh" || true

export YAP_BENCH_COMPETITORS="${YAP_BENCH_COMPETITORS:-paper,purpur,yapcore}"
export YAP_MSPT_REQUIRE_SHIP_KNOBS="${YAP_MSPT_REQUIRE_SHIP_KNOBS:-1}"

echo "=== Paper/Purpur scale context (disclose: single-thread vs YaP-Folia regions) ==="
echo "scenario=$SCENARIO seconds=$SECONDS_N competitors=$YAP_BENCH_COMPETITORS"

# Reuse Folia runner only for yapcore row; Paper/Purpur need plain competitor slots.
# run-vs-folia.sh already knows folia/canvas/yapcore — paper/purpur via COMPETITORS if supported.
if ! grep -q 'paper)' "$ROOT/scripts/bench/run-vs-folia.sh" 2>/dev/null; then
  echo "NOTE: run-vs-folia.sh may not define paper/purpur slots yet."
  echo "Running yapcore-only ship profile for scale documentation stamp."
  export YAP_BENCH_COMPETITORS=yapcore
fi

"$ROOT/scripts/bench/run-vs-folia.sh" "$SCENARIO" "$SECONDS_N"

STAMP="$(ls -1t "$ROOT/bench/results"/*-"$SCENARIO"-*.json 2>/dev/null | head -1 || true)"
echo "Latest result hint: $STAMP"
echo "Document under docs/folia/PAPER_PURPUR_SCALE.md — do not claim single-thread MSPT win."
