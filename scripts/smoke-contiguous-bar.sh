#!/usr/bin/env bash
# Contiguous-strip check: live strip → carve → forcePartition + split → gap holds.
# Product view-distance. Aligned microticks OFF. BLOCKS lockstep is not the pass.
# Isolated workdir + port 25575. Does not touch the GUI, fleet, or lib/yap-folia-26.2.jar.
# Usage: ./scripts/smoke-contiguous-bar.sh [seconds]
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

WAIT_SECS="${1:-420}"
cd "$ROOT"
export ROOT
yap_require_java
yap_load_config

VER="${FOLIA_VERSION:-26.2}"
# Prefer a lab paperclip so we never mmap-overwrite the GUI's product jar.
if [ -n "${YAP_FOLIA_JAR:-}" ] && [ -f "$YAP_FOLIA_JAR" ]; then
  YAP_JAR_CAND="$YAP_FOLIA_JAR"
elif [ -f "$ROOT/lib/yap-folia-${VER}-lab.jar" ]; then
  YAP_JAR_CAND="$ROOT/lib/yap-folia-${VER}-lab.jar"
else
  YAP_JAR_CAND="$ROOT/lib/yap-folia-${VER}.jar"
fi
if [ ! -f "$YAP_JAR_CAND" ]; then
  echo "Need a YaP-Folia jar (lab or product)" >&2
  exit 1
fi

echo "Building MSPT bench plugin…"
gradle :bench-plugin:jar --no-daemon -q
YAP_JAR="$(yap_find_jar)"
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac
BENCH_JAR="$ROOT/yap-first-party/dev/bench-plugin/build/libs/yap-mspt-bench.jar"
if [ -z "$YAP_JAR" ] || [ ! -f "$YAP_JAR" ]; then
  echo "Missing yapcore jar" >&2
  exit 1
fi
if [ ! -f "$BENCH_JAR" ]; then
  echo "Missing $BENCH_JAR" >&2
  exit 1
fi

WORK="$ROOT/bench/workdir-contiguous-bar"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs" "$WORK/bench" "$WORK/folia-kernel/config"
cat >"$WORK/folia-kernel/config/paper-world-defaults.yml" <<'EOF'
_version: 31
chunks:
  delay-chunk-unloads-by: 0s
spawn:
  keep-spawn-loaded: false
  keep-spawn-loaded-range: 0
EOF
/bin/cp -f "$YAP_JAR_CAND" "$WORK/lib/yap-folia-${VER}.jar"
/bin/cp -f "$YAP_JAR_CAND" "$WORK/lib/folia-${VER}.jar"
/bin/cp -f "$BENCH_JAR" "$WORK/plugins/yap-mspt-bench.jar"
if [ -f "$ROOT/lib/yap-sched-agent.jar" ]; then
  /bin/cp -f "$ROOT/lib/yap-sched-agent.jar" "$WORK/lib/yap-sched-agent.jar"
fi

PORT="${YAP_CONTIGUOUS_BAR_PORT:-25575}"
cat >"$WORK/config/server.properties" <<EOF
server-name=YaP-Contiguous-Bar
bind-host=127.0.0.1
port=${PORT}
max-players=8
ram-mb=4096
gui-enabled=false
online-mode=false
java-enabled=true
bedrock-enabled=false
crossplay-enabled=false
protocol-via-enabled=false
protocol-geyser-enabled=false
game-authority=folia
folia-embed=true
folia-dir=folia-kernel
folia-port=${PORT}
folia-version=${VER}
folia-jar-source=build
folia-sched-compat=true
folia-teleport-transactions=true
folia-ready-timeout-sec=240
folia-async-chunk-save=true
folia-entity-tick-budget=400
folia-budget-mspt-threshold=12
folia-aligned-microticks=false
folia-region-metrics=true
folia-subregion-partition=true
folia-subregion-shards=2
folia-subregion-mspt-threshold=1
folia-subregion-mspt-clear=1
folia-subregion-min-sections=4
folia-subregion-min-entities=8
folia-subregion-partition-delay-ticks=20
folia-grid-exponent=3
folia-regionizer-cut=true
view-distance=10
simulation-distance=10
folia-subregion-carve=true
folia-scheduler-probe=true
velocity-enabled=false
web-dashboard-enabled=false
resource-pack-enabled=false
yap-ranks-auto-apply=false
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$WORK/contiguous-bar.log"
: >"$LOG"
echo "Booting contiguous-bar harness (timeout ${WAIT_SECS}s)…"
echo "  home=$WORK"
echo "  folia-jar=$YAP_JAR_CAND"
echo "  product VD/sim=10 grid-exponent=3 aligned-microticks=OFF"
echo "  contiguous_carve=true (no pre-gap). Pass = force+split+gap hold. Not BLOCKS lockstep."

(
  exec "$JAVA_BIN" -Xms1G -Xmx3G \
    -Dyapcore.home="$WORK" \
    -Dyap.folia.scheduler-probe=true \
    -Dyap.folia.scheduler-probe-file="$WORK/yap-scheduler-probe.txt" \
    -Dyap.folia.aligned-microticks=false \
    -Dyap.folia.subregion-carve-halo-sections=0 \
    -Dyap.folia.subregion-carve-landing-margin=0 \
    -Dyap.folia.subregion-carve-settle-ms=3000 \
    -Dyap.folia.subregion-carve-unload-rounds=8 \
    -Dyap.bench.scenario=partitioncut \
    -Dyap.bench.strip_half_width=32 \
    -Dyap.bench.strip_z_radius=1 \
    -Dyap.bench.strip_two_phase=false \
    -Dyap.bench.contiguous_carve=true \
    -Dyap.bench.strip_gap_half=0 \
    -Dyap.bench.gap_hold_sec=15 \
    -Dyap.bench.entities=200 \
    -Dyap.bench.hoppers=32 \
    -Dyap.bench.mobs=64 \
    -Dyap.bench.partition_wait_sec=240 \
    -jar "$YAP_JAR" --nogui
) >>"$LOG" 2>&1 &
PID=$!
echo "  pid=$PID"

RESULT="$WORK/bench/partition-cut.json"
FOLIA_RESULT="$WORK/folia-kernel/yap-partition-cut.json"
start_ts="$(date +%s)"
reason=""
while kill -0 "$PID" 2>/dev/null; do
  now="$(date +%s)"
  elapsed=$((now - start_ts))
  if [ "$elapsed" -ge "$WAIT_SECS" ]; then
    reason="timeout"
    break
  fi
  if [ -f "$RESULT" ] || [ -f "$FOLIA_RESULT" ]; then
    break
  fi
  sleep 1
done

echo "Stopping pid=${PID}..."
# Only this harness chassis. Never fuser/pkill fleet or the GUI.
kill "$PID" 2>/dev/null || true
wait "$PID" 2>/dev/null || true
sleep 3

PICK="$RESULT"
[ -f "$FOLIA_RESULT" ] && PICK="$FOLIA_RESULT"
[ -f "$RESULT" ] && PICK="$RESULT"

tick_err=0
if grep -Eqi 'Failed to apply|corrupt patch|NullPointerException: Cannot invoke' "$LOG" \
    "$WORK/folia-kernel/logs/latest.log" 2>/dev/null; then
  tick_err=1
fi

echo "---- probe ----"
cat "$WORK/yap-scheduler-probe.txt" 2>/dev/null \
  || cat "$WORK/folia-kernel/yap-scheduler-probe.txt" 2>/dev/null \
  || echo "(no probe file)"
echo "---- result ----"
cat "$PICK" 2>/dev/null || echo "(no result json)"
echo "  log=$LOG"

if [ "$tick_err" -eq 1 ]; then
  echo "FAIL: apply/NPE crash in logs" >&2
  tail -n 40 "$LOG" >&2 || true
  exit 1
fi

if [ ! -f "$PICK" ]; then
  echo "FAIL: contiguous-bar did not write result (${reason:-no-json}) within ${WAIT_SECS}s" >&2
  echo "---- tail $LOG ----" >&2
  tail -n 80 "$LOG" >&2 || true
  exit 1
fi

python3 - <<PY
import json,sys
p=json.load(open("$PICK"))
print("contiguous-bar pass=%s reason=%s splits=%s force=%s gap_bands=%s ticking_regions=%s contiguous=%s pulses_ran=%s" % (
    p.get("pass"), p.get("reason"), p.get("splits"), p.get("force_partitions"),
    p.get("gap_bands"), p.get("ticking_regions"), p.get("contiguous"), p.get("pulses_ran")))
ok = bool(p.get("pass")) and p.get("contiguous") is True and int(p.get("splits") or 0) >= 1 \
     and int(p.get("force_partitions") or 0) >= 1 and int(p.get("gap_bands") or 0) >= 1 \
     and int(p.get("ticking_regions") or 0) >= 2
sys.exit(0 if ok else 1)
PY
