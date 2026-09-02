# YaPcore — project status (complete rundown)

**As of:** 2026-09-02 (evening)  
**Branch:** `main` (commits ahead of `origin/main`; push needs `gh auth login`)  
**Last production battery:** `build/production-test-battery-latest.json` — **all gates PASS**  
**Bot swarm:** **100 / 200 join verified**; **fullcite** yapcore **−5.8%** vs stock Folia (citeable) — see [BENCH_BOTS.md](../performance/BENCH_BOTS.md)

This is the honest operator view: what exists, what automated CI proves, what still needs human soak, and what is explicitly out of scope. For architecture and pitch, see [FULL_RUNDOWN.md](FULL_RUNDOWN.md). For phased backlog ticks, see [COMPLETION_BACKLOG.md](COMPLETION_BACKLOG.md).

---

## One-line status

**YaPcore is a shippable YaP-Folia network product** with first-party Java + Bedrock crossplay, YaP Link proxy, and a full native plugin stack. **Automated release gates are green.** Remaining work is mostly **live-client soak** (10-minute sessions, chunk borders, retail Xbox), a few **Partial parity rows**, and **optional deeper stress** — not missing core code paths.

---

## What we have (product surface)

### Three layers

| Layer | What it is |
|-------|------------|
| **YaP Link** | Native Velocity-class proxy (`0.6.0-phase6`) — multi-backend, forwarding, link plugins |
| **YapEngine chassis** | YaPcore parent JVM — Netty edge, dual-stack gateway, Via/Geyser-class protocol, ops GUI + web dashboard |
| **YaP-Folia** | Patched Folia 26.2 child JVM — game tick, world, commands, plugins (`lib/yap-folia-26.2.jar`) |

Default config: `game-authority=folia`, `folia-jar-source=build`, public JE `:25566`, Bedrock UDP same port, optional Link on `:25565`.

### Core network plugins (product defaults)

Installed via `gradle installProductDefaults` / `assembleRelease`:

| Area | Jars (representative) |
|------|------------------------|
| Data | `yap-db`, `yap-playerdata` (claims, economy, warps, NPC traders) |
| Social | `yap-chat`, `yap-perms`, `yap-moderation`, `yap-tab`, `yap-discord` |
| World / protect | `yap-world`, `yap-protect`, `yap-regions`, `yap-pregen`, `yap-packs` |
| Ops | `yap-essentials`, `yap-admin`, `yap-lagguard`, `yap-guard`, `yap-map` |
| Crossplay | `yap-floodgate` (identity); protocol in core (`com.yapcore.protocol.*`, `com.yapcore.crossplay.*`) |
| Compat | `plugin-compat`, PlaceholderAPI fork |

### Gameplay stack (opt-in)

`gradle installGameplayDefaults` or `-PyapGameplay=true`:

| Area | Jars |
|------|------|
| MMO | skills, combat, mechanics, content (100 quests + 20 bosses), abilities (233 + dual hotbar + ability book), bedrock-ui |
| Extras | vehicles, stacker, games, factions, guilds, gameplay-knobs |

### Ops surfaces

- **Swing control panel** + **web dashboard** (`http://127.0.0.1:8080/`) — status, console, plugins, ranks, regions, map, guard, link, etc.
- **Resource pack HTTP** (`:8081`) — default pack in `resourcepacks/yapcore-default.zip`
- **MariaDB** via `./scripts/db/ensure-db.sh`
- **Release zip:** `gradle assembleRelease` → `build/dist/yapcore-release/{linux,windows}/`

### Protocol / crossplay (no Via\* / Geyser jars)

- **JE floor:** 1.20.2+ onto YaP-Folia 26.2 (~776); optional forward for newer clients when dumps exist
- **Bedrock pin:** 1.21.50 — RakNet, login, spawn, dig/place, chat, commands, inventory bridge
- **Floodgate-class identity:** offline JWT + Xbox chain validation in core + `yap-floodgate`

Full feature matrix: [VIA_GEYSER_PARITY.md](../protocol/VIA_GEYSER_PARITY.md)  
Known JE limitations: [VIA_BACKWARDS_LIMITATIONS.md](../protocol/VIA_BACKWARDS_LIMITATIONS.md)

---

## Fully done and verified (automated gates green)

These are **proven by CI/smoke scripts**, not just “code exists.”

### Production test battery (2026-09-01, commit `270f679`)

| Gate | Result | Artifact |
|------|--------|----------|
| `gradle verifyConcurrency` | **PASS** | `build/verify-concurrency.log` |
| `./scripts/smoke-network-full.sh` | **PASS (9/9)** | `build/smoke-network-full-latest.json` |
| JE protocol matrix (4 bands spawn) | **PASS (4/4)** | `build/protocol-matrix-latest.json` |
| Bedrock smoke (`geyserParitySmoke`) | **PASS** | `build/bedrock-smoke-latest.json` |
| Bedrock play smoke (dig/chat/command) | **PASS** | `build/bedrock-play-smoke-latest.json` |
| Folia compat soak (300s) | **PASS** (~292s held) | `bench/results/20260901T202816Z-yap-folia-soak-compat.json` |

### smoke-network-full steps (all pass)

1. assembleRelease  
2. plugin layout check  
3. Folia smoke  
4. YaP Link Folia smoke  
5. YaP Link plugins smoke  
6. YaP Link Bedrock UDP smoke  
7. YaP Link two-backend smoke  
8. Bedrock play smoke  
9. aggregate PASS  

### Completion backlog tiers 1–4 (code + gates)

| Tier | Theme | Status |
|------|-------|--------|
| **1** | Core fixes (YaPTab sidebar, claim flags, admin menus, combat PvE) | **Done** |
| **2** | Ops (dashboard polish, tune docs, web map, Discord relay) | **Done** |
| **3** | Gameplay depth (RS quest/boss roster, abilities, Bedrock UI, TAB cross-server) | **Done** (v1) |
| **4** | Protocol / edge (4A→4F phased plan) | **Done** with live-soak caveats |

Tier 4 phase detail: [TIER4_PHASES.md](TIER4_PHASES.md)

### Roadmap phases with shipped jars

| Phase | Deliverable | Status |
|-------|-------------|--------|
| 9 | YaPTab (tab list, sidebar, cross-server sync v1.1) | **Shipped** |
| 10 | YaPDiscord (webhooks, MC↔Discord optional) | **Shipped** |
| 11 | YaPRegions (admin regions + claim flags) | **Shipped** (Tier 1 closed flags) |
| 12 | YaPMap (flat tiles + Leaflet) | **Shipped** (v1 flat renderer) |
| 13 | YaPGuard (lightweight native AC) | **Shipped** (heuristic v1; Grim optional) |
| 14 | NPCs / quests | **Partial → content shipped** (100 quests in jar; deeper Citizens-class UX optional) |
| 15 | Bedrock play depth | **Done** (smoke green) |
| 16 | Plugin compat matrix | **Done** |
| 17 | Release polish + network smoke gate | **Done** |

### Protocol rows marked Done in parity doc (high level)

- **ViaVersion-class:** handshake, config, compression, proxy front, dump plumbing  
- **ViaBackwards-class:** login→play, mid ID remaps, slot/window click bodies, chunk/entity kick-safe remaps, catalog, optional-pack auto-ack  
- **Geyser-class:** RakNet, start_game, Paper column stream (default), movement, dig/place, chat/commands, inventory inject, containers/forms/skins, Xbox-shaped CI  
- **Floodgate-class:** UUID mapping, JWT, Velocity cipher, `yap-floodgate`  

---

## Shipped and working — but not “perfect” yet

These have **green automated smokes** or **Done code**, but parity doc or live checklist still marks gaps.

### Live soak checklist (§E) — operator-owned

Automated ticks (2026-09-01):

- [x] JE matrix 4/4 spawn under compression + optional pack  
- [x] Bedrock RakNet + login + spawn  
- [x] Bedrock chat + break + `/help` (scripted)  

**Still unchecked (need real clients, 10+ minutes):**

| Client | Remaining manual checks |
|--------|-------------------------|
| **JE 1.20.4 / 1.21.1** | Chunk-border walk 200+ blocks; chest/furnace/crafting + shift-click; 10-min stability; mob PvP visibility |
| **Bedrock 1.21.50** | Chunk-border terrain vs Paper; full inventory UI + `/give`; forms return; retail Xbox login |

Source: [VIA_GEYSER_PARITY.md §E](../protocol/VIA_GEYSER_PARITY.md)

### Partial parity rows (honest)

| ID | Area | Notes |
|----|------|-------|
| **V1.3 / V1.9** | JE forward heuristics / content forward | Works with dumps; heuristics partial without dump |
| **VB.15 / VB.18 / VB.21 / VB.25** | Spawn bodies, chat signing, block states, smithing UI | Matrix spawn OK; edge cases documented |
| **G.33** | Player-head / skull textures on Bedrock | Block-actor sync wired; **item-in-hand texture v2** still partial |
| **G.32** | Bedrock emotes | Low priority gap |
| **VR.\*** | ViaRewind 1.8 join | Best-effort only — **not product DoD** |

### Known shared limitations (not bugs — translation reality)

- Smithing templates on older mid clients  
- Sound/particle approximations  
- Unknown blocks → stone; unknown entities → pig  
- Component/NBT strip on cross-era items  
- No Via\* / Geyser **plugin API** for third-party plugins  

Full list: [VIA_BACKWARDS_LIMITATIONS.md](../protocol/VIA_BACKWARDS_LIMITATIONS.md)

### Dashboard Phase 8 (ROADMAP)

API routes exist for Protect, World, Chat, etc.; some tabs are **polish-level** vs the original Phase 8 spec (full lazy-load POST parity for every mutating action). Usable today; not every Phase 8 acceptance bullet is audited.

### Anti-cheat stance

- **YaPGuard** = native lightweight (fly, speed, reach, scaffold) — **not** Matrix/Vulcan parity  
- **Grim** = optional fetch + docs; disable YaPGuard when Grim present — [GRIM.md](../ops/GRIM.md)

### Stress / perf

| Test | Status |
|------|--------|
| Compat soak 300s | **PASS** |
| Perf soak 600s (`SOAK_SECS=600 ./scripts/soak-yap-folia.sh perf`) | **Not run** in last battery |
| Spawncollapse MSPT (8k TNT / 1024 hoppers / 2500 mobs) | **CITEABLE** — YaP-Folia −22% to −26% vs stock; `20260901T210712Z-speedtest` |
| High-pop bot join (100 / 200) | **PASS** — `players_ok: true` on stock Folia |
| High-pop **fullcite** yapcore vs Folia | **CITEABLE** — −5.8% MSPT (`20260902T005200Z-fullcite-knobs2`, ship knobs) |
| High-pop **highpop** yapcore vs Folia | **Valid tie** — −4.2% at 100 bots (`20260902T010200Z-highpop-knobs`; within 5% band) |

Bot bench doc: [BENCH_BOTS.md](../performance/BENCH_BOTS.md). MSPT tables: [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md).

---

## Explicitly not done / out of scope

| Item | Stance |
|------|--------|
| ViaRewind 1.8 **play depth** | Out of product DoD |
| Shipping ViaVersion / ViaBackwards / Geyser / Floodgate **jars** | Forbidden on product path |
| Floodgate Global Linking | Out |
| Bedrock vehicles / boats mount sync | Out of v1 — use JE for mounted travel |
| Bedrock custom `block_properties` without custom-block product | Out |
| Bit-identical packets vs Via\* / Geyser | Out — behavioral parity only |
| “Faster than Paper everywhere” marketing | Not claimed; bench fairly |
| Stock Folia / Paper Phase 3 as **default** product path | Legacy benches only |

---

## Git / release state

| Item | State |
|------|-------|
| Local commits | `main` **ahead** of `origin/main` (bench + release notes; abilities commit pending) |
| Push | Run `gh auth login && git push -u origin main` when ready |
| Recent gameplay | **Abilities** — dual hotbar, ability book GUI, Shift+F open, `/yapabilities reload` (hot) |
| Verify locally | `./scripts/smoke-network-full.sh` or full battery in [TESTING.md](../start/TESTING.md) |

---

## How to re-run verification

```bash
# Full release gate (~3 min)
./scripts/smoke-network-full.sh

# Production battery (server on :25566)
gradle verifyConcurrency
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-matrix.sh
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-bedrock-smoke.sh
./scripts/smoke-bedrock-play.sh
SOAK_SECS=300 ./scripts/soak-yap-folia.sh compat
```

Summary written to `build/production-test-battery-latest.json`.

---

## Recommended next steps (priority order)

1. **`gh auth login && git push -u origin main`** — publish local commits  
2. **Manual §E soak** — JE + Bedrock clients, 10 minutes each, tick checklist in parity doc  
3. **Optional perf soak** — `SOAK_SECS=600 ./scripts/soak-yap-folia.sh perf` before a major release  
4. **G.33 live pass** — verify placed skulls + item-in-hand on real Bedrock client  
5. **CI depth** — optional nightly `smoke-network-full.sh` (GitHub CI today = build + unit tests only)  
6. **When Mojang ships new protocol** — follow [PROTOCOL_DUMPS.md](../protocol/PROTOCOL_DUMPS.md), re-run matrix  

---

## Related docs

| Doc | Use when |
|-----|----------|
| [WHERE_WE_STAND.md](WHERE_WE_STAND.md) | **Executive snapshot** — verdict, scorecard, what's left |
| [RELEASE_READINESS.md](RELEASE_READINESS.md) | **What’s left to do + production readiness score** |
| [FULL_RUNDOWN.md](FULL_RUNDOWN.md) | Architecture, how to run, comparison table |
| [COMPLETION_BACKLOG.md](COMPLETION_BACKLOG.md) | Tier 1–4 checklist ticks |
| [TIER4_PHASES.md](TIER4_PHASES.md) | Protocol phase gates 4A→4F |
| [VIA_GEYSER_PARITY.md](../protocol/VIA_GEYSER_PARITY.md) | Every parity row + §E checklist |
| [ROADMAP_COMPLETION_PHASES.md](ROADMAP_COMPLETION_PHASES.md) | Phases 8–17 agent roadmap |
| [TESTING.md](../start/TESTING.md) | All smoke commands |
| [BENCH_BOTS.md](../performance/BENCH_BOTS.md) | Mineflayer swarm + join verification |
| [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md) | MSPT scoreboard |
| [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) | Dashboard tabs and API |
| [YAP_LINK.md](../network/YAP_LINK.md) | Multi-backend proxy |

---

## Claim language (safe to say today)

**Allowed:**

- “First-party ViaBackwards-class for **1.20.2+**; no Via\* jars on the product path.”  
- “First-party Geyser-class Bedrock join on shared YaP-Folia world.”  
- “JE matrix 4/4 spawn; Bedrock smoke + play-depth smoke green; network full 9/9.”  
- “Mineflayer population bench: 100/200 bots join verified; **fullcite** yapcore −5.8% vs stock Folia (citeable, ship knobs).”  
- “highpop at 100 bots: valid join, tie vs Folia (−4.2%, within noise band).”  
- “Native plugin stack replaces LuckPerms/EssentialsX/TAB/DiscordSRV-class setups for typical SMP.”  

**Say with caveats until §E live soak closes:**

- “Full play depth parity” — prefer: *join/spawn + scripted play smoke green; live 10-min soak recommended*  
- “100% Geyser clone” — forbidden  

**Forbidden:**

- Full ViaRewind / 1.8 PvP parity  
- Identical to ViaBackwards in every smithing/sound edge case  

---

*This document is regenerated from backlog + battery artifacts; update after major gates or releases.*
