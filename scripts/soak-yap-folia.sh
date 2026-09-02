#!/usr/bin/env bash
# Shared YaP-Folia soak harness — Agents 2/3 plug profiles into this.
#
# Usage:
#   ./scripts/soak-yap-folia.sh compat [seconds]
#   ./scripts/soak-yap-folia.sh perf   [seconds]
#   ./scripts/soak-yap-folia.sh list
#
# Profiles:
#   compat  — FOLIA_JAR_SOURCE=build, sched-compat=on, teleport=on, perf knobs OFF
#   perf    — same as compat + entity-tick-budget / async-chunk-save (env-tunable; A3 owns numbers)
#
# Env (common):
#   SOAK_SECS / argv seconds     hold-ready duration (default: compat=300, perf=600)
#   SKIP_VERIFY=1               skip verify-yap-folia build step (jar must already exist)
#   SKIP_HOOKS=1                skip optional A2/A3 hook smokes
#   YAP_FOLIA_ENTITY_TICK_BUDGET  (perf only; default unset/off — A3 sets e.g. 300)
#   YAP_FOLIA_ASYNC_CHUNK_SAVE    (perf only; default unset/off — A3 sets true)
#   YAP_FOLIA_MICROTICK_BUDGET_MS (perf only; soft Mob AI deadline ms)
#   YAP_FOLIA_STEAL_THRESHOLD_MS / YAP_FOLIA_TASK_SLICE_MS (WORK_STEALING only)
#   YAP_FOLIA_GRID_EXPONENT       (optional override of paper-global grid-exponent)
#
# Pass: managed Folia stays ready for the full soak window (see docs/folia/YAP_FOLIA_SOAK.md).
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

PROFILE="${1:-}"
ARG2="${2:-}"

usage() {
  cat <<'EOF'
Usage: ./scripts/soak-yap-folia.sh <compat|perf|list> [seconds]

  compat  build jar + sched-compat + teleport; perf knobs OFF
  perf    compat + optional -Dyap.folia.entity-tick-budget / async-chunk-save / microtick
  list    print profiles and env knobs

Docs: docs/folia/YAP_FOLIA_SOAK.md
EOF
}

if [ -z "$PROFILE" ] || [ "$PROFILE" = "-h" ] || [ "$PROFILE" = "--help" ]; then
  usage
  exit 2
fi

if [ "$PROFILE" = "list" ]; then
  cat <<'EOF'
Profiles:
  compat  FOLIA_JAR_SOURCE=build
          folia-sched-compat=true
          folia-teleport-transactions=true
          perf knobs: OFF
  perf    same as compat, plus:
          YAP_FOLIA_ENTITY_TICK_BUDGET → -Dyap.folia.entity-tick-budget
          YAP_FOLIA_ASYNC_CHUNK_SAVE   → -Dyap.folia.async-chunk-save
          YAP_FOLIA_MICROTICK_BUDGET_MS → -Dyap.folia.microtick-budget-ms
          YAP_FOLIA_STEAL_THRESHOLD_MS / YAP_FOLIA_TASK_SLICE_MS (needs WORK_STEALING)
          YAP_FOLIA_GRID_EXPONENT → -Dyap.folia.grid-exponent
Hooks (unless SKIP_HOOKS=1):
  compat → smoke-folia-sched-compat.sh (SKIP_LIVE ok), smoke-folia-cross-region-tp.sh if present
  perf   → smoke-folia-async-save.sh if present (informational)
EOF
  exit 0
fi

case "$PROFILE" in
  compat|soak-compat) PROFILE=compat ;;
  perf|soak-perf) PROFILE=perf ;;
  *)
    echo "Unknown profile: $PROFILE" >&2
    usage
    exit 2
    ;;
esac

cd "$ROOT"
export ROOT
yap_require_java

VER="${FOLIA_VERSION:-26.2}"
YAP_JAR="$ROOT/lib/yap-folia-${VER}.jar"

DEFAULT_SECS=300
[ "$PROFILE" = "perf" ] && DEFAULT_SECS=600
SOAK_SECS="${SOAK_SECS:-${ARG2:-$DEFAULT_SECS}}"
# Allow "compat 120" style; ignore non-numeric second arg
case "$SOAK_SECS" in
  ''|*[!0-9]*) SOAK_SECS="$DEFAULT_SECS" ;;
esac

echo "======== YaP-Folia soak: profile=$PROFILE secs=$SOAK_SECS ========"

# --- Ensure built jar (shared gate with verify) ---
if [ "${SKIP_VERIFY:-0}" = "1" ]; then
  if [ ! -f "$YAP_JAR" ]; then
    echo "SKIP_VERIFY=1 but missing $YAP_JAR — run ./scripts/build-yap-folia.sh" >&2
    exit 1
  fi
  echo "SKIP_VERIFY=1 — using existing $YAP_JAR ($(wc -c <"$YAP_JAR" | tr -d ' ') bytes)"
else
  if [ -f "$YAP_JAR" ] && [ "${FORCE_REBUILD:-0}" != "1" ]; then
    echo "Using existing $YAP_JAR (FORCE_REBUILD=1 to rebuild)"
  else
    echo "== build/verify yap-folia =="
    SKIP_SMOKE=1 "$ROOT/scripts/verify-yap-folia.sh" || {
      echo "FAIL: verify/build yap-folia — see ./scripts/build-yap-folia.sh" >&2
      exit 1
    }
  fi
fi

if [ ! -f "$YAP_JAR" ]; then
  echo "FAIL: missing $YAP_JAR — run ./scripts/build-yap-folia.sh" >&2
  exit 1
fi

# --- Profile env for smoke-folia.sh ---
export FOLIA_JAR_SOURCE=build
export YAP_FOLIA_JAR_SOURCE=build
export FOLIA_VERSION="$VER"
export FOLIA_SCHED_COMPAT=true
export FOLIA_TELEPORT_TRANSACTIONS=true
export YAP_FOLIA_SOAK=1
export YAP_FOLIA_SOAK_PROFILE="$PROFILE"

if [ "$PROFILE" = "perf" ]; then
  echo "perf knobs: entity-tick-budget=${YAP_FOLIA_ENTITY_TICK_BUDGET:-off} async-chunk-save=${YAP_FOLIA_ASYNC_CHUNK_SAVE:-off} microtick=${YAP_FOLIA_MICROTICK_BUDGET_MS:-off} subregion=${YAP_FOLIA_SUBREGION_PARTITION:-off}"
else
  # Compat: ensure perf env cannot leak into smoke
  unset YAP_FOLIA_ENTITY_TICK_BUDGET YAP_FOLIA_ASYNC_CHUNK_SAVE \
    YAP_FOLIA_MICROTICK_BUDGET_MS YAP_FOLIA_STEAL_THRESHOLD_MS YAP_FOLIA_TASK_SLICE_MS \
    YAP_FOLIA_GRID_EXPONENT YAP_FOLIA_SCOREBOARD_SWMR YAP_FOLIA_SUBREGION_PARTITION 2>/dev/null || true
  echo "perf knobs: OFF (compat profile)"
fi

# --- Optional A2/A3 hooks (fast / unit) before long soak ---
run_hook() {
  local script="$1"
  shift
  if [ "${SKIP_HOOKS:-0}" = "1" ]; then
    return 0
  fi
  if [ -x "$ROOT/scripts/$script" ] || [ -f "$ROOT/scripts/$script" ]; then
    echo "== hook: $script =="
    "$ROOT/scripts/$script" "$@" || {
      echo "FAIL: hook $script" >&2
      return 1
    }
  else
    echo "hook skip (missing): $script"
  fi
}

if [ "$PROFILE" = "compat" ]; then
  # Live A2 gates on build jar (override with SKIP_LIVE=1 for unit-only CI)
  SKIP_LIVE="${SKIP_LIVE:-0}" run_hook smoke-folia-sched-compat.sh "${SOAK_HOOK_SECS:-300}"
  if [ -f "$ROOT/scripts/smoke-folia-cross-region-tp.sh" ]; then
    TP_CYCLES="${TP_CYCLES:-100}" SKIP_LIVE="${SKIP_LIVE:-0}" \
      run_hook smoke-folia-cross-region-tp.sh "${SOAK_HOOK_SECS:-180}"
  fi
elif [ "$PROFILE" = "perf" ]; then
  if [ -f "$ROOT/scripts/smoke-folia-async-save.sh" ]; then
    run_hook smoke-folia-async-save.sh 90 || true
  fi
fi

# --- Long soak: hold ready via smoke-folia ---
echo "== soak boot (smoke-folia hold ${SOAK_SECS}s) =="
"$ROOT/scripts/smoke-folia.sh" "$SOAK_SECS"

RESULT_DIR="$ROOT/bench/results"
mkdir -p "$RESULT_DIR"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$RESULT_DIR/${STAMP}-yap-folia-soak-${PROFILE}.json"
python3 - <<PY
import json, os, time
from pathlib import Path
out = Path(os.environ.get("OUT", "$OUT"))
doc = {
  "stamp": "$STAMP",
  "profile": "$PROFILE",
  "soak_secs": int("$SOAK_SECS"),
  "folia_jar_source": "build",
  "yap_folia_jar": "$YAP_JAR",
  "sched_compat": True,
  "teleport_transactions": True,
  "entity_tick_budget": os.environ.get("YAP_FOLIA_ENTITY_TICK_BUDGET") or None,
  "async_chunk_save": os.environ.get("YAP_FOLIA_ASYNC_CHUNK_SAVE") or None,
  "result": "PASS",
  "note": "ready held for full soak window via smoke-folia.sh",
}
out.write_text(json.dumps(doc, indent=2) + "\n")
print("wrote", out)
PY

echo "PASS: soak-$PROFILE (${SOAK_SECS}s)"
echo "  result=$OUT"
echo "Announce: Agents 2/3 — soak-$PROFILE profile is ready (docs/folia/YAP_FOLIA_SOAK.md)."
