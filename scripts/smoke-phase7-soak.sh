#!/usr/bin/env bash
# Phase 7 — live play soak + gameplay add-ons (items 8–18).
#
# Automated: JE walk/hold, Bedrock movement/hold, abilities/combat/vehicles boot,
# MMO content validate, codec/unit gates, Xbox-shaped CI.
# Still manual: retail Xbox console (14), G.33 skull item-in-hand (15).
#
# Usage:
#   ./scripts/smoke-phase7-soak.sh
#   FAST_PHASE7=1 ./scripts/smoke-phase7-soak.sh   # 60s hold instead of 600s
#   SKIP_LIVE=1 ./scripts/smoke-phase7-soak.sh      # unit gates only
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

cd "$ROOT"
export ROOT
yap_require_java

OUT="$ROOT/build/phase7-soak-latest.json"
PORT=25569
WAIT_SECS="${1:-240}"
VER="${FOLIA_VERSION:-26.2}"
SOAK_SECS="${PHASE7_SOAK_SECS:-600}"
if [ "${FAST_PHASE7:-0}" = "1" ]; then
  SOAK_SECS=60
fi

PASS=0
FAIL=0
STEPS=()

step() {
  local name="$1"
  shift
  echo "== $name =="
  if "$@"; then
    STEPS+=("\"$name\":true")
    PASS=$((PASS + 1))
  else
    STEPS+=("\"$name\":false")
    FAIL=$((FAIL + 1))
    echo "FAIL: $name" >&2
  fi
}

step "validate-mmo-content" "$ROOT/scripts/validate-mmo-content.sh"

step "gameplay-unit-gates" gradle :test \
  --tests 'com.yapcore.abilities.bar.AbilityBarConfigTest' \
  --tests 'com.yapcore.abilities.book.AbilityBookConfigTest' \
  --tests 'com.yapcore.abilities.load.AbilityPackLoaderTest' \
  --tests 'com.yapcore.combat.formula.DamageCalculatorTest' \
  --tests 'com.yapcore.combat.combo.ComboServiceTest' \
  --tests 'com.yapcore.crossplay.bedrock.BedrockColumnStreamerTest' \
  --tests 'com.yapcore.crossplay.bedrock.BedrockContainerBridgeTest' \
  --tests 'com.yapcore.crossplay.form.FormServiceTest' \
  --tests 'com.yapcore.crossplay.skin.SkinServiceTest' \
  --tests 'com.yapcore.crossplay.bedrock.BedrockUiCodecTest' \
  --no-daemon -q

step "play-soak-automated" "$ROOT/scripts/protocol-matrix/play-soak.sh"
step "xbox-shaped-ci" "$ROOT/scripts/protocol-matrix/xbox-chain-soak.sh"

if [ "${SKIP_LIVE:-0}" = "1" ]; then
  echo "SKIP_LIVE=1 — skipping live boot soak"
  OK=false
  [ "$FAIL" -eq 0 ] && OK=true
  {
    echo "{"
    echo "  \"at\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","
    echo "  \"passed\": $PASS,"
    echo "  \"failed\": $FAIL,"
    echo "  \"ok\": $OK,"
    echo "  \"skip_live\": true,"
    echo "  \"steps\": { $(IFS=,; echo "${STEPS[*]}") }"
    echo "}"
  } >"$OUT"
  [ "$OK" = true ] && exit 0
  exit 1
fi

echo "Building + installing gameplay defaults…"
gradle installGameplayDefaults \
  :abilities-plugin:installIntoPlugins \
  :combat-plugin:installIntoPlugins \
  :vehicles-plugin:installIntoPlugins \
  :mmo-content-plugin:installIntoPlugins \
  shadowJar --no-daemon -q

YAP_JAR="$(yap_find_jar)"
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac
if [ ! -f "$YAP_JAR" ]; then
  gradle :distJar --no-daemon -q
  YAP_JAR="$(yap_find_jar)"
  case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac
fi

if [ ! -f "$ROOT/lib/yap-folia-${VER}.jar" ]; then
  "$ROOT/scripts/build-yap-folia.sh"
fi

WORK="$ROOT/bench/workdir-phase7-soak"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs"
/bin/cp -f "$ROOT/lib/yap-folia-${VER}.jar" "$WORK/lib/"
/bin/cp -f "$ROOT/lib/yap-folia-${VER}.jar" "$WORK/lib/folia-${VER}.jar"

for j in yap-folia-bridge.jar yap-db.jar yap-essentials.jar yap-abilities.jar yap-combat.jar yap-vehicles.jar \
         yap-mmo-content.jar yap-floodgate.jar yap-packs.jar yap-placeholderapi.jar; do
  [ -f "$ROOT/plugins/$j" ] && cp -f "$ROOT/plugins/$j" "$WORK/plugins/"
done

"$ROOT/scripts/db/ensure-db.sh" --root "$WORK" --server-id phase7-soak

cat >"$WORK/config/server.properties" <<EOF
server-name=Phase7-Soak
bind-host=127.0.0.1
port=${PORT}
max-players=20
ram-mb=3072
gui-enabled=false
online-mode=false
auto-op=true
ops=YapPhase7JE
allow-localhost=true
java-enabled=true
bedrock-enabled=true
crossplay-enabled=true
shared-listen-port=true
protocol-via-enabled=true
protocol-geyser-enabled=true
game-authority=folia
folia-embed=true
folia-dir=folia-kernel
folia-port=${PORT}
folia-version=${VER}
folia-jar-source=build
folia-ready-timeout-sec=180
velocity-enabled=false
web-dashboard-enabled=false
resource-pack-enabled=false
yap-ranks-auto-apply=false
plugin-compat-enabled=false
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$WORK/smoke.log"
: >"$LOG"

echo "Booting Phase 7 soak server on :${PORT} (hold ${SOAK_SECS}s after walk)…"
pkill -f "yapcore.home=$WORK" 2>/dev/null || true
sleep 1
( exec "$JAVA_BIN" -Xms512M -Xmx2048M -Dyapcore.home="$WORK" -jar "$YAP_JAR" --nogui ) >>"$LOG" 2>&1 &
PID=$!

start_ts="$(date +%s)"
ready=0
abilities_ok=0
vehicles_ok=0
combat_ok=0
while kill -0 "$PID" 2>/dev/null; do
  now="$(date +%s)"
  [ $((now - start_ts)) -ge "$WAIT_SECS" ] && break
  grep -q 'Enabling YaPAbilities' "$LOG" 2>/dev/null && abilities_ok=1
  grep -q 'Enabling YaPVehicles' "$LOG" 2>/dev/null && vehicles_ok=1
  grep -q 'YaPCombat ready\|Enabling YaPCombat' "$LOG" 2>/dev/null && combat_ok=1
  if grep -q 'Dual-stack gateway ready' "$LOG" 2>/dev/null \
    && grep -q 'Bedrock Edition UDP on' "$LOG" 2>/dev/null \
    && { [ -f "$WORK/folia-kernel/yap-folia-ready.marker" ] \
      || grep -q 'Managed Folia online\|\[folia\].*Done (' "$LOG" 2>/dev/null; }; then
    ready=1
    break
  fi
  sleep 1
done

if [ "$ready" -ne 1 ]; then
  echo "FAIL: Phase 7 server not ready" >&2
  kill "$PID" 2>/dev/null || true
  tail -40 "$LOG" >&2 || true
  FAIL=$((FAIL + 1))
  STEPS+=("\"live-boot\":false")
else
  STEPS+=("\"live-boot\":true")
  PASS=$((PASS + 1))
  echo "  abilities=$abilities_ok vehicles=$vehicles_ok combat=$combat_ok"

  cd "$ROOT/scripts/bench/bots"
  if [ ! -d node_modules/mineflayer ]; then
    npm install --no-fund --no-audit
  fi

  export HOST=127.0.0.1 PORT="$PORT"
  export PHASE7_SOAK_SECS="$SOAK_SECS"
  export FAST_PHASE7="${FAST_PHASE7:-0}"

  if node "$ROOT/scripts/protocol-matrix/je-play-soak.mjs" \
    | tee "$ROOT/build/phase7-je-soak-latest.json"; then
    STEPS+=("\"je-play-soak\":true")
    PASS=$((PASS + 1))
  else
    STEPS+=("\"je-play-soak\":false")
    FAIL=$((FAIL + 1))
  fi

  if [ ! -d node_modules/bedrock-protocol ]; then
    npm install bedrock-protocol --no-fund --no-audit
  fi
  if node "$ROOT/scripts/protocol-matrix/bedrock-play-soak.mjs" \
    | tee "$ROOT/build/phase7-bedrock-soak-latest.json"; then
    STEPS+=("\"bedrock-play-soak\":true")
    PASS=$((PASS + 1))
  else
    STEPS+=("\"bedrock-play-soak\":false")
    FAIL=$((FAIL + 1))
  fi

  if grep -q 'YaP Abilities reloaded' "$LOG" 2>/dev/null \
    || grep -q '"abilitiesReload": true' "$ROOT/build/phase7-je-soak-latest.json" 2>/dev/null \
    || grep -q 'YAPABILITIES_JSON' "$ROOT/build/phase7-je-soak-latest.json" 2>/dev/null; then
    STEPS+=("\"abilities-reload\":true")
    PASS=$((PASS + 1))
  else
    STEPS+=("\"abilities-reload\":false")
    FAIL=$((FAIL + 1))
  fi

  if [ "$vehicles_ok" -eq 1 ] && [ "$combat_ok" -eq 1 ]; then
    STEPS+=("\"gameplay-plugins\":true")
    PASS=$((PASS + 1))
  else
    STEPS+=("\"gameplay-plugins\":false")
    FAIL=$((FAIL + 1))
  fi
fi

kill "$PID" 2>/dev/null || true
pkill -f "yapcore.home=$WORK" 2>/dev/null || true
wait "$PID" 2>/dev/null || true

OK=false
[ "$FAIL" -eq 0 ] && OK=true
{
  echo "{"
  echo "  \"at\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","
  echo "  \"soak_secs\": $SOAK_SECS,"
  echo "  \"passed\": $PASS,"
  echo "  \"failed\": $FAIL,"
  echo "  \"ok\": $OK,"
  echo "  \"manual_remaining\": [\"retail_xbox_login\",\"g33_skull_item_in_hand\"],"
  echo "  \"steps\": { $(IFS=,; echo "${STEPS[*]}") }"
  echo "}"
} >"$OUT"

if [ "$OK" = true ]; then
  echo "PASS: phase7-soak ($PASS steps)"
  echo "  artifact=$OUT"
  exit 0
fi
echo "FAIL: phase7-soak ($FAIL failed / $PASS passed)" >&2
exit 1
