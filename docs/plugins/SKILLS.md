# YaPSkills

Optional gameplay plugin (`yap-skills.jar`). Thin progression — **mining**, **woodcutting**, **strength**, plus a real **overall player level**.

## Progression

| Track | Cap (default) | How XP works |
|-------|---------------|--------------|
| Per-skill | **120** | Mining/woodcutting breaks + melee → strength; RS curve |
| **Overall** | **120** | Stored in `yap_player_overall`. Each skill XP grant also adds `amount × overall.xp-share` (default **0.5**) to overall. Same RS curve, separate table. Continues after a skill is maxed. |
| Total level | n/a | Sum of skill levels (display / PlaceholderAPI only) |

Overall is **not** derived from skill averages — it has its own XP row and levels up independently when skill actions feed it.

Config (`plugins/YaPSkills/config.yml`):

```yaml
xp-table:
  max-level: 120
overall:
  max-level: 120
  xp-share: 0.5
  maxed-xp-share: 0.75
  multiplier: 1.0
```

## Requirements

- **YaPDB** (soft-depend; shared MariaDB/Postgres/SQLite pool)
- Enable in `plugins/YaPSkills/config.yml`: `enabled: true`

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/skills` `[player]` | `yapskills.use` | Skills menu (overall + per-skill) |
| `/skill top` `<skill\|overall>` `[page]` | `yapskills.use` | Leaderboard |
| `/skill set` / `addxp` … | `yapskills.admin` | Staff level/XP |
| `/yskills reload` | `yapskills.admin` | Reload config + skill packs |

## Placeholders (PlaceholderAPI)

- `%yapskill_overall_level%` / `%yapskill_level%` — overall level (1–120)
- `%yapskill_overall_xp%` — stored overall XP
- `%yapskill_total_level%` — sum of skill levels
- `%yapskill_combined_xp%` — sum of skill XP
- `%yapskill_<skill>_level%` / `%yapskill_<skill>_xp%` — per skill

## Dashboard

**Gameplay → Skills** — `GET/POST /api/skills` (jar presence, enable flag, online sample).

Install via `gradle installGameplayDefaults` or a full release box (`-PyapGameplay=true`).
