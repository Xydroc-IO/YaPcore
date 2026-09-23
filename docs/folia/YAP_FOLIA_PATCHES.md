# YaP-Folia patch inventory

Ordered deltas under [`vendor/folia/patches/`](../../vendor/folia/patches/). Authors: **YapLabs** (`folia@yaplabs.com`).

**Apply:** `folia-patch.sh pre` → Folia `applyAllPatches` → `folia-patch.sh post`  
(see [`scripts/folia/build-yap-folia.sh`](../../scripts/folia/build-yap-folia.sh)).

Upstream pin: [`vendor/folia/UPSTREAM.lock`](../../vendor/folia/UPSTREAM.lock) — **`14b7fee` / `ver/26.2.x` / 2026-09-06**. Refresh with `./scripts/folia/vendor-folia.sh --update-lock` then rebuild and re-verify cites.

The pin is still **`14b7fee`**. `0000`–`0033` (**26**) are YaP behavior or repairs to that behavior. `0034`–`0081` (**48**) are Folia-itself improvements on that pin. Product jar md5 `ec017174c8f35273c814c6cbdf9a3989`. Fullcite `20260919T165559Z` held 500 players at both ends, TNT 2400, hoppers 770, 32 villagers, fuse drop 808.5, `into 2 shards`, busiest region 37.24 ms. Stock `20260919T171015Z` started at 500 on that scene and ended at 119, busiest region 65.03 ms. Those 119 left because the encoder ran out of direct memory, not because a watchdog fired. `0073`–`0079` keep spawn search off the cut (including chunk X=−1), skip block spread into the hole, and finish the login handshake without a configuration keepalive after the client is in play. `0080` makes portal-couple reschedule safe; `0081` re-enables Bukkit `createWorld` for YaPWorld / YaPDungeons (unload stays stubbed).

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
| `0018-yap-corridor-carve-before-partition.patch` | Unload Folia-wide corridor then force-partition (`YapCorridorCarver` + Entities/Planner/Unload) | landed |
| `0019-yap-post-partition-gap-hold.patch` | Maintain corridor gap after partition | landed |
| `0020-yap-version-fetcher.patch` | Version fetcher branding | landed |
| `0021-yap-advertise-secure-chat.patch` | Secure-chat advertise UX | landed |
| `0022-yap-hopper-tick-budget.patch` | Hopper BE transfer budget | landed |
| `0023-yap-smart-entity-budget-microtick.patch` | MSPT-gated budget + anti-starve | landed |
| `0024-yap-subregion-partition-harden.patch` | Engage hysteresis + coalesce wall + cuts | landed |
| `0025-yap-encyclopedia-hooks.patch` | Encyclopedia NMS: crop slow/accelerate + fluid tick gate (**defaults off**) | landed |
| `0026-yap-tick-epoch-coordinator.patch` | Per-world epoch-wave barriers + `YapMicroPhase` | landed |
| `0027-yap-region-microphase-tick.patch` | Full `ServerLevel` micro-phase tick split | landed |
| `0028-yap-phase-tagged-task-queue.patch` | Phase-tagged cross-region neighbor/task drain | landed |
| `0029-yap-microphase-budgets.patch` | Naming: AI time-slice vs aligned micro-phases | landed |
| `0030-yap-universal-rtq-phase-tag.patch` | Universal `RegionizedTaskQueue` tick-queue phase tagging | landed |
| `0031-yap-physics-substeps.patch` | Internal travel/move physics sub-steps (combat/feel) | landed |
| `0032-yap-regionized-world-data-split-harden.patch` | YaP force-partition split/merge guards (not an upstream Folia fix) | landed |
| `0033-yap-scheduler-probe.patch` | Lab split + BLOCKS-drain probe (off unless `-Dyap.folia.scheduler-probe`) | landed |
| `0034-yap-ticket-unload-hygiene.patch` | Last-ticket drop (no UNKNOWN) on owning thread; LOADING-only holds | landed |
| `0035-yap-region-ownership-ai-leash-dragon.patch` | Folia PRs 491/495/504: AI sensors, delayed leash, dragon part sync | landed |
| `0036-yap-portal-region-thread-couple.patch` | Folia #469: portal-linked regions tick on the same OS thread | landed |
| `0037-yap-split-scheduler-rtq-harden.patch` | Null-safe scheduler/RTQ split requeue; entity split via `YapRegionSplit` | landed |
| `0038-yap-teleport-bukkit-events.patch` | Folia #490: fire `PlayerTeleportEvent` / portal events on `teleportAsync` | landed |
| `0039-yap-map-autosave-server-storage.patch` | Folia #505/#506: map autosave uses server-global `SavedDataStorage` | landed |
| `0040-yap-debug-subscribers-cme.patch` | Folia #472 / PR 499: disable debug subscriptions; no region-thread HashMap tick | landed |
| `0041-yap-regionizer-packed-spawn-cut.patch` | Native cut flag + ticket clamp + thin gap (`YapRegionizerGap`) | landed |
| `0042-yap-async-brain-end-vehicle-spawn.patch` | Folia #446/#453: defer villager brain during async transform; vehicle END→overworld uses rider respawn | landed |
| `0043-yap-contiguous-bar-relocate-probe.patch` | Same-world `teleportTo` evacuate; probe `gap_bands` / `ticking_regions` (split bar, not BLOCKS lockstep) | landed |
| `0044-yap-fork-correctness.patch` | Cut AABB (not infinite slab); on-thread gap maintain; RTQ requeue on split; portal couple by portal chunk; `synchronize(flush)` waits; never strip PLAYER tickets | landed |
| `0045-yap-contiguous-bar-ticket-gap-hold.patch` | Ticket-level clamp + no sync-load into the cut; keep carve plan so gap hold registers. Lab: `smoke-contiguous-bar` PASS | landed |
| `0046-yap-spawn-portal-cut.patch` | Exact-key this-world cuts; pin nether/end portal chunks; portal-travel tickets skip clamp; `unloadIfCut` only when `blocksAddChunk` | landed |
| `0047-yap-player-loader-cut-null.patch` | Moonrise player loader skips unpinned cut keys; null-safe send so corridor carve cannot NPE the region | landed |
| `0048-yap-inflight-cut-key.patch` | In-flight gap key is `~regionId` so region 0 actually registers; `unloadIfCut` can drop the corridor | landed |
| `0049-yap-ticking-chunk-null-skip.patch` | Skip null entity-ticking slots after mid-tick corridor unload (`tickChunk` / spawn collect) | landed |
| `0050-yap-unload-poi-ownership.patch` | Idempotent `regioniser.removeChunk`; POI search skips off-owner chunks (AcquirePoi TickThread) | landed |
| `0051-yap-fluid-spread-ownership.patch` | Defer `FlowingFluid.spreadTo` onto the owning region after split | landed |
| `0052-yap-cut-missing-chunkholder.patch` | Ticket FULL-load next to a cut must not `Missing chunkholder` | landed |
| `0053-yap-cut-getchunk-unload.patch` | Cut keys do not `getChunk(load)`; player unload skips null holder | landed |
| `0054-yap-cut-full-neighbour-holder.patch` | FULL-load on the kept edge skips null cut neighbours; player send/unload is holder-safe | landed |
| `0055-yap-player-loader-cut-load-count.patch` | Player-loader scheduleChunkLoad iterates queued keys, not the pre-skip ticket count | landed |
| `0056-yap-missing-holder-is-gap.patch` | Null Moonrise holder is a gap (not `Missing chunkholder`); CraftWorld.getChunkAt null-safe | landed |
| `0057-yap-strip-unpinned-cut-tickets.patch` | Strip leftover PLAYER tickets from the cut unless the chunk is pinned | landed |
| `0058-yap-evacuate-cut-players.patch` | Relocate players (not only mobs) out of the corridor so packed spawn can split | landed |
| `0059-yap-player-pad-no-syncload.patch` | Player pad relocate uses loaded-chunk snapTo + internalTeleport; no nested spawn sync-load | landed |
| `0060-yap-loaded-edge-pads.patch` | Landing pads are nearest loaded kept-edge chunks, not the VD rim (restore packed-spawn evacuate) | landed |
| `0061-yap-partition-along-carve-plan.patch` | After carve, force-partition along the pending corridor (not a median recut). Packed spawn was stuck on `force-partition deferred` with no `into 2 shards` | landed |
| `0062-yap-cut-ticket-wall-drop.patch` | Neighbor VD skip must zero the propagator cell; ticket clamp is the bounded section AABB. Still abort if corridor keys remain (no force-unload / no delete) | landed |
| `0063-yap-partition-region-zero.patch` | `requestPartition` accepts region 0 (overworld spawn). 0062 emptied the packed hole then dropped the retry because 0 was treated as unset | landed |
| `0064-yap-spawn-finder-skip-cut.patch` | Death during carve must not `syncLoadNonFull` the empty buffer (region-thread watchdog). Spawn search skips cut keys | landed |
| `0065-yap-kept-sim-tickets-after-cut.patch` | Cut-wall decrease must not demote kept PLUGIN/FORCED chunks below their ticket level (BLOCK_TICKING froze TNT while grass still ran); force-load on owning region; plugin tickets KEEP_DIMENSION_ACTIVE | landed |
| `0066-yap-grass-skip-cut-neighbor.patch` | Grass/mycelium randomTick must not `getChunkAt` a null cut neighbor (0065 ticking shards otherwise halt the server) | landed |
| `0067-yap-cut-neighbor-full-bit.patch` | Registered cut counts as a finished neighbor only for full-chunk status. The loaded-bit still clears, so the hole is not treated as a readable chunk | landed |
| `0068-yap-teleport-join-dest-chunk.patch` | Join commits only on the shard that owns the destination chunk (not region id 0). aiStep skips an off-thread pickup box. isInWall skips a null cut chunk. Teleport accept ignores a cut neighbor | landed |
| `0069-yap-teleport-accept-cut.patch` | Accept halo must not treat the carved corridor as a foreign shard when a region pointer still sits on the other side of the hole | landed |
| `0070-yap-fastclip-cut-miss.patch` | `fastClip` treats a missing corridor chunk as a miss instead of calling `getSections` on null. Superseded for the kept floor by `0072` | landed |
| `0071-yap-carve-edge-keep.patch` | Relocate only an entity whose chunk key is in the corridor. Pads are loaded, at least two chunks off the hole, with a motion-blocking floor | landed |
| `0072-yap-collision-owned-floor.patch` | A cut chunk contributes no collision box. An owned chunk keeps its blocks when the radius-4 miss is only the corridor | landed |
| `0073-yap-spawn-search-cut-section.patch` | Spawn search uses the registered cut, not a player standing in the chunk | landed |
| `0074-yap-spread-skip-cut.patch` | Block spread and grow into a cut section return before snapshotting the missing chunk | landed |
| `0075-yap-login-keepalive-while-queued.patch` | Configuration stays on the global tick so a queued login still gets keepalives | landed |
| `0076-yap-login-queue-keepalive.patch` | Joins are not capped per tick. The play listener is installed before the connection is added. Spawn avoids the cut column | landed |
| `0077-yap-chunk-neg1-and-login-ack.patch` | Spawn search returns null for chunk X=−1 before the cut is registered. Login acknowledgement does not park the global tick | landed |
| `0078-yap-protocol-switch-before-play.patch` | Play packets are sent only after the protocol switch | landed |
| `0079-yap-no-config-keepalive-after-finish.patch` | No configuration keepalive after `finish_configuration`. The client is already in play | landed |
| `0080-yap-portal-couple-safe-reschedule.patch` | Portal-couple partner tick reschedule must not race a dead region | landed |
| `0081-yap-enable-createworld-api.patch` | Unstub Bukkit `createWorld` (Paper body) for ephemeral worlds; keep `unloadWorld` stubbed | landed |

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
| `-Dyap.folia.tick-wave-max-wait-ms=N` | **2** | Soft per-world barrier max wait (must be &gt; 0) |
| `-Dyap.folia.physics-substeps=true` | **true** (product) | N-step travel + collision subdivision inside one tick |
| `-Dyap.folia.physics-substep-count=N` | **4** | Substep count 2–8 |
| `-Dyap.folia.physics-substep-min-move=D` | **0.02** | Min move length to subdivide collision |
| `-Dyap.folia.subregion-partition=true` | **true** (product) | Force-partition hot regions into parallel shards |
| `-Dyap.folia.subregion-carve=true` | **true** (product) | Unload an empty corridor before force-partition (`0018`) |
| `-Dyap.folia.regionizer-cut=true` | **true** (product) | Skip empty-section create / BFS jump / PLAYER ticket refill across a registered cut (`0041`) |
| `-Dyap.folia.regionizer-thin-gap=true` | **true** (product) | 1-section hole is enough when the cut is registered (`0041`) |
| `-Dyap.folia.grid-exponent=3` | **3** (product) | 8-chunk sections so a VD=10 spawn blob has ≥4 sections to cut (`0041`) |
| `-Dyap.folia.subregion-mspt-clear=N` | **16** | Hysteresis vs engage threshold |
| `-Dyap.folia.subregion-coalesce-min-wall-ms=N` | **30000** | Min wall-clock ms after partition before coalesce |
| `-Dyap.folia.scheduler-probe=true` | **false** | Lab: dump split/BLOCKS-drain counters + pulse file. **Not a ship default.** |
| `-Dyap.folia.subregion-carve-force-unload=true` | **false** | Lab: unload corridor even if relocate misses. **Not a ship default.** |
| `-Dyap.folia.ticket-hygiene=true` | **true** (product) | Last ticket on owning thread drops immediately; hold tickets are LOADING-only |
| `-Dyap.folia.portal-couple=true` | **true** (product) | Nether/overworld portal-linked regions steal a due partner tick onto this thread |

Operator soak profiles: [YAP_FOLIA_PATCHES.md](YAP_FOLIA_PATCHES.md). Citeable MSPT: [YAP_FOLIA_PATCHES.md](YAP_FOLIA_PATCHES.md) · [YAP_FOLIA_PATCHES.md](YAP_FOLIA_PATCHES.md).

## Region model (short)

Folia’s invariant: **one tick thread owns one region**. Two regions cannot tick in parallel if their loaded sections still touch — Folia will merge them or violate TickThread. YaP force-partition is therefore a **topological cut**: unload a Folia-legal empty buffer (`0017`/`0018`), `forcePartition`, then keep the gap empty (`0019`/`0041`) so shards stay independent. Each shard is then ordinary Folia: one thread, one 20 TPS clock. Cross-cut neighbors queue onto the owning region (`0015`) and may land on the **next** region tick. That lag is Folia, not a second clock.

YaP split guards (`0016`/`0032`) keep entities, connections, players, block-entities, and chunk lists on the source region when force-partition races a missing target — a repair of **YaP partition**, not a Folia regionizer backport.

**Packed spawn:** stock Folia (and Canvas) will not split a contiguous loaded blob. `0041` registers the corridor as a first-class cut: `addChunk` will not create empty glue sections, merge BFS will not jump the band, and PLAYER/sim tickets will not refill it (a player standing in the strip still pins that chunk). `0062` actually drops the pre-cut ticket *level* on that band — skip without zero left holders at `lvl=MAX` with empty `getTicketsAt`. Ship `folia-grid-exponent=3` so a default view-distance-10 spawn has four 8-chunk sections — enough for left / hole / right. A blob that fits in one section still cannot split; that is the regionizer atom. Partition still refuses if the hole is loaded.

**Split bar:** a **live contiguous** hot region splits and holds without a YaP epoch/microtick barrier. Aligned microticks (`0026`–`0030`) are optional phase tagging. Same-tick BLOCKS lockstep across shards **is** a second clock and is not required for the regionizer to be correct. `0041` is the native cut; `0043` relocates a live corridor; `0045` keeps neighbor sim-distance from refilling the hole; `0046` keeps spawn nether/end frames and dest-search tickets off that clamp. Lab check: `./scripts/folia/smoke-contiguous-bar.sh` (PASS: force+split+gap hold, `pulses_ran=0`).

### Aligned micro/sub-ticks (patches 0026–0030)

When `-Dyap.folia.aligned-microticks=true`, each **logical** 20 TPS region tick is subdivided into `YapMicroPhase` slices (CHUNKS → BLOCKS → ENTITIES → BLOCK_ENTITIES) with soft **per-world** epoch-wave barriers. Every `RegionizedTaskQueue` tick-queue runnable is phase-tagged at `createTickTaskQueue` / same-thread `queueOrExecuteTickTask` (`0030`). Game time still advances once per region tick — **not** a faster simulation clock, **not** hard global lockstep, and **not** the professional split bar. Rollback: set `folia-aligned-microticks=false`.

### Physics sub-steps (patch 0031)

When `-Dyap.folia.physics-substeps=true`, eligible players/combat/projectiles run **N** internal `travel` passes (gravity/friction scaled so net ≈ one vanilla tick) and/or subdivided `Entity.move` collision. Bukkit still sees one entity tick; no extra events; game time unchanged. Improves feel and anti-tunnel — **not** primary MSPT capacity vs stock Folia. Rollback: `folia-physics-substeps=false`.

## Next tick-wise work (not knobs)

Real fork work beyond toggles, when we pick the next stream:

1. **Adaptive per-region tick cadence** — hot regions keep 20 TPS; cold/idle regions drop entity AI / random-tick rate with hysteresis (still one logical day clock).
2. **Smarter activation + simulation distance** — entity/block-entity wake sets driven by player focus and region MSPT, not flat radii.
3. **Redstone / hopper schedule coalescing** — batch cross-chunk neighbor updates and hopper transfers on phase boundaries (extends 0015/0022).
4. **Watchdog-aware phase budgets** — hard soft-deadline per `YapMicroPhase` so one overloaded phase cannot starve the rest of the region tick.
5. **Chunk ticket / load pacing** — rate-limit ticket inflation and gen under join storms so tick threads stay on simulation, not IO.

Capacity still mostly comes from **real Folia shards after a legal empty-buffer cut + budgets + async save**. Phases and physics are optional correctness/feel layers on top — not a second world clock.

### Scheduler proof

Idle smoke does not force-partition. **Contiguous-strip check:** a **contiguous** loaded strip (not pre-gapped) carves, unloads, `forcePartition`s, `RegionizedWorldData.split` (0032) increments, the gap holds, and an off-owner neighbor/RTQ pulse runs on the **owning region thread** without TickThread violation. One region-tick of neighbor lag (`0015`) is acceptable. Same-tick BLOCKS drain under aligned microticks is **not** the pass.

Lab-only (`-Dyap.folia.scheduler-probe`, lowered MSPT threshold / delay / min-sections):

```bash
# Pre-gapped lobes:
./scripts/folia/smoke-partition-cut.sh 240

# Contiguous-strip check — live strip, product VD=10, aligned microticks off.
# Uses lib/yap-folia-26.2-lab.jar when present (does not overwrite the GUI product jar).
./scripts/folia/smoke-contiguous-bar.sh 420
```

Pre-gapped lab last run: `contiguous_carve=false` / `gapHalf=32`. Contiguous-strip pass: `contiguous_carve=true`, `forcePartition` + `RegionizedWorldData.split`, `gap_bands>=1` and `ticking_regions>=2` after a hold under view-distance **10**. Same-tick BLOCKS pulse is **not** the pass.


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
| `folia-aligned-microticks` | **true** | Real micro/sub-tick phases + per-world waves (0026–0030); universal RTQ tagging |
| `folia-micro-phases` | **4** | Phase count when aligned microticks on (2–4) |
| `folia-tick-wave-max-wait-ms` | **2** | Soft barrier max wait ms |
| `folia-physics-substeps` | **true** | Internal travel/move N-step physics (0031); plugin tick stays 20 TPS |
| `folia-physics-substep-count` | **4** | Substep count 2–8 |
| `folia-subregion-partition` | **true** | Parallel shards when hot + geometry allows |
| `folia-subregion-carve` | **true** | Empty corridor unload before force-partition (`0018`) |
| `folia-regionizer-cut` | **true** | Native cut + ticket clamp so a packed-spawn hole holds (`0041`) |
| `folia-grid-exponent` | **3** | 8-chunk sections (packed spawn has enough atoms to cut) |
| `folia-subregion-mspt-clear` | **16** | Hysteresis vs engage threshold (20) |
| `folia-subregion-coalesce-min-wall-ms` | **30000** | Anti-thrash before coalesce |
| `folia-ticket-hygiene` | **true** | Last-ticket drop + LOADING-only holds (`0034`) |
| `folia-portal-couple` | **true** | Portal-linked regions share an OS thread (`0036`) |

**Official cites use this ship profile.** Stock Folia / Canvas ignore YaP `-D` knobs — that is the product delta. Result JSON records `knob_*` fields; `YAP_MSPT_REQUIRE_SHIP_KNOBS=1` fails the gate if micro/subregion/entity are missing or below ship floor.

Scheduler: `folia-kernel/config/paper-global.yml` → `threaded-regions.scheduler: WORK_STEALING`.

## Aligned micro/sub-ticks gate

Ship default **on** (`folia-aligned-microticks=true`) after idle smoke with universal RTQ tagging (`0030`). Wave timeouts log at debug. Barriers are per-world: a region waits only when same-world peers have arrived at the prior phase. This is **optional coherence**, not the professional split bar.

```bash
./scripts/folia/build-yap-folia.sh
YAP_FOLIA_ALIGNED_MICROTICKS=true ./scripts/folia/smoke-folia.sh
./scripts/lifecycle/yapctl soak-compat
./scripts/lifecycle/yapctl cite-fullcite   # disclose knob_aligned_microticks in JSON
```

Rollback: `folia-aligned-microticks=false`.

## Physics sub-steps gate

Ship default **on** (`folia-physics-substeps=true`, patch `0031`). Feel/combat stability, not MSPT capacity.

```bash
./scripts/folia/build-yap-folia.sh
# product defaults already forward -Dyap.folia.physics-substeps=*
./scripts/lifecycle/yapctl soak-compat
```

Rollback: `folia-physics-substeps=false`.

## Profile: ship cite (default)

```bash
# cite-fullcite.sh already exports ship knobs + NO_DIG + VD/sim 8
./scripts/lifecycle/yapctl cite-fullcite

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
| subregion-carve | true |
| aligned-microticks | true (phases=4, wave-max-wait=2) |
| physics-substeps | true (count=4, min-move=0.02) |

See also: [YAP_FOLIA_PATCHES.md](YAP_FOLIA_PATCHES.md) · [TUNE.md](../ops/TUNE.md) (encyclopedia).

## Why micro / subregion / parallel

1. **Smart entity budget + AI time-slice (`microtick-budget-ms`)** — soft-cap Mob AI when a region is hot (≥12 ms MSPT)
2. **Subregion partition** — parallel Folia shards when hot + geometry allows
3. **Aligned micro/sub-ticks** (`folia-aligned-microticks`, patches 0026–0030) — real phase machine inside each logical 20 TPS tick with per-world waves + universal RTQ tagging; **ship-on**
4. **Physics sub-steps** (`folia-physics-substeps`, patch 0031) — N-step travel + collision subdivision for players/combat/projectiles; same plugin tick; **feel**, not capacity

Product features, disclosed on every cite. Baseline A/B (`YAP_BENCH_CITE_BASELINE=1`)
shows async+hopper alone (~−7% heavypop); ship profile adds headroom under hot fullcite.

## Stability

```bash
./scripts/lifecycle/yapctl soak-compat   # PASS 20260904T033554Z (ship knobs ON); also PASS after 0025 jar `20260905T010908Z`
./scripts/lifecycle/yapctl soak-perf 30  # PASS 20260904T033626Z
./scripts/lifecycle/yapctl cite-fullcite # PASS 20260904T040935Z (−5.53%; peak cite −12.40% at shipFc2)
./scripts/lifecycle/yapctl soak-long 12  # PASS 20260905T031507Z — soak-proven (heap/thread slope OK; Folia pid locked 12h)
./scripts/bench/cite-canvas-heavypop.sh 40  # Canvas ≥5% campaign (heavypop)
```

**Soak-proven stamp:** `logs/soak/soak-long-20260905T031507Z.log` — ship knobs on; encyclopedia NMS defaults off.
Encyclopedia E2 NMS (`0025-yap-encyclopedia-hooks.patch`): **defaults off**. Not part of the soft-launch claim.
Enable `gameplay.crop-growth-nms` / `tick-fluids=false` only after:

1. `./scripts/folia/build-yap-folia.sh` (post patches include `002*.patch`)
2. `./scripts/lifecycle/yapctl soak-compat` PASS
3. `/yapknobs status` shows `nmsHooks: present=true`

Without the patch, enabling those knobs logs a WARNING (event-wired encyclopedia still works).

## Anti-gaming

- Disclose knobs; same load proofs; no keepalive-only population cites
- Do not claim “vanilla Folia settings” when ship smart knobs are on
