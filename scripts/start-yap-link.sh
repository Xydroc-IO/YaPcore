#!/usr/bin/env bash
# Build & start YaP Link (native proxy).
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

yap_require_java
HOME_DIR="${1:-$ROOT/link-data}"
mkdir -p "$HOME_DIR"

if [ -f "$ROOT/forwarding.secret" ] && [ ! -f "$HOME_DIR/forwarding.secret" ]; then
  cp -f "$ROOT/forwarding.secret" "$HOME_DIR/forwarding.secret"
  echo "Copied forwarding.secret → $HOME_DIR"
fi

JAVA_BIN="$(yap_java_bin)"

echo "Building YaP Link (native) + plugins…"
( cd "$ROOT" && gradle :yap-link-native:shadowJar \
    :yap-link-plugin-chat-bridge:installIntoLinkPlugins \
    :yap-link-plugin-mod-sync:installIntoLinkPlugins \
    :yap-link-plugin-server-selector:installIntoLinkPlugins \
    :yap-link-plugin-tab-bridge:installIntoLinkPlugins \
    :yap-link-plugin-discord:installIntoLinkPlugins \
    --no-daemon -q )
JAR="$ROOT/yap-first-party/link/native/build/libs/yap-link.jar"
if [ ! -f "$JAR" ]; then
  echo "Missing native yap-link.jar — run: gradle :yap-link-native:shadowJar" >&2
  exit 1
fi
if [ ! -f "$HOME_DIR/link.properties" ]; then
  mkdir -p "$HOME_DIR/plugins"
  if [ -d "$ROOT/link-data/plugins" ]; then
    cp -n "$ROOT/link-data/plugins/"*.jar "$HOME_DIR/plugins/" 2>/dev/null || true
  fi
  cat >"$HOME_DIR/link.properties" <<EOF
# YaP Link — native proxy (see docs/YAP_LINK_NATIVE.md)
bind=0.0.0.0:25565
motd=YaP Link
max-players=500
online-mode=false
player-info-forwarding-mode=modern
forwarding-secret-file=forwarding.secret
# Multi-backend example — adjust hosts/ports to your Folia boxes
servers.lobby=127.0.0.1:25566
servers.survival=127.0.0.1:25567
try=lobby,survival
forced-host.lobby.yaplabs.us=lobby
force-default-server=true
enable-server-command=true
public-host=127.0.0.1
public-port=0
bedrock-enabled=false
floodgate-key-file=floodgate-key.pem
# Code default is plugins-enabled=false; first-run seed opts in + installs jars above
plugins-enabled=true
ping-passthrough=true
backend-probe-interval-sec=10
backend-probe-timeout-ms=3000
connect-timeout-ms=10000
login-timeout-ms=30000
read-timeout-sec=300
aggregate-player-count=true
chat-relay-enabled=true
chat-relay-channel=network
chat-relay-format=[{server}] {name}: {message}
chat-join-announce=false
global-tab-list=false
EOF
  echo "Wrote $HOME_DIR/link.properties"
fi
echo "Starting YaP Link (native)"

echo "  home=$HOME_DIR"
echo "  jar=$JAR"
cd "$HOME_DIR"
exec "$JAVA_BIN" -Xms512M -Xmx1G -jar "$JAR" --home "$HOME_DIR"
