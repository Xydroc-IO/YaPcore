# Where we stand

**Product:** YaPcore **1.0.0.0**  
**As of:** 2026-09-02  
**Master doc:** [YAPCORE_MASTER.md](YAPCORE_MASTER.md) — identity, advantages, gates, operator path (printable PDF available)

This is the executive snapshot. For full detail see [PROJECT_STATUS.md](PROJECT_STATUS.md) and [RELEASE_READINESS.md](RELEASE_READINESS.md).

---

## Verdict

| Question | Answer |
|----------|--------|
| **Can we ship v1 to players?** | **Yes** — core network product is shippable today |
| **Are automated release gates green?** | **Yes** — full battery + Phase 7 soak **PASS** (2026-09-02) |
| **Is it 100% done with zero caveats?** | **No** — retail Xbox login + G.33 skull texture need real clients |
| **Production readiness** | **~95%** — operator edge/nginx + two Bedrock retail checks remain |

**One line:** YaPcore is a **shippable YaP-Folia network product** with first-party Java + Bedrock crossplay, YaP Link, native plugin stack, and **600s automated play soak** green.

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
| Java + Bedrock crossplay (join, spawn, dig, chat, commands) | ✅ Ship | Scripted + 600s soak green |
| YaP Link multi-backend proxy | ✅ Ship | 9/9 network smoke |
| Web dashboard + Swing GUI | ✅ Ship | Abilities on MMO tab |
| MariaDB / playerdata / economy | ✅ Ship | Operator configures secrets |
| Release zip build + deploy | ✅ Ship | Build YaP-Folia locally first |
| Opt-in MMO / abilities / vehicles | ✅ Ship v1 | Phase 7 automated boot + unit gates |
| “Full play depth” vs Geyser marketing | ⚠️ Caveat | Retail Xbox + G.33 skull manual |
| Gold-standard anti-cheat | ✅ Ready | `./scripts/grim-ac.sh enable` when needed |
| 600s perf soak (marketing scale) | ⏸ Optional | `SOAK_SECS=600 ./scripts/soak-yap-folia.sh perf` |
| GitHub Release CI artifact | ⚠️ Mismatch | CI uses stock Folia fetch, not product jar |

---

## Proven by automation (2026-09-02)

| Gate | Result |
|------|--------|
| `gradle verifyConcurrency` | **PASS** |
| `./scripts/smoke-network-full.sh` | **PASS (9/9)** |
| JE protocol matrix (4 bands spawn) | **PASS (4/4)** |
| Bedrock smoke + play smoke | **PASS** |
| Folia compat soak (300s) | **PASS** |
| **Phase 7 play soak (600s)** | **PASS (9/9)** — `build/phase7-soak-latest.json` |
| Completion backlog Tiers 1–4 | **Done** |
| Roadmap phases 8–17 | **Done** |
| Bot join (100 / 200 Mineflayer) | **PASS** |
| MMO content | **105 quests / 20 bosses** |

**Performance (citeable):**

| Scenario | Result |
|----------|--------|
| **spawncollapse** (8k TNT / 1024 hoppers / 2500 mobs) | YaP-Folia **−22% to −26%** vs stock Folia |
| **fullcite** (100 bots + fixtures) | yapcore **−5.8%** MSPT vs stock Folia (ship knobs) |
| **highpop** (100 bots, lighter) | **Valid tie** — −4.2% (within 5% noise band) |

Re-run: `./scripts/smoke-network-full.sh` · `./scripts/smoke-phase7-soak.sh` → `build/production-test-battery-latest.json`

---

## What you still need to do

### Must do before production (operator)

| # | Task | Status |
|---|------|--------|
| 1 | Build with product YaP-Folia | ✅ done |
| 2 | Re-run release gate | ✅ 9/9 |
| 3 | Configure secrets | ✅ DB + forwarding (set Discord before relay) |
| 4 | Seed defaults + DB | ✅ lobby JDBC |
| 5 | Production server.properties | ✅ `apply-production-profile.sh` |
| 6 | Edge hardening (if public) | ⚠️ set domain → `sudo ./scripts/nginx-setup.sh` |
| 7 | Push commits | ✅ synced with origin |

### Manual retail checks (not blocking v1)

- Retail **Xbox login** on real console
- **G.33 skull** item-in-hand texture on Bedrock
- **Shift+F ability book** in-game (keyboard UI)

**Until closed:** *“join/spawn + automated 600s soak green; retail Xbox + cosmetic skull verify recommended.”*

### Optional (not blocking v1)

- 600s **perf** soak: `SOAK_SECS=600 ./scripts/soak-yap-folia.sh perf`
- Nightly CI smoke in GitHub Actions
- Fix Release workflow to use product YaP-Folia build

---

## Where we are better (summary)

| vs | YaPcore edge |
|----|--------------|
| **Paper + plugin stack** | YaP-Folia regions + shipped natives + Link + dual-stack — one product |
| **Stock Folia + DIY** | YaP-Folia patches (−5.8% fullcite MSPT, spawncollapse wins) + Link + built-in BE |
| **Purpur / Leaf** | Whole network product, not “replace the jar” only |

Full comparison: [YAPCORE_MASTER.md §3](YAPCORE_MASTER.md#3-where-we-are-better--and-why) · [COMPARE_ECOSYSTEM.md](COMPARE_ECOSYSTEM.md)

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

## Safe claim language

**Allowed today:** YaP-Folia product jar · shipped native stack · 9/9 network smoke · 600s play soak · fullcite −5.8% vs stock Folia · YaP Link phases 0–6.

**With caveats:** “Full Geyser clone” → use automated soak language instead.

**Forbidden:** “100% Geyser clone” · “every Paper plugin works” · “250 keepalive = MSPT win”.

---

## Related docs

| Doc | Use when |
|-----|----------|
| [**YAPCORE_MASTER.md**](YAPCORE_MASTER.md) | **Single master doc** — start here |
| [PROJECT_STATUS.md](PROJECT_STATUS.md) | Full gate + plugin rundown |
| [RELEASE_READINESS.md](RELEASE_READINESS.md) | Operator checklist by surface |
| [COMPARISON_BRIEF.md](COMPARISON_BRIEF.md) | Partner pitch (no hype) |
| [TESTING.md](../start/TESTING.md) | Every smoke command |
| [pdf/README.md](../pdf/README.md) | Printable PDFs |

---

*Update after major gates, retail soak closure, or a new release.*
