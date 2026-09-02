#!/usr/bin/env bash
# Drop into Konsole — CMU Fray deterministic concurrency tests.
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"
yap_test_bootstrap
yap_banner "Fray concurrency tests (gradle frayTest)"
set +e
yap_gradle frayTest
CODE=$?
set -e
yap_pause_end "$CODE"
exit "$CODE"
