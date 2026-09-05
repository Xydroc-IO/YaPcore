#!/usr/bin/env bash
# Apply ordered YaP Folia patches under vendor/folia/patches/.
#
# Two phases (paperweight generates sources during applyAllPatches):
#   pre  — edits Folia's tracked .patch files (branding). Call BEFORE applyAllPatches.
#   post — edits generated folia-server/src + paper-server/src. Call AFTER applyAllPatches.
#
# Usage:
#   ./scripts/folia-patch.sh pre|post [--check]
#   ./scripts/folia-patch.sh --list
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="$ROOT/vendor/folia/work"
PATCH_DIR="$ROOT/vendor/folia/patches"

PHASE="${1:-}"
MODE=apply
case "${2:-}" in
  --check) MODE=check ;;
  "" ) ;;
  *) echo "Usage: $0 pre|post|list [--check]" >&2; exit 2 ;;
esac

case "$PHASE" in
  pre|post) ;;
  --list|list)
    echo "pre:"; ls -1 "$PATCH_DIR"/0000-*.patch 2>/dev/null || true
    echo "post:"; ls -1 "$PATCH_DIR"/000[1-9]-*.patch "$PATCH_DIR"/001*.patch "$PATCH_DIR"/002*.patch 2>/dev/null || true
    exit 0
    ;;
  *)
    echo "Usage: $0 pre|post|list [--check]" >&2
    exit 2
    ;;
esac

if [ ! -d "$WORK" ]; then
  echo "Missing vendor/folia/work — run ./scripts/vendor-folia.sh first" >&2
  exit 1
fi

if [ "$PHASE" = "pre" ]; then
  mapfile -t PATCHES < <(find "$PATCH_DIR" -maxdepth 1 -type f -name '0000-*.patch' | sort)
else
  mapfile -t PATCHES < <(find "$PATCH_DIR" -maxdepth 1 -type f \( -name '000[1-9]-*.patch' -o -name '001*.patch' -o -name '002*.patch' \) | sort)
fi

if [ "${#PATCHES[@]}" -eq 0 ]; then
  echo "No $PHASE patches in $PATCH_DIR"
  exit 0
fi

cd "$WORK"
for p in "${PATCHES[@]}"; do
  name="$(basename "$p")"
  echo "== $PHASE $name =="
  if [ "$MODE" = "check" ]; then
    git apply --check --whitespace=nowarn "$p"
  else
    if git apply --check --whitespace=nowarn "$p" 2>/dev/null; then
      git apply --whitespace=nowarn "$p"
      echo "applied $name"
    elif git apply --reverse --check --whitespace=nowarn "$p" 2>/dev/null; then
      echo "already applied $name (skip)"
    else
      echo "FAILED to apply $name" >&2
      git apply --check --whitespace=nowarn "$p" || true
      exit 1
    fi
  fi
done
echo "YaP Folia $PHASE patches OK ($MODE)"
