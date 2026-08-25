#!/usr/bin/env bash
# Phase 3.4 — Folia vanilla scoreboard/team/bossbar smoke (SWMR).
# Usage: ./scripts/smoke-folia-scoreboard.sh
# Env:
#   SKIP_LIVE=1              — compile/install only
#   FOLIA_JAR_SOURCE=build   — use lib/yap-folia (default when jar present)
#   EXPECT_FAIL=0|1          — default 0 for yap-folia+SWMR, 1 for stock
#   SCOREBOARD_SWMR=true     — pass -Dyap.folia.scoreboard-swmr (default true for build)
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"
export ROOT
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"
yap_require_java

VER="${FOLIA_VERSION:-26.2}"

echo "== build scoreboard smoke =="
gradle :scoreboard-smoke-plugin:installIntoPlugins --no-daemon -q
# Also mirror into top-level plugins/ for operators
SMOKE_BUILT="$(find "$ROOT/yap-first-party/dev/scoreboard-smoke-plugin/build/libs" -name 'yap-scoreboard-smoke.jar' | head -1)"
if [ -n "$SMOKE_BUILT" ]; then
  mkdir -p "$ROOT/plugins" "$ROOT/server/plugins"
  /bin/cp -f "$SMOKE_BUILT" "$ROOT/plugins/yap-scoreboard-smoke.jar"
  /bin/cp -f "$SMOKE_BUILT" "$ROOT/server/plugins/yap-scoreboard-smoke.jar"
fi

if [ "${SKIP_LIVE:-0}" = "1" ]; then
  echo "SKIP_LIVE=1 — scoreboard smoke PASS (compile only)"
  exit 0
fi

YAP_CAND=""
for c in "$ROOT/lib/yap-folia-${VER}.jar" "$ROOT/server/lib/yap-folia-${VER}.jar"; do
  [ -f "$c" ] && YAP_CAND="$c" && break
done
STOCK_CAND=""
for c in "$ROOT/lib/folia-${VER}.jar" "$ROOT/server/lib/folia-${VER}.jar"; do
  [ -f "$c" ] && STOCK_CAND="$c" && break
done

SRC="${FOLIA_JAR_SOURCE:-}"
if [ -z "$SRC" ]; then
  if [ -n "$YAP_CAND" ]; then SRC=build; else SRC=fetch; fi
fi

if [ "$SRC" = "build" ]; then
  FOLIA_SRC="$YAP_CAND"
  EXPECT_FAIL="${EXPECT_FAIL:-0}"
  SCOREBOARD_SWMR="${SCOREBOARD_SWMR:-true}"
else
  FOLIA_SRC="$STOCK_CAND"
  EXPECT_FAIL="${EXPECT_FAIL:-1}"
  SCOREBOARD_SWMR="${SCOREBOARD_SWMR:-false}"
fi

if [ -z "$FOLIA_SRC" ] || [ ! -f "$FOLIA_SRC" ]; then
  echo "FAIL: Folia jar missing for FOLIA_JAR_SOURCE=$SRC" >&2
  exit 1
fi

PLUGIN_JAR=""
for c in \
  "$ROOT/plugins/yap-scoreboard-smoke.jar" \
  "$ROOT/server/plugins/yap-scoreboard-smoke.jar" \
  "$SMOKE_BUILT"; do
  if [ -n "$c" ] && [ -f "$c" ]; then
    PLUGIN_JAR="$c"
    break
  fi
done
if [ -z "$PLUGIN_JAR" ]; then
  echo "FAIL: yap-scoreboard-smoke.jar not built" >&2
  exit 1
fi

WORK="$ROOT/bench/workdir-scoreboard-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/plugins"
/bin/cp -f "$FOLIA_SRC" "$WORK/server.jar"
/bin/cp -f "$PLUGIN_JAR" "$WORK/plugins/yap-scoreboard-smoke.jar"
printf 'eula=true\n' >"$WORK/eula.txt"
cat >"$WORK/server.properties" <<EOF
server-port=25591
online-mode=false
max-players=5
motd=YaP scoreboard smoke
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$WORK/smoke.log"
echo "Booting scoreboard smoke (src=$SRC swmr=$SCOREBOARD_SWMR expect_fail=$EXPECT_FAIL)…"
(
  cd "$WORK"
  "$JAVA_BIN" -Xms512M -Xmx1536M \
    -Dyap.scoreboard.smoke.expect_fail="$EXPECT_FAIL" \
    -Dyap.folia.scoreboard-swmr="$SCOREBOARD_SWMR" \
    -jar server.jar --nogui
) >"$LOG" 2>&1 &
PID=$!
cleanup() { kill "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; }
trap cleanup EXIT

ready=0
for _ in $(seq 1 120); do
  if grep -q "SCOREBOARD_SMOKE OK" "$LOG" 2>/dev/null; then
    ready=1
    break
  fi
  if grep -q "SCOREBOARD_SMOKE BAD" "$LOG" 2>/dev/null; then
    echo "FAIL: scoreboard smoke gate"
    tail -n 50 "$LOG" || true
    exit 1
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    if grep -q "SCOREBOARD_SMOKE OK" "$LOG" 2>/dev/null; then
      ready=1
      break
    fi
    echo "Server exited early:"
    tail -n 60 "$LOG" || true
    exit 1
  fi
  sleep 1
done

if [ "$ready" -eq 0 ]; then
  echo "Timed out waiting for SCOREBOARD_SMOKE"
  tail -n 80 "$LOG" || true
  exit 1
fi

echo "smoke-folia-scoreboard PASS (SWMR=$SCOREBOARD_SWMR expect_fail=$EXPECT_FAIL)"
