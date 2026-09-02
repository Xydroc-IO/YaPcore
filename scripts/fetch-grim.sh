#!/usr/bin/env bash
# Download official Grim Anticheat (Folia-capable Bukkit jar) into plugins/ — not vendored in git.
# Upstream recommends Modrinth: https://modrinth.com/plugin/grimac (GPLv3)
#
# Usage:
#   ./scripts/fetch-grim.sh                    # → plugins/grim.jar (active)
#   ./scripts/fetch-grim.sh --disabled         # → plugins/grim.jar.disabled (default on setup)
#   ./scripts/fetch-grim.sh --disabled --root /path/to/home
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UA="YaPcore/fetch-grim"
OUT_DIR="$ROOT/plugins"
DISABLED=0
QUIET=0

while [ $# -gt 0 ]; do
  case "$1" in
    --disabled) DISABLED=1; shift ;;
    --quiet) QUIET=1; shift ;;
    --root|--plugins-dir)
      OUT_DIR="$2/plugins"
      shift 2
      ;;
    -h|--help)
      sed -n '1,12p' "$0" | tail -n +2
      exit 0
      ;;
    *)
      echo "Unknown arg: $1" >&2
      exit 1
      ;;
  esac
done

mkdir -p "$OUT_DIR" "$ROOT/third-party/grim"

if [ -f "$OUT_DIR/grim.jar" ] || [ -f "$OUT_DIR/grim.jar.disabled" ]; then
  if [ "$QUIET" -eq 0 ]; then
    echo "Grim AC already present in $OUT_DIR — skip fetch"
  fi
  exit 0
fi

if [ "$DISABLED" -eq 1 ]; then
  OUT="$OUT_DIR/grim.jar.disabled"
else
  OUT="$OUT_DIR/grim.jar"
fi

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

NOTICE="$ROOT/third-party/grim"
if [ "$QUIET" -eq 0 ]; then
  echo "Downloading Grim Anticheat ${VER} (${FILE})"
  echo "  → ${OUT}"
fi
curl -fL -A "$UA" -o "$OUT" "$URL"

cp -f "$NOTICE/NOTICE.txt" "$OUT_DIR/grim-NOTICE.txt" 2>/dev/null || true
if [ -f "$NOTICE/LICENSE-GPLv3.txt" ]; then
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

if [ "$QUIET" -eq 0 ]; then
  echo "OK $(wc -c < "$OUT") bytes"
  if [ "$DISABLED" -eq 1 ]; then
    echo "Grim is disabled until: ./scripts/grim-ac.sh enable"
  else
    echo "Disable or tune YaPGuard if both are active — see docs/ops/GRIM.md"
  fi
  echo "Docs: docs/ops/GRIM.md · License: third-party/grim/"
fi
