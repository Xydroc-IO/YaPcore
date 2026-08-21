# What YaPcore is

> Prefer plain English? See [PLAIN_ENGLISH.md](PLAIN_ENGLISH.md).

## One sentence

**YaPcore** is a Minecraft server product built around **YapEngine** (a fixed 16-thread chassis) that uses **Paper** as the game (world, entities, redstone, commands) and runs interior entity tick work on YapEngine’s spatial cores **3–6** — plus Java + Bedrock on one join story, Spigot/Paper-style plugins, and YaP all-in-one plugin pools.

## Are we “a better version of Paper”?

**Not that slogan — and not “faster everywhere.”** We use Paper for the game and YapEngine for the chassis.

| | Paper | YaPcore (today) |
|--|--------|------------------|
| Game (chunks, mobs, redstone, commands) | Yes — *is* the game | **Uses Paper** as the game |
| Multithreaded world tick | No (single main thread) | **Phase 3 / 3.5 / 3.7** — interior on cores 3–6; border entities/TE/events on T8 under DLM; players on Paper main |

| Thread design | Paper’s model | YapEngine **16-thread matrix** |
| Bedrock + Java same product | Usually via Geyser stack | Built-in dual-stack / crossplay path |
| Plugins | Bukkit/Spigot/Paper | One folder `plugins/` + YaP dual-pool API |

So:

- We are **not** “Paper but already faster everywhere.”
- We are **not** Folia (region pool).
- We **are**: **Paper’s game + YapEngine’s threading + YaPcore’s edge** (network, Bedrock, packs, shared MariaDB, GUI + **web dashboard**, vehicles, network playerdata, YaP plugins).

**Honest pitch today:**

> **High-pop Minecraft on YapEngine — Paper gameplay, Phase 3–3.7 spatial tick on by default, beat Paper on the `heavypop` MSPT scoreboard (not yet), Phase 4 first-party dual-stack + shipped network plugins (YapDb, playerdata, ranks).**

## What we do (product surface)

1. **Run a joinable Minecraft server** for modern JE (target **26.2** / protocol 776; **Java 25+** for Paper).
2. **Own the public edge** with YapEngine: watchdog, traffic/sequencing, spatial cores, sync/DLM, compatibility bridge, UI/IO sandboxes.
3. **Delegate the Minecraft game to Paper** (`game-authority=paper`, `paper-embed=true`).
4. **Phase 3 (done):** interior entity tick on cores **3–6** with DLM leases; border entity/TE/redstone tick on **T8** under DLM (`spatial-borders`). YaP Paperclip via `scripts/build-vendor-paper.sh` → `lib/paper-26.2-yap.jar` (required when `paper-phase3-nms-tick=true`; no silent fallback).
5. **Phases 3.5–3.7 (shipped, default on):** interior block/fluid/random + block entities/redstone on quads; border TE/events on T8. Product target is **high-pop / heavy load** — idle MSPT may lose; gate is `heavypop` ([BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)).
6. **Dual-stack:** Java + Bedrock toward one shared world — first-party Via\* + Geyser parity for supported bands ([PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md)); no Via\*/Geyser jars on the product path.
7. **Plugins:** all jars in `plugins/`; YaP plugins use SYNC/HEAVY/UI pools.
   **CORE+NETWORK (default):** `yap-placeholderapi`, `yap-pregen`, `yap-plugin-compat`,
   **`yap-db`**, **`yap-playerdata`**, **`yap-packs`**, **`yap-chat`**, **`yap-floodgate`**.
   **GAMEPLAY (opt-in):** `yap-vehicles`, `yap-gameplay-knobs`, **`yap-stacker`** —
   `gradle installGameplayDefaults` or `-PyapGameplay=true`.
8. **Ops:** config, multi-pack HTTP (`yapcore-default.zip` + extras), control GUI, **web dashboard** (`:8080` — Console, Packs, **Ranks**), LuckPerms pack ([PERMISSIONS.md](PERMISSIONS.md)), MariaDB Docker ([MARIADB.md](MARIADB.md)), crash/logging, `gradle assembleRelease`.
9. **Vehicles:** real chassis / fleet / fuel / upgrades / shop — GAMEPLAY opt-in — [VEHICLES.md](VEHICLES.md).

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
| 3 — Tick on YapEngine cores 3–6 | **Done** (leased interior + YaP Paperclip) |
| 3.5 — Interior block/fluid/random | **Done** (default on) |
| 3.6 — Block entities + redstone on quads | **Done** (default on) |
| 3.7 — Border entities/TE/events on T8 | **Done** (default on) |
| **Beat Paper `heavypop` MSPT** | **Active** — harness live; all-on still LOSS — [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md) |
| 4 — Dual-stack + **full Via + Geyser parity** (own code) + YaP network plugins | **In progress** — JE matrix + BE smoke join/spawn green — [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md) |

## How to say it out loud

**Short:** “A multi-threaded Minecraft server on YapEngine, using Paper for the game.”

**Accurate:** “We’re not another Paper fork that only renames branding. Paper is the game; YapEngine’s 16-thread design runs the chassis and Phase 3 spatial tick. Dual-stack and YaP plugin pools are part of the product.”
