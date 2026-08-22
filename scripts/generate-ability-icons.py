#!/usr/bin/env python3
"""Generate yap-abilities resource pack icons (blaze_rod CMD overrides)."""
from __future__ import annotations

import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "resourcepacks/yap-abilities"
CMD_BASE = 78001
SHOWCASE = {
    "fireball_splash": (78010, (255, 120, 40)),
    "homing_arc": (78011, (120, 200, 255)),
    "chain_lightning": (78012, (255, 255, 80)),
    "ground_slam": (78013, (180, 120, 60)),
    "aerial_strike": (78014, (200, 200, 255)),
}
CATEGORIES = {
    "magic": (78020, (160, 80, 255)),
    "melee": (78021, (255, 80, 80)),
    "ranged": (78022, (80, 200, 120)),
    "prayer": (78023, (255, 240, 160)),
    "utility": (78024, (160, 220, 255)),
}


def png_bytes(r: int, g: int, b: int, size: int = 16) -> bytes:
    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = b""
    row = bytes([r, g, b, 255]) * size
    for _ in range(size):
        raw += b"\x00" + row
    compressed = zlib.compress(raw, 9)
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", compressed) + chunk(b"IEND", b"")


def write_model(path: Path, texture: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({
        "parent": "item/generated",
        "textures": {"layer0": texture},
    }, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    assets = OUT / "assets"
    tex_dir = assets / "yapabilities/textures/item"
    model_dir = assets / "yapabilities/models/item"
    tex_dir.mkdir(parents=True, exist_ok=True)
    model_dir.mkdir(parents=True, exist_ok=True)

    overrides = []
    for name, (cmd, rgb) in {**SHOWCASE, **CATEGORIES}.items():
        tex_rel = f"yapabilities:item/{name}"
        tex_file = tex_dir / f"{name}.png"
        tex_file.write_bytes(png_bytes(*rgb))
        write_model(model_dir / f"{name}.json", tex_rel)
        overrides.append({
            "predicate": {"custom_model_data": cmd},
            "model": f"yapabilities:item/{name}",
        })

    rod = assets / "minecraft/models/item/blaze_rod.json"
    rod.parent.mkdir(parents=True, exist_ok=True)
    rod.write_text(json.dumps({
        "parent": "item/handheld",
        "textures": {"layer0": "item/blaze_rod"},
        "overrides": sorted(overrides, key=lambda o: o["predicate"]["custom_model_data"]),
    }, indent=2) + "\n", encoding="utf-8")

    (OUT / "pack.mcmeta").write_text(json.dumps({
        "pack": {
            "pack_format": 34,
            "description": "YaP Abilities spell icons",
        }
    }, indent=2) + "\n", encoding="utf-8")

    print(f"Wrote {len(overrides)} ability icons to {OUT}")


if __name__ == "__main__":
    main()
