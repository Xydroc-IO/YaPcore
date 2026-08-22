#!/usr/bin/env bash
# Build & start full YaP Link (Velocity fork).
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
LINK="$ROOT/yap-link"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

yap_require_java
HOME_DIR="${1:-$ROOT/link-data}"
mkdir -p "$HOME_DIR"

if [ ! -x "$LINK/gradlew" ]; then
  echo "Missing $LINK/gradlew — YaP Link Velocity fork expected at yap-link/" >&2
  exit 1
fi

echo "Building YaP Link (full Velocity fork)…"
(
  cd "$LINK"
  ./gradlew :velocity-proxy:shadowJar --no-daemon -q
)

JAR="$(ls -1 "$LINK/proxy/build/libs"/yap-link*.jar 2>/dev/null | head -n 1)"
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
  # fallback name
  JAR="$(ls -1 "$LINK/proxy/build/libs"/*.jar 2>/dev/null | grep -v plain | head -n 1 || true)"
fi
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
  echo "Missing yap-link shadow jar under proxy/build/libs" >&2
  exit 1
fi

if [ -f "$ROOT/forwarding.secret" ] && [ ! -f "$HOME_DIR/forwarding.secret" ]; then
  cp -f "$ROOT/forwarding.secret" "$HOME_DIR/forwarding.secret"
  echo "Copied forwarding.secret → $HOME_DIR"
fi

# Seed velocity.toml if missing
if [ ! -f "$HOME_DIR/velocity.toml" ]; then
  cat >"$HOME_DIR/velocity.toml" <<EOF
config-version = "2.7"
bind = "0.0.0.0:25565"
motd = "<#09add3>YaP Link"
show-max-players = 500
online-mode = false
force-key-authentication = true
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"

[servers]
lobby = "127.0.0.1:25566"
try = [ "lobby" ]

[advanced]
compression-threshold = 256
EOF
  echo "Wrote $HOME_DIR/velocity.toml"
fi

JAVA_BIN="$(yap_java_bin)"
echo "Starting YaP Link (full proxy)"
echo "  home=$HOME_DIR"
echo "  jar=$JAR"
cd "$HOME_DIR"
exec "$JAVA_BIN" -Xms512M -Xmx1G -jar "$JAR"
