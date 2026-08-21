# YaPcore MariaDB (shared YapDb + player data)

One shared database for **YaPDB** (`yap-db.jar`) and **YaPPlayerData** —
works for a single YaPcore instance or many backends behind Velocity.

## Quick start (recommended)

**Needs [Docker](https://docs.docker.com/get-docker/)** (Desktop on Windows/macOS, Engine on Linux).

### Linux / macOS

```bash
./scripts/db/start-mariadb.sh
./scripts/db/configure-db.sh --server-id lobby   # shared pool + playerdata
# multi-backend on LAN:
./scripts/db/configure-db.sh --host 192.168.1.10 --server-id lobby
```

### Windows (PowerShell)

```powershell
.\scripts\windows\Start-MariaDB.ps1
.\scripts\windows\Configure-Db.ps1 -ServerId lobby
# multi-backend:
.\scripts\windows\Configure-Db.ps1 -HostAddress 192.168.1.10 -ServerId survival
```

Then start YaPcore as usual (`yap-db.jar` + `yap-playerdata.jar`). Schema is created by playerdata on enable.

Stop: `./scripts/db/stop-mariadb.sh` or `.\scripts\windows\Stop-MariaDB.ps1`

## Layouts

| Setup | What to do |
|-------|------------|
| **Single server** | Start MariaDB on the same machine; JDBC `127.0.0.1`; `server-id: lobby` |
| **Multi-backend / Velocity** | One MariaDB (any host); **same** JDBC URL on every backend; **unique** `server-id` each |
| **Per-server inv (minigames)** | Same DB; set `inventory-profile: server` on that backend |

Firewall: only expose `3306` to your game hosts (or keep bind on localhost + SSH tunnel / private network).

## Change password

1. Edit `deploy/mariadb/.env` (created from `.env.example` on first start)
2. Recreate: `./scripts/db/stop-mariadb.sh && ./scripts/db/start-mariadb.sh`  
   (volume keeps data; if user password mismatch, reset volume — **destroys DB**)
3. Re-run `configure-playerdata.sh` so plugin config matches

## Without Docker

Install MariaDB/MySQL from your OS (apt, winget, Homebrew), create DB/user matching
`.env.example`, then run `configure-playerdata.sh --host …`.

See [docs/MARIADB.md](../../docs/MARIADB.md).
