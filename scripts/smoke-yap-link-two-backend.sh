#!/usr/bin/env bash
# Phase 6: multi-backend Link config — probe both names, chat relay via console `say`.
# Uses one Folia box; lobby + survival are logical backends (same host:port).
# Usage: ./scripts/smoke-yap-link-two-backend.sh [seconds]
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

WAIT_SECS="${1:-180}"
cd "$ROOT"
export ROOT
yap_require_java
yap_load_config

VER="${FOLIA_VERSION:-26.2}"
if [ ! -f "$ROOT/lib/folia-${VER}.jar" ]; then
  "$ROOT/scripts/fetch-folia.sh" "$VER"
fi

echo "Building YaPcore, Link, plugins…"
gradle shadowJar :folia-bridge-plugin:installIntoPlugins \
  :yap-link-native:shadowJar \
  :yap-link-plugin-chat-bridge:installIntoLinkPlugins \
  :yap-link-plugin-mod-sync:installIntoLinkPlugins \
  :yap-link-plugin-server-selector:installIntoLinkPlugins \
  :yap-link-plugin-tab-bridge:installIntoLinkPlugins \
  :yap-link-plugin-discord:installIntoLinkPlugins \
  --no-daemon -q

YAP_JAR="$(yap_find_jar)"
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac
LINK_JAR="$ROOT/yap-first-party/link/native/build/libs/yap-link.jar"
if [ ! -f "$LINK_JAR" ]; then
  echo "Missing native yap-link.jar" >&2
  exit 1
fi

WORK="$ROOT/bench/workdir-link-two-backend"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs" "$WORK/link-data/plugins"
/bin/cp -f "$ROOT/lib/folia-${VER}.jar" "$WORK/lib/"
[ -f "$ROOT/plugins/yap-folia-bridge.jar" ] && cp -f "$ROOT/plugins/yap-folia-bridge.jar" "$WORK/plugins/"
cp -f "$ROOT/link-data/plugins/"*.jar "$WORK/link-data/plugins/" 2>/dev/null || true

FOLIA_PORT=25596
LINK_PORT=25597
SECRET="$WORK/forwarding.secret"
openssl rand -base64 32 | tr -d '\n' >"$SECRET"
chmod 600 "$SECRET"
cp -f "$SECRET" "$WORK/link-data/forwarding.secret"

cat >"$WORK/config/server.properties" <<EOF
server-name=LinkSmoke-MultiBackend
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
motd=Multi-backend smoke
online-mode=false
player-info-forwarding-mode=modern
forwarding-secret-file=forwarding.secret
servers.lobby=127.0.0.1:${FOLIA_PORT}
servers.survival=127.0.0.1:${FOLIA_PORT}
try=lobby,survival
ping-passthrough=true
aggregate-player-count=true
chat-relay-enabled=true
plugins-enabled=true
backend-probe-interval-sec=5
backend-probe-timeout-ms=8000
EOF

JAVA_BIN="$(yap_java_bin)"
YAP_LOG="$WORK/yap.log"
LINK_LOG="$WORK/link.log"
: >"$YAP_LOG"
: >"$LINK_LOG"

echo "Booting Folia :${FOLIA_PORT}…"
pkill -f "folia-${VER}.jar" 2>/dev/null || true
sleep 1
( exec "$JAVA_BIN" -Xms512M -Xmx1536M -Dyapcore.home="$WORK" -jar "$YAP_JAR" --nogui ) >>"$YAP_LOG" 2>&1 &
YAP_PID=$!

start_ts="$(date +%s)"
folia_ok=0
while kill -0 "$YAP_PID" 2>/dev/null; do
  now="$(date +%s)"
  [ $((now - start_ts)) -ge "$WAIT_SECS" ] && break
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
  echo "FAIL: Folia not ready" >&2
  kill "$YAP_PID" 2>/dev/null || true
  tail -40 "$YAP_LOG" >&2
  exit 1
fi

echo "Starting Link :${LINK_PORT}…"
(
  cd "$WORK/link-data"
  {
    for _ in $(seq 1 60); do
      if "$JAVA_BIN" -e 'try(var s=new java.net.Socket()){s.connect(new java.net.InetSocketAddress("127.0.0.1",'"$LINK_PORT"'),500);System.exit(0);}catch(Exception e){System.exit(1);}' 2>/dev/null; then
        break
      fi
      sleep 0.5
    done
    sleep 12
    echo "servers"
    sleep 2
    echo "say phase6-multi-backend"
    sleep 2
    echo "stop"
  } | exec "$JAVA_BIN" -Xms128M -Xmx384M -jar "$LINK_JAR" --home "$WORK/link-data"
) >>"$LINK_LOG" 2>&1 &
LINK_PID=$!

probe_ok=0
chat_ok=0
start_ts="$(date +%s)"
while kill -0 "$LINK_PID" 2>/dev/null; do
  now="$(date +%s)"
  [ $((now - start_ts)) -ge 120 ] && break
  if grep -q 'lobby up=true' "$LINK_LOG" 2>/dev/null && grep -q 'survival up=true' "$LINK_LOG" 2>/dev/null; then
    probe_ok=1
  fi
  if grep -q 'CHAT relay' "$LINK_LOG" 2>/dev/null; then
    chat_ok=1
  fi
  [ "$probe_ok" -eq 1 ] && [ "$chat_ok" -eq 1 ] && break
  sleep 1
done

kill "$LINK_PID" 2>/dev/null || true
kill "$YAP_PID" 2>/dev/null || true
pkill -f "folia-${VER}.jar" 2>/dev/null || true
wait "$LINK_PID" 2>/dev/null || true
wait "$YAP_PID" 2>/dev/null || true

if [ "$probe_ok" -eq 1 ] && [ "$chat_ok" -eq 1 ]; then
  echo "PASS: multi-backend probe (lobby+survival) + chat relay"
  echo "  folia=:${FOLIA_PORT} link=:${LINK_PORT}"
  exit 0
fi
echo "FAIL: probe_ok=$probe_ok chat_ok=$chat_ok" >&2
tail -50 "$LINK_LOG" >&2 || true
exit 1
