#!/usr/bin/env bash
# Status for yapcore-mariadb container.
set -eu

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker not installed."
  exit 1
fi

if ! docker ps -a --format '{{.Names}}' | grep -qx 'yapcore-mariadb'; then
  echo "yapcore-mariadb: not created (run ./scripts/db/start-mariadb.sh)"
  exit 0
fi

docker ps -a --filter name=yapcore-mariadb --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}}' yapcore-mariadb 2>/dev/null || echo n/a)"
echo "health: $health"
