#!/usr/bin/env bash
# Fail if any first-party / chassis Java source exceeds 500 lines (CONTRIBUTING domain rule).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAX=500
fail=0
count=0

while IFS= read -r -d '' f; do
  n=$(wc -l < "$f")
  count=$((count + 1))
  if [ "$n" -gt "$MAX" ]; then
    printf 'OVER %s: %s lines (max %s)\n' "${f#"$ROOT/"}" "$n" "$MAX"
    fail=1
  fi
done < <(find "$ROOT/src/main/java" "$ROOT/yap-first-party" \
  -name '*.java' -not -path '*/build/*' -print0 2>/dev/null | sort -z)

if [ "$fail" -ne 0 ]; then
  echo "Domain line limit failed: split files over ${MAX} lines (see CONTRIBUTING.md)." >&2
  exit 1
fi
echo "Domain line limits OK (${count} Java files, max ${MAX} lines each)."
