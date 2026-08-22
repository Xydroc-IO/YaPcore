#!/usr/bin/env bash
# M6 smoke: yap-factions-api unit tests + yap-factions compile + optional Folia boot.
# Usage: ./scripts/smoke-factions-m6.sh
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

echo "== M6 unit tests =="
gradle :yap-factions-api:test :factions-plugin:test --no-daemon -q

echo "== M6 build + install =="
gradle :yap-factions-api:jar :factions-plugin:installIntoPlugins :playerdata-plugin:shadowJar --no-daemon -q

if [ ! -f "$ROOT/plugins/yap-factions.jar" ]; then
  echo "FAIL: missing plugins/yap-factions.jar"
  exit 1
fi

if [ "${SKIP_LIVE:-0}" = "1" ]; then
  echo "SKIP_LIVE=1 — skipping Folia boot"
  echo "M6 factions smoke PASS (compile + unit)"
  exit 0
fi

# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"
yap_require_java
yap_load_config

VER="${FOLIA_VERSION:-26.2}"
if [ ! -f "$ROOT/lib/folia-${VER}.jar" ]; then
  "$ROOT/scripts/fetch-folia.sh" "$VER"
fi

WORK="$ROOT/bench/workdir-factions-m6-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs"
/bin/cp -f "$ROOT/lib/folia-${VER}.jar" "$WORK/lib/"

for j in yap-folia-bridge.jar yap-db.jar yap-playerdata.jar yap-factions.jar yap-placeholderapi.jar; do
  if [ -f "$ROOT/plugins/$j" ]; then
    /bin/cp -f "$ROOT/plugins/$j" "$WORK/plugins/"
  fi
done

"$ROOT/scripts/db/ensure-db.sh" --root "$WORK" --server-id smoke-factions-m6

PORT=25582
WAIT_SECS="${1:-180}"
cat >"$WORK/config/server.properties" <<EOF
server-name=YaP-Factions-M6-Smoke
bind-host=127.0.0.1
port=${PORT}
max-players=10
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
folia-ready-timeout-sec=${WAIT_SECS}
velocity-enabled=false
web-dashboard-enabled=false
resource-pack-enabled=false
yap-ranks-auto-apply=false
plugin-compat-enabled=false
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$WORK/smoke.log"
YAP_JAR="$(yap_find_jar)"
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac

: >"$LOG"
echo "Booting Folia with yap-factions (port $PORT, timeout ${WAIT_SECS}s)…"
(
  exec "$JAVA_BIN" -Xms512M -Xmx2048M \
    -Dyapcore.home="$WORK" \
    -jar "$YAP_JAR" --nogui
) >>"$LOG" 2>&1 &
PID=$!
trap 'kill "$PID" 2>/dev/null || true' EXIT

start_ts="$(date +%s)"
ready=0
while kill -0 "$PID" 2>/dev/null; do
  now="$(date +%s)"
  if [ $((now - start_ts)) -ge "$WAIT_SECS" ]; then
    break
  fi
  if grep -q "YaPFactions ready" "$LOG" 2>/dev/null; then
    ready=1
    break
  fi
  if grep -qiE '\[YaPFactions\].*(Failed|SEVERE|Error enabling)|Could not load.*yap-factions' "$LOG" 2>/dev/null; then
    echo "YaPFactions failed during enable:"
    grep -iE 'YaPFactions|yap-factions|YaPPlayerdata' "$LOG" 2>/dev/null | tail -25 || true
    exit 1
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "Server exited early:"
    tail -n 60 "$LOG" || true
    exit 1
  fi
  sleep 1
done

if [ "$ready" -eq 0 ]; then
  echo "Timed out waiting for YaPFactions"
  grep -iE 'YaPFactions|YaPDB|YaPPlayerdata|folia|Done \(' "$LOG" 2>/dev/null | tail -40 || true
  tail -n 40 "$LOG" || true
  exit 1
fi

echo "M6 factions smoke PASS (live boot + YaPFactions ready)"
