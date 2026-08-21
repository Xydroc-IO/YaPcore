#!/usr/bin/env bash
# Download LuckPerms (Bukkit) into plugins/ — MIT licensed; not redistributed in-repo.
# Then start the server and run: ranks apply   (or enable yap-ranks-auto-apply=true)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PLUGINS="${ROOT}/plugins"
UA="YaPcore/install-luckperms"
mkdir -p "$PLUGINS"

# Prefer Modrinth versions API (project: luckperms)
JSON="$(curl -fsSL -A "$UA" \
  "https://api.modrinth.com/v2/project/luckperms/version?loaders=%5B%22bukkit%22%5D&limit=5")"
URL="$(printf '%s' "$JSON" | python3 -c '
import json,sys
vers=json.load(sys.stdin)
for v in vers:
    for f in v.get("files") or []:
        url=f.get("url") or ""
        name=(f.get("filename") or "").lower()
        if url and ("bukkit" in name or "luckperms" in name) and name.endswith(".jar"):
            # Prefer Bukkit over fabric/velocity
            if "fabric" in name or "velocity" in name or "sponge" in name or "nukkit" in name:
                continue
            print(url)
            raise SystemExit
print("", end="")
')"

if [[ -z "${URL}" ]]; then
  echo "Could not resolve LuckPerms Bukkit download from Modrinth." >&2
  echo "Download manually from https://luckperms.net/ and place the jar in plugins/" >&2
  exit 1
fi

OUT="${PLUGINS}/LuckPerms-Bukkit.jar"
# Remove older LuckPerms jars to avoid double-load
shopt -s nullglob
for old in "${PLUGINS}"/LuckPerms*.jar "${PLUGINS}"/luckperms*.jar; do
  [[ "$(basename "$old")" == "LuckPerms-Bukkit.jar" ]] && continue
  echo "Removing old $(basename "$old")"
  rm -f "$old"
done
shopt -u nullglob

echo "Downloading LuckPerms → ${OUT}"
curl -fL -A "$UA" -o "$OUT" "$URL"
echo "OK $(wc -c < "$OUT") bytes"
echo
echo "Next:"
echo "  1. Start YaPcore (./scripts/start.sh --fg)"
echo "  2. Apply ranks:  ranks apply"
echo "     or dashboard → Ranks → Apply pack"
echo "     or set yap-ranks-auto-apply=true in config/server.properties"
echo "  3. Assign: lp user Steve parent set vip"
echo "Docs: docs/PERMISSIONS.md"
