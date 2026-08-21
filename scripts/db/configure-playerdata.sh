#!/usr/bin/env bash
# Write / patch plugins/YaPPlayerData/config.yml JDBC for this machine.
# Usage:
#   ./scripts/db/configure-playerdata.sh
#   ./scripts/db/configure-playerdata.sh --host 192.168.1.10 --server-id survival --profile global
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_DIR="$ROOT/deploy/mariadb"
PLUGIN_DIR="$ROOT/plugins/YaPPlayerData"
CONFIG="$PLUGIN_DIR/config.yml"

HOST="127.0.0.1"
SERVER_ID="lobby"
PROFILE="global"

while [ $# -gt 0 ]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --server-id) SERVER_ID="$2"; shift 2 ;;
    --profile) PROFILE="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--host IP] [--server-id name] [--profile global|server]"
      exit 0
      ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if [ ! -f "$COMPOSE_DIR/.env" ]; then
  if [ -f "$COMPOSE_DIR/.env.example" ]; then
    cp "$COMPOSE_DIR/.env.example" "$COMPOSE_DIR/.env"
  else
    echo "Missing deploy/mariadb/.env — run start-mariadb.sh first."
    exit 1
  fi
fi

# shellcheck disable=SC1091
set -a
# shellcheck source=/dev/null
. "$COMPOSE_DIR/.env"
set +a

PORT="${YAP_DB_PORT:-3306}"
DB="${YAP_DB_NAME:-yap_playerdata}"
USER="${YAP_DB_USER:-yap}"
PASS="${YAP_DB_PASSWORD:-change-me}"
JDBC="jdbc:mysql://${HOST}:${PORT}/${DB}?useSSL=false&allowPublicKeyRetrieval=true"

mkdir -p "$PLUGIN_DIR"

DEFAULT_CFG="$ROOT/playerdata-plugin/src/main/resources/config.yml"
if [ ! -f "$CONFIG" ] && [ -f "$DEFAULT_CFG" ]; then
  cp "$DEFAULT_CFG" "$CONFIG"
elif [ ! -f "$CONFIG" ]; then
  cat > "$CONFIG" <<EOF
server-id: ${SERVER_ID}
inventory-profile: ${PROFILE}
jdbc:
  url: ${JDBC}
  user: ${USER}
  password: ${PASS}
EOF
fi

# Portable patch without requiring yq: rewrite known keys via awk/sed-safe python if available
if command -v python3 >/dev/null 2>&1; then
  python3 - "$CONFIG" "$SERVER_ID" "$PROFILE" "$JDBC" "$USER" "$PASS" <<'PY'
import sys, re
path, server_id, profile, jdbc, user, password = sys.argv[1:7]
text = open(path, encoding="utf-8").read()
def set_scalar(key, value, text):
    # top-level or nested jdbc keys
    pat = re.compile(rf'(?m)^({re.escape(key)}:\s*).*$')
    if pat.search(text):
        return pat.sub(rf'\g<1>{value}', text, count=1)
    return text
text = set_scalar("server-id", server_id, text)
text = set_scalar("inventory-profile", profile, text)
# jdbc block
text = re.sub(r'(?m)^(  url:\s*).*$', rf'\g<1>{jdbc}', text, count=1)
text = re.sub(r'(?m)^(  user:\s*).*$', rf'\g<1>{user}', text, count=1)
text = re.sub(r'(?m)^(  password:\s*).*$', rf'\g<1>{password}', text, count=1)
open(path, "w", encoding="utf-8").write(text)
print(f"Updated {path}")
PY
else
  # Fallback: write jdbc snippet sidecar owners can merge
  cat > "$PLUGIN_DIR/jdbc.generated.yml" <<EOF
# Merge into config.yml (python3 not found for auto-patch)
server-id: ${SERVER_ID}
inventory-profile: ${PROFILE}
jdbc:
  url: ${JDBC}
  user: ${USER}
  password: ${PASS}
EOF
  echo "Wrote $PLUGIN_DIR/jdbc.generated.yml — merge into config.yml (install python3 for auto-patch)."
fi

echo ""
echo "PlayerData JDBC ready:"
echo "  server-id: $SERVER_ID"
echo "  inventory-profile: $PROFILE"
echo "  url: $JDBC"
echo "  user: $USER"
echo ""

# Also configure shared YaPDB when that plugin is present / shipped
YAPDB_DIR="$ROOT/plugins/YaPDB"
YAPDB_CFG="$YAPDB_DIR/config.yml"
mkdir -p "$YAPDB_DIR"
DEFAULT_YAPDB="$ROOT/yap-db-plugin/src/main/resources/config.yml"
if [ ! -f "$YAPDB_CFG" ] && [ -f "$DEFAULT_YAPDB" ]; then
  cp "$DEFAULT_YAPDB" "$YAPDB_CFG"
fi
if [ -f "$YAPDB_CFG" ] && command -v python3 >/dev/null 2>&1; then
  python3 - "$YAPDB_CFG" "$JDBC" "$USER" "$PASS" <<'PY'
import sys, re
path, jdbc, user, password = sys.argv[1:5]
text = open(path, encoding="utf-8").read()
def set_key(key, value, text):
    pat = re.compile(rf'(?m)^([ \t]*{re.escape(key)}:\s*).*$')
    if pat.search(text):
        return pat.sub(rf'\g<1>{value}', text, count=1)
    return text
text = set_key("url", jdbc, text)
text = set_key("user", user, text)
text = set_key("password", password, text)
open(path, "w", encoding="utf-8").write(text)
print(f"Updated {path} (shared YaPDB)")
PY
fi

echo "Restart YaPcore so YaPDB + YaPPlayerData connect."
echo "Tip: ./scripts/db/configure-db.sh  (shared pool only)"
