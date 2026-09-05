# Built-in PlaceholderAPI (YaP)

YaPcore ships a **clean-room, clip-compatible** PlaceholderAPI so plugins that
soft/hard-depend on HelpChat’s PlaceholderAPI work **without** installing
`PlaceholderAPI.jar` from SpigotMC / Modrinth.

| Item | Value |
|------|--------|
| Product jar | `plugins/yap-placeholderapi.jar` |
| Plugin name (for `getPlugin` / depends) | **`PlaceholderAPI`** |
| Package | `me.clip.placeholderapi.*` (binary-compatible surface) |
| Built-in expansions | `player`, `server` (full upstream-style placeholder sets) |
| Extra expansions | Drop jars into `plugins/PlaceholderAPI/expansions/` |
| Admin | `/papi` full local command tree |

## Intentional product scope

YaP PlaceholderAPI is a **first-party, curated local-expansions** engine — not a
partial stub and not a HelpChat eCloud mirror.

| In scope | Out of scope (by design) |
|----------|---------------------------|
| Parse API, built-ins `player` / `server` | HelpChat eCloud browse / download / update |
| Jar-drop external expansions | Bundling third-party expansion jars in release zips |
| First-party `%yap*_*%` from other YaP plugins | GPL eCloud coupling |
| `/papi parse\|list\|reload\|register\|dump\|…` | |

Operators curate expansions on disk. That is the supported delivery path.

## Product rule

Do **not** also install HelpChat / clip PlaceholderAPI. Two jars both named
`PlaceholderAPI` will conflict. YaP’s jar is the supported path.

```bash
gradle :placeholderapi-plugin:installIntoPlugins
# or: gradle shadowJar / assembleRelease
```

## Local expansions workflow

1. Create (auto on first enable) or open `plugins/PlaceholderAPI/expansions/`
2. Copy a compatible expansion `.jar` into that folder
3. Run `/papi reload` or `/papi register <jar>`
4. Confirm with `/papi list` / `/papi info <id>`

```text
plugins/PlaceholderAPI/expansions/
```

`/papi ecloud` explains this workflow (eCloud download UX is intentionally absent).

## Commands

| Command | Purpose |
|---------|---------|
| `/papi help` | Command list |
| `/papi parse <me\|--null\|player> <text…>` | Parse placeholders |
| `/papi bcparse <target> <text…>` | Broadcast parsed text |
| `/papi cmdparse <target> <text…>` | Dispatch parsed text as a command |
| `/papi parserel <p1> <p2> <text…>` | Relational placeholders |
| `/papi dump` | Local dump file + optional paste.helpch.at upload |
| `/papi list` / `info [id]` / `reload` | Expansion admin |
| `/papi register <jar>` / `unregister <id>` | Expansion folder admin |
| `/papi version` | Engine version |
| `/papi ecloud` | Points at local expansions folder workflow (no eCloud) |

## What works for other plugins

- `softdepend: [PlaceholderAPI]` / `depend: [PlaceholderAPI]`
- `Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null`
- `PlaceholderAPI.setPlaceholders(player, text)` (+ bracket / relational)
- `new MyExpansion().register()` extending `PlaceholderExpansion`
- Expansion register / unregister events
- `PlaceholderAPIPlugin.getAdventure()` (BukkitAudiences)
- `Msg`, `booleanTrue/False`, `getDateFormat()`, `getServerVersion()`
- Configurable / Cacheable / Cleanable / Taskable / Relational / VersionSpecific

## Built-in placeholders

**Player** — name, displayname, uuid, health*, food, level/exp*, gamemode, world*,
coords, yaw/pitch, biome*, direction*, ping/colored_ping, armor_*, item_in_hand*,
permissions (`has_permission_*`), potion effects, locale*, flight/sneak/sprint/dead
flags, bed/compass, first/last join, and more.

**Server** — name, online / `online_<world>`, max_players, unique_joins, version/build,
tps / colored tps, uptime, ram_*, whitelist, total_* entity/chunk counts, `time_*`
patterns, countdown/countup helpers.

## First-party expansions

Registered **in-process** by other YaP jars when installed (no jar drop needed):

| Plugin | Identifier | Notes |
|--------|------------|--------|
| yap-perms | `yapperms` | Permission / group placeholders |
| skills | `yapskills` | Skills / levels — [SKILLS.md](SKILLS.md) |
| factions | `yapfactions` | Faction data |
| stacker | `yapstacker` | See [STACKER.md](STACKER.md) |

## Ops extras

- **bStats** charts (YaP service id, not HelpChat’s)
- **Update checker** — `check-updates` + optional `update-check-url`
- **Adventure** — `getAdventure()` for expansions that send components

## Honesty bar

Clean-room compatibility engine — not a GPL port. Expansion delivery is
**intentionally local and operator-curated**; eCloud download UX is out of scope.
Everything else above is in-tree.

See also [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md) · [PLUGINS.md](PLUGINS.md).
