#!/usr/bin/env bash
# YaPcore stop — graceful then forceful if needed
# Usage: ./scripts/stop.sh [--force]
# Safe to double-click / "Run in Konsole" from Dolphin (pauses at end).

# Re-exec under bash if someone invoked us with sh/dash (Dolphin quirk).
if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/../build.gradle.kts" ] || [ -f "$SCRIPT_DIR/../config/server.properties" ]; then
  ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
else
  ROOT="$(pwd)"
fi
cd "$ROOT"

# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"

FORCE=0
for arg in "$@"; do
  case "$arg" in
    --force|-f) FORCE=1 ;;
    -h|--help)
      echo "Usage: $0 [--force]"
      exit 0
      ;;
  esac
done

PID_FILE="$ROOT/yapcore.pid"
mapfile -t PIDS < <(yap_find_pids || true)

if [ "${#PIDS[@]}" -eq 0 ] || [ -z "${PIDS[0]:-}" ]; then
  echo "YaPcore does not appear to be running."
  rm -f "$PID_FILE"
  yap_pause_end 0
  exit 0
fi

echo "Stopping YaPcore (pid ${PIDS[*]})…"

signal_all() {
  local sig="$1" pid
  for pid in "${PIDS[@]}"; do
    [ -n "$pid" ] || continue
    kill "-$sig" "$pid" 2>/dev/null || true
  done
}

any_alive() {
  local pid
  for pid in "${PIDS[@]}"; do
    [ -n "$pid" ] || continue
    if kill -0 "$pid" 2>/dev/null; then
      return 0
    fi
  done
  return 1
}

if [ "$FORCE" -eq 1 ]; then
  signal_all 9
else
  signal_all TERM
  i=0
  while any_alive; do
    i=$((i + 1))
    if [ "$i" -ge 40 ]; then
      echo "Graceful stop timed out; sending SIGKILL"
      signal_all 9
      break
    fi
    sleep 0.5
  done
fi

# Catch stragglers (second instance, pid-file race, etc.)
sleep 0.2
mapfile -t LEFTOVER < <(yap_find_pids || true)
if [ "${#LEFTOVER[@]}" -gt 0 ] && [ -n "${LEFTOVER[0]:-}" ]; then
  echo "Killing leftover YaPcore pid(s): ${LEFTOVER[*]}"
  for pid in "${LEFTOVER[@]}"; do
    kill -9 "$pid" 2>/dev/null || true
  done
  sleep 0.2
fi

rm -f "$PID_FILE"

if [ -n "$(yap_find_pids | head -n 1)" ]; then
  echo "YaPcore still appears to be running after stop." >&2
  yap_pause_end 1
  exit 1
fi

echo "YaPcore stopped."
yap_pause_end 0
exit 0
