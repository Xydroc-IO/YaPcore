#!/usr/bin/env bash
# Start packaged MariaDB for YaPPlayerData (Docker Compose).
# Usage: ./scripts/db/start-mariadb.sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_DIR="$ROOT/deploy/mariadb"

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
    echo "Next: ./scripts/db/configure-playerdata.sh"
    echo "      (multi-backend: ./scripts/db/configure-playerdata.sh --host <db-ip> --server-id <name>)"
    exit 0
  fi
  sleep 1
done

echo "MariaDB container started but health check timed out. Check: docker logs yapcore-mariadb"
exit 1
