# Permissions & ranks

YaP first-party plugins gate commands with **Bukkit permission nodes**.
**Native ranks** ship in **`yap-perms.jar`** (`YaPPerms`) — LuckPerms-class groups,
inheritance, tracks, temp/world nodes, Vault Permission, prefix/suffix, per-rank chat colors, MariaDB,
PlaceholderAPI `%yapperms_*%`. `/lp` is an alias.

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

Starter pack (`default` → `vip` → `staff` → `admin` → `owner` + track `yap`) applies automatically
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
| Web dashboard | **Access & ranks** (permission editor) · **Rank pack** → Apply pack |
| Config | `plugins/YaPPerms/config.yml` |

OP still receives every node with `default: op` without YaPPerms attachments.

## YaPPerms admin commands

| Command | Permission |
|---------|------------|
| `/yapperm user <player> info` | `yapperm.user` / admin |
| `/yapperm user <player> parent set\|add\|remove <group>` | `yapperm.admin` |
| `/yapperm user <player> permission set <node> true\|false [1d] [world=x]` | `yapperm.admin` |
| `/yapperm user <player> permission unset <node> [world=x]` | `yapperm.admin` |
| `/yapperm user <player> meta set <prefix> [suffix]` | `yapperm.admin` |
| `/yapperm group create\|delete\|list\|info\|setprefix\|setsuffix\|setnamecolor\|setchatcolor\|parent …` | `yapperm.admin` |
| `/yapperm track list\|info\|create\|append\|remove\|delete` | `yapperm.admin` |
| `/yapperm check <player> <node> [world]` | `yapperm.user` / admin |
| `/yapperm export [file.yml]` · `import <file.yml>` | `yapperm.admin` |
| `/yapperm dump` | `yapperm.admin` — write `editor-snapshot.yml` for the web editor |
| `/yapperm editor-apply` | `yapperm.admin` — apply `editor-apply.yml` / `editor-nodes` from the dashboard |
| `/yapperm applypack` | `yapperm.admin` |
| `/promote <player> [track]` | `yapperm.promote` |
| `/demote <player> [track]` | `yapperm.demote` |

## Rank ladder (starter pack)

| Rank | Weight | Prefix | Name / chat | Inherits | Role |
|------|--------|--------|-------------|----------|------|
| `default` | 0 | gray name | `&7` / `&f` | — | All players |
| `vip` | 10 | `[VIP]` | `&a` / `&f` | default | Donors / trusted |
| `staff` | 50 | `[Staff]` | `&b` / `&f` | vip | Helpers / junior–senior staff |
| `admin` | 100 | `[Admin]` | `&c` / `&f` | staff | Server admins — includes `yap.bypass` + MMO admin nodes |
| `owner` | 200 | `[Owner]` | `&6` / `&f` | admin | Full access (`*` + `yap.bypass`) — network owners |

**Bypass rules:** OP, `yap.bypass`, and creative/spectator skip land protection and MMO
restrictions. Creative always gets vanilla-like build/combat (no skill gates, stamina,
custom combat, or ability-bar locks). Admins get `yap.bypass` / `yap.bypass.mmo` in the
starter pack — run `/yapperm applypack` after upgrading.

Legacy group **`mod`** still exists (same general staff tools, not on the promote track). Prefer **`staff`**.

Track name: **`yap`**. Promote with `/promote Steve` or `/yapperm user Steve parent set staff`.

```bash
/yapperm user Steve parent set staff
/yapperm user Steve parent set admin
/yapperm user Steve parent set owner
```

### Chat colors per rank

Each group has a **prefix** (tag), **name-color** (player name), and **chat-color** (message).
Chat format: `{prefix}{namecolor}{player}{suffix}&7: {chatcolor}{message}`.

```bash
/yapperm group setnamecolor vip &a
/yapperm group setchatcolor vip &f
/yapperm group info vip
```

Dashboard **Access & ranks** has the same fields. Codes are `&a` green, `&b` aqua, `&c` red, `&6` gold, `&f` white. YAML keys are `name-color` / `chat-color` under `groups.<rank>`. Empty database colors are filled from YAML on plugin start; `/yapperm applypack` also writes them.

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
| `yapessentials.gamemode` | op | `/gm` `/gms` `/gmc` `/gma` `/gmsp` |
| `yapessentials.item` | op | `/i` `/item` |
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

Grant `yapadmin.menu` (+ give/server) on `staff` / `admin` / `owner` ranks. Individual actions still need the underlying plugin nodes (`yapessentials.*`, `yapmod.*`, …). See [ADMIN_MENU.md](ADMIN_MENU.md).

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
| `yapdata.bag` | true | `/bag` extra storage |
| `yapdata.bag.pages.5` | false | At least 5 bag pages (VIP starter grant) |
| `yapdata.bag.pages.7` | false | At least 7 bag pages (staff starter grant) |
| `yapdata.bag.pages.*` | op | All configured bag pages |
| `yapdata.bag.see` | op | `/bag see <player>` |
| `yapdata.balance` | true | `/bal` (self) |
| `yapdata.balance.others` | op | `/bal <other>` |
| `yapdata.pay` | true | `/pay` |
| `yapdata.eco` | op | `/eco give\|take\|set\|reset` |
| `yapdata.home` | true | `/home` `/sethome` `/delhome` `/homes` |
| `yapdata.warp` | true | `/warp` `/warps` |
| `yapdata.warp.admin` | op | `/setwarp` `/delwarp` |
| `yapdata.kit` | true | `/kit` `/kits` GUI |
| `yapdata.kit.starter` | true | Starter kit |
| `yapdata.kit.adventurer` | false | Adventurer kit (quest / store unlock) |
| `yapdata.kit.vip` | false | VIP kit (VIP rank also has `yapdata.kit.*`) |
| `yapdata.kit.*` | op | All kits |
| `yapdata.kit.give` | op | `/kit give` `/kit grant` (console / Tebex) |
| `yapdata.kit.create` | op | `/createkit` `/delkit` kit signs |
| `yapdata.kit.reset` | op | `/kitreset <player> [kit\|all]` |
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
| `yapabilities.use` | true | `/ability list`, `/ability cast`, `/ability bar`, `/ability book`, `/abilities`, hotbar cast keys 4–9 |
| `yapabilities.bar` | true | `/ability bind`, `/ability clear`, drag-bind in ability book |
| `yapabilities.admin` | op | `/yapabilities reload` |

233 combat abilities ship in YAML packs; `/cast` delegates when yap-abilities is loaded.

## Web rank editor

Dashboard **Access & ranks** edits what each rank may do (YaP commands, vanilla `/gamemode` / `/give`, Paper `/plugins`, deny vs inherit).

Create a rank with a **permission pack** (player / staff / admin) or **copy perms from another rank**. Add any node — including wildcards and nodes discovered from installed `plugin.yml` files — via the custom / bulk fields.

- Saves `starter-grants` (allows) and `editor-nodes` (allow + deny) in `plugins/YaPPerms/config.yml`
- Applies to MariaDB with `/yapperm editor-apply` (dashboard does this after Save)
- Live extras (custom nodes) refresh via `/yapperm dump` → `editor-snapshot.yml`
- Re-`applypack` reapplies starter grants **and** editor-nodes, so dashboard denies survive

## See also

- [COMMANDS.md](COMMANDS.md) · [WEB_DASHBOARD.md](WEB_DASHBOARD.md) · [PLUGIN_COMPAT.md](../plugins/PLUGIN_COMPAT.md)
