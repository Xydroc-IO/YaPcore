# YaPcore documentation

![YaPcore](../branding/yapcore-banner.png)

Operator docs for install, configuration, plugins, and gameplay features.
**Markdown is the source of truth** — do not commit PDFs or office exports (`docs/pdf/` is gitignored).

| Next step | Doc |
|-----------|-----|
| First boot | [Quick Start](start/QUICK_START.md) |
| Full index | [Wiki](WIKI.md) |
| Architecture | [Whitepaper](whitepaper/YAPCORE_WHITEPAPER.md) |
| Releases & assets | [RELEASES.md](start/RELEASES.md) |

---

## By topic

| Folder | Contents |
|--------|----------|
| [**start/**](start/) | Quick start, releases, secrets, licensing, Windows |
| [**ops/**](ops/) | Commands, permissions, dashboard, tune, [production ready](ops/PRODUCTION_READY.md), [code elegance](ops/CODE_ELEGANCE_FOLLOWUP.md) |
| [**network/**](network/) | Ports, crossplay, nginx, YaP Link, edge hardening |
| [**plugins/**](plugins/) | Plugin list, modules, vehicles, stacker, compat |
| [**data/**](data/) | YaPDB, MariaDB, Postgres, SQLite, playerdata |
| [**mmo/**](mmo/) | Opt-in MMO gameplay |
| [**gameplay/**](gameplay/) | Guilds, factions, regions |
| [**folia/**](folia/) | YaP-Folia soak, cite, Canvas parity |
| [**whitepaper/**](whitepaper/) | Technical architecture |

---

## Essential docs

| Document | When to read |
|----------|--------------|
| [QUICK_START.md](start/QUICK_START.md) | Install and first boot |
| [DEFAULTS.md](start/DEFAULTS.md) | First-boot configs |
| [SECRETS.md](start/SECRETS.md) | Passwords and tokens |
| [PLUGINS.md](plugins/PLUGINS.md) | Shipped plugins |
| [MODULES_AND_API.md](plugins/MODULES_AND_API.md) | Modules and API |
| [YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) | Architecture deep dive |
| [CLIENTS_AND_PACKS.md](network/CLIENTS_AND_PACKS.md) | Resource packs + optional Fabric clients |

---

## Contributing

[CONTRIBUTING.md](../CONTRIBUTING.md) · [CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md) · [SECURITY.md](../SECURITY.md)

Optional local PDF print (gitignored): `./scripts/export-docs-pdf.sh`
