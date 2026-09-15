# YaP-Folia patch inventory

Ordered deltas under [`vendor/folia/patches/`](../../vendor/folia/patches/). Authors: **YapLabs** (`folia@yaplabs.com`).

**Apply:** `folia-patch.sh pre` → Folia `applyAllPatches` → `folia-patch.sh post`  
(see [`scripts/build-yap-folia.sh`](../../scripts/build-yap-folia.sh)).

Upstream pin: [`vendor/folia/UPSTREAM.lock`](../../vendor/folia/UPSTREAM.lock) — refresh with `./scripts/vendor-folia.sh --update-lock` then rebuild and re-verify cites.

## Patches

| File | Workstream | Status |
|------|------------|--------|
| `0000-yap-branding.patch` | Brand-Id `yaplabs:yap-folia` | landed |
| `0001-yap-teleport-transactions.patch` | Cross-region TP PREPARE/COMMIT/CONFIRM | landed |
| `0010-yap-async-chunk-save.patch` | Moonrise flush off region thread | landed |
| `0011-yap-scoreboard-swmr.patch` | CraftScoreboard SWMR | landed |
| `0012-yap-entity-tick-budget.patch` | Hot-region Mob AI count budget | landed |
| `0013-yap-region-pool-and-microtick.patch` | Pool metrics, steal knobs, microtick, grid override | landed |
| `0014-yap-subregion-force-partition.patch` | Force-partition + merge-inhibit | landed |
| `0015-yap-cross-region-neighbor-defer.patch` | Defer cross-shard neighbor/shape updates | landed |
| `0016-yap-partition-stability-gates.patch` | Min-entities + coalesce quiet + null-safe split | landed |
| `0017-yap-partition-empty-buffer-required.patch` | Refuse force-partition without empty-buffer cut | landed |
| `0018-yap-corridor-carve-before-partition.patch` | Unload Folia-wide corridor then force-partition | landed |
| `0019-yap-post-partition-gap-hold.patch` | Maintain corridor gap after partition | landed |
| `0020-yap-version-fetcher.patch` | Version fetcher branding | landed |
| `0021-yap-advertise-secure-chat.patch` | Secure-chat advertise UX | landed |
| `0022-yap-hopper-tick-budget.patch` | Hopper BE transfer budget | landed |
| `0023-yap-smart-entity-budget-microtick.patch` | MSPT-gated budget + anti-starve | landed |
| `0024-yap-subregion-partition-harden.patch` | Engage hysteresis + coalesce wall + cuts | landed |
| `0025-yap-encyclopedia-hooks.patch` | Encyclopedia NMS: crop slow/accelerate + fluid tick gate (**defaults off**) | landed |
| `0026-yap-tick-epoch-coordinator.patch` | Soft epoch-wave barriers + `YapMicroPhase` | landed |
| `0027-yap-region-microphase-tick.patch` | Full `ServerLevel` micro-phase tick split | landed |
| `0028-yap-phase-tagged-task-queue.patch` | Phase-tagged cross-region neighbor/task drain | landed |
| `0029-yap-microphase-budgets.patch` | Naming: AI time-slice vs aligned micro-phases | landed |
| `0030-yap-universal-rtq-phase-tag.patch` | Universal `RegionizedTaskQueue` tick-queue phase tagging | landed |
| `0031-yap-physics-substeps.patch` | Internal travel/move physics sub-steps (combat/feel) | landed |

Scheduler shim is **not** a Folia patch — it is `yap-sched-agent` (`-javaagent`). See [PLUGINS.md](../plugins/PLUGINS.md).

## Knobs

| System property | Default | Effect |
|-----------------|---------|--------|
| `-Dyap.folia.async-chunk-save=true` | **true** (product) | Enqueue Moonrise flush off region thread |
| `-Dyap.folia.scoreboard-swmr=true` | true (product) | Bukkit scoreboard mutations under write lock |
| `-Dyap.folia.teleport-transactions=true` | true (product) | Cross-region teleport integrity |
| `-Dyap.folia.entity-tick-budget=N` | **400** (product) | Max Mob AI ticks/region tick when hot; 0=off |
| `-Dyap.folia.budget-mspt-threshold=M` | **12** | Engage entity/AI-budget only if prior MSPT ≥ M |
| `-Dyap.folia.entity-tick-max-deferred=A` | **40** | Force-tick after A consecutive skips |
| `-Dyap.folia.hopper-tick-budget=N` | **64** (product) | Max hopper transfers per region tick |
| `-Dyap.folia.microtick-budget-ms=N` | **8** (product) | Soft ms deadline for Mob AI phase (not a finer clock) |
| `-Dyap.folia.aligned-microticks=true` | **true** (product) | Real micro/sub-tick phases + soft cross-region waves |
| `-Dyap.folia.micro-phases=N` | **4** | Phase count 2–4 when aligned microticks on |
| `-Dyap.folia.tick-wave-max-wait-ms=N` | **2** | Soft barrier max wait (must be &gt; 0) |
| `-Dyap.folia.physics-substeps=true` | **true** (product) | N-step travel + collision subdivision inside one tick |
| `-Dyap.folia.physics-substep-count=N` | **4** | Substep count 2–8 |
| `-Dyap.folia.physics-substep-min-move=D` | **0.02** | Min move length to subdivide collision |
| `-Dyap.folia.subregion-partition=true` | **true** (product) | Force-partition hot regions into parallel shards |
| `-Dyap.folia.subregion-mspt-clear=N` | **16** | Hysteresis vs engage threshold |
| `-Dyap.folia.subregion-coalesce-min-wall-ms=N` | **30000** | Min wall-clock ms after partition before coalesce |

Operator soak profiles: [YAP_FOLIA_PATCHES.md](YAP_FOLIA_PATCHES.md). Citeable MSPT: [YAP_FOLIA_PATCHES.md](YAP_FOLIA_PATCHES.md) · [YAP_FOLIA_PATCHES.md](YAP_FOLIA_PATCHES.md).

## Region model (short)

Folia’s invariant: **one tick thread owns one region**. YaP force-partitions hot contiguous areas into independent Folia regions with merge-inhibition so they stay schedulable in parallel. Neighbor updates across shard cuts may lag by up to one region tick (`0015`); with aligned microticks they are phase-tagged (`0028`) and may soft-slip one micro-phase on wave timeout.

### Aligned micro/sub-ticks (patches 0026–0030)

When `-Dyap.folia.aligned-microticks=true`, each **logical** 20 TPS region tick is subdivided into `YapMicroPhase` slices (CHUNKS → BLOCKS → ENTITIES → BLOCK_ENTITIES) with soft epoch-wave barriers across regions in the same ~50 ms wall epoch. Every `RegionizedTaskQueue` tick-queue runnable is phase-tagged at `createTickTaskQueue` / same-thread `queueOrExecuteTickTask` (`0030`). Game time still advances once per region tick — **not** a faster simulation clock and **not** hard global lockstep. Rollback: set `folia-aligned-microticks=false`.

### Physics sub-steps (patch 0031)

When `-Dyap.folia.physics-substeps=true`, eligible players/combat/projectiles run **N** internal `travel` passes (gravity/friction scaled so net ≈ one vanilla tick) and/or subdivided `Entity.move` collision. Bukkit still sees one entity tick; no extra events; game time unchanged. Improves feel and anti-tunnel — **not** primary MSPT capacity vs stock Folia. Rollback: `folia-physics-substeps=false`.

## Next tick-wise work (not knobs)

Real fork work beyond toggles, when we pick the next stream:

1. **Adaptive per-region tick cadence** — hot regions keep 20 TPS; cold/idle regions drop entity AI / random-tick rate with hysteresis (still one logical day clock).
2. **Smarter activation + simulation distance** — entity/block-entity wake sets driven by player focus and region MSPT, not flat radii.
3. **Redstone / hopper schedule coalescing** — batch cross-chunk neighbor updates and hopper transfers on phase boundaries (extends 0015/0022).
4. **Watchdog-aware phase budgets** — hard soft-deadline per `YapMicroPhase` so one overloaded phase cannot starve the rest of the region tick.
5. **Chunk ticket / load pacing** — rate-limit ticket inflation and gen under join storms so tick threads stay on simulation, not IO.

Capacity still mostly comes from **subregion partition + budgets + async save**; phases and physics are correctness/feel layers on top.


---

## Soak gates

Operator guide for YaP-Folia performance knobs and soak gates.
Patch inventory: [YAP_FOLIA_PATCHES.md](YAP_FOLIA_PATCHES.md) · cites: [YAP_FOLIA_PATCHES.md](YAP_FOLIA_PATCHES.md).

## Product defaults (ship) — what we cite

| Knob | Default | Why |
|------|---------|-----|
| `folia-async-chunk-save` | **true** | Moonrise flush off region thread |
| `folia-hopper-tick-budget` | **64** | Soft-defer excess hopper transfers per region tick |
| `folia-scoreboard-swmr` | true | Correctness under Folia |
| `folia-teleport-transactions` | true | Cross-region TP integrity |
| `folia-entity-tick-budget` | **400** | MSPT-gated Mob AI cap (never players/TNT/vehicles/items/bosses) |
| `folia-budget-mspt-threshold` | **12** | Shared gate for entity budget + microtick |
| `folia-entity-tick-max-deferred` | **40** | Anti-starve: force-tick after N consecutive skips |
| `folia-microtick-budget-ms` | **8** | Soft Mob AI deadline on hot regions (AI time-slice; not a finer clock) |
| `folia-aligned-microticks` | **true** | Real micro/sub-tick phases + soft cross-region waves (0026–0030); universal RTQ tagging |
| `folia-micro-phases` | **4** | Phase count when aligned microticks on (2–4) |
| `folia-tick-wave-max-wait-ms` | **2** | Soft barrier max wait ms |
| `folia-physics-substeps` | **true** | Internal travel/move N-step physics (0031); plugin tick stays 20 TPS |
| `folia-physics-substep-count` | **4** | Substep count 2–8 |
| `folia-subregion-partition` | **true** | Parallel shards when hot + geometry allows |
| `folia-subregion-mspt-clear` | **16** | Hysteresis vs engage threshold (20) |
| `folia-subregion-coalesce-min-wall-ms` | **30000** | Anti-thrash before coalesce |

**Official cites use this ship profile.** Stock Folia / Canvas ignore YaP `-D` knobs — that is the product delta. Result JSON records `knob_*` fields; `YAP_MSPT_REQUIRE_SHIP_KNOBS=1` fails the gate if micro/subregion/entity are missing or below ship floor.

Scheduler: `folia-kernel/config/paper-global.yml` → `threaded-regions.scheduler: WORK_STEALING`.

## Aligned micro/sub-ticks gate

Ship default **on** (`folia-aligned-microticks=true`) after smoke PASS with universal RTQ tagging (`0030`). Soft-wave timeout logs are expected under load (not a TickThread failure).

```bash
./scripts/build-yap-folia.sh
YAP_FOLIA_ALIGNED_MICROTICKS=true ./scripts/smoke-folia.sh
./scripts/yapctl soak-compat
./scripts/yapctl cite-fullcite   # disclose knob_aligned_microticks in JSON
```

Rollback: `folia-aligned-microticks=false`.

## Physics sub-steps gate

Ship default **on** (`folia-physics-substeps=true`, patch `0031`). Feel/combat stability, not MSPT capacity.

```bash
./scripts/build-yap-folia.sh
# product defaults already forward -Dyap.folia.physics-substeps=*
./scripts/yapctl soak-compat
```

Rollback: `folia-physics-substeps=false`.

## Profile: ship cite (default)

```bash
# cite-fullcite.sh already exports ship knobs + NO_DIG + VD/sim 8
./scripts/yapctl cite-fullcite

# Heavypop + Canvas peer:
./scripts/bench/cite-canvas-heavypop.sh 40
# or:
YAP_BENCH_COMPETITORS=folia,canvas,yapcore YAP_BENCH_SHUFFLE=0 \
YAP_MSPT_REQUIRE_SHIP_KNOBS=1 \
./scripts/bench/run-vs-folia.sh heavypop 40
```

## Profile: baseline A/B (prove knobs help)

```bash
# Async+hopper only — no smart budget / microtick / partition
YAP_BENCH_CITE_BASELINE=1 \
YAP_FOLIA_ASYNC_CHUNK_SAVE=true YAP_FOLIA_HOPPER_TICK_BUDGET=64 \
./scripts/bench/run-vs-folia.sh heavypop 40
```

Compare baseline vs ship on the same machine/stamp pair.

## Profile: spawn-hot (aggressive — lab)

```bash
YAP_FOLIA_ENTITY_TICK_BUDGET=300 \
YAP_FOLIA_MICROTICK_BUDGET_MS=8 \
YAP_BENCH_FULL_STACK=1 \
./scripts/bench/run-vs-folia.sh spawncollapse 40
```

## Gaming blacklist

- Softening the 5% cite gate or MSPT&lt;2 noise rules
- Physics-off / keepalive-only bots as a “population” cite
- YaP-only lobe layouts vs stock contiguous without fair-paired disclosure
- Claiming chassis JVM time as game-tick MSPT
- Hiding ship knobs — always disclose `knob_*` in JSON / whitepaper
- Claiming “vanilla Folia knobs” when ship smart budget / partition are on


---

## Citeable gains

Prove YaP-Folia **ship knobs** beat stock Folia and rank ahead of Canvas — knobs
**disclosed** in result JSON (`knob_*`).

## Truth table (2026-09-04)

| Scenario | Stamp | Outcome |
|----------|-------|---------|
| **fullcite** ship (peak) | `20260904TshipFc2` | YaP **−12.40%** vs stock — **citeable**; knobs proven |
| **fullcite** re-verify | `20260904T040935Z` | YaP **−5.53%** vs stock — **citeable** ship gate PASS (knobs disclosed) |
| **fullcite** 3-way | `20260904TshipFc` | YaP #1 vs Folia & Canvas (Canvas ~35 mspt); pairwise −4.8% that run (noise) |
| **heavypop** ship | `20260904TshipOn` | YaP **−7.55%** vs stock; **#1 vs Canvas** (−3.85%) |
| **heavypop** baseline | `20260904TshipBase` | YaP **−7.19%** (async+hopper only; smart knobs off) |
| **heavypop** Canvas campaign | `20260904T065505Z` | YaP **−16.56%** vs stock; **−8.09% vs Canvas** — **citeable ≥5%** |

## Ship cite profile

| Knob | Value |
|------|-------|
| async-chunk-save | true |
| hopper-tick-budget | 64 |
| entity-tick-budget | 400 (MSPT-gated @ 12) |
| microtick-budget-ms | 8 |
| subregion-partition | true |
| aligned-microticks | true (phases=4, wave-max-wait=2) |
| physics-substeps | true (count=4, min-move=0.02) |

See also: [YAP_FOLIA_PATCHES.md](YAP_FOLIA_PATCHES.md) · [TUNE.md](../ops/TUNE.md) (encyclopedia).

## Why micro / subregion / parallel

1. **Smart entity budget + AI time-slice (`microtick-budget-ms`)** — soft-cap Mob AI when a region is hot (≥12 ms MSPT)
2. **Subregion partition** — parallel Folia shards when hot + geometry allows
3. **Aligned micro/sub-ticks** (`folia-aligned-microticks`, patches 0026–0030) — real phase machine inside each logical 20 TPS tick with soft cross-region waves + universal RTQ tagging; **ship-on**
4. **Physics sub-steps** (`folia-physics-substeps`, patch 0031) — N-step travel + collision subdivision for players/combat/projectiles; same plugin tick; **feel**, not capacity

Product features, disclosed on every cite. Baseline A/B (`YAP_BENCH_CITE_BASELINE=1`)
shows async+hopper alone (~−7% heavypop); ship profile adds headroom under hot fullcite.

## Stability

```bash
./scripts/yapctl soak-compat   # PASS 20260904T033554Z (ship knobs ON); also PASS after 0025 jar `20260905T010908Z`
./scripts/yapctl soak-perf 30  # PASS 20260904T033626Z
./scripts/yapctl cite-fullcite # PASS 20260904T040935Z (−5.53%; peak cite −12.40% at shipFc2)
./scripts/yapctl soak-long 12  # PASS 20260905T031507Z — soak-proven (heap/thread slope OK; Folia pid locked 12h)
./scripts/bench/cite-canvas-heavypop.sh 40  # Canvas ≥5% campaign (heavypop)
```

**Soak-proven stamp:** `logs/soak/soak-long-20260905T031507Z.log` — ship knobs on; encyclopedia NMS defaults off.
Encyclopedia E2 NMS (`0025-yap-encyclopedia-hooks.patch`): **defaults off**. Not part of the soft-launch claim.
Enable `gameplay.crop-growth-nms` / `tick-fluids=false` only after:

1. `./scripts/build-yap-folia.sh` (post patches include `002*.patch`)
2. `./scripts/yapctl soak-compat` PASS
3. `/yapknobs status` shows `nmsHooks: present=true`

Without the patch, enabling those knobs logs a WARNING (event-wired encyclopedia still works).

## Anti-gaming

- Disclose knobs; same load proofs; no keepalive-only population cites
- Do not claim “vanilla Folia settings” when ship smart knobs are on
