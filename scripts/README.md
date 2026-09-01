# YaPcore scripts

Operator and maintainer helpers. Prefer this short list; see
[docs/start/RELEASES.md](../docs/start/RELEASES.md) and [docs/start/QUICK_START.md](../docs/start/QUICK_START.md).

## Everyday (ship in release zips)

| Script | Purpose |
|--------|---------|
| `start.sh` / `stop.sh` / `status.sh` | Server lifecycle |
| `gui.sh` / `start-prod.sh` | Swing panel / production launch |
| `seed-defaults.sh` | First-boot configs from `config/defaults/` |
| `yapctl` | CLI helper |
| `setup-velocity-forwarding.sh` | Shared secret + Folia Velocity forwarding |
| `db/ensure-db.sh` · `db/configure-db.sh` | MariaDB + JDBC wiring |
| `db/start-mariadb.sh` · `stop-mariadb.sh` · `status-mariadb.sh` | Docker MariaDB |
| `windows/*.ps1` | Windows equivalents |

## YaP-Folia (product game jar)

| Script | Purpose |
|--------|---------|
| `build-yap-folia.sh` | Build `lib/yap-folia-26.2.jar` |
| `verify-yap-folia.sh` | Sanity-check the fork jar |
| `vendor-folia.sh` · `folia-patch.sh` | Vendor + apply patches |
| `fetch-folia.sh` | Stock Fill Folia (bench / fallback only) |
| `fetch-tebex.sh` | Official Tebex Folia plugin → `plugins/tebex.jar` (GPLv3) |
| `fetch-grim.sh` | Official Grim AC Folia jar → `plugins/grim.jar` (GPLv3) |
| `soak-yap-folia.sh` | Compat / perf soak profiles |
| `smoke-folia.sh` | Boot + ready hold |

Soak hooks (called by `soak-yap-folia.sh`): `smoke-folia-sched-compat.sh`,
`smoke-folia-cross-region-tp.sh`, `smoke-folia-async-save.sh`,
`smoke-folia-scoreboard.sh`.

## Network / release gates

| Script | Purpose |
|--------|---------|
| `start-yap-link.sh` | Run YaP Link proxy |
| `smoke-yap-link-folia.sh` · `smoke-yap-link-plugins.sh` | Link + Folia |
| `smoke-yap-link-bedrock.sh` · `smoke-yap-link-two-backend.sh` | Crossplay / multi-backend |
| `smoke-folia-plugins.sh` · `smoke-bedrock-play.sh` | Plugin + Bedrock play |
| `smoke-network-full.sh` | Full release gate (assemble + smokes) |
| `smoke-playerdata-shops-ah.sh` · `smoke-link-rate-limit.sh` · `smoke-lagguard.sh` | Feature smokes |
| `check-plugin-layout.sh` | Plugin folder layout check |

## Content / packs

| Script | Purpose |
|--------|---------|
| `content/generate-mmo-quest-compendium.py` | Tiered quest YAML |
| `content/generate-mmo-baseline-pack.py` | Recipes / bosses |
| `validate-mmo-content.sh` | Manifest + quest validation |
| `generate-ability-pack.py` · `generate-mmo-icons.py` | Abilities YAML + CLAY_BALL icon pack |
| `build-default-resourcepack.sh` · `fetch-faithful-64x.sh` · `verify-packs.sh` | Client packs |

## Bench / protocol / tests

| Path | Purpose |
|------|---------|
| `bench/run-vs-folia.sh` · `run-full-stack.sh` | MSPT / stack benches |
| `bench/fetch-*.sh` · `compare-folia.py` | Competitor jars + compare |
| `protocol-matrix/` | JE/BE protocol matrix (Via/Geyser parity) |
| `test-unit.sh` · `test-fray.sh` · `test-all.sh` | `gradle test` / Fray / verify |
| `export-docs-pdf.sh` | Rebuild docs PDFs |
| `lib.sh` | Shared helpers (sourced by other scripts) |

Milestone one-shot smokes (`smoke-mmo-m*`, factions/guilds/games gates) and root
`test-*.sh` wrappers were removed — use the gates above.
