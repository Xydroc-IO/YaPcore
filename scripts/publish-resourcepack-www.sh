#!/usr/bin/env bash
# Publish active client pack into nginx docroot so Cloudflare :80 can serve it.
# Always publishes a content-addressed name (…-<sha8>.zip) to bust CF stale cache —
# Minecraft rejects packs when advertised SHA-1 ≠ downloaded bytes ("failed to download").
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
PACK="${1:-$ROOT/resourcepacks/yapcore-default.zip}"
if [ ! -f "$PACK" ]; then
  echo "Missing pack: $PACK" >&2
  exit 1
fi

SHA1="$(sha1sum "$PACK" | awk '{print $1}')"
SHORT="${SHA1:0:8}"
BASE="$(basename "$PACK")"
STEM="${BASE%.*}"
EXT="${BASE##*.}"
HASHED="${STEM}-${SHORT}.${EXT}"

publish() {
  local dest_dir=$1
  mkdir -p "$dest_dir"
  cp -f "$PACK" "$dest_dir/$BASE"
  cp -f "$PACK" "$dest_dir/$HASHED"
  chmod 644 "$dest_dir/$BASE" "$dest_dir/$HASHED"
  echo "Published → $dest_dir/$HASHED (and $BASE)"
  echo "SHA-1 $SHA1"
}

if [ -w /var/www/html ]; then
  publish /var/www/html/pack
elif command -v docker >/dev/null 2>&1; then
  docker run --rm \
    -v /var/www/html:/www \
    -v "$(dirname -- "$PACK"):/src:ro" \
    alpine sh -c "mkdir -p /www/pack && cp -f /src/$(basename "$PACK") /www/pack/$BASE && cp -f /src/$(basename "$PACK") /www/pack/$HASHED && chmod 644 /www/pack/$BASE /www/pack/$HASHED && ls -la /www/pack/$HASHED"
  echo "Published via Docker → /var/www/html/pack/$HASHED"
elif command -v sudo >/dev/null 2>&1; then
  sudo mkdir -p /var/www/html/pack
  sudo cp -f "$PACK" "/var/www/html/pack/$BASE"
  sudo cp -f "$PACK" "/var/www/html/pack/$HASHED"
  sudo chmod 644 "/var/www/html/pack/$BASE" "/var/www/html/pack/$HASHED"
  echo "Published via sudo → /var/www/html/pack/$HASHED"
else
  echo "Cannot write /var/www/html/pack (need docker or sudo)" >&2
  exit 1
fi

# Keep a copy under resourcepacks/ so YaPcore can activate the cache-busted name
cp -f "$PACK" "$ROOT/resourcepacks/$HASHED"

echo "Activate this pack (busts Cloudflare stale cache):"
echo "  resource-pack-files=$HASHED"
echo "  URL: http://yapcoremc.yaplabs.us/pack/$HASHED"
echo "Test: curl -sL http://yapcoremc.yaplabs.us/pack/$HASHED | sha1sum"
curl -sI --max-time 5 "http://127.0.0.1/pack/$HASHED" | head -5 || true
