# YaPcore documentation

![YaPcore](../branding/yapcore-banner.png)

**New here?** Start with [**Quick Start**](QUICK_START.md), then browse the [**Wiki**](WIKI.md).

| Document | Description |
|----------|-------------|
| [**Quick Start**](QUICK_START.md) | **10-minute setup** — release zip or source |
| [**Wiki**](WIKI.md) | Full operator doc index |
| [**Licensing**](LICENSING.md) | MIT + third-party (Folia, Mojang EULA, packs) |
| [**Commands**](COMMANDS.md) | Console + in-game command reference |
| [**Permissions**](PERMISSIONS.md) | Permission nodes + YaPPerms rank ladder |
| [**Releases**](RELEASES.md) | Downloadable zips, build commands |
| [**Whitepaper (plain English)**](whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) | Same whitepaper story for non-tech readers |
| [**PLAIN_ENGLISH**](PLAIN_ENGLISH.md) | **Non-tech overview** — what we are / what we’re into, in plain English |
| [**FULL_RUNDOWN**](FULL_RUNDOWN.md) | **Full “what we are / what we do” rundown** |
| [WHAT_WE_ARE](WHAT_WE_ARE.md) | Short identity / are we better Paper? |
| [**COMPARE_ECOSYSTEM**](COMPARE_ECOSYSTEM.md) | **YaPcore vs Paper, Purpur, Pufferfish, Leaf, Folia** |
| [**PDFs**](pdf/) | Printable PDFs of the identity + whitepaper + vehicles + web dashboard docs |
| [VELOCITY](VELOCITY.md) | **Velocity proxy** — optional stand-in; Folia/Paper backends with modern forwarding |
| [**YAP_LINK_NATIVE**](YAP_LINK_NATIVE.md) | **YaP Link** phased Velocity-class parity matrix |
| [**YAP_LINK**](YAP_LINK.md) | **YaP Link** — first-party native network proxy |
| [**YAP_SCHED**](YAP_SCHED.md) | **YapSched** — Folia-safe scheduler helper for YaP plugins |
| [**PLAYERDATA**](PLAYERDATA.md) | **Cross-server inv / claims / GUIs** — `yap-playerdata` |
| [**YAPDB**](YAPDB.md) | **Shared MariaDB Hikari pool** — `yap-db` for any SQL plugin |
| [**MARIADB**](MARIADB.md) | **Easy MariaDB** — Docker package for Linux + Windows (single & multi) |
| [YAPENGINE_16THREAD](YAPENGINE_16THREAD.md) | **YapEngine chassis** — edge/I/O thread roles (v2.0); Folia owns game tick |
| [BENCH_VS_FOLIA](BENCH_VS_FOLIA.md) | **MSPT product gate** — stock Folia vs YaP Folia chassis |
| [PERF_AND_LAYOUT](PERF_AND_LAYOUT.md) | Performance layout & folder discipline |
| [ZGC_NUMA](ZGC_NUMA.md) | Generational ZGC + NUMA launch flags |
| [NETWORKING](NETWORKING.md) | Domain / public ports / boot banner (`yapcoremc.yaplabs.us`) |
| [VANILLA_CLIENTS](VANILLA_CLIENTS.md) | Java Edition join + built-in multi-version bands |
| [PHASE4_PROTOCOL](PHASE4_PROTOCOL.md) | **Phase 4:** full Via\* + Geyser parity in first-party code |
| [**VIA_GEYSER_PARITY**](VIA_GEYSER_PARITY.md) | **Feature checklist** — ViaVersion / Backwards / Rewind / Geyser / Floodgate rows + gates |
| [**PROTOCOL_DUMPS**](PROTOCOL_DUMPS.md) | **P4.10** — add next Mojang protocol dump (`index.json` + generator) |
| [**VIA_BACKWARDS_LIMITATIONS**](VIA_BACKWARDS_LIMITATIONS.md) | **P4.11** — honesty notes (smithing, sounds, placeholders, claim language) |
| [XBOX_RETAIL_CAPTURE](XBOX_RETAIL_CAPTURE.md) | Optional live Mojang Xbox JWT capture → soak |
| [CLIENTS_AND_PACKS](CLIENTS_AND_PACKS.md) | Multi-version matrix, dual-stack, resource packs |
| [NGINX_AND_LOCALHOST](NGINX_AND_LOCALHOST.md) | Same-PC joins + nginx |
| [**WINDOWS**](WINDOWS.md) | **Windows parity** — launchers, Paperclip, nginx stream |
| [CLOUDFLARE_AND_NGINX](CLOUDFLARE_AND_NGINX.md) | **yapcoremc.yaplabs.us** + Cloudflare DNS / SSL |
| [PLUGINS](PLUGINS.md) | Plugin API — Folia/Paper + YaP jars in one `plugins/` folder |
| [PLUGIN_COMPAT](PLUGIN_COMPAT.md) | Folia product path + unified `plugins/`; Paper path legacy |
| [**PLUGIN_BACKCOMPAT**](PLUGIN_BACKCOMPAT.md) | **1.20–1.21 jars on 26.2** — Tier A + light ASM rewrite |
| [**PREGEN**](PREGEN.md) | **Built-in chunk pregen** (Chunky-class) — `/yappregen` |
| [**STACKER**](STACKER.md) | **PDC mob/item/spawner stacker** (no NMS) — `/yapstacker` |
| [**PLACEHOLDERAPI**](PLACEHOLDERAPI.md) | **Built-in clip-compatible PlaceholderAPI** (no HelpChat jar) |
| [**PAPER_API_COVERAGE**](PAPER_API_COVERAGE.md) | Folia APIs on Folia path; complete Paper API on legacy Paper path |
| [**WEB_DASHBOARD**](WEB_DASHBOARD.md) | **Headless browser control** — start/stop, console, packs, vehicles |
| [**VEHICLES**](VEHICLES.md) | **Real vehicle API** (not minecarts) — Folia/Paper plugin + author guide |
| [TUNE](TUNE.md) | **Central config hub** + Purpur-class gameplay knobs module |
| [COMMANDS](COMMANDS.md) | Vanilla / Paper / plugin commands from GUI + in-game |
| [**PERMISSIONS**](PERMISSIONS.md) | **Permission nodes** + YaPPerms rank ladder |
| [MODULES_AND_API](MODULES_AND_API.md) | Modules + API coverage + pools |
| [BRIGADIER_NMS_EVENTS](BRIGADIER_NMS_EVENTS.md) | Brigadier, Craft/NMS, Paper event catalog |
| [TESTING](TESTING.md) | Test lab, soak, Fray, JCStress |
| [**RELEASES**](RELEASES.md) | **Linux/Windows boxes + standalone suite zips** (v1.0.0.0) |

## MMO gameplay (opt-in)

`gradle installGameplayDefaults` or `-PyapGameplay=true`. Plan: [MMO_PHASES.md](MMO_PHASES.md).

| Page | Description |
|------|-------------|
| [MMO_PHASES.md](MMO_PHASES.md) | M0–M7 milestones + smoke checklist |
| [MMO_SKILLS.md](MMO_SKILLS.md) | 13 RS skills |
| [MMO_COMBAT.md](MMO_COMBAT.md) | Custom combat |
| [MMO_ABILITIES.md](MMO_ABILITIES.md) | 233 combat abilities |
| [MMO_BEDROCK_UI.md](MMO_BEDROCK_UI.md) | Bedrock MMO UI |

## Branding

Official marks live in [`branding/`](../branding/README.md) (icon, mark, banner).

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) and [SECURITY.md](../SECURITY.md).
