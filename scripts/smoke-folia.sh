#!/usr/bin/env bash
# Smoke: boot YaPcore with game-authority=folia (managed Folia process).
# Usage: ./scripts/smoke-folia.sh [seconds]
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

VER="${FOLIA_VERSION:-26.2}"
if [ ! -f "$ROOT/lib/folia-${VER}.jar" ]; then
  echo "Fetching Folia ${VER}…"
  "$ROOT/scripts/fetch-folia.sh" "$VER"
fi

echo "Building YaPcore…"
gradle shadowJar --no-daemon -q
YAP_JAR="$(yap_find_jar)"
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac
if [ -z "$YAP_JAR" ] || [ ! -f "$YAP_JAR" ]; then
  echo "Missing yapcore jar" >&2
  exit 1
fi

WORK="$ROOT/bench/workdir-folia-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs"
/bin/cp -f "$ROOT/lib/folia-${VER}.jar" "$WORK/lib/"
PORT=25575
cat >"$WORK/config/server.properties" <<EOF
server-name=YaP-Folia-Smoke
bind-host=127.0.0.1
port=${PORT}
max-players=20
ram-mb=2048
gui-enabled=false
online-mode=false
java-enabled=true
bedrock-enabled=false
crossplay-enabled=false
protocol-via-enabled=false
protocol-geyser-enabled=false
game-authority=folia
folia-embed=true
folia-dir=folia-kernel
folia-port=${PORT}
folia-version=${VER}
folia-ready-timeout-sec=180
velocity-enabled=false
web-dashboard-enabled=false
resource-pack-enabled=false
yap-ranks-auto-apply=false
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$WORK/smoke.log"
: >"$LOG"
echo "Booting Folia smoke (timeout ${WAIT_SECS}s)…"
echo "  home=$WORK"
echo "  java=$JAVA_BIN"
echo "  jar=$YAP_JAR"
echo "  port=$PORT"

(
  exec "$JAVA_BIN" -Xms512M -Xmx1536M \
    -Dyapcore.home="$WORK" \
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
  if grep -q 'Managed Folia online' "$LOG" 2>/dev/null \
    && { [ -f "$WORK/folia-kernel/yap-folia-ready.marker" ] || grep -q '\[folia\].*Done (' "$LOG" 2>/dev/null; }; then
    # TCP accept on Folia port
    if "$JAVA_BIN" -e 'try(var s=new java.net.Socket()){s.connect(new java.net.InetSocketAddress("127.0.0.1",'"$PORT"'),1500);System.exit(0);}catch(Exception e){System.exit(1);}' 2>/dev/null \
      || (exec 3<>/dev/tcp/127.0.0.1/"$PORT") 2>/dev/null; then
      ok=1
      break
    fi
  fi
  sleep 1
done

echo "Stopping pid=$PID…"
kill "$PID" 2>/dev/null || true
# Folia child may outlive parent briefly
pkill -f "folia-${VER}.jar" 2>/dev/null || true
wait "$PID" 2>/dev/null || true
sleep 2

if [ "$ok" -eq 1 ]; then
  echo "PASS: Folia managed process became ready on :${PORT}"
  echo "  log=$LOG"
  exit 0
fi
echo "FAIL: Folia smoke did not become ready within ${WAIT_SECS}s" >&2
echo "---- tail $LOG ----" >&2
tail -n 80 "$LOG" >&2 || true
exit 1
