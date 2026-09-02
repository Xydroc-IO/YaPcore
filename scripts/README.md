# YaPcore scripts

Operator and maintainer helpers. See [docs/start/TESTING.md](../docs/start/TESTING.md) for release gates.

## Everyday (shipped in release zips)

| Script | Purpose |
|--------|---------|
| `start.sh` / `stop.sh` / `status.sh` | Server lifecycle |
| `gui.sh` / `start-prod.sh` | Swing panel / production launch |
| `seed-defaults.sh` | First-boot configs |
| `apply-production-profile.sh` | Public production keys |
| `yapctl` | CLI helper |
| `setup-velocity-forwarding.sh` | Velocity forwarding secret |
| `db/*.sh` | MariaDB + JDBC |
| `windows/*.ps1` | Windows equivalents |

## YaP-Folia (product game jar)

| Script | Purpose |
|--------|---------|
| `build-yap-folia.sh` | Build `lib/yap-folia-26.2.jar` |
| `verify-yap-folia.sh` | Sanity-check fork jar |
| `vendor-folia.sh` · `folia-patch.sh` | Vendor + patches |
| `fetch-folia.sh` | Stock Folia (fallback / CI) |
| `fetch-tebex.sh` · `fetch-grim.sh` · `grim-ac.sh` | Optional plugins |
| `soak-yap-folia.sh` · `smoke-folia.sh` | Soak + boot smoke |

Soak hooks: `smoke-folia-sched-compat.sh`, `smoke-folia-cross-region-tp.sh`, `smoke-folia-async-save.sh`.

## Release gates

| Script | Purpose |
|--------|---------|
| `smoke-network-full.sh` | **Primary release gate** (9 steps) |
| `smoke-phase7-soak.sh` | Play soak + gameplay (600s) |
| `smoke-yap-link-*.sh` · `smoke-folia-plugins.sh` · `smoke-bedrock-play.sh` | Network / crossplay |
| `check-plugin-layout.sh` | Plugin folder layout |
| `protocol-matrix/` | JE/BE matrix + play soak bots |

## Content / packs

| Script | Purpose |
|--------|---------|
| `content/*.py` · `validate-mmo-content.sh` | MMO content pipeline |
| `generate-ability-pack.py` · `generate-mmo-icons.py` | Abilities + icons |
| `build-default-resourcepack.sh` · `fetch-faithful-64x.sh` | Resource packs |

## Bench / tests (maintainers)

| Path | Purpose |
|------|---------|
| `bench/run-vs-folia.sh` · `run-vs-all.sh` | MSPT benches |
| `bench/bots/` | Mineflayer swarm |
| `test-unit.sh` · `test-fray.sh` · `test-all.sh` | Gradle test wrappers |
| `export-docs-pdf.sh` | Rebuild docs PDFs |
| `lib.sh` | Shared helpers |
