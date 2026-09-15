#!/usr/bin/env bash
# Produce a Paper/Purpur vs YaP-Folia *scale* stamp under spread fullcite.
# Discloses tick_model mismatch — never a Folia peer ≥5% MSPT claim.
#
# Usage:
#   ./scripts/bench/cite-paper-scale.sh
#   YAP_BENCH_PLAYERS=100 YAP_BENCH_SECONDS=40 ./scripts/bench/cite-paper-scale.sh
#
# Requires: Java 25+, node/npm (Mineflayer), Paper/Purpur jars, Yap-Folia + yapcore.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

SECONDS_N="${YAP_BENCH_SECONDS:-40}"
PLAYERS="${YAP_BENCH_PLAYERS:-100}"
STAMP="${YAP_BENCH_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
VER="${FOLIA_VERSION:-${PAPER_VERSION:-26.2}}"

export YAP_BENCH_COMPETITORS="${YAP_BENCH_COMPETITORS:-paper,purpur,yapcore}"
export YAP_BENCH_PLAYERS="$PLAYERS"
export YAP_BENCH_STAMP="$STAMP"
export YAP_BENCH_SHUFFLE="${YAP_BENCH_SHUFFLE:-0}"
export YAP_FOLIA_ASYNC_CHUNK_SAVE="${YAP_FOLIA_ASYNC_CHUNK_SAVE:-true}"
export YAP_FOLIA_HOPPER_TICK_BUDGET="${YAP_FOLIA_HOPPER_TICK_BUDGET:-64}"
export YAP_FOLIA_ENTITY_TICK_BUDGET="${YAP_FOLIA_ENTITY_TICK_BUDGET:-400}"
export YAP_FOLIA_MICROTICK_BUDGET_MS="${YAP_FOLIA_MICROTICK_BUDGET_MS:-8}"
export YAP_FOLIA_BUDGET_MSPT_THRESHOLD="${YAP_FOLIA_BUDGET_MSPT_THRESHOLD:-12}"
export YAP_FOLIA_SUBREGION_PARTITION="${YAP_FOLIA_SUBREGION_PARTITION:-true}"
export YAP_FOLIA_ALIGNED_MICROTICKS="${YAP_FOLIA_ALIGNED_MICROTICKS:-true}"
export YAP_FOLIA_MICRO_PHASES="${YAP_FOLIA_MICRO_PHASES:-4}"
export YAP_FOLIA_TICK_WAVE_MAX_WAIT_MS="${YAP_FOLIA_TICK_WAVE_MAX_WAIT_MS:-2}"
export YAP_BOT_CITE_STABLE="${YAP_BOT_CITE_STABLE:-0}"
export YAP_BOT_NO_DIG="${YAP_BOT_NO_DIG:-1}"
export YAP_BENCH_VIEW_DISTANCE="${YAP_BENCH_VIEW_DISTANCE:-8}"
export YAP_BENCH_SIM_DISTANCE="${YAP_BENCH_SIM_DISTANCE:-8}"
export YAP_MSPT_REQUIRE_SHIP_KNOBS=1

yap_banner "cite-paper-scale · stamp=$STAMP · players=$PLAYERS · ${SECONDS_N}s"

"$SCRIPT_DIR/fetch-competitors.sh" "$VER"

for id in paper purpur; do
  jar="$ROOT/lib/${id}-${VER}.jar"
  if [[ ! -f "$jar" ]] || [[ ! -s "$jar" ]]; then
    echo "FAIL: missing $jar" >&2
    exit 2
  fi
done

if [[ ! -f "$ROOT/lib/yap-folia-${VER}.jar" ]]; then
  echo "Building YaP-Folia…"
  "$ROOT/scripts/build-yap-folia.sh"
fi

"$SCRIPT_DIR/run-vs-paper-scale.sh" fullcite "$SECONDS_N"

mapfile -t ROWS < <(ls -1 "$ROOT/bench/results/${STAMP}-fullcite"-{paper,purpur,yapcore}.json 2>/dev/null || true)
if (( ${#ROWS[@]} < 2 )); then
  echo "FAIL: expected paper/purpur/yapcore JSON under stamp=$STAMP" >&2
  ls -lt "$ROOT/bench/results/${STAMP}"* 2>/dev/null || true
  exit 1
fi

echo
echo "== paper scale gate =="
set +e
out="$(python3 "$SCRIPT_DIR/compare-paper-scale.py" "${ROWS[@]}" 2>&1)"
crc=$?
set -e
printf '%s\n' "$out"

mkdir -p "$ROOT/bench/results"
printf '%s\n' "$out" >"$ROOT/bench/results/cite-paper-scale-latest-verdict.txt"
printf '%s\n' "$STAMP" >"$ROOT/bench/results/cite-paper-scale-latest-stamp.txt"
for f in "${ROWS[@]}"; do
  base="$(basename "$f")"
  # Stable pointers: cite-paper-scale-latest-paper.json etc.
  id="${base##*-}"
  id="${id%.json}"
  cp -f "$f" "$ROOT/bench/results/cite-paper-scale-latest-${id}.json"
done

case "$crc" in
  0)
    if printf '%s\n' "$out" | grep -q 'SCALE WIN'; then
      echo "PASS: paper scale win (stamp=$STAMP) — record under docs/folia/YAP_FOLIA_PATCHES.md"
      exit 0
    fi
    echo "STAMPED: results recorded but no scale win yet (raise YAP_BENCH_PLAYERS). stamp=$STAMP"
    exit 0
    ;;
  3)
    echo "FAIL: fairness (stamp=$STAMP)" >&2
    exit 3
    ;;
  *)
    echo "FAIL: compare-paper-scale rc=$crc" >&2
    exit "$crc"
    ;;
esac
