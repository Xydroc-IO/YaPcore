# Agent 3 Folia patches (Phase 3–5)

| File | Workstream | Status |
|------|------------|--------|
| `0010-yap-async-chunk-save.patch` | 3.3 Moonrise flush off region thread | **landed** |
| `0011-yap-scoreboard-swmr.patch` | 3.4 CraftScoreboard SWMR | **landed** |
| `0012-yap-entity-tick-budget.patch` | 3.2 hot-region Mob AI count budget | **landed** |
| `0013-yap-region-pool-and-microtick.patch` | 4.x pool metrics, steal knobs, microtick, grid override | **landed** |
| `0014-yap-subregion-force-partition.patch` | 5.x true parallel sub-regions via force-partition + merge-inhibit | **landed** |
| `0015-yap-cross-region-neighbor-defer.patch` | 5.x defer cross-shard neighbor/shape updates; empty-cut preference | **landed** |
| `0016-yap-partition-stability-gates.patch` | 5.x min-entities + coalesce quiet + null-safe entity split | **landed** |
| `0017-yap-partition-empty-buffer-required.patch` | 5.x refuse force-partition without empty-buffer cut | **landed** |
| `0018-yap-corridor-carve-before-partition.patch` | 5.x unload Folia-wide corridor then force-partition | **landed** |
| `0019-yap-post-partition-gap-hold.patch` | 5.x maintain corridor gap after partition (sim re-pin fix) | **landed** |
| `0019` (continued) | partition delay + skip carve when pre-buffered | **landed** (merged into 0019) |
| `0020-yap-version-fetcher.patch` | branding / version | **landed** |
| `0021-yap-advertise-secure-chat.patch` | secure-chat UX | **landed** |
| `0022-yap-hopper-tick-budget.patch` | hopper BE transfer budget (fullcite/heavypop) | **landed** |
| `0023-yap-smart-entity-budget-microtick.patch` | MSPT-gated budget + bosses/near-player + anti-starve | **landed** |
| `0024-yap-subregion-partition-harden.patch` | engage hysteresis + coalesce wall + YapSubRegionCuts | **landed** |

**Apply order:** `folia-patch.sh pre` → `applyAllPatches` → `folia-patch.sh post`  
(see `scripts/build-yap-folia.sh`).

## Knobs

| System property | Default | Effect |
|-----------------|---------|--------|
| `-Dyap.folia.async-chunk-save=true` | **true** (product) | Enqueue Moonrise flush off region thread |
| `-Dyap.folia.scoreboard-swmr=true` | true (product) | Allow Bukkit scoreboard mutations under write lock |
| `-Dyap.folia.entity-tick-budget=N` | **400** (product) | Max **Mob** AI ticks/region tick when hot; 0=off |
| `-Dyap.folia.budget-mspt-threshold=M` | **12** | Engage entity/microtick only if prior MSPT ≥ M (0=always) |
| `-Dyap.folia.entity-tick-max-deferred=A` | **40** | Force-tick after A consecutive skips |
| `-Dyap.folia.hopper-tick-budget=N` | **64** (product) | Max hopper transfers per region tick; soft defer over-cap |
| `-Dyap.folia.microtick-budget-ms=N` | **8** (product) | Soft ms deadline for Mob AI phase (MSPT-gated) |
| `-Dyap.folia.steal-threshold-ms=N` | 3 | WORK_STEALING steal threshold |
| `-Dyap.folia.task-slice-ms=N` | 2 | WORK_STEALING intermediate task slice |
| `-Dyap.folia.grid-exponent=N` | unset | Override `threaded-regions.grid-exponent` (finer = more regions) |
| `-Dyap.folia.region-metrics=false` | true | Disable merge/split/migration counters |
| `-Dyap.folia.subregion-partition=true` | **true** (product) | Force-partition hot regions into parallel Folia shards |
| `-Dyap.folia.subregion-shards=N` | 2 | Shards per partition (2–4) |
| `-Dyap.folia.subregion-mspt-threshold=N` | 20 | MSPT (ms) to advance partition engage streak |
| `-Dyap.folia.subregion-mspt-clear=N` | 16 | Clear engage streak below this (hysteresis) |
| `-Dyap.folia.subregion-min-sections=N` | 4 | Min sections before partition |
| `-Dyap.folia.subregion-min-entities=N` | 32 | Min entities before partition (avoids worldgen shred) |
| `-Dyap.folia.subregion-coalesce-mspt=N` | 8 | Cool MSPT to allow re-merge |
| `-Dyap.folia.subregion-coalesce-ticks=N` | 100 | Cool ticks before coalesce |
| `-Dyap.folia.subregion-coalesce-quiet-ticks=N` | 200 | Post-partition quiet before cool-count can complete |
| `-Dyap.folia.subregion-coalesce-min-wall-ms=N` | **30000** | Min wall-clock ms after partition before coalesce |
| `-Dyap.folia.subregion-carve=true` | true | Unload Folia-wide corridor before force-partition |
| `-Dyap.folia.subregion-carve-cooldown-ms=N` | 8000 | Min ms between carve attempts per region |
| `-Dyap.folia.subregion-carve-max-chunks=N` | 2048 | Cap corridor unload size |
| `-Dyap.folia.subregion-gap-maintain-interval=N` | 10 | Ticks between post-partition corridor gap sweeps |
| `-Dyap.folia.subregion-partition-delay-ticks=N` | 600 | Hot ticks before partition request (~30s @ 20 TPS) |

## Region pool / sub-ticks

Folia’s invariant: **one tick thread owns one region**. True parallel ticking of one
hot contiguous area is done by **force-partitioning** into independent Folia regions
with **merge-inhibition** across shard boundaries so they stay schedulable.

| Blueprint item | YaP delivery |
|----------------|--------------|
| Sub-region micro-ticking (deferral) | Count budget (`0012`) + time-slice (`0013`) |
| **True parallel sub-region ticking** | Force-partition + merge-inhibit (`0014`) + neighbor defer (`0015`) + gates (`0016`/`0017`) + corridor carve (`0018`) |
| Dynamic region merge/split | Upstream Folia + YaP force-split/coalesce + telemetry |
| Thread stealing / pool balance | `WORK_STEALING` + steal/slice knobs (`0013`) |
| Steal/queue metrics | `YapRegionPoolMetrics` + `YapSubRegionPartitioner.snapshot()` |

**Shard border caveat (`0015`):** shape/neighbor updates that target a foreign shard are
queued onto the owning region’s tick thread (same pattern as Folia setblock/POI). Physics
at the cut may lag by up to one region tick. Product default is **ON** with
engage hysteresis + coalesce wall-clock (`0024`). Official population cites use the
**ship profile** (partition on) and disclose `knob_*` in JSON — see `docs/folia/YAP_FOLIA_SOAK.md`.

Log snapshot: `YapRegionPoolMetrics.snapshot()` includes partition counters.

## Citeable bench

Stamp **`20260901T010804Z-budget`** spawncollapse (region MSPT @ chunk 0,0; 8k TNT / 1024 hoppers / 2500 mobs):

| Side | mspt_mean | fuse_ok | entities |
|------|----------:|:-------:|---------:|
| stock Folia | 26.5446 | yes | ~8560 |
| YaP-Folia | 20.5415 | yes | ~8755 |

**−22.6%** with `-Dyap.folia.entity-tick-budget=300` + async-chunk-save. See `docs/BENCH_VS_FOLIA.md`.

Prior stamp **`20260824T234919Z`**: −15.0% (25.25 → 21.45).

**Partition (force-split):** product default **ON** (`0024` hysteresis + 30s coalesce wall).
Folia’s invariant is stronger than “no shared block”: **adjacent regions (within
`2×emptySectionCreateRadius`) cannot tick in parallel** — they go inactive and must merge.
YaP `0017` refuses unsafe cuts. Ship cites keep partition **ON** (disclosed); use
`YAP_BENCH_CITE_BASELINE=1` for async+hopper-only A/B.

**Fair paired soak (stock contiguous vs YaP lobe parallel):** bench
`-Dyap.bench.strip_two_phase=true` + `-Dyap.bench.strip_lobe_gap_half=24` spawns YaP load
on separated lobes (same 8000 TNT / entity totals as stock full strip). Stamp
**`20260901T050321Z-carveFair12`**: stock **27.83** → YaP **22.89** MSPT (**−17.7%**),
both `fuse_ticking_ok`, TNT=8000, yap lobe-spawn 192 chunks vs stock contiguous 819.
**`20260901T052718Z-fullNoCarve`**: partition ON + two-phase auto + carve OFF —
fuse_ok, **−10.6%** MSPT (budget=300, async-save). Use carve OFF for lobe/pre-gap layouts;
carve ON only for contiguous dynamic split (experimental).

Run paired soak:
Pre-gap cite remains valid: **`20260901T022139Z-gap1`** (−22.1%). Budget path:
**`20260901T010804Z-budget`** (−22.6%).

## Perf soak (Next Wave)

- `./scripts/smoke-folia-async-save.sh` — stamp `20260825T014032Z`
- Operator knobs in `server.properties` (**perf knobs default OFF**)
- Profiles: `docs/folia/YAP_FOLIA_SOAK.md` · population cite: `docs/folia/REAL_GAINS.md`
- Parallel shards: `YAP_FOLIA_SUBREGION_PARTITION=true ./scripts/soak-yap-folia.sh perf`
- For steal tuning: set `scheduler: WORK_STEALING` in `folia-kernel/config/paper-global.yml`
