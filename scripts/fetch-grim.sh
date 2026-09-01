#!/usr/bin/env bash
# Download official Grim Anticheat (Folia-capable Bukkit jar) into plugins/ — not vendored in git.
# Upstream recommends Modrinth: https://modrinth.com/plugin/grimac (GPLv3)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UA="YaPcore/fetch-grim"
OUT_DIR="${2:-$ROOT/plugins}"
mkdir -p "$OUT_DIR" "$ROOT/third-party/grim"

read -r VER FILE URL <<EOF
$(curl -fsSL -A "$UA" "https://api.modrinth.com/v2/project/grimac/version?loaders=%5B%22folia%22%5D" | python3 -c '
import json, sys
versions = json.load(sys.stdin)
if not versions:
    sys.stderr.write("No Folia Grim versions on Modrinth\n")
    sys.exit(1)
v = versions[0]
files = v.get("files") or []
primary = next((f for f in files if f.get("primary")), files[0] if files else None)
if not primary:
    sys.stderr.write("No jar file in latest Grim version\n")
    sys.exit(1)
print(v.get("version_number", ""), primary.get("filename", ""), primary.get("url", ""))
')
EOF

OUT="$OUT_DIR/grim.jar"
NOTICE="$ROOT/third-party/grim"
echo "Downloading Grim Anticheat ${VER} (${FILE})"
echo "  → ${OUT}"
curl -fL -A "$UA" -o "$OUT" "$URL"

cp -f "$NOTICE/NOTICE.txt" "$OUT_DIR/grim-NOTICE.txt" 2>/dev/null || true
if [[ -f "$NOTICE/LICENSE-GPLv3.txt" ]]; then
  cp -f "$NOTICE/LICENSE-GPLv3.txt" "$OUT_DIR/grim-LICENSE-GPLv3.txt"
fi
cat > "$NOTICE/FETCHED.txt" <<EOF
Fetched: $(date -u +%Y-%m-%dT%H:%M:%SZ)
Version: ${VER}
File: ${FILE}
URL: ${URL}
Installed as: ${OUT}
Upstream: https://github.com/GrimAnticheat/Grim
Modrinth: https://modrinth.com/plugin/grimac
EOF

echo "OK $(wc -c < "$OUT") bytes"
echo "Disable or tune YaPGuard if both are active — see docs/ops/GRIM.md"
echo "Docs: docs/ops/GRIM.md · License: third-party/grim/"
