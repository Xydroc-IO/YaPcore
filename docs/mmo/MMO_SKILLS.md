# YaP MMO Skills (M1+)

Thirteen RuneScape-style progression skills ship in **`yap-skills.jar`** (`YaPSkills`), config-driven via
`plugins/YaPSkills/skills/*.yml`.

> Full skill list (combat styles, prayers, spells): [MMO_RS_SKILLS.md](MMO_RS_SKILLS.md)

## Skill list (progression)

| Skill | Config file | Primary XP source |
|-------|-------------|-------------------|
| Mining | `mining.yml` | Breaking ores/stone |
| Woodcutting | `woodcutting.yml` | Breaking logs |
| Fishing | `fishing.yml` | `PlayerFishEvent` |
| Cooking | `cooking.yml` | Furnace extract |
| Smithing | `smithing.yml` | Furnace smelt + crafting stations |
| Crafting | `crafting.yml` | Recipe crafts (`yap-crafting`) |
| Attack | `attack.yml` | Melee damage dealt |
| Strength | `strength.yml` | Melee damage dealt |
| Defence | `defence.yml` | Damage taken |
| Hitpoints | `hitpoints.yml` | Combat XP ratio |
| Ranged | `ranged.yml` | Ranged damage dealt |
| Magic | `magic.yml` | Magic damage / spell casts |
| Prayer | `prayer.yml` | Prayer drain |

Custom combat XP flows through **`yap-combat.jar`** when loaded (M2+). See [MMO_COMBAT.md](MMO_COMBAT.md).

## Combat level (display)

Configured in `plugins/YaPSkills/config.yml` — see [MMO_RS_SKILLS.md](MMO_RS_SKILLS.md) for the RS-style formula.

Placeholders: `%yapskill_combat_level%`, `%yapskill_<skill>_level%`, `%yapskill_total_level%`

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/skills [player]` | `yapskills.use` / `yapskills.others` | Chest GUI with all skills + combat level |
| `/skill top <skill> [page]` | *(none)* | Async DB leaderboard (10 per page) |
| `/skill addxp <player> <skill> <amount>` | `yapskills.admin` | Grant XP |
| `/skill set <player> <skill> <level>` | `yapskills.admin` | Set level (`setlevel` alias) |
| `/yskills reload` | `yapskills.admin` | Reload config + skill packs |

Skill GUI icons use dedicated **`CLAY_BALL` + CustomModelData** tokens (`icon` / `icon-cmd` in each skill YAML) so the menu does not borrow swords, pickaxes, or other gameplay items. Regenerate textures with `python3 scripts/generate-mmo-icons.py` (see [MMO_ABILITIES.md](MMO_ABILITIES.md)).

Bedrock players: `/skills` opens a FormService panel when `yap-mmo-bedrock.jar` is loaded — [MMO_BEDROCK_UI.md](MMO_BEDROCK_UI.md).

## Level gates

Each action in a skill YAML may set `min-level`. Blocked actions show a chat message citing the required level.

## Dashboard

`GET /api/mmo` — read-only snapshot when skills plugin is loaded (M1+).

## Smoke

```bash
./scripts/validate-mmo-content.sh
```

## See also

- [MMO_PHASES.md](MMO_PHASES.md) — milestone plan M0–M7
- [MMO_ABILITIES.md](MMO_ABILITIES.md) — combat abilities (separate from RS skills)
- [PERMISSIONS.md](../ops/PERMISSIONS.md) — `yapskills.*`
