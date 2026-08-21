#!/usr/bin/env bash
# Stop packaged MariaDB (keeps data volume).
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_DIR="$ROOT/deploy/mariadb"

cd "$COMPOSE_DIR"
if docker compose version >/dev/null 2>&1; then
  docker compose down
elif command -v docker-compose >/dev/null 2>&1; then
  docker-compose down
else
  echo "Docker Compose not available."
  exit 1
fi
echo "MariaDB stopped (data volume retained)."
