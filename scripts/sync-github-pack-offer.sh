#!/usr/bin/env bash
# Sync Folia login-prompt pack offer to GitHub Releases CDN + YaPPacks manifests.
# Usage: ./scripts/sync-github-pack-offer.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="yapcore-default.zip"
GH_URL="https://github.com/Xydroc-IO/YaPcore/releases/latest/download/${FILE}"

echo "Hashing GitHub release asset…"
SHA="$(python3 - <<PY
import hashlib, urllib.request
url="${GH_URL}"
req=urllib.request.Request(url, headers={"User-Agent":"YaPcore-ResourcePack/1.0"})
with urllib.request.urlopen(req, timeout=60) as r:
    h=hashlib.sha1()
    while True:
        b=r.read(1024*1024)
        if not b: break
        h.update(b)
    print(h.hexdigest())
PY
)"
ID="$(python3 - <<PY
import hashlib, uuid
sha="${SHA}"
fileName="${FILE}"
h=hashlib.md5(f"yapcore-pack:{fileName}:{sha}".encode()).digest()
b=bytearray(h); b[6]=(b[6]&0x0f)|0x30; b[8]=(b[8]&0x3f)|0x80
print(uuid.UUID(bytes=bytes(b)))
PY
)"
PROMPT_JSON='{"text":"This server offers a resource pack. Click Yes to download, or No to play without it."}'
PROMPT_PLAIN='This server offers a resource pack. Click Yes to download, or No to play without it.'

# Product config
CFG="$ROOT/config/server.properties"
sed -i "s|^resource-pack-url=.*|resource-pack-url=https://github.com/Xydroc-IO/YaPcore/releases/latest/download/{file}|" "$CFG"
grep -q '^resource-pack-sha1=' "$CFG" || echo 'resource-pack-sha1=' >> "$CFG"
sed -i "s|^resource-pack-sha1=.*|resource-pack-sha1=${SHA}|" "$CFG"

# Folia login prompt (Paper reads these at JVM start)
PROPS="$ROOT/folia-kernel/server.properties"
for key in resource-pack resource-pack-sha1 resource-pack-id resource-pack-prompt require-resource-pack; do
  grep -q "^${key}=" "$PROPS" || echo "${key}=" >> "$PROPS"
done
sed -i "s|^resource-pack=.*|resource-pack=${GH_URL}|" "$PROPS"
sed -i "s|^resource-pack-sha1=.*|resource-pack-sha1=${SHA}|" "$PROPS"
sed -i "s|^resource-pack-id=.*|resource-pack-id=${ID}|" "$PROPS"
sed -i "s|^resource-pack-prompt=.*|resource-pack-prompt=${PROMPT_JSON}|" "$PROPS"
sed -i "s|^require-resource-pack=.*|require-resource-pack=false|" "$PROPS"

MANIFEST=$(cat <<EOF
{
  "enabled": true,
  "forced": false,
  "prompt": "${PROMPT_PLAIN}",
  "packs": [
    {
      "file": "${FILE}",
      "url": "${GH_URL}",
      "sha1": "${SHA}",
      "uuid": "${ID}"
    }
  ]
}
EOF
)
for dest in \
  "$ROOT/plugins/YaPPacks/active.json" \
  "$ROOT/folia-kernel/plugins/YaPPacks/active.json"
do
  mkdir -p "$(dirname "$dest")"
  printf '%s\n' "$MANIFEST" > "$dest"
  echo "Wrote $dest"
done

echo "GitHub pack offer ready:"
echo "  url  $GH_URL"
echo "  sha1 $SHA"
echo "Restart YaPcore (Stop + Start in Control Panel) so Folia reloads server.properties."
grep -E '^resource-pack|^require-resource' "$PROPS"
