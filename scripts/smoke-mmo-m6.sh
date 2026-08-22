#!/usr/bin/env bash
# M6 smoke: Ability engine compile + unit tests + optional Folia boot.
# Usage: ./scripts/smoke-mmo-m6.sh
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT"

echo "== M6 ability pack count =="
COUNT="$(python3 - <<'PY'
from pathlib import Path
root = Path("yap-first-party/gameplay/abilities-plugin/src/main/resources/abilities")
total = 0
for f in root.glob("*.yml"):
    total += sum(1 for line in f.read_text().splitlines() if line.startswith("  ") and line.endswith(":") and not line.startswith("    "))
print(total)
PY
)"
if [ "$COUNT" -lt 200 ]; then
  echo "FAIL: expected >= 200 abilities, got $COUNT"
  exit 1
fi
echo "Ability definitions: $COUNT"

echo "== M6 unit tests =="
gradle :yap-abilities-api:compileJava :abilities-plugin:test --no-daemon -q

echo "== M6 build + install =="
gradle :yap-abilities-api:jar :abilities-plugin:installIntoPlugins \
  :combat-plugin:installIntoPlugins :skills-plugin:installIntoPlugins \
  --no-daemon -q

for j in yap-abilities.jar yap-combat.jar yap-skills.jar; do
  if [ ! -f "$ROOT/plugins/$j" ]; then
    echo "FAIL: missing plugins/$j"
    exit 1
  fi
done

if [ "${SKIP_LIVE:-0}" = "1" ]; then
  echo "SKIP_LIVE=1 — skipping Folia boot"
  echo "M6 smoke PASS (compile + unit + $COUNT abilities)"
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

WORK="$ROOT/bench/workdir-mmo-m6-smoke"
rm -rf "$WORK"
mkdir -p "$WORK/config" "$WORK/lib" "$WORK/plugins" "$WORK/logs"
/bin/cp -f "$ROOT/lib/folia-${VER}.jar" "$WORK/lib/"

for j in yap-folia-bridge.jar yap-db.jar yap-skills.jar yap-combat.jar yap-abilities.jar; do
  if [ -f "$ROOT/plugins/$j" ]; then
    /bin/cp -f "$ROOT/plugins/$j" "$WORK/plugins/"
  fi
done

"$ROOT/scripts/db/ensure-db.sh" --root "$WORK" --server-id smoke-mmo-m6

PORT=25578
cat >"$WORK/config/server.properties" <<EOF
server-name=YaP-MMO-M6-Smoke
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

echo "Booting Folia with YaP Abilities (port $PORT)…"
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
    echo "Server exited early:"
    tail -n 40 "$LOG" || true
    exit 1
  fi
  sleep 1
done

if [ "$ready" -eq 0 ]; then
  echo "Timed out waiting for YaPAbilities"
  tail -n 50 "$LOG" || true
  exit 1
fi

echo "M6 smoke PASS (live boot + YaPAbilities ready, $COUNT abilities)"
chmod +x /home/xydroc/Desktop/YaPcore/scripts/smoke-mmo-m6.sh 2>/dev/null || true
