# How to use YaPDB

**Jar:** `yap-db.jar`  
Shared Hikari SQL pool (MariaDB/MySQL, PostgreSQL, SQLite) for fleet plugins.

## What it does

One connection pool per JVM. PlayerData, Claims, Moderation, Essentials spawn DB, NPC shops, etc. soft-depend on YaPDB.

## Quick start

```bash
./scripts/db/ensure-db.sh --server-id lobby
# PostgreSQL:
./scripts/db/ensure-postgres.sh --server-id lobby
# SQLite (single node only):
./scripts/db/configure-db.sh --engine sqlite --server-id lobby
```

In-game: `/yapdb` for status/reload (admin).

## Ops rules

- Multi-backend: **same JDBC**, unique `server-id` per instance
- Do **not** use SQLite for hub+survival fleet
- Prefer `use-shared-yapdb: true` in dependent plugins

## Troubleshoot

| Symptom | Fix |
|---------|-----|
| Plugins warn no pool | YaPDB not enabled / wrong JDBC |
| Lock/contention | Check MariaDB max connections; pool sizes |

## Related

[YAPDB.md](../../data/YAPDB.md) · [yapplayerdata.md](yapplayerdata.md)
