# YaP-Folia — our game fork

**YaPcore does not ship stock PaperMC Folia as the product game.**  
The default game jar is **YaP-Folia**: PaperMC Folia **26.2** plus ordered YapLabs patches, built to `lib/yap-folia-26.2.jar`.

Stock Fill Folia remains a **fallback / bench** path only (`folia-jar-source=fetch`).

## Product defaults

```properties
game-authority=folia
folia-embed=true
folia-version=26.2
folia-dir=folia-kernel
folia-jar-source=build          # prefer lib/yap-folia-*.jar
folia-teleport-transactions=true
```

Jar resolution (`FoliaFiles.ensureFoliaJar()`):

1. `folia-jar-path` (if set)
2. `lib/yap-folia-{version}.jar` when `folia-jar-source=build` (or `auto` and present)
3. Cached `folia-kernel/folia-*.jar` / `lib/folia-*.jar`
4. PaperMC Fill download when `folia-jar-source=fetch`

## What YaP-Folia is

| Piece | Role |
|-------|------|
| Upstream | PaperMC Folia `ver/26.2.x` pin in `vendor/folia/UPSTREAM.lock` |
| Patches | `vendor/folia/patches/0000`…`0015` (ordered) |
| Brand | Jar metadata → **YaP-Folia** |
| Runtime | Child JVM under YaPcore (`folia-kernel/`); chassis owns edge/I/O |

YapEngine **never** owns world/entity/redstone tick. That is YaP-Folia’s region thread pool.

## Layout

```
vendor/folia/
  UPSTREAM.lock
  patches/          # YaP patches (see table below)
  work/             # gitignored clone (scripts/vendor-folia.sh)
scripts/
  vendor-folia.sh
  folia-patch.sh
  build-yap-folia.sh
  verify-yap-folia.sh
  soak-yap-folia.sh
```

## Patches (current tree)

| Patch | Purpose | Default |
|-------|---------|---------|
| `0000-yap-branding` | Rebrand jar to YaP-Folia | always |
| `0001-yap-teleport-transactions` | Cross-region teleport PREPARE/COMMIT/CONFIRM | **on** (`folia-teleport-transactions=true`) |
| `0010-yap-async-chunk-save` | Moonrise flush off region thread | **off** |
| `0011-yap-scoreboard-swmr` | Scoreboard mutations under SWMR lock | **off** |
| `0012-yap-entity-tick-budget` | Per-region Mob AI tick budget | **off** (0) |
| `0013-yap-region-pool-and-microtick` | Pool metrics, steal/slice knobs, microtick | metrics on; budgets off |
| `0014-yap-subregion-force-partition` | Force-partition hot regions into parallel shards | **off** |
| `0015-yap-cross-region-neighbor-defer` | Defer neighbor/shape updates across shard borders | with partition |

Perf knobs stay **off/safe** unless you turn them on after soak. See `vendor/folia/patches/AGENT3.md` and [YAP_FOLIA_SOAK.md](YAP_FOLIA_SOAK.md).

## Build

```bash
./scripts/vendor-folia.sh          # clone + pin
./scripts/build-yap-folia.sh       # patch → applyAllPatches → jar
# → lib/yap-folia-26.2.jar
```

Requires **JDK 25+**, Git, and network (Paperweight downloads Minecraft + Paper).

```bash
./scripts/verify-yap-folia.sh      # build + smoke with source=build
./scripts/soak-yap-folia.sh compat
FOLIA_JAR_SOURCE=build ./scripts/smoke-folia.sh
```

## Stock Folia (non-product)

```properties
folia-jar-source=fetch
```

```bash
./scripts/fetch-folia.sh
```

Use this for A/B benches against upstream only. Product docs, releases, and smokes assume **YaP-Folia**.

## Adding a patch

1. `./scripts/vendor-folia.sh` — clean pin.
2. Edit Folia tracked patch inputs under `folia-server/paper-patches/`, `minecraft-patches/`, etc., or fold via Folia’s `./rb.sh`.
3. Export ordered patch: `git -C vendor/folia/work diff > vendor/folia/patches/00NN-name.patch`
4. `./scripts/folia-patch.sh --check` then `./scripts/build-yap-folia.sh`
5. Smoke: `FOLIA_JAR_SOURCE=build ./scripts/smoke-folia.sh`

Handoff / file ownership: [FOLIA_FORK_AGENT_HANDOFF.md](FOLIA_FORK_AGENT_HANDOFF.md).

## Refresh upstream

```bash
./scripts/vendor-folia.sh --update-lock
./scripts/folia-patch.sh --check
./scripts/build-yap-folia.sh
```

Commit `UPSTREAM.lock` + any patch rebases together.

## License

Upstream Folia is **GPL-3.0**. YaPcore (this repository) is also **GPLv3**. Shipping
`yap-folia-*.jar` requires offering corresponding source (this tree + patches + build
scripts). See Folia `LICENSE` and [LICENSING.md](../start/LICENSING.md).

## Citeable bench (spawncollapse)

Stamp `20260824T234919Z` — region MSPT @ chunk 0,0 with `-Dyap.folia.entity-tick-budget=300`:

| Side | mspt_mean |
|------|----------:|
| Stock Folia | 25.25 |
| YaP-Folia | 21.45 (−15%) |

Details: [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md).
