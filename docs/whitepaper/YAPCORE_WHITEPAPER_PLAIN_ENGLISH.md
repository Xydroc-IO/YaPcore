# YaPcore whitepaper — plain English edition

**YapLabs · companion to the technical whitepaper**  
Version 0.1 · August 2026  
Pairs with: [YAPCORE_WHITEPAPER.md](YAPCORE_WHITEPAPER.md) (`YAP-WP-16T-001`)

This is the same story as the technical whitepaper, written for people who don’t live in systems engineering.  
If you want the academic / engineer version, use the [technical whitepaper](YAPCORE_WHITEPAPER.md).

---

## The big idea (abstract)

Most Minecraft-style servers put almost everything important on **one main worker**: updating the world, talking to plugins, and a lot of network-related work. That keeps the design simple — until the server gets busy, and then that one worker becomes a traffic jam.

**YaPcore** (built on **YapEngine**) splits that work across a **fixed set of sixteen workers**, each with a clear job. Ordering rules keep things from happening out of turn. Older plugins still work through a careful “compatibility” path. Java and Bedrock players can join toward the same world story. New plugins are guided into lanes so heavy database work doesn’t freeze the fun parts of the game.

This plain-English paper explains:

- Why that matters  
- How the sixteen jobs fit together  
- How plugins are supposed to behave  
- How we think about networking and testing  

---

## 1. What’s the problem?

### The everyday version

Imagine a store with **one cashier** for:

- ringing up customers  
- restocking shelves  
- answering the phone  
- updating the inventory computer  

When it’s quiet, that works. When it’s busy, the line piles up — even if other people in the building could help.

That’s roughly how classic Minecraft servers work: one “main thread” does the authoritative world updates (~20 heartbeats per second). If a plugin spends that time waiting on a database or the internet, the whole world feels laggy. If you instead let lots of workers change the world at once **with no rules**, you get broken chunks, duplicate mobs, and inventory glitches.

Bedrock players also use a different connection style (UDP) than Java (TCP), but operators usually still want **one shared world**, not two separate games.

### What YaPcore is trying to contribute

1. **Sixteen named jobs** — everyone knows who owns networking, map areas, sync, menus, and heavy file/database work.  
2. **Order tickets (SequenceToken)** — work can move between workers without everything sharing one giant lock.  
3. **A Compatibility Bridge** — older plugins can still change the world safely by waiting for the right window.  
4. **Java + Bedrock on one product story** — both can join; optionally even on the same port number.  
5. **Three ways to extend the server** — familiar Paper-style plugins, YaP plugins, and smaller “fine-tune” modules — all Paper/YaP jars drop into one `plugins/` folder.

### What we are *not* promising

We don’t claim “every Paper plugin works perfectly on day one,” and we’re not trying to be a full NeoForge dedicated-server clone. We grow compatibility based on what people actually need.

### Where the product is today (August 2026)

We use **Paper** for the real Minecraft game. Getting Paper to own the public
join path is done. Spreading **interior** entity, farm, hopper, and redstone work
across the four map-area workers — plus border work on a dedicated border worker —
is also **done** (Phases 3–3.7; those switches are **on by default**). The product
is aimed at **busy / high-pop** servers; we measure beat-Paper on a heavy
`heavypop` scoreboard and are **honest when we haven’t won yet**. Next is
Phase 4: polishing Java + Bedrock join (first-party Via/Geyser parity for supported bands)
and shipping network plugins — shared MariaDB (`yap-db`), playerdata (offline `/login`, claims),
LuckPerms ranks, multi-pack helpers — on that Paper-backed world.

Details: [PAPER_YAPENGINE_PORT.md](../PAPER_YAPENGINE_PORT.md) ·
[BENCH_VS_PAPER.md](../BENCH_VS_PAPER.md).

---

## 2. How this compares to other approaches

- **Paper / Purpur** improve the classic Bukkit model; Folia experiments with region-based multithreading.  
- **Proxies** (like Velocity) route players but don’t usually *own* the world.  
- **Research engines** often prove fancy parallel designs but drop familiar plugin support.

YaPcore sits in the middle:

> Keep a **predictable kitchen of sixteen roles**, and keep a **bridge** so older plugins don’t all have to be rewritten overnight.

---

## 3. Architecture in plain English

### 3.1 The sixteen jobs

| Workers | Job (plain English) |
|---------|---------------------|
| **1** | Watchdog — watches health and helps recover if something stalls |
| **2** | Front door / traffic — players connecting, order tickets assigned |
| **3–6** | Map cooks — four areas of the world (NW / NE / SW / SE) |
| **7** | Chunk leases — who temporarily “owns” which chunk work |
| **8** | Border referee — when two areas touch, resolve conflicts |
| **9** | Compatibility Bridge — stages older plugin world changes safely |
| **10–11** | Menus / UI polish — clicks, HUD, menu feel |
| **12–15** | Heavy chores — databases, HTTP, files, big saves |
| **16** | Telemetry — metrics and health signals |

More detail for engineers: [YAPENGINE_16THREAD.md](../YAPENGINE_16THREAD.md).

### 3.2 Order tickets (sequencing)

Every logical stream of work (a connection, a chunk lease, a plugin task) gets an **order ticket**. Within that stream, things must stay in order. Across different streams, work can happen in parallel. That way you get speed *and* sanity.

### 3.3 Splitting the map

The world is divided so the four map workers can often update **different regions** at the same time. Anything that crosses a border goes through the border referee before other areas treat it as official.

### 3.4 Memory cleanup (GC)

Production startups prefer a modern garbage collector (**Generational ZGC**) and can pin work to nearby CPU/memory banks (**NUMA**) on big machines when that helps. Details: [ZGC_NUMA.md](../ZGC_NUMA.md).

---

## 4. How plugins are supposed to behave

Plugins don’t all get the same lane. There are three:

| Lane | Meant for | Don’t do here |
|------|-----------|---------------|
| **SYNC** | Blocks, inventory, teleport, world changes | Slow database / HTTP waits |
| **HEAVY** | Databases, HTTP, disk, messaging | Direct world edits without hopping back to SYNC |
| **UI** | Menu animation and polish | Authoritative world writes |

If something tries to change the world from the wrong lane, the Compatibility Bridge can queue it for a safe moment — but authors should still schedule world work on purpose.

Smaller **modules** use the same lanes and can declare what they provide or need so operators can mix features cleanly. See [MODULES_AND_API.md](../MODULES_AND_API.md).

The product build ships **YaP Vehicles** (real cars/trucks — not minecarts), gameplay knobs,
**YapDb** / playerdata (shared MariaDB networks), packs/chat/floodgate helpers, and a **web dashboard**
in the browser for headless hosts (`:8080` — including a Ranks tab) alongside the desktop control panel.
Client textures/models come from the default pack `yapcore-default.zip`.

---

## 5. Networking & crossplay (the join story)

- **Java Edition** players connect the usual TCP way, including modern login/play flows.  
- **Bedrock** players connect over UDP; the same port number can be shared with Java when configured that way.  
- Operators can put a domain / reverse proxy (nginx) + Cloudflare in front — this
  project’s public hostname is **`yapcoremc.yaplabs.us`**.
- A **crossplay hub** aims at one shared player identity and world path across editions.

On the same computer as the server, use `127.0.0.1` to join (hairpin NAT issues are common otherwise).

---

## 6. How we evaluate whether it works

We recommend a practical test stack:

1. Unit tests for basic correctness  
2. Concurrency explorers (Fray and friends) to hunt race bugs  
3. Optional stress tools (JCStress / TSan / Infer)  
4. Long “soak” runs that record clear FAIL codes  
5. Profiling under load at the interesting boundaries  

What we care about measuring:

- How long ticks take in the worst cases  
- How backed-up the plugin bridge gets  
- How often borders need refereeing  
- Join success across Minecraft versions  
- Whether the HEAVY lane is overloaded  

See [TESTING.md](../TESTING.md).

---

## 7. Caveats (what could make results misleading)

- Rare Phase 3 threading edge cases can still surprise plugins that assume one entity thread (same “try it on Paper” discipline).  
- Minecraft versions keep changing; packet/registry work is ongoing.  
- NUMA / ZGC benefits depend on the machine.  
- Bedrock feature parity can lag Java for some gameplay packets.

---

## 8. Closing thought

YaPcore’s bet is practical:

> Split Minecraft-class server work into **sixteen specialized jobs**, give plugins **clear lanes**, and keep a **Compatibility Bridge** so the ecosystem isn’t thrown away.

Still ahead: richer registry sync, deeper world streaming, fuller command graphs, and stronger formal checks on the order-ticket queues.

---

## Where to go next

| If you are… | Start here |
|-------------|------------|
| Non-tech / just curious | [PLAIN_ENGLISH.md](../PLAIN_ENGLISH.md) |
| Reading the engineer paper | [YAPCORE_WHITEPAPER.md](YAPCORE_WHITEPAPER.md) |
| Running a server | [README](../../README.md), [NETWORKING](../NETWORKING.md), [WEB_DASHBOARD](../WEB_DASHBOARD.md) |
| Writing plugins | [PLUGINS](../PLUGINS.md), [MODULES_AND_API](../MODULES_AND_API.md), [VEHICLES](../VEHICLES.md) |
| Working on the engine | [YAPENGINE_16THREAD](../YAPENGINE_16THREAD.md), [PERF_AND_LAYOUT](../PERF_AND_LAYOUT.md) |

### Citation (technical paper)

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
