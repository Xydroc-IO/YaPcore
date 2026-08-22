#!/usr/bin/env bash
# Smoke: Folia + YaP Link modern forwarding (M2).
# Boots Folia with velocity-enabled, then Link; checks Folia loopback + Link public port.
# Usage: ./scripts/smoke-yap-link-folia.sh [seconds]
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

WAIT_SECS="${1:-150}"
cd "$ROOT"
export ROOT
yap_require_java
yap_load_config

VER="${FOLIA_VERSION:-26.2}"
if [ ! -f "$ROOT/lib/folia-${VER}.jar" ]; then
  echo "Fetching Folia ${VER}…"
  "$ROOT/scripts/fetch-folia.sh" "$VER"
fi

echo "Building YaPcore + YaP Link + Folia bridge…"
gradle shadowJar :yap-link:shadowJar :folia-bridge-plugin:installIntoPlugins --no-daemon -q
YAP_JAR="$(yap_find_jar)"
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac
LINK_JAR="$ROOT/yap-link/build/libs/yap-link.jar"
if [ -z "$YAP_JAR" ] || [ ! -f "$YAP_JAR" ]; then
  echo "Missing yapcore jar" >&2
  exit 1
fi
if [ ! -f "$LINK_JAR" ]; then
  echo "Missing $LINK_JAR" >&2
  exit 1
fi

WORK="$ROOT/bench/workdir-link-folia-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs" "$WORK/link-data"
/bin/cp -f "$ROOT/lib/folia-${VER}.jar" "$WORK/lib/"
# Core Folia built-in
if [ -f "$ROOT/plugins/yap-folia-bridge.jar" ]; then
  /bin/cp -f "$ROOT/plugins/yap-folia-bridge.jar" "$WORK/plugins/"
fi

FOLIA_PORT=25576
LINK_PORT=25577
SECRET="$WORK/forwarding.secret"
openssl rand -base64 32 | tr -d '\n' >"$SECRET"
chmod 600 "$SECRET"
cp -f "$SECRET" "$WORK/link-data/forwarding.secret"

cat >"$WORK/config/server.properties" <<EOF
server-name=YaP-Link-Folia-Smoke
bind-host=127.0.0.1
port=${FOLIA_PORT}
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
folia-port=${FOLIA_PORT}
folia-version=${VER}
folia-ready-timeout-sec=180
velocity-enabled=true
velocity-secret-file=forwarding.secret
velocity-online-mode=false
velocity-bind-localhost=true
web-dashboard-enabled=false
resource-pack-enabled=false
yap-ranks-auto-apply=false
EOF

cat >"$WORK/link-data/link.properties" <<EOF
bind=127.0.0.1:${LINK_PORT}
motd=YaP Link Folia Smoke
max-players=50
online-mode=false
player-info-forwarding-mode=modern
forwarding-secret-file=forwarding.secret
servers.lobby=127.0.0.1:${FOLIA_PORT}
try=lobby
force-default-server=true
EOF

JAVA_BIN="$(yap_java_bin)"
YAP_LOG="$WORK/yap.log"
LINK_LOG="$WORK/link.log"
: >"$YAP_LOG"
: >"$LINK_LOG"

echo "Booting Folia backend (timeout ${WAIT_SECS}s)…"
(
  exec "$JAVA_BIN" -Xms512M -Xmx1536M \
    -Dyapcore.home="$WORK" \
    -jar "$YAP_JAR" --nogui
) >>"$YAP_LOG" 2>&1 &
YAP_PID=$!
echo "  yap pid=$YAP_PID port=$FOLIA_PORT"

start_ts="$(date +%s)"
folia_ok=0
while kill -0 "$YAP_PID" 2>/dev/null; do
  now="$(date +%s)"
  if [ $((now - start_ts)) -ge "$WAIT_SECS" ]; then
    break
  fi
  if grep -q 'Managed Folia online\|Velocity/YaP Link modern forwarding enabled for Folia' "$YAP_LOG" 2>/dev/null \
    && { [ -f "$WORK/folia-kernel/yap-folia-ready.marker" ] || grep -q '\[folia\].*Done (' "$YAP_LOG" 2>/dev/null; }; then
    if "$JAVA_BIN" -e 'try(var s=new java.net.Socket()){s.connect(new java.net.InetSocketAddress("127.0.0.1",'"$FOLIA_PORT"'),1500);System.exit(0);}catch(Exception e){System.exit(1);}' 2>/dev/null \
      || (exec 3<>/dev/tcp/127.0.0.1/"$FOLIA_PORT") 2>/dev/null; then
      folia_ok=1
      break
    fi
  fi
  sleep 1
done

if [ "$folia_ok" -ne 1 ]; then
  echo "FAIL: Folia+Velocity did not become ready" >&2
  kill "$YAP_PID" 2>/dev/null || true
  pkill -f "folia-${VER}.jar" 2>/dev/null || true
  tail -n 60 "$YAP_LOG" >&2 || true
  exit 1
fi

# Confirm velocity config was written
if ! grep -q 'enabled: true' "$WORK/folia-kernel/config/paper-global.yml" 2>/dev/null \
  && ! grep -q 'enabled: true' "$WORK/folia-kernel/config/paper-global.yml" 2>/dev/null; then
  # yaml may dump as "enabled: true" under proxies.velocity
  if ! grep -A5 'velocity:' "$WORK/folia-kernel/config/paper-global.yml" 2>/dev/null | grep -q 'true'; then
    echo "FAIL: paper-global.yml missing velocity.enabled=true" >&2
    cat "$WORK/folia-kernel/config/paper-global.yml" >&2 || true
    kill "$YAP_PID" 2>/dev/null || true
    exit 1
  fi
fi

echo "Starting YaP Link on :${LINK_PORT} → Folia :${FOLIA_PORT}…"
(
  exec "$JAVA_BIN" -Xms128M -Xmx256M \
    -jar "$LINK_JAR" --home "$WORK/link-data"
) >>"$LINK_LOG" 2>&1 &
LINK_PID=$!
echo "  link pid=$LINK_PID"

link_ok=0
for _ in $(seq 1 40); do
  if "$JAVA_BIN" -e 'try(var s=new java.net.Socket()){s.connect(new java.net.InetSocketAddress("127.0.0.1",'"$LINK_PORT"'),1000);System.exit(0);}catch(Exception e){System.exit(1);}' 2>/dev/null \
    || (exec 3<>/dev/tcp/127.0.0.1/"$LINK_PORT") 2>/dev/null; then
    link_ok=1
    break
  fi
  sleep 0.5
done

echo "Stopping…"
kill "$LINK_PID" 2>/dev/null || true
kill "$YAP_PID" 2>/dev/null || true
pkill -f "folia-${VER}.jar" 2>/dev/null || true
wait "$LINK_PID" 2>/dev/null || true
wait "$YAP_PID" 2>/dev/null || true
sleep 2

if [ "$link_ok" -eq 1 ] && [ "$folia_ok" -eq 1 ]; then
  echo "PASS: Folia Velocity modern forwarding + YaP Link listening"
  echo "  folia=:${FOLIA_PORT} (127.0.0.1) link=:${LINK_PORT}"
  echo "  yap-log=$YAP_LOG link-log=$LINK_LOG"
  exit 0
fi
echo "FAIL: Link did not accept on :${LINK_PORT}" >&2
tail -n 40 "$LINK_LOG" >&2 || true
exit 1
