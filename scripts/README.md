# YaPcore scripts

Install, setup, and lifecycle helpers for operators. Scripts live in topic folders:

| Folder | What’s here |
|--------|-------------|
| [`lifecycle/`](lifecycle/) | Start / stop / status / GUI / Link / `yapctl` |
| [`setup/`](setup/) | First-boot seed, production profile, Velocity, nginx, Bedrock mode |
| [`folia/`](folia/) | Build / vendor / patch / verify / smoke / soak YaP-Folia |
| [`packs/`](packs/) | Resource packs, client mods zip, Faithful fetch, texture generators |
| [`plugins/`](plugins/) | Optional Tebex / Grim AC fetch + enable |
| [`db/`](db/) | MariaDB / Postgres / SQLite JDBC setup |
| [`bench/`](bench/) | MSPT A/B, fullcite, competitor runners |
| [`parity/`](parity/) | Bedrock-feel extract / convert / smoke |
| [`check/`](check/) | CI hygiene (domain line limits, seed drift, DB bootstrap) |
| [`docs/`](docs/) | Optional local PDF export |
| [`windows/`](windows/) | PowerShell equivalents |
| `lib.sh` | Shared bash helpers (sourced by most scripts) |

```bash
find scripts -type f \( -name '*.sh' -o -name yapctl \) -exec chmod +x {} +
```

## Server lifecycle

| Script | Purpose |
|--------|---------|
| `lifecycle/start.sh` / `stop.sh` / `status.sh` | Server lifecycle |
| `lifecycle/gui.sh` / `start-prod.sh` | Swing panel / production launch |
| `lifecycle/start-yap-link.sh` | Start YaP Link proxy |
| `lifecycle/yapctl` | CLI helper (`soak-compat` / `soak-perf` / `soak-long`) |
| `folia/soak-yap-folia.sh` | Live YaP-Folia mem/crash soak (compat / perf / long) |
| `folia/verify-yap-folia.sh` | Patch check + build `lib/yap-folia-*.jar` + `smoke-folia.sh` (CI nightly/main). `SKIP_SMOKE=1` for build-only local runs |
| `folia/smoke-contiguous-bar.sh` | Live contiguous carve + gap hold (port 25575; lab jar) |
| `bench/cite-fullcite.sh` | Stock Folia vs YaPcore fullcite — **ship knobs** cite gate (`knob_*` required) |
| Docs | [YaP-Folia](../docs/folia/YAP_FOLIA_PATCHES.md) |
| `bench/compare-folia.py` | MSPT A/B vs stock Folia (fairness + tie band) |
| `bench/check-mspt-regression.sh` | Wrapper gate over compare-folia.py |
| `bench/run-vs-folia.sh` | Multi-competitor MSPT runner |

### Mem / uptime / YaP-Folia parity

- Fast gates (CI): `gradle verifyConcurrency` + `gradle soakTest` + `scanFirstPartyFoliaCompat` + MSPT fixture compare
- Chassis retention: `gradle endurance -Dyap.endurance.seconds=300`
- Live YaP-Folia: `./scripts/lifecycle/yapctl soak-compat` then `soak-long` (**12h** default, **8h** floor)
- YaP-Folia jar: nightly / main / `workflow_dispatch` builds via `.github/workflows/folia-fork.yml`
- **Cite:** `./scripts/lifecycle/yapctl cite-fullcite` → `bench/results/cite-latest-*.json` (ship knobs; must be citeable ≥5%; currently **−12.4%**)
- Target: flat YaP-Folia heap/thread slope for 12h continuous; optional scheduled restart is ops hygiene, not a substitute for fixing slope failures
- Reports: `logs/soak/`, `logs/endurance/`, `bench/results/`

## First boot and setup

| Script | Purpose |
|--------|---------|
| `setup/seed-defaults.sh` | First-boot configs |
| `setup/apply-production-profile.sh` | Public production keys |
| `setup/setup-velocity-forwarding.sh` | Velocity forwarding secret |
| `setup/nginx-setup.sh` | nginx edge template install |
| `db/*.sh` | MariaDB / Postgres / SQLite JDBC setup |
| `windows/*.ps1` | Windows equivalents |

## Build YaP-Folia (from source)

| Script | Purpose |
|--------|---------|
| `folia/build-yap-folia.sh` | Build `lib/yap-folia-26.2.jar` |
| `folia/vendor-folia.sh` · `folia/folia-patch.sh` | Vendor + patches |
| `folia/fetch-folia.sh` | Stock Folia fallback |

## Optional plugins and packs

| Script | Purpose |
|--------|---------|
| `plugins/fetch-tebex.sh` · `plugins/fetch-grim.sh` · `plugins/grim-ac.sh` | Optional Tebex / Grim AC (**PvP: enable Grim**) |
| `packs/build-default-resourcepack.sh` · `packs/fetch-faithful-64x.sh` · `packs/generate-yap-skies.py` · `packs/generate-yap-water.py` · `packs/generate-yap-foliage.py` | Default pack overlays |
| `packs/build-yap-client-render.sh` | Fabric `client_mods.zip` (visuals + bag + **yap-420** + presence + blocks + staff + ultrawide) |
| `packs/sync-github-pack-offer.sh` | Hash GitHub Releases latest pack → Folia `resource-pack` + YaPPacks `active.json` |

See [docs/start/QUICK_START.md](../docs/start/QUICK_START.md) and [docs/start/WINDOWS.md](../docs/start/WINDOWS.md).
