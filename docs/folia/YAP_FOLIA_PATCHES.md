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
| `0025-yap-encyclopedia-hooks.patch` | Encyclopedia NMS hooks (**defaults off**) | landed |
| `0026-yap-tick-epoch-coordinator.patch` | Soft epoch-wave barriers + `YapMicroPhase` | landed |
| `0027-yap-region-microphase-tick.patch` | Full `ServerLevel` micro-phase tick split | landed |
| `0028-yap-phase-tagged-task-queue.patch` | Phase-tagged cross-region neighbor/task drain | landed |
| `0029-yap-microphase-budgets.patch` | Naming: AI time-slice vs aligned micro-phases | landed |
| `0030-yap-universal-rtq-phase-tag.patch` | Universal `RegionizedTaskQueue` tick-queue phase tagging | landed |
| `0031-yap-physics-substeps.patch` | Internal travel/move physics sub-steps (combat/feel) | landed |

Scheduler shim is **not** a Folia patch — it is `yap-sched-agent` (`-javaagent`). See [MODULES_AND_API.md](../plugins/MODULES_AND_API.md).

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

Operator soak profiles: [YAP_FOLIA_SOAK.md](YAP_FOLIA_SOAK.md). Citeable MSPT: [REAL_GAINS.md](REAL_GAINS.md) · [CANVAS_PARITY.md](CANVAS_PARITY.md).

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
