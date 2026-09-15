#!/usr/bin/env bash
# Bedrock-feel Phase 6 matrix smoke — Green only if convert-verified + client packs present.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

BAND="${1:-band_26_50}"
FAIL=0

echo "==> Parity golden tests (chassis)"
if command -v gradle >/dev/null 2>&1; then
  gradle --no-daemon :test --tests 'com.yapcore.crossplay.bedrock.parity.*' || FAIL=1
else
  echo "WARN: gradle not on PATH — skip chassis tests" >&2
fi

echo "==> Tailor UI codec + catalog tests"
if command -v gradle >/dev/null 2>&1; then
  gradle --no-daemon :tailor-plugin:test --tests 'com.yapcore.tailor.PresenceUiCodecTest' \
    --tests 'com.yapcore.tailor.TailorEmoteCatalogTest' || FAIL=1
  gradle --no-daemon :bedrock-blocks-plugin:test || FAIL=1
fi

echo "==> Provenance + catalogs for $BAND"
MANIFEST="src/main/resources/protocol/bedrock/parity/${BAND}/provenance/manifest.v1.json"
BLOCKS="src/main/resources/protocol/bedrock/parity/${BAND}/catalogs/blocks.v1.json"
EMOTES="src/main/resources/protocol/bedrock/parity/${BAND}/catalogs/emotes.v1.json"
MOVEMENT="src/main/resources/protocol/bedrock/parity/${BAND}/catalogs/movement.v1.json"
for f in "$MANIFEST" "$BLOCKS" "$EMOTES" "$MOVEMENT"; do
  if [[ ! -f "$f" ]]; then
    echo "MISSING $f" >&2
    FAIL=1
  else
    echo "OK $f"
  fi
done

YAPBLOCKS=$(find "src/main/resources/protocol/bedrock/parity/${BAND}/converted/block" -name '*.yapblock.json' 2>/dev/null | wc -l | tr -d ' ')
if [[ "$YAPBLOCKS" -lt 24 ]]; then
  echo "FAIL: expected ≥24 yapblock, got $YAPBLOCKS" >&2
  FAIL=1
else
  echo "OK yapblock count=$YAPBLOCKS"
fi

echo "==> Resource pack extract"
PACK="resourcepacks/yap-bedrock-blocks"
if [[ ! -f "$PACK/EXTRACT_REPORT.json" ]]; then
  echo "MISSING $PACK/EXTRACT_REPORT.json" >&2
  FAIL=1
else
  python3 - <<PY || FAIL=1
import json,sys
r=json.load(open("$PACK/EXTRACT_REPORT.json"))
assert r.get("catalog_blocks")==24, r
assert r.get("models_written")==24, r
assert r.get("textures_copied",0)>=24, r
assert not r.get("missing"), r.get("missing")
print("OK extract report")
PY
fi

echo "==> Client mods zip"
ZIP="dist/client-mods/client_mods.zip"
if [[ ! -f "$ZIP" ]]; then
  echo "MISSING $ZIP — run ./scripts/build-yap-client-render.sh" >&2
  FAIL=1
else
  python3 - <<PY || FAIL=1
import zipfile,sys
z=zipfile.ZipFile("$ZIP")
names=z.namelist()
need=["yap-presence","yap-blocks","yap-visuals","yap-bag","yap-staff","yap-ultrawide","INSTALL.txt"]
missing=[n for n in need if not any(n in x for x in names)]
if missing:
    print("FAIL client_mods.zip missing:", missing, file=sys.stderr)
    sys.exit(1)
print("OK client_mods.zip entries=", len(names))
PY
fi

echo "==> Parity doc claims Phase 5–6 Green"
PARITY="docs/product/BEDROCK_FEEL_PARITY.md"
if ! rg -q 'JE UX \| Presence UI.*5 — Green' "$PARITY"; then
  echo "FAIL: Phase 5 JE UX not Green in parity doc" >&2
  FAIL=1
fi
if ! rg -q 'Release \| client_mods.*6 — Green' "$PARITY"; then
  echo "FAIL: Phase 6 release not Green in parity doc" >&2
  FAIL=1
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo "SMOKE FAILED" >&2
  exit 1
fi
echo "SMOKE OK — band=$BAND"
