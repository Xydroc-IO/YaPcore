#!/usr/bin/env python3
"""Dense YaP foliage overlays for Faithful 64x.

Faithful birch/oak leaves are sparse vanilla-upscales. At night, transparent
holes read as black static on the canopy. This writes denser grayscale
cutouts (still biome-tintable) under yap-skies so the default pack merge
picks them up — without the old muddy FBM stomps.
"""
from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
BLOCK = ROOT / "resourcepacks" / "yap-skies" / "assets/minecraft/textures/block"
FAITHFUL = ROOT / "resourcepacks" / "faithful-64x.zip"

# Grayscale biome-tinted leaves (Faithful / vanilla convention).
TINTED_LEAVES = (
    "oak_leaves.png",
    "birch_leaves.png",
    "spruce_leaves.png",
    "jungle_leaves.png",
    "acacia_leaves.png",
    "dark_oak_leaves.png",
    "mangrove_leaves.png",
    "pale_oak_leaves.png",
)

# Pre-colored — densify alpha only, keep hue.
COLORED_LEAVES = (
    "cherry_leaves.png",
    "azalea_leaves.png",
    "flowering_azalea_leaves.png",
)

MCMETA = '{\n  "texture": {\n      "mipmap_strategy": "strict_cutout"\n  }\n}\n'
SIZE = 64


def _leaf_stamp(draw: ImageDraw.ImageDraw, cx: float, cy: float, ang: float, scale: float, tone: int) -> None:
    """Draw one small pointed leaf (filled polygon) in grayscale."""
    # Local leaf coords: tip + base lobes
    tip = (0.0, -7.0 * scale)
    left = (-3.2 * scale, 1.5 * scale)
    right = (3.2 * scale, 1.5 * scale)
    base = (0.0, 4.5 * scale)
    pts = [tip, right, base, left]
    ca, sa = math.cos(ang), math.sin(ang)
    rot = []
    for x, y in pts:
        rx = cx + x * ca - y * sa
        ry = cy + x * sa + y * ca
        rot.append((rx, ry))
    draw.polygon(rot, fill=(tone, tone, tone, 255))
    # Midrib
    mx0, my0 = cx + tip[0] * ca - tip[1] * sa, cy + tip[0] * sa + tip[1] * ca
    mx1, my1 = cx + base[0] * ca - base[1] * sa, cy + base[0] * sa + base[1] * ca
    dark = max(40, tone - 35)
    draw.line([(mx0, my0), (mx1, my1)], fill=(dark, dark, dark, 255), width=max(1, int(scale)))


def densify_grayscale(src: Image.Image, seed: int, coverage: float = 0.78) -> Image.Image:
    """Keep Faithful leaf shapes, fill holes with matching stamps until denser."""
    src = src.convert("RGBA")
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    # Nearest scale if needed
    if src.size != (SIZE, SIZE):
        src = src.resize((SIZE, SIZE), Image.NEAREST)
    out.paste(src, (0, 0))
    rng = random.Random(seed)
    draw = ImageDraw.Draw(out)

    def opaque_frac() -> float:
        a = out.split()[-1]
        return sum(1 for p in a.getdata() if p > 128) / float(SIZE * SIZE)

    tones = [111, 135, 151, 182]
    guard = 0
    while opaque_frac() < coverage and guard < 400:
        guard += 1
        cx = rng.uniform(2, SIZE - 2)
        cy = rng.uniform(2, SIZE - 2)
        # Prefer empty regions
        px, py = int(cx), int(cy)
        if out.getpixel((px, py))[3] > 128 and rng.random() < 0.65:
            continue
        ang = rng.uniform(0, math.tau)
        scale = rng.uniform(0.55, 1.15)
        tone = tones[rng.randrange(len(tones))]
        _leaf_stamp(draw, cx, cy, ang, scale, tone)

    # Hard binary alpha
    px = []
    for r, g, b, a in out.getdata():
        if a < 128:
            px.append((0, 0, 0, 0))
        else:
            # Force grayscale for biome tint
            y = int(0.299 * r + 0.587 * g + 0.114 * b)
            px.append((y, y, y, 255))
    out.putdata(px)
    return out


def densify_colored(src: Image.Image, seed: int, coverage: float = 0.72) -> Image.Image:
    src = src.convert("RGBA")
    if src.size != (SIZE, SIZE):
        src = src.resize((SIZE, SIZE), Image.NEAREST)
    out = src.copy()
    rng = random.Random(seed)
    # Sample opaque colors from source
    colors = [p for p in src.getdata() if p[3] > 200]
    if not colors:
        return out
    draw = ImageDraw.Draw(out)

    def opaque_frac() -> float:
        a = out.split()[-1]
        return sum(1 for p in a.getdata() if p > 128) / float(SIZE * SIZE)

    guard = 0
    while opaque_frac() < coverage and guard < 350:
        guard += 1
        cx = rng.uniform(2, SIZE - 2)
        cy = rng.uniform(2, SIZE - 2)
        if out.getpixel((int(cx), int(cy)))[3] > 128 and rng.random() < 0.6:
            continue
        c = colors[rng.randrange(len(colors))]
        ang = rng.uniform(0, math.tau)
        scale = rng.uniform(0.5, 1.1)
        # Temporary grayscale stamp then recolor
        stamp = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        sd = ImageDraw.Draw(stamp)
        _leaf_stamp(sd, cx, cy, ang, scale, 200)
        for i, (r, g, b, a) in enumerate(stamp.getdata()):
            if a < 128:
                continue
            x, y = i % SIZE, i // SIZE
            # shade from stamp tone
            shade = r / 255.0
            out.putpixel((x, y), (
                min(255, int(c[0] * shade)),
                min(255, int(c[1] * shade)),
                min(255, int(c[2] * shade)),
                255,
            ))
    # Binary alpha
    px = []
    for r, g, b, a in out.getdata():
        px.append((0, 0, 0, 0) if a < 128 else (r, g, b, 255))
    out.putdata(px)
    return out


def load_faithful(name: str) -> Image.Image | None:
    import zipfile
    import io
    if not FAITHFUL.is_file():
        return None
    key = f"assets/minecraft/textures/block/{name}"
    with zipfile.ZipFile(FAITHFUL) as z:
        if key not in z.namelist():
            return None
        return Image.open(io.BytesIO(z.read(key))).convert("RGBA")


def main() -> None:
    BLOCK.mkdir(parents=True, exist_ok=True)
    print("Generating dense YaP foliage overlays…")
    for i, name in enumerate(TINTED_LEAVES):
        src = load_faithful(name)
        if src is None:
            src = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        # Birch especially sparse — denser target
        cov = 0.82 if "birch" in name else 0.76
        img = densify_grayscale(src, seed=1000 + i, coverage=cov)
        img.save(BLOCK / name)
        (BLOCK / f"{name}.mcmeta").write_text(MCMETA)
        a = sum(1 for p in img.getdata() if p[3] > 128) / (SIZE * SIZE)
        print(f"  {name} coverage={a:.0%}")

    for i, name in enumerate(COLORED_LEAVES):
        src = load_faithful(name)
        if src is None:
            continue
        img = densify_colored(src, seed=2000 + i, coverage=0.74)
        img.save(BLOCK / name)
        (BLOCK / f"{name}.mcmeta").write_text(MCMETA)
        a = sum(1 for p in img.getdata() if p[3] > 128) / (SIZE * SIZE)
        print(f"  {name} coverage={a:.0%}")

    print(f"Wrote overlays into {BLOCK}")


if __name__ == "__main__":
    main()
