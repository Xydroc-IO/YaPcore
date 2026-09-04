#!/usr/bin/env bash
# Sync resourcepacks/yapcore-default.zip into nginx :80 docroots + Folia SHA.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/resourcepacks/yapcore-default.zip"
if [[ ! -f "$SRC" ]]; then
  echo "Missing $SRC — run ./scripts/build-default-resourcepack.sh first" >&2
  exit 1
fi

copy_dest() {
  local dest="$1"
  local dir
  dir="$(dirname "$dest")"
  if [[ ! -d "$dir" ]]; then
    echo "Skip (no dir): $dir"
    return 0
  fi
  if cp -f "$SRC" "$dest" 2>/dev/null; then
    echo "Updated $dest"
    return 0
  fi
  if sudo -n cp -f "$SRC" "$dest" 2>/dev/null; then
    echo "Updated $dest (sudo -n)"
    return 0
  fi
  if command -v docker >/dev/null 2>&1; then
    if docker run --rm -v "$SRC:/src.zip:ro" -v "$dir:/dest" alpine cp /src.zip "/dest/$(basename "$dest")" 2>/dev/null; then
      echo "Updated $dest (docker)"
      return 0
    fi
  fi
  echo "Need privileges for $dest — run: sudo cp -f \"$SRC\" \"$dest\"" >&2
  return 1
}

fail=0
for dest in /var/www/html/pack/yapcore-default.zip /var/www/html/packs/yapcore-default.zip; do
  copy_dest "$dest" || fail=1
done

SHA="$(sha1sum "$SRC" | awk '{print $1}')"
# Keep Folia login-prompt hash in sync with the zip clients download.
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

sha1sum "$SRC"
ls -lh "$SRC"
echo "Folia/clients should use :8081 (YaP pack HTTP) or :80 after nginx sync."
echo "NOTE: restart Folia if it was already running so the new SHA is advertised."
exit "$fail"
