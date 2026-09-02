#!/usr/bin/env bash
# Smoke: Folia boots with first-party Folia-native product plugins (M3).
# Usage: ./scripts/smoke-folia-plugins.sh [seconds]
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

echo "Building + installing Folia product plugins…"
gradle installProductDefaults :folia-bridge-plugin:installIntoPlugins \
  :stacker-plugin:installIntoPlugins :vehicles-plugin:installIntoPlugins \
  :gameplay-knobs-plugin:installIntoPlugins \
  shadowJar --no-daemon -q

# Refuse spatial tick on product Folia smoke
rm -f "$ROOT/plugins/yap-spatial-tick.jar"

YAP_JAR="$(yap_find_jar)"
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac

WORK="$ROOT/bench/workdir-folia-plugins-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs"
/bin/cp -f "$ROOT/lib/folia-${VER}.jar" "$WORK/lib/"

# Core first-party Folia-native jars (no spatial-tick, no plugin-compat needed)
for j in yap-folia-bridge.jar yap-chat.jar yap-floodgate.jar yap-packs.jar \
         yap-placeholderapi.jar yap-pregen.jar yap-db.jar yap-playerdata.jar \
         yap-gameplay-knobs.jar yap-stacker.jar yap-vehicles.jar; do
  if [ -f "$ROOT/plugins/$j" ]; then
    /bin/cp -f "$ROOT/plugins/$j" "$WORK/plugins/"
  fi
done

# MariaDB must be up + JDBC written into THIS workdir (not only repo-root plugins/)
echo "Ensuring MariaDB + workdir JDBC…"
"$ROOT/scripts/db/ensure-db.sh" --root "$WORK" --server-id smoke-folia-plugins

PORT=25578
cat >"$WORK/config/server.properties" <<EOF
server-name=YaP-Folia-Plugins-Smoke
bind-host=127.0.0.1
port=${PORT}
max-players=20
ram-mb=3072
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
plugin-compat-enabled=false
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$WORK/smoke.log"
: >"$LOG"
echo "Booting Folia + product plugins (timeout ${WAIT_SECS}s)…"
(
  exec "$JAVA_BIN" -Xms512M -Xmx2048M \
    -Dyapcore.home="$WORK" \
    -jar "$YAP_JAR" --nogui
) >>"$LOG" 2>&1 &
PID=$!
echo "  pid=$PID port=$PORT"

start_ts="$(date +%s)"
ok=0
bridge_ok=0
db_ok=0
playerdata_ok=0
fail_plugin=0
fail_db=0
while kill -0 "$PID" 2>/dev/null; do
  now="$(date +%s)"
  if [ $((now - start_ts)) -ge "$WAIT_SECS" ]; then
    break
  fi
  if grep -q 'YaP Folia bridge online\|Enabled — folia-supported' "$LOG" 2>/dev/null; then
    bridge_ok=1
  fi
  if grep -q 'Shared YapDb pool ready' "$LOG" 2>/dev/null; then
    db_ok=1
  fi
  if grep -q 'Using shared YaPDB\|YaPPlayerData 0\.6' "$LOG" 2>/dev/null; then
    if ! grep -qiE '\[YaPPlayerData\].*(Failed to open|Access denied|disabling YaPPlayerData)' "$LOG" 2>/dev/null; then
      playerdata_ok=1
    fi
  fi
  # Hard fail if Folia rejects a product jar or spatial tick somehow loads
  if grep -qiE 'UnsupportedOperationException|Not supported on Folia|YapSpatialTick.*(Enabling|online)' "$LOG" 2>/dev/null; then
    fail_plugin=1
    break
  fi
  if grep -qiE '\[YaPDB\].*(Failed to open|Access denied)|\[YaPPlayerData\].*(Failed to open|Access denied|disabling YaPPlayerData)' "$LOG" 2>/dev/null; then
    fail_db=1
    break
  fi
  if [ "$bridge_ok" -eq 1 ] && [ "$db_ok" -eq 1 ] && grep -q 'Managed Folia online' "$LOG" 2>/dev/null \
    && { [ -f "$WORK/folia-kernel/yap-folia-ready.marker" ] || grep -q '\[folia\].*Done (' "$LOG" 2>/dev/null; }; then
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
pkill -f "folia-${VER}.jar" 2>/dev/null || true
wait "$PID" 2>/dev/null || true
sleep 2

# Summarize loaded YaP plugins
echo "---- YaP plugin lines ----"
grep -E '\[folia\].*YaP|\[YaP|folia-supported|YapSpatialTick|Shared YapDb|YaPPlayerData' "$LOG" 2>/dev/null | head -50 || true

if [ "$fail_plugin" -eq 1 ]; then
  echo "FAIL: Folia scheduler / spatial-tick issue in logs" >&2
  tail -n 80 "$LOG" >&2 || true
  exit 1
fi
if [ "$fail_db" -eq 1 ] || [ "$db_ok" -ne 1 ]; then
  echo "FAIL: YaPDB did not open a shared pool (db_ok=$db_ok fail_db=$fail_db)" >&2
  echo "  ensure: ./scripts/db/ensure-db.sh --root <home> --server-id <id>" >&2
  echo "  workdir JDBC: $WORK/plugins/YaPDB/config.yml" >&2
  tail -n 80 "$LOG" >&2 || true
  exit 1
fi
if [ "$ok" -eq 1 ] && [ "$bridge_ok" -eq 1 ] && [ "$db_ok" -eq 1 ]; then
  echo "PASS: Folia ready with first-party Folia-native plugins + YapDb on :${PORT}"
  echo "  bridge_ok=$bridge_ok db_ok=$db_ok playerdata_ok=$playerdata_ok"
  echo "  log=$LOG"
  if [ "$playerdata_ok" -ne 1 ]; then
    echo "WARN: YaPPlayerData enable line not confirmed — check log for shared pool use" >&2
  fi
  exit 0
fi
echo "FAIL: Folia plugin smoke did not become ready (bridge_ok=$bridge_ok db_ok=$db_ok)" >&2
tail -n 80 "$LOG" >&2 || true
exit 1
