# YaPcore releases (v1.0.0.0)

All first-party artifacts share version **1.0.0.0** (Gradle `version`, plugin `plugin.yml`, Link `link-plugin.json`).

**Release notes:** [RELEASE_NOTES.md](RELEASE_NOTES.md)  
**License:** release trees include root **`LICENSE`** (GNU GPLv3). See [LICENSING.md](LICENSING.md).

**Game jar:** product path expects **YaP-Folia** (`lib/yap-folia-26.2.jar`) built via `./scripts/build-yap-folia.sh`. Stock Fill Folia is not the release default. Build with `./scripts/build-yap-folia.sh`.

## Build commands

| Task | Output |
|------|--------|
| `./scripts/build-yap-folia.sh` | `lib/yap-folia-26.2.jar` — **required for product path** |
| `gradle assembleRelease` | `build/dist/yapcore-release/` — **linux/** + **windows/** full trees (all plugins) |
| `gradle assembleRelease` | Slim CORE+NETWORK by default (`yapGameplay=false`) |
| `gradle assembleRelease -PyapGameplay=true` | Full box including GAMEPLAY (skills / stacker / knobs / disasters) |
| `gradle assembleRelease -PyapGameplay=false` | Explicit slim CORE+NETWORK only |
| `gradle publishReleasesFolder` | **`releases/<version>/`** — trees + linux/windows zips + suite zips |
| Git tag `1.0.0.0` or `v*` push | GitHub Actions → `yapcore-release-linux.zip` + `-windows.zip` |
| `gradle assembleNetworkSuite` | `build/dist/yap-network-suite.zip` — YaP Link + native link plugins |
| `gradle assembleGameplaySuite` | `build/dist/yap-gameplay-suite.zip` — GAMEPLAY plugins (skills / stacker / knobs / disasters) |
| `gradle assemblePluginDist` | `build/dist/yap-plugins/` — flat jar mirror by tier |
| `gradle assembleAllReleases` | Full box + standalone suite zips (under `build/dist/`) |

## Durable release folder

```bash
./scripts/build-yap-folia.sh
gradle publishReleasesFolder
# → releases/1.0.0.0/
#      yapcore-release/linux/   yapcore-release/windows/
#      yapcore-release-linux.zip  yapcore-release-windows.zip
#      yap-network-suite.zip  yap-gameplay-suite.zip
```

The entire `releases/` directory is **gitignored** (local artifacts only). Rebuild anytime with the same task.

## GitHub release assets (tag `1.0.0.0`)

Attach (or refresh with `--clobber`) so `/releases/latest/download/{file}` works:

| Asset | Role |
|-------|------|
| `yapcore-release-linux.zip` / `-windows.zip` | Full server boxes (CI also uploads these on tag) |
| `yap-network-suite.zip` / `yap-gameplay-suite.zip` | Standalone suites |
| `yapcore-default.zip` | **Required** for pack CDN (`resource-pack-url` default) |
| `yap-visuals-1.0.1.jar` | Optional Fabric visuals (Sodium+Iris+shaders) |
| `yap-bag-1.0.0.jar` / `yap-ultrawide-1.0.0.jar` | Optional Fabric bag UI / ultrawide FOV |

```bash
gh release upload 1.0.0.0 \
  releases/1.0.0.0/yapcore-release-linux.zip \
  releases/1.0.0.0/yapcore-release-windows.zip \
  releases/1.0.0.0/yap-network-suite.zip \
  releases/1.0.0.0/yap-gameplay-suite.zip \
  resourcepacks/yapcore-default.zip \
  dist/client-mods/yap-visuals-1.0.1.jar \
  dist/client-mods/yap-bag-1.0.0.jar \
  dist/client-mods/yap-ultrawide-1.0.0.jar \
  --clobber -R Xydroc-IO/YaPcore
```

**After Link wire fixes:** `publishReleasesFolder` refreshes `yap-link.jar` inside the
trees/zips. Also copy the shadow jar to **repo-root** `yap-link.jar` — the Swing/web
GUI `LinkProcessManager` prefers that path over `build/libs`.

## Linux / Windows full box

Each OS folder is self-contained:

- `yapcore.jar` — YaPcore chassis + embedded web dashboard
- `lib/yap-folia-*.jar` — **YaP-Folia** game (when builder ran `build-yap-folia.sh`)
- `yap-link.jar` + `link-data/` — native multi-backend proxy (`0.6.0-phase6`)
- `plugins/` — CORE+NETWORK first-party stack
- `modules/` — fine-tune packaging modules
- `resourcepacks/` — default client pack
- `config/`, `deploy/nginx`, `deploy/mariadb`, `docs/`, launch scripts

Launch:

```bash
# Linux
cd build/dist/yapcore-release/linux && ./start.sh --fg

# Windows
cd build\dist\yapcore-release\windows
start.cmd -Fg
```

See [WINDOWS.md](WINDOWS.md) and [MARIADB.md](../data/MARIADB.md).

## Standalone add-ons

| Zip | Contents | Default in full box? |
|-----|----------|----------------------|
| **yap-network-suite.zip** | Link proxy + chat/mod/selector/tab/discord bridge plugins | Yes (`link-data/plugins/`) |
| **yap-gameplay-suite.zip** | yap-skills, yap-stacker, yap-gameplay-knobs, yap-disasters + stacker/knobs modules | Also included in the full box |

## Repo layout discipline

- First-party code: [`yap-first-party/`](../../yap-first-party/README.md)
- YaP-Folia: [`vendor/folia/`](../vendor/folia/) + [QUICK_START.md](../start/QUICK_START.md)
- Gradle split: `build.gradle.kts` + `gradle/yap-product.gradle.kts`, `yap-release.gradle.kts`, `yap-packaging.gradle.kts`
- **Do not commit** live kernel/link state (`folia-kernel/logs`, `usercache`, `ops`, `link-data/link.properties`, plugin config dirs) — enforced by `.gitignore`

## Refresh release zips (same version)

When docs/plugins change but the product version stays **1.0.0.0**:

```bash
./scripts/build-yap-folia.sh   # if Folia fork patches changed
gradle publishReleasesFolder   # refreshes releases/1.0.0.0/ trees + zips
```

Update [RELEASE_NOTES.md](RELEASE_NOTES.md) “After 1.0.0.0” — do **not** change Gradle `version`.

**Latest refresh:** 2026-09-04 — GitHub assets complete (OS zips, suites, `yapcore-default.zip`, client jars); pack URL/SHA sync; docs PDFs gitignored. Prior same-day: YaP Encyclopedia + Canvas heavypop cite (−8.09% vs Canvas / −16.56% vs stock); Ops Waves 1–5. Gameplay slimmed to skills / stacker / knobs / disasters (MMO / vehicles / abilities packs removed).

## Version bump checklist (new tag only)

1. Root `build.gradle.kts` `version = "…"`
2. Each subproject `build.gradle.kts` + `plugin.yml` / `link-plugin.json`
3. Rebuild YaP-Folia + `gradle assembleAllReleases` / `publishReleasesFolder`
4. Tag: e.g. **`1.0.0.0`** or later `1.x.y.z` (also accepted: `v…`)
