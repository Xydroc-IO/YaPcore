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
STILL_FRAMES = 48
FLOW_SIZE = 128  # Faithful-compatible frame size
FLOW_FRAMES = 48


def _save_rgba(path: Path, arr: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(path, optimize=True)


def _hash2(x: np.ndarray, y: np.ndarray, seed: float) -> np.ndarray:
    return np.mod(np.sin(x * 127.1 + y * 311.7 + seed * 19.19) * 43758.5453, 1.0)


def _value_noise(w: int, h: int, scale: float, seed: float) -> np.ndarray:
    gy = max(2, int(h / scale) + 2)
    gx = max(2, int(w / scale) + 2)
    grid = _hash2(
        np.arange(gx, dtype=np.float64)[None, :],
        np.arange(gy, dtype=np.float64)[:, None],
        seed,
    )
    ys = np.linspace(0, gy - 2, h)
    xs = np.linspace(0, gx - 2, w)
    y0 = np.floor(ys).astype(np.int32)
    x0 = np.floor(xs).astype(np.int32)
    fy = (ys - y0)[:, None]
    fx = (xs - x0)[None, :]
    fy = fy * fy * (3.0 - 2.0 * fy)
    fx = fx * fx * (3.0 - 2.0 * fx)
    n00 = grid[y0[:, None], x0[None, :]]
    n10 = grid[y0[:, None], x0[None, :] + 1]
    n01 = grid[y0[:, None] + 1, x0[None, :]]
    n11 = grid[y0[:, None] + 1, x0[None, :] + 1]
    return n00 * (1 - fx) * (1 - fy) + n10 * fx * (1 - fy) + n01 * (1 - fx) * fy + n11 * fx * fy


def _fbm(w: int, h: int, scale: float, octaves: int, seed: float) -> np.ndarray:
    acc = np.zeros((h, w), dtype=np.float64)
    amp = 0.5
    total = 0.0
    s = scale
    for i in range(octaves):
        acc += amp * _value_noise(w, h, s, seed + i * 17.3)
        total += amp
        amp *= 0.5
        s *= 0.5
    return acc / total


def _smoothstep(edge0: float, edge1: float, x: np.ndarray) -> np.ndarray:
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def _water_field(n: int, t: float, flow: bool) -> np.ndarray:
    """Unit height field in [0,1] for one animation frame — soft natural ripples."""
    y, x = np.mgrid[0:n, 0:n]
    u = x / n
    v = y / n
    if flow:
        w = (
            0.32 * np.sin((u * 4.2 + v * 0.9 + t * 1.6) * math.pi * 2)
            + 0.24 * np.sin((u * 2.1 - v * 3.4 + t * 1.15) * math.pi * 2)
            + 0.16 * np.sin((u * 8.5 + v * 6.0 - t * 2.0) * math.pi * 2)
            + 0.12 * np.sin((u * 1.4 + v * 10.0 + t * 0.85) * math.pi * 2)
        )
        streak = _fbm(n, n, 14.0, 5, 3.1 + t * 0.15)
        w = w * 0.7 + (streak - 0.5) * 0.35
        w = w + 0.08 * np.sin((v * 12.0 - t * 2.6) * math.pi * 2)
    else:
        # Broad swell + fine capillary noise (not loud sine ridges)
        w = (
            0.28 * np.sin((u * 2.8 + v * 1.1 + t * 0.7) * math.pi * 2)
            + 0.22 * np.sin((u * 1.3 + v * 3.4 - t * 0.55) * math.pi * 2)
            + 0.14 * np.sin((u * 6.5 - v * 4.8 + t * 1.25) * math.pi * 2)
            + 0.10 * np.sin(((u + v) * 5.2 + t * 0.5) * math.pi * 2)
        )
        capillary = _fbm(n, n, 18.0, 6, 7.7 + t * 0.4)
        w = w * 0.55 + (capillary - 0.5) * 0.9
    w = (w - w.min()) / max(float(w.max() - w.min()), 1e-6)
    return w


def _shade_water(height: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Soft grayscale + alpha — shaders own the specular; texture is microdetail."""
    gy, gx = np.gradient(height)
    ndotl = np.clip(0.55 - gx * 1.4 + gy * 0.9, 0.0, 1.0)
    # Very soft crest hint (avoid plastic ridges baked into the pack)
    crest = _smoothstep(0.78, 0.96, height) * ndotl
    lum = 0.48 + 0.22 * height + 0.16 * ndotl + 0.08 * crest
    lum = np.clip(lum, 0.32, 0.78)
    alpha = 0.38 + 0.22 * (1.0 - height * 0.4) + 0.05 * ndotl
    alpha = np.clip(alpha, 0.32, 0.62)
    return lum, alpha


def make_still_sheet() -> np.ndarray:
    n = STILL_SIZE
    frames = STILL_FRAMES
    sheet = np.zeros((n * frames, n, 4), dtype=np.float64)
    for i in range(frames):
        t = i / frames
        h = _water_field(n, t, flow=False)
        lum, alpha = _shade_water(h)
        # Soft tile seam blend
        edge = np.minimum(np.minimum(np.arange(n), n - 1 - np.arange(n))[None, :],
                          np.minimum(np.arange(n), n - 1 - np.arange(n))[:, None])
        seam = _smoothstep(0.0, 3.0, edge.astype(np.float64))
        lum = lum * (0.85 + 0.15 * seam)
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


def make_underwater(size: int = 128) -> np.ndarray:
    """Full-screen underwater overlay — soft caustics + depth blue."""
    y, x = np.mgrid[0:size, 0:size]
    u = x / size
    v = y / size
    caust = (
        np.abs(np.sin((u * 9.0 + v * 3.0) * math.pi * 2))
        * np.abs(np.cos((u * 4.0 - v * 11.0) * math.pi * 2))
    )
    caust = caust * 0.55 + _fbm(size, size, 12.0, 4, 2.2) * 0.45
    caust = _smoothstep(0.35, 0.85, caust)
    # Vignette / depth
    dx = (u - 0.5) * 2
    dy = (v - 0.5) * 2
    vig = np.clip(dx * dx + dy * dy, 0.0, 1.0)
    rgb = np.zeros((size, size, 4), dtype=np.float64)
    rgb[..., 0] = 18 + 40 * caust + 10 * (1 - vig)
    rgb[..., 1] = 70 + 90 * caust
    rgb[..., 2] = 170 + 70 * caust
    rgb[..., 3] = (95 + 55 * vig + 25 * caust) * (0.85 + 0.15 * (1 - vig))
    return rgb


def make_rain(size: int = 64) -> np.ndarray:
    img = np.zeros((size, size, 4), dtype=np.float64)
    rng = np.random.default_rng(42)
    for _ in range(90):
        x = int(rng.integers(0, size))
        y0 = int(rng.integers(0, size))
        length = int(rng.integers(6, 18))
        bright = float(rng.uniform(0.55, 0.95))
        for k in range(length):
            yy = (y0 + k) % size
            img[yy, x, :3] = 220 * bright
            img[yy, x, 3] = 140 * bright
            if x + 1 < size:
                img[yy, x + 1, :3] = 180 * bright
                img[yy, x + 1, 3] = 70 * bright
    return img


def make_snow(size: int = 64) -> np.ndarray:
    img = np.zeros((size, size, 4), dtype=np.float64)
    rng = np.random.default_rng(7)
    yy, xx = np.mgrid[0:size, 0:size]
    for _ in range(55):
        cx = float(rng.uniform(0, size))
        cy = float(rng.uniform(0, size))
        r = float(rng.uniform(1.2, 2.8))
        d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
        flake = _smoothstep(r, r * 0.2, d)
        img[..., :3] = np.maximum(img[..., :3], flake[..., None] * 245)
        img[..., 3] = np.maximum(img[..., 3], flake * 200)
    return img


def make_drip(size: int = 16) -> np.ndarray:
    img = np.zeros((size, size, 4), dtype=np.float64)
    cx = (size - 1) * 0.5
    for y in range(size):
        for x in range(size):
            dx = x - cx
            # Teardrop
            ty = y / size
            rad = 0.35 * size * (0.35 + 0.9 * ty)
            if abs(dx) <= rad * (1.0 - ty * 0.15) and y > size * 0.15:
                img[y, x, :3] = 200
                img[y, x, 3] = 180 - ty * 40
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

    _save_rgba(MISC / "underwater.png", make_underwater(128))
    _save_rgba(ENV / "rain.png", make_rain(64))
    _save_rgba(ENV / "snow.png", make_snow(64))
    _save_rgba(PARTICLE / "drip_hang.png", make_drip(16))
    _save_rgba(PARTICLE / "drip_fall.png", make_drip(16))
    _save_rgba(PARTICLE / "drip_land.png", make_drip(16))

    print(f"  water_still {STILL_SIZE}x{STILL_SIZE}×{STILL_FRAMES}")
    print(f"  water_flow  {FLOW_SIZE}x{FLOW_SIZE}×{FLOW_FRAMES}")
    print(f"Wrote into {OUT}")


if __name__ == "__main__":
    main()
