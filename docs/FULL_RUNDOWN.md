# YaPcore — full rundown

**What we are, what we do, and where we are today.**

> Prefer plain English (non-tech)? See [PLAIN_ENGLISH.md](PLAIN_ENGLISH.md).

---

## One sentence

**YaPcore** is a Minecraft server product built on **YapEngine** (a fixed **16-thread** chassis) that uses **Paper** as the game — world, entities, redstone, commands — with **Phase 3** interior entity tick on YapEngine spatial cores **3–6**, Java + Bedrock on one join story, Paper plugins, and YaP all-in-one plugin pools.

---

## What we are

| Piece | Meaning |
|-------|---------|
| **YaPcore** | The product / server you run (`yapcore.jar`, scripts, GUI, config, packs) |
| **YapEngine** | The 16-thread runtime chassis (watchdog, Netty traffic, 4 spatial cores, DLM, bridge, UI/I/O, telemetry) |
| **Paper** | The **game authority** — real Minecraft gameplay, not a clean-room rewrite |

**Brand pitch (short):**  
“A multi-threaded Minecraft server on YapEngine, using Paper for the game.”

**Brand pitch (accurate today):**  
“YaPcore front door + YapEngine chassis + Paper as the full game. Phase 3 same-JVM Paper ticks interior entities on cores 3–6 under DLM leases (YaP Paperclip). Players stay on Paper main; borders use T7/T8. Phase 4 polishes dual-stack + YaP plugins on that world.”

---

## What we do

1. **Run a joinable Minecraft server** for modern Java Edition (target **Paper 26.2** / protocol ~776). **Java 25+** required.
2. **Boot YapEngine’s 16 logical threads** every start — chassis is always on.
3. **Delegate the Minecraft game to Paper** (`game-authority=paper`).
4. **Own the public edge** — dual-stack gateway, sequencing (`SequenceToken`), multi-version JE bands, resource-pack HTTP, ops GUI, crash dumps.
5. **Phase 3 tick on cores 3–6** — NW/NE/SW/SE interior entities under leases; borders via DLM/boundary.
6. **Dual-stack / crossplay** — Java TCP + Bedrock UDP toward one shared world (Phase 4 polish next).
7. **Plugins** — all jars in `plugins/` (Paper + YaP); `paper-kernel/plugins` → symlink.
8. **Ops** — `config/server.properties`, Generational ZGC + NUMA, control panel, `logs/crashes/`.

---

## What we are *not*

| Not this | Why |
|----------|-----|
| “Already a faster Paper everywhere” | Phase 3 is interior-entity spatial tick + leases — not every Paper subsystem on quads yet |
| Folia / region-thread pool | Rejected; we use YapEngine’s fixed 16 roles |
| Clean-room Minecraft | We wrap/port **Paper** on purpose |
| Mojang `server.jar` as the product | Legacy `game-authority=mojang` only |
| A rename-only Paper fork | Threading, dual-stack, YaP pools, and chassis are first-class |

---

## Architecture — YapEngine 16 threads

```
Clients (JE TCP / BE UDP)
        │
        ▼
 DualStackGateway + Traffic Cop (2)     ← ingest, SequenceToken
        │
        ▼
 Compatibility Bridge (9)               ← plugins → game marshaling
        │
        ▼
 Spatial cores 3–6 (NW/NE/SW/SE)        ← Phase 3 interior tick
        │  (borders)
        ▼
 DLM (7) + Boundary (8)                 ← leases / handoff
        │
 UI (10–11) · Heavy I/O (12–15) · Telemetry (16)
 Controller / watchdog (1)
```

| Threads | Role |
|---------|------|
| **1** | Controller / watchdog |
| **2** | Traffic Cop + SequenceToken |
| **3–6** | Spatial game cores — NW / NE / SW / SE |
| **7** | Chunk Sync DLM & leases |
| **8** | Boundary sync & entity handoff |
| **9** | Compatibility Bridge |
| **10–11** | UI sandboxes |
| **12–15** | Heavy I/O |
| **16** | Telemetry / async worker |

Details: [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md)

---

## Game path — Paper as authority

Default config:

```properties
game-authority=paper
paper-embed=true
paper-phase3-tick-bridge=true
paper-phase3-nms-tick=true
paper-version=26.2
paper-dir=paper-kernel
```

| Who owns what (today) | |
|-----------------------|--|
| **Public JE port** | Paper (Phase 3 same-JVM) |
| **World / commands / redstone** | Paper |
| **YapEngine chassis** | Always boots |
| **Interior entity tick** | Cores 3–6 under leases; needs `lib/paper-*-yap.jar` (fail-closed if NMS on) |
| **Border chunk work** | T7/T8 handoffs |
| **Players** | Paper main |
| **Paper / YaP plugins** | `plugins/` (unified; `paper-kernel/plugins` → symlink) |

Port plan: [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md)

---

## Roadmap phases (honest)

| Phase | What | Status |
|-------|------|--------|
| **1** | Paper wrap + TCP proxy | Done (optional) |
| **2** | Paper owns public JE | Done |
| **3** | Leased interior tick on cores 3–6 + border T7/T8 + vendored Paper | **Done** |
| **3.5** | Beat Paper MSPT — block/fluid/random + public bench | **Active** |
| **3.6** | Interior block entities + redstone block events on quads | **Done** (opt-in flags) |
| **4** | Dual-stack + YaP plugins polished on Paper-backed world | **Next** (after scoreboard) |

### Phase 3 pieces (shipping)

- `Phase3PaperRuntime` — Paperclip in-process  
- `Phase3PaperClassLoader` — platform parent + host bridge  
- `YapSpatialTickCoordinator` — parallel tick + `runLeased` / border handoffs  
- `yap-spatial-tick` plugin — main-thread snapshot → spatial leased tick  
- `InteriorEntityTickDriver` — NMS entity tick when YaP Paperclip present  
- `InteriorWorldTickBridge` — Phase 3.5–3.6 interior block/fluid/random + block entities + redstone events under leases  
- `scripts/vendor-paper.sh` / `build-vendor-paper.sh` — pin 26.2-112 → `lib/paper-26.2-yap.jar`  
- `scripts/bench/run-vs-paper.sh` — vs-Paper MSPT scoreboard  
- `scripts/start.sh` — `cd` into `paper-dir`

---

## Product surface (players & ops)

### Join

- **Java Edition** — TCP (default `:25566`); with Paper embed, Paper owns JE protocol  
- **Bedrock** — UDP, same port number by default (`shared-listen-port=true`)  
- See [CROSSPLAY.md](CROSSPLAY.md), [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md)

### Plugins & modules

| Kind | Where | Notes |
|------|-------|--------|
| Paper / Spigot / YaP jars | `plugins/` | Paper (`plugin.yml`) + YaP (`yap.yml`) |
| YaP modules (`module.yml`) | `modules/` | Module runtime |
| Fine-tune modules | `modules/` | `module.yml` |

See [PLUGINS.md](PLUGINS.md), [MODULES_AND_API.md](MODULES_AND_API.md)

### Packs & ops

- Resource packs in `resourcepacks/` over HTTP (default `:8081`)
- Control GUI: `./scripts/gui.sh`
- Crash reports: `logs/crashes/`
- JVM: [ZGC_NUMA.md](ZGC_NUMA.md)

---

## How to run

```bash
# Java 25+ for Paper 26.2 / Phase 3
./scripts/vendor-paper.sh          # once — clone pin
./scripts/build-vendor-paper.sh    # → lib/paper-26.2-yap.jar
gradle distJar
./scripts/start.sh --fg            # cds into paper-kernel
./scripts/stop.sh
```

| Need | Setting |
|------|---------|
| Phase 3 same-JVM + leases | `paper-phase3-tick-bridge=true` (default) + `scripts/start.sh` |
| Phase 3 NMS interior tick | `paper-phase3-nms-tick=true` + `lib/paper-*-yap.jar` (required; fail-closed) |
| Phase 3 leases only (no NMS) | `paper-phase3-nms-tick=false` |
| Phase 2 managed Paper only | `paper-phase3-tick-bridge=false` |
| Phase 1 proxy wrap | `paper-embed=false` + `paper-port=…` |

---

## How we compare (today)

| | Paper | YaPcore (today) |
|--|--------|------------------|
| Game | Is the game | **Uses Paper** |
| Multithreaded world tick | Single main thread | **Phase 3 / 3.5** — interior entities + block/fluid/random on 3–6; players on main |

| Thread design | Paper’s model | YapEngine **16-thread** matrix |
| Bedrock + Java | Usually Geyser stack | Built-in dual-stack path |
| Plugins | Bukkit/Spigot/Paper | Paper plugins + YaP pools |

---

## How to say it out loud

**Elevator:**  
“Multi-threaded Minecraft server on YapEngine, using Paper for the game.”

**Technical:**  
“Paper is the game authority. YapEngine’s 16-thread chassis is always on. Phase 3 puts interior entity tick on spatial cores under DLM leases. Next is Phase 4 — dual-stack and YaP plugins polished on that Paper-backed world.”

---

## Related docs

| Doc | Topic |
|-----|--------|
| [PLAIN_ENGLISH.md](PLAIN_ENGLISH.md) | Non-tech overview |
| [WHAT_WE_ARE.md](WHAT_WE_ARE.md) | Short identity |
| [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md) | Phase 1–4 port plan |
| [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md) | Thread matrix |
| [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md) | Clients, versions, packs |
| [CROSSPLAY.md](CROSSPLAY.md) | JE + BE |
| [whitepaper/YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) | Long-form architecture |
