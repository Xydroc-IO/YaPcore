# YaPDungeons

Optional gameplay plugin (`yap-dungeons.jar`). Procedural **instanced dungeons** with party invites, skill gates, shared lives, and ephemeral world cleanup.

Layouts use **non-overlapping** rooms, framed **doorways**, themed **room templates** (entrance / combat / treasure / trap / boss), corridors with lighting, and a bedrock foundation pad so the dungeon sits as a readable complex rather than floating boxes.

## Progression

| Band | Levels | Notes |
|------|--------|-------|
| Locked | — | YaPSkills **overall &lt; 10** → no entry |
| Core | **1–50** | Clear L unlocks L+1; can replay any cleared level |
| Prestige | **51–100** | Requires clear of dungeon **50**; separate prestige clear ladder |

Per-level **mining / strength** floors ship in `plugins/YaPDungeons/dungeons/gates.yml` (all 100 levels).

## Requirements

- **YaPDB** (soft; shared MariaDB/Postgres/SQLite pool)
- **YaPSkills** (soft; required for gates — overall + mining/strength)
- **YaPWorld** (soft; preferred for `createWorld` / `deleteWorld`; Bukkit fallback exists)
- Enable in `plugins/YaPDungeons/config.yml`: `enabled: true`

## Player loop

1. **Craftable portal:** craft a Dungeon Portal item, place it, right-click.
2. **Buildable portal (base):** build a standing **4×5** obsidian frame (like a nether portal). When complete you get a chat tip — right-click any frame block with an **Ender Eye** to activate. Right-click the lit portal to open the menu.
3. Or `/dungeon open` from anywhere.
4. Pick an unlocked level → instance generates into an ephemeral `yd_*` world.
5. Invite with `/dungeon invite <player>`; they `/dungeon accept <prefix>`.
6. Kill the **Dungeon Boss** to clear → loot + unlock next → world deleted after grace.
7. Shared **party lives** (base 3 + 1 per extra member, cap 6). Inventory kept on death.

### Buildable frame shape (default)

Outer **4 wide × 5 tall** obsidian (inner opening **2×3**), facing north/south or east/west — same proportions as a nether portal. Configurable under `portal.structure` in `config.yml`.

**Materials**

| Portal type | Materials |
|-------------|-----------|
| Buildable frame | Obsidian (frame) + 1 Ender Eye to activate |
| Craftable item | 4 Obsidian + 2 Deepslate + 3 Ender Eyes (3×3 recipe) |

Staff: `/yapdungeons giveportal [player]`.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/dungeon` `[open]` | `yapdungeons.use` | Level picker GUI |
| `/dungeon invite\|accept\|deny\|leave\|status` | `yapdungeons.use` | Party + run status |
| `/yapdungeons reload` | `yapdungeons.admin` | Reload config + packs |
| `/yapdungeons forcestop <runId>` | `yapdungeons.admin` | Fail + cleanup run |
| `/yapdungeons giveportal [player]` | `yapdungeons.admin` | Give portal item |

## Config packs

Under `plugins/YaPDungeons/dungeons/`:

| File | Contents |
|------|----------|
| `gates.yml` | overall / mining / strength per level 1–100 |
| `difficulty.yml` | rooms, mobs, HP/damage mults, boss HP, traps |
| `themes.yml` | material + mob palettes by band |
| `loot.yml` | guaranteed / rare drops + economy per level |

## Placeholders

- `%yapdungeon_highest%` / `%yapdungeon_prestige%`
- `%yapdungeon_completions%`
- `%yapdungeon_in_run%` / `%yapdungeon_level%` / `%yapdungeon_lives%`
- `%yapdungeon_active_runs%`

## API

```java
DungeonServices.find().ifPresent(dungeons ->
    dungeons.startRun(player, 1));
```

Jar: `yap-dungeons-api.jar` (`com.yapcore.dungeons.DungeonService`).

## Install

```bash
gradle installGameplayDefaults
# or full box:
gradle assembleRelease -PyapGameplay=true
```

Set `enabled: true` in `plugins/YaPDungeons/config.yml` after first boot.
