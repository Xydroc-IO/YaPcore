# YaPcore scripts

Install, setup, and lifecycle helpers for operators.

## Server lifecycle

| Script | Purpose |
|--------|---------|
| `start.sh` / `stop.sh` / `status.sh` | Server lifecycle |
| `gui.sh` / `start-prod.sh` | Swing panel / production launch |
| `start-yap-link.sh` | Start YaP Link proxy |
| `yapctl` | CLI helper |

## First boot and setup

| Script | Purpose |
|--------|---------|
| `seed-defaults.sh` | First-boot configs |
| `apply-production-profile.sh` | Public production keys |
| `setup-velocity-forwarding.sh` | Velocity forwarding secret |
| `nginx-setup.sh` | nginx edge template install |
| `db/*.sh` | MariaDB + JDBC setup |
| `windows/*.ps1` | Windows equivalents |

## Build YaP-Folia (from source)

| Script | Purpose |
|--------|---------|
| `build-yap-folia.sh` | Build `lib/yap-folia-26.2.jar` |
| `vendor-folia.sh` · `folia-patch.sh` | Vendor + patches |
| `fetch-folia.sh` | Stock Folia fallback |

## Optional plugins and packs

| Script | Purpose |
|--------|---------|
| `fetch-tebex.sh` · `fetch-grim.sh` · `grim-ac.sh` | Optional Tebex / Grim AC |
| `build-default-resourcepack.sh` · `fetch-faithful-64x.sh` | Default resource pack |

See [docs/start/QUICK_START.md](../docs/start/QUICK_START.md) and [docs/start/WINDOWS.md](../docs/start/WINDOWS.md).
