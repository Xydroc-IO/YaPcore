#!/usr/bin/env bash
# Ecosystem MSPT scoreboard — Paper + Purpur + Leaf + YaPcore (same load / fairness).
# Usage: ./scripts/bench/run-vs-ecosystem.sh [scenario] [seconds]
# Default scenario: heavypop
# Env: YAP_BENCH_WARMUP, YAP_BENCH_ENTITIES, YAP_BENCH_HOPPERS,
#      YAP_BENCH_COMPETITORS=paper,purpur,leaf,yapcore (comma list; default all)
#      YAP_BENCH_JFR=1 → write bench/profiles/<stamp>-heavypop-<id>.jfr (Leaf gap profiling)
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=../lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

SCENARIO="${1:-heavypop}"
SECONDS_N="${2:-40}"
WARMUP="${YAP_BENCH_WARMUP:-15}"
ENTITIES="${YAP_BENCH_ENTITIES:-}"
HOPPERS="${YAP_BENCH_HOPPERS:-}"
COMPETITORS_CSV="${YAP_BENCH_COMPETITORS:-paper,purpur,leaf,yapcore}"
STAMP="${YAP_BENCH_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
RESULTS="$ROOT/bench/results"
PROFILES="$ROOT/bench/profiles"
mkdir -p "$RESULTS" "$ROOT/logs/bench" "$PROFILES"

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

echo "Ensuring competitor jars…"
"$SCRIPT_DIR/fetch-competitors.sh" "$PAPER_VERSION"

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

YAP_PAPER="$ROOT/lib/paper-${PAPER_VERSION}-yap.jar"
if [[ "$COMPETITORS_CSV" == *yapcore* ]] && [ ! -f "$YAP_PAPER" ]; then
  echo "Missing $YAP_PAPER — run ./scripts/build-vendor-paper.sh" >&2
  exit 1
fi

write_spigot_yml() {
  local dest="$1"
  # Same fairness caps as run-vs-paper.sh (unlimited TNT + no 50ms entity abort).
  cat >"$dest" <<'EOF'
world-settings:
  default:
    max-tnt-per-tick: 0
    max-tick-time:
      tile: 0
      entity: 0
    entity-activation-range:
      animals: 48
      monsters: 48
      raiders: 48
      # 0 = always active — needed for dense primed-TNT fuse proofs with no players.
      misc: 0
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
view-distance=10
simulation-distance=12
spawn-monsters=false
EOF
}

prepare_workdir() {
  local work="$1"
  local server_jar="$2"
  local port="$3"
  local motd="$4"
  rm -rf "$work"
  mkdir -p "$work/plugins"
  /bin/cp -f "$server_jar" "$work/paper.jar"
  /bin/cp -f "$BENCH_JAR" "$work/plugins/yap-mspt-bench.jar"
  printf 'eula=true\n' >"$work/eula.txt"
  write_server_props "$work/server.properties" "$port" "$motd"
  write_spigot_yml "$work/spigot.yml"
}

run_plain_jar() {
  # Stock Paper / Purpur / Leaf — plain -jar, same JVM heap + bench props
  local id="$1"
  local jar="$2"
  local port="$3"
  local label="$4"
  local out="$RESULTS/${STAMP}-${SCENARIO}-${id}.json"
  local work="$ROOT/bench/workdir-${id}"
  if [ ! -f "$jar" ]; then
    echo "WARN: missing $jar — skip $id" >&2
    return
  fi
  prepare_workdir "$work" "$jar" "$port" "YaP MSPT bench $id"
  echo "=== $id scenario=$SCENARIO → $out ==="
  local jfr_args=()
  if [[ "${YAP_BENCH_JFR:-}" == "1" ]]; then
    local jfr="$PROFILES/${STAMP}-${SCENARIO}-${id}.jfr"
    jfr_args=(-XX:StartFlightRecording="filename=${jfr},settings=profile,maxsize=256m,dumponexit=true")
    echo "JFR → $jfr"
  fi
  (
    cd "$work"
    local extra=()
    [[ -n "$ENTITIES" ]] && extra+=(-Dyap.bench.entities="$ENTITIES")
    [[ -n "$HOPPERS" ]] && extra+=(-Dyap.bench.hoppers="$HOPPERS")
    "$JAVA_BIN" -Xms2G -Xmx4G \
      "${jfr_args[@]}" \
      -Dyap.bench.scenario="$SCENARIO" \
      -Dyap.bench.seconds="$SECONDS_N" \
      -Dyap.bench.warmup="$WARMUP" \
      -Dyap.bench.label="$label" \
      -Dyap.bench.out="$out" \
      -Dyapcore.home="$ROOT" \
      "${extra[@]}" \
      -jar paper.jar --nogui </dev/null
  ) || true
  if [ ! -f "$out" ]; then
    echo "WARN: $id run did not write $out" >&2
  fi
}

run_yap() {
  local out="$RESULTS/${STAMP}-${SCENARIO}-yapcore.json"
  local work="$ROOT/bench/workdir-yap"
  # YaP PaperFiles preserves bench props when -Dyap.bench.scenario is set.
  # Use 25571 so we never collide with a leftover product listen on 25566.
  prepare_workdir "$work" "$YAP_PAPER" 25571 "YaP MSPT bench yapcore"
  /bin/cp -f "$YAP_PAPER" "$work/paper-${PAPER_VERSION}.jar"
  if [ -f "$ROOT/src/main/resources/phase3/yap-spatial-tick.jar" ]; then
    /bin/cp -f "$ROOT/src/main/resources/phase3/yap-spatial-tick.jar" "$work/plugins/" || true
  fi
  write_server_props "$work/server.properties" 25571 "YaP MSPT bench yapcore"
  echo "=== yapcore scenario=$SCENARIO → $out ==="
  local jfr_args=()
  if [[ "${YAP_BENCH_JFR:-}" == "1" ]]; then
    local jfr="$PROFILES/${STAMP}-${SCENARIO}-yapcore.jfr"
    jfr_args=(-XX:StartFlightRecording="filename=${jfr},settings=profile,maxsize=256m,dumponexit=true")
    echo "JFR → $jfr"
  fi
  (
    cd "$work"
    local extra=()
    [[ -n "$ENTITIES" ]] && extra+=(-Dyap.bench.entities="$ENTITIES")
    [[ -n "$HOPPERS" ]] && extra+=(-Dyap.bench.hoppers="$HOPPERS")
    "$JAVA_BIN" -Xms2G -Xmx4G \
      "${jfr_args[@]}" \
      -Dyapcore.home="$ROOT" \
      -Dyapcore.paper.dir=bench/workdir-yap \
      -Dyapcore.phase3.spatial-tick=true \
      -Dyapcore.phase3.spatial-blockfluid=true \
      -Dyapcore.phase3.spatial-random=true \
      -Dyapcore.phase3.spatial-blockentities=true \
      -Dyapcore.phase3.spatial-redstone=true \
      -Dyapcore.phase3.spatial-borders=true \
      -Dyapcore.phase3.spatial-tracker="${YAP_BENCH_TRACKER:-true}" \
      -Dyapcore.phase3.spatial-distant-brain="${YAP_BENCH_DISTANT_BRAIN:-true}" \
      -Dyap.bench.scenario="$SCENARIO" \
      -Dyap.bench.seconds="$SECONDS_N" \
      -Dyap.bench.warmup="$WARMUP" \
      -Dyap.bench.label=yapcore-phase3 \
      -Dyap.bench.out="$out" \
      "${extra[@]}" \
      -jar "$YAP_JAR" --nogui </dev/null
  ) || true
  if [ ! -f "$out" ]; then
    echo "WARN: yapcore run did not write $out" >&2
  fi
}

wait_ports_free() {
  # Avoid Address-already-in-use between sequential competitors (Paperclip shutdown lag).
  local ports=(25566 25570 25571 25572 25573)
  local i
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
    local busy=0
    local p
    for p in "${ports[@]}"; do
      if ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE ":${p}$"; then
        busy=1
        # Force-kill leftover bench JVMs holding the port (zombie Phase 3 is common).
        local pid
        pid="$(ss -ltnp 2>/dev/null | awk -v p=":$p" '$4 ~ p {print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)"
        if [[ -n "$pid" ]]; then
          local cmd
          cmd="$(ps -p "$pid" -o args= 2>/dev/null || true)"
          if [[ "$cmd" == *yap.bench* ]] || [[ "$cmd" == *bench/workdir* ]] || [[ "$cmd" == *yapcore* ]]; then
            echo "Killing leftover bench JVM pid=$pid on port $p" >&2
            kill -9 "$pid" 2>/dev/null || true
          fi
        fi
        break
      fi
    done
    [[ "$busy" -eq 0 ]] && return 0
    sleep 2
  done
  echo "WARN: bench ports still busy after wait — continuing anyway" >&2
}

IFS=',' read -r -a COMPETITORS <<<"$COMPETITORS_CSV"
echo "Competitors: ${COMPETITORS[*]} (stamp=$STAMP scenario=$SCENARIO)"

for c in "${COMPETITORS[@]}"; do
  c="$(echo "$c" | tr -d '[:space:]')"
  wait_ports_free
  case "$c" in
    paper|stock)
      run_plain_jar paper "$ROOT/lib/paper-${PAPER_VERSION}.jar" 25570 stock-paper
      ;;
    purpur)
      run_plain_jar purpur "$ROOT/lib/purpur-${PAPER_VERSION}.jar" 25572 purpur
      ;;
    leaf)
      run_plain_jar leaf "$ROOT/lib/leaf-${PAPER_VERSION}.jar" 25573 leaf
      ;;
    yapcore|yap)
      run_yap
      ;;
    *)
      echo "Unknown competitor: $c (use paper,purpur,leaf,yapcore)" >&2
      exit 2
      ;;
  esac
done

echo
echo "Results under $RESULTS"
ls -1 "$RESULTS"/${STAMP}-${SCENARIO}-*.json 2>/dev/null || true
python3 "$SCRIPT_DIR/compare-ecosystem.py" "$RESULTS" "$STAMP" "$SCENARIO" || true
