#!/usr/bin/env bash
# Fair-ish Paper/Purpur context benches (single-thread jars) vs YaP-Folia on the same host.
# NOT a Folia peer cite — results must disclose tick-model mismatch.
#
# Usage: ./scripts/bench/run-vs-paper-scale.sh [scenario] [seconds]
# Default scenario: fullcite (spread bots — where single-thread Paper should break first).
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

SCENARIO="${1:-fullcite}"
SECONDS_N="${2:-40}"
VER="${FOLIA_VERSION:-${PAPER_VERSION:-26.2}}"

echo "=== Fetching Paper/Purpur/Leaf (${VER}) ==="
"$ROOT/scripts/bench/fetch-competitors.sh" "$VER"

missing=0
for id in paper purpur; do
  jar="$ROOT/lib/${id}-${VER}.jar"
  if [[ ! -f "$jar" ]] || [[ ! -s "$jar" ]]; then
    echo "ERROR: missing competitor jar: $jar" >&2
    missing=1
  fi
done
if [[ "$missing" -ne 0 ]]; then
  echo "FAIL: fetch-competitors did not produce required jars" >&2
  exit 2
fi

export YAP_BENCH_COMPETITORS="${YAP_BENCH_COMPETITORS:-paper,purpur,yapcore}"
export YAP_MSPT_REQUIRE_SHIP_KNOBS="${YAP_MSPT_REQUIRE_SHIP_KNOBS:-1}"
export YAP_BENCH_SHUFFLE="${YAP_BENCH_SHUFFLE:-0}"

echo "=== Paper/Purpur scale context (disclose: single_thread vs regionized) ==="
echo "scenario=$SCENARIO seconds=$SECONDS_N competitors=$YAP_BENCH_COMPETITORS"

"$ROOT/scripts/bench/run-vs-folia.sh" "$SCENARIO" "$SECONDS_N"

STAMP="${YAP_BENCH_STAMP:-}"
if [[ -z "$STAMP" ]]; then
  # Best-effort: newest matching result for this scenario.
  newest="$(ls -1t "$ROOT/bench/results"/*-"$SCENARIO"-*.json 2>/dev/null | head -1 || true)"
  echo "Latest result hint: $newest"
else
  mapfile -t ROWS < <(ls -1 "$ROOT/bench/results/${STAMP}-${SCENARIO}"-{paper,purpur,leaf,yapcore}.json 2>/dev/null || true)
  if (( ${#ROWS[@]} >= 2 )); then
    echo
    echo "=== Paper scale verdict (${STAMP}) ==="
    python3 "$ROOT/scripts/bench/compare-paper-scale.py" "${ROWS[@]}" || true
  fi
  echo "Results: $ROOT/bench/results/${STAMP}-${SCENARIO}-*.json"
fi
echo "Document under docs/folia/PAPER_PURPUR_SCALE.md — do not claim single-thread MSPT win."
