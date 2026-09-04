#!/usr/bin/env python3
"""
Import free Automobility (MIT) automobile frames into yap-vehicles.

Converts JsonEM bone/cuboid models → Minecraft Java item models for ItemDisplay,
bakes wheels from Automobility wheel models, and rewrites the fleet textures.

Source: https://github.com/FoundationGames/Automobility (MIT License)
"""

from __future__ import annotations

import json
import math
import shutil
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepacks" / "yap-vehicles"
AUTO_ROOT = Path("/tmp/yap-car-models/Automobility/common/src/main/resources/assets/automobility")
AUTO_MODELS = AUTO_ROOT / "models" / "entity" / "automobile"
AUTO_TEX = AUTO_ROOT / "textures" / "entity" / "automobile"
AUTO_DATA = Path(
    "/tmp/yap-car-models/Automobility/neoforge/src/generated/resources"
    "/data/automobility/automobility/automobile_frame"
)

# YaP type id → (frame model folder, texture file stem, datapack frame id for wheel layout)
FLEET: dict[str, tuple[str, str, str]] = {
    "chassis": ("shopping_cart", "shopping_cart", "shopping_cart"),
    "buggy": ("standard", "standard_orange", "standard_orange"),
    "truck_4x4": ("tractor", "green_tractor", "green_tractor"),
    "monster_truck": ("tractor", "red_tractor", "red_tractor"),
    "sport_car": ("standard", "standard_red", "standard_red"),
    "hypercar": ("c_arr", "c_arr", "c_arr"),
    "lambo": ("standard", "standard_lime", "standard_lime"),
    "ferrari": ("standard", "standard_magenta", "standard_magenta"),
    "mclaren": ("motorcar", "copper_motorcar", "copper_motorcar"),
    "porsche": ("motorcar", "steel_motorcar", "steel_motorcar"),
    "hoverbike": ("rickshaw", "amethyst_rickshaw", "amethyst_rickshaw"),
}

DISPLAY = {
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -2, 0], "scale": [1.15, 1.15, 1.15]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "gui": {"rotation": [25, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {
        "rotation": [75, 180, 0],
        "translation": [0, 2.5, 0],
        "scale": [0.28, 0.28, 0.28],
    },
}


def mat_mul(a: list[list[float]], b: list[list[float]]) -> list[list[float]]:
    return [
        [sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)]
        for i in range(3)
    ]


def mat_vec(m: list[list[float]], v: tuple[float, float, float]) -> tuple[float, float, float]:
    return (
        m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
        m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
        m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2],
    )


def rot_matrix(rx: float, ry: float, rz: float) -> list[list[float]]:
    """JsonEM / Blockbench: radians, applied ZYX-ish (X then Y then Z)."""
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    rxm = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    rym = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rzm = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mat_mul(rzm, mat_mul(rym, rxm))


def box_faces(u: float, v: float, sx: float, sy: float, sz: float, tw: int, th: int) -> dict:
    """Entity-style UV unwrap from textureOffset → item-model faces (0–16 UV space scaled to tex)."""
    w, h, d = max(sx, 0.01), max(sy, 0.01), max(sz, 0.01)

    def face(u0, v0, u1, v1):
        # Minecraft item models expect UV in texture pixel coords when texture is not 16×16…
        # Actually item model UV is in 0–16 relative to texture width via atlas;
        # for custom textures, UV is in texture pixels (Java edition uses pixel UVs).
        return {"uv": [u0, v0, u1, v1], "texture": "#0"}

    # Classic MC entity box layout
    return {
        "down": face(u + d, v, u + d + w, v + d),
        "up": face(u + d + w, v + d, u + d + w + w, v),  # flip V for up
        "west": face(u, v + d, u + d, v + d + h),
        "north": face(u + d, v + d, u + d + w, v + d + h),
        "east": face(u + d + w, v + d, u + d + w + d, v + d + h),
        "south": face(u + d + w + d, v + d, u + d + w + d + w, v + d + h),
    }


def collect_cuboids(model: dict) -> list[tuple[tuple[float, float, float], tuple[float, float, float], list[float], list[list[float]]]]:
    """Walk bones + nested children; accumulate parent transforms."""
    out: list = []
    identity = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]

    def walk(bones: dict, parent_origin: tuple[float, float, float], parent_rm: list[list[float]]) -> None:
        for _name, bone in bones.items():
            t = bone.get("transform") or {}
            local_origin = tuple(float(x) for x in (t.get("origin") or [0, 0, 0]))
            rot = t.get("rotation") or [0, 0, 0]
            local_rm = rot_matrix(float(rot[0]), float(rot[1]), float(rot[2]))
            # world rotation = parent * local
            rm = mat_mul(parent_rm, local_rm)
            # world origin = parent_origin + parent_rm * local_origin
            ro = mat_vec(parent_rm, local_origin)
            origin = (parent_origin[0] + ro[0], parent_origin[1] + ro[1], parent_origin[2] + ro[2])

            for c in bone.get("cuboids", []):
                off = tuple(float(x) for x in c["offset"])
                dim = tuple(float(x) for x in c["dimensions"])
                uv = [float(x) for x in c.get("uv", [0, 0])]
                corners = []
                for dx in (0.0, dim[0]):
                    for dy in (0.0, dim[1]):
                        for dz in (0.0, dim[2]):
                            corners.append(mat_vec(rm, (off[0] + dx, off[1] + dy, off[2] + dz)))
                xs = [p[0] for p in corners]
                ys = [p[1] for p in corners]
                zs = [p[2] for p in corners]
                size = (max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs))
                world_from = (origin[0] + min(xs), origin[1] + min(ys), origin[2] + min(zs))
                out.append((world_from, size, uv, rm))

            children = bone.get("children")
            if isinstance(children, dict) and children:
                walk(children, origin, rm)

    walk(model.get("bones", {}), (0.0, 0.0, 0.0), identity)
    return out


def to_elements(cuboids, tex_w: int, tex_h: int, uv_v_offset: float = 0.0) -> list[dict]:
    elements = []
    for (fx, fy, fz), (sx, sy, sz), uv, _rm in cuboids:
        # Shift so model sits roughly in item space centered on XZ, Y>=0
        u, v = uv[0], uv[1] + uv_v_offset
        faces = box_faces(u, v, sx, sy, sz, tex_w, tex_h)
        elements.append(
            {
                "from": [round(fx, 3), round(fy, 3), round(fz, 3)],
                "to": [round(fx + sx, 3), round(fy + sy, 3), round(fz + sz, 3)],
                "faces": faces,
            }
        )
    return elements


def load_wheel_cuboids() -> list:
    wheel = json.loads((AUTO_MODELS / "wheel" / "standard" / "main.json").read_text())
    return collect_cuboids(wheel)


def wheel_positions(frame_id: str) -> list[tuple[float, float, float, float]]:
    path = AUTO_DATA / f"{frame_id}.json"
    if not path.exists():
        return [(-5.0, 0.0, 13.0, 1.0), (5.0, 0.0, 13.0, 1.0), (-5.0, 0.0, -13.0, 1.0), (5.0, 0.0, -13.0, 1.0)]
    data = json.loads(path.read_text())
    wheels = data.get("display", {}).get("wheels", [])
    pos: list[tuple[float, float, float, float]] = []
    for w in wheels:
        # Automobility: right = +X, forward = +Z (vehicle forward)
        right = float(w.get("right", 0))
        forward = float(w.get("forward", 0))
        scale = float(w.get("scale", 1.0))
        pos.append((right, 0.0, forward, scale))
    return pos or [(-5.0, 0.0, 13.0, 1.0), (5.0, 0.0, 13.0, 1.0), (-5.0, 0.0, -13.0, 1.0), (5.0, 0.0, -13.0, 1.0)]


def bake_wheels(elements: list, frame_id: str, wheel_cuboids: list, wheel_uv_v: float, tex_w: int, tex_h: int) -> None:
    positions = wheel_positions(frame_id)
    for rx, ry, rz, scale in positions:
        for (fx, fy, fz), (sx, sy, sz), uv, _ in wheel_cuboids:
            # wheel model is local; place at axle
            wx = rx + fx * scale
            wy = ry + fy * scale
            wz = rz + fz * scale
            ssx, ssy, ssz = sx * scale, sy * scale, sz * scale
            u, v = uv[0], uv[1] + wheel_uv_v
            faces = box_faces(u, v, ssx, ssy, ssz, tex_w, tex_h)
            elements.append(
                {
                    "from": [round(wx, 3), round(wy, 3), round(wz, 3)],
                    "to": [round(wx + ssx, 3), round(wy + ssy, 3), round(wz + ssz, 3)],
                    "faces": faces,
                }
            )


def normalize_elements(elements: list[dict]) -> list[dict]:
    """Translate so min Y = 0 and center XZ around 8,8 for nicer ItemDisplay pivot."""
    if not elements:
        return elements
    mins = [min(e["from"][i] for e in elements) for i in range(3)]
    maxs = [max(e["to"][i] for e in elements) for i in range(3)]
    cx = (mins[0] + maxs[0]) / 2
    cz = (mins[2] + maxs[2]) / 2
    out = []
    for e in elements:
        fr = e["from"]
        to = e["to"]
        out.append(
            {
                **e,
                "from": [fr[0] - cx + 8, fr[1] - mins[1], fr[2] - cz + 8],
                "to": [to[0] - cx + 8, to[1] - mins[1], to[2] - cz + 8],
            }
        )
    return out


def build_atlas(frame_tex: Path, wheel_tex: Path) -> tuple[Image.Image, int, int, float]:
    """Stack frame texture above wheel texture; return image, w, h, wheel_v_offset."""
    frame = Image.open(frame_tex).convert("RGBA")
    wheel = Image.open(wheel_tex).convert("RGBA")
    tw = max(frame.width, wheel.width)
    # pad to equal width
    def pad(im: Image.Image) -> Image.Image:
        if im.width == tw:
            return im
        canvas = Image.new("RGBA", (tw, im.height), (0, 0, 0, 0))
        canvas.paste(im, (0, 0))
        return canvas

    frame = pad(frame)
    wheel = pad(wheel)
    atlas = Image.new("RGBA", (tw, frame.height + wheel.height), (0, 0, 0, 0))
    atlas.paste(frame, (0, 0))
    atlas.paste(wheel, (0, frame.height))
    return atlas, tw, atlas.height, float(frame.height)


def convert_one(type_id: str, frame_name: str, tex_stem: str, data_id: str, wheel_cuboids: list) -> None:
    model_path = AUTO_MODELS / "frame" / frame_name / "main.json"
    tex_path = AUTO_TEX / "frame" / f"{tex_stem}.png"
    wheel_tex = AUTO_TEX / "wheel" / "standard.png"
    if not model_path.exists() or not tex_path.exists():
        raise SystemExit(f"missing assets for {type_id}: {model_path} / {tex_path}")

    jem = json.loads(model_path.read_text())
    cuboids = collect_cuboids(jem)
    atlas, tw, th, wheel_v = build_atlas(tex_path, wheel_tex)

    elements = to_elements(cuboids, tw, th, uv_v_offset=0.0)
    bake_wheels(elements, data_id, wheel_cuboids, wheel_v, tw, th)
    elements = normalize_elements(elements)

    out_tex = PACK / "assets" / "yapvehicles" / "textures" / "entity" / f"{type_id}.png"
    out_model = PACK / "assets" / "yapvehicles" / "models" / "vehicle" / f"{type_id}.json"
    out_item = PACK / "assets" / "yapvehicles" / "models" / "item" / f"vehicle_{type_id}.json"

    atlas.save(out_tex)
    model = {
        "credit": "Converted from Automobility (MIT) — FoundationGames",
        "texture_size": [tw, th],
        "textures": {"0": f"yapvehicles:entity/{type_id}", "particle": f"yapvehicles:entity/{type_id}"},
        "elements": elements,
        "display": DISPLAY,
    }
    out_model.write_text(json.dumps(model, indent=2) + "\n")
    out_item.write_text(
        json.dumps({"parent": f"yapvehicles:vehicle/{type_id}"}, indent=2) + "\n"
    )
    print(f"  {type_id}: {len(elements)} elements, texture {tw}x{th}")


def zip_pack() -> None:
    zip_path = ROOT / "resourcepacks" / "yap-vehicles.zip"
    if zip_path.exists():
        zip_path.unlink()
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in PACK.rglob("*"):
            if path.is_file() and path.name != ".DS_Store":
                zf.write(path, path.relative_to(PACK).as_posix())
    print(f"Wrote {zip_path}")


def write_showcase() -> None:
    """Docs preview cards from texture atlases."""
    from PIL import ImageDraw, ImageFont

    out = PACK / "showcase"
    out.mkdir(parents=True, exist_ok=True)
    names = {
        "chassis": ("Shopping Cart", "Utility"),
        "buggy": ("Orange Standard", "Buggy"),
        "truck_4x4": ("Green Tractor", "4×4"),
        "monster_truck": ("Red Tractor", "Monster"),
        "sport_car": ("Red Standard", "Sport"),
        "hypercar": ("Open Racer", "Hyper"),
        "lambo": ("Lime Speeder", "Exotic"),
        "ferrari": ("Magenta Coupe", "Exotic"),
        "mclaren": ("Copper Motorcar", "Exotic"),
        "porsche": ("Steel Motorcar", "Exotic"),
        "hoverbike": ("Amethyst Rickshaw", "Hover"),
    }
    cmds = {
        "chassis": 77200,
        "buggy": 77201,
        "truck_4x4": 77202,
        "monster_truck": 77203,
        "sport_car": 77204,
        "hypercar": 77205,
        "lambo": 77206,
        "ferrari": 77207,
        "mclaren": 77208,
        "porsche": 77209,
        "hoverbike": 77210,
    }

    def font(size: int):
        for p in (
            "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        ):
            if Path(p).exists():
                return ImageFont.truetype(p, size)
        return ImageFont.load_default()

    title_f = font(22)
    sub_f = font(14)
    tex_dir = PACK / "assets" / "yapvehicles" / "textures" / "entity"
    for tid, (title, kind) in names.items():
        src = Image.open(tex_dir / f"{tid}.png").convert("RGBA")
        h = max(src.height // 2, 32)
        crop = src.crop((0, 0, src.width, h)).resize((256, 256), Image.NEAREST)
        card = Image.new("RGBA", (320, 360), (28, 30, 34, 255))
        card.paste(crop, (32, 24), crop)
        draw = ImageDraw.Draw(card)
        draw.text((16, 300), title, fill=(140, 200, 255, 255), font=title_f)
        draw.text((16, 328), f"{kind} · CMD {cmds[tid]}", fill=(160, 160, 160, 255), font=sub_f)
        card.convert("RGB").save(out / f"{tid}.png")
    print(f"Wrote showcase cards → {out}")


def main() -> None:
    if not AUTO_MODELS.exists():
        raise SystemExit(
            "Automobility sources missing. Clone to /tmp/yap-car-models/Automobility first."
        )
    wheel_cuboids = load_wheel_cuboids()
    print(f"Importing {len(FLEET)} vehicles from Automobility…")
    for type_id, (frame, tex, data_id) in FLEET.items():
        convert_one(type_id, frame, tex, data_id, wheel_cuboids)
    write_showcase()
    zip_pack()
    print("Done. See resourcepacks/CREDITS.md for attribution.")


if __name__ == "__main__":
    main()
