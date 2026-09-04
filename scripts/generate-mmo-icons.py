#!/usr/bin/env python3
"""
Regenerate bulk YaP ability icons as clearer 32×32 silhouettes (V5 visual pass).
Keeps hero showcase art (ability_* with unique names from generate-hero-ability-icons.py).
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepacks/yap-abilities"
TEX = PACK / "assets/yapabilities/textures/item"
MANIFEST = PACK / "icon-manifest.json"

TRANSPARENT = (0, 0, 0, 0)

# Kit palette: border, fill, accent, highlight
KITS: dict[str, tuple[tuple[int, int, int, int], ...]] = {
    "arcanum": ((40, 20, 80, 255), (90, 50, 160, 255), (180, 120, 255, 255), (240, 220, 255, 255)),
    "curse": ((40, 10, 40, 255), (90, 20, 70, 255), (180, 40, 120, 255), (255, 160, 200, 255)),
    "melee": ((50, 40, 20, 255), (120, 90, 40, 255), (220, 180, 80, 255), (255, 240, 180, 255)),
    "ranged": ((20, 50, 30, 255), (40, 110, 60, 255), (90, 200, 110, 255), (200, 255, 200, 255)),
    "fire": ((80, 20, 10, 255), (180, 50, 15, 255), (255, 120, 30, 255), (255, 220, 120, 255)),
    "water": ((10, 40, 90, 255), (30, 100, 180, 255), (80, 180, 255, 255), (200, 240, 255, 255)),
    "wind": ((30, 60, 70, 255), (60, 130, 140, 255), (140, 220, 230, 255), (230, 255, 255, 255)),
    "earth": ((40, 30, 15, 255), (90, 70, 35, 255), (160, 130, 70, 255), (220, 200, 140, 255)),
    "prayer": ((70, 60, 20, 255), (160, 140, 50, 255), (240, 220, 100, 255), (255, 250, 200, 255)),
    "utility": ((40, 40, 50, 255), (90, 90, 110, 255), (160, 160, 190, 255), (230, 230, 245, 255)),
    "default": ((35, 35, 45, 255), (80, 80, 100, 255), (150, 150, 180, 255), (230, 230, 250, 255)),
}

HERO_PREFIXES = (
    "inferno_", "tidal_", "gale_", "quake_", "void_", "solar_",
    "blood_", "aether_", "iron_", "sky_", "hex_", "sanctum_",
    "aerial_", "meteor_", "collapse_", "lance_", "tempest_",
)


def kit_for(name: str) -> str:
    n = name.lower()
    for key in ("arcanum", "curse", "melee", "ranged", "fire", "water", "wind", "earth", "prayer", "utility"):
        if key in n:
            return key
    if n.startswith("magic_"):
        for key in ("fire", "water", "wind", "earth"):
            if key in n:
                return key
    return "default"


def is_hero(name: str) -> bool:
    stem = name.removeprefix("ability_").removesuffix(".png")
    # Numeric kit templates (arcanum_01) are bulk; named heroes stay.
    if any(ch.isdigit() for ch in stem[-2:]):
        return False
    return any(stem.startswith(p.rstrip("_")) or p.rstrip("_") in stem for p in HERO_PREFIXES)


def frame(draw: ImageDraw.ImageDraw, border, fill) -> None:
    draw.rounded_rectangle((2, 2, 29, 29), radius=4, fill=fill, outline=border, width=2)


def draw_glyph(draw: ImageDraw.ImageDraw, kit: str, accent, hi, idx: int) -> None:
    # Distinct glyph per kit family so hotbar reads at a glance.
    if kit in ("fire",):
        draw.polygon([(16, 6), (22, 18), (16, 26), (10, 18)], fill=accent)
        draw.ellipse((13, 10, 19, 16), fill=hi)
    elif kit in ("water",):
        draw.ellipse((8, 10, 24, 24), fill=accent)
        draw.polygon([(16, 5), (22, 14), (10, 14)], fill=hi)
    elif kit in ("wind",):
        for y in (10, 15, 20):
            draw.arc((6, y - 4, 26, y + 8), 200, 340, fill=accent, width=2)
        draw.ellipse((14, 14, 18, 18), fill=hi)
    elif kit in ("earth",):
        draw.rectangle((8, 14, 24, 26), fill=accent)
        draw.polygon([(8, 14), (16, 6), (24, 14)], fill=hi)
    elif kit in ("melee",):
        draw.polygon([(16, 5), (20, 16), (16, 27), (12, 16)], fill=accent)
        draw.line((16, 8, 16, 24), fill=hi, width=2)
    elif kit in ("ranged",):
        draw.arc((7, 8, 25, 24), 200, 340, fill=accent, width=3)
        draw.line((16, 7, 16, 25), fill=hi, width=2)
        draw.ellipse((14, 6, 18, 10), fill=accent)
    elif kit in ("curse",):
        draw.ellipse((8, 8, 24, 24), outline=accent, width=2)
        draw.line((10, 10, 22, 22), fill=hi, width=2)
        draw.line((22, 10, 10, 22), fill=accent, width=2)
    elif kit in ("arcanum",):
        draw.regular_polygon((16, 16, 9), n_sides=6, rotation=idx * 12, fill=accent, outline=hi)
        draw.ellipse((13, 13, 19, 19), fill=hi)
    elif kit in ("prayer",):
        draw.polygon([(16, 5), (20, 16), (16, 27), (12, 16)], fill=accent)
        draw.ellipse((12, 8, 20, 16), fill=hi)
    else:
        draw.ellipse((9, 9, 23, 23), fill=accent)
        draw.ellipse((13, 13, 19, 19), fill=hi)


def render(name: str, idx: int) -> Image.Image:
    kit = kit_for(name)
    border, fill, accent, hi = KITS[kit]
    img = Image.new("RGBA", (32, 32), TRANSPARENT)
    draw = ImageDraw.Draw(img)
    frame(draw, border, fill)
    draw_glyph(draw, kit, accent, hi, idx)
    # Corner pip so siblings in a kit look unique
    pip = (2 + (idx % 5) * 2, 2)
    draw.rectangle((pip[0], pip[1], pip[0] + 2, pip[1] + 2), fill=hi)
    return img


def main() -> None:
    TEX.mkdir(parents=True, exist_ok=True)
    updated = 0
    skipped = 0
    for i, path in enumerate(sorted(TEX.glob("ability_*.png"))):
        if is_hero(path.name):
            skipped += 1
            continue
        render(path.name, i).save(path, optimize=True)
        updated += 1
    if MANIFEST.exists():
        data = json.loads(MANIFEST.read_text())
        data["bulk-icon-pass"] = "v5-32x32"
        MANIFEST.write_text(json.dumps(data, indent=2) + "\n")
    print(f"updated {updated} bulk icons (32x32); skipped {skipped} hero/unique")


if __name__ == "__main__":
    main()
