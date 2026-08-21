# YaP overlays / vendor notes

Phase 3 / **3.5** / **3.6** hooks in the vendored Paper tree pinned by
[`vendor/paper.pin`](../paper.pin) (26.2 build 112 / `c9e894d`), re-applied after
`applyPatches` by `scripts/apply-yap-paper-hooks.sh`:

1. **`TickThread.isTickThread()`** — accepts YapEngine spatial thread names when
   `-Dyapcore.phase3.spatial-tick=true`.
2. **`ServerLevel.tickNonPassenger`** — skips interior non-player entities on
   Paper main; flushed on cores 3–6 under DLM leases.
3. **`ServerLevel` block/fluid/random (Phase 3.5)** — defers interior scheduled
   block/fluid ticks and interior `tickChunk` random work to
   `InteriorWorldTickBridge` → spatial cores under leases.
4. **`ServerLevel` block entities + redstone (Phase 3.6)** — interior
   `TickingBlockEntity` tickers and interior `BlockEventData` (pistons, etc.)
   offer/flush onto quads (`spatial-blockentities` / `spatial-redstone`).
5. Rebuild → **`lib/paper-26.2-yap.jar`** (`scripts/build-vendor-paper.sh`).

Stock Fill Paper is **not** used for default Phase 3 NMS: missing
`lib/paper-*-yap.jar` fails boot. Set `paper-phase3-nms-tick=false` only for
intentional leases/accounting without authoritative interior tick.

MSPT scoreboard: [docs/BENCH_VS_PAPER.md](../docs/BENCH_VS_PAPER.md).

## Build

```bash
./scripts/vendor-paper.sh
./scripts/build-vendor-paper.sh
./scripts/start.sh --fg
```

Enable Phase 3.6 (opt-in):

```bash
export JAVA_TOOL_OPTIONS="-Dyapcore.phase3.spatial-blockentities=true -Dyapcore.phase3.spatial-redstone=true -Dyapcore.phase3.spatial-blockfluid=true"
```

See [docs/PAPER_YAPENGINE_PORT.md](../docs/PAPER_YAPENGINE_PORT.md).
