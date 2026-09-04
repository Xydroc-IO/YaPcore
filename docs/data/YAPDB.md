# YaPDB — shared SQL pool for YaP plugins

First-party **`yap-db.jar`** (`YaPDB`) owns **one HikariCP pool** per JVM on each
**YaP-Folia** backend. Other plugins (and your own addons) borrow connections instead of
each shading Hikari + a JDBC driver.

## Supported engines

| Engine | Status | Notes |
|--------|--------|-------|
| **MariaDB 10.11+ / 11.x** | **Supported (default)** | `deploy/mariadb/` · `./scripts/db/ensure-db.sh` |
| **MySQL 8.x** | **Supported** | Same `jdbc:mysql://…` path |
| **PostgreSQL 14+** | **Supported** | `deploy/postgres/` · `./scripts/db/ensure-postgres.sh` |
| **SQLite 3** | **Supported (single-node)** | `./scripts/db/configure-db.sh --engine sqlite` → `data/yap.db` |
| MongoDB / Redis-as-primary | Not supported | Redis may appear elsewhere for cache only |

Engine is detected from the JDBC URL (`jdbc:mysql:`, `jdbc:postgresql:`, `jdbc:sqlite:`),
or set explicitly with `jdbc.engine: auto|mysql|postgres|sqlite` in `plugins/YaPDB/config.yml`.

First-party plugins build DDL/DML through `YapDb.dialect()` (`YapSqlDialect`) so upserts and
types stay portable (`ON DUPLICATE KEY` / `ON CONFLICT` / `INSERT OR IGNORE`).

**SQLite caveat:** one Folia JVM / one file only. Multi-backend + YaP Link need MariaDB or Postgres.

See [MARIADB.md](MARIADB.md) · [POSTGRES.md](POSTGRES.md) · [SQLITE.md](SQLITE.md).

## Why

| Without YaPDB | With YaPDB |
|---------------|------------|
| Every SQL plugin embeds its own pool | One pool → same database |
| Duplicate JDBC config | Config once in `plugins/YaPDB/config.yml` |
| Easy to point backends at different DBs by mistake | Same shared instance by design |

## Setup

```bash
# MariaDB (default)
./scripts/db/ensure-db.sh --server-id lobby

# PostgreSQL
./scripts/db/ensure-postgres.sh --server-id lobby

# SQLite (single server)
./scripts/db/configure-db.sh --engine sqlite --server-id lobby

# jars: plugins/yap-db.jar + plugins/yap-playerdata.jar
```

## Commands

| Command | What |
|---------|------|
| `/yapdb status` | Open? engine? JDBC URL? |
| `/yapdb reload` | Re-read config and reopen pool |

## For plugin authors

1. Soft-depend (or depend) on `YaPDB`.
2. Compile against the API module:

```kotlin
compileOnly(project(":yap-db-api"))
// runtime: yap-db.jar must be in plugins/
```

```java
import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbProvider;
import com.yapcore.db.YapSqlDialect;

Optional<YapDb> db = YapDbProvider.find();
if (db.isEmpty()) {
    return;
}
YapSqlDialect dialect = db.get().dialect();
try (Connection c = db.get().connection()) {
    String sql = dialect.upsert("my_table",
        List.of("id"),
        List.of("id", "value"),
        Map.of("value", "EXCLUDED.value"));
    // …
}
```

`plugin.yml`:

```yaml
softdepend: [YaPDB]
# or: depend: [YaPDB]
```

Do **not** relocate `com.yapcore.db` in your shadow jar — the interface must match YaPDB.

## YaPPlayerData

Uses the shared pool when `use-shared-yapdb: true` (default) and YaPDB is enabled.
Falls back to an embedded pool only if YaPDB is missing (not recommended for multi-plugin setups).

## Same database for all backends

All backends + all SQL plugins → **same** JDBC URL from `configure-db.sh` / `ensure-*.sh`.
SQLite cannot be shared across machines — use MariaDB or Postgres for networks.
