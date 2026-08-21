#!/usr/bin/env bash
# Drop into Konsole — JCStress AtomicLeaseManager suite.
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"
yap_test_bootstrap
yap_banner "JCStress (gradle jcstress)"
set +e
yap_gradle jcstress
CODE=$?
set -e
yap_pause_end "$CODE"
exit "$CODE"
