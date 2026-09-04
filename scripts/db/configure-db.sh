#!/usr/bin/env bash
# Write / patch plugins/YaPDB/config.yml JDBC for the shared YapDb pool.
#
# Usage:
#   ./scripts/db/configure-db.sh
#   ./scripts/db/configure-db.sh --engine postgres
#   ./scripts/db/configure-db.sh --engine sqlite
#   ./scripts/db/configure-db.sh --engine postgres --host 192.168.1.10 --server-id survival
#   ./scripts/db/configure-db.sh --root /path/to/yap-home --server-id lobby
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
MARIADB_DIR="$REPO_ROOT/deploy/mariadb"
POSTGRES_DIR="$REPO_ROOT/deploy/postgres"

TARGET_ROOT="$REPO_ROOT"
HOST="127.0.0.1"
SERVER_ID=""
PROFILE=""
ENGINE="mysql"

while [ $# -gt 0 ]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --server-id) SERVER_ID="$2"; shift 2 ;;
    --profile) PROFILE="$2"; shift 2 ;;
    --engine)
      ENGINE="$(echo "$2" | tr '[:upper:]' '[:lower:]')"
      shift 2
      ;;
    --root|--plugins-parent)
      TARGET_ROOT="$(CDPATH= cd -- "$2" && pwd)"
      shift 2
      ;;
    -h|--help)
      cat <<EOF
Usage: $0 [--root DIR] [--engine mysql|postgres|sqlite] [--host IP] [--server-id name] [--profile global|server]
  Writes shared YaPDB JDBC under DIR/plugins (DIR defaults to repo root).
  Default engine: mysql (MariaDB). Optional --server-id also patches YaPPlayerData.
EOF
      exit 0
      ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

case "$ENGINE" in
  mysql|mariadb) ENGINE="mysql" ;;
  postgres|postgresql|pgsql) ENGINE="postgres" ;;
  sqlite) ENGINE="sqlite" ;;
  *) echo "Unknown --engine: $ENGINE (use mysql|postgres|sqlite)"; exit 1 ;;
esac

YAPDB_DIR="$TARGET_ROOT/plugins/YaPDB"
YAPDB_CFG="$YAPDB_DIR/config.yml"
PLAYER_DIR="$TARGET_ROOT/plugins/YaPPlayerData"
PLAYER_CFG="$PLAYER_DIR/config.yml"

USER="yap"
PASS="change-me"
DB="yap_playerdata"
JDBC=""
POOL_MAX=16
POOL_MIN=2

load_env_file() {
  local dir="$1"
  if [ ! -f "$dir/.env" ]; then
    if [ -f "$dir/.env.example" ]; then
      cp "$dir/.env.example" "$dir/.env"
    else
      echo "Missing $dir/.env"
      exit 1
    fi
  fi
  # shellcheck disable=SC1091
  set -a
  # shellcheck source=/dev/null
  . "$dir/.env"
  set +a
}

case "$ENGINE" in
  mysql)
    load_env_file "$MARIADB_DIR"
    PORT="${YAP_DB_PORT:-3306}"
    DB="${YAP_DB_NAME:-yap_playerdata}"
    USER="${YAP_DB_USER:-yap}"
    PASS="${YAP_DB_PASSWORD:-change-me}"
    JDBC="jdbc:mysql://${HOST}:${PORT}/${DB}?useSSL=false&allowPublicKeyRetrieval=true"
    ;;
  postgres)
    load_env_file "$POSTGRES_DIR"
    PORT="${YAP_PG_PORT:-5432}"
    DB="${YAP_DB_NAME:-yap_playerdata}"
    USER="${YAP_DB_USER:-yap}"
    PASS="${YAP_DB_PASSWORD:-change-me}"
    JDBC="jdbc:postgresql://${HOST}:${PORT}/${DB}"
    ;;
  sqlite)
    SQLITE_PATH="${TARGET_ROOT}/data/yap.db"
    mkdir -p "$(dirname "$SQLITE_PATH")"
    JDBC="jdbc:sqlite:${SQLITE_PATH}"
    USER=""
    PASS=""
    POOL_MAX=1
    POOL_MIN=1
    ;;
esac

mkdir -p "$YAPDB_DIR"
DEFAULT_YAPDB="$REPO_ROOT/yap-first-party/core-network/yap-db-plugin/src/main/resources/config.yml"
if [ ! -f "$YAPDB_CFG" ] && [ -f "$DEFAULT_YAPDB" ]; then
  cp "$DEFAULT_YAPDB" "$YAPDB_CFG"
elif [ ! -f "$YAPDB_CFG" ]; then
  cat > "$YAPDB_CFG" <<EOF
jdbc:
  engine: ${ENGINE}
  url: ${JDBC}
  user: ${USER}
  password: ${PASS}
pool:
  name: YaPDB
  maximum-pool-size: ${POOL_MAX}
  minimum-idle: ${POOL_MIN}
  connection-timeout-ms: 10000
EOF
fi

patch_jdbc() {
  local path="$1"
  if ! command -v python3 >/dev/null 2>&1; then
    echo "python3 required to patch $path"
    exit 1
  fi
  python3 - "$path" "$JDBC" "$USER" "$PASS" "$ENGINE" "$POOL_MAX" "$POOL_MIN" <<'PY'
import sys, re
path, jdbc, user, password, engine, pool_max, pool_min = sys.argv[1:8]

def yaml_scalar(v: str) -> str:
    if v == "" or any(ch in v for ch in " :#{}[]&*!|>'\"%@`?"):
        return '"' + v.replace("\\", "\\\\").replace('"', '\\"') + '"'
    return v

block = f"""jdbc:
  engine: {engine}
  url: {yaml_scalar(jdbc)}
  user: {yaml_scalar(user)}
  password: {yaml_scalar(password)}

pool:
  name: YaPDB
  maximum-pool-size: {pool_max}
  minimum-idle: {pool_min}
  connection-timeout-ms: 10000
"""
text = open(path, encoding="utf-8").read()
# Drop any existing jdbc/pool trees (tolerate prior corrupt patches)
text = re.sub(r'(?ms)^jdbc:\n(?:^[ \t].*\n)*', '', text)
text = re.sub(r'(?ms)^pool:\n(?:^[ \t].*\n)*', '', text)
text = text.strip() + "\n\n" + block
open(path, "w", encoding="utf-8").write(text)
PY
}

if [ -n "$SERVER_ID" ] || [ -f "$PLAYER_CFG" ] || [ -f "$REPO_ROOT/yap-first-party/core-network/playerdata-plugin/src/main/resources/config.yml" ]; then
  if [ -x "$SCRIPT_DIR/configure-playerdata.sh" ] && [ "$ENGINE" = "mysql" ]; then
    ARGS=(--root "$TARGET_ROOT" --host "$HOST")
    [ -n "$SERVER_ID" ] && ARGS+=(--server-id "$SERVER_ID")
    [ -n "$PROFILE" ] && ARGS+=(--profile "$PROFILE")
    "$SCRIPT_DIR/configure-playerdata.sh" "${ARGS[@]}" || true
  elif [ -f "$PLAYER_CFG" ] || [ "$ENGINE" != "mysql" ]; then
    mkdir -p "$PLAYER_DIR"
    if [ ! -f "$PLAYER_CFG" ]; then
      DEFAULT_PD="$REPO_ROOT/yap-first-party/core-network/playerdata-plugin/src/main/resources/config.yml"
      if [ -f "$DEFAULT_PD" ]; then
        cp "$DEFAULT_PD" "$PLAYER_CFG"
      fi
    fi
    if [ -f "$PLAYER_CFG" ]; then
      python3 - "$PLAYER_CFG" "$JDBC" "$USER" "$PASS" "${SERVER_ID:-lobby}" "${PROFILE:-global}" <<'PY'
import sys, re
path, jdbc, user, password, server_id, profile = sys.argv[1:7]
text = open(path, encoding="utf-8").read()
def set_key(key, value, text):
    pat = re.compile(rf'(?m)^([ \t]*{re.escape(key)}:\s*).*$')
    if pat.search(text):
        return pat.sub(rf'\g<1>{value}', text, count=1)
    return text
def yaml_scalar(v: str) -> str:
    if v == "" or any(ch in v for ch in " :#{}[]&*!|>'\"%@`"):
        return '"' + v.replace("\\", "\\\\").replace('"', '\\"') + '"'
    return v
text = set_key("url", yaml_scalar(jdbc), text)
text = set_key("user", yaml_scalar(user), text)
text = set_key("password", yaml_scalar(password), text)
text = set_key("server-id", server_id, text)
text = set_key("inventory-profile", profile, text)
text = re.sub(r"jdbc:(?:mysql|postgresql|sqlite):[^\s\"']+", jdbc, text)
open(path, "w", encoding="utf-8").write(text)
print(f"Updated {path}")
PY
    fi
  fi
fi

# Always write YaPDB last so playerdata helpers cannot overwrite engine/url.
patch_jdbc "$YAPDB_CFG"
echo "YaPDB engine=${ENGINE}"
echo "YaPDB JDBC → $JDBC"
echo "  config: $YAPDB_CFG"


# Patch plugin JDBC fallbacks to the same URL (all engines).
if command -v python3 >/dev/null 2>&1; then
  python3 - "$TARGET_ROOT" "$JDBC" "$USER" "$PASS" <<'PY'
import sys, re
from pathlib import Path
root, jdbc, user, password = sys.argv[1:5]
n = 0
plugins = Path(root) / "plugins"
if plugins.is_dir():
    for cfg in plugins.glob("*/config.yml"):
        text = cfg.read_text(encoding="utf-8")
        orig = text
        if "jdbc" not in text.lower() and "mysql" not in text.lower() and "postgresql" not in text.lower() and "sqlite" not in text.lower():
            continue
        text = re.sub(r"jdbc:(?:mysql|postgresql|sqlite):[^\s\"']+", jdbc, text)
        if user:
            text = re.sub(r"(?m)^([ \t]*(?:user|jdbc-user):\s*).*$", rf"\g<1>{user}", text)
        if password:
            text = re.sub(r"(?m)^([ \t]*(?:password|jdbc-password):\s*).*$", rf"\g<1>{password}", text)
        if text != orig:
            cfg.write_text(text, encoding="utf-8")
            n += 1
            print(f"  fallback JDBC → {cfg.parent.name}")
link = Path(root) / "link-data" / "plugins"
if link.is_dir():
    for cfg in link.glob("*/config.properties"):
        text = cfg.read_text(encoding="utf-8")
        if "jdbc" not in text.lower():
            continue
        orig = text
        text = re.sub(r"(?m)^(jdbc-url=).*$", rf"\g<1>{jdbc}", text)
        if user:
            text = re.sub(r"(?m)^(user=).*$", rf"\g<1>{user}", text)
        if password:
            text = re.sub(r"(?m)^(password=).*$", rf"\g<1>{password}", text)
        if text != orig:
            cfg.write_text(text, encoding="utf-8")
            n += 1
            print(f"  link JDBC → {cfg.parent.name}")
print(f"Patched {n} additional JDBC config(s)")
PY
fi

echo "Done. Restart backends after installing yap-db.jar + yap-playerdata.jar."
