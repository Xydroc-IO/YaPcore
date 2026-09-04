#!/usr/bin/env python3
"""
Generate / upgrade YaP ability pack YAML with element + archetype VFX kits (V2).

Preserves gameplay identity (id, name, levels, costs, damage, buffs, teleports, …)
and rewrites cast / travel / hit cosmetics using V1 engine primitives.

Usage:
  python3 scripts/generate-ability-pack.py
  python3 scripts/generate-ability-pack.py --dry-run

Does NOT overwrite showcase_*.yml (hand-authored).
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:
    print("PyYAML required: pip install pyyaml", file=sys.stderr)
    sys.exit(1)

ROOT = Path(__file__).resolve().parents[1]
ABILITIES_DIR = ROOT / "yap-first-party/gameplay/abilities-plugin/src/main/resources/abilities"

GENERATED_PACKS = [
    "legacy_spells.yml",
    "magic_wind.yml",
    "magic_water.yml",
    "magic_earth.yml",
    "magic_fire.yml",
    "arcanum.yml",
    "curses.yml",
    "melee_specials.yml",
    "ranged_specials.yml",
    "prayer_powers.yml",
    "utility.yml",
]

# ---------------------------------------------------------------------------
# Kits — element / archetype art direction
# ---------------------------------------------------------------------------

KITS: dict[str, dict[str, Any]] = {
    "fire": {
        "color": "255,80,20",
        "cast_particle": "FLAME",
        "cast_secondary": "LAVA",
        "hit_particle": "LAVA",
        "trail_particle": "FLAME",
        "cast_sound": "ENTITY_BLAZE_SHOOT",
        "hit_sound": "ENTITY_GENERIC_EXPLODE",
        "anim": "cast",
        "pose": "glow",
        "block": None,
        "cast_shapes": ["burst", "helix", "helix", "cone", "orb", "pillar"],
        "hit_shapes": ["nova", "nova", "shockwave", "shockwave", "orb", "shockwave"],
        "trail_styles": ["burst", "motion", "motion", "ribbon", "motion", "motion"],
        "paths": ["straight", "straight", "arc", "arc", "arc", "arc"],
        "arc_heights": [0, 0, 1.4, 1.8, 2.2, 2.6],
        "impact_shake_from": 2,
    },
    "water": {
        "color": "40,120,255",
        "cast_particle": "DRIPPING_WATER",
        "cast_secondary": "BUBBLE_POP",
        "hit_particle": "SPLASH",
        "trail_particle": "DRIPPING_WATER",
        "cast_sound": "ENTITY_PLAYER_SPLASH",
        "hit_sound": "ENTITY_GENERIC_SPLASH",
        "anim": "cast",
        "pose": "glow",
        "block": None,
        "cast_shapes": ["burst", "ring", "helix", "cone", "orb", "shockwave"],
        "hit_shapes": ["nova", "ring", "shockwave", "orb", "shockwave", "shockwave"],
        "trail_styles": ["burst", "ribbon", "ribbon", "motion", "motion", "motion"],
        "paths": ["straight", "straight", "arc", "arc", "arc", "arc"],
        "arc_heights": [0, 0, 1.2, 1.6, 2.0, 2.4],
        "impact_shake_from": 2,
    },
    "wind": {
        "color": "220,240,255",
        "cast_particle": "CLOUD",
        "cast_secondary": "GUST",
        "hit_particle": "GUST",
        "trail_particle": "CLOUD",
        "cast_sound": "ENTITY_BREEZE_WIND_CHARGE_BURST",
        "hit_sound": "ENTITY_BREEZE_WIND_BURST",
        "anim": "cast",
        "pose": "glow",
        "block": None,
        "cast_shapes": ["burst", "helix", "cone", "helix", "orb", "shockwave"],
        "hit_shapes": ["nova", "ring", "shockwave", "nova", "shockwave", "shockwave"],
        "trail_styles": ["burst", "motion", "ribbon", "motion", "motion", "motion"],
        "paths": ["straight", "straight", "straight", "arc", "arc", "arc"],
        "arc_heights": [0, 0, 0, 1.5, 2.0, 2.5],
        "impact_shake_from": 3,
    },
    "earth": {
        "color": "120,80,40",
        "cast_particle": "BLOCK",
        "cast_secondary": "CRIT",
        "hit_particle": "BLOCK",
        "trail_particle": "BLOCK",
        "cast_sound": "BLOCK_GRAVEL_BREAK",
        "hit_sound": "ENTITY_IRON_GOLEM_ATTACK",
        "anim": "slam",
        "pose": "glow",
        "block": "DIRT",
        "cast_shapes": ["burst", "ring", "pillar", "pillar", "shockwave", "pillar"],
        "hit_shapes": ["nova", "ring", "shockwave", "pillar", "shockwave", "shockwave"],
        "trail_styles": ["burst", "burst", "ribbon", "motion", "motion", "motion"],
        "paths": ["straight", "straight", "arc", "arc", "arc", "arc"],
        "arc_heights": [0, 0, 1.0, 1.3, 1.6, 2.0],
        "impact_shake_from": 1,
    },
    "arcane": {
        "color": "160,60,255",
        "cast_particle": "END_ROD",
        "cast_secondary": "ENCHANT",
        "hit_particle": "END_ROD",
        "trail_particle": "END_ROD",
        "cast_sound": "ENTITY_ILLUSIONER_CAST_SPELL",
        "hit_sound": "ENTITY_EVOKER_CAST_SPELL",
        "anim": "channel",
        "pose": "glow",
        "block": None,
        "cast_shapes": ["helix", "orb", "helix", "orb", "pillar", "orb"],
        "hit_shapes": ["nova", "orb", "shockwave", "orb", "shockwave", "shockwave"],
        "trail_styles": ["motion", "ribbon", "motion", "ribbon", "motion", "motion"],
        "paths": ["straight", "arc", "arc", "arc", "arc", "arc"],
        "arc_heights": [0, 1.2, 1.6, 2.0, 2.4, 2.8],
        "impact_shake_from": 2,
    },
    "curse": {
        "color": "90,20,120",
        "cast_particle": "WITCH",
        "cast_secondary": "SMOKE",
        "hit_particle": "WITCH",
        "trail_particle": "WITCH",
        "cast_sound": "ENTITY_WITCH_THROW",
        "hit_sound": "ENTITY_WITCH_CELEBRATE",
        "anim": "cast",
        "pose": "glow",
        "block": None,
        "cast_shapes": ["burst", "orb", "helix", "orb", "cone", "orb"],
        "hit_shapes": ["nova", "orb", "shockwave", "orb", "shockwave", "orb"],
        "trail_styles": ["burst", "ribbon", "motion", "ribbon", "motion", "motion"],
        "paths": ["straight", "arc", "arc", "arc", "arc", "arc"],
        "arc_heights": [0, 1.0, 1.4, 1.8, 2.2, 2.6],
        "impact_shake_from": 2,
    },
    "melee": {
        "color": "255,220,120",
        "cast_particle": "SWEEP_ATTACK",
        "cast_secondary": "CRIT",
        "hit_particle": "CRIT",
        "trail_particle": "SWEEP_ATTACK",
        "cast_sound": "ENTITY_PLAYER_ATTACK_SWEEP",
        "hit_sound": "ENTITY_PLAYER_ATTACK_STRONG",
        "anim": "slam",
        "pose": "glow",
        "block": None,
        "cast_shapes": ["burst", "cone", "ring", "cone", "shockwave", "shockwave"],
        "hit_shapes": ["nova", "ring", "shockwave", "nova", "shockwave", "shockwave"],
        "trail_styles": ["burst", "burst", "ribbon", "motion", "motion", "motion"],
        "paths": ["straight", "straight", "straight", "straight", "arc", "arc"],
        "arc_heights": [0, 0, 0, 0, 1.0, 1.4],
        "impact_shake_from": 2,
    },
    "ranged": {
        "color": "180,255,140",
        "cast_particle": "CRIT",
        "cast_secondary": "ENCHANTED_HIT",
        "hit_particle": "CRIT",
        "trail_particle": "CRIT",
        "cast_sound": "ENTITY_ARROW_SHOOT",
        "hit_sound": "ENTITY_ARROW_HIT",
        "anim": "cast",
        "pose": "glow",
        "block": None,
        "cast_shapes": ["burst", "cone", "helix", "cone", "orb", "shockwave"],
        "hit_shapes": ["nova", "ring", "nova", "shockwave", "orb", "shockwave"],
        "trail_styles": ["burst", "motion", "ribbon", "motion", "motion", "motion"],
        "paths": ["straight", "straight", "straight", "arc", "arc", "arc"],
        "arc_heights": [0, 0, 0, 1.2, 1.6, 2.0],
        "impact_shake_from": 3,
    },
    "prayer": {
        "color": "255,230,120",
        "cast_particle": "ENCHANT",
        "cast_secondary": "END_ROD",
        "hit_particle": "ENCHANT",
        "trail_particle": "ENCHANT",
        "cast_sound": "BLOCK_ENCHANTMENT_TABLE_USE",
        "hit_sound": "BLOCK_ENCHANTMENT_TABLE_USE",
        "anim": "both",
        "pose": "glow",
        "block": None,
        "cast_shapes": ["helix", "helix", "orb", "helix", "pillar", "orb"],
        "hit_shapes": ["ring", "ring", "orb", "shockwave", "orb", "shockwave"],
        "trail_styles": ["burst", "burst", "burst", "burst", "burst", "burst"],
        "paths": ["straight"] * 6,
        "arc_heights": [0] * 6,
        "impact_shake_from": 99,
    },
    "utility": {
        "color": "120,60,200",
        "cast_particle": "PORTAL",
        "cast_secondary": "REVERSE_PORTAL",
        "hit_particle": "REVERSE_PORTAL",
        "trail_particle": "PORTAL",
        "cast_sound": "ENTITY_ENDERMAN_TELEPORT",
        "hit_sound": "ENTITY_ENDERMAN_TELEPORT",
        "anim": "cast",
        "pose": "levitate",
        "block": None,
        "cast_shapes": ["helix", "helix", "orb", "helix", "pillar", "orb"],
        "hit_shapes": ["nova", "nova", "shockwave", "orb", "shockwave", "shockwave"],
        "trail_styles": ["burst"] * 6,
        "paths": ["straight"] * 6,
        "arc_heights": [0] * 6,
        "impact_shake_from": 99,
    },
}

ELEMENT_FROM_ID = {
    "fire": "fire",
    "water": "water",
    "wind": "wind",
    "earth": "earth",
    "crumble": "earth",
}

PACK_KIT = {
    "magic_fire.yml": "fire",
    "magic_water.yml": "water",
    "magic_wind.yml": "wind",
    "magic_earth.yml": "earth",
    "arcanum.yml": "arcane",
    "curses.yml": "curse",
    "melee_specials.yml": "melee",
    "ranged_specials.yml": "ranged",
    "prayer_powers.yml": "prayer",
    "utility.yml": "utility",
}

MELEE_FAMILY_ANIM = {
    "melee_slash": "cast",
    "melee_cleave": "slam",
    "melee_bash": "slam",
    "melee_riposte": "cast",
    "melee_whirlwind": "channel",
}

RANGED_FAMILY_ANIM = {
    "ranged_shot": "cast",
    "ranged_volley": "channel",
    "ranged_pierce": "cast",
    "ranged_bind": "cast",
    "ranged_barrage": "channel",
}

TIER_NAMES = ("strike", "bolt", "blast", "wave", "surge", "storm")

COSMETIC_TYPES = {"animation", "vfx", "sound", "shake", "display"}


def resolve_kit(pack_name: str, ability_id: str, ability: dict) -> str:
    if pack_name in PACK_KIT:
        return PACK_KIT[pack_name]
    for key, kit in ELEMENT_FROM_ID.items():
        if ability_id.startswith(key) or key in ability_id:
            return kit
    cat = (ability.get("category") or "").lower()
    if cat in KITS:
        return cat
    return "arcane"


def resolve_tier(ability_id: str, ability: dict) -> int:
    for i, name in enumerate(TIER_NAMES):
        if ability_id.endswith("_" + name) or ability_id == name:
            return i
    m = re.search(r"_(\d+)$", ability_id)
    if m:
        n = int(m.group(1))
        return min(5, max(0, (n - 1) // 2))
    # prayer / named — scale by required level
    levels = ability.get("min-level") or {}
    if levels:
        lvl = max(int(v) for v in levels.values())
        return min(5, max(0, lvl // 16))
    hit = _damage_max(ability)
    if hit:
        return min(5, max(0, (hit - 4) // 6))
    return 1


def _damage_max(ability: dict) -> int:
    for effect in ability.get("on-hit") or []:
        if effect.get("type") == "damage":
            return int(effect.get("max-hit") or effect.get("amount") or 0)
    return 0


def _scale(tier: int, base: int, step: int = 2) -> int:
    return base + tier * step


def build_cast_vfx(kit: dict, tier: int) -> list[dict]:
    shape = kit["cast_shapes"][tier]
    secondary_shape = "ring" if shape != "ring" else "helix"
    # Competitive density — readable in combat, not a single pop.
    count = _scale(tier, 22, 5)
    radius = round(1.05 + tier * 0.15, 2)
    steps: list[dict] = []

    primary: dict[str, Any] = {
        "type": "vfx",
        "particle": kit["cast_particle"],
        "shape": shape,
        "count": count,
        "offset-y": 0.25 if shape != "pillar" else 0.0,
        "radius": radius if shape != "cone" else round(2.8 + tier * 0.45, 2),
    }
    if kit["color"] and kit["cast_particle"] in ("DUST", "FLAME", "CLOUD", "WITCH", "END_ROD"):
        primary["color"] = kit["color"]
    if kit["block"] and kit["cast_particle"] == "BLOCK":
        primary["block"] = kit["block"]
    # Sustained cast pulse for tier 2+ (channel feel without blocking combat)
    if tier >= 2:
        primary["ticks"] = 6 + tier
        primary["interval"] = 2
    steps.append(primary)

    # Layered timed ring / secondary — V1 parallel beat
    secondary: dict[str, Any] = {
        "type": "vfx",
        "at": 2 + min(tier, 3),
        "particle": "DUST" if kit["color"] else kit["cast_secondary"],
        "shape": secondary_shape,
        "count": _scale(tier, 18, 4),
        "radius": round(0.95 + tier * 0.12, 2),
        "offset-y": 0.2,
    }
    if kit["color"]:
        secondary["color"] = kit["color"]
        secondary["size"] = round(1.15 + tier * 0.06, 2)
    else:
        secondary["particle"] = kit["cast_secondary"]
    # earth uses block secondary as crit ring without dust if preferred
    if kit["block"] and kit["cast_particle"] == "BLOCK":
        secondary = {
            "type": "vfx",
            "at": 3,
            "particle": "BLOCK",
            "shape": "shockwave" if tier >= 2 else "ring",
            "block": "STONE" if tier >= 2 else kit["block"],
            "count": _scale(tier, 20, 4),
            "radius": round(1.4 + tier * 0.22, 2),
            "offset-y": 0.05,
        }
    steps.append(secondary)

    # Mid-cast display pulse — spell glyph in front of caster
    steps.append({
        "type": "display",
        "at": 1,
        "scale": round(0.7 + tier * 0.08, 2),
        "ticks": 10 + tier,
    })

    if tier >= 2:
        steps.append({
            "type": "vfx",
            "at": 5,
            "particle": kit["cast_secondary"],
            "shape": "orb" if tier < 4 else "pillar",
            "count": _scale(tier, 14, 3),
            "radius": round(0.85 + tier * 0.1, 2),
            "offset-y": 0.3,
            **({"color": kit["color"]} if kit["color"] and kit["cast_secondary"] == "DUST" else {}),
            **({"block": kit["block"]} if kit["block"] and kit["cast_secondary"] == "BLOCK" else {}),
        })

    if tier >= 1:
        steps.append({
            "type": "shake",
            "power": round(0.06 + tier * 0.02, 2),
            "pulses": 2 + (1 if tier >= 3 else 0),
        })
    return steps


def build_hit_vfx(kit: dict, tier: int) -> list[dict]:
    shape = kit["hit_shapes"][tier]
    hit: dict[str, Any] = {
        "type": "vfx",
        "particle": kit["hit_particle"],
        "shape": shape,
        "count": _scale(tier, 18, 5),
        "radius": round(1.35 + tier * 0.18, 2),
        "offset-y": 0.3 if shape != "pillar" else 0.0,
    }
    if kit["color"] and kit["hit_particle"] in ("DUST", "FLAME", "CLOUD", "WITCH", "GUST"):
        hit["color"] = kit["color"]
    if kit["block"] and kit["hit_particle"] == "BLOCK":
        hit["block"] = "STONE" if tier >= 2 else kit["block"]
    steps = [hit]
    if tier >= 1:
        secondary: dict[str, Any] = {
            "type": "vfx",
            "at": 2,
            "particle": kit["cast_secondary"],
            "shape": "nova" if tier >= 3 else "ring",
            "count": _scale(tier, 14, 3),
            "radius": round(1.1 + tier * 0.15, 2),
            "offset-y": 0.25,
        }
        if kit["block"] and kit["cast_secondary"] == "BLOCK":
            secondary["block"] = kit["block"]
        if kit["color"] and kit["cast_secondary"] == "DUST":
            secondary["color"] = kit["color"]
        steps.append(secondary)
    if tier >= kit["impact_shake_from"]:
        steps.append({
            "type": "shake",
            "power": round(0.14 + tier * 0.03, 2),
            "pulses": 3,
            "radius": round(2.5 + tier * 0.4, 2),
        })
    return steps


def upgrade_projectile(proj: dict | None, kit: dict, tier: int) -> dict | None:
    if not proj:
        return None
    out = dict(proj)
    # Never ship visible egg carriers — snowball hides cleanly behind ItemDisplay.
    entity = str(out.get("entity") or "").upper()
    if entity in ("", "EGG", "CHICKEN_EGG", "THROWN_EGG"):
        out["entity"] = "SNOWBALL"
    path = kit["paths"][tier]
    out["path"] = path
    if path == "arc":
        out["arc-height"] = kit["arc_heights"][tier]
    else:
        out.pop("arc-height", None)
        out.pop("arc", None)

    trail = dict(out.get("trail") or {})
    if trail.get("particle") or kit["trail_particle"]:
        trail["particle"] = trail.get("particle") or kit["trail_particle"]
        trail["count"] = max(int(trail.get("count") or 6), 6 + tier * 2)
        trail["interval"] = int(trail.get("interval") or 1)
        trail["style"] = kit["trail_styles"][tier]
        trail["falloff"] = round(0.18 + tier * 0.06, 2)
        out["trail"] = trail

    if tier >= kit["impact_shake_from"]:
        out["impact-shake"] = True
        out["shake-power"] = round(0.14 + tier * 0.03, 2)
    else:
        out.pop("impact-shake", None)
        out.pop("shake-power", None)
        out.pop("shake", None)

    out["hide"] = True
    out["scale"] = max(float(out.get("scale") or 0), round(1.15 + tier * 0.05, 2))
    return out


def keep_non_cosmetic(effects: list | None) -> list[dict]:
    return [dict(e) for e in (effects or []) if e.get("type") not in COSMETIC_TYPES]


def animation_for(ability_id: str, kit: dict, tier: int, ability: dict) -> dict:
    anim = kit["anim"]
    for fam, style in MELEE_FAMILY_ANIM.items():
        if ability_id.startswith(fam):
            anim = style
            break
    for fam, style in RANGED_FAMILY_ANIM.items():
        if ability_id.startswith(fam):
            anim = style
            break
    out: dict[str, Any] = {"type": "animation", "style": anim}
    if anim in ("cast", "channel"):
        out["pulses"] = 2 + (1 if tier >= 3 else 0) + (1 if anim == "channel" else 0)
    pose = kit["pose"]
    if pose:
        out["pose"] = pose
        if pose == "levitate":
            out["pose-ticks"] = 8
    return out


def rebuild_combat_ability(ability_id: str, ability: dict, kit_name: str) -> dict:
    kit = KITS[kit_name]
    tier = resolve_tier(ability_id, ability)
    preserved_cast = keep_non_cosmetic(ability.get("cast"))
    preserved_hit = keep_non_cosmetic(ability.get("on-hit"))

    cast: list[dict] = [animation_for(ability_id, kit, tier, ability)]
    cast.append({
        "type": "sound",
        "sound": kit["cast_sound"],
        "volume": 1.0,
        "pitch": round(1.05 - tier * 0.02, 2),
    })
    cast.extend(build_cast_vfx(kit, tier))
    # re-append non-cosmetic (xp, etc.) — xp usually last
    for effect in preserved_cast:
        cast.append(effect)

    hit: list[dict] = []
    # Keep mechanical order: damage first, then cosmetics, then knockback/debuff
    damage_effects = [e for e in preserved_hit if e.get("type") == "damage"]
    other_mech = [e for e in preserved_hit if e.get("type") != "damage"]
    hit.extend(damage_effects)
    hit.extend(build_hit_vfx(kit, tier))
    hit.append({
        "type": "sound",
        "sound": kit["hit_sound"],
        "volume": 0.85,
        "pitch": round(1.15 - tier * 0.03, 2),
    })
    hit.extend(other_mech)

    out = dict(ability)
    out["cast"] = cast
    if ability.get("on-hit") is not None or damage_effects or other_mech:
        out["on-hit"] = hit
    if ability.get("projectile"):
        out["projectile"] = upgrade_projectile(ability.get("projectile"), kit, tier)
    return out


def rebuild_prayer_ability(ability_id: str, ability: dict) -> dict:
    kit = KITS["prayer"]
    tier = resolve_tier(ability_id, ability)
    preserved = keep_non_cosmetic(ability.get("cast"))
    cast: list[dict] = [animation_for(ability_id, kit, tier, ability)]
    # sustained helix
    cast.append({
        "type": "vfx",
        "particle": "ENCHANT",
        "shape": kit["cast_shapes"][tier],
        "count": 24 + tier * 3,
        "radius": round(0.9 + tier * 0.08, 2),
        "ticks": 12 + tier * 2,
        "interval": 2,
    })
    cast.append({
        "type": "vfx",
        "at": 3,
        "particle": "DUST",
        "shape": "ring",
        "count": 14 + tier * 2,
        "color": kit["color"],
        "size": 1.2,
        "radius": round(0.85 + tier * 0.08, 2),
    })
    if tier >= 3:
        cast.append({
            "type": "vfx",
            "at": 6,
            "particle": "END_ROD",
            "shape": "orb",
            "count": 12,
            "radius": 0.8,
        })
    cast.append({
        "type": "sound",
        "sound": kit["cast_sound"],
        "volume": 1.0,
    })
    cast.extend(preserved)
    out = dict(ability)
    out["cast"] = cast
    out.pop("on-hit", None)
    out.pop("projectile", None)
    return out


def rebuild_utility_ability(ability_id: str, ability: dict) -> dict:
    kit = KITS["utility"]
    tier = resolve_tier(ability_id, ability)
    preserved = keep_non_cosmetic(ability.get("cast"))
    # Preserve delay + teleport order from original
    delays = [e for e in preserved if e.get("type") == "delay"]
    teles = [e for e in preserved if e.get("type") == "teleport"]
    other = [e for e in preserved if e.get("type") not in ("delay", "teleport")]

    cast: list[dict] = [animation_for(ability_id, kit, tier, ability)]
    cast.append({
        "type": "vfx",
        "particle": "PORTAL",
        "shape": "helix" if tier < 4 else "pillar",
        "count": 24 + tier * 2,
        "radius": round(0.85 + tier * 0.05, 2),
    })
    cast.append({
        "type": "vfx",
        "at": 2,
        "particle": "DUST",
        "shape": "ring",
        "count": 12,
        "color": kit["color"],
        "size": 1.1,
        "radius": 0.7,
    })
    cast.append({"type": "sound", "sound": kit["cast_sound"]})
    if delays:
        cast.extend(delays)
    else:
        cast.append({"type": "delay", "ticks": 3})
    if teles:
        cast.extend(teles)
    cast.append({
        "type": "vfx",
        "particle": "REVERSE_PORTAL",
        "shape": "shockwave" if tier >= 2 else "nova",
        "count": 16 + tier * 2,
        "radius": round(1.0 + tier * 0.1, 2),
    })
    if tier >= 2:
        cast.append({"type": "shake", "power": 0.08, "pulses": 2})
    cast.extend(other)
    out = dict(ability)
    out["cast"] = cast
    out.pop("on-hit", None)
    out.pop("projectile", None)
    return out


def rebuild_ability(pack_name: str, ability_id: str, ability: dict) -> dict:
    kit_name = resolve_kit(pack_name, ability_id, ability)
    if kit_name == "prayer" or (ability.get("category") == "prayer"):
        return rebuild_prayer_ability(ability_id, ability)
    if kit_name == "utility" or (ability.get("category") == "utility"):
        return rebuild_utility_ability(ability_id, ability)
    return rebuild_combat_ability(ability_id, ability, kit_name)


# ---------------------------------------------------------------------------
# YAML emission (stable, readable, close to existing packs)
# ---------------------------------------------------------------------------

def emit_scalar(value: Any) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, float):
        text = f"{value:.4f}".rstrip("0").rstrip(".")
        return text if text else "0"
    if isinstance(value, int):
        return str(value)
    text = str(value)
    if text == "" or any(c in text for c in ":#{}[],&*!|>%@`'\"") or text.strip() != text:
        return json_quote(text)
    # quote display names with spaces
    if " " in text or not text.replace("_", "").replace("-", "").isalnum():
        # keep simple tokens unquoted when safe
        if re.fullmatch(r"[A-Za-z0-9_./+-]+", text):
            return text
        return json_quote(text)
    return text


def json_quote(text: str) -> str:
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def emit_mapping(obj: dict, indent: int, lines: list[str], inline_keys: bool = False) -> None:
    pad = " " * indent
    for key, value in obj.items():
        if value is None:
            continue
        if isinstance(value, dict):
            lines.append(f"{pad}{key}:")
            emit_mapping(value, indent + 2, lines)
        elif isinstance(value, list):
            lines.append(f"{pad}{key}:")
            for item in value:
                if isinstance(item, dict):
                    # first key on same line as -
                    items = list(item.items())
                    if not items:
                        lines.append(f"{pad}  - {{}}")
                        continue
                    first_k, first_v = items[0]
                    if isinstance(first_v, (dict, list)):
                        lines.append(f"{pad}  -")
                        emit_mapping(item, indent + 4, lines)
                    else:
                        lines.append(f"{pad}  - {first_k}: {emit_scalar(first_v)}")
                        for k, v in items[1:]:
                            if isinstance(v, dict):
                                lines.append(f"{pad}    {k}:")
                                emit_mapping(v, indent + 6, lines)
                            elif isinstance(v, list):
                                lines.append(f"{pad}    {k}:")
                                for sub in v:
                                    lines.append(f"{pad}      - {emit_scalar(sub)}")
                            else:
                                lines.append(f"{pad}    {k}: {emit_scalar(v)}")
                else:
                    lines.append(f"{pad}  - {emit_scalar(item)}")
        else:
            lines.append(f"{pad}{key}: {emit_scalar(value)}")


def emit_pack(abilities: dict[str, dict]) -> str:
    lines = [
        "# Generated by scripts/generate-ability-pack.py — do not hand-edit at scale",
        "# V2 kits: element/archetype cast · travel · hit cosmetics (see docs/mmo/MMO_ABILITY_VFX.md)",
        "abilities:",
    ]
    for ability_id, ability in abilities.items():
        lines.append(f"  {ability_id}:")
        # Prefer stable key order
        order = [
            "name", "icon-cmd", "category", "min-level", "costs", "cooldown",
            "range", "target", "conditions", "cast", "projectile", "on-hit",
        ]
        ordered: dict[str, Any] = {}
        for key in order:
            if key in ability:
                ordered[key] = ability[key]
        for key, value in ability.items():
            if key not in ordered:
                ordered[key] = value
        emit_mapping(ordered, 4, lines)
    lines.append("")
    return "\n".join(lines)


def process_pack(path: Path, dry_run: bool) -> tuple[int, int]:
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    abilities = data.get("abilities") or {}
    upgraded: dict[str, dict] = {}
    for ability_id, ability in abilities.items():
        upgraded[ability_id] = rebuild_ability(path.name, ability_id, ability)
    text = emit_pack(upgraded)
    if not dry_run:
        path.write_text(text, encoding="utf-8")
    return len(upgraded), len(text)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--dir", type=Path, default=ABILITIES_DIR)
    args = parser.parse_args()
    if not args.dir.is_dir():
        print(f"abilities dir missing: {args.dir}", file=sys.stderr)
        return 1

    total = 0
    for name in GENERATED_PACKS:
        path = args.dir / name
        if not path.exists():
            print(f"skip missing {name}")
            continue
        count, size = process_pack(path, args.dry_run)
        total += count
        action = "would write" if args.dry_run else "wrote"
        print(f"{action} {name}: {count} abilities ({size} bytes)")

    # Never touch showcases
    for path in sorted(args.dir.glob("showcase_*.yml")):
        print(f"preserved {path.name}")

    print(f"done: {total} abilities ({'dry-run' if args.dry_run else 'written'})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
