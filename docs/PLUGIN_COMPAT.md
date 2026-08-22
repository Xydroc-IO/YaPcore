# Folia / Paper plugin compatibility

**Default product path:** Folia is the game (`game-authority=folia`, `folia-embed=true`).  
Drop **all** plugin jars into **[`plugins/`](../plugins/)** — one folder.

| Kind | Loader |
|------|--------|
| Folia-aware (`plugin.yml` / Folia APIs) | Real Folia (via `folia-kernel/plugins` → `../plugins`) |
| Paper / Spigot (`plugin.yml` / `paper-plugin.yml`) | Real Paper on **legacy** `game-authority=paper` |
| YaP next-gen (`yap.yml`) | YaP Compatibility Bridge |
| YaP modules (`module.yml`) | `modules/` (separate) |

`folia-kernel/plugins` (and legacy `paper-kernel/plugins`) is a **symlink** to `plugins/` so the game and YaP share the same directory. On first start, jars left under an old real kernel `plugins/` are migrated automatically.

## Quick answer

| Question | Answer |
|----------|--------|
| Will Folia-aware plugins work? | **Yes** — product path is Folia (same expectations as stock Folia) |
| Will LuckPerms / WorldEdit / Vault / most **Paper** plugins work? | **Often no** on Folia (same as stock Folia). Use Folia builds, or legacy `game-authority=paper` for Paper benches |
| Is Folia API coverage on product path? | **Yes** — real Folia owns the API |
| Is Paper API complete on Paper path? | **Yes** — legacy Paper authority ([PAPER_API_COVERAGE.md](PAPER_API_COVERAGE.md)) |
| Can 1.20–1.21 plugins run on 26.2? | **Often** on Paper path — Tier A+B rewrite ([PLUGIN_BACKCOMPAT.md](PLUGIN_BACKCOMPAT.md)); Folia still needs Folia-aware jars |
| Can I use Folia-only plugins? | **Yes** on the Folia product path |
| Do I need two plugin folders? | **No** — only `plugins/` |
| Is every NMS/reflection plugin guaranteed? | **No** — same caveats as stock Folia/Paper |

## Where jars go

| Jar type | Directory | Loader |
|----------|-----------|--------|
| Folia-aware | **`plugins/`** | Real Folia (default) |
| Paper / Spigot / Purpur-style | **`plugins/`** | Real Paper (legacy authority) |
| YaP next-gen (`yap.yml`) | **`plugins/`** | YaP facade |
| YaP modules (`module.yml`) | `modules/` | Module runtime |
| Phase 3 bridge (Paper benches only) | `yap-spatial-tick.jar` — **not** Folia product | Paper Phase 3 |
| First-party Folia plugins | `YapSched` + `folia-supported: true` | Folia |

```bash
cp LuckPerms-Bukkit-*.jar plugins/   # prefer Folia-ready builds on product path
./scripts/start.sh --fg
# Confirm enable in folia-kernel/logs/ or logs/server.log
```

YaP command access is permission-node based. Install + apply ranks:

```bash
./scripts/install-luckperms.sh
ranks apply   # after server start
```

See [PERMISSIONS.md](PERMISSIONS.md) · [`examples/luckperms/`](../examples/luckperms/).

Layout check:

```bash
./scripts/check-plugin-layout.sh
```

## What “within reason” means

### Will work (in `plugins/` on Folia product path)

- Folia-aware plugins that work on **stock Folia 26.2**
- Vault / services / **built-in PlaceholderAPI** (`yap-placeholderapi.jar`, plugin name `PlaceholderAPI`) when Folia-compatible
- Normal schedulers / events that respect Folia’s region model
- YaP first-party jars shipped for the product path

### Will work on legacy Paper path

- Standard Bukkit/Spigot/Paper plugins that work on **stock Paper 26.2**
- Most popular survival / minigame / chat / permission plugins

### Maybe (same as Folia / Paper)

- Plugins pinned to an exact CraftBukkit / NMS mapping
- Plugins that reflect deep into Folia/Paper internals
- On **legacy Paper + Phase 3**: plugins sensitive to entity tick threading (rare; report if broken)

### Will not

- Classic Paper-only plugins that assume a single main tick thread **on the Folia product path** (same breakage as stock Folia)
- Plugins that require a different Minecraft version than the pinned Folia/Paper

## Dual loaders, one folder

Under Folia authority, **Folia** loads compatible `plugin.yml` jars; **YaP** loads only `yap.yml` jars from the same `plugins/` folder (no double-enable).

Under legacy Paper authority, **Paper** loads `plugin.yml` jars the same way.

When `game-authority` is neither Folia nor Paper, YaP’s Compatibility Bridge may also load lightweight `plugin.yml` jars (soft-fail) — that path is not full Folia/Paper parity.

| Facade feature (yap.yml / non-game authority) | Status |
|------------------------------------------------|--------|
| Soft-fail enable (one bad jar ≠ crash server) | Yes |
| Parent-first classloader isolation | Yes (limited) |
| Real world / entity / NMS depth | Use Folia (product) or Paper (legacy) + `plugins/` |
| Folia region schedulers | **Supported on Folia product path** |

Details: [PLUGINS.md](PLUGINS.md) · Brigadier/NMS notes: [BRIGADIER_NMS_EVENTS.md](BRIGADIER_NMS_EVENTS.md).

## Phase 3 note (legacy Paper only)

With `game-authority=paper`, `paper-phase3-nms-tick=true`, and the YaP Paperclip,
interior non-player entities tick on YapEngine cores 3–6. **Product defaults keep
Phase 3 off.** Folia path does **not** run Phase 3 spatial tick. Almost all plugins
on the Paper path keep using Bukkit/Paper APIs on the main thread as usual. If a
plugin assumes **all** entity mutations happen only on the single Paper main thread
in ways that break under leased interior tick, treat it as a Phase 3 bug report —
not “Paper incompatible.”

## Verify

```bash
./scripts/check-plugin-layout.sh   # plugins/ unified; kernel plugins → symlink
./scripts/smoke-folia.sh           # Folia product smoke
./scripts/smoke-paper-plugins.sh   # optional Paper-path enable smoke
```

Also: [PLUGINS.md](PLUGINS.md) · [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md) · [WHAT_WE_ARE.md](WHAT_WE_ARE.md).
