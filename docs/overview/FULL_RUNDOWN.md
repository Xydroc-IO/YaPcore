# YaPcore — full rundown

**What we are, what we do, and where we are today.**

> Prefer plain English? See [PLAIN_ENGLISH.md](PLAIN_ENGLISH.md).

---

## One sentence

**YaPcore** is a Minecraft server product with **three layers**: **YaP-Folia** (our Folia 26.2 fork) runs the game, **YapEngine** runs the slim chassis (Netty, dual-stack, I/O, ops — not world tick), and **YaP Link** fronts multi-backend networks — plus Java + Bedrock on one join story and a shipped first-party plugin stack. Legacy **Paper + Phase 3 spatial** remains opt-in for benches.

---

## What we are

| Piece | Meaning |
|-------|---------|
| **YaPcore** | The product you run (`yapcore.jar`, scripts, GUI + web dashboard, config, packs) |
| **YapEngine** | Slim **chassis** in the YaPcore parent — watchdog, Netty traffic, bridge, UI/Heavy I/O (**not** game tick) |
| **YaP-Folia** | The **default game authority** — patched Folia 26.2 (`lib/yap-folia-26.2.jar`) |
| **YaP Link** | First-party native Velocity-class proxy — [YAP_LINK.md](../network/YAP_LINK.md) |
| **Stock Folia** | Upstream Fill jar — **fallback/bench only** (`folia-jar-source=fetch`) |
| **Paper (legacy)** | Alternate authority for Phase 3 spatial benches (`game-authority=paper`) |

**Brand pitch (short):**  
“A YaP-Folia–backed Minecraft server with YapEngine chassis and YaP Link for the network.”

**Brand pitch (accurate today):**  
“Next-gen server software: YaP-Folia game + shipped YaP natives (not Paper + ten plugins) + YaP Link + dual-stack. See [PLUGIN_COMPAT_MATRIX.md](../plugins/PLUGIN_COMPAT_MATRIX.md) · [FOLIA_FORK.md](../folia/FOLIA_FORK.md).”

---

## What we do

1. **Run a joinable Minecraft server** for modern Java Edition (target **YaP-Folia 26.2** / protocol ~776). **Java 25+** required.
2. **Boot YapEngine chassis** every start — edge/I/O always on (YaP-Folia owns game tick).
3. **Delegate the Minecraft game to YaP-Folia** (`game-authority=folia`, `folia-embed=true`, `folia-jar-source=build`).
4. **Own the public edge** — dual-stack gateway, sequencing, multi-version JE bands, resource-pack HTTP, ops GUI + **web dashboard**, crash dumps.
5. **YaP Link** — native Velocity-class proxy (phases 0–6; `0.6.0-phase6`). Stock Velocity remains optional.
6. **Dual-stack / crossplay** — first-party Via\* + Geyser-class code on one shared world; JE floor **1.20.2+**; Bedrock join/spawn + play-depth smoke green.
7. **Plugins** — all jars in `plugins/` (symlinked into `folia-kernel/plugins`).
   **CORE+NETWORK (default):** PlaceholderAPI, pregen, plugin-compat, **yap-db**, **playerdata**, **packs**, **chat**, **floodgate**, perms, essentials, protect, world, lagguard, …
   **GAMEPLAY (opt-in):** vehicles, stacker, knobs, **MMO stack** — `gradle installGameplayDefaults` / `-PyapGameplay=true`.
8. **Ops** — `config/server.properties`, ZGC + NUMA, control panel, **browser dashboard** (`:8080`), Docker MariaDB, `gradle assembleRelease`.
9. **Vehicles** — real cars/trucks/exotics, fuel, upgrades, shop — [VEHICLES.md](../plugins/VEHICLES.md).

---

## What we are *not*

| Not this | Why |
|----------|-----|
| “Already faster than Paper/Leaf everywhere” | High-pop product; fair cites matter |
| Stock Folia as the product jar | Product default is **YaP-Folia** (`folia-jar-source=build`) |
| Clean-room Minecraft | We fork Folia on purpose and patch it |
| Mojang `server.jar` as the product | Legacy only |
| Full Geyser play parity today | Join/spawn + play-depth smoke green; some fidelity partial |

---

## Architecture — three layers

```
Clients (JE TCP / BE UDP)
        │
        ▼ optional
   YaP Link :25565  ──forwarding──►  YaPcore Via edge :25566
        │                                    │
        ▼                                    ▼
 YapEngine chassis (YaPcore parent)     YaP-Folia child JVM
   Traffic · Bridge · UI · Heavy I/O      **GAME TICK** (regions)
                                          folia-kernel/ + lib/yap-folia-*.jar

Legacy (benches only): game-authority=paper + Phase 3 on chassis
```

| Layer | Role |
|-------|------|
| **YaP Link** | Proxy JVM |
| **YapEngine chassis** | Edge + I/O + plugin sandboxes (**not** game tick) |
| **YaP-Folia** | Game — regionized tick |

Chassis: [YAPENGINE_16THREAD.md](../performance/YAPENGINE_16THREAD.md) · Fork: [FOLIA_FORK.md](../folia/FOLIA_FORK.md)

---

## Game path — YaP-Folia as authority (default)

```properties
game-authority=folia
folia-embed=true
folia-version=26.2
folia-dir=folia-kernel
folia-jar-source=build
folia-teleport-transactions=true
paper-phase3-tick-bridge=false
paper-phase3-nms-tick=false
```

| Who owns what | |
|---------------|--|
| **Public JE port** | Chassis Via / Link → YaP-Folia loopback |
| **World / commands / redstone** | **YaP-Folia** |
| **YapEngine chassis** | Always boots |
| **Phase 3 spatial tick** | **Off** on product path |
| **Plugins** | `plugins/` (unified) |

Build: `./scripts/build-yap-folia.sh` → `lib/yap-folia-26.2.jar`.  
Smoke: `FOLIA_JAR_SOURCE=build ./scripts/smoke-folia.sh`.

### Stock Folia fallback

```properties
folia-jar-source=fetch
```

```bash
./scripts/fetch-folia.sh
```

### Legacy Paper + Phase 3 (opt-in)

```properties
game-authority=paper
paper-embed=true
paper-phase3-tick-bridge=true
paper-phase3-nms-tick=true
```

Requires YaP Paperclip. Not for new deployments.

---

## Roadmap phases (honest)

| Phase | What | Status |
|-------|------|--------|
| **1–2** | Chassis + game owns public JE | Done |
| **3–3.7** | Paper spatial on cores 3–6 | **Done as code** — **retired as product default** |
| **YaP-Folia product path** | Managed fork embed (`folia-jar-source=build`) | **Default** |
| **YaP Link** | Native Velocity-class proxy (phases 0–6) | **Shipped** |
| **8–17** | Dashboard, TAB, Discord, regions, map, guard, NPCs, Bedrock depth, release polish | **Done** (v1) |
| **Tier 4** | First-party Via/Geyser parity | **Done** — automated gates green; live §E partial |
| **Gate** | Fair highpop MSPT (~100 active bots) | **Active** |

---

## Product surface

### Join

- **Java Edition** — local TCP `:25566`; public/nginx `:25565`; or **YaP Link** `:25565` → backends
- **Bedrock** — UDP, same port numbers by default (`shared-listen-port=true`)
- **Same PC** — always `127.0.0.1:25566`

### Plugins & modules

| Kind | Where | Notes |
|------|-------|--------|
| Folia-aware / YaP jars | `plugins/` | Product path = YaP-Folia |
| YaP modules | `modules/` | Fine-tune packaging |

### Packs & ops

- Default pack: `resourcepacks/yapcore-default.zip` — HTTP `:8081`
- Web dashboard: `http://127.0.0.1:8080/`
- MariaDB: `./scripts/db/ensure-db.sh --server-id lobby`
- Release: `gradle assembleRelease` → `build/dist/yapcore-release/{linux,windows}/`

---

## How to run

```bash
# Java 25+
./scripts/build-yap-folia.sh       # YaP-Folia → lib/yap-folia-26.2.jar
gradle installProductDefaults
gradle assembleRelease
./scripts/start.sh --fg
# Multi-backend:
./scripts/start-yap-link.sh
```

| Need | Setting |
|------|---------|
| Product path (default) | `game-authority=folia` + `folia-jar-source=build` |
| Stock Folia bench | `folia-jar-source=fetch` |
| Legacy Paper + Phase 3 | `game-authority=paper` + Phase 3 flags |

---

## How we compare (today)

| | Paper | Stock Folia | YaPcore |
|--|--------|-------------|---------|
| Game | Is the game | Is the game | **YaP-Folia** (our fork) |
| Multithreaded world tick | Single main | Regions | **YaP-Folia regions** + Yap chassis |
| Network proxy | DIY | DIY | **YaP Link** |
| Bedrock + Java | Usually Geyser stack | Usually Geyser stack | **Built-in** first-party |
| Plugins | Paper | Folia-aware | YaP-Folia + shipped YaP stack |
| Shared SQL / auth / ranks | Bring your own | Bring your own | **Shipped** |

---

## How to say it out loud

**Elevator:**  
“Multi-threaded Minecraft on YapEngine, **YaP-Folia** for the game, YaP Link for the network.”

**Technical:**  
“YaP-Folia is the default game authority (`folia-jar-source=build`). YapEngine’s slim chassis is always on. YaP Link (`0.6.0-phase6`) fronts backends. Phase 4 dual-stack join DoD is green. Phase 3 Paper spatial remains for legacy benches.”

---

## Related docs

| Doc | Topic |
|-----|--------|
| [PROJECT_STATUS.md](PROJECT_STATUS.md) | Done / partial / remaining (with test artifacts) |
| [PLAIN_ENGLISH.md](PLAIN_ENGLISH.md) | Non-tech overview |
| [WHAT_WE_ARE.md](WHAT_WE_ARE.md) | Short identity |
| [FOLIA_FORK.md](../folia/FOLIA_FORK.md) | YaP-Folia patches & build |
| [YAP_LINK.md](../network/YAP_LINK.md) | YaP Link proxy |
| [YAPENGINE_16THREAD.md](../performance/YAPENGINE_16THREAD.md) | Chassis channels |
| [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) | Browser control |
| [VEHICLES.md](../plugins/VEHICLES.md) | Vehicles |
| [PLAYERDATA.md](../data/PLAYERDATA.md) / [YAPDB.md](../data/YAPDB.md) / [MARIADB.md](../data/MARIADB.md) | Data plane |
| [whitepaper/YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md) | Long-form architecture + plugin suite (v0.3) |
