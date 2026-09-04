# MariaDB for YaP (shared YaPDB + PlayerData)

YaPcore does **not** embed a database engine. Owners run one MariaDB instance
(packaged via Docker). **`yap-db.jar` (YaPDB)** owns the shared Hikari pool on each
**YaP-Folia** backend; **YaPPlayerData** and other SQL plugins borrow it.

**Engine support:** MariaDB / MySQL (default), plus [PostgreSQL](POSTGRES.md) and
[SQLite](SQLITE.md) via the same `YapSqlDialect` layer. Details: [YAPDB.md](YAPDB.md#supported-engines).

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
