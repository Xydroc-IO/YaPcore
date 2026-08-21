# YapEngine 16-Thread Architecture (v1.1)

Optimized 16-thread footprint with dedicated **Boundary Arbitration** (Thread 8)
and consolidated UI sandboxes (Threads 10–11).

**Product context:** YaPcore uses **Paper** as game authority. Phase 3 places
interior entity tick on spatial cores **3–6** under DLM leases; border work uses
Threads **7–8**. See [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md).

| Thread | Component | Role |
|--------|-----------|------|
| 1 | Controller / Watchdog | Tick health, deadlock, crash snapshot |
| 2 | Traffic Cop + SequenceToken | Ingest, µs sequencing, Epoll/Zstd |
| 3–6 | Spatial Game Cores 0–3 | NW / NE / SW / SE quad-tree loops (**Phase 3 tick**) |
| 7 | Chunk Sync DLM & Lease Manager | Atomic leases, sector mutations |
| 8 | Boundary Sync & Entity Handoff | Cross-quad arbitration via DLM leases |
| 9 | Compatibility Bridge | Legacy Spigot/Paper sync marshaling |
| 10 | UI Sandbox 0 | Menus / inventory clicks |
| 11 | UI Sandbox 1 | Scoreboard / bossbar / HUD |
| 12 | Heavy I/O 0 | Database |
| 13 | Heavy I/O 1 | World save / Anvil |
| 14 | Heavy I/O 2 | Resource pack HTTP |
| 15 | Heavy I/O 3 | Bedrock / floodgate queues |
| 16 | Async Worker / Telemetry | Metrics, GC samples, low-pri logging |

## Packages

```
sync/dlm/ChunkSyncDlm.java
sync/boundary/BoundaryArbitrator.java
sync/handoff/ChunkSyncLayer.java          # facade T7+T8
sandbox/ui/UiSandboxPool.java
sandbox/io/HeavyIoSandbox.java + HeavyIoRole
sandbox/telemetry/TelemetryWorker.java
sandbox/PluginSandbox.java                # facade
```

## Flow

```
Traffic Cop → UI 10 → DB IO 12 → Bridge 9 → Spatial 3–6
                                              ↓ (border)
                                    Boundary 8 → DLM 7 lease → apply
Telemetry 16 samples GC / metrics off the hot path.
```

## Run

```bash
gradle runYapEngine
```
