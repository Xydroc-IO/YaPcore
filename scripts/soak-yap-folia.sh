#!/usr/bin/env bash
# YaP-Folia soak — mem / crash / uptime confidence for the product path.
# Usage:
#   ./scripts/soak-yap-folia.sh compat              # boot + API smoke (~5–15 min)
#   ./scripts/soak-yap-folia.sh perf [minutes]      # load + heap samples (default 30)
#   ./scripts/soak-yap-folia.sh long [hours]        # slope soak (default 12, min 8 via YAP_SOAK_HOURS)
#
# Env:
#   YAP_SOAK_HOURS=12          long-mode duration (hours)
#   YAP_SOAK_SAMPLE_SEC=300    sample interval for long mode
#   YAP_SOAK_KEEP=1            do not stop server when finished
#   YAP_SOAK_SKIP_START=1      assume server already running
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib.sh"
cd "$ROOT"

MODE="${1:-compat}"
shift || true

REPORT_DIR="$ROOT/logs/soak"
mkdir -p "$REPORT_DIR"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
REPORT="$REPORT_DIR/soak-${MODE}-${STAMP}.log"
CSV="$REPORT_DIR/soak-${MODE}-${STAMP}.csv"
LATEST_LOG="$REPORT_DIR/latest.log"
LATEST_CSV="$REPORT_DIR/latest.csv"

yap_banner "soak-yap-folia · ${MODE}"

heap_mb() {
  local pid="$1"
  local out used
  out="$(jcmd "$pid" GC.heap_info 2>/dev/null || true)"
  if [ -z "$out" ]; then
    out="$(jcmd "$pid" VM.native_memory summary 2>/dev/null || true)"
  fi
  used="$(printf '%s\n' "$out" | sed -nE 's/.*used[= ]*([0-9]+)([KMG]).*/\1 \2/ip' | head -n 1)"
  if [ -z "$used" ]; then
    echo ""
    return 0
  fi
  # shellcheck disable=SC2086
  set -- $used
  local n="$1" unit
  unit="$(echo "$2" | tr '[:lower:]' '[:upper:]')"
  case "$unit" in
    G) awk -v n="$n" 'BEGIN{printf "%.0f", n*1024}' ;;
    K) awk -v n="$n" 'BEGIN{printf "%.0f", n/1024}' ;;
    *) echo "$n" ;;
  esac
}

folia_pid() {
  # Prefer the real Folia JVM only — avoid pgrep -f false positives (shells, soak script, cwd paths).
  local pid=""
  while IFS= read -r pid; do
    [ -n "$pid" ] || continue
    if [ -r "/proc/$pid/cmdline" ] && tr '\0' ' ' <"/proc/$pid/cmdline" | grep -Eq 'java.*(folia-26|yap-folia).*\.jar'; then
      echo "$pid"
      return 0
    fi
  done < <(pgrep -f '[j]ava.*(folia-26|yap-folia).*\.jar' 2>/dev/null || true)
  # Fallback: child of chassis with folia-kernel cwd
  local cpid
  cpid="$(chassis_pid)"
  if [ -n "$cpid" ]; then
    for pid in $(pgrep -P "$cpid" 2>/dev/null || true); do
      if [ -r "/proc/$pid/cmdline" ] && tr '\0' ' ' <"/proc/$pid/cmdline" | grep -Eq 'java.*(folia|yap-folia)'; then
        echo "$pid"
        return 0
      fi
    done
  fi
  return 0
}

chassis_pid() {
  yap_find_product_pid || true
}

thread_count() {
  local pid="$1"
  [ -n "$pid" ] || { echo ""; return; }
  if [ -d "/proc/$pid/task" ]; then
    find "/proc/$pid/task" -mindepth 1 -maxdepth 1 | wc -l
  else
    echo ""
  fi
}

dashboard_token() {
  local props="$ROOT/config/server.properties"
  [ -f "$props" ] || return 1
  sed -nE 's/^web-dashboard-token=//p' "$props" | head -n 1 | tr -d '\r'
}

wait_ready() {
  local tok deadline now
  tok="$(dashboard_token || true)"
  deadline=$(( $(date +%s) + ${1:-180} ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if [ -n "$tok" ]; then
      if curl -fsS -H "Authorization: Bearer $tok" "http://127.0.0.1:8080/api/status" >/dev/null 2>&1; then
        return 0
      fi
    fi
    if yap_is_running && [ -n "$(folia_pid)" ]; then
      # Chassis up + Folia child — treat as ready even if dashboard disabled
      return 0
    fi
    sleep 2
  done
  return 1
}

ensure_started() {
  if [ "${YAP_SOAK_SKIP_START:-0}" = "1" ]; then
    yap_is_running || { echo "YAP_SOAK_SKIP_START=1 but server not running" >&2; exit 1; }
    return 0
  fi
  if yap_is_running; then
    echo "Server already running — reusing (set YAP_SOAK_SKIP_START=1 to silence)."
    return 0
  fi
  echo "Starting YaPcore…"
  "$SCRIPT_DIR/start.sh" --nogui
  wait_ready 240 || { echo "Server did not become ready" >&2; exit 1; }
}

maybe_stop() {
  if [ "${YAP_SOAK_KEEP:-0}" = "1" ]; then
    echo "YAP_SOAK_KEEP=1 — leaving server running"
    return 0
  fi
  "$SCRIPT_DIR/stop.sh" || true
}

sample_row() {
  local elapsed="$1"
  local cpid fpid c_heap f_heap c_thr f_thr
  cpid="$(chassis_pid)"
  fpid="$(folia_pid)"
  c_heap="$(heap_mb "${cpid:-}")"
  f_heap="$(heap_mb "${fpid:-}")"
  c_thr="$(thread_count "${cpid:-}")"
  f_thr="$(thread_count "${fpid:-}")"
  # CSV only — never mix human log lines into the CSV (breaks slope parsers).
  printf '%s,%s,%s,%s,%s,%s,%s\n' \
    "$elapsed" "${cpid:-}" "${fpid:-}" "${c_heap:-}" "${f_heap:-}" "${c_thr:-}" "${f_thr:-}" >>"$CSV"
  printf 't=%ss chassis_pid=%s folia_pid=%s chassis_heap=%sMB folia_heap=%sMB chassis_thr=%s folia_thr=%s\n' \
    "$elapsed" "${cpid:-?}" "${fpid:-?}" "${c_heap:-?}" "${f_heap:-?}" "${c_thr:-?}" "${f_thr:-?}" \
    | tee -a "$REPORT"
  # Expose last Folia pid for callers via global
  LAST_FOLIA_PID="${fpid:-}"
  LAST_FOLIA_HEAP="${f_heap:-}"
}

scan_logs_for_bad() {
  local log="$ROOT/folia-kernel/logs/latest.log"
  local crash_dir="$ROOT/logs/crashes"
  local bad=0
  if [ -f "$log" ]; then
    # Only the last 80KB — full latest.log retains older noise across restarts.
    local recent
    recent="$(tail -c 80000 "$log" 2>/dev/null || true)"
    if printf '%s\n' "$recent" | grep -E 'OutOfMemoryError|owning region|Cannot modify|TickThread' >/dev/null 2>&1; then
      echo "WARN: suspicious lines in recent folia-kernel/logs/latest.log" | tee -a "$REPORT"
      printf '%s\n' "$recent" | grep -E 'OutOfMemoryError|owning region|Cannot modify|TickThread' | tail -n 20 | tee -a "$REPORT" || true
      bad=1
    fi
  fi
  if [ -d "$crash_dir" ] && find "$crash_dir" -type f -newer "$REPORT" 2>/dev/null | grep -q .; then
    echo "FAIL: new crash dumps under logs/crashes/" | tee -a "$REPORT"
    bad=1
  fi
  return "$bad"
}

slope_fail() {
  # CSV: elapsed,chassis_pid,folia_pid,chassis_heap,folia_heap,chassis_thr,folia_thr
  # Fail if Folia heap more than doubles from first third median to last third median (+256MB slack).
  python3 - "$CSV" <<'PY'
import sys, statistics
path = sys.argv[1]
rows = []
with open(path) as f:
    next(f, None)
    for line in f:
        parts = line.strip().split(",")
        if len(parts) < 5:
            continue
        try:
            eh = float(parts[0])
            fh = float(parts[4]) if parts[4] else None
            ft = float(parts[6]) if len(parts) > 6 and parts[6] else None
        except ValueError:
            continue
        if fh is None:
            continue
        rows.append((eh, fh, ft))
if len(rows) < 6:
    print("WARN: too few heap samples for slope check (%d)" % len(rows))
    sys.exit(0)
n = len(rows)
a = [r[1] for r in rows[: max(1, n // 3)]]
b = [r[1] for r in rows[-max(1, n // 3):]]
ma, mb = statistics.median(a), statistics.median(b)
print(f"folia heap median early={ma:.0f}MB late={mb:.0f}MB")
if mb > ma * 2 + 256:
    print(f"FAIL: HEAP_SLOPE folia {ma:.0f} → {mb:.0f} MB")
    sys.exit(1)
ta = [r[2] for r in rows[: max(1, n // 3)] if r[2] is not None]
tb = [r[2] for r in rows[-max(1, n // 3):] if r[2] is not None]
if ta and tb:
    mta, mtb = statistics.median(ta), statistics.median(tb)
    print(f"folia threads median early={mta:.0f} late={mtb:.0f}")
    if mtb > mta + 64:
        print(f"FAIL: THREAD_SLOPE folia {mta:.0f} → {mtb:.0f}")
        sys.exit(1)
print("PASS: heap/thread slope within bounds")
sys.exit(0)
PY
}

run_compat() {
  ensure_started
  echo "elapsed,chassis_pid,folia_pid,chassis_heap_mb,folia_heap_mb,chassis_threads,folia_threads" >"$CSV"
  sample_row 0
  local tok
  tok="$(dashboard_token || true)"
  if [ -n "$tok" ]; then
    curl -fsS -H "Authorization: Bearer $tok" "http://127.0.0.1:8080/api/status" | tee -a "$REPORT" >/dev/null \
      || { echo "FAIL: dashboard /api/status" | tee -a "$REPORT"; return 1; }
  fi
  if [ -x "$ROOT/scripts/disasters-mem-smoke.py" ] || [ -f "$ROOT/scripts/disasters-mem-smoke.py" ]; then
    python3 "$ROOT/scripts/disasters-mem-smoke.py" 2>&1 | tee -a "$REPORT" || {
      echo "FAIL: disasters-mem-smoke" | tee -a "$REPORT"
      return 1
    }
  fi
  sample_row 60
  scan_logs_for_bad || return 1
  echo "PASS: compat soak" | tee -a "$REPORT"
  return 0
}

run_perf() {
  local minutes="${1:-30}"
  ensure_started
  echo "elapsed,chassis_pid,folia_pid,chassis_heap_mb,folia_heap_mb,chassis_threads,folia_threads" >"$CSV"
  local end=$(( $(date +%s) + minutes * 60 ))
  local start now elapsed locked_folia
  start="$(date +%s)"
  sample_row 0
  locked_folia="${LAST_FOLIA_PID:-}"
  while [ "$(date +%s)" -lt "$end" ]; do
    now="$(date +%s)"
    elapsed=$(( now - start ))
    sample_row "$elapsed"
    if [ -n "$locked_folia" ] && [ -n "${LAST_FOLIA_PID:-}" ] && [ "$LAST_FOLIA_PID" != "$locked_folia" ]; then
      echo "FAIL: Folia PID changed ${locked_folia} → ${LAST_FOLIA_PID} at t=${elapsed}s (child restart)" | tee -a "$REPORT"
      return 1
    fi
    tok="$(dashboard_token || true)"
    if [ -n "$tok" ]; then
      curl -fsS -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
        -d '{"command":"yapdisaster status"}' \
        "http://127.0.0.1:8080/api/command" >/dev/null 2>&1 || true
    fi
    sleep 60
  done
  if [ -f "$ROOT/scripts/disasters-mem-smoke.py" ]; then
    python3 "$ROOT/scripts/disasters-mem-smoke.py" 2>&1 | tee -a "$REPORT" || return 1
  fi
  scan_logs_for_bad || return 1
  slope_fail || return 1
  echo "PASS: perf soak (${minutes}m)" | tee -a "$REPORT"
  return 0
}

run_long() {
  local hours="${1:-${YAP_SOAK_HOURS:-12}}"
  # Floor at 8h unless operator forces lower with YAP_SOAK_ALLOW_SHORT=1
  if [ "$hours" -lt 8 ] && [ "${YAP_SOAK_ALLOW_SHORT:-0}" != "1" ]; then
    echo "Raising soak from ${hours}h to 8h minimum (set YAP_SOAK_ALLOW_SHORT=1 to override)" | tee -a "$REPORT"
    hours=8
  fi
  local sample_sec="${YAP_SOAK_SAMPLE_SEC:-300}"
  ensure_started
  echo "Long soak ${hours}h — samples every ${sample_sec}s" | tee -a "$REPORT"
  echo "elapsed,chassis_pid,folia_pid,chassis_heap_mb,folia_heap_mb,chassis_threads,folia_threads" >"$CSV"
  local end=$(( $(date +%s) + hours * 3600 ))
  local start now elapsed locked_folia blank_heap=0
  start="$(date +%s)"
  sample_row 0
  locked_folia="${LAST_FOLIA_PID:-}"
  if [ -z "$locked_folia" ]; then
    echo "FAIL: could not resolve Folia JVM pid at soak start" | tee -a "$REPORT"
    return 1
  fi
  echo "Locked Folia pid=${locked_folia}" | tee -a "$REPORT"
  while [ "$(date +%s)" -lt "$end" ]; do
    now="$(date +%s)"
    elapsed=$(( now - start ))
    sample_row "$elapsed"
    if ! yap_is_running; then
      echo "FAIL: chassis died at t=${elapsed}s" | tee -a "$REPORT"
      return 1
    fi
    if [ -z "${LAST_FOLIA_PID:-}" ]; then
      echo "FAIL: Folia child missing at t=${elapsed}s" | tee -a "$REPORT"
      return 1
    fi
    if [ "$LAST_FOLIA_PID" != "$locked_folia" ]; then
      echo "FAIL: Folia PID changed ${locked_folia} → ${LAST_FOLIA_PID} at t=${elapsed}s (child restart)" | tee -a "$REPORT"
      return 1
    fi
    if [ -z "${LAST_FOLIA_HEAP:-}" ]; then
      blank_heap=$((blank_heap + 1))
      if [ "$blank_heap" -ge 3 ]; then
        echo "FAIL: Folia heap unreadable for ${blank_heap} samples (pid=${LAST_FOLIA_PID})" | tee -a "$REPORT"
        return 1
      fi
    else
      blank_heap=0
    fi
    scan_logs_for_bad || return 1
    sleep "$sample_sec"
  done
  slope_fail || return 1
  echo "PASS: long soak (${hours}h)" | tee -a "$REPORT"
  return 0
}

rc=0
case "$MODE" in
  compat)
    run_compat || rc=$?
    ;;
  perf)
    run_perf "${1:-30}" || rc=$?
    ;;
  long)
    run_long "${1:-}" || rc=$?
    ;;
  help|-h|--help)
    cat <<'EOF'
soak-yap-folia.sh — live Folia mem / crash soak

  compat              Boot + dashboard/disasters smoke
  perf [minutes]      Heap samples under light churn (default 30)
  long [hours]        Slope soak (default 12, minimum 8)

Env: YAP_SOAK_HOURS YAP_SOAK_SAMPLE_SEC YAP_SOAK_KEEP YAP_SOAK_SKIP_START YAP_SOAK_ALLOW_SHORT
Reports: logs/soak/
EOF
    exit 0
    ;;
  *)
    echo "Unknown mode: $MODE (compat|perf|long)" >&2
    exit 2
    ;;
esac

cp -f "$REPORT" "$LATEST_LOG" 2>/dev/null || true
cp -f "$CSV" "$LATEST_CSV" 2>/dev/null || true
echo "Report: $REPORT"
echo "CSV:    $CSV"

maybe_stop
exit "$rc"
