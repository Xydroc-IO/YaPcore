#!/usr/bin/env bash
# Smoke: build minimal Paper plugin, boot YaPcore Phase 3 briefly, require enable log.
# Usage: ./scripts/smoke-paper-plugins.sh [seconds]
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

WAIT_SECS="${1:-40}"
cd "$ROOT"
export ROOT
yap_require_java
yap_load_config

echo "Building compat smoke plugin + YaPcore…"
gradle :compat-smoke-plugin:jar :phase3-plugin:installIntoResources shadowJar --no-daemon -q

SMOKE_JAR="$(ls -1 "$ROOT/compat-smoke-plugin/build/libs"/yap-compat-smoke.jar 2>/dev/null | head -n 1)"
YAP_JAR="$(yap_find_jar)"
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac
YAP_PAPER="$ROOT/lib/paper-${PAPER_VERSION}-yap.jar"
if [ ! -f "$SMOKE_JAR" ] || [ -z "$YAP_JAR" ] || [ ! -f "$YAP_JAR" ] || [ ! -f "$YAP_PAPER" ]; then
  echo "Missing smoke jar / yapcore / yap paperclip" >&2
  exit 1
fi

WORK="$ROOT/bench/workdir-plugin-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/plugins"
/bin/cp -f "$YAP_PAPER" "$WORK/paper.jar"
/bin/cp -f "$YAP_PAPER" "$WORK/paper-${PAPER_VERSION}.jar"
/bin/cp -f "$SMOKE_JAR" "$WORK/plugins/yap-compat-smoke.jar"
if [ -f "$ROOT/src/main/resources/phase3/yap-spatial-tick.jar" ]; then
  /bin/cp -f "$ROOT/src/main/resources/phase3/yap-spatial-tick.jar" "$WORK/plugins/" || true
fi
printf 'eula=true\n' >"$WORK/eula.txt"
cat >"$WORK/server.properties" <<EOF
motd=YaP plugin compat smoke
online-mode=false
max-players=10
server-port=25572
level-seed=yap-plugin-smoke
view-distance=4
simulation-distance=4
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$WORK/smoke.log"
: >"$LOG"
echo "Booting YaPcore paper-dir=bench/workdir-plugin-smoke (timeout ${WAIT_SECS}s)…"
echo "  java=$JAVA_BIN"
echo "  jar=$YAP_JAR"

(
  cd "$WORK"
  exec "$JAVA_BIN" -Xms512M -Xmx1G \
    -Dyapcore.home="$ROOT" \
    -Dyapcore.paper.dir=bench/workdir-plugin-smoke \
    -jar "$YAP_JAR" --nogui
) >>"$LOG" 2>&1 &
PID=$!
echo "  pid=$PID"

start_ts="$(date +%s)"
ok=0
while kill -0 "$PID" 2>/dev/null; do
  now="$(date +%s)"
  if [ $((now - start_ts)) -ge "$WAIT_SECS" ]; then
    break
  fi
  if grep -q 'YaP-COMPAT-SMOKE enabled' "$LOG" 2>/dev/null \
    && grep -q 'YaP-COMPAT-SMOKE scheduler-ok' "$LOG" 2>/dev/null; then
    ok=1
    break
  fi
  sleep 1
done

kill "$PID" 2>/dev/null || true
# Give Paper a moment to flush
sleep 2
kill -9 "$PID" 2>/dev/null || true
wait "$PID" 2>/dev/null || true

if [ "$ok" -ne 1 ]; then
  # Re-check after shutdown flush
  if grep -q 'YaP-COMPAT-SMOKE enabled' "$LOG" 2>/dev/null \
    && grep -q 'YaP-COMPAT-SMOKE scheduler-ok' "$LOG" 2>/dev/null; then
    ok=1
  fi
fi

if [ "$ok" -ne 1 ]; then
  echo "FAIL: compat smoke markers not found in $LOG" >&2
  echo "---- log tail ----" >&2
  strings "$LOG" | grep -E 'COMPAT-SMOKE|ERROR|Exception|Enabling|Done |Phase 3' | tail -40 >&2 || true
  exit 1
fi

echo "OK — Paper plugin enable + scheduler smoke passed"
echo "  log=$LOG"
./scripts/check-plugin-layout.sh || true
exit 0
