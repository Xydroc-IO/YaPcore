#!/usr/bin/env bash
# Fetch fork-side parity plugins that match YaPcore *native* product surface.
# Via* = JE multi-version (YaP ProtocolCompat / ViaStyleRemapper).
# PlaceholderAPI / vehicles / knobs come from the YaP build (same jars on all sides).
set -euo pipefail
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
mkdir -p "$ROOT/lib"

fetch_modrinth_latest() {
  local project="$1"
  local out="$2"
  if [ -f "$out" ] && [ "${YAP_BENCH_REFETCH:-0}" != "1" ]; then
    echo "OK already present: $out ($(wc -c <"$out") bytes)"
    return 0
  fi
  local url
  url="$(curl -fsSL -A 'YaPcore-bench' "https://api.modrinth.com/v2/project/${project}/version?limit=1" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["files"][0]["url"])')"
  echo "Fetching $project → $out"
  /bin/curl -fL -A 'YaPcore-bench' -o "$out" "$url"
  echo "OK $out ($(wc -c <"$out") bytes)"
}

fetch_modrinth_latest viaversion "$ROOT/lib/ViaVersion.jar"
fetch_modrinth_latest viabackwards "$ROOT/lib/ViaBackwards.jar"
fetch_modrinth_latest viarewind "$ROOT/lib/ViaRewind.jar"
echo "Parity Via* jars ready under $ROOT/lib"
