#!/usr/bin/env python3
"""Generate YaP Skies textures (sun, moon, clouds, End, OptiFine skyboxes)."""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "resourcepacks" / "yap-skies"
ENV = OUT / "assets/minecraft/textures/environment"
CELESTIAL = ENV / "celestial"
MOON_DIR = CELESTIAL / "moon"
OF0 = OUT / "assets/minecraft/optifine/sky/world0"
OF1 = OUT / "assets/minecraft/optifine/sky/world1"

FACE = 384
SUN = 256
MOON = 256
CLOUDS = 256

MOON_PHASES = [
    "full_moon",
    "waning_gibbous",
    "third_quarter",
    "waning_crescent",
    "new_moon",
    "waxing_crescent",
    "first_quarter",
    "waxing_gibbous",
]


def _save_rgba(path: Path, arr: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(path, optimize=True)


def _save_l(path: Path, arr: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "L").save(path, optimize=True)


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


def make_sun() -> np.ndarray:
    n = SUN
    y, x = np.mgrid[0:n, 0:n]
    cx = cy = (n - 1) * 0.5
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / (n * 0.5)
    disk = _smoothstep(0.20, 0.145, d)
    core = _smoothstep(0.10, 0.0, d)
    corona = np.exp(-np.maximum(d, 1e-4) * 5.2)
    limb = 0.55 + 0.45 * np.sqrt(np.clip(1.0 - (d / 0.18) ** 2, 0.0, 1.0))
    rgb = np.zeros((n, n, 4), dtype=np.float64)
    rgb[..., 0] = 255.0 * (0.55 * corona + disk * limb * (0.95 + 0.05 * core))
    rgb[..., 1] = 255.0 * (0.32 * corona + disk * limb * (0.78 + 0.20 * core))
    rgb[..., 2] = 255.0 * (0.08 * corona + disk * (0.28 + 0.62 * core))
    rgb[..., 3] = 255.0 * np.clip(disk + corona * 0.88, 0.0, 1.0)
    return rgb


def make_moon(phase: str) -> np.ndarray:
    n = MOON
    y, x = np.mgrid[0:n, 0:n]
    cx = cy = (n - 1) * 0.5
    nx = (x - cx) / (n * 0.42)
    ny = (y - cy) / (n * 0.42)
    r2 = nx * nx + ny * ny
    inside = r2 <= 1.0
    nz = np.sqrt(np.clip(1.0 - r2, 0.0, 1.0))
    craters = _fbm(n, n, 14.0, 6, 4.2) * 0.50 + _fbm(n, n, 5.5, 4, 9.1) * 0.40
    rings = _smoothstep(0.22, 0.12, np.abs(_fbm(n, n, 9.0, 2, 6.6) - 0.48))
    albedo = 0.38 + 0.48 * craters - 0.12 * rings
    mares = _smoothstep(0.60, 0.76, _fbm(n, n, 28.0, 3, 2.7))
    albedo *= 1.0 - 0.32 * mares
    albedo = np.clip(albedo, 0.08, 1.0)

    # +X right, +Z toward camera. 0 = full, ±π = new.
    angles = {
        "full_moon": 0.0,
        "waxing_gibbous": math.radians(48),
        "first_quarter": math.radians(90),
        "waxing_crescent": math.radians(142),
        "new_moon": math.pi,
        "waning_crescent": math.radians(-142),
        "third_quarter": math.radians(-90),
        "waning_gibbous": math.radians(-48),
    }
    a = angles[phase]
    lx, ly, lz = math.sin(a), 0.10, math.cos(a)
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    lx, ly, lz = lx / ln, ly / ln, lz / ln
    ndotl = nx * lx + ny * ly + nz * lz
    shade = np.clip(ndotl, 0.0, 1.0)
    shade = shade ** 0.85
    earthshine = 0.035 * np.clip(-ndotl, 0.0, 1.0)
    shade = np.clip(shade + earthshine, 0.0, 1.0)

    rgb = np.zeros((n, n, 4), dtype=np.float64)
    tint = np.array([0.94, 0.92, 0.86])
    lit = albedo[..., None] * shade[..., None] * tint
    rgb[..., :3] = 255.0 * lit
    edge = _smoothstep(1.02, 0.92, np.sqrt(r2))
    rgb[..., 3] = 255.0 * edge * inside
    rgb[..., :3] *= (rgb[..., 3:4] / 255.0)
    return rgb


def make_moon_sheet(phases: dict[str, np.ndarray]) -> np.ndarray:
    tile = MOON
    sheet = np.zeros((tile * 2, tile * 4, 4), dtype=np.float64)
    for i, name in enumerate(MOON_PHASES):
        row, col = divmod(i, 4)
        sheet[row * tile : (row + 1) * tile, col * tile : (col + 1) * tile] = phases[name]
    return sheet


def make_clouds() -> np.ndarray:
    n = CLOUDS
    # White = cloud cell (26.2 occupancy). Multi-scale banks + soft wisps (~32% cover).
    large = _fbm(n, n, 56.0, 4, 1.4)
    mid = _fbm(n, n, 22.0, 5, 7.2)
    # Anisotropic stretch so banks read as wind-blown sheets, not blobs.
    stretch_x = _value_noise(n, n, 28.0, 8.8)
    stretch_y = _value_noise(n, n, 12.0, 14.1)
    field = 0.48 * large + 0.32 * mid + 0.14 * stretch_x + 0.06 * stretch_y
    xs = np.linspace(0, 3 * math.pi, n, endpoint=False)[None, :]
    ys = np.linspace(0, 2 * math.pi, n, endpoint=False)[:, None]
    field += 0.04 * np.sin(xs + 0.35 * ys) + 0.025 * np.sin(ys * 1.7 - xs * 0.4)
    # Carve soft holes so the sky reads through banks.
    holes = _smoothstep(0.58, 0.78, _fbm(n, n, 48.0, 3, 21.5))
    field = field * (1.0 - 0.35 * holes)
    detail = _fbm(n, n, 8.0, 3, 3.3)
    core = field > 0.56
    fringe = (field > 0.50) & (field <= 0.56) & (detail > 0.55)
    wisps = (field > 0.46) & (field <= 0.50) & (detail > 0.72)
    out = np.zeros((n, n), dtype=np.float64)
    out[core | fringe | wisps] = 255.0
    return out


def make_end_sky() -> np.ndarray:
    n = 256
    neb = _fbm(n, n, 40.0, 5, 11.0)
    stars = _hash2(
        np.arange(n, dtype=np.float64)[None, :],
        np.arange(n, dtype=np.float64)[:, None],
        21.0,
    )
    rgb = np.zeros((n, n, 4), dtype=np.float64)
    rgb[..., 0] = 255.0 * (0.10 + 0.35 * neb)
    rgb[..., 1] = 255.0 * (0.02 + 0.10 * neb)
    rgb[..., 2] = 255.0 * (0.14 + 0.42 * neb)
    spark = stars > 0.992
    rgb[..., :3] += spark[..., None] * 180.0
    rgb[..., 3] = 255.0
    return rgb


def _cube_dirs(face: int, size: int) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    t = (np.arange(size, dtype=np.float64) + 0.5) / size * 2.0 - 1.0
    v, u = np.meshgrid(t, t, indexing="ij")
    # OptiFine 3×2: bottom, top, back / left, front, right
    if face == 0:  # -Y
        return u, np.full_like(u, -1.0), v
    if face == 1:  # +Y
        return u, np.full_like(u, 1.0), -v
    if face == 2:  # -Z
        return u, -v, np.full_like(u, -1.0)
    if face == 3:  # -X
        return np.full_like(u, -1.0), -v, -u
    if face == 4:  # +Z
        return -u, -v, np.full_like(u, 1.0)
    return np.full_like(u, 1.0), -v, u  # +X


def _normalize(x: np.ndarray, y: np.ndarray, z: np.ndarray):
    n = np.sqrt(x * x + y * y + z * z)
    return x / n, y / n, z / n


def _sample_equirect(tex: np.ndarray, dx, dy, dz) -> np.ndarray:
    h, w = tex.shape
    u = (np.arctan2(dz, dx) / (2.0 * math.pi) + 0.5) * (w - 1)
    v = (np.arccos(np.clip(dy, -1.0, 1.0)) / math.pi) * (h - 1)
    x0 = np.floor(u).astype(np.int32) % w
    y0 = np.clip(np.floor(v).astype(np.int32), 0, h - 2)
    x1 = (x0 + 1) % w
    y1 = y0 + 1
    fx = u - np.floor(u)
    fy = v - np.floor(v)
    n00 = tex[y0, x0]
    n10 = tex[y0, x1]
    n01 = tex[y1, x0]
    n11 = tex[y1, x1]
    return n00 * (1 - fx) * (1 - fy) + n10 * fx * (1 - fy) + n01 * (1 - fx) * fy + n11 * fx * fy


def _atmosphere(dx, dy, dz, mode: str, cloud_tex: np.ndarray, wisp_tex: np.ndarray) -> np.ndarray:
    up = np.clip(dy, 0.0, 1.0)
    hz = np.exp(-up * 4.4)
    clouds = _sample_equirect(cloud_tex, dx, dy, dz)
    wisps = _sample_equirect(wisp_tex, dx, dy, dz)
    # Soft undersides + lit tops for depth in the baked dome.
    underside = _smoothstep(0.08, 0.42, up)
    if mode == "day":
        zenith = np.array([0.20, 0.48, 0.94])
        horizon = np.array([0.74, 0.84, 0.96])
        sun = np.array([1.0, 0.78, 0.42])
        glow = np.exp(-((dx - 0.62) ** 2 + (dy - 0.22) ** 2 + (dz + 0.12) ** 2) * 5.5)
        cloud_lit = np.array([0.96, 0.97, 0.99])
        cloud_shade = np.array([0.62, 0.68, 0.78])
        cloud_col = cloud_shade + (cloud_lit - cloud_shade) * underside[..., None]
        cloud_amt = np.clip(clouds * 1.15 + wisps * 0.35, 0.0, 1.0)
        cloud_amt = cloud_amt * (0.28 + 0.42 * hz) * (0.25 + 0.75 * up)
        cloud_blend = 0.72
    elif mode == "sunrise":
        zenith = np.array([0.16, 0.26, 0.52])
        horizon = np.array([0.99, 0.58, 0.30])
        sun = np.array([1.0, 0.74, 0.32])
        glow = np.exp(-((dx - 0.88) ** 2 + (dy - 0.04) ** 2 + dz**2) * 3.2)
        cloud_lit = np.array([1.0, 0.72, 0.42])
        cloud_shade = np.array([0.55, 0.28, 0.32])
        cloud_col = cloud_shade + (cloud_lit - cloud_shade) * (0.35 + 0.65 * underside)[..., None]
        cloud_amt = np.clip(clouds * 1.2 + wisps * 0.4, 0.0, 1.0) * (0.30 + 0.48 * hz)
        cloud_blend = 0.78
    elif mode == "sunset":
        zenith = np.array([0.10, 0.12, 0.36])
        horizon = np.array([0.96, 0.30, 0.16])
        sun = np.array([1.0, 0.42, 0.10])
        glow = np.exp(-((dx + 0.88) ** 2 + (dy - 0.04) ** 2 + dz**2) * 3.2)
        cloud_lit = np.array([1.0, 0.48, 0.22])
        cloud_shade = np.array([0.42, 0.16, 0.18])
        cloud_col = cloud_shade + (cloud_lit - cloud_shade) * (0.35 + 0.65 * underside)[..., None]
        cloud_amt = np.clip(clouds * 1.2 + wisps * 0.4, 0.0, 1.0) * (0.30 + 0.50 * hz)
        cloud_blend = 0.78
    elif mode == "storm":
        zenith = np.array([0.22, 0.26, 0.30])
        horizon = np.array([0.36, 0.38, 0.40])
        sun = np.array([0.42, 0.42, 0.40])
        glow = np.zeros_like(up)
        cloud_col = np.array([0.18, 0.20, 0.22]) + underside[..., None] * np.array([0.10, 0.11, 0.12])
        cloud_amt = np.clip(clouds * 1.45 + wisps * 0.55, 0.0, 1.0) * (0.70 + 0.28 * up)
        cloud_blend = 0.88
    elif mode == "end":
        zenith = np.array([0.05, 0.02, 0.09])
        horizon = np.array([0.22, 0.04, 0.28])
        sun = np.array([0.55, 0.12, 0.70])
        glow = np.exp(-((dx * 0.3 + dz) ** 2 + (dy - 0.2) ** 2) * 2.0) * 0.4
        cloud_col = np.array([0.40, 0.08, 0.48])
        cloud_amt = wisps * 0.28 * up
        cloud_blend = 0.55
    else:
        zenith = np.array([0.008, 0.015, 0.05])
        horizon = np.array([0.035, 0.045, 0.09])
        sun = np.array([0.16, 0.20, 0.32])
        glow = np.exp(-((dx + 0.2) ** 2 + (dy - 0.35) ** 2) * 8.0) * 0.25
        cloud_col = np.array([0.07, 0.08, 0.11]) + underside[..., None] * 0.04
        cloud_amt = np.clip(clouds * 0.95 + wisps * 0.25, 0.0, 1.0) * (0.18 + 0.22 * up)
        cloud_blend = 0.62

    # Fade clouds near cube poles so equirect samples do not pinch.
    cloud_amt = cloud_amt * (1.0 - _smoothstep(0.72, 0.94, np.abs(dy)))
    col = zenith + (horizon - zenith) * hz[..., None]
    col = col + sun * glow[..., None]
    col = col * (1.0 - cloud_blend * cloud_amt)[..., None] + cloud_col * (cloud_blend * cloud_amt)[..., None]
    if mode == "night":
        h = np.mod(np.sin(dx * 812.1 + dy * 311.7 + dz * 197.3) * 43758.5453, 1.0)
        col = col + (h > 0.996)[..., None] * 0.90
        band = np.exp(-((dx * 0.35 + dy * 0.15 + dz) ** 2) * 8.0)
        col = col + band[..., None] * np.array([0.12, 0.13, 0.20])
        h2 = np.mod(np.sin(dx * 191.7 + dy * 74.2 + dz * 501.3) * 24634.91, 1.0)
        col = col + (h2 > 0.9985)[..., None] * 1.0
    if mode == "end":
        h = np.mod(np.sin(dx * 401.2 + dy * 88.1 + dz * 266.4) * 31337.1, 1.0)
        col = col + (h > 0.994)[..., None] * 0.55
    return np.clip(col, 0.0, 1.0)


def make_skybox(mode: str, cloud_tex: np.ndarray, wisp_tex: np.ndarray) -> np.ndarray:
    s = FACE
    out = np.zeros((s * 2, s * 3, 4), dtype=np.float64)
    slots = [(0, 0), (0, 1), (0, 2), (1, 0), (1, 1), (1, 2)]
    for face, (row, col) in enumerate(slots):
        dx, dy, dz = _normalize(*_cube_dirs(face, s))
        rgb = _atmosphere(dx, dy, dz, mode, cloud_tex, wisp_tex)
        tile = np.zeros((s, s, 4), dtype=np.float64)
        tile[..., :3] = rgb * 255.0
        tile[..., 3] = 255.0
        if mode in ("sunrise", "sunset"):
            lum = rgb.max(axis=2)
            tile[..., 3] = 255.0 * np.clip(lum * 1.35, 0.0, 1.0)
        out[row * s : (row + 1) * s, col * s : (col + 1) * s] = tile
    return out


def main() -> None:
    print("Generating YaP Skies…")
    sun = make_sun()
    _save_rgba(CELESTIAL / "sun.png", sun)
    _save_rgba(OF0 / "sun.png", sun)

    phases = {name: make_moon(name) for name in MOON_PHASES}
    for name, img in phases.items():
        _save_rgba(MOON_DIR / f"{name}.png", img)
    sheet = make_moon_sheet(phases)
    _save_rgba(ENV / "moon_phases.png", sheet)
    _save_rgba(OF0 / "moon_phases.png", sheet)

    _save_l(ENV / "clouds.png", make_clouds())
    _save_rgba(ENV / "end_sky.png", make_end_sky())

    # Larger banks + finer wisps for richer panoramic cloud cover.
    banks = _fbm(384, 192, 28.0, 5, 12.4)
    ripples = _fbm(384, 192, 11.0, 4, 18.2)
    cloud_tex = _smoothstep(0.46, 0.70, 0.72 * banks + 0.28 * ripples)
    wisp_tex = _smoothstep(0.40, 0.78, _fbm(384, 192, 16.0, 5, 19.7))
    boxes = {
        "day": "day",
        "sunrise": "sunrise",
        "sunset": "sunset",
        "night": "night",
        "storm": "storm",
    }
    for name, mode in boxes.items():
        print(f"  skybox {name}")
        _save_rgba(OF0 / f"{name}.png", make_skybox(mode, cloud_tex, wisp_tex))
    _save_rgba(OF1 / "end.png", make_skybox("end", cloud_tex, wisp_tex))
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
