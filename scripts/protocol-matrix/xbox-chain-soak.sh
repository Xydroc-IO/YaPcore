#!/usr/bin/env bash
# Xbox / Floodgate chain soak helper.
#
# Always: multi-hop stand-in-root unit test (mojangAuthenticated=true path).
#
# Capture a *real* Mojang-rooted retail chain:
#   1. Restart with:  JAVA_TOOL_OPTIONS='-Dyap.floodgate.dumpChain=true' ./scripts/start.sh --nogui
#      (or set yap.floodgate.dumpChainPath=/abs/path.json)
#   2. Join once from Xbox-signed Bedrock / retail client.
#   3. Stop server. Fixture is at build/xbox-chain-capture.json (gitignored).
#   4. Soak: XBOX_CHAIN_JSON=build/xbox-chain-capture.json ./scripts/protocol-matrix/xbox-chain-soak.sh
#
# Or copy into src/test/resources/xbox/retail-chain.json (gitignored) and re-run soak.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "== Xbox chain unit tests (incl. Mojang-rooted multi-hop + retail-shaped CI gate) =="
gradle :test --tests 'com.yapcore.crossplay.floodgate.XboxChainValidatorTest' \
  --tests 'com.yapcore.crossplay.floodgate.FloodgateAuthLoginParseTest' -q

echo "== P4.9 retail-shaped chain (always-on CI) =="
gradle :test --tests 'com.yapcore.crossplay.floodgate.XboxChainValidatorTest.retailShapedChainAlwaysPassesInCi' -q
echo "Retail-shaped soak: PASS"

FIXTURE="${XBOX_CHAIN_JSON:-}"
if [[ -z "$FIXTURE" && -f build/xbox-chain-capture.json ]]; then
  FIXTURE=build/xbox-chain-capture.json
  echo "== Using build/xbox-chain-capture.json =="
fi

if [[ -n "$FIXTURE" ]]; then
  if [[ ! -f "$FIXTURE" ]]; then
    echo "XBOX_CHAIN_JSON not a file: $FIXTURE" >&2
    exit 1
  fi
  mkdir -p src/test/resources/xbox
  cp -f "$FIXTURE" src/test/resources/xbox/retail-chain.json
  echo "== Retail fixture → optionalRetailFixtureIfPresent =="
  gradle :test --tests 'com.yapcore.crossplay.floodgate.XboxChainValidatorTest.optionalRetailFixtureIfPresent' -q
  echo "Retail Mojang-rooted soak: PASS"
else
  echo "No retail fixture yet. Capture with -Dyap.floodgate.dumpChain=true then re-run."
fi

echo "Xbox soak done."
