#!/usr/bin/env bash
# Smoke: YaPPlayerData with shops + AH enabled — compile, unit, MariaDB round-trip, Folia boot.
# Usage: ./scripts/smoke-playerdata-shops-ah.sh
#        SKIP_LIVE=1 ./scripts/smoke-playerdata-shops-ah.sh
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

echo "== PlayerData shops+AH unit tests =="
gradle :playerdata-plugin:test --tests 'com.yapcore.playerdata.EconomyFeaturesConfigTest' \
  --tests 'com.yapcore.playerdata.sync.ItemSerializerTest' --no-daemon -q

echo "== Build + install yap-playerdata =="
gradle :playerdata-plugin:installIntoPlugins :yap-db-plugin:installIntoPlugins \
  :folia-bridge-plugin:installIntoPlugins --no-daemon -q

if [ ! -f "$ROOT/plugins/yap-playerdata.jar" ]; then
  echo "FAIL: missing plugins/yap-playerdata.jar"
  exit 1
fi

# Confirm jar embeds enabled defaults
if ! jar tf "$ROOT/plugins/yap-playerdata.jar" | grep -q '^config.yml$'; then
  echo "FAIL: config.yml not in yap-playerdata.jar"
  exit 1
fi
jar xf "$ROOT/plugins/yap-playerdata.jar" config.yml
trap 'rm -f "$ROOT/config.yml"' EXIT
if ! grep -q 'shops: true' "$ROOT/config.yml" || ! grep -q 'auctions: true' "$ROOT/config.yml"; then
  echo "FAIL: jar config.yml missing shops/auctions enabled"
  cat "$ROOT/config.yml" | head -50 >&2 || true
  exit 1
fi
rm -f "$ROOT/config.yml"
trap - EXIT
echo "Jar defaults OK (shops+ah on, jobs off)"

if [ "${SKIP_LIVE:-0}" = "1" ]; then
  echo "SKIP_LIVE=1 — skipping MariaDB + Folia boot"
  echo "PlayerData shops+AH smoke PASS (compile + unit + jar defaults)"
  exit 0
fi

# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"
yap_require_java
yap_load_config

VER="${FOLIA_VERSION:-26.2}"
if [ ! -f "$ROOT/lib/folia-${VER}.jar" ]; then
  echo "Fetching Folia ${VER}…"
  "$ROOT/scripts/fetch-folia.sh" "$VER"
fi

YAP_JAR="$(yap_find_jar)"
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac

WORK="$ROOT/bench/workdir-playerdata-shops-ah-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs"
/bin/cp -f "$ROOT/lib/folia-${VER}.jar" "$WORK/lib/"
for j in yap-folia-bridge.jar yap-db.jar yap-playerdata.jar; do
  /bin/cp -f "$ROOT/plugins/$j" "$WORK/plugins/"
done

echo "Ensuring MariaDB + workdir JDBC…"
"$ROOT/scripts/db/ensure-db.sh" --root "$WORK" --server-id smoke-shops-ah

# Seed feature flags into extracted data folder (plugin may copy jar defaults on first run;
# pre-seed so reload cannot silently use an old offline tree).
mkdir -p "$WORK/plugins/YaPPlayerData"
/bin/cp -f "$ROOT/yap-first-party/core-network/playerdata-plugin/src/main/resources/config.yml" \
  "$WORK/plugins/YaPPlayerData/config.yml"
# Re-apply JDBC into that file after copy
"$ROOT/scripts/db/ensure-db.sh" --root "$WORK" --server-id smoke-shops-ah --skip-start

PORT=25579
WAIT_SECS="${1:-150}"
cat >"$WORK/config/server.properties" <<EOF
server-name=YaP-PlayerData-ShopsAH-Smoke
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
echo "Booting Folia + YaPDB + YaPPlayerData (timeout ${WAIT_SECS}s)…"
(
  exec "$JAVA_BIN" -Xms512M -Xmx2048M \
    -Dyapcore.home="$WORK" \
    -jar "$YAP_JAR" --nogui
) >>"$LOG" 2>&1 &
PID=$!
echo "  pid=$PID port=$PORT"

start_ts="$(date +%s)"
ok=0
modules_ok=0
db_ok=0
fail_db=0
while kill -0 "$PID" 2>/dev/null; do
  now="$(date +%s)"
  if [ $((now - start_ts)) -ge "$WAIT_SECS" ]; then
    break
  fi
  if grep -q 'Shared YapDb pool ready\|Using shared YaPDB' "$LOG" 2>/dev/null; then
    db_ok=1
  fi
  if grep -qE 'YaPPlayerData 0\.6.*modules=.*shops.*ah' "$LOG" 2>/dev/null \
    || grep -qE 'YaPPlayerData 0\.6.*modules=.*ah.*shops' "$LOG" 2>/dev/null; then
    modules_ok=1
  fi
  if grep -qiE '\[YaPDB\].*(Failed to open|Access denied)|\[YaPPlayerData\].*(Failed to open|Access denied|disabling YaPPlayerData)' "$LOG" 2>/dev/null; then
    fail_db=1
    break
  fi
  if [ "$db_ok" -eq 1 ] && [ "$modules_ok" -eq 1 ] && grep -q 'Managed Folia online' "$LOG" 2>/dev/null \
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

echo "---- PlayerData / DB lines ----"
grep -E 'YaPPlayerData|Shared YapDb|Using shared YaPDB|shops|auctions' "$LOG" 2>/dev/null | head -40 || true

if [ "$fail_db" -eq 1 ] || [ "$db_ok" -ne 1 ]; then
  echo "FAIL: DB did not open (db_ok=$db_ok fail_db=$fail_db)" >&2
  tail -n 80 "$LOG" >&2 || true
  exit 1
fi
if [ "$modules_ok" -ne 1 ]; then
  echo "FAIL: enable line missing shops+ah in modules=…" >&2
  grep 'YaPPlayerData 0.6' "$LOG" >&2 || true
  exit 1
fi

# SQL round-trip against migrated schema (tables created on plugin enable)
set -a
# shellcheck source=/dev/null
. "$ROOT/deploy/mariadb/.env"
set +a
HOST=127.0.0.1
PORT_DB="${YAP_DB_PORT:-3306}"
DB="${YAP_DB_NAME:-yap_playerdata}"
USER="${YAP_DB_USER:-yap}"
PASS="${YAP_DB_PASSWORD:-change-me}"
OWNER="00000000-0000-0000-0000-00000000shop"
SELLER="00000000-0000-0000-0000-00000000ah01"

run_sql() {
  if command -v mysql >/dev/null 2>&1; then
    mysql -h"$HOST" -P"$PORT_DB" -u"$USER" -p"$PASS" --protocol=TCP "$DB" "$@"
  else
    docker exec -i yapcore-mariadb mariadb -u"$USER" -p"$PASS" "$DB" "$@"
  fi
}

echo "== SQL shop + auction round-trip =="
run_sql <<SQL
DELETE FROM shops WHERE server_id='smoke-shops-ah' AND world='world' AND x=0 AND y=64 AND z=0;
INSERT INTO shops (owner_uuid, server_id, world, x, y, z, material, amount, price)
VALUES ('$OWNER', 'smoke-shops-ah', 'world', 0, 64, 0, 'DIAMOND', 1, 12.50)
ON DUPLICATE KEY UPDATE material=VALUES(material), amount=VALUES(amount), price=VALUES(price);
DELETE FROM auctions WHERE seller_uuid='$SELLER';
INSERT INTO auctions (seller_uuid, seller_name, price, item_blob, expires_at)
VALUES ('$SELLER', 'SmokeSeller', 99.00, X'DEADBEEF', DATE_ADD(NOW(), INTERVAL 2 DAY));
SQL

shop_count="$(run_sql -N -e "SELECT COUNT(*) FROM shops WHERE server_id='smoke-shops-ah' AND material='DIAMOND' AND price=12.50;")"
ah_count="$(run_sql -N -e "SELECT COUNT(*) FROM auctions WHERE seller_uuid='$SELLER' AND price=99.00 AND expires_at > NOW();")"
# cleanup
run_sql -e "DELETE FROM shops WHERE server_id='smoke-shops-ah' AND owner_uuid='$OWNER'; DELETE FROM auctions WHERE seller_uuid='$SELLER';" >/dev/null

if [ "${shop_count:-0}" -lt 1 ] || [ "${ah_count:-0}" -lt 1 ]; then
  echo "FAIL: SQL round-trip shop_count=$shop_count ah_count=$ah_count" >&2
  exit 1
fi
echo "SQL OK (shop_count=$shop_count ah_count=$ah_count)"

if [ "$ok" -eq 1 ] && [ "$modules_ok" -eq 1 ]; then
  echo "PASS: YaPPlayerData shops+AH enabled, Folia ready, schema writable"
  echo "  log=$LOG"
  exit 0
fi
echo "FAIL: Folia did not become ready (ok=$ok modules_ok=$modules_ok)" >&2
tail -n 80 "$LOG" >&2 || true
exit 1
