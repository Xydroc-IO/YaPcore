#!/usr/bin/env bash
# Prepare Velocity modern-forwarding secret for YaPcore.
# Does NOT enable velocity by default (direct joins would break). Pass --enable to flip.
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
SECRET_FILE="${VELOCITY_SECRET_FILE:-$ROOT/forwarding.secret}"
CFG="$ROOT/config/server.properties"
ENABLE=0
for a in "$@"; do
  case "$a" in
    --enable) ENABLE=1 ;;
    --help|-h)
      echo "Usage: $0 [--enable]"
      echo "  Creates forwarding.secret and sets velocity-secret-file in config."
      echo "  --enable  also sets velocity-enabled=true (only when Velocity is in front)."
      exit 0
      ;;
  esac
done

if [ ! -f "$SECRET_FILE" ]; then
  openssl rand -base64 32 | tr -d '\n' > "$SECRET_FILE"
  chmod 600 "$SECRET_FILE"
  echo "Created $SECRET_FILE"
else
  echo "Keeping existing $SECRET_FILE"
fi

python3 - <<PY
from pathlib import Path
import re
cfg = Path("$CFG")
secret = "forwarding.secret"
enable = $ENABLE
text = cfg.read_text() if cfg.exists() else ""
def setprop(t, k, v):
    if re.search(rf'^{re.escape(k)}=.*$', t, flags=re.M):
        return re.sub(rf'^{re.escape(k)}=.*$', f'{k}={v}', t, flags=re.M)
    return t + f'\n{k}={v}\n'
text = setprop(text, 'velocity-secret-file', secret)
text = setprop(text, 'velocity-online-mode', 'false')  # match YaP Link default offline
text = setprop(text, 'velocity-bind-localhost', 'true')
text = setprop(text, 'online-mode', 'false')
# Prefer Folia product path when enabling proxy
if enable:
    text = setprop(text, 'game-authority', 'folia')
    text = setprop(text, 'folia-embed', 'true')
    text = setprop(text, 'velocity-enabled', 'true')
else:
    # leave enabled as-is unless missing
    if 'velocity-enabled=' not in text:
        text = setprop(text, 'velocity-enabled', 'false')
cfg.write_text(text)
print('Updated', cfg)
print('velocity-enabled=', 'true' if enable else 'unchanged/false')
print('Copy the same file to YaP Link / Velocity as forwarding.secret')
print('See docs/VELOCITY.md, docs/YAP_LINK.md, and examples/velocity/')
PY

if [ "$ENABLE" -eq 1 ]; then
  echo "WARNING: velocity-enabled=true — Folia/Paper will bind localhost; players must join via YaP Link or Velocity."
fi
