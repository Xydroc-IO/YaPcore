# How to use YaPPlayerData

**Jar:** `yap-playerdata.jar`  
Cross-server data plane: inventory/XP sync, session lock, optional `/login`, economy, homes/warps/kits/mail/shops/AH/bag, NPC trader catalogs.

## What it does

YaPPlayerData is the **shared SQL profile** every fleet backend talks to. Players rarely type `/yapdata` — they use Essentials commands that read this store.

Always on: session lock · profile sync · `/menu` · `/yapdata` admin.

## Prerequisites

```bash
./scripts/db/ensure-db.sh --server-id lobby
# each Folia instance needs a unique server-id
```

- `yap-db.jar` healthy
- `use-shared-yapdb: true` (default)
- Same JDBC for all backends; **not** SQLite for multi-server

## Inventory sync (fleet)

Default product:

```yaml
inventory-profile: global
sync:
  inventory: true
```

Hub → survival keeps the same inventory (including YaPItems). Minigame/creative backends should use `inventory-profile: server` so they do **not** share survival gear.

## Session lock

One UUID online on one backend at a time.

| Situation | What to do |
|-----------|------------|
| “Already logged in on server X” | Wait for quit unlock, or `/yapdata unlock <player>` |
| Stuck after crash | Unlock command, or restart holder backend |
| Soft-switch via portals | Portals release lock before Connect |

TTL: `lock-ttl-seconds` (default 120).

## Optional offline auth

```yaml
auth:
  enabled: true
  force: false
  trust-velocity: false
```

| Command | Use |
|---------|-----|
| `/register <pass> <pass>` | Create account |
| `/login <pass>` | Authenticate |
| `/changepassword <old> <new>` | Change |
| `/logout` | Clear session |
| `/unregister <player>` | Admin wipe |

Until logged in: frozen, limited commands.

## Feature flags

| Feature | Default | Player cmds (via Essentials) |
|---------|---------|------------------------------|
| Economy | on | `/bal` `/pay` `/eco` |
| Homes / warps / kits / mail | on | `/home` `/warp` `/kit` `/mail` |
| Claims | on | `/claim` |
| Shops / AH | on | `/shop` `/ah` |
| Jobs | off (forced off if YaPSkills) | `/jobs` |
| NPC traders | on | `/npc shop …` |
| Backpack | on | `/bag` |

Money-dependent features need `economy.enabled: true`.

## Admin

```text
/yapdata
/yapdata unlock Steve
```

Dashboard: economy, shops, player tools. Kits file: `plugins/YaPPlayerData/kits.yml` — keep identical on Hub + gameplay backends.

## Troubleshoot

| Symptom | Fix |
|---------|-----|
| Inv empty after portal | Check `inventory-profile` + `sync.inventory`; wait for “Synced profile” |
| Economy off | `economy.enabled: true` + restart/reload |
| NPC shop presets empty | `features.traders: true` |
| Dual login kicks | Unlock; ensure portals release lock |

## Related

- [PLAYERDATA.md](../../data/PLAYERDATA.md) · [yapdb.md](yapdb.md) · [yapessentials.md](yapessentials.md)
- [yapnpcs.md](yapnpcs.md) · [yap420.md](yap420.md)
