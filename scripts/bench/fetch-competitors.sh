#!/usr/bin/env bash
# Fetch competitor jars for ecosystem MSPT bench (Paper / Purpur / Leaf @ PAPER_VERSION).
# Usage: ./scripts/bench/fetch-competitors.sh [version]
# Env: YAP_BENCH_FORCE_FETCH=1 to re-download even if present
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=../lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

cd "$ROOT"
yap_load_config
VER="${1:-$PAPER_VERSION}"
UA="YaPcore/fetch-competitors"
FORCE="${YAP_BENCH_FORCE_FETCH:-0}"
mkdir -p "$ROOT/lib"

need() {
  local path="$1"
  if [[ "$FORCE" == "1" ]]; then
    return 0
  fi
  [[ ! -f "$path" ]] || [[ ! -s "$path" ]]
}

fetch_paper() {
  local out="$ROOT/lib/paper-${VER}.jar"
  if ! need "$out"; then
    echo "OK paper already present: $out ($(wc -c <"$out") bytes)"
    return
  fi
  echo "Downloading Paper ${VER}…"
  "$ROOT/scripts/fetch-paper.sh" "$VER"
}

fetch_purpur() {
  local out="$ROOT/lib/purpur-${VER}.jar"
  if ! need "$out"; then
    echo "OK purpur already present: $out ($(wc -c <"$out") bytes)"
    return
  fi
  local url="https://api.purpurmc.org/v2/purpur/${VER}/latest/download"
  echo "Downloading Purpur ${VER} ← $url"
  curl -fL -A "$UA" -o "$out" "$url"
  echo "OK purpur $(wc -c <"$out") bytes → $out"
}

fetch_leaf() {
  local out="$ROOT/lib/leaf-${VER}.jar"
  if ! need "$out"; then
    echo "OK leaf already present: $out ($(wc -c <"$out") bytes)"
    return
  fi
  local meta build name url
  meta="$(curl -fsSL -A "$UA" "https://api.leafmc.one/v2/projects/leaf/versions/${VER}/builds")"
  build="$(printf '%s' "$meta" | python3 -c '
import json,sys
builds=json.load(sys.stdin)["builds"]
print(builds[-1]["build"])
')"
  name="leaf-${VER}-${build}.jar"
  url="https://api.leafmc.one/v2/projects/leaf/versions/${VER}/builds/${build}/downloads/${name}"
  echo "Downloading Leaf ${VER} build ${build} ← $url"
  curl -fL -A "$UA" -o "$out" "$url"
  echo "OK leaf $(wc -c <"$out") bytes → $out"
}

fetch_paper
fetch_purpur
fetch_leaf
echo "Competitors ready under $ROOT/lib (paper / purpur / leaf ${VER})"
