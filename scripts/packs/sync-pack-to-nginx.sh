#!/usr/bin/env bash
# Sync Java zip + Bedrock mcpack into nginx :80 docroots + YaP-Folia SHA.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ZIP="$ROOT/resourcepacks/yapcore-default.zip"
if [[ ! -f "$ZIP" ]]; then
  echo "Missing $ZIP — run ./scripts/packs/build-default-resourcepack.sh first" >&2
  exit 1
fi

copy_dest() {
  local src="$1"
  local dest="$2"
  local dir
  dir="$(dirname "$dest")"
  if [[ ! -f "$src" ]]; then
    echo "Skip (missing src): $src"
    return 0
  fi
  if [[ ! -d "$dir" ]]; then
    echo "Skip (no dir): $dir"
    return 0
  fi
  if cp -f "$src" "$dest" 2>/dev/null; then
    echo "Updated $dest"
    return 0
  fi
  if sudo -n cp -f "$src" "$dest" 2>/dev/null; then
    echo "Updated $dest (sudo -n)"
    return 0
  fi
  if command -v docker >/dev/null 2>&1; then
    local base
    base="$(basename "$dest")"
    if docker run --rm -v "$src:/src.bin:ro" -v "$dir:/dest" alpine cp /src.bin "/dest/$base" 2>/dev/null; then
      echo "Updated $dest (docker)"
      return 0
    fi
  fi
  echo "Need privileges for $dest — run: sudo cp -f \"$src\" \"$dest\"" >&2
  return 1
}

fail=0
copy_dest "$ZIP" /var/www/html/pack/yapcore-default.zip || fail=1
copy_dest "$ZIP" /var/www/html/packs/yapcore-default.zip || fail=1

# Bedrock join packs (Link ResourcePacksInfo CDN on :80). Without these, clients hang
# on "Loading resource packs" until Link's CDN timeout.
for base in yapcore-portals.mcpack yapcore-default.mcpack; do
  src=""
  if [[ -f "$ROOT/resourcepacks/$base" ]]; then
    src="$ROOT/resourcepacks/$base"
  elif [[ -f "$ROOT/link-data/$base" ]]; then
    src="$ROOT/link-data/$base"
  fi
  if [[ -n "$src" ]]; then
    copy_dest "$src" "/var/www/html/pack/$base" || fail=1
  else
    echo "Skip (missing mcpack): $base"
  fi
done

SHA="$(sha1sum "$ZIP" | awk '{print $1}')"
# Keep YaP-Folia login-prompt hash in sync with the zip clients download.
for props in \
  "$ROOT/folia-kernel/server.properties" \
  "$ROOT/config/paper-server.properties" \
  "$ROOT/config/game-server.properties"
do
  if [[ -f "$props" ]] && grep -q '^resource-pack-sha1=' "$props"; then
    sed -i "s/^resource-pack-sha1=.*/resource-pack-sha1=${SHA}/" "$props"
    echo "Set resource-pack-sha1=${SHA} in ${props#"$ROOT"/}"
  fi
done

sha1sum "$ZIP"
ls -lh "$ZIP"
echo "YaP-Folia/clients should use :8081 (YaP pack HTTP) or :80 after nginx sync."
echo "NOTE: restart YaP-Folia if it was already running so the new SHA is advertised."
exit "$fail"
