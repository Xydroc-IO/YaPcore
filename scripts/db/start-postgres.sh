#!/usr/bin/env bash
# Start packaged PostgreSQL for YaPDB (Docker Compose).
# Usage: ./scripts/db/start-postgres.sh [--configure] [--root DIR] [--server-id name]
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_DIR="$ROOT/deploy/postgres"

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
      echo "  Starts Docker Postgres. With --configure, also writes JDBC into DIR/plugins."
      exit 0
      ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker not found. Install Docker Engine or Docker Desktop, then retry."
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker is installed but not running. Start Docker and retry."
  exit 1
fi

mkdir -p "$COMPOSE_DIR"
if [ ! -f "$COMPOSE_DIR/.env" ]; then
  cp "$COMPOSE_DIR/.env.example" "$COMPOSE_DIR/.env"
  echo "Created deploy/postgres/.env from .env.example — change YAP_DB_PASSWORD for production."
fi

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
CUR_PORT="${YAP_PG_PORT:-5432}"
OUR_RUNNING=0
if docker inspect -f '{{.State.Running}}' yapcore-postgres 2>/dev/null | grep -qx true; then
  OUR_RUNNING=1
fi
if [ "$OUR_RUNNING" -eq 0 ] && [ "$CUR_PORT" = "5432" ] && port_in_use 5432; then
  ALT=""
  for try in 5433 5434 5435; do
    if ! port_in_use "$try"; then
      ALT="$try"
      break
    fi
  done
  if [ -n "$ALT" ]; then
    echo "Host :5432 is busy — setting YAP_PG_PORT=$ALT in deploy/postgres/.env"
    if command -v python3 >/dev/null 2>&1; then
      python3 - "$COMPOSE_DIR/.env" "$ALT" <<'PY'
import re, sys
path, alt = sys.argv[1], sys.argv[2]
text = open(path, encoding="utf-8").read()
if re.search(r'(?m)^YAP_PG_PORT=', text):
    text = re.sub(r'(?m)^YAP_PG_PORT=.*$', f'YAP_PG_PORT={alt}', text, count=1)
else:
    text += f'\nYAP_PG_PORT={alt}\n'
open(path, 'w', encoding='utf-8').write(text)
PY
    else
      echo "YAP_PG_PORT=$ALT" >> "$COMPOSE_DIR/.env"
    fi
  else
    echo "WARN: host :5432–5435 busy — edit deploy/postgres/.env YAP_PG_PORT manually."
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
echo "Waiting for Postgres healthy..."
for i in $(seq 1 40); do
  status="$(docker inspect -f '{{.State.Health.Status}}' yapcore-postgres 2>/dev/null || echo starting)"
  if [ "$status" = "healthy" ]; then
    echo "Postgres is ready."
    # shellcheck disable=SC1091
    set -a
    # shellcheck source=/dev/null
    . "$COMPOSE_DIR/.env"
    set +a
    PORT="${YAP_PG_PORT:-5432}"
    echo "  JDBC: jdbc:postgresql://127.0.0.1:${PORT}/${YAP_DB_NAME:-yap_playerdata}"
    echo "  User: ${YAP_DB_USER:-yap}"
    echo ""
    if [ "$DO_CONFIGURE" -eq 1 ]; then
      "$SCRIPT_DIR/configure-db.sh" --engine postgres --root "$CFG_ROOT" --server-id "$CFG_SERVER_ID"
    else
      echo "Next (one-shot): ./scripts/db/ensure-postgres.sh --server-id lobby"
      echo "  or:            ./scripts/db/configure-db.sh --engine postgres --server-id lobby"
    fi
    exit 0
  fi
  sleep 1
done

echo "Postgres container started but health check timed out. Check: docker logs yapcore-postgres"
exit 1
