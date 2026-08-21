#!/usr/bin/env bash
# Soak with JFR — drop into Konsole.
# Usage: ./scripts/soak-jfr.sh [--seconds=N] [--bots=N]
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"
yap_test_bootstrap

SECONDS_RUN="${YAP_SOAK_SECONDS:-300}"
BOTS="${YAP_SOAK_BOTS:-100}"

for arg in "$@"; do
  case "$arg" in
    --seconds=*) SECONDS_RUN="${arg#*=}" ;;
    --bots=*) BOTS="${arg#*=}" ;;
    -h|--help)
      echo "Usage: $0 [--seconds=N] [--bots=N]"
      exit 0
      ;;
  esac
done

mkdir -p "$ROOT/logs/jfr"
JFR_FILE="$ROOT/logs/jfr/yapcore_soak.jfr"

yap_banner "Soak + JFR (bots=$BOTS seconds=$SECONDS_RUN)"
echo "JFR → $JFR_FILE"
echo ""

set +e
yap_gradle boundaryStress \
  "-Dyap.stress.bots=$BOTS" \
  "-Dyap.stress.seconds=$SECONDS_RUN" \
  "-Dyap.stress.handoffs=999999999" \
  "-Dyap.stress.jfr=$JFR_FILE"
CODE=$?
set -e

echo ""
echo "Open in JDK Mission Control → Old Object Sample:"
echo "  $JFR_FILE"
yap_pause_end "$CODE"
exit "$CODE"
