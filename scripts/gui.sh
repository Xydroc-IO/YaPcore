#!/usr/bin/env bash
# Launch YaPcore control GUI (foreground), rebuilding jar so UI changes are visible.
# Usage: ./scripts/gui.sh [--no-build]

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"

SKIP_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --no-build) SKIP_BUILD=1 ;;
  esac
done

cd "$ROOT"
export YAPCORE_HOME="$ROOT"

if [ "$SKIP_BUILD" -eq 0 ]; then
  echo "Rebuilding yapcore.jar so GUI tabs (Connect / Access / Settings) are current…"
  yap_build
  JAR="$(yap_find_jar)"
  if [ -z "$JAR" ]; then
    echo "Build produced no jar" >&2
    exit 1
  fi
  # Refresh root copy used by start.sh
  if [ -f "$ROOT/build/libs/yapcore-0.1.0.jar" ]; then
    cp -f "$ROOT/build/libs/yapcore-0.1.0.jar" "$ROOT/yapcore.jar"
  elif [ -f "$JAR" ] && [ "$JAR" != "$ROOT/yapcore.jar" ]; then
    cp -f "$JAR" "$ROOT/yapcore.jar"
  fi
  echo "Jar ready: $ROOT/yapcore.jar"
fi

exec "$SCRIPT_DIR/start.sh" --gui --fg
