# YaPcore scripts

Install, setup, and lifecycle helpers for operators.

## Server lifecycle

| Script | Purpose |
|--------|---------|
| `start.sh` / `stop.sh` / `status.sh` | Server lifecycle |
| `gui.sh` / `start-prod.sh` | Swing panel / production launch |
| `start-yap-link.sh` | Start YaP Link proxy |
| `yapctl` | CLI helper (`soak-compat` / `soak-perf` / `soak-long`) |
| `soak-yap-folia.sh` | Live Folia mem/crash soak (compat / perf / long) |
| `verify-yap-folia.sh` | Patch check + build `lib/yap-folia-*.jar` (`SKIP_SMOKE=1` for CI) |
| `bench/cite-fullcite.sh` | Stock Folia vs YaPcore fullcite — **ship knobs** cite gate (`knob_*` required) |
| Docs | [YAP_FOLIA_SOAK](../docs/folia/YAP_FOLIA_SOAK.md), [CANVAS_PARITY](../docs/folia/CANVAS_PARITY.md), [REAL_GAINS](../docs/folia/REAL_GAINS.md) |
| `bench/compare-folia.py` | MSPT A/B vs stock Folia (fairness + tie band) |
| `bench/check-mspt-regression.sh` | Wrapper gate over compare-folia.py |
| `bench/run-vs-folia.sh` | Multi-competitor MSPT runner |

### Mem / uptime / Folia parity

- Fast gates (CI): `gradle verifyConcurrency` + `gradle soakTest` + `scanFirstPartyFoliaCompat` + MSPT fixture compare
- Chassis retention: `gradle endurance -Dyap.endurance.seconds=300`
- Live Folia: `./scripts/yapctl soak-compat` then `soak-long` (**12h** default, **8h** floor)
- Folia jar: nightly / main / `workflow_dispatch` builds via `.github/workflows/folia-fork.yml`
- **Cite:** `./scripts/yapctl cite-fullcite` → `bench/results/cite-latest-*.json` (ship knobs; must be citeable ≥5%; currently **−12.4%**)
- Target: flat Folia heap/thread slope for 12h continuous; optional scheduled restart is ops hygiene, not a substitute for fixing slope failures
- Reports: `logs/soak/`, `logs/endurance/`, `bench/results/`

## First boot and setup

| Script | Purpose |
|--------|---------|
| `seed-defaults.sh` | First-boot configs |
| `apply-production-profile.sh` | Public production keys |
| `setup-velocity-forwarding.sh` | Velocity forwarding secret |
| `nginx-setup.sh` | nginx edge template install |
| `db/*.sh` | MariaDB / Postgres / SQLite JDBC setup |
| `windows/*.ps1` | Windows equivalents |
| `content/generate-mmo-quest-compendium.py` | Validate MMO quest YAML objective types (Wave 3) |
| `generate-ability-pack.py` | Regenerate bulk ability YAML with V2 element/archetype VFX kits (`docs/mmo/MMO_ABILITY_VFX.md`) |
| `generate-hero-ability-icons.py` | Unique 16×16 hero ability icons (CMD 78020–78031) |
| `content/ability-vfx-soak-gate.py` | Offline Folia VFX authoring soak gate (V4) |

## Build YaP-Folia (from source)

| Script | Purpose |
|--------|---------|
| `build-yap-folia.sh` | Build `lib/yap-folia-26.2.jar` |
| `vendor-folia.sh` · `folia-patch.sh` | Vendor + patches |
| `fetch-folia.sh` | Stock Folia fallback |

## Optional plugins and packs

| Script | Purpose |
|--------|---------|
| `fetch-tebex.sh` · `fetch-grim.sh` · `grim-ac.sh` | Optional Tebex / Grim AC |
| `build-default-resourcepack.sh` · `fetch-faithful-64x.sh` · `generate-yap-skies.py` | Default resource pack + YaP Skies |

See [docs/start/QUICK_START.md](../docs/start/QUICK_START.md) and [docs/start/WINDOWS.md](../docs/start/WINDOWS.md).
