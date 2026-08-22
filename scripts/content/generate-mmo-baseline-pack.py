#!/usr/bin/env python3
"""
Generate bare-minimum YaP MMO content packs:
  - 30 quests (6 chains × 5)
  - 20 bosses
  - 80 recipes (smithing / cooking / crafting)

Outputs into plugin resources + content-manifest.txt for jar extract.
"""
from __future__ import annotations

import math
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MMO_QUESTS = ROOT / "yap-first-party/gameplay/mmo-content-plugin/src/main/resources/quests"
MMO_BOSSES = ROOT / "yap-first-party/gameplay/mmo-content-plugin/src/main/resources/bosses"
CRAFT_RECIPES = ROOT / "yap-first-party/gameplay/crafting-plugin/src/main/resources/recipes"
MANIFEST = ROOT / "yap-first-party/gameplay/mmo-content-plugin/src/main/resources/content-manifest.txt"

GEAR_TIERS = [
    ("bronze", "COPPER_INGOT", 1, "WOODEN", 25),
    ("iron", "IRON_INGOT", 15, "IRON", 37),
    ("steel", "IRON_INGOT", 30, "IRON", 50),
    ("mithril", "DIAMOND", 50, "DIAMOND", 75),
]

TOOL_SLOTS = [
    ("dagger", "SWORD", 1),
    ("sword", "SWORD", 2),
    ("pickaxe", "PICKAXE", 2),
    ("axe", "AXE", 2),
    ("helmet", "HELMET", 3),
    ("platebody", "CHESTPLATE", 5),
    ("leggings", "LEGGINGS", 4),
    ("boots", "BOOTS", 2),
]

SMELTS = [
    ("smelt_copper", "RAW_COPPER", "COPPER_INGOT", 1, 5.6),
    ("smelt_iron", "IRON_ORE", "IRON_INGOT", 15, 12.5),
    ("smelt_gold", "GOLD_ORE", "GOLD_INGOT", 40, 22.5),
    ("smelt_deepslate_iron", "DEEPSLATE_IRON_ORE", "IRON_INGOT", 15, 12.5),
    ("smelt_deepslate_gold", "DEEPSLATE_GOLD_ORE", "GOLD_INGOT", 40, 22.5),
    ("smelt_ancient", "ANCIENT_DEBRIS", "NETHERITE_SCRAP", 75, 50.0),
]

COOK_FISH = [
    ("cook_cod", "COD", "COOKED_COD", 1, 30, 34),
    ("cook_salmon", "SALMON", "COOKED_SALMON", 5, 40, 40),
    ("cook_tropical", "TROPICAL_FISH", "COOKED_COD", 15, 70, 45),
    ("cook_puffer", "PUFFERFISH", "COOKED_SALMON", 25, 80, 50),
    ("cook_lobster", "TROPICAL_FISH", "COOKED_COD", 40, 120, 60),
]

COOK_MEAT = [
    ("cook_beef", "BEEF", "COOKED_BEEF", 1, 30, 35),
    ("cook_pork", "PORKCHOP", "COOKED_PORKCHOP", 5, 35, 38),
    ("cook_chicken", "CHICKEN", "COOKED_CHICKEN", 10, 40, 42),
    ("cook_mutton", "MUTTON", "COOKED_MUTTON", 15, 45, 45),
    ("cook_rabbit", "RABBIT", "COOKED_RABBIT", 20, 50, 48),
    ("cook_potato", "POTATO", "BAKED_POTATO", 7, 25, 40),
    ("cook_kelp", "KELP", "DRIED_KELP", 1, 15, 30),
    ("cook_bread", "WHEAT", "BREAD", 1, 20, 30),
    ("cook_cookie", "WHEAT", "COOKIE", 5, 25, 35),
]

CRAFT_MISC = [
    ("craft_leather_cap", "LEATHER", 2, "LEATHER_HELMET", 1, 15, 1),
    ("craft_leather_pants", "LEATHER", 3, "LEATHER_LEGGINGS", 1, 22, 5),
    ("craft_leather_boots", "LEATHER", 2, "LEATHER_BOOTS", 1, 18, 1),
    ("craft_shortbow", "STICK", 3, "BOW", 1, 25, 5, "STRING", 2),
    ("craft_crossbow", "STICK", 3, "CROSSBOW", 1, 35, 20, "IRON_INGOT", 1),
    ("craft_arrows_10", "FLINT", 1, "ARROW", 10, 15, 1, "STICK", 1),
    ("craft_arrows_50", "FLINT", 5, "ARROW", 50, 40, 15, "FEATHER", 5),
    ("craft_fishing_rod", "STICK", 3, "FISHING_ROD", 1, 20, 5, "STRING", 2),
    ("craft_shield", "IRON_INGOT", 1, "SHIELD", 1, 30, 10, "OAK_PLANKS", 4),
    ("craft_shears", "IRON_INGOT", 2, "SHEARS", 1, 18, 5),
    ("craft_bucket", "IRON_INGOT", 3, "BUCKET", 1, 20, 8),
    ("craft_torch_16", "COAL", 1, "TORCH", 16, 10, 1, "STICK", 1),
    ("craft_ladder_8", "STICK", 7, "LADDER", 8, 15, 5),
    ("craft_compass", "IRON_INGOT", 4, "COMPASS", 1, 25, 15, "REDSTONE", 1),
    ("craft_clock", "GOLD_INGOT", 4, "CLOCK", 1, 30, 20, "REDSTONE", 1),
]

BOSS_DEFS = [
    ("goblin_king", "&cGoblin King", "ZOMBIE", 200, 120, 64, -40),
    ("stone_titan", "&8Stone Titan", "IRON_GOLEM", 400, -80, 64, 90),
    ("swamp_hydra", "&2Swamp Hydra", "DROWNED", 280, 40, 62, 160),
    ("cave_stalker", "&7Cave Stalker", "SPIDER", 160, -160, 48, -120),
    ("ash_wraith", "&8Ash Wraith", "SKELETON", 220, 200, 70, -200),
    ("frost_brute", "&bFrost Brute", "STRAY", 260, -220, 72, 180),
    ("magma_hound", "&6Magma Hound", "HUSK", 240, 280, 64, 40),
    ("void_shade", "&5Void Shade", "PHANTOM", 300, -300, 80, -60),
    ("thorn_matriarch", "&2Thorn Matriarch", "WITCH", 320, 60, 64, -280),
    ("sand_colossus", "&eSand Colossus", "HUSK", 350, -60, 66, 280),
    ("deep_leviathan", "&3Deep Leviathan", "GUARDIAN", 380, 320, 62, 220),
    ("crypt_lich", "&dCrypt Lich", "WITHER_SKELETON", 420, -340, 55, 120),
    ("ember_drake", "&cEmber Drake", "BLAZE", 360, 180, 75, -160),
    ("hive_queen", "&eHive Queen", "SILVERFISH", 200, -120, 50, -240),
    ("iron_juggernaut", "&7Iron Juggernaut", "VINDICATOR", 450, 240, 64, 300),
    ("shadow_assassin", "&8Shadow Assassin", "PILLAGER", 280, -200, 68, 60),
    ("ancient_golem", "&6Ancient Golem", "RAVAGER", 500, 0, 64, 400),
    ("plague_rat_king", "&2Plague Rat King", "SILVERFISH", 180, 400, 64, -80),
    ("storm_elemental", "&9Storm Elemental", "BREEZE", 340, -400, 90, 0),
    ("world_eater", "&4World Eater", "WARDEN", 800, 0, -40, 0),
]


def yaml_header(title: str) -> str:
    return f"# {title} — generated by scripts/content/generate-mmo-baseline-pack.py\n"


def write_smithing_recipes() -> list[str]:
    lines = [yaml_header("Smithing recipes (generated baseline pack)"), "recipes:"]
    ids: list[str] = []
    for rid, inp, out, lvl, xp in SMELTS:
        ids.append(rid)
        lines += [
            f"  {rid}:",
            "    type: SMITHING",
            "    station: FURNACE",
            "    skill: smithing",
            f"    level: {lvl}",
            f"    name: {rid.replace('_', ' ').title()}",
            "    inputs:",
            f"      - material: {inp}",
            "        amount: 1",
            "    output:",
            f"      material: {out}",
            "      amount: 1",
            f"    xp: {xp}",
            "",
        ]
    for tier, ingot, base_lvl, mat_prefix, base_xp in GEAR_TIERS:
        ingot_amt = 2 if tier == "steel" else 1
        for slot, kind, ingots in TOOL_SLOTS:
            rid = f"{tier}_{slot}"
            ids.append(rid)
            mat = f"{mat_prefix}_{kind}"
            lvl = base_lvl + ingots
            xp = base_xp + ingots * 8
            name = f"{tier.title()} {slot.replace('_', ' ').title()}"
            lines += [
                f"  {rid}:",
                "    type: SMITHING",
                "    station: ANVIL",
                "    skill: smithing",
                f"    level: {lvl}",
                f"    name: {name}",
                "    inputs:",
                f"      - material: {ingot}",
                f"        amount: {ingot_amt if slot != 'platebody' else max(ingot_amt, 3)}",
                "    output:",
                f"      material: {mat}",
                "      amount: 1",
                f"      gear-tier: {tier}",
                f"      display-name: {name}",
                f"    xp: {xp}",
                "",
            ]
    path = CRAFT_RECIPES / "smithing.yml"
    path.write_text("\n".join(lines), encoding="utf-8")
    return ids


def write_cooking_recipes() -> list[str]:
    lines = [yaml_header("Cooking recipes"), "recipes:"]
    ids: list[str] = []
    for row in COOK_FISH + COOK_MEAT:
        rid, raw, cooked, lvl, xp, burn = row
        ids.append(rid)
        lines += [
            f"  {rid}:",
            "    type: COOKING",
            "    skill: cooking",
            f"    level: {lvl}",
            f"    name: {rid.replace('_', ' ').title()}",
            "    inputs:",
            f"      - material: {raw}",
            "        amount: 1",
            "    output:",
            f"      material: {cooked}",
            "      amount: 1",
            f"    xp: {xp}",
            f"    burn-level: {burn}",
            "    burn-chance: 0.35",
            "    burn-output: CHARCOAL",
            "",
        ]
    path = CRAFT_RECIPES / "cooking.yml"
    path.write_text("\n".join(lines), encoding="utf-8")
    return ids


def write_crafting_recipes() -> list[str]:
    lines = [yaml_header("Crafting table recipes"), "recipes:"]
    ids: list[str] = []
    for row in CRAFT_MISC:
        rid = row[0]
        ids.append(rid)
        lines += [
            f"  {rid}:",
            "    type: CRAFTING",
            "    station: CRAFTING_TABLE",
            "    skill: crafting",
            f"    level: {row[6]}",
            f"    name: {rid.replace('_', ' ').title()}",
            "    inputs:",
            f"      - material: {row[1]}",
            f"        amount: {row[2]}",
        ]
        if len(row) > 7:
            lines += [
                f"      - material: {row[7]}",
                f"        amount: {row[8]}",
            ]
        lines += [
            "    output:",
            f"      material: {row[3]}",
            f"      amount: {row[4]}",
            f"    xp: {row[5]}",
            "",
        ]
    for prefix, mat in [("wooden", "OAK_PLANKS"), ("stone", "COBBLESTONE")]:
        lvl = 1 if prefix == "wooden" else 5
        for slot, kind, planks in [
            ("pickaxe", "PICKAXE", 3),
            ("axe", "AXE", 3),
            ("sword", "SWORD", 2),
            ("shovel", "SHOVEL", 1),
            ("hoe", "HOE", 2),
        ]:
            rid = f"craft_{prefix}_{slot}"
            ids.append(rid)
            tool_mat = f"{mat.split('_')[0]}_{kind}" if prefix == "wooden" else kind
            if prefix == "wooden":
                out_mat = f"WOODEN_{kind}"
            else:
                out_mat = f"STONE_{kind}"
            lines += [
                f"  {rid}:",
                "    type: CRAFTING",
                "    station: CRAFTING_TABLE",
                "    skill: crafting",
                f"    level: {lvl}",
                f"    name: {prefix.title()} {slot.title()}",
                "    inputs:",
                f"      - material: {mat}",
                f"        amount: {planks}",
                "    output:",
                f"      material: {out_mat}",
                "      amount: 1",
                f"    xp: {10 + planks * 3}",
                "",
            ]
    path = CRAFT_RECIPES / "crafting.yml"
    path.write_text("\n".join(lines), encoding="utf-8")
    return ids


def write_bosses() -> list[str]:
    MMO_BOSSES.mkdir(parents=True, exist_ok=True)
    ids: list[str] = []
    # Split into 4 files of 5 for maintainability
    for chunk_idx in range(0, 20, 5):
        chunk = BOSS_DEFS[chunk_idx : chunk_idx + 5]
        file_id = chunk_idx // 5 + 1
        lines = [yaml_header(f"Boss pack {file_id}"), "bosses:"]
        for bid, name, entity, hp, x, y, z in chunk:
            ids.append(bid)
            yaw = (chunk_idx * 45) % 360
            lines += [
                f"  {bid}:",
                f'    display-name: "{name}"',
                f"    entity: {entity}",
                f"    health: {hp}",
                "    world: world",
                f"    x: {x}",
                f"    y: {y}",
                f"    z: {z}",
                f"    yaw: {yaw}",
                f"    respawn-seconds: {300 + chunk_idx * 15}",
                "    loot:",
                "      - item: GOLD_INGOT",
                f"        amount: {2 + chunk_idx // 5}",
                f'        name: "&6{bid.replace("_", " ").title()} Token"',
                "",
            ]
        out = MMO_BOSSES / f"pack_{file_id:02d}.yml"
        out.write_text("\n".join(lines), encoding="utf-8")
    return ids


def quest_file(name: str, quests: dict) -> None:
    lines = [yaml_header(name), "quests:"]
    for qid, q in quests.items():
        lines.append(f"  {qid}:")
        lines.append(f'    name: "{q["name"]}"')
        lines.append(f'    description: "{q["desc"]}"')
        if q.get("requires"):
            lines.append(f'    requires: {q["requires"]}')
        lines.append("    objectives:")
        for obj in q["objectives"]:
            lines.append(f"      - id: {obj['id']}")
            lines.append(f"        type: {obj['type']}")
            for k, v in obj.items():
                if k in ("id", "type"):
                    continue
                if isinstance(v, str):
                    lines.append(f'        {k}: {v}')
                else:
                    lines.append(f"        {k}: {v}")
        lines.append("    rewards:")
        for r in q["rewards"]:
            lines.append(f"      - {r}")
        lines.append("")
    MMO_QUESTS.mkdir(parents=True, exist_ok=True)
    (MMO_QUESTS / f"{name}.yml").write_text("\n".join(lines), encoding="utf-8")


def write_quests(boss_ids: list[str], recipe_ids: list[str]) -> list[str]:
    all_ids: list[str] = []

    starter = {
        "starter_mine": {
            "name": "First Strike",
            "desc": "Mine iron ore to learn gathering.",
            "objectives": [{"id": "mine_iron", "type": "GATHER", "material": "IRON_ORE", "amount": 5}],
            "rewards": ["skill_xp:mining:100"],
        },
        "starter_smelt": {
            "name": "Hot Metal",
            "desc": "Gather coal for smelting.",
            "requires": "starter_mine",
            "objectives": [{"id": "coal", "type": "GATHER", "material": "COAL_ORE", "amount": 3}],
            "rewards": ["skill_xp:mining:75", "skill_xp:smithing:75"],
        },
        "starter_craft": {
            "name": "Apprentice Smith",
            "desc": "Craft an iron dagger.",
            "requires": "starter_smelt",
            "objectives": [{"id": "dagger", "type": "CRAFT_ITEM", "recipe": "iron_dagger", "amount": 1}],
            "rewards": ["skill_xp:smithing:150", "unlock_recipe:iron_sword"],
        },
        "starter_boss": {
            "name": "Crown of the Goblin",
            "desc": "Slay the Goblin King.",
            "requires": "starter_craft",
            "objectives": [{"id": "gk", "type": "KILL_BOSS", "boss-id": "goblin_king", "amount": 1}],
            "rewards": ["skill_xp:attack:100", "skill_xp:strength:100", "money:50"],
        },
        "starter_fish": {
            "name": "River's Bounty",
            "desc": "Fish and reach fishing level 5.",
            "requires": "starter_boss",
            "objectives": [
                {"id": "cod", "type": "GATHER", "material": "COD", "amount": 3},
                {"id": "fish_lvl", "type": "SKILL_LEVEL", "skill": "fishing", "level": 5},
            ],
            "rewards": ["skill_xp:fishing:150", "teleport_unlock:fishing_spot"],
        },
    }
    quest_file("starter_chain", starter)
    all_ids.extend(starter.keys())

    mining_chain = {}
    prev = "starter_fish"
    ores = [("COAL_ORE", 10), ("COPPER_ORE", 15), ("IRON_ORE", 20), ("GOLD_ORE", 25), ("DIAMOND_ORE", 30)]
    for i, (ore, amt) in enumerate(ores, 1):
        qid = f"mining_depth_{i}"
        mining_chain[qid] = {
            "name": f"Mining Depth {i}",
            "desc": f"Mine {ore.replace('_', ' ').lower()} for the guild.",
            "requires": prev,
            "objectives": [{"id": "gather", "type": "GATHER", "material": ore, "amount": amt}],
            "rewards": [f"skill_xp:mining:{50 + i * 25}"],
        }
        prev = qid
        all_ids.append(qid)
    quest_file("chain_mining", mining_chain)

    wood_chain = {}
    prev = "mining_depth_5"
    logs = ["OAK_LOG", "BIRCH_LOG", "SPRUCE_LOG", "JUNGLE_LOG", "DARK_OAK_LOG"]
    for i, log in enumerate(logs, 1):
        qid = f"woodland_{i}"
        wood_chain[qid] = {
            "name": f"Woodland Path {i}",
            "desc": f"Chop {log.replace('_', ' ').lower()}.",
            "requires": prev,
            "objectives": [{"id": "chop", "type": "GATHER", "material": log, "amount": 12 + i * 3}],
            "rewards": [f"skill_xp:woodcutting:{40 + i * 20}"],
        }
        prev = qid
        all_ids.append(qid)
    quest_file("chain_woodcutting", wood_chain)

    fish_chain = {}
    prev = "woodland_5"
    fish = ["COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH"]
    for i, f in enumerate(fish, 1):
        qid = f"coastline_{i}"
        fish_chain[qid] = {
            "name": f"Coastline Catch {i}",
            "desc": f"Catch {f.lower()}.",
            "requires": prev,
            "objectives": [{"id": "catch", "type": "GATHER", "material": f, "amount": 5 + i}],
            "rewards": [f"skill_xp:fishing:{60 + i * 15}"],
        }
        prev = qid
        all_ids.append(qid)
    qid = "master_cook"
    fish_chain[qid] = {
        "name": "Master Cook",
        "desc": "Reach cooking level 10.",
        "requires": prev,
        "objectives": [{"id": "cook_lvl", "type": "SKILL_LEVEL", "skill": "cooking", "level": 10}],
        "rewards": ["skill_xp:cooking:200", "unlock_recipe:cook_salmon"],
    }
    all_ids.append(qid)
    quest_file("chain_fishing_cooking", fish_chain)

    smith_chain = {}
    prev = "master_cook"
    crafts = ["bronze_dagger", "iron_dagger", "steel_dagger", "mithril_dagger", "iron_platebody"]
    for i, rid in enumerate(crafts, 1):
        qid = f"forge_step_{i}"
        smith_chain[qid] = {
            "name": f"Forge Step {i}",
            "desc": f"Craft {rid.replace('_', ' ')}.",
            "requires": prev,
            "objectives": [{"id": "craft", "type": "CRAFT_ITEM", "recipe": rid, "amount": 1}],
            "rewards": [f"skill_xp:smithing:{80 + i * 30}"],
        }
        prev = qid
        all_ids.append(qid)
    quest_file("chain_smithing", smith_chain)

    boss_chain = {}
    prev = "forge_step_5"
    hunt_bosses = boss_ids[3:8]  # 5 bosses after starter trio
    for i, bid in enumerate(hunt_bosses, 1):
        qid = f"bounty_{bid}"
        boss_chain[qid] = {
            "name": f"Bounty: {bid.replace('_', ' ').title()}",
            "desc": f"Defeat {bid}.",
            "requires": prev,
            "objectives": [{"id": "kill", "type": "KILL_BOSS", "boss-id": bid, "amount": 1}],
            "rewards": [f"skill_xp:attack:{100 + i * 20}", f"skill_xp:strength:{100 + i * 20}"],
        }
        prev = qid
        all_ids.append(qid)
    quest_file("chain_boss_hunter", boss_chain)

    combat_chain = {}
    prev = f"bounty_{hunt_bosses[-1]}"
    mobs = ["ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "WITCH"]
    for i, mob in enumerate(mobs, 1):
        qid = f"purge_{mob.lower()}"
        combat_chain[qid] = {
            "name": f"Purge the {mob.title()}",
            "desc": f"Slay {mob.lower()}s threatening the roads.",
            "requires": prev,
            "objectives": [{"id": "kill", "type": "KILL_MOB", "entity": mob, "amount": 8 + i * 2}],
            "rewards": [f"skill_xp:attack:{30 + i * 10}", f"skill_xp:defence:{20 + i * 5}"],
        }
        prev = qid
        all_ids.append(qid)
    quest_file("chain_combat", combat_chain)

    return all_ids


def write_manifest(quest_files: list[str], boss_files: list[str]) -> None:
    lines = ["# One resource path per line (relative to plugin jar root)", "DIR quests", "DIR bosses"]
    for f in quest_files:
        lines.append(f"quests/{f}")
    for f in boss_files:
        lines.append(f"bosses/{f}")
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_smithing_extended() -> list[str]:
    lines = [yaml_header("Extended smithing — ores, bars, armor tiers"), "recipes:"]
    ids: list[str] = []
    extra_smelts = [
        ("smelt_coal", "COAL_ORE", "COAL", 1, 4),
        ("smelt_deepslate_coal", "DEEPSLATE_COAL_ORE", "COAL", 1, 4),
        ("smelt_copper_ore", "COPPER_ORE", "COPPER_INGOT", 1, 5),
        ("smelt_lapis", "LAPIS_ORE", "LAPIS_LAZULI", 20, 8),
        ("smelt_redstone", "REDSTONE_ORE", "REDSTONE", 8, 6),
        ("smelt_emerald", "EMERALD_ORE", "EMERALD", 55, 35),
        ("smelt_netherite", "NETHERITE_SCRAP", "NETHERITE_INGOT", 80, 60, "GOLD_INGOT", 1),
    ]
    for row in extra_smelts:
        rid, inp, out, lvl, xp = row[:5]
        ids.append(rid)
        lines += [
            f"  {rid}:",
            "    type: SMITHING",
            "    station: FURNACE",
            "    skill: smithing",
            f"    level: {lvl}",
            f"    name: {rid.replace('_', ' ').title()}",
            "    inputs:",
            f"      - material: {inp}",
            "        amount: 1",
        ]
        if len(row) > 5:
            lines += [f"      - material: {row[5]}", f"        amount: {row[6]}"]
        lines += [
            "    output:",
            f"      material: {out}",
            "      amount: 1",
            f"    xp: {xp}",
            "",
        ]
    iron_tools = [
        ("iron_warhammer", "IRON_SWORD", 2, 20, 45, "iron"),
        ("iron_kiteshield", "SHIELD", 1, 18, 40, "iron"),
        ("iron_full_helm", "IRON_HELMET", 2, 16, 38, "iron"),
        ("iron_chainbody", "IRON_CHESTPLATE", 4, 22, 55, "iron"),
        ("iron_platelegs", "IRON_LEGGINGS", 3, 20, 48, "iron"),
        ("iron_plateboots", "IRON_BOOTS", 2, 14, 35, "iron"),
    ]
    for rid, mat, ingots, lvl, xp, tier in iron_tools:
        ids.append(rid)
        lines += [
            f"  {rid}:",
            "    type: SMITHING",
            "    station: ANVIL",
            "    skill: smithing",
            f"    level: {lvl}",
            f"    name: {rid.replace('_', ' ').title()}",
            "    inputs:",
            "      - material: IRON_INGOT",
            f"        amount: {ingots}",
            "    output:",
            f"      material: {mat}",
            "      amount: 1",
            f"      gear-tier: {tier}",
            f"      display-name: {rid.replace('_', ' ').title()}",
            f"    xp: {xp}",
            "",
        ]
    path = CRAFT_RECIPES / "smithing_extended.yml"
    path.write_text("\n".join(lines), encoding="utf-8")
    return ids


def write_herblore() -> list[str]:
    lines = [yaml_header("Herblore-style potion crafting"), "recipes:"]
    ids: list[str] = []
    potions = [
        ("mix_attack_potion", "BLAZE_POWDER", "GLASS_BOTTLE", "POTION", 5, 25),
        ("mix_strength_potion", "REDSTONE", "GLASS_BOTTLE", "POTION", 8, 30),
        ("mix_defence_potion", "IRON_INGOT", "GLASS_BOTTLE", "POTION", 10, 32),
        ("mix_restore_potion", "GOLDEN_CARROT", "GLASS_BOTTLE", "POTION", 15, 40),
        ("mix_ranged_potion", "FEATHER", "GLASS_BOTTLE", "POTION", 12, 35),
        ("mix_magic_potion", "LAPIS_LAZULI", "GLASS_BOTTLE", "POTION", 18, 42),
        ("mix_antipoison", "MILK_BUCKET", "GLASS_BOTTLE", "POTION", 6, 28),
        ("mix_energy_potion", "SUGAR", "GLASS_BOTTLE", "POTION", 4, 22),
        ("mix_super_attack", "BLAZE_POWDER", "POTION", "POTION", 25, 55),
        ("mix_super_strength", "REDSTONE", "POTION", "POTION", 28, 58),
        ("mix_super_defence", "IRON_INGOT", "POTION", "POTION", 30, 60),
        ("mix_prayer_potion", "GLOWSTONE_DUST", "GLASS_BOTTLE", "POTION", 20, 45),
        ("mix_fishing_potion", "COD", "GLASS_BOTTLE", "POTION", 8, 26),
        ("mix_mining_potion", "COAL", "GLASS_BOTTLE", "POTION", 8, 26),
        ("mix_cooking_potion", "WHEAT", "GLASS_BOTTLE", "POTION", 8, 26),
        ("mix_combo_elixir", "EMERALD", "POTION", "POTION", 45, 90),
        ("mix_combo_elixir2", "DIAMOND", "POTION", "POTION", 55, 110),
        ("mix_combo_elixir3", "NETHERITE_INGOT", "POTION", "POTION", 70, 140),
    ]
    for rid, a, b, out, lvl, xp in potions:
        ids.append(rid)
        lines += [
            f"  {rid}:",
            "    type: CRAFTING",
            "    station: CRAFTING_TABLE",
            "    skill: crafting",
            f"    level: {lvl}",
            f"    name: {rid.replace('_', ' ').title()}",
            "    inputs:",
            f"      - material: {a}",
            "        amount: 1",
            f"      - material: {b}",
            "        amount: 1",
            "    output:",
            f"      material: {out}",
            "      amount: 1",
            f"      display-name: {rid.replace('_', ' ').title()}",
            f"    xp: {xp}",
            "",
        ]
    path = CRAFT_RECIPES / "herblore.yml"
    path.write_text("\n".join(lines), encoding="utf-8")
    return ids


def write_cooking_extended() -> list[str]:
    lines = [yaml_header("Extended cooking"), "recipes:"]
    ids: list[str] = []
    extras = [
        ("cook_rabbit_stew", "RABBIT", "MUSHROOM_STEW", 25, 80, 50),
        ("cook_beetroot_soup", "BEETROOT", "BEETROOT_SOUP", 10, 45, 42),
        ("cook_pumpkin_pie", "PUMPKIN", "PUMPKIN_PIE", 20, 60, 48),
        ("cook_honey_bottle", "HONEY_BOTTLE", "HONEY_BOTTLE", 30, 70, 55),
        ("cook_dried_kelp_block", "KELP", "DRIED_KELP_BLOCK", 15, 55, 45),
        ("cook_golden_apple", "APPLE", "GOLDEN_APPLE", 50, 150, 70, "GOLD_INGOT", 8),
        ("cook_golden_carrot_ext", "CARROT", "GOLDEN_CARROT", 35, 100, 60, "GOLD_NUGGET", 8),
    ]
    for row in extras:
        rid, raw, cooked, lvl, xp, burn = row[:6]
        ids.append(rid)
        lines += [
            f"  {rid}:",
            "    type: COOKING",
            "    skill: cooking",
            f"    level: {lvl}",
            f"    name: {rid.replace('_', ' ').title()}",
            "    inputs:",
            f"      - material: {raw}",
            "        amount: 1",
        ]
        if len(row) > 6:
            lines += [f"      - material: {row[6]}", f"        amount: {row[7]}"]
        lines += [
            "    output:",
            f"      material: {cooked}",
            "      amount: 1",
            f"    xp: {xp}",
            f"    burn-level: {burn}",
            "    burn-chance: 0.25",
            "    burn-output: CHARCOAL",
            "",
        ]
    path = CRAFT_RECIPES / "cooking_extended.yml"
    path.write_text("\n".join(lines), encoding="utf-8")
    return ids


def write_crafting_extended() -> list[str]:
    lines = [yaml_header("Extended crafting — utilities and gear prep"), "recipes:"]
    ids: list[str] = []
    extras = [
        ("craft_iron_pick", "IRON_INGOT", 3, "IRON_PICKAXE", 1, 30, 10),
        ("craft_iron_axe", "IRON_INGOT", 3, "IRON_AXE", 1, 30, 10),
        ("craft_iron_sword", "IRON_INGOT", 2, "IRON_SWORD", 1, 28, 8),
        ("craft_diamond_pick", "DIAMOND", 3, "DIAMOND_PICKAXE", 1, 55, 45),
        ("craft_diamond_sword", "DIAMOND", 2, "DIAMOND_SWORD", 1, 50, 40),
        ("craft_gold_pick", "GOLD_INGOT", 3, "GOLDEN_PICKAXE", 1, 35, 25),
        ("craft_leather_tunic", "LEATHER", 5, "LEATHER_CHESTPLATE", 1, 28, 8),
        ("craft_chainmail_vest", "IRON_INGOT", 4, "CHAINMAIL_CHESTPLATE", 1, 40, 30),
        ("craft_paper_3", "SUGAR_CANE", 3, "PAPER", 3, 12, 3),
        ("craft_book", "PAPER", 3, "BOOK", 1, 18, 5, "LEATHER", 1),
        ("craft_enchant_table", "BOOK", 1, "ENCHANTING_TABLE", 1, 45, 35, "DIAMOND", 2),
        ("craft_anvil", "IRON_BLOCK", 3, "ANVIL", 1, 40, 30, "IRON_INGOT", 4),
        ("craft_brewing_stand", "BLAZE_ROD", 1, "BREWING_STAND", 1, 25, 15, "COBBLESTONE", 3),
        ("craft_glass_bottle_3", "GLASS", 3, "GLASS_BOTTLE", 3, 15, 5),
        ("craft_chest", "OAK_PLANKS", 8, "CHEST", 1, 20, 8),
        ("craft_furnace", "COBBLESTONE", 8, "FURNACE", 1, 18, 5),
        ("craft_campfire", "COAL", 1, "CAMPFIRE", 1, 12, 3, "STICK", 3),
        ("craft_smoker", "FURNACE", 1, "SMOKER", 1, 22, 12, "OAK_LOG", 4),
        ("craft_blast_furnace", "FURNACE", 1, "BLAST_FURNACE", 1, 28, 18, "IRON_INGOT", 5),
        ("craft_stonecutter", "STONE", 3, "STONECUTTER", 1, 20, 10, "IRON_INGOT", 1),
        ("craft_grindstone", "STONE", 2, "GRINDSTONE", 1, 22, 12, "OAK_PLANKS", 2),
        ("craft_lectern", "BOOKSHELF", 1, "LECTERN", 1, 30, 20, "OAK_PLANKS", 4),
        ("craft_bookshelf", "OAK_PLANKS", 6, "BOOKSHELF", 1, 25, 15, "BOOK", 3),
        ("craft_torch_64", "COAL", 1, "TORCH", 64, 25, 10, "STICK", 1),
        ("craft_lantern", "TORCH", 1, "LANTERN", 1, 18, 8, "IRON_NUGGET", 8),
        ("craft_soul_torch", "SOUL_SAND", 1, "SOUL_TORCH", 4, 20, 12, "STICK", 1),
        ("craft_candle_4", "HONEYCOMB", 1, "CANDLE", 4, 15, 8, "STRING", 1),
        ("craft_map", "PAPER", 8, "MAP", 1, 22, 12, "COMPASS", 1),
        ("craft_name_tag", "PAPER", 1, "NAME_TAG", 1, 35, 25, "IRON_INGOT", 1),
        ("craft_saddle", "LEATHER", 5, "SADDLE", 1, 40, 30, "IRON_INGOT", 2),
        ("craft_lead", "STRING", 4, "LEAD", 1, 18, 10, "SLIME_BALL", 1),
        ("craft_tripwire_hook", "IRON_INGOT", 1, "TRIPWIRE_HOOK", 1, 15, 8, "STICK", 1),
        ("craft_stone_bricks", "STONE", 4, "STONE_BRICKS", 4, 12, 5),
        ("craft_brick_block", "BRICK", 4, "BRICKS", 1, 14, 6),
        ("craft_nether_bricks", "NETHERRACK", 4, "NETHER_BRICKS", 4, 20, 15),
        ("craft_end_rod", "BLAZE_ROD", 1, "END_ROD", 1, 25, 18, "CHORUS_FRUIT", 1),
    ]
    for row in extras:
        rid = row[0]
        ids.append(rid)
        lines += [
            f"  {rid}:",
            "    type: CRAFTING",
            "    station: CRAFTING_TABLE",
            "    skill: crafting",
            f"    level: {row[6]}",
            f"    name: {rid.replace('_', ' ').title()}",
            "    inputs:",
            f"      - material: {row[1]}",
            f"        amount: {row[2]}",
        ]
        if len(row) > 7:
            lines += [f"      - material: {row[7]}", f"        amount: {row[8]}"]
        lines += [
            "    output:",
            f"      material: {row[3]}",
            f"      amount: {row[4]}",
            f"    xp: {row[5]}",
            "",
        ]
    path = CRAFT_RECIPES / "crafting_extended.yml"
    path.write_text("\n".join(lines), encoding="utf-8")
    return ids


def main() -> None:
    smith_ids = write_smithing_recipes()
    smith_ext = write_smithing_extended()
    cook_ids = write_cooking_recipes()
    cook_ext = write_cooking_extended()
    craft_ids = write_crafting_recipes()
    craft_ext = write_crafting_extended()
    herb_ids = write_herblore()
    recipe_ids = smith_ids + smith_ext + cook_ids + cook_ext + craft_ids + craft_ext + herb_ids
    boss_ids = write_bosses()
    quest_ids = write_quests(boss_ids, recipe_ids)

    quest_files = sorted(p.name for p in MMO_QUESTS.glob("*.yml"))
    boss_files = sorted(p.name for p in MMO_BOSSES.glob("*.yml"))
    write_manifest(quest_files, boss_files)

    print(f"Generated {len(quest_ids)} quests in {len(quest_files)} files")
    print(f"Generated {len(boss_ids)} bosses in {len(boss_files)} files")
    print(f"Generated {len(recipe_ids)} recipes in recipe packs")
    if len(quest_ids) < 20:
        raise SystemExit("Quest count below bare minimum 20")
    if len(boss_ids) < 20:
        raise SystemExit("Boss count below bare minimum 20")
    if len(recipe_ids) < 150:
        raise SystemExit(f"Recipe count {len(recipe_ids)} below stretch target 150")


if __name__ == "__main__":
    main()
