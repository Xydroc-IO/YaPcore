#!/usr/bin/env python3
"""
Generate unique 16x16 hero ability icons for YaP V4.
Matches the low-color silhouette style of existing yap-abilities icons.
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepacks/yap-abilities"
TEX = PACK / "assets/yapabilities/textures/item"
MODELS = PACK / "assets/yapabilities/models/item"
CLAY = PACK / "assets/minecraft/models/item/clay_ball.json"
MANIFEST = PACK / "icon-manifest.json"

TRANSPARENT = (0, 0, 0, 0)


def px(img: Image.Image, x: int, y: int, color: tuple[int, int, int, int]) -> None:
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), color)


def fill_rect(img, x0, y0, x1, y1, color) -> None:
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, x, y, color)


def disc(img, cx, cy, r, color) -> None:
    for y in range(16):
        for x in range(16):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                px(img, x, y, color)


def ring(img, cx, cy, r, color, thick: int = 1) -> None:
    for y in range(16):
        for x in range(16):
            d2 = (x - cx) ** 2 + (y - cy) ** 2
            if (r - thick) ** 2 <= d2 <= r * r:
                px(img, x, y, color)


def frame(img, border, fill) -> None:
    fill_rect(img, 1, 1, 14, 14, fill)
    for x in range(16):
        px(img, x, 0, TRANSPARENT)
        px(img, x, 15, TRANSPARENT)
        px(img, 0, x, TRANSPARENT)
        px(img, 15, x, TRANSPARENT)
    for x in range(1, 15):
        px(img, x, 1, border)
        px(img, x, 14, border)
        px(img, 1, x, border)
        px(img, 14, x, border)


def draw_inferno_meteor() -> Image.Image:
    c0, c1, c2, c3, hi = (90, 20, 10, 255), (180, 40, 10, 255), (255, 90, 20, 255), (255, 140, 40, 255), (255, 230, 140, 255)
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    frame(img, c0, c1)
    disc(img, 8, 9, 4, c2)
    disc(img, 8, 8, 2, c3)
    px(img, 8, 7, hi)
    # trail sparks above
    for x, y in [(5, 3), (7, 2), (9, 3), (11, 4), (6, 4), (10, 2)]:
        px(img, x, y, c3 if y > 2 else hi)
    return img


def draw_tidal_collapse() -> Image.Image:
    deep, mid, lite, foam = (20, 60, 140, 255), (40, 120, 220, 255), (90, 180, 255, 255), (200, 240, 255, 255)
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    frame(img, deep, mid)
    # wave crest
    for x in range(2, 14):
        y = 8 + int(2.2 * __import__("math").sin(x * 0.9))
        fill_rect(img, x, y, x, 13, lite)
        px(img, x, y, foam)
    disc(img, 8, 5, 2, foam)
    return img


def draw_cyclone_rift() -> Image.Image:
    edge, body, swirl, core = (70, 90, 110, 255), (180, 200, 220, 255), (230, 240, 255, 255), (255, 255, 255, 255)
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    frame(img, edge, body)
    ring(img, 8, 8, 5, swirl, 1)
    ring(img, 8, 8, 3, edge, 1)
    disc(img, 8, 8, 1, core)
    for x, y in [(4, 5), (12, 6), (5, 11), (11, 10)]:
        px(img, x, y, swirl)
    return img


def draw_tectonic_spike() -> Image.Image:
    dirt, stone, tip, crack = (80, 50, 25, 255), (120, 110, 100, 255), (200, 200, 190, 255), (40, 30, 20, 255)
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    frame(img, dirt, stone)
    # rising spike
    for i, w in enumerate([1, 2, 3, 4, 5, 4, 3]):
        y = 12 - i
        x0 = 8 - w // 2
        fill_rect(img, x0, y, x0 + w - 1, y, tip if i > 4 else stone)
    fill_rect(img, 3, 12, 12, 13, dirt)
    px(img, 8, 4, tip)
    return img


def draw_void_lance() -> Image.Image:
    void, glow, core, edge = (40, 10, 70, 255), (140, 60, 220, 255), (220, 180, 255, 255), (20, 0, 40, 255)
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    frame(img, edge, void)
    # lance beam
    fill_rect(img, 7, 2, 8, 13, glow)
    fill_rect(img, 7, 4, 8, 11, core)
    disc(img, 8, 3, 2, glow)
    disc(img, 8, 3, 1, core)
    for y in (6, 9, 12):
        px(img, 6, y, glow)
        px(img, 9, y, glow)
    return img


def draw_hex_bloom() -> Image.Image:
    dark, petal, eye, vein = (40, 10, 50, 255), (120, 40, 160, 255), (200, 80, 255, 255), (60, 20, 80, 255)
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    frame(img, dark, vein)
    # petals
    for cx, cy in [(8, 4), (5, 7), (11, 7), (6, 11), (10, 11), (8, 8)]:
        disc(img, cx, cy, 2, petal)
    disc(img, 8, 8, 2, eye)
    disc(img, 8, 8, 1, dark)
    return img


def draw_solar_aegis() -> Image.Image:
    gold, lite, white, rim = (180, 140, 40, 255), (255, 220, 100, 255), (255, 250, 200, 255), (120, 90, 20, 255)
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    frame(img, rim, gold)
    ring(img, 8, 8, 5, lite, 1)
    disc(img, 8, 8, 3, white)
    disc(img, 8, 8, 1, gold)
    # rays
    for x, y in [(8, 2), (8, 13), (2, 8), (13, 8), (4, 4), (12, 4), (4, 12), (12, 12)]:
        px(img, x, y, lite)
    return img


def draw_blade_tempest() -> Image.Image:
    steel, edge, spark, dark = (160, 160, 170, 255), (230, 230, 240, 255), (255, 220, 120, 255), (50, 50, 60, 255)
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    frame(img, dark, steel)
    # crossed blades
    for i in range(2, 14):
        px(img, i, i, edge)
        px(img, i, 15 - i, edge)
        px(img, i - 1, i, steel)
        px(img, i + 1, 15 - i, steel)
    disc(img, 8, 8, 1, spark)
    for p in [(3, 5), (12, 4), (4, 12), (11, 11)]:
        px(img, p[0], p[1], spark)
    return img


def draw_skyfall_volley() -> Image.Image:
    sky, shaft, tip, fletch = (40, 90, 50, 255), (180, 140, 80, 255), (220, 220, 230, 255), (200, 60, 60, 255)
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    frame(img, (20, 50, 30, 255), sky)
    # three arrows descending
    for ox in (4, 8, 12):
        fill_rect(img, ox, 3, ox, 11, shaft)
        px(img, ox, 2, tip)
        px(img, ox - 1, 11, fletch)
        px(img, ox + 1, 11, fletch)
    return img


def draw_phase_mirror() -> Image.Image:
    void, portal, rim, spark = (30, 10, 50, 255), (120, 50, 180, 255), (200, 120, 255, 255), (230, 200, 255, 255)
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    frame(img, void, portal)
    ring(img, 8, 8, 5, rim, 1)
    ring(img, 8, 8, 3, spark, 1)
    disc(img, 8, 8, 1, void)
    # mirror shard
    fill_rect(img, 10, 4, 12, 7, spark)
    return img


def draw_dragonfire_cascade() -> Image.Image:
    dark, red, orange, white = (60, 10, 10, 255), (200, 30, 20, 255), (255, 120, 20, 255), (255, 240, 160, 255)
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    frame(img, dark, red)
    # cascading columns
    for x in (3, 6, 9, 12):
        h = 4 + (x % 5)
        fill_rect(img, x, 13 - h, x + 1, 13, orange)
        px(img, x, 13 - h, white)
    disc(img, 8, 4, 2, orange)
    disc(img, 8, 4, 1, white)
    return img


def draw_judgment_smite() -> Image.Image:
    navy, gold, bolt, white = (30, 40, 90, 255), (220, 190, 80, 255), (255, 240, 160, 255), (255, 255, 255, 255)
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    frame(img, navy, (50, 60, 120, 255))
    # lightning bolt
    pts = [(8, 2), (7, 4), (9, 5), (6, 8), (10, 9), (7, 12), (9, 13)]
    for x, y in pts:
        px(img, x, y, bolt)
        px(img, x + 1, y, gold)
    disc(img, 8, 3, 1, white)
    fill_rect(img, 5, 13, 11, 13, gold)
    return img


HEROES = [
    ("inferno_meteor", 78020, draw_inferno_meteor),
    ("tidal_collapse", 78021, draw_tidal_collapse),
    ("cyclone_rift", 78022, draw_cyclone_rift),
    ("tectonic_spike", 78023, draw_tectonic_spike),
    ("void_lance", 78024, draw_void_lance),
    ("hex_bloom", 78025, draw_hex_bloom),
    ("solar_aegis", 78026, draw_solar_aegis),
    ("blade_tempest", 78027, draw_blade_tempest),
    ("skyfall_volley", 78028, draw_skyfall_volley),
    ("phase_mirror", 78029, draw_phase_mirror),
    ("dragonfire_cascade", 78030, draw_dragonfire_cascade),
    ("judgment_smite", 78031, draw_judgment_smite),
]


def main() -> int:
    TEX.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)

    for aid, cmd, drawer in HEROES:
        tex_name = f"ability_{aid}"
        img = drawer()
        img.save(TEX / f"{tex_name}.png")
        model = {
            "parent": "item/generated",
            "textures": {"layer0": f"yapabilities:item/{tex_name}"},
        }
        (MODELS / f"{tex_name}.json").write_text(json.dumps(model, indent=2) + "\n")
        print(f"wrote {tex_name}.png cmd={cmd}")

    clay = json.loads(CLAY.read_text())
    overrides = [
        o for o in clay.get("overrides", [])
        if o["predicate"]["custom_model_data"] not in {c for _, c, _ in HEROES}
    ]
    for aid, cmd, _ in HEROES:
        overrides.append({
            "predicate": {"custom_model_data": cmd},
            "model": f"yapabilities:item/ability_{aid}",
        })
    overrides.sort(key=lambda o: o["predicate"]["custom_model_data"])
    clay["overrides"] = overrides
    CLAY.write_text(json.dumps(clay, indent=2) + "\n")

    manifest = json.loads(MANIFEST.read_text())
    abilities = manifest.setdefault("abilities", {})
    for aid, cmd, _ in HEROES:
        abilities[aid] = {"cmd": cmd, "texture": f"ability_{aid}"}
    MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"done: {len(HEROES)} unique hero icons")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
