#!/usr/bin/env bash
# Build resourcepacks/yapcore-default.mcpack
# Product Bedrock default: Faithful 64x Bedrock + YaP Skies / Water / Foliage overlays.
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
PACKS="$ROOT/resourcepacks"
OUT="$PACKS/yapcore-default.mcpack"
FAITHFUL="$PACKS/faithful-64x-bedrock.mcpack"
SKIES_DIR="$PACKS/yap-skies"
# Stable UUIDs (uuid5 of yapcore pack URLs) — must match what we offer on login.
HEADER_UUID="343c58a5-4df6-5360-a48e-eb2a4a78fc55"
MODULE_UUID="e1d0269a-1cfc-5248-9c3f-31c0f223c280"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

if [ ! -f "$FAITHFUL" ]; then
  echo "Missing $FAITHFUL — fetching…"
  bash "$ROOT/scripts/fetch-faithful-64x-bedrock.sh"
fi

# Ensure JE overlays exist (same generators as Java default pack).
if [ ! -f "$SKIES_DIR/assets/minecraft/textures/environment/celestial/sun.png" ]; then
  python3 "$ROOT/scripts/generate-yap-skies.py"
fi
python3 "$ROOT/scripts/generate-yap-water.py"
python3 "$ROOT/scripts/generate-yap-foliage.py"

echo "Unpacking Faithful Bedrock…"
unzip -q -o "$FAITHFUL" -d "$STAGE"

python3 - <<'PY' "$STAGE" "$SKIES_DIR" "$HEADER_UUID" "$MODULE_UUID"
import json, sys
from pathlib import Path

try:
    from PIL import Image
except ImportError as e:
    raise SystemExit("Pillow required: pip install Pillow") from e

stage = Path(sys.argv[1])
skies = Path(sys.argv[2])
header_uuid = sys.argv[3]
module_uuid = sys.argv[4]
je = skies / "assets/minecraft/textures"

def save_png(src: Path, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if src.resolve() != dest.resolve():
        dest.write_bytes(src.read_bytes())

def save_tga(src: Path, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    img = Image.open(src).convert("RGBA")
    img.save(dest, format="TGA")

# Water — BE Faithful uses *_grey + classic flow.
water_map = [
    ("block/water_still.png", [
        "textures/blocks/water_still.png",
        "textures/blocks/water_still_grey.png",
    ]),
    ("block/water_flow.png", [
        "textures/blocks/water_flow.png",
        "textures/blocks/water_flow_grey.png",
    ]),
]
for rel, dests in water_map:
    src = je / rel
    if not src.is_file():
        continue
    for d in dests:
        save_png(src, stage / d)

# Environment
env = [
    ("environment/celestial/sun.png", "textures/environment/sun.png"),
    ("environment/moon_phases.png", "textures/environment/moon_phases.png"),
    ("environment/clouds.png", "textures/environment/clouds.png"),
]
for rel, dest in env:
    src = je / rel
    if src.is_file():
        save_png(src, stage / dest)

# Leaves — Faithful BE stores many as .tga (+ optional .png)
leaf_map = {
    "block/oak_leaves.png": ["textures/blocks/leaves_oak"],
    "block/spruce_leaves.png": ["textures/blocks/leaves_spruce"],
    "block/birch_leaves.png": ["textures/blocks/leaves_birch"],
    "block/jungle_leaves.png": ["textures/blocks/leaves_jungle"],
    "block/acacia_leaves.png": ["textures/blocks/leaves_acacia"],
    "block/dark_oak_leaves.png": ["textures/blocks/leaves_big_oak"],
    "block/mangrove_leaves.png": ["textures/blocks/mangrove_leaves"],
    "block/azalea_leaves.png": ["textures/blocks/azalea_leaves"],
    "block/cherry_leaves.png": ["textures/blocks/cherry_leaves"],
    "block/pale_oak_leaves.png": ["textures/blocks/pale_oak_leaves"],
}
for rel, bases in leaf_map.items():
    src = je / rel
    if not src.is_file():
        continue
    for base in bases:
        tga = stage / f"{base}.tga"
        png = stage / f"{base}.png"
        if tga.exists() or not png.exists():
            save_tga(src, tga)
        if png.exists() or not tga.exists():
            save_png(src, png)

manifest_path = stage / "manifest.json"
data = json.loads(manifest_path.read_text(encoding="utf-8"))
desc = "YaPcore default — Faithful 64x Bedrock + YaP Skies + Water + Foliage"
header = data.setdefault("header", {})
header["name"] = "YaPcore Default (Bedrock)"
header["description"] = desc
header["uuid"] = header_uuid
header["version"] = [1, 0, 0]
# Pin engine floor to current product BE pin (1.21.60). Faithful upstream may ship
# min_engine_version 1.26.x which rejects every 1.21 client during pack download.
header["min_engine_version"] = [1, 21, 60]
# Drop PBR capability — cracked / non-RTX clients often fail the pack prompt.
data.pop("capabilities", None)
modules = data.get("modules") or []
if modules:
    modules[0]["description"] = desc
    modules[0]["uuid"] = module_uuid
    modules[0]["version"] = [1, 0, 0]
meta = data.setdefault("metadata", {})
authors = meta.get("authors") or []
if "YaPcore" not in authors:
    authors = list(authors) + ["YaPcore"]
meta["authors"] = authors
meta["license"] = "https://faithfulpack.net/license (Faithful base); YaP overlays GPLv3"
manifest_path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
print("Wrote manifest", header_uuid)
PY

rm -f "$OUT"
(cd "$STAGE" && zip -qr "$OUT" .)
echo "OK $OUT ($(du -h "$OUT" | awk '{print $1}'))"
echo "Upload: gh release upload <tag> resourcepacks/yapcore-default.mcpack --clobber -R Xydroc-IO/YaPcore"
