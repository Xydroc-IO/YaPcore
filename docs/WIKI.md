# YaPcore Wiki

Operator documentation for running and configuring YaPcore. Start with
**[Quick Start](start/QUICK_START.md)** if you are new.

Browse by folder: [docs/README.md](README.md).

---

## Getting started — [`start/`](start/)

| Page | Description |
|------|-------------|
| [**Quick Start**](start/QUICK_START.md) | **10-minute setup** — release zip or source build |
| [RELEASES.md](start/RELEASES.md) | Downloadable zips, `assembleRelease`, version bumps |
| [RELEASE_NOTES.md](start/RELEASE_NOTES.md) | Version changelog and upgrade notes |
| [DEFAULTS.md](start/DEFAULTS.md) | First-boot configs + seed script |
| [SECRETS.md](start/SECRETS.md) | Passwords, tokens, webhooks (operator-owned) |
| [LICENSING.md](start/LICENSING.md) | GPLv3 (YaPcore) + third-party notices |
| [PRIVACY_POLICY.md](start/PRIVACY_POLICY.md) | Privacy (software + operator template) |
| [TERMS_OF_USE.md](start/TERMS_OF_USE.md) | Terms of use + operator template |
| [WINDOWS.md](start/WINDOWS.md) | Windows launchers and parity |
| [TESTING.md](start/TESTING.md) | Smoke, soak, bench scripts |

### Identity — [`overview/`](overview/)

| Page | Description |
|------|-------------|
| [WHAT_WE_ARE.md](overview/WHAT_WE_ARE.md) | What YaPcore is (and isn't) |
| [PLAIN_ENGLISH.md](overview/PLAIN_ENGLISH.md) | Non-technical overview |
| [FULL_RUNDOWN.md](overview/FULL_RUNDOWN.md) | Complete product rundown |
| [PROJECT_STATUS.md](overview/PROJECT_STATUS.md) | Done / partial / remaining |
| [COMPLETION_BACKLOG.md](overview/COMPLETION_BACKLOG.md) | Tier 1–4 checklist |
| [COMPARE_ECOSYSTEM.md](overview/COMPARE_ECOSYSTEM.md) | vs Paper, Purpur, Folia, … |
| [ROADMAP_COMPLETION_PHASES.md](overview/ROADMAP_COMPLETION_PHASES.md) | Completion roadmap |

---

## Daily operations — [`ops/`](ops/)

| Page | Description |
|------|-------------|
| [COMMANDS.md](ops/COMMANDS.md) | Console + in-game commands |
| [PERMISSIONS.md](ops/PERMISSIONS.md) | Permission nodes, rank ladder, YaPPerms |
| [ADMIN_MENU.md](ops/ADMIN_MENU.md) | In-game staff super menu (`/yapadmin`) |
| [TEBEX.md](ops/TEBEX.md) | Tebex / store → Hub console commands |
| [GRIM.md](ops/GRIM.md) | Grim AC (optional fetch) vs YaPGuard |
| [ANTICHEAT.md](ops/ANTICHEAT.md) | AC strategy — regions vs guard vs Grim |
| [WEB_DASHBOARD.md](ops/WEB_DASHBOARD.md) | Browser control panel (`:8080`) |
| [TUNE.md](ops/TUNE.md) | Config hub + gameplay knobs |

---

## Network & clients — [`network/`](network/)

| Page | Description |
|------|-------------|
| [NETWORKING.md](network/NETWORKING.md) | Ports, domains, boot banner |
| [CLIENTS_AND_PACKS.md](network/CLIENTS_AND_PACKS.md) | Java + Bedrock, resource packs |
| [VANILLA_CLIENTS.md](network/VANILLA_CLIENTS.md) | Java Edition join + version bands |
| [CROSSPLAY.md](network/CROSSPLAY.md) | Dual-stack Java + Bedrock |
| [NGINX_AND_LOCALHOST.md](network/NGINX_AND_LOCALHOST.md) | Same-PC joins + nginx edge |
| [CLOUDFLARE_AND_NGINX.md](network/CLOUDFLARE_AND_NGINX.md) | Public DNS + TLS front |
| [YAP_LINK.md](network/YAP_LINK.md) | Multi-backend native proxy |
| [YAP_LINK_NATIVE.md](network/YAP_LINK_NATIVE.md) | Velocity-class parity matrix |
| [VELOCITY.md](network/VELOCITY.md) | Optional stock Velocity stand-in |
| [EDGE_HARDEN.md](network/EDGE_HARDEN.md) | Edge hardening |
| [EDGE_RATE_LIMIT.md](network/EDGE_RATE_LIMIT.md) | Edge rate limits |

### Protocol / Via / Geyser — [`protocol/`](protocol/)

| Page | Description |
|------|-------------|
| [PHASE4_PROTOCOL.md](protocol/PHASE4_PROTOCOL.md) | Phase 4 Via* + Geyser parity |
| [VIA_GEYSER_PARITY.md](protocol/VIA_GEYSER_PARITY.md) | Feature checklist |
| [PROTOCOL_DUMPS.md](protocol/PROTOCOL_DUMPS.md) | Add next Mojang protocol dump |
| [VIA_BACKWARDS_LIMITATIONS.md](protocol/VIA_BACKWARDS_LIMITATIONS.md) | Honesty notes |
| [XBOX_RETAIL_CAPTURE.md](protocol/XBOX_RETAIL_CAPTURE.md) | Optional Xbox JWT capture |

---

## Plugins & data

### Plugins — [`plugins/`](plugins/)

| Page | Description |
|------|-------------|
| [PLUGINS.md](plugins/PLUGINS.md) | Plugin types, folders, load order |
| [PLUGIN_COMPAT.md](plugins/PLUGIN_COMPAT.md) | Folia vs Paper paths |
| [PLUGIN_BACKCOMPAT.md](plugins/PLUGIN_BACKCOMPAT.md) | 1.20–1.21 jars on 26.2 |
| [PLUGIN_COMPAT_MATRIX.md](plugins/PLUGIN_COMPAT_MATRIX.md) | Common plugin matrix |
| [MODULES_AND_API.md](plugins/MODULES_AND_API.md) | Fine-tune modules + API jars |
| [PLACEHOLDERAPI.md](plugins/PLACEHOLDERAPI.md) | Built-in PlaceholderAPI |
| [PREGEN.md](plugins/PREGEN.md) | Chunk pre-generator |
| [VEHICLES.md](plugins/VEHICLES.md) | YaP Vehicles |
| [STACKER.md](plugins/STACKER.md) | Mob/item stacker |
| [YAPWORLD.md](plugins/YAPWORLD.md) | World / selection / edit |
| [LAGGUARD.md](plugins/LAGGUARD.md) | Lag guard |
| [PAPER_API_COVERAGE.md](plugins/PAPER_API_COVERAGE.md) | Paper API coverage notes |
| [BRIGADIER_NMS_EVENTS.md](plugins/BRIGADIER_NMS_EVENTS.md) | Brigadier / NMS / events |

### Data — [`data/`](data/)

| Page | Description |
|------|-------------|
| [YAPDB.md](data/YAPDB.md) | Shared MariaDB pool |
| [MARIADB.md](data/MARIADB.md) | Docker MariaDB setup |
| [PLAYERDATA.md](data/PLAYERDATA.md) | Cross-server player data |

### MMO gameplay (opt-in) — [`mmo/`](mmo/)

| Page | Description |
|------|-------------|
| [MMO_PHASES.md](mmo/MMO_PHASES.md) | M0–M7 plan + smoke scripts |
| [MMO_SKILLS.md](mmo/MMO_SKILLS.md) | Skills, XP, `/skills` GUI |
| [MMO_COMBAT.md](mmo/MMO_COMBAT.md) | Custom combat system |
| [MMO_ABILITIES.md](mmo/MMO_ABILITIES.md) | Combat abilities |
| [MMO_BEDROCK_UI.md](mmo/MMO_BEDROCK_UI.md) | Bedrock MMO forms |
| [MMO_CONTENT.md](mmo/MMO_CONTENT.md) | Quests, bosses, recipes |
| [MMO_QUESTS.md](mmo/MMO_QUESTS.md) | 100-quest progression compendium |
| [MMO_MECHANICS.md](mmo/MMO_MECHANICS.md) | Mechanics notes |
| [MMO_RS_SKILLS.md](mmo/MMO_RS_SKILLS.md) | RS-style skills detail |

### Other gameplay — [`gameplay/`](gameplay/)

| Page | Description |
|------|-------------|
| [FACTIONS.md](gameplay/FACTIONS.md) | Factions |
| [GUILDS.md](gameplay/GUILDS.md) | Guilds |
| [REGIONS.md](gameplay/REGIONS.md) | Regions / claims |

---

## Architecture & Folia

### YaP-Folia — [`folia/`](folia/)

| Page | Description |
|------|-------------|
| [FOLIA_FORK.md](folia/FOLIA_FORK.md) | YaP-Folia product fork |
| [YAP_SCHED.md](folia/YAP_SCHED.md) | Folia-safe scheduler helper |
| [FOLIA_SCHED_COMPAT.md](folia/FOLIA_SCHED_COMPAT.md) | Scheduler compatibility |
| [FOLIA_TELEPORT_TRANSACTIONS.md](folia/FOLIA_TELEPORT_TRANSACTIONS.md) | Cross-region teleport |
| [YAP_FOLIA_SOAK.md](folia/YAP_FOLIA_SOAK.md) | Soak profiles |
| [FOLIA_FORK_CI.md](folia/FOLIA_FORK_CI.md) | Fork CI |
| [FOLIA_FORKS_COMPARE.md](folia/FOLIA_FORKS_COMPARE.md) | Fork comparison |
| [FOLIA_PLUGIN_COMPAT_MATRIX.md](folia/FOLIA_PLUGIN_COMPAT_MATRIX.md) | Plugin compat matrix |

### Performance — [`performance/`](performance/)

| Page | Description |
|------|-------------|
| [YAPENGINE_16THREAD.md](performance/YAPENGINE_16THREAD.md) | Chassis thread map |
| [BENCH_VS_FOLIA.md](performance/BENCH_VS_FOLIA.md) | MSPT product gate |
| [PERF_AND_LAYOUT.md](performance/PERF_AND_LAYOUT.md) | Performance layout |
| [ZGC_NUMA.md](performance/ZGC_NUMA.md) | Generational ZGC + NUMA |

### Whitepaper — [`whitepaper/`](whitepaper/)

| Page | Description |
|------|-------------|
| [YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) | Technical architecture |
| [YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) | Plain-English whitepaper |

---

## Contributing & legal

| Page | Description |
|------|-------------|
| [LICENSING.md](start/LICENSING.md) | **GPLv3** + third-party notices |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | How to contribute |
| [SECURITY.md](../SECURITY.md) | Security reporting |
