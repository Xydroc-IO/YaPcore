#!/usr/bin/env bash
# Download official Tebex Folia plugin (GPLv3) into plugins/ — not vendored in git.
# Legal: https://github.com/tebexio/Tebex-Minecraft (GNU GPLv3) — redistribute OK with notices.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UA="YaPcore/fetch-tebex"
TAG="${1:-}"
OUT_DIR="${2:-$ROOT/plugins}"
mkdir -p "$OUT_DIR" "$ROOT/third-party/tebex"

API="https://api.github.com/repos/tebexio/Tebex-Minecraft/releases"
if [[ -n "$TAG" ]]; then
  JSON="$(curl -fsSL -A "$UA" "$API/tags/${TAG}")"
else
  JSON="$(curl -fsSL -A "$UA" "$API/latest")"
fi

read -r ASSET_NAME URL VER <<EOF
$(printf '%s' "$JSON" | python3 -c '
import json,sys
r=json.load(sys.stdin)
tag=r.get("tag_name") or ""
assets=r.get("assets") or []
folia=[a for a in assets if "folia" in a["name"].lower() and a["name"].endswith(".jar")]
if not folia:
    sys.stderr.write("No tebex-folia-*.jar asset in release %s\n" % tag)
    sys.exit(1)
a=folia[0]
print(a["name"], a["browser_download_url"], tag)
')
EOF

OUT="$OUT_DIR/tebex.jar"
NOTICE="$ROOT/third-party/tebex"
echo "Downloading Tebex Folia ${VER} (${ASSET_NAME})"
echo "  → ${OUT}"
curl -fL -A "$UA" -o "$OUT" "$URL"

# Keep redistributable license artifacts next to the jar (GPLv3 obligation helpers)
cp -f "$NOTICE/NOTICE.txt" "$OUT_DIR/tebex-NOTICE.txt" 2>/dev/null || true
if [[ -f "$NOTICE/LICENSE-GPLv3.txt" ]]; then
  cp -f "$NOTICE/LICENSE-GPLv3.txt" "$OUT_DIR/tebex-LICENSE-GPLv3.txt"
fi
cat > "$NOTICE/FETCHED.txt" <<EOF
Fetched: $(date -u +%Y-%m-%dT%H:%M:%SZ)
Release: ${VER}
Asset: ${ASSET_NAME}
URL: ${URL}
Installed as: ${OUT}
Source: https://github.com/tebexio/Tebex-Minecraft
EOF

echo "OK $(wc -c < "$OUT") bytes — dashboard: Tebex store → paste secret, or Hub: tebex secret <key>"
echo "Docs: docs/ops/TEBEX.md · License: third-party/tebex/"
