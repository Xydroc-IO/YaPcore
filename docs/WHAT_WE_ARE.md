# What YaPcore is

> Prefer plain English? See [PLAIN_ENGLISH.md](PLAIN_ENGLISH.md).

## One sentence

**YaPcore** is a Minecraft server product with **three layers**: **Folia** runs the game (regionized tick), **YapEngine** runs the slim chassis (Netty, dual-stack, I/O, ops — not world tick), and **YaP Link** (native Velocity-class proxy, phased build-out) fronts multi-backend networks — plus Java + Bedrock on one join story, Folia/Paper-class plugins, and YaP all-in-one plugin pools.

## Are we “a better version of Paper”?

**No — and you usually don’t need Paper anymore.** YaPcore is **next-gen server software** for
most survival and network operators: **Folia** for multithreaded gameplay, **first-party YaP
plugins** for what people used to install separately (perms, essentials, protect, world edit,
chat, moderation, map, tab, link, dual-stack, DB, playerdata), and **YaP Link** for the proxy
edge. The old **Paper + ten plugins + Velocity + Geyser** assembly path is not the product story.

Legacy **`game-authority=paper`** remains for benches only — not for new deployments.

| | Paper + DIY plugins | Stock Folia + DIY | **YaPcore (default)** |
|--|---------------------|-------------------|------------------------|
| Game tick | Single main thread | Region threads | **Folia regions** |
| Typical network stack | Install LP, Essentials, WE, Velocity, Geyser, … | Same glue work | **Shipped natives** — [PLUGIN_COMPAT_MATRIX.md](PLUGIN_COMPAT_MATRIX.md) |
| Proxy / crossplay | DIY | DIY | **YaP Link** + built-in dual-stack |
| Who it’s for | Exotic Paper-only plugin niche | Folia jar purists | **Most servers** — survival, networks, high-pop |

So:

- We are **not** “Paper but already faster everywhere.”
- We are **not** claiming every Paper plugin still runs — use **YaP natives** or Folia-aware jars.
- We **are**: the **complete next-gen stack** — Folia game + YapEngine chassis + shipped plugin pool + Link + dual-stack so operators stop assembling Paper-era glue.

**Honest pitch today:**

> **Next-gen Minecraft for most operators — Folia gameplay, shipped YaP plugin stack (~90% of what networks used to glue on), YaP Link for multi-backend networks, dual-stack join + play-depth smoke green.**

## What we do (product surface)

1. **Run a joinable Minecraft server** for modern JE (target **26.2** / protocol 776; **Java 25+** for Folia/Paper).
2. **Own the public edge** with YapEngine chassis: watchdog, traffic/sequencing, dual-stack, compatibility bridge, UI/Heavy I/O sandboxes — **not** world tick (Folia owns that).
3. **Delegate the Minecraft game to Folia** (`game-authority=folia`, `folia-embed=true`). Legacy: `game-authority=paper` for Phase 3 benches.
4. **YaP Link** — first-party **native network proxy** (Velocity-class features, phased) for multi-backend networks. Stock Velocity remains optional stand-in — [YAP_LINK.md](YAP_LINK.md) · [YAP_LINK_NATIVE.md](YAP_LINK_NATIVE.md) · [VELOCITY.md](VELOCITY.md).
5. **Phase 3 Paper spatial (legacy):** interior entity tick on cores **3–6** with DLM leases — **defaults off** on the product path; re-enable only for Paperclip benches. Folia path does **not** run Phase 3 spatial tick.
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
- Not a claim of “faster Paper/Leaf in every workload.”
- Not a clean-room rewrite of all of Minecraft.
- Not a claim that DIY Folia+Velocity is obsolete — Link is the product proxy; stock Velocity still works.
- Not “full Geyser clone” marketing — join/spawn + play-depth smoke green; emotes/custom skulls partial.

## Architecture (three layers)

| Layer | What it does |
|-------|----------------|
| **YaP Link** | Proxy — forwarding, compression, `/server`, YaP Link plugins ([YAP_LINK.md](YAP_LINK.md)) |
| **YapEngine chassis** | Edge + I/O + plugin sandboxes (16 logical channels; **not** game tick) |
| **Folia** | Game — chunks, entities, redstone, commands (region thread pool) |

Chassis channel map (T1–16): [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md) · Legacy Paper spatial: [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md)

## Current phase (honest)

| Phase | Status |
|-------|--------|
| 1 — Paper wrap + TCP proxy | Done (optional via `paper-embed=false`) |
| 2 — Game owns public JE port | Done (Folia default; Paper legacy) |
| 3 — Tick on YapEngine cores 3–6 | **Done as code** — **retired as product default** (opt-in for Paper benches) |
| 3.5–3.7 — Interior / border world tick | **Done as code** — same: legacy Paper path only |
| **Folia product path** | **Default** — `game-authority=folia`; fetch/smoke via `scripts/fetch-folia.sh` / `smoke-folia.sh` |
| **YaP Link** | **Phases 0–6 shipped** — native Velocity-class proxy (`0.6.0-phase6`): forwarding through plugin platform, Bedrock UDP edge, metrics, release bundle — [YAP_LINK_NATIVE.md](YAP_LINK_NATIVE.md) · Bedrock at Link: [YAP_LINK.md](YAP_LINK.md#bedrock--geyser) |
| **Fair highpop MSPT** | **Active** — cite **~100 active bots**; 250 keepalive holds are HOLD-ONLY — [BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md) |
| 4 — Dual-stack + **Via + Geyser parity** (own code) + YaP network plugins | **Join DoD green** — JE matrix + BE play-depth smoke; optional fidelity soak — [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md) |

## How to say it out loud

**Short:** “A multi-threaded Minecraft server on YapEngine, using Folia for the game, with YaP Link (native proxy) for the network.”

**Accurate:** “We’re not another rename-only fork. Folia is the default game; YapEngine runs the chassis; YaP Link is our own proxy built to Velocity-class parity in phases. Dual-stack and YaP plugin pools are part of the product. Phase 3 Paper spatial stays for legacy benches.”
