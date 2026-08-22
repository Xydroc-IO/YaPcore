#!/usr/bin/env bash
# YaPcore start — portable across common Linux distros (bash 3.2+)
# Usage: ./scripts/start.sh [--nogui|--gui] [--fg]
# JVM: Generational ZGC + NUMA via scripts/lib.sh (config/server.properties)
# Folia product path: YaP stays at $ROOT; Folia runs as a child JVM with cwd=folia-kernel.

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# Prefer project root when invoked from scripts/, else cwd
# Works for full checkouts (build.gradle.kts) and release packages (yapcore.jar + config/).
if [ -f "$SCRIPT_DIR/../build.gradle.kts" ] \
  || [ -f "$SCRIPT_DIR/../config/server.properties" ] \
  || [ -f "$SCRIPT_DIR/../yapcore.jar" ]; then
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
      echo "  Product path: game-authority=folia (Folia child JVM under folia-dir)"
      echo "  YaP process cwd stays at project root; Folia cwd is folia-kernel"
      exit 0
      ;;
  esac
done

yap_require_java
yap_load_config
yap_apply_ulimits
yap_ensure_dirs
yap_build_jvm_opts
yap_filter_jvm_opts
yap_numa_prefix

if yap_is_running; then
  RUN_PID="$(yap_find_product_pid)"
  echo "YaPcore is already running for this install (pid ${RUN_PID:-?}, home=$ROOT)." >&2
  echo "Use scripts/stop.sh first. (Bench runs under bench/workdir-* do not block start/gui.)" >&2
  exit 1
fi
BENCH_PID="$(yap_find_bench_pids | head -n 1 || true)"
if [ -n "$BENCH_PID" ]; then
  echo "Note: MSPT bench JVM still running (pid $BENCH_PID) — product start/gui is allowed." >&2
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

mkdir -p "$ROOT/logs"
LOG_FILE="$ROOT/logs/server.log"
PID_FILE="$ROOT/yapcore.pid"

echo "Starting YaPcore"
echo "  home=$ROOT"
echo "  cwd=$(pwd)"
echo "  game-authority=${GAME_AUTHORITY:-folia}"
echo "  folia-dir=${FOLIA_DIR:-folia-kernel}"
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
  # Keep stdin open: Folia/JLine EOF on /dev/null shuts the dedicated server down.
  STDIN_KEEPALIVE="$ROOT/logs/yap-stdin.keepalive"
  rm -f "$STDIN_KEEPALIVE"
  mkfifo "$STDIN_KEEPALIVE"
  setsid bash -c "exec tail -f /dev/null >\"$STDIN_KEEPALIVE\"" </dev/null >/dev/null 2>&1 &
  echo $! >"$ROOT/logs/yap-stdin.keepalive.pid"
  # New session; echo real JVM/shell-exec PID into pidfile (plain `setsid cmd &` stores a fleeting pid).
  setsid bash -c '
    echo $$ > "$1"
    shift
    logfile=$1; shift
    fifopath=$1; shift
    exec "$@" >>"$logfile" 2>&1 <"$fifopath"
  ' bash "$PID_FILE" "$LOG_FILE" "$STDIN_KEEPALIVE" \
    "${NUMA_PREFIX[@]}" "$JAVA_BIN" "${JVM_OPTS[@]}" -jar "$JAR" "${APP_ARGS[@]}" \
    </dev/null >/dev/null 2>&1 &
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if [ -s "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      break
    fi
    sleep 0.2
  done
  echo "Started in background pid=$(cat "$PID_FILE" 2>/dev/null || echo '?')  log=$LOG_FILE"
  echo "Stop with: $ROOT/scripts/stop.sh"
fi
