#!/usr/bin/env bash
# Write / patch plugins/YaPDB/config.yml JDBC for the shared YapDb pool.
# Also patches YaPPlayerData jdbc fallback when that config exists.
#
# Usage:
#   ./scripts/db/configure-db.sh
#   ./scripts/db/configure-db.sh --host 192.168.1.10
#   ./scripts/db/configure-db.sh --host 192.168.1.10 --server-id survival
#   ./scripts/db/configure-db.sh --root /path/to/yap-home --server-id lobby
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_DIR="$REPO_ROOT/deploy/mariadb"

# Target tree that owns plugins/ (repo root by default; smoke workdirs pass --root)
TARGET_ROOT="$REPO_ROOT"
HOST="127.0.0.1"
SERVER_ID=""
PROFILE=""

while [ $# -gt 0 ]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --server-id) SERVER_ID="$2"; shift 2 ;;
    --profile) PROFILE="$2"; shift 2 ;;
    --root|--plugins-parent)
      TARGET_ROOT="$(CDPATH= cd -- "$2" && pwd)"
      shift 2
      ;;
    -h|--help)
      echo "Usage: $0 [--root DIR] [--host IP] [--server-id name] [--profile global|server]"
      echo "  Writes shared YaPDB JDBC under DIR/plugins (DIR defaults to repo root)."
      echo "  Optional --server-id also patches YaPPlayerData."
      exit 0
      ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

YAPDB_DIR="$TARGET_ROOT/plugins/YaPDB"
YAPDB_CFG="$YAPDB_DIR/config.yml"
PLAYER_DIR="$TARGET_ROOT/plugins/YaPPlayerData"
PLAYER_CFG="$PLAYER_DIR/config.yml"

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

mkdir -p "$YAPDB_DIR"
DEFAULT_YAPDB="$REPO_ROOT/yap-db-plugin/src/main/resources/config.yml"
if [ ! -f "$YAPDB_CFG" ] && [ -f "$DEFAULT_YAPDB" ]; then
  cp "$DEFAULT_YAPDB" "$YAPDB_CFG"
elif [ ! -f "$YAPDB_CFG" ]; then
  cat > "$YAPDB_CFG" <<EOF
jdbc:
  url: ${JDBC}
  user: ${USER}
  password: ${PASS}
pool:
  name: YaPDB
  maximum-pool-size: 16
  minimum-idle: 2
  connection-timeout-ms: 10000
EOF
fi

patch_jdbc() {
  local path="$1"
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$path" "$JDBC" "$USER" "$PASS" <<'PY'
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
PY
  else
    echo "python3 required to patch $path"
    exit 1
  fi
}

patch_jdbc "$YAPDB_CFG"
echo "YaPDB JDBC → $JDBC"
echo "  config: $YAPDB_CFG"

# Optional: also configure playerdata (server-id / fallback jdbc)
if [ -n "$SERVER_ID" ] || [ -f "$PLAYER_CFG" ] || [ -f "$REPO_ROOT/playerdata-plugin/src/main/resources/config.yml" ]; then
  if [ -x "$SCRIPT_DIR/configure-playerdata.sh" ]; then
    ARGS=(--root "$TARGET_ROOT" --host "$HOST")
    [ -n "$SERVER_ID" ] && ARGS+=(--server-id "$SERVER_ID")
    [ -n "$PROFILE" ] && ARGS+=(--profile "$PROFILE")
    "$SCRIPT_DIR/configure-playerdata.sh" "${ARGS[@]}"
  fi
fi

echo "Done. Restart backends after installing yap-db.jar + yap-playerdata.jar."
