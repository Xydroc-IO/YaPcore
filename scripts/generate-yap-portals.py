#!/usr/bin/env python3
"""Generate YaP colored portal textures for stained glass / panes.

Vanilla only ships one nether-portal look. Multi-color fleet portals need either
a resource pack or particles. This script paints first-party procedural portal
sheets (animated swirl) onto each dye-colored stained glass (+ pane) texture so
YaPPortals can place matching blocks / displays without third-party packs.

Output: resourcepacks/yap-portals/assets/minecraft/textures/block/
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "resourcepacks" / "yap-portals"
BLOCK = OUT / "assets/minecraft/textures/block"

# Match PortalColors + vanilla dye names
DYE_RGB: dict[str, tuple[int, int, int]] = {
    "white": (249, 255, 254),
    "orange": (249, 128, 29),
    "magenta": (199, 78, 189),
    "light_blue": (58, 179, 218),
    "yellow": (254, 216, 61),
    "lime": (128, 199, 31),
    "pink": (243, 139, 170),
    "gray": (71, 79, 82),
    "light_gray": (157, 157, 151),
    "cyan": (22, 156, 156),
    "purple": (137, 50, 184),
    "blue": (60, 68, 170),
    "brown": (131, 84, 50),
    "green": (94, 124, 22),
    "red": (176, 46, 38),
    "black": (29, 29, 33),
}

SIZE = 64
FRAMES = 32
FRAMETIME = 2


def _save_rgba(path: Path, arr: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(path, optimize=True)


def _hash2(x: np.ndarray, y: np.ndarray, seed: float) -> np.ndarray:
    return np.mod(np.sin(x * 127.1 + y * 311.7 + seed * 19.19) * 43758.5453, 1.0)


def _fbm(w: int, h: int, seed: float, octaves: int = 4) -> np.ndarray:
    acc = np.zeros((h, w), dtype=np.float64)
    amp = 0.5
    total = 0.0
    scale = 4.0
    for i in range(octaves):
        ys = np.linspace(0, scale, h, endpoint=False)
        xs = np.linspace(0, scale, w, endpoint=False)
        y0 = np.floor(ys).astype(np.int32)
        x0 = np.floor(xs).astype(np.int32)
        fy = (ys - y0)[:, None]
        fx = (xs - x0)[None, :]
        fy = fy * fy * (3.0 - 2.0 * fy)
        fx = fx * fx * (3.0 - 2.0 * fx)
        period = max(2, int(math.ceil(scale)) + 2)
        grid = _hash2(
            np.arange(period, dtype=np.float64)[None, :],
            np.arange(period, dtype=np.float64)[:, None],
            seed + i * 13.7,
        )
        x1 = (x0 + 1) % period
        y1 = (y0 + 1) % period
        n00 = grid[y0[:, None] % period, x0[None, :] % period]
        n10 = grid[y0[:, None] % period, x1[None, :] % period]
        n01 = grid[y1[:, None] % period, x0[None, :] % period]
        n11 = grid[y1[:, None] % period, x1[None, :] % period]
        n = n00 * (1 - fx) * (1 - fy) + n10 * fx * (1 - fy) + n01 * (1 - fx) * fy + n11 * fx * fy
        acc += amp * n
        total += amp
        amp *= 0.5
        scale *= 2.0
    return acc / total


def _portal_frame(rgb: tuple[int, int, int], frame: int) -> np.ndarray:
    """One animated portal sheet frame — translucent swirl like nether portal."""
    h = w = SIZE
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64)
    # Vertical flow (classic portal)
    t = frame / FRAMES
    flow = (yy / h + t) % 1.0
    swirl = _fbm(w, h, seed=rgb[0] * 0.1 + frame * 0.37)
    ribbons = np.sin((xx / w) * math.pi * 6.0 + swirl * 8.0 + t * math.pi * 2.0)
    ribbons = 0.55 + 0.45 * ribbons
    edge = np.clip(1.0 - abs(xx / (w - 1) - 0.5) * 2.2, 0.0, 1.0)
    edge *= np.clip(1.0 - abs(yy / (h - 1) - 0.5) * 0.15, 0.75, 1.0)
    glow = ribbons * (0.45 + 0.55 * flow) * edge
    spark = (_fbm(w, h, seed=99.0 + frame) > 0.78).astype(np.float64) * 0.35

    r0, g0, b0 = [c / 255.0 for c in rgb]
    # Brighten midtones toward white for portal "energy"
    r = np.clip(r0 * 0.55 + glow * 0.9 + spark, 0, 1)
    g = np.clip(g0 * 0.55 + glow * 0.75 + spark, 0, 1)
    b = np.clip(b0 * 0.55 + glow * 1.05 + spark, 0, 1)
    a = np.clip(0.35 + glow * 0.55 + spark * 0.2, 0.25, 0.92) * edge

    out = np.zeros((h, w, 4), dtype=np.float64)
    out[..., 0] = r * 255
    out[..., 1] = g * 255
    out[..., 2] = b * 255
    out[..., 3] = a * 255
    return out


def _write_animated(path: Path, rgb: tuple[int, int, int]) -> None:
    strip = np.zeros((SIZE * FRAMES, SIZE, 4), dtype=np.float64)
    for i in range(FRAMES):
        strip[i * SIZE : (i + 1) * SIZE] = _portal_frame(rgb, i)
    _save_rgba(path, strip)
    mcmeta = {
        "animation": {
            "frametime": FRAMETIME,
            "interpolate": True,
        }
    }
    path.with_suffix(path.suffix + ".mcmeta").write_text(
        json.dumps(mcmeta, indent=2) + "\n", encoding="utf-8"
    )


def main() -> None:
    BLOCK.mkdir(parents=True, exist_ok=True)
    pack_meta = OUT / "pack.mcmeta"
    pack_meta.parent.mkdir(parents=True, exist_ok=True)
    pack_meta.write_text(
        json.dumps(
            {
                "pack": {
                    "description": "YaP Portals — colored portal sheets for stained glass",
                    "pack_format": 88,
                    "min_format": [88, 0],
                    "max_format": [88, 0],
                }
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    for name, rgb in DYE_RGB.items():
        _write_animated(BLOCK / f"{name}_stained_glass.png", rgb)
        _write_animated(BLOCK / f"{name}_stained_glass_pane_top.png", rgb)
        # Side pane uses same sheet (vanilla looks for pane texture names)
        side = BLOCK / f"{name}_stained_glass_pane.png"
        if not side.exists():
            # Some versions only use top; still write a side alias for older models
            _write_animated(side, rgb)
        print(f"  portal texture {name}")

    print(f"Wrote YaP portal textures → {OUT}")


if __name__ == "__main__":
    main()
