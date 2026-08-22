# Windows host guide — parity with Linux

YaPcore is meant to run **the same product** on Windows and Linux.
Release trees: `build/dist/yapcore-release/linux/` and `…/windows/`.

## Requirements (both)

| Need | Notes |
|------|--------|
| **JDK 25+** | Folia 26.2 |
| **Git** | Optional (checkout / contrib) |

## Launch

| Linux | Windows |
|-------|---------|
| `./start.sh --fg` | `start.cmd -Fg` |
| `./gui.sh` | `gui.cmd` |
| `./stop.sh` / `./status.sh` | `stop.cmd` / `status.cmd` |
| `./start-prod.sh` | `start-prod.cmd` |

Product path is **Folia** (`game-authority=folia`). YaP stays at the install root;
the Folia child JVM uses `folia-kernel/`. Fetch with `./scripts/fetch-folia.sh`
(Linux) before first boot if you want the jar pre-cached under `lib/`.

> **Retired:** YaP Paperclip / Phase 3 vendor scripts (`Vendor-Paper.ps1`,
> `Build-Vendor-Paper.ps1`, `vendor-paper.sh`, `apply-yap-paper-hooks.sh`) are
> removed. Do not expect Paperclip build steps on Windows or Linux.

## nginx edge — both platforms

Templates: `deploy/nginx/*.template` (shared).  
Generated: `deploy/nginx/generated/`.

```powershell
.\scripts\Nginx-Setup.ps1          # or nginx-setup.cmd in a release tree
```

Linux: `./scripts/nginx-setup.sh`.

## MariaDB

```powershell
.\scripts\Start-MariaDB.ps1
.\scripts\Configure-Db.ps1
.\scripts\Configure-PlayerData.ps1
```

See [MARIADB.md](MARIADB.md) · [PLAYERDATA.md](PLAYERDATA.md).

## Release packaging

`gradle assembleRelease` builds `build/dist/yapcore-release/windows/` with
`scripts/*.ps1` and root `*.cmd` wrappers. No Paperclip wrappers are shipped.
