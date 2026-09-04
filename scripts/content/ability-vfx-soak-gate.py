#!/usr/bin/env python3
"""
Offline V4 Folia VFX soak gate for YaP abilities.

Checks authoring budgets that matter under Folia (cast/hit length, splash radius,
timed-layer density) without needing a live world. Exit 0 = pass.
"""

from __future__ import annotations

import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
ABILITIES = ROOT / "yap-first-party/gameplay/abilities-plugin/src/main/resources/abilities"
CONFIG = ROOT / "yap-first-party/gameplay/abilities-plugin/src/main/resources/config.yml"

ALLOWED_SHAPES = {
    "burst", "ring", "helix", "spiral", "beam", "nova",
    "cone", "pillar", "column", "orb", "sphere", "shockwave", "wave", None,
}


def main() -> int:
    cfg = yaml.safe_load(CONFIG.read_text())
    folia = cfg.get("folia-safe") or {}
    max_per = int(folia.get("max-projectiles-per-player", 0))
    max_global = int(folia.get("max-projectiles-global", 0))
    if max_per < 8 or max_global < 64:
        print(f"FAIL folia caps too low: per={max_per} global={max_global}")
        return 1

    abilities = 0
    timed = 0
    shakes = 0
    arcs = 0
    motion_trails = 0
    bad = []
    shapes = set()

    for path in sorted(ABILITIES.glob("*.yml")):
        data = yaml.safe_load(path.read_text()) or {}
        for aid, ability in (data.get("abilities") or {}).items():
            abilities += 1
            cast = ability.get("cast") or []
            hit = ability.get("on-hit") or []
            if not path.name.startswith("showcase_") and (len(cast) > 12 or len(hit) > 12):
                bad.append(f"{aid}: effect list too long cast={len(cast)} hit={len(hit)}")
            for step in cast + hit:
                if step.get("type") == "vfx":
                    shapes.add(step.get("shape"))
                if "at" in step:
                    timed += 1
                if step.get("type") == "shake":
                    shakes += 1
            proj = ability.get("projectile") or {}
            if float(proj.get("splash-radius") or 0) > 6:
                bad.append(f"{aid}: splash-radius {proj.get('splash-radius')} > 6")
            if proj.get("path") == "arc":
                arcs += 1
            trail = proj.get("trail") or {}
            if trail.get("style") in ("motion", "ribbon"):
                motion_trails += 1

    for shape in shapes:
        if shape not in ALLOWED_SHAPES:
            bad.append(f"unknown shape: {shape}")

    print(f"abilities={abilities} timed_at={timed} shake={shakes} arcs={arcs} motion_trails={motion_trails}")
    print(f"shapes={sorted(s for s in shapes if s)}")
    print(f"folia-safe max-per={max_per} max-global={max_global}")

    if abilities < 230:
        bad.append(f"ability count {abilities} < 230")
    if timed < 200:
        bad.append(f"timed at: steps {timed} < 200")
    if shakes < 100:
        bad.append(f"shake steps {shakes} < 100")
    if arcs < 50:
        bad.append(f"arc projectiles {arcs} < 50")
    if "shockwave" not in shapes:
        bad.append("missing shockwave shape usage")

    heroes = yaml.safe_load((ABILITIES / "showcase_heroes.yml").read_text())["abilities"]
    if len(heroes) < 12:
        bad.append(f"heroes {len(heroes)} < 12")
    for aid, ability in heroes.items():
        vfx = [s for s in (ability.get("cast") or []) if s.get("type") == "vfx"]
        if len(vfx) < 2:
            bad.append(f"hero {aid} has <2 cast vfx")
        if vfx and all(s.get("shape") == "burst" for s in vfx):
            bad.append(f"hero {aid} is burst-only")

    # Unique hero textures exist
    tex = ROOT / "resourcepacks/yap-abilities/assets/yapabilities/textures/item"
    for aid in heroes:
        path = tex / f"ability_{aid}.png"
        if not path.is_file():
            bad.append(f"missing hero texture {path.name}")

    if bad:
        print("FAIL")
        for line in bad[:30]:
            print(" -", line)
        return 1
    print("PASS ability VFX Folia soak gate")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
