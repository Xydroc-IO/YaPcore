#!/usr/bin/env bash
# Prefetch Folia into lib/ (optional — FoliaKernel downloads on boot).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VER="${1:-26.2}"
UA="YaPcore/fetch-folia"
mkdir -p "$ROOT/lib"
JSON="$(curl -fsSL -A "$UA" "https://fill.papermc.io/v3/projects/folia/versions/${VER}/builds")"
URL="$(printf '%s' "$JSON" | python3 -c '
import json,sys
builds=json.load(sys.stdin)
for b in builds:
    if b.get("channel")=="STABLE":
        print(b["downloads"]["server:default"]["url"]); break
else:
    print(builds[0]["downloads"]["server:default"]["url"])
')"
OUT="$ROOT/lib/folia-${VER}.jar"
echo "Downloading Folia ${VER} → ${OUT}"
curl -fL -A "$UA" -o "$OUT" "$URL"
echo "OK $(wc -c < "$OUT") bytes"
