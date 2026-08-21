# Paper → YapEngine port

**Product path:** Paper is the game authority. **Phases 1–3 done.**  
**Phases 3.5–3.7 shipped** (interior world ticks, block entities/redstone, border T8) —
spatial flags **default on** for high-pop / heavy-load product.  
**Active gate:** beat stock Paper on **`heavypop`** MSPT — [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)
(not yet won; flush overhead still dominates at current density).  
**Phase 4 (next):** dual-stack + YaP plugins polish (can proceed in parallel with
scoreboard work).

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
| Light idle/entity/farm with lean flags | ✅ prior WIN row (deferral mostly off) |
| Spatial world flags default **on** (3.5–3.7) | ✅ `Phase3PaperRuntime` sets if unset |
| Beat stock Paper on **`heavypop`** (all-on) | ❌ LOSS −42% MSPT — still the product gate |
| Interior **block entities** on quads | ✅ Phase 3.6 (`yapcore.phase3.spatial-blockentities`) |
| Interior **redstone block events** on quads | ✅ Phase 3.6 (`yapcore.phase3.spatial-redstone`) |
| **Border** entities / TE / block events on T8 + DLM | ✅ Phase 3.7 (`yapcore.phase3.spatial-borders`) |
| Instant neighbor-update / cross-quad piston chains | Later hardening (still Paper main) |

**Honest:** Authoritative interior tick (entities + Phase 3.5 world ticks)
**requires** `lib/paper-26.2-yap.jar`. Defaults (`paper-phase3-nms-tick=true`)
**fail closed** if that jar is missing — no silent accounting-only mode. Set
`paper-phase3-nms-tick=false` only when you intentionally want leases/borders
without NMS. **Players always stay on Paper main.**

### Vendor build

```bash
./scripts/vendor-paper.sh          # clone pin c9e894d (26.2 #112)
./scripts/build-vendor-paper.sh    # → lib/paper-26.2-yap.jar
./scripts/start.sh --fg
```

`PaperFiles` prefers `lib/paper-*-yap.jar` over Fill stock.

### Phase 3.7 — Border tick on T8 (DLM leases)

When `yapcore.phase3.spatial-borders=true` (default with Phase 3 NMS),
non-player work in **border chunks** (chunks that touch another Yap quadrant)
is deferred from Paper main onto Thread 8 under a DLM lease:

| Work | Path |
|------|------|
| Border entities | `offerBorderEntity` → T8 `runBorderTickSync` |
| Border block entities | same, `border:blockentities` lease |
| Border redstone block events | same, `border:blockevents` lease |

Players still tick on Paper main. Disable with `-Dyapcore.phase3.spatial-borders=false`.

Rebuild YaP Paperclip after hooks: `./scripts/build-vendor-paper.sh`.

### Phase 3.6 — Block entities + redstone on quads

**Default on** with Phase 3 NMS (high-pop product). `Phase3PaperRuntime` sets
these if unset. To lean out for idle experiments, disable explicitly:

```bash
# Default production / heavypop path (set automatically if unset):
# -Dyapcore.phase3.spatial-blockfluid=true
# -Dyapcore.phase3.spatial-random=true
# -Dyapcore.phase3.spatial-blockentities=true
# -Dyapcore.phase3.spatial-redstone=true
# -Dyapcore.phase3.spatial-borders=true

# Lean / idle experiments only:
-Dyapcore.phase3.spatial-blockentities=false
-Dyapcore.phase3.spatial-redstone=false
```

| Work | On quads (interior) | Border (`spatial-borders`) | Else |
|------|---------------------|---------------------------|------|
| Hoppers, furnaces, chests, … | ✅ DLM on 3–6 | ✅ T8 DLM | Paper main |
| Piston / note / dispenser **block events** | ✅ same-quad | ✅ T8 DLM | Paper main |
| Scheduled redstone (`blockTicks`) | ✅ via blockfluid | ❌ still main | Paper main |
| Instant neighbor wire updates | ❌ | ❌ | Always main |
| Non-player entities | ✅ DLM on 3–6 | ✅ T8 DLM | Paper main |
| Players | — | — | Always Paper main |

Rebuild YaP Paperclip after hooks: `./scripts/build-vendor-paper.sh`.

Classes: `InteriorWorldTickBridge.offerBlockEntity` / `offerBlockEvent`, hooks in `scripts/apply-yap-paper-hooks.sh`.

### Phase 4 — Dual-stack + **full Via + Geyser parity** (own code) + YaP plugins

**Product DoD:** first-party equivalents of **ViaVersion + ViaBackwards +
ViaRewind** and **Geyser (+ Floodgate-class auth)** — no Via\*/Geyser jars.
JE + BE on the Paper-backed world; YaP SYNC/HEAVY/UI pools under Phase 3 leases.

See **[PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md)** for the slice matrix (4.V\* / 4.G\*).

Scaffold today: `ViaStyleRemapper` / `ProtocolCompat`, `GeyserStyleTranslator` /
`CrossplayHub` — expand until parity checklists pass.

## Definition of done (Phase 3)

- [x] `vendor/paper` pin + scripts  
- [x] Interior leased work on cores 3–6  
- [x] Border handoffs via T7/T8  
- [x] Border entity/TE/event tick on T8 under DLM (`spatial-borders`)  
- [x] Rebuildable YaP Paperclip path  
- [x] Docs mark Phase 3 complete  

## Classes

- `com.yapcore.paper.PaperKernel` / `phase3.Phase3PaperRuntime`
- `phase3.YapSpatialTickCoordinator` / `PaperTickBridge`
- `phase3.nms.InteriorEntityTickDriver` / `InteriorWorldTickBridge`
- `phase3-plugin` → `yap-spatial-tick.jar` (auto-installed into unified `plugins/`)
- `bench-plugin` → `yap-mspt-bench.jar`
