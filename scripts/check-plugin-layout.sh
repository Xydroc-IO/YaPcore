#!/usr/bin/env bash
# Verify unified plugins layout: folia-dir/plugins → ../plugins (product path).
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"
yap_load_config
yap_ensure_unified_plugins

KERNEL_DIR="$(yap_active_kernel_dir)"
KERNEL_PLUGINS="$ROOT/$KERNEL_DIR/plugins"
YAP_PLUGINS="$ROOT/plugins"

echo "Plugin layout check"
echo "  Unified plugins:    $YAP_PLUGINS"
echo "  Kernel plugins dir: $KERNEL_PLUGINS"
echo "  game-authority:     $GAME_AUTHORITY"
echo "  kernel-dir:         $KERNEL_DIR"

if [ ! -L "$KERNEL_PLUGINS" ]; then
  echo "FAIL: $KERNEL_PLUGINS is not a symlink to ../plugins"
  exit 1
fi

unified_real="$(readlink -f "$YAP_PLUGINS" 2>/dev/null || echo "$YAP_PLUGINS")"
kernel_real="$(readlink -f "$KERNEL_PLUGINS")"
if [ "$kernel_real" != "$unified_real" ]; then
  echo "FAIL: symlink resolves to $kernel_real — expected $unified_real"
  exit 1
fi

count=0
shopt -s nullglob
for jar in "$YAP_PLUGINS"/*.jar; do
  count=$((count + 1))
done
shopt -u nullglob
echo "  jars in plugins/:   $count"
echo "OK — all plugins go in plugins/ ($KERNEL_DIR/plugins → ../plugins)"

# Phase 16: warn on known-bad third-party jars (non-fatal)
MATRIX="$ROOT/src/main/resources/plugin-compat-matrix.json"
if [ -f "$MATRIX" ]; then
  WARN=0
  shopt -s nullglob
  for jar in "$YAP_PLUGINS"/*.jar; do
    base="$(basename "$jar")"
    lower="$(echo "$base" | tr '[:upper:]' '[:lower:]')"
    # Match broken entries with common patterns
    case "$lower" in
      luckperms*|essentialsx*|essentials-*.jar|coreprotect*|worldedit*|worldguard*|viaversion*|viabackwards*|viarewind*|geyser*|floodgate-spigot*|velocity*.jar|discordsrv*|dynmap*|bluemap*|tab-*.jar|citizens*)
        entry="$(grep -i "$(echo "$base" | sed 's/[*.]/ /g' | awk '{print $1}')" "$MATRIX" 2>/dev/null | head -1 || true)"
        alt="$(echo "$entry" | sed -n 's/.*"nativeAlternative":"\([^"]*\)".*/\1/p')"
        note="$(echo "$entry" | sed -n 's/.*"note":"\([^"]*\)".*/\1/p')"
        echo "WARN: $base — known incompatible${alt:+; use $alt}${note:+ ($note)}"
        WARN=$((WARN + 1))
        ;;
    esac
  done
  shopt -u nullglob
  if [ "$WARN" -gt 0 ]; then
    echo "  compat warnings:   $WARN (see docs/PLUGIN_COMPAT_MATRIX.md)"
  fi
fi

exit 0
