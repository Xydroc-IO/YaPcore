# Paper / Spigot plugin compatibility

**Default product path:** Paper is the game (`game-authority=paper`).  
Drop **all** plugin jars into **[`plugins/`](../plugins/)** — one folder.

| Kind | Loader |
|------|--------|
| Paper / Spigot (`plugin.yml` / `paper-plugin.yml`) | Real Paper (via `paper-kernel/plugins` → `../plugins`) |
| YaP next-gen (`yap.yml`) | YaP Compatibility Bridge |
| YaP modules (`module.yml`) | `modules/` (separate) |

`paper-kernel/plugins` is a **symlink** to `plugins/` so Paper and YaP share the same directory. On first start, jars left under an old real `paper-kernel/plugins/` are migrated automatically.

## Quick answer

| Question | Answer |
|----------|--------|
| Will LuckPerms / WorldEdit / Vault / most Paper plugins work? | **Yes** — put them in `plugins/` (same as stock Paper 26.x) |
| Is Paper API coverage complete? | **Yes** on the product path — real Paper owns the API ([PAPER_API_COVERAGE.md](PAPER_API_COVERAGE.md)) |
| Can 1.20–1.21 plugins run on 26.2? | **Often** — built-in Tier A+B rewrite ([PLUGIN_BACKCOMPAT.md](PLUGIN_BACKCOMPAT.md)) |
| Can I use Folia-only plugins? | **No** — YaPcore is not Folia |
| Do I need two plugin folders? | **No** — only `plugins/` |
| Is every NMS/reflection plugin guaranteed? | **No** — same caveats as stock Paper (version pins, remaps) |

## Where jars go

| Jar type | Directory | Loader |
|----------|-----------|--------|
| Paper / Spigot / Purpur-style | **`plugins/`** | Real Paper |
| YaP next-gen (`yap.yml`) | **`plugins/`** | YaP facade |
| YaP modules (`module.yml`) | `modules/` | Module runtime |
| Phase 3 bridge | auto-installed → `plugins/yap-spatial-tick.jar` | Paper |

```bash
cp LuckPerms-Bukkit-*.jar plugins/
./scripts/start.sh --fg
# Confirm enable in paper-kernel/logs/ or logs/server.log
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

### Will work (in `plugins/`)

- Standard Bukkit/Spigot/Paper plugins that work on **stock Paper 26.2**
- Vault / services / **built-in PlaceholderAPI** (`yap-placeholderapi.jar`, plugin name `PlaceholderAPI`)
- Normal schedulers, events, commands, inventories, worlds
- Most popular survival / minigame / chat / permission plugins

### Maybe (same as Paper)

- Plugins pinned to an exact CraftBukkit / NMS mapping
- Plugins that reflect deep into Paper internals
- Plugins sensitive to **entity tick threading** under Phase 3 (rare; report if broken)

### Will not

- **Folia-only** plugins (`RegionScheduler`, regionized APIs)
- Plugins that require a different Minecraft version than the pinned Paper

## Dual loaders, one folder

Under Paper authority, **Paper** loads `plugin.yml` jars; **YaP** loads only `yap.yml` jars from the same `plugins/` folder (no double-enable).

When `game-authority` is not Paper, YaP’s Compatibility Bridge may also load lightweight `plugin.yml` jars (soft-fail) — that path is not full Paper parity.

| Facade feature (yap.yml / non-Paper authority) | Status |
|------------------------------------------------|--------|
| Soft-fail enable (one bad jar ≠ crash server) | Yes |
| Parent-first classloader isolation | Yes (limited) |
| Real world / entity / NMS depth for Paper jars | Use Paper authority + `plugins/` |
| Folia region schedulers | Unsupported |

Details: [PLUGINS.md](PLUGINS.md) · Brigadier/NMS notes: [BRIGADIER_NMS_EVENTS.md](BRIGADIER_NMS_EVENTS.md).

## Phase 3 note

With `paper-phase3-nms-tick=true` and the YaP Paperclip, interior non-player
entities tick on YapEngine cores 3–6. Almost all plugins keep using Bukkit/Paper
APIs on the main thread as usual. If a plugin assumes **all** entity mutations
happen only on the single Paper main thread in ways that break under leased
interior tick, treat it as a Phase 3 bug report — not “Paper incompatible.”

## Verify

```bash
./scripts/check-plugin-layout.sh   # plugins/ unified; paper-kernel/plugins → symlink
./scripts/smoke-paper-plugins.sh   # optional enable smoke
```

Also: [PLUGINS.md](PLUGINS.md) · [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md).

