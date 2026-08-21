#!/usr/bin/env bash
# vs-Paper MSPT scoreboard — run idle|entity|farm on stock Paper and YaPcore Phase 3.
# Usage: ./scripts/bench/run-vs-paper.sh [scenario] [seconds]
# Env: YAP_BENCH_WARMUP (default 15), YAP_BENCH_ENTITIES (default 250)
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=../lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

SCENARIO="${1:-entity}"
SECONDS_N="${2:-30}"
WARMUP="${YAP_BENCH_WARMUP:-15}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RESULTS="$ROOT/bench/results"
mkdir -p "$RESULTS"

cd "$ROOT"
export ROOT
yap_require_java
yap_load_config

JAVA_BIN="$(yap_java_bin)"
FEATURE="$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | awk -F= '/java.specification.version/ {gsub(/ /,"",$2); print $2; exit}')"
MAJOR="${FEATURE%%.*}"
if [ "${MAJOR:-0}" -lt 25 ] 2>/dev/null; then
  echo "Paper 26.2 / Phase 3 bench needs Java 25+ (have $FEATURE)" >&2
  exit 1
fi

echo "Building bench plugin + YaPcore…"
(cd "$ROOT" && gradle :bench-plugin:jar :phase3-plugin:installIntoResources shadowJar --no-daemon -q)

BENCH_JAR="$(ls -1 "$ROOT/bench-plugin/build/libs"/yap-mspt-bench.jar 2>/dev/null | head -n 1)"
if [ ! -f "$BENCH_JAR" ]; then
  echo "bench plugin jar missing" >&2
  exit 1
fi

YAP_JAR="$(yap_find_jar)"
if [ -z "$YAP_JAR" ]; then
  echo "yapcore jar missing" >&2
  exit 1
fi
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac

STOCK_PAPER="$ROOT/lib/paper-${PAPER_VERSION}.jar"
YAP_PAPER="$ROOT/lib/paper-${PAPER_VERSION}-yap.jar"
if [ ! -f "$YAP_PAPER" ]; then
  echo "Missing $YAP_PAPER — run ./scripts/build-vendor-paper.sh" >&2
  exit 1
fi
if [ ! -f "$STOCK_PAPER" ]; then
  echo "Stock Paper missing at $STOCK_PAPER" >&2
  exit 1
fi

write_spigot_yml() {
  local dest="$1"
  cat >"$dest" <<'EOF'
world-settings:
  default:
    entity-activation-range:
      animals: 48
      monsters: 48
      raiders: 48
      misc: 16
      water: 16
      villagers: 32
      flying-monsters: 48
      wake-up-inactive:
        animals-max-per-tick: 2000
        animals-every: 1
        animals-for: 100
        monsters-max-per-tick: 2000
        monsters-every: 1
        monsters-for: 100
        villagers-max-per-tick: 200
        villagers-every: 1
        villagers-for: 100
        flying-monsters-max-per-tick: 200
        flying-monsters-every: 1
        flying-monsters-for: 100
EOF
}

write_server_props() {
  local dest="$1"
  local port="$2"
  local motd="$3"
  cat >"$dest" <<EOF
motd=$motd
online-mode=false
max-players=20
server-port=$port
level-seed=yap-bench-1
view-distance=6
simulation-distance=6
spawn-monsters=false
EOF
}

prepare_workdir() {
  local work="$1"
  local paper_jar="$2"
  local port="$3"
  local motd="$4"
  rm -rf "$work"
  mkdir -p "$work/plugins"
  /bin/cp -f "$paper_jar" "$work/paper.jar"
  /bin/cp -f "$BENCH_JAR" "$work/plugins/yap-mspt-bench.jar"
  printf 'eula=true\n' >"$work/eula.txt"
  write_server_props "$work/server.properties" "$port" "$motd"
  write_spigot_yml "$work/spigot.yml"
}

run_stock() {
  local out="$RESULTS/${STAMP}-${SCENARIO}-stock.json"
  local work="$ROOT/bench/workdir-stock"
  prepare_workdir "$work" "$STOCK_PAPER" 25570 "YaP MSPT bench stock"
  echo "=== STOCK Paper scenario=$SCENARIO → $out ==="
  (
    cd "$work"
    "$JAVA_BIN" -Xms1G -Xmx2G \
      -Dyap.bench.scenario="$SCENARIO" \
      -Dyap.bench.seconds="$SECONDS_N" \
      -Dyap.bench.warmup="$WARMUP" \
      -Dyap.bench.label=stock-paper \
      -Dyap.bench.out="$out" \
      -Dyapcore.home="$ROOT" \
      -jar paper.jar --nogui
  ) || true
  if [ ! -f "$out" ]; then
    echo "WARN: stock run did not write $out" >&2
  fi
}

run_yap() {
  local out="$RESULTS/${STAMP}-${SCENARIO}-yapcore.json"
  # Fresh isolated paper-dir — same seed/view as stock (not bloated paper-kernel)
  local work="$ROOT/bench/workdir-yap"
  prepare_workdir "$work" "$YAP_PAPER" 25571 "YaP MSPT bench yapcore"
  /bin/cp -f "$YAP_PAPER" "$work/paper-${PAPER_VERSION}.jar"
  if [ -f "$ROOT/src/main/resources/phase3/yap-spatial-tick.jar" ]; then
    /bin/cp -f "$ROOT/src/main/resources/phase3/yap-spatial-tick.jar" "$work/plugins/" || true
  fi
  echo "=== YAPCORE Phase3 scenario=$SCENARIO → $out (paper-dir=bench/workdir-yap) ==="
  (
    cd "$work"
    "$JAVA_BIN" -Xms1G -Xmx2G \
      -Dyapcore.home="$ROOT" \
      -Dyapcore.paper.dir=bench/workdir-yap \
      -Dyapcore.phase3.spatial-tick=true \
      -Dyapcore.phase3.spatial-blockfluid=true \
      -Dyapcore.phase3.spatial-random=true \
      -Dyapcore.phase3.spatial-blockentities=true \
      -Dyapcore.phase3.spatial-redstone=true \
      -Dyap.bench.scenario="$SCENARIO" \
      -Dyap.bench.seconds="$SECONDS_N" \
      -Dyap.bench.warmup="$WARMUP" \
      -Dyap.bench.label=yapcore-phase3 \
      -Dyap.bench.out="$out" \
      -jar "$YAP_JAR" --nogui
  ) || true
  if [ ! -f "$out" ]; then
    echo "WARN: yapcore run did not write $out" >&2
  fi
}

run_stock
run_yap

echo
echo "Results under $RESULTS"
ls -1 "$RESULTS"/${STAMP}-${SCENARIO}-*.json 2>/dev/null || true
if [ -f "$RESULTS/${STAMP}-${SCENARIO}-stock.json" ] && [ -f "$RESULTS/${STAMP}-${SCENARIO}-yapcore.json" ]; then
  python3 "$SCRIPT_DIR/compare-results.py" \
    "$RESULTS/${STAMP}-${SCENARIO}-stock.json" \
    "$RESULTS/${STAMP}-${SCENARIO}-yapcore.json" || true
fi
