# YaPcore releases (v1.0.0.0)

All first-party artifacts share version **1.0.0.0** (Gradle `version`, plugin `plugin.yml`, Link `link-plugin.json`).

## Build commands

| Task | Output |
|------|--------|
| `gradle assembleRelease` | `build/dist/yapcore-release/` — **linux/** + **windows/** full server trees |
| `gradle assembleRelease -PyapGameplay=true` | Same + vehicles, stacker, gameplay-knobs jars & modules |
| Git tag `v*` push | GitHub Actions → `yapcore-release-linux.zip` + `-windows.zip` |
| `gradle assembleNetworkSuite` | `build/dist/yap-network-suite.zip` — YaP Link + native link plugins |
| `gradle assembleGameplaySuite` | `build/dist/yap-gameplay-suite.zip` — GAMEPLAY plugins/modules (standalone) |
| `gradle assembleAddonsRelease` | `build/dist/yap-addons-release.zip` — example vehicle addon |
| `gradle assemblePluginDist` | `build/dist/yap-plugins/` — flat jar mirror by tier |
| `gradle assembleAllReleases` | Full box + all standalone zips |

## Linux / Windows full box

Each OS folder is self-contained:

- `yapcore.jar` — YaPcore chassis + embedded web dashboard
- `yap-link.jar` + `link-data/` — native multi-backend proxy
- `plugins/` — CORE+NETWORK first-party stack (tab, discord, chat, protect, world, …)
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

See [WINDOWS.md](WINDOWS.md) and [MARIADB.md](MARIADB.md).

## Standalone add-ons (also bundled in full release when applicable)

| Zip | Contents | Default in full box? |
|-----|----------|----------------------|
| **yap-network-suite.zip** | Link proxy + chat/mod/selector/tab/discord bridge plugins | Yes (`link-data/plugins/`) |
| **yap-gameplay-suite.zip** | yap-vehicles, yap-stacker, yap-gameplay-knobs + modules + yap-vehicles.zip | Only with `-PyapGameplay=true` |
| **yap-addons-release.zip** | `examples/yap-vehicle-addon` built jar + source | No — author reference |

Operators can drop standalone zips onto an existing tree without rebuilding the main release.

## Repo layout discipline

- First-party code: [`yap-first-party/`](../yap-first-party/README.md)
- Gradle split: `build.gradle.kts` + `gradle/yap-product.gradle.kts`, `yap-release.gradle.kts`, `yap-packaging.gradle.kts`
- **≤500 lines per domain file** — see [PERF_AND_LAYOUT.md](PERF_AND_LAYOUT.md) and [CONTRIBUTING.md](../CONTRIBUTING.md)

## Version bump checklist

1. Root `build.gradle.kts` `version = "1.0.0.0"`
2. Each subproject `build.gradle.kts` + `plugin.yml` / `link-plugin.json`
3. Rebuild: `gradle assembleAllReleases`
4. Tag git: `v1.0.0.0`
