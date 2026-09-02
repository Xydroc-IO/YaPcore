#!/usr/bin/env bash
# Trigger a heap dump for Eclipse MAT Leak Suspects analysis.
# Usage: ./scripts/heap-dump.sh [pid] [output.hprof]
# If pid omitted, uses yapcore.pid from the project root.

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

PID="${1:-}"
OUT="${2:-$ROOT/logs/jfr/yapcore_heap.hprof}"

if [ -z "$PID" ]; then
  if [ -f "$ROOT/yapcore.pid" ]; then
    PID="$(tr -d '[:space:]' <"$ROOT/yapcore.pid")"
  else
    echo "Usage: $0 <pid> [output.hprof]" >&2
    echo "No pid given and $ROOT/yapcore.pid missing." >&2
    exit 1
  fi
fi

mkdir -p "$(dirname "$OUT")"
echo "Dumping heap of pid=$PID → $OUT"
jcmd "$PID" GC.heap_dump "$OUT"
echo "Open in Eclipse Memory Analyzer (MAT) → Leak Suspects Report"
echo "Also useful: jcmd $PID JFR.dump name=1 filename=$ROOT/logs/jfr/live.jfr"
