#!/usr/bin/env bash
# Fetch latest Faithful 64x for YaPcore's MC version from Modrinth (official CDN).
# License: https://faithfulpack.net/license — keep FAITHFUL_LICENSE.txt + CREDITS.md.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/resourcepacks/faithful-64x.zip"
LICENSE_OUT="$ROOT/resourcepacks/FAITHFUL_LICENSE.txt"
MC_VER="${1:-26.2}"
UA="YaPcore/1.0 (Faithful fetch; https://github.com/)"

echo "Querying Modrinth for Faithful 64x (game_versions=$MC_VER)…"
JSON="$(curl -fsSL -A "$UA" \
  "https://api.modrinth.com/v2/project/faithful-64x/version?game_versions=%5B%22${MC_VER}%22%5D&limit=5")"
URL="$(printf '%s' "$JSON" | python3 -c '
import json,sys
vers=json.load(sys.stdin)
if not vers:
    sys.exit("No Faithful 64x versions for that game version")
files=vers[0].get("files") or []
primary=next((f for f in files if f.get("primary")), files[0] if files else None)
if not primary:
    sys.exit("No files on version")
print(primary["url"])
print(primary["hashes"]["sha1"], file=sys.stderr)
print(vers[0].get("name","?"), file=sys.stderr)
')"
SHA_EXPECTED="$(printf '%s' "$JSON" | python3 -c '
import json,sys
vers=json.load(sys.stdin)
files=vers[0].get("files") or []
primary=next((f for f in files if f.get("primary")), files[0])
print(primary["hashes"]["sha1"])
')"

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
echo "Downloading → $OUT"
curl -fsSL -A "$UA" -o "$TMP" "$URL"
GOT="$(sha1sum "$TMP" | awk '{print $1}')"
if [[ "$GOT" != "$SHA_EXPECTED" ]]; then
  echo "SHA-1 mismatch: expected $SHA_EXPECTED got $GOT" >&2
  exit 1
fi
mkdir -p "$(dirname "$OUT")"
mv "$TMP" "$OUT"
trap - EXIT
curl -fsSL -A "$UA" -o "$LICENSE_OUT" \
  "https://raw.githubusercontent.com/Faithful-Resource-Pack/Faithful-64x-Java/main/LICENSE.txt"
echo "OK $OUT ($GOT)"
echo "License → $LICENSE_OUT"
echo "Set resource-pack-file=faithful-64x.zip and restart so Paper picks up the new hash."
