#!/usr/bin/env bash
# Xbox / Floodgate chain soak helper.
#
# Always: multi-hop stand-in-root unit test (mojangAuthenticated=true path).
# Optional retail: drop a captured Login identity JSON at
#   src/test/resources/xbox/retail-chain.json
# (gitignored — never commit real JWTs). See retail-chain.json.example.
#
# Usage:
#   ./scripts/protocol-matrix/xbox-chain-soak.sh
#   XBOX_CHAIN_JSON=/path/to/chain.json ./scripts/protocol-matrix/xbox-chain-soak.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "== Xbox chain unit tests (incl. Mojang-rooted multi-hop soak) =="
gradle :test --tests 'com.yapcore.crossplay.floodgate.XboxChainValidatorTest' \
  --tests 'com.yapcore.crossplay.floodgate.FloodgateAuthLoginParseTest' -q

if [[ -n "${XBOX_CHAIN_JSON:-}" ]]; then
  mkdir -p src/test/resources/xbox
  cp -f "$XBOX_CHAIN_JSON" src/test/resources/xbox/retail-chain.json
  echo "== Re-run with retail fixture copied to src/test/resources/xbox/retail-chain.json =="
  gradle :test --tests 'com.yapcore.crossplay.floodgate.XboxChainValidatorTest.optionalRetailFixtureIfPresent' -q
fi

echo "Xbox soak: multi-hop path green. Real Mojang root needs retail-chain.json (see example)."
