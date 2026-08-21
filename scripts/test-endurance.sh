#!/usr/bin/env bash
# Months-long readiness soak — drop into Konsole.
# Writes actionable HTML+JSON under logs/endurance/latest.html
# Usage: ./scripts/test-endurance.sh [seconds] [bots]
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"
yap_test_bootstrap

SECS="${1:-120}"
BOTS="${2:-64}"
mkdir -p "$ROOT/logs/endurance" "$ROOT/logs/jfr"
JFR="$ROOT/logs/jfr/endurance.jfr"

yap_banner "Endurance soak (seconds=$SECS bots=$BOTS)"
echo "Report → $ROOT/logs/endurance/latest.html"
echo "JFR    → $JFR"
echo ""

set +e
yap_gradle endurance \
  "-Dyap.endurance.seconds=$SECS" \
  "-Dyap.endurance.bots=$BOTS" \
  "-Dyap.endurance.idleSeconds=15" \
  "-Dyap.endurance.jfr=$JFR"
CODE=$?
set -e

echo ""
if [ -f "$ROOT/logs/endurance/latest.html" ]; then
  echo "Open: $ROOT/logs/endurance/latest.html"
  # Print FAIL/WARN lines from JSON if present
  if command -v python3 >/dev/null 2>&1 && [ -f "$ROOT/logs/endurance/latest.json" ]; then
    python3 - <<'PY' "$ROOT/logs/endurance/latest.json" || true
import json,sys
d=json.load(open(sys.argv[1]))
for f in d.get("findings",[]):
    print(f"[{f['severity']}] {f['code']}: {f['detail']}")
    if f['severity']!='OK':
        print(f"         → {f['fixHint']}")
PY
  fi
fi
yap_pause_end "$CODE"
exit "$CODE"
