# YaP PlayerData — cross-server sync, offline auth, claims, taxes, NPC traders, GUIs

First-party **`yap-playerdata.jar`** (`YaPPlayerData`) — shared SQL across
**YaP Link** (or Velocity) backends running **YaP-Folia**.

## Database setup (required)

See **[YAPDB.md](YAPDB.md)** — shared `yap-db.jar` pool. Default is MariaDB; Postgres and SQLite are also supported:

```bash
# MariaDB (default, multi-backend OK)
./scripts/db/ensure-db.sh --server-id lobby

# PostgreSQL (multi-backend OK)
./scripts/db/ensure-postgres.sh --server-id lobby

# SQLite (single-node only)
./scripts/db/configure-db.sh --engine sqlite --server-id lobby

# Windows PowerShell (MariaDB)
.\scripts\windows\Start-MariaDB.ps1
.\scripts\windows\Configure-Db.ps1 -ServerId lobby
```

YaPPlayerData prefers the shared YaPDB pool (`use-shared-yapdb: true`). Multi-backend: same JDBC, unique `server-id` (MariaDB or Postgres — not SQLite).

**Fleet / portals:** product defaults use `inventory-profile: global` and `sync.inventory: true`
so hub → survival (YaPPortals, `/hub`, server selector) keeps the same inventory, enderchest,
XP, and vitals — including **YaPItems** custom weapons (full ItemStack + PDC). Each backend
must keep a **different** `server-id` (fleet stamps this). Item *definitions* sync separately
via the YaPItems catalog (see [YAPITEMS.md](../plugins/YAPITEMS.md)).

## Session lock (double-login)

Always on. Prevents the same UUID being online on two backends at once:

- Acquire `lock_server` / `lock_until` on join; refresh on autosave; **release immediately on quit**
  (profile save continues async so Link soft-switch / `/hub` is not blocked)
- Contested lock → kick with holder server name
- Async pre-login rejects early when another server holds a live lock (short retry for transfers)
- Stuck lock: `/yapdata unlock <player>` — or restart the holder backend (clears its locks on enable)
- Crash / kill of a backend: Link clears the lock when that backend is **down**; startup also wipes
  locks for that `server-id`
- **Save path:** snapshot profile on quit, `repository.saveProfile` on Bukkit async — I/O off main

`lock-ttl-seconds` (default 120) auto-expires crashed holds if the holder stays marked online.

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

**Bedrock hub forms (P0):** When `yap-bedrock-ui.jar` (YaPBedrockUI) is present, Floodgate/native Bedrock players get simple forms for `/menu` (bag/homes/kits/warps/balance + other feature buttons), kit claim, homes, and warps. JE keeps the chest GUIs. Soft-dep — without YaPBedrockUI, behavior is unchanged. See [CROSSPLAY.md](../network/CROSSPLAY.md).

| Area | Default | Config |
|------|---------|--------|
| Auth `/login` | on | `auth.enabled` |
| Economy balance API + optional Vault | on | `economy.enabled` — cmds `/bal` `/pay` `/eco` via Essentials |
| Homes / warps / kits / mail | on | `features.homes` … — cmds via Essentials |
| Claims | on | `features.claims` (+ `claims.*`) — `/claim` via Essentials |
| Shops / auctions (AH) | **on** | `features.shops` / `auctions` — cmds via Essentials |
| Jobs | **off** (forced off when YaPSkills is loaded) | `features.jobs` |
| NPC shop catalogs | **on** | `features.traders` — used by YaPNpcs `/npc shop` (no `/trader`) |
| Backpack storage | **on** | `features.backpack` — `/bag` via Essentials |

Money features require `economy.enabled: true`. When economy is off, shops/jobs/AH/traders and claim tax stay off even if their feature flags are true.

**Native economy (no Vault required):** first-party plugins use Bukkit `ServicesManager` → `PlayerDataService`
(`balance` / `deposit` / `withdraw` / `setBalance`). Deposits fire `PlayerBalanceChangeEvent` for quest
`ECONOMY_EARN` hooks. Lifetime **playtime** is tracked as `players.play_minutes` (join/quit) and exposed via
`PlayerDataService.playMinutes(UUID)` for quest `PLAYTIME` objectives.
`/eco give`, Tab `{balance}`, and other first-party payouts go through that API. Vault remains an
**optional** bridge (`YaPEconomy`) only if you drop `Vault.jar` for third-party plugins.

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
  traders: true
  backpack: true
backpack:
  default-pages: 3
  max-pages: 9
```

**NPC shops:** administered via **YaPNpcs** — `/npc shop enable|apply|presets|addbuy|addsell|setitem|setoffer|list|clearoffers|clear`
(and dashboard **Shops** tab). Built-in presets (`weapons`, `armor`, `tools`, `food`, `blocks`,
`redstone`, `crafting`, `enchants`): unlimited stock, buy + sell spreads (~38% buyback),
enchanted lines buy-only. `setitem` upserts buy+sell for one material in a single command
(blank/`-` disables a side).

**Trade GUI:** one icon per item — **left-click** to buy, **right-click** to sell; quantity
picker shows running totals (you pay / you receive) before confirm.

PlayerData stores offer catalogs + opens the trade GUI; there is no `/trader` command.

**Ownership:** YaPPlayerData is the **data plane** (sync, lock, auth, schema, `PlayerDataService`, storage for bag/homes/kits/…).  
Player-facing QoL commands (`/bag`, `/home`, `/kit`, `/bal`, `/shop`, `/ah`, `/claim`, `/menu`, …) are owned by **`yap-essentials.jar`** and wired through the `PlayerFeatures` Bukkit service. Enable both jars.

| Area | What |
|------|------|
| Auth | `/register` `/login` · freeze until auth · BCrypt |
| Session lock | Cross-server dual-login kick · `/yapdata unlock` |
| Sync | Inv / XP / vitals · economy balance rows when enabled |
| NPC shop catalogs | Backend for `/npc shop` (YaPNpcs) |
| Feature storage | Homes/warps/kits/mail/shops/AH/claims/backpack tables + repos |

QoL UX (commands + GUIs + claim shovel) → **YaPEssentials** (requires this plugin).

### Backpack (extra bag space)

The vanilla **E** inventory cannot grow from the server. **`/bag`** (aliases `/backpack` `/bp`) is registered by **YaPEssentials** and opens a double-chest GUI with **pages** backed by YaPPlayerData (`player_backpack_pages`, same `inventory-profile` as inv/enderchest).

| Who | Pages |
|-----|-------|
| Everyone with `yapdata.bag` | `backpack.default-pages` (3) |
| VIP `yapdata.bag.pages.5` | 5 |
| Staff `yapdata.bag.pages.7` | 7 |
| Admin `yapdata.bag.pages.*` | `backpack.max-pages` (9) |
| Staff `yapdata.bag.see` | `/bag see <player> [page]` |

Vanilla Java and Bedrock use the command / hub icon. On Bedrock with YaPBedrockUI, the `/menu` Bag button opens `/bag` and prints a short tip; the bag itself remains a Paper double-chest (inventory authority works on BE). The optional **yap-bag** Fabric client (Minecraft 26.2) binds **B** and adds a Bag button on the inventory screen plus page tabs on the bag chest. Same items — the mod is not required.

Existing ranks: `ranks apply force` (or dashboard) so starter-grants pick up the new bag nodes.

### Kits (EssentialsX-class)

Kits are **stored** by YaPPlayerData (`kits.yml` + MariaDB). Commands (`/kit`, `/createkit`, …) are owned by **YaPEssentials**. `/createkit` captures inventory, armor, and offhand with full item data (enchants, names, components).

| What | Where |
|------|--------|
| Definitions | `plugins/YaPPlayerData/kits.yml` — **same file on Hub + every survival backend** (Control syncs catalog → `fleet/instances/*/plugins/` on ensure / dashboard kit save) |
| Cooldowns / uses | MariaDB `kit_cooldowns` (network-wide) |
| Store grants | MariaDB `kit_grants` via `kit grant <player> <kit>` |
| Access | `yapdata.kit.<id>` / VIP `yapdata.kit.*` (YaPPerms) |
| First join | `first-join: true` on a kit (starter ships on) |
| Cost | `cost:` + economy balance |
| Signs | `[Kit]` line 1, kit id line 2 |

Player: `/kit` `/kits` `/showkit` · Admin: `/createkit` `/delkit` `/kitreset` · Console/store: `kit give` · `kit grant`.  
Dashboard: **Gameplay → Kits** builds the same `kits.yml` (items, armor slot, cooldown, cost, first-join, commands).  
Tebex on Hub: [INTEGRATIONS.md](../ops/INTEGRATIONS.md) · [examples/tebex/](../../examples/tebex/).

Plugin jar: `gradle :playerdata-plugin:installIntoPlugins` (also in `assembleRelease`).
