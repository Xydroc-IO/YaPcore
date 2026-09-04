#!/usr/bin/env bash
# Shared helpers for YaPcore scripts — keep POSIX-friendly bash.

yap_java_bin() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "$JAVA_HOME/bin/java"
    return
  fi
  if command -v java >/dev/null 2>&1; then
    command -v java
    return
  fi
  echo ""
}

yap_require_java() {
  BIN="$(yap_java_bin)"
  if [ -z "$BIN" ]; then
    echo "Java not found. Install JDK 21+ and ensure 'java' is on PATH or set JAVA_HOME." >&2
    exit 1
  fi
  # Soft version check — accept 17+ within reason
  VER_LINE="$("$BIN" -version 2>&1 | head -n 1 || true)"
  echo "Using $VER_LINE"
}

yap_load_config() {
  CFG="$ROOT/config/server.properties"
  RAM_MB=2048
  RAM_MIN_MB=512
  MAX_PLAYERS=100
  PORT=25566
  JVM_GC=zgc
  JVM_NUMA=true
  JVM_HEAP_PIN=true
  JVM_NUMA_NODE=0
  JVM_THREAD_PRIORITY=true
  GAME_AUTHORITY=folia
  PAPER_EMBED=true
  PAPER_DIR=paper-kernel
  PAPER_VERSION=26.2
  FOLIA_EMBED=true
  FOLIA_DIR=folia-kernel
  FOLIA_VERSION=26.2
  if [ -f "$CFG" ]; then
    # portable key=value parse (ignore comments / blanks)
    while IFS= read -r line || [ -n "$line" ]; do
      case "$line" in
        ''|\#*) continue ;;
      esac
      key="${line%%=*}"
      val="${line#*=}"
      key="$(echo "$key" | tr -d '[:space:]')"
      case "$key" in
        ram-mb) RAM_MB="$val" ;;
        ram-min-mb) RAM_MIN_MB="$val" ;;
        max-players) MAX_PLAYERS="$val" ;;
        port) PORT="$val" ;;
        jvm-gc) JVM_GC="$val" ;;
        jvm-numa) JVM_NUMA="$val" ;;
        jvm-heap-pin) JVM_HEAP_PIN="$val" ;;
        jvm-numa-node) JVM_NUMA_NODE="$val" ;;
        jvm-thread-priority) JVM_THREAD_PRIORITY="$val" ;;
        game-authority) GAME_AUTHORITY="$val" ;;
        paper-embed) PAPER_EMBED="$val" ;;
        paper-dir) PAPER_DIR="$val" ;;
        paper-version) PAPER_VERSION="$val" ;;
        folia-embed) FOLIA_EMBED="$val" ;;
        folia-dir) FOLIA_DIR="$val" ;;
        folia-version) FOLIA_VERSION="$val" ;;
      esac
    done <"$CFG"
  fi
  # sanitize integers
  RAM_MB="$(echo "$RAM_MB" | tr -cd '0-9')"
  RAM_MIN_MB="$(echo "$RAM_MIN_MB" | tr -cd '0-9')"
  MAX_PLAYERS="$(echo "$MAX_PLAYERS" | tr -cd '0-9')"
  PORT="$(echo "$PORT" | tr -cd '0-9')"
  JVM_NUMA_NODE="$(echo "$JVM_NUMA_NODE" | tr -cd '0-9')"
  [ -n "$RAM_MB" ] || RAM_MB=2048
  [ -n "$RAM_MIN_MB" ] || RAM_MIN_MB=512
  [ -n "$MAX_PLAYERS" ] || MAX_PLAYERS=100
  [ -n "$PORT" ] || PORT=25566
  [ -n "$JVM_NUMA_NODE" ] || JVM_NUMA_NODE=0
  GAME_AUTHORITY="$(echo "${GAME_AUTHORITY:-folia}" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
  PAPER_EMBED="$(echo "${PAPER_EMBED:-true}" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
  PAPER_DIR="$(echo "${PAPER_DIR:-paper-kernel}" | tr -d '[:space:]')"
  PAPER_VERSION="$(echo "${PAPER_VERSION:-26.2}" | tr -d '[:space:]')"
  FOLIA_EMBED="$(echo "${FOLIA_EMBED:-true}" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
  FOLIA_DIR="$(echo "${FOLIA_DIR:-folia-kernel}" | tr -d '[:space:]')"
  FOLIA_VERSION="$(echo "${FOLIA_VERSION:-26.2}" | tr -d '[:space:]')"
  [ -n "$PAPER_DIR" ] || PAPER_DIR=paper-kernel
  [ -n "$PAPER_VERSION" ] || PAPER_VERSION=26.2
  [ -n "$FOLIA_DIR" ] || FOLIA_DIR=folia-kernel
  [ -n "$FOLIA_VERSION" ] || FOLIA_VERSION=26.2
  JVM_GC="$(echo "${JVM_GC:-zgc}" | tr '[:upper:]' '[:lower:]')"
  JVM_NUMA="$(echo "${JVM_NUMA:-true}" | tr '[:upper:]' '[:lower:]')"
  JVM_HEAP_PIN="$(echo "${JVM_HEAP_PIN:-true}" | tr '[:upper:]' '[:lower:]')"
  JVM_THREAD_PRIORITY="$(echo "${JVM_THREAD_PRIORITY:-true}" | tr '[:upper:]' '[:lower:]')"
  if [ "$JVM_HEAP_PIN" = "true" ] || [ "$JVM_HEAP_PIN" = "1" ] || [ "$JVM_HEAP_PIN" = "yes" ]; then
    RAM_MIN_MB="$RAM_MB"
  fi
  if [ "$RAM_MIN_MB" -gt "$RAM_MB" ]; then
    RAM_MIN_MB="$RAM_MB"
  fi
}

# Build JVM_OPTS array for Generational ZGC + NUMA-aware YapEngine production.
# Shell arrays require bash; callers already use bash.
yap_build_jvm_opts() {
  JVM_OPTS=()
  JVM_OPTS+=(-Xms"${RAM_MIN_MB}m" -Xmx"${RAM_MB}m")
  JVM_OPTS+=(-Dyapcore.home="$ROOT")
  JVM_OPTS+=(-Dyapengine.gc.profile=zgc-numa)

  case "$JVM_GC" in
    zgc|generational-zgc|genzgc)
      JVM_OPTS+=(-XX:+UseZGC)
      # JDK 21–22: opt into generational; JDK 23+ ignores / already default.
      JVM_OPTS+=(-XX:+UnlockExperimentalVMOptions)
      JVM_OPTS+=(-XX:+UnlockDiagnosticVMOptions)
      # Harmless if flag removed on newer JDKs — filtered below when probing.
      JVM_OPTS+=(-XX:+ZGenerational)
      ;;
    g1)
      JVM_OPTS+=(-XX:+UseG1GC)
      ;;
    *)
      echo "Unknown jvm-gc='$JVM_GC' — defaulting to ZGC" >&2
      JVM_OPTS+=(-XX:+UseZGC)
      JVM_OPTS+=(-XX:+UnlockExperimentalVMOptions)
      JVM_OPTS+=(-XX:+UnlockDiagnosticVMOptions)
      ;;
  esac

  if [ "$JVM_NUMA" = "true" ] || [ "$JVM_NUMA" = "1" ] || [ "$JVM_NUMA" = "yes" ]; then
    JVM_OPTS+=(-XX:+UseNUMA)
  fi

  if [ "$JVM_THREAD_PRIORITY" = "true" ] || [ "$JVM_THREAD_PRIORITY" = "1" ] || [ "$JVM_THREAD_PRIORITY" = "yes" ]; then
    JVM_OPTS+=(-XX:ThreadPriorityPolicy=1)
  fi

  # Extra opts from env (space-separated), e.g. YAPCORE_JAVA_OPTS="-XX:SoftMaxHeapSize=10G"
  if [ -n "${YAPCORE_JAVA_OPTS:-}" ]; then
    # shellcheck disable=SC2206
    EXTRA=( $YAPCORE_JAVA_OPTS )
    JVM_OPTS+=("${EXTRA[@]}")
  fi
}

# Drop flags the local HotSpot rejects (e.g. removed ZGenerational on JDK 26).
yap_filter_jvm_opts() {
  JAVA_BIN="$(yap_java_bin)"
  FILTERED=()
  for opt in "${JVM_OPTS[@]}"; do
    case "$opt" in
      -XX:+ZGenerational|-XX:-ZGenerational)
        if "$JAVA_BIN" -XX:+UnlockExperimentalVMOptions "$opt" -version >/dev/null 2>&1; then
          FILTERED+=("$opt")
        fi
        ;;
      *)
        FILTERED+=("$opt")
        ;;
    esac
  done
  JVM_OPTS=("${FILTERED[@]}")
}

# Prefix command with numactl when available and NUMA enabled.
# Sets NUMA_PREFIX as array (empty if unused).
yap_numa_prefix() {
  NUMA_PREFIX=()
  if [ "$JVM_NUMA" != "true" ] && [ "$JVM_NUMA" != "1" ] && [ "$JVM_NUMA" != "yes" ]; then
    return
  fi
  if ! command -v numactl >/dev/null 2>&1; then
    echo "numactl not found — JVM -XX:+UseNUMA still applied; install numactl for node bind" >&2
    return
  fi
  NODE="${JVM_NUMA_NODE:-0}"
  NUMA_PREFIX=(numactl --cpunodebind="$NODE" --membind="$NODE")
}

yap_apply_ulimits() {
  # Soft limits — ignore failures on restricted environments
  ulimit -n 65535 2>/dev/null || ulimit -n 4096 2>/dev/null || true
  # Optional process / memory soft caps when permitted
  if [ -n "${YAPCORE_ULIMIT_STACK:-}" ]; then
    ulimit -s "$YAPCORE_ULIMIT_STACK" 2>/dev/null || true
  fi
}

# Active game kernel dir for product path (YaP-Folia default).
yap_active_kernel_dir() {
  case "${GAME_AUTHORITY:-folia}" in
    paper) echo "${PAPER_DIR:-paper-kernel}" ;;
    *) echo "${FOLIA_DIR:-folia-kernel}" ;;
  esac
}

yap_ensure_dirs() {
  mkdir -p "$ROOT/config" "$ROOT/plugins" "$ROOT/logs" "$ROOT/lib" "$ROOT/$FOLIA_DIR"
  yap_ensure_unified_plugins
  yap_ensure_config_hub
  # Shippable defaults (never overwrite operator files)
  if [ -x "$ROOT/scripts/seed-defaults.sh" ]; then
    bash "$ROOT/scripts/seed-defaults.sh" --root "$ROOT" || true
  elif [ -f "$ROOT/scripts/seed-defaults.sh" ]; then
    bash "$ROOT/scripts/seed-defaults.sh" --root "$ROOT" || true
  fi
  if [ ! -f "$ROOT/config/server.properties" ]; then
    if [ -f "$ROOT/config/defaults/server.properties" ]; then
      cp -f "$ROOT/config/defaults/server.properties" "$ROOT/config/server.properties"
    elif [ -f "$ROOT/config/server.properties.example" ]; then
      cp -f "$ROOT/config/server.properties.example" "$ROOT/config/server.properties"
    else
      cat >"$ROOT/config/server.properties" <<'EOF'
server-name=YaPcore
bind-host=0.0.0.0
port=25566
max-players=100
ram-mb=2048
ram-min-mb=512
view-distance=10
motd=YaPcore · YaP-Folia · Yap Edge
plugins-dir=plugins
logs-dir=logs
online-mode=false
gui-enabled=true
jvm-gc=zgc
jvm-numa=true
jvm-heap-pin=true
jvm-numa-node=0
jvm-thread-priority=true
internet-exposed=false
server-domain=
public-host=
public-port=0
public-bedrock-port=0
public-pack-port=0
srv-enabled=true
game-authority=folia
folia-embed=true
folia-dir=folia-kernel
folia-version=26.2
resource-pack-enabled=true
resource-pack-file=yapcore-default.zip
yap-ranks-auto-apply=true
web-dashboard-enabled=true
web-dashboard-port=8080
web-dashboard-bind=127.0.0.1
EOF
    fi
  fi
  local folia_dir="${FOLIA_DIR:-folia-kernel}"
  if [ ! -f "$ROOT/$folia_dir/server.properties" ]; then
    if [ -f "$ROOT/$folia_dir/server.properties.example" ]; then
      cp -f "$ROOT/$folia_dir/server.properties.example" "$ROOT/$folia_dir/server.properties"
    fi
  fi
}

# Link kernel_dir/plugins → ../plugins (migrate jars out of a former real dir).
yap_link_kernel_plugins() {
  local kernel_dir="$1"
  local unified="$ROOT/plugins"
  local kernel_plugins="$ROOT/$kernel_dir/plugins"
  mkdir -p "$unified" "$ROOT/$kernel_dir"

  if [ -L "$kernel_plugins" ]; then
    local target
    target="$(readlink -f "$kernel_plugins" 2>/dev/null || readlink "$kernel_plugins" || true)"
    local unified_real
    unified_real="$(readlink -f "$unified" 2>/dev/null || echo "$unified")"
    if [ -n "$target" ] && [ "$target" = "$unified_real" ]; then
      return 0
    fi
    rm -f "$kernel_plugins"
  fi
  if [ -d "$kernel_plugins" ]; then
    shopt -s nullglob
    local f
    for f in "$kernel_plugins"/*.jar "$kernel_plugins"/*.yap; do
      [ -e "$f" ] || continue
      mv -f "$f" "$unified/" 2>/dev/null || true
      echo "Migrated $(basename "$f") → plugins/"
    done
    shopt -u nullglob
    rmdir "$kernel_plugins" 2>/dev/null || rm -rf "$kernel_plugins"
  fi
  ln -sfn ../plugins "$kernel_plugins"
}

# Central operator config: config/folia → folia-dir/config (+ optional file links).
yap_ensure_config_hub() {
  local hub="$ROOT/config"
  local kernel_dir
  kernel_dir="$(yap_active_kernel_dir)"
  local kernel_cfg="$ROOT/$kernel_dir/config"
  mkdir -p "$hub" "$kernel_cfg"

  # Product hub name follows active authority.
  if [ "$GAME_AUTHORITY" = "folia" ]; then
    if [ -L "$hub/folia" ] || [ ! -e "$hub/folia" ]; then
      ln -sfn "../$kernel_dir/config" "$hub/folia"
    elif [ ! -L "$hub/folia" ]; then
      echo "WARN: $hub/folia exists and is not a symlink — leave as-is" >&2
    fi
    # Retarget stale config/paper symlink left from Paper-era trees.
    if [ -L "$hub/paper" ]; then
      ln -sfn "../$kernel_dir/config" "$hub/paper"
    fi
  else
    if [ -L "$hub/paper" ] || [ ! -e "$hub/paper" ]; then
      ln -sfn "../$kernel_dir/config" "$hub/paper"
    elif [ ! -L "$hub/paper" ]; then
      echo "WARN: $hub/paper exists and is not a symlink — leave as-is" >&2
    fi
  fi

  for f in spigot.yml bukkit.yml commands.yml; do
    if [ -f "$ROOT/$kernel_dir/$f" ] && [ ! -e "$hub/$f" ]; then
      ln -sfn "../$kernel_dir/$f" "$hub/$f"
    fi
  done
  if [ -f "$ROOT/$kernel_dir/server.properties" ] && [ ! -e "$hub/game-server.properties" ]; then
    ln -sfn "../$kernel_dir/server.properties" "$hub/game-server.properties"
  fi
  if [ ! -f "$hub/README.md" ]; then
    cat >"$hub/README.md" <<'EOF'
# YaPcore config hub

Edit **here** for day-to-day tuning.

| Path | What |
|------|------|
| `server.properties` | YaP product (ports, dual-stack, packs) |
| `folia/` | YaP-Folia / Paper-family globals (product path) |
| `spigot.yml` / `bukkit.yml` | Classic Spigot/Bukkit (symlinks when present) |

Gameplay encyclopedia: `plugins/YaPGameplayKnobs/knobs.yml` (jar in `plugins/`).
See `docs/ops/TUNE.md`.
EOF
  fi
}

# One operator folder: $ROOT/plugins. folia-kernel/plugins → ../plugins (product path).
yap_ensure_unified_plugins() {
  yap_link_kernel_plugins "${FOLIA_DIR:-folia-kernel}"
  # If a leftover paper-kernel tree exists, keep it unified too (no Paper product path).
  if [ -d "$ROOT/${PAPER_DIR:-paper-kernel}" ]; then
    yap_link_kernel_plugins "${PAPER_DIR:-paper-kernel}"
  fi
}

# Retired: Paperclip / Phase 3 NMS is not on the product path.
yap_require_yap_paperclip() {
  return 0
}

yap_find_jar() {
  if [ -f "$ROOT/yapcore.jar" ]; then
    echo "$ROOT/yapcore.jar"
    return
  fi
  CANDIDATE="$(ls -1 "$ROOT/build/libs"/yapcore-*.jar 2>/dev/null | grep -v -- '-plain' | tail -n 1 || true)"
  if [ -n "$CANDIDATE" ] && [ -f "$CANDIDATE" ]; then
    echo "$CANDIDATE"
    return
  fi
  echo ""
}

yap_build() {
  if command -v gradle >/dev/null 2>&1; then
    (cd "$ROOT" && gradle shadowJar --quiet)
  elif [ -x "$ROOT/gradlew" ]; then
    (cd "$ROOT" && ./gradlew shadowJar --quiet)
  else
    echo "Neither gradle nor ./gradlew found; cannot auto-build." >&2
    return 1
  fi
}

yap_read_pid() {
  if [ -f "$ROOT/yapcore.pid" ]; then
    tr -d '[:space:]' <"$ROOT/yapcore.pid"
  fi
}

yap_root_real() {
  if command -v readlink >/dev/null 2>&1; then
    readlink -f "$ROOT" 2>/dev/null || echo "$ROOT"
  else
    echo "$ROOT"
  fi
}

# True when pid's -Dyapcore.home resolves to this install (bench/workdir trees excluded).
yap_pid_belongs_to_root() {
  local pid="$1" cmd home root_real file_pid
  [ -n "$pid" ] || return 1
  [ -r "/proc/$pid/cmdline" ] || return 1
  cmd="$(tr '\0' ' ' <"/proc/$pid/cmdline" 2>/dev/null || true)"
  case "$cmd" in
    *-Dyapcore.home=*)
      home="${cmd#*-Dyapcore.home=}"
      home="${home%% *}"
      ;;
    *)
      home=""
      ;;
  esac
  root_real="$(yap_root_real)"
  if [ -n "$home" ]; then
    if command -v readlink >/dev/null 2>&1; then
      home="$(readlink -f "$home" 2>/dev/null || echo "$home")"
    fi
    [ "$home" = "$root_real" ]
    return
  fi
  file_pid="$(yap_read_pid)"
  [ -n "$file_pid" ] && [ "$file_pid" = "$pid" ]
}

# True if /proc/$1/cmdline looks like a YaPcore JVM (not a shell/IDE that merely mentions the path).
yap_pid_is_yapcore() {
  local pid="$1" cmd=""
  [ -n "$pid" ] || return 1
  [ -r "/proc/$pid/cmdline" ] || return 1
  cmd="$(tr '\0' ' ' <"/proc/$pid/cmdline" 2>/dev/null || true)"
  case "$cmd" in
    *'/java '*|*' java '*|java\ *)
      ;;
    *)
      return 1
      ;;
  esac
  case "$cmd" in
    *-jar\ *yapcore*.jar*|*-jar\ *yapcore.jar*)
      return 0
      ;;
    *-Dyapcore.home=*|*com.yapcore.Main*)
      return 0
      ;;
  esac
  return 1
}

# Print every live YaPcore JVM pid (one per line). Dedupes pid-file + process scan.
yap_find_pids() {
  local pid="" seen=" "
  if [ -f "$ROOT/yapcore.pid" ]; then
    pid="$(tr -d '[:space:]' <"$ROOT/yapcore.pid" || true)"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null \
        && yap_pid_is_yapcore "$pid" && yap_pid_belongs_to_root "$pid"; then
      echo "$pid"
      seen=" $pid "
    fi
  fi
  if command -v pgrep >/dev/null 2>&1; then
    # Real JVMs only: argv0 is java (optional path), then -jar …yapcore… or main class / home prop
    while IFS= read -r pid; do
      [ -n "$pid" ] || continue
      case "$seen" in
        *" $pid "*) continue ;;
      esac
      if kill -0 "$pid" 2>/dev/null && yap_pid_is_yapcore "$pid" \
          && yap_pid_belongs_to_root "$pid"; then
        echo "$pid"
        seen="$seen$pid "
      fi
    done < <(pgrep -f '(^|/)java .*(yapcore\.jar|yapcore-.*\.jar|-Dyapcore\.home=|com\.yapcore\.Main)' 2>/dev/null || true)
    return
  fi
  ps ax -o pid=,args= 2>/dev/null | awk '
    /[j]ava .*(yapcore\.jar|yapcore-.*\.jar|-Dyapcore\.home=|com\.yapcore\.Main)/ {print $1}
  ' | while IFS= read -r pid; do
    [ -n "$pid" ] || continue
    case "$seen" in
      *" $pid "*) continue ;;
    esac
    if kill -0 "$pid" 2>/dev/null; then
      echo "$pid"
      seen="$seen$pid "
    fi
  done
}

# True when pid is an MSPT bench JVM (must not block product start/gui).
yap_pid_is_bench() {
  local pid="$1" cmd=""
  [ -n "$pid" ] || return 1
  [ -r "/proc/$pid/cmdline" ] || return 1
  cmd="$(tr '\0' ' ' <"/proc/$pid/cmdline" 2>/dev/null || true)"
  case "$cmd" in
    *-Dyap.bench.scenario=*|*bench/workdir-*)
      return 0
      ;;
  esac
  return 1
}

yap_find_pid() {
  yap_find_pids | head -n 1
}

yap_find_product_pids() {
  local pid=""
  while IFS= read -r pid; do
    [ -n "$pid" ] || continue
    if yap_pid_is_bench "$pid"; then
      continue
    fi
    echo "$pid"
  done < <(yap_find_pids)
}

yap_find_product_pid() {
  yap_find_product_pids | head -n 1
}

yap_find_bench_pids() {
  local pid=""
  while IFS= read -r pid; do
    [ -n "$pid" ] || continue
    if yap_pid_is_bench "$pid"; then
      echo "$pid"
    fi
  done < <(yap_find_pids)
}

yap_is_running() {
  if [ -n "$(yap_find_product_pids | head -n 1)" ]; then
    return 0
  fi
  return 1
}

# Resolve gradle / gradlew from PATH or project root (works when Konsole cwd ≠ ROOT).
yap_gradle_bin() {
  if [ -x "$ROOT/gradlew" ]; then
    echo "$ROOT/gradlew"
    return
  fi
  if command -v gradle >/dev/null 2>&1; then
    command -v gradle
    return
  fi
  echo ""
}

yap_gradle() {
  BIN="$(yap_gradle_bin)"
  if [ -z "$BIN" ]; then
    echo "ERROR: gradle not found. Install Gradle or add ./gradlew to the project." >&2
    return 127
  fi
  echo "+ (cd $ROOT && $BIN $*)"
  (cd "$ROOT" && "$BIN" "$@")
}

yap_banner() {
  echo ""
  echo "════════════════════════════════════════════════════════"
  echo "  YaPcore · $*"
  echo "  root: $ROOT"
  echo "════════════════════════════════════════════════════════"
  echo ""
}

# Poll a log file until a grep pattern matches (returns 0) or timeout (returns 1).
yap_wait_log_grep() {
  local log="$1" pattern="$2" timeout="${3:-20}"
  local start now
  start="$(date +%s)"
  while [ -f "$log" ]; do
    if grep -qE "$pattern" "$log" 2>/dev/null; then
      return 0
    fi
    now="$(date +%s)"
    if [ $((now - start)) -ge "$timeout" ]; then
      return 1
    fi
    sleep 1
  done
  return 1
}

# Keep Konsole / Dolphin "Run" windows open so you can read results.
yap_pause_end() {
  CODE="${1:-0}"
  echo ""
  if [ "$CODE" -eq 0 ]; then
    echo "✓ Finished OK (exit $CODE)"
  else
    echo "✗ Failed (exit $CODE)"
  fi
  # Only pause when attached to a real terminal and not disabled
  if [ -t 0 ] && [ -t 1 ] && [ "${YAP_NO_PAUSE:-0}" != "1" ]; then
    echo ""
    printf "Press Enter to close…"
    # shellcheck disable=SC2034
    read -r _ || true
  fi
  return "$CODE"
}

# Shared bootstrap for drop-in test scripts (source after setting SCRIPT_DIR).
yap_test_bootstrap() {
  if [ -z "${SCRIPT_DIR:-}" ]; then
    echo "SCRIPT_DIR not set" >&2
    exit 1
  fi
  ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
  # shellcheck disable=SC1091
  . "$SCRIPT_DIR/lib.sh"
  cd "$ROOT"
  export YAPCORE_HOME="$ROOT"
  yap_require_java
}

