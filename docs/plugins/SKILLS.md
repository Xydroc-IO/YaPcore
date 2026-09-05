# YaPSkills

Optional gameplay plugin (`yap-skills.jar`). Thin progression — **mining**, **woodcutting**, **strength** only.

## Requirements

- **YaPDB** (soft-depend; shared MariaDB/Postgres/SQLite pool)
- Enable in `plugins/YaPSkills/config.yml`: `enabled: true`

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/skills` `[player]` | `yapskills.use` | Skills menu (or inspect another player) |
| `/skill top` `<skill>` `[page]` | `yapskills.use` | Leaderboard |
| `/skill set` / `addxp` … | `yapskills.admin` | Staff level/XP |
| `/yskills reload` | `yapskills.admin` | Reload config + skill packs |

## Dashboard

**Gameplay → Skills** — `GET/POST /api/skills` (jar presence, enable flag, online sample).

Install via `gradle installGameplayDefaults` or a full release box (`-PyapGameplay=true`).
