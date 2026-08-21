# YaP PlayerData — cross-server sync + essentials

First-party Paper plugin **`yap-playerdata.jar`** (`YaPPlayerData`) — own YaPcore code
(not AuthMe / Essentials / ChestShop wrappers). Shared **MariaDB/MySQL** across Velocity
backends. **No offline password auth.**

## Features

| Area | What |
|------|------|
| Sync | Inventory, ender chest, XP, vitals + economy balance |
| Profiles | `inventory-profile: global` (shared) or `server` (per-backend / minigame wipe) |
| Homes / warps | `/sethome` `/home` `/homes` · `/setwarp` `/warp` `/warps` (cross-server aware) |
| Kits | Config kits + cooldowns · `/kit` `/kits` |
| Mail | `/mail read\|clear\|send` |
| Shops | Chest shops · `/shop create <price>` · left-click chest to buy |
| Jobs | Config jobs · `/jobs join` · earn on block break |
| Auctions | `/ah list\|sell\|buy\|cancel` |
| Vault | Soft-depend Economy provider |

## Install

Shipped on `gradle shadowJar` / `assembleRelease` /
`gradle :playerdata-plugin:installIntoPlugins`.

1. Shared MariaDB (Docker example):

```bash
docker run -d --name yap-mariadb \
  -e MARIADB_ROOT_PASSWORD=root \
  -e MARIADB_DATABASE=yap_playerdata \
  -e MARIADB_USER=yap \
  -e MARIADB_PASSWORD=change-me \
  -p 3306:3306 \
  mariadb:11
```

2. On **each** backend, edit `plugins/YaPPlayerData/config.yml`:

```yaml
server-id: lobby                 # unique per backend
inventory-profile: global        # or: server  (minigame wipe)
jdbc:
  url: jdbc:mysql://127.0.0.1:3306/yap_playerdata?useSSL=false&allowPublicKeyRetrieval=true
  user: yap
  password: change-me
```

3. Restart. Schema migrates automatically (v1 rows copy into `player_profiles`).

### Per-server inventories (minigames)

Set `inventory-profile: server` on the minigame backend so its `server-id` is the
profile key. Keep `global` on survival/lobby so inv/money follow those servers.
Economy, mail, homes, and warps stay global either way.

## Velocity check

Two backends, same JDBC URL, distinct `server-id` → join A, get items/money, `/server B`,
confirm same data (when both use `global`). Lock kicks double-join while A holds session.

Homes/warps store `server_id`; teleporting from the wrong backend tells the player to switch.

## Commands (summary)

`/bal` `/pay` · `/yapdata` · homes/warps · `/kit` · `/mail` · `/shop` · `/jobs` · `/ah`

## Notes

- DB is source of truth for synced fields; fail-closed on DB/lock errors.
- Shops/jobs/auctions are first-party essentials — not full ChestShop/Jobs-Reborn/AH clones.
- Still not included: offline `/login`, claims, advanced GUIs.
