# YaP MMO — RuneScape-style skills reference

Full skill set for the YaP MMO stack (`yap-skills`, `yap-combat`, `yap-crafting`, `yap-mmo-content`).

## Skill list (13 skills)

| Skill | XP sources | Plugin |
|-------|------------|--------|
| **Mining** | Breaking ores/stone | yap-skills |
| **Woodcutting** | Breaking logs | yap-skills |
| **Fishing** | Catching fish | yap-skills |
| **Cooking** | Smelting/cooking at furnace | yap-skills |
| **Smithing** | Smelting bars, anvil smithing | yap-skills + yap-crafting |
| **Crafting** | Crafting table recipes | yap-crafting |
| **Attack** | Melee damage dealt | yap-combat |
| **Strength** | Melee damage dealt (shared) | yap-combat |
| **Defence** | Damage taken | yap-combat |
| **Hitpoints** | Combat XP ratio | yap-combat |
| **Ranged** | Bow/crossbow damage dealt | yap-combat |
| **Magic** | Spell cast + spell damage | yap-combat |
| **Prayer** | Prayer points drained while active | yap-combat |

All skills use the shared RS-style XP table (`max-level: 99`) in `plugins/YaPSkills/config.yml`.

## Combat styles

YaPCombat routes XP and accuracy by **combat style**:

| Style | Trigger | Accuracy | Max hit | XP skills |
|-------|---------|----------|---------|-----------|
| **Melee** | Sword, axe, etc. | Attack + gear vs Defence | Strength + gear | Attack, Strength, Hitpoints |
| **Ranged** | Bow, crossbow, projectile | Ranged + gear vs Defence | Ranged + gear | Ranged, Hitpoints |
| **Magic** | `/cast <spell>` | Magic + gear vs Defence | Spell base + magic level | Magic, Hitpoints |

Gear bonuses (`items.yml`) add to accuracy/max hit: `attack-bonus`, `strength-bonus`, `defence-bonus`, `ranged-bonus`, `magic-bonus`, `prayer-bonus`.

## Spell book

Config: `plugins/YaPCombat/spells.yml`

| Command | Description |
|---------|-------------|
| `/spells` | List spells unlocked by your Magic level |
| `/cast <id>` | Cast at targeted entity (ray trace, 20 blocks) |

Each spell defines:

- `min-magic-level` — level gate
- `prayer-cost` — prayer points consumed on cast
- `max-hit` — base spell damage (scaled by magic level)
- `cast-xp` — Magic XP on cast
- `runes` — inventory materials consumed per cast (e.g. `LAPIS_LAZULI: 1`)
- `required-staff` — optional staff/wand in main or off hand
- `target-filter` — e.g. `undead` for Crumble Undead

Default spells: `wind_strike`, `water_strike`, `fire_bolt`, `crumble`.

## Prayer

Config: `plugins/YaPCombat/prayers.yml`

| Command | Description |
|---------|-------------|
| `/prayer list` | Prayers available at your Prayer level |
| `/prayer on <id>` | Activate a prayer (drains points each tick) |
| `/prayer off [id]` | Deactivate one or all prayers |

**Prayer points:** max = Prayer level. Full restore on respawn. Partial restore via `GLISTERING_MELON_SLICE` (+30) and `GOLDEN_CARROT` (+15) in `food.yml`.

**Prayer effects** (wired into combat formulas):

| Prayer | Effect |
|--------|--------|
| `thick_skin` | +5 Defence |
| `burst_of_strength` | +5 Strength |
| `clarity_of_thought` | +5 Attack |
| `hawk_eye` | +8 Ranged |
| `mystic_might` | +8 Magic |
| `protect_from_melee` | 40% melee damage reduction |
| `protect_from_missiles` | 40% ranged damage reduction |
| `protect_from_magic` | 40% magic damage reduction |

Only one prayer per **boost group** and one **overhead** (protect) at a time. `prayer-bonus` gear reduces drain rate (1% per bonus point).

**Prayer XP:** `xp-per-point` in `plugins/YaPSkills/skills/prayer.yml` (default 0.5 XP per point drained).

## Ranged accuracy

Ranged uses a dedicated roll in `DamageCalculator.rollRanged()`:

```
effectiveRanged = rangedLevel + rangedBonus + boosts
maxHit = floor(effectiveRanged × levelFactor)
attackRoll = effectiveRanged + random(0..effectiveRanged)
defenceRoll = defenceLevel + defenceBonus + random(0..defence)
hit if attackRoll > defenceRoll
```

Projectiles (arrows) and bow/crossbow in hand use **Ranged** style automatically.

## Skill YAML packs

One file per skill under `plugins/YaPSkills/skills/`:

```yaml
# skills/ranged.yml
id: ranged
display: Ranged
icon: BOW
ranged-dealt:
  xp-per-damage: 2.0
  share: 1.0

# skills/magic.yml
magic-dealt:
  xp-per-damage: 2.0
  share: 1.0

# skills/prayer.yml
prayer-drain:
  xp-per-point: 0.5
```

Reload: `/yskills reload`

## GUI

`/skills` opens a 54-slot menu showing all enabled skills plus **combat level** (OSRS-weighted formula including Ranged, Magic, and Prayer).

## Combat level

```
base = 0.25 × (Defence + Hitpoints + floor(Prayer/2))
melee = 0.325 × (Attack + Strength)
ranged = 0.325 × (Ranged + floor(Ranged/2))
magic = 0.325 × (Magic + floor(Magic/2))
combatLevel = floor(base + max(melee, ranged, magic))
```

Implemented in `CombatLevelCalculator` (yap-mmo-api) and shown in `/skills` + PlaceholderAPI `%yapskills_combat_level%`.

## Permissions

| Node | Grants |
|------|--------|
| `yapcombat.cast` | `/cast` |
| `yapcombat.prayer` | `/prayer` |
| `yapcombat.use` | `/combat` stats |
| `yapskills.use` | Gain XP, `/skills` |

See [PERMISSIONS.md](PERMISSIONS.md) for the full list.

## Folia safety

All combat mutations (damage, spell cast, prayer drain) run on entity/region schedulers via `YapSched`. Prayer drain uses `GlobalRegionScheduler` timer with per-player `entity()` callbacks.

## Related docs

- [MMO_PHASES.md](MMO_PHASES.md) — milestone plan
- [MMO_SKILLS.md](MMO_SKILLS.md) — M1 skill checklist (if present)
- Combat config: `plugins/YaPCombat/config.yml`
