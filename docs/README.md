# YaPcore documentation

![YaPcore](../branding/yapcore-banner.png)

**New here?** Start with [**Quick Start**](start/QUICK_START.md), then browse the [**Wiki**](WIKI.md).

Docs are grouped by topic:

| Folder | What’s in it |
|--------|----------------|
| [**start/**](start/) | Quick start, defaults, releases, licensing, legal, Windows, testing |
| [**overview/**](overview/) | What we are, plain English, ecosystem compare, roadmap |
| [**ops/**](ops/) | Commands, permissions, web dashboard, tune |
| [**network/**](network/) | Ports, clients, crossplay, nginx, Cloudflare, YaP Link, Velocity |
| [**protocol/**](protocol/) | Via/Geyser parity, protocol dumps, Xbox capture |
| [**plugins/**](plugins/) | Plugin API, compat, pregen, stacker, vehicles, world edit |
| [**data/**](data/) | YaPDB, MariaDB, playerdata |
| [**mmo/**](mmo/) | Opt-in MMO (skills, combat, quests, abilities) |
| [**gameplay/**](gameplay/) | Factions, guilds, regions |
| [**folia/**](folia/) | YaP-Folia fork, scheduler, soak, teleport |
| [**performance/**](performance/) | Bench gates, ZGC/NUMA, YapEngine threads |
| [**whitepaper/**](whitepaper/) | Technical + plain-English whitepapers |
| [**pdf/**](pdf/) | Printable PDFs |

---

## Start here

| Document | Description |
|----------|-------------|
| [**Quick Start**](start/QUICK_START.md) | **10-minute setup** — release zip or source |
| [**Defaults**](start/DEFAULTS.md) | Shipped first-boot configs — `config/defaults/` |
| [**Secrets**](start/SECRETS.md) | Passwords, tokens, webhooks (operator-owned) |
| [**Wiki**](WIKI.md) | Full operator doc index |
| [**Releases**](start/RELEASES.md) | Downloadable zips, build commands |
| [**Licensing**](start/LICENSING.md) | GPLv3 + third-party notices |
| [**Privacy Policy**](start/PRIVACY_POLICY.md) | Data handling (software + operator guidance) |
| [**Terms of Use**](start/TERMS_OF_USE.md) | Acceptable use, disclaimers, operator duties |

## Overview

| Document | Description |
|----------|-------------|
| [**PROJECT_STATUS**](overview/PROJECT_STATUS.md) | **What's done, what's partial, what's left** (operator rundown) |
| [PLAIN_ENGLISH](overview/PLAIN_ENGLISH.md) | Non-tech overview |
| [FULL_RUNDOWN](overview/FULL_RUNDOWN.md) | Full product rundown |
| [WHAT_WE_ARE](overview/WHAT_WE_ARE.md) | Short identity |
| [COMPARE_ECOSYSTEM](overview/COMPARE_ECOSYSTEM.md) | vs Paper, Purpur, Folia, … |
| [Whitepaper](whitepaper/YAPCORE_WHITEPAPER.md) | Technical architecture |
| [Whitepaper (plain English)](whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) | Same story for non-tech readers |

## Ops & network

| Document | Description |
|----------|-------------|
| [COMMANDS](ops/COMMANDS.md) | Console + in-game commands |
| [PERMISSIONS](ops/PERMISSIONS.md) | Permission nodes + YaPPerms |
| [ADMIN_MENU](ops/ADMIN_MENU.md) | In-game staff super menu (`/yapadmin`) |
| [ANTICHEAT](ops/ANTICHEAT.md) | YaPGuard vs regions; optional Grim AC |
| [COMPLETION_BACKLOG](overview/COMPLETION_BACKLOG.md) | What is shipped vs still thin |
| [TEBEX](ops/TEBEX.md) | Store / Tebex → Hub console cmds |
| [WEB_DASHBOARD](ops/WEB_DASHBOARD.md) | Browser control (`:8080`) |
| [TUNE](ops/TUNE.md) | Central config hub |
| [NETWORKING](network/NETWORKING.md) | Domain / ports / boot banner |
| [YAP_LINK](network/YAP_LINK.md) | Native multi-backend proxy |
| [CROSSPLAY](network/CROSSPLAY.md) | Java + Bedrock dual-stack |

## Plugins, data & Folia

| Document | Description |
|----------|-------------|
| [PLUGINS](plugins/PLUGINS.md) | Plugin folders + load order |
| [PREGEN](plugins/PREGEN.md) | Chunk pre-generator |
| [VEHICLES](plugins/VEHICLES.md) | Vehicle API |
| [YAPWORLD](plugins/YAPWORLD.md) | World / selection / edit |
| [YAPDB](data/YAPDB.md) · [MARIADB](data/MARIADB.md) | Shared DB pool + Docker |
| [PLAYERDATA](data/PLAYERDATA.md) | Cross-server player data |
| [FOLIA_FORK](folia/FOLIA_FORK.md) | YaP-Folia product game jar |
| [BENCH_VS_FOLIA](performance/BENCH_VS_FOLIA.md) | MSPT product gate |

## MMO gameplay (opt-in)

`gradle installGameplayDefaults` or `-PyapGameplay=true`. Plan: [mmo/MMO_PHASES.md](mmo/MMO_PHASES.md).

| Page | Description |
|------|-------------|
| [MMO_PHASES](mmo/MMO_PHASES.md) | M0–M7 milestones |
| [MMO_QUESTS](mmo/MMO_QUESTS.md) | 100-quest compendium |
| [MMO_SKILLS](mmo/MMO_SKILLS.md) | Skills |
| [MMO_COMBAT](mmo/MMO_COMBAT.md) | Custom combat |
| [MMO_ABILITIES](mmo/MMO_ABILITIES.md) | Combat abilities |
| [MMO_BEDROCK_UI](mmo/MMO_BEDROCK_UI.md) | Bedrock MMO UI |
| [MMO_CONTENT](mmo/MMO_CONTENT.md) | Quests / bosses / recipes |

## Branding

Official marks live in [`branding/`](../branding/README.md) (icon, mark, banner).

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) and [SECURITY.md](../SECURITY.md).
