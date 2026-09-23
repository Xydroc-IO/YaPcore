# YaPcore releases (v0.0.0.1)

All first-party artifacts share version **0.0.0.1**.

**Upload from one place only:** the repo-root **`UPLOAD/`** folder.

```bash
# 1) Build packs (source of truth)
./scripts/packs/build-default-resourcepack.sh
./scripts/packs/build-default-bedrock-pack.sh
./scripts/packs/build-yap-client-render.sh   # optional client_mods.zip

# 2) Build release zips
gradle publishReleasesFolder -PyapGameplay=true

# 3) Refresh UPLOAD/ (copies packs from resourcepacks/)
./scripts/release/stage-github-upload.sh

# 4) Push to GitHub — ONLY UPLOAD/
gh release upload 0.0.0.1 UPLOAD/*.{zip,mcpack} --clobber -R Xydroc-IO/YaPcore
# or: ./scripts/release/stage-github-upload.sh --upload
```

| Path | Role |
|------|------|
| **`UPLOAD/`** | **The only folder to upload** (7 files) — open this in the file manager |
| `resourcepacks/yapcore-default.{zip,mcpack}` | Pack source of truth — edit/build here |
| `dist/client-mods/` | Client mod build output (intermediate) |
| `build/dist/` | Gradle assemble output (intermediate, wipe anytime) |
| `releases/<version>/` | Full publish dump (zips + optional unzipped trees) |
| `releases/upload` | Symlink → `UPLOAD/` (legacy path) |

Do **not** upload `yapcore-release/` trees, `dist/`, or anything under an old tag folder.

CDN URL: `https://github.com/Xydroc-IO/YaPcore/releases/download/0.0.0.1/{file}`  
(`/releases/latest` ignores prereleases.)

**Release notes:** [RELEASE_NOTES.md](RELEASE_NOTES.md) · **License:** [LICENSING.md](LICENSING.md)

## Build commands

| Task | Output |
|------|--------|
| `./scripts/folia/build-yap-folia.sh` | `lib/yap-folia-26.2.jar` |
| `gradle assembleRelease` | `build/dist/yapcore-release/` |
| `gradle publishReleasesFolder` | `releases/<version>/` **and** refreshes `UPLOAD/` |
| `./scripts/release/stage-github-upload.sh` | Sync packs → stage `UPLOAD/` |

## GitHub assets (tag `0.0.0.1`)

| Asset | Role |
|-------|------|
| `yapcore-release-linux.zip` / `-windows.zip` | Full server boxes |
| `yap-network-suite.zip` / `yap-gameplay-suite.zip` | Standalone suites |
| `yapcore-default.zip` | JE pack CDN |
| `yapcore-default.mcpack` | Bedrock pack CDN |
| `client_mods.zip` | Optional Fabric clients |

## Linux / Windows full box

After `publishReleasesFolder`, unzipped trees (local testing only) live under
`releases/<version>/yapcore-release/{linux,windows}/` — **not** GitHub assets.

```bash
cd releases/0.0.0.1/yapcore-release/linux && ./start.sh --fg
```

See [QUICK_START.md](QUICK_START.md) for product launch from `build/dist/`.
