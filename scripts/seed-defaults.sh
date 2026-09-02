#!/usr/bin/env bash
# Seed shippable defaults into a YaP home (never overwrites existing files).
#
# Usage:
#   ./scripts/seed-defaults.sh
#   ./scripts/seed-defaults.sh --root /path/to/yap-home
#   ./scripts/seed-defaults.sh --force-missing-only   # default behavior
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"

while [ $# -gt 0 ]; do
  case "$1" in
    --root)
      ROOT="$(CDPATH= cd -- "$2" && pwd)"
      shift 2
      ;;
    -h|--help)
      echo "Usage: $0 [--root DIR]"
      echo "  Copies config/defaults/** into DIR when destinations are missing."
      exit 0
      ;;
    *)
      echo "Unknown arg: $1" >&2
      exit 1
      ;;
  esac
done

DEFAULTS="$ROOT/config/defaults"
# Release / checkout may keep defaults next to scripts when ROOT is a release tree
if [ ! -d "$DEFAULTS" ] && [ -d "$SCRIPT_DIR/../config/defaults" ]; then
  DEFAULTS="$(CDPATH= cd -- "$SCRIPT_DIR/../config/defaults" && pwd)"
fi

if [ ! -d "$DEFAULTS" ]; then
  echo "No config/defaults/ found — skip seed (jar saveDefaultConfig still applies)."
  exit 0
fi

seeded=0

copy_if_missing() {
  local src="$1"
  local dest="$2"
  if [ ! -f "$src" ]; then
    return 0
  fi
  if [ -e "$dest" ]; then
    return 0
  fi
  mkdir -p "$(dirname -- "$dest")"
  cp -f "$src" "$dest"
  echo "  seeded $(basename "$(dirname -- "$dest")")/$(basename "$dest")"
  seeded=$((seeded + 1))
}

echo "Seeding defaults into $ROOT …"

# Product server.properties (full profile — packs, ranks, Folia)
if [ -f "$DEFAULTS/server.properties" ]; then
  copy_if_missing "$DEFAULTS/server.properties" "$ROOT/config/server.properties"
elif [ -f "$ROOT/config/server.properties.example" ]; then
  copy_if_missing "$ROOT/config/server.properties.example" "$ROOT/config/server.properties"
fi

# Plugin configs (pre-Folia so saveDefaultConfig does not win with stale jar copies)
if [ -d "$DEFAULTS/plugins" ]; then
  while IFS= read -r -d '' src; do
    rel="${src#"$DEFAULTS/plugins/"}"
    copy_if_missing "$src" "$ROOT/plugins/$rel"
  done < <(find "$DEFAULTS/plugins" -type f \( -name 'config.yml' -o -name '*.yml' -o -name '*.properties' \) -print0)
fi

# YaP Link
mkdir -p "$ROOT/link-data"
if [ -f "$DEFAULTS/link.properties" ]; then
  copy_if_missing "$DEFAULTS/link.properties" "$ROOT/link-data/link.properties"
elif [ -f "$ROOT/link-data/link.properties.example" ]; then
  copy_if_missing "$ROOT/link-data/link.properties.example" "$ROOT/link-data/link.properties"
fi

if [ "$seeded" -eq 0 ]; then
  echo "  (nothing new — operator configs already present)"
else
  echo "  seeded $seeded file(s)"
fi

# Optional third-party jars (GPLv3) — fetched disabled until operator enables.
# Skip in CI/offline: YAP_SKIP_OPTIONAL_FETCH=1
if [ "${YAP_SKIP_OPTIONAL_FETCH:-}" != "1" ] && [ -x "$SCRIPT_DIR/fetch-grim.sh" ]; then
  if [ ! -f "$ROOT/plugins/grim.jar" ] && [ ! -f "$ROOT/plugins/grim.jar.disabled" ]; then
    echo "Fetching Grim AC (top-tier, disabled until ./scripts/grim-ac.sh enable) …"
    if "$SCRIPT_DIR/fetch-grim.sh" --disabled --root "$ROOT" --quiet; then
      echo "  grim.jar.disabled ready"
    else
      echo "  Grim fetch skipped (offline or Modrinth unavailable) — run ./scripts/fetch-grim.sh --disabled later"
    fi
  fi
fi

echo "Tip: ./configure-db.sh --server-id lobby  # after MariaDB is healthy"
echo "Tip: ./scripts/grim-ac.sh enable          # when you want Grim AC active"
