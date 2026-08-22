#!/usr/bin/env bash
# Detached active-bot ladder: 100 → 150 → 200 → 250 vs Paper/Leaf/YaP.
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "$ROOT"
mkdir -p logs/bench bench/results
rm -f bench/highpop.lock

STAMP="${YAP_BENCH_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
LOG="logs/bench/pop-ladder-100-250-${STAMP}.log"
export YAP_BENCH_COMPETITORS="${YAP_BENCH_COMPETITORS:-paper,leaf,yapcore}"
export YAP_BOT_CITE_STABLE=0

{
  echo "=== Fresh active ladder ${STAMP} (YAP_BOT_CITE_STABLE=0) ==="
  echo "competitors=${YAP_BENCH_COMPETITORS}"
  echo "started=$(date -Is)"
  echo "pid=$$"
  echo
} | tee "$LOG"

YAP_BENCH_STAMP="${STAMP}" \
  ./scripts/bench/run-pop-ladder.sh --bots-only --tiers=mid,high,heavy 2>&1 | tee -a "$LOG"

echo "--- tier=xheavy players=250 ---" | tee -a "$LOG"
YAP_BENCH_STAMP="${STAMP}-xheavy" YAP_BENCH_WARMUP=30 YAP_BENCH_XMS=10G YAP_BENCH_XMX=14G \
  ./scripts/bench/run-highpop.sh 250 45 2>&1 | tee -a "$LOG"

echo "=== DONE ${STAMP} at $(date -Is) ===" | tee -a "$LOG"
for t in mid high heavy; do
  echo "-- compare ${STAMP}-${t} --" | tee -a "$LOG"
  python3 scripts/bench/compare-highpop.py bench/results "${STAMP}-${t}" 2>&1 | tee -a "$LOG" || true
done
echo "-- compare ${STAMP}-xheavy --" | tee -a "$LOG"
python3 scripts/bench/compare-highpop.py bench/results "${STAMP}-xheavy" 2>&1 | tee -a "$LOG" || true
echo "Log: $LOG"
