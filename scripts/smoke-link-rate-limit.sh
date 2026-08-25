#!/usr/bin/env bash
# Prove YaP Link rate limits fire (unit soak). Optional live TCP soak with LOOPBACK_SOAK=1.
# Usage:
#   ./scripts/smoke-link-rate-limit.sh
#   LOOPBACK_SOAK=1 ./scripts/smoke-link-rate-limit.sh   # boots Link, floods 127.0.0.1 with exemption off
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"
yap_set_install 2>/dev/null || true
export ROOT

echo "== Link rate-limit unit soak =="
gradle :yap-link-native:test --tests 'com.yapcore.link.ratelimit.*' --no-daemon -q
echo "OK: IpRateLimiter + ConnectRateGuard tests"

if [ "${LOOPBACK_SOAK:-0}" != "1" ]; then
  echo "Link rate-limit smoke PASS (unit). Set LOOPBACK_SOAK=1 for live TCP flood."
  exit 0
fi

# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"
yap_require_java
yap_load_config 2>/dev/null || true

echo "== Live TCP soak (rate-limit-exempt-loopback=false) =="
gradle :yap-link-native:shadowJar --no-daemon -q
LINK_JAR="$ROOT/yap-first-party/link/native/build/libs/yap-link.jar"
if [ ! -f "$LINK_JAR" ]; then
  echo "FAIL: missing $LINK_JAR" >&2
  exit 1
fi

WORK="$ROOT/bench/workdir-link-rate-soak"
rm -rf "$WORK"
mkdir -p "$WORK"
PORT=25991
METRICS=25992
cat >"$WORK/link.properties" <<EOF
bind=127.0.0.1:${PORT}
motd=rate-soak
max-players=50
online-mode=false
player-info-forwarding-mode=modern
forwarding-secret-file=forwarding.secret
servers.lobby=127.0.0.1:25566
try=lobby
plugins-enabled=false
bedrock-enabled=false
connect-rate-limit-enabled=true
connect-rate-per-ip=5
connect-rate-window-ms=60000
handshake-rate-limit-enabled=false
login-rate-limit-enabled=false
max-concurrent-per-ip-enabled=true
max-concurrent-per-ip=3
rate-limit-exempt-loopback=false
metrics-http-enabled=true
metrics-http-bind=127.0.0.1
metrics-http-port=${METRICS}
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$WORK/link.log"
(
  cd "$WORK"
  exec "$JAVA_BIN" -Xms64M -Xmx256M -jar "$LINK_JAR" --home "$WORK"
) >"$LOG" 2>&1 &
PID=$!
trap 'kill "$PID" 2>/dev/null || true' EXIT

ready=0
for _ in $(seq 1 40); do
  if curl -fsS "http://127.0.0.1:${METRICS}/health" >/dev/null 2>&1; then
    ready=1
    break
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "Link exited early:"; tail -n 40 "$LOG" || true
    exit 1
  fi
  sleep 0.25
done
if [ "$ready" -eq 0 ]; then
  echo "FAIL: metrics not up"; tail -n 40 "$LOG" || true
  exit 1
fi

echo "Flooding :${PORT} (30 connects)…"
for _ in $(seq 1 30); do
  (echo | timeout 0.2 nc -w 1 127.0.0.1 "$PORT" >/dev/null 2>&1) || true
done
sleep 0.5
METRICS_BODY="$(curl -fsS "http://127.0.0.1:${METRICS}/metrics")"
echo "$METRICS_BODY" | grep -E 'yap_link_connect_throttled|yap_link_throttle_connect' || true
THROTTLED="$(echo "$METRICS_BODY" | awk '/^yap_link_connect_throttled_total / {print $2; exit}')"
if [ -z "$THROTTLED" ]; then
  THROTTLED="$(echo "$METRICS_BODY" | awk '/^yap_link_connect_throttled / {print $2; exit}')"
fi
# Also accept gauge form
if [ -z "$THROTTLED" ] || [ "$THROTTLED" = "0" ]; then
  THROTTLED="$(echo "$METRICS_BODY" | awk '/yap_link_rate_connect_throttled / {print $2; exit}')"
fi
echo "connect.throttled≈${THROTTLED:-missing}"
if [ -z "$THROTTLED" ] || [ "${THROTTLED%.*}" -lt 1 ] 2>/dev/null; then
  # numeric compare: allow float
  awk -v t="${THROTTLED:-0}" 'BEGIN{exit !(t+0 >= 1)}' || {
    echo "FAIL: expected connect throttles ≥ 1 under loopback soak"
    echo "$METRICS_BODY" | head -n 40
    exit 1
  }
fi
echo "Link rate-limit smoke PASS (unit + live soak)"
