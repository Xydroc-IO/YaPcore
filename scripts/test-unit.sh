#!/usr/bin/env bash
# Drop this into Konsole (or: ./scripts/test-unit.sh) — runs JUnit unit tests.
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"
yap_test_bootstrap
yap_banner "Unit tests (gradle test)"
set +e
yap_gradle test
CODE=$?
set -e
yap_pause_end "$CODE"
exit "$CODE"
