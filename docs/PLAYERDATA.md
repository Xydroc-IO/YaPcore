# YaP PlayerData — cross-server sync, claims, fancy GUIs

First-party Paper plugin **`yap-playerdata.jar`** (`YaPPlayerData`) — **own YaPcore code**,
not Essentials / GriefPrevention / ChestShop wrappers. Shared **MariaDB/MySQL** across
Velocity backends. **No offline password auth.**

YaPcore’s Paper path gives us the full Bukkit API; this plugin uses inventory GUIs,
particles, and a shared SQL schema so network-wide data stays consistent.

## Features

| Area | What |
|------|------|
| Sync | Inv / ender / XP / vitals / economy · `inventory-profile: global` or `server` |
| **Fancy GUIs** | `/menu` hub · homes · warps · kits · jobs · AH · mail · claims |
| **Claims** | Golden shovel corners · stick inspect · trust · particle borders · claim blocks |
| Homes / warps | Cross-server aware teleports |
| Kits / mail | Config kits + cooldowns · mail send/read |
| Shops | Chest shops · left-click buy |
| Jobs | Config jobs · earn on break |
| Auctions | GUI browse + `/ah sell\|buy` |
| Vault | Soft-depend Economy |

## Claims (quick start)

1. `/claim tool` — golden shovel + stick  
2. Right-click two opposite corners with the shovel  
3. Stick right-click inspects + visualizes borders  
4. `/claim trust <player> [access|build|manage]` while standing in your claim  
5. `/claim` opens the claims GUI  

Config: `claims.*` in `plugins/YaPPlayerData/config.yml`.

## Install

```bash
gradle :playerdata-plugin:installIntoPlugins
# or: gradle shadowJar / assembleRelease
```

Point every backend at the same MariaDB; unique `server-id` each. Schema auto-migrates
(including `claims`, `claim_trust`, `claim_balances`).

Docker one-liner: see earlier docs / `MARIADB_DATABASE=yap_playerdata`.

## Commands

`/menu` · `/bal` `/pay` · `/yapdata` · homes/warps · `/kit` · `/mail` · `/shop` · `/jobs` · `/ah` · `/claim`

## Honest scope note

This is a full first-party essentials+claims stack for YaPcore networks — not a line-for-line
clone of every GriefPrevention / Jobs-Reborn edge case. Still omitted: offline `/login`,
subdivision subdivides, tax systems, NPC traders. Those can extend this codebase next.
