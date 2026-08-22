#!/usr/bin/env bash
# Phase 15: Bedrock play-depth gate — unit tests + live join/chat/break/inventory smoke.
# Usage: ./scripts/smoke-bedrock-play.sh [folia-wait-secs]
#   SKIP_LIVE=1  — unit tests only (no server boot)
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

echo "== Phase 15 bedrock play — automated codec/inventory gates =="
gradle :test \
  --tests 'com.yapcore.crossplay.bedrock.BedrockPacketCodecGameplayTest' \
  --tests 'com.yapcore.crossplay.bedrock.BedrockContainerBridgeTest' \
  --tests 'com.yapcore.crossplay.bedrock.BedrockColumnStreamerTest' \
  --tests 'com.yapcore.crossplay.bedrock.BedrockPaperInventoryInjectTest' \
  --tests 'com.yapcore.crossplay.bedrock.BedrockUiCodecTest' \
  --tests 'com.yapcore.crossplay.bedrock.bridge.BedrockUiBridgeTest' \
  --no-daemon -q

if [ "${SKIP_LIVE:-0}" = "1" ]; then
  echo "PASS: bedrock play unit gates (SKIP_LIVE=1)"
  exit 0
fi

VER="${FOLIA_VERSION:-26.2}"
if [ ! -f "$ROOT/lib/folia-${VER}.jar" ]; then
  "$ROOT/scripts/fetch-folia.sh" "$VER"
fi

YAP_JAR="$(yap_find_jar)"
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac
if [ ! -f "$YAP_JAR" ]; then
  echo "Building YaPcore…"
  gradle :distJar --no-daemon -q
fi
if [ ! -f "$YAP_JAR" ]; then
  echo "Missing yapcore jar" >&2
  exit 1
fi

WORK="$ROOT/bench/workdir-bedrock-play-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs"
/bin/cp -f "$ROOT/lib/folia-${VER}.jar" "$WORK/lib/"
[ -f "$ROOT/plugins/yap-folia-bridge.jar" ] && cp -f "$ROOT/plugins/yap-folia-bridge.jar" "$WORK/plugins/"
/bin/cp -f "$ROOT/lib/folia-${VER}.jar" "$WORK/lib/"

PORT=25568
cat >"$WORK/config/server.properties" <<EOF
server-name=Bedrock-Play-Smoke
bind-host=127.0.0.1
port=${PORT}
max-players=20
ram-mb=2048
gui-enabled=false
online-mode=false
java-enabled=true
bedrock-enabled=true
crossplay-enabled=true
shared-listen-port=true
protocol-via-enabled=false
protocol-geyser-enabled=true
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

echo "Booting Folia + Bedrock dual-stack :${PORT}…"
pkill -f "folia-${VER}.jar" 2>/dev/null || true
sleep 1
( exec "$JAVA_BIN" -Xms512M -Xmx1536M -Dyapcore.home="$WORK" -jar "$YAP_JAR" --nogui ) >>"$LOG" 2>&1 &
PID=$!

start_ts="$(date +%s)"
ready=0
while kill -0 "$PID" 2>/dev/null; do
  now="$(date +%s)"
  [ $((now - start_ts)) -ge "$WAIT_SECS" ] && break
  if grep -q 'Dual-stack gateway ready' "$LOG" 2>/dev/null \
    && { [ -f "$WORK/folia-kernel/yap-folia-ready.marker" ] || grep -q 'Managed Folia online\|\[folia\].*Done (' "$LOG" 2>/dev/null; }; then
    if "$JAVA_BIN" -e 'try(var s=new java.net.DatagramSocket()){s.setSoTimeout(1500);var p=new byte[1];s.send(new java.net.DatagramPacket(p,1,java.net.InetAddress.getByName("127.0.0.1"),'"$PORT"'));System.exit(0);}catch(Exception e){System.exit(1);}' 2>/dev/null; then
      ready=1
      break
    fi
  fi
  sleep 1
done
if [ "$ready" -eq 0 ]; then
  echo "FAIL: Bedrock listener not ready on :${PORT}" >&2
  kill "$PID" 2>/dev/null || true
  tail -40 "$LOG" >&2 || true
  exit 1
fi

echo "Running bedrock-play-smoke.mjs…"
cd "$ROOT/scripts/bench/bots"
if [[ ! -d node_modules/bedrock-protocol ]]; then
  npm install bedrock-protocol --no-fund --no-audit
fi
export HOST=127.0.0.1 PORT="$PORT"
set +e
node "$ROOT/scripts/protocol-matrix/bedrock-play-smoke.mjs" | tee "${BEDROCK_PLAY_OUT:-$ROOT/build/bedrock-play-smoke-latest.json}"
PLAY_RC=${PIPESTATUS[0]}
set -e

kill "$PID" 2>/dev/null || true
pkill -f "folia-${VER}.jar" 2>/dev/null || true
wait "$PID" 2>/dev/null || true

if [ "$PLAY_RC" -eq 0 ]; then
  echo "PASS: Phase 15 bedrock play smoke (unit + live)"
  exit 0
fi
echo "FAIL: bedrock-play-smoke.mjs exit=$PLAY_RC" >&2
exit 1
