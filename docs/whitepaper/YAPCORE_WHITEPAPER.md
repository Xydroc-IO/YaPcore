# YaPcore: YaP-Folia Game Authority, Slim Edge Chassis, Native Network Stack, and First-Party Plugin Suite

**YapLabs Technical Whitepaper**  
Version **0.3** · September 2026  
Document ID: `YAP-WP-16T-001`  
Supersedes: v0.2 (August 2026)

> Prefer plain English? See [YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md).  
> Operator rundown: [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md).

---

## Abstract

Minecraft-class game servers traditionally serialize world mutation, plugin callbacks, and much of network I/O onto a single “main” thread, trading simplicity for latency under concurrent load. **YaPcore** ships as a three-layer product:

1. **YaP-Folia** — YapLabs’ maintained fork of PaperMC Folia **26.2** — owns regionized game tick (`lib/yap-folia-26.2.jar`, `folia-jar-source=build`).
2. **YapEngine** — a slim chassis in the YaPcore parent process — owns the public edge: watchdog, Netty traffic/sequencing, Compatibility Bridge, UI sandboxes, and heavy I/O workers (**not** world tick).
3. **YaP Link** — a first-party Velocity-class proxy (`0.6.0-phase6`) — fronts multi-backend networks.

A **SequenceToken** model orders work across chassis streams. Folia-aware first-party plugins use an explicit **SYNC / HEAVY / UI** contract via [`YapSched`](../plugins/MODULES_AND_API.md). The product ships a **CORE+NETWORK** plugin suite (permissions, chat, moderation, playerdata/economy, protect, world, regions, map, …) and an opt-in **GAMEPLAY** tier (vehicles, stacker, knobs, and a full **MMO** stack M0–M7). Dual-stack **Java TCP + Bedrock UDP** is first-party (no Via\*/Geyser jars). Stock Fill Folia and Paper + Phase 3 spatial tick remain **legacy / bench** paths only.

This paper describes architecture, concurrency invariants, networking/crossplay, the shipped plugin and data plane, evaluation methodology, and honest product status as of September 2026.

**Keywords:** game server concurrency; Folia fork; regionized tick; plugin compatibility; Minecraft protocol; dual-stack crossplay; generational ZGC; MariaDB; MMO progression.

---

## Table of contents

1. [Introduction](#1-introduction)
2. [Related work](#2-related-work)
3. [Architecture](#3-architecture)
4. [YaP-Folia fork](#4-yap-folia-fork)
5. [Plugin concurrency contract](#5-plugin-concurrency-contract)
6. [Shipped first-party plugins](#6-shipped-first-party-plugins)
7. [Data plane](#7-data-plane)
8. [YaP Link](#8-yap-link)
9. [Networking & crossplay](#9-networking--crossplay)
10. [Ops surface](#10-ops-surface)
11. [Evaluation methodology](#11-evaluation-methodology)
12. [Threats to validity](#12-threats-to-validity)
13. [Product status (September 2026)](#13-product-status-september-2026)
14. [Conclusion](#14-conclusion)
15. [References](#15-references)
16. [Appendices](#appendices)

---

## 1. Introduction

### 1.1 Problem

Vanilla and Paper-derived Java Edition servers concentrate authoritative world updates on one thread (~20 TPS). Plugins that perform database or HTTP work on that thread stall ticks. Unsynchronized parallel mutation produces torn chunks, duplicate entities, and inventory races. Network operators still assemble **LuckPerms + EssentialsX + CoreProtect + WorldEdit + WorldGuard + Velocity + Geyser + …** by hand. Bedrock clients require a second transport (UDP) while operators demand a single shared world.

### 1.2 Contribution

YaPcore contributes:

1. A **three-layer product stack**: YaP-Folia game tick + YapEngine edge/I/O chassis + YaP Link proxy.
2. A **maintained Folia fork** (branding, teleport transactions, optional hot-region budgets / partition) — [QUICK_START.md](../start/QUICK_START.md).
3. **SequenceToken** sequencing for ordered handoff across chassis threads — [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md).
4. A **Compatibility Bridge** that stages legacy Bukkit mutations onto the game-core drain window (non-product Paper path; best-effort on Folia via Folia APIs + `YapSched`).
5. Dual-stack **Java TCP + Bedrock UDP** ingress with optional shared listen port — first-party code, not Via\*/Geyser jars — [CROSSPLAY.md](../network/CROSSPLAY.md).
6. A **three-tier extension model**: Folia-aware plugins (`plugin.yml`), YaP plugins (`yap.yml`), and fine-tune modules (`module.yml`) — [PLUGINS.md](../plugins/PLUGINS.md) · [MODULES_AND_API.md](../plugins/MODULES_AND_API.md).
7. A **shipped first-party plugin suite** that replaces the common DIY glue stack for ~90% of survival/network operators — §6.
8. An opt-in **RuneScape-style MMO progression stack** (skills, combat, crafting, quests, abilities, Bedrock UI) — [MMO_PHASES.md](../mmo/MMO_PHASES.md).

### 1.3 Non-goals

- **Stock Paper plugins on YaP-Folia** are unsupported (same reality as upstream Folia). Prefer Folia-aware jars or YaP natives — [PLUGIN_COMPAT_MATRIX.md](../plugins/PLUGIN_COMPAT_MATRIX.md).
- YaPcore is **not** a clean-room rewrite of Minecraft; it **forks Folia on purpose**.
- We do **not** claim “faster than Paper/Leaf on every workload.” Population cite: **fullcite** (100 active bots + fixtures) under the **ship Folia knob profile** (async-save, hopper budget, MSPT-gated entity/microtick budgets, subregion partition) — yapcore **−12.4%** vs stock Folia (`20260904TshipFc2`); knobs disclosed in bench JSON (`knob_*`). See [`YAP_FOLIA_SOAK.md`](../folia/YAP_FOLIA_SOAK.md). **250 keepalive = HOLD-ONLY**.
- Bedrock play-depth is **join/spawn + play-depth smoke green**; Wave 2 fidelity matrix
  (inventory/forms/G.33 heads) is in [CROSSPLAY.md](../network/CROSSPLAY.md) — Floodgate-only
  forms and anvil/smithing UIs are **Limited/Out**, not silent Partial.
- The Compatibility Bridge facade (non-game authority) remains **best-effort stubs** — [PAPER_API_COVERAGE.md](../plugins/PAPER_API_COVERAGE.md).

### 1.4 Audience

| Audience | Primary sections |
|----------|------------------|
| Operators / network owners | §3, §6–10, §13 |
| Plugin authors | §5–7, Appendix A |
| Systems / engine contributors | §3–4, §11–12 |
| Non-technical readers | [Plain English whitepaper](YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) |

---

## 2. Related work

**Paper / Purpur / Pufferfish / Leaf** improve the classic Bukkit single-main-tick model (performance and/or knobs). **PaperMC Folia** introduces regionized multithreading for Bukkit-class servers. **YaPcore forks Folia** as **YaP-Folia** rather than shipping upstream Fill as the product jar. Peer Folia forks (e.g. Canvas) typically leave operators to assemble proxy + plugins themselves.

**Netty-based proxies** (Velocity; YaP Link) separate player routing from world authority. **Geyser / Via\*** stacks provide dual-stack crossplay as separate jars; YaPcore embeds Via-class and Geyser-class code in chassis/`crossplay` packages.

YaPcore sits as: **YaP-Folia’s game + deterministic YapEngine thread roles + first-party Link + shipped plugin/data plane**, rather than “DIY Folia + Velocity + ten community plugins.” Comparison matrix: [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md).

---

## 3. Architecture

### 3.1 Three layers (product)

```
Clients (JE TCP / BE UDP)
        │
        ▼ optional
   YaP Link :25565  ──forwarding──►  YaPcore Via edge :25566
        │                                    │
        ▼                                    ▼
 YapEngine chassis (YaPcore parent)     YaP-Folia child JVM
   Traffic · Bridge · UI · Heavy I/O      **GAME TICK** (regions)
                                          folia-kernel/ + lib/yap-folia-*.jar

Legacy (benches only): game-authority=paper + Phase 3 on chassis
```

| Layer | Game tick? | Role |
|-------|------------|------|
| **YaP Link** | No | Proxy JVM — multi-backend routing, compression, Link plugins (`0.6.0-phase6`) |
| **YapEngine chassis** | No | Edge, bridge, UI/Heavy I/O, telemetry, dual-stack gateway |
| **YaP-Folia** | **Yes** | Region thread pool in embedded JVM (`lib/yap-folia-*.jar`) |

Default product properties:

```properties
game-authority=folia
folia-embed=true
folia-version=26.2
folia-jar-source=build
folia-teleport-transactions=true
paper-phase3-tick-bridge=false
paper-phase3-nms-tick=false
```

### 3.2 Chassis channel matrix (T1–16)

| ID | Role | Responsibility (v2.0 / product) |
|----|------|----------------------------------|
| 1 | Controller | Watchdog, recovery, process health |
| 2 | Traffic Cop | Ingress shaping, SequenceToken assignment |
| 3–6 | Worker quads | Sequenced bridge/plugin tasks; **legacy Phase 3 NMS tick on Paper benches only** |
| 7 | Chunk Sync DLM | Deferred lease / chunk ownership (**Paper Phase 3 legacy**) |
| 8 | Boundary Arbitrator | Cross-quadrant handoff (**Paper Phase 3 legacy**) |
| 9 | Compatibility Bridge | Legacy SYNC mutation queue → YaP-Folia region APIs |
| 10–11 | UI sandbox | Menu polish, click routing |
| 12–15 | Heavy I/O | DB, HTTP, files, proxy sync |
| 16 | Telemetry | Metrics / JFR hooks |

See [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md).

### 3.3 Sequencing

Each logical stream obtains a `SequenceToken` carrying a per-stream sequence and a global identifier. Strict ordered queues refuse out-of-order commits within a stream while allowing cross-stream parallelism — [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md).

### 3.4 Spatial model

**YaP-Folia** indexes world interest by region and runs authoritative tick on a dynamic region thread pool. Optional YaP patches add teleport transactions (default **on**), entity tick budgets, async chunk save, scoreboard SWMR, and experimental subregion partition (default **off**) — §4. **YapEngine chassis quads (T3–6)** route sequenced bridge/plugin work; they do **not** replace YaP-Folia game tick. Legacy Paper Phase 3 used quads + T7/T8 for interior NMS tick (**benches only**).

### 3.5 Memory & GC posture

Production launch scripts prefer **Generational ZGC** with optional **NUMA** pinning — [TUNE.md](../ops/TUNE.md). **Java 25+** is required.

---

## 4. YaP-Folia fork

YaPcore does **not** ship stock PaperMC Folia as the product game jar. Upstream pin lives in `vendor/folia/UPSTREAM.lock`; ordered patches in `vendor/folia/patches/`.

| Patch | Purpose | Default |
|-------|---------|---------|
| `0000` | Branding → YaP-Folia | always |
| `0001` | Cross-region teleport PREPARE/COMMIT/CONFIRM | **on** |
| `0010` | Async chunk save (Moonrise flush off region thread) | **on** (ship) |
| `0011` | Scoreboard SWMR | **on** (ship) |
| `0012` / `0023` | Smart Mob AI tick budget (MSPT-gated) | **400** (ship); 0=off |
| `0013` | Region pool metrics / microtick knobs | microtick **8 ms** (ship) |
| `0014`–`0019` / `0024` | Subregion partition + harden | **on** (ship; hysteresis) |
| `0022` | Hopper BE transfer budget | **64** (ship) |

Build: `./scripts/build-yap-folia.sh` → `lib/yap-folia-26.2.jar`.  
Docs: [YAP_FOLIA_SOAK.md](../folia/YAP_FOLIA_SOAK.md) · [REAL_GAINS.md](../folia/REAL_GAINS.md) · [QUICK_START.md](../start/QUICK_START.md).

Stock Folia fallback: `folia-jar-source=fetch` + `./scripts/fetch-folia.sh` (bench / comparison only).

---

## 5. Plugin concurrency contract

| Pool | Allowed work | Forbidden |
|------|--------------|-----------|
| **SYNC** (region / entity / global via `YapSched`) | Block / inventory / teleport / world | Blocking DB / HTTP |
| **HEAVY** | JDBC, HTTP, disk, messaging | Direct block set / `openInventory` without hop |
| **UI** | Menu animation, polish | Authoritative world writes |

Rules for every first-party jar:

- `folia-supported: true` in `plugin.yml`
- World/block/entity work via `com.yapcore.sched.YapSched` (or Folia region APIs)
- Shared persistence via **YaPDB** when MariaDB is required
- Public APIs registered on Bukkit `ServicesManager`

Operator layout: all jars drop into **`plugins/`** (symlinked into `folia-kernel/plugins`). Fine-tune packaging modules drop into **`modules/`**. See [PLUGINS.md](../plugins/PLUGINS.md) · [PLUGIN_COMPAT.md](../plugins/PLUGIN_COMPAT.md).

---

## 6. Shipped first-party plugins

Sources live under `yap-first-party/`. Install tiers:

| Tier | Gradle | Audience |
|------|--------|----------|
| **CORE+NETWORK** | `gradle installProductDefaults` | Every release |
| **GAMEPLAY** | `gradle installGameplayDefaults` or `-PyapGameplay=true` | Opt-in survival / MMO |
| **Both** | `gradle installAllProductDefaults` | Full product box |
| **Dist** | `gradle assemblePluginDist` | `build/dist/yap-plugins/{core-network,gameplay,api,modules}/` |

### 6.1 CORE + NETWORK (default product)

| Jar | Plugin | Role |
|-----|--------|------|
| `yap-db.jar` | YaPDB | Shared MariaDB Hikari pool |
| `yap-perms.jar` | YaPPerms | Groups, tracks, prefixes (`/yapperm`, `/promote`) |
| `yap-playerdata.jar` | YaPPlayerData | Cross-server sync, auth, economy, homes/warps/kits/mail, **chest shops**, **AH**, claims |
| `yap-moderation.jar` | YaPModeration | Ban / mute / warn / kick + history |
| `yap-essentials.jar` | YaPEssentials | Essentials-class QoL (`/spawn`, `/tpa`, `/fly`, `/vanish`, …) |
| `yap-chat.jar` | YaPChat | Channels, PM, filter, staff chat |
| `yap-packs.jar` | YaPPacks | Multi resource-pack push |
| `yap-floodgate.jar` | YaPFloodgate | Bedrock identity without Floodgate jar |
| `yap-placeholderapi.jar` | PlaceholderAPI | Clip-compatible PAPI (local expansions; eCloud out of scope by design) |
| `yap-plugin-compat.jar` | YaPPluginCompat | 1.20–1.21 → 26.2 back-compat status |
| `yap-pregen.jar` | YaPPregen | Folia-safe chunk pre-generator |
| `yap-folia-bridge.jar` | YaPFoliaBridge | Folia surface / scheduler smoke |
| `yap-protect.jar` | YaPProtect | CoreProtect-class audit / rollback / restore |
| `yap-world.jar` | YaPWorld | FAWE-class edit (masks, brushes, schems) + world mgmt |
| `yap-regions.jar` | YaPRegions | WorldGuard-class cuboid flags |
| `yap-npcs.jar` | YaPNpcs | Quest NPCs + dialogue |
| `yap-tab.jar` | YaPTab | Tab list / header / footer / sidebar |
| `yap-discord.jar` | YaPDiscord | Discord webhooks + relay |
| `yap-guard.jar` | YaPGuard | Lightweight anti-cheat heuristics |
| `yap-lagguard.jar` | YaPLagGuard | Per-chunk lag governor |
| `yap-map.jar` | YaPMap | Web flat map (Leaflet + PNG tiles + live markers; 3D Stretch) |
| `yap-factions.jar` | YaPFactions | Factions overlay on playerdata claims |
| `yap-bedrock-ui.jar` | YaPBedrockUI | Bedrock `FormService` bridge |

### 6.2 GAMEPLAY (opt-in)

| Jar | Plugin | Role |
|-----|--------|------|
| `yap-vehicles.jar` | YaPVehicles | Real vehicle mechanics (not minecarts) |
| `yap-stacker.jar` | YaPStacker | PDC mob / item / spawner stacker |
| `yap-gameplay-knobs.jar` | YaPGameplayKnobs | YaP encyclopedia (Purpur-inspired; original code) |
| `yap-skills.jar` | YaPSkills | 13 RS-style skills + `/skills` GUI |
| `yap-combat.jar` | YaPCombat | Custom PvE combat, gear, food, potions, spells, prayer |
| `yap-crafting.jar` | YaPCrafting | Recipes, stations, `/sell` |
| `yap-mmo-content.jar` | YaPMmoContent | Quests v2, bosses, skill areas, hiscores |
| `yap-abilities.jar` | YaPAbilities | Config-driven ability engine (230+) |
| `yap-mechanics.jar` | YaPMechanics | Stamina, resource nodes, farming |
| `yap-games.jar` | YaPGames | Minigames (arenas, queue, duels) |
| `yap-guilds.jar` | YaPGuilds | MMO guilds |
| `yap-mmo-bedrock.jar` | YaPMmoBedrock | Bedrock MMO forms UI |

**MMO milestones M0–M7** are shipped — [MMO_PHASES.md](../mmo/MMO_PHASES.md). Combat skill XP is owned by **YaPCombat** when loaded; YaPSkills provides a vanilla-damage fallback when combat is absent.

### 6.3 APIs & modules

Nineteen `yap-*-api` jars under `yap-first-party/api/` for soft-depend authors. Fine-tune modules under `modules/` declare `provides` / `requires` and write `FINE_TUNE.txt` pointers at real config knobs — they are **packaging**, not alternate game engines. Games packaging modules (`yap-games-module`, FFA, duels) install with GAMEPLAY / `installFineTuneModules`.

### 6.4 What first-party plugins intentionally replace

| Community staple | YaP native |
|------------------|------------|
| LuckPerms | YaPPerms |
| EssentialsX (QoL) | YaPEssentials (+ playerdata for data/economy) |
| CoreProtect | YaPProtect |
| WorldEdit / FAWE-class ops | YaPWorld (Folia-safe; stock FAWE not used) |
| WorldGuard | YaPRegions (+ playerdata claims) |
| TAB / scoreboard | YaPTab |
| DiscordSRV (MVP) | YaPDiscord |
| Dynmap-class | YaPMap |
| PlaceholderAPI | Bundled Clip-compatible engine |
| Floodgate | YaPFloodgate |
| Velocity | YaP Link |
| Via\* + Geyser jars | Chassis dual-stack |

---

## 7. Data plane

### 7.1 YaPDB

`yap-db.jar` exposes a shared Hikari pool. Prefer `use-shared-yapdb: true` in consumers. Setup: `./scripts/db/ensure-db.sh --server-id lobby` — [YAPDB.md](../data/YAPDB.md) · [MARIADB.md](../data/MARIADB.md).

### 7.2 YaPPlayerData

Cross-server inventory / XP / vitals sync, session lock (always on), optional offline `/login`, economy (`/bal` `/pay` + Vault), homes/warps/kits/mail, claims + tax, NPC traders (opt-in).

**Default feature flags (September 2026):**

| Feature | Default |
|---------|---------|
| Economy | **on** |
| Homes / warps / kits / mail / claims | **on** |
| **Chest shops** (`/shop`) | **on** |
| **Auction house** (`/ah`) | **on** |
| Jobs | **off** (keep off when YaPSkills is enabled — avoids double mining payouts) |
| NPC traders | **off** (opt-in) |

Smoke: . Docs: [PLAYERDATA.md](../data/PLAYERDATA.md).

### 7.3 Permissions

Native ranks/groups/tracks — [PERMISSIONS.md](../ops/PERMISSIONS.md). Soft integration with TAB prefixes and dashboard ranks APIs.

---

## 8. YaP Link

First-party **Velocity-class** proxy — **not** a Velocity fork. Phases **0–6 shipped** (`0.6.0-phase6`): passthrough ping, forced hosts, health failover, chat relay, system chat, Link plugin loader, edge rate limits, metrics hooks, two-backend smoke.

Link plugins (`link-plugin.json`): chat-bridge, mod-sync, server-selector, tab-bridge, discord. Docs: [YAP_LINK.md](../network/YAP_LINK.md) · [YAP_LINK_NATIVE.md](../network/YAP_LINK_NATIVE.md). Stock Velocity remains optional for migration — [VELOCITY.md](../network/VELOCITY.md).

---

## 9. Networking & crossplay

| Path | Notes |
|------|-------|
| **Java Edition** | Framed Netty; Via-class remapping in chassis (`com.yapcore.protocol.via*`). Target protocol ~**776** (26.2). JE floor **1.20.2+**. |
| **Bedrock** | UDP path; Geyser-class hub in `com.yapcore.crossplay*` — **no Geyser jar**. |
| **Shared listen** | Optional same port for JE+BE (`shared-listen-port=true`). |
| **YaP Link** | Optional JE front + optional Bedrock UDP forwarder. |
| **Packs HTTP** | Default pack `resourcepacks/yapcore-default.zip` on `:8081`. |
| **Publicity** | Domain / SRV / nginx + Cloudflare — [NETWORKING.md](../network/NETWORKING.md). |

Same-machine clients must use `127.0.0.1` (hairpin NAT) — [NGINX_AND_LOCALHOST.md](../network/NGINX_AND_LOCALHOST.md).

Phase 4 dual-stack **join DoD is green**; play-depth smoke green; Wave 2 matrix in [CROSSPLAY.md](../network/CROSSPLAY.md).

---

## 10. Ops surface

| Surface | Detail |
|---------|--------|
| Config | `config/server.properties` + hub under `config/` |
| Control GUI | Desktop Swing panel |
| Web dashboard | Token-auth browser UI `:8080` — [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) |
| Crash dumps | `logs/crashes/crash-<ts>-<kind>.log` |
| Release | `gradle assembleRelease` → `build/dist/yapcore-release/{linux,windows}/` |
| Windows | Parity launchers — [WINDOWS.md](../start/WINDOWS.md) |

Dashboard Phase 8 ops tabs are **shipped** (Factions / Guilds / Games interactive; Disasters + MMO reload) — see [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md).

---

## 11. Evaluation methodology

Unit tests (JUnit) cover plugin and API behavior. Operators validate with a local boot:
`./scripts/seed-defaults.sh` then `./scripts/start.sh --fg`.

**Metrics:** region MSPT p99, bridge queue depth, join success by protocol version, HEAVY pool saturation, MariaDB pool health.

---

## 12. Threats to validity

- Folia (and YaP-Folia) reject stock single-thread Paper plugins — operator education required.
- Protocol version sprawl requires continuous registry/packet maintenance.
- NUMA/ZGC gains are hardware-dependent.
- Complex JE containers on Bedrock (anvil/smithing/etc.) remain **Out**; Floodgate-only forms are **Limited** — see [CROSSPLAY.md](../network/CROSSPLAY.md).
- YaP-Folia patch rebase risk when refreshing `UPSTREAM.lock`.
- Phase 3 spatial edge cases apply only on the **legacy Paper** path.
- Soft-depend plugin names are **case-sensitive** (`YaPPlayerData`); integrations must match `plugin.yml` `name:` exactly.

---

## 13. Product status (September 2026)

| Area | Status |
|------|--------|
| YaP-Folia product path | **Default** (`folia-jar-source=build`) |
| YapEngine slim chassis | **Always on** |
| YaP Link phases 0–6 | **Shipped** (`0.6.0-phase6`) |
| Phase 3 Paper spatial | **Complete as code** — **retired as product default** |
| Phase 4 dual-stack join DoD | **Green** |
| CORE+NETWORK plugins | **Shipped** |
| GAMEPLAY + MMO M0–M7 | **Shipped** (opt-in install) |
| Ability VFX V1–V4 | **Shipped** — kits + heroes + soak gates ([MMO_ABILITY_VFX.md](../mmo/MMO_ABILITY_VFX.md)) |
| PlayerData shops + AH | **On by default** (jobs remain off) |
| Fair population MSPT gate | **Citeable** — fullcite 100 bots; peak −12.4% (`shipFc2`); re-verify −5.53% (`20260904T040935Z`); **ship knobs** disclosed in JSON |
| Dashboard Phase 8 full tabs | **Done (ship)** — Factions/Guilds/Games/Disasters/Stacker interactive; Protect restore; Regions flags; MMO reload; YAML-only leftovers documented |
| Wave 2 Bedrock fidelity | **Done (ship)** — [CROSSPLAY.md](../network/CROSSPLAY.md) matrix; Floodgate forms Limited; anvil/smithing Out |
| Wave 3 MMO quest objectives | **Done (ship)** — PLAYTIME/ECONOMY/PLACE/ENCHANT/ANVIL/TALK; silent proxies removed |
| Wave 4 Discord / Map / PAPI | **Done (ship)** — event webhooks; map markers; curated local expansions |
| Wave 5 Access context/temp | **Done (ship)** — dashboard duration + world/server grants |
| PAPI eCloud | **Intentionally local-only** (expansions folder) |
| Stretch (3D / CFI / full RS / clone mirrors) | **Separate plan only** — not stubbed (see §13.1) |
| Stock Paper jars on Folia | **Unsupported** |

Ops notes: [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md).

### 13.1 Stretch (deferred — no stub ship)

| Item | Why not in Waves 2–5 |
|------|----------------------|
| True BlueMap-style 3D mesh | Flat map + markers is product Map |
| FAWE CFI / NMS section injection | Folia region-threading conflict |
| Full RuneScape skill/quest matrix | Wave 3 uses existing YaP systems |
| DiscordSRV account-link / slash matrix | Event webhooks are product Discord |
| HelpChat eCloud full mirror | Curated expansions are product PAPI |
| LuckPerms web pixel-clone | Access context/temp is product ops |

Stretch starts only with its own plan and done bars.

---

## 14. Conclusion

YaPcore demonstrates a practical decomposition for Minecraft-class servers: **YaP-Folia** owns regionized game tick; **YapEngine** owns a slim edge/I/O chassis with an explicit plugin pool contract; **YaP Link** owns multi-backend routing; and a **first-party plugin + MariaDB data plane** replaces the usual DIY glue stack. Opt-in GAMEPLAY and MMO tiers extend the same Folia-safe patterns for vehicles and RS-style progression.

Future work emphasizes Stretch items (3D map, CFI, full RS) only under a separate plan, hot-region partition soak under load, and continued Folia upstream rebase hygiene.

---

## 15. References

1. Minecraft Wiki — *Java Edition protocol*.
2. OpenJDK — *Generational ZGC*.
3. Netty project.
4. PaperMC Folia — regionized threading for Bukkit servers.
5. YapLabs — YaP-Folia patches (`vendor/folia/patches/`), YapEngine chassis notes, YaP Link native suite.
6. YapLabs docs — [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md), [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md), [MMO_PHASES.md](../mmo/MMO_PHASES.md).

---

## Appendices

### Appendix A — Document map

| Audience | Start here |
|----------|------------|
| Non-tech readers | [Plain English whitepaper](YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md), [YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md](../whitepaper/YAPCORE_WHITEPAPER_PLAIN_ENGLISH.md) |
| Operators | [QUICK_START.md](../start/QUICK_START.md), [QUICK_START.md](../start/QUICK_START.md), [PLUGINS.md](../plugins/PLUGINS.md) |
| Plugin authors | [PLUGINS.md](../plugins/PLUGINS.md), [MODULES_AND_API.md](../plugins/MODULES_AND_API.md), [MODULES_AND_API.md](../plugins/MODULES_AND_API.md) |
| Engine contributors | [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md), [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md), [QUICK_START.md](../start/QUICK_START.md) |
| Network / crossplay | [YAP_LINK.md](../network/YAP_LINK.md), [CROSSPLAY.md](../network/CROSSPLAY.md), [CROSSPLAY.md](../network/CROSSPLAY.md) |
| Data | [YAPDB.md](../data/YAPDB.md), [PLAYERDATA.md](../data/PLAYERDATA.md), [PERMISSIONS.md](../ops/PERMISSIONS.md) |
| MMO | [MMO_PHASES.md](../mmo/MMO_PHASES.md), [MMO_SKILLS.md](../mmo/MMO_SKILLS.md), [MMO_COMBAT.md](../mmo/MMO_COMBAT.md) |
| Comparison | [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md) |

### Appendix B — Quick install

```bash
# Java 25+
./scripts/build-yap-folia.sh          # → lib/yap-folia-26.2.jar
./scripts/db/ensure-db.sh --server-id lobby
gradle installProductDefaults         # CORE+NETWORK → plugins/
# optional:
gradle installGameplayDefaults        # vehicles + stacker + MMO …
gradle assembleRelease
./scripts/start.sh --fg
# multi-backend:
./scripts/start-yap-link.sh
```

### Appendix C — Citation

```bibtex
@techreport{yapcore2026sixteen,
  title       = {YaPcore: YaP-Folia Game Authority, Slim Edge Chassis, Native Network Stack, and First-Party Plugin Suite},
  author      = {{YapLabs}},
  institution = {YapLabs},
  year        = {2026},
  month       = sep,
  number      = {YAP-WP-16T-001},
  note        = {Technical whitepaper, YaPcore 0.3}
}
```

### Appendix D — Changelog (whitepaper)

| Ver | Date | Notes |
|-----|------|-------|
| 0.2 | Aug 2026 | Three-layer architecture; chassis T1–16; Link phase6; Folia product path |
| **0.3** | **Sep 2026** | Comprehensive plugin catalog; MMO M0–M7; data plane (shops/AH defaults); Link/crossplay/ops status; evaluation smokes; honest roadmap gaps |

### Appendix E — License

YaPcore first-party code is **GNU GPLv3** (or later). See root [`LICENSE`](../../LICENSE) and
[LICENSING.md](../start/LICENSING.md). YaP-Folia derivatives inherit Folia/Paper GPLv3 obligations.
