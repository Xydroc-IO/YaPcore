#!/usr/bin/env bash
# Refresh the single upload folder at repo root: UPLOAD/
#
# That folder is the ONLY place to grab GitHub release assets from.
# Build intermediates (resourcepacks/, dist/, build/, releases/<ver>/) stay elsewhere.
#
# Usage:
#   ./scripts/release/stage-github-upload.sh
#   ./scripts/release/stage-github-upload.sh --upload
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
VER="${YAP_VERSION:-$(grep -E '^version\s*=' "$ROOT/build.gradle.kts" 2>/dev/null | head -1 | sed -E 's/.*"([^"]+)".*/\1/' || true)}"
VER="${VER:-0.0.0.2}"
SRC="$ROOT/releases/$VER"
DEST="$ROOT/UPLOAD"
DO_UPLOAD=0
for a in "$@"; do
  case "$a" in
    --upload) DO_UPLOAD=1 ;;
  esac
done

mkdir -p "$SRC" "$DEST"
# Keep releases/upload as a stable alias of UPLOAD/
rm -rf "$ROOT/releases/upload"
ln -sfn ../UPLOAD "$ROOT/releases/upload"

# Packs always from resourcepacks/ (source of truth)
[[ -f "$ROOT/resourcepacks/yapcore-default.zip" ]] && \
  /bin/cp -f "$ROOT/resourcepacks/yapcore-default.zip" "$SRC/yapcore-default.zip"
[[ -f "$ROOT/resourcepacks/yapcore-default.mcpack" ]] && \
  /bin/cp -f "$ROOT/resourcepacks/yapcore-default.mcpack" "$SRC/yapcore-default.mcpack"
[[ -f "$ROOT/dist/client-mods/client_mods.zip" ]] && \
  /bin/cp -f "$ROOT/dist/client-mods/client_mods.zip" "$SRC/client_mods.zip"

# Also pull suite/OS zips from build/dist when publish left them there but not in releases/
for f in yapcore-release-linux.zip yapcore-release-windows.zip yap-network-suite.zip yap-gameplay-suite.zip; do
  if [[ ! -f "$SRC/$f" && -f "$ROOT/build/dist/$f" ]]; then
    /bin/cp -f "$ROOT/build/dist/$f" "$SRC/$f"
  fi
done

# Wipe UPLOAD/ and stage only the seven GitHub assets
find "$DEST" -mindepth 1 -maxdepth 1 ! -name 'README.txt' -exec rm -rf {} +
ASSETS=(
  yapcore-release-linux.zip
  yapcore-release-windows.zip
  yap-network-suite.zip
  yap-gameplay-suite.zip
  yapcore-default.zip
  yapcore-default.mcpack
  client_mods.zip
)
missing=0
echo "UPLOAD folder → $DEST"
for f in "${ASSETS[@]}"; do
  if [[ -f "$SRC/$f" ]]; then
    /bin/cp -f "$SRC/$f" "$DEST/$f"
    printf '  OK  %s (%s)\n' "$f" "$(du -h "$DEST/$f" | awk '{print $1}')"
  else
    printf '  !!  MISSING %s\n' "$f"
    missing=1
  fi
done

cat >"$DEST/README.txt" <<EOF
╔══════════════════════════════════════════════════════════╗
║  YaPcore $VER — UPLOAD THIS FOLDER TO GITHUB             ║
╚══════════════════════════════════════════════════════════╝

This is the ONLY folder you upload from.

Files:
  yapcore-release-linux.zip      Full Linux server
  yapcore-release-windows.zip    Full Windows server
  yap-network-suite.zip          Link + network plugins
  yap-gameplay-suite.zip         Gameplay plugins
  yapcore-default.zip            Java resource pack
  yapcore-default.mcpack         Bedrock resource pack
  client_mods.zip                Fabric client mods

Refresh this folder after rebuilding:
  ./scripts/release/stage-github-upload.sh

Push to GitHub:
  gh release upload $VER UPLOAD/*.{zip,mcpack} --clobber -R Xydroc-IO/YaPcore
  # or: ./scripts/release/stage-github-upload.sh --upload

Do NOT upload from: resourcepacks/  dist/  build/  releases/$VER/
EOF

echo
if [[ "$missing" -ne 0 ]]; then
  echo "Incomplete — run: gradle publishReleasesFolder -PyapGameplay=true"
  echo "             then: ./scripts/packs/build-default-resourcepack.sh"
  exit 1
fi

if [[ "$DO_UPLOAD" -eq 1 ]]; then
  command -v gh >/dev/null || { echo "gh CLI missing"; exit 1; }
  gh release upload "$VER" "$DEST"/*.{zip,mcpack} --clobber -R Xydroc-IO/YaPcore
  echo "Uploaded to GitHub tag $VER"
fi
