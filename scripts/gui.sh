#!/usr/bin/env bash
# Launch YaPcore control GUI (foreground).
# Usage: ./scripts/gui.sh [--no-build|--build]
# Default: use existing yapcore.jar (fast). Pass --build to rebuild first.
# In a release package (no Gradle), uses the shipped yapcore.jar.

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# Resolve install root from script location (cwd-independent).
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
if [ ! -f "$ROOT/build.gradle.kts" ] \
  && [ ! -f "$ROOT/config/server.properties" ] \
  && [ ! -f "$ROOT/yapcore.jar" ]; then
  # Symlinked / relocated scripts/ — walk up from cwd as last resort.
  CAND="$(pwd)"
  FOUND=""
  for _ in 1 2 3 4 5 6 7 8; do
    if [ -f "$CAND/build.gradle.kts" ] \
      || [ -f "$CAND/config/server.properties" ] \
      || [ -f "$CAND/yapcore.jar" ]; then
      FOUND="$CAND"
      break
    fi
    PARENT="$(dirname -- "$CAND")"
    [ "$PARENT" = "$CAND" ] && break
    CAND="$PARENT"
  done
  if [ -n "$FOUND" ]; then
    ROOT="$FOUND"
  fi
fi
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"

SKIP_BUILD=1
for arg in "$@"; do
  case "$arg" in
    --no-build) SKIP_BUILD=1 ;;
    --build) SKIP_BUILD=0 ;;
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
  echo "No yapcore.jar in $ROOT — cannot launch GUI. Run: $0 --build" >&2
  exit 1
fi

echo "YaPcore home: $ROOT (GUI, no rebuild)"
# Use bash so release zips that lost +x still launch (Ant zip historically stored 0644).
exec bash "$SCRIPT_DIR/start.sh" --gui --fg
