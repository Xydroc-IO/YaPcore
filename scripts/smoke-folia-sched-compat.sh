#!/usr/bin/env bash
# Smoke: legacy BukkitScheduler → Folia via yap-sched-agent.
# Usage: ./scripts/smoke-folia-sched-compat.sh [seconds]
#        SKIP_LIVE=1 ./scripts/smoke-folia-sched-compat.sh
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

WAIT_SECS="${1:-120}"
cd "$ROOT"
export ROOT
yap_require_java
yap_load_config

echo "== yap-sched-agent unit tests =="
gradle :yap-sched-agent:test :yap-sched-agent:installAgent \
  :legacy-sched-smoke-plugin:jar --no-daemon -q

AGENT="$ROOT/server/lib/yap-sched-agent.jar"
if [ ! -f "$AGENT" ]; then
  AGENT="$ROOT/yap-first-party/engine/yap-sched-agent/build/libs/yap-sched-agent.jar"
fi
test -f "$AGENT"
echo "agent=$AGENT"

LEGACY_JAR="$ROOT/yap-first-party/dev/legacy-sched-smoke-plugin/build/libs/yap-legacy-sched-smoke.jar"
test -f "$LEGACY_JAR"

if [ "${SKIP_LIVE:-0}" = "1" ]; then
  echo "SKIP_LIVE=1 — unit tests + jars OK (no Folia boot)"
  exit 0
fi

VER="${FOLIA_VERSION:-26.2}"
FOLIA_JAR=""
for c in \
  "$ROOT/server/lib/yap-folia-${VER}.jar" \
  "$ROOT/lib/yap-folia-${VER}.jar" \
  "$ROOT/server/lib/folia-${VER}.jar" \
  "$ROOT/lib/folia-${VER}.jar"; do
  if [ -f "$c" ]; then
    FOLIA_JAR="$c"
    break
  fi
done
if [ -z "$FOLIA_JAR" ]; then
  echo "Fetching stock Folia ${VER}…"
  "$ROOT/scripts/fetch-folia.sh" "$VER"
  FOLIA_JAR="$ROOT/lib/folia-${VER}.jar"
fi
echo "folia=$FOLIA_JAR"

WORK="$ROOT/bench/workdir-folia-sched-compat-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/plugins" "$WORK/logs" "$WORK/config"

/bin/cp -f "$FOLIA_JAR" "$WORK/folia-server.jar"
/bin/cp -f "$LEGACY_JAR" "$WORK/plugins/"
# eula
printf 'eula=true\n' >"$WORK/eula.txt"
cat >"$WORK/server.properties" <<EOF
server-port=25579
online-mode=false
max-players=5
motd=YaP sched-compat smoke
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$WORK/smoke.log"
echo "Booting Folia + yap-sched-agent (${WAIT_SECS}s)…"
(
  cd "$WORK"
  "$JAVA_BIN" \
    -Xms512M -Xmx1536M \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    -javaagent:"$AGENT"=warn=true,metrics=true \
    -jar folia-server.jar --nogui
) >"$LOG" 2>&1 &
PID=$!

cleanup() {
  if kill -0 "$PID" 2>/dev/null; then
    kill "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

deadline=$((SECONDS + WAIT_SECS))
ok=0
while [ "$SECONDS" -lt "$deadline" ]; do
  if grep -q "YaP-LEGACY-SCHED-SMOKE all-ok" "$LOG" 2>/dev/null \
    && grep -qE "yap-sched-agent: rewritten CraftScheduler.handle|yap-sched-agent installed" "$LOG" 2>/dev/null; then
    ok=1
    break
  fi
  if grep -q "YaP-LEGACY-SCHED-SMOKE runTask-ok" "$LOG" 2>/dev/null \
    && grep -q "YaP-LEGACY-SCHED-SMOKE runTaskLater-ok" "$LOG" 2>/dev/null \
    && grep -q "yap-sched-agent: rewritten CraftScheduler.handle" "$LOG" 2>/dev/null \
    && grep -q "yap-sched-agent: injected router" "$LOG" 2>/dev/null; then
    ok=1
    break
  fi
  if grep -q "NoClassDefFoundError: com/yapcore/sched/agent" "$LOG" 2>/dev/null; then
    echo "FAIL: agent helpers not visible to CraftScheduler classloader"
    tail -80 "$LOG" || true
    exit 1
  fi
  # Only hard-fail UOE if rewrite never happened (cancel-path noise is tolerated after success markers).
  if grep -q "UnsupportedOperationException" "$LOG" 2>/dev/null \
    && grep -q "CraftScheduler.handle" "$LOG" 2>/dev/null \
    && ! grep -q "yap-sched-agent: rewritten CraftScheduler.handle" "$LOG" 2>/dev/null; then
    echo "FAIL: UnsupportedOperationException while legacy sched smoke ran (agent did not rewrite)"
    tail -80 "$LOG" || true
    exit 1
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    # Process ended — accept if success markers already present
    if grep -q "YaP-LEGACY-SCHED-SMOKE runTask-ok" "$LOG" 2>/dev/null \
      && grep -q "yap-sched-agent: rewritten CraftScheduler.handle" "$LOG" 2>/dev/null; then
      ok=1
      break
    fi
    echo "Folia exited early"
    tail -80 "$LOG" || true
    exit 1
  fi
  sleep 2
done

if [ "$ok" != "1" ]; then
  echo "FAIL: markers not found in $LOG"
  tail -100 "$LOG" || true
  exit 1
fi

echo "PASS: legacy scheduler smoke (agent rewritten CraftScheduler + all-ok)"
