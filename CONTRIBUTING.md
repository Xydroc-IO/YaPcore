# Contributing to YaPcore

Thanks for helping build a clean, professional Minecraft-class server engine.

## Ground rules

1. **Keep the repo clean** — never commit build outputs, live Paper/Folia trees, worlds, logs,
   plugin jars, bench workdirs, Link `link.properties`, or secrets. `.gitignore` enforces this;
   see the hygiene table in [README.md](README.md) and [docs/start/RELEASES.md](docs/start/RELEASES.md).
2. **Threading** — world / inventory / block changes on **SYNC**; DB/HTTP on **HEAVY**; menu polish on **UI**.
3. **Size** — prefer ≤500 lines per domain file; split by package when a class grows
   (see [whitepaper](docs/whitepaper/YAPCORE_WHITEPAPER.md)). Link JE outbound framing belongs in
   `McOutboundPacketEncoder`, not a stacked compress+frame Netty pair.
4. **Docs** — API or architecture changes update `docs/` and, when substantial, the [whitepaper](docs/whitepaper/YAPCORE_WHITEPAPER.md).

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
- One logical change per PR when possible.

## Modules & plugins

First-party sources live under [`yap-first-party/`](yap-first-party/README.md) (by release tier).
Do not vendor third-party plugins into this repository. Extend APIs so *others* can
publish jars to the unified **`plugins/`** folder or to **`modules/`**.
Gameplay encyclopedia: [docs/ops/TUNE.md](docs/ops/TUNE.md) (`yap-gameplay-knobs`).
See [docs/plugins/PLUGIN_COMPAT.md](docs/plugins/PLUGIN_COMPAT.md).

## Code of conduct

Be respectful. No harassment. Assume good faith in reviews.

## License

YaPcore is licensed under the **[GNU GPLv3](LICENSE)** (or later). By opening a pull
request, you agree that your contribution is licensed under the same terms.
Details and third-party notices: [docs/start/LICENSING.md](docs/start/LICENSING.md).
