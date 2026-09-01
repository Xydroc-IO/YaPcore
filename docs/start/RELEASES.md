# YaPcore releases (v1.0.0.0)

All first-party artifacts share version **1.0.0.0** (Gradle `version`, plugin `plugin.yml`, Link `link-plugin.json`).

**License:** release trees include root **`LICENSE`** (GNU GPLv3). See [LICENSING.md](LICENSING.md).

**Game jar:** product path expects **YaP-Folia** (`lib/yap-folia-26.2.jar`) built via `./scripts/build-yap-folia.sh`. Stock Fill Folia is not the release default. See [FOLIA_FORK.md](../folia/FOLIA_FORK.md).

## Build commands

| Task | Output |
|------|--------|
| `./scripts/build-yap-folia.sh` | `lib/yap-folia-26.2.jar` — **required for product path** |
| `gradle assembleRelease` | `build/dist/yapcore-release/` — **linux/** + **windows/** full server trees |
| `gradle assembleRelease -PyapGameplay=true` | Same + vehicles, stacker, gameplay-knobs, MMO jars & modules |
| `gradle publishReleasesFolder` | **`releases/<version>/`** — trees + linux/windows zips + suite zips |
| Git tag `v*` push | GitHub Actions → `yapcore-release-linux.zip` + `-windows.zip` |
| `gradle assembleNetworkSuite` | `build/dist/yap-network-suite.zip` — YaP Link + native link plugins |
| `gradle assembleGameplaySuite` | `build/dist/yap-gameplay-suite.zip` — GAMEPLAY plugins/modules (standalone) |
| `gradle assembleAddonsRelease` | `build/dist/yap-addons-release.zip` — example vehicle addon |
| `gradle assemblePluginDist` | `build/dist/yap-plugins/` — flat jar mirror by tier |
| `gradle assembleAllReleases` | Full box + all standalone zips (under `build/dist/`) |

## Durable release folder

```bash
./scripts/build-yap-folia.sh
gradle publishReleasesFolder
# → releases/1.0.0.0/
#      yapcore-release/linux/   yapcore-release/windows/
#      yapcore-release-linux.zip  yapcore-release-windows.zip
#      yap-network-suite.zip  yap-gameplay-suite.zip  yap-addons-release.zip
```

The entire `releases/` directory is **gitignored** (local artifacts only). Rebuild anytime with the same task.

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
| **yap-gameplay-suite.zip** | yap-vehicles, yap-stacker, yap-gameplay-knobs + MMO + modules | Only with `-PyapGameplay=true` |
| **yap-addons-release.zip** | `examples/yap-vehicle-addon` built jar + source | No — author reference |

## Repo layout discipline

- First-party code: [`yap-first-party/`](../../yap-first-party/README.md)
- YaP-Folia: [`vendor/folia/`](../vendor/folia/) + [FOLIA_FORK.md](../folia/FOLIA_FORK.md)
- Gradle split: `build.gradle.kts` + `gradle/yap-product.gradle.kts`, `yap-release.gradle.kts`, `yap-packaging.gradle.kts`
- **Do not commit** live kernel/link state (`folia-kernel/logs`, `usercache`, `ops`, `link-data/link.properties`, plugin config dirs) — enforced by `.gitignore`

## Version bump checklist

1. Root `build.gradle.kts` `version = "1.0.0.0"`
2. Each subproject `build.gradle.kts` + `plugin.yml` / `link-plugin.json`
3. Rebuild YaP-Folia + `gradle assembleAllReleases`
4. Tag git: `v1.0.0.0`
