#!/usr/bin/env bash
# Enable or disable fetched Grim AC (installed as grim.jar.disabled until enabled).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PLUGINS="$ROOT/plugins"
ACTIVE="$PLUGINS/grim.jar"
DISABLED="$PLUGINS/grim.jar.disabled"
GUARD_CFG="$PLUGINS/YaPGuard/config.yml"

usage() {
  cat <<EOF
Usage: $0 {status|enable|disable} [--root DIR]

Grim AC is downloaded on first setup as grim.jar.disabled (not loaded by YaP-Folia).
Competitive / PvP networks: use 'enable', then restart YaP-Folia.
YaPGuard alone is not gold-standard AC — see docs/ops/ANTICHEAT.md.

  status   — show whether Grim is missing, downloaded, or enabled
  enable   — load grim.jar + turn off YaPGuard movement checks (avoids double punishment); restart Folia
  disable  — rename back to grim.jar.disabled (YaPGuard stays as configured)

See docs/ops/GRIM.md
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --root)
      ROOT="$(cd "$2" && pwd)"
      PLUGINS="$ROOT/plugins"
      ACTIVE="$PLUGINS/grim.jar"
      DISABLED="$PLUGINS/grim.jar.disabled"
      GUARD_CFG="$PLUGINS/YaPGuard/config.yml"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    status|enable|disable)
      CMD="$1"
      shift
      ;;
    *)
      echo "Unknown arg: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

CMD="${CMD:-status}"

disable_yapguard_checks() {
  if [ ! -f "$GUARD_CFG" ]; then
    echo "  YaPGuard config not found — skip (Grim-only is fine)"
    return 0
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    echo "  Tip: disable YaPGuard checks in $GUARD_CFG manually"
    return 0
  fi
  python3 - "$GUARD_CFG" <<'PY'
import re, sys
from pathlib import Path
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
orig = text
for key in ("fly", "speed", "reach", "scaffold"):
    pat = re.compile(rf"(?m)^([ \t]*{key}:[ \t]*\n[ \t]*enabled:\s*).*$")
    if pat.search(text):
        text = pat.sub(r"\g<1>false", text, count=1)
if text != orig:
    path.write_text(text, encoding="utf-8")
    print("  YaPGuard movement checks → off (alerts unchanged)")
else:
    print("  YaPGuard config unchanged")
PY
}

case "$CMD" in
  status)
    if [ -f "$ACTIVE" ]; then
      echo "Grim AC: enabled (grim.jar) — restart YaP-Folia after changes"
      exit 0
    fi
    if [ -f "$DISABLED" ]; then
      echo "Grim AC: downloaded, disabled (grim.jar.disabled)"
      echo "  Enable: $0 enable"
      exit 0
    fi
    echo "Grim AC: not installed"
    echo "  Run: ./scripts/seed-defaults.sh  (or ./scripts/fetch-grim.sh --disabled)"
    exit 1
    ;;
  enable)
    if [ -f "$ACTIVE" ]; then
      echo "Grim AC already enabled ($ACTIVE)"
      exit 0
    fi
    if [ ! -f "$DISABLED" ]; then
      echo "Missing $DISABLED — run ./scripts/seed-defaults.sh first" >&2
      exit 1
    fi
    mv "$DISABLED" "$ACTIVE"
    echo "Enabled Grim AC → $ACTIVE"
    disable_yapguard_checks
    echo "Restart YaP-Folia to load Grim. Docs: docs/ops/GRIM.md"
    ;;
  disable)
    if [ -f "$DISABLED" ]; then
      echo "Grim AC already disabled ($DISABLED)"
      exit 0
    fi
    if [ ! -f "$ACTIVE" ]; then
      echo "Grim AC not enabled (nothing to disable)" >&2
      exit 1
    fi
    mv "$ACTIVE" "$DISABLED"
    echo "Disabled Grim AC → $DISABLED"
    echo "Restart YaP-Folia. Re-enable YaPGuard checks in $GUARD_CFG if needed."
    ;;
esac
