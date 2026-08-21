#!/usr/bin/env bash
# Summarize JFR CPU samples for Leaf-gap comparison.
# Usage: ./scripts/bench/summarize-jfr.sh bench/profiles/foo.jfr [limit]
set -euo pipefail
JFR="${1:?jfr path}"
LIMIT="${2:-40}"
if [[ ! -f "$JFR" ]]; then
  echo "missing $JFR" >&2
  exit 1
fi
echo "=== $JFR ==="
# Prefer jfr print view if available; fall back to summary events
if jfr print --events jdk.ExecutionSample "$JFR" 2>/dev/null | head -1 | grep -q .; then
  jfr print --events jdk.ExecutionSample "$JFR" 2>/dev/null \
    | awk '
      /stackTrace/ {grab=1; next}
      grab && /^[[:space:]]+[a-zA-Z0-9_$.]+\(/ {
        line=$0
        sub(/^[[:space:]]+/,"",line)
        # top frame only
        if (!seen) { top[line]++; n++; seen=1 }
      }
      /^$/ { seen=0; grab=0 }
      END {
        for (k in top) print top[k], k
      }
    ' | sort -rn | head -n "$LIMIT"
else
  jfr summary "$JFR" 2>/dev/null | head -n 80
fi
