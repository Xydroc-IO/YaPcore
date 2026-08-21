#!/usr/bin/env bash
# YapLabs production launcher — Generational ZGC + NUMA node bind.
# Equivalent to:
#   numactl --cpunodebind=0 --membind=0 java \
#     -Xms12G -Xmx12G -XX:+UseZGC \
#     -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions \
#     -XX:ThreadPriorityPolicy=1 -XX:+UseNUMA \
#     -jar yapengine.jar
#
# Heap defaults to 12G for this profile; override with config or:
#   YAPCORE_PROD_HEAP_GB=16 ./scripts/start-prod.sh
#
# Usage: ./scripts/start-prod.sh [--nogui|--gui] [--fg] [--heap-gb N]

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/../build.gradle.kts" ] || [ -f "$SCRIPT_DIR/../config/server.properties" ]; then
  ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
else
  ROOT="$(pwd)"
fi

cd "$ROOT"
export YAPCORE_HOME="$ROOT"

# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"

MODE="nogui"
FOREGROUND=0
HEAP_GB="${YAPCORE_PROD_HEAP_GB:-12}"

for arg in "$@"; do
  case "$arg" in
    --gui|-gui) MODE="gui" ;;
    --nogui|-nogui) MODE="nogui" ;;
    --fg|--foreground) FOREGROUND=1 ;;
    --heap-gb=*) HEAP_GB="${arg#*=}" ;;
    --heap-gb)
      echo "Use --heap-gb=N" >&2
      exit 2
      ;;
    -h|--help)
      echo "Usage: $0 [--gui|--nogui] [--fg] [--heap-gb=N]"
      echo "  Production Generational ZGC + numactl node bind (default heap ${HEAP_GB}G)"
      exit 0
      ;;
  esac
done

yap_require_java
yap_load_config

# Production profile overrides — pinned equal heap, ZGC, NUMA on node 0
HEAP_GB="$(echo "$HEAP_GB" | tr -cd '0-9')"
[ -n "$HEAP_GB" ] || HEAP_GB=12
RAM_MB=$((HEAP_GB * 1024))
RAM_MIN_MB="$RAM_MB"
JVM_GC=zgc
JVM_NUMA=true
JVM_HEAP_PIN=true
JVM_NUMA_NODE="${JVM_NUMA_NODE:-0}"
JVM_THREAD_PRIORITY=true

yap_apply_ulimits
yap_ensure_dirs
yap_build_jvm_opts
yap_filter_jvm_opts
yap_numa_prefix

if yap_is_running; then
  echo "YaPcore is already running (pid $(yap_read_pid)). Use scripts/stop.sh first." >&2
  exit 1
fi

JAR="$(yap_find_jar)"
if [ -z "$JAR" ]; then
  echo "No yapcore jar found. Building…"
  yap_build
  JAR="$(yap_find_jar)"
fi
if [ -z "$JAR" ]; then
  echo "Build failed: jar still missing under build/libs/" >&2
  exit 1
fi

JAVA_BIN="$(yap_java_bin)"
APP_ARGS=(--nogui)
[ "$MODE" = "gui" ] && APP_ARGS=(--gui)

mkdir -p "$ROOT/logs"
LOG_FILE="$ROOT/logs/server-prod.log"
PID_FILE="$ROOT/yapcore.pid"

echo "=== YapLabs production (Generational ZGC + NUMA) ==="
echo "  heap=${RAM_MB}m (pinned)"
echo "  flags: ${JVM_OPTS[*]}"
if [ "${#NUMA_PREFIX[@]}" -gt 0 ]; then
  echo "  wrap: ${NUMA_PREFIX[*]}"
else
  echo "  wrap: (no numactl — install for --cpunodebind/--membind)"
fi

if [ "$FOREGROUND" -eq 1 ] || [ "$MODE" = "gui" ]; then
  exec "${NUMA_PREFIX[@]}" "$JAVA_BIN" "${JVM_OPTS[@]}" -jar "$JAR" "${APP_ARGS[@]}"
else
  nohup "${NUMA_PREFIX[@]}" "$JAVA_BIN" "${JVM_OPTS[@]}" -jar "$JAR" "${APP_ARGS[@]}" \
    >>"$LOG_FILE" 2>&1 < /dev/null &
  echo $! >"$PID_FILE"
  echo "Started production pid=$(cat "$PID_FILE")  log=$LOG_FILE"
fi
