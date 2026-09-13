#!/usr/bin/env bash
# Prepare YaP Link modern-forwarding secret for YaPcore (NOT Velocity.jar).
# Product default is enabled (premium identity + skins via Link).
# Pass --disable for direct :25566 without Link.
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
SECRET_FILE="${VELOCITY_SECRET_FILE:-$ROOT/forwarding.secret}"
CFG="$ROOT/config/server.properties"
LINK_PROPS="$ROOT/link-data/link.properties"
ENABLE=1
for a in "$@"; do
  case "$a" in
    --enable) ENABLE=1 ;;
    --disable) ENABLE=0 ;;
    --help|-h)
      echo "Usage: $0 [--enable|--disable]"
      echo "  Creates forwarding.secret and wires config for YaP Link modern forwarding."
      echo "  Does NOT download or install Velocity.jar — product path is YaP Link only."
      echo "  --enable   (default) velocity-enabled=true — join via Link :25565"
      echo "             Link online-mode=true + Folia proxies.velocity.online-mode=true"
      echo "  --disable  velocity-enabled=false — direct chassis :25566 (no player_info)"
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

# Keep Link home in sync
mkdir -p "$ROOT/link-data"
if [ ! -f "$ROOT/link-data/forwarding.secret" ]; then
  cp -f "$SECRET_FILE" "$ROOT/link-data/forwarding.secret"
  chmod 600 "$ROOT/link-data/forwarding.secret"
  echo "Copied → link-data/forwarding.secret"
fi

python3 - <<PY
from pathlib import Path
import re
cfg = Path("$CFG")
link = Path("$LINK_PROPS")
secret = "forwarding.secret"
enable = $ENABLE
text = cfg.read_text() if cfg.exists() else ""
def setprop(t, k, v):
    if re.search(rf'^{re.escape(k)}=.*$', t, flags=re.M):
        return re.sub(rf'^{re.escape(k)}=.*$', f'{k}={v}', t, flags=re.M)
    return t + f'\n{k}={v}\n'
# Folia backend stays offline; Link authenticates (online-mode=true).
text = setprop(text, 'velocity-secret-file', secret)
text = setprop(text, 'velocity-online-mode', 'true')  # match YaP Link online-mode=true
text = setprop(text, 'velocity-bind-localhost', 'true')
text = setprop(text, 'online-mode', 'false')
text = setprop(text, 'game-authority', 'folia')
text = setprop(text, 'folia-embed', 'true')
text = setprop(text, 'velocity-enabled', 'true' if enable else 'false')
cfg.write_text(text)
print('Updated', cfg)
print('velocity-enabled=', 'true' if enable else 'false')
print('velocity-online-mode=true (Link premium auth)')
print('online-mode=false (Folia backend)')
if link.exists():
    lt = link.read_text()
    lt = setprop(lt, 'online-mode', 'true' if enable else 'false')
    lt = setprop(lt, 'player-info-forwarding-mode', 'modern')
    lt = setprop(lt, 'forwarding-secret-file', 'forwarding.secret')
    link.write_text(lt)
    print('Updated', link, 'online-mode=', 'true' if enable else 'false')
print('See docs/network/YAP_LINK.md (YaP Link — not Velocity.jar)')
PY

if [ "$ENABLE" -eq 1 ]; then
  echo "OK: modern forwarding ON — join YaP Link :25565 with a premium (Microsoft) launcher."
  echo "    Link online-mode=true → Mojang UUID; Folia proxies.velocity.online-mode=true on next start."
else
  echo "OK: modern forwarding OFF — direct :25566 allowed; no Velocity player_info from Link."
fi
