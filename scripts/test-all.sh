#!/usr/bin/env bash
# Drop into Konsole — SpotBugs + unit + Fray (CI-style).
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"
yap_test_bootstrap
yap_banner "verifyConcurrency (SpotBugs + test + frayTest)"
set +e
yap_gradle verifyConcurrency
CODE=$?
set -e
yap_pause_end "$CODE"
exit "$CODE"
