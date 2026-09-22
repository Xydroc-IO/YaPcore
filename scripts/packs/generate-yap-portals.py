#!/usr/bin/env python3
"""Generate YaP cinematic portal textures + warp sounds for stained glass / panes.

Vanilla only ships one nether-portal look. Multi-color fleet portals need either
a resource pack or particles. This script paints a tileable, time-looping warp
onto each dye-colored stained glass (+ pane) texture so YaPPortals can cut that
same flowing surface into any block mask (oval, ring, arch, or painted custom).

Also writes custom sounds under assets/yap/ for transfer / arrival / ambient hum.

Output: resourcepacks/yap-portals/
"""
from __future__ import annotations

import json
import math
import shutil
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "resourcepacks" / "yap-portals"
BLOCK = OUT / "assets/minecraft/textures/block"
SOUND_DIR = OUT / "assets/yap/sounds/portal"
SOUNDS_JSON = OUT / "assets/yap/sounds.json"

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
FRAMES = 64
FRAMETIME = 1
_TAU = math.tau
SAMPLE_RATE = 44100


def _save_rgba(path: Path, arr: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(path, optimize=True)


def _fade(x: np.ndarray) -> np.ndarray:
    return x * x * x * (x * (x * 6.0 - 15.0) + 10.0)


def _hash2(ix: np.ndarray, iy: np.ndarray, cells: int, seed: int) -> np.ndarray:
    ix = np.mod(ix, cells).astype(np.int64)
    iy = np.mod(iy, cells).astype(np.int64)
    n = (ix * 374761393 + iy * 668265263 + int(seed) * 1440662683) & 0x7FFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0x7FFFFFFF
    return n.astype(np.float64) / 0x7FFFFFFF


def _value_noise(u: np.ndarray, v: np.ndarray, cells: int, seed: int) -> np.ndarray:
    """Smooth value noise that repeats every 1.0 in u and v."""
    x = u * cells
    y = v * cells
    x0 = np.floor(x).astype(np.int64)
    y0 = np.floor(y).astype(np.int64)
    fx = _fade(x - x0)
    fy = _fade(y - y0)
    n00 = _hash2(x0, y0, cells, seed)
    n10 = _hash2(x0 + 1, y0, cells, seed)
    n01 = _hash2(x0, y0 + 1, cells, seed)
    n11 = _hash2(x0 + 1, y0 + 1, cells, seed)
    return (
        n00 * (1.0 - fx) * (1.0 - fy)
        + n10 * fx * (1.0 - fy)
        + n01 * (1.0 - fx) * fy
        + n11 * fx * fy
    )


def _curl(u: np.ndarray, v: np.ndarray, cells: int, seed: int) -> tuple[np.ndarray, np.ndarray]:
    """2D curl of wrapping noise — local swirls that still match across tile edges."""
    eps = 1.0 / SIZE
    phi_u = _value_noise(u + eps, v, cells, seed) - _value_noise(u - eps, v, cells, seed)
    phi_v = _value_noise(u, v + eps, cells, seed) - _value_noise(u, v - eps, cells, seed)
    inv = 0.5 / eps
    return phi_v * inv, -phi_u * inv


def _write_clear_glass(path: Path) -> None:
    """Glass stays in the world for collision. The picture is the one display disc.

    A repeating swirl on every block is what made the opening look tiled.
    """
    _save_rgba(path, np.zeros((16, 16, 4), dtype=np.float64))
    path.with_suffix(path.suffix + ".mcmeta").write_text(
        json.dumps({"animation": {"frametime": 1, "interpolate": False}}, indent=2) + "\n",
        encoding="utf-8",
    )


def _envelope(n: int, attack: float, release: float) -> np.ndarray:
    env = np.ones(n, dtype=np.float64)
    a = max(1, int(n * attack))
    r = max(1, int(n * release))
    env[:a] = np.linspace(0.0, 1.0, a, endpoint=False)
    env[-r:] = np.linspace(1.0, 0.0, r, endpoint=True)
    return env


def _synth_warp() -> np.ndarray:
    """Whoosh + descending tone — cinematic transfer."""
    duration = 1.35
    n = int(SAMPLE_RATE * duration)
    t = np.arange(n, dtype=np.float64) / SAMPLE_RATE
    # Falling carrier (warp stretch).
    freq = 880.0 * (0.22 ** t)
    phase = np.cumsum(freq) / SAMPLE_RATE * _TAU
    tone = 0.35 * np.sin(phase) + 0.18 * np.sin(phase * 1.5)
    # Noise whoosh shaped by a rising-then-falling band.
    rng = np.random.default_rng(42)
    noise = rng.standard_normal(n)
    # Cheap band emphasis via cumulative differencing.
    whoosh = np.cumsum(noise)
    whoosh = whoosh / (np.max(np.abs(whoosh)) + 1e-9)
    whoosh *= _envelope(n, 0.08, 0.45) * 0.55
    # Sub thump at the start.
    sub = 0.45 * np.sin(_TAU * 55.0 * t) * np.exp(-4.5 * t)
    # High shimmer sparkles.
    shimmer = 0.12 * np.sin(_TAU * (2400.0 - 900.0 * t) * t) * np.exp(-2.2 * t)
    sig = (tone * _envelope(n, 0.02, 0.55) + whoosh + sub + shimmer) * 0.85
    return np.clip(sig, -1.0, 1.0)


def _synth_arrive() -> np.ndarray:
    """Soft impact + rising shimmer — land on the other side."""
    duration = 0.95
    n = int(SAMPLE_RATE * duration)
    t = np.arange(n, dtype=np.float64) / SAMPLE_RATE
    rng = np.random.default_rng(7)
    boom = 0.5 * np.sin(_TAU * 70.0 * t) * np.exp(-6.0 * t)
    rise_freq = 220.0 * (4.0 ** t)
    phase = np.cumsum(rise_freq) / SAMPLE_RATE * _TAU
    rise = 0.28 * np.sin(phase) * _envelope(n, 0.05, 0.4)
    sparkle = 0.1 * rng.standard_normal(n) * np.exp(-3.0 * t)
    sig = (boom + rise + sparkle) * 0.9
    return np.clip(sig, -1.0, 1.0)


def _synth_hum() -> np.ndarray:
    """Loopable low portal drone for ambient pads."""
    duration = 2.0
    n = int(SAMPLE_RATE * duration)
    t = np.arange(n, dtype=np.float64) / SAMPLE_RATE
    base = 0.22 * np.sin(_TAU * 72.0 * t)
    fifth = 0.12 * np.sin(_TAU * 108.0 * t + 0.3)
    wobble = 0.08 * np.sin(_TAU * 0.5 * t) * np.sin(_TAU * 180.0 * t)
    # Crossfade ends for seamless loop.
    fade = 0.08
    env = np.ones(n, dtype=np.float64)
    k = int(n * fade)
    ramp = np.linspace(0.0, 1.0, k, endpoint=False)
    env[:k] = ramp
    env[-k:] = ramp[::-1]
    sig = (base + fifth + wobble) * env * 0.7
    return np.clip(sig, -1.0, 1.0)


def _write_wav(path: Path, samples: np.ndarray) -> None:
    pcm = (samples * 32767.0).astype(np.int16)
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(pcm.tobytes())


def _wav_to_ogg(wav: Path, ogg: Path) -> None:
    ogg.parent.mkdir(parents=True, exist_ok=True)
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        raise RuntimeError("ffmpeg required to encode portal sounds")
    subprocess.run(
        [
            ffmpeg,
            "-y",
            "-i",
            str(wav),
            "-c:a",
            "libvorbis",
            "-q:a",
            "4",
            str(ogg),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def _write_sounds() -> None:
    generators = {
        "warp": _synth_warp,
        "arrive": _synth_arrive,
        "hum": _synth_hum,
    }
    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        for name, gen in generators.items():
            wav = tmp_path / f"{name}.wav"
            ogg = SOUND_DIR / f"{name}.ogg"
            _write_wav(wav, gen())
            _wav_to_ogg(wav, ogg)
            print(f"  portal sound {name}")

    SOUNDS_JSON.parent.mkdir(parents=True, exist_ok=True)
    SOUNDS_JSON.write_text(
        json.dumps(
            {
                "portal.warp": {
                    "sounds": [{"name": "yap:portal/warp", "stream": False}],
                    "subtitle": "subtitles.yap.portal.warp",
                },
                "portal.arrive": {
                    "sounds": [{"name": "yap:portal/arrive", "stream": False}],
                    "subtitle": "subtitles.yap.portal.arrive",
                },
                "portal.hum": {
                    "sounds": [{"name": "yap:portal/hum", "stream": True}],
                    "subtitle": "subtitles.yap.portal.hum",
                },
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


# One crisp disc per dye. Bulk color is the bright dye; a thin dark spiral sits on top.
SHEET_SIZE = 64
SHEET_FRAMES = 32


def _disc_index(spin: float) -> np.ndarray:
    """64×64 portal. The whole disc is the swirl: thick color bands, not a flat tint."""
    n = SHEET_SIZE
    yy, xx = np.mgrid[0:n, 0:n]
    x = (xx + 0.5) / n * 2.0 - 1.0
    y = ((n - 1 - yy) + 0.5) / n * 2.0 - 1.0
    r = np.hypot(x, y)
    ang = np.arctan2(y, x) + spin
    sector = np.floor((ang + math.pi) / (math.pi / 8.0)).astype(np.int32) % 16
    bumps = np.array([0, 2, -1, 1, -2, 2, 0, -1, 2, 1, -2, 0, 1, -1, 2, -2], dtype=np.float64)
    rim = 0.86 + bumps[sector] * (1.6 / (n / 2.0))
    inside = r <= rim
    # Two fat arms winding out from the middle.
    arm = (r - 0.05) / 0.07
    phase = np.mod(ang * 2.0 - arm, _TAU) / _TAU
    idx = np.zeros((n, n), dtype=np.uint8)
    idx[inside] = 1
    idx[inside & (phase >= 0.42) & (phase < 0.72)] = 2
    idx[inside & (phase >= 0.72)] = 3
    idx[inside & (phase < 0.10)] = 4
    idx[inside & (phase >= 0.10) & (phase < 0.18)] = 3
    idx[r <= 0.07] = 4
    return idx


def _portal_palette(rgb: tuple[int, int, int]) -> np.ndarray:
    """Bright body, dark groove, pale highlight, white sparks. Same hue as the dye."""
    r, g, b = [c / 255.0 for c in rgb]
    peak = max(r, g, b, 1e-4)
    # Lift muddy dyes so the disc reads as a portal gun color.
    body = [min(1.0, c / peak * 0.92) for c in (r, g, b)]
    dark = [c * 0.28 for c in body]
    pale = [min(1.0, c * 0.55 + 0.45) for c in body]
    colors = [(0, 0, 0), dark, body, pale, (1, 1, 1)]
    return (np.array(colors) * 255.0).astype(np.uint8)


def _write_cohesive_sheet() -> None:
    """One still disc per dye. The server spins that picture, the way a portal gun does.

    The blocks atlas only stitches textures/item and textures/block. A file under
    textures/portal never reaches the model, so the disc draws as the missing texture.
    """
    frame = _disc_index(0.0)
    tex_dir = OUT / "assets/yap/textures/item"
    model_dir = OUT / "assets/yap/models/item"
    item_dir = OUT / "assets/yap/items/portal"
    for folder in (tex_dir, model_dir, item_dir):
        folder.mkdir(parents=True, exist_ok=True)
    for name, rgb in DYE_RGB.items():
        palette = _portal_palette(rgb)
        rgb_img = palette[frame]
        image = np.zeros((SHEET_SIZE, SHEET_SIZE, 4), dtype=np.uint8)
        image[..., :3] = rgb_img
        image[..., 3] = np.where(frame == 0, 0, 255).astype(np.uint8)
        path = tex_dir / f"portal_{name}.png"
        _save_rgba(path, image)
        mcmeta = path.with_suffix(".png.mcmeta")
        if mcmeta.exists():
            mcmeta.unlink()
        model = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"yap:item/portal_{name}"},
        }
        (model_dir / f"portal_{name}.json").write_text(json.dumps(model) + "\n", encoding="utf-8")
        (item_dir / f"{name}.json").write_text(
            json.dumps({"model": {"type": "minecraft:model", "model": f"yap:item/portal_{name}"}}) + "\n",
            encoding="utf-8",
        )
    (item_dir / "face.json").write_text(
        json.dumps({"model": {"type": "minecraft:model", "model": "yap:item/portal_purple"}}) + "\n",
        encoding="utf-8",
    )
    # Drop the old off-atlas copies so a later pack rebuild cannot point at them.
    stale_tex = OUT / "assets/yap/textures/portal"
    stale_model = OUT / "assets/yap/models/portal"
    for name in list(DYE_RGB) + ["face"]:
        for folder, suffix in ((stale_tex, ".png"), (stale_model, ".json")):
            old = folder / f"{name}{suffix}"
            if old.exists():
                old.unlink()
            meta = folder / f"{name}.png.mcmeta"
            if meta.exists():
                meta.unlink()
    print(f"  portal discs colors={len(DYE_RGB)} size={SHEET_SIZE}")


def main() -> None:
    BLOCK.mkdir(parents=True, exist_ok=True)
    pack_meta = OUT / "pack.mcmeta"
    pack_meta.parent.mkdir(parents=True, exist_ok=True)
    pack_meta.write_text(
        json.dumps(
            {
                "pack": {
                    "description": "YaP Portals — cinematic warp sheets + sounds",
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

    for name in DYE_RGB:
        _write_clear_glass(BLOCK / f"{name}_stained_glass.png")
        _write_clear_glass(BLOCK / f"{name}_stained_glass_pane_top.png")
        _write_clear_glass(BLOCK / f"{name}_stained_glass_pane.png")
        print(f"  portal texture {name}")

    _write_sounds()
    _write_cohesive_sheet()
    print(f"Wrote YaP portal textures + sounds → {OUT}")


if __name__ == "__main__":
    main()
