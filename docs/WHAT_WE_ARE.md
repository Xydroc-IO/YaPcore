# What YaPcore is

> Prefer plain English? See [PLAIN_ENGLISH.md](PLAIN_ENGLISH.md).

## One sentence

**YaPcore** is a Minecraft server product built around **YapEngine** (a fixed 16-thread chassis) that uses **Paper** as the game (world, entities, redstone, commands) and runs interior entity tick work on YapEngine’s spatial cores **3–6** — plus Java + Bedrock on one join story, Spigot/Paper-style plugins, and YaP all-in-one plugin pools.

## Are we “a better version of Paper”?

**Not that slogan — and not “faster everywhere.”** We use Paper for the game and YapEngine for the chassis.

| | Paper | YaPcore (today) |
|--|--------|------------------|
| Game (chunks, mobs, redstone, commands) | Yes — *is* the game | **Uses Paper** as the game |
| Multithreaded world tick | No (single main thread) | **Phase 3 / 3.5** — interior entities + scheduled block/fluid/random on cores 3–6 under DLM leases; players + borders via main + T7/T8 |

| Thread design | Paper’s model | YapEngine **16-thread matrix** |
| Bedrock + Java same product | Usually via Geyser stack | Built-in dual-stack / crossplay path |
| Plugins | Bukkit/Spigot/Paper | One folder `plugins/` + YaP dual-pool API |

So:

- We are **not** “Paper but already faster everywhere.”
- We are **not** Folia (region pool).
- We **are**: **Paper’s game + YapEngine’s threading + YaPcore’s edge** (network, Bedrock, packs, GUI, YaP plugins).

**Honest pitch today:**

> **Paper gameplay on YapEngine’s multi-threaded tick (Phase 3 / 3.5) — beat Paper on a public MSPT scoreboard, then Phase 4 dual-stack + YaP plugins.**

## What we do (product surface)

1. **Run a joinable Minecraft server** for modern JE (target **26.2** / protocol 776; **Java 25+** for Paper).
2. **Own the public edge** with YapEngine: watchdog, traffic/sequencing, spatial cores, sync/DLM, compatibility bridge, UI/IO sandboxes.
3. **Delegate the Minecraft game to Paper** (`game-authority=paper`, `paper-embed=true`).
4. **Phase 3 (done):** interior entity tick on cores **3–6** with DLM leases; border work via T7/T8. YaP Paperclip via `scripts/build-vendor-paper.sh` → `lib/paper-26.2-yap.jar` (required when `paper-phase3-nms-tick=true`; no silent fallback).
5. **Phase 3.5 (in progress):** beat stock Paper on a public MSPT scoreboard — interior scheduled block/fluid + random ticks under the same leases; see [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md).
6. **Dual-stack:** Java + Bedrock toward one shared world (Phase 4 polish after scoreboard).
7. **Plugins:** all jars in `plugins/`; YaP plugins use SYNC/HEAVY/UI pools.
8. **Ops:** config, resource packs HTTP, control GUI, crash/logging tooling.

## What we are *not*

- Not a Mojang/`server.jar` proxy as the product (legacy `mojang` authority only).
- Not Folia / “better Folia.”
- Not a claim of “faster Paper in every workload.”
- Not a clean-room rewrite of all of Minecraft.

## Architecture (YapEngine 16 threads)

| Threads | Role |
|---------|------|
| 1 | Controller / watchdog |
| 2 | Traffic Cop + SequenceToken |
| 3–6 | Spatial game cores (NW / NE / SW / SE) — **Phase 3 tick** |
| 7–8 | Chunk sync DLM + boundary handoff |
| 9 | Compatibility Bridge (plugins → game) |
| 10–11 | UI sandbox |
| 12–15 | Heavy I/O |
| 16 | Telemetry / async worker |

Details: [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md) · Port plan: [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md)

## Current phase (honest)

| Phase | Status |
|-------|--------|
| 1 — Paper wrap + TCP proxy | Done (optional via `paper-embed=false`) |
| 2 — Paper owns public JE port | Done |
| 3 — Tick on YapEngine cores 3–6 | **Done** (leased interior + border handoffs; YaP Paperclip for NMS) |
| **3.5 — Beat Paper MSPT** | **Active** — bench harness + interior block/fluid/random; [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md) |
| 4 — Dual-stack + YaP plugins polished | **Next** (after scoreboard) |

## How to say it out loud

**Short:** “A multi-threaded Minecraft server on YapEngine, using Paper for the game.”

**Accurate:** “We’re not another Paper fork that only renames branding. Paper is the game; YapEngine’s 16-thread design runs the chassis and Phase 3 spatial tick. Dual-stack and YaP plugin pools are part of the product.”
