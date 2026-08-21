#!/usr/bin/env bash
# Open the Test Lab GUI (buttons + live console).
# Drop into Konsole or: ./scripts/test-gui.sh
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"
yap_test_bootstrap

yap_banner "Test Lab GUI"
set +e
yap_gradle runTestLab
CODE=$?
set -e
yap_pause_end "$CODE"
exit "$CODE"
