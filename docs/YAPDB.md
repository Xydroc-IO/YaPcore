# YaPDB — shared MariaDB pool for YaP plugins

First-party **`yap-db.jar`** (`YaPDB`) owns **one HikariCP pool** per JVM. Other plugins
(and your own addons) borrow connections instead of each shading Hikari + the MySQL driver.

## Why

| Without YaPDB | With YaPDB |
|---------------|------------|
| Every SQL plugin embeds its own pool | One pool → same Docker MariaDB |
| Duplicate JDBC config | Config once in `plugins/YaPDB/config.yml` |
| Easy to point backends at different DBs by mistake | Same shared instance by design |

## Setup

```bash
./scripts/db/start-mariadb.sh
./scripts/db/configure-db.sh          # or configure-playerdata.sh (does both)
# jars: plugins/yap-db.jar + plugins/yap-playerdata.jar
```

Windows: `Configure-Db.ps1` / `Configure-PlayerData.ps1`.

## Commands

| Command | What |
|---------|------|
| `/yapdb status` | Pool open? JDBC URL? |
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

Optional<YapDb> db = YapDbProvider.find();
if (db.isEmpty()) {
    // YaPDB missing — disable feature or use your own store
    return;
}
try (Connection c = db.get().connection()) {
    // your SQL
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

## Same Docker MariaDB

All backends + all SQL plugins → **same** JDBC URL from `deploy/mariadb/.env`.
See [MARIADB.md](MARIADB.md).
