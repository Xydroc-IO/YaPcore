# Commands

Reference for **YaPcore console**, **web dashboard console**, and **in-game** commands.

| Surface | How |
|---------|-----|
| YaP Control Panel console | Type commands in the GUI |
| Headless | `./start.sh --fg` stdin |
| Web dashboard | http://127.0.0.1:8080/ → Console tab |
| In-game | Folia/Paper commands when OP'd |

Leading `/` is optional in the YaP console.

---

## YaPcore builtins (console)

| Command | Description |
|---------|-------------|
| `help` | Command list |
| `status` | Server status report |
| `start` / `stop` | Lifecycle (GUI context) |
| `plugins` | List installed plugins |
| `packs` / `setpack` / `clearpack` | Resource pack management |
| `ranks status` | YaPPerms rank pack status |
| `ranks apply` | Apply starter rank pack (`yapperm applypack`) |
| `ranks apply force` | Re-apply after clearing marker |
| `ranks reset-marker` | Clear `config/yap-ranks-applied` |
| `crashdump` | Write diagnostic report |
| `dashboard` | Web admin dashboard login link + token |

See [PERMISSIONS.md](PERMISSIONS.md) for rank assignment.

---

## First-party plugin commands

### YaPPerms (`yap-perms.jar`)

| Command | Permission | Description |
|---------|------------|-------------|
| `/yapperm user <player> info` | `yapperm.user` | View effective permissions |
| `/yapperm user <player> parent set\|add\|remove <group>` | `yapperm.admin` | Group membership |
| `/yapperm user <player> permission set <node> true\|false` | `yapperm.admin` | Direct node grant |
| `/yapperm user <player> meta set <prefix> [suffix]` | `yapperm.admin` | Chat meta |
| `/yapperm group create\|delete\|list\|info\|setprefix\|setnamecolor\|setchatcolor\|…` | `yapperm.admin` | Group CRUD + chat colors |
| `/yapperm track list\|info` | `yapperm.admin` | Promotion tracks |
| `/yapperm applypack` | `yapperm.admin` | Apply starter rank pack |
| `/yapperm dump` | `yapperm.admin` | Write web-editor snapshot |
| `/yapperm editor-apply` | `yapperm.admin` | Apply dashboard rank editor batch |
| `/yapperm reload` | `yapperm.admin` | Reload config |
| `/promote <player> [track]` | `yapperm.promote` | Move up track `yap` |
| `/demote <player> [track]` | `yapperm.demote` | Move down track |

Aliases: `/yperms`, `/perms`

### YaPAbilities (`yap-abilities.jar`)

| Command | Permission | Description |
|---------|------------|-------------|
| `/abilities` `/ability` `/spell` | `yapabilities.use` | Open the ability book |
| `/spell <ability> [1-6]` | `yapabilities.bar` | Put a spell on combat keys 4–9 |
| `/ability add <ability> [1-6]` | `yapabilities.bar` | Same as `/spell` |
| `/ability bind <1-6> <ability>` | `yapabilities.bar` | Place on a specific slot |
| `/ability mode combat` | `yapabilities.use` | Show the combat hotbar (keys 4–9 cast) |
| `/ability bar` | `yapabilities.use` | List current bindings |
| `/ability info <ability>` | `yapabilities.use` | What the spell does, costs, and requirements |
| `/ability tome` | `yapabilities.use` | Get an Ability Tome |

Click a spell in the book to add it. Press **4–9** to cast. Middle-click (or `/ability mode`) switches back to the build hotbar.

### YaPChat (`yap-chat.jar`)

| Command | Permission | Description |
|---------|------------|-------------|
| `/msg` `/tell` `/w` `<player> <msg>` | `yapchat.msg` | Private message |
| `/reply` `/r` `<msg>` | `yapchat.msg` | Reply to last PM |
| `/staffchat` `/sc` `[msg]` | `yapchat.staff` | Toggle staff channel, or one-shot message |
| `/adminchat` `/ac` `[msg]` | `yapchat.admin` | Toggle admin channel, or one-shot message |
| `/channel` `/ch` `<global\|local\|trade\|staff\|admin>` | `yapchat.use` | Switch sticky chat channel (`!<msg>` = one-shot local) |
| `/ignore` `/unignore` `<player>` | `yapchat.use` | Ignore list |
| `/ignorelist` | `yapchat.use` | Show ignored players |
| `/clearchat` `/cc` | `yapchat.admin` | Clear chat |
| `/yapchat reload` | `yapchat.admin` | Reload config |

Local chat: prefix message with `!` when in global channel.

Chat line colors come from each YaPPerms rank (`name-color` / `chat-color`). Format tokens: `{prefix}{namecolor}{player}{suffix}&7: {chatcolor}{message}`.

### YaPModeration (`yap-moderation.jar`)

| Command | Permission | Description |
|---------|------------|-------------|
| `/ban` `<player> [reason]` | `yapmod.ban` | Permanent ban |
| `/tempban` `/tban` `<player> <duration> [reason]` | `yapmod.ban` | Timed ban |
| `/unban` `<player>` | `yapmod.ban` | Remove ban |
| `/ipban` `<player\|ip> [reason]` | `yapmod.ipban` | IP ban |
| `/unbanip` `<ip>` | `yapmod.ipban` | Remove IP ban |
| `/mute` `<player> [reason]` | `yapmod.mute` | Permanent mute |
| `/tempmute` `/tmute` `<player> <duration> [reason]` | `yapmod.mute` | Timed mute |
| `/unmute` `<player>` | `yapmod.mute` | Remove mute |
| `/warn` `<player> [reason]` | `yapmod.warn` | Warn |
| `/kick` `<player> [reason]` | `yapmod.kick` | Kick |
| `/modhistory` `/history` `<player> [limit]` | `yapmod.history` | Punishment log |
| `/modcheck` `/check` `/alts` `<player>` | `yapmod.history` | Status + linked alts |
| `/banlist` `[limit]` | `yapmod.history` | Active bans |
| `/yapmod reload` | `yapmod.admin` | Reload config |
| `/yapmod seen [json\|snapshot]` | `yapmod.admin` | List join history, dump JSON, or write dashboard snapshot |

Duration examples: `30m`, `2h`, `7d`, `1w`.

### YaPEssentials (`yap-essentials.jar`)

| Command | Permission | Description |
|---------|------------|-------------|
| `/spawn` | `yapessentials.spawn` | Teleport to spawn |
| `/setspawn` | `yapessentials.setspawn` | Set spawn |
| `/back` | `yapessentials.back` | Previous location |
| `/tpa` `/tpahere` `/tpaccept` `/tpdeny` | `yapessentials.tpa` | Teleport requests |
| `/tp` `/tphere` `/s` | `yapessentials.teleport` | Staff teleport |
| `/gm` `<0\|1\|2\|3\|s\|c\|a\|sp>` `[player]` | `yapessentials.gamemode` | Game mode (Essentials-style) |
| `/gms` `/gmc` `/gma` `/gmsp` `[player]` | `yapessentials.gamemode` | Survival / creative / adventure / spectator |
| `/i` `/item` `<item>` `[amount]` `[player]` | `yapessentials.item` | Give items (e.g. `/i diamond 64`) |
| `/fly` `[player]` | `yapessentials.fly` | Toggle flight |
| `/god` `[player]` | `yapessentials.god` | God mode |
| `/speed` `<0-10> [fly\|walk]` | `yapessentials.speed` | Move speed |
| `/heal` `/feed` `[player]` | `yapessentials.heal` | Restore health/hunger |
| `/repair` `[hand\|all]` | `yapessentials.repair` | Repair items |
| `/clear` `[player]` | `yapessentials.clear` | Clear inventory |
| `/vanish` `/v` `[player]` | `yapessentials.vanish` | Vanish |
| `/invsee` `<player>` | `yapessentials.invsee` | View inventory |
| `/echest` `/ec` `<player>` | `yapessentials.echest` | View ender chest |
| `/nick` `<name\|off> [player]` | `yapessentials.nick` | Display name |
| `/afk` | `yapessentials.afk` | AFK status |
| `/list` `/online` `/who` | `yapessentials.list` | Online players |
| `/ptime` `/pweather` | `yapessentials.ptime` | Client time/weather |
| `/broadcast` `/bc` `<msg>` | `yapessentials.broadcast` | Server broadcast |
| `/rules` `/motd` | `yapessentials.rules` | Info messages |
| `/hat` | `yapessentials.hat` | Wear held item |
| `/socialspy` `/ss` | `yapessentials.staff.socialspy` | Spy on PMs |
| `/freeze` `<player>` | `yapessentials.staff.freeze` | Freeze player |
| `/check` `<player>` | `yapessentials.staff.check` | Inspect player |
| `/yapess reload` | `yapessentials.admin` | Reload config |

Feature toggles: `plugins/YaPEssentials/config.yml`.

### YaPAdmin (`yap-admin.jar`)

| Command | Permission | Description |
|---------|------------|-------------|
| `/yapadmin` `/staff` `/adminmenu` `/am` | `yapadmin.menu` | Open staff super menu |
| `/yapadmin reload` | `yapadmin.server` | Reload menu config |

In-game kitchen-sink hub: players, give (presets + kits + materials), moderation, self tools, economy, deep-links. See [ADMIN_MENU.md](ADMIN_MENU.md).

### Other shipped plugins

| Plugin | Command | Doc |
|--------|---------|-----|
| Pregen | `/yappregen …` | [PREGEN.md](../plugins/PREGEN.md) |
| Stacker | `/yapstacker …` | [STACKER.md](../plugins/STACKER.md) |
| Vehicles | `/yapvehicle …` | [VEHICLES.md](../plugins/VEHICLES.md) |
| Gameplay knobs | `/yapknobs …` | [TUNE.md](TUNE.md) |
| Plugin compat | `/yapcompat …` | [PLUGIN_BACKCOMPAT.md](../plugins/PLUGIN_BACKCOMPAT.md) |
| PlaceholderAPI | `/papi …` | [PLACEHOLDERAPI.md](../plugins/PLACEHOLDERAPI.md) |
| Player data | `/yapdata …` · `/bal` `/pay` `/eco` · `/bag` `/backpack` · `/kit` `/kits` `/createkit` `/showkit` `/kitreset` | [PLAYERDATA.md](../data/PLAYERDATA.md) |
| Store / Tebex | Hub console: `yapperm …` · `kit grant …` | [TEBEX.md](TEBEX.md) |
| Resource packs | `/yappacks …` | [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) |

### YaPPlayerData kits (console / store)

| Command | Description |
|---------|-------------|
| `/bag` `[page]` | Open extra bag (45 slots/page). Aliases `/backpack` `/bp` |
| `/bag see <player> [page]` | Staff — open another player's bag |
| `/bal` `[player]` | Show balance |
| `/pay` `<player> <amount>` | Pay a player |
| `/eco give\|take\|set <player> <amount>` | Staff money (also `/eco reset <player>`) |
| `/kit [name]` | Claim kit (player) |
| `/kits` | Kits GUI (live cooldown / cost / uses) |
| `/createkit <name> [delay]` | Save inventory + armor + offhand to `kits.yml` |
| `/delkit <name>` | Remove a kit |
| `/showkit <name>` | Preview GUI (shift-click in `/kits`) |
| `/kitreset <player> [kit\|all]` | Clear cooldown and uses |
| `kit give <player> <kit> [-force]` | Instant give (online on this backend) |
| `kit grant <player> <kit>` | Queue delivery (shared DB → any backend) |
| `kit list` | Kits loaded from `kits.yml` on this server |

Copy the same `plugins/YaPPlayerData/kits.yml` to Hub + survival. See [TEBEX.md](TEBEX.md).

Full permission node map: [PERMISSIONS.md](PERMISSIONS.md).

---

## Vanilla / Folia commands (in-game)

Under Folia game authority, players use **real vanilla / plugin commands** when OP'd —
`/give`, `/tp`, `/gamemode`, `/difficulty`, etc.

**Non-OP players** only see plugin commands they have permission for — normal Bukkit behavior.

### Grant OP

| Method | How |
|--------|-----|
| Config seed | `ops=YourName` in `config/server.properties` |
| Console | `op YourName` |
| Auto (dev only) | `auto-op=true` (default **false**) |

Reconnect after OP to refresh tab-complete.

### Console examples (forwarded to Folia)

```
give @p diamond 64
tp Steve 0 80 0
gamemode creative @a
yapperm user Steve parent set vip
kit grant Steve vip
promote Steve
yappregen status
```

Store packages (Tebex on Hub): [TEBEX.md](TEBEX.md) · [examples/tebex/](../../examples/tebex/).
---

## Implementation

| Path | Role |
|------|------|
| `YaPcoreServer.executeCommand` | YaP builtins → else Folia/Paper dispatch |
| Folia kernel | `foliaKernel.dispatchConsoleCommand` |
| Web dashboard | `/api/console` → same execute path |

See [QUICK_START.md](../start/QUICK_START.md) · [WEB_DASHBOARD.md](WEB_DASHBOARD.md).
