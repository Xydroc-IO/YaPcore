# Windows launch scripts (PowerShell)

Used by `gradle assembleRelease` → `build/dist/yapcore-release/windows/`.

| Script | Role |
|--------|------|
| `Lib.ps1` | Shared config / JVM / pid / junctions / Paperclip check |
| `Start.ps1` | Start server (`-Gui`, `-Fg`) |
| `Stop.ps1` | Stop (`-Force`) |
| `Status.ps1` | Running / config summary |
| `Gui.ps1` | Swing control panel |
| `Start-Prod.ps1` | Large pinned heap + ZGC |
| `Nginx-Setup.ps1` | Same nginx templates as Linux (`-DryRun`, `-Uninstall`) |
| `Vendor-Paper.ps1` | Clone/pin `vendor/paper` |
| `Start-MariaDB.ps1` | Docker MariaDB for YaPPlayerData |
| `Stop-MariaDB.ps1` | Stop MariaDB container (keeps data) |
| `Configure-PlayerData.ps1` | Patch `plugins/YaPPlayerData/config.yml` JDBC |

Root wrappers: `start.cmd`, `nginx-setup.cmd`, `start-mariadb.cmd`, `configure-playerdata.cmd`, …

See [docs/WINDOWS.md](../../docs/WINDOWS.md) · [docs/MARIADB.md](../../docs/MARIADB.md).
