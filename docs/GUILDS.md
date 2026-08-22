# YaP MMO Guilds

Social progression guilds for the MMO stack — separate from **YaP Factions** (territory/claims).
Guilds focus on levels, perks, bank, chat, and XP from skills/bosses.

## Install

```bash
gradle :guilds-plugin:installIntoPlugins
# or full gameplay:
YAP_GAMEPLAY=1 gradle installGameplayDefaults
```

Requires `yap-db.jar` and shared YaPDB. Optional: `yap-playerdata.jar` (bank), `yap-skills.jar` (skill level-up XP), `yap-mmo-content.jar` (boss kill XP).

## Commands

### Player (`/g` or `/guild`)

| Command | Description |
|---------|-------------|
| `/g create <name> <tag>` | Create guild |
| `/g disband` | Leader disbands |
| `/g join <guild>` | Join (respects join mode) |
| `/g leave` / `/g kick <player>` | Membership |
| `/g invite` / `/g accept\|deny` | Invite flow |
| `/g promote\|demote\|leader` | Roles |
| `/g desc` / `/g motd` | Description & MOTD |
| `/g open\|closed\|inviteonly` | Join mode |
| `/g home\|sethome\|delhome` | Guild hall home |
| `/g chat` / `/g oc` / `/g ac` | Guild, officer, ally chat |
| `/g ally\|enemy\|neutral` | Guild relations |
| `/g level` / `/g perks` / `/g contrib` | Progression info |
| `/g top [page]` / `/g members` | Leaderboards |
| `/g deposit\|withdraw\|bank` | Guild bank |

### Admin (`/yapguilds`)

| Command | Description |
|---------|-------------|
| `/yapguilds reload` | Reload config + service |
| `/yapguilds snapshot json` | Dashboard live snapshot |
| `/yapguilds setlevel <guild> <level> [xp]` | Set level/XP |
| `/yapguilds disband <guild>` | Force disband |

## Guild XP & levels

- XP from: skill level-ups (online members), boss kills, periodic online tick
- Level unlocks: more member slots, higher bank cap (config `guild-xp.*`)
- Perks listed in config `perks.level-N` — shown via `/g perks`

## Roles

`LEADER` → `OFFICER` → `VETERAN` → `MEMBER` → `RECRUIT`

## API

Register `GuildService` on Bukkit ServicesManager. Soft lookup:

```java
GuildServices.find().ifPresent(s -> s.addGuildXp(guildId, playerId, 100, "quest"));
```

## Placeholders (PlaceholderAPI)

| Placeholder | Value |
|-------------|-------|
| `%yapguild_name%` | Guild name |
| `%yapguild_tag%` | Tag |
| `%yapguild_level%` | Level |
| `%yapguild_xp%` | Current level XP |
| `%yapguild_bank%` | Bank balance |
| `%yapguild_max_members%` | Member cap |
| `%yapguild_bank_cap%` | Bank cap |

## Dashboard

`GET /api/guilds` — read-only JSON snapshot.

## Smoke

```bash
./scripts/smoke-guilds-m7.sh
SKIP_LIVE=1 ./scripts/smoke-guilds-m7.sh
```

## vs Factions

| Feature | Guilds | Factions |
|---------|--------|----------|
| Territory/claims | No | Yes |
| Levels/perks | Yes | Power/shield |
| MMO XP hooks | Yes | Death power loss |
| Gameplay bucket | `yap-guilds.jar` | `yap-factions.jar` |
