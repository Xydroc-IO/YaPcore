#!/usr/bin/env bash
# YaPcore start — portable across common Linux distros (bash 3.2+)
# Usage: ./scripts/start.sh [--nogui|--gui] [--fg]
# JVM: Generational ZGC + NUMA via scripts/lib.sh (config/server.properties)
# Phase 3: cwd must be paper-dir (Paperclip Path cwd is fixed at JVM start).

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# Prefer project root when invoked from scripts/, else cwd
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
for arg in "$@"; do
  case "$arg" in
    --gui|-gui) MODE="gui" ;;
    --nogui|-nogui) MODE="nogui" ;;
    --fg|--foreground) FOREGROUND=1 ;;
    -h|--help)
      echo "Usage: $0 [--gui|--nogui] [--fg]"
      echo "  Starts YaPcore with Generational ZGC / NUMA flags from config/server.properties"
      echo "  Phase 3 (paper-phase3-tick-bridge): cds into paper-dir before java"
      echo "  Phase 3 NMS (paper-phase3-nms-tick): requires lib/paper-*-yap.jar"
      exit 0
      ;;
  esac
done

yap_require_java
yap_load_config
yap_apply_ulimits
yap_ensure_dirs
yap_require_yap_paperclip
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
# Absolute jar path — Phase 3 may chdir into paper-kernel
case "$JAR" in
  /*) ;;
  *) JAR="$ROOT/$JAR" ;;
esac

JAVA_BIN="$(yap_java_bin)"
APP_ARGS=()
if [ "$MODE" = "nogui" ]; then
  APP_ARGS+=(--nogui)
else
  APP_ARGS+=(--gui)
fi

# Phase 3 same-JVM Paperclip requires process cwd == paper-dir
PHASE3_CWD=0
if [ "$GAME_AUTHORITY" = "paper" ] \
  && { [ "$PAPER_EMBED" = "true" ] || [ "$PAPER_EMBED" = "1" ] || [ "$PAPER_EMBED" = "yes" ]; } \
  && { [ "$PAPER_PHASE3" = "true" ] || [ "$PAPER_PHASE3" = "1" ] || [ "$PAPER_PHASE3" = "yes" ]; }; then
  mkdir -p "$ROOT/$PAPER_DIR"
  cd "$ROOT/$PAPER_DIR"
  PHASE3_CWD=1
fi

mkdir -p "$ROOT/logs"
LOG_FILE="$ROOT/logs/server.log"
PID_FILE="$ROOT/yapcore.pid"

echo "Starting YaPcore"
echo "  home=$ROOT"
echo "  cwd=$(pwd)"
if [ "$PHASE3_CWD" -eq 1 ]; then
  echo "  phase3=true (cwd=paper-dir for Paperclip)"
fi
echo "  java=$JAVA_BIN"
echo "  jar=$JAR"
echo "  heap=${RAM_MIN_MB}m–${RAM_MB}m (pin=$JVM_HEAP_PIN)"
echo "  gc=$JVM_GC  numa=$JVM_NUMA node=$JVM_NUMA_NODE"
echo "  max-players=${MAX_PLAYERS}"
echo "  mode=$MODE"
echo "  jvm: ${JVM_OPTS[*]}"
if [ "${#NUMA_PREFIX[@]}" -gt 0 ]; then
  echo "  numactl: ${NUMA_PREFIX[*]}"
fi

if [ "$FOREGROUND" -eq 1 ] || [ "$MODE" = "gui" ]; then
  exec "${NUMA_PREFIX[@]}" "$JAVA_BIN" "${JVM_OPTS[@]}" -jar "$JAR" "${APP_ARGS[@]}"
else
  nohup "${NUMA_PREFIX[@]}" "$JAVA_BIN" "${JVM_OPTS[@]}" -jar "$JAR" "${APP_ARGS[@]}" \
    >>"$LOG_FILE" 2>&1 < /dev/null &
  echo $! >"$PID_FILE"
  echo "Started in background pid=$(cat "$PID_FILE")  log=$LOG_FILE"
  echo "Stop with: $ROOT/scripts/stop.sh"
fi
