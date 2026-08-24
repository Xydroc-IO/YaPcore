#!/usr/bin/env bash
# Verify YaP-Folia build + Folia product smoke with folia-jar-source=build.
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

VER="$(grep -E '^MC_VERSION=' "$ROOT/vendor/folia/UPSTREAM.lock" | cut -d= -f2-)"
VER="${VER:-26.2}"

echo "== patch check =="
"$ROOT/scripts/vendor-folia.sh"
git -C "$ROOT/vendor/folia/work" reset --hard "$(grep -E '^COMMIT=' "$ROOT/vendor/folia/UPSTREAM.lock" | cut -d= -f2-)"
"$ROOT/scripts/folia-patch.sh" --check
git -C "$ROOT/vendor/folia/work" reset --hard "$(grep -E '^COMMIT=' "$ROOT/vendor/folia/UPSTREAM.lock" | cut -d= -f2-)"

echo "== build yap-folia =="
"$ROOT/scripts/build-yap-folia.sh"

YAP_JAR="$ROOT/lib/yap-folia-${VER}.jar"
if [ ! -f "$YAP_JAR" ]; then
  echo "FAIL: missing $YAP_JAR" >&2
  exit 1
fi
echo "OK jar $(wc -c <"$YAP_JAR" | tr -d ' ') bytes"

if [ "${SKIP_SMOKE:-0}" = "1" ]; then
  echo "SKIP_SMOKE=1 — verify-yap-folia PASS (build only)"
  exit 0
fi

echo "== smoke-folia with folia-jar-source=build =="
export FOLIA_JAR_SOURCE=build
export YAP_FOLIA_JAR_SOURCE=build
"$ROOT/scripts/smoke-folia.sh"

echo "verify-yap-folia PASS"
