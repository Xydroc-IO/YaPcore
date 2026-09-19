# YaPcore releases (v0.0.0.1)

All first-party artifacts share version **0.0.0.1** (Gradle `version`, plugin `plugin.yml`, Link `link-plugin.json`).

**This line ships as a GitHub prerelease.** `/releases/latest` stays on stable **1.0.0.0**. Operators, packs, and the chassis use tag URLs:

`https://github.com/Xydroc-IO/YaPcore/releases/download/0.0.0.1/{file}`

**Release notes:** [RELEASE_NOTES.md](RELEASE_NOTES.md)  
**License:** release trees include root **`LICENSE`** (GNU GPLv3). See [LICENSING.md](LICENSING.md).

**Game jar:** product path expects **YaP-Folia** (`lib/yap-folia-26.2.jar`) built via `./scripts/build-yap-folia.sh`. Stock Fill Folia is not the release default. Build with `./scripts/build-yap-folia.sh`.

## Build commands

| Task | Output |
|------|--------|
| `./scripts/build-yap-folia.sh` | `lib/yap-folia-26.2.jar` — **required for product path** |
| `gradle assembleRelease` | `build/dist/yapcore-release/` — **linux/** + **windows/** full trees (all plugins) |
| `gradle assembleRelease` | Slim CORE+NETWORK by default (`yapGameplay=false`) |
| `gradle assembleRelease -PyapGameplay=true` | Full box including GAMEPLAY (skills / dungeons / stacker / knobs / disasters) |
| `gradle assembleRelease -PyapGameplay=false` | Explicit slim CORE+NETWORK only |
| `gradle publishReleasesFolder` | **`releases/<version>/`** — trees + linux/windows zips + suite zips |
| Git tag `0.0.0.1` or `v0.*` push | GitHub Actions → prerelease + `yapcore-release-linux.zip` + `-windows.zip` |
| `gradle assembleNetworkSuite` | `build/dist/yap-network-suite.zip` — YaP Link + native link plugins |
| `gradle assembleGameplaySuite` | `build/dist/yap-gameplay-suite.zip` — GAMEPLAY plugins (skills / dungeons / stacker / knobs / disasters) |
| `gradle assemblePluginDist` | `build/dist/yap-plugins/` — flat jar mirror by tier |
| `gradle assembleAllReleases` | Full box + standalone suite zips (under `build/dist/`) |

## Durable release folder

```bash
./scripts/build-yap-folia.sh
gradle publishReleasesFolder
# → releases/0.0.0.1/
#      yapcore-release/linux/   yapcore-release/windows/
#      yapcore-release-linux.zip  yapcore-release-windows.zip
#      yap-network-suite.zip  yap-gameplay-suite.zip
```

The entire `releases/` directory is **gitignored** (local artifacts only). Rebuild anytime with the same task.

## GitHub prerelease assets (tag `0.0.0.1`)

Attach (or refresh with `--clobber`) so `/releases/download/0.0.0.1/{file}` works.
Do **not** rely on `/releases/latest` — that URL ignores prereleases and still serves **1.0.0.0**.

| Asset | Role |
|-------|------|
| `yapcore-release-linux.zip` / `-windows.zip` | Full server boxes (CI also uploads these on tag) |
| `yap-network-suite.zip` / `yap-gameplay-suite.zip` | Standalone suites |
| `yapcore-default.zip` | **Required** for JE pack CDN (`resource-pack-url` default) |
| `yapcore-default.mcpack` | **Required** for Bedrock pack CDN (`resource-pack-bedrock-file`) |
| `client_mods.zip` | Optional Fabric clients — unzip → `client_mods/` with **yap-visuals**, **yap-bag**, **yap-presence**, **yap-blocks**, **yap-staff**, **yap-ultrawide** |

Build clients: `./scripts/build-yap-client-render.sh` → `dist/client-mods/client_mods.zip`.  
Parity smoke: `./scripts/parity/smoke-bedrock-feel.sh`.  
Prefer the zip for releases (one upload). Loose jars stay under `dist/client-mods/` for local installs only.

```bash
gh release create 0.0.0.1 --prerelease --title "0.0.0.1" \
  --notes-file docs/start/RELEASE_NOTES.md -R Xydroc-IO/YaPcore || true
gh release upload 0.0.0.1 \
  releases/0.0.0.1/yapcore-release-linux.zip \
  releases/0.0.0.1/yapcore-release-windows.zip \
  releases/0.0.0.1/yap-network-suite.zip \
  releases/0.0.0.1/yap-gameplay-suite.zip \
  releases/0.0.0.1/yapcore-default.zip \
  releases/0.0.0.1/yapcore-default.mcpack \
  releases/0.0.0.1/client_mods.zip \
  --clobber -R Xydroc-IO/YaPcore
```

Copy `dist/client-mods/client_mods.zip` (and `resourcepacks/yapcore-default.zip` /
`resourcepacks/yapcore-default.mcpack`) into `releases/0.0.0.1/` after building so the durable
folder holds every GitHub asset.

**After Link wire fixes:** `publishReleasesFolder` refreshes `yap-link.jar` inside the
trees/zips. Also copy the shadow jar to **repo-root** `yap-link.jar` — the Swing/web
GUI `LinkProcessManager` prefers that path over `build/libs`.

## Linux / Windows full box

Each OS folder is self-contained:

- `yapcore.jar` — YaPcore chassis + embedded web dashboard
- `lib/yap-folia-*.jar` — **YaP-Folia** game (when builder ran `build-yap-folia.sh`)
- `yap-link.jar` + `link-data/` — native multi-backend proxy (`0.6.0-phase6`)
- `plugins/` — CORE+NETWORK first-party stack (includes **yap-factions** + **yap-conquest**, both opt-in via `enabled: false`)
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

See [WINDOWS.md](WINDOWS.md) and [YAPDB.md](../data/YAPDB.md).

## Standalone add-ons

| Zip | Contents | Default in full box? |
|-----|----------|----------------------|
| **yap-network-suite.zip** | Link proxy + chat/mod/selector/tab/discord bridge plugins | Yes (`link-data/plugins/`) |
| **yap-gameplay-suite.zip** | yap-skills, yap-dungeons, yap-stacker, yap-gameplay-knobs, yap-disasters + stacker/knobs modules | Also included in the full box |

## Repo layout discipline

- First-party code: [`yap-first-party/`](../../yap-first-party/README.md)
- YaP-Folia: [`vendor/folia/`](../vendor/folia/) + [QUICK_START.md](../start/QUICK_START.md)
- Gradle split: `build.gradle.kts` + `gradle/yap-product.gradle.kts`, `yap-release.gradle.kts`, `yap-packaging.gradle.kts`
- **Do not commit** live kernel/link state (`folia-kernel/logs`, `usercache`, `ops`, `link-data/link.properties`, plugin config dirs) — enforced by `.gitignore`

## Refresh release zips (same version)

When docs/plugins change but the product version stays **0.0.0.1**:

```bash
./scripts/build-yap-folia.sh   # if Folia fork patches changed
gradle publishReleasesFolder   # refreshes releases/0.0.0.1/ trees + zips
```

Update [RELEASE_NOTES.md](RELEASE_NOTES.md) “After 0.0.0.1” — do **not** change Gradle `version`.

**Latest:** 2026-09-19 — **0.0.0.1** (no version bump). Product jar `76aeaf3f` (`0073`–`0079`). Fullcite `20260919T165559Z` held 500 at 37.24 ms against stock `20260919T171015Z` ending at 119 (65.03 ms; encoder out of direct memory, not a watchdog). Packs pin `/releases/download/0.0.0.1/{file}`. Rebuild: `gradle publishReleasesFolder -PyapGameplay=true`.

## Version bump checklist (new tag only)

1. Root `build.gradle.kts` `version = "…"`
2. Each subproject `build.gradle.kts` + `plugin.yml` / `link-plugin.json`
3. Rebuild YaP-Folia + `gradle assembleAllReleases` / `publishReleasesFolder`
4. Tag: e.g. **`0.0.0.1`** (also accepted: `v…`)
5. GitHub release: **`--prerelease`** while the line is `0.*`
6. Point `resource-pack-url` at `/releases/download/<tag>/{file}` — not `/releases/latest`
