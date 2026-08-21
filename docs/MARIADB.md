# MariaDB for YaP (shared YaPDB + PlayerData)

YaPcore does **not** embed a database engine. Owners run one MariaDB instance
(packaged via Docker). **`yap-db.jar` (YaPDB)** owns the shared Hikari pool;
**YaPPlayerData** and future SQL plugins borrow it.

Works the same on **Linux** and **Windows**.

## Recommended: Docker package

| OS | Start | Configure | Stop |
|----|-------|-----------|------|
| Linux / macOS | `./scripts/db/start-mariadb.sh` | `./scripts/db/configure-db.sh` (or `configure-playerdata.sh`) | `./scripts/db/stop-mariadb.sh` |
| Windows | `.\scripts\windows\Start-MariaDB.ps1` | `Configure-Db.ps1` / `Configure-PlayerData.ps1` | `Stop-MariaDB.ps1` |
| Release zip | `./start-mariadb.sh` / `.cmd` | `./configure-db.sh` / `configure-playerdata` | `./stop-mariadb.sh` |

Requires [Docker](https://docs.docker.com/get-docker/) (Desktop on Windows).

Compose lives in [`deploy/mariadb/`](../deploy/mariadb/). First start copies `.env.example` → `.env`.

### Single server (same machine)

```bash
./scripts/db/start-mariadb.sh
./scripts/db/configure-db.sh --server-id lobby
./scripts/start.sh --fg
```

JDBC points at `127.0.0.1`. Shared config: `plugins/YaPDB/config.yml`.

### Multi-backend / Velocity

1. Run MariaDB **once**.
2. On **each** game backend:

```bash
./scripts/db/configure-db.sh --host 192.168.1.10 --server-id lobby
./scripts/db/configure-db.sh --host 192.168.1.10 --server-id survival
```

Rules:

- **Same** JDBC URL / user / password on every backend (YaPDB + playerdata fallback)
- **Unique** `server-id` per backend (playerdata)
- Open firewall `3306` only to backend IPs

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
| YaPDB disables on boot | Start MariaDB; `./scripts/db/status-mariadb.sh` |
| PlayerData uses embedded pool | Install `yap-db.jar`; check `/yapdb status` |
| Access denied | Password in `plugins/YaPDB/config.yml` must match `.env` |
| Multi-backend can't connect | Use LAN IP not `127.0.0.1` |
| Port 3306 busy | `YAP_DB_PORT=3307` in `.env` and reconfigure |

See also [YAPDB.md](YAPDB.md) · [PLAYERDATA.md](PLAYERDATA.md) · [deploy/mariadb/README.md](../deploy/mariadb/README.md).
