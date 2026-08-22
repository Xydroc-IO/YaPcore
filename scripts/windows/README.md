# Windows launch scripts (PowerShell)

Used by `gradle assembleRelease` → `build/dist/yapcore-release/windows/`.

| Script | Role |
|--------|------|
| `Lib.ps1` | Shared config / JVM / pid / Folia kernel junctions |
| `Start.ps1` | Start server (`-Gui`, `-Fg`) — Folia product path |
| `Stop.ps1` | Stop (`-Force`) |
| `Status.ps1` | Running / config summary |
| `Gui.ps1` | Swing control panel |
| `Start-Prod.ps1` | Large pinned heap + ZGC |
| `Nginx-Setup.ps1` | Same nginx templates as Linux (`-DryRun`, `-Uninstall`) |
| `Start-MariaDB.ps1` | Docker MariaDB for YaPPlayerData |
| `Stop-MariaDB.ps1` | Stop MariaDB container (keeps data) |
| `Configure-PlayerData.ps1` | Patch `plugins/YaPPlayerData/config.yml` JDBC |
| `Configure-Db.ps1` | Patch YapDb JDBC |

Root wrappers: `start.cmd`, `nginx-setup.cmd`, `start-mariadb.cmd`, `configure-playerdata.cmd`, …

See [docs/WINDOWS.md](../../docs/WINDOWS.md) · [docs/MARIADB.md](../../docs/MARIADB.md).
