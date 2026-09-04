# YaPcore Wiki

Operator documentation for install, configuration, plugins, and gameplay.

Browse by folder: [docs/README.md](README.md).

---

## Getting started — [`start/`](start/)

| Page | Description |
|------|-------------|
| [**QUICK_START.md**](start/QUICK_START.md) | Install and first boot |
| [RELEASES.md](start/RELEASES.md) | Release zips and build |
| [RELEASE_NOTES.md](start/RELEASE_NOTES.md) | Changelog |
| [DEFAULTS.md](start/DEFAULTS.md) | First-boot configs |
| [SECRETS.md](start/SECRETS.md) | Passwords and tokens |
| [WINDOWS.md](start/WINDOWS.md) | Windows launchers |
| [LICENSING.md](start/LICENSING.md) | License and legal |

---

## Daily operations — [`ops/`](ops/)

| Page | Description |
|------|-------------|
| [COMMANDS.md](ops/COMMANDS.md) | Console and in-game commands |
| [PERMISSIONS.md](ops/PERMISSIONS.md) | Permission nodes |
| [WEB_DASHBOARD.md](ops/WEB_DASHBOARD.md) | Browser panel (`:8080`) |
| [TUNE.md](ops/TUNE.md) | Config hub |
| [CODE_ELEGANCE_FOLLOWUP.md](ops/CODE_ELEGANCE_FOLLOWUP.md) | Post–500-line splits: DB, packages, tests |
| [GRIM.md](ops/GRIM.md) | Optional Grim AC |
| [ANTICHEAT.md](ops/ANTICHEAT.md) | AC strategy |
| [ADMIN_MENU.md](ops/ADMIN_MENU.md) | In-game admin menu |
| [DISCORD_RELAY.md](ops/DISCORD_RELAY.md) | Discord webhooks |
| [TEBEX.md](ops/TEBEX.md) | Tebex store integration |

---

## Network — [`network/`](network/)

| Page | Description |
|------|-------------|
| [NETWORKING.md](network/NETWORKING.md) | Ports and domains |
| [CROSSPLAY.md](network/CROSSPLAY.md) | Java + Bedrock |
| [YAP_LINK.md](network/YAP_LINK.md) | Multi-backend proxy |
| [YAP_LINK_NATIVE.md](network/YAP_LINK_NATIVE.md) | Native Link setup |
| [VELOCITY.md](network/VELOCITY.md) | Velocity forwarding |
| [CLIENTS_AND_PACKS.md](network/CLIENTS_AND_PACKS.md) | Clients and resource packs |
| [VANILLA_CLIENTS.md](network/VANILLA_CLIENTS.md) | Vanilla client support |
| [EDGE_HARDEN.md](network/EDGE_HARDEN.md) | Public edge hardening |
| [EDGE_RATE_LIMIT.md](network/EDGE_RATE_LIMIT.md) | Rate limits |
| [NGINX_AND_LOCALHOST.md](network/NGINX_AND_LOCALHOST.md) | nginx and localhost |
| [CLOUDFLARE_AND_NGINX.md](network/CLOUDFLARE_AND_NGINX.md) | DNS and Cloudflare |

---

## Plugins, data, gameplay, MMO

| Area | Index |
|------|-------|
| Plugins | [PLUGINS.md](plugins/PLUGINS.md) · [MODULES_AND_API.md](plugins/MODULES_AND_API.md) |
| Data | [YAPDB.md](data/YAPDB.md) · [MARIADB.md](data/MARIADB.md) · [POSTGRES.md](data/POSTGRES.md) · [SQLITE.md](data/SQLITE.md) · [PLAYERDATA.md](data/PLAYERDATA.md) |
| Gameplay | [GUILDS.md](gameplay/GUILDS.md) · [FACTIONS.md](gameplay/FACTIONS.md) · [REGIONS.md](gameplay/REGIONS.md) |
| MMO (opt-in) | [MMO_PHASES.md](mmo/MMO_PHASES.md) · [MMO_CONTENT.md](mmo/MMO_CONTENT.md) |

---

## Architecture — [`whitepaper/`](whitepaper/) · Folia — [`folia/`](folia/)

| Page | Description |
|------|-------------|
| [YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) | Technical architecture |
| [YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) | Plain-English summary |
| [YAP_FOLIA_SOAK.md](folia/YAP_FOLIA_SOAK.md) | Soak gates and knob profiles |
| [REAL_GAINS.md](folia/REAL_GAINS.md) | Citeable Folia gains |
| [CANVAS_PARITY.md](folia/CANVAS_PARITY.md) | vs Canvas |

Docs are **Markdown only** in git. Optional local PDFs: `./scripts/export-docs-pdf.sh` (gitignored).
