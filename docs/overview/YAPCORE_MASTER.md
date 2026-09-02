# YaPcore — master document

**Product:** YaPcore **1.0.0.0** · **YaP-Folia 26.2**  
**As of:** 2026-09-02  
**Audience:** operators, partners, contributors — one place for identity, status, advantages, and next steps.

This is the **single entry point**. Deep dives live in linked docs and printable PDFs under [`docs/pdf/`](../pdf/README.md).

---

## Table of contents

1. [What we are](#1-what-we-are)
2. [Where we stand](#2-where-we-stand)
3. [Where we are better — and why](#3-where-we-are-better--and-why)
4. [Architecture in one picture](#4-architecture-in-one-picture)
5. [What ships by default](#5-what-ships-by-default)
6. [Proof (automated gates)](#6-proof-automated-gates)
7. [What is still manual or partial](#7-what-is-still-manual-or-partial)
8. [Operator path to production](#8-operator-path-to-production)
9. [Safe language for marketing](#9-safe-language-for-marketing)
10. [Document map](#10-document-map)

---

## 1. What we are

### One sentence

**YaPcore** is next-gen Minecraft server software: **YaP-Folia** (our patched Folia 26.2 fork) runs the game, **YapEngine** runs the slim edge/I/O chassis, **YaP Link** fronts multi-backend networks, and **built-in dual-stack** joins Java + Bedrock to the same world — with a **shipped first-party plugin stack** instead of Paper + LuckPerms + Essentials + Velocity + Geyser.

### What we are not

| Claim | Truth |
|-------|-------|
| “Paper but faster everywhere” | **No** — product default is **YaP-Folia**, not Paper |
| “Stock Folia with a sticker” | **No** — `lib/yap-folia-26.2.jar` with YaP patches |
| “100% Geyser clone” | **No** — join/spawn + automated soak green; some fidelity rows partial |
| “Every Paper plugin still runs” | **No** — Folia-aware + YaP natives on product path |

### Three layers

| Layer | Role | Default |
|-------|------|---------|
| **YaP Link** | Velocity-class proxy — forwarding, `/server`, link plugins | Optional on `:25565` |
| **YapEngine chassis** | Netty edge, dual-stack gateway, protocol, GUI + web dashboard | Always on (parent JVM) |
| **YaP-Folia** | Regionized game tick — chunks, entities, redstone, Bukkit API | Child JVM `folia-kernel/` |

Config defaults: `game-authority=folia`, `folia-embed=true`, `folia-jar-source=build`, JE TCP + Bedrock UDP on **`:25566`** (shared port).

License: **GNU GPLv3** — [LICENSING.md](../start/LICENSING.md).

---

## 2. Where we stand

### Executive verdict

| Question | Answer |
|----------|--------|
| **Can we ship v1.0.0.0 to players?** | **Yes** — core network product is shippable |
| **Are automated release gates green?** | **Yes** — full battery + Phase 7 soak **PASS** (2026-09-02) |
| **Production readiness** | **~95%** — operator secrets/edge + 2 retail cosmetic checks remain |
| **Is it “100% done”?** | **No** — retail Xbox login + G.33 skull item-in-hand need real clients |

**One line:** Shippable **YaP-Folia network product** with first-party Java + Bedrock crossplay, YaP Link, native plugin stack, and **600s automated play soak** green. Remaining gaps are **operator edge config** and **two Bedrock retail visual checks** — not missing core code.

### Scorecard by surface

| Surface | Ready? | Confidence |
|---------|--------|------------|
| Core SMP (perms, chat, claims, regions, essentials, protect, world) | ✅ Ship | High |
| YaP-Folia game tick (regionized 26.2 + patches) | ✅ Ship | High |
| Java + Bedrock crossplay (join, spawn, dig, chat, commands) | ✅ Ship | High (scripted + soak) |
| YaP Link multi-backend proxy | ✅ Ship | High (9/9 network smoke) |
| Web dashboard + Swing GUI | ✅ Ship | Medium-high |
| MariaDB / playerdata / economy | ✅ Ship | High (operator configures secrets) |
| Release zip + deploy path | ✅ Ship | High (build YaP-Folia locally) |
| Opt-in MMO / abilities / vehicles | ✅ Ship v1 | Medium (automated boot + unit gates) |
| “Full play depth” vs Geyser marketing | ⚠️ Caveat | Retail Xbox + skull texture manual |
| Gold-standard anti-cheat | ✅ Ready | Grim optional; YaPGuard lightweight v1 |
| 600s perf soak (marketing scale) | ⏸ Optional | `SOAK_SECS=600 ./scripts/soak-yap-folia.sh perf` |

Artifact: `build/production-test-battery-latest.json` · Phase 7: `build/phase7-soak-latest.json`

---

## 3. Where we are better — and why

### vs Paper + DIY plugin stack

| Their path | YaPcore path | Why it matters |
|------------|--------------|----------------|
| Paper + LuckPerms + EssentialsX + TAB + DiscordSRV + … | **Shipped YaP natives** in one release | Fewer moving parts, one support story, shared MariaDB pool |
| Add Velocity + Geyser + Floodgate jars | **YaP Link + built-in dual-stack** | No forbidden third-party protocol jars on product path |
| Single main-thread tick | **YaP-Folia region threads** | Better headroom under spread load (citeable on population benches) |
| DIY MariaDB per plugin | **`yap-db` shared Hikari pool** | One JDBC config, multi-backend ready |

**Choose Paper when:** one exotic Paper-only plugin with no Folia/YaP equivalent.

### vs stock Folia + DIY Velocity

| Their path | YaPcore path | Why it matters |
|------------|--------------|----------------|
| Upstream `folia-*.jar` only | **YaP-Folia** with perf/teleport/sched patches | spawncollapse **−22% to −26%** MSPT vs stock; cross-region teleport transactions |
| Separate Velocity JVM + config | **YaP Link** native (`0.6.0-phase6`) | First-party forwarding, rate limits, link plugins |
| Assemble Geyser yourself | **Built-in Bedrock column + codec stack** | Same world as JE without Geyser jar |

**Choose stock Folia when:** you only want the upstream jar and enjoy assembling everything else.

### vs Purpur / Pufferfish / Leaf

| Their edge | YaPcore edge | Why it matters |
|------------|--------------|----------------|
| “Replace the Paper jar” | **Whole product** — game fork + chassis + Link + ops | Not a single-thread TPS patch story |
| No crossplay | **Dual-stack product** | One operator surface for JE + BE |
| No shipped network DB/auth | **Playerdata + offline `/login` + claims** | Network-grade data plane out of the box |

**Choose Leaf/Pufferfish when:** classic single-tick MSPT on Paper path is the only metric you care about.

### Performance (citeable, in-repo only)

| Scenario | YaP-Folia vs stock Folia | Notes |
|----------|--------------------------|-------|
| **spawncollapse** (8k TNT / 1024 hoppers / 2500 mobs) | **−22% to −26%** MSPT | Stress win with entity budget + async-save knobs |
| **fullcite** (100 active bots + fixtures) | **−5.8%** MSPT | Fair population cite with ship knobs |
| **highpop** (100 bots, lighter) | **−4.2%** (valid tie) | Within 5% noise band |
| **250 keepalive** | HOLD-ONLY | Not an MSPT win — do not cite |

Details: [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md) · [BENCH_BOTS.md](../performance/BENCH_BOTS.md)

### Product capabilities ranked (maturity)

| Rank | Capability | Status |
|:----:|------------|--------|
| 1 | **YaP-Folia** default game jar | Shipped |
| 2 | YapEngine slim chassis (edge/I/O) | Shipped |
| 3 | **YaP Link** proxy (phases 0–6) | Shipped |
| 4 | Network stack (DB, playerdata, packs, ranks) | Shipped |
| 5 | Ops (GUI + web dashboard `:8080`) | Shipped |
| 6 | Gameplay opt-in (vehicles, stacker, MMO) | Shipped |
| 7 | JE + Bedrock dual-stack | Join DoD + 600s soak green |
| 8 | Population MSPT (`fullcite`) | Citeable −5.8% vs stock Folia |

| Full matrix: [COMPARE_ECOSYSTEM.md](COMPARE_ECOSYSTEM.md)

---

## 4. Architecture in one picture

```text
Internet players
      │  TCP/UDP :25565 (nginx optional) or :25566 direct
      ▼
┌─────────────┐     ┌──────────────────────────────────────┐
│  YaP Link   │────▶│  YaPcore (YapEngine chassis)         │
│  (optional) │     │  · dual-stack gateway                │
└─────────────┘     │  · Via/Geyser-class protocol (native)│
                    │  · web dashboard :8080               │
                    │  · plugins/ (unified)                │
                    └──────────────┬───────────────────────┘
                                   │ embed
                                   ▼
                    ┌──────────────────────────────────────┐
                    │  YaP-Folia (child JVM, folia-kernel) │
                    │  · regionized tick + YaP patches     │
                    │  · world, mobs, redstone, commands   │
                    └──────────────────────────────────────┘
                                   │
                                   ▼
                    ┌──────────────────────────────────────┐
                    │  MariaDB (optional Docker)           │
                    │  yap-db shared pool · playerdata     │
                    └──────────────────────────────────────┘
```

Public edge hardening: [EDGE_HARDEN.md](../network/EDGE_HARDEN.md) · Cloudflare: [CLOUDFLARE_AND_NGINX.md](../network/CLOUDFLARE_AND_NGINX.md)

---

## 5. What ships by default

### CORE + NETWORK (`gradle installProductDefaults`)

Representative jars: `yap-perms`, `yap-essentials`, `yap-chat`, `yap-moderation`, `yap-tab`, `yap-discord`, `yap-world`, `yap-protect`, `yap-regions`, `yap-pregen`, `yap-packs`, `yap-db`, `yap-playerdata`, `yap-floodgate`, `yap-lagguard`, `yap-guard`, `yap-map`, `yap-folia-bridge`, …

**Replaces (typical DIY):** LuckPerms, EssentialsX, Chat plugins, TAB, DiscordSRV, WorldEdit-class tooling (partial), separate Floodgate jar, ad-hoc economy DB glue.

### GAMEPLAY opt-in (`gradle installGameplayDefaults`)

Vehicles, stacker, gameplay-knobs, full MMO stack (100+ quests, 20 bosses, abilities, skills, combat, Bedrock MMO UI).

### Release artifact

```bash
./scripts/build-yap-folia.sh
gradle publishReleasesFolder
# → build/dist/yapcore-release/linux/
```

See [RELEASES.md](../start/RELEASES.md) · [QUICK_START.md](../start/QUICK_START.md)

---

## 6. Proof (automated gates)

Last full battery: **2026-09-02** — all **PASS**

| Gate | Result |
|------|--------|
| `gradle verifyConcurrency` | PASS |
| `./scripts/smoke-network-full.sh` | **9/9** |
| JE protocol matrix | **4/4** spawn bands |
| Bedrock smoke + play smoke | PASS |
| Folia compat soak (300s) | PASS |
| **Phase 7 play soak (600s)** | **9/9** — JE walk 224 blocks / 15 chunks, Bedrock 15 chunks, abilities reload, MMO content, gameplay plugins |
| MMO content validate | 105 quests / 20 bosses / 151 recipes |
| Bot join bench | 100 / 200 Mineflayer verified |

Re-run:

```bash
./scripts/smoke-network-full.sh
./scripts/smoke-phase7-soak.sh          # ~21 min full / FAST_PHASE7=1 for 60s dev
```

Summary: `build/production-test-battery-latest.json`

---

## 7. What is still manual or partial

| Item | Status | Action |
|------|--------|--------|
| Retail **Xbox login** (Floodgate chain) | Manual | Real console; capture fixture with `-Dyap.floodgate.dumpChain=true` |
| **G.33 skull** item-in-hand texture | Manual | Codec test passes; visual verify on Bedrock client |
| JE **shift-click** chest UI in smoke world | Partial | Automated dig + optional chest; place structures for full UI |
| **Shift+F ability book** | Manual | Keyboard UI — join in-game once |
| **nginx install** on public host | Operator | `./scripts/nginx-setup.sh` after setting domain |
| **Discord webhooks** | Operator | Dashboard or YAML before enabling relay |
| GitHub Release CI jar | Mismatch | CI uses stock Folia fetch — use local `build-yap-folia.sh` for tags |

Until retail Xbox + skull are ticked: say *“join/spawn + scripted smoke + 600s automated soak green; retail Xbox + cosmetic skull verify recommended.”*

---

## 8. Operator path to production

### Minimum (private LAN)

```bash
./scripts/build-yap-folia.sh
./scripts/seed-defaults.sh
./scripts/db/ensure-db.sh --server-id lobby
./scripts/start.sh --fg
```

### Public SMP

```bash
./scripts/apply-production-profile.sh          # or --with-link behind YaP Link
cp deploy/mariadb/.env.example deploy/mariadb/.env   # edit passwords
./scripts/db/configure-db.sh --server-id lobby
./scripts/setup-velocity-forwarding.sh         # if using Link
# set public-host / nginx-domain in config/server.properties
sudo ./scripts/nginx-setup.sh                # when domain ready
./scripts/smoke-network-full.sh                # before inviting players
./scripts/start-prod.sh --fg
```

Secrets: [SECRETS.md](../start/SECRETS.md) · Full checklist: [RELEASE_READINESS.md](RELEASE_READINESS.md)

---

## 9. Safe language for marketing

### Allowed today

- First-party **YaP-Folia 26.2** fork — not stock Folia as product jar
- **Shipped native plugin stack** replaces typical Paper + LP + Essentials + Velocity + Geyser assembly
- JE matrix **4/4** spawn; Bedrock play smoke green; network smoke **9/9**
- **600s automated play soak** PASS (chunk borders, stability, abilities reload)
- **fullcite** population MSPT **−5.8%** vs stock Folia (in-repo bench)
- **YaP Link** phases 0–6 shipped

### With caveats

- “Full Geyser/Via parity” → prefer *automated soak green; retail Xbox recommended*
- “Faster than Paper everywhere” → **do not claim**
- “Every Paper plugin works” → **do not claim**

### Forbidden

- “100% Geyser clone”
- “Stock Folia with branding only”
- “250-bot keepalive = MSPT win”

---

## 10. Document map

| Need | Read |
|------|------|
| **This doc (master)** | `YAPCORE_MASTER.md` → PDF: `docs/pdf/YAPCORE_MASTER.pdf` |
| Plain English overview | [PLAIN_ENGLISH.md](PLAIN_ENGLISH.md) |
| Production operator checklist | [RELEASE_READINESS.md](RELEASE_READINESS.md) |
| vs Paper / Folia / Leaf | [COMPARE_ECOSYSTEM.md](COMPARE_ECOSYSTEM.md) |
| Technical architecture | [YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md) |
| Setup | [QUICK_START.md](../start/QUICK_START.md) |
| Every smoke command | [TESTING.md](../start/TESTING.md) |
| Crossplay parity matrix | [VIA_GEYSER_PARITY.md](../protocol/VIA_GEYSER_PARITY.md) |
| MSPT methods | [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md) |
| All printable PDFs | [pdf/README.md](../pdf/README.md) |
| Historical docs | [archive/README.md](../archive/README.md) |

Regenerate PDFs:

```bash
./scripts/export-docs-pdf.sh
```

---

*Update this master doc when major gates close, a release is cut, or citeable bench rows change.*
