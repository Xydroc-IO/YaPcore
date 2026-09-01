#!/usr/bin/env python3
"""
Generate YaP MMO custom icons on a dedicated base item (CLAY_BALL).

Why CLAY_BALL: not used as runes, staffs, tools, or vehicle paper tokens —
so normal gameplay items keep their vanilla look.

CMD ranges:
  79000       combat level (skills GUI)
  79001-79020 RS skills
  78010-78015 showcase abilities (fixed)
  78200+      generated abilities (stable by sorted id)
"""
from __future__ import annotations

import json
import re
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "resourcepacks/yap-abilities"
ABIL_DIR = ROOT / "yap-first-party/gameplay/abilities-plugin/src/main/resources/abilities"
MANIFEST = OUT / "icon-manifest.json"

# Dedicated UI token — do not reuse swords, rods, paper (vehicles), or rune items.
ICON_ITEM = "clay_ball"
ICON_ITEM_MC = "item/clay_ball"

SKILLS: dict[str, tuple[int, tuple[int, int, int], str]] = {
    # id: (cmd, rgb, pattern key)
    "combat": (79000, (220, 60, 60), "star"),
    "attack": (79001, (220, 80, 80), "sword"),
    "strength": (79002, (200, 60, 40), "fist"),
    "defence": (79003, (80, 120, 200), "shield"),
    "hitpoints": (79004, (220, 40, 40), "heart"),
    "ranged": (79005, (80, 180, 80), "bow"),
    "prayer": (79006, (255, 230, 120), "pray"),
    "magic": (79007, (160, 80, 255), "orb"),
    "mining": (79008, (140, 140, 150), "pick"),
    "woodcutting": (79009, (100, 160, 60), "axe"),
    "fishing": (79010, (60, 140, 220), "fish"),
    "cooking": (79011, (220, 140, 60), "food"),
    "smithing": (79012, (160, 160, 180), "anvil"),
    "crafting": (79013, (180, 120, 80), "table"),
}

SHOWCASE_CMDS = {
    "fireball_splash": 78010,
    "homing_arc": 78011,
    "chain_lightning": 78012,
    "ground_slam": 78013,
    "aerial_strike": 78014,
    "meteor_strike": 78015,
}

ELEMENT_COLORS = {
    "fire": (255, 90, 30),
    "wind": (210, 235, 255),
    "water": (40, 130, 255),
    "earth": (130, 90, 45),
    "melee": (255, 90, 90),
    "ranged": (90, 200, 120),
    "prayer": (255, 235, 150),
    "utility": (150, 220, 255),
    "curse": (140, 50, 180),
    "arcanum": (170, 70, 255),
    "default": (255, 200, 90),
}

ABILITY_CMD_START = 78200


def png_rgba(pixels: list[list[tuple[int, int, int, int]]], size: int = 16) -> bytes:
    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = b""
    for y in range(size):
        raw += b"\x00"
        for x in range(size):
            r, g, b, a = pixels[y][x]
            raw += bytes((r, g, b, a))
    compressed = zlib.compress(raw, 9)
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", compressed) + chunk(b"IEND", b"")


def blank(size: int = 16) -> list[list[tuple[int, int, int, int]]]:
    return [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]


def fill_rect(px, x0, y0, x1, y1, rgba):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= y < len(px) and 0 <= x < len(px[0]):
                px[y][x] = rgba


def set_px(px, x, y, rgba):
    if 0 <= y < len(px) and 0 <= x < len(px[0]):
        px[y][x] = rgba


def draw_border(px, rgba, inset: int = 0):
    n = len(px) - 1 - inset
    for i in range(inset, n + 1):
        set_px(px, inset, i, rgba)
        set_px(px, n, i, rgba)
        set_px(px, i, inset, rgba)
        set_px(px, i, n, rgba)


def darken(rgb, f=0.55):
    return (int(rgb[0] * f), int(rgb[1] * f), int(rgb[2] * f), 255)


def lighten(rgb, f=1.25):
    return (min(255, int(rgb[0] * f)), min(255, int(rgb[1] * f)), min(255, int(rgb[2] * f)), 255)


def paint_pattern(pattern: str, rgb: tuple[int, int, int]) -> list[list[tuple[int, int, int, int]]]:
    px = blank()
    c = (*rgb, 255)
    d = darken(rgb)
    l = lighten(rgb)
    # background plate
    fill_rect(px, 1, 1, 14, 14, (*rgb, 230))
    fill_rect(px, 2, 2, 13, 13, (*tuple(max(0, v - 25) for v in rgb), 255))
    draw_border(px, d, 1)

    if pattern == "sword":
        fill_rect(px, 7, 2, 8, 10, l)
        fill_rect(px, 5, 10, 10, 11, d)
        fill_rect(px, 7, 11, 8, 13, c)
    elif pattern == "fist":
        fill_rect(px, 4, 5, 11, 11, l)
        fill_rect(px, 5, 4, 10, 5, c)
    elif pattern == "shield":
        fill_rect(px, 4, 3, 11, 11, l)
        fill_rect(px, 5, 12, 10, 13, d)
        fill_rect(px, 7, 5, 8, 9, d)
    elif pattern == "heart":
        fill_rect(px, 4, 5, 6, 7, l)
        fill_rect(px, 9, 5, 11, 7, l)
        fill_rect(px, 5, 6, 10, 10, l)
        fill_rect(px, 6, 11, 9, 12, c)
    elif pattern == "bow":
        fill_rect(px, 3, 3, 4, 12, l)
        fill_rect(px, 4, 3, 12, 4, c)
        fill_rect(px, 4, 11, 12, 12, c)
        fill_rect(px, 7, 5, 8, 10, d)
    elif pattern == "pray":
        fill_rect(px, 7, 2, 8, 13, l)
        fill_rect(px, 4, 5, 11, 6, l)
    elif pattern == "orb":
        fill_rect(px, 5, 4, 10, 11, l)
        fill_rect(px, 4, 5, 11, 10, c)
        set_px(px, 6, 6, (255, 255, 255, 255))
    elif pattern == "pick":
        fill_rect(px, 3, 3, 12, 5, l)
        fill_rect(px, 7, 5, 8, 13, d)
    elif pattern == "axe":
        fill_rect(px, 4, 3, 11, 6, l)
        fill_rect(px, 7, 6, 8, 13, d)
    elif pattern == "fish":
        fill_rect(px, 4, 6, 11, 9, l)
        fill_rect(px, 11, 5, 13, 10, c)
        set_px(px, 5, 7, d)
    elif pattern == "food":
        fill_rect(px, 4, 5, 11, 11, l)
        fill_rect(px, 5, 4, 10, 5, c)
    elif pattern == "anvil":
        fill_rect(px, 3, 5, 12, 7, l)
        fill_rect(px, 5, 7, 10, 12, d)
    elif pattern == "table":
        fill_rect(px, 3, 6, 12, 8, l)
        fill_rect(px, 4, 8, 5, 13, d)
        fill_rect(px, 10, 8, 11, 13, d)
    elif pattern == "star":
        fill_rect(px, 7, 2, 8, 13, l)
        fill_rect(px, 2, 7, 13, 8, l)
        fill_rect(px, 4, 4, 5, 5, c)
        fill_rect(px, 10, 4, 11, 5, c)
        fill_rect(px, 4, 10, 5, 11, c)
        fill_rect(px, 10, 10, 11, 11, c)
    elif pattern == "flame":
        fill_rect(px, 6, 3, 9, 12, l)
        fill_rect(px, 5, 6, 10, 12, c)
        fill_rect(px, 7, 2, 8, 4, (255, 255, 180, 255))
    elif pattern == "bolt":
        fill_rect(px, 8, 2, 11, 5, l)
        fill_rect(px, 5, 5, 10, 8, c)
        fill_rect(px, 4, 8, 7, 13, d)
    elif pattern == "wave":
        for x in range(3, 13):
            y = 8 + int(2 * __import__("math").sin(x))
            fill_rect(px, x, y, x, y + 1, l)
        fill_rect(px, 4, 5, 11, 7, c)
    elif pattern == "skull":
        fill_rect(px, 5, 4, 10, 9, l)
        set_px(px, 6, 6, d)
        set_px(px, 9, 6, d)
        fill_rect(px, 6, 10, 9, 12, c)
    else:  # gem / default spell
        fill_rect(px, 6, 3, 9, 12, l)
        fill_rect(px, 4, 5, 11, 10, c)
        set_px(px, 7, 6, (255, 255, 255, 220))
    return px


def color_for_ability(aid: str) -> tuple[int, int, int]:
    for key, rgb in ELEMENT_COLORS.items():
        if aid.startswith(key) or f"_{key}_" in f"_{aid}_":
            return rgb
    if aid.startswith("melee_"):
        return ELEMENT_COLORS["melee"]
    if aid.startswith("ranged_"):
        return ELEMENT_COLORS["ranged"]
    if aid.startswith("utility_"):
        return ELEMENT_COLORS["utility"]
    if aid.startswith("curse_"):
        return ELEMENT_COLORS["curse"]
    if aid.startswith("arcanum_"):
        return ELEMENT_COLORS["arcanum"]
    # prayer buffs
    if aid in {"thick_skin", "burst_of_strength", "clarity", "sharp_eye", "mystic_might",
               "protect_melee", "protect_missiles", "protect_magic", "retribution",
               "redemption", "smite", "chivalry", "piety", "rigour", "augury"}:
        return ELEMENT_COLORS["prayer"]
    return ELEMENT_COLORS["default"]


def pattern_for_ability(aid: str) -> str:
    if "fire" in aid or "meteor" in aid or "burn" in aid:
        return "flame"
    if "chain" in aid or "lightning" in aid or "bolt" in aid:
        return "bolt"
    if "wave" in aid or "water" in aid or "splash" in aid:
        return "wave"
    if "slam" in aid or "earth" in aid or "ground" in aid:
        return "anvil"
    if "curse" in aid or "crumble" in aid:
        return "skull"
    if "wind" in aid or "aerial" in aid:
        return "bolt"
    if aid.startswith("prayer") or aid in SKILLS and False:
        return "pray"
    if aid.startswith("melee_"):
        return "sword"
    if aid.startswith("ranged_"):
        return "bow"
    if aid.startswith("utility_"):
        return "orb"
    if aid.startswith("arcanum_"):
        return "orb"
    return "gem"


def write_model(path: Path, texture: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({
        "parent": "item/generated",
        "textures": {"layer0": texture},
    }, indent=2) + "\n", encoding="utf-8")


def scan_ability_ids() -> list[str]:
    ids: list[str] = []
    if not ABIL_DIR.is_dir():
        return ids
    pat = re.compile(r"^  ([a-z0-9_]+):\s*$")
    for path in sorted(ABIL_DIR.glob("*.yml")):
        for line in path.read_text(encoding="utf-8").splitlines():
            m = pat.match(line)
            if m:
                ids.append(m.group(1))
    # unique preserve order
    seen = set()
    out = []
    for i in ids:
        if i not in seen:
            seen.add(i)
            out.append(i)
    return out


def assign_ability_cmds(ability_ids: list[str]) -> dict[str, int]:
    cmds: dict[str, int] = {}
    next_cmd = ABILITY_CMD_START
    # showcase first (fixed)
    for aid, cmd in SHOWCASE_CMDS.items():
        cmds[aid] = cmd
    for aid in sorted(a for a in ability_ids if a not in SHOWCASE_CMDS):
        cmds[aid] = next_cmd
        next_cmd += 1
    return cmds


def patch_ability_yaml_icon_cmds(cmds: dict[str, int]) -> int:
    """Ensure every ability YAML has exactly one top-level icon-cmd."""
    if not ABIL_DIR.is_dir():
        return 0
    updated_files = 0
    for path in sorted(ABIL_DIR.glob("*.yml")):
        lines = path.read_text(encoding="utf-8").splitlines()
        out: list[str] = []
        current: str | None = None
        saw_cmd = False
        changed = False
        i = 0
        while i < len(lines):
            line = lines[i]
            m = re.match(r"^  ([a-z0-9_]+):\s*$", line)
            if m:
                # flush previous ability without icon-cmd
                current = m.group(1)
                saw_cmd = False
                out.append(line)
                i += 1
                continue
            if current and re.match(r"^    icon-cmd:\s*\d+\s*$", line):
                if saw_cmd:
                    changed = True
                    i += 1
                    continue
                desired = f"    icon-cmd: {cmds[current]}"
                if line != desired:
                    changed = True
                out.append(desired)
                saw_cmd = True
                i += 1
                continue
            if current and re.match(r"^    name:\s*", line):
                out.append(line)
                i += 1
                if not saw_cmd:
                    out.append(f"    icon-cmd: {cmds[current]}")
                    saw_cmd = True
                    changed = True
                continue
            if current and re.match(r"^    [a-z]", line) and not saw_cmd:
                out.append(f"    icon-cmd: {cmds[current]}")
                saw_cmd = True
                changed = True
                out.append(line)
                i += 1
                continue
            # nested projectile icon-cmd etc. — leave alone (more indented)
            out.append(line)
            i += 1
        if changed:
            path.write_text("\n".join(out) + "\n", encoding="utf-8")
            updated_files += 1
    return updated_files


def main() -> None:
    assets = OUT / "assets"
    tex_dir = assets / "yapabilities/textures/item"
    model_dir = assets / "yapabilities/models/item"
    tex_dir.mkdir(parents=True, exist_ok=True)
    model_dir.mkdir(parents=True, exist_ok=True)

    # remove old blaze_rod override so we don't steal staff visuals
    old_rod = assets / "minecraft/models/item/blaze_rod.json"
    if old_rod.exists():
        old_rod.unlink()

    ability_ids = scan_ability_ids()
    ability_cmds = assign_ability_cmds(ability_ids)
    patched = patch_ability_yaml_icon_cmds(ability_cmds)

    overrides = []
    entries = {"base_item": "CLAY_BALL", "skills": {}, "abilities": {}}

    # Skills
    for sid, (cmd, rgb, pattern) in SKILLS.items():
        name = f"skill_{sid}"
        tex_rel = f"yapabilities:item/{name}"
        (tex_dir / f"{name}.png").write_bytes(png_rgba(paint_pattern(pattern, rgb)))
        write_model(model_dir / f"{name}.json", tex_rel)
        overrides.append({"predicate": {"custom_model_data": cmd}, "model": f"yapabilities:item/{name}"})
        entries["skills"][sid] = {"cmd": cmd, "texture": name}

    # Abilities
    for aid, cmd in sorted(ability_cmds.items(), key=lambda x: x[1]):
        safe = re.sub(r"[^a-z0-9_]", "_", aid)
        name = f"ability_{safe}"
        rgb = color_for_ability(aid)
        pattern = pattern_for_ability(aid)
        tex_rel = f"yapabilities:item/{name}"
        (tex_dir / f"{name}.png").write_bytes(png_rgba(paint_pattern(pattern, rgb)))
        write_model(model_dir / f"{name}.json", tex_rel)
        overrides.append({"predicate": {"custom_model_data": cmd}, "model": f"yapabilities:item/{name}"})
        entries["abilities"][aid] = {"cmd": cmd, "texture": name}

    overrides.sort(key=lambda o: o["predicate"]["custom_model_data"])

    clay = assets / "minecraft/models/item" / f"{ICON_ITEM}.json"
    clay.parent.mkdir(parents=True, exist_ok=True)
    clay.write_text(json.dumps({
        "parent": "item/generated",
        "textures": {"layer0": ICON_ITEM_MC},
        "overrides": overrides,
    }, indent=2) + "\n", encoding="utf-8")

    (OUT / "pack.mcmeta").write_text(json.dumps({
        "pack": {
            "pack_format": 34,
            "description": "YaP MMO icons (CLAY_BALL CMD — skills + abilities)",
        }
    }, indent=2) + "\n", encoding="utf-8")

    MANIFEST.write_text(json.dumps(entries, indent=2) + "\n", encoding="utf-8")

    print(f"skills: {len(SKILLS)}")
    print(f"abilities: {len(ability_cmds)} (yaml icon-cmd patched in {patched} files)")
    print(f"overrides: {len(overrides)} on {ICON_ITEM}.json")
    print(f"manifest: {MANIFEST}")
    print(f"pack: {OUT}")


if __name__ == "__main__":
    main()
