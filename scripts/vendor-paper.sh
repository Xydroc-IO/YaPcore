#!/usr/bin/env bash
# Clone / update vendor/paper to the commit in vendor/paper.pin
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
PIN="$ROOT/vendor/paper.pin"
DEST="$ROOT/vendor/paper"

if [[ ! -f "$PIN" ]]; then
  echo "Missing $PIN" >&2
  exit 1
fi

# shellcheck disable=SC1090
commit="$(grep '^commit=' "$PIN" | cut -d= -f2-)"
repo="$(grep '^repo=' "$PIN" | cut -d= -f2-)"
build="$(grep '^build=' "$PIN" | cut -d= -f2-)"
mc="$(grep '^mc=' "$PIN" | cut -d= -f2-)"

mkdir -p "$ROOT/vendor"
if [[ ! -d "$DEST/.git" ]]; then
  echo "Cloning Paper → $DEST (commit $commit, $mc #$build)"
  git clone --filter=blob:none "$repo" "$DEST"
fi

cd "$DEST"
git fetch --depth 1 origin "$commit" 2>/dev/null || git fetch origin "$commit"
git checkout --force "$commit"
git reset --hard "$commit"
echo "vendor/paper @ $(git rev-parse --short HEAD) ($mc build $build)"
echo "Next: $ROOT/scripts/build-vendor-paper.sh"
