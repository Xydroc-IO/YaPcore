#!/usr/bin/env bash
# Gate: YaP MSPT must not regress vs stock Folia beyond the compare-folia tie band.
# Usage:
#   ./scripts/bench/check-mspt-regression.sh stock.json yap.json
# Exit: 0 pass/tie/not-citeable(optional), 1 regression, 3 fairness fail
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
COMPARE="$ROOT/scripts/bench/compare-folia.py"

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 stock-folia.json yap-folia.json" >&2
  exit 2
fi

STOCK="$1"
YAP="$2"
[ -f "$STOCK" ] || { echo "missing stock JSON: $STOCK" >&2; exit 2; }
[ -f "$YAP" ] || { echo "missing yap JSON: $YAP" >&2; exit 2; }

set +e
python3 "$COMPARE" "$STOCK" "$YAP"
rc=$?
set -e

case "$rc" in
  0)
    echo "PASS: MSPT win or within +2% tie band"
    exit 0
    ;;
  1)
    echo "FAIL: YaP MSPT slower than stock Folia" >&2
    exit 1
    ;;
  3)
    echo "FAIL: fairness — comparison invalid" >&2
    exit 3
    ;;
  4)
    # Noise / too-light load — fail when citeability is required.
    if [ "${YAP_MSPT_STRICT_CITEABLE:-0}" = "1" ] || [ "${YAP_MSPT_REQUIRE_CITEABLE:-0}" = "1" ]; then
      echo "FAIL: not citeable (delta too small or MSPT too low)" >&2
      exit 4
    fi
    echo "WARN: not citeable (load too light) — treated as pass"
    exit 0
    ;;
  *)
    echo "FAIL: compare-folia exited $rc" >&2
    exit "$rc"
    ;;
esac
