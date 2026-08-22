#!/usr/bin/env bash
# M7 smoke: AoE/homing/conditions/graphics compile + unit tests.
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

echo "== M7 ability icons =="
python3 "$ROOT/scripts/generate-ability-icons.py"
test -d "$ROOT/resourcepacks/yap-abilities/assets/yapabilities/textures/item"

echo "== M7 unit tests =="
gradle :yap-abilities-api:compileJava :abilities-plugin:test :mmo-bedrock-plugin:compileJava --no-daemon -q

echo "== M7 build + install =="
gradle :abilities-plugin:installIntoPlugins :mmo-bedrock-plugin:installIntoPlugins \
  :combat-plugin:installIntoPlugins --no-daemon -q

if [ "${SKIP_LIVE:-0}" = "1" ]; then
  echo "M7 smoke PASS (compile + unit + icons)"
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

WORK="$ROOT/bench/workdir-mmo-m7-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs"
/bin/cp -f "$ROOT/lib/folia-${VER}.jar" "$WORK/lib/"

for j in yap-folia-bridge.jar yap-db.jar yap-skills.jar yap-combat.jar \
  yap-abilities.jar yap-mmo-bedrock.jar yap-bedrock-ui.jar; do
  [ -f "$ROOT/plugins/$j" ] && /bin/cp -f "$ROOT/plugins/$j" "$WORK/plugins/"
done

"$ROOT/scripts/db/ensure-db.sh" --root "$WORK" --server-id smoke-mmo-m7

PORT=25579
cat >"$WORK/config/server.properties" <<EOF
server-name=YaP-MMO-M7-Smoke
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

nohup "$JAVA_BIN" -jar "$YAP_JAR" --workdir "$WORK" >"$LOG" 2>&1 &
PID=$!
trap 'kill "$PID" 2>/dev/null || true' EXIT

ready=0
for _ in $(seq 1 120); do
  if grep -q "YaP Abilities ready" "$LOG" 2>/dev/null; then
    ready=1
    break
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    tail -n 40 "$LOG" || true
    exit 1
  fi
  sleep 1
done

[ "$ready" -eq 1 ] || { tail -n 50 "$LOG"; exit 1; }
echo "M7 smoke PASS (live boot)"
chmod +x "$ROOT/scripts/smoke-mmo-m7.sh" 2>/dev/null || true
