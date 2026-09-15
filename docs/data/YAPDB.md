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

**Probe (what’s actually listening / connected):**

```bash
./scripts/db/probe-db.sh              # TCP 3306 / 3316 / 5432
./scripts/db/probe-db.sh --host 10.0.0.5
```

In-game (Folia up): `/yapdb probe [host]` and `/yapdb status` — status shows the JDBC
`DatabaseMetaData` product string (e.g. `MariaDB 11.x`) after the pool opens. Port probe
does **not** invent credentials; it only reports open listeners.

First-party plugins build DDL/DML through `YapDb.dialect()` (`YapSqlDialect`) so upserts and
types stay portable (`ON DUPLICATE KEY` / `ON CONFLICT` / `INSERT OR IGNORE`).

**SQLite caveat:** one Folia JVM / one file only. Multi-backend + YaP Link need MariaDB or Postgres.

If the configured MariaDB/Postgres pool fails at enable, YaPDB **stays loaded** (so dependents can still resolve `YapSqlDialects` / `YapDbBootstrap`) and opens a local `plugins/YaPDB/yap-fallback.db` SQLite file when possible. Fix the primary JDBC URL for fleets — fallback is single-node only.

See [YAPDB.md](YAPDB.md) · [YAPDB.md](YAPDB.md) · [YAPDB.md](YAPDB.md).

## Why

| Without YaPDB | With YaPDB |
|---------------|------------|
| Every SQL plugin embeds its own pool | One pool → same database |
| Duplicate JDBC config | Config once in `plugins/YaPDB/config.yml` |
| Easy to point backends at different DBs by mistake | Same shared instance by design |

## Setup

**Dashboard / Control GUI (recommended):** Fleet tab → **Database (YaPDB)** card, or Swing **Database…**

- Pick **MariaDB / MySQL**, **PostgreSQL**, or **SQLite**
- **Start Docker** for MariaDB/Postgres (packaged compose under `deploy/`)
- **Set up database** writes `plugins/YaPDB/config.yml` and syncs JDBC to fleet instances
- API: `GET/POST /api/database` (`ensure`, `start-docker`, `stop-docker`, `sync-fleet`)

**CLI:**

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

### Preferred: `YapDbBootstrap`

First-party plugins open pools through `YapDbBootstrap` so shared-YaPDB vs embedded Hikari stays one path.
Callers still construct `HikariConfig` / `HikariDataSource` themselves (shade/relocate of Hikari keeps working).

```java
import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

YapDbBootstrap.Settings settings = YapDbBootstrap.Settings.of(
        "MyPlugin", jdbcUrl, user, pass, poolMax, poolMinIdle, timeoutMs, preferShared);
var shared = YapDbBootstrap.openSharedOrEmpty(settings, YapDbBootstrap.warnTo(getLogger()));
if (shared.isPresent()) {
    YapDb db = shared.get();
    // migrate + queries via db.connection() / db.dialect()
    return;
}
HikariConfig hc = new HikariConfig();
var dialect = YapDbBootstrap.configureEmbedded(hc, settings);
HikariDataSource ds = new HikariDataSource(hc);
```

### Direct service lookup

When you only need the shared pool (no embedded fallback):

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


---

## MariaDB setup

YaPcore does **not** embed a database engine. Owners run one MariaDB instance
(packaged via Docker). **`yap-db.jar` (YaPDB)** owns the shared Hikari pool on each
**YaP-Folia** backend; **YaPPlayerData** and other SQL plugins borrow it.

**Engine support:** MariaDB / MySQL (default), plus [PostgreSQL](YAPDB.md) and
[SQLite](YAPDB.md) via the same `YapSqlDialect` layer. Details: [YAPDB.md](YAPDB.md#supported-engines).

Works the same on **Linux** and **Windows**.

## Recommended: Docker package

| OS | Start | Configure | Stop |
|----|-------|-----------|------|
| Linux / macOS | `./scripts/db/start-mariadb.sh` | `./scripts/db/ensure-db.sh` (or `configure-db.sh`) | `./scripts/db/stop-mariadb.sh` |
| Windows | `.\scripts\windows\Start-MariaDB.ps1` | `Configure-Db.ps1` / `Configure-PlayerData.ps1` | `Stop-MariaDB.ps1` |
| Release zip | `./start-mariadb.sh` / `.cmd` | `./configure-db.sh` / `configure-playerdata` | `./stop-mariadb.sh` |

Requires [Docker](https://docs.docker.com/get-docker/) (Desktop on Windows).

Compose lives in [`deploy/mariadb/`](../deploy/mariadb/). First start copies `.env.example` → `.env`.
If host **:3306** is already taken, `start-mariadb.sh` remaps to **3316** automatically.

### One-shot (preferred)

```bash
./scripts/db/ensure-db.sh --server-id lobby
./scripts/start.sh --fg
```

`ensure-db.sh` starts MariaDB (if needed), writes JDBC into `plugins/YaPDB` + `plugins/YaPPlayerData`, and probes login before you boot.

For a custom home / smoke workdir:

```bash
./scripts/db/ensure-db.sh --root /path/to/yap-home --server-id lobby
```

### Single server (same machine)

```bash
./scripts/db/start-mariadb.sh --configure --server-id lobby
# or: ./scripts/db/ensure-db.sh --server-id lobby
./scripts/start.sh --fg
```

JDBC points at `127.0.0.1` (port from `.env`). Shared config: `plugins/YaPDB/config.yml`.

### Multi-backend / Velocity / YaP Link

1. Run MariaDB **once**.
2. On **each** game backend:

```bash
./scripts/db/configure-db.sh --host 192.168.1.10 --server-id lobby
./scripts/db/configure-db.sh --host 192.168.1.10 --server-id survival
```

Rules:

- **Same** JDBC URL / user / password on every backend (YaPDB + playerdata fallback)
- **Unique** `server-id` per backend (playerdata)
- Open firewall for `YAP_DB_PORT` only to backend IPs

## Shared pool vs embedded

| Jar | Role |
|-----|------|
| `yap-db.jar` | Shared Hikari pool — install this for any SQL plugin |
| `yap-playerdata.jar` | Prefers YaPDB; embedded fallback if YaPDB missing |

See [YAPDB.md](YAPDB.md) for the plugin API.

## Without Docker

Install MariaDB/MySQL, create database/user matching `deploy/mariadb/.env.example`, then
`configure-db.sh --host …`.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| YaPDB disables on boot | `./scripts/db/ensure-db.sh --server-id <id>` then restart; `./scripts/db/status-mariadb.sh` |
| Access denied for `yap@localhost` | Jar default is **:3306**; YaP Docker may be on **3316**. Re-run ensure/configure into the **same** home that boots (`--root`) |
| PlayerData uses embedded pool | Install `yap-db.jar`; check `/yapdb status` |
| Multi-backend can't connect | Use LAN IP not `127.0.0.1` |
| Port 3306 busy | Auto-bumped to 3316 on start; or set `YAP_DB_PORT` in `.env` and reconfigure |

See also [YAPDB.md](YAPDB.md) · [PLAYERDATA.md](PLAYERDATA.md) · [deploy/mariadb/README.md](../../deploy/mariadb/README.md).


---

## PostgreSQL setup

Packaged Docker Postgres for YaPDB + first-party SQL plugins.

## Quick start

```bash
./scripts/db/start-postgres.sh
./scripts/db/configure-db.sh --engine postgres --server-id lobby
# or one-shot:
./scripts/db/ensure-postgres.sh --server-id lobby
./scripts/start.sh --fg
```

Compose: [`deploy/postgres/`](../../deploy/postgres/). Credentials: `.env` from `.env.example`.

Default host port **5432** (`YAP_PG_PORT`). If busy, `start-postgres.sh` remaps to **5433**.

JDBC example:

```text
jdbc:postgresql://127.0.0.1:5432/yap_playerdata
```

## Status / stop

```bash
./scripts/db/status-postgres.sh
docker compose -f deploy/postgres/docker-compose.yml down   # keeps volume
```

## Notes

- Same schema as MariaDB via `YapSqlDialect` (upserts use `ON CONFLICT`).
- Fine for multi-backend / YaP Link (unlike SQLite).
- Migrating an existing MariaDB dataset is out of band (`pgloader` / dump+load).

See also [YAPDB.md](YAPDB.md) · [YAPDB.md](YAPDB.md) · [YAPDB.md](YAPDB.md).


---

## SQLite setup

Zero-Docker SQL for a **single** Folia JVM. Not for multi-backend or shared Link networks.

## Quick start

```bash
./scripts/db/configure-db.sh --engine sqlite --server-id lobby
./scripts/start.sh --fg
```

Creates/uses:

```text
jdbc:sqlite:{yap-home}/data/yap.db
```

YaPDB forces pool size **1**, enables **WAL** + busy timeout on open.

## Limits

| OK | Not OK |
|----|--------|
| One game backend on one machine | Multiple Folia backends sharing one file over NFS |
| Local / LAN / small SMP | Production proxy farms (use MariaDB or Postgres) |

## Switch away later

Point `plugins/YaPDB/config.yml` at MariaDB or Postgres (`configure-db.sh --engine mysql|postgres`).
Schema is recreated on migrate for empty DBs — bring your own data export if you need to keep rows.

See [YAPDB.md](YAPDB.md) · [YAPDB.md](YAPDB.md) · [YAPDB.md](YAPDB.md).
