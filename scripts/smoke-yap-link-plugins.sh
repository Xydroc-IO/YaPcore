#!/usr/bin/env bash
# Smoke: native YaP Link loads YaP Link plugins (Phase 3+ gate).
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

yap_require_java
HOME_DIR="$ROOT/bench/link-plugins-smoke"
rm -rf "$HOME_DIR"
mkdir -p "$HOME_DIR/plugins"

echo "Building YaP Link + plugins…"
gradle :yap-link-native:shadowJar \
  :yap-link-plugin-chat-bridge:installIntoLinkPlugins \
  :yap-link-plugin-mod-sync:installIntoLinkPlugins \
  :yap-link-plugin-server-selector:installIntoLinkPlugins \
  :yap-link-plugin-tab-bridge:installIntoLinkPlugins \
  :yap-link-plugin-discord:installIntoLinkPlugins \
  --no-daemon -q

JAR="$ROOT/yap-first-party/link/native/build/libs/yap-link.jar"
cp -a "$ROOT/link-data/." "$HOME_DIR/"
mkdir -p "$HOME_DIR/plugins"
/bin/cp -f "$ROOT/link-data/plugins/"*.jar "$HOME_DIR/plugins/"

cat >"$HOME_DIR/link.properties" <<EOF
bind=127.0.0.1:25578
motd=Plugin smoke
max-players=20
online-mode=false
player-info-forwarding-mode=modern
forwarding-secret-file=forwarding.secret
servers.lobby=127.0.0.1:25566
try=lobby
plugins-enabled=true
bedrock-enabled=false
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$HOME_DIR/link.log"
: >"$LOG"

timeout 25 "$JAVA_BIN" -Xms128M -Xmx256M -jar "$JAR" --home "$HOME_DIR" >>"$LOG" 2>&1 &
PID=$!

if ! yap_wait_log_grep "$LOG" 'Enabled plugin yaplink-discord' 20; then
  kill "$PID" 2>/dev/null || true
  wait "$PID" 2>/dev/null || true
  echo "FAIL: Link did not enable plugins in time" >&2
  tail -n 40 "$LOG" >&2
  exit 1
fi

count=0
for id in yaplink-chat-bridge yaplink-mod-sync yaplink-server-selector yaplink-tab-bridge yaplink-discord; do
  if grep -q "Enabled plugin $id" "$LOG"; then
    count=$((count + 1))
  fi
done

kill "$PID" 2>/dev/null || true
wait "$PID" 2>/dev/null || true

if [ "$count" -ge 5 ]; then
  echo "PASS: $count/5 YaP Link plugins loaded"
  exit 0
fi
echo "FAIL: expected 5 plugins loaded, got $count" >&2
tail -n 40 "$LOG" >&2
exit 1
