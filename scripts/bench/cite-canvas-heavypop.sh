#!/usr/bin/env bash
# Canvas peer cite campaign — heavypop under ship knobs; target ≥5% vs Canvas when fair.
# Usage: ./scripts/bench/cite-canvas-heavypop.sh [seconds]
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "$ROOT"
SECONDS_N="${1:-40}"

"$ROOT/scripts/bench/fetch-folia-forks.sh"

export YAP_BENCH_COMPETITORS=folia,canvas,yapcore
export YAP_MSPT_REQUIRE_SHIP_KNOBS=1
export YAP_BENCH_NO_DIG="${YAP_BENCH_NO_DIG:-1}"

echo "=== Canvas heavypop cite campaign (ship knobs) ==="
"$ROOT/scripts/bench/run-vs-folia.sh" heavypop "$SECONDS_N"

python3 - <<'PY'
import json
from pathlib import Path
cands = sorted(Path("bench/results").glob("*heavypop*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
by = {}
for p in cands[:20]:
    try:
        d = json.loads(p.read_text())
    except Exception:
        continue
    label = (d.get("label") or "").lower()
    mspt = d.get("mspt_mean")
    if mspt is None:
        continue
    if "canvas" in label:
        by.setdefault("canvas", (mspt, p.name))
    elif "yap" in label:
        by.setdefault("yap", (mspt, p.name))
    elif "folia" in label:
        by.setdefault("folia", (mspt, p.name))
print("latest labels:", {k: round(v[0], 4) for k, v in by.items()})
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
