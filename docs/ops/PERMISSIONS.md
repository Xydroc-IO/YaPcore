# Permissions & ranks

YaP first-party plugins gate commands with **Bukkit permission nodes**.
**Native ranks** ship in **`yap-perms.jar`** (`YaPPerms`) — groups, inheritance, tracks,
prefix/suffix meta, MariaDB-backed, PlaceholderAPI expansion `%yapperms_*%`.

## Quick start (native — default on product installs)

```bash
gradle installProductDefaults
./scripts/db/ensure-db.sh --server-id lobby
./scripts/start.sh --fg
# console or dashboard:
ranks apply
/yapperm user Steve parent set vip
/promote Steve
```

Starter pack (`default` → `vip` → `mod` → `admin` + track `yap`) applies automatically
on first boot, or via `ranks apply` / `/yapperm applypack`.

Optional auto-apply on boot (default **on** for new installs):

```properties
yap-ranks-auto-apply=true
```

Set `false` to disable. Requires `yap-perms.jar`; waits ~8s after start.

| Surface | How |
|---------|-----|
| Console / stdin | `ranks status` · `ranks apply` · `ranks apply force` |
| In-game | `/yapperm applypack` · `/promote` · `/demote` |
| Web dashboard | **Ranks** tab → Apply pack |
| Config | `plugins/YaPPerms/config.yml` |

OP still receives every node with `default: op` without YaPPerms attachments.

## YaPPerms admin commands

| Command | Permission |
|---------|------------|
| `/yapperm user <player> info` | `yapperm.user` / admin |
| `/yapperm user <player> parent set\|add\|remove <group>` | `yapperm.admin` |
| `/yapperm user <player> permission set <node> true\|false` | `yapperm.admin` |
| `/yapperm user <player> meta set <prefix> [suffix]` | `yapperm.admin` |
| `/yapperm group create\|delete\|list\|info\|setprefix …` | `yapperm.admin` |
| `/yapperm track list\|info` | `yapperm.admin` |
| `/yapperm applypack` | `yapperm.admin` |
| `/promote <player> [track]` | `yapperm.promote` |
| `/demote <player> [track]` | `yapperm.demote` |

## Rank ladder (starter pack)

| Rank | Weight | Prefix | Inherits | Role |
|------|--------|--------|----------|------|
| `default` | 0 | *(none)* | — | All players |
| `vip` | 10 | `[VIP]` | default | Donors / trusted |
| `mod` | 50 | `[Mod]` | vip | Moderators |
| `admin` | 100 | `[Admin]` | mod | Admins (not necessarily OP) |

Track name: **`yap`**. Promote with `/promote Steve` or `/yapperm user Steve parent set vip`.

**Web store (Tebex):** run those commands on the **Hub** backend console — [TEBEX.md](TEBEX.md).

## YaPChat nodes

| Node | Default | Feature |
|------|---------|---------|
| `yapchat.use` | true | Public chat |
| `yapchat.msg` | true | `/msg` `/reply` |
| `yapchat.staff` | op | `/staffchat` staff channel |
| `yapchat.socialspy` | op | See private messages |
| `yapchat.admin` | op | `/clearchat` `/yapchat reload` |
| `yapchat.bypass.filter` | op | Skip word filter |
| `yapchat.bypass.slow` | op | Skip slow mode |

## YaPModeration nodes

| Node | Default | Feature |
|------|---------|---------|
| `yapmod.ban` | op | `/ban` `/tempban` `/unban` |
| `yapmod.ipban` | op | `/ipban` `/unbanip` |
| `yapmod.mute` | op | `/mute` `/tempmute` `/unmute` |
| `yapmod.warn` | op | `/warn` |
| `yapmod.kick` | op | `/kick` |
| `yapmod.history` | op | `/modhistory` `/modcheck` `/banlist` |
| `yapmod.admin` | op | `/yapmod reload` |

## YaPEssentials nodes

| Node | Default | Command / feature |
|------|---------|-------------------|
| `yapessentials.spawn` | true | `/spawn` |
| `yapessentials.setspawn` | op | `/setspawn` |
| `yapessentials.back` | true | `/back` |
| `yapessentials.tpa` | true | `/tpa` `/tpahere` `/tpaccept` `/tpdeny` |
| `yapessentials.teleport` | op | `/tp` `/tphere` |
| `yapessentials.fly` / `.god` / `.speed` / `.heal` / `.feed` | op | QoL toggles |
| `yapessentials.repair` / `.clear` / `.vanish` | op | Item / vanish tools |
| `yapessentials.invsee` / `.echest` / `.nick` | op | Staff QoL |
| `yapessentials.nick.others` | op | `/nick` for others |
| `yapessentials.afk` / `.list` / `.rules` / `.motd` / `.suicide` | true | Player info |
| `yapessentials.ptime` / `.pweather` / `.broadcast` / `.hat` | op | Client / broadcast |
| `yapessentials.staff.socialspy` | op | `/socialspy` |
| `yapessentials.staff.freeze` | op | `/freeze` |
| `yapessentials.staff.check` | op | `/check` |
| `yapessentials.admin` | op | `/yapess reload` |

Toggle domains in `plugins/YaPEssentials/config.yml` under `features.*` (including `features.staff`).

## YaPAdmin nodes

| Node | Default | Command / feature |
|------|---------|-------------------|
| `yapadmin.menu` | op | `/yapadmin` `/staff` open hub |
| `yapadmin.give` | op | Give presets / materials / kits |
| `yapadmin.server` | op | Broadcast presets, `/yapadmin reload` |
| `yapadmin.economy` | op | Money grants (`/yapmmo givemoney`) |

Grant `yapadmin.menu` (+ give/server) on `mod` / `admin` ranks. Individual actions still need the underlying plugin nodes (`yapessentials.*`, `yapmod.*`, …). See [ADMIN_MENU.md](ADMIN_MENU.md).

## YaPProtect / YaPWorld

| Node | Default | Command / feature |
|------|---------|-------------------|
| `yapprotect.admin` | op | `/yapprotect status|reload|prune` |
| `yapprotect.lookup` | op | `/yapprotect lookup user\|block …` |
| `yapprotect.rollback` | op | `/yapprotect rollback <id>…` |
| `yapworld.admin` | op | `/yapworld status|reload` |
| `yapworld.load` / `.unload` | op | `/yapworld load\|unload <world>` |
| `yapworld.teleport` | op | `/yapworld tp <world> [player]` |
| `yapworld.selection` | op | `/yapworld wand`, pos1/pos2 |
| `yapworld.schematic` | op | `/yapworld schem save\|paste\|import` |
| `yapworld.brush` | op | `/yapworld brush`, undo/redo |
| `yapworld.pregen` | op | `/yapworld pregen …` |

## YaPRegions / YaPGuard / YaPMap / YaPNpcs

| Node | Default | Feature |
|------|---------|---------|
| `yapregions.admin` | op | `/region define`, flag, list |
| `yapguard.admin` | op | `/yapguard reload`, status |
| `yapguard.bypass` | op | Skip anti-cheat checks |
| `yapguard.alerts` | op | Staff violation alerts |
| `yapmap.admin` | op | Map render config / reload |
| `yapnpcs.admin` | op | `/npc create\|remove` |
| `yapnpcs.quest` | true | `/quests list\|progress` |

See [REGIONS.md](../gameplay/REGIONS.md) for claim flag commands (`/claim flag set`).

Dashboard: `POST /api/protect`, `POST /api/world`.

## Playerdata command nodes

| Node | Default | Command / feature |
|------|---------|-------------------|
| `yapdata.menu` | true | `/menu` |
| `yapdata.balance` | true | `/bal` (self) |
| `yapdata.balance.others` | op | `/bal <other>` |
| `yapdata.pay` | true | `/pay` |
| `yapdata.home` | true | `/home` `/sethome` `/delhome` `/homes` |
| `yapdata.warp` | true | `/warp` `/warps` |
| `yapdata.warp.admin` | op | `/setwarp` `/delwarp` |
| `yapdata.kit` | true | `/kit` `/kits` GUI |
| `yapdata.kit.starter` | true | Starter kit |
| `yapdata.kit.adventurer` | false | Adventurer kit (quest / store unlock) |
| `yapdata.kit.vip` | false | VIP kit (VIP rank also has `yapdata.kit.*`) |
| `yapdata.kit.*` | op | All kits |
| `yapdata.kit.give` | op | `/kit give` `/kit grant` (console / Tebex) |
| `yapdata.mail` | true | `/mail` |
| `yapdata.shop` | true | `/shop` + chest shop buy |
| `yapdata.jobs` | true | `/jobs` GUI |
| `yapdata.ah` | true | `/ah` |
| `yapdata.claim` | true | `/claim` |
| `yapdata.claims.admin` | op | Bypass claims |
| `yapdata.admin` | op | `/yapdata`, override shops/AH/claims |

Auth (`/register` `/login` …) stays ungated so offline login always works.

## YaPSkills (`yap-skills.jar`, gameplay opt-in)

| Node | Default | Grants |
|------|---------|--------|
| `yapskills.use` | true | `/skills`, gain skill XP |
| `yapskills.others` | op | `/skills <player>` |
| `yapskills.admin` | op | `/skill addxp`, `/skill set`, `/yskills reload` |

Placeholders (PlaceholderAPI): `%yapskill_<skill>_level%`, `%yapskill_<skill>_xp%`, `%yapskill_total_level%`, `%yapskill_combat_level%`.

Public leaderboard: `/skill top <skill> [page]` (no extra permission).

Bedrock MMO UI (`yap-mmo-bedrock.jar`, gameplay opt-in):

| Node | Default | Grants |
|------|---------|--------|
| `yapmmo.bedrock.use` | true | `/mmoui`, Bedrock `/skills` form redirect |

## YaPFactions (`yap-factions.jar`)

| Node | Default | Grants |
|------|---------|--------|
| `yapfactions.use` | true | `/f` commands |
| `yapfactions.create` | true | `/f create` |
| `yapfactions.officer` | true | Officer actions (invite, claim, bank withdraw) |
| `yapfactions.leader` | true | Leader actions (disband, transfer, join mode) |
| `yapfactions.admin` | op | `/yapfactions` admin commands |

Dashboard: `GET /api/factions` (read-only snapshot).

## YaPGuilds (`yap-guilds.jar`, gameplay opt-in)

| Node | Default | Grants |
|------|---------|--------|
| `yapguilds.use` | true | `/g` commands |
| `yapguilds.create` | true | `/g create` |
| `yapguilds.admin` | op | `/yapguilds` admin commands |

Dashboard: `GET /api/guilds` (read-only snapshot).

## YaPCombat (`yap-combat.jar`, gameplay opt-in)

| Node | Default | Grants |
|------|---------|--------|
| `yapcombat.use` | true | `/combat stats` |
| `yapcombat.cast` | true | `/cast <spell>` |
| `yapcombat.prayer` | true | `/prayer on/off/list` |
| `yapcombat.admin` | op | `/combat reload`, `/yapcombat admin sethp` |

See [MMO_RS_SKILLS.md](../mmo/MMO_RS_SKILLS.md) for spell book, prayer drain, and ranged accuracy.

## YaPCrafting (`yap-crafting.jar`, gameplay opt-in)

| Node | Default | Grants |
|------|---------|--------|
| `yapcraft.use` | true | Station crafting, `/recipe list` |
| `yapcraft.sell` | true | `/sell` when economy enabled |
| `yapcraft.admin` | op | `/ycraft reload` |

## YaPGames (`yap-games.jar`, gameplay opt-in)

| Node | Default | Grants |
|------|---------|--------|
| `yapgames.use` | true | `/queue`, `/duel`, `/game` |
| `yapgames.admin` | op | `/ygames reload`, `list`, `forcestart`, `info`, `snapshot json` |

Requires `yap-combat.jar` for custom PvP in arenas (global `pvp: false` is overridden in active matches).

## YaPMmoContent (`yap-mmo-content.jar`, gameplay opt-in)

| Node | Default | Grants |
|------|---------|--------|
| `yapmmo.hiscores` | true | `/hiscores <skill>` |
| `yapmmo.admin` | op | `/yapmmo reload`, `/yapmmo snapshot`, quest reward hooks |

Content packs: `plugins/yap-mmo-content/quests/starter_chain.yml`

## YaPAbilities (`yap-abilities.jar`, gameplay opt-in)

| Node | Default | Grants |
|------|---------|--------|
| `yapabilities.use` | true | `/ability list`, `/ability cast`, shorthand `/ability <id>` |
| `yapabilities.admin` | op | `/yapabilities reload` |

233 combat abilities ship in YAML packs; `/cast` delegates when yap-abilities is loaded.

## See also

- [COMMANDS.md](COMMANDS.md) · [WEB_DASHBOARD.md](WEB_DASHBOARD.md) · [PLUGIN_COMPAT.md](../plugins/PLUGIN_COMPAT.md)
