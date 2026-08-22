#!/usr/bin/env bash
# Real stress sweep — fullcite (bots + redstone/hoppers/TNT) at 100/150/200/250.
# Bots use spread grid + ACTIVE physics (not keepalive-only cite-stable).
#
# Usage:
#   ./scripts/bench/run-stress-sweep.sh [sample_seconds]
#   YAP_STRESS_LEVELS=100,150,200,250   — override counts
#   YAP_BENCH_COMPETITORS=yapcore        — default yapcore only (faster)
#   YAP_BENCH_COMPETITORS=folia,yapcore  — compare both
#
# Requires: Java 25+, Node, npm deps in scripts/bench/bots (mineflayer).
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=../lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

SAMPLE_SEC="${1:-60}"
LEVELS_CSV="${YAP_STRESS_LEVELS:-100,150,200,250}"
STAMP="${YAP_BENCH_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
SUMMARY="$ROOT/bench/results/${STAMP}-stress-sweep-summary.txt"

cd "$ROOT"
export ROOT
yap_require_java

echo "=== YaP stress sweep (fullcite + active bots + spread redstone) ===" | tee "$SUMMARY"
echo "stamp=$STAMP sample=${SAMPLE_SEC}s levels=$LEVELS_CSV" | tee -a "$SUMMARY"
echo "YAP_BOT_ACTIVE=1 YAP_BOT_CITE_STABLE=0 (real movement/inventory/redstone load)" | tee -a "$SUMMARY"
echo | tee -a "$SUMMARY"

export YAP_BOT_ACTIVE=1
export YAP_BOT_CITE_STABLE=0
export YAP_BENCH_COMPETITORS="${YAP_BENCH_COMPETITORS:-yapcore}"
export YAP_BENCH_ENTITIES="${YAP_BENCH_ENTITIES:-600}"
export YAP_BENCH_HEAVY_HOPPERS="${YAP_BENCH_HEAVY_HOPPERS:-128}"
export YAP_BENCH_HOPPERS="${YAP_BENCH_HOPPERS:-64}"
export YAP_BENCH_WARMUP="${YAP_BENCH_WARMUP:-30}"
export YAP_BENCH_COOLDOWN="${YAP_BENCH_COOLDOWN:-45}"
export YAP_BENCH_GAME_XMS="${YAP_BENCH_GAME_XMS:-8G}"
export YAP_BENCH_GAME_XMX="${YAP_BENCH_GAME_XMX:-12G}"
export NODE_OPTIONS="${NODE_OPTIONS:---max-old-space-size=6144}"

IFS=',' read -r -a LEVELS <<<"$LEVELS_CSV"
for n in "${LEVELS[@]}"; do
  n="$(echo "$n" | tr -d '[:space:]')"
  [[ -z "$n" ]] && continue
  echo "--- level $n bots ---" | tee -a "$SUMMARY"
  export YAP_BENCH_PLAYERS="$n"
  export YAP_BENCH_JOIN_TIMEOUT="$((240 + n))"
  export YAP_BENCH_STAMP="${STAMP}-p${n}"
  # More workers under heavy active load
  if [ "$n" -ge 200 ]; then
    export YAP_BOT_WORKERS="${YAP_BOT_WORKERS:-4}"
    export YAP_BOT_STAGGER_MS="${YAP_BOT_STAGGER_MS:-200}"
  elif [ "$n" -ge 150 ]; then
    export YAP_BOT_WORKERS="${YAP_BOT_WORKERS:-3}"
    export YAP_BOT_STAGGER_MS="${YAP_BOT_STAGGER_MS:-180}"
  else
    unset YAP_BOT_WORKERS || true
    export YAP_BOT_STAGGER_MS="${YAP_BOT_STAGGER_MS:-150}"
  fi
  if ! "$SCRIPT_DIR/run-vs-folia.sh" fullcite "$SAMPLE_SEC"; then
    echo "WARN: fullcite run failed at $n bots" | tee -a "$SUMMARY"
  fi
  for f in "$ROOT/bench/results/${STAMP}-p${n}"-fullcite-*.json; do
    [ -f "$f" ] || continue
    python3 - "$f" "$n" <<'PY' | tee -a "$SUMMARY"
import json, sys
path, n = sys.argv[1], sys.argv[2]
with open(path) as f:
    d = json.load(f)
mspt = d.get("mspt_mean") or d.get("mean_mspt") or d.get("mspt")
p95 = d.get("mspt_p95") or d.get("p95_mspt")
players = d.get("players_ok") or d.get("players")
label = d.get("label") or path
print(f"  {label}: players={players} mspt_mean={mspt} mspt_p95={p95} file={path}")
PY
  done
  echo | tee -a "$SUMMARY"
  sleep "${YAP_BENCH_COOLDOWN:-45}"
done

echo "Done. Summary: $SUMMARY"
echo "JSON results: $ROOT/bench/results/${STAMP}-p*"*-fullcite-*.json"
