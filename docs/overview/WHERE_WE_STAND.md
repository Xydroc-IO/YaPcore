# Where we stand

**Product:** YaPcore **1.0.0.0**  
**As of:** 2026-09-02  
**Branch:** `main` (local commits ahead of `origin/main` — push when ready)

This is the executive snapshot: **can we ship, what's proven, what's left.**  
For full detail see [PROJECT_STATUS.md](PROJECT_STATUS.md) and [RELEASE_READINESS.md](RELEASE_READINESS.md).

---

## Verdict

| Question | Answer |
|----------|--------|
| **Can we ship v1 to players?** | **Yes** — core network product is shippable today |
| **Are automated release gates green?** | **Yes** — last full battery passed 2026-09-01 |
| **Is it 100% done with zero caveats?** | **No** — manual client soak + a few parity rows remain |
| **Production readiness** | **~90%** — remaining work is operator verification and honest parity caveats, not missing core code |

**One line:** YaPcore is a **shippable YaP-Folia network product** with first-party Java + Bedrock crossplay, YaP Link proxy, and a full native plugin stack. Automated gates are green; live-client soak is the main gap before claiming “full play depth.”

---

## What we are

| Layer | What it is |
|-------|------------|
| **YaP Link** | Native Velocity-class proxy (`0.6.0-phase6`) — multi-backend, forwarding |
| **YapEngine chassis** | Parent JVM — Netty edge, dual-stack gateway, built-in protocol, ops GUI + web dashboard |
| **YaP-Folia** | Patched Folia 26.2 child JVM — game tick, world, commands, plugins |

Default path: `game-authority=folia`, `folia-jar-source=build`, JE `:25566`, Bedrock UDP same port, optional Link on `:25565`.

**We replace (on the product path):** Paper glue + LuckPerms + EssentialsX + TAB + DiscordSRV + Velocity + Geyser + Floodgate jars — with first-party YaP plugins and built-in crossplay.

---

## Scorecard

| Surface | Ready? | Notes |
|---------|--------|-------|
| Core SMP (perms, chat, claims, regions, essentials) | ✅ Ship | High confidence |
| YaP-Folia game tick (regionized 26.2) | ✅ Ship | High confidence |
| Java + Bedrock crossplay (join, spawn, dig, chat, commands) | ✅ Ship | Scripted smokes green |
| YaP Link multi-backend proxy | ✅ Ship | 9/9 network smoke |
| Web dashboard + Swing GUI | ✅ Ship | Abilities exposed on MMO tab (latest) |
| MariaDB / playerdata / economy | ✅ Ship | Operator configures secrets |
| Release zip build + deploy | ✅ Ship | Build YaP-Folia locally first |
| Opt-in MMO / abilities / vehicles | ✅ Ship v1 | Manual soak recommended |
| “Full play depth” marketing claim | ⚠️ Caveat | §E live soak not closed |
| Bedrock skull item-in-hand (G.33) | ⚠️ Partial | Cosmetic; not a blocker |
| Gold-standard anti-cheat | ✅ Ready | `./scripts/grim-ac.sh enable` when needed |
| 600s perf soak | ⏸ Not run | Optional before big launch |
| GitHub Release CI artifact | ⚠️ Mismatch | CI uses stock Folia fetch, not product jar |

---

## Proven by automation (2026-09-01)

All gates in the production test battery **PASS**:

| Gate | Result |
|------|--------|
| `gradle verifyConcurrency` | **PASS** |
| `./scripts/smoke-network-full.sh` | **PASS (9/9)** |
| JE protocol matrix (4 bands spawn) | **PASS (4/4)** |
| Bedrock smoke + play smoke | **PASS** |
| Folia compat soak (300s) | **PASS** |
| Completion backlog Tiers 1–4 | **Done** |
| Roadmap phases 8–17 | **Done** |
| Bot join (100 / 200 Mineflayer) | **PASS** (`players_ok: true`) |

**Performance (citeable):**

| Scenario | Result |
|----------|--------|
| **spawncollapse** (8k TNT / 1024 hoppers / 2500 mobs) | YaP-Folia **−22% to −26%** vs stock Folia |
| **fullcite** (100 bots + fixtures) | yapcore **−5.8%** MSPT vs stock Folia (ship knobs) |
| **highpop** (100 bots, lighter) | **Valid tie** — −4.2% (within 5% noise band) |

Re-run locally: `./scripts/smoke-network-full.sh` → summary in `build/production-test-battery-latest.json`.

---

## What you still need to do

### Must do before production (operator)

| # | Task | How |
|---|------|-----|
| 1 | Build with product YaP-Folia | `./scripts/build-yap-folia.sh` then `gradle publishReleasesFolder` |
| 2 | Re-run release gate on your machine | `./scripts/smoke-network-full.sh` |
| 3 | Configure secrets | [SECRETS.md](../start/SECRETS.md) |
| 4 | Seed defaults + DB | `./scripts/seed-defaults.sh` · `./configure-db.sh --server-id lobby` |
| 5 | Production server.properties | `./scripts/apply-production-profile.sh` (or `--with-link`) — [QUICK_START.md](../start/QUICK_START.md) |
| 6 | Edge hardening (if public) | [EDGE_HARDEN.md](../network/EDGE_HARDEN.md) |
| 7 | Push local commits | `gh auth login && git push -u origin main` |

### Should do before claiming “full play depth”

Real clients, **10+ minutes each** — checklist in [VIA_GEYSER_PARITY.md §E](../protocol/VIA_GEYSER_PARITY.md):

- [ ] JE chunk-border walk 200+ blocks; chest/furnace/crafting + shift-click; 10-min stability
- [ ] Bedrock chunk terrain, full inventory + `/give`, forms, **retail Xbox login**
- [ ] G.33 skull textures in-hand on Bedrock

**Until closed:** say *“join/spawn + scripted smoke green; live soak recommended”* — not *“100% Geyser clone.”*

### Optional (not blocking v1)

- 600s perf soak: `SOAK_SECS=600 ./scripts/soak-yap-folia.sh perf`
- Nightly CI smoke in GitHub Actions
- Fix Release workflow to use product YaP-Folia build
- Enable Grim AC: `./scripts/grim-ac.sh enable`

---

## Explicitly out of scope

| Item | Stance |
|------|--------|
| ViaRewind 1.8 play depth | Out of product DoD |
| Shipping ViaVersion / Geyser / Floodgate jars | Forbidden — built-in stack replaces them |
| Bit-identical packets vs Via/Geyser | Behavioral parity only |
| Bedrock vehicles / boat mount sync | Out of v1 |
| “Faster than Paper everywhere” | Not claimed |

---

## Decision guide

| Your goal | Minimum left to do |
|-----------|-------------------|
| Private LAN / dev server | Operator items **1–4** |
| Public SMP (core stack) | **1–7** + strongly recommend live soak |
| Public SMP + gameplay add-ons | Above + abilities/MMO manual spot-check |
| Marketing “battle-tested at scale” | Above + 600s soak + bench docs |

---

## Safe claim language

**Allowed today:**

- First-party ViaBackwards-class for **1.20.2+**; no Via\* jars on product path
- First-party Geyser-class Bedrock join on shared YaP-Folia world
- JE matrix 4/4 spawn; Bedrock smoke + play-depth green; network full 9/9
- Mineflayer bench: 100/200 bots join verified; **fullcite** −5.8% vs stock Folia
- Native plugin stack replaces LuckPerms/EssentialsX/TAB/DiscordSRV-class setups

**With caveats until live soak closes:**

- “Full play depth parity” → prefer *join/spawn + scripted smoke green; live soak recommended*

**Forbidden:**

- Full ViaRewind / 1.8 PvP parity
- “100% Geyser clone”
- Identical to ViaBackwards in every smithing/sound edge case

---

## Related docs

| Doc | Use when |
|-----|----------|
| [PROJECT_STATUS.md](PROJECT_STATUS.md) | Full rundown — every gate, plugin, parity row |
| [RELEASE_READINESS.md](RELEASE_READINESS.md) | Operator checklist + readiness by surface |
| [COMPLETION_BACKLOG.md](COMPLETION_BACKLOG.md) | Tier 1–4 code ticks (all Done) |
| [RELEASE_NOTES.md](../start/RELEASE_NOTES.md) | v1.0.0.0 changelog |
| [TESTING.md](../start/TESTING.md) | Every smoke command |
| [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md) | MSPT scoreboard |
| [VIA_GEYSER_PARITY.md](../protocol/VIA_GEYSER_PARITY.md) | §E live soak checklist |

---

*Update after major gates, §E soak closure, or a new release.*
