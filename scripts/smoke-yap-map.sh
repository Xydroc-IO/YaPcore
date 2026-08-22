#!/usr/bin/env bash
# Smoke: build yap-map.jar and verify a 16×16 sample tile PNG is written.
# Usage: ./scripts/smoke-yap-map.sh
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

echo "Building yap-map.jar + tile renderer test…"
gradle :map-plugin:shadowJar :map-plugin:test --tests com.yapcore.map.TileRendererTest --no-daemon -q

JAR="$ROOT/yap-first-party/core-network/map-plugin/build/libs/yap-map.jar"
if [ ! -f "$JAR" ]; then
  echo "FAIL: yap-map.jar not found at $JAR" >&2
  exit 1
fi

TILE="$ROOT/yap-first-party/core-network/map-plugin/build/smoke-yap-map/tiles/world/0/0_0.png"
if [ ! -f "$TILE" ]; then
  echo "FAIL: expected tile at $TILE" >&2
  exit 1
fi

SIZE=$(wc -c <"$TILE" | tr -d ' ')
if [ "$SIZE" -lt 50 ]; then
  echo "FAIL: tile too small ($SIZE bytes)" >&2
  exit 1
fi

echo "PASS: yap-map.jar built and tile created ($TILE, ${SIZE} bytes)"
echo "  jar=$JAR"
echo "  map=http://127.0.0.1:8081/map/ (when plugin enabled)"
