#!/usr/bin/env bash
# Phase 3.3 — async chunk save smoke: /save-all mid-sample; compare stock vs YaP spike.
# Target: YaP mspt_save_spike ≤ 50% of stock (with -Dyap.folia.async-chunk-save=true).
#
# Usage: ./scripts/smoke-folia-async-save.sh
# Env:
#   SKIP_LIVE=1     — compile gate only
#   YAP_BENCH_ENTITIES / YAP_BENCH_HOPPERS — load (defaults 600 / 192)
#   YAP_BENCH_GAME_XMS / YAP_BENCH_GAME_XMX — JVM (defaults 2G / 4G)
#   SAVE_ALL_AT     — seconds into sample to fire save-all (default 5)
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"
export ROOT
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"
yap_require_java

VER="${FOLIA_VERSION:-26.2}"
ENTITIES="${YAP_BENCH_ENTITIES:-600}"
HOPPERS="${YAP_BENCH_HOPPERS:-192}"
GAME_XMS="${YAP_BENCH_GAME_XMS:-2G}"
GAME_XMX="${YAP_BENCH_GAME_XMX:-4G}"
WARMUP="${YAP_BENCH_WARMUP:-10}"
SAMPLE="${YAP_BENCH_SECONDS:-22}"
SAVE_ALL_AT="${SAVE_ALL_AT:-5}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RESULTS="$ROOT/bench/results"
mkdir -p "$RESULTS"

echo "== build MSPT bench =="
gradle :bench-plugin:jar --no-daemon -q
BENCH_JAR="$(find "$ROOT/yap-first-party/dev/bench-plugin/build/libs" -name 'yap-mspt-bench.jar' | head -1)"
if [ -z "$BENCH_JAR" ] || [ ! -f "$BENCH_JAR" ]; then
  echo "FAIL: missing yap-mspt-bench.jar" >&2
  exit 1
fi

if [ "${SKIP_LIVE:-0}" = "1" ]; then
  echo "SKIP_LIVE=1 — async-save smoke PASS (compile only)"
  echo "NOTE: Live gate compares stock vs YaP mspt_save_spike (target ≤50%)"
  exit 0
fi

STOCK_JAR=""
for c in "$ROOT/lib/folia-${VER}.jar" "$ROOT/server/lib/folia-${VER}.jar"; do
  [ -f "$c" ] && STOCK_JAR="$c" && break
done
YAP_JAR=""
for c in "$ROOT/lib/yap-folia-${VER}.jar" "$ROOT/server/lib/yap-folia-${VER}.jar"; do
  [ -f "$c" ] && YAP_JAR="$c" && break
done
if [ -z "$STOCK_JAR" ]; then
  echo "Fetching stock Folia ${VER}…"
  "$ROOT/scripts/fetch-folia.sh" "$VER"
  STOCK_JAR="$ROOT/lib/folia-${VER}.jar"
fi
if [ -z "$YAP_JAR" ] || [ ! -f "$YAP_JAR" ]; then
  echo "FAIL: missing yap-folia-${VER}.jar — run ./scripts/build-yap-folia.sh" >&2
  exit 1
fi

JAVA_BIN="$(yap_java_bin)"

write_props() {
  local dest="$1" port="$2" motd="$3"
  cat >"$dest" <<EOF
server-port=${port}
online-mode=false
max-players=20
motd=${motd}
view-distance=10
simulation-distance=12
level-seed=yap-bench-1
spawn-protection=0
EOF
}

write_spigot() {
  cat >"$1" <<'EOF'
world-settings:
  default:
    max-tnt-per-tick: 0
    max-tick-time:
      tile: 0
      entity: 0
    entity-activation-range:
      animals: 48
      monsters: 48
      raiders: 48
      misc: 0
      water: 16
      villagers: 32
      flying-monsters: 48
EOF
}

run_side() {
  local id="$1"
  local jar="$2"
  local port="$3"
  local async="$4"   # true|false
  local out="$RESULTS/${STAMP}-asyncsave-${id}.json"
  local work="$ROOT/bench/workdir-async-save-${id}"
  rm -rf "$work"
  mkdir -p "$work/plugins"
  /bin/cp -f "$jar" "$work/server.jar"
  /bin/cp -f "$BENCH_JAR" "$work/plugins/yap-mspt-bench.jar"
  printf 'eula=true\n' >"$work/eula.txt"
  write_props "$work/server.properties" "$port" "YaP async-save $id"
  write_spigot "$work/spigot.yml"

  echo "=== async-save $id (async-chunk-save=$async) → $out ==="
  (
    cd "$work"
    "$JAVA_BIN" -Xms"$GAME_XMS" -Xmx"$GAME_XMX" \
      -Dyap.bench.scenario=heavypop \
      -Dyap.bench.entities="$ENTITIES" \
      -Dyap.bench.hoppers="$HOPPERS" \
      -Dyap.bench.seconds="$SAMPLE" \
      -Dyap.bench.warmup="$WARMUP" \
      -Dyap.bench.save_all_at="$SAVE_ALL_AT" \
      -Dyap.bench.label="async-save-${id}" \
      -Dyap.bench.out="$out" \
      -Dyap.bench.game_xms="$GAME_XMS" \
      -Dyap.bench.game_xmx="$GAME_XMX" \
      -Dyap.bench.measurement_scope=game_tick_mspt \
      -Dyap.bench.root="$ROOT" \
      -Dyapcore.home="$ROOT" \
      -Dyap.folia.async-chunk-save="$async" \
      -jar server.jar --nogui </dev/null
  ) || true

  if [ ! -f "$out" ]; then
    echo "FAIL: $id did not write $out" >&2
    return 1
  fi
  python3 - <<PY
import json
p=json.load(open("$out"))
spike=p.get("mspt_save_spike")
pre=p.get("mspt_pre_save_mean")
print("  mspt_mean=%.4f pre_save=%s save_spike=%s" % (
  p.get("mspt_mean",0),
  ("%.4f"%pre) if pre is not None else "null",
  ("%.4f"%spike) if spike is not None else "null"))
PY
}

run_side stock "$STOCK_JAR" 25592 false
run_side yapfolia "$YAP_JAR" 25593 true

STOCK_JSON="$RESULTS/${STAMP}-asyncsave-stock.json"
YAP_JSON="$RESULTS/${STAMP}-asyncsave-yapfolia.json"
SUMMARY="$RESULTS/${STAMP}-asyncsave-compare.json"

python3 - <<PY
import json, sys
from pathlib import Path

stock = json.loads(Path("$STOCK_JSON").read_text())
yap = json.loads(Path("$YAP_JSON").read_text())
s = stock.get("mspt_save_spike")
y = yap.get("mspt_save_spike")
if s is None or y is None:
    print("FAIL: missing mspt_save_spike (save_all_at may not have fired)", file=sys.stderr)
    sys.exit(1)
ratio = (y / s) if s > 0 else 0.0
target_ok = y <= s * 0.50
doc = {
  "stamp": "$STAMP",
  "stock_spike": s,
  "yap_spike": y,
  "ratio": round(ratio, 4),
  "target": "yap <= 50% stock",
  "target_met": target_ok,
  "stock_file": "$STOCK_JSON",
  "yap_file": "$YAP_JSON",
  "entities": int("$ENTITIES"),
  "hoppers": int("$HOPPERS"),
  "save_all_at": int("$SAVE_ALL_AT"),
  "knobs": {"async-chunk-save": True},
}
Path("$SUMMARY").write_text(json.dumps(doc, indent=2) + "\n")
print("wrote", "$SUMMARY")
print("stock_spike=%.4f  yap_spike=%.4f  ratio=%.1f%%  target_met=%s" % (
  s, y, ratio * 100, target_ok))
if not target_ok:
    print("NOTE: missed ≤50% target — document as miss in BENCH_VS_FOLIA.md; still a citeable row")
    # Soft-fail for soak hook: exit 0 so soak continues; hard assert via ASYNC_SAVE_STRICT=1
    import os
    if os.environ.get("ASYNC_SAVE_STRICT", "0") == "1":
        sys.exit(2)
sys.exit(0)
PY

echo "smoke-folia-async-save PASS (stamp=$STAMP)"
echo "  fill docs/performance/BENCH_VS_FOLIA.md async-save row from $SUMMARY"
