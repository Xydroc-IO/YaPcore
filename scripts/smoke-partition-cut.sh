#!/usr/bin/env bash
# Lab scheduler proof: force-partition + 0032 split + BLOCKS RTQ pulse.
# Isolated workdir + port 25677. Does not touch the GUI or fleet instances.
# Lab knobs only — does not change ship defaults.
# Usage: ./scripts/smoke-partition-cut.sh [seconds]
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

WAIT_SECS="${1:-360}"
cd "$ROOT"
export ROOT
yap_require_java
yap_load_config

VER="${FOLIA_VERSION:-26.2}"
YAP_JAR_CAND="$ROOT/lib/yap-folia-${VER}.jar"
if [ ! -f "$YAP_JAR_CAND" ]; then
  echo "Need $YAP_JAR_CAND — run ./scripts/build-yap-folia.sh" >&2
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

WORK="$ROOT/bench/workdir-partition-cut"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs" "$WORK/bench" "$WORK/folia-kernel/config"
# First boot would default delay-chunk-unloads-by=10s, which outlasts 0018's carve settle.
mkdir -p "$WORK/folia-kernel/config"
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

PORT="${YAP_PARTITION_CUT_PORT:-25677}"
# Lab thresholds only (ship remains 20/16/32/600). Default grid-exponent (16-chunk
# sections): carve landing pads need strip half-width ≳40.
cat >"$WORK/config/server.properties" <<EOF
server-name=YaP-Partition-Cut
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
folia-aligned-microticks=true
folia-micro-phases=4
folia-tick-wave-max-wait-ms=2
folia-region-metrics=true
folia-subregion-partition=true
folia-subregion-shards=2
folia-subregion-mspt-threshold=1
folia-subregion-mspt-clear=1
folia-subregion-min-sections=4
folia-subregion-min-entities=8
folia-subregion-partition-delay-ticks=20
view-distance=2
simulation-distance=2
folia-subregion-carve=true
folia-scheduler-probe=true
velocity-enabled=false
web-dashboard-enabled=false
resource-pack-enabled=false
yap-ranks-auto-apply=false
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$WORK/partition-cut.log"
: >"$LOG"
echo "Booting partition-cut harness (timeout ${WAIT_SECS}s)…"
echo "  home=$WORK"
echo "  lab knobs: mspt-threshold=1 min-entities=8 delay-ticks=20 stripHalf=64 gapHalf=32 (not ship)"
echo "  0018 strip-then-wait + Moonrise processUnloads (no corridor RTQ pin)"
echo "  probe + BLOCKS pulse"

(
  exec "$JAVA_BIN" -Xms512M -Xmx1536M \
    -Dyapcore.home="$WORK" \
    -Dyap.folia.scheduler-probe=true \
    -Dyap.folia.scheduler-probe-file="$WORK/yap-scheduler-probe.txt" \
    -Dyap.folia.subregion-carve-halo-sections=0 \
    -Dyap.folia.subregion-carve-landing-margin=0 \
    -Dyap.folia.subregion-carve-settle-ms=3000 \
    -Dyap.folia.subregion-carve-unload-rounds=8 \
    -Dyap.bench.scenario=partitioncut \
    -Dyap.bench.strip_half_width=64 \
    -Dyap.bench.strip_z_radius=1 \
    -Dyap.bench.strip_two_phase=true \
    -Dyap.bench.contiguous_carve=false \
    -Dyap.bench.strip_gap_half=32 \
    -Dyap.bench.entities=200 \
    -Dyap.bench.hoppers=64 \
    -Dyap.bench.mobs=64 \
    -Dyap.bench.partition_wait_sec=180 \
    -jar "$YAP_JAR" --nogui
) >>"$LOG" 2>&1 &
PID=$!
echo "  pid=$PID"

RESULT="$WORK/bench/partition-cut.json"
FOLIA_RESULT="$WORK/folia-kernel/yap-partition-cut.json"
start_ts="$(date +%s)"
ok=0
reason=""
while kill -0 "$PID" 2>/dev/null; do
  now="$(date +%s)"
  elapsed=$((now - start_ts))
  if [ "$elapsed" -ge "$WAIT_SECS" ]; then
    reason="timeout"
    break
  fi
  if [ -f "$RESULT" ] || [ -f "$FOLIA_RESULT" ]; then
    ok=1
    break
  fi
  sleep 1
done

echo "Stopping pid=$PID…"
# Only this harness chassis. Never fuser/pkill fleet or the GUI.
kill "$PID" 2>/dev/null || true
wait "$PID" 2>/dev/null || true
sleep 2

PICK="$RESULT"
[ -f "$FOLIA_RESULT" ] && PICK="$FOLIA_RESULT"
[ -f "$RESULT" ] && PICK="$RESULT"

tick_err=0
# Fixture fan-out logs TickThread on purpose (far chunks). Fail only on split/NPE crashes.
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
  echo "FAIL: partition-cut did not write result (${reason:-no-json}) within ${WAIT_SECS}s" >&2
  echo "---- tail $LOG ----" >&2
  tail -n 80 "$LOG" >&2 || true
  exit 1
fi

python3 - <<PY
import json,sys
p=json.load(open("$PICK"))
print("partition-cut pass=%s reason=%s splits=%s force=%s pulses_ran=%s slipped_delta=%s" % (
    p.get("pass"), p.get("reason"), p.get("splits"), p.get("force_partitions"),
    p.get("pulses_ran"), p.get("slipped_delta")))
sys.exit(0 if p.get("pass") else 1)
PY
