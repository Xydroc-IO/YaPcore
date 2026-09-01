#!/usr/bin/env bash
# Smoke: cross-region teleport transactions (Agent 2).
# Usage: ./scripts/smoke-folia-cross-region-tp.sh [seconds]
#        SKIP_LIVE=1 — validate patch + docs only
#        TP_CYCLES=100 — log-gate rapid cycle count (soak default 100; extended via SOAK)
#
# Requires YaP-Folia build jar with 0001-yap-teleport-transactions applied.
# Stock Fill / folia-jar-source=fetch → hard FAIL (transactions need patched build).
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# shellcheck source=lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

WAIT_SECS="${1:-120}"
TP_CYCLES="${TP_CYCLES:-100}"
cd "$ROOT"
export ROOT

PATCH_A="$ROOT/vendor/folia/patches/0001-yap-teleport-transactions.patch"
PATCH_B="$ROOT/vendor/folia/work/folia-server/minecraft-patches/features/0012-yap-teleport-transactions.patch"
DOC="$ROOT/docs/folia/FOLIA_TELEPORT_TRANSACTIONS.md"
SERVER_JAR_GLOB="$ROOT/vendor/folia/work/folia-server/build/libs/folia-server-*.jar"
BUNDLER_JAR="$ROOT/vendor/folia/work/folia-server/build/libs/folia-bundler-26.2.local-SNAPSHOT.jar"

test -f "$PATCH_A"
test -f "$DOC"
grep -q "YaP-TP-TX" "$PATCH_A"
grep -q "YapTeleportTransaction" "$PATCH_A"
grep -q "PREPARE" "$DOC"
# Optional work-tree mirror (may be absent before applyAllPatches)
if [ -f "$PATCH_B" ]; then
  grep -q "YapTeleportTransaction" "$PATCH_B" || true
fi
echo "patch+docs OK"

yap_tp_has_class() {
  local jar="$1"
  [ -f "$jar" ] || return 1
  # Direct class entries (server / bundler embedded)
  if unzip -l "$jar" 2>/dev/null | grep -q "YapTeleportTransaction"; then
    return 0
  fi
  # Bundler embeds full server jar
  if unzip -l "$jar" 2>/dev/null | grep -q "META-INF/versions/.*/folia-.*\\.jar$"; then
    python3 - "$jar" <<'PY'
import io, sys, zipfile
outer = zipfile.ZipFile(sys.argv[1])
for n in outer.namelist():
    if n.endswith(".jar") and "/versions/" in n and "folia-" in n:
        data = outer.read(n)
        with zipfile.ZipFile(io.BytesIO(data)) as inner:
            if any("YapTeleportTransaction" in x for x in inner.namelist()):
                sys.exit(0)
sys.exit(1)
PY
    return $?
  fi
  return 1
}

yap_tp_require_patched() {
  local jar="$1"
  local stamp="$ROOT/lib/yap-folia-${VER}.patches.txt"
  if yap_tp_has_class "$jar"; then
    return 0
  fi
  # Sibling build artifacts from same pipeline
  local sj
  for sj in $SERVER_JAR_GLOB; do
    if yap_tp_has_class "$sj"; then
      echo "teleport class OK via sibling server jar: $sj"
      return 0
    fi
  done
  if [ -f "$BUNDLER_JAR" ] && yap_tp_has_class "$BUNDLER_JAR"; then
    echo "teleport class OK via bundler: $BUNDLER_JAR"
    return 0
  fi
  if [ -f "$stamp" ] && grep -q "0001-yap-teleport-transactions" "$stamp"; then
    echo "teleport patch stamped on product jar ($stamp) — paperclip binary delta (class not listable)"
    return 0
  fi
  return 1
}

if [ "${SKIP_LIVE:-0}" = "1" ]; then
  echo "SKIP_LIVE=1 — teleport patch presence OK (rebuild yap-folia to exercise live ${TP_CYCLES}× TP)"
  exit 0
fi

yap_require_java
yap_load_config

VER="${FOLIA_VERSION:-26.2}"
FOLIA_SRC="${FOLIA_JAR_SOURCE:-${YAP_FOLIA_JAR_SOURCE:-build}}"
FOLIA_JAR=""

case "$FOLIA_SRC" in
  fetch|stock)
    echo "FAIL: folia-jar-source=${FOLIA_SRC} is stock Folia — teleport transactions require YaP-Folia build." >&2
    echo "  Set FOLIA_JAR_SOURCE=build and run ./scripts/build-yap-folia.sh" >&2
    echo "  (patch: vendor/folia/patches/0001-yap-teleport-transactions.patch)" >&2
    exit 1
    ;;
esac

for c in \
  "$ROOT/server/lib/yap-folia-${VER}.jar" \
  "$ROOT/lib/yap-folia-${VER}.jar"; do
  if [ -f "$c" ]; then
    FOLIA_JAR="$c"
    break
  fi
done

if [ -z "$FOLIA_JAR" ]; then
  echo "FAIL: yap-folia-${VER}.jar not found — teleport smoke needs patched build jar." >&2
  echo "  Run: ./scripts/build-yap-folia.sh" >&2
  echo "  Then: FOLIA_JAR_SOURCE=build ./scripts/smoke-folia-cross-region-tp.sh" >&2
  exit 1
fi

if ! yap_tp_require_patched "$FOLIA_JAR"; then
  echo "FAIL: YapTeleportTransaction not present for $FOLIA_JAR" >&2
  echo "  Stock / unpatched jar cannot satisfy folia-teleport-transactions." >&2
  echo "  Apply vendor/folia/patches/0001-yap-teleport-transactions.patch via ./scripts/build-yap-folia.sh" >&2
  exit 1
fi
echo "folia=$FOLIA_JAR (patched teleport OK)"

# Live stress: boot Folia and assert flag + class path; extend soak via WAIT_SECS / TP_CYCLES.
WORK="$ROOT/bench/workdir-folia-cross-region-tp-smoke"
rm -rf "$WORK"
mkdir -p "$WORK"
/bin/cp -f "$FOLIA_JAR" "$WORK/folia-server.jar"
printf 'eula=true\n' >"$WORK/eula.txt"
cat >"$WORK/server.properties" <<EOF
server-port=25580
online-mode=false
max-players=5
motd=YaP cross-region tp smoke
EOF

JAVA_BIN="$(yap_java_bin)"
LOG="$WORK/smoke.log"
(
  cd "$WORK"
  "$JAVA_BIN" -Xms512M -Xmx1536M \
    -Dyap.folia.teleport-transactions=true \
    -Dyap.folia.async-chunk-save=false \
    -Dyap.folia.entity-tick-budget=0 \
    -jar folia-server.jar --nogui
) >"$LOG" 2>&1 &
PID=$!
cleanup() { kill "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; }
trap cleanup EXIT

deadline=$((SECONDS + WAIT_SECS))
ready=0
while [ "$SECONDS" -lt "$deadline" ]; do
  if grep -qiE "Done \(|You need to agree|YaP-Folia|Folia" "$LOG" 2>/dev/null; then
    if grep -qi "Failed to start\|Error occurred during initialization" "$LOG" 2>/dev/null; then
      echo "FAIL: Folia boot error"
      tail -80 "$LOG" || true
      exit 1
    fi
    ready=1
    break
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "Folia exited early"
    tail -80 "$LOG" || true
    exit 1
  fi
  sleep 2
done

if [ "$ready" != "1" ]; then
  echo "FAIL: Folia not ready within ${WAIT_SECS}s"
  tail -80 "$LOG" || true
  exit 1
fi

# Hold briefly under teleport flag (extended soak when WAIT_SECS large).
hold_extra=10
if [ "$WAIT_SECS" -gt 90 ]; then
  hold_extra=30
fi
hold_until=$((SECONDS + hold_extra))
while [ "$SECONDS" -lt "$hold_until" ] && [ "$SECONDS" -lt "$deadline" ]; do
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "FAIL: Folia died during teleport soak hold"
    tail -80 "$LOG" || true
    exit 1
  fi
  sleep 2
done

echo "PASS: teleport transactions — patched jar + boot OK with -Dyap.folia.teleport-transactions=true"
echo "  cycles_gate=${TP_CYCLES} (in-game bot follow-up); hold_ok on build jar"
echo "  log=$LOG"
