# Performance & domain layout

**Product context:** Folia owns the game tick (`game-authority=folia`). YapEngine
is chassis only (Netty / dual-stack / I/O / ops). Legacy Phase 3 Paper spatial tick is opt-in for benches — see [BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md).

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

## Chassis thread map (v2.0)

See [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md) — Folia owns game tick; chassis T1–16 cover edge/I/O.

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

com.yapcore/
  folia/               FoliaKernel, FoliaFiles (orchestrators)
  folia/process/       FoliaProcess (JVM lifecycle)
  folia/surface/       FoliaSurface (product markers)
  game/command/        GameCommandBridge (BE/console → game)
  game/sched/          GameSchedulers (same-JVM GlobalRegion reflect)
  config/              ServerConfig facade
  config/authority/    FoliaAuthorityConfig, PaperAuthorityConfig, GameAuthorityConfig
  config/proxy/        VelocityProxyConfig
  config/protocol/     ProtocolEdgeConfig
  server/              YaPcoreServer orchestrator
  server/console/      ServerConsoleCommands
  protocol/            DualStackGateway facade
  protocol/gateway/    JavaListenerBoot, BedrockUdpBoot
  protocol/via/proxy/  ViaProxyHandler helpers
  crossplay/bedrock/bridge/    BedrockGameplayBridge helpers
  crossplay/bedrock/paper/     BedrockPaperWorldSync helpers
  crossplay/bedrock/codec/     BedrockPacketCodec helpers
  crossplay/bedrock/inventory/ BedrockInventoryAuthority helpers
  web/auth|http|api/   WebDashboard helpers
  api/                 ApiCoverage (Folia product claim)

com.yapcore.sched/     YapSched + YapTask (first-party plugin module :yap-sched)

com.yapcore.gui/
  theme/GuiTheme
  panels/PluginsPanel, PacksPanel
  ControlPanel         (orchestrator only)

com.yapcore.link/          (yap-first-party/link/native — own JVM)
  protocol/                McCodec, McFrameCodec (inbound), McOutboundPacketEncoder,
                           McCompressionCodec (inbound + wrapOutbound)
  forwarding/              ModernForwarding (velocity:player_info)
  status/                  StatusPing
  …                        ClientSession* login/bridge/relay (split by phase)

com.yapcore.protocol/      (yap-first-party/link/protocol — shared wire, no Netty server)
```

**Rule:** prefer ≤500 lines per domain file; split by folder when a class grows
(process vs surface vs orchestrator). Link outbound framing+zlib stays in
`McOutboundPacketEncoder` — do not reintroduce stacked Netty compress+frame encoders.
