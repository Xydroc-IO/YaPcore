#!/usr/bin/env bash
# Validate bare-minimum MMO content counts in plugin resources.
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

count_quests() {
  rg -o '^\s{2}[a-z0-9_]+:\s*$' "$ROOT/yap-first-party/gameplay/mmo-content-plugin/src/main/resources/quests" \
    --glob '*.yml' 2>/dev/null | wc -l
}

count_bosses() {
  rg '^\s{2}[a-z0-9_]+:\s*$' "$ROOT/yap-first-party/gameplay/mmo-content-plugin/src/main/resources/bosses" \
    --glob '*.yml' 2>/dev/null | wc -l
}

count_recipes() {
  rg '^\s{2}[a-z0-9_]+:\s*$' "$ROOT/yap-first-party/gameplay/crafting-plugin/src/main/resources/recipes" \
    --glob '*.yml' 2>/dev/null | wc -l
}

Q=$(count_quests | tr -d ' ')
B=$(count_bosses | tr -d ' ')
R=$(count_recipes | tr -d ' ')

echo "Content counts: quests=$Q bosses=$B recipes=$R"

fail=0
[ "$Q" -ge 100 ] || { echo "FAIL: need >= 100 quests (compendium)"; fail=1; }
[ "$B" -ge 20 ] || { echo "FAIL: need >= 20 bosses"; fail=1; }
[ "$R" -ge 75 ] || { echo "FAIL: need >= 75 recipes"; fail=1; }
if [ "$R" -lt 150 ]; then
  echo "WARN: below 150 recipe stretch goal (have $R)"
fi

if [ "$fail" -ne 0 ]; then
  echo "Regenerate: python3 scripts/content/generate-mmo-baseline-pack.py"
  exit 1
fi
echo "Content baseline OK"
