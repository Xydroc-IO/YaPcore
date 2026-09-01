# YaP MMO Mechanics (`yap-mechanics.jar`)

World interaction layer for the MMO framework: **tools, stamina, resource nodes, farming, fishing physics, fall/projectile tuning**.

## Systems

| System | Config | Behaviour |
|--------|--------|-----------|
| **Tool requirements** | `tools.yml` | Block → required tool type + min tier (wood/stone/iron/diamond) |
| **Stamina** | `config.yml` | Break/fish/sprint drain; regen over time; blocks actions at 0 |
| **Resource nodes** | `nodes.yml` | Fixed coords deplete → respawn on timer |
| **Farming** | `farming.yml` | Right-click mature crop to harvest; plant seeds on farmland |
| **Fishing spots** | `nodes.yml` | `fishing-bonus` multiplier near tagged coords |
| **Physics** | `physics.yml` | Fall/projectile damage multipliers; soft-landing blocks |

## API

`MechanicsService` on Bukkit ServicesManager (`yap-mechanics-api`):

- `canBreak` / `breakDeniedReason`
- `stamina` / `consumeStamina` / `regenStamina`
- `fishingXpMultiplier`
- `fallDamageMultiplier` / `projectileDamageMultiplier`

Other plugins (skills, combat, content) can query via `MechanicsServices.find()`.

## Commands

| Command | Permission |
|---------|------------|
| `/ymechanics reload` | `yapmechanics.admin` |
| `/ymechanics stamina [player]` | self / `yapmechanics.stamina.others` |

## Integration order

Load **before or with** `yap-skills` — both listen on `BlockBreakEvent` at HIGH (tools/stamina) then skills grant XP at MONITOR.

## Smoke

```bash
./scripts/validate-mmo-content.sh
```

## Content scale (recipes)

Regenerate expanded recipe packs (150+):

```bash
python3 scripts/content/generate-mmo-baseline-pack.py
./scripts/validate-mmo-content.sh
```

Recipe files under `plugins/YaPCrafting/recipes/`:

- `smithing.yml`, `smithing_extended.yml`
- `cooking.yml`, `cooking_extended.yml`
- `crafting.yml`, `crafting_extended.yml`
- `herblore.yml`
