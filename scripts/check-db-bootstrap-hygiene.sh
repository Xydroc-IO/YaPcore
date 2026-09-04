#!/usr/bin/env bash
# Fail if plugin db packages reintroduce raw YapDbProvider.find + HikariConfig open blocks
# instead of YapDbBootstrap (except yap-db-api itself).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bad=0
while IFS= read -r -d '' f; do
  if grep -q 'YapDbProvider\.find' "$f" && grep -q 'new HikariConfig' "$f"; then
    echo "legacy DB open path (use YapDbBootstrap): $f" >&2
    bad=1
  fi
done < <(find "$ROOT/yap-first-party" -path '*/db/*.java' -print0 2>/dev/null)

if [[ "$bad" -ne 0 ]]; then
  exit 1
fi
echo "DB bootstrap hygiene OK"
