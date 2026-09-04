# SQLite for YaP (single-node)

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

See [YAPDB.md](YAPDB.md) · [MARIADB.md](MARIADB.md) · [POSTGRES.md](POSTGRES.md).
