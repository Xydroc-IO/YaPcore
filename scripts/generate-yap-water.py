#!/usr/bin/env python3
"""Generate YaP water / weather textures for the default client pack.

Grayscale water still/flow so biome tint still works. First-party procedural
art — not from Complementary/BSL/Faithful.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "resourcepacks" / "yap-skies"
BLOCK = OUT / "assets/minecraft/textures/block"
MISC = OUT / "assets/minecraft/textures/misc"
ENV = OUT / "assets/minecraft/textures/environment"
PARTICLE = OUT / "assets/minecraft/textures/particle"

STILL_SIZE = 128
STILL_FRAMES = 64
FLOW_SIZE = 128
FLOW_FRAMES = 64


def _save_rgba(path: Path, arr: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(path, optimize=True)


def _hash2(x: np.ndarray, y: np.ndarray, seed: float) -> np.ndarray:
    return np.mod(np.sin(x * 127.1 + y * 311.7 + seed * 19.19) * 43758.5453, 1.0)


def _value_noise_wrap(w: int, h: int, period: int, seed: float) -> np.ndarray:
    """Toroidal value noise so each water tile edge matches its opposite edge."""
    period = max(2, int(period))
    grid = _hash2(
        np.arange(period, dtype=np.float64)[None, :],
        np.arange(period, dtype=np.float64)[:, None],
        seed,
    )
    ys = np.linspace(0, period, h, endpoint=False)
    xs = np.linspace(0, period, w, endpoint=False)
    y0 = np.floor(ys).astype(np.int32)
    x0 = np.floor(xs).astype(np.int32)
    fy = (ys - y0)[:, None]
    fx = (xs - x0)[None, :]
    fy = fy * fy * (3.0 - 2.0 * fy)
    fx = fx * fx * (3.0 - 2.0 * fx)
    x1 = (x0 + 1) % period
    y1 = (y0 + 1) % period
    n00 = grid[y0[:, None], x0[None, :]]
    n10 = grid[y0[:, None], x1[None, :]]
    n01 = grid[y1[:, None], x0[None, :]]
    n11 = grid[y1[:, None], x1[None, :]]
    return n00 * (1 - fx) * (1 - fy) + n10 * fx * (1 - fy) + n01 * (1 - fx) * fy + n11 * fx * fy


def _fbm(w: int, h: int, period: float, octaves: int, seed: float) -> np.ndarray:
    """Wrapping FBM — period = integer cycles across the tile (kills square seams)."""
    acc = np.zeros((h, w), dtype=np.float64)
    amp = 0.5
    total = 0.0
    p = max(2, int(round(period)))
    for i in range(octaves):
        acc += amp * _value_noise_wrap(w, h, p, seed + i * 17.3)
        total += amp
        amp *= 0.5
        p = max(2, p * 2)
    return acc / total


def _smoothstep(edge0: float, edge1: float, x: np.ndarray) -> np.ndarray:
    t = np.clip((x - edge0) / max(edge1 - edge0, 1e-6), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def _water_field(n: int, t: float, flow: bool) -> np.ndarray:
    """Unit height field — integer-frequency sines + wrapping FBM (no tile seams)."""
    y, x = np.mgrid[0:n, 0:n]
    u = x / n
    v = y / n
    if flow:
        # Integer frequencies only so opposite edges match
        w = (
            0.28 * np.sin((u * 2.0 + v * 1.0 + t * 1.55) * math.pi * 2)
            + 0.22 * np.sin((u * 1.0 - v * 3.0 + t * 1.05) * math.pi * 2)
            + 0.16 * np.sin((u * 4.0 + v * 2.0 - t * 2.15) * math.pi * 2)
            + 0.12 * np.sin((u * 1.0 + v * 5.0 + t * 0.9) * math.pi * 2)
            + 0.08 * np.sin((u * 6.0 - v * 1.0 + t * 2.8) * math.pi * 2)
        )
        streak = _fbm(n, n, 3.0, 5, 3.1 + t * 0.2)
        foam = _fbm(n, n, 5.0, 3, 9.4 + t * 0.5)
        w = w * 0.58 + (streak - 0.5) * 0.38 + (foam - 0.5) * 0.10
        w = w + 0.06 * np.sin((v * 4.0 - t * 2.8) * math.pi * 2)
    else:
        # Long swell (1–2 cycles/tile) + soft mid detail — avoids a grid of identical squares
        w = (
            0.30 * np.sin((u * 1.0 + v * 0.0 + t * 0.48) * math.pi * 2)
            + 0.24 * np.sin((u * 0.0 + v * 1.0 - t * 0.36) * math.pi * 2)
            + 0.16 * np.sin((u * 2.0 - v * 1.0 + t * 0.72) * math.pi * 2)
            + 0.12 * np.sin(((u + v) * 1.0 + t * 0.30) * math.pi * 2)
            + 0.08 * np.sin((u * 3.0 + v * 2.0 - t * 1.1) * math.pi * 2)
        )
        swell = _fbm(n, n, 2.0, 4, 1.4 + t * 0.15)
        capillary = _fbm(n, n, 4.0, 5, 7.7 + t * 0.45)
        sparkle = _fbm(n, n, 8.0, 3, 12.2 + t * 0.9)
        w = w * 0.52 + (swell - 0.5) * 0.32 + (capillary - 0.5) * 0.42 + (sparkle - 0.5) * 0.10
    w = (w - w.min()) / max(float(w.max() - w.min()), 1e-6)
    return w


def _shade_water(height: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Soft grayscale + alpha — low contrast so block edges don't read as a grid."""
    # Periodic gradient so wrap edges stay continuous
    gy = np.gradient(np.vstack([height[-1:], height, height[:1]]), axis=0)[1:-1]
    gx = np.gradient(np.hstack([height[:, -1:], height, height[:, :1]]), axis=1)[:, 1:-1]
    ndotl = np.clip(0.50 - gx * 1.15 + gy * 0.85, 0.0, 1.0)
    crest = _smoothstep(0.74, 0.96, height) * ndotl
    trough = _smoothstep(0.32, 0.06, height)
    # Narrower luminance range = less obvious per-block tiling
    lum = 0.48 + 0.16 * height + 0.12 * ndotl + 0.06 * crest - 0.04 * trough
    lum = np.clip(lum, 0.38, 0.72)
    alpha = 0.36 + 0.22 * (1.0 - height * 0.40) + 0.05 * ndotl + 0.04 * trough
    alpha = np.clip(alpha, 0.30, 0.62)
    return lum, alpha


def make_still_sheet() -> np.ndarray:
    n = STILL_SIZE
    frames = STILL_FRAMES
    sheet = np.zeros((n * frames, n, 4), dtype=np.float64)
    for i in range(frames):
        t = i / frames
        h = _water_field(n, t, flow=False)
        lum, alpha = _shade_water(h)
        # No edge darkening — that painted a square on every block
        frame = np.zeros((n, n, 4), dtype=np.float64)
        frame[..., 0] = frame[..., 1] = frame[..., 2] = lum * 255.0
        frame[..., 3] = alpha * 255.0
        sheet[i * n : (i + 1) * n] = frame
    return sheet


def make_flow_sheet() -> np.ndarray:
    n = FLOW_SIZE
    frames = FLOW_FRAMES
    sheet = np.zeros((n * frames, n, 4), dtype=np.float64)
    for i in range(frames):
        t = i / frames
        h = _water_field(n, t, flow=True)
        lum, alpha = _shade_water(h)
        frame = np.zeros((n, n, 4), dtype=np.float64)
        frame[..., 0] = frame[..., 1] = frame[..., 2] = lum * 255.0
        frame[..., 3] = alpha * 255.0
        sheet[i * n : (i + 1) * n] = frame
    return sheet


def make_underwater(size: int = 160) -> np.ndarray:
    """Full-screen underwater overlay — mostly see-through soft tint + faint caustics.

    Vanilla uses a translucent blue wash. Ours was too opaque and read as a
    wallpaper grid over the whole view.
    """
    y, x = np.mgrid[0:size, 0:size]
    u = x / size
    v = y / size
    # Soft, low-contrast caustic flecks (not a dense screen-door grid)
    caust = (
        np.abs(np.sin((u * 7.0 + v * 2.4) * math.pi * 2))
        * np.abs(np.cos((u * 3.2 - v * 8.5) * math.pi * 2))
    )
    caust = caust * 0.55 + _fbm(size, size, 14.0, 4, 2.2) * 0.45
    caust = _smoothstep(0.55, 0.92, caust)  # sparse highlights only
    dx = (u - 0.5) * 2
    dy = (v - 0.5) * 2
    vig = np.clip(dx * dx + dy * dy, 0.0, 1.0) ** 1.15

    rgb = np.zeros((size, size, 4), dtype=np.float64)
    # Pale blue wash — RGB mostly unused when alpha is low; keep mild tint
    rgb[..., 0] = 18 + 22 * caust
    rgb[..., 1] = 72 + 40 * caust
    rgb[..., 2] = 140 + 50 * caust
    # Alpha: light center (~28), slightly denser vignette edges (~48), caustic flecks bump ~8
    rgb[..., 3] = 26.0 + 22.0 * vig + 10.0 * caust
    return rgb


def make_rain(size: int = 64) -> np.ndarray:
    img = np.zeros((size, size, 4), dtype=np.float64)
    rng = np.random.default_rng(42)
    for _ in range(34):
        x = int(rng.integers(0, size))
        y0 = int(rng.integers(0, size))
        length = int(rng.integers(5, 13))
        bright = float(rng.uniform(0.6, 1.0))
        for k in range(length):
            yy = (y0 + k) % size
            fade = 1.0 - (k / length) * 0.35
            img[yy, x, 0] = max(img[yy, x, 0], 195 * bright * fade)
            img[yy, x, 1] = max(img[yy, x, 1], 212 * bright * fade)
            img[yy, x, 2] = max(img[yy, x, 2], 238 * bright * fade)
            img[yy, x, 3] = max(img[yy, x, 3], 58 * bright * fade)
            if length > 8 and x + 1 < size and k % 2 == 0:
                img[yy, x + 1, 0] = max(img[yy, x + 1, 0], 165 * bright)
                img[yy, x + 1, 1] = max(img[yy, x + 1, 1], 180 * bright)
                img[yy, x + 1, 2] = max(img[yy, x + 1, 2], 205 * bright)
                img[yy, x + 1, 3] = max(img[yy, x + 1, 3], 24 * bright)
    return img


def make_snow(size: int = 64) -> np.ndarray:
    img = np.zeros((size, size, 4), dtype=np.float64)
    rng = np.random.default_rng(7)
    yy, xx = np.mgrid[0:size, 0:size]
    for _ in range(48):
        cx = float(rng.uniform(0, size))
        cy = float(rng.uniform(0, size))
        r = float(rng.uniform(0.85, 2.2))
        d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
        flake = _smoothstep(r, r * 0.15, d)
        img[..., :3] = np.maximum(img[..., :3], flake[..., None] * 248)
        img[..., 3] = np.maximum(img[..., 3], flake * 118)
    return img


def make_drip(size: int = 16) -> np.ndarray:
    img = np.zeros((size, size, 4), dtype=np.float64)
    cx = (size - 1) * 0.5
    for y in range(size):
        for x in range(size):
            dx = x - cx
            ty = y / size
            rad = 0.35 * size * (0.35 + 0.9 * ty)
            if abs(dx) <= rad * (1.0 - ty * 0.15) and y > size * 0.15:
                img[y, x, :3] = 205
                img[y, x, 3] = 185 - ty * 45
    return img


def write_mcmeta(path: Path, frametime: int = 2) -> None:
    path.write_text(json.dumps({"animation": {"frametime": frametime}}, indent=2) + "\n")


def main() -> None:
    print("Generating YaP water / weather textures…")
    still = make_still_sheet()
    _save_rgba(BLOCK / "water_still.png", still)
    write_mcmeta(BLOCK / "water_still.png.mcmeta", frametime=2)

    flow = make_flow_sheet()
    _save_rgba(BLOCK / "water_flow.png", flow)
    write_mcmeta(BLOCK / "water_flow.png.mcmeta", frametime=1)

    _save_rgba(MISC / "underwater.png", make_underwater(160))
    # Rain/snow stay Faithful 64x
    for stale in ("rain.png", "snow.png"):
        p = ENV / stale
        if p.exists():
            p.unlink()
            print(f"  removed overlay {stale} (use Faithful)")
    _save_rgba(PARTICLE / "drip_hang.png", make_drip(16))
    _save_rgba(PARTICLE / "drip_fall.png", make_drip(16))
    _save_rgba(PARTICLE / "drip_land.png", make_drip(16))

    print(f"  water_still {STILL_SIZE}x{STILL_SIZE}×{STILL_FRAMES}")
    print(f"  water_flow  {FLOW_SIZE}x{FLOW_SIZE}×{FLOW_FRAMES}")
    print(f"Wrote into {OUT}")


if __name__ == "__main__":
    main()
