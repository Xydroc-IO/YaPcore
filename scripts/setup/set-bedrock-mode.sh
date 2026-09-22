#!/usr/bin/env bash
# Set Bedrock path before starting servers (mirrors dashboard set-bedrock-mode).
# Usage: ./scripts/setup/set-bedrock-mode.sh first-party|geyser-backup
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODE="${1:-}"
if [[ -z "$MODE" ]]; then
  echo "Usage: $0 first-party|geyser-backup" >&2
  exit 2
fi
case "$MODE" in
  first-party|geyser-backup) ;;
  geyser|backup) MODE=geyser-backup ;;
  *)
    echo "Unknown mode: $MODE (want first-party or geyser-backup)" >&2
    exit 2
    ;;
esac

LINK_HOME="${ROOT}/link-data"
SERVER_PROPS="${ROOT}/config/server.properties"
mkdir -p "$LINK_HOME"

# Prefer Java applier when a built jar exists; else patch properties with sed/python.
apply_java() {
  local jar="${ROOT}/yapcore.jar"
  [[ -f "$jar" ]] || jar="$(ls -1 "${ROOT}/build/libs"/yapcore-*.jar 2>/dev/null | head -1 || true)"
  [[ -n "${jar:-}" && -f "$jar" ]] || return 1
  java -cp "$jar" com.yapcore.config.BedrockModeApplierCli "$ROOT" "$LINK_HOME" "$SERVER_PROPS" "$MODE"
}

apply_shell() {
  local link_props="${LINK_HOME}/link.properties"
  local server_props="$SERVER_PROPS"
  touch "$link_props" "$server_props"
  python3 - "$MODE" "$link_props" "$server_props" <<'PY'
import sys
from pathlib import Path

mode, link_path, server_path = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3])
backup = mode == "geyser-backup"

def load(p: Path) -> dict:
    d = {}
    if p.is_file():
        for line in p.read_text().splitlines():
            s = line.strip()
            if not s or s.startswith("#") or "=" not in s:
                continue
            k, v = s.split("=", 1)
            d[k.strip()] = v.strip()
    return d

def store(p: Path, d: dict, comment: str) -> None:
    lines = [f"# {comment}"]
    for k, v in d.items():
        lines.append(f"{k}={v}")
    p.write_text("\n".join(lines) + "\n")

link = load(link_path)
link["bedrock-mode"] = mode
if backup:
    link["bedrock-enabled"] = "false"
    link["geyser-enabled"] = "true"
    link.setdefault("geyser-home", "geyser")
    link.setdefault("geyser-jar", "Geyser-Standalone.jar")
    link.setdefault("geyser-remote-host", "127.0.0.1")
    link.setdefault("geyser-remote-port", "25565")
    link.setdefault("geyser-bedrock-bind", "0.0.0.0:19132")
else:
    link["bedrock-enabled"] = "true"
    link["geyser-enabled"] = "false"
    link.setdefault("bedrock-bind", "0.0.0.0:25565,0.0.0.0:19132")
    link.setdefault("bedrock-backend", "127.0.0.1:25566")
    link["bedrock-shared-port"] = "true"
    link["bedrock-also-19132"] = "true"
store(link_path, link, "YaP Link — bedrock-mode applied")

server = load(server_path)
server["bedrock-mode"] = mode
if backup:
    server["bedrock-enabled"] = "false"
    server["crossplay-enabled"] = "false"
    server["shared-listen-port"] = "false"
else:
    server["bedrock-enabled"] = "true"
    server["crossplay-enabled"] = "true"
    server["shared-listen-port"] = "true"
store(server_path, server, "YaPcore server configuration")

jar = Path(link_path).parent / link.get("geyser-home", "geyser") / link.get("geyser-jar", "Geyser-Standalone.jar")
print(f"bedrock-mode={mode}")
print(f"link bedrock-enabled={link['bedrock-enabled']}")
print(f"chassis bedrock-enabled={server['bedrock-enabled']}")
print(f"geyser jar={jar} present={jar.is_file()}")
if backup and not jar.is_file():
    print(f"WARNING: stage Geyser-Standalone.jar at {jar} before starting", file=sys.stderr)
PY
}

if ! apply_java 2>/dev/null; then
  apply_shell
fi

echo "Done. Stop any running stack, then start with the chosen Bedrock path."
if [[ "$MODE" == "geyser-backup" ]]; then
  echo "Phone: host:19132 → Geyser-Standalone → Link :25565"
else
  echo "Phone: host:19132 → Link → chassis YapGeyserSession"
fi
