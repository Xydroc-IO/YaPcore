#!/usr/bin/env bash
# Apply public production keys to config/server.properties (never overwrites unrelated keys).
# Usage: ./scripts/apply-production-profile.sh [--with-link]
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
CFG="$ROOT/config/server.properties"
PROFILE="$ROOT/config/defaults/server.properties.production"
WITH_LINK=0
for a in "$@"; do
  case "$a" in
    --with-link) WITH_LINK=1 ;;
    -h|--help)
      echo "Usage: $0 [--with-link]"
      echo "  Applies production profile from config/defaults/server.properties.production"
      echo "  --with-link  sets velocity forwarding (online-mode=false, keeps forwarding.secret)"
      exit 0
      ;;
  esac
done

if [ ! -f "$CFG" ]; then
  if [ -f "$ROOT/config/server.properties.example" ]; then
    cp "$ROOT/config/server.properties.example" "$CFG"
  else
    echo "Missing $CFG — copy config/server.properties.example first." >&2
    exit 1
  fi
fi

python3 - <<'PY' "$CFG" "$PROFILE" "$WITH_LINK"
import re, sys
from pathlib import Path
cfg_path, profile_path, with_link = Path(sys.argv[1]), Path(sys.argv[2]), int(sys.argv[3])
text = cfg_path.read_text()
profile = {}
for line in profile_path.read_text().splitlines():
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    if "=" in line:
        k, v = line.split("=", 1)
        profile[k.strip()] = v.strip()

def setprop(t, k, v):
    if re.search(rf"^{re.escape(k)}=.*$", t, flags=re.M):
        return re.sub(rf"^{re.escape(k)}=.*$", f"{k}={v}", t, flags=re.M)
    return t + f"\n{k}={v}\n"

for k, v in profile.items():
    text = setprop(text, k, v)

if with_link:
    text = setprop(text, "online-mode", "false")
    text = setprop(text, "velocity-enabled", "true")
    text = setprop(text, "velocity-secret-file", "forwarding.secret")
    text = setprop(text, "velocity-online-mode", "false")
else:
    text = setprop(text, "online-mode", "true")
    text = setprop(text, "velocity-enabled", "false")

cfg_path.write_text(text)
print(f"Applied production profile → {cfg_path}")
if with_link:
    print("Link mode: online-mode=false, velocity-enabled=true")
else:
    print("Direct mode: online-mode=true, velocity-enabled=false")
PY
