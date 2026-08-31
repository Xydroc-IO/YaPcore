# Contributing to YaPcore

Thanks for helping build a clean, professional Minecraft-class server engine.

## Ground rules

1. **Keep the repo clean** — never commit build outputs, live Paper/Folia trees, worlds, logs,
   plugin jars, bench workdirs, Link `link.properties`, or secrets. `.gitignore` enforces this;
   see the hygiene table in [README.md](README.md) and [docs/RELEASES.md](docs/RELEASES.md).
2. **Threading** — world / inventory / block changes on **SYNC**; DB/HTTP on **HEAVY**; menu polish on **UI**.
3. **Size** — prefer ≤500 lines per domain file; split by package when a class grows
   (see [docs/PERF_AND_LAYOUT.md](docs/PERF_AND_LAYOUT.md)). Link JE outbound framing belongs in
   `McOutboundPacketEncoder`, not a stacked compress+frame Netty pair.
4. **Docs** — API or architecture changes update `docs/` and, when substantial, the [whitepaper](docs/whitepaper/YAPCORE_WHITEPAPER.md).
   Regenerated PDFs under `docs/pdf/` are gitignored — commit Markdown, run `./scripts/export-docs-pdf.sh` locally.

## Setup

```bash
git clone <your-fork-url>
cd YaPcore
chmod +x scripts/*.sh
gradle shadowJar
./scripts/gui.sh
```

- **Java 21+** to compile the YaPcore toolchain.
- **Java 25+** to run Paper 26.2 / Phase 3 (`./scripts/start.sh`).

Optional YaP Paperclip (Phase 3 NMS interior tick):

```bash
./scripts/fetch-folia.sh
```

## Pull requests

- Use the PR template.
- Run `./test-unit.sh` (and relevant soaks for engine changes).
- One logical change per PR when possible.

## Modules & plugins

First-party sources live under [`yap-first-party/`](yap-first-party/README.md) (by release tier).
Do not vendor third-party plugins into this repository. Extend APIs so *others* can
publish jars to the unified **`plugins/`** folder or to **`modules/`**.
Gameplay encyclopedia: [docs/TUNE.md](docs/TUNE.md) (`yap-gameplay-knobs`).
See [docs/PLUGIN_COMPAT.md](docs/PLUGIN_COMPAT.md).

## Code of conduct

Be respectful. No harassment. Assume good faith in reviews.
