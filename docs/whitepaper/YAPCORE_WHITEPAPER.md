# YaPcore: A Sixteen-Thread Architecture for Concurrent Minecraft-Class Game Servers

**YapLabs Technical Whitepaper**  
Version 0.1 · August 2026  
Document ID: `YAP-WP-16T-001`

> Prefer plain English? See [YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md).

---

## Abstract

Minecraft-class game servers traditionally serialize world mutation, plugin callbacks, and network I/O onto a single “main” thread, trading simplicity for latency under load. YaPcore (YapEngine) proposes a **fixed sixteen-thread** partitioning of server work: watchdog control, traffic/sequencing, parallel spatial game cores, chunk synchronization with deferred lease management, a legacy **Compatibility Bridge**, UI sandboxes, and heavy I/O workers. A **SequenceToken** model provides per-stream and global ordering without requiring every subsystem to share one lock. Legacy Spigot/Paper plugins and first-party YaP plugins/modules execute under an explicit **SYNC / HEAVY / UI** pool contract so inventory and block mutations never race the spatial cores. The shipping product path uses **Paper as game authority**, with Phase 3 interior entity tick on spatial cores 3–6 under DLM leases.

This paper describes the architecture, concurrency invariants, networking and crossplay stance, plugin/module surface, and evaluation methodology. It is intended for systems researchers, server operators, and plugin authors evaluating YaPcore as a research and production platform.

**Keywords:** game server concurrency; spatial partitioning; plugin compatibility; Minecraft protocol; generational ZGC; NUMA affinity.

---

## 1. Introduction

### 1.1 Problem

Vanilla and Paper-derived Java Edition servers concentrate authoritative world state updates on one thread (~20 TPS). Plugins that perform database or HTTP work on that thread stall ticks. Conversely, unsynchronized parallel mutation produces torn chunks, duplicate entities, and inventory races. Bedrock clients further require a second transport (UDP) while operators demand a single shared world.

### 1.2 Contribution

YaPcore contributes:

1. A **named 16-thread matrix** with clear ownership of networking, physics/spatial loops, sync, UI, and I/O.
2. **SequenceToken** sequencing for ordered handoff across threads.
3. A **Compatibility Bridge** that stages legacy Bukkit mutations onto the game-core drain window.
4. Dual-stack **Java TCP + Bedrock UDP** ingress with optional shared listen port and Geyser-class crossplay hub.
5. A **three-tier extension model**: Paper-style plugins, YaP plugins, and fine-tune modules.

### 1.3 Non-goals

YaPcore does not claim bit-identical Paper API coverage on day one, nor full NeoForge dedicated-server semantics. API surface grows against measured plugin import demand (`ApiCoverage`).

### 1.4 Product status (August 2026)

YaPcore’s shipping product path uses **Paper as game authority**. Phases 1–2
(wrap / Paper owns public JE) and **Phase 3** (interior entity tick on spatial
cores 3–6 under DLM leases, border handoffs on threads 7–8, vendored YaP
Paperclip) are **complete**. **Phase 4** (dual-stack + YaP plugin polish on the
Paper-backed world) is next. See [PAPER_YAPENGINE_PORT.md](../PAPER_YAPENGINE_PORT.md).

---

## 2. Related Work

Paper/Purpur extend Bukkit with asynchronous events and regionized threading experiments (Folia). Netty-based proxies (Velocity) separate player routing from world authority. Academic engines (e.g., parallel ECS frameworks) demonstrate spatial sharding but rarely retain a Bukkit-compatible plugin ABI. YaPcore sits between: **deterministic thread roles** plus a **bridge** for legacy plugins, rather than requiring immediate rewrite onto a new ECS.

---

## 3. Architecture

### 3.1 Thread matrix

| ID | Role | Responsibility |
|----|------|----------------|
| 1 | Controller | Watchdog, recovery, process health |
| 2 | Traffic Cop | Ingress shaping, SequenceToken assignment |
| 3–6 | Game Core | Parallel spatial loops (quad / bitwise quadrant index) |
| 7 | Chunk Sync DLM | Deferred lease / chunk ownership (T7) |
| 8 | Boundary Arbitrator | Cross-quadrant conflict resolution (T8) |
| 9 | Compatibility Bridge | Legacy SYNC mutation queue → tick drain |
| 10–11 | UI sandbox | Menu polish, click routing |
| 12–15 | Heavy I/O | DB, HTTP, files, proxy sync |
| 16 | Telemetry | Metrics / JFR hooks |

See also [YAPENGINE_16THREAD.md](../YAPENGINE_16THREAD.md).

### 3.2 Sequencing

Each logical stream (connection, chunk lease, plugin task) obtains a `SequenceToken` carrying a per-stream sequence and a global identifier with microsecond timestamp. Strict ordered queues refuse out-of-order commits within a stream while allowing cross-stream parallelism.

### 3.3 Spatial model

World interest is indexed with bitwise quadrant structures so cores 3–6 operate on disjoint regions when possible. Boundary packets cross T8 arbitration before becoming visible to other cores.

### 3.4 Memory & GC posture

Production launch scripts prefer **Generational ZGC** with optional **NUMA** pinning (`numactl`, heap pin flags). See [ZGC_NUMA.md](../ZGC_NUMA.md).

---

## 4. Plugin concurrency contract

| Pool | Allowed work | Forbidden |
|------|--------------|-----------|
| **SYNC** | Block/inventory/teleport/world | Blocking DB/HTTP |
| **HEAVY** | JDBC, HTTP, disk, messaging | Direct block set / openInventory without hop |
| **UI** | Menu animation, polish | Authoritative world writes |

`ThreadPools` tags the executing thread. Off-SYNC world APIs auto-queue through the Compatibility Bridge; authors should still schedule explicitly via `runTask` / `runSync` for clarity.

Fine-tune **modules** (`module.yml`) share the same pools and may declare `provides`/`requires` for operator composition ([MODULES_AND_API.md](../MODULES_AND_API.md)).

**Operator layout:** Paper (`plugin.yml`) and YaP (`yap.yml`) jars share one folder, `plugins/`. Under Paper game authority, Paper loads legacy jars; YaP loads only `yap.yml` jars from that same directory (`paper-kernel/plugins` → symlink). See [PLUGIN_COMPAT.md](../PLUGIN_COMPAT.md).

---

## 5. Networking & crossplay

- **Java Edition:** framed Netty pipeline, status/login/configuration/play, known-packs registry sync.
- **Bedrock:** UDP path; optional shared port with Java TCP.
- **Publicity:** domain/SRV/nginx stream templates for edge termination ([NGINX_AND_LOCALHOST.md](../NGINX_AND_LOCALHOST.md)).
- **Crossplay hub:** unified player identity across editions ([CROSSPLAY.md](../CROSSPLAY.md)).

Same-machine clients must use `127.0.0.1` (hairpin NAT).

---

## 6. Evaluation methodology

Recommended operator/research harness ([TESTING.md](../TESTING.md)):

1. Unit tests (JUnit).
2. Fray concurrency exploration.
3. JCStress / TSan / Infer RacerD (optional).
4. Endurance soak with HTML FAIL codes.
5. JFR boundary soak under load.

Metrics of interest: tick time p99, bridge queue depth, cross-quadrant arbitration rate, join success rate by protocol version, HEAVY pool saturation.

---

## 7. Threats to validity

- Incomplete Paper API may bias plugin-porting studies.
- Protocol version sprawl (1.21.x+) requires continuous registry/packet maintenance.
- NUMA/ZGC gains are hardware-dependent.
- Bedrock parity lags Java for some gameplay packets.

---

## 8. Conclusion

YaPcore demonstrates a practical decomposition of Minecraft-class server work into sixteen specialized threads with an explicit plugin pool contract and a Compatibility Bridge for legacy code. Future work includes fuller synchronized registry dumps, deeper world streaming, Brigadier command graphs, and formal verification of SequenceToken queues.

---

## References (selected)

1. Minecraft Wiki — *Java Edition protocol* (handshake, configuration, registry data).
2. OpenJDK — *ZGC* and *Generational ZGC* documentation.
3. Netty project — asynchronous event-driven network application framework.
4. PaperMC / Folia — regionized threading discussions for Bukkit servers.
5. YapLabs — *YapEngine 16-thread architecture notes* (in-repo).

---

## Appendix A — Document map

| Audience | Start here |
|----------|------------|
| Non-tech readers | [PLAIN ENGLISH whitepaper](YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md), [PLAIN_ENGLISH](../PLAIN_ENGLISH.md) |
| Operators | [README](../../README.md), [NETWORKING](../NETWORKING.md) |
| Plugin authors | [PLUGINS](../PLUGINS.md), [MODULES_AND_API](../MODULES_AND_API.md) |
| Engine contributors | [PERF_AND_LAYOUT](../PERF_AND_LAYOUT.md), [YAPENGINE_16THREAD](../YAPENGINE_16THREAD.md) |
| Branding | [branding/](../../branding/) |

## Appendix B — Citation

```bibtex
@techreport{yapcore2026sixteen,
  title       = {YaPcore: A Sixteen-Thread Architecture for Concurrent Minecraft-Class Game Servers},
  author      = {{YapLabs}},
  institution = {YapLabs},
  year        = {2026},
  number      = {YAP-WP-16T-001},
  note        = {Technical whitepaper, YaPcore 0.1}
}
```
