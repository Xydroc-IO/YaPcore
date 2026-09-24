#!/usr/bin/env python3
"""Build YaP420 plant/item textures from Blazin 3.1 ("Get Cobblestoned!").

Blazin replaces nether-wart crops with real cannabis plant sheets. We split those
dual 256×256 crop panels into single-plant squares, map them onto YaP420 stages,
color-shift indica, and remap a few thematic item icons.

Source: resourcepacks/THIRD_PARTY/blazin/  (see blazin-NOTICE.txt)
Output: resourcepacks/yap-items/assets/yapitems/textures/{block,item}/
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageEnhance, ImageOps

ROOT = Path(__file__).resolve().parents[2]
BLAZIN = ROOT / "resourcepacks/THIRD_PARTY/blazin/textures"
BLOCK = ROOT / "resourcepacks/yap-items/assets/yapitems/textures/block"
ITEM = ROOT / "resourcepacks/yap-items/assets/yapitems/textures/item"
MODELS = ROOT / "resourcepacks/yap-items/assets/yapitems/models/item"
SIZE = 128  # crisp under Faithful 64x; Blazin source is 256 dual


def load(path: Path) -> Image.Image:
    return Image.open(path).convert("RGBA")


def half(im: Image.Image, which: str = "left") -> Image.Image:
    """Blazin crop sheets are two plants side-by-side (classic dual-cross)."""
    w, h = im.size
    if which == "left":
        return im.crop((0, 0, w // 2, h))
    return im.crop((w // 2, 0, w, h))


def square_from_bottom(tall: Image.Image, size: int = SIZE) -> Image.Image:
    """Bottom-align a tall plant into a square crop sheet."""
    tall = tall.convert("RGBA")
    tw, th = tall.size
    # Scale width to size, keep aspect
    scale = size / tw
    nh = max(1, int(round(th * scale)))
    scaled = tall.resize((size, nh), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    y = size - min(size, nh)
    src = scaled if nh <= size else scaled.crop((0, nh - size, size, nh))
    out.paste(src, (0, y if nh <= size else 0), src)
    return out


def band(tall: Image.Image, y0_frac: float, y1_frac: float, size: int = SIZE) -> Image.Image:
    """Horizontal band of a tall sheet → square (for stacked tall crops)."""
    tw, th = tall.size
    y0 = int(th * y0_frac)
    y1 = int(th * y1_frac)
    strip = tall.crop((0, y0, tw, y1))
    return strip.resize((size, size), Image.Resampling.LANCZOS)


def indica_shift(im: Image.Image) -> Image.Image:
    """Darker, cooler green for indica vs Blazin's lime sativa look."""
    im = im.convert("RGBA")
    r, g, b, a = im.split()
    # pull red down slightly, boost blue in greens
    r = r.point(lambda v: int(v * 0.78))
    g = g.point(lambda v: int(v * 0.88))
    b = b.point(lambda v: min(255, int(v * 1.15 + 8)))
    out = Image.merge("RGBA", (r, g, b, a))
    return ImageEnhance.Brightness(out).enhance(0.92)


def save(im: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print("wrote", path.relative_to(ROOT))


DISPLAY = {
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


def _plane(y0: float, y1: float, tex: str, origin_y: float) -> list:
    face = {"uv": [0, 0, 16, 16], "texture": tex}
    rot = {"origin": [8, origin_y, 8], "axis": "y", "angle": 45, "rescale": True}
    return [
        {
            "from": [0.8, y0, 8],
            "to": [15.2, y1, 8],
            "rotation": rot,
            "shade": False,
            "faces": {"north": face, "south": face},
        },
        {
            "from": [8, y0, 0.8],
            "to": [8, y1, 15.2],
            "rotation": rot,
            "shade": False,
            "faces": {"west": face, "east": face},
        },
    ]


def write_models() -> None:
    MODELS.mkdir(parents=True, exist_ok=True)
    for strain, prefix in (("sativa", "yap420_plant"), ("indica", "yap420_plant_indica")):
        for stage in range(4):
            path = f"yapitems:block/yap420_{strain}_{stage}"
            data = {
                "parent": "minecraft:block/cross",
                "textures": {"cross": path, "particle": path},
                "display": DISPLAY,
            }
            (MODELS / f"{prefix}_{stage}.json").write_text(json.dumps(data, indent=2) + "\n")

        def tall2(bottom: str, top: str) -> dict:
            d = dict(DISPLAY)
            d["fixed"] = {"rotation": [0, 0, 0], "translation": [0, 16, 0], "scale": [1, 1, 1]}
            return {
                "ambientocclusion": False,
                "textures": {"particle": bottom, "cross": bottom, "top": top},
                "elements": _plane(-16, 0, "#cross", -8) + _plane(0, 16, "#top", 8),
                "display": d,
            }

        def tall3(bottom: str, mid: str, top: str) -> dict:
            d = dict(DISPLAY)
            d["fixed"] = {"rotation": [0, 0, 0], "translation": [0, 16, 0], "scale": [1, 1, 1]}
            return {
                "ambientocclusion": False,
                "textures": {"particle": bottom, "cross": bottom, "mid": mid, "top": top},
                "elements": (
                    _plane(-16, 0, "#cross", -8)
                    + _plane(0, 16, "#mid", 8)
                    + _plane(16, 32, "#top", 24)
                ),
                "display": d,
            }

        (MODELS / f"{prefix}_4.json").write_text(
            json.dumps(
                tall2(f"yapitems:block/yap420_{strain}_4", f"yapitems:block/yap420_{strain}_4_top"),
                indent=2,
            )
            + "\n"
        )
        if strain == "sativa":
            data = tall3(
                f"yapitems:block/yap420_{strain}_5",
                f"yapitems:block/yap420_{strain}_5_mid",
                f"yapitems:block/yap420_{strain}_5_top",
            )
        else:
            data = tall2(
                f"yapitems:block/yap420_{strain}_5",
                f"yapitems:block/yap420_{strain}_5_top",
            )
        (MODELS / f"{prefix}_5.json").write_text(json.dumps(data, indent=2) + "\n")
        print("wrote models for", prefix)


def plant_set(seedling: Image.Image, veg: Image.Image, flower: Image.Image, *, indica: bool) -> dict[str, Image.Image]:
    """Build all block sheets for one strain from Blazin halves."""
    if indica:
        seedling, veg, flower = map(indica_shift, (seedling, veg, flower))

    # Single-block stages
    s0 = square_from_bottom(seedling)
    # stage 1: slightly taller crop of seedling / young veg bottom
    s1 = square_from_bottom(veg.crop((0, veg.size[1] // 2, veg.size[0], veg.size[1])))
    s2 = square_from_bottom(veg)
    s3 = square_from_bottom(flower.crop((0, flower.size[1] // 3, flower.size[0], flower.size[1])))

    # Tall: vegetative lower canopy + flowering cola top
    # stage 4 (2-high): veg lower + flower upper
    b4 = band(veg, 0.35, 1.0)  # lower-mid veg
    t4 = band(flower, 0.0, 0.55)  # cola top

    # stage 5: fuller flower stack
    b5 = band(flower, 0.55, 1.0)  # lower cola / sugar leaves
    m5 = band(flower, 0.28, 0.72)
    t5 = band(flower, 0.0, 0.45)

    return {
        "0": s0,
        "1": s1,
        "2": s2,
        "3": s3,
        "4": b4,
        "4_top": t4,
        "5": b5,
        "5_mid": m5,
        "5_top": t5,
    }


def draw_bud_icon(size: int = 16, *, wet: bool = True) -> Image.Image:
    """Original YaP cannabis cola — organic bud, not a bottle/potion silhouette."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    if wet:
        leaf = (52, 140, 48, 255)
        leaf_l = (98, 190, 72, 255)
        leaf_d = (28, 88, 32, 255)
        tip = (210, 230, 90, 255)
        dew = (180, 220, 255, 200)
    else:
        leaf = (72, 118, 42, 255)
        leaf_l = (130, 160, 70, 255)
        leaf_d = (48, 72, 28, 255)
        tip = (196, 150, 55, 255)
        dew = (0, 0, 0, 0)

    cx = size // 2
    # Cone cola: wide base, taper to tip
    for y in range(size):
        t = y / max(1, size - 1)
        half_w = max(1.0, (1.0 - t * 0.72) * (size * 0.38))
        for x in range(size):
            dx = x - cx + 0.5
            if abs(dx) > half_w:
                continue
            edge = abs(dx) / half_w
            # jagged bract edges
            jagged = 0.15 * ((x * 3 + y * 5) % 3)
            if edge > 0.92 - jagged * 0.1 and (x + y) % 2 == 0:
                continue
            if edge > 0.78:
                c = leaf_d
            elif (x + y * 2) % 5 == 0:
                c = tip  # pistil flecks
            elif edge < 0.35 and t < 0.55:
                c = leaf_l
            else:
                c = leaf
            img.putpixel((x, y), c)
    # stem nub at bottom
    for x in range(cx - 1, cx + 2):
        for y in range(size - 2, size):
            if 0 <= x < size:
                img.putpixel((x, y), (70, 55, 30, 255) if not wet else (60, 90, 40, 255))
    # wet dew highlights
    if wet and dew[3] > 0:
        for px, py in ((cx - 2, 4), (cx + 1, 7), (cx - 1, 10)):
            if 0 <= px < size and 0 <= py < size and img.getpixel((px, py))[3] > 0:
                img.putpixel((px, py), dew)
    return img


def draw_seed_icon(size: int = 16) -> Image.Image:
    """Original YaP seed — classic oval cannabis seed with tiger stripes."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    shell = (168, 138, 88, 255)
    shell_l = (210, 185, 130, 255)
    shell_d = (110, 80, 48, 255)
    stripe = (62, 42, 28, 255)
    # Teardrop/oval: slightly taller than wide, pointed tip up
    cx, cy = size // 2 - 0.5, size // 2 + 0.5
    for y in range(size):
        for x in range(size):
            # parametric oval with soft tip
            nx = (x - cx) / 4.2
            ny = (y - cy) / 5.4
            # slight pear: narrower toward top
            narrow = 1.0 + 0.22 * max(0.0, -(y - cy) / 5.4)
            nx *= narrow
            d = nx * nx + ny * ny
            if d > 1.0:
                continue
            # outline
            if d > 0.78:
                c = shell_d
            # tiger stripes (curved dark bands)
            elif int((x * 1.4 + y * 0.35) // 2) % 2 == 0 and d < 0.7:
                c = stripe
            elif y <= cy - 1 and abs(x - cx) < 2.2:
                c = shell_l
            else:
                c = shell
            img.putpixel((x, y), c)
    # hilum (small scar) at bottom
    hx, hy = size // 2, size - 3
    for dx in (-1, 0, 1):
        if 0 <= hx + dx < size and 0 <= hy < size:
            img.putpixel((hx + dx, hy), shell_d)
    return img


def remap_items() -> None:
    """Thematic Blazin / GanjaCraft item icons → YaP420 where they fit."""
    items = BLAZIN / "items"
    blocks = BLAZIN / "blocks"
    ganja = ROOT / "resourcepacks/THIRD_PARTY/ganjacraft/textures/items"

    # Seeds = actual seeds (never the leaf icon)
    seed = draw_seed_icon(16)
    save(seed, ITEM / "yap420_seed.png")

    # Fiber — GanjaCraft hemp fiber if present, else Blazin leaf
    fiber_src = ganja / "hempfiber.png"
    if fiber_src.is_file():
        save(load(fiber_src).resize((16, 16), Image.Resampling.NEAREST), ITEM / "yap420_fiber.png")
    else:
        leaf = load(items / "nether_wart.png")
        save(leaf.resize((16, 16), Image.Resampling.NEAREST), ITEM / "yap420_fiber.png")

    # Kief → gram bags
    kief = load(items / "blaze_powder.png")
    save(kief.resize((16, 16), Image.Resampling.NEAREST), ITEM / "yap420_gram.png")

    # Bud block → pound brick
    brick = load(blocks / "nether_wart_block.png")
    save(brick.resize((16, 16), Image.Resampling.NEAREST), ITEM / "yap420_brick.png")

    # Grinder → packaging press (static + frames)
    grind = load(items / "brewing_stand.png")
    g16 = grind.resize((16, 16), Image.Resampling.NEAREST)
    save(g16, ITEM / "yap420_press.png")
    save(g16, ITEM / "yap420_press-0.png")
    save(g16, ITEM / "yap420_press-1.png")
    save(g16, ITEM / "yap420_press-2.png")

    # Smoke bottle was dragon_breath (looks like a potion) — use original bud colas
    save(draw_bud_icon(16, wet=True), ITEM / "yap420_bud_wet.png")
    cured = draw_bud_icon(16, wet=False)
    save(cured, ITEM / "yap420_bud_cured.png")
    # Ounce bag still from flower tip crop
    flower = half(load(BLAZIN / "blocks/nether_wart_stage_2.png"), "left")
    tip = band(flower, 0.05, 0.35, 32)
    save(tip.resize((16, 16), Image.Resampling.NEAREST), ITEM / "yap420_ounce.png")

    # Joint / blunt / paper / rack / brownie stay YaP-authored (no Blazin equivalent)


def main() -> None:
    stage0 = load(BLAZIN / "blocks/nether_wart_stage_0.png")
    stage1 = load(BLAZIN / "blocks/nether_wart_stage_1.png")
    stage2 = load(BLAZIN / "blocks/nether_wart_stage_2.png")

    # Left = sativa, right = indica base (Blazin panels differ slightly)
    sat = plant_set(half(stage0, "left"), half(stage1, "left"), half(stage2, "left"), indica=False)
    ind = plant_set(half(stage0, "right"), half(stage1, "right"), half(stage2, "right"), indica=True)

    write_models()
    for strain, sheets in (("sativa", sat), ("indica", ind)):
        for key, im in sheets.items():
            if key == "5_mid" and strain == "indica":
                continue  # indica has no mid segment
            save(im, BLOCK / f"yap420_{strain}_{key}.png")
        # inventory plant icons
        for stage in range(6):
            src = sheets["5_top" if stage >= 4 else str(min(stage, 3))]
            name = (
                f"yap420_plant_{stage}.png"
                if strain == "sativa"
                else f"yap420_plant_indica_{stage}.png"
            )
            save(src.resize((32, 32), Image.Resampling.LANCZOS), ITEM / name)

    remap_items()
    print("Blazin → YaP420 plant/item textures done.")


if __name__ == "__main__":
    main()
