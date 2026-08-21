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
| **YaPcore** | The product / server you run (`yapcore.jar`, scripts, GUI + web dashboard, config, packs, shipped vehicles) |
| **YapEngine** | The 16-thread runtime chassis (watchdog, Netty traffic, 4 spatial cores, DLM, bridge, UI/I/O, telemetry) |
| **Paper** | The **game authority** — real Minecraft gameplay, not a clean-room rewrite |

**Brand pitch (short):**  
“A multi-threaded Minecraft server on YapEngine, using Paper for the game.”

**Brand pitch (accurate today):**  
“YaPcore front door + YapEngine chassis + Paper as the full game. Phases 3–3.7 same-JVM Paper ticks interiors on cores 3–6 and borders on T8 under DLM (YaP Paperclip; flags default on). Aimed at high-pop load; `heavypop` MSPT gate not yet won. Phase 4: full first-party Via\* + Geyser parity + YaP plugins.”

---

## What we do

1. **Run a joinable Minecraft server** for modern Java Edition (target **Paper 26.2** / protocol ~776). **Java 25+** required.
2. **Boot YapEngine’s 16 logical threads** every start — chassis is always on.
3. **Delegate the Minecraft game to Paper** (`game-authority=paper`).
4. **Own the public edge** — dual-stack gateway, sequencing (`SequenceToken`), multi-version JE bands, resource-pack HTTP, ops GUI + **web dashboard**, crash dumps.
5. **Phase 3 tick on cores 3–6** — NW/NE/SW/SE interior entities under leases; borders via DLM/boundary.
6. **Dual-stack / crossplay** — full Via\* + Geyser feature parity in our code on one shared world (Phase 4 — [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md)).
7. **Plugins** — all jars in `plugins/` (Paper + YaP); `paper-kernel/plugins` → symlink.
   Defaults: **vehicles**, **gameplay knobs**, **PlaceholderAPI**, **pregen**, **stacker**,
   **plugin-compat**, **playerdata** — see [PLUGINS.md](PLUGINS.md) / [STACKER.md](STACKER.md).
8. **Ops** — `config/server.properties`, Generational ZGC + NUMA, control panel, **browser dashboard** (`:8080`), `logs/crashes/`, `gradle assembleRelease`.
9. **Vehicles** — real cars/trucks/exotics, fuel, upgrades, shop, HD models — [VEHICLES.md](VEHICLES.md).

---

## What we are *not*

| Not this | Why |
|----------|-----|
| “Already a faster Paper everywhere” | High-pop product; `heavypop` gate not yet won; light idle may lose MSPT with full spatial on |
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
| **Border entities / TE / redstone** | T8 under DLM (`spatial-borders`) |
| **Paper / YaP plugins** | `plugins/` (unified; `paper-kernel/plugins` → symlink) |

Port plan: [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md)

---

## Roadmap phases (honest)

| Phase | What | Status |
|-------|------|--------|
| **1** | Paper wrap + TCP proxy | Done (optional) |
| **2** | Paper owns public JE | Done |
| **3** | Leased interior tick on cores 3–6 + vendored Paper | **Done** |
| **3.5** | Interior block/fluid/random under leases | **Done** (default on) |
| **3.6** | Interior block entities + redstone block events on quads | **Done** (default on) |
| **3.7** | Border entities / TE / events on T8 under DLM | **Done** (default on) |
| **Gate** | Beat Paper on **`heavypop`** MSPT | **Active** — not yet won ([BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)) |
| **4** | Dual-stack + YaP plugins polished on Paper-backed world | **Next** |

### Phase 3 pieces (shipping)

- `Phase3PaperRuntime` — Paperclip in-process; sets spatial 3.5–3.7 flags **on** if unset  
- `Phase3PaperClassLoader` — platform parent + host bridge  
- `YapSpatialTickCoordinator` — parallel tick + `runLeased` / border T8 handoffs  
- `yap-spatial-tick` plugin — main-thread snapshot → spatial leased tick  
- `InteriorEntityTickDriver` — NMS entity tick when YaP Paperclip present  
- `InteriorWorldTickBridge` — Phase 3.5–3.7 interior + border world tick under leases  
- `scripts/vendor-paper.sh` / `build-vendor-paper.sh` — pin 26.2-112 → `lib/paper-26.2-yap.jar`  
- `scripts/bench/run-vs-paper.sh` — vs-Paper MSPT scoreboard (`heavypop` primary)  
- `scripts/start.sh` — `cd` into `paper-dir`

---

## Product surface (players & ops)

### Join

- **Java Edition** — local TCP `:25566`; public/nginx `:25565` (`yapcoremc.yaplabs.us`)
- **Bedrock** — UDP, same port numbers by default (`shared-listen-port=true`)
- **Same PC** — always `127.0.0.1:25566` (not the public domain / WAN IP)
- See [CROSSPLAY.md](CROSSPLAY.md), [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md),
  [NETWORKING.md](NETWORKING.md), [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md)

### Plugins & modules

| Kind | Where | Notes |
|------|-------|--------|
| Paper / Spigot / YaP jars | `plugins/` | Paper (`plugin.yml`) + YaP (`yap.yml`) |
| YaP modules (`module.yml`) | `modules/` | Module runtime |
| Fine-tune modules | `modules/` | `module.yml` |

See [PLUGINS.md](PLUGINS.md), [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md), [MODULES_AND_API.md](MODULES_AND_API.md)

### Packs & ops

- Default client pack: `resourcepacks/yapcore-default.zip` (Faithful 64x + YaP Vehicles) — HTTP `:8081`
- Public pack edge: `https://yapcoremc.yaplabs.us/pack/`
- nginx edge: `scripts/nginx-setup.sh` · [NGINX_AND_LOCALHOST.md](NGINX_AND_LOCALHOST.md)
- Control GUI: `./scripts/gui.sh`
- **Web dashboard (headless):** `http://127.0.0.1:8080/` — [WEB_DASHBOARD.md](WEB_DASHBOARD.md)
- Release package: `gradle assembleRelease` → `build/dist/yapcore-release/{linux,windows}/`
- Windows: Paperclip + nginx scripts — [WINDOWS.md](WINDOWS.md)
- Crash reports: `logs/crashes/`
- JVM: [ZGC_NUMA.md](ZGC_NUMA.md)
- Vehicles: [VEHICLES.md](VEHICLES.md)

---

## How to run

```bash
# Java 25+ for Paper 26.2 / Phase 3
./scripts/vendor-paper.sh          # once — clone pin
./scripts/build-vendor-paper.sh    # → lib/paper-26.2-yap.jar
gradle assembleRelease             # jar + default plugins/packs + release folder
./scripts/start.sh --fg            # headless + web dashboard :8080
# or: cd build/dist/yapcore-release && ./start.sh --fg
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
| Bedrock + Java | Usually Geyser stack | **Built-in full Geyser + Via parity** (Phase 4) |
| Plugins | Bukkit/Spigot/Paper | Paper plugins + YaP pools |

---

## How to say it out loud

**Elevator:**  
“Multi-threaded Minecraft server on YapEngine, using Paper for the game.”

**Technical:**  
“Paper is the game authority. YapEngine’s 16-thread chassis is always on. Phase 3 puts interior entity tick on spatial cores under DLM leases. Next is Phase 4 — full first-party Via\* + Geyser parity and YaP plugins on that Paper-backed world.”

---

## Related docs

| Doc | Topic |
|-----|--------|
| [PLAIN_ENGLISH.md](PLAIN_ENGLISH.md) | Non-tech overview |
| [WHAT_WE_ARE.md](WHAT_WE_ARE.md) | Short identity |
| [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md) | Phase 1–4 port plan |
| [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md) | Thread matrix |
| [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md) | Clients, versions, packs |
| [WEB_DASHBOARD.md](WEB_DASHBOARD.md) | Headless browser control |
| [VEHICLES.md](VEHICLES.md) | Real vehicle API + fleet |
| [CROSSPLAY.md](CROSSPLAY.md) | JE + BE |
| [whitepaper/YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) | Long-form architecture |
