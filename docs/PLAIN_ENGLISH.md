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
| **YaP Link** | Our network front door (native Velocity-class proxy) — one public address, many backends |

**Short pitch:**  
*Next-gen Minecraft server software — Folia for the game, built-in YaP plugins instead of Paper + ten jars, YaP Link for networks, Java and Bedrock on one product.*

---

## What problem are we solving?

Normal Minecraft servers (including classic Paper) do a lot of the “game tick” — the heartbeat that updates the world — on **one main thread**. That works, but when lots of players, farms, redstone, and plugins pile on, that one lane gets crowded and the server can lag.

**Folia** spreads game work across **regions** (that’s the game tick). **YapEngine** runs the **slim chassis** — network edge, dual-stack, plugins bridge, I/O, ops — **not** world tick. **YaP Link** lets you run **many** Folia boxes behind one public join address — the normal way big networks scale.

We’re **not** optimizing for empty lobby servers. The product is aimed at
**high-pop / heavy-load** networks. Fair bot cites focus on **~100 active players** per shard;
claiming “250 MSPT wins” from keepalive-only holds is not honest — see
[BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md).

---

## What you get as a player or server owner

1. **A real Minecraft server** — **Folia by default** (multithreaded regions). Paper is legacy/benches only.  
2. **Java and Bedrock together** — built-in dual-stack; no Via\*/Geyser jar stack.  
3. **Plugins most servers need — shipped** — perms, essentials, protect, world tools, chat, moderation, map, tab, DB, playerdata, link — see [PLUGIN_COMPAT_MATRIX.md](PLUGIN_COMPAT_MATRIX.md). You don’t assemble the old Paper plugin pile.  
4. **Network essentials** — shared MariaDB (`yap-db`), offline `/login`, claims, ranks pack, multi-pack HTTP.  
5. **YaP Link** — native proxy for multi-backend networks ([YAP_LINK.md](YAP_LINK.md)).  
6. **Ops** — control panel, **web dashboard**, resource packs, crash reports.  
7. **Vehicles** (opt-in) — real cars/trucks, fuel, shop.  
8. **Next-gen by design** — not “Paper + plugins”; one product for most survival/network ops.

YaPcore does **not** embed a database. Owners run one Docker MariaDB (Linux + Windows scripts) and point every backend at it — see [MARIADB.md](MARIADB.md) / [PLAYERDATA.md](PLAYERDATA.md) / [PERMISSIONS.md](PERMISSIONS.md).

---

## How to picture the architecture (no tech degree needed)

Imagine a restaurant:

- **Folia** is the **kitchen** that cooks different parts of the map on different stations (regions).  
- **YapEngine** is the **front-of-house and back-office** — networking, plugin bridge, storage, health checks (not the kitchen).
- **YaP Link** is the **host at the front door** who seats guests at the right dining room when you run more than one kitchen.

Players still follow careful rules so plugins and inventory stay correct. Dual-stack means Java and Bedrock can share the same story without installing a pile of third-party protocol jars.

---

## Where we are today (honest status)

| Stage | In plain English | Status |
|-------|------------------|--------|
| Folia runs the game | Default product path | **Default on** |
| YapEngine chassis | Fixed worker roles always boot | **Done** |
| YaP Link proxy | Public join → Folia backends | **Shipped** (native proxy, phases 0–6) |
| Fair highpop cite | ~100 active bots MSPT | **Active gate** |
| Phase 4 dual-stack join | First-party Via\* + Geyser parity for supported bands | **Join DoD green** — JE matrix + play-depth smoke; optional fidelity soak |
| Legacy Paper + Phase 3 spatial | Old four-map-cook path | **Opt-in / benches only** |

**What that means for you:**  
Install YaPcore expecting **Folia + YapEngine + (optionally) YaP Link**. We only claim MSPT wins with honest cites. Phase 4 is about dual-stack join depth — not “install Via and Geyser.”

---

## What we are *not*

- **Not** “Paper, but already faster in every situation.”  
- **Not** “Folia, but already better in every situation.”  
- **Not** a from-scratch rewrite of all of Minecraft.  
- **Not** “you must abandon Link plugins” — Link has its own plugin API (`yap-link-api`).  
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
| Network front door? | DIY Velocity | DIY Velocity | **YaP Link** (native Velocity-class proxy) |
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
| Roadmap phases | [FULL_RUNDOWN.md](FULL_RUNDOWN.md) |
| Docs index | [README.md](README.md) |
| Deep architecture paper | [whitepaper/YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) |
| Same paper, plain English | [whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) |
