#!/usr/bin/env bash
# Launch YaPcore control GUI (foreground).
# Usage: ./scripts/gui.sh [--build|--no-build]
# Fast path (default): use existing yapcore.jar and open the panel immediately.
# Pass --build after Control GUI / chassis source changes (full shadowJar — can take minutes).

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# Resolve install root from script location (cwd-independent).
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
if [ ! -f "$ROOT/build.gradle.kts" ] \
  && [ ! -f "$ROOT/config/server.properties" ] \
  && [ ! -f "$ROOT/yapcore.jar" ]; then
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

# Default: no rebuild — waiting on Gradle every click is unacceptable for ops.
SKIP_BUILD=1
FORCE_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --no-build) SKIP_BUILD=1; FORCE_BUILD=0 ;;
    --build) SKIP_BUILD=0; FORCE_BUILD=1 ;;
  esac
done

cd "$ROOT"
export YAPCORE_HOME="$ROOT"

yap_refresh_root_jar() {
  JAR="$(yap_find_built_jar 2>/dev/null || true)"
  if [ -z "${JAR:-}" ]; then
    JAR="$(ls -1 "$ROOT/build/libs"/yapcore-*.jar 2>/dev/null | grep -v -- '-plain' | tail -n 1 || true)"
  fi
  if [ -z "${JAR:-}" ] || [ ! -f "$JAR" ]; then
    return 1
  fi
  cp -f "$JAR" "$ROOT/yapcore.jar"
  echo "Updated $ROOT/yapcore.jar ← $JAR"
}

if [ "$FORCE_BUILD" -eq 1 ] || [ ! -f "$ROOT/yapcore.jar" ]; then
  if [ ! -f "$ROOT/build.gradle.kts" ] && [ ! -f "$ROOT/yapcore.jar" ]; then
    echo "No yapcore.jar in $ROOT and no Gradle checkout to build from." >&2
    exit 1
  fi
  if [ ! -f "$ROOT/yapcore.jar" ]; then
    echo "No yapcore.jar — building once…"
  else
    echo "Rebuilding yapcore.jar (--build)…"
  fi
  yap_build
  if ! yap_refresh_root_jar; then
    echo "Build produced no jar under build/libs/" >&2
    exit 1
  fi
else
  echo "Using existing yapcore.jar (fast launch). Rebuild with: $0 --build"
fi

echo "YaPcore home: $ROOT (GUI)"
mkdir -p "$ROOT/logs"
# shellcheck disable=SC2094
exec > >(tee -a "$ROOT/logs/start-gui.out") 2>&1
exec bash "$SCRIPT_DIR/start.sh" --gui --fg
