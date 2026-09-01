# YaPcore — plain English overview

**This page is for anyone who isn’t deep into server tech.**  
No jargon required. If you just want to know *what this is* and *what we’re building*, start here.

The technical versions live in [WHAT_WE_ARE.md](WHAT_WE_ARE.md) and [FULL_RUNDOWN.md](FULL_RUNDOWN.md).

---

## In one breath

**YaPcore** is software that runs a Minecraft server.

Players join and play Minecraft as usual. Under the hood, the world runs on **YaP-Folia** — our own multi-threaded game engine, built as a fork of PaperMC Folia 26.2 (not the stock Folia download). Around that, **YapEngine** keeps networking, plugins, and ops organized. For multi-server networks, **YaP Link** is the front door — one public address, many game backends.

| Piece | Everyday meaning |
|-------|------------------|
| **YaPcore** | The product you install and run — the “server in a box” |
| **YaP-Folia** | The actual Minecraft gameplay (world and rules), multi-threaded by region — **our fork** |
| **YapEngine** | Our chassis for networking, plugins, and heavy lifting (not the world heartbeat) |
| **YaP Link** | Our network front door — one public address, many backends |

**Short pitch:**  
*Next-gen Minecraft server software — YaP-Folia for the game, built-in YaP plugins instead of Paper + ten jars, YaP Link for networks, Java and Bedrock on one product.*

---

## What problem are we solving?

Normal Minecraft servers (including classic Paper) do a lot of the “game tick” — the heartbeat that updates the world — on **one main thread**. When lots of players, farms, redstone, and plugins pile on, that one lane gets crowded.

**YaP-Folia** spreads game work across **regions**. **YapEngine** runs the **slim chassis** — network edge, dual-stack, plugins bridge, I/O, ops — **not** world tick. **YaP Link** lets you run **many** game boxes behind one public join address.

We’re **not** optimizing for empty lobby servers. The product is aimed at **high-pop / heavy-load** networks. Fair bot cites focus on **~100 active players** per shard — see [BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md).

---

## What you get as a player or server owner

1. **A real Minecraft server** — **YaP-Folia by default** (our Folia fork). Paper is benches only.  
2. **Java and Bedrock together** — built-in dual-stack; no Via\*/Geyser jar stack.  
3. **Plugins most servers need — shipped** — perms, essentials, protect, world tools, chat, moderation, map, tab, DB, playerdata — see [PLUGIN_COMPAT_MATRIX.md](PLUGIN_COMPAT_MATRIX.md).  
4. **Network essentials** — shared MariaDB (`yap-db`), offline `/login`, claims, ranks, multi-pack HTTP.  
5. **YaP Link** — native proxy for multi-backend networks.  
6. **Ops** — control panel, **web dashboard**, resource packs, crash reports.  
7. **Vehicles** (opt-in) — real cars/trucks, fuel, shop.  
8. **Next-gen by design** — not “Paper + plugins”; one product for most survival/network ops.

YaPcore does **not** embed a database. Owners run one Docker MariaDB and point every backend at it — [MARIADB.md](MARIADB.md).

---

## How to picture the architecture

Imagine a restaurant:

- **YaP-Folia** is the **kitchen** that cooks different parts of the map on different stations (regions).  
- **YapEngine** is the **front-of-house and back-office** — networking, plugin bridge, storage, health checks.  
- **YaP Link** is the **host at the front door** when you run more than one kitchen.

Stock Folia would be “someone else’s kitchen plans.” We run **our** kitchen build with YapLabs patches (teleport safety, optional hot-region budgets, and more) — [FOLIA_FORK.md](FOLIA_FORK.md).

---

## Where we are today (honest status)

| Stage | In plain English | Status |
|-------|------------------|--------|
| YaP-Folia runs the game | Default product path (`folia-jar-source=build`) | **Default on** |
| YapEngine chassis | Fixed worker roles always boot | **Done** |
| YaP Link proxy | Public join → YaP-Folia backends | **Shipped** (phases 0–6) |
| Fair highpop cite | ~100 active bots MSPT | **Active gate** |
| Dual-stack join | First-party Via\* + Geyser-class for supported bands | **Join DoD green** |
| Legacy Paper + Phase 3 | Old Paper path | **Opt-in / benches only** |

**What that means for you:**  
Install YaPcore expecting **YaP-Folia + YapEngine + (optionally) YaP Link**. We only claim MSPT wins with honest cites.

---

## What we are *not*

- **Not** “Paper, but already faster in every situation.”  
- **Not** stock Folia with a new name.  
- **Not** a from-scratch rewrite of all of Minecraft.  
- **Not** just Mojang’s plain `server.jar` with a new name.

---

## How we talk about it out loud

**For friends / non-tech people:**  
“We’re building a Minecraft server that uses our own multi-threaded game engine (YaP-Folia), YapEngine to keep the rest of the product organized, and YaP Link so big networks have one front door. Java and Bedrock players can join the same world.”

**For technical people:**  
See [WHAT_WE_ARE.md](WHAT_WE_ARE.md) and [FULL_RUNDOWN.md](FULL_RUNDOWN.md).

**Elevator:**  
“YaP-Folia–backed Minecraft server with YapEngine edge/I/O chassis and YaP Link for the network.”

---

## Simple comparison

| Question | Paper alone | Stock Folia alone | YaPcore |
|----------|-------------|-------------------|---------|
| Real Minecraft gameplay? | Yes | Yes | Yes — **YaP-Folia** (our fork) |
| World update on many workers? | Mostly one main thread | Yes — regions | Yes — YaP-Folia regions + Yap chassis |
| Built for empty lobbies or busy worlds? | Either | Busy | **Busy / high-pop** first |
| Network front door? | DIY Velocity | DIY Velocity | **YaP Link** |
| Java + Bedrock in one product? | Usually extras | Usually extras | Built into our story |
| Plugins? | Paper plugins | Folia-aware plugins | YaP-Folia path + YaP jars — **one** `plugins/` folder |
| Browser ops? | Usually third-party | Usually third-party | **Built-in web dashboard** (`:8080`) |
| Shared MariaDB? | Bring your own | Bring your own | **Shipped** Docker + `yap-db` |

---

## Want more detail?

| If you want… | Read… |
|--------------|--------|
| Short technical identity | [WHAT_WE_ARE.md](WHAT_WE_ARE.md) |
| Full technical rundown | [FULL_RUNDOWN.md](FULL_RUNDOWN.md) |
| YaP-Folia fork | [FOLIA_FORK.md](FOLIA_FORK.md) |
| YaP Link proxy | [YAP_LINK.md](YAP_LINK.md) |
| Headless / browser control | [WEB_DASHBOARD.md](WEB_DASHBOARD.md) |
| Vehicles | [VEHICLES.md](VEHICLES.md) |
| Shared MariaDB / YapDb | [MARIADB.md](MARIADB.md) · [YAPDB.md](YAPDB.md) |
| Deep architecture paper | [whitepaper/YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) |
| Same paper, plain English | [whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) |
