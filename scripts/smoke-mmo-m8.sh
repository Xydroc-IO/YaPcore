#!/usr/bin/env bash
# M8 smoke: yap-mechanics compile + unit tests + content baseline.
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

echo "== M8 unit tests =="
gradle :yap-mechanics-api:jar :mechanics-plugin:test --no-daemon -q

echo "== M8 content baseline =="
python3 "$ROOT/scripts/content/generate-mmo-baseline-pack.py"
"$ROOT/scripts/validate-mmo-content.sh"

echo "== M8 build + install =="
gradle :mechanics-plugin:installIntoPlugins :crafting-plugin:installIntoPlugins --no-daemon -q

if [ ! -f "$ROOT/plugins/yap-mechanics.jar" ]; then
  echo "FAIL: missing plugins/yap-mechanics.jar"
  exit 1
fi

echo "M8 smoke PASS"
