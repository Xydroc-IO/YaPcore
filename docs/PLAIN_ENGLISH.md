# YaPcore — plain English overview

**This page is for anyone who isn’t deep into server tech.**  
No jargon required. If you just want to know *what this is* and *what we’re building*, start here.

The technical versions live in [WHAT_WE_ARE.md](WHAT_WE_ARE.md) and [FULL_RUNDOWN.md](FULL_RUNDOWN.md).

---

## In one breath

**YaPcore** is software that runs a Minecraft server.

Players join and play Minecraft as usual. Under the hood, we use **Folia** (PaperMC’s multi-threaded game engine) for the world, mobs, redstone, and commands — and we wrap that with our own system, **YapEngine**, which keeps a fixed set of worker roles for networking, plugins, and ops. For multi-server networks, **YaP Link** is our front door — a complete Velocity-class proxy — so players hit one address and get sent to the right backend.

Think of it like this:

| Piece | Everyday meaning |
|-------|------------------|
| **YaPcore** | The product you install and run — the “server in a box” |
| **Folia** | The actual Minecraft gameplay (the world and rules), multi-threaded by region |
| **YapEngine** | Our engine that organizes networking, plugins, and heavy lifting across fixed worker roles |
| **YaP Link** | Our network front door (full Velocity fork) — one public address, many backends |

**Short pitch:**  
*A Minecraft server that uses Folia for the game, YapEngine for the chassis, and YaP Link for the network — with Java and Bedrock players able to join the same place.*

---

## What problem are we solving?

Normal Minecraft servers (including classic Paper) do a lot of the “game tick” — the heartbeat that updates the world — on **one main thread**. That works, but when lots of players, farms, redstone, and plugins pile on, that one lane gets crowded and the server can lag.

**Folia** spreads game work across **regions** (that’s the game tick). **YapEngine** runs the **slim chassis** — network edge, dual-stack, plugins bridge, I/O, ops — **not** world tick. **YaP Link** lets you run **many** Folia boxes behind one public join address — the normal way big networks scale.

We’re **not** optimizing for empty lobby servers. The product is aimed at
**high-pop / heavy-load** networks. Fair bot cites focus on **~100 active players** per shard;
claiming “250 MSPT wins” from keepalive-only holds is not honest — see
[BENCH_VS_PAPER.md](BENCH_VS_PAPER.md).

---

## What you get as a player or server owner

1. **A real Minecraft server** — powered by Folia by default (Paper path still exists for legacy benches).  
2. **Java and Bedrock together** — toward the same shared world story (no Via\*/Geyser jars required for the product path).  
3. **Familiar plugins** — Folia/Paper-class jars and YaP plugins in **one** `plugins/` folder; Folia-aware plugins are the product path.  
4. **Network essentials shipped** — shared MariaDB pool (`yap-db`), cross-server player data with offline `/login`, claims/taxes/NPC traders, multi-pack helper, unsigned-chat fix, LuckPerms rank pack.  
5. **YaP Link** — first-party full Velocity fork for multi-backend networks ([YAP_LINK.md](YAP_LINK.md)).  
6. **Day-to-day ops tools** — config, desktop control panel, **web dashboard** (browser, for headless — including Ranks), resource packs, crash reports.  
7. **Vehicles built in** — real cars/trucks (not minecarts), fuel, upgrades, shop; HD models in the default pack.  
8. **A clear design, not a rename** — Folia game + Yap chassis + Link + dual-stack are intentional.

YaPcore does **not** embed a database. Owners run one Docker MariaDB (Linux + Windows scripts) and point every backend at it — see [MARIADB.md](MARIADB.md) / [PLAYERDATA.md](PLAYERDATA.md) / [PERMISSIONS.md](PERMISSIONS.md).

---

## How to picture the architecture (no tech degree needed)

Imagine a restaurant:

- **Folia** is the **kitchen** that cooks different parts of the map on different stations (regions).  
- **YapEngine** is the **building layout** — door staff, runners, storage, health checks (sixteen fixed jobs).  
- **YaP Link** is the **host at the front door** who seats guests at the right dining room when you run more than one kitchen.

Players still follow careful rules so plugins and inventory stay correct. Dual-stack means Java and Bedrock can share the same story without installing a pile of third-party protocol jars.

---

## Where we are today (honest status)

| Stage | In plain English | Status |
|-------|------------------|--------|
| Folia runs the game | Default product path | **Default on** |
| YapEngine chassis | Fixed worker roles always boot | **Done** |
| YaP Link proxy | Public join → Folia backends | **Shipped** (full Velocity fork) |
| Fair highpop cite | ~100 active bots MSPT | **Active gate** |
| Phase 4 dual-stack join | First-party Via\* + Geyser parity for supported bands | **In progress** — JE matrix + Bedrock smoke green for join/spawn; play depth deepening |
| Legacy Paper + Phase 3 spatial | Old four-map-cook path | **Opt-in / benches only** |

**What that means for you:**  
Install YaPcore expecting **Folia + YapEngine + (optionally) YaP Link**. We only claim MSPT wins with honest cites. Phase 4 is about dual-stack join depth — not “install Via and Geyser.”

---

## What we are *not*

- **Not** “Paper, but already faster in every situation.”  
- **Not** “Folia, but already better in every situation.”  
- **Not** a from-scratch rewrite of all of Minecraft.  
- **Not** “you must abandon Velocity plugins” — Link keeps the Velocity plugin API.  
- **Not** just Mojang’s plain `server.jar` with a new name.

---

## How we talk about it out loud

**For friends / non-tech people:**  
“We’re building a Minecraft server that uses Folia so the world can update on many workers, YapEngine to keep the rest of the product organized, and YaP Link so big networks have one front door. Java and Bedrock players can join the same world.”

**For technical people:**  
See [WHAT_WE_ARE.md](WHAT_WE_ARE.md) and [FULL_RUNDOWN.md](FULL_RUNDOWN.md).

**Elevator:**  
“Folia-backed Minecraft server with YapEngine edge/I/O chassis and YaP Link for the network.”

---

## Simple comparison

| Question | Paper alone | Folia alone | YaPcore |
|----------|-------------|-------------|---------|
| Real Minecraft gameplay? | Yes | Yes | Yes — we **use Folia** by default |
| World update on many workers? | Mostly one main thread | Yes — regions | Yes — Folia regions + Yap chassis |
| Built for empty lobbies or busy worlds? | Either | Busy | **Busy / high-pop** first |
| Network front door? | DIY Velocity | DIY Velocity | **YaP Link** (full Velocity fork) |
| Java + Bedrock in one product? | Usually needs extra tools | Usually needs extra tools | Built into our story |
| Plugins? | Paper plugins | Folia-aware plugins | Folia path + YaP jars — **one** `plugins/` folder |
| Browser ops without a desktop? | Usually third-party panels | Usually third-party | **Built-in web dashboard** (`:8080`) |
| Vehicles / cars? | Usually separate plugins | Usually separate | **Shipped** YaP Vehicles + default client pack |
| Shared MariaDB for networks? | Bring your own | Bring your own | **Shipped** Docker MariaDB + `yap-db` shared pool |
| Offline `/login` + claims / traders? | AuthMe / extras | AuthMe / extras | **Shipped** in `yap-playerdata` |
| Ranks / permission groups? | Install LuckPerms yourself | Install LuckPerms yourself | **LuckPerms pack** + install script + dashboard Ranks tab |

---

## Want more detail?

| If you want… | Read… |
|--------------|--------|
| Short technical identity | [WHAT_WE_ARE.md](WHAT_WE_ARE.md) |
| Full technical rundown | [FULL_RUNDOWN.md](FULL_RUNDOWN.md) |
| YaP Link proxy | [YAP_LINK.md](YAP_LINK.md) |
| Velocity stand-in notes | [VELOCITY.md](VELOCITY.md) |
| Headless / browser control | [WEB_DASHBOARD.md](WEB_DASHBOARD.md) |
| Vehicles | [VEHICLES.md](VEHICLES.md) |
| Stacker (mobs / items / spawners) | [STACKER.md](STACKER.md) |
| Shared MariaDB / YapDb | [MARIADB.md](MARIADB.md) · [YAPDB.md](YAPDB.md) |
| Player data / offline auth | [PLAYERDATA.md](PLAYERDATA.md) |
| Permissions / LuckPerms ranks | [PERMISSIONS.md](PERMISSIONS.md) |
| Paper / Folia port plan | [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md) |
| Docs index | [README.md](README.md) |
| Deep architecture paper | [whitepaper/YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) |
| Same paper, plain English | [whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) |
