#!/usr/bin/env bash
# Fetch Folia-ecosystem jars for MSPT scoreboard (stock Folia + Canvas @ FOLIA_VERSION).
# Kaiiju is skipped: latest public releases are 1.20.x, not 26.2-class.
# Usage: ./scripts/bench/fetch-folia-forks.sh [version]
# Env: YAP_BENCH_FORCE_FETCH=1 to re-download even if present
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=../lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

cd "$ROOT"
yap_load_config
VER="${1:-${FOLIA_VERSION:-26.2}}"
UA="YaPcore/fetch-folia-forks"
FORCE="${YAP_BENCH_FORCE_FETCH:-0}"
mkdir -p "$ROOT/lib"

need() {
  local path="$1"
  if [[ "$FORCE" == "1" ]]; then
    return 0
  fi
  [[ ! -f "$path" ]] || [[ ! -s "$path" ]]
}

fetch_folia() {
  local out="$ROOT/lib/folia-${VER}.jar"
  if ! need "$out"; then
    echo "OK folia already present: $out ($(wc -c <"$out") bytes)"
    return
  fi
  echo "Downloading Folia ${VER}…"
  "$ROOT/scripts/fetch-folia.sh" "$VER"
}

fetch_canvas() {
  local out="$ROOT/lib/canvas-${VER}.jar"
  if ! need "$out"; then
    echo "OK canvas already present: $out ($(wc -c <"$out") bytes)"
    return
  fi
  local meta url channel
  meta="$(curl -fsSL -A "$UA" 'https://canvasmc.io/api/v2/builds/latest?project=canvas')"
  channel="$(printf '%s' "$meta" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("channelVersion",""))')"
  url="$(printf '%s' "$meta" | python3 -c 'import json,sys; print(json.load(sys.stdin)["downloadUrl"])')"
  if [[ -n "$channel" && "$channel" != "$VER" ]]; then
    echo "WARN: Canvas latest channelVersion=$channel (wanted $VER) — still fetching latest stable" >&2
  fi
  echo "Downloading Canvas ← $url"
  curl -fL -A "$UA" -o "$out" "$url"
  echo "OK canvas $(wc -c <"$out") bytes → $out (channel=$channel)"
}

fetch_folia
fetch_canvas
echo "Folia forks ready under $ROOT/lib (folia / canvas ${VER})"
echo "NOTE: Kaiiju not fetched — public releases are 1.20.x, not ${VER}."
