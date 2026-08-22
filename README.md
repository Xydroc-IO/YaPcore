# YaPcore

<p align="center">
  <img src="branding/yapcore-banner.png" alt="YaPcore banner" width="100%"/>
</p>

<p align="center">
  <img src="branding/yapcore-mark.png" alt="YaPcore mark" width="160"/>
</p>

**Folia-first** Minecraft server product (YapLabs **YaPcore**) for Linux —
**Folia** runs the game, **YapEngine** runs the slim chassis (Netty, dual-stack,
I/O, ops), **YaP Link** (complete Velocity fork) fronts multi-backend networks,
Folia-aware plugins, YaP plugins & fine-tune modules, Java+Bedrock dual-stack,
resource packs, control GUI, and deep crash diagnostics.

> Not affiliated with Mojang / Microsoft. See [LICENSE](LICENSE).

| | |
|--|--|
| **What we are** | [docs/WHAT_WE_ARE.md](docs/WHAT_WE_ARE.md) · [plain English](docs/PLAIN_ENGLISH.md) |
| **Full rundown** | [docs/FULL_RUNDOWN.md](docs/FULL_RUNDOWN.md) |
| **Whitepaper** | [docs/whitepaper/YAPCORE_WHITEPAPER.md](docs/whitepaper/YAPCORE_WHITEPAPER.md) · [plain English](docs/whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) |
| **Docs index** | [docs/README.md](docs/README.md) |
| **Branding** | [branding/](branding/README.md) |

## Architecture (three layers)

| Layer | Role |
|-------|------|
| **YaP Link** | Multi-backend proxy (separate JVM) — [docs/YAP_LINK.md](docs/YAP_LINK.md) |
| **YapEngine chassis** | Edge, dual-stack, bridge, UI/Heavy I/O, telemetry (16 logical channels) |
| **Folia** | **Game tick** — regionized world/entity/redstone (embedded JVM) |

Chassis thread map: [docs/YAPENGINE_16THREAD.md](docs/YAPENGINE_16THREAD.md).

**Game path:** **Folia** authority (default) · YapEngine **slim chassis** (not game tick) · **YaP Link** (full Velocity fork) for
multi-backend networks · Phase 3 Paper spatial **retired as product default**
(opt-in benches only; Folia path has no Phase 3 spatial tick) · fair highpop cite
**~100 active bots** (250 keepalive = HOLD-ONLY) · Phase 4 dual-stack (join green;
play depth smoke green) —
[docs/BENCH_VS_FOLIA.md](docs/BENCH_VS_FOLIA.md) ·
[docs/YAP_LINK.md](docs/YAP_LINK.md) · [docs/BENCH_VS_FOLIA.md](docs/BENCH_VS_FOLIA.md).
Default: `game-authority=folia`, `folia-embed=true`; Phase 3 flags **off**.  
Product target: **high-pop / heavy load** (not empty lobbies).

## Quick start

```bash
git clone https://github.com/<you>/YaPcore.git
cd YaPcore
chmod +x scripts/*.sh
# Folia 26.2 needs Java 25+
./scripts/fetch-folia.sh          # Folia product path
gradle shadowJar          # jar + default plugins/packs; release → build/dist/yapcore-release/
./scripts/gui.sh          # control panel
# or
./scripts/start.sh --fg   # headless (YaP stays at repo root; Folia child uses folia-kernel)
# YaP Link (optional multi-backend): see docs/YAP_LINK.md
# Release tree: cd build/dist/yapcore-release/linux && ./start.sh --fg
# Windows release: build\dist\yapcore-release\windows\start.cmd -Fg
./scripts/stop.sh
```

Requires **Java 25+** for Folia/Paper 26.2 (project toolchain may still compile
main sources with JDK 21). The fat jar is built locally and is **not** committed.
Release packages include **linux/** and **windows/** trees with native launchers.
Standalone zips: network suite, gameplay suite, addons — see [docs/RELEASES.md](docs/RELEASES.md).

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

**Source tree:** first-party plugin/module/Link sources live under [`yap-first-party/`](yap-first-party/README.md)
(labeled by release tier: `core-network/`, `gameplay/`, `api/`, `link/`, …).  
**Runtime:** drop built jars into **`plugins/`** and fine-tune packaging into **`modules/`**.

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
| `scripts/start.sh` / `stop.sh` / `status.sh` | Lifecycle (YaP at repo root; Folia child under `folia-kernel`) |
| `scripts/windows/*.ps1` + release `*.cmd` | Windows launchers in `yapcore-release/windows/` |
| `scripts/fetch-folia.sh` / `smoke-folia.sh` | Folia product path fetch + smoke |
| `scripts/nginx-setup.sh` | Optional nginx edge |
| `scripts/db/start-mariadb.sh` / `configure-db.sh` | Docker MariaDB + JDBC into YapDb/playerdata |
| `gradle assemblePluginDist` | All first-party jars → `build/dist/yap-plugins/` |
| `./tests.sh` | Interactive test menu |
| `./test-endurance.sh` | Long soak → `logs/endurance/` |

Full strategy: [docs/TESTING.md](docs/TESTING.md).

## Repository hygiene

This tree is meant to stay **GitHub-clean**. `.gitignore` excludes:

| Ignored | Why |
|---------|-----|
| `build/`, `.gradle/`, `*.jar`, `yapcore.jar` | Build outputs — run `gradle shadowJar` locally |
| `folia-kernel/`, `game-kernel/`, `lib/*` jars | Live Folia tree + downloaded clips |
| `libraries/`, `versions/`, `cache/` | Minecraft dependency caches |
| `bench/workdir-*`, `bench/results/*` | Bench lab state (keep `bench/results/README.md`) |
| `logs/`, `world*/`, `config/*` | Runtime / operator state (keep `.gitkeep` / `*.example`) |
| `/server.properties`, `/eula.txt` | Accidental root cwd leftovers |
| `docs/pdf/*.pdf` | Regenerated via `./scripts/export-docs-pdf.sh` |
| secrets (`forwarding.secret`, `.env`, keys) | Never commit |

**Tracked:** source, docs, whitepaper Markdown, branding, scripts, examples, GitHub templates, `vendor/yap-overlays/`.

See [.gitignore](.gitignore), [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md).

## Citation

```bibtex
@techreport{yapcore2026chassis,
  title  = {YaPcore: Folia Game Authority with a Slim Edge Chassis for Minecraft-Class Servers},
  author = {{YapLabs}},
  year   = {2026},
  number = {YAP-WP-16T-001}
}
```
