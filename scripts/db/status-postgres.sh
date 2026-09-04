#!/usr/bin/env bash
# Show packaged Postgres container status.
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker not installed."
  exit 1
fi

if ! docker inspect yapcore-postgres >/dev/null 2>&1; then
  echo "yapcore-postgres: not created (run ./scripts/db/start-postgres.sh)"
  exit 1
fi

docker ps -a --filter name=yapcore-postgres --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
status="$(docker inspect -f '{{.State.Health.Status}}' yapcore-postgres 2>/dev/null || echo none)"
echo "health: $status"

if [ -f "$ROOT/deploy/postgres/.env" ]; then
  # shellcheck disable=SC1091
  set -a
  # shellcheck source=/dev/null
  . "$ROOT/deploy/postgres/.env"
  set +a
  echo "JDBC: jdbc:postgresql://127.0.0.1:${YAP_PG_PORT:-5432}/${YAP_DB_NAME:-yap_playerdata}"
fi
