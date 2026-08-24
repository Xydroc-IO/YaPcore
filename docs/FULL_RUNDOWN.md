# YaPcore — full rundown

**What we are, what we do, and where we are today.**

> Prefer plain English (non-tech)? See [PLAIN_ENGLISH.md](PLAIN_ENGLISH.md).

---

## One sentence

**YaPcore** is a Minecraft server product with **three layers**: **Folia** runs the game (world, entities, redstone, commands), **YapEngine** runs the slim chassis (Netty, dual-stack, I/O, ops — not world tick), and **YaP Link** (native Velocity-class proxy) fronts multi-backend networks — plus Java + Bedrock on one join story, Folia/Paper-class plugins, and YaP all-in-one plugin pools. Legacy **Paper + Phase 3 spatial** remains opt-in for benches.

---

## What we are

| Piece | Meaning |
|-------|---------|
| **YaPcore** | The product / server you run (`yapcore.jar`, scripts, GUI + web dashboard, config, packs, shipped vehicles) |
| **YapEngine** | Slim **chassis** in the YaPcore parent process — watchdog, Netty traffic, bridge, UI/Heavy I/O, telemetry (**not** game tick) |
| **Folia** | The **default game authority** — regionized Minecraft gameplay |
| **YaP Link** | First-party native Velocity-class proxy — [YAP_LINK.md](YAP_LINK.md) |
| **Paper (legacy)** | Alternate game authority for Phase 3 spatial benches (`game-authority=paper`) |

**Brand pitch (short):**  
“A Folia-backed Minecraft server with YapEngine chassis and YaP Link for the network.”

**Brand pitch (accurate today):**  
“Next-gen server software for most operators: Folia game + shipped YaP natives (not Paper + ten plugins) + YaP Link + dual-stack. See [PLUGIN_COMPAT_MATRIX.md](PLUGIN_COMPAT_MATRIX.md).”

---

## What we do

1. **Run a joinable Minecraft server** for modern Java Edition (target **Folia/Paper 26.2** / protocol ~776). **Java 25+** required.
2. **Boot YapEngine chassis** every start — edge/I/O always on (Folia owns game tick).
3. **Delegate the Minecraft game to Folia** (`game-authority=folia`, `folia-embed=true`).
4. **Own the public edge** — dual-stack gateway, sequencing (`SequenceToken`), multi-version JE bands, resource-pack HTTP, ops GUI + **web dashboard**, crash dumps.
5. **YaP Link** — native Velocity-class proxy (phases 0–6; forwarding, online-mode, compression, transfers, YaP Link plugins); stock Velocity remains optional. **Bedrock at Link:** optional UDP edge — until enabled, use backend dual-stack or stock Velocity for BE-heavy networks ([YAP_LINK.md](YAP_LINK.md#bedrock--geyser)).
6. **Dual-stack / crossplay** — first-party Via\* + Geyser feature parity on one shared world ([PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md)); JE product floor **1.20.2+**; Bedrock join/spawn + play-depth smoke green (`smoke-bedrock-play.sh`).
7. **Plugins** — all jars in `plugins/` (Folia/Paper + YaP); kernel `plugins` → symlink.
   **CORE+NETWORK (default):** PlaceholderAPI, pregen, plugin-compat, **yap-db**, **playerdata**,
   **packs**, **chat**, **floodgate**.
   **GAMEPLAY (opt-in):** vehicles, stacker, knobs, **MMO stack** (skills, combat, crafting, content, abilities, bedrock UI, mechanics, games, guilds) — `gradle installGameplayDefaults` / `-PyapGameplay=true`.
   See [PLUGINS.md](PLUGINS.md) / [PLAYERDATA.md](PLAYERDATA.md) / [YAPDB.md](YAPDB.md).
8. **Ops** — `config/server.properties`, Generational ZGC + NUMA, control panel, **browser dashboard** (`:8080` — Packs + **Ranks**), LuckPerms pack ([PERMISSIONS.md](PERMISSIONS.md)), Docker MariaDB ([MARIADB.md](MARIADB.md)), `logs/crashes/`, `gradle assembleRelease`.
9. **Vehicles** — real cars/trucks/exotics, fuel, upgrades, shop, HD models — [VEHICLES.md](VEHICLES.md).

---

## What we are *not*

| Not this | Why |
|----------|-----|
| “Already faster than Paper/Leaf everywhere” | High-pop product; fair cites matter; 250 keepalive ≠ MSPT win |
| “Folia but better” as a slogan | We **use Folia**; we add chassis + Link + dual-stack + YaP plugins |
| Clean-room Minecraft | We wrap **Folia** (default) / Paper (legacy) on purpose |
| Mojang `server.jar` as the product | Legacy `game-authority=mojang` only |
| Must use Link instead of Velocity | Stock Velocity still works; Link is the product default |
| Full Geyser play parity today | Join/spawn + play-depth smoke green; emotes partial; placed skull textures v2 |

---

## Architecture — three layers + chassis channels

```
Clients (JE TCP / BE UDP)
        │
        ▼ optional
   YaP Link :25565  ──forwarding──►  Folia backend(s)
        │
        ▼
 YapEngine chassis (YaPcore parent)
   Traffic Cop (2) · Bridge (9) · UI (10–11) · Heavy I/O (12–15) · Telemetry (16)
        │
        ▼
 Folia child JVM  ← **GAME TICK** (region thread pool)
   folia-kernel/

Legacy (benches only): game-authority=paper + Phase 3 on chassis quads 3–6
```

| Layer | Role |
|-------|------|
| **YaP Link** | Proxy JVM — [YAP_LINK.md](YAP_LINK.md) |
| **YapEngine chassis** | Edge + I/O + plugin sandboxes (**not** game tick) |
| **Folia** | Game — regionized tick |

Chassis channel map (T1–16): [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md)

---

## Game path — Folia as authority (default)

Default config:

```properties
game-authority=folia
folia-embed=true
folia-version=26.2
folia-dir=folia-kernel
paper-phase3-tick-bridge=false
paper-phase3-nms-tick=false
```

| Who owns what (today) | |
|-----------------------|--|
| **Public JE port** | Folia (or YaP Link → Folia loopback) |
| **World / commands / redstone** | Folia |
| **YapEngine chassis** | Always boots |
| **Phase 3 spatial tick** | **Off** on product path (Folia does not use it) |
| **Folia / YaP plugins** | `plugins/` (unified) |

Fetch / smoke: `scripts/fetch-folia.sh`, `scripts/smoke-folia.sh`.  
Fork build: `scripts/build-yap-folia.sh` → `lib/yap-folia-26.2.jar` (`folia-jar-source=build`). See [FOLIA_FORK.md](FOLIA_FORK.md).

### Legacy Paper + Phase 3 (opt-in)

```properties
game-authority=paper
paper-embed=true
paper-phase3-tick-bridge=true
paper-phase3-nms-tick=true
paper-version=26.2
```

Requires YaP Paperclip (`lib/paper-26.2-yap.jar`). See [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md) (legacy Paper spatial).

---

## Roadmap phases (honest)

| Phase | What | Status |
|-------|------|--------|
| **1** | Paper wrap + TCP proxy | Done (optional) |
| **2** | Game owns public JE | Done (Folia default) |
| **3–3.7** | Paper spatial on cores 3–6 / T8 | **Done as code** — **retired as product default** |
| **Folia product path** | Managed Folia embed | **Default** |
| **YaP Link** | Native Velocity-class proxy (phases 0–6) | **Shipped** |
| **Gate** | Fair highpop MSPT (~100 active bots) | **Active** — [BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md) |
| **4** | Dual-stack + YaP network plugins | **Join DoD green** — JE matrix + BE play-depth smoke; optional fidelity soak ([PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md)) |

---

## Product surface (players & ops)

### Join

- **Java Edition** — local TCP `:25566`; public/nginx `:25565` (`yapcoremc.yaplabs.us`); or **YaP Link** `:25565` → Folia
- **Bedrock** — UDP, same port numbers by default (`shared-listen-port=true`)
- **Same PC** — always `127.0.0.1:25566` (not the public domain / WAN IP)
- See [CROSSPLAY.md](CROSSPLAY.md), [YAP_LINK.md](YAP_LINK.md), [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md),
  [NETWORKING.md](NETWORKING.md), [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md)

### Plugins & modules

| Kind | Where | Notes |
|------|-------|--------|
| Folia / Paper / YaP jars | `plugins/` | Folia product path; Paper legacy |
| YaP modules (`module.yml`) | `modules/` | Module runtime |
| Fine-tune modules | `modules/` | `module.yml` |

See [PLUGINS.md](PLUGINS.md), [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md), [MODULES_AND_API.md](MODULES_AND_API.md)

### Packs & ops

- Default client pack: `resourcepacks/yapcore-default.zip` (Faithful 64x + YaP Vehicles + **Abilities icons** when gameplay build) — HTTP `:8081`
- Multi-active extras via `yap-packs` + Packs panel / dashboard
- Public pack edge: `https://yapcoremc.yaplabs.us/pack/`
- nginx edge: `scripts/nginx-setup.sh` · [NGINX_AND_LOCALHOST.md](NGINX_AND_LOCALHOST.md)
- Control GUI: `./scripts/gui.sh`
- **Web dashboard (headless):** `http://127.0.0.1:8080/` — [WEB_DASHBOARD.md](WEB_DASHBOARD.md)
- **MariaDB:** `./scripts/db/start-mariadb.sh` then `configure-db.sh` — [MARIADB.md](MARIADB.md)
- **Ranks:** `gradle installProductDefaults` then `ranks apply` — [PERMISSIONS.md](PERMISSIONS.md)
- Release package: `gradle assembleRelease` → `build/dist/yapcore-release/{linux,windows}/`
- Windows: Folia/Paperclip + nginx + MariaDB scripts — [WINDOWS.md](WINDOWS.md)
- Crash reports: `logs/crashes/`
- JVM: [ZGC_NUMA.md](ZGC_NUMA.md)
- Vehicles: [VEHICLES.md](VEHICLES.md)

---

## How to run

```bash
# Java 25+ for Folia 26.2
./scripts/fetch-folia.sh           # once — Folia jar into folia-kernel
gradle assembleRelease             # jar + default plugins/packs + release folder
./scripts/start.sh --fg            # headless + web dashboard :8080
# Multi-backend:
./scripts/start-yap-link.sh
./scripts/stop.sh
```

| Need | Setting |
|------|---------|
| Folia product path (default) | `game-authority=folia` + `folia-embed=true` |
| Legacy Paper + Phase 3 | `game-authority=paper` + Phase 3 flags true + YaP Paperclip |
| Phase 1 proxy wrap | `*-embed=false` + game port |

---

## How we compare (today)

| | Paper | Folia | YaPcore (today) |
|--|--------|-------|------------------|
| Game | Is the game | Is the game | **Uses Folia** by default |
| Multithreaded world tick | Single main | Regions | **Folia regions** + Yap chassis |
| Network proxy | DIY | DIY | **YaP Link** (native proxy, phases 0–6) |
| Thread design | Paper’s model | Region pool | **Folia regions** for game + YapEngine **chassis** for edge/I/O |
| Bedrock + Java | Usually Geyser stack | Usually Geyser stack | **Built-in** first-party Geyser + Via parity (Phase 4) |
| Plugins | Paper | Folia-aware | Folia path + YaP pools + shipped network stack |
| Shared SQL / offline auth / ranks | Bring your own | Bring your own | **Shipped** YapDb + playerdata + LuckPerms pack |

---

## How to say it out loud

**Elevator:**  
“Multi-threaded Minecraft server on YapEngine, Folia for the game, YaP Link for the network.”

**Technical:**  
“Folia is the default game authority. YapEngine’s slim chassis (edge/I/O) is always on. YaP Link (phases 0–6) fronts Folia backends as a native Velocity-class proxy. Phase 4 dual-stack join + play-depth smoke is green; optional fidelity soak remains. Phase 3 Paper spatial remains for legacy benches.”

---

## Related docs

| Doc | Topic |
|-----|--------|
| [PLAIN_ENGLISH.md](PLAIN_ENGLISH.md) | Non-tech overview |
| [WHAT_WE_ARE.md](WHAT_WE_ARE.md) | Short identity |
| [YAP_LINK.md](YAP_LINK.md) | YaP Link proxy |
| [VELOCITY.md](VELOCITY.md) | Velocity stand-in |
| [FULL_RUNDOWN.md](FULL_RUNDOWN.md) | Roadmap phases |
| [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md) | Thread matrix |
| [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md) | Clients, versions, packs |
| [WEB_DASHBOARD.md](WEB_DASHBOARD.md) | Headless browser control |
| [VEHICLES.md](VEHICLES.md) | Real vehicle API + fleet |
| [PLAYERDATA.md](PLAYERDATA.md) | Cross-server data + auth |
| [YAPDB.md](YAPDB.md) / [MARIADB.md](MARIADB.md) | Shared MariaDB |
| [PERMISSIONS.md](PERMISSIONS.md) | LuckPerms ranks |
| [CROSSPLAY.md](CROSSPLAY.md) | JE + BE |
| [whitepaper/YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) | Long-form architecture |
