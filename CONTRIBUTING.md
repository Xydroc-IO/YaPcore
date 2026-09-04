# Contributing to YaPcore

Thank you for contributing to a production Folia network stack.

## Ground rules

1. **Repository hygiene** — never commit build outputs, live Paper/Folia trees, worlds, logs, plugin jars, bench workdirs, Link `link.properties`, secrets, **PDFs**, or office dumps. Markdown under `docs/` is the documentation source of truth (optional local print: `./scripts/export-docs-pdf.sh`). See [README.md](README.md) and [RELEASES.md](docs/start/RELEASES.md).
2. **Threading** — world / inventory / block changes on **SYNC**; DB/HTTP on **HEAVY**; menu polish on **UI**.
3. **Size** — **hard rule:** ≤500 lines per domain `.java` file under `src/main/java` and
   `yap-first-party/` (enforced by `./scripts/check-domain-line-limits.sh` / `gradle checkDomainLineLimits`).
   Split by package when a class grows ([whitepaper](docs/whitepaper/YAPCORE_WHITEPAPER.md)).
   Link JE outbound framing belongs in `McOutboundPacketEncoder`, not a stacked compress+frame Netty pair.
   Follow-up elegance (DB bootstrap, packages, tests): [CODE_ELEGANCE_FOLLOWUP.md](docs/ops/CODE_ELEGANCE_FOLLOWUP.md).
4. **Docs** — behavior or API changes update `docs/` Markdown (and the [whitepaper](docs/whitepaper/YAPCORE_WHITEPAPER.md) when substantial). Do not add generated PDFs to PRs.

## Setup

```bash
git clone https://github.com/Xydroc-IO/YaPcore.git
cd YaPcore
chmod +x scripts/*.sh scripts/db/*.sh scripts/yapctl
./scripts/build-yap-folia.sh
gradle shadowJar
./scripts/gui.sh
```

- **Java 25+** to build and run the product path (YaP-Folia 26.2).
- First boot: [QUICK_START.md](docs/start/QUICK_START.md).

## Pull requests

- Use the PR template.
- One logical change per PR when possible.
- Include a short test plan (`gradle test`, smoke join, docs touchpoints).

## Modules & plugins

First-party sources live under [`yap-first-party/`](yap-first-party/README.md) (by release tier).  
Do not vendor third-party plugins into this repository. Extend APIs so others can publish jars to `plugins/` or `modules/`.  
Gameplay encyclopedia: [TUNE.md](docs/ops/TUNE.md). Compat: [PLUGIN_COMPAT.md](docs/plugins/PLUGIN_COMPAT.md).

## Conduct

Be respectful. No harassment. Assume good faith in reviews.

## License

YaPcore is **[GNU GPLv3](LICENSE)** (or later). By opening a pull request, you agree that your contribution is licensed under the same terms.  
Third-party notices: [LICENSING.md](docs/start/LICENSING.md).
