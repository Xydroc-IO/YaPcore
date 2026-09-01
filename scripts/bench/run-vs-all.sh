#!/usr/bin/env bash
# Full ecosystem speed test — Folia + forks + YaP vs Paper / Purpur / Leaf.
#
# Usage: ./scripts/bench/run-vs-all.sh [sample_seconds]
#
# Runs:
#   1. spawncollapse (citeable load: 8k TNT / 1024 hoppers / 2500 mobs) — all peers
#   2. fullcite (100 active bots + fixtures) — folia, yapcore, paper
#
# Requires: Java 25+, Node/npm in scripts/bench/bots for fullcite.
# Fetch jars first (or this script will fetch):
#   ./scripts/bench/fetch-folia-forks.sh
#   ./scripts/bench/fetch-competitors.sh
#
# Results: bench/results/<stamp>-speedtest-*.json
# Summary: bench/results/<stamp>-speedtest-summary.txt
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=../lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

SAMPLE="${1:-45}"
STAMP="${YAP_BENCH_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)-speedtest}"
RESULTS="$ROOT/bench/results"
SUMMARY="$RESULTS/${STAMP}-summary.txt"

cd "$ROOT"
yap_require_java
mkdir -p "$RESULTS" "$ROOT/logs/bench"

echo "=== YaP full ecosystem speed test ===" | tee "$SUMMARY"
echo "stamp=$STAMP sample=${SAMPLE}s" | tee -a "$SUMMARY"
echo "started=$(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a "$SUMMARY"
echo | tee -a "$SUMMARY"

echo "== Fetching competitor jars ==" | tee -a "$SUMMARY"
"$SCRIPT_DIR/fetch-folia-forks.sh" 2>&1 | tee -a "$SUMMARY"
"$SCRIPT_DIR/fetch-competitors.sh" 2>&1 | tee -a "$SUMMARY"
test -f "$ROOT/lib/yap-folia-26.2.jar" || "$ROOT/scripts/build-yap-folia.sh" 2>&1 | tee -a "$SUMMARY"
echo | tee -a "$SUMMARY"

COMMON=(
  YAP_BENCH_STAMP="$STAMP"
  YAP_BENCH_CITE_LOAD=1
  YAP_BENCH_SHUFFLE=1
  YAP_BENCH_GAME_XMS=4G
  YAP_BENCH_GAME_XMX=8G
  YAP_BENCH_WARMUP=25
  YAP_BENCH_COOLDOWN=10
  YAP_FOLIA_ENTITY_TICK_BUDGET=300
  YAP_FOLIA_ASYNC_CHUNK_SAVE=true
)

ALL_FOLIA="folia,canvas,yapfolia,yapcore,paper,purpur,leaf"

echo "== Phase 1/2: spawncollapse (citeable region overload) ==" | tee -a "$SUMMARY"
echo "competitors=$ALL_FOLIA" | tee -a "$SUMMARY"
env "${COMMON[@]}" \
  YAP_BENCH_COMPETITORS="$ALL_FOLIA" \
  "$SCRIPT_DIR/run-vs-folia.sh" spawncollapse "$SAMPLE" 2>&1 | tee -a "$SUMMARY"
echo | tee -a "$SUMMARY"

echo "== Phase 1 rank ==" | tee -a "$SUMMARY"
mapfile -t P1 < <(ls -1 "$RESULTS/${STAMP}"-spawncollapse-*.json 2>/dev/null | sort)
if [ "${#P1[@]}" -ge 2 ]; then
  python3 "$SCRIPT_DIR/compare-folia.py" --rank "${P1[@]}" 2>&1 | tee -a "$SUMMARY"
else
  echo "WARN: missing spawncollapse JSONs" | tee -a "$SUMMARY"
fi
echo | tee -a "$SUMMARY"

echo "== Phase 2/2: fullcite (100 active bots + redstone load) ==" | tee -a "$SUMMARY"
echo "competitors=folia,yapcore,paper (Paper-line uses main-thread MSPT)" | tee -a "$SUMMARY"
export YAP_BOT_ACTIVE=1
export YAP_BOT_CITE_STABLE=0
env "${COMMON[@]}" \
  YAP_BENCH_COMPETITORS=folia,yapcore,paper \
  YAP_BENCH_PLAYERS=100 \
  YAP_BENCH_ENTITIES=600 \
  YAP_BENCH_HEAVY_HOPPERS=128 \
  YAP_BENCH_HOPPERS=64 \
  YAP_BENCH_COOLDOWN=30 \
  YAP_BENCH_GAME_XMS=8G \
  YAP_BENCH_GAME_XMX=12G \
  YAP_BENCH_WARMUP=30 \
  "$SCRIPT_DIR/run-vs-folia.sh" fullcite 60 2>&1 | tee -a "$SUMMARY"
echo | tee -a "$SUMMARY"

echo "== Phase 2 rank ==" | tee -a "$SUMMARY"
mapfile -t P2 < <(ls -1 "$RESULTS/${STAMP}"-fullcite-*.json 2>/dev/null | sort)
if [ "${#P2[@]}" -ge 2 ]; then
  python3 "$SCRIPT_DIR/compare-folia.py" --rank "${P2[@]}" 2>&1 | tee -a "$SUMMARY"
else
  echo "WARN: missing fullcite JSONs" | tee -a "$SUMMARY"
fi
echo | tee -a "$SUMMARY"

echo "finished=$(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a "$SUMMARY"
echo "JSON: $RESULTS/${STAMP}-*.json" | tee -a "$SUMMARY"
echo "Summary: $SUMMARY" | tee -a "$SUMMARY"
echo "PASS: full speed test complete ($STAMP)"
