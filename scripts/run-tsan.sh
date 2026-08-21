#!/usr/bin/env bash
# Run YaPcore under experimental OpenJDK ThreadSanitizer (if available).
# Download/build a tsan early-access JDK, then:
#   YAP_TSAN_JAVA=/path/to/jdk-tsan/bin/java ./scripts/run-tsan.sh
#
# TSan logs happens-before violations across Java + JNI.

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"
export YAPCORE_HOME="$ROOT"

# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"

TSAN_JAVA="${YAP_TSAN_JAVA:-}"
if [ -z "$TSAN_JAVA" ] || [ ! -x "$TSAN_JAVA" ]; then
  echo "Set YAP_TSAN_JAVA to an OpenJDK build with -XX:+ThreadSanitizer support."
  echo "Example: YAP_TSAN_JAVA=./jdk-tsan/bin/java $0"
  exit 1
fi

if [ ! -f "$ROOT/yapcore.jar" ]; then
  gradle distJar
fi

echo "Running under ThreadSanitizer: $TSAN_JAVA"
exec "$TSAN_JAVA" -XX:+ThreadSanitizer -jar "$ROOT/yapcore.jar" --nogui "$@"
