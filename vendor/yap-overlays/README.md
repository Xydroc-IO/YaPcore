# YaP overlays / vendor notes

Phase 3 / **3.5** / **3.6** / **3.7** hooks in the vendored Paper tree pinned by
[`vendor/paper.pin`](../paper.pin) (26.2 build 112 / `c9e894d`), re-applied after
`applyPatches` by `scripts/apply-yap-paper-hooks.sh`:

1. **`TickThread.isTickThread()`** — accepts YapEngine spatial thread names when
   `-Dyapcore.phase3.spatial-tick=true`.
2. **`ServerLevel.tickNonPassenger`** — skips interior non-player entities on
   Paper main; flushed on cores 3–6 under DLM leases. Border non-players go to
   T8 when `spatial-borders=true`.
3. **`ServerLevel` block/fluid/random (Phase 3.5)** — defers interior scheduled
   block/fluid ticks and interior `tickChunk` random work to
   `InteriorWorldTickBridge` → spatial cores under leases.
4. **`ServerLevel` block entities + redstone (Phase 3.6)** — interior
   `TickingBlockEntity` tickers and interior `BlockEventData` (pistons, etc.)
   offer/flush onto quads (`spatial-blockentities` / `spatial-redstone`).
5. **Border TE / block events (Phase 3.7)** — border chunks flush on Thread 8
   under DLM (`spatial-borders`).
6. **Tracker sendChanges (Phase 3.8)** — `ChunkMap.newTrackerTick` offers
   non-player `sendChanges` by quadrant (`spatial-tracker`, **default on**);
   `moonrise$tick` / players / track-untrack stay on Paper main.
7. **Tracker skip-clean + early-out (Phase 3.9)** — skip queueing empty
   `sendChanges` (`spatial-tracker-skip-clean`, **default on**); `ServerEntity`
   cheap early-out on main and spatial. Still **not** Folia player tick.
8. **Tracker dirty-bit snapshot (high-pop)** — `ChunkMap` reuses
   `trackerEntities` array unless add/remove dirtied it (kills per-tick
   `.clone()` tax that only YaP pays for spatial safety). Player-path skip-clean
   before main `sendChanges` for bots. Passenger fast-path in `ServerEntity`.
9. **Tracker interior+border single barrier** — `runParallelTickWithBorder`
   when both queues have work (avoids back-to-back waits).
10. **Barrier coalesce + distant brain (Phase 3.10)** —
   `spatial-coalesce-barriers` merges entity+BE+events into one barrier;
   `spatial-entity-activation` runs Paper EAR on spatial ticks;
   `spatial-distant-brain` throttles far path recomputes / full ticks
   (`YapDistantBrain` — YaP code, not Leaf).
11. Rebuild → **`lib/paper-26.2-yap.jar`** (`scripts/build-vendor-paper.sh`).
Stock Fill Paper is **not** used for default Phase 3 NMS: missing
`lib/paper-*-yap.jar` fails boot. Set `paper-phase3-nms-tick=false` only for
intentional leases/accounting without authoritative interior tick.

**Defaults:** `Phase3PaperRuntime` turns spatial-blockfluid / random /
blockentities / redstone / borders / **tracker** / **tracker-skip-clean** /
**coalesce-barriers** / **entity-activation** / **distant-brain** **on**
if unset (high-pop product). Set `yapcore.phase3.spatial-tracker=false` to leave
`sendChanges` on Paper main. Disable spatial world flags explicitly for lean
idle experiments.

MSPT scoreboard (primary gate = `heavypop`):
[docs/BENCH_VS_PAPER.md](../docs/BENCH_VS_PAPER.md).

## Build

```bash
./scripts/vendor-paper.sh
./scripts/build-vendor-paper.sh
./scripts/start.sh --fg
```

Lean / idle experiment (optional — not the product default):

```bash
export JAVA_TOOL_OPTIONS="-Dyapcore.phase3.spatial-blockentities=false -Dyapcore.phase3.spatial-redstone=false"
```

See [docs/PAPER_YAPENGINE_PORT.md](../docs/PAPER_YAPENGINE_PORT.md).
