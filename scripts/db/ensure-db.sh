#!/usr/bin/env bash
# One-shot: start Docker MariaDB (if needed) + write JDBC into a YaP home tree.
#
# Usage:
#   ./scripts/db/ensure-db.sh
#   ./scripts/db/ensure-db.sh --server-id lobby
#   ./scripts/db/ensure-db.sh --root /path/to/yap-home --server-id smoke
#   ./scripts/db/ensure-db.sh --host 192.168.1.10 --server-id survival
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
      echo "  Starts packaged MariaDB (unless healthy / --skip-start), then configures YaPDB + playerdata."
      echo "  For Postgres: ./scripts/db/ensure-postgres.sh"
      echo "  For SQLite:   ./scripts/db/configure-db.sh --engine sqlite --server-id lobby"
      exit 0
      ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

# shellcheck disable=SC1091
set -a
if [ -f "$REPO_ROOT/deploy/mariadb/.env" ]; then
  # shellcheck source=/dev/null
  . "$REPO_ROOT/deploy/mariadb/.env"
fi
set +a
PORT="${YAP_DB_PORT:-3306}"
DB="${YAP_DB_NAME:-yap_playerdata}"
USER="${YAP_DB_USER:-yap}"
PASS="${YAP_DB_PASSWORD:-change-me}"

need_start=1
if [ "$SKIP_START" -eq 1 ]; then
  need_start=0
elif docker inspect -f '{{.State.Health.Status}}' yapcore-mariadb 2>/dev/null | grep -qx healthy; then
  need_start=0
  echo "MariaDB container already healthy."
fi

if [ "$need_start" -eq 1 ]; then
  "$SCRIPT_DIR/start-mariadb.sh"
fi

"$SCRIPT_DIR/configure-db.sh" \
  --root "$TARGET_ROOT" \
  --host "$HOST" \
  --server-id "$SERVER_ID" \
  --profile "$PROFILE"

# Fail-closed connectivity check (same credentials plugins will use)
echo "Probing JDBC ${HOST}:${PORT}/${DB} as ${USER}…"
probe_ok=0
if command -v mysql >/dev/null 2>&1; then
  if mysql -h"$HOST" -P"$PORT" -u"$USER" -p"$PASS" --protocol=TCP \
      -e "SELECT 1" "$DB" >/dev/null 2>&1; then
    probe_ok=1
  fi
elif command -v docker >/dev/null 2>&1 \
  && docker inspect -f '{{.State.Running}}' yapcore-mariadb 2>/dev/null | grep -qx true; then
  if docker exec yapcore-mariadb \
      mariadb -u"$USER" -p"$PASS" -e "SELECT 1" "$DB" >/dev/null 2>&1; then
    # Container-local probe OK; still verify host port is reachable when host is localhost
    if [ "$HOST" = "127.0.0.1" ] || [ "$HOST" = "localhost" ]; then
      if command -v python3 >/dev/null 2>&1; then
        if python3 - "$HOST" "$PORT" <<'PY'
import socket, sys
host, port = sys.argv[1], int(sys.argv[2])
s = socket.create_connection((host, port), 3)
s.close()
PY
        then
          probe_ok=1
        fi
      else
        probe_ok=1
      fi
    else
      probe_ok=1
    fi
  fi
fi

if [ "$probe_ok" -ne 1 ]; then
  echo "FAIL: cannot authenticate to MariaDB as ${USER}@${HOST}:${PORT}/${DB}" >&2
  echo "  Check deploy/mariadb/.env (YAP_DB_PORT often 3316 when host :3306 is busy)." >&2
  echo "  Status: ./scripts/db/status-mariadb.sh" >&2
  exit 1
fi

echo "OK: MariaDB reachable; JDBC written under $TARGET_ROOT/plugins/{YaPDB,YaPPlayerData}"
