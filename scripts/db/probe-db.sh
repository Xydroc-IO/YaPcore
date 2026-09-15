#!/usr/bin/env bash
# Probe common DB listeners (MariaDB/MySQL/Postgres) and print configure hints.
#
# Usage:
#   ./scripts/db/probe-db.sh
#   ./scripts/db/probe-db.sh --host 192.168.1.10
set -eu

HOST="127.0.0.1"
TIMEOUT=1

while [ $# -gt 0 ]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --timeout) TIMEOUT="$2"; shift 2 ;;
    -h|--help)
      cat <<EOF
Usage: $0 [--host IP] [--timeout SEC]
  TCP-probes 3306 (MySQL/MariaDB), 3316 (YaP docker MariaDB), 5432 (Postgres).
  Does not guess passwords — only reports open ports.
  In-game: /yapdb probe [host]  (also prints JDBC product when pool is open)
EOF
      exit 0
      ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

probe() {
  local label="$1" port="$2" note="$3"
  if timeout "$TIMEOUT" bash -c "echo >/dev/tcp/${HOST}/${port}" 2>/dev/null; then
    echo "  [OPEN]   ${HOST}:${port}  ${label} — ${note}"
    return 0
  fi
  echo "  [closed] ${HOST}:${port}  ${label}"
  return 1
}

echo "YaPDB TCP probe host=${HOST}"
any=0
probe "MariaDB/MySQL" 3306 "default host/docker" && any=1 || true
probe "MariaDB (YaP docker)" 3316 "deploy/mariadb → 3316" && any=1 || true
probe "PostgreSQL" 5432 "default Postgres" && any=1 || true

if [ "$any" -eq 0 ]; then
  echo "No common DB ports answered."
  echo "  ./scripts/db/start-mariadb.sh"
  echo "  ./scripts/db/start-postgres.sh"
  echo "  ./scripts/db/configure-db.sh --engine sqlite"
  exit 1
fi

echo "Next: ./scripts/db/configure-db.sh --engine mysql|postgres|sqlite --host ${HOST}"
echo "In-game product check (after Folia up): /yapdb status · /yapdb probe"
exit 0
