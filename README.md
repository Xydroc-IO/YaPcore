# YaPcore

<p align="center">
  <img src="branding/yapcore-banner.png" alt="YaPcore banner" width="100%"/>
</p>

<p align="center">
  <img src="branding/yapcore-mark.png" alt="YaPcore mark" width="160"/>
</p>

**16-thread** Minecraft-class server engine (YapLabs **YapEngine**) for Linux —
**Folia** as the default game, Folia-aware plugins, YaP plugins & fine-tune modules,
**YaP Link** (complete Velocity fork), Java+Bedrock dual-stack, resource packs,
control GUI, and deep crash diagnostics.

> Not affiliated with Mojang / Microsoft. See [LICENSE](LICENSE).

| | |
|--|--|
| **What we are** | [docs/WHAT_WE_ARE.md](docs/WHAT_WE_ARE.md) · [plain English](docs/PLAIN_ENGLISH.md) |
| **Full rundown** | [docs/FULL_RUNDOWN.md](docs/FULL_RUNDOWN.md) |
| **Whitepaper** | [docs/whitepaper/YAPCORE_WHITEPAPER.md](docs/whitepaper/YAPCORE_WHITEPAPER.md) · [plain English](docs/whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) |
| **Docs index** | [docs/README.md](docs/README.md) |
| **Branding** | [branding/](branding/README.md) |

## Architecture (16 threads)

| Threads | Component |
|---------|-----------|
| 1 | Controller (watchdog) |
| 2 | Traffic Cop + SequenceToken |
| 3–6 | Spatial game cores (chassis; Phase 3 tick only on legacy Paper path) |
| 7–8 | Chunk Sync DLM + Boundary Arbitrator |
| 9 | Compatibility Bridge |
| 10–11 | High-Speed UI sandbox |
| 12–15 | Heavy I/O sandbox |
| 16 | Telemetry / async worker |

See [docs/YAPENGINE_16THREAD.md](docs/YAPENGINE_16THREAD.md).

**Game path:** **Folia** authority (default) · YapEngine chassis · **YaP Link** (full Velocity fork) for
multi-backend networks · Phase 3 Paper spatial **retired as product default**
(opt-in benches only; Folia path has no Phase 3 spatial tick) · fair highpop cite
**~100 active bots** (250 keepalive = HOLD-ONLY) · Phase 4 dual-stack (join green;
play depth deepening) —
[docs/PAPER_YAPENGINE_PORT.md](docs/PAPER_YAPENGINE_PORT.md) ·
[docs/YAP_LINK.md](docs/YAP_LINK.md) · [docs/BENCH_VS_PAPER.md](docs/BENCH_VS_PAPER.md).  
Default: `game-authority=folia`, `folia-embed=true`; Phase 3 flags **off**.  
Product target: **high-pop / heavy load** (not empty lobbies).

## Quick start

```bash
git clone https://github.com/<you>/YaPcore.git
cd YaPcore
chmod +x scripts/*.sh
# Folia / Paper 26.2 needs Java 25+
./scripts/fetch-folia.sh          # Folia product path
# Legacy Paper + Phase 3 benches only:
# ./scripts/vendor-paper.sh && ./scripts/build-vendor-paper.sh
gradle shadowJar          # jar + default plugins/packs; release → build/dist/yapcore-release/
./scripts/gui.sh          # control panel
# or
./scripts/start.sh --fg   # headless (cds into folia-kernel by default)
# YaP Link (optional multi-backend): see docs/YAP_LINK.md
# Release tree: cd build/dist/yapcore-release/linux && ./start.sh --fg
# Windows release: build\dist\yapcore-release\windows\start.cmd -Fg
./scripts/stop.sh
```

Requires **Java 25+** for Folia/Paper 26.2 (project toolchain may still compile
main sources with JDK 21). The fat jar is built locally and is **not** committed.
Release packages include **linux/** and **windows/** trees with native launchers.

**Headless web dashboard** (default `:8080`): mirrors the control GUI in a browser —
start/stop, console, settings, plugins, packs, vehicles. See
[docs/WEB_DASHBOARD.md](docs/WEB_DASHBOARD.md).

## Dual-stack + resource packs

Java **and** Bedrock are enabled by default. **JE multi-version is built in**
(`ProtocolBand` / `ViaStyleRemapper` / `backwards-compatible=true`) — **full Via\*
parity** and **full Geyser parity** as Phase 4 DoD (own code). **Do not** install
ViaVersion / ViaBackwards / ViaRewind / Geyser. See [docs/PHASE4_PROTOCOL.md](docs/PHASE4_PROTOCOL.md).
Texture/resource packs in `resourcepacks/` download over the built-in HTTP host (default `:8081`).
Default: **`yapcore-default.zip`** (Faithful 64x + YaP Vehicles HD models) — built on
`gradle shadowJar` / `assembleRelease`; credit in `resourcepacks/CREDITS.md`.
Public edge: **`yapcoremc.yaplabs.us`** via nginx + Cloudflare — see
[docs/CLOUDFLARE_AND_NGINX.md](docs/CLOUDFLARE_AND_NGINX.md) and [docs/NETWORKING.md](docs/NETWORKING.md).
See [docs/CLIENTS_AND_PACKS.md](docs/CLIENTS_AND_PACKS.md).

**YaP Link:** first-party complete Velocity fork (forwarding, online-mode,
compression, transfers, Velocity plugins) —
[docs/YAP_LINK.md](docs/YAP_LINK.md). Stock Velocity remains a temporary stand-in
([docs/VELOCITY.md](docs/VELOCITY.md)). Folia backends: `velocity-enabled=true`.

## Plugins & modules

**One folder for jars:** drop Folia/Paper and YaP plugins into **`plugins/`**.
Product path is Folia (`folia-kernel/plugins` → symlink). Legacy Paper path uses
`paper-kernel/plugins` the same way. Fine-tune modules stay in `modules/`.

| Type | Folder | Manifest | Base class |
|------|--------|----------|------------|
| Folia / Paper | `plugins/` | `plugin.yml` | `JavaPlugin` |
| YaP plugin | `plugins/` | `yap.yml` | `YaPPlugin` |
| Fine-tune module | `modules/` | `module.yml` | `YaPModule` |

World/inventory → **SYNC** · DB/HTTP → **HEAVY** · menus → **UI**.  
See [docs/PLUGINS.md](docs/PLUGINS.md), [docs/PLUGIN_COMPAT.md](docs/PLUGIN_COMPAT.md),
[docs/PLUGIN_BACKCOMPAT.md](docs/PLUGIN_BACKCOMPAT.md),
[docs/PAPER_API_COVERAGE.md](docs/PAPER_API_COVERAGE.md), [docs/TUNE.md](docs/TUNE.md),
[docs/MODULES_AND_API.md](docs/MODULES_AND_API.md), [docs/VEHICLES.md](docs/VEHICLES.md),
[docs/STACKER.md](docs/STACKER.md), [docs/PREGEN.md](docs/PREGEN.md),
[docs/PLACEHOLDERAPI.md](docs/PLACEHOLDERAPI.md), [docs/PLAYERDATA.md](docs/PLAYERDATA.md),
[docs/YAPDB.md](docs/YAPDB.md), [docs/MARIADB.md](docs/MARIADB.md), and
[docs/PERMISSIONS.md](docs/PERMISSIONS.md).

**Tune everything:** `config/` (Paper via `config/paper/`) + GUI **Tune** + **Modules**
(`modules/*.jar` packaging → `FINE_TUNE.txt`). See [docs/MODULES_AND_API.md](docs/MODULES_AND_API.md).

**Shipped by default (CORE + NETWORK)** on `gradle shadowJar` / `assembleRelease`:
`yap-placeholderapi`, `yap-plugin-compat`, `yap-pregen`, `yap-db`, `yap-playerdata`,
`yap-packs`, `yap-chat`, `yap-floodgate`, CORE fine-tune modules under `modules/`,
and `resourcepacks/yapcore-default.zip` (Faithful).

**GAMEPLAY opt-in** (`gradle installGameplayDefaults` or `assembleRelease -PyapGameplay=true`):
`yap-vehicles`, `yap-gameplay-knobs`, `yap-stacker`, vehicles/stacker/knobs modules,
vehicles overlay in the default pack.
Release folder: `build/dist/yapcore-release/` with **`linux/`** and **`windows/`**
trees (each self-contained). Linux: `./start.sh --fg`. Windows: `start.cmd -Fg`.
Vehicles: `/yapvehicle spawn buggy` — [docs/VEHICLES.md](docs/VEHICLES.md).
Stacker: `/yapstacker gui` — [docs/STACKER.md](docs/STACKER.md).
MariaDB / playerdata: [docs/MARIADB.md](docs/MARIADB.md) · [docs/PLAYERDATA.md](docs/PLAYERDATA.md).
Ranks: [docs/PERMISSIONS.md](docs/PERMISSIONS.md).

## Crash logger

Reports under `logs/crashes/` (gitignored contents): thread dumps, heap/JVM/OS, config, bridge stats, plugins/modules, console tail.

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/gui.sh` | Control GUI (Linux/dev) |
| `scripts/start.sh` / `stop.sh` / `status.sh` | Lifecycle (cds into `folia-kernel` by default) |
| `scripts/windows/*.ps1` + release `*.cmd` | Windows launchers in `yapcore-release/windows/` |
| `scripts/fetch-folia.sh` / `smoke-folia.sh` | Folia product path fetch + smoke |
| `scripts/vendor-paper.sh` / `build-vendor-paper.sh` | Legacy YaP Paperclip for Phase 3 benches |
| `scripts/nginx-setup.sh` | Optional nginx edge |
| `scripts/db/start-mariadb.sh` / `configure-db.sh` | Docker MariaDB + JDBC into YapDb/playerdata |
| `scripts/install-luckperms.sh` | Download LuckPerms + YaP rank pack ready |
| `gradle assemblePluginDist` | All first-party jars → `build/dist/yap-plugins/` |
| `./tests.sh` | Interactive test menu |
| `./test-endurance.sh` | Long soak → `logs/endurance/` |

Full strategy: [docs/TESTING.md](docs/TESTING.md).

## Repository hygiene

This tree is meant to stay **GitHub-clean**. `.gitignore` excludes:

| Ignored | Why |
|---------|-----|
| `build/`, `.gradle/`, `*.jar`, `yapcore.jar` | Build outputs — run `gradle shadowJar` locally |
| `folia-kernel/`, `paper-kernel/`, `game-kernel/`, `lib/*` jars | Live Folia/Paper trees + downloaded clips |
| `libraries/`, `versions/`, `cache/` | Minecraft/Paper dependency caches |
| `bench/workdir-*`, `bench/results/*` | Bench lab state (keep `bench/results/README.md`) |
| `logs/`, `world*/`, `config/*` | Runtime / operator state (keep `.gitkeep` / `*.example`) |
| `/server.properties`, `/eula.txt` | Accidental root cwd leftovers |
| `docs/pdf/*.pdf` | Regenerated via `./scripts/export-docs-pdf.sh` |
| `vendor/paper/` | Cloned by `./scripts/vendor-paper.sh` (pin stays in `vendor/paper.pin`) |
| secrets (`forwarding.secret`, `.env`, keys) | Never commit |

**Tracked:** source, docs, whitepaper Markdown, branding, scripts, examples, GitHub templates, `vendor/yap-overlays/`.

See [.gitignore](.gitignore), [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md).

## Citation

```bibtex
@techreport{yapcore2026sixteen,
  title  = {YaPcore: A Sixteen-Thread Architecture for Concurrent Minecraft-Class Game Servers},
  author = {{YapLabs}},
  year   = {2026},
  number = {YAP-WP-16T-001}
}
```
