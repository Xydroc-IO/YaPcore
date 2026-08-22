# YaP PlayerData — cross-server sync, offline auth, claims, taxes, NPC traders, GUIs

First-party **`yap-playerdata.jar`** (`YaPPlayerData`) — shared MariaDB across Velocity.

## Database setup (required)

See **[MARIADB.md](MARIADB.md)** and **[YAPDB.md](YAPDB.md)** — Docker MariaDB + shared `yap-db.jar` pool:

```bash
# Linux
./scripts/db/start-mariadb.sh && ./scripts/db/configure-db.sh --server-id lobby

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
| Economy `/bal` `/pay` + Vault | on | `economy.enabled` |
| Homes / warps / kits / mail | on | `features.homes` … |
| Claims | on | `features.claims` (+ `claims.*`) |
| Shops / jobs / auctions | **off** | `features.shops` / `jobs` / `auctions` |
| NPC traders | **off** | `features.traders` |

Money features require `economy.enabled: true`. When economy is off, shops/jobs/AH/traders and claim tax stay off even if their feature flags are true.

```yaml
# No economy network (sync + homes only):
economy:
  enabled: false
features:
  homes: true
  warps: true
  kits: true
  mail: true
  shops: false
  jobs: false
  auctions: false
  claims: true
  traders: false
```

**Freeze:** no new Essentials-class commands without product review. Prefer feature toggles over a separate `yap-essentials` jar unless a real install profile needs it.

| Area | What |
|------|------|
| Auth | `/register` `/login` · freeze until auth · BCrypt |
| Session lock | Cross-server dual-login kick · `/yapdata unlock` |
| Sync | Inv / XP / vitals · economy when enabled |
| Fancy GUIs | `/menu` hub (icons match enabled modules) |
| Claims | Shovel · subdivides · taxes (tax needs economy) |
| NPC traders | `/trader` (needs economy) |
| Homes/warps/kits/mail | Cross-server |
| Shops/jobs/AH | Opt-in money modules |

Plugin jar: `gradle :playerdata-plugin:installIntoPlugins` (also in `assembleRelease`).
