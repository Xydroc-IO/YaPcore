#!/usr/bin/env python3
"""Generate YaP MMO Quest Compendium (~100 quests) adapted from operator design.

Maps AuraSkills → YaPSkills, MythicMobs → YaP bosses / vanilla kills,
kits/warps/ranks → kit_unlock / teleport_unlock / group / permission rewards.

Talk quests use empty objectives — turn in at Mayor / Quest Board NPC.
"""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
QUESTS = ROOT / "yap-first-party/gameplay/mmo-content-plugin/src/main/resources/quests"
MANIFEST = ROOT / "yap-first-party/gameplay/mmo-content-plugin/src/main/resources/content-manifest.txt"
QUESTS.mkdir(parents=True, exist_ok=True)


def q(qid, name, desc, objectives, rewards, requires=None):
    return {
        "id": qid,
        "name": name,
        "description": desc,
        "requires": requires,
        "objectives": objectives,
        "rewards": rewards,
    }


def gather(oid, material, amount):
    return [{"id": oid, "type": "GATHER", "material": material, "amount": amount}]


def kill(oid, entity, amount):
    return [{"id": oid, "type": "KILL_MOB", "entity": entity, "amount": amount}]


def boss(oid, boss_id, amount=1):
    return [{"id": oid, "type": "KILL_BOSS", "boss-id": boss_id, "amount": amount}]


def skill(oid, skill_id, level):
    return [{"id": oid, "type": "SKILL_LEVEL", "skill": skill_id, "level": level}]


def craft(oid, recipe, amount=1):
    return [{"id": oid, "type": "CRAFT_ITEM", "recipe": recipe, "amount": amount}]


def dump_file(path: Path, title: str, quests: list) -> None:
    lines = [
        f"# {title}",
        "# YaP MMO Quest Compendium — YaP-Folia / YaPSkills / YaP bosses",
        "",
        "quests:",
    ]
    for quest in quests:
        lines.append(f"  {quest['id']}:")
        lines.append(f'    name: "{quest["name"]}"')
        desc = quest["description"].replace('"', "'")
        lines.append(f'    description: "{desc}"')
        if quest.get("requires"):
            lines.append(f"    requires: {quest['requires']}")
        objs = quest["objectives"]
        if not objs:
            lines.append("    objectives: []")
        else:
            lines.append("    objectives:")
            for o in objs:
                lines.append(f"      - id: {o['id']}")
                lines.append(f"        type: {o['type']}")
                for k, v in o.items():
                    if k in ("id", "type"):
                        continue
                    lines.append(f"        {k}: {v}")
        lines.append("    rewards:")
        for r in quest["rewards"]:
            lines.append(f"      - {r}")
        lines.append("")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {path.name}: {len(quests)} quests")


class Chain:
    def __init__(self, start_requires: str | None = None):
        self.prev = start_requires
        self.quests: list = []

    def add(self, quest: dict) -> None:
        if self.prev and not quest.get("requires"):
            quest["requires"] = self.prev
        self.quests.append(quest)
        self.prev = quest["id"]


def build() -> None:
    t1 = Chain()
    t1.add(q(
        "t1_first_steps", "First Steps",
        "Talk to the Mayor at the Quest Board NPC at spawn. Walk over and right-click to turn in.",
        [],
        ["skill_xp:attack:50", "item:BREAD:5", "kit_unlock:adventurer"],
    ))
    t1.add(q(
        "t1_woodcutter", "Woodcutter's Initiation",
        "Collect 32 Oak Logs to learn gathering.",
        gather("oak", "OAK_LOG", 32),
        ["skill_xp:woodcutting:100", "item:STONE_AXE:1"],
    ))
    t1.add(q(
        "t1_stone_age", "Stone Age",
        "Mine 64 Cobblestone.",
        gather("cobble", "COBBLESTONE", 64),
        ["skill_xp:mining:100", "item:STONE_PICKAXE:1"],
    ))
    t1.add(q(
        "t1_feeding", "Feeding the Village",
        "Cook beef via YaPCrafting (cook_beef) sixteen times.",
        craft("steak", "cook_beef", 16),
        ["skill_xp:cooking:150", "item:GOLDEN_CARROT:5"],
    ))
    t1.add(q(
        "t1_leather", "Leather Armor",
        "Craft a leather tunic (craft_leather_tunic).",
        craft("tunic", "craft_leather_tunic", 1),
        ["skill_xp:crafting:200", "item:EMERALD:10"],
    ))
    t1.add(q(
        "t1_first_hunt", "The First Hunt",
        "Kill 10 Zombies.",
        kill("zombies", "ZOMBIE", 10),
        ["skill_xp:attack:250", "item:IRON_SWORD:1"],
    ))
    t1.add(q(
        "t1_spider", "Spider Slayer",
        "Kill 5 Spiders.",
        kill("spiders", "SPIDER", 5),
        ["skill_xp:ranged:250", "item:BOW:1", "item:ARROW:16"],
    ))
    t1.add(q(
        "t1_cave", "Cave Explorer",
        "Mine deep — break 48 Deepslate while exploring caves near spawn.",
        gather("deepslate", "DEEPSLATE", 48),
        ["skill_xp:mining:300", "item:TORCH:32"],
    ))
    t1.add(q(
        "t1_iron_will", "Iron Will",
        "Smelt iron via YaPCrafting (smelt_iron) sixteen times.",
        craft("smelt", "smelt_iron", 16),
        ["skill_xp:smithing:400", "item:IRON_CHESTPLATE:1"],
    ))
    t1.add(q(
        "t1_farmer", "Farmer's Market",
        "Harvest 64 Wheat.",
        gather("wheat", "WHEAT", 64),
        ["skill_xp:cooking:300", "item:EMERALD:5", "money:25"],
    ))
    t1.add(q(
        "t1_coal_run", "Coal for the Forge",
        "Mine 32 Coal Ore.",
        gather("coal", "COAL_ORE", 32),
        ["skill_xp:mining:200", "item:COAL:16"],
    ))
    t1.add(q(
        "t1_sheep", "Wool Gatherer",
        "Collect 24 White Wool.",
        gather("wool", "WHITE_WOOL", 24),
        ["skill_xp:crafting:150", "item:SHEARS:1"],
    ))
    t1.add(q(
        "t1_skeleton", "Bone Collector",
        "Kill 8 Skeletons.",
        kill("skeles", "SKELETON", 8),
        ["skill_xp:defence:200", "item:BONE:16"],
    ))
    t1.add(q(
        "t1_fishing_intro", "Cast a Line",
        "Catch 10 Cod and reach Fishing level 3.",
        gather("cod", "COD", 10) + skill("fish_lvl", "fishing", 3),
        ["skill_xp:fishing:200", "item:FISHING_ROD:1"],
    ))
    t1.add(q(
        "t1_mining_five", "First Steps in Mining",
        "Reach Mining Level 5 — unlock the mines warp.",
        skill("mine_lvl", "mining", 5),
        ["skill_xp:mining:500", "item:IRON_PICKAXE:1", "teleport_unlock:mines"],
    ))

    t2 = Chain(t1.prev)
    t2.add(q(
        "t2_nether", "Into the Nether",
        "Prove you reached the Nether — kill 5 Magma Cubes.",
        kill("magma", "MAGMA_CUBE", 5),
        ["skill_xp:attack:500", "item:POTION:1", "money:50"],
    ))
    t2.add(q(
        "t2_blaze", "Blaze Hunter",
        "Kill 5 Blazes for blaze rods.",
        kill("blaze", "BLAZE", 5),
        ["skill_xp:magic:600", "item:BLAZE_ROD:5", "item:BOOK:1"],
    ))
    t2.add(q(
        "t2_ender", "Ender Pearl Dive",
        "Kill 10 Endermen.",
        kill("endermen", "ENDERMAN", 10),
        ["skill_xp:magic:700", "item:ENDER_PEARL:4"],
    ))
    t2.add(q(
        "t2_goblin_menace", "Goblin Menace",
        "Kill 20 Zombies outside spawn (Goblin Scout stand-ins).",
        kill("goblin_scouts", "ZOMBIE", 20),
        ["skill_xp:attack:800", "item:LEATHER_CHESTPLATE:1", "money:100"],
    ))
    t2.add(q(
        "t2_miner_delight", "Miner's Delight",
        "Reach Mining Level 15 (YaPSkills).",
        skill("mine15", "mining", 15),
        ["skill_xp:mining:1000", "item:IRON_PICKAXE:1", "teleport_unlock:mining_guild"],
    ))
    t2.add(q(
        "t2_warrior", "Warrior's Path",
        "Reach Strength Level 15 (YaPSkills).",
        skill("str15", "strength", 15),
        ["skill_xp:strength:1000", "item:DIAMOND_SWORD:1"],
    ))
    t2.add(q(
        "t2_lost_mine", "The Lost Mine",
        "Mine 64 Deepslate Iron Ore in abandoned shafts.",
        gather("ds_iron", "DEEPSLATE_IRON_ORE", 64),
        ["skill_xp:mining:1200", "item:GOLD_INGOT:16", "item:CHEST_MINECART:1"],
    ))
    t2.add(q(
        "t2_ocean", "Ocean Monument Scout",
        "Kill 15 Guardians near ocean monuments.",
        kill("guardians", "GUARDIAN", 15),
        ["skill_xp:ranged:1500", "item:POTION:2", "money:75"],
    ))
    t2.add(q(
        "t2_wither_skull", "Wither Skeleton Skull",
        "Kill 25 Wither Skeletons.",
        kill("wskeles", "WITHER_SKELETON", 25),
        ["skill_xp:attack:2000", "item:WITHER_SKELETON_SKULL:1"],
    ))
    t2.add(q(
        "t2_enchanter", "Enchanter's Apprentice",
        "Reach Magic Level 10, then craft an enchanting table.",
        skill("magic10", "magic", 10) + craft("etable", "craft_enchant_table", 1),
        ["skill_xp:magic:1500", "item:LAPIS_LAZULI:32"],
    ))
    t2.add(q(
        "t2_cave_stalker", "Cave Stalker Bounty",
        "Slay the Cave Stalker boss (YaPMmoContent).",
        boss("cs", "cave_stalker"),
        ["skill_xp:attack:900", "skill_xp:defence:400", "money:80"],
    ))
    t2.add(q(
        "t2_ash_wraith", "Ash Wraith Hunt",
        "Slay the Ash Wraith boss.",
        boss("aw", "ash_wraith"),
        ["skill_xp:magic:900", "money:90"],
    ))
    t2.add(q(
        "t2_cook_mid", "Camp Cook",
        "Cook 24 Salmon (cook_salmon).",
        craft("salmon", "cook_salmon", 24),
        ["skill_xp:cooking:600", "item:COOKED_SALMON:16"],
    ))
    t2.add(q(
        "t2_wood_mid", "Timber Rights",
        "Reach Woodcutting Level 15.",
        skill("wc15", "woodcutting", 15),
        ["skill_xp:woodcutting:800", "item:IRON_AXE:1"],
    ))
    t2.add(q(
        "t2_stone_titan", "Stone Titan Trial",
        "Defeat the Stone Titan boss.",
        boss("st", "stone_titan"),
        ["skill_xp:strength:1000", "money:120"],
    ))

    t3 = Chain(t2.prev)
    t3.add(q(
        "t3_diamond", "Diamond Dreams",
        "Mine 10 Diamond Ore.",
        gather("dia", "DIAMOND_ORE", 10),
        ["skill_xp:mining:3000", "item:DIAMOND_PICKAXE:1"],
    ))
    t3.add(q(
        "t3_dragon_breath", "The Dragon's Breath",
        "Kill 5 Ghasts while preparing for the End.",
        kill("ghasts", "GHAST", 5),
        ["skill_xp:ranged:4000", "item:GLASS_BOTTLE:8", "item:GHAST_TEAR:2"],
    ))
    t3.add(q(
        "t3_goblin_king", "Defeat the Goblin King",
        "Kill Goblin King (YaP boss).",
        boss("gk", "goblin_king"),
        ["skill_xp:attack:5000", "item:GOLD_INGOT:64", "item:GOLDEN_HELMET:1", "money:250"],
    ))
    t3.add(q(
        "t3_debris", "Netherite Ambition",
        "Mine 1 Ancient Debris.",
        gather("debris", "ANCIENT_DEBRIS", 1),
        ["skill_xp:mining:6000", "item:NETHERITE_UPGRADE_SMITHING_TEMPLATE:1"],
    ))
    t3.add(q(
        "t3_netherite", "Full Netherite",
        "Reach Smithing Level 40 (full netherite set proxy).",
        skill("smith40", "smithing", 40),
        ["skill_xp:smithing:10000", "group:guardian", "permission:yap.mmo.title.guardian"],
    ))
    t3.add(q(
        "t3_undead", "The Undead Army",
        "Kill 100 Skeletons.",
        kill("army", "SKELETON", 100),
        ["skill_xp:attack:5000", "item:SADDLE:1"],
    ))
    t3.add(q(
        "t3_elder", "Ocean Conqueror",
        "Kill an Elder Guardian.",
        kill("elder", "ELDER_GUARDIAN", 1),
        ["skill_xp:defence:6000", "item:WET_SPONGE:5"],
    ))
    t3.add(q(
        "t3_mansion", "Woodland Explorer",
        "Kill 20 Vindicators (Woodland Mansion).",
        kill("vind", "VINDICATOR", 20),
        ["skill_xp:attack:7000", "item:TOTEM_OF_UNDYING:1"],
    ))
    t3.add(q(
        "t3_witch", "The Witch's Brew",
        "Kill 10 Witches.",
        kill("witch", "WITCH", 10),
        ["skill_xp:magic:5000", "item:GLOWSTONE_DUST:16", "item:POTION:3"],
    ))
    t3.add(q(
        "t3_guild", "Guild Initiate",
        "Create or join a YaP Guild, then talk to the Guild Clerk NPC.",
        [],
        ["skill_xp:attack:5000", "money:200", "permission:yap.guild.claim_bonus"],
    ))
    t3.add(q(
        "t3_frost", "Frost Brute",
        "Slay the Frost Brute boss.",
        boss("fb", "frost_brute"),
        ["skill_xp:defence:3500", "money:150"],
    ))
    t3.add(q(
        "t3_magma_hound", "Magma Hound",
        "Slay the Magma Hound boss.",
        boss("mh", "magma_hound"),
        ["skill_xp:attack:3500", "money:150"],
    ))
    t3.add(q(
        "t3_thorn", "Thorn Matriarch",
        "Slay the Thorn Matriarch boss.",
        boss("tm", "thorn_matriarch"),
        ["skill_xp:ranged:4000", "money:175"],
    ))
    t3.add(q(
        "t3_sand", "Sand Colossus",
        "Slay the Sand Colossus boss.",
        boss("sc", "sand_colossus"),
        ["skill_xp:strength:4000", "money:175"],
    ))
    t3.add(q(
        "t3_attack25", "Battle Hardened",
        "Reach Attack Level 25.",
        skill("atk25", "attack", 25),
        ["skill_xp:attack:5000", "item:DIAMOND_SWORD:1"],
    ))

    t4 = Chain(t3.prev)
    t4.add(q(
        "t4_master_miner", "Master Miner",
        "Reach Mining Level 50 (YaPSkills).",
        skill("mine50", "mining", 50),
        ["skill_xp:mining:15000", "item:NETHERITE_PICKAXE:1", "permission:yap.mmo.title.bedrock_breaker"],
    ))
    t4.add(q(
        "t4_master_lumber", "Master Lumberjack",
        "Reach Woodcutting Level 50.",
        skill("wc50", "woodcutting", 50),
        ["skill_xp:woodcutting:15000", "item:NETHERITE_AXE:1", "permission:yap.mmo.title.tree_feller"],
    ))
    t4.add(q(
        "t4_dragon", "The Dragon Slayer",
        "Kill the Ender Dragon.",
        kill("dragon", "ENDER_DRAGON", 1),
        ["skill_xp:attack:50000", "item:ELYTRA:1", "group:dragon_slayer", "permission:yap.mmo.title.dragon_slayer"],
    ))
    t4.add(q(
        "t4_wither", "Wither Destruction",
        "Kill the Wither Boss.",
        kill("wither", "WITHER", 1),
        ["skill_xp:attack:40000", "item:NETHER_STAR:1", "item:BONE_BLOCK:8"],
    ))
    t4.add(q(
        "t4_raid", "Raid Leader",
        "Kill 50 Pillagers (raid wave proxy).",
        kill("pillagers", "PILLAGER", 50),
        ["skill_xp:attack:20000", "item:TOTEM_OF_UNDYING:3"],
    ))
    t4.add(q(
        "t4_deep_dark", "The Deep Dark",
        "Break 64 Sculk carefully in the Deep Dark.",
        gather("sculk", "SCULK", 64),
        ["skill_xp:mining:25000", "item:SCULK_CATALYST:10"],
    ))
    t4.add(q(
        "t4_warden", "Warden's Bane",
        "Kill the Warden.",
        kill("warden", "WARDEN", 1),
        ["skill_xp:attack:100000", "item:ECHO_SHARD:8", "item:NETHERITE_HELMET:1"],
    ))
    t4.add(q(
        "t4_tycoon", "Economic Tycoon",
        "Craft 50 iron swords for trade stock (economy proxy).",
        craft("trade_stock", "iron_sword", 50),
        ["skill_xp:smithing:30000", "money:10000", "item:GOLD_BLOCK:4"],
    ))
    t4.add(q(
        "t4_fish_legend", "Fisherman's Tale",
        "Reach Fishing Level 50 and catch 64 Cod.",
        skill("fish50", "fishing", 50) + gather("cod64", "COD", 64),
        ["skill_xp:fishing:20000", "item:FISHING_ROD:1"],
    ))
    t4.add(q(
        "t4_architect", "Architect's Dream",
        "Gather 10000 Cobblestone while building.",
        gather("build", "COBBLESTONE", 10000),
        ["skill_xp:crafting:25000", "permission:yap.mmo.flight_token", "money:500"],
    ))
    t4.add(q(
        "t4_void_shade", "Void Shade",
        "Slay the Void Shade boss.",
        boss("vs", "void_shade"),
        ["skill_xp:magic:12000", "money:400"],
    ))
    t4.add(q(
        "t4_leviathan", "Deep Leviathan",
        "Slay the Deep Leviathan boss.",
        boss("dl", "deep_leviathan"),
        ["skill_xp:strength:15000", "money:500"],
    ))
    t4.add(q(
        "t4_lich", "Crypt Lich",
        "Slay the Crypt Lich boss.",
        boss("cl", "crypt_lich"),
        ["skill_xp:magic:15000", "money:500"],
    ))
    t4.add(q(
        "t4_ember", "Ember Drake",
        "Slay the Ember Drake boss.",
        boss("ed", "ember_drake"),
        ["skill_xp:attack:18000", "money:600"],
    ))
    t4.add(q(
        "t4_hive", "Hive Queen",
        "Slay the Hive Queen boss.",
        boss("hq", "hive_queen"),
        ["skill_xp:ranged:18000", "money:600"],
    ))

    t5 = Chain(t4.prev)
    t5.add(q(
        "t5_full_enchant", "Full Enchantment",
        "Reach Magic Level 50 (multi-enchant proxy).",
        skill("magic50", "magic", 50),
        ["skill_xp:magic:50000", "item:ENCHANTED_GOLDEN_APPLE:5"],
    ))
    t5.add(q(
        "t5_collector", "The Collector",
        "Kill 50 Creepers while hunting music discs.",
        kill("creepers", "CREEPER", 50),
        ["skill_xp:defence:75000", "item:JUKEBOX:1", "item:MUSIC_DISC_CAT:1"],
    ))
    t5.add(q(
        "t5_beacon", "Beacon Power",
        "Smelt iron 164 times (beacon pyramid materials proxy).",
        craft("beacon_iron", "smelt_iron", 164),
        ["skill_xp:smithing:60000", "item:BEACON:1"],
    ))
    t5.add(q(
        "t5_nether_biomes", "Nether Conqueror",
        "Kill 30 Hoglins across Nether biomes.",
        kill("hoglin", "HOGLIN", 30),
        ["skill_xp:attack:40000", "item:NETHERITE_BOOTS:1"],
    ))
    t5.add(q(
        "t5_shulker", "End City Loot",
        "Kill 32 Shulkers.",
        kill("shulker", "SHULKER", 32),
        ["skill_xp:magic:50000", "item:SHULKER_SHELL:32", "item:SHULKER_BOX:1"],
    ))
    t5.add(q(
        "t5_hydra", "Mythic Slayer: Hydra",
        "Kill Swamp Hydra (YaP multi-head boss).",
        boss("hydra", "swamp_hydra"),
        ["skill_xp:attack:150000", "item:DIAMOND_SWORD:1", "money:2000"],
    ))
    t5.add(q(
        "t5_void_lord", "Mythic Slayer: Void Lord",
        "Kill Void Shade as Void Lord stand-in.",
        boss("vl", "void_shade"),
        ["skill_xp:magic:200000", "item:ELYTRA:1", "permission:yap.mmo.cosmetic.void_cape"],
    ))
    t5.add(q(
        "t5_potion", "Potion Master",
        "Mix attack potion 100 times (herblore).",
        craft("pots", "mix_attack_potion", 100),
        ["skill_xp:magic:30000", "item:BREWING_STAND:1"],
    ))
    t5.add(q(
        "t5_anvil", "Anvil God",
        "Craft anvils 50 times (anvil-use proxy).",
        craft("anvils", "craft_anvil", 50),
        ["skill_xp:smithing:40000", "item:ENCHANTED_BOOK:3"],
    ))
    t5.add(q(
        "t5_speedster", "Speedster",
        "Reach Defence Level 75 (Agility stand-in).",
        skill("def75", "defence", 75),
        ["skill_xp:defence:50000", "item:DIAMOND_BOOTS:1", "permission:yap.mmo.title.hermes"],
    ))
    t5.add(q(
        "t5_juggernaut", "Iron Juggernaut",
        "Slay the Iron Juggernaut boss.",
        boss("ij", "iron_juggernaut"),
        ["skill_xp:strength:80000", "money:1500"],
    ))
    t5.add(q(
        "t5_shadow", "Shadow Assassin",
        "Slay the Shadow Assassin boss.",
        boss("sa", "shadow_assassin"),
        ["skill_xp:attack:80000", "money:1500"],
    ))
    t5.add(q(
        "t5_golem", "Ancient Golem",
        "Slay the Ancient Golem boss.",
        boss("ag", "ancient_golem"),
        ["skill_xp:defence:90000", "money:1800"],
    ))
    t5.add(q(
        "t5_plague", "Plague Rat King",
        "Slay the Plague Rat King boss.",
        boss("prk", "plague_rat_king"),
        ["skill_xp:hitpoints:70000", "money:1600"],
    ))
    t5.add(q(
        "t5_storm", "Storm Elemental",
        "Slay the Storm Elemental boss.",
        boss("se", "storm_elemental"),
        ["skill_xp:magic:90000", "money:1800"],
    ))

    t6 = Chain(t5.prev)
    t6.add(q(
        "t6_veteran", "Server Veteran",
        "Reach Hitpoints Level 80 (500-hour playtime proxy).",
        skill("hp80", "hitpoints", 80),
        ["group:veteran", "permission:yap.mmo.particle.clouds", "skill_xp:hitpoints:100000"],
    ))
    t6.add(q(
        "t6_quest_master", "Quest Master",
        "Talk to the Quest Master NPC after prior tiers.",
        [],
        ["skill_xp:attack:500000", "permission:yap.mmo.title.quest_master", "money:5000"],
    ))
    t6.add(q(
        "t6_god_gear", "The Ultimate Gear",
        "Reach Smithing Level 75 (God Tier armor proxy).",
        skill("smith75", "smithing", 75),
        ["skill_xp:smithing:1000000", "group:ascended", "permission:yap.mmo.rank.ascended_trial"],
    ))
    t6.add(q(
        "t6_economy_king", "Economy King",
        "Smelt gold 200 times (economy proxy).",
        craft("gold200", "smelt_gold", 200),
        ["permission:yap.mmo.title.midas", "permission:yap.mmo.particle.gold", "money:50000"],
    ))
    t6.add(q(
        "t6_guild_warlord", "Guild Warlord",
        "Reach Attack Level 80 then report to Guild Marshal NPC.",
        skill("atk80", "attack", 80),
        ["skill_xp:attack:200000", "item:GOAT_HORN:1", "money:3000"],
    ))
    t6.add(q(
        "t6_dungeon", "Dungeon Master",
        "Kill World Eater boss (Infinite Dungeon proxy).",
        boss("we", "world_eater"),
        ["skill_xp:attack:300000", "permission:yap.mmo.dungeon_key", "money:4000"],
    ))
    t6.add(q(
        "t6_monster_hunter", "Monster Hunter",
        "Kill 1000 Zombies (MythicMob trash clear proxy).",
        kill("trash", "ZOMBIE", 1000),
        ["skill_xp:attack:250000", "item:LEATHER:64", "permission:yap.mmo.cosmetic.hunter_cloak"],
    ))
    t6.add(q(
        "t6_peacekeeper", "Peacekeeper",
        "Reach Prayer Level 60 (pacifist proxy).",
        skill("pray60", "prayer", 60),
        ["permission:yap.mmo.title.saint", "permission:yap.mmo.cosmetic.halo"],
    ))
    t6.add(q(
        "t6_chaos", "Chaos Bringer",
        "Win 50 PvP arena kills (PLAYER kills).",
        kill("pvp", "PLAYER", 50),
        ["skill_xp:attack:250000", "item:DIAMOND_CHESTPLATE:1", "permission:yap.mmo.title.gladiator"],
    ))
    t6.add(q(
        "t6_architect", "The Architect",
        "Gather 5000 Stone Bricks while building.",
        gather("bricks", "STONE_BRICKS", 5000),
        ["skill_xp:crafting:500000", "permission:yap.mmo.worldedit_token", "money:8000"],
    ))
    t6.add(q(
        "t6_mining99", "Mining Virtuoso",
        "Reach Mining Level 90.",
        skill("mine90", "mining", 90),
        ["skill_xp:mining:400000", "item:NETHERITE_PICKAXE:1"],
    ))
    t6.add(q(
        "t6_combat99", "Combat Virtuoso",
        "Reach Attack Level 90.",
        skill("atk90", "attack", 90),
        ["skill_xp:attack:400000", "item:NETHERITE_SWORD:1"],
    ))
    t6.add(q(
        "t6_all_round", "All-Rounder",
        "Reach Cooking Level 70 and Fishing Level 70.",
        skill("cook70", "cooking", 70) + skill("fish70", "fishing", 70),
        ["skill_xp:cooking:200000", "skill_xp:fishing:200000", "money:10000"],
    ))
    t6.add(q(
        "t6_defence99", "Iron Wall",
        "Reach Defence Level 90.",
        skill("def90", "defence", 90),
        ["skill_xp:defence:400000", "item:NETHERITE_CHESTPLATE:1"],
    ))
    t6.add(q(
        "t6_magic99", "Arcane Master",
        "Reach Magic Level 90.",
        skill("mag90", "magic", 90),
        ["skill_xp:magic:400000", "item:ENCHANTED_GOLDEN_APPLE:16"],
    ))

    ult = Chain(t6.prev)
    ult.add(q(
        "ult_impossible_trio", "The Impossible Trio",
        "Kill Wither, Ender Dragon, and Warden.",
        kill("w", "WITHER", 1) + kill("d", "ENDER_DRAGON", 1) + kill("wa", "WARDEN", 1),
        ["permission:yap.mmo.title.god_slayer", "permission:yap.mmo.cosmetic.cape_god", "skill_xp:attack:500000"],
    ))
    ult.add(q(
        "ult_full_beacon", "Full Beacon",
        "Smelt gold x36 and iron x164 for full beacon materials.",
        craft("g36", "smelt_gold", 36) + craft("i164", "smelt_iron", 164),
        ["permission:yap.mmo.title.beacon_lord", "item:BEACON:2"],
    ))
    ult.add(q(
        "ult_all_skills", "All Skills Maxed",
        "Reach Mining 99 and Attack 99.",
        skill("m99", "mining", 99) + skill("a99", "attack", 99),
        ["group:omni_god", "permission:yap.mmo.rank.omni_god"],
    ))
    ult.add(q(
        "ult_completionist", "Quest Completionist",
        "Speak to the Legend NPC after finishing the chain.",
        [],
        ["permission:yap.mmo.title.server_legend", "permission:yap.mmo.particle.legend_aura", "money:100000"],
    ))
    ult.add(q(
        "ult_economy_dom", "Economy Domination",
        "Craft iron_platebody x100 for shop stock domination.",
        craft("plates", "iron_platebody", 100),
        ["permission:yap.mmo.title.tycoon", "money:250000"],
    ))
    ult.add(q(
        "ult_pvp_undefeated", "PvP Undefeated",
        "Win 100 arena PLAYER kills.",
        kill("arena100", "PLAYER", 100),
        ["item:NETHERITE_SWORD:1", "permission:yap.mmo.title.undefeated"],
    ))
    ult.add(q(
        "ult_dungeon_flawless", "Dungeon Flawless",
        "Slay World Eater again as flawless clear proxy.",
        boss("we2", "world_eater"),
        ["permission:yap.mmo.title.ghost", "item:NETHERITE_CHESTPLATE:1"],
    ))
    ult.add(q(
        "ult_collection", "Collection Master",
        "Defeat Goblin King, Hydra, and Juggernaut again.",
        boss("gk2", "goblin_king") + boss("hy2", "swamp_hydra") + boss("ij2", "iron_juggernaut"),
        ["permission:yap.mmo.title.collector", "money:50000"],
    ))
    ult.add(q(
        "ult_time", "Time Traveler",
        "Reach Hitpoints Level 99 (1000-hour proxy).",
        skill("hp99", "hitpoints", 99),
        ["permission:yap.mmo.title.immortal", "group:immortal"],
    ))
    ult.add(q(
        "ult_end_of_line", "The End of the Line",
        "Reach Attack Level 100 and speak to the Mayor one last time.",
        skill("atk100", "attack", 100),
        [
            "permission:yap.mmo.cosmetic.admin_tag",
            "permission:yap.mmo.cosmetic.custom_join",
            "permission:yap.mmo.statue_honor",
            "money:1000000",
        ],
    ))

    for old in QUESTS.glob("*.yml"):
        old.unlink()
        print("removed", old.name)

    files = [
        ("compendium_tier_01.yml", "Tier 1 — The Adventurer's Beginning", t1.quests),
        ("compendium_tier_02.yml", "Tier 2 — The Rising Hero", t2.quests),
        ("compendium_tier_03.yml", "Tier 3 — The Elite Guardian", t3.quests),
        ("compendium_tier_04.yml", "Tier 4 — The Master Craftsman", t4.quests),
        ("compendium_tier_05.yml", "Tier 5 — The Legendary Hero", t5.quests),
        ("compendium_tier_06.yml", "Tier 6 — The Ascended One", t6.quests),
        ("compendium_ultimate.yml", "Ultimate Completionist Quests", ult.quests),
    ]

    total = 0
    for name, title, qs in files:
        dump_file(QUESTS / name, title, qs)
        total += len(qs)

    manifest = [
        "# One resource path per line (relative to plugin jar root)",
        "DIR quests",
        "DIR bosses",
    ]
    for name, _, _ in files:
        manifest.append(f"quests/{name}")
    manifest.extend(
        [
            "bosses/pack_01.yml",
            "bosses/pack_02.yml",
            "bosses/pack_03.yml",
            "bosses/pack_04.yml",
        ]
    )
    MANIFEST.write_text("\n".join(manifest) + "\n", encoding="utf-8")
    print(f"TOTAL quests: {total}")
    print("manifest updated")


if __name__ == "__main__":
    build()
