#!/usr/bin/env bash
# Apply ordered YaP Folia patches under vendor/folia/patches/.
# Called by build-yap-folia.sh BEFORE Folia's applyAllPatches (branding edits
# Folia's tracked .patch files; later patches may target generated trees).
#
# Usage:
#   ./scripts/folia-patch.sh              # apply all 000*.patch
#   ./scripts/folia-patch.sh --check      # dry-run (git apply --check)
#   ./scripts/folia-patch.sh --list
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="$ROOT/vendor/folia/work"
PATCH_DIR="$ROOT/vendor/folia/patches"

MODE=apply
case "${1:-}" in
  --check) MODE=check ;;
  --list) MODE=list ;;
  ""|--apply) MODE=apply ;;
  *) echo "Usage: $0 [--apply|--check|--list]" >&2; exit 2 ;;
esac

if [ ! -d "$WORK" ]; then
  echo "Missing vendor/folia/work — run ./scripts/vendor-folia.sh first" >&2
  exit 1
fi

mapfile -t PATCHES < <(find "$PATCH_DIR" -maxdepth 1 -type f -name '0*.patch' | sort)
if [ "${#PATCHES[@]}" -eq 0 ]; then
  echo "No patches in $PATCH_DIR"
  exit 0
fi

if [ "$MODE" = "list" ]; then
  printf '%s\n' "${PATCHES[@]#$ROOT/}"
  exit 0
fi

cd "$WORK"
for p in "${PATCHES[@]}"; do
  name="$(basename "$p")"
  echo "== $name =="
  if [ "$MODE" = "check" ]; then
    git apply --check --whitespace=nowarn "$p"
  else
    # Idempotent: skip if already applied
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
echo "YaP Folia patches OK ($MODE)"
