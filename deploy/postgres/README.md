# Packaged PostgreSQL for YaP

```bash
./scripts/db/start-postgres.sh
./scripts/db/configure-db.sh --engine postgres --server-id lobby
# or one-shot:
./scripts/db/ensure-postgres.sh --server-id lobby
```

Compose: this directory. Credentials: `.env` (from `.env.example`).

Default host port **5432** (`YAP_PG_PORT`). JDBC:

`jdbc:postgresql://127.0.0.1:5432/yap_playerdata`

See [docs/data/POSTGRES.md](../../docs/data/POSTGRES.md).
