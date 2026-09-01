#!/usr/bin/env bash
# Validate full YaP-Folia stack: budget, microtick, parallel sub-regions, optional contiguous carve.
# Uses citeable load (8k TNT / 1024 hoppers / 2500 mobs / strip half-width 56).
#
# Usage: ./scripts/bench/run-full-stack.sh [sample_seconds]
# Env: YAP_BENCH_STAMP, YAP_BENCH_SKIP_CONTIGUOUS=1 to skip P3 contiguous carve test
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"
SAMPLE="${1:-50}"
STAMP="${YAP_BENCH_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)-fullstack}"
RESULTS="$ROOT/bench/results"
mkdir -p "$RESULTS"

COMMON=(
  YAP_BENCH_CITE_LOAD=1
  YAP_BENCH_SHUFFLE=0
  YAP_BENCH_COMPETITORS=folia,yapfolia
  YAP_BENCH_GAME_XMS=4G
  YAP_BENCH_GAME_XMX=8G
  YAP_BENCH_WARMUP=25
)

fail=0
check_json() {
  local label="$1" f="$2"
  if [ ! -f "$f" ]; then
    echo "FAIL: missing $f" >&2
    fail=1
    return
  fi
  python3 - <<PY || fail=1
import json, sys
label = "$label"
d = json.load(open("$f"))
ok = d.get("fuse_ticking_ok", False)
mspt = float(d.get("mspt_mean", 0))
print(f"  {label}: mspt={mspt:.2f} fuse_ok={ok} tnt={d.get('tnt_start')}->{d.get('tnt_end')} chunks={d.get('chunks_loaded_end')}")
if not ok:
    sys.exit(1)
PY
}

echo "======== Full-stack validation stamp=$STAMP sample=${SAMPLE}s ========"

echo ""
echo "== 1/3 Budget + async (partition OFF) =="
env "${COMMON[@]}" \
  YAP_BENCH_STAMP="${STAMP}-budget" \
  YAP_FOLIA_ENTITY_TICK_BUDGET=300 \
  YAP_FOLIA_ASYNC_CHUNK_SAVE=true \
  YAP_FOLIA_SUBREGION_PARTITION=false \
  "$ROOT/scripts/bench/run-vs-folia.sh" spawncollapse "$SAMPLE"
check_json stock-folia "$RESULTS/${STAMP}-budget-spawncollapse-folia.json" || true
check_json yap-budget "$RESULTS/${STAMP}-budget-spawncollapse-yapfolia.json" || true

echo ""
echo "== 2/3 Full stack: budget + async + microtick + partition (lobe, carve OFF) =="
env "${COMMON[@]}" \
  YAP_BENCH_STAMP="${STAMP}-fullstack" \
  YAP_BENCH_FULL_STACK=1 \
  "$ROOT/scripts/bench/run-vs-folia.sh" spawncollapse "$SAMPLE"
check_json stock-lobe "$RESULTS/${STAMP}-fullstack-spawncollapse-folia.json" || true
check_json yap-fullstack "$RESULTS/${STAMP}-fullstack-spawncollapse-yapfolia.json" || true

if [ "${YAP_BENCH_SKIP_CONTIGUOUS:-0}" != "1" ]; then
  echo ""
  echo "== 3/3 Dynamic contiguous carve + partition =="
  env "${COMMON[@]}" \
    YAP_BENCH_STAMP="${STAMP}-contig" \
    YAP_BENCH_WARMUP=60 \
    YAP_BENCH_CONTIGUOUS_CARVE=true \
    YAP_FOLIA_ENTITY_TICK_BUDGET=300 \
    YAP_FOLIA_ASYNC_CHUNK_SAVE=true \
    YAP_FOLIA_MICROTICK_BUDGET_MS=8 \
    YAP_FOLIA_SUBREGION_PARTITION=true \
    YAP_FOLIA_SUBREGION_CARVE=true \
    YAP_FOLIA_SUBREGION_MSPT_THRESHOLD=15 \
    YAP_FOLIA_SUBREGION_PARTITION_DELAY_TICKS=800 \
    "$ROOT/scripts/bench/run-vs-folia.sh" spawncollapse "$SAMPLE"
  check_json stock-contig "$RESULTS/${STAMP}-contig-spawncollapse-folia.json" || true
  check_json yap-contig "$RESULTS/${STAMP}-contig-spawncollapse-yapfolia.json" || true
else
  echo "SKIP contiguous carve (YAP_BENCH_SKIP_CONTIGUOUS=1)"
fi

echo ""
echo "======== Summary ========"
python3 - <<PY
import json, glob, os
stamp = "$STAMP"
rows = []
for pat in [
    f"{stamp}-budget-spawncollapse-*.json",
    f"{stamp}-fullstack-spawncollapse-*.json",
    f"{stamp}-contig-spawncollapse-*.json",
]:
    for f in sorted(glob.glob(os.path.join("$RESULTS", pat))):
        d = json.load(open(f))
        rows.append((os.path.basename(f), d.get("label"), d.get("mspt_mean"), d.get("fuse_ticking_ok"), d.get("tnt_end")))
for name, label, mspt, fuse, tnt in rows:
    print(f"  {name}: {label} mspt={mspt:.2f} fuse_ok={fuse} tnt_end={tnt}")
PY

if [ "$fail" -ne 0 ]; then
  echo "FAIL: one or more runs failed fuse_ticking_ok" >&2
  exit 1
fi
echo "PASS: full-stack validation ($STAMP)"
