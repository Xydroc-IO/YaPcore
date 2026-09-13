#!/usr/bin/env bash
# Phase 4 — extract Bedrock exclusive/chemistry block textures into a JE resource pack
# (yap-bedrock-blocks) and generate block/item models + paper CMD 7001–7024 overrides.
#
# Usage:
#   scripts/parity/extract-block-textures.sh [path/to/resource_packs]
# Env:
#   YAP_BEDROCK_RESOURCE_PACKS — override source root (vanilla + chemistry packs)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BAND="${YAP_PARITY_BAND:-band_26_50}"
CATALOG="$ROOT/src/main/resources/protocol/bedrock/parity/$BAND/catalogs/blocks.v1.json"
PACK_OUT="$ROOT/resourcepacks/yap-bedrock-blocks"
TEX_OUT="$PACK_OUT/assets/yapbedrock/textures/block"
MODEL_BLOCK="$PACK_OUT/assets/yapbedrock/models/block"
MODEL_ITEM="$PACK_OUT/assets/yapbedrock/models/item"
MC_ITEM="$PACK_OUT/assets/minecraft/models/item"

RP_ROOT="${1:-${YAP_BEDROCK_RESOURCE_PACKS:-}}"
if [[ -z "$RP_ROOT" ]]; then
  CAND=$(find "$HOME/.local/share/bedrock-on-linux/games/release" -type d -path '*/data/resource_packs' 2>/dev/null | sort -V | tail -1 || true)
  RP_ROOT="$CAND"
fi
if [[ -z "$RP_ROOT" || ! -d "$RP_ROOT" ]]; then
  echo "No Bedrock resource_packs found. Pass path or set YAP_BEDROCK_RESOURCE_PACKS." >&2
  exit 1
fi
if [[ ! -f "$CATALOG" ]]; then
  echo "Missing catalog: $CATALOG" >&2
  exit 1
fi

echo "Source:  $RP_ROOT"
echo "Catalog: $CATALOG"
echo "Output:  $PACK_OUT"

mkdir -p "$TEX_OUT" "$MODEL_BLOCK" "$MODEL_ITEM" "$MC_ITEM"

python3 - "$RP_ROOT" "$CATALOG" "$PACK_OUT" <<'PY'
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

rp_root = Path(sys.argv[1])
catalog_path = Path(sys.argv[2])
pack_out = Path(sys.argv[3])
tex_out = pack_out / "assets/yapbedrock/textures/block"
model_block = pack_out / "assets/yapbedrock/models/block"
model_item = pack_out / "assets/yapbedrock/models/item"
mc_item = pack_out / "assets/minecraft/models/item"

catalog = json.loads(catalog_path.read_text())
entries = catalog["entries"]
names = [e["id"].split(":", 1)[1] for e in entries]
assert len(names) == 24, f"expected 24 catalog blocks, got {len(names)}"

# Prefer newest versioned packs, then unversioned chemistry/vanilla.
def pack_dirs() -> list[Path]:
    dirs: list[Path] = []
    for d in sorted(rp_root.iterdir()):
        if not d.is_dir():
            continue
        n = d.name
        if n.startswith("chemistry") or n.startswith("vanilla"):
            dirs.append(d)
    # sort: chemistry_* versioned last (highest), then chemistry, same for vanilla
    def key(p: Path):
        n = p.name
        kind = 0 if n.startswith("chemistry") else 1
        # unversioned base last among kind so versioned overlays win when we reverse
        if n in ("chemistry", "vanilla", "vanilla_base"):
            ver = (0,)
        else:
            parts = n.split("_")[1:]
            nums = []
            for part in parts:
                try:
                    nums.append(int(part))
                except ValueError:
                    nums.append(0)
            ver = tuple(nums) if nums else (0,)
        return (kind, ver)

    return sorted(dirs, key=key)


PACKS = pack_dirs()


def find_png(*rel_candidates: str) -> Path | None:
    """Prefer earlier candidates; within a candidate, prefer newest pack and blocks/ over items/."""
    for rel in rel_candidates:
        found: Path | None = None
        for pack in PACKS:
            for sub in ("textures/blocks", "textures/items", "textures/item"):
                p = pack / sub / rel
                if p.is_file() and p.suffix.lower() == ".png":
                    found = p
                    break  # blocks before items within the same pack
        if found is not None:
            return found
    return None


def copy_as(src: Path, dest_name: str) -> str:
    dest = tex_out / dest_name
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dest)
    return dest_name


def write_json(path: Path, obj: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def cube_all(tex: str) -> dict:
    return {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": f"yapbedrock:block/{tex}"},
    }


def cube_faces(particle: str, down: str, up: str, north: str, south: str, west: str, east: str) -> dict:
    def t(name: str) -> str:
        return f"yapbedrock:block/{name}"

    return {
        "parent": "minecraft:block/cube",
        "textures": {
            "particle": t(particle),
            "down": t(down),
            "up": t(up),
            "north": t(north),
            "south": t(south),
            "west": t(west),
            "east": t(east),
        },
    }


def torch_model(tex: str) -> dict:
    return {
        "parent": "minecraft:block/template_torch",
        "textures": {"torch": f"yapbedrock:block/{tex}"},
    }


def pane_model(tex: str) -> dict:
    # Simple full-height thin pane (inventory + BlockDisplay friendly).
    t = f"yapbedrock:block/{tex}"
    return {
        "ambientocclusion": False,
        "textures": {"pane": t, "particle": t},
        "elements": [
            {
                "from": [7, 0, 0],
                "to": [9, 16, 16],
                "faces": {
                    "north": {"uv": [7, 0, 9, 16], "texture": "#pane"},
                    "south": {"uv": [7, 0, 9, 16], "texture": "#pane"},
                    "west": {"uv": [0, 0, 16, 16], "texture": "#pane"},
                    "east": {"uv": [0, 0, 16, 16], "texture": "#pane"},
                    "up": {"uv": [7, 0, 9, 16], "texture": "#pane"},
                    "down": {"uv": [7, 0, 9, 16], "texture": "#pane"},
                },
            }
        ],
    }


def item_from_block(name: str) -> dict:
    return {"parent": f"yapbedrock:block/{name}"}


def item_generated(tex: str) -> dict:
    return {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"yapbedrock:block/{tex}"},
    }


found_report: list[dict] = []
missing_report: list[dict] = []
# short_name -> (block_model, item_model, primary texture note)
built: dict[str, tuple[dict, dict, str]] = {}


def record_found(name: str, role: str, src: Path, dest: str) -> None:
    found_report.append(
        {
            "block": name,
            "role": role,
            "dest": dest,
            "source": str(src.relative_to(rp_root)) if src.is_relative_to(rp_root) else str(src),
        }
    )


def record_missing(name: str, role: str, tried: list[str], note: str = "") -> None:
    missing_report.append({"block": name, "role": role, "tried": tried, "note": note})


def require_copy(name: str, role: str, dest: str, *candidates: str) -> Path | None:
    src = find_png(*candidates)
    if src is None:
        record_missing(name, role, list(candidates))
        return None
    copy_as(src, dest)
    record_found(name, role, src, dest)
    return src


# --- per-block extract + model ---

# allow / deny / border
if require_copy("allow", "all", "allow.png", "build_allow.png"):
    built["allow"] = (cube_all("allow"), item_from_block("allow"), "build_allow.png")
if require_copy("deny", "all", "deny.png", "build_deny.png"):
    built["deny"] = (cube_all("deny"), item_from_block("deny"), "build_deny.png")
if require_copy("border_block", "all", "border_block.png", "border.png"):
    built["border_block"] = (cube_all("border_block"), item_from_block("border_block"), "border.png")

# invisible_bedrock — transparent cube referencing vanilla glass (no solid-color invent)
# Prefer copying real glass into pack for offline display; model still uses glass semantics.
glass = find_png("glass.png")
if glass is not None:
    copy_as(glass, "invisible_bedrock.png")
    record_found("invisible_bedrock", "glass_fallback", glass, "invisible_bedrock.png")
    built["invisible_bedrock"] = (
        {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": "yapbedrock:block/invisible_bedrock"},
        },
        item_from_block("invisible_bedrock"),
        "glass.png (transparent cube)",
    )
else:
    # Absolute fallback: reference minecraft namespace glass texture without a local PNG.
    record_missing(
        "invisible_bedrock",
        "glass_fallback",
        ["glass.png"],
        "model will reference minecraft:block/glass",
    )
    built["invisible_bedrock"] = (
        {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": "minecraft:block/glass"},
        },
        item_from_block("invisible_bedrock"),
        "minecraft:block/glass",
    )

# camera — education faces
cam_ok = all(
    [
        require_copy("camera", "front", "camera_front.png", "camera_front.png"),
        require_copy("camera", "back", "camera_back.png", "camera_back.png"),
        require_copy("camera", "side", "camera_side.png", "camera_side.png"),
        require_copy("camera", "top", "camera_top.png", "camera_top.png"),
    ]
)
if cam_ok:
    built["camera"] = (
        cube_faces(
            "camera_front",
            "camera_top",
            "camera_top",
            "camera_front",
            "camera_back",
            "camera_side",
            "camera_side",
        ),
        item_from_block("camera"),
        "camera_* faces",
    )

# chalkboard — best education item texture
if require_copy(
    "chalkboard",
    "all",
    "chalkboard.png",
    "chalkboard_medium.png",
    "chalkboard_large.png",
    "chalkboard_small.png",
):
    built["chalkboard"] = (cube_all("chalkboard"), item_from_block("chalkboard"), "chalkboard_*.png")

# underwater torch
if require_copy("underwater_torch", "torch", "underwater_torch.png", "torch_underwater.png"):
    built["underwater_torch"] = (
        torch_model("underwater_torch"),
        item_generated("underwater_torch"),
        "torch_underwater.png",
    )

# underwater TNT
tnt_ok = all(
    [
        require_copy("underwater_tnt", "top", "underwater_tnt_top.png", "tnt_top_underwater.png"),
        require_copy("underwater_tnt", "bottom", "underwater_tnt_bottom.png", "tnt_bottom_underwater.png"),
        require_copy("underwater_tnt", "side", "underwater_tnt_side.png", "tnt_side_underwater.png"),
    ]
)
if tnt_ok:
    built["underwater_tnt"] = (
        cube_faces(
            "underwater_tnt_side",
            "underwater_tnt_bottom",
            "underwater_tnt_top",
            "underwater_tnt_side",
            "underwater_tnt_side",
            "underwater_tnt_side",
            "underwater_tnt_side",
        ),
        item_from_block("underwater_tnt"),
        "tnt_*_underwater",
    )

# colored torches
for color in ("red", "blue", "green", "purple"):
    short = f"colored_torch_{color}"
    dest = f"{short}.png"
    if require_copy(short, "torch", dest, f"torch_{color}.png"):
        built[short] = (torch_model(short), item_generated(short), f"torch_{color}.png")

# hard glass + pane
if require_copy("hard_glass", "all", "hard_glass.png", "hard_glass.png"):
    built["hard_glass"] = (cube_all("hard_glass"), item_from_block("hard_glass"), "hard_glass.png")
# pane reuses hard_glass texture (do not invent)
hg = tex_out / "hard_glass.png"
if hg.is_file():
    built["hard_glass_pane"] = (pane_model("hard_glass"), item_from_block("hard_glass_pane"), "hard_glass.png (shared)")
    found_report.append(
        {
            "block": "hard_glass_pane",
            "role": "pane",
            "dest": "hard_glass.png",
            "source": "(shared with hard_glass)",
        }
    )
else:
    record_missing("hard_glass_pane", "pane", ["hard_glass.png"], "needs hard_glass texture")

# frame / glow_frame
if require_copy("frame", "all", "frame.png", "itemframe_background.png"):
    built["frame"] = (cube_all("frame"), item_from_block("frame"), "itemframe_background.png")
if require_copy("glow_frame", "all", "glow_frame.png", "glow_item_frame.png"):
    built["glow_frame"] = (cube_all("glow_frame"), item_from_block("glow_frame"), "glow_item_frame.png")

# chemistry stations
# compound_creator
cc_ok = all(
    [
        require_copy("compound_creator", "top", "compound_creator_top.png", "compound_creator_top.png"),
        require_copy("compound_creator", "side_a", "compound_creator_side_a.png", "compound_creator_side_a.png"),
        require_copy("compound_creator", "side_b", "compound_creator_side_b.png", "compound_creator_side_b.png"),
        require_copy("compound_creator", "side_c", "compound_creator_side_c.png", "compound_creator_side_c.png"),
        require_copy(
            "compound_creator",
            "bottom",
            "compound_creator_bottom.png",
            "compound_creator_inside_bottom.png",
            "compound_creator_inside.png",
        ),
    ]
)
if cc_ok:
    built["compound_creator"] = (
        cube_faces(
            "compound_creator_side_a",
            "compound_creator_bottom",
            "compound_creator_top",
            "compound_creator_side_a",
            "compound_creator_side_b",
            "compound_creator_side_c",
            "compound_creator_side_a",
        ),
        item_from_block("compound_creator"),
        "compound_creator_* faces",
    )

# material_reducer
mr_ok = all(
    [
        require_copy("material_reducer", "top", "material_reducer_top.png", "material_reducer_top.png"),
        require_copy("material_reducer", "bottom", "material_reducer_bottom.png", "material_reducer_bottom.png"),
        require_copy("material_reducer", "front", "material_reducer_front.png", "material_reducer_front.png"),
        require_copy("material_reducer", "side", "material_reducer_side.png", "material_reducer_side.png"),
    ]
)
if mr_ok:
    built["material_reducer"] = (
        cube_faces(
            "material_reducer_front",
            "material_reducer_bottom",
            "material_reducer_top",
            "material_reducer_front",
            "material_reducer_side",
            "material_reducer_side",
            "material_reducer_side",
        ),
        item_from_block("material_reducer"),
        "material_reducer_* faces",
    )

# lab_table
lt_ok = all(
    [
        require_copy("lab_table", "top", "lab_table_top.png", "lab_table_top.png"),
        require_copy("lab_table", "bottom", "lab_table_bottom.png", "lab_table_bottom.png"),
        require_copy("lab_table", "front", "lab_table_front.png", "lab_table_front.png"),
        require_copy("lab_table", "side_a", "lab_table_side_a.png", "lab_table_side_a.png"),
        require_copy("lab_table", "side_b", "lab_table_side_b.png", "lab_table_side_b.png"),
        require_copy("lab_table", "side_c", "lab_table_side_c.png", "lab_table_side_c.png"),
    ]
)
if lt_ok:
    built["lab_table"] = (
        cube_faces(
            "lab_table_front",
            "lab_table_bottom",
            "lab_table_top",
            "lab_table_front",
            "lab_table_side_a",
            "lab_table_side_b",
            "lab_table_side_c",
        ),
        item_from_block("lab_table"),
        "lab_table_* faces",
    )

# element_constructor
ec_ok = all(
    [
        require_copy("element_constructor", "top", "element_constructor_top.png", "element_constructor_top.png"),
        require_copy("element_constructor", "front", "element_constructor_front.png", "element_constructor_front.png"),
        require_copy("element_constructor", "back", "element_constructor_back.png", "element_constructor_back.png"),
        require_copy("element_constructor", "side_a", "element_constructor_side_a.png", "element_constructor_side_a.png"),
        require_copy("element_constructor", "side_b", "element_constructor_side_b.png", "element_constructor_side_b.png"),
    ]
)
if ec_ok:
    # no dedicated bottom — reuse back/top nearest real texture
    bottom_src = find_png("element_constructor_back.png")
    if bottom_src:
        copy_as(bottom_src, "element_constructor_bottom.png")
        record_found("element_constructor", "bottom", bottom_src, "element_constructor_bottom.png")
    built["element_constructor"] = (
        cube_faces(
            "element_constructor_front",
            "element_constructor_bottom",
            "element_constructor_top",
            "element_constructor_front",
            "element_constructor_back",
            "element_constructor_side_a",
            "element_constructor_side_b",
        ),
        item_from_block("element_constructor"),
        "element_constructor_* faces",
    )

# light blocks — item textures (inventory icon); empty-ish cube for world display
for level in (0, 7, 15):
    short = f"light_block_{level}"
    dest = f"{short}.png"
    if require_copy(short, "item", dest, f"light_block_{level}.png"):
        built[short] = (
            {
                # Nearly empty cube using the light icon so BlockDisplay has a real texture.
                "parent": "minecraft:block/cube_all",
                "textures": {"all": f"yapbedrock:block/{short}"},
            },
            item_generated(short),
            f"light_block_{level}.png",
        )

# stonecutter_block — prefer modern stonecutter_* then stonecutter2_*
sc_top = require_copy(
    "stonecutter_block",
    "top",
    "stonecutter_block_top.png",
    "stonecutter_top.png",
    "stonecutter2_top.png",
)
sc_bottom = require_copy(
    "stonecutter_block",
    "bottom",
    "stonecutter_block_bottom.png",
    "stonecutter_bottom.png",
    "stonecutter2_bottom.png",
)
sc_side = require_copy(
    "stonecutter_block",
    "side",
    "stonecutter_block_side.png",
    "stonecutter_side.png",
    "stonecutter2_side.png",
)
sc_other = find_png("stonecutter_other_side.png", "stonecutter2_side.png", "stonecutter_side.png")
if sc_other and sc_side:
    copy_as(sc_other, "stonecutter_block_other_side.png")
    record_found("stonecutter_block", "other_side", sc_other, "stonecutter_block_other_side.png")
if sc_top and sc_bottom and sc_side:
    other = "stonecutter_block_other_side" if (tex_out / "stonecutter_block_other_side.png").is_file() else "stonecutter_block_side"
    built["stonecutter_block"] = (
        cube_faces(
            "stonecutter_block_side",
            "stonecutter_block_bottom",
            "stonecutter_block_top",
            "stonecutter_block_side",
            other,
            "stonecutter_block_side",
            other,
        ),
        item_from_block("stonecutter_block"),
        "stonecutter_* faces",
    )

# Write models for every catalog name (skip only if we truly have nothing)
cmd_entries = []
paper_overrides = []
for i, name in enumerate(names):
    cmd = 7001 + i
    bedrock_id = f"minecraft:{name}"
    if name not in built:
        missing_report.append(
            {
                "block": name,
                "role": "model",
                "tried": [],
                "note": "no usable Bedrock textures — model skipped",
            }
        )
        continue
    block_m, item_m, note = built[name]
    write_json(model_block / f"{name}.json", block_m)
    write_json(model_item / f"{name}.json", item_m)
    cmd_entries.append(
        {
            "cmd": cmd,
            "bedrock_id": bedrock_id,
            "short_name": name,
            "model": f"yapbedrock:item/{name}",
            "textures_note": note,
        }
    )
    paper_overrides.append(
        {"predicate": {"custom_model_data": cmd}, "model": f"yapbedrock:item/{name}"}
    )

write_json(
    pack_out / "pack.mcmeta",
    {
        "pack": {
            "description": "YaP Bedrock blocks — Phase 4 exclusive/chemistry textures (CMD 7001-7024)",
            "pack_format": 88,
            "min_format": [88, 0],
            "max_format": [88, 0],
        }
    },
)

write_json(
    pack_out / "CMD_MAP.json",
    {
        "host_item": "minecraft:paper",
        "cmd_start": 7001,
        "cmd_end": 7001 + len(names) - 1,
        "band": catalog.get("band", "band_26_50"),
        "catalog": str(catalog_path.name),
        "entries": cmd_entries,
    },
)

write_json(
    mc_item / "paper.json",
    {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "minecraft:item/paper"},
        "overrides": paper_overrides,
    },
)

report = {
    "source": str(rp_root),
    "pack": str(pack_out),
    "catalog_blocks": len(names),
    "models_written": len(cmd_entries),
    "textures_copied": len([f for f in found_report if f.get("source") != "(shared with hard_glass)"]),
    "found": found_report,
    "missing": missing_report,
}
write_json(pack_out / "EXTRACT_REPORT.json", report)

print("=== FOUND ===")
for f in found_report:
    print(f"  [{f['block']}] {f['role']}: {f['dest']} <- {f['source']}")
print(f"\n=== MISSING ({len(missing_report)}) ===")
if not missing_report:
    print("  (none)")
else:
    for m in missing_report:
        extra = f" — {m['note']}" if m.get("note") else ""
        tried = ", ".join(m.get("tried") or []) or "(n/a)"
        print(f"  [{m['block']}] {m['role']}: tried {tried}{extra}")
print(f"\nModels: {len(cmd_entries)}/{len(names)}  Textures copied: {report['textures_copied']}")
print(f"CMD map: {pack_out / 'CMD_MAP.json'}")
print(f"Report:  {pack_out / 'EXTRACT_REPORT.json'}")
PY
