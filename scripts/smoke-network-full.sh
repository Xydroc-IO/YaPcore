#!/usr/bin/env bash
# Phase 17: full network release gate — build + core smokes + artifact summary.
# Usage: ./scripts/smoke-network-full.sh
#   FAST=1  — skip bedrock play live boot (unit tests only for phase 15)
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

cd "$ROOT"
export ROOT
mkdir -p "$ROOT/build"
OUT="$ROOT/build/smoke-network-full-latest.json"
START_TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
PASS=0
FAIL=0
STEPS=()

step() {
  local name="$1"
  shift
  echo "== $name =="
  if "$@"; then
    STEPS+=("\"$name\":true")
    PASS=$((PASS + 1))
  else
    STEPS+=("\"$name\":false")
    FAIL=$((FAIL + 1))
    echo "FAIL: $name" >&2
  fi
}

step "assembleRelease" gradle assembleRelease --no-daemon -q
step "check-plugin-layout" "$ROOT/scripts/check-plugin-layout.sh"
step "smoke-folia" "$ROOT/scripts/smoke-folia.sh" 120
step "smoke-folia-plugins" "$ROOT/scripts/smoke-folia-plugins.sh" 120
step "smoke-yap-link-folia" "$ROOT/scripts/smoke-yap-link-folia.sh" 150
step "smoke-yap-link-plugins" "$ROOT/scripts/smoke-yap-link-plugins.sh"
step "smoke-yap-link-bedrock" "$ROOT/scripts/smoke-yap-link-bedrock.sh"
step "smoke-yap-link-two-backend" "$ROOT/scripts/smoke-yap-link-two-backend.sh" 180

if [ "${FAST:-0}" = "1" ]; then
  step "smoke-bedrock-play-unit" env SKIP_LIVE=1 "$ROOT/scripts/smoke-bedrock-play.sh"
else
  step "smoke-bedrock-play" "$ROOT/scripts/smoke-bedrock-play.sh" 180
fi

END_TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
OK=false
[ "$FAIL" -eq 0 ] && OK=true

{
  echo "{"
  echo "  \"at\": \"$END_TS\","
  echo "  \"started\": \"$START_TS\","
  echo "  \"passed\": $PASS,"
  echo "  \"failed\": $FAIL,"
  echo "  \"ok\": $OK,"
  echo "  \"steps\": { $(IFS=,; echo "${STEPS[*]}") }"
  echo "}"
} >"$OUT"

if [ "$OK" = true ]; then
  echo "PASS: smoke-network-full ($PASS steps)"
  echo "  artifact=$OUT"
  exit 0
fi
echo "FAIL: smoke-network-full ($FAIL failed / $((PASS + FAIL)) total)" >&2
cat "$OUT" >&2
exit 1
