#!/usr/bin/env bash
# Build resourcepacks/yapcore-default.mcpack
# Product Bedrock default: Faithful 64x Bedrock + YaP Skies / Water / Foliage overlays.
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
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
  bash "$ROOT/scripts/packs/fetch-faithful-64x-bedrock.sh"
fi

# Ensure JE overlays exist (same generators as Java default pack).
if [ ! -f "$SKIES_DIR/assets/minecraft/textures/environment/celestial/sun.png" ]; then
  python3 "$ROOT/scripts/packs/generate-yap-skies.py"
fi
python3 "$ROOT/scripts/packs/generate-yap-water.py"
python3 "$ROOT/scripts/packs/generate-yap-foliage.py"
python3 "$ROOT/scripts/packs/generate-yap420-plants.py"
# Portal stained-glass overlays (YaPPortals) — JE pack has them; BE Faithful does not.
if [ ! -f "$PACKS/yap-portals/assets/minecraft/textures/block/purple_stained_glass.png" ]; then
  python3 "$ROOT/scripts/packs/generate-yap-portals.py"
fi

echo "Unpacking Faithful Bedrock…"
unzip -q -o "$FAITHFUL" -d "$STAGE"

python3 - <<'PY' "$STAGE" "$SKIES_DIR" "$HEADER_UUID" "$MODULE_UUID" "$PACKS/yap-portals"
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
portals = Path(sys.argv[5])
je = skies / "assets/minecraft/textures"
portal_je = portals / "assets/minecraft/textures/block"

def save_png(src: Path, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if src.resolve() != dest.resolve():
        dest.write_bytes(src.read_bytes())

def save_tga(src: Path, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    img = Image.open(src).convert("RGBA")
    img.save(dest, format="TGA")

def save_first_frame(src: Path, dest: Path, size: int = 64) -> None:
    """YaP portal sheets are vertical flipbooks; Bedrock glass_* are single frames."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    img = Image.open(src).convert("RGBA")
    w, h = img.size
    if h > w:
        img = img.crop((0, 0, w, min(h, size if w == size else w)))
        if img.size[0] != size or img.size[1] != size:
            img = img.resize((size, size), Image.Resampling.NEAREST)
    img.save(dest, format="PNG", optimize=True)

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

# YaPPortals — JE dye names → Bedrock glass_* / glass_pane_top_* (light_gray → silver).
_dye_to_be = {
    "white": "white",
    "orange": "orange",
    "magenta": "magenta",
    "light_blue": "light_blue",
    "yellow": "yellow",
    "lime": "lime",
    "pink": "pink",
    "gray": "gray",
    "light_gray": "silver",
    "cyan": "cyan",
    "purple": "purple",
    "blue": "blue",
    "brown": "brown",
    "green": "green",
    "red": "red",
    "black": "black",
}
portal_n = 0
for dye, be in _dye_to_be.items():
    glass = portal_je / f"{dye}_stained_glass.png"
    pane_top = portal_je / f"{dye}_stained_glass_pane_top.png"
    if glass.is_file():
        save_first_frame(glass, stage / f"textures/blocks/glass_{be}.png")
        portal_n += 1
    if pane_top.is_file():
        save_first_frame(pane_top, stage / f"textures/blocks/glass_pane_top_{be}.png")
        portal_n += 1
print("YaP portal glass overlays", portal_n)

manifest_path = stage / "manifest.json"
data = json.loads(manifest_path.read_text(encoding="utf-8"))
desc = "YaPcore default — Faithful 64x Bedrock + YaP Skies + Water + Foliage + Portals"
header = data.setdefault("header", {})
header["name"] = "YaPcore Default (Bedrock)"
header["description"] = desc
header["uuid"] = header_uuid
header["version"] = [1, 0, 3]
# Pin engine floor to current product BE pin (1.21.60). Faithful upstream may ship
# min_engine_version 1.26.x which rejects every 1.21 client during pack download.
header["min_engine_version"] = [1, 21, 60]
# Drop PBR capability — cracked / non-RTX clients often fail the pack prompt.
data.pop("capabilities", None)
modules = data.get("modules") or []
if modules:
    modules[0]["description"] = desc
    modules[0]["uuid"] = module_uuid
    modules[0]["version"] = [1, 0, 3]
meta = data.setdefault("metadata", {})
authors = meta.get("authors") or []
if "YaPcore" not in authors:
    authors = list(authors) + ["YaPcore"]
meta["authors"] = authors
meta["license"] = "https://faithfulpack.net/license (Faithful base); YaP overlays GPLv3"
manifest_path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
print("Wrote manifest", header_uuid)

# Drop PBR texture sets. The manifest no longer declares pbr, and leftover
# texture_set + *_mers files make clear glass render as missing on non-RTX clients.
stripped = 0
for path in list(stage.rglob("*")):
    name = path.name.lower()
    if name.endswith(".texture_set.json") or name.endswith("_mers.tga") or name.endswith("_mers.png"):
        path.unlink()
        stripped += 1
print("Stripped PBR texture overrides", stripped)
PY

rm -f "$OUT"
(cd "$STAGE" && zip -qr "$OUT" .)
echo "OK $OUT ($(du -h "$OUT" | awk '{print $1}'))"
echo "Upload: gh release upload <tag> resourcepacks/yapcore-default.mcpack --clobber -R Xydroc-IO/YaPcore"
