#!/usr/bin/env bash
# Clone / pin PaperMC Folia into vendor/folia/work (gitignored).
# Usage:
#   ./scripts/vendor-folia.sh              # clone or reset to UPSTREAM.lock
#   ./scripts/vendor-folia.sh --update-lock # fetch branch tip and rewrite lock
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

LOCK="$ROOT/vendor/folia/UPSTREAM.lock"
WORK="$ROOT/vendor/folia/work"
UPDATE=0
if [ "${1:-}" = "--update-lock" ]; then
  UPDATE=1
fi

if [ ! -f "$LOCK" ]; then
  echo "Missing $LOCK" >&2
  exit 1
fi

# shellcheck disable=SC1090
. <(grep -E '^(REPO|BRANCH|COMMIT|MC_VERSION)=' "$LOCK" | sed 's/^/export /')

REPO="${REPO:-https://github.com/PaperMC/Folia.git}"
BRANCH="${BRANCH:-ver/26.2.x}"
COMMIT="${COMMIT:-}"
MC_VERSION="${MC_VERSION:-26.2}"

mkdir -p "$ROOT/vendor/folia"

if [ ! -d "$WORK/.git" ]; then
  echo "Cloning Folia ${BRANCH} → vendor/folia/work …"
  git clone --branch "$BRANCH" --single-branch --filter=blob:none "$REPO" "$WORK"
fi

git -C "$WORK" remote set-url origin "$REPO" >/dev/null 2>&1 || true
git -C "$WORK" fetch --depth 50 origin "$BRANCH"

if [ "$UPDATE" -eq 1 ]; then
  TIP="$(git -C "$WORK" rev-parse "origin/$BRANCH")"
  MSG="$(git -C "$WORK" log -1 --pretty=%s "$TIP")"
  cat >"$LOCK" <<EOF
# YaP Folia upstream pin — Phase 1 bootstrap
# Do not edit by hand; refresh with: ./scripts/vendor-folia.sh --update-lock
REPO=${REPO}
BRANCH=${BRANCH}
COMMIT=${TIP}
MC_VERSION=${MC_VERSION}
PINNED_AT=$(date -u +%Y-%m-%d)
NOTE=${MSG}
EOF
  COMMIT="$TIP"
  echo "Updated UPSTREAM.lock → $COMMIT ($MSG)"
fi

if [ -z "$COMMIT" ]; then
  echo "UPSTREAM.lock has empty COMMIT" >&2
  exit 1
fi

# Ensure commit is reachable (deepen if needed)
if ! git -C "$WORK" cat-file -e "${COMMIT}^{commit}" 2>/dev/null; then
  echo "Fetching commit $COMMIT …"
  git -C "$WORK" fetch --depth 1 origin "$COMMIT" || git -C "$WORK" fetch origin "$COMMIT"
fi

git -C "$WORK" checkout --detach "$COMMIT"
git -C "$WORK" reset --hard "$COMMIT"
echo "Folia vendor ready: $(git -C "$WORK" rev-parse --short HEAD) ($BRANCH / $MC_VERSION)"
echo "Next: ./scripts/build-yap-folia.sh"
