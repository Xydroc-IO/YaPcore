# PostgreSQL for YaP

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

See also [YAPDB.md](YAPDB.md) · [MARIADB.md](MARIADB.md) · [SQLITE.md](SQLITE.md).
