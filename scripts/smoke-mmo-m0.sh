#!/usr/bin/env bash
# M0 smoke: yap-mmo-api unit tests + yap-skills compile + optional Folia boot.
# Usage: ./scripts/smoke-mmo-m0.sh
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

echo "== M0 unit tests =="
gradle :yap-mmo-api:test :skills-plugin:test --no-daemon -q

echo "== M0 build + install =="
gradle :yap-mmo-api:jar :skills-plugin:installIntoPlugins :npcs-plugin:shadowJar --no-daemon -q

if [ ! -f "$ROOT/plugins/yap-skills.jar" ]; then
  echo "FAIL: missing plugins/yap-skills.jar"
  exit 1
fi

if [ "${SKIP_LIVE:-0}" = "1" ]; then
  echo "SKIP_LIVE=1 — skipping Folia boot"
  echo "M0 smoke PASS (compile + unit)"
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

WORK="$ROOT/bench/workdir-mmo-m0-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs"
/bin/cp -f "$ROOT/lib/folia-${VER}.jar" "$WORK/lib/"

for j in yap-folia-bridge.jar yap-db.jar yap-skills.jar yap-npcs.jar yap-placeholderapi.jar; do
  if [ -f "$ROOT/plugins/$j" ]; then
    /bin/cp -f "$ROOT/plugins/$j" "$WORK/plugins/"
  fi
done

"$ROOT/scripts/db/ensure-db.sh" --root "$WORK" --server-id smoke-mmo-m0

PORT=25579
cat >"$WORK/config/server.properties" <<EOF
server-name=YaP-MMO-M0-Smoke
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
folia-ready-timeout-sec=120
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

echo "Booting Folia with yap-skills (port $PORT)…"
nohup "$JAVA_BIN" -jar "$YAP_JAR" --workdir "$WORK" >"$LOG" 2>&1 &
PID=$!
trap 'kill "$PID" 2>/dev/null || true' EXIT

ready=0
for _ in $(seq 1 120); do
  if grep -q "YaPSkills ready" "$LOG" 2>/dev/null; then
    ready=1
    break
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "Server exited early:"
    tail -n 40 "$LOG" || true
    exit 1
  fi
  sleep 1
done

if [ "$ready" -eq 0 ]; then
  echo "Timed out waiting for YaPSkills"
  tail -n 50 "$LOG" || true
  exit 1
fi

echo "M0 smoke PASS (live boot + YaPSkills ready)"
