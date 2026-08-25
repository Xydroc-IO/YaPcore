#!/usr/bin/env bash
# Smoke: boot YaPcore with game-authority=folia (managed Folia process).
# Usage: ./scripts/smoke-folia.sh [seconds]
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

WAIT_SECS="${1:-120}"
cd "$ROOT"
export ROOT
yap_require_java
yap_load_config

VER="${FOLIA_VERSION:-26.2}"
# Prefer yap-folia when present if caller did not pin source (soak / local dev).
if [ -z "${FOLIA_JAR_SOURCE:-${YAP_FOLIA_JAR_SOURCE:-}}" ] && [ -f "$ROOT/lib/yap-folia-${VER}.jar" ]; then
  FOLIA_SRC=build
  echo "folia-jar-source: auto → build (lib/yap-folia-${VER}.jar present)"
else
  FOLIA_SRC="${FOLIA_JAR_SOURCE:-${YAP_FOLIA_JAR_SOURCE:-fetch}}"
fi
YAP_JAR_CAND="$ROOT/lib/yap-folia-${VER}.jar"
STOCK_JAR_CAND="$ROOT/lib/folia-${VER}.jar"

if [ "$FOLIA_SRC" = "build" ]; then
  if [ ! -f "$YAP_JAR_CAND" ]; then
    echo "folia-jar-source=build requires $YAP_JAR_CAND — run ./scripts/build-yap-folia.sh" >&2
    exit 1
  fi
elif [ ! -f "$STOCK_JAR_CAND" ] && [ ! -f "$YAP_JAR_CAND" ]; then
  echo "Fetching Folia ${VER}…"
  "$ROOT/scripts/fetch-folia.sh" "$VER"
fi

echo "Building YaPcore…"
gradle shadowJar --no-daemon -q
YAP_JAR="$(yap_find_jar)"
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac
if [ -z "$YAP_JAR" ] || [ ! -f "$YAP_JAR" ]; then
  echo "Missing yapcore jar" >&2
  exit 1
fi

WORK="$ROOT/bench/workdir-folia-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs"
if [ "$FOLIA_SRC" = "build" ] || { [ "$FOLIA_SRC" = "auto" ] && [ -f "$YAP_JAR_CAND" ]; }; then
  /bin/cp -f "$YAP_JAR_CAND" "$WORK/lib/yap-folia-${VER}.jar"
  /bin/cp -f "$YAP_JAR_CAND" "$WORK/lib/folia-${VER}.jar"
else
  /bin/cp -f "$STOCK_JAR_CAND" "$WORK/lib/folia-${VER}.jar"
fi

SCHED_COMPAT="${FOLIA_SCHED_COMPAT:-true}"
TP_TX="${FOLIA_TELEPORT_TRANSACTIONS:-true}"
PORT=25575
cat >"$WORK/config/server.properties" <<EOF
server-name=YaP-Folia-Smoke
bind-host=127.0.0.1
port=${PORT}
max-players=20
ram-mb=2048
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
folia-jar-source=${FOLIA_SRC}
folia-sched-compat=${SCHED_COMPAT}
folia-teleport-transactions=${TP_TX}
folia-ready-timeout-sec=180
velocity-enabled=false
web-dashboard-enabled=false
resource-pack-enabled=false
yap-ranks-auto-apply=false
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$WORK/smoke.log"
: >"$LOG"
echo "Booting Folia smoke (timeout ${WAIT_SECS}s)…"
echo "  home=$WORK"
echo "  java=$JAVA_BIN"
echo "  jar=$YAP_JAR"
echo "  port=$PORT"
echo "  folia-jar-source=$FOLIA_SRC sched-compat=$SCHED_COMPAT teleport=$TP_TX"

# Optional A3 perf knobs forwarded into chassis → FoliaKernel → Folia JVM
EXTRA_D=()
if [ -n "${YAP_FOLIA_ENTITY_TICK_BUDGET:-}" ]; then
  EXTRA_D+=("-Dyap.folia.entity-tick-budget=${YAP_FOLIA_ENTITY_TICK_BUDGET}")
fi
if [ -n "${YAP_FOLIA_ASYNC_CHUNK_SAVE:-}" ]; then
  EXTRA_D+=("-Dyap.folia.async-chunk-save=${YAP_FOLIA_ASYNC_CHUNK_SAVE}")
fi
if [ "${YAP_FOLIA_SOAK_PROFILE:-}" = "compat" ]; then
  # Compat soak: never enable perf knobs even if env leaked
  EXTRA_D=()
fi

(
  exec "$JAVA_BIN" -Xms512M -Xmx1536M \
    -Dyapcore.home="$WORK" \
    "${EXTRA_D[@]}" \
    -jar "$YAP_JAR" --nogui
) >>"$LOG" 2>&1 &
PID=$!
echo "  pid=$PID"

start_ts="$(date +%s)"
ok=0
ready_ts=0
while kill -0 "$PID" 2>/dev/null; do
  now="$(date +%s)"
  elapsed=$((now - start_ts))
  if [ "$elapsed" -ge "$WAIT_SECS" ]; then
    break
  fi
  if [ "$ok" -eq 0 ]; then
    if grep -q 'Managed Folia online' "$LOG" 2>/dev/null \
      && { [ -f "$WORK/folia-kernel/yap-folia-ready.marker" ] || grep -q '\[folia\].*Done (' "$LOG" 2>/dev/null; }; then
      if "$JAVA_BIN" -e 'try(var s=new java.net.Socket()){s.connect(new java.net.InetSocketAddress("127.0.0.1",'"$PORT"'),1500);System.exit(0);}catch(Exception e){System.exit(1);}' 2>/dev/null \
        || (exec 3<>/dev/tcp/127.0.0.1/"$PORT") 2>/dev/null; then
        ok=1
        ready_ts="$now"
        if [ "${YAP_FOLIA_SOAK:-0}" = "1" ]; then
          echo "Ready at t=${elapsed}s — holding for soak (${WAIT_SECS}s total)…"
        else
          break
        fi
      fi
    fi
  elif [ "${YAP_FOLIA_SOAK:-0}" = "1" ]; then
    # Soak: stay up; fail early if Folia dies after ready
    if ! kill -0 "$PID" 2>/dev/null; then
      ok=0
      break
    fi
  fi
  sleep 1
done

echo "Stopping pid=$PID…"
kill "$PID" 2>/dev/null || true
# Folia child may outlive parent briefly
pkill -f "folia-${VER}.jar" 2>/dev/null || true
wait "$PID" 2>/dev/null || true
sleep 2

if [ "$ok" -eq 1 ]; then
  if [ "${YAP_FOLIA_SOAK:-0}" = "1" ]; then
    hold=$(( $(date +%s) - ready_ts ))
    echo "PASS: Folia soak held ready ~${hold}s (window ${WAIT_SECS}s) on :${PORT}"
  else
    echo "PASS: Folia managed process became ready on :${PORT}"
  fi
  echo "  log=$LOG"
  exit 0
fi
echo "FAIL: Folia smoke did not become ready within ${WAIT_SECS}s" >&2
echo "---- tail $LOG ----" >&2
tail -n 80 "$LOG" >&2 || true
exit 1
