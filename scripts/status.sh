#!/usr/bin/env bash
# Status helper
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"
cd "$ROOT"
yap_load_config
# Extra public/domain keys (optional)
PUBLIC_HOST=""
SERVER_DOMAIN=""
INTERNET_EXPOSED=false
if [ -f "$ROOT/config/server.properties" ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|\#*) continue ;; esac
    key="${line%%=*}"; val="${line#*=}"
    key="$(echo "$key" | tr -d '[:space:]')"
    case "$key" in
      public-host) PUBLIC_HOST="$val" ;;
      server-domain) SERVER_DOMAIN="$val" ;;
      internet-exposed) INTERNET_EXPOSED="$val" ;;
    esac
  done <"$ROOT/config/server.properties"
fi
if yap_is_running; then
  PID="$(yap_find_product_pid || true)"
  [ -n "$PID" ] || PID="$(yap_read_pid || true)"
  echo "YaPcore: RUNNING (pid ${PID:-unknown})"
else
  echo "YaPcore: STOPPED"
fi
BENCH_PIDS="$(yap_find_bench_pids | tr '\n' ' ' | sed 's/ $//')"
if [ -n "$BENCH_PIDS" ]; then
  echo "Bench: RUNNING (pid(s) $BENCH_PIDS — does not block gui/start)"
fi
echo "Config: max-players=$MAX_PLAYERS ram=${RAM_MIN_MB}-${RAM_MB}MB port=$PORT"
JOIN_HOST="${PUBLIC_HOST:-$SERVER_DOMAIN}"
[ -n "$JOIN_HOST" ] || JOIN_HOST="(set server-domain / public-host)"
echo "Public: exposed=$INTERNET_EXPOSED host=$JOIN_HOST java=${JOIN_HOST}:$PORT"
echo "Home: $ROOT"
echo "Docs: docs/network/NETWORKING.md"
