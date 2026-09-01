# YapEngine Chassis Architecture (v2.0)

YaPcore’s **product stack** is three processes, not one “16-thread game engine”:

| Layer | What ticks the world? | Where |
|-------|------------------------|-------|
| **YaP Link** | Nothing (proxy only) | Separate JVM — `yap-first-party/link/native/` (native, phased) |
| **YapEngine chassis** | Nothing (edge + I/O + sandboxes) | YaPcore parent process |
| **Folia** | **Yes** — chunks, entities, redstone, commands | Embedded child JVM (`folia-kernel/`) |

**Folia owns game tick.** YapEngine owns the **public edge** (Netty, dual-stack, sequencing,
Compatibility Bridge, UI/Heavy sandboxes, telemetry). YaP Link fronts multi-backend
networks. Do **not** run a second tick engine beside Folia.

Legacy **Paper + Phase 3 spatial** (interior NMS tick on chassis cores 3–6) remains
**opt-in for benches only** — defaults off. See [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md) and [FULL_RUNDOWN.md](../overview/FULL_RUNDOWN.md).

---

## Process diagram (product path)

```
Clients (JE TCP / BE UDP)
        │
        ▼ optional
   YaP Link :25565  ──modern forwarding──►  Folia backend(s)
        │
        ▼ direct / wrapped
 YapEngine Traffic Cop (T2)     ← ingest, SequenceToken, compression
        │
        ├─► Dual-stack / crossplay hub (chassis)
        │
        ├─► Compatibility Bridge (T9)  ← YaP plugin SYNC marshaling → Folia APIs
        │
        ├─► UI sandboxes (T10–11) · Heavy I/O (T12–15) · Telemetry (T16)
        │
        └─► Folia child JVM  ← **GAME TICK** (RegionScheduler / region thread pool)
              folia-kernel/     dynamic region threads — not fixed to T3–6
```

**Scale-out:** split clumps across Link backends; make each hot Folia region cheaper
(fork patches, view/sim policy, first-party plugin discipline) — not a parallel player
ticker on the same region state.

---

## Sixteen logical channels (chassis only)

Fixed footprint inside the YaPcore parent process. Thread IDs are **logical roles**,
not Folia region threads.

| Thread | Component | Role (v2.0 product) |
|--------|-----------|---------------------|
| 1 | Controller / Watchdog | Process health, deadlock, crash snapshot |
| 2 | Traffic Cop + SequenceToken | Ingest, µs sequencing, Epoll/Zstd |
| 3–6 | Chassis worker quads | Sequenced plugin/bridge tasks by quadrant; **legacy Phase 3 NMS tick on Paper benches only** |
| 7 | Chunk Sync DLM & Lease Manager | Leases for **Paper Phase 3** spatial path; idle on Folia product path |
| 8 | Boundary Sync & Entity Handoff | Cross-quad arbitration + **Phase 3.7 border tick** (Paper legacy only) |
| 9 | Compatibility Bridge | Legacy Spigot/Paper sync marshaling → Folia game APIs |
| 10 | UI Sandbox 0 | Menus / inventory clicks |
| 11 | UI Sandbox 1 | Scoreboard / bossbar / HUD |
| 12 | Heavy I/O 0 | Database (MariaDB pool) |
| 13 | Heavy I/O 1 | World save / Anvil (chassis-side) |
| 14 | Heavy I/O 2 | Resource pack HTTP |
| 15 | Heavy I/O 3 | Bedrock / floodgate queues |
| 16 | Async Worker / Telemetry | Metrics, GC samples, low-pri logging |

### What moved out of “game cores 3–6”

| Old story (v1.1) | v2.0 product |
|------------------|--------------|
| Cores 3–6 run Minecraft world tick | **Folia region threads** run world tick |
| YapEngine = game + edge | YapEngine = **slim chassis** (edge + I/O + ops) |
| Compete on spatial MSPT | Compete on **Folia clump MSPT** + product stack — [BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md) |

Cores 3–6 still boot for quadrant-routed **chassis work** (bridge drain, demos,
legacy Phase 3 when explicitly enabled). They are **not** the product game authority.

---

## Plugin pools (unchanged contract)

First-party YaP plugins use explicit pools — never block Folia region tick with DB/HTTP:

| Pool | Threads | Use for |
|------|---------|---------|
| **SYNC** | Bridge → Folia region APIs | Inventory, block mutations, entity changes |
| **UI** | 10–11 | Menus, scoreboards |
| **HEAVY** | 12–15 | MariaDB, HTTP packs, file I/O |

On Folia, prefer [`YapSched`](../folia/YAP_SCHED.md) (`RegionScheduler`, `AsyncScheduler`,
`EntityScheduler`) over `BukkitScheduler` sync APIs.

---

## Packages

```
sync/dlm/ChunkSyncDlm.java              # Paper Phase 3 legacy
sync/boundary/BoundaryArbitrator.java   # Paper Phase 3 legacy
sync/handoff/ChunkSyncLayer.java
sandbox/ui/UiSandboxPool.java
sandbox/io/HeavyIoSandbox.java + HeavyIoRole
sandbox/telemetry/TelemetryWorker.java
sandbox/PluginSandbox.java
core/spatial/ParallelGameCore.java      # chassis quads T3–6 (not Folia tick)
```

---

## Flow (Folia product path)

```
Traffic Cop → Bridge 9 → Folia RegionScheduler (game)
         ↘ UI 10–11 / Heavy 12–15 (async work off hot path)
Telemetry 16 samples GC / metrics.
Chassis quads 3–6: sequenced bridge tasks only (no Phase 3 unless Paper legacy).
```

---

## Run (chassis demo)

```bash
gradle runYapEngine
```

Product boot: `./scripts/start.sh --fg` with `game-authority=folia`.

---

## See also

- [WHAT_WE_ARE.md](../overview/WHAT_WE_ARE.md) — identity
- [FULL_RUNDOWN.md](../overview/FULL_RUNDOWN.md) — full architecture rundown
- [YAP_LINK.md](../network/YAP_LINK.md) — proxy layer
- [FULL_RUNDOWN.md](../overview/FULL_RUNDOWN.md) — legacy Phase 3 spatial (benches)
