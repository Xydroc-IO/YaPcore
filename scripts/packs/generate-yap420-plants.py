#!/usr/bin/env python3
"""Generate original YaP420 cannabis crop textures (Minecraft pixel art).

Target look: CannabisCraft-style ladder of opposite palmate fans on a thin
stem, with tan buds at each node — YaP-authored, not their files.

64×32 sheets read crisply under Faithful 64x.
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "resourcepacks/yap-items/assets/yapitems/textures/block"
ITEM = ROOT / "resourcepacks/yap-items/assets/yapitems/textures/item"
W = 64
H = 64


def C(h: str, a: int = 255) -> tuple[int, int, int, int]:
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# Match reference screenshot: lime-yellow tips, mid green, gold node buds
SATIVA = {
    "stem": C("#78b048"),
    "stem_d": C("#508030"),
    "leaf_d": C("#2e7018"),
    "leaf": C("#58b030"),
    "leaf_l": C("#c8f050"),
    "vein": C("#1c5010"),
    "bud_d": C("#9a7820"),
    "bud": C("#e0b838"),
    "bud_l": C("#f8e878"),
    "pistil": C("#f09028"),
}
INDICA = {
    "stem": C("#589048"),
    "stem_d": C("#386838"),
    "leaf_d": C("#246030"),
    "leaf": C("#3c9040"),
    "leaf_l": C("#98d850"),
    "vein": C("#184028"),
    "bud_d": C("#806020"),
    "bud": C("#c89830"),
    "bud_l": C("#e8d068"),
    "pistil": C("#e07820"),
}


def blank() -> Image.Image:
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))


def put(img: Image.Image, x: int, y: int, c: tuple[int, int, int, int]) -> None:
    if 0 <= x < W and 0 <= y < H and c[3]:
        img.putpixel((x, y), c)


def stem(img: Image.Image, x: int, y0: int, y1: int, pal: dict) -> None:
    for y in range(min(y0, y1), max(y0, y1) + 1):
        put(img, x - 1, y, pal["stem_d"])
        put(img, x, y, pal["stem"])
        put(img, x + 1, y, pal["stem_d"] if y % 2 else pal["stem"])


def leaflet(
    img: Image.Image,
    ox: float,
    oy: float,
    ang_deg: float,
    length: float,
    half_w: float,
    pal: dict,
) -> None:
    ang = math.radians(ang_deg)
    dx, dy = math.sin(ang), -math.cos(ang)
    px, py = math.cos(ang), math.sin(ang)
    n = max(5, int(round(length)))
    for i in range(n + 1):
        t = i / n
        if t < 0.12:
            env = (t / 0.12) * 0.5
        elif t < 0.38:
            env = 0.5 + (t - 0.12) / 0.26 * 0.5
        else:
            env = math.cos(((t - 0.38) / 0.62) * (math.pi / 2))
        jag = 0.58 + 0.42 * abs(math.sin(t * math.pi * 6))
        w = max(0.4, half_w * env * jag)
        cx, cy = ox + dx * i, oy + dy * i
        for s in range(-int(w) - 1, int(w) + 2):
            if abs(s) > w + 0.3:
                continue
            x, y = round(cx + px * s), round(cy + py * s)
            if abs(s) >= w - 0.5:
                put(img, x, y, pal["leaf_d"])
            elif abs(s) <= 0.55:
                put(img, x, y, pal["vein"])
            elif t > 0.62:
                put(img, x, y, pal["leaf_l"])
            else:
                put(img, x, y, pal["leaf"])


def fan_out(
    img: Image.Image,
    cx: int,
    cy: int,
    *,
    side: int,
    scale: float,
    pal: dict,
    fingers: int = 7,
) -> None:
    """One cannabis fan — up-and-out like the reference (~55° from vertical)."""
    fingers = max(3, fingers | 1)
    half = fingers // 2
    # 0° = straight up; ±55° = outward tiers in the screenshot
    base = 55.0 * side
    span = 48.0
    base_len = 22.0 * scale
    base_w = 2.4 * scale
    for i in range(-half, half + 1):
        ang = base + i * (span / max(half, 1))
        length = base_len * (1.0 - 0.13 * abs(i))
        half_w = base_w * (1.0 - 0.09 * abs(i))
        leaflet(img, cx, cy, ang, length, half_w, pal)


def bud(img: Image.Image, cx: int, cy: int, r: int, pal: dict, rich: bool) -> None:
    """Tan/gold node bud like the reference."""
    for y in range(cy - r - 1, cy + r + 2):
        for x in range(cx - r - 1, cx + r + 2):
            dx, dy = x - cx, y - cy
            if dx * dx + dy * dy * 0.8 > (r + 0.55) ** 2:
                continue
            if abs(dx) + abs(dy) >= r:
                put(img, x, y, pal["bud_d"])
            elif dy < 0:
                put(img, x, y, pal["bud_l"])
            else:
                put(img, x, y, pal["bud"])
    if rich:
        put(img, cx, cy - r - 1, pal["pistil"])
        put(img, cx - 1, cy - r, pal["pistil"])
        put(img, cx + 1, cy - r, pal["pistil"])


def tier(
    img: Image.Image,
    cx: int,
    cy: int,
    pal: dict,
    *,
    scale: float = 1.0,
    fingers: int = 7,
    with_bud: bool = True,
    rich: bool = False,
    bud_r: int = 2,
) -> None:
    """Opposite leaf pair + optional node bud — the CannabisCraft unit."""
    fan_out(img, cx - 1, cy, side=-1, scale=scale, pal=pal, fingers=fingers)
    fan_out(img, cx + 1, cy, side=+1, scale=scale, pal=pal, fingers=fingers)
    if with_bud:
        bud(img, cx, cy, bud_r, pal, rich)


def paint_ladder(
    tiers: list[tuple[int, float, int, bool, bool, int]],
    pal: dict,
    *,
    stem_top: int,
    stem_bot: int = H - 1,
) -> Image.Image:
    """tiers: (y, scale, fingers, with_bud, rich, bud_r)"""
    img = blank()
    cx = W // 2
    stem(img, cx, stem_top, stem_bot, pal)
    for y, sc, fingers, with_bud, rich, br in tiers:
        tier(img, cx, y, pal, scale=sc, fingers=fingers, with_bud=with_bud, rich=rich, bud_r=br)
    return img


def stage0(pal: dict) -> Image.Image:
    img = blank()
    cx = W // 2
    stem(img, cx, 52, 63, pal)
    put(img, cx - 2, 50, pal["leaf"])
    put(img, cx + 2, 50, pal["leaf"])
    put(img, cx, 48, pal["leaf_l"])
    put(img, cx - 3, 51, pal["leaf_d"])
    put(img, cx + 3, 51, pal["leaf_d"])
    return img


def stage1(pal: dict, sativa: bool) -> Image.Image:
    # Young ~ like right plant: 2 tiers
    sc = 0.7 if sativa else 0.85
    return paint_ladder(
        [
            (56, sc * 0.75, 5, False, False, 1),
            (44, sc, 5, True, False, 1),
        ],
        pal,
        stem_top=40,
    )


def stage2(pal: dict, sativa: bool) -> Image.Image:
    sc = 0.8 if sativa else 0.95
    return paint_ladder(
        [
            (58, sc * 0.7, 5, False, False, 1),
            (46, sc * 0.9, 7, True, False, 1),
            (32, sc, 7, True, False, 2),
        ],
        pal,
        stem_top=26,
    )


def stage3(pal: dict, sativa: bool) -> Image.Image:
    # Full single block — ~4–5 clear tiers
    sc = 0.85 if sativa else 1.0
    return paint_ladder(
        [
            (60, sc * 0.7, 5, True, False, 1),
            (48, sc * 0.85, 7, True, False, 2),
            (36, sc * 0.95, 7, True, False, 2),
            (24, sc, 7, True, True, 2),
            (12, sc * 0.9, 5, True, True, 2),
        ],
        pal,
        stem_top=6,
    )


def tall_bottom(stage: int, pal: dict, sativa: bool) -> Image.Image:
    sc = 0.9 if sativa else 1.05
    rich = stage >= 5
    return paint_ladder(
        [
            (60, sc * 0.85, 7, True, rich, 2),
            (48, sc * 0.95, 7, True, rich, 2),
            (36, sc, 7, True, rich, 2),
            (24, sc, 7, True, rich, 2),
            (12, sc * 0.95, 7, True, rich, 2),
            (4, sc * 0.85, 5, True, rich, 2),
        ],
        pal,
        stem_top=0,
    )


def tall_mid(pal: dict) -> Image.Image:
    sc = 0.95
    return paint_ladder(
        [
            (60, sc * 0.9, 7, True, True, 2),
            (48, sc, 7, True, True, 2),
            (36, sc, 7, True, True, 2),
            (24, sc * 0.95, 7, True, True, 2),
            (12, sc * 0.9, 7, True, True, 2),
            (4, sc * 0.8, 5, True, True, 2),
        ],
        pal,
        stem_top=0,
    )


def tall_top(stage: int, pal: dict, sativa: bool) -> Image.Image:
    sc = 0.9 if sativa else 1.05
    rich = stage >= 5
    img = paint_ladder(
        [
            (56, sc * 0.95, 7, True, rich, 2),
            (42, sc, 7, True, rich, 2),
            (28, sc, 7, True, rich, 2),
            (16, sc * 0.9, 5, True, rich, 2),
        ],
        pal,
        stem_top=8,
        stem_bot=63,
    )
    # Apical cola — denser bud cluster at tip
    cx = W // 2
    bud(img, cx, 8, 3 if rich else 2, pal, rich)
    if rich:
        bud(img, cx - 4, 14, 2, pal, True)
        bud(img, cx + 4, 14, 2, pal, True)
        bud(img, cx, 3, 2, pal, True)
    return img


def save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print("wrote", path.relative_to(ROOT))


def write_models() -> None:
    """4-plane bush models (0°+45°) so tiers read fuller in-world."""
    import json

    models = ROOT / "resourcepacks/yap-items/assets/yapitems/models/item"
    display = {
        "gui": {"rotation": [30, 45, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.35, 0.35, 0.35]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {
            "rotation": [0, 45, 0],
            "translation": [0, 2.5, 0],
            "scale": [0.3, 0.3, 0.3],
        },
        "firstperson_righthand": {
            "rotation": [0, 45, 0],
            "translation": [0, 1.5, 0],
            "scale": [0.35, 0.35, 0.35],
        },
    }

    def face(tex: str) -> dict:
        return {"uv": [0, 0, 16, 16], "texture": tex}

    def plane_pair(y0: float, y1: float, tex: str, angle: float, origin_y: float) -> list:
        return [
            {
                "from": [0.8, y0, 8],
                "to": [15.2, y1, 8],
                "rotation": {
                    "origin": [8, origin_y, 8],
                    "axis": "y",
                    "angle": angle,
                    "rescale": True,
                },
                "shade": False,
                "faces": {"north": face(tex), "south": face(tex)},
            },
            {
                "from": [8, y0, 0.8],
                "to": [8, y1, 15.2],
                "rotation": {
                    "origin": [8, origin_y, 8],
                    "axis": "y",
                    "angle": angle,
                    "rescale": True,
                },
                "shade": False,
                "faces": {"west": face(tex), "east": face(tex)},
            },
        ]

    def single(tex: str) -> dict:
        els: list = []
        for angle in (45, 0):
            els += plane_pair(0, 16, "#cross", angle, 8)
        return {
            "ambientocclusion": False,
            "textures": {"particle": tex, "cross": tex},
            "elements": els,
            "display": display,
        }

    def tall2(bottom: str, top: str) -> dict:
        els: list = []
        for angle in (45, 0):
            els += plane_pair(-16, 0, "#cross", angle, -8)
            els += plane_pair(0, 16, "#top", angle, 8)
        d = dict(display)
        d["fixed"] = {"rotation": [0, 0, 0], "translation": [0, 16, 0], "scale": [1, 1, 1]}
        return {
            "ambientocclusion": False,
            "textures": {"particle": bottom, "cross": bottom, "top": top},
            "elements": els,
            "display": d,
        }

    def tall3(bottom: str, mid: str, top: str) -> dict:
        els: list = []
        for angle in (45, 0):
            els += plane_pair(-16, 0, "#cross", angle, -8)
            els += plane_pair(0, 16, "#mid", angle, 8)
            els += plane_pair(16, 32, "#top", angle, 24)
        d = dict(display)
        d["fixed"] = {"rotation": [0, 0, 0], "translation": [0, 16, 0], "scale": [1, 1, 1]}
        return {
            "ambientocclusion": False,
            "textures": {"particle": bottom, "cross": bottom, "mid": mid, "top": top},
            "elements": els,
            "display": d,
        }

    models.mkdir(parents=True, exist_ok=True)
    for strain, prefix in (("sativa", "yap420_plant"), ("indica", "yap420_plant_indica")):
        tex = "sativa" if strain == "sativa" else "indica"
        for stage in range(4):
            data = single(f"yapitems:block/yap420_{tex}_{stage}")
            path = models / f"{prefix}_{stage}.json"
            path.write_text(json.dumps(data, indent=2) + "\n")
            print("wrote", path.relative_to(ROOT))
        path = models / f"{prefix}_4.json"
        path.write_text(
            json.dumps(
                tall2(f"yapitems:block/yap420_{tex}_4", f"yapitems:block/yap420_{tex}_4_top"),
                indent=2,
            )
            + "\n"
        )
        print("wrote", path.relative_to(ROOT))
        if strain == "sativa":
            data = tall3(
                f"yapitems:block/yap420_{tex}_5",
                f"yapitems:block/yap420_{tex}_5_mid",
                f"yapitems:block/yap420_{tex}_5_top",
            )
        else:
            data = tall2(f"yapitems:block/yap420_{tex}_5", f"yapitems:block/yap420_{tex}_5_top")
        path = models / f"{prefix}_5.json"
        path.write_text(json.dumps(data, indent=2) + "\n")
        print("wrote", path.relative_to(ROOT))


def main() -> None:
    write_models()
    for name, pal, sativa in (("sativa", SATIVA, True), ("indica", INDICA, False)):
        save(stage0(pal), BLOCK / f"yap420_{name}_0.png")
        save(stage1(pal, sativa), BLOCK / f"yap420_{name}_1.png")
        save(stage2(pal, sativa), BLOCK / f"yap420_{name}_2.png")
        save(stage3(pal, sativa), BLOCK / f"yap420_{name}_3.png")
        save(tall_bottom(4, pal, sativa), BLOCK / f"yap420_{name}_4.png")
        save(tall_top(4, pal, sativa), BLOCK / f"yap420_{name}_4_top.png")
        save(tall_bottom(5, pal, sativa), BLOCK / f"yap420_{name}_5.png")
        save(tall_top(5, pal, sativa), BLOCK / f"yap420_{name}_5_top.png")
        if sativa:
            save(tall_mid(pal), BLOCK / f"yap420_{name}_5_mid.png")

        for stage in range(6):
            src = {
                0: stage0(pal),
                1: stage1(pal, sativa),
                2: stage2(pal, sativa),
                3: stage3(pal, sativa),
                4: tall_top(4, pal, sativa),
                5: tall_top(5, pal, sativa),
            }[stage]
            item = (
                f"yap420_plant_{stage}.png"
                if name == "sativa"
                else f"yap420_plant_indica_{stage}.png"
            )
            save(src, ITEM / item)


if __name__ == "__main__":
    main()
