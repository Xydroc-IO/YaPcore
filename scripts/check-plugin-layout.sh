#!/usr/bin/env bash
# Verify unified plugins layout: paper-dir/plugins → ../plugins (or equivalent).
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"
yap_load_config
yap_ensure_unified_plugins

PAPER_PLUGINS="$ROOT/$PAPER_DIR/plugins"
YAP_PLUGINS="$ROOT/plugins"

echo "Plugin layout check"
echo "  Unified plugins:   $YAP_PLUGINS"
echo "  Paper plugins dir: $PAPER_PLUGINS"
echo "  game-authority:    $GAME_AUTHORITY"

if [ ! -L "$PAPER_PLUGINS" ]; then
  echo "FAIL: $PAPER_PLUGINS is not a symlink to ../plugins"
  exit 1
fi

target="$(readlink -f "$PAPER_PLUGINS" 2>/dev/null || readlink "$PAPER_PLUGINS")"
unified_real="$(readlink -f "$YAP_PLUGINS" 2>/dev/null || echo "$YAP_PLUGINS")"
if [ -d "$PAPER_PLUGINS" ]; then
  paper_real="$(readlink -f "$PAPER_PLUGINS")"
  if [ "$paper_real" != "$unified_real" ]; then
    echo "FAIL: symlink resolves to $paper_real — expected $unified_real"
    exit 1
  fi
fi

count=0
shopt -s nullglob
for jar in "$YAP_PLUGINS"/*.jar; do
  count=$((count + 1))
done
shopt -u nullglob
echo "  jars in plugins/:  $count"
echo "OK — all plugins go in plugins/ (Paper loads via $PAPER_DIR/plugins → ../plugins)"
exit 0
