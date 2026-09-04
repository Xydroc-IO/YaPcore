#!/usr/bin/env bash
# Produce a *citeable* fullcite MSPT A/B: stock Folia vs YaPcore (YaP-Folia chassis).
# Fails unless compare-folia.py returns a citeable win or ≤+2% tie (not noise).
#
# Usage:
#   ./scripts/bench/cite-fullcite.sh
#   YAP_BENCH_PLAYERS=100 YAP_BENCH_SECONDS=40 ./scripts/bench/cite-fullcite.sh
#
# Requires: Java 25+, node/npm (Mineflayer), lib/folia-*.jar, lib/yap-folia-*.jar, yapcore jar.
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

SECONDS_N="${YAP_BENCH_SECONDS:-40}"
PLAYERS="${YAP_BENCH_PLAYERS:-100}"
STAMP="${YAP_BENCH_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"

# Ship-knob defaults — cite the product profile (smart budget + microtick + partition).
# Stock Folia/Canvas ignore unknown -D; disclose knobs in result JSON.
export YAP_BENCH_COMPETITORS="${YAP_BENCH_COMPETITORS:-folia,yapcore}"
export YAP_BENCH_PLAYERS="$PLAYERS"
export YAP_BENCH_STAMP="$STAMP"
export YAP_BENCH_SHUFFLE="${YAP_BENCH_SHUFFLE:-0}"
export YAP_FOLIA_ASYNC_CHUNK_SAVE="${YAP_FOLIA_ASYNC_CHUNK_SAVE:-true}"
export YAP_FOLIA_HOPPER_TICK_BUDGET="${YAP_FOLIA_HOPPER_TICK_BUDGET:-64}"
export YAP_FOLIA_ENTITY_TICK_BUDGET="${YAP_FOLIA_ENTITY_TICK_BUDGET:-400}"
export YAP_FOLIA_MICROTICK_BUDGET_MS="${YAP_FOLIA_MICROTICK_BUDGET_MS:-8}"
export YAP_FOLIA_BUDGET_MSPT_THRESHOLD="${YAP_FOLIA_BUDGET_MSPT_THRESHOLD:-12}"
export YAP_FOLIA_SUBREGION_PARTITION="${YAP_FOLIA_SUBREGION_PARTITION:-true}"
export YAP_BOT_CITE_STABLE="${YAP_BOT_CITE_STABLE:-0}"
# Active physics without terraforming — dig/caves explode chunk load and kill cite deltas.
export YAP_BOT_NO_DIG="${YAP_BOT_NO_DIG:-1}"
# Cite geometry: keep view/sim matched; 8 ≈ spread-grid VD union without thrash.
export YAP_BENCH_VIEW_DISTANCE="${YAP_BENCH_VIEW_DISTANCE:-8}"
export YAP_BENCH_SIM_DISTANCE="${YAP_BENCH_SIM_DISTANCE:-8}"
export YAP_MSPT_STRICT_CITEABLE=1
export YAP_MSPT_REQUIRE_CITEABLE=1
# Require JSON knobs prove ship profile (compare-folia fairness).
export YAP_MSPT_REQUIRE_SHIP_KNOBS=1

yap_banner "cite-fullcite · stamp=$STAMP · players=$PLAYERS · ${SECONDS_N}s"

if [ ! -f "$ROOT/lib/folia-26.2.jar" ] && [ ! -f "$ROOT/lib/folia-${FOLIA_VERSION:-26.2}.jar" ]; then
  echo "Fetching stock Folia…"
  "$ROOT/scripts/fetch-folia.sh" || "$SCRIPT_DIR/fetch-folia-forks.sh" "${FOLIA_VERSION:-26.2}"
fi
if [ ! -f "$ROOT/lib/yap-folia-26.2.jar" ] && [ ! -f "$ROOT/lib/yap-folia-${FOLIA_VERSION:-26.2}.jar" ]; then
  echo "Building YaP-Folia…"
  "$ROOT/scripts/build-yap-folia.sh"
fi

"$SCRIPT_DIR/run-vs-folia.sh" fullcite "$SECONDS_N"

STOCK="$ROOT/bench/results/${STAMP}-fullcite-folia.json"
YAP="$ROOT/bench/results/${STAMP}-fullcite-yapcore.json"
if [ ! -f "$STOCK" ] || [ ! -f "$YAP" ]; then
  echo "FAIL: missing result JSON (stock=$STOCK yap=$YAP)" >&2
  ls -lt "$ROOT/bench/results/${STAMP}"* 2>/dev/null || true
  exit 1
fi

echo
echo "== cite gate =="
set +e
"$SCRIPT_DIR/check-mspt-regression.sh" "$STOCK" "$YAP"
rc=$?
set -e

# Soft-pass (exit 0 from check) still needs a *citeable* verdict from compare.
set +e
out="$(python3 "$SCRIPT_DIR/compare-folia.py" "$STOCK" "$YAP" 2>&1)"
crc=$?
set -e
printf '%s\n' "$out"
case "$crc" in
  0)
    if printf '%s\n' "$out" | grep -q 'NOT CITEABLE'; then
      echo "FAIL: compare returned 0 but NOT CITEABLE" >&2
      exit 4
    fi
    echo "PASS: citeable fullcite (stamp=$STAMP)"
    echo "Stock: $STOCK"
    echo "YaP:   $YAP"
    # Publish a stable pointer for docs / operators
    mkdir -p "$ROOT/bench/results"
    cp -f "$STOCK" "$ROOT/bench/results/cite-latest-stock-folia.json"
    cp -f "$YAP" "$ROOT/bench/results/cite-latest-yapcore.json"
    printf '%s\n' "$out" >"$ROOT/bench/results/cite-latest-verdict.txt"
    exit 0
    ;;
  4)
    echo "FAIL: not citeable — increase load / fix bots / re-run" >&2
    exit 4
    ;;
  *)
    echo "FAIL: cite gate rc=$crc (check wrapper rc=$rc)" >&2
    exit "$crc"
    ;;
esac
