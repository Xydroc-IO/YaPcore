# YaPAdmin — in-game staff super menu

Chest GUI hub for on-server staff. Complements the [web dashboard](WEB_DASHBOARD.md) and desktop Control Panel; it does **not** replace them.

## Install

Built as `yap-admin.jar` (CORE + NETWORK product default).

```bash
gradle :admin-plugin:installIntoPlugins
```

Soft-depends on YaPEssentials, YaPModeration, YaPPerms, YaPWorld, YaPStacker, YaPPlayerData, YaPSkills — tiles hide when a plugin is missing.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/yapadmin` `/staff` `/adminmenu` `/am` | `yapadmin.menu` | Open the hub |
| `/yapadmin reload` | `yapadmin.server` | Reload `plugins/YaPAdmin/config.yml` |

## Hub sections

- **Players** — online picker → TP to/here/spawn, freeze, invsee/echest, heal/feed/clear, promote/demote, kick/warn/mute 1h/tempban 1d, jump to Give
- **Self tools** — fly, god, vanish, heal, feed, socialspy, night vision
- **Give** — curated presets, kits (`/kit give`), paginated material browser (categories + amount 1/16/64)
- **Moderation** — same player picker (actions gated by `yapmod.*`)
- **Server** — broadcast presets, status (online/worlds/TPS), deep-link to Ranks GUI
- **Economy** — money grants via playerdata `/eco` (when economy present)
- **Deep links** — `/yapperm gui`, `/yapworld gui`, `/yapstacker gui`, `/menu`
- **Skills** — `/skills` when YaPSkills is installed

## Permissions

| Node | Default | Notes |
|------|---------|-------|
| `yapadmin.menu` | op | Open hub |
| `yapadmin.give` | op | Presets / materials / kits section |
| `yapadmin.server` | op | Broadcast + reload |
| `yapadmin.economy` | op | Money grants |

Per-action nodes from other plugins still apply (`yapessentials.teleport`, `yapmod.kick`, `yapdata.kit.give`, …). Grant `yapadmin.menu` (and give/server as needed) on `mod` / `admin` ranks.

## Config

`plugins/YaPAdmin/config.yml` — kit ids, money amounts, broadcast presets, curated item presets.

## Folia

Teleports and inventory mutations use `YapSched.entity`. Moderation DB calls go through `ModerationService` then hop back to the entity thread for feedback.
