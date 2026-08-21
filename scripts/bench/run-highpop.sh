#!/usr/bin/env bash
# High-pop proof: 500-player-class Mineflayer load + fair plugin surface vs Paper/Purpur/Leaf/YaP.
#
# Fairness rule — match what YaPcore offers *natively*:
#   • All sides: yap-mspt-bench, yap-pop-sim, yap-placeholderapi, yap-gameplay-knobs, yap-vehicles
#   • Stock forks only: ViaVersion + ViaBackwards + ViaRewind  (YaP uses built-in ProtocolCompat)
#   • YaP only: yap-spatial-tick (the product under test — not mirrored onto forks)
#
# Usage: ./scripts/bench/run-highpop.sh [player_count] [sample_seconds]
# Default: 500 players / 45s sample
# Env: YAP_BENCH_COMPETITORS, YAP_BENCH_WARMUP, YAP_BOT_VERSION, YAP_BENCH_XMS, YAP_BENCH_XMX
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=../lib.sh
# shellcheck disable=SC1091
. "$ROOT/scripts/lib.sh"

PLAYERS="${1:-500}"
SECONDS_N="${2:-45}"
WARMUP="${YAP_BENCH_WARMUP:-25}"
# ~stagger*players + login headroom
JOIN_TIMEOUT="${YAP_BENCH_JOIN_TIMEOUT:-$(( 180 + PLAYERS ))}"
COMPETITORS_CSV="${YAP_BENCH_COMPETITORS:-paper,purpur,leaf,yapcore}"
STAMP="${YAP_BENCH_STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
SCENARIO=highpop
XMS="${YAP_BENCH_XMS:-8G}"
XMX="${YAP_BENCH_XMX:-12G}"
RESULTS="$ROOT/bench/results"
BOTS_DIR="$SCRIPT_DIR/bots"
START_BOTS_PID=""
LOCK="$ROOT/bench/highpop.lock"
mkdir -p "$RESULTS" "$ROOT/logs/bench"

# Single-flight: overlapping highpop runs OOM the host and duplicate_login bots.
exec 9>"$LOCK"
if ! flock -n 9; then
  echo "Another highpop run holds $LOCK — aborting" >&2
  exit 4
fi

cd "$ROOT"
export ROOT
yap_require_java
yap_load_config

JAVA_BIN="$(yap_java_bin)"
FEATURE="$("$JAVA_BIN" -XshowSettings:properties -version 2>&1 | awk -F= '/java.specification.version/ {gsub(/ /,"",$2); print $2; exit}')"
MAJOR="${FEATURE%%.*}"
if [ "${MAJOR:-0}" -lt 25 ] 2>/dev/null; then
  echo "Needs Java 25+ (have $FEATURE)" >&2
  exit 1
fi

echo "Ensuring competitor jars + parity Via* + npm bots…"
"$SCRIPT_DIR/fetch-competitors.sh" "$PAPER_VERSION"
"$SCRIPT_DIR/fetch-parity-plugins.sh"
if [ ! -d "$BOTS_DIR/node_modules/mineflayer" ]; then
  (cd "$BOTS_DIR" && npm install --no-fund --no-audit)
fi

echo "Building bench + shipped product plugins…"
(cd "$ROOT" && gradle \
  :bench-plugin:jar :bench-plugin:popSimJar \
  :placeholderapi-plugin:jar \
  :gameplay-knobs-plugin:jar \
  :vehicles-plugin:jar \
  --no-daemon -q)
if [[ "$COMPETITORS_CSV" == *yapcore* ]]; then
  (cd "$ROOT" && gradle :phase3-plugin:installIntoResources shadowJar --no-daemon -q)
fi

BENCH_JAR="$(ls -1 "$ROOT/bench-plugin/build/libs"/yap-mspt-bench.jar | head -n 1)"
POP_JAR="$(ls -1 "$ROOT/bench-plugin/build/libs"/yap-pop-sim.jar | head -n 1)"
PAPI_JAR="$(ls -1 "$ROOT/placeholderapi-plugin/build/libs"/yap-placeholderapi.jar | head -n 1)"
KNOBS_JAR="$(ls -1 "$ROOT/gameplay-knobs-plugin/build/libs"/yap-gameplay-knobs.jar | head -n 1)"
VEHICLES_JAR="$(ls -1 "$ROOT/vehicles-plugin/build/libs"/yap-vehicles.jar | head -n 1)"
YAP_JAR="$(yap_find_jar)"
case "$YAP_JAR" in /*) ;; *) YAP_JAR="$ROOT/$YAP_JAR" ;; esac
YAP_PAPER="$ROOT/lib/paper-${PAPER_VERSION}-yap.jar"

for need in "$BENCH_JAR" "$POP_JAR" "$PAPI_JAR" "$KNOBS_JAR" "$VEHICLES_JAR"; do
  if [ ! -f "$need" ]; then
    echo "Missing built jar: $need" >&2
    exit 1
  fi
done

write_spigot_yml() {
  local dest="$1"
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
      misc: 16
      water: 16
      villagers: 48
      flying-monsters: 48
      wake-up-inactive:
        animals-max-per-tick: 2000
        animals-every: 1
        animals-for: 100
        monsters-max-per-tick: 2000
        monsters-every: 1
        monsters-for: 100
        villagers-max-per-tick: 400
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
  local maxp=$(( PLAYERS + 50 ))
  if [ "$maxp" -lt 100 ]; then maxp=100; fi
  cat >"$dest" <<EOF
motd=$motd
online-mode=false
max-players=$maxp
server-port=$port
level-seed=yap-highpop-1
view-distance=10
simulation-distance=10
spawn-monsters=true
player-idle-timeout=0
network-compression-threshold=-1
allow-flight=true
EOF
}

# mode: stock | yap
install_common_plugins() {
  local work="$1"
  /bin/cp -f "$BENCH_JAR" "$work/plugins/yap-mspt-bench.jar"
  /bin/cp -f "$POP_JAR" "$work/plugins/yap-pop-sim.jar"
  # Same shipped product jars YaP advertises — identical code on every competitor.
  /bin/cp -f "$PAPI_JAR" "$work/plugins/yap-placeholderapi.jar"
  /bin/cp -f "$KNOBS_JAR" "$work/plugins/yap-gameplay-knobs.jar"
  /bin/cp -f "$VEHICLES_JAR" "$work/plugins/yap-vehicles.jar"
}

install_stock_parity_plugins() {
  local work="$1"
  # Forks do not have ProtocolCompat — Via* stands in for YaP native multi-version.
  for v in ViaVersion ViaBackwards ViaRewind; do
    if [ -f "$ROOT/lib/${v}.jar" ]; then
      /bin/cp -f "$ROOT/lib/${v}.jar" "$work/plugins/${v}.jar"
    else
      echo "WARN: missing $ROOT/lib/${v}.jar — stock fork multi-version unfair" >&2
    fi
  done
}

prepare_workdir() {
  local work="$1"
  local server_jar="$2"
  local port="$3"
  local motd="$4"
  local mode="${5:-stock}"
  rm -rf "$work"
  mkdir -p "$work/plugins" "$work/cache"
  /bin/cp -f "$server_jar" "$work/paper.jar"
  # Paperclip re-download hangs offline — seed Mojang jar from any known cache.
  local mojang=""
  for cand in \
      "$ROOT/cache/mojang_${PAPER_VERSION}.jar" \
      "$ROOT/paper-kernel/cache/mojang_${PAPER_VERSION}.jar" \
      "$ROOT/bench/workdir-paper/cache/mojang_${PAPER_VERSION}.jar" \
      "$ROOT/lib/server-${PAPER_VERSION}.jar"; do
    if [[ -f "$cand" ]]; then mojang="$cand"; break; fi
  done
  if [[ -n "$mojang" ]]; then
    /bin/cp -f "$mojang" "$work/cache/mojang_${PAPER_VERSION}.jar"
  fi
  install_common_plugins "$work"
  if [[ "$mode" == "stock" ]]; then
    install_stock_parity_plugins "$work"
  fi
  # yap mode: no Via* — native multi-version is the product offer under test
  printf 'eula=true\n' >"$work/eula.txt"
  write_server_props "$work/server.properties" "$port" "$motd"
  write_spigot_yml "$work/spigot.yml"
}

wait_ports_free() {
  local ports=(25566 25570 25571 25572 25573 25574)
  local i p
  for i in $(seq 1 20); do
    local busy=0
    for p in "${ports[@]}"; do
      if ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE ":${p}$"; then
        busy=1
        local pid
        pid="$(ss -ltnp 2>/dev/null | awk -v p=":$p" '$4 ~ p {print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -1)"
        if [[ -n "$pid" ]]; then
          local cmd; cmd="$(ps -p "$pid" -o args= 2>/dev/null || true)"
          if [[ "$cmd" == *bench/workdir* ]]; then
            echo "Killing leftover bench JVM pid=$pid on port $p" >&2
            kill -9 "$pid" 2>/dev/null || true
          fi
        fi
      fi
    done
    [[ "$busy" -eq 0 ]] && return 0
    sleep 2
  done
}

start_bots() {
  local port="$1"
  local log="$2"
  rm -f "$ROOT/bench/highpop-ready.port"
  : >"$log"
  # Do NOT call via $(start_bots) — command substitution waits on the waiter.
  (
    local n=0
    local ready=0
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
    export YAP_BOT_COUNT="$PLAYERS"
    # Keep total connect ramp ~40s even at 500
    export YAP_BOT_STAGGER_MS="${YAP_BOT_STAGGER_MS:-$(( 90000 / PLAYERS ))}"
    if [ "${YAP_BOT_STAGGER_MS}" -lt 100 ]; then YAP_BOT_STAGGER_MS=100; fi
    if [ "${YAP_BOT_STAGGER_MS}" -gt 250 ]; then YAP_BOT_STAGGER_MS=250; fi
    export YAP_BOT_VERSION="${YAP_BOT_VERSION:-1.21.11}"
    echo "bot stagger=${YAP_BOT_STAGGER_MS}ms count=$PLAYERS version=$YAP_BOT_VERSION" >>"$log"
    cd "$BOTS_DIR"
    node swarm.js >>"$log" 2>&1
  ) &
  START_BOTS_PID=$!
}

stop_bots() {
  local pid="${1:-}"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    kill -INT "$pid" 2>/dev/null || true
    sleep 2
    kill -9 "$pid" 2>/dev/null || true
  fi
  pkill -f 'yapbot_' 2>/dev/null || true
  pkill -f 'scripts/bench/bots/swarm.js' 2>/dev/null || true
}

run_plain() {
  local id="$1"
  local jar="$2"
  local port="$3"
  local label="$4"
  local out="$RESULTS/${STAMP}-${SCENARIO}-${id}.json"
  local work="$ROOT/bench/workdir-${id}"
  local botlog="$ROOT/logs/bench/bots-${STAMP}-${id}.log"
  prepare_workdir "$work" "$jar" "$port" "YaP highpop $id" stock
  echo "=== $id highpop players=$PLAYERS → $out (stock+Via* parity) ==="
  local botpid=""
  start_bots "$port" "$botlog"
  botpid="$START_BOTS_PID"
  (
    cd "$work"
    "$JAVA_BIN" -Xms"$XMS" -Xmx"$XMX" \
      -Dyap.bench.scenario=highpop \
      -Dyap.bench.seconds="$SECONDS_N" \
      -Dyap.bench.warmup="$WARMUP" \
      -Dyap.bench.players="$PLAYERS" \
      -Dyap.bench.join_timeout="$JOIN_TIMEOUT" \
      -Dyap.bench.label="$label" \
      -Dyap.bench.out="$out" \
      -Dyapcore.home="$ROOT" \
      -jar paper.jar --nogui
  ) || true
  stop_bots "$botpid"
  if [ ! -f "$out" ]; then
    echo "WARN: $id did not write $out" >&2
  fi
}

run_yap() {
  local out="$RESULTS/${STAMP}-${SCENARIO}-yapcore.json"
  local work="$ROOT/bench/workdir-yap"
  local botlog="$ROOT/logs/bench/bots-${STAMP}-yapcore.log"
  local public_port=25571
  local paper_port=25574
  local cfg="$ROOT/config/server.properties"
  local cfg_bak="$ROOT/config/server.properties.highpop.bak"
  # Paper owns loopback paper_port; YaP native Via front owns public_port (bots join here).
  prepare_workdir "$work" "$YAP_PAPER" "$paper_port" "YaP highpop yapcore" yap
  /bin/cp -f "$YAP_PAPER" "$work/paper-${PAPER_VERSION}.jar"
  if [ -f "$ROOT/src/main/resources/phase3/yap-spatial-tick.jar" ]; then
    /bin/cp -f "$ROOT/src/main/resources/phase3/yap-spatial-tick.jar" "$work/plugins/" || true
  fi
  /bin/cp -f "$cfg" "$cfg_bak"
  # Patch product config for this run (restored in stop).
  python3 - "$cfg" "$public_port" "$paper_port" "$PLAYERS" <<'PY'
import sys
from pathlib import Path
cfg, pub, paper, players = Path(sys.argv[1]), sys.argv[2], sys.argv[3], int(sys.argv[4])
lines = cfg.read_text().splitlines()
kv = {}
for line in lines:
    if line.strip() and not line.strip().startswith("#") and "=" in line:
        k, _, v = line.partition("=")
        kv[k.strip()] = v.strip()
kv.update({
    "port": pub,
    "paper-port": paper,
    "paper-dir": "bench/workdir-yap",
    "protocol-via-enabled": "true",
    "protocol-geyser-enabled": "false",
    "bedrock-enabled": "false",
    "max-players": str(players + 50),
    "gui-enabled": "false",
    "game-authority": "paper",
    "paper-phase3-nms-tick": "true",
    # Bots cannot satisfy forced packs — match stock Paper highpop (no pack gate).
    "resource-pack-enabled": "false",
    "resource-pack-forced": "false",
})
out = ["#YaPcore highpop bench overlay\n"]
for k, v in sorted(kv.items()):
    out.append(f"{k}={v}\n")
cfg.write_text("".join(out))
PY
  restore_yap_cfg() {
    if [ -f "$cfg_bak" ]; then
      /bin/mv -f "$cfg_bak" "$cfg"
    fi
  }
  trap restore_yap_cfg RETURN
  echo "=== yapcore highpop players=$PLAYERS → $out (native Via front :$public_port → Paper :$paper_port) ==="
  local botpid=""
  start_bots "$public_port" "$botlog"
  botpid="$START_BOTS_PID"
  (
    cd "$work"
    "$JAVA_BIN" -Xms"$XMS" -Xmx"$XMX" \
      -Dyapcore.home="$ROOT" \
      -Dyapcore.paper.dir=bench/workdir-yap \
      -Dyapcore.phase3.spatial-tick=true \
      -Dyapcore.phase3.spatial-blockfluid=true \
      -Dyapcore.phase3.spatial-random=true \
      -Dyapcore.phase3.spatial-blockentities=true \
      -Dyapcore.phase3.spatial-redstone=true \
      -Dyap.bench.scenario=highpop \
      -Dyap.bench.seconds="$SECONDS_N" \
      -Dyap.bench.warmup="$WARMUP" \
      -Dyap.bench.players="$PLAYERS" \
      -Dyap.bench.join_timeout="$JOIN_TIMEOUT" \
      -Dyap.bench.bot_port="$public_port" \
      -Dyap.bench.label=yapcore-phase3 \
      -Dyap.bench.out="$out" \
      -jar "$YAP_JAR" --nogui
  ) || true
  stop_bots "$botpid"
  restore_yap_cfg
  trap - RETURN
  if [ ! -f "$out" ]; then
    echo "WARN: yapcore did not write $out" >&2
  fi
}

IFS=',' read -r -a COMPETITORS <<<"$COMPETITORS_CSV"
echo "Highpop competitors=${COMPETITORS[*]} players=$PLAYERS heap=${XMS}/${XMX} join_timeout=${JOIN_TIMEOUT}s stamp=$STAMP"
echo "Parity: all=PAPI+knobs+vehicles+pop-sim; stock+=Via*; yap+=spatial-tick (native Via)"

for c in "${COMPETITORS[@]}"; do
  c="$(echo "$c" | tr -d '[:space:]')"
  wait_ports_free
  stop_bots ""
  case "$c" in
    paper|stock) run_plain paper "$ROOT/lib/paper-${PAPER_VERSION}.jar" 25570 stock-paper ;;
    purpur) run_plain purpur "$ROOT/lib/purpur-${PAPER_VERSION}.jar" 25572 purpur ;;
    leaf) run_plain leaf "$ROOT/lib/leaf-${PAPER_VERSION}.jar" 25573 leaf ;;
    yapcore|yap) run_yap ;;
    *) echo "Unknown competitor $c" >&2; exit 2 ;;
  esac
done

echo
python3 "$SCRIPT_DIR/compare-highpop.py" "$RESULTS" "$STAMP" || true
ls -1 "$RESULTS"/${STAMP}-${SCENARIO}-*.json 2>/dev/null || true
