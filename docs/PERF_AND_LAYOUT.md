# Performance & domain layout

**Product context:** Phase 3 tick uses `BitwiseQuadrantIndex` + DLM leases on
spatial cores 3–6. See [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md).

## SequenceToken (microsecond precision)

| Class | Role |
|-------|------|
| `sequencing/SequenceClock` | Monotonic µs clock (`nanoTime`, never regresses) |
| `sequencing/SequenceToken` | `globalId` + per-stream `streamSeq` + `ingestMicros` |
| `sequencing/StrictOrderedQueue` | Gap-strict release on **streamSeq** — never runs click #2 before #1 on that player |
| `sequencing/InteractionSequencer` | Per-player multiplexed ordered lanes |

Traffic Cop stamps every interaction; ready items dispatch only in per-player order.
Global ids stay unique for tracing; stream seqs stay independent so Steve never waits on Alex.

## Native I/O (Thread 2)

`network/traffic/NativeEventLoops` selects:
- **Linux** → Epoll (`EpollEventLoopGroup` / `EpollServerSocketChannel`)
- **macOS** → KQueue
- else → NIO fallback

Used by YapEngine Traffic Cop bind path and YaPcore `DualStackGateway` Java listener.

## Native compression

`network/compression/ZstdPacketCompressor` via **zstd-jni** — no `java.util.zip.Deflater` locks on the packet path.

## Bitwise quadrants

`core/spatial/BitwiseQuadrantIndex` packs chunk X/Z into a `long` and resolves NW/NE/SW/SE with only `>>` / `&` (no string or HashMap lookup on the hot path).

## Generational ZGC + NUMA

Deployment-only: see [ZGC_NUMA.md](ZGC_NUMA.md). Scripts use `-XX:+UseZGC`, pinned heap, `-XX:+UseNUMA`, and optional `numactl --cpunodebind=0 --membind=0` (`./scripts/start-prod.sh`).

## Thread map v1.1

See [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md) — DLM (7) / Boundary (8), UI 10–11, Heavy I/O 12–15, Telemetry 16.

## Package map (≤500 lines / file)

```
com.yaplabs.yapengine/
  sequencing/          SequenceToken, Clock, StrictOrderedQueue, InteractionSequencer
  network/traffic/     TrafficCop, NativeEventLoops
  network/compression/ PacketCompressor, Zstd*, PacketCompressors
  core/spatial/        BitwiseQuadrantIndex, SpatialQuadrant, QuadTree*, SpatialGameLoop, ParallelGameCore
  sync/lease/          AtomicLeaseManager
  sync/dlm/            ChunkSyncDlm (Thread 7)
  sync/boundary/       BoundaryArbitrator (Thread 8)
  sync/handoff/        ChunkSyncLayer facade
  sandbox/ui/          UiSandboxPool (10–11)
  sandbox/io/          HeavyIoSandbox + roles (12–15)
  sandbox/telemetry/   TelemetryWorker (16)
  sandbox/             PluginSandbox facade
  bridge/              CompatibilityBridge (9)
  controller/          EngineController (1)

com.yapcore.gui/
  theme/GuiTheme
  panels/PluginsPanel, PacksPanel
  ControlPanel         (orchestrator only)
```
