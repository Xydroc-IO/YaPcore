# Paper → YapEngine port (legacy path)

> **Product default is Folia**, not Paper.  
> `game-authority=folia`, `folia-embed=true` — Folia owns the game; YapEngine is the chassis;
> YaP Link fronts multi-backend networks. Phase 3 Paper spatial flags **default off**
> and do **not** run on the Folia path.  
> This document is the **legacy Paper + Phase 3 spatial** plan for benches and ops who
> opt back into Paper authority. See [WHAT_WE_ARE.md](WHAT_WE_ARE.md) · [YAP_LINK.md](YAP_LINK.md).

**Legacy path:** Paper is the game authority when `game-authority=paper`. **Phases 1–3 done as code.**  
**Phases 3.5–3.7 shipped** (interior world ticks, block entities/redstone, border T8) —
spatial flags **default off** on the product path; re-enable only for Paperclip benches.  
**Active product gate:** fair highpop MSPT (~100 active bots) — [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)
(250 keepalive = HOLD-ONLY).  
**Phase 4:** dual-stack + YaP plugins (join green; play depth deepening) on the **Folia** world.

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

### Phase 3 — Tick → YapEngine cores 3–6 ✅ (legacy / opt-in)

```properties
game-authority=paper
paper-embed=true
paper-phase3-tick-bridge=true
paper-phase3-nms-tick=true
paper-version=26.2
paper-dir=paper-kernel
```

**Product defaults keep these flags false.** Enable only for Paper spatial benches.

**Java 25+.** Start via `./scripts/start.sh` — cds into `paper-dir` when Paper authority is set (Paperclip Path cwd is fixed at JVM start; `-Dyapcore.home` stays on the project root).

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
| Spatial world flags **product default** | ❌ **Off** — retired as product default; opt-in for benches |
| Interior **block entities** on quads | ✅ Phase 3.6 (`yapcore.phase3.spatial-blockentities`) |
| Interior **redstone block events** on quads | ✅ Phase 3.6 (`yapcore.phase3.spatial-redstone`) |
| **Border** entities / TE / block events on T8 + DLM | ✅ Phase 3.7 (`yapcore.phase3.spatial-borders`) |
| Instant neighbor-update / cross-quad piston chains | Later hardening (still Paper main) |

**Honest:** Authoritative interior tick (entities + Phase 3.5 world ticks)
**requires** `lib/paper-26.2-yap.jar` when Phase 3 NMS is enabled.
**Player tick + Bukkit events stay on Paper main**; Phase 3.12 may
export player tracker `sendChanges` on spatial cores after that tick.
Folia product path never uses this Phase 3 spatial tick.

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

**Opt-in** with Phase 3 NMS on the **legacy Paper** path (not product default).
`Phase3PaperRuntime` may set these if you enable Phase 3 for benches. To lean out:

```bash
# Legacy Paper Phase 3 bench path (explicit):
# -Dyapcore.phase3.spatial-blockfluid=true
# -Dyapcore.phase3.spatial-random=true
# -Dyapcore.phase3.spatial-blockentities=true
# -Dyapcore.phase3.spatial-redstone=true
# -Dyapcore.phase3.spatial-borders=true

# Lean / idle experiments:
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
| Players | — | — | Tick + events on Paper main; **sendChanges** may flush on spatial (3.12) |

Rebuild YaP Paperclip after hooks: `./scripts/build-vendor-paper.sh`.

Classes: `InteriorWorldTickBridge.offerBlockEntity` / `offerBlockEvent`, hooks in `scripts/apply-yap-paper-hooks.sh`.

### Phase 4 — Dual-stack + **Via + Geyser parity** (own code) + YaP plugins

**Product DoD:** first-party equivalents of **ViaVersion + ViaBackwards +
ViaRewind** and **Geyser (+ Floodgate-class auth)** — no Via\*/Geyser jars.
JE + BE on the **Folia**-backed world (product default); YaP SYNC/HEAVY/UI pools
on the chassis. Join/spawn green; play depth deepening — not full Geyser play
parity yet.

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
