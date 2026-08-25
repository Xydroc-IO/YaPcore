#!/usr/bin/env bash
# Smoke: YaPLagGuard compile + install (+ optional live Folia boot).
# Usage:
#   ./scripts/smoke-lagguard.sh           # compile/install (default)
#   SKIP_LIVE=1 ./scripts/smoke-lagguard.sh
#   LIVE=1 ./scripts/smoke-lagguard.sh    # also runs smoke-folia.sh
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

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
(cd "$TMP" && jar xf "$JAR" plugin.yml config.yml)
grep -q 'folia-supported: true' "$TMP/plugin.yml" || { echo "FAIL: folia-supported"; exit 1; }
grep -q 'max-entities-per-chunk' "$TMP/config.yml" || { echo "FAIL: config.yml incomplete"; exit 1; }
echo "OK: plugin.yml folia-supported + survival config defaults present"

if [ "${LIVE:-0}" != "1" ] || [ "${SKIP_LIVE:-0}" = "1" ]; then
  echo "LagGuard smoke PASS (compile). LIVE=1 for Folia boot."
  exit 0
fi

echo "== Optional live Folia =="
"$ROOT/scripts/smoke-folia.sh" 60
echo "LagGuard smoke PASS (compile + live Folia)"
