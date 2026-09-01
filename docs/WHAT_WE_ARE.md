# What YaPcore is

> Prefer plain English? See [PLAIN_ENGLISH.md](PLAIN_ENGLISH.md).

## One sentence

**YaPcore** is a Minecraft server product with **three layers**: **YaP-Folia** (our Folia 26.2 fork) runs the game, **YapEngine** runs the slim chassis (Netty, dual-stack, I/O, ops — not world tick), and **YaP Link** fronts multi-backend networks — plus Java + Bedrock on one join story and a shipped first-party plugin stack.

## Are we “a better version of Paper”?

**No — and you usually don’t need Paper anymore.** YaPcore is next-gen server software for most survival and network operators: **YaP-Folia** for multithreaded gameplay, **first-party YaP plugins** for what people used to install separately, and **YaP Link** for the proxy edge. The old **Paper + ten plugins + Velocity + Geyser** assembly path is not the product story.

We are **not** “stock Folia with a sticker.” The game jar is **YaP-Folia** (`lib/yap-folia-26.2.jar`, `folia-jar-source=build`). Stock Fill Folia is fallback only. See [FOLIA_FORK.md](FOLIA_FORK.md).

Legacy **`game-authority=paper`** remains for benches only — not for new deployments.

| | Paper + DIY plugins | Stock Folia + DIY | **YaPcore (default)** |
|--|---------------------|-------------------|------------------------|
| Game tick | Single main thread | Upstream Folia regions | **YaP-Folia** regions (+ our patches) |
| Typical network stack | LP, Essentials, WE, Velocity, Geyser, … | Same glue work | **Shipped natives** — [PLUGIN_COMPAT_MATRIX.md](PLUGIN_COMPAT_MATRIX.md) |
| Proxy / crossplay | DIY | DIY | **YaP Link** + built-in dual-stack |
| Who it’s for | Exotic Paper-only niche | Folia jar purists | **Most servers** — survival, networks, high-pop |

So:

- We are **not** “Paper but already faster everywhere.”
- We are **not** claiming every Paper plugin still runs — use **YaP natives** or Folia-aware jars on YaP-Folia.
- We **are**: YaP-Folia game + YapEngine chassis + shipped plugin pool + Link + dual-stack.

**Honest pitch today:**

> **Next-gen Minecraft for most operators — YaP-Folia gameplay, shipped YaP plugin stack, YaP Link for multi-backend networks, dual-stack join + play-depth smoke green.**

## What we do (product surface)

1. **Run a joinable Minecraft server** for modern JE (target **26.2** / protocol ~776; **Java 25+**).
2. **Own the public edge** with YapEngine chassis: watchdog, traffic/sequencing, dual-stack, compatibility bridge, UI/Heavy I/O sandboxes — **not** world tick.
3. **Delegate the Minecraft game to YaP-Folia** (`game-authority=folia`, `folia-embed=true`, `folia-jar-source=build`).
4. **YaP Link** — first-party native Velocity-class proxy (`0.6.0-phase6`) — [YAP_LINK.md](YAP_LINK.md) · [YAP_LINK_NATIVE.md](YAP_LINK_NATIVE.md).
5. **Phase 3 Paper spatial (legacy):** defaults **off**; Folia path has **no** Phase 3 spatial tick.
6. **Dual-stack:** first-party Via\* + Geyser-class code — no Via\*/Geyser jars — [VIA_GEYSER_PARITY.md](VIA_GEYSER_PARITY.md).
7. **Plugins:** all jars in `plugins/` (symlinked into `folia-kernel/plugins`).
   **CORE+NETWORK (default):** perms, essentials, protect, world, chat, moderation, map, tab, DB, playerdata, packs, floodgate, lagguard, …
   **GAMEPLAY (opt-in):** vehicles, stacker, knobs, MMO stack — `gradle installGameplayDefaults` or `-PyapGameplay=true`.
8. **Ops:** config, multi-pack HTTP, control GUI, **web dashboard** (`:8080`), MariaDB Docker, `gradle assembleRelease`.
9. **Vehicles:** real chassis / fleet / fuel — GAMEPLAY opt-in — [VEHICLES.md](VEHICLES.md).

## What we are *not*

- Not stock PaperMC Folia as the product jar.
- Not a claim of “faster Paper/Leaf in every workload.”
- Not a clean-room rewrite of all of Minecraft.
- Not “full Geyser clone” marketing — join/spawn + play-depth smoke green; some fidelity still partial.
- Not Mojang/`server.jar` as the product.

## Architecture (three layers)

| Layer | What it does |
|-------|----------------|
| **YaP Link** | Proxy — forwarding, compression, `/server`, Link plugins |
| **YapEngine chassis** | Edge + I/O + plugin sandboxes (**not** game tick) |
| **YaP-Folia** | Game — chunks, entities, redstone, commands (region threads) |

Chassis channel map: [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md). Fork patches: [FOLIA_FORK.md](FOLIA_FORK.md).

## Current phase (honest)

| Phase | Status |
|-------|--------|
| YaP-Folia product path | **Default** — `folia-jar-source=build` |
| YapEngine chassis | **Always on** |
| YaP Link | **Phases 0–6 shipped** (`0.6.0-phase6`) |
| Fair highpop MSPT | **Active** — cite ~100 active bots — [BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md) |
| Dual-stack join DoD | **Green** — JE matrix + BE play-depth smoke |
| Phase 3 Paper spatial | **Legacy / off** |

## How to say it out loud

**Short:** “A multi-threaded Minecraft server on YapEngine, using **YaP-Folia** for the game, with YaP Link for the network.”

**Accurate:** “We’re not another rename-only Folia wrap. **YaP-Folia** is our patched Folia 26.2 fork; YapEngine runs the chassis; YaP Link is our own proxy. Dual-stack and YaP plugin pools are part of the product.”
