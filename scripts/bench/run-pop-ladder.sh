#!/usr/bin/env bash
# Pop ladder — real-load validation after tracker/engine changes + before play-depth claims.
#
# Tiers (Mineflayer bots doing move/combat/chest/dig — not TNT-only):
#   low    50 players
#   mid   100 players
#   high  150 players
#   heavy 200 players
#
# Also runs:
#   1) Phase 4 automated play-soak gates (unit + Xbox shaped CI)
#   2) Denser heavypop MSPT re-bench vs Leaf (2400 TNT / 512 hoppers) — product cite gate
#
# Usage:
#   ./scripts/bench/run-pop-ladder.sh                  # soak + heavypop + all bot tiers (yap-only)
#   ./scripts/bench/run-pop-ladder.sh --tiers mid,heavy
#   ./scripts/bench/run-pop-ladder.sh --bots-only
#   ./scripts/bench/run-pop-ladder.sh --msp-only        # denser heavypop only
#   ./scripts/bench/run-pop-ladder.sh --soak-only
#   YAP_BENCH_COMPETITORS=paper,leaf,yapcore ./scripts/bench/run-pop-ladder.sh --bots-only
#
# Env:
#   YAP_LADDER_TIERS          comma list (default low,mid,high,heavy)
#   YAP_BENCH_COMPETITORS     default yapcore (fast regression); set leaf,paper,… for cites
#   YAP_BENCH_SAMPLE_SECONDS  override sample window for bot tiers
#   YAP_SKIP_SOAK=1 / YAP_SKIP_MSP=1 / YAP_SKIP_BOTS=1
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=../lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

cd "$ROOT"
mkdir -p "$ROOT/logs/bench" "$ROOT/bench/results"

RUN_SOAK=1
RUN_MSP=1
RUN_BOTS=1
TIERS_CSV="${YAP_LADDER_TIERS:-low,mid,high,heavy}"

for arg in "$@"; do
  case "$arg" in
    --soak-only) RUN_SOAK=1; RUN_MSP=0; RUN_BOTS=0 ;;
    --msp-only|--heavypop-only) RUN_SOAK=0; RUN_MSP=1; RUN_BOTS=0 ;;
    --bots-only) RUN_SOAK=0; RUN_MSP=0; RUN_BOTS=1 ;;
    --tiers=*) TIERS_CSV="${arg#--tiers=}" ;;
    --help|-h)
      sed -n '2,28p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown arg: $arg (try --help)" >&2
      exit 2
      ;;
  esac
done

[[ "${YAP_SKIP_SOAK:-0}" == "1" ]] && RUN_SOAK=0
[[ "${YAP_SKIP_MSP:-0}" == "1" ]] && RUN_MSP=0
[[ "${YAP_SKIP_BOTS:-0}" == "1" ]] && RUN_BOTS=0

# Default competitor set for ladder: YaP only (engine regression). Expand for cites.
export YAP_BENCH_COMPETITORS="${YAP_BENCH_COMPETITORS:-yapcore}"

tier_players() {
  case "$1" in
    low) echo 50 ;;
    mid) echo 100 ;;
    high) echo 150 ;;
    heavy) echo 200 ;;
    *)
      echo "Unknown tier: $1 (use low|mid|high|heavy)" >&2
      return 1
      ;;
  esac
}

tier_sample() {
  case "$1" in
    low) echo "${YAP_BENCH_SAMPLE_SECONDS:-30}" ;;
    mid) echo "${YAP_BENCH_SAMPLE_SECONDS:-40}" ;;
    high|heavy) echo "${YAP_BENCH_SAMPLE_SECONDS:-45}" ;;
    *) echo 40 ;;
  esac
}

tier_warmup() {
  case "$1" in
    low) echo 15 ;;
    mid) echo 20 ;;
    high|heavy) echo 25 ;;
    *) echo 20 ;;
  esac
}

tier_heap() {
  # XMS XMX
  case "$1" in
    low) echo "4G 6G" ;;
    mid) echo "6G 8G" ;;
    high) echo "8G 10G" ;;
    heavy) echo "8G 12G" ;;
    *) echo "8G 12G" ;;
  esac
}

STAMP_BASE="${YAP_BENCH_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
SUMMARY="$ROOT/logs/bench/pop-ladder-${STAMP_BASE}.log"
{
  echo "=== YaPcore pop ladder ${STAMP_BASE} ==="
  echo "competitors=${YAP_BENCH_COMPETITORS}"
  echo "tiers=${TIERS_CSV}"
  echo "soak=${RUN_SOAK} msp=${RUN_MSP} bots=${RUN_BOTS}"
  echo
} | tee "$SUMMARY"

FAIL=0

if [[ "$RUN_SOAK" == "1" ]]; then
  echo "== [1/3] Phase 4 play-soak automated gates ==" | tee -a "$SUMMARY"
  if ./scripts/protocol-matrix/play-soak.sh 2>&1 | tee -a "$SUMMARY"; then
    echo "SOAK: PASS" | tee -a "$SUMMARY"
  else
    echo "SOAK: FAIL" | tee -a "$SUMMARY"
    FAIL=1
  fi
  echo | tee -a "$SUMMARY"
fi

if [[ "$RUN_MSP" == "1" ]]; then
  echo "== [2/3] Denser heavypop MSPT (2400 TNT / 512 hoppers) vs competitors ==" | tee -a "$SUMMARY"
  # Prefer Leaf+Yap when competitors defaulted to yap-only — still prove Leaf gap.
  local_comp="$YAP_BENCH_COMPETITORS"
  if [[ "$local_comp" == "yapcore" ]]; then
    local_comp="leaf,yapcore"
  fi
  if YAP_BENCH_ENTITIES=600 YAP_BENCH_HOPPERS=128 \
      YAP_BENCH_COMPETITORS="$local_comp" \
      YAP_BENCH_STAMP="${STAMP_BASE}-msp" \
      ./scripts/bench/run-vs-ecosystem.sh heavypop 45 2>&1 | tee -a "$SUMMARY"; then
    echo "MSP denser heavypop: PASS (see bench/results/${STAMP_BASE}-msp-heavypop-*.json)" | tee -a "$SUMMARY"
  else
    echo "MSP denser heavypop: FAIL" | tee -a "$SUMMARY"
    FAIL=1
  fi
  echo | tee -a "$SUMMARY"
fi

if [[ "$RUN_BOTS" == "1" ]]; then
  echo "== [3/3] Bot pop ladder (move/combat/chest/dig) ==" | tee -a "$SUMMARY"
  IFS=',' read -ra TIERS <<<"$TIERS_CSV"
  for raw in "${TIERS[@]}"; do
    tier="$(echo "$raw" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
    [[ -z "$tier" ]] && continue
    players="$(tier_players "$tier")" || { FAIL=1; continue; }
    sample="$(tier_sample "$tier")"
    warmup="$(tier_warmup "$tier")"
    read -r xms xmx <<<"$(tier_heap "$tier")"
    stamp="${STAMP_BASE}-${tier}"
    echo "--- tier=${tier} players=${players} sample=${sample}s warmup=${warmup}s heap=${xms}/${xmx} ---" | tee -a "$SUMMARY"
    if YAP_BENCH_STAMP="$stamp" \
        YAP_BENCH_WARMUP="$warmup" \
        YAP_BENCH_XMS="$xms" \
        YAP_BENCH_XMX="$xmx" \
        YAP_BENCH_COMPETITORS="$YAP_BENCH_COMPETITORS" \
        ./scripts/bench/run-highpop.sh "$players" "$sample" 2>&1 | tee -a "$SUMMARY"; then
      # run-highpop exits 0 even on missing JSON — require a fair result file
      out_json="$ROOT/bench/results/${stamp}-highpop-yapcore.json"
      if [[ ! -f "$out_json" ]]; then
        # also accept competitor-named files when comparing
        shopt -s nullglob
        outs=( "$ROOT/bench/results/${stamp}-highpop-"*.json )
        shopt -u nullglob
        if [[ ${#outs[@]} -eq 0 ]]; then
          echo "BOT ${tier}: FAIL (no result JSON)" | tee -a "$SUMMARY"
          FAIL=1
        else
          echo "BOT ${tier}: PASS → ${outs[*]}" | tee -a "$SUMMARY"
        fi
      else
        echo "BOT ${tier}: PASS → $out_json" | tee -a "$SUMMARY"
      fi
    else
      echo "BOT ${tier}: FAIL" | tee -a "$SUMMARY"
      FAIL=1
    fi
    echo | tee -a "$SUMMARY"
  done
fi

echo "== Ladder summary ==" | tee -a "$SUMMARY"
echo "Log: $SUMMARY" | tee -a "$SUMMARY"
echo "Results under bench/results/${STAMP_BASE}*" | tee -a "$SUMMARY"
echo
echo "Live play-depth checklist (operator): docs/VIA_GEYSER_PARITY.md §E"
echo "  ./scripts/protocol-matrix/play-soak.sh --all   # when Via front + BE are up"
echo

if [[ "$FAIL" -ne 0 ]]; then
  echo "POP LADDER: FAIL" | tee -a "$SUMMARY"
  exit 1
fi
echo "POP LADDER: PASS" | tee -a "$SUMMARY"
exit 0
