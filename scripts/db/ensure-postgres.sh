#!/usr/bin/env bash
# One-shot: start Docker Postgres (if needed) + write JDBC into a YaP home tree.
#
# Usage:
#   ./scripts/db/ensure-postgres.sh
#   ./scripts/db/ensure-postgres.sh --server-id lobby
#   ./scripts/db/ensure-postgres.sh --root /path/to/yap-home --server-id smoke
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"

TARGET_ROOT="$REPO_ROOT"
HOST="127.0.0.1"
SERVER_ID="lobby"
PROFILE="global"
SKIP_START=0

while [ $# -gt 0 ]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --server-id) SERVER_ID="$2"; shift 2 ;;
    --profile) PROFILE="$2"; shift 2 ;;
    --root|--plugins-parent)
      TARGET_ROOT="$(CDPATH= cd -- "$2" && pwd)"
      shift 2
      ;;
    --skip-start) SKIP_START=1; shift ;;
    -h|--help)
      echo "Usage: $0 [--root DIR] [--host IP] [--server-id name] [--profile global|server] [--skip-start]"
      echo "  Starts packaged Postgres (unless healthy / --skip-start), then configures YaPDB + playerdata."
      exit 0
      ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

# shellcheck disable=SC1091
set -a
if [ -f "$REPO_ROOT/deploy/postgres/.env" ]; then
  # shellcheck source=/dev/null
  . "$REPO_ROOT/deploy/postgres/.env"
fi
set +a
PORT="${YAP_PG_PORT:-5432}"
DB="${YAP_DB_NAME:-yap_playerdata}"
USER="${YAP_DB_USER:-yap}"
PASS="${YAP_DB_PASSWORD:-change-me}"

need_start=1
if [ "$SKIP_START" -eq 1 ]; then
  need_start=0
elif docker inspect -f '{{.State.Health.Status}}' yapcore-postgres 2>/dev/null | grep -qx healthy; then
  need_start=0
  echo "Postgres container already healthy."
fi

if [ "$need_start" -eq 1 ]; then
  "$SCRIPT_DIR/start-postgres.sh"
fi

"$SCRIPT_DIR/configure-db.sh" \
  --engine postgres \
  --root "$TARGET_ROOT" \
  --host "$HOST" \
  --server-id "$SERVER_ID" \
  --profile "$PROFILE"

echo "Probing Postgres ${HOST}:${PORT}/${DB} as ${USER}…"
probe_ok=0
if command -v psql >/dev/null 2>&1; then
  if PGPASSWORD="$PASS" psql -h "$HOST" -p "$PORT" -U "$USER" -d "$DB" -c "SELECT 1" >/dev/null 2>&1; then
    probe_ok=1
  fi
elif command -v docker >/dev/null 2>&1 \
  && docker inspect -f '{{.State.Running}}' yapcore-postgres 2>/dev/null | grep -qx true; then
  if docker exec -e PGPASSWORD="$PASS" yapcore-postgres \
      psql -U "$USER" -d "$DB" -c "SELECT 1" >/dev/null 2>&1; then
    probe_ok=1
  fi
fi

if [ "$probe_ok" -eq 1 ]; then
  echo "OK: Postgres reachable; JDBC written under ${TARGET_ROOT}/plugins/YaPDB"
  exit 0
fi

echo "WARN: could not probe Postgres (psql/docker). JDBC was still written — verify manually."
exit 0
