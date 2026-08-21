#!/usr/bin/env bash
# Drop into Konsole — in-process boundary stress (headless bot stand-in).
# Usage: ./scripts/test-stress.sh [bots] [seconds]
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"
yap_test_bootstrap
BOTS="${1:-32}"
SECS="${2:-30}"
yap_banner "Boundary stress (bots=$BOTS seconds=$SECS)"
set +e
yap_gradle boundaryStress \
  "-Dyap.stress.bots=$BOTS" \
  "-Dyap.stress.seconds=$SECS" \
  "-Dyap.stress.handoffs=999999"
CODE=$?
set -e
yap_pause_end "$CODE"
exit "$CODE"
