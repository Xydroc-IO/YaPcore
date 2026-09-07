# YaPAdmin — in-game staff super menu

Chest GUI hub for on-server staff, plus optional Fabric **yap-staff** client GUI.
Complements the [web dashboard](WEB_DASHBOARD.md) and desktop Control Panel.

## Install

Built as `yap-admin.jar` (CORE + NETWORK product default).

```bash
gradle :admin-plugin:installIntoPlugins
```

Soft-depends on YaPEssentials, YaPModeration, YaPPerms, YaPWorld, YaPStacker, YaPPlayerData, YaPSkills — tiles hide when a plugin is missing.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/yapadmin` `/staff` `/adminmenu` `/am` | `yapadmin.menu` | Open the hub chest GUI |
| `/yapadmin reload` | `yapadmin.server` | Reload `plugins/YaPAdmin/config.yml` |
| `/yapadmin give <mat> [amt] [player]` | `yapadmin.give` | Give items (client give browser) |
| `/yapadmin troll <type> <player>` | `yapadmin.troll` | Smite, launch, burn, rocket, squash, blind, confuse, slap, drop |
| `/yapadmin tp` / `tphere` / `tpspawn` | `yapessentials.teleport` | Teleports |
| `/yapadmin heal` / `feed` / `nv` / `clear` | menu | Self/target tools |
| `/yapadmin kick` / `warn` / `mute` / `tempban` | `yapmod.*` | Moderation shortcuts |
| `/yapadmin money <amt> [player]` | `yapadmin.economy` | Economy grant |
| `/yapadmin broadcast <msg>` | `yapadmin.server` | Broadcast |
| `/yapadmin chest` | `yapadmin.menu` | Open chest hub explicitly |

Also opens from:

- **`/menu` → Staff** (JE chest / Bedrock form) when the player has `yapadmin.menu` and YaPAdmin is loaded
- **Esc pause / keybind R → full Staff GUI** with the optional Fabric **yap-staff** client mod

## Hub sections (chest + client)

- **Players** — online picker → TP to/here/spawn, freeze, invsee/echest, heal/feed/clear, promote/demote, kick/warn/mute 1h/tempban 1d, trolls, check/history, jump to Give / money / ranks
- **Self tools** — fly, god, vanish, heal, feed, night vision, gamemodes, repair, speed
- **Give** — curated presets, kits (`/kit give`), paginated / searchable material browser (amount 1/16/64)
- **Trolls** — smite, launch, burn, rocket, squash, blind, confuse, slap, drop hand (`yapadmin.troll`)
- **Moderation** — same player picker (actions gated by `yapmod.*`)
- **Server** — broadcast presets, status, weather/disasters, reloads
- **Economy** — money grants via YaPPlayerData deposit (`/yapadmin money`; Folia entity-thread safe)
- **Ranks & perms** (client) — searchable YaPPerms editor (primary/parents, node allow/deny/unset, tracks); chest still deep-links `/yapperm gui`
- **Deep links** — `/yapperm gui`, `/yapworld gui`, `/yapstacker gui`, `/menu`, skills, dungeons, …

## Permissions

| Node | Default | Notes |
|------|---------|-------|
| `yapadmin.menu` | op | Open hub |
| `yapadmin.give` | op | Presets / materials / kits |
| `yapadmin.server` | op | Broadcast + reload |
| `yapadmin.economy` | op | Money grants |
| `yapadmin.troll` | op | Staff trolls |
| `yapadmin.plugins` | op | `/yapplugins` |

Per-action nodes from other plugins still apply (`yapessentials.teleport`, `yapmod.kick`, `yapdata.kit.give`, `yapperm.admin`, …). Grant `yapadmin.menu` (and give/server/troll as needed) on `mod` / `admin` ranks. Owner has `yapadmin.*`. **OP** also unlocks all `default: op` nodes even if YaPPerms primary is `default`.

## Config

`plugins/YaPAdmin/config.yml` — kit ids, money amounts, broadcast presets, curated item presets.

## Folia

Teleports and inventory mutations use `YapSched.entity`. Moderation DB calls go through `ModerationService` then hop back to the entity thread for feedback. **Economy** deposits via `PlayerDataService` on the target’s region thread (do not dispatch `/eco` on the global scheduler).

## Client mod

See [`client/yap-staff/README.md`](../../client/yap-staff/README.md) — native Screens for every hub section (scrollable/scaled layout, searchable player select that returns to the calling tool, ranks catalog). Jar **1.0.4+**.
