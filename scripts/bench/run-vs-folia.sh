#!/usr/bin/env bash
# vs-Folia MSPT scoreboard — stock Folia + Canvas (± forks) vs YaP Folia chassis (M5).
# Usage: ./scripts/bench/run-vs-folia.sh [scenario] [seconds]
# Scenarios: heavypop (default, no bots) | spawncollapse (single-region overload) |
#            highpop | fullcite (bots + fixtures + load)
# Env: YAP_BENCH_WARMUP, YAP_BENCH_ENTITIES, YAP_BENCH_HOPPERS, YAP_BENCH_HEAVY_HOPPERS,
#      YAP_BENCH_MOBS (spawncollapse mob count),
#      YAP_BENCH_PLAYERS (default 100 for highpop/fullcite),
#      YAP_BENCH_COMPETITORS=folia,canvas,yapcore (comma list; default all)
#      YAP_BENCH_GAME_XMS=2G YAP_BENCH_GAME_XMX=4G  (8G/12G default for bot scenarios)
#      YAP_BENCH_CHASSIS_XMS=256m YAP_BENCH_CHASSIS_XMX=512m  (YaP parent only)
#      YAP_BENCH_SHUFFLE=1  (randomize competitor order — default on)
#      YAP_BENCH_COOLDOWN=5   (seconds between runs; default 5, 30 for bots)
#      YAP_BOT_CITE_STABLE=0  (force active physics MSPT cite at ≥150 bots)
# No Phase 3 spatial tick. No yap-spatial-tick.jar.
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
HEAVY_HOPPERS="${YAP_BENCH_HEAVY_HOPPERS:-}"
MOBS="${YAP_BENCH_MOBS:-}"
COMPETITORS_CSV="${YAP_BENCH_COMPETITORS:-folia,canvas,yapfolia,yapcore}"
SHUFFLE="${YAP_BENCH_SHUFFLE:-1}"
STAMP="${YAP_BENCH_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
RESULTS="$ROOT/bench/results"
BOTS_DIR="$SCRIPT_DIR/bots"
START_BOTS_PID=""
mkdir -p "$RESULTS" "$ROOT/logs/bench"

NEEDS_BOTS=0
case "$SCENARIO" in
  highpop|fullcite) NEEDS_BOTS=1 ;;
esac

PLAYERS="${YAP_BENCH_PLAYERS:-$([ "$NEEDS_BOTS" = "1" ] && echo 100 || echo 0)}"
JOIN_TIMEOUT="${YAP_BENCH_JOIN_TIMEOUT:-$((180 + PLAYERS))}"
if [ -n "${YAP_BOT_CITE_STABLE:-}" ]; then
  if [ "$YAP_BOT_CITE_STABLE" = "0" ] || [ "$YAP_BOT_CITE_STABLE" = "false" ]; then
    BOT_LOAD=active
  else
    BOT_LOAD=cite-stable
  fi
elif [ "$PLAYERS" -ge 150 ]; then
  BOT_LOAD=cite-stable
else
  BOT_LOAD=active
fi

if [ "$SCENARIO" = "fullcite" ]; then
  ENTITIES="${ENTITIES:-600}"
  HEAVY_HOPPERS="${HEAVY_HOPPERS:-128}"
fi

if [ "$SCENARIO" = "spawncollapse" ]; then
  # Single-region overload defaults — tune until mspt_mean ≥ ~2 for citeable rows.
  ENTITIES="${ENTITIES:-800}"
  HOPPERS="${HOPPERS:-256}"
  MOBS="${MOBS:-200}"
fi

if [ "$NEEDS_BOTS" = "1" ]; then
  GAME_XMS="${YAP_BENCH_GAME_XMS:-8G}"
  GAME_XMX="${YAP_BENCH_GAME_XMX:-12G}"
  COOLDOWN="${YAP_BENCH_COOLDOWN:-30}"
  WARMUP="${YAP_BENCH_WARMUP:-25}"
else
  GAME_XMS="${YAP_BENCH_GAME_XMS:-2G}"
  GAME_XMX="${YAP_BENCH_GAME_XMX:-4G}"
  COOLDOWN="${YAP_BENCH_COOLDOWN:-5}"
fi
CHASSIS_XMS="${YAP_BENCH_CHASSIS_XMS:-256m}"
CHASSIS_XMX="${YAP_BENCH_CHASSIS_XMX:-512m}"

cd "$ROOT"
export ROOT
yap_require_java
yap_load_config

JAVA_BIN="$(yap_java_bin)"
FEATURE="$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | awk -F= '/java.specification.version/ {gsub(/ /,"",$2); print $2; exit}')"
MAJOR="${FEATURE%%.*}"
if [ "${MAJOR:-0}" -lt 25 ] 2>/dev/null; then
  echo "Folia 26.2 bench needs Java 25+ (have $FEATURE)" >&2
  exit 1
fi

if [ "$NEEDS_BOTS" = "1" ]; then
  LOCK="$ROOT/bench/fullcite.lock"
  exec 9>"$LOCK"
  if ! flock -n 9; then
    echo "Another bot bench holds $LOCK — aborting" >&2
    exit 4
  fi
fi

VER="${FOLIA_VERSION:-26.2}"
echo "Ensuring Folia-ecosystem jars (${VER})…"
"$SCRIPT_DIR/fetch-folia-forks.sh" "$VER"

echo "Building bench plugin + parity jars (no phase3 spatial)…"
if [ "$NEEDS_BOTS" = "1" ]; then
  (cd "$ROOT" && gradle \
    :bench-plugin:jar :bench-plugin:popSimJar \
    :placeholderapi-plugin:jar \
    :gameplay-knobs-plugin:jar \
    :vehicles-plugin:jar \
    --no-daemon -q)
  if [ ! -d "$BOTS_DIR/node_modules/mineflayer" ]; then
    echo "Installing Mineflayer bots…"
    (cd "$BOTS_DIR" && npm install --no-fund --no-audit)
  fi
else
  (cd "$ROOT" && gradle :bench-plugin:jar --no-daemon -q)
fi
if [ ! -f "$(yap_find_jar 2>/dev/null || true)" ] && [ ! -f "$ROOT/yapcore.jar" ]; then
  echo "Building YaPcore jar…"
  (cd "$ROOT" && gradle shadowJar --no-daemon -q)
fi

BENCH_JAR="$ROOT/yap-first-party/dev/bench-plugin/build/libs/yap-mspt-bench.jar"
POP_JAR="$ROOT/yap-first-party/dev/bench-plugin/build/libs/yap-pop-sim.jar"
PAPI_JAR="$ROOT/yap-first-party/core-network/placeholderapi-plugin/build/libs/yap-placeholderapi.jar"
KNOBS_JAR="$ROOT/yap-first-party/gameplay/gameplay-knobs-plugin/build/libs/yap-gameplay-knobs.jar"
VEHICLES_JAR="$ROOT/yap-first-party/gameplay/vehicles-plugin/build/libs/yap-vehicles.jar"
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

STOCK_FOLIA="$ROOT/lib/folia-${VER}.jar"
STOCK_CANVAS="$ROOT/lib/canvas-${VER}.jar"
YAP_FOLIA="$ROOT/lib/yap-folia-${VER}.jar"
# Phase 3 knobs (YaP-Folia only) — stock Folia ignores unknown -D props
ENTITY_TICK_BUDGET="${YAP_FOLIA_ENTITY_TICK_BUDGET:-}"
ASYNC_CHUNK_SAVE="${YAP_FOLIA_ASYNC_CHUNK_SAVE:-}"
if [ "$SCENARIO" = "spawncollapse" ] && [ -z "$ENTITY_TICK_BUDGET" ]; then
  ENTITY_TICK_BUDGET="${YAP_FOLIA_ENTITY_TICK_BUDGET:-400}"
fi
if [ -z "$ASYNC_CHUNK_SAVE" ]; then
  ASYNC_CHUNK_SAVE="${YAP_FOLIA_ASYNC_CHUNK_SAVE:-true}"
fi

write_server_props() {
  local dest="$1"
  local port="$2"
  local motd="$3"
  local maxp=20
  if [ "$NEEDS_BOTS" = "1" ]; then
    maxp=$((PLAYERS + 50))
    if [ "$maxp" -lt 100 ]; then maxp=100; fi
  fi
  cat >"$dest" <<EOF
motd=$motd
online-mode=false
max-players=$maxp
server-port=$port
level-seed=yap-bench-1
view-distance=10
simulation-distance=12
spawn-monsters=false
player-idle-timeout=0
network-compression-threshold=-1
allow-flight=true
EOF
}

write_spigot_yml() {
  local dest="$1"
  if [ "$NEEDS_BOTS" = "1" ]; then
    cat >"$dest" <<'EOF'
settings:
  timeout-time: 180
  restart-on-crash: false
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
      misc: 16
      water: 16
      villagers: 48
      flying-monsters: 48
      wake-up-inactive:
        animals-max-per-tick: 4
        animals-every: 1200
        animals-for: 100
        monsters-max-per-tick: 8
        monsters-every: 400
        monsters-for: 100
        villagers-max-per-tick: 4
        villagers-every: 600
        villagers-for: 100
        flying-monsters-max-per-tick: 8
        flying-monsters-every: 200
        flying-monsters-for: 100
EOF
  else
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
      misc: 0
      water: 16
      villagers: 32
      flying-monsters: 48
EOF
  fi
}

install_parity_plugins() {
  local work="$1"
  /bin/cp -f "$BENCH_JAR" "$work/plugins/yap-mspt-bench.jar"
  if [ "$NEEDS_BOTS" = "1" ]; then
    for jar in "$POP_JAR" "$PAPI_JAR" "$KNOBS_JAR" "$VEHICLES_JAR"; do
      if [ -f "$jar" ]; then
        /bin/cp -f "$jar" "$work/plugins/$(basename "$jar")"
      else
        echo "WARN: missing parity plugin $jar" >&2
      fi
    done
  fi
}

bench_jvm_extra() {
  local port="$1"
  local yap_knobs="${2:-0}"
  local extra=()
  [[ -n "$ENTITIES" ]] && extra+=(-Dyap.bench.entities="$ENTITIES")
  [[ -n "$HOPPERS" ]] && extra+=(-Dyap.bench.hoppers="$HOPPERS")
    [[ -n "$HEAVY_HOPPERS" ]] && extra+=(-Dyap.bench.heavy_hoppers="$HEAVY_HOPPERS")
    [[ -n "$MOBS" ]] && extra+=(-Dyap.bench.mobs="$MOBS")
    extra+=(-Dyap.bench.root="$ROOT")
  if [ "$NEEDS_BOTS" = "1" ]; then
    extra+=(
      -Dyap.bench.players="$PLAYERS"
      -Dyap.bench.bot_load="$BOT_LOAD"
      -Dyap.bench.join_timeout="$JOIN_TIMEOUT"
      -Dyap.bench.bot_port="$port"
    )
  fi
  if [ "$yap_knobs" = "1" ]; then
    [[ -n "$ENTITY_TICK_BUDGET" ]] && extra+=(-Dyap.folia.entity-tick-budget="$ENTITY_TICK_BUDGET")
    [[ -n "$ASYNC_CHUNK_SAVE" ]] && extra+=(-Dyap.folia.async-chunk-save="$ASYNC_CHUNK_SAVE")
  fi
  printf '%s\0' "${extra[@]}"
}

start_bots() {
  local port="$1"
  local log="$2"
  rm -f "$ROOT/bench/highpop-ready.port"
  : >"$log"
  (
    local n=0 ready=0
    while [[ $n -lt 1800 ]]; do
      if [[ -f "$ROOT/bench/highpop-ready.port" ]]; then
        local marked
        marked="$(head -n1 "$ROOT/bench/highpop-ready.port" 2>/dev/null || true)"
        if [[ "$marked" == "$port" ]]; then
          echo "ready marker ok port=$port after ${n}s — launching bots" >>"$log"
          ready=1
          break
        fi
      fi
      sleep 2
      n=$((n + 2))
    done
    if [[ "$ready" -ne 1 ]]; then
      echo "ERROR: no matching ready marker after ${n}s — not launching bots" >>"$log"
      exit 3
    fi
    sleep 5
    export YAP_BOT_HOST=127.0.0.1
    export YAP_BOT_PORT="$port"
    export YAP_BOT_STAGGER_MS="${YAP_BOT_STAGGER_MS:-$((100000 / PLAYERS))}"
    if [ "${YAP_BOT_STAGGER_MS}" -lt 150 ]; then YAP_BOT_STAGGER_MS=150; fi
    if [ "${YAP_BOT_STAGGER_MS}" -gt 500 ]; then YAP_BOT_STAGGER_MS=500; fi
    export YAP_BOT_VERSION="${YAP_BOT_VERSION:-26.2}"
    export YAP_BOT_TOTAL="$PLAYERS"
    export YAP_BOT_CITE_STABLE="${YAP_BOT_CITE_STABLE:-}"
    export NODE_OPTIONS="${NODE_OPTIONS:---max-old-space-size=4096}"
    local workers="${YAP_BOT_WORKERS:-1}"
    if [ -z "${YAP_BOT_WORKERS:-}" ] && [ "$PLAYERS" -ge 150 ]; then workers=2; fi
    if [ "$BOT_LOAD" = "active" ] && [ "$PLAYERS" -ge 150 ] && [ -z "${YAP_BOT_WORKERS:-}" ]; then
      workers=4
    fi
    echo "bot stagger=${YAP_BOT_STAGGER_MS}ms count=$PLAYERS workers=$workers version=$YAP_BOT_VERSION" >>"$log"
    cd "$BOTS_DIR"
    local pids=() base=0 w per n
    per=$(( (PLAYERS + workers - 1) / workers ))
    for ((w = 0; w < workers; w++)); do
      n=$per
      if [ $((base + n)) -gt "$PLAYERS" ]; then n=$((PLAYERS - base)); fi
      if [ "$n" -le 0 ]; then break; fi
      (
        export YAP_BOT_COUNT="$n"
        export YAP_BOT_INDEX_BASE="$base"
        exec node swarm.js
      ) >>"$log" 2>&1 &
      pids+=("$!")
      base=$((base + n))
    done
    local fail=0 pid
    for pid in "${pids[@]}"; do wait "$pid" || fail=1; done
    exit "$fail"
  ) &
  START_BOTS_PID=$!
}

stop_bots() {
  local pid="${1:-}"
  pkill -INT -f 'scripts/bench/bots/swarm.js' 2>/dev/null || true
  sleep 1
  pkill -9 -f 'scripts/bench/bots/swarm.js' 2>/dev/null || true
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    kill -INT "$pid" 2>/dev/null || true
    kill -9 "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  fi
  pkill -f 'yapbot_' 2>/dev/null || true
}

wait_ports_free() {
  local ports=(25680 25681 25682)
  local i p
  for i in $(seq 1 30); do
    local busy=0
    for p in "${ports[@]}"; do
      if ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE ":${p}$"; then
        busy=1
        local pid
        pid="$(ss -ltnp 2>/dev/null | awk -v p=":$p" '$4 ~ p {print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)"
        if [[ -n "$pid" ]]; then
          local cmd; cmd="$(ps -p "$pid" -o args= 2>/dev/null || true)"
          if [[ "$cmd" == *bench/workdir* ]] || [[ "$cmd" == *workdir-yap-folia* ]]; then
            echo "Killing leftover bench JVM pid=$pid on port $p" >&2
            kill -9 "$pid" 2>/dev/null || true
          fi
        fi
      fi
    done
    [[ "$busy" -eq 0 ]] && return 0
    sleep 2
  done
  echo "WARN: bench ports still busy after wait" >&2
}

prepare_plain() {
  local work="$1"
  local server_jar="$2"
  local port="$3"
  local motd="$4"
  rm -rf "$work"
  mkdir -p "$work/plugins"
  /bin/cp -f "$server_jar" "$work/server.jar"
  install_parity_plugins "$work"
  printf 'eula=true\n' >"$work/eula.txt"
  write_server_props "$work/server.properties" "$port" "$motd"
  write_spigot_yml "$work/spigot.yml"
}

run_plain() {
  local id="$1"
  local jar="$2"
  local port="$3"
  local label="$4"
  local out="$RESULTS/${STAMP}-${SCENARIO}-${id}.json"
  local work="$ROOT/bench/workdir-folia-${id}"
  local botlog="$ROOT/logs/bench/bots-${STAMP}-${id}.log"
  if [ ! -f "$jar" ]; then
    echo "WARN: missing $jar — skip $id" >&2
    return
  fi
  prepare_plain "$work" "$jar" "$port" "YaP MSPT bench $id"
  echo "=== $id scenario=$SCENARIO → $out ==="
  local botpid=""
  if [ "$NEEDS_BOTS" = "1" ]; then
    start_bots "$port" "$botlog"
    botpid="$START_BOTS_PID"
  fi
  mapfile -d '' -t extra < <(bench_jvm_extra "$port" 0)
  (
    cd "$work"
    "$JAVA_BIN" -Xms"$GAME_XMS" -Xmx"$GAME_XMX" \
      -Dyap.bench.scenario="$SCENARIO" \
      -Dyap.bench.seconds="$SECONDS_N" \
      -Dyap.bench.warmup="$WARMUP" \
      -Dyap.bench.label="$label" \
      -Dyap.bench.out="$out" \
      -Dyap.bench.game_xms="$GAME_XMS" \
      -Dyap.bench.game_xmx="$GAME_XMX" \
      -Dyap.bench.measurement_scope=game_tick_mspt \
      -Dyap.bench.root="$ROOT" \
      -Dyapcore.home="$ROOT" \
      "${extra[@]}" \
      -jar server.jar --nogui </dev/null
  ) || true
  if [ "$NEEDS_BOTS" = "1" ]; then stop_bots "$botpid"; fi
  if [ ! -f "$out" ]; then
    echo "WARN: $id run did not write $out" >&2
  fi
}

run_yap() {
  local out="$RESULTS/${STAMP}-${SCENARIO}-yapcore.json"
  local work="$ROOT/bench/workdir-yap-folia"
  local botlog="$ROOT/logs/bench/bots-${STAMP}-yapcore.log"
  if [ ! -f "$YAP_FOLIA" ]; then
    echo "WARN: missing $YAP_FOLIA — skip yapcore (run ./scripts/build-yap-folia.sh)" >&2
    return
  fi
  rm -rf "$work"
  mkdir -p "$work/config" "$work/lib" "$work/plugins" "$work/logs"
  /bin/cp -f "$YAP_FOLIA" "$work/lib/yap-folia-${VER}.jar"
  /bin/cp -f "$YAP_FOLIA" "$work/lib/folia-${VER}.jar"
  install_parity_plugins "$work"
  rm -f "$work/plugins/yap-spatial-tick.jar"

  local port=25681
  local maxp=20
  if [ "$NEEDS_BOTS" = "1" ]; then
    maxp=$((PLAYERS + 50))
    if [ "$maxp" -lt 100 ]; then maxp=100; fi
  fi
  cat >"$work/config/server.properties" <<EOF
server-name=YaP-Folia-Bench
bind-host=127.0.0.1
port=${port}
max-players=${maxp}
ram-mb=12288
gui-enabled=false
online-mode=false
java-enabled=true
bedrock-enabled=false
crossplay-enabled=false
protocol-via-enabled=false
protocol-geyser-enabled=false
game-authority=folia
folia-embed=true
folia-dir=folia-kernel
folia-port=${port}
folia-version=${VER}
folia-jar-source=build
folia-ready-timeout-sec=180
velocity-enabled=false
web-dashboard-enabled=false
resource-pack-enabled=false
yap-ranks-auto-apply=false
plugin-compat-enabled=false
EOF

  mkdir -p "$work/folia-kernel"
  write_spigot_yml "$work/folia-kernel/spigot.yml"
  write_server_props "$work/folia-kernel/server.properties" "$port" "YaP MSPT bench yap-folia"
  rm -rf "$work/folia-kernel/world" "$work/folia-kernel/world_nether" "$work/folia-kernel/world_the_end"

  echo "=== yapcore (YaP Folia chassis) scenario=$SCENARIO → $out ==="
  echo "    knobs: entity-tick-budget=${ENTITY_TICK_BUDGET:-off} async-chunk-save=${ASYNC_CHUNK_SAVE:-off}"
  local botpid=""
  if [ "$NEEDS_BOTS" = "1" ]; then
    start_bots "$port" "$botlog"
    botpid="$START_BOTS_PID"
  fi
  mapfile -d '' -t extra < <(bench_jvm_extra "$port" 1)
  (
    cd "$work"
    "$JAVA_BIN" -Xms"$CHASSIS_XMS" -Xmx"$CHASSIS_XMX" \
      -Dyap.bench.root="$ROOT" \
      -Dyapcore.home="$ROOT" \
      -Dyap.bench.scenario="$SCENARIO" \
      -Dyap.bench.seconds="$SECONDS_N" \
      -Dyap.bench.warmup="$WARMUP" \
      -Dyap.bench.label=yap-folia-chassis \
      -Dyap.bench.out="$out" \
      -Dyap.bench.game_xms="$GAME_XMS" \
      -Dyap.bench.game_xmx="$GAME_XMX" \
      -Dyap.bench.chassis_present=true \
      -Dyap.bench.measurement_scope=game_tick_mspt \
      "${extra[@]}" \
      -jar "$YAP_JAR" --nogui </dev/null
  ) || true
  if [ "$NEEDS_BOTS" = "1" ]; then stop_bots "$botpid"; fi
  if [ ! -f "$out" ]; then
    echo "WARN: yap Folia run did not write $out" >&2
  fi
}

run_yapfolia_plain() {
  # YaP-Folia paperclip without chassis — fair game-tick MSPT vs stock Folia.
  if [ ! -f "$YAP_FOLIA" ]; then
    echo "WARN: missing $YAP_FOLIA — skip yapfolia" >&2
    return
  fi
  local out="$RESULTS/${STAMP}-${SCENARIO}-yapfolia.json"
  local work="$ROOT/bench/workdir-folia-yapfolia"
  local port=25683
  prepare_plain "$work" "$YAP_FOLIA" "$port" "YaP MSPT bench yap-folia-plain"
  echo "=== yapfolia (plain) scenario=$SCENARIO → $out ==="
  echo "    knobs: entity-tick-budget=${ENTITY_TICK_BUDGET:-off} async-chunk-save=${ASYNC_CHUNK_SAVE:-off}"
  mapfile -d '' -t extra < <(bench_jvm_extra "$port" 1)
  (
    cd "$work"
    "$JAVA_BIN" -Xms"$GAME_XMS" -Xmx"$GAME_XMX" \
      -Dyap.bench.scenario="$SCENARIO" \
      -Dyap.bench.seconds="$SECONDS_N" \
      -Dyap.bench.warmup="$WARMUP" \
      -Dyap.bench.label=yap-folia-plain \
      -Dyap.bench.out="$out" \
      -Dyap.bench.game_xms="$GAME_XMS" \
      -Dyap.bench.game_xmx="$GAME_XMX" \
      -Dyap.bench.measurement_scope=game_tick_mspt \
      -Dyap.bench.root="$ROOT" \
      -Dyapcore.home="$ROOT" \
      "${extra[@]}" \
      -jar server.jar --nogui </dev/null
  ) || true
  if [ ! -f "$out" ]; then
    echo "WARN: yapfolia run did not write $out" >&2
  fi
}

IFS=',' read -r -a COMPETITORS <<<"$COMPETITORS_CSV"
if [ "$SHUFFLE" = "1" ]; then
  mapfile -t COMPETITORS < <(printf '%s\n' "${COMPETITORS[@]}" | shuf)
fi
echo "Competitors: ${COMPETITORS[*]}  stamp=$STAMP  scenario=$SCENARIO  sample=${SECONDS_N}s"
if [ "$NEEDS_BOTS" = "1" ]; then
  echo "Bots: players=$PLAYERS bot_load=$BOT_LOAD join_timeout=${JOIN_TIMEOUT}s entities=${ENTITIES:-default} heavy_hoppers=${HEAVY_HOPPERS:-default}"
fi
echo "Game JVM: -Xms${GAME_XMS} -Xmx${GAME_XMX}  YaP chassis: -Xms${CHASSIS_XMS} -Xmx${CHASSIS_XMX}"
echo "measurement_scope=game_tick_mspt (Folia MSPT only; chassis overhead not in MSPT)"

wait_ports_free

first=1
for id in "${COMPETITORS[@]}"; do
  if [ "$first" = "1" ]; then
    first=0
  else
    if [ "$NEEDS_BOTS" = "1" ]; then stop_bots ""; fi
    wait_ports_free
    if [ "${COOLDOWN:-0}" -gt 0 ] 2>/dev/null; then sleep "$COOLDOWN"; fi
  fi
  id="$(echo "$id" | tr -d '[:space:]')"
  case "$id" in
    folia)     run_plain folia "$STOCK_FOLIA" 25680 stock-folia ;;
    canvas)    run_plain canvas "$STOCK_CANVAS" 25682 stock-canvas ;;
    yapfolia)  run_yapfolia_plain ;;
    yapcore)   run_yap ;;
    *) echo "WARN: unknown competitor '$id' (want folia|canvas|yapfolia|yapcore)" >&2 ;;
  esac
done

echo
echo "Results under $RESULTS"
ls -1 "$RESULTS"/${STAMP}-${SCENARIO}-*.json 2>/dev/null || true

if [ -f "$RESULTS/${STAMP}-${SCENARIO}-folia.json" ] && [ -f "$RESULTS/${STAMP}-${SCENARIO}-yapfolia.json" ]; then
  python3 "$SCRIPT_DIR/compare-folia.py" \
    "$RESULTS/${STAMP}-${SCENARIO}-folia.json" \
    "$RESULTS/${STAMP}-${SCENARIO}-yapfolia.json" || true
elif [ -f "$RESULTS/${STAMP}-${SCENARIO}-folia.json" ] && [ -f "$RESULTS/${STAMP}-${SCENARIO}-yapcore.json" ]; then
  python3 "$SCRIPT_DIR/compare-folia.py" \
    "$RESULTS/${STAMP}-${SCENARIO}-folia.json" \
    "$RESULTS/${STAMP}-${SCENARIO}-yapcore.json" || true
fi
mapfile -t JSONS < <(ls -1 "$RESULTS"/${STAMP}-${SCENARIO}-*.json 2>/dev/null || true)
if (( ${#JSONS[@]} >= 2 )); then
  echo
  echo "=== Folia-ecosystem ranking (${STAMP}) ==="
  python3 "$SCRIPT_DIR/compare-folia.py" --rank "${JSONS[@]}" || true
fi
