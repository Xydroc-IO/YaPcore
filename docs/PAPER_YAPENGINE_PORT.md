# Paper → YapEngine port

**Product path:** Paper is the game authority. **Phases 1–3 done.**  
**Phase 3.5 (active):** beat stock Paper on a public MSPT scoreboard — expand leased interior tick (block/fluid/random) + [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md).  
**Phase 4 (next):** dual-stack + YaP plugins polished on the Paper-backed world (after scoreboard).

Not a from-scratch vanilla rewrite.

## Phases

### Phase 1 — Wrap + TCP proxy ✅
```properties
game-authority=paper
paper-embed=false
paper-port=25567
```

### Phase 2 — Paper owns public JE ✅
```properties
game-authority=paper
paper-embed=true
paper-phase3-tick-bridge=false
paper-version=26.2
paper-dir=paper-kernel
```

### Phase 3 — Tick → YapEngine cores 3–6 ✅

```properties
game-authority=paper
paper-embed=true
paper-phase3-tick-bridge=true
paper-phase3-nms-tick=true
paper-version=26.2
paper-dir=paper-kernel
```

**Java 25+.** Start via `./scripts/start.sh` — cds into `paper-dir` (Paperclip Path cwd is fixed at JVM start; `-Dyapcore.home` stays on the project root).

| Milestone | Status |
|-----------|--------|
| Same-JVM Paperclip (`Phase3PaperClassLoader`) | ✅ |
| `YapSpatialTickCoordinator` fan-out + barrier | ✅ |
| DLM leases on interior chunk work (`runLeased`) | ✅ |
| Border chunks → `ChunkSyncLayer` / T7–T8 handoffs | ✅ |
| Bridge plugin snapshots entities on main, ticks on 3–6 | ✅ |
| Vendor Paper 26.2-112 (`vendor/paper.pin`) | ✅ |
| TickThread + ServerLevel patches for spatial NMS tick | ✅ → `lib/paper-26.2-yap.jar` |
| Interior scheduled block/fluid ticks on cores 3–6 | ✅ Phase 3.5 (`yapcore.phase3.spatial-blockfluid`) |
| Interior random ticks on cores 3–6 | ✅ Phase 3.5 (`yapcore.phase3.spatial-random`) |
| Public vs-Paper MSPT bench | ✅ `scripts/bench/run-vs-paper.sh` — see [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md) |
| Beat stock Paper on light idle/farm | ✅ idle WIN; farm ~tie; entity TNT WIN — [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md) |
| Interior **block entities** on quads | ✅ Phase 3.6 (`yapcore.phase3.spatial-blockentities`) |
| Interior **redstone block events** on quads | ✅ Phase 3.6 (`yapcore.phase3.spatial-redstone`) |
| Instant neighbor-update / cross-quad piston chains | Later hardening (border + cross-quad stay on main) |

**Honest:** Authoritative interior tick (entities + Phase 3.5 world ticks)
**requires** `lib/paper-26.2-yap.jar`. Defaults (`paper-phase3-nms-tick=true`)
**fail closed** if that jar is missing — no silent accounting-only mode. Set
`paper-phase3-nms-tick=false` only when you intentionally want leases/borders
without NMS. Players always stay on Paper main.

### Vendor build

```bash
./scripts/vendor-paper.sh          # clone pin c9e894d (26.2 #112)
./scripts/build-vendor-paper.sh    # → lib/paper-26.2-yap.jar
./scripts/start.sh --fg
```

`PaperFiles` prefers `lib/paper-*-yap.jar` over Fill stock.

### Phase 3.6 — Block entities + redstone on quads

Opt-in (defaults **off** for idle MSPT scoreboard):

```bash
# JVM / start flags
-Dyapcore.phase3.spatial-blockentities=true
-Dyapcore.phase3.spatial-redstone=true
# scheduled redstone (repeaters/observers) also wants:
-Dyapcore.phase3.spatial-blockfluid=true
```

| Work | On quads (interior) | Stays on Paper main |
|------|---------------------|---------------------|
| Hoppers, furnaces, chests, … | ✅ under DLM leases | Border chunks |
| Piston / note / dispenser **block events** | ✅ same-quad interior | Border + reschedule queue |
| Scheduled redstone (`blockTicks`) | ✅ via Phase 3.5 blockfluid | Border |
| Instant neighbor wire updates | ❌ not yet | Always main |

Rebuild YaP Paperclip after hooks: `./scripts/build-vendor-paper.sh`.

Classes: `InteriorWorldTickBridge.offerBlockEntity` / `offerBlockEvent`, hooks in `scripts/apply-yap-paper-hooks.sh`.

### Phase 4 — Dual-stack + YaP plugins (next)

JE + BE on the Paper-backed world as one polished product story; YaP SYNC/HEAVY/UI pools aligned with Compatibility Bridge rules under Phase 3 leases.

## Definition of done (Phase 3)

- [x] `vendor/paper` pin + scripts  
- [x] Interior leased work on cores 3–6  
- [x] Border handoffs via T7/T8  
- [x] Rebuildable YaP Paperclip path  
- [x] Docs mark Phase 3 complete  

## Classes

- `com.yapcore.paper.PaperKernel` / `phase3.Phase3PaperRuntime`
- `phase3.YapSpatialTickCoordinator` / `PaperTickBridge`
- `phase3.nms.InteriorEntityTickDriver` / `InteriorWorldTickBridge`
- `phase3-plugin` → `yap-spatial-tick.jar` (auto-installed into unified `plugins/`)
- `bench-plugin` → `yap-mspt-bench.jar`
