# YaPcore documentation

![YaPcore](../branding/yapcore-banner.png)

Enterprise operator and engineering documentation for YaPcore.
**Markdown under `docs/` is the source of truth** — do not commit PDFs or office exports
(`docs/pdf/` is gitignored; regenerate locally with `./scripts/export-docs-pdf.sh`).

| Next step | Doc |
|-----------|-----|
| First boot | [Quick Start](start/QUICK_START.md) |
| Full index | [Wiki](WIKI.md) |
| Architecture | [Whitepaper](whitepaper/YAPCORE_WHITEPAPER.md) |
| Releases & assets | [RELEASES.md](start/RELEASES.md) |
| AI disclosure | [AI_TRANSPARENCY.md](start/AI_TRANSPARENCY.md) |

---

## By topic

| Folder | Contents |
|--------|----------|
| [**start/**](start/) | Quick start, releases, secrets, licensing, [AI transparency](start/AI_TRANSPARENCY.md), Windows |
| [**ops/**](ops/) | Commands, permissions, dashboard, tune, [production ready](ops/PRODUCTION_READY.md), [code elegance](ops/CODE_ELEGANCE_FOLLOWUP.md) |
| [**network/**](network/) | Ports, crossplay, nginx, YaP Link (native Bedrock), edge hardening, clients & packs |
| [**product/**](product/) | [Bedrock-feel parity](product/BEDROCK_FEEL_PARITY.md) contract + [matrix](product/BEDROCK_FEEL_MATRIX.md) |
| [**geyser-join-reference/**](geyser-join-reference/) | Native join port notes (`YapGeyserSession` / Link Bedrock) |
| [**plugins/**](plugins/) | Plugin list, modules, skills, stacker, [items](plugins/YAPITEMS.md), compat |
| [**data/**](data/) | YaPDB, MariaDB, Postgres, SQLite, playerdata |
| [**gameplay/**](gameplay/) | Factions, conquest, regions |
| [**folia/**](folia/) | YaP-Folia soak, cite, Canvas parity |
| [**whitepaper/**](whitepaper/) | Technical architecture |

---

## Essential docs

| Document | When to read |
|----------|--------------|
| [QUICK_START.md](start/QUICK_START.md) | Install and first boot |
| [DEFAULTS.md](start/DEFAULTS.md) | First-boot configs |
| [SECRETS.md](start/SECRETS.md) | Passwords and tokens |
| [AI_TRANSPARENCY.md](start/AI_TRANSPARENCY.md) | Disclosure of AI-assisted development |
| [LICENSING.md](start/LICENSING.md) | GPLv3 + third-party notices |
| [PLUGINS.md](plugins/PLUGINS.md) | Shipped plugins |
| [MODULES_AND_API.md](plugins/MODULES_AND_API.md) | Modules and API |
| [CROSSPLAY.md](network/CROSSPLAY.md) | Java + Bedrock paths |
| [BEDROCK_FEEL_PARITY.md](product/BEDROCK_FEEL_PARITY.md) | Port-don’t-recreate parity contract |
| [YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) | Architecture deep dive |
| [CLIENTS_AND_PACKS.md](network/CLIENTS_AND_PACKS.md) | Resource packs + optional Fabric clients |

---

## Contributing

[CONTRIBUTING.md](../CONTRIBUTING.md) · [CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md) · [SECURITY.md](../SECURITY.md) · [AI_TRANSPARENCY.md](start/AI_TRANSPARENCY.md)

Optional local PDF print (gitignored): `./scripts/export-docs-pdf.sh`
