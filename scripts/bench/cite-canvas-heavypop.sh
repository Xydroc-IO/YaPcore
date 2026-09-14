#!/usr/bin/env bash
# Canvas peer cite campaign — heavypop under ship knobs; target ≥5% vs Canvas when fair.
# Usage: ./scripts/bench/cite-canvas-heavypop.sh [seconds]
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "$ROOT"
SECONDS_N="${1:-40}"
STAMP="${YAP_BENCH_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"

"$ROOT/scripts/bench/fetch-folia-forks.sh"

export YAP_BENCH_COMPETITORS=folia,canvas,yapcore
export YAP_BENCH_STAMP="$STAMP"
# Same ship tick profile as cite-fullcite (aligned microticks 0026–0030 + physics 0031).
export YAP_FOLIA_ASYNC_CHUNK_SAVE="${YAP_FOLIA_ASYNC_CHUNK_SAVE:-true}"
export YAP_FOLIA_HOPPER_TICK_BUDGET="${YAP_FOLIA_HOPPER_TICK_BUDGET:-64}"
export YAP_FOLIA_ENTITY_TICK_BUDGET="${YAP_FOLIA_ENTITY_TICK_BUDGET:-400}"
export YAP_FOLIA_MICROTICK_BUDGET_MS="${YAP_FOLIA_MICROTICK_BUDGET_MS:-8}"
export YAP_FOLIA_BUDGET_MSPT_THRESHOLD="${YAP_FOLIA_BUDGET_MSPT_THRESHOLD:-12}"
export YAP_FOLIA_SUBREGION_PARTITION="${YAP_FOLIA_SUBREGION_PARTITION:-true}"
export YAP_FOLIA_SUBREGION_MSPT_THRESHOLD="${YAP_FOLIA_SUBREGION_MSPT_THRESHOLD:-20}"
export YAP_FOLIA_SUBREGION_MSPT_CLEAR="${YAP_FOLIA_SUBREGION_MSPT_CLEAR:-16}"
export YAP_FOLIA_ALIGNED_MICROTICKS="${YAP_FOLIA_ALIGNED_MICROTICKS:-true}"
export YAP_FOLIA_MICRO_PHASES="${YAP_FOLIA_MICRO_PHASES:-4}"
export YAP_FOLIA_TICK_WAVE_MAX_WAIT_MS="${YAP_FOLIA_TICK_WAVE_MAX_WAIT_MS:-2}"
export YAP_FOLIA_PHYSICS_SUBSTEPS="${YAP_FOLIA_PHYSICS_SUBSTEPS:-true}"
export YAP_FOLIA_PHYSICS_SUBSTEP_COUNT="${YAP_FOLIA_PHYSICS_SUBSTEP_COUNT:-4}"
export YAP_FOLIA_PHYSICS_SUBSTEP_MIN_MOVE="${YAP_FOLIA_PHYSICS_SUBSTEP_MIN_MOVE:-0.02}"
export YAP_MSPT_REQUIRE_SHIP_KNOBS=1
export YAP_BENCH_NO_DIG="${YAP_BENCH_NO_DIG:-1}"

echo "=== Canvas heavypop cite campaign (ship knobs) ==="
"$ROOT/scripts/bench/run-vs-folia.sh" heavypop "$SECONDS_N"

STOCK="$ROOT/bench/results/${STAMP}-heavypop-folia.json"
CANVAS="$ROOT/bench/results/${STAMP}-heavypop-canvas.json"
YAP="$ROOT/bench/results/${STAMP}-heavypop-yapcore.json"
if [ ! -f "$YAP" ]; then
  echo "FAIL: missing yap heavypop JSON: $YAP" >&2
  ls -lt "$ROOT/bench/results/${STAMP}"* 2>/dev/null || true
  exit 1
fi

echo
echo "== ship knob gate (yap vs stock folia) =="
set +e
YAP_MSPT_REQUIRE_SHIP_KNOBS=1 python3 "$ROOT/scripts/bench/compare-folia.py" "$STOCK" "$YAP"
crc=$?
set -e
case "$crc" in
  0|1|4) ;; # MSPT delta may lose to stock; ship knobs must still pass (rc 3 = fairness)
  3)
    echo "FAIL: ship knob / fairness gate" >&2
    exit 3
    ;;
  *)
    echo "FAIL: compare-folia rc=$crc" >&2
    exit "$crc"
    ;;
esac

python3 - <<PY
import json
from pathlib import Path
stamp = "$STAMP"
by = {}
for name, key in (("canvas", "canvas"), ("folia", "folia"), ("yapcore", "yap")):
    p = Path(f"bench/results/{stamp}-heavypop-{name}.json")
    if not p.exists():
        continue
    d = json.loads(p.read_text())
    by[key] = (d.get("mspt_mean"), p.name, {k: d.get(k) for k in d if k.startswith("knob_")})
print("latest labels:", {k: round(v[0], 4) for k, v in by.items() if v[0] is not None})
if "yap" in by:
    print("yap ship knobs:", by["yap"][2])
if "yap" in by and "canvas" in by:
    yap, canvas = by["yap"][0], by["canvas"][0]
    delta = (yap - canvas) / canvas * 100.0
    print(f"yap vs canvas: {delta:.2f}% (negative = YaP faster)")
    if delta <= -5.0:
        print("VERDICT: citeable ≥5% vs Canvas")
    elif delta < 0:
        print("VERDICT: rank lead only (short of 5% cite gate)")
    else:
        print("VERDICT: Canvas ahead this stamp — investigate")
else:
    print("VERDICT: incomplete competitor set — check bench/results")
PY
