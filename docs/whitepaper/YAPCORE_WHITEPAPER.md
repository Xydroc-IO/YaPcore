# YaPcore: Folia Game Authority with a Slim Edge Chassis for Minecraft-Class Servers

**YapLabs Technical Whitepaper**  
Version 0.1 · August 2026  
Document ID: `YAP-WP-16T-001`

> Prefer plain English? See [YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md).

---

## Abstract

Minecraft-class game servers traditionally serialize world mutation, plugin callbacks, and network I/O onto a single “main” thread, trading simplicity for latency under load. YaPcore’s **shipping product** uses **Folia** for regionized game tick and a **YapEngine slim chassis** for the public edge: watchdog control, traffic/sequencing, Compatibility Bridge, UI sandboxes, and heavy I/O workers. A **SequenceToken** model provides per-stream and global ordering without requiring every subsystem to share one lock. Legacy Spigot/Paper/Folia plugins and first-party YaP plugins/modules execute under an explicit **SYNC / HEAVY / UI** pool contract so inventory and block mutations never race Folia region state. **YaP Link** (native Velocity-class proxy) fronts multi-backend networks. Phase 3–3.7 Paper spatial tick on chassis quads remains **legacy / opt-in** for Paperclip benches (defaults **off**).

This paper describes the architecture, concurrency invariants, networking and crossplay stance, plugin/module surface, and evaluation methodology. It is intended for systems researchers, server operators, and plugin authors evaluating YaPcore as a research and production platform.

**Keywords:** game server concurrency; spatial partitioning; plugin compatibility; Minecraft protocol; generational ZGC; NUMA affinity.

---

## 1. Introduction

### 1.1 Problem

Vanilla and Paper-derived Java Edition servers concentrate authoritative world state updates on one thread (~20 TPS). Plugins that perform database or HTTP work on that thread stall ticks. Conversely, unsynchronized parallel mutation produces torn chunks, duplicate entities, and inventory races. Bedrock clients further require a second transport (UDP) while operators demand a single shared world.

### 1.2 Contribution

YaPcore contributes:

1. A **three-layer product stack**: Folia game tick + YapEngine edge/I/O chassis + YaP Link proxy.
2. **SequenceToken** sequencing for ordered handoff across chassis threads.
3. A **Compatibility Bridge** that stages legacy Bukkit mutations onto the game-core drain window.
4. Dual-stack **Java TCP + Bedrock UDP** ingress with optional shared listen port and Geyser-class crossplay hub.
5. A **three-tier extension model**: Folia/Paper-style plugins, YaP plugins, and fine-tune modules.

### 1.3 Non-goals

YaPcore uses **Folia as game authority** for the shipping product: Folia-aware
first-party plugins receive Folia API coverage (region schedulers via Folia +
[`YapSched`](../YAP_SCHED.md)). Legacy `game-authority=paper` still provides
**complete Paper API coverage** from the embedded Paperclip (`paper-api` 26.2)
for benches. Stock Paper jars on Folia are unsupported. The Compatibility Bridge
facade (non-game authority) remains best-effort stubs only — see `ApiCoverage`
and [PAPER_API_COVERAGE.md](../PAPER_API_COVERAGE.md).

### 1.4 Product status (August 2026)

YaPcore’s shipping product path uses **Folia as game authority**
(`game-authority=folia`, `folia-embed=true`). YapEngine’s **slim chassis** always
boots (edge/I/O; **not** game tick). **YaP Link** is a native Velocity-class proxy (modern forwarding, online-mode,
compression, transfers, YaP Link plugin API; **phases 0–6 shipped**). Phases
3–3.7 Paper spatial tick are **complete as code** but **retired as product default**
(opt-in for Paper benches only; Folia path has no Phase 3 spatial tick). The product
targets **high-population / heavy-load** networks; fair highpop cites focus on
**~100 active bots** (250 keepalive = HOLD-ONLY) —
([BENCH_VS_FOLIA.md](../BENCH_VS_FOLIA.md)). **Phase 4** (dual-stack + first-party
Via/Geyser join/spawn + network plugins; join DoD green, optional fidelity soak). See
[FULL_RUNDOWN.md](../FULL_RUNDOWN.md) · [YAP_LINK.md](../YAP_LINK.md) · [YAP_LINK_NATIVE.md](../YAP_LINK_NATIVE.md).

---

## 2. Related Work

Paper/Purpur extend Bukkit with asynchronous events. Folia provides regionized
multithreading for Bukkit-class servers — YaPcore’s **default game authority**.
Netty-based proxies (Velocity; YaP Link) separate player routing from world
authority. Academic engines (e.g., parallel ECS frameworks) demonstrate spatial
sharding but rarely retain a Bukkit-compatible plugin ABI. YaPcore sits as
**Folia’s game + deterministic YapEngine thread roles + first-party Link**, rather
than requiring a clean-room rewrite onto a new ECS.

---

## 3. Architecture

### 3.1 Three layers (product)

| Layer | Game tick? | Notes |
|-------|------------|-------|
| YaP Link | No | Proxy JVM — multi-backend |
| YapEngine chassis | No | Edge, bridge, UI/Heavy I/O, telemetry |
| Folia | **Yes** | Region thread pool in embedded JVM |

### 3.2 Chassis channel matrix (T1–16)

| ID | Role | Responsibility (v2.0) |
|----|------|------------------------|
| 1 | Controller | Watchdog, recovery, process health |
| 2 | Traffic Cop | Ingress shaping, SequenceToken assignment |
| 3–6 | Chassis worker quads | Sequenced bridge/plugin tasks; **legacy Phase 3 NMS tick on Paper benches only** |
| 7 | Chunk Sync DLM | Deferred lease / chunk ownership (**Paper Phase 3 legacy**) |
| 8 | Boundary Arbitrator | Cross-quadrant handoff (**Paper Phase 3 legacy**) |
| 9 | Compatibility Bridge | Legacy SYNC mutation queue → Folia region APIs |
| 10–11 | UI sandbox | Menu polish, click routing |
| 12–15 | Heavy I/O | DB, HTTP, files, proxy sync |
| 16 | Telemetry | Metrics / JFR hooks |

See also [YAPENGINE_16THREAD.md](../YAPENGINE_16THREAD.md).

### 3.3 Sequencing

Each logical stream (connection, chunk lease, plugin task) obtains a `SequenceToken` carrying a per-stream sequence and a global identifier with microsecond timestamp. Strict ordered queues refuse out-of-order commits within a stream while allowing cross-stream parallelism.

### 3.4 Spatial model (chassis quads + Folia regions)

**Folia** indexes world interest by region and runs authoritative tick on a dynamic
region thread pool. **YapEngine chassis quads (T3–6)** route sequenced bridge/plugin
work by bitwise quadrant — they do **not** replace Folia game tick on the product
path. Legacy Paper Phase 3 used quads + T7/T8 for interior NMS tick (benches only).
Boundary packets on the legacy path crossed T8 arbitration before becoming visible
to other quads.

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

Fine-tune **modules** (`module.yml`) share the same pools and may declare `provides`/`requires` for operator composition ([MODULES_AND_API.md](../MODULES_AND_API.md)). First-party defaults ship **YaP Vehicles**, gameplay knobs, **YapDb** (shared MariaDB pool), **YaPPlayerData**, packs/chat/floodgate helpers, and stacker/pregen into `plugins/` / `modules/` on product builds.

**Operator layout:** Folia/Paper (`plugin.yml`) and YaP (`yap.yml`) jars share one folder, `plugins/`. Under Folia game authority (default), Folia loads compatible jars; under legacy Paper authority, Paper loads legacy jars; YaP loads only `yap.yml` jars from that same directory (kernel `plugins` → symlink). See [PLUGIN_COMPAT.md](../PLUGIN_COMPAT.md). Product ops include a Swing control panel and a token-authenticated **web dashboard** (`:8080`) for headless hosts (Console, Packs, **Ranks**), plus resource-pack HTTP (default `yapcore-default.zip`, multi-active extras). LuckPerms starter ranks: [PERMISSIONS.md](../PERMISSIONS.md). MariaDB packaging: [MARIADB.md](../MARIADB.md).

---

## 5. Networking & crossplay

- **Java Edition:** framed Netty pipeline, status/login/configuration/play, known-packs registry sync.
- **Bedrock:** UDP path; optional shared port with Java TCP.
- **Publicity:** domain/SRV/nginx + Cloudflare edge for **`yapcoremc.yaplabs.us`** ([CLOUDFLARE_AND_NGINX.md](../CLOUDFLARE_AND_NGINX.md), [NETWORKING.md](../NETWORKING.md)).
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

- Phase 3 spatial tick edge cases (legacy Paper path only) may still surprise plugins that assume a single-thread entity model (report vs stock Paper).
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
5. YapLabs — *YapEngine chassis architecture notes* (in-repo).

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
  title       = {YaPcore: Folia Game Authority with a Slim Edge Chassis for Minecraft-Class Servers},
  author      = {{YapLabs}},
  institution = {YapLabs},
  year        = {2026},
  number      = {YAP-WP-16T-001},
  note        = {Technical whitepaper, YaPcore 0.1}
}
```
