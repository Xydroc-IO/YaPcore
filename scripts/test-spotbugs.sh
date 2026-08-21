#!/usr/bin/env bash
# Drop into Konsole — SpotBugs on sync packages.
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"
yap_test_bootstrap
yap_banner "SpotBugs (gradle spotbugsMain)"
set +e
yap_gradle spotbugsMain
CODE=$?
set -e
echo ""
echo "Report: $ROOT/build/reports/spotbugs/main.html"
yap_pause_end "$CODE"
exit "$CODE"
