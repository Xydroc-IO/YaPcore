# YaPcore — plain English overview

**This page is for anyone who isn’t deep into server tech.**  
No jargon required. If you just want to know *what this is* and *what we’re building*, start here.

The technical versions live in [WHAT_WE_ARE.md](WHAT_WE_ARE.md) and [FULL_RUNDOWN.md](FULL_RUNDOWN.md).

---

## In one breath

**YaPcore** is software that runs a Minecraft server.

Players join and play Minecraft as usual. Under the hood, we use a proven Minecraft game engine called **Paper** for the world, mobs, redstone, and commands — and we wrap that with our own system, **YapEngine**, which spreads work across fixed worker roles so the server can stay smoother as more people and activity show up.

Think of it like this:

| Piece | Everyday meaning |
|-------|------------------|
| **YaPcore** | The product you install and run — the “server in a box” |
| **Paper** | The actual Minecraft gameplay (the world and rules) |
| **YapEngine** | Our engine that organizes the heavy lifting across fixed worker roles |

**Short pitch:**  
*A Minecraft server that uses Paper for the game, and YapEngine to handle work across many threads — with Java and Bedrock players able to join the same place.*

---

## What problem are we solving?

Normal Minecraft servers (including Paper) do a lot of the “game tick” — the heartbeat that updates the world — on **one main thread**. That works, but when lots of players, farms, redstone, and plugins pile on, that one lane gets crowded and the server can lag.

YaPcore spreads more of that work across **dedicated workers**, instead of everything waiting in one line.

We’re **not** optimizing for empty lobby servers. The product is aimed at
**high-pop / heavy-load** networks — many entities, farms, hoppers, redstone —
where YapEngine’s spatial workers earn their keep. Light idle MSPT can look worse
with full spatial deferral on; that’s acceptable. The beat-Paper gate is the
`heavypop` scoreboard ([BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)).

---

## What you get as a player or server owner

1. **A real Minecraft server** — powered by Paper.  
2. **Java and Bedrock together** — toward the same shared world story (no Via\*/Geyser jars required for the product path).  
3. **Familiar plugins** — Paper/Spigot-style jars and YaP plugins in **one** `plugins/` folder; YaP plugins keep heavy jobs off the hot path.  
4. **Network essentials shipped** — shared MariaDB pool (`yap-db`), cross-server player data with offline `/login`, claims/taxes/NPC traders, multi-pack helper, unsigned-chat fix, LuckPerms rank pack.  
5. **Day-to-day ops tools** — config, desktop control panel, **web dashboard** (browser, for headless — including Ranks), resource packs, crash reports.  
6. **Vehicles built in** — real cars/trucks (not minecarts), fuel, upgrades, shop; HD models in the default pack.  
7. **A clear design, not a rename** — threading, dual-stack, and YaP tooling are intentional.

YaPcore does **not** embed a database. Owners run one Docker MariaDB (Linux + Windows scripts) and point every backend at it — see [MARIADB.md](MARIADB.md) / [PLAYERDATA.md](PLAYERDATA.md) / [PERMISSIONS.md](PERMISSIONS.md).

---

## How to picture the architecture (no tech degree needed)

Imagine a restaurant kitchen with **sixteen fixed jobs**:

- One person **watches the kitchen**  
- One person **takes orders at the door**  
- Four people **cook different areas of the map** (NW / NE / SW / SE)  
- Others handle **borders between stations**, **plugins**, **menus**, **heavy storage**, and **health checks**

Paper still supplies the **recipes and the food** (real Minecraft). YapEngine is the **kitchen layout**. Phases 3–3.7 mean those four map cooks already handle entities, farms, hoppers, and a lot of redstone in the *middle* of their stations; edge-of-station work goes to a border worker; players still follow careful main-thread rules.

---

## Where we are today (honest status)

| Stage | In plain English | Status |
|-------|------------------|--------|
| Early wrap | YaPcore sits in front of Paper | Done (optional) |
| Paper runs the public game | Players get real Paper gameplay | Done |
| Split map work across four workers | Interior entities, farms, hoppers, redstone on map-area workers; borders on a border worker | **Done (Phases 3–3.7)** |
| Beat Paper under heavy load | Public `heavypop` MSPT scoreboard — not won yet | **Active gate** |
| Phase 4 dual-stack join | First-party Via\* + Geyser parity for supported bands | **In progress** — JE matrix + Bedrock smoke green for join/spawn |

**What that means for you:**  
YaPcore is aimed at **busy / high-pop** servers, not empty lobbies. Paper does the game; YapEngine’s spatial workers are **on by default**. Light empty-world numbers can look worse — that’s OK. We only claim “faster under load” when the `heavypop` bench says so ([BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)). Phase 4 is about dual-stack join depth and YaP network plugins — not “install Via and Geyser.”

---

## What we are *not*

- **Not** “Paper, but already faster in every situation.”  
- **Not** Folia (a different multi-threaded approach).  
- **Not** a from-scratch rewrite of all of Minecraft.  
- **Not** just Mojang’s plain `server.jar` with a new name.

---

## How we talk about it out loud

**For friends / non-tech people:**  
“We’re building a Minecraft server that shares work across many workers so it stays smoother, while still using Paper so the game feels like real Minecraft. Java and Bedrock players can join the same world.”

**For technical people:**  
See [WHAT_WE_ARE.md](WHAT_WE_ARE.md) and [FULL_RUNDOWN.md](FULL_RUNDOWN.md).

**Elevator:**  
“Multi-threaded Minecraft server on YapEngine, using Paper for the game.”

---

## Simple comparison

| Question | Paper alone | YaPcore |
|----------|-------------|---------|
| Real Minecraft gameplay? | Yes | Yes — we **use Paper** |
| World update on many workers? | Mostly one main thread | Yes — interiors on four map workers; borders on a border worker (high-pop defaults on) |
| Built for empty lobbies or busy worlds? | Either | **Busy / high-pop** first |
| Java + Bedrock in one product? | Usually needs extra tools | Built into our story |
| Plugins? | Paper/Spigot plugins | Same + YaP jars — **one** `plugins/` folder |
| Browser ops without a desktop? | Usually third-party panels | **Built-in web dashboard** (`:8080`) |
| Vehicles / cars? | Usually separate plugins | **Shipped** YaP Vehicles + default client pack |
| Mob / item stacker? | Usually separate plugins | **Shipped** YaP Stacker (no fragile server internals) |
| Shared MariaDB for networks? | Bring your own + each plugin’s pool | **Shipped** Docker MariaDB + `yap-db` shared pool |
| Offline `/login` + claims / traders? | AuthMe / GriefPrevention / extras | **Shipped** in `yap-playerdata` |
| Ranks / permission groups? | Install LuckPerms yourself | **LuckPerms pack** + install script + dashboard Ranks tab |

---

## Want more detail?

| If you want… | Read… |
|--------------|--------|
| Short technical identity | [WHAT_WE_ARE.md](WHAT_WE_ARE.md) |
| Full technical rundown | [FULL_RUNDOWN.md](FULL_RUNDOWN.md) |
| Headless / browser control | [WEB_DASHBOARD.md](WEB_DASHBOARD.md) |
| Vehicles | [VEHICLES.md](VEHICLES.md) |
| Stacker (mobs / items / spawners) | [STACKER.md](STACKER.md) |
| Shared MariaDB / YapDb | [MARIADB.md](MARIADB.md) · [YAPDB.md](YAPDB.md) |
| Player data / offline auth | [PLAYERDATA.md](PLAYERDATA.md) |
| Permissions / LuckPerms ranks | [PERMISSIONS.md](PERMISSIONS.md) |
| Paper → YapEngine phases | [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md) |
| Docs index | [README.md](README.md) |
| Deep architecture paper | [whitepaper/YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) |
| Same paper, plain English | [whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) |
