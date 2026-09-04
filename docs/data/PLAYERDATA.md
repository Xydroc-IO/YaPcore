# YaP PlayerData — cross-server sync, offline auth, claims, taxes, NPC traders, GUIs

First-party **`yap-playerdata.jar`** (`YaPPlayerData`) — shared MariaDB across
**YaP Link** (or Velocity) backends running **YaP-Folia**.

## Database setup (required)

See **[MARIADB.md](MARIADB.md)** and **[YAPDB.md](YAPDB.md)** — Docker MariaDB + shared `yap-db.jar` pool:

```bash
# Linux (preferred one-shot)
./scripts/db/ensure-db.sh --server-id lobby

# Windows PowerShell
.\scripts\windows\Start-MariaDB.ps1
.\scripts\windows\Configure-Db.ps1 -ServerId lobby
```

YaPPlayerData prefers the shared YaPDB pool (`use-shared-yapdb: true`). Multi-backend: same JDBC, unique `server-id`.

## Session lock (double-login)

Always on. Prevents the same UUID being online on two backends at once:

- Acquire `lock_server` / `lock_until` on join; refresh on autosave; release on quit
- Contested lock → kick with holder server name
- Async pre-login rejects early when another server holds a live lock
- Stuck lock: `/yapdata unlock <player>`
- **Save path:** snapshot profile on main (or quit), `repository.saveProfile` on Bukkit async — I/O off main, apply stays sync

`lock-ttl-seconds` (default 120) auto-expires crashed holds.

## Offline password auth (`/login`)

AuthMe-class for cracked / offline-mode servers (BCrypt hashes in `auth_accounts`):

| Command | Who | What |
|---------|-----|------|
| `/register <pass> <pass>` | player | create account |
| `/login <pass>` | player | authenticate |
| `/changepassword <old> <new>` | logged-in | change hash |
| `/logout` | logged-in | clear session (must login again) |
| `/unregister <player>` | admin | delete auth row |

Until logged in: frozen (no move/interact), no chat, no commands except auth. Inventory apply waits until success. Login timeout / max attempts kick.

### Config (`auth:`)

```yaml
auth:
  enabled: true
  force: false           # true = require /login even on online-mode
  trust-velocity: false  # true = skip auth on Velocity modern-forwarding backends
  min-password-length: 4
  timeout-seconds: 60
  max-attempts: 5
```

- **Cracked offline server:** `enabled: true`, `force: false`, `trust-velocity: false`
- **Online-mode / Mojang:** auth auto-skips unless `force: true`
- **Velocity + modern forwarding:** set `trust-velocity: true` to skip passwords, or leave false / `force: true` if you still want `/login`

Session lock and password auth are independent: lock always runs; auth is optional via config.

## Features (v0.6) — modular

Always on: session lock · inv/XP/vitals sync · `/menu` hub · `/yapdata` admin.

| Area | Default | Config |
|------|---------|--------|
| Auth `/login` | on | `auth.enabled` |
| Economy `/bal` `/pay` `/eco` + native `PlayerDataService` | on | `economy.enabled` |
| Homes / warps / kits / mail | on | `features.homes` … |
| Claims | on | `features.claims` (+ `claims.*`) |
| Shops / auctions (AH) | **on** | `features.shops` / `auctions` |
| Jobs | **off** (keep off with YaPSkills) | `features.jobs` |
| NPC traders | **off** | `features.traders` |
| Backpack `/bag` | **on** | `features.backpack` |

Money features require `economy.enabled: true`. When economy is off, shops/jobs/AH/traders and claim tax stay off even if their feature flags are true.

**Native economy (no Vault required):** first-party plugins use Bukkit `ServicesManager` → `PlayerDataService`
(`balance` / `deposit` / `withdraw` / `setBalance`). Deposits fire `PlayerBalanceChangeEvent` for quest
`ECONOMY_EARN` hooks. Lifetime **playtime** is tracked as `players.play_minutes` (join/quit) and exposed via
`PlayerDataService.playMinutes(UUID)` for quest `PLAYTIME` objectives.
(`balance` / `deposit` / `withdraw` / `setBalance`). `/sell`, minigame rewards, Tab `{balance}`, and
`/yapmmo givemoney` all go through that API. Vault remains an **optional** bridge (`YaPEconomy`) only if
you drop `Vault.jar` for third-party plugins.

```yaml
# Default product features (economy on):
economy:
  enabled: true
features:
  homes: true
  warps: true
  kits: true
  mail: true
  shops: true
  jobs: false
  auctions: true
  claims: true
  traders: false
  backpack: true
backpack:
  default-pages: 3
  max-pages: 9
```

**Freeze lifted:** Essentials-class QoL lives in **`yap-essentials.jar`**. Playerdata stays the data/sync layer.

| Area | What |
|------|------|
| Auth | `/register` `/login` · freeze until auth · BCrypt |
| Session lock | Cross-server dual-login kick · `/yapdata unlock` |
| Sync | Inv / XP / vitals · economy when enabled |
| Fancy GUIs | `/menu` hub (icons match enabled modules) |
| Claims | Shovel · subdivides · taxes (tax needs economy) |
| NPC traders | `/trader` (opt-in; needs economy) |
| Homes/warps/kits/mail | Cross-server |
| Shops / AH | Chest shops (`/shop`) + auction house (`/ah`) |
| Backpack | `/bag` paged extra storage (45 slots/page). Vanilla E inventory stays 36. Optional Fabric `yap-bag` adds a keybind and inventory tabs. |

### Backpack (extra bag space)

The vanilla **E** inventory cannot grow from the server. `/bag` (aliases `/backpack` `/bp`) opens a double-chest GUI with **pages**. Bottom row is Prev / page tabs / Next. Contents live in MariaDB `player_backpack_pages` under the same `inventory-profile` as inv/enderchest, so they follow the player across backends.

| Who | Pages |
|-----|-------|
| Everyone with `yapdata.bag` | `backpack.default-pages` (3) |
| VIP `yapdata.bag.pages.5` | 5 |
| Staff `yapdata.bag.pages.7` | 7 |
| Admin `yapdata.bag.pages.*` | `backpack.max-pages` (9) |
| Staff `yapdata.bag.see` | `/bag see <player> [page]` |

Vanilla Java and Bedrock use the command / hub icon. The optional **yap-bag** Fabric client (Minecraft 26.2) binds **B** and adds a Bag button on the inventory screen plus page tabs on the bag chest. Same items — the mod is not required.

Existing ranks: `ranks apply force` (or dashboard) so starter-grants pick up the new bag nodes.

### Kits (EssentialsX-class)

Kits live in **YaPPlayerData**. `/createkit` captures inventory, armor, and offhand with full item data (enchants, names, components).

| What | Where |
|------|--------|
| Definitions | `plugins/YaPPlayerData/kits.yml` — **same file on Hub + every survival backend** |
| Cooldowns / uses | MariaDB `kit_cooldowns` (network-wide) |
| Store grants | MariaDB `kit_grants` via `kit grant <player> <kit>` |
| Access | `yapdata.kit.<id>` / VIP `yapdata.kit.*` (YaPPerms) |
| First join | `first-join: true` on a kit (starter ships on) |
| Cost | `cost:` + economy balance |
| Signs | `[Kit]` line 1, kit id line 2 |

Player: `/kit` `/kits` `/showkit` · Admin: `/createkit` `/delkit` `/kitreset` · Console/store: `kit give` · `kit grant`.  
Dashboard: **Gameplay → Kits** builds the same `kits.yml` (items, armor slot, cooldown, cost, first-join, commands).  
Tebex on Hub: [TEBEX.md](../ops/TEBEX.md) · [examples/tebex/](../../examples/tebex/).

Plugin jar: `gradle :playerdata-plugin:installIntoPlugins` (also in `assembleRelease`).
