# YaPcore

<p align="center">
  <img src="branding/yapcore-banner.png" alt="YaPcore banner" width="100%"/>
</p>

<p align="center">
  <img src="branding/yapcore-mark.png" alt="YaPcore mark" width="160"/>
</p>

**16-thread** Minecraft-class server engine (YapLabs **YapEngine**) for Linux —
Paper as the game, Spigot/Paper-compatible plugins, YaP plugins & fine-tune modules,
Java+Bedrock dual-stack, resource packs, control GUI, and deep crash diagnostics.

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
| 3–6 | Parallel Game Core (quad-tree spatial loops) — Phase 3 tick |
| 7–8 | Chunk Sync DLM + Boundary Arbitrator |
| 9 | Compatibility Bridge |
| 10–11 | High-Speed UI sandbox |
| 12–15 | Heavy I/O sandbox |
| 16 | Telemetry / async worker |

See [docs/YAPENGINE_16THREAD.md](docs/YAPENGINE_16THREAD.md).

**Game path:** **Paper** authority · **Phase 3 done** · **Phase 3.5 active** (MSPT scoreboard + leased world ticks) ·
**Phase 4 next** (dual-stack + YaP plugins polish) —
[docs/PAPER_YAPENGINE_PORT.md](docs/PAPER_YAPENGINE_PORT.md) · [docs/BENCH_VS_PAPER.md](docs/BENCH_VS_PAPER.md).  
Default: `game-authority=paper`, `paper-embed=true`, `paper-phase3-tick-bridge=true`.

## Quick start

```bash
git clone https://github.com/<you>/YaPcore.git
cd YaPcore
chmod +x scripts/*.sh
# Paper 26.2 / Phase 3 needs Java 25+
./scripts/vendor-paper.sh && ./scripts/build-vendor-paper.sh   # optional YaP Paperclip
gradle shadowJar          # produces ./yapcore.jar (gitignored)
./scripts/gui.sh          # control panel
# or
./scripts/start.sh --fg   # headless (cds into paper-kernel for Phase 3)
./scripts/stop.sh
```

Requires **Java 25+** for Paper 26.2 / Phase 3 (project toolchain may still compile
main sources with JDK 21). The fat jar is built locally and is **not** committed.

## Dual-stack + resource packs

Java **and** Bedrock are enabled by default. **JE multi-version is built in**
(`ProtocolBand` / `backwards-compatible=true`) — no third-party protocol plugins.
Texture/resource packs in `resourcepacks/` download over the built-in HTTP host (default `:8081`).
See [docs/CLIENTS_AND_PACKS.md](docs/CLIENTS_AND_PACKS.md).

**Velocity:** YaPcore is a Paper backend with modern forwarding — set `velocity-enabled=true`
(see [docs/VELOCITY.md](docs/VELOCITY.md)). We do not embed Velocity.

## Plugins & modules

**One folder for jars:** drop Paper/Spigot and YaP plugins into **`plugins/`**.
`paper-kernel/plugins` is a symlink to that folder so real Paper loads the same jars.
Fine-tune modules stay in `modules/`.

| Type | Folder | Manifest | Base class |
|------|--------|----------|------------|
| Legacy Spigot/Paper | `plugins/` | `plugin.yml` | `JavaPlugin` |
| YaP plugin | `plugins/` | `yap.yml` | `YaPPlugin` |
| Fine-tune module | `modules/` | `module.yml` | `YaPModule` |

World/inventory → **SYNC** · DB/HTTP → **HEAVY** · menus → **UI**.  
See [docs/PLUGINS.md](docs/PLUGINS.md), [docs/PLUGIN_COMPAT.md](docs/PLUGIN_COMPAT.md), and [docs/MODULES_AND_API.md](docs/MODULES_AND_API.md).

## Crash logger

Reports under `logs/crashes/` (gitignored contents): thread dumps, heap/JVM/OS, config, bridge stats, plugins/modules, console tail.

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/gui.sh` | Control GUI |
| `scripts/start.sh` / `stop.sh` / `status.sh` | Lifecycle (Phase 3 cds into `paper-kernel`) |
| `scripts/vendor-paper.sh` / `build-vendor-paper.sh` | Vendor Paper 26.2 → `lib/paper-*-yap.jar` |
| `scripts/nginx-setup.sh` | Optional nginx edge |
| `./tests.sh` | Interactive test menu |
| `./test-endurance.sh` | Long soak → `logs/endurance/` |

Full strategy: [docs/TESTING.md](docs/TESTING.md).

## Repository hygiene

This tree is meant to stay **GitHub-clean**. `.gitignore` excludes:

| Ignored | Why |
|---------|-----|
| `build/`, `.gradle/`, `*.jar`, `yapcore.jar` | Build outputs — run `gradle shadowJar` locally |
| `paper-kernel/`, `game-kernel/`, `lib/*` jars | Live Paper trees + downloaded Paperclips |
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
