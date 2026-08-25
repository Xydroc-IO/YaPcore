#!/usr/bin/env bash
# Launch YaPcore control GUI (foreground).
# Usage: ./scripts/gui.sh [--no-build]
# In a release package (no Gradle), uses the shipped yapcore.jar.

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/../build.gradle.kts" ] \
  || [ -f "$SCRIPT_DIR/../config/server.properties" ] \
  || [ -f "$SCRIPT_DIR/../yapcore.jar" ]; then
  ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
else
  ROOT="$(pwd)"
fi
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

# Release trees ship yapcore.jar and usually have no Gradle — skip rebuild.
if [ ! -f "$ROOT/build.gradle.kts" ]; then
  SKIP_BUILD=1
fi

if [ "$SKIP_BUILD" -eq 0 ]; then
  echo "Rebuilding yapcore.jar so GUI tabs (Connect / Access / Settings) are current…"
  yap_build
  JAR="$(yap_find_jar)"
  if [ -z "$JAR" ]; then
    echo "Build produced no jar" >&2
    exit 1
  fi
  if [ -f "$ROOT/build/libs/yapcore-0.1.0.jar" ]; then
    cp -f "$ROOT/build/libs/yapcore-0.1.0.jar" "$ROOT/yapcore.jar"
  elif [ -f "$JAR" ] && [ "$JAR" != "$ROOT/yapcore.jar" ]; then
    cp -f "$JAR" "$ROOT/yapcore.jar"
  fi
  echo "Jar ready: $ROOT/yapcore.jar"
elif [ ! -f "$ROOT/yapcore.jar" ]; then
  echo "No yapcore.jar in $ROOT — cannot launch GUI." >&2
  exit 1
fi

# Use bash so release zips that lost +x still launch (Ant zip historically stored 0644).
exec bash "$SCRIPT_DIR/start.sh" --gui --fg
