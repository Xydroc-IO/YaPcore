# Shipped release defaults

First-boot / fresh-install configs copied by `./scripts/seed-defaults.sh`
(also invoked from `start.sh` via `yap_ensure_dirs`).

**Never overwrites** existing operator files — only fills gaps.

| Path | Purpose |
|------|---------|
| `server.properties` | Full product profile (packs, ranks, Folia, dashboard) |
| `plugins/YaPDB/config.yml` | JDBC aligned with `deploy/mariadb/.env.example` |
| `plugins/YaPPlayerData/config.yml` | LAN-friendly auth off; shared YaPDB |
| `plugins/YaPDiscord/config.yml` | Discord inbound off until webhooks set |
| `link.properties` | YaP Link single-backend + plugins on |

After MariaDB is up, run `./configure-db.sh --server-id lobby` (or
`./scripts/db/ensure-db.sh`) so JDBC host/port/password match the live `.env`.

See [docs/start/DEFAULTS.md](../docs/start/DEFAULTS.md).
