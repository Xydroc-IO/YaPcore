#!/usr/bin/env bash
# Fetch Faithful 64x Bedrock (.mcpack) from the Faithful CDN (experimental channel).
# License: https://faithfulpack.net/license — keep FAITHFUL_LICENSE.txt + CREDITS.md.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/resourcepacks/faithful-64x-bedrock.mcpack"
UA="YaPcore/1.0 (Faithful Bedrock fetch; https://github.com/Xydroc-IO/YaPcore)"
# Experimental tracks newest BE engine; Release channel lags JE Chaos Cubed naming.
URL="${FAITHFUL_BEDROCK_URL:-https://database.faithfulpack.net/packs/64x-Bedrock/Experimental/Faithful%2064x%20-%20Latest%20Experimental.mcpack}"

mkdir -p "$(dirname "$OUT")"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
echo "Downloading Faithful 64x Bedrock → $OUT"
curl -fsSL -A "$UA" -o "$TMP" "$URL"
# Basic sanity: ZIP/mcpack magic + manifest present
python3 - <<'PY' "$TMP"
import sys, zipfile
path = sys.argv[1]
with zipfile.ZipFile(path) as z:
    names = set(z.namelist())
    if "manifest.json" not in names:
        sys.exit("Downloaded file is not a Bedrock pack (missing manifest.json)")
print("OK manifest.json present,", sum(1 for _ in zipfile.ZipFile(path).namelist()), "entries")
PY
mv "$TMP" "$OUT"
trap - EXIT
echo "OK $OUT ($(du -h "$OUT" | awk '{print $1}'))"
echo "Build: ./scripts/build-default-bedrock-pack.sh"
