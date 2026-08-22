#!/usr/bin/env bash
# Start packaged MariaDB for YaPPlayerData (Docker Compose).
# Usage: ./scripts/db/start-mariadb.sh [--configure] [--root DIR] [--server-id name]
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_DIR="$ROOT/deploy/mariadb"

DO_CONFIGURE=0
CFG_ROOT="$ROOT"
CFG_SERVER_ID="lobby"

while [ $# -gt 0 ]; do
  case "$1" in
    --configure) DO_CONFIGURE=1; shift ;;
    --root) CFG_ROOT="$(CDPATH= cd -- "$2" && pwd)"; shift 2 ;;
    --server-id) CFG_SERVER_ID="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--configure] [--root DIR] [--server-id name]"
      echo "  Starts Docker MariaDB. With --configure, also writes JDBC into DIR/plugins."
      exit 0
      ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker not found. Install Docker Engine or Docker Desktop, then retry."
  echo "  Linux: https://docs.docker.com/engine/install/"
  echo "  Windows/macOS: https://docs.docker.com/desktop/"
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker is installed but not running. Start Docker and retry."
  exit 1
fi

mkdir -p "$COMPOSE_DIR"
if [ ! -f "$COMPOSE_DIR/.env" ]; then
  cp "$COMPOSE_DIR/.env.example" "$COMPOSE_DIR/.env"
  echo "Created deploy/mariadb/.env from .env.example — change YAP_DB_PASSWORD for production."
fi

# If host :3306 is already taken by something other than our container, bump YAP_DB_PORT.
port_in_use() {
  local p="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -ltn "( sport = :$p )" 2>/dev/null | grep -q ":$p"
  elif command -v python3 >/dev/null 2>&1; then
    python3 - "$p" <<'PY'
import socket, sys
p = int(sys.argv[1])
s = socket.socket()
s.settimeout(0.3)
try:
    s.connect(("127.0.0.1", p))
    sys.exit(0)
except Exception:
    sys.exit(1)
finally:
    s.close()
PY
  else
    return 1
  fi
}

# shellcheck disable=SC1091
set -a
# shellcheck source=/dev/null
. "$COMPOSE_DIR/.env"
set +a
CUR_PORT="${YAP_DB_PORT:-3306}"
OUR_RUNNING=0
if docker inspect -f '{{.State.Running}}' yapcore-mariadb 2>/dev/null | grep -qx true; then
  OUR_RUNNING=1
fi
if [ "$OUR_RUNNING" -eq 0 ] && [ "$CUR_PORT" = "3306" ] && port_in_use 3306; then
  if ! port_in_use 3316; then
    echo "Host :3306 is busy — setting YAP_DB_PORT=3316 in deploy/mariadb/.env"
    if command -v python3 >/dev/null 2>&1; then
      python3 - "$COMPOSE_DIR/.env" <<'PY'
import re, sys
path = sys.argv[1]
text = open(path, encoding="utf-8").read()
if re.search(r'(?m)^YAP_DB_PORT=', text):
    text = re.sub(r'(?m)^YAP_DB_PORT=.*$', 'YAP_DB_PORT=3316', text, count=1)
else:
    text += '\nYAP_DB_PORT=3316\n'
open(path, 'w', encoding='utf-8').write(text)
PY
    else
      echo "YAP_DB_PORT=3316" >> "$COMPOSE_DIR/.env"
    fi
  else
    echo "WARN: host :3306 busy and :3316 also busy — edit deploy/mariadb/.env YAP_DB_PORT manually."
  fi
fi

cd "$COMPOSE_DIR"
if docker compose version >/dev/null 2>&1; then
  docker compose up -d
elif command -v docker-compose >/dev/null 2>&1; then
  docker-compose up -d
else
  echo "Neither 'docker compose' nor 'docker-compose' is available."
  exit 1
fi

echo ""
echo "Waiting for MariaDB healthy..."
for i in $(seq 1 40); do
  status="$(docker inspect -f '{{.State.Health.Status}}' yapcore-mariadb 2>/dev/null || echo starting)"
  if [ "$status" = "healthy" ]; then
    echo "MariaDB is ready."
    # shellcheck disable=SC1091
    set -a
    # shellcheck source=/dev/null
    . "$COMPOSE_DIR/.env"
    set +a
    PORT="${YAP_DB_PORT:-3306}"
    echo "  JDBC: jdbc:mysql://127.0.0.1:${PORT}/${YAP_DB_NAME:-yap_playerdata}?useSSL=false&allowPublicKeyRetrieval=true"
    echo "  User: ${YAP_DB_USER:-yap}"
    echo ""
    if [ "$DO_CONFIGURE" -eq 1 ]; then
      "$SCRIPT_DIR/configure-db.sh" --root "$CFG_ROOT" --server-id "$CFG_SERVER_ID"
    else
      echo "Next (one-shot): ./scripts/db/ensure-db.sh --server-id lobby"
      echo "  or:            ./scripts/db/configure-db.sh --server-id lobby"
      echo "  (multi-backend: add --host <db-ip> --server-id <name>)"
    fi
    exit 0
  fi
  sleep 1
done

echo "MariaDB container started but health check timed out. Check: docker logs yapcore-mariadb"
exit 1
