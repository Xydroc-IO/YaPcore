#!/usr/bin/env bash
# Fetch official Sodium Fabric jar (PolyForm Shield — unmodified redistribute only).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/dist/client-mods"
mkdir -p "$OUT"

VERSION_ID="2Yom1N68"
JAR_NAME="sodium-fabric-0.9.1+mc26.2.jar"
URL="https://cdn.modrinth.com/data/AANobbMI/versions/${VERSION_ID}/${JAR_NAME}"

DEST="${OUT}/${JAR_NAME}"
if [[ -f "$DEST" ]]; then
  echo "Already present: $DEST"
else
  echo "Downloading Sodium ${JAR_NAME}..."
  curl -fsSL "$URL" -o "$DEST"
fi

# Notices for redistribution
cp -f "${ROOT}/client/yap-sodium/LICENSE-PolyForm-Shield.txt" "${OUT}/LICENSE-Sodium-PolyForm-Shield.txt"
cp -f "${ROOT}/client/yap-sodium/NOTICE.txt" "${OUT}/NOTICE-Sodium.txt"
echo "Sodium → $DEST"
ls -lh "$DEST"
