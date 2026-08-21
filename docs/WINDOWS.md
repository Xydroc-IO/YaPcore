# Windows host guide — parity with Linux

YaPcore is meant to run **the same product** on Windows and Linux.
Release trees: `build/dist/yapcore-release/linux/` and `…/windows/`.

## Requirements (both)

| Need | Notes |
|------|--------|
| **JDK 25+** | Paper 26.2 / Phase 3 |
| **Git** | Vendor Paper |
| **Git Bash** (Windows) | Runs `scripts/apply-yap-paper-hooks.sh` during Paperclip build |

## Launch

| Linux | Windows |
|-------|---------|
| `./start.sh --fg` | `start.cmd -Fg` |
| `./gui.sh` | `gui.cmd` |
| `./stop.sh` / `./status.sh` | `stop.cmd` / `status.cmd` |
| `./start-prod.sh` | `start-prod.cmd` |

## YaP Paperclip (Phase 3) — native on Windows

Same pin as Linux (`vendor/paper.pin`):

```powershell
.\scripts\Vendor-Paper.ps1
.\scripts\Build-Vendor-Paper.ps1
# → lib\paper-26.2-yap.jar  (+ paper-kernel\paper-26.2.jar)
```

Linux equivalent: `./scripts/vendor-paper.sh` then `./scripts/build-vendor-paper.sh`.

`Start.ps1` **fails closed** if Phase 3 NMS is on and the yap Paperclip jar is missing (same idea as Linux).

## nginx edge — both platforms

Templates: `deploy/nginx/*.template` (shared).  
Generated: `deploy/nginx/generated/`.

| Linux | Windows |
|-------|---------|
| `sudo ./scripts/nginx-setup.sh` | `.\scripts\Nginx-Setup.ps1` (admin if writing under Program Files) |
| `--dry-run` / `--uninstall` | `-DryRun` / `-Uninstall` |
| `--install-pkg` | Install nginx yourself; set `NGINX_HOME` |

### Windows nginx + stream

Minecraft public proxy needs the **stream** module (TCP + UDP).  
Many **nginx.org Windows** zip builds **omit stream**.

Options that work:

1. **nginx build with stream** under e.g. `C:\nginx`, then:
   ```powershell
   $env:NGINX_HOME = "C:\nginx"
   .\scripts\Nginx-Setup.ps1
   ```
2. **WSL2** — install Linux nginx inside WSL and use `scripts/nginx-setup.sh` (edge on Windows host via WSL ports).
3. **No nginx** — bind YaPcore directly (`port=25565` in `config/server.properties`) for a simple public listen (no 25565→25566 split).

`Nginx-Setup.ps1` always writes generated configs; it only installs when `nginx.exe` is found and `-V` reports stream.

## Config / plugins layout

Windows start creates **directory junctions** (like Linux symlinks):

- `config\paper` → `paper-kernel\config`
- `paper-kernel\plugins` → `..\plugins` (when safe)

## MariaDB (shared YapDb + PlayerData)

Same Docker package as Linux — [MARIADB.md](MARIADB.md) / [YAPDB.md](YAPDB.md):

```powershell
.\scripts\windows\Start-MariaDB.ps1
.\scripts\windows\Configure-Db.ps1 -ServerId lobby
# or both YaPDB + playerdata:
.\scripts\windows\Configure-PlayerData.ps1 -ServerId lobby
.\scripts\windows\Stop-MariaDB.ps1
```

Release zip ships matching `start-mariadb.cmd` / `configure-db.cmd` / `configure-playerdata.cmd`.

## Docs

- [NGINX_AND_LOCALHOST.md](NGINX_AND_LOCALHOST.md)
- [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md)
- [MARIADB.md](MARIADB.md) · [PLAYERDATA.md](PLAYERDATA.md)
- [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md)
