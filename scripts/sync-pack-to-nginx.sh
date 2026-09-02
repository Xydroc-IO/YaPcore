#!/usr/bin/env bash
# Sync resourcepacks/yapcore-default.zip into nginx :80 docroots (needs write access).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/resourcepacks/yapcore-default.zip"
if [[ ! -f "$SRC" ]]; then
  echo "Missing $SRC — run ./scripts/build-default-resourcepack.sh first" >&2
  exit 1
fi
for dest in /var/www/html/pack/yapcore-default.zip /var/www/html/packs/yapcore-default.zip; do
  dir="$(dirname "$dest")"
  if [[ ! -d "$dir" ]]; then
    echo "Skip (no dir): $dir"
    continue
  fi
  if cp -f "$SRC" "$dest" 2>/dev/null; then
    echo "Updated $dest"
  else
    echo "Need privileges for $dest — run: sudo cp -f \"$SRC\" \"$dest\""
  fi
done
sha1sum "$SRC"
ls -lh "$SRC"
echo "Folia/clients should use :8081 (YaP pack HTTP) or refresh nginx after sync."
