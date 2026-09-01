#!/usr/bin/env bash
# Phase 6: Bedrock UDP edge forwards datagrams to configured backend.
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

yap_require_java
HOME_DIR="$ROOT/bench/link-bedrock-smoke"
rm -rf "$HOME_DIR"
mkdir -p "$HOME_DIR"

gradle :yap-link-native:shadowJar --no-daemon -q
JAR="$ROOT/yap-first-party/link/native/build/libs/yap-link.jar"
BED_PORT=19140
BACKEND_PORT=19141
PAYLOAD="phase6-bedrock-udp"

cat >"$HOME_DIR/link.properties" <<EOF
bind=127.0.0.1:25579
motd=Bedrock smoke
online-mode=false
player-info-forwarding-mode=modern
forwarding-secret-file=forwarding.secret
servers.lobby=127.0.0.1:25566
servers.lobby.bedrock=127.0.0.1:${BACKEND_PORT}
try=lobby
bedrock-enabled=true
bedrock-bind=127.0.0.1:${BED_PORT}
bedrock-backend=127.0.0.1:${BACKEND_PORT}
plugins-enabled=false
EOF
openssl rand -hex 16 >"$HOME_DIR/forwarding.secret"

JAVA_BIN="$(yap_java_bin)"
LOG="$HOME_DIR/link.log"
: >"$LOG"

# UDP backend listener
python3 -u -c "
import socket, sys
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(('127.0.0.1', ${BACKEND_PORT}))
s.settimeout(20)
data, addr = s.recvfrom(4096)
sys.stdout.write(data.decode('latin-1', errors='replace'))
" >"$HOME_DIR/udp-recv.txt" 2>/dev/null &
UDP_PID=$!
sleep 0.5

timeout 20 "$JAVA_BIN" -Xms128M -Xmx256M -jar "$JAR" --home "$HOME_DIR" >>"$LOG" 2>&1 &
LINK_PID=$!

if ! yap_wait_log_grep "$LOG" 'Bedrock UDP edge' 15; then
  kill "$LINK_PID" 2>/dev/null || true
  kill "$UDP_PID" 2>/dev/null || true
  echo "FAIL: Bedrock UDP edge did not start" >&2
  tail -n 30 "$LOG" >&2 || true
  exit 1
fi

python3 -c "
import socket
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.sendto(b'${PAYLOAD}', ('127.0.0.1', ${BED_PORT}))
" 2>/dev/null || true

sleep 2
kill "$LINK_PID" 2>/dev/null || true
wait "$LINK_PID" 2>/dev/null || true
wait "$UDP_PID" 2>/dev/null || true

edge_ok=0
forward_ok=0
if grep -q "Bedrock UDP edge" "$LOG"; then
  edge_ok=1
fi
if [ -f "$HOME_DIR/udp-recv.txt" ] && grep -q "$PAYLOAD" "$HOME_DIR/udp-recv.txt" 2>/dev/null; then
  forward_ok=1
fi

if [ "$edge_ok" -eq 1 ] && [ "$forward_ok" -eq 1 ]; then
  echo "PASS: Bedrock UDP edge + forward to backend :${BACKEND_PORT}"
  exit 0
fi
echo "FAIL: edge_ok=$edge_ok forward_ok=$forward_ok" >&2
tail -n 30 "$LOG" >&2 || true
cat "$HOME_DIR/udp-recv.txt" 2>/dev/null >&2 || true
exit 1
