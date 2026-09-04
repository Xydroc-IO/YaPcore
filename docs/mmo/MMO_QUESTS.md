# YaP MMO Quest Compendium

**100 quests** linking kits, skills, warps, bosses, and ranks — shipped in
`yap-mmo-content` and loaded by `yap-npcs`.

Validate / document objective types:

```bash
python3 scripts/content/generate-mmo-quest-compendium.py
python3 scripts/content/generate-mmo-quest-compendium.py --list-types
./gradlew installGameplayDefaults
```

## Progression flow (intro)

| Quest | Objective | Reward |
|-------|-----------|--------|
| **First Steps** (`t1_first_steps`) | `TALK` Mayor / Quest Board NPC (`npc-id: mayor`) | 50 Attack XP, 5 Bread, unlock `/kit adventurer` |
| **First Steps in Mining** (`t1_mining_five`) | Reach Mining Level 5 | Iron pick, unlock `mines` teleport |
| **Goblin Menace** (`t2_goblin_menace`) | Kill 20 Zombies (**documented Goblin Scout stand-in**) | 800 Attack XP, Scout vest, 100 gold |

## Tiers

| File | Tier | Count | Focus |
|------|------|------:|-------|
| `compendium_tier_01.yml` | 1 Adventurer (1–10) | 15 | Survival, gather, first combat, kit + mines unlock |
| `compendium_tier_02.yml` | 2 Rising Hero (11–25) | 15 | Nether, skills 15, early YaP bosses |
| `compendium_tier_03.yml` | 3 Elite Guardian (26–50) | 15 | Diamonds, Goblin King, guild, mid bosses |
| `compendium_tier_04.yml` | 4 Master Craftsman (51–75) | 15 | Skill 50s, Dragon/Wither/Warden, economy balance, place-blocks |
| `compendium_tier_05.yml` | 5 Legendary (76–90) | 15 | Enchant / anvil use, mythic YaP bosses, cosmetics |
| `compendium_tier_06.yml` | 6 Ascended (91–100+) | 15 | Playtime, TALK NPCs, economy earn, endgame |
| `compendium_ultimate.yml` | Completionist | 10 | Capstone challenges |

All quests form **one linear chain** via `requires` (finish previous to turn in next).

## Objective types

| Type | Progress | YAML keys |
|------|----------|-----------|
| `BREAK_BLOCK` / `GATHER` | Increment on break / fish catch | `material`, `amount` |
| `KILL_MOB` | Increment on kill | `entity`, `amount` |
| `KILL_BOSS` | Increment on YaP boss kill | `boss-id`, `amount` |
| `SKILL_LEVEL` | Snapshot from YaPSkills | `skill`, `level` |
| `CRAFT_ITEM` | Increment on YaP craft | `recipe`, `amount` |
| `PLAYTIME` | Snapshot from YaPPlayerData `play_minutes` | `minutes` |
| `ECONOMY_BALANCE` | Snapshot from balance | `min-balance` |
| `ECONOMY_EARN` | Increment on deposits (`PlayerBalanceChangeEvent`) | `amount` |
| `PLACE_BLOCKS` | Increment on place | `material` (optional), `amount` |
| `ENCHANT` | Increment on enchanting table use | `amount` |
| `ANVIL_USE` | Increment when taking anvil result | `amount` |
| `TALK` | Increment when interacting with matching NPC | `npc-id`, `amount` |

## YaP adaptations (vs AuraSkills / MythicMobs design)

| Design intent | YaP implementation |
|---------------|-------------------|
| AuraSkills levels | **YaPSkills** (`SKILL_LEVEL`) |
| MythicMobs Goblin Scout | `KILL_MOB` Zombie — **documented stand-in** (spawn pack near spawn) |
| MythicBoss Goblin King / Hydra / Void Lord | **YaP bosses** `goblin_king`, `swamp_hydra`, `void_shade` (Void Lord **stand-in**) |
| `/kit adventurer` unlock | `kit_unlock:adventurer` → `yapdata.kit.adventurer` |
| `/warp mines` | `teleport_unlock:mines` (+ `areas.yml` `mines`) |
| VIP trial / titles | `group:` / `permission:yap.mmo.*` via YaPPerms |
| Talk to Mayor (intro) | Empty `objectives: []` — right-click NPC with quest bound |
| Talk to Quest Master / Legend | `TALK` + `npc-id: quest_master` / `legend` |
| Playtime | `PLAYTIME` + YaPPlayerData join/quit minutes |
| Economy holdings / earnings | `ECONOMY_BALANCE` / `ECONOMY_EARN` |
| Place blocks / enchant / anvil | `PLACE_BLOCKS` / `ENCHANT` / `ANVIL_USE` |
| Agility (not shipped) | Defence skill — **documented stand-in** on `t5_speedster` |

There are **no silent proxies** in tier 4–6 / ultimate packs. Remaining zombie/goblin and skill stand-ins are named in quest descriptions and this doc.

## Operator setup

1. Install GAMEPLAY tier (`gradle installGameplayDefaults` or `-PyapGameplay=true`).
2. Ensure MariaDB + YaPDB for quest progress + playerdata playtime.
3. Place NPCs and bind quests:

```text
/npc create mayor
/npc setquest mayor t1_first_steps
/npc create quest_master
/npc setquest quest_master t6_quest_master
/npc create legend
/npc setquest legend ult_completionist
# later: guild clerk → t3_guild
```

4. Spawn Goblin Scout stand-ins (zombies) just outside spawn for `t2_goblin_menace`.
5. Place YaP bosses via `/yapmmo` / boss YAML coords.
6. Add YaPPerms groups `vip`, `guardian`, `veteran`, `ascended`, `dragon_slayer`, `omni_god`, `immortal` as needed for `group:` rewards.

## Player commands

| Command | What |
|---------|------|
| `/quests list` | Known quest ids |
| `/quests progress <id>` | Objective progress |
| Right-click quest NPC | Turn in when complete; also advances `TALK` objectives |

## Reward types

| Prefix | Example |
|--------|---------|
| `skill_xp:` | `skill_xp:mining:500` |
| `item:` | `item:IRON_PICKAXE:1` |
| `money:` | `money:100` |
| `kit_unlock:` | `kit_unlock:adventurer` |
| `teleport_unlock:` | `teleport_unlock:mines` |
| `permission:` | `permission:yap.mmo.title.guardian` |
| `group:` | `group:vip` |
| `unlock_recipe:` | `unlock_recipe:iron_sword` |
| *(raw)* | any console command with `{player}` |

## Related

- [MMO_CONTENT.md](MMO_CONTENT.md) — packs + bosses + recipes  
- [MMO_SKILLS.md](MMO_SKILLS.md) — YaPSkills  
- [GUILDS.md](../gameplay/GUILDS.md) — guilds (manual join for Guild Initiate)  
- [PLAYERDATA.md](../data/PLAYERDATA.md) — kits / warps / economy / playtime  
