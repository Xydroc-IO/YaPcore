#!/usr/bin/env bash
# Build resourcepacks/yapcore-default.zip = Faithful 64x (if present) + YaP Vehicles overlay.
# Clients need this pack for HD vehicle models / upgrade icons.
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
PACKS="$ROOT/resourcepacks"
OUT="$PACKS/yapcore-default.zip"
VEH_DIR="$PACKS/yap-vehicles"
VEH_ZIP="$PACKS/yap-vehicles.zip"
FAITHFUL="$PACKS/faithful-64x.zip"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

# Ensure yap-vehicles.zip exists from folder
if [ -d "$VEH_DIR" ]; then
  (cd "$VEH_DIR" && zip -qr "$VEH_ZIP" .)
fi
if [ ! -f "$VEH_ZIP" ]; then
  echo "ERROR: missing $VEH_ZIP (and no yap-vehicles/ folder)" >&2
  exit 1
fi

# Base: Faithful if available, else empty pack
if [ -f "$FAITHFUL" ]; then
  unzip -q -o "$FAITHFUL" -d "$STAGE"
else
  mkdir -p "$STAGE"
  printf '%s\n' '{"pack":{"pack_format":34,"description":"YaPcore default client pack"}}' \
    >"$STAGE/pack.mcmeta"
fi

# Overlay vehicles (textures, models, etc.)
unzip -q -o "$VEH_ZIP" -d "$STAGE"

# Merge paper.json overrides if Faithful also shipped one
python3 - <<'PY' "$STAGE"
import json, sys
from pathlib import Path
stage = Path(sys.argv[1])
paper = stage / "assets/minecraft/models/item/paper.json"
# After unzip overlay, paper.json is vehicles-only. Re-merge from backup if needed.
# Vehicles zip wins for structure; ensure overrides sorted by CMD.
if paper.exists():
    data = json.loads(paper.read_text())
    overs = data.get("overrides") or []
    overs.sort(key=lambda o: o.get("predicate", {}).get("custom_model_data", 0))
    data["overrides"] = overs
    if "textures" not in data:
        data["textures"] = {"layer0": "item/paper"}
    if "parent" not in data:
        data["parent"] = "item/generated"
    paper.write_text(json.dumps(data, indent=2) + "\n")

meta = stage / "pack.mcmeta"
meta.write_text(json.dumps({
    "pack": {
        "pack_format": 34,
        "supported_formats": {"min_inclusive": 22, "max_inclusive": 99},
        "description": "YaPcore default — Faithful 64x + YaP Vehicles (HD models & parts)"
    }
}, indent=2) + "\n")
PY

rm -f "$OUT"
(cd "$STAGE" && zip -qr "$OUT" .)
echo "Wrote $OUT ($(du -h "$OUT" | awk '{print $1}'))"

# Mirror into nginx docroot for Cloudflare :80 /pack/ (when available)
if [ -x "$ROOT/scripts/publish-resourcepack-www.sh" ]; then
  "$ROOT/scripts/publish-resourcepack-www.sh" "$OUT" || true
fi
