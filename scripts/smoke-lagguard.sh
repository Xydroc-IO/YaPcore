#!/usr/bin/env bash
# Smoke: compile + install YaPLagGuard (no live Folia required).
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

echo "== LagGuard compile + install =="
gradle :yap-lagguard-api:jar :lagguard-plugin:installIntoPlugins --no-daemon -q

INSTALL="$ROOT/server"
[ -d "$INSTALL" ] || INSTALL="$ROOT"
JAR="$INSTALL/plugins/yap-lagguard.jar"
if [ ! -f "$JAR" ]; then
  echo "FAIL: missing $JAR"
  exit 1
fi
echo "OK: $JAR ($(wc -c <"$JAR") bytes)"
echo "LagGuard smoke PASS"
