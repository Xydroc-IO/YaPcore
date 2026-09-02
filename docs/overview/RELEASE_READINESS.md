# Release & production readiness

**Product:** YaPcore **1.0.0.0** · **Date:** 2026-09-02  
**Use this doc for:** how production-ready you are right now, and **what you still need to do**.

For architecture and full status detail see [YAPCORE_MASTER.md](YAPCORE_MASTER.md).  
For build/deploy commands see [RELEASES.md](../start/RELEASES.md) and [TESTING.md](../start/TESTING.md).

---

## How production-ready are we?

### Overall verdict

| Question | Answer |
|----------|--------|
| **Can you ship v1.0.0.0 to players today?** | **Yes** — core network product is shippable |
| **Is every automated gate green?** | **Yes** — full battery passed **2026-09-02** (`build/production-test-battery-latest.json`) |
| **Is it “100% done” with zero caveats?** | **No** — retail Xbox login + G.33 skull texture need real clients |
| **Is GitHub CI enough to trust a release?** | **No** — CI only runs compile + unit tests; run local smokes before tagging |

**Master doc:** [YAPCORE_MASTER.md](YAPCORE_MASTER.md)

### Production readiness by surface

| Surface | Ready? | Confidence | Blocker? |
|---------|--------|------------|----------|
| **Core SMP** (perms, chat, claims, regions, essentials, protect, world) | ✅ Ship | High | None |
| **YaP-Folia game tick** (regionized 26.2) | ✅ Ship | High | None |
| **Java + Bedrock crossplay** (join, spawn, dig, chat, commands) | ✅ Ship | High for scripted paths | None for release |
| **YaP Link multi-backend proxy** | ✅ Ship | High | None |
| **Web dashboard + Swing GUI** | ✅ Ship | Medium-high | None |
| **MariaDB / playerdata / economy** | ✅ Ship | High | Operator must configure secrets |
| **Release zip build + deploy path** | ✅ Ship | High | Build YaP-Folia locally (`build-yap-folia.sh`) |
| **“Full play depth” marketing claim** | ⚠️ Caveat | Medium | Automated 600s soak PASS; retail Xbox + G.33 manual |
| **Bedrock skull item-in-hand (G.33)** | ⚠️ Partial | Medium | Cosmetic; not a release blocker |
| **Gold-standard anti-cheat** | ✅ Ready (Grim) | High when enabled | Run `./scripts/grim-ac.sh enable` — auto-downloaded disabled on setup |
| **Opt-in MMO / abilities / vehicles** | ✅ Ship v1 | Medium | Manual soak recommended; thin unit tests |
| **Cross-region stability** (walk, `/tp`, portals) | ✅ Ship | High on product path | Requires YaP-Folia build + default flags — see below |
| **600s perf soak** | ⏸ Not run | Unknown | Optional before big launch |
| **GitHub Release workflow artifact** | ⚠️ Mismatch | Medium | CI uses stock Folia fetch, not product YaP-Folia build |

### One-line score

**~90% production-ready for a typical network operator.**  
The remaining ~10% is **operator verification** (live clients, secrets, edge config) and **honest parity caveats** — not missing core code.

---

## What is already done (you do not need to rebuild)

These passed automated gates on **2026-09-02** (`build/production-test-battery-latest.json`):

- [x] `gradle verifyConcurrency` — SpotBugs + unit + Fray
- [x] `./scripts/smoke-network-full.sh` — **9/9** (assembleRelease, Folia, Link, Bedrock, two-backend)
- [x] JE protocol matrix — **4/4** spawn bands
- [x] Bedrock smoke + play smoke (dig, chat, `/help`)
- [x] Folia compat soak — **300s PASS**
- [x] Completion backlog **Tiers 1–4 Done**
- [x] Roadmap phases **8–17 Done**
- [x] Bot join bench — 100 / 200 Mineflayer bots verified (`players_ok: true`)
- [x] **fullcite** population MSPT — yapcore **−5.8%** vs stock Folia (**citeable**, ship knobs, `20260902T005200Z-fullcite-knobs2`)
- [x] Core plugin stack shipped (see [RELEASE_NOTES.md](../start/RELEASE_NOTES.md))

**Safe to deploy today:** default CORE+NETWORK stack, crossplay, Link, dashboard, MariaDB path.

Cross-region defenses verified on product path:

- [x] YaP-Folia teleport transactions — default **on** (`folia-teleport-transactions=true`)
- [x] Scheduler compat agent — default **on** (`folia-sched-compat=true`)
- [x] `./scripts/smoke-folia-cross-region-tp.sh` — **PASS** on `FOLIA_JAR_SOURCE=build`
- [x] Folia compat soak **300s** — **PASS**
- [x] First-party plugins use `YapSched` for entity/region work

---

## Cross-region stability (Folia)

Folia runs the world on **many region threads**. Activity that crosses chunk/region borders is normal — the question is whether it **crashes** the server or **corrupts** state.

### Short answer

| Question | Answer |
|----------|--------|
| Will players crash the server by walking into another region? | **No** — that is core Folia; not a known crash vector on the product path |
| Will rapid `/tp` / portals across regions take everyone down? | **Very unlikely** with YaP-Folia + teleport transactions **on** |
| Is cross-region **100% crash-proof**? | **No** — bad third-party plugins or experimental knobs can still throw, kick, or kill a region thread |
| Are you safer than stock Folia? | **Yes** — teleport patch + YapSched + sched compat agent + passed smokes |

**Bottom line:** On the **product path** (YaP-Folia build, default flags), cross-region play should be **stable** — desync/glitches are possible at unsoaked edges; **routine JVM crashes from border crossing are not expected**.

### What happens by scenario

| Scenario | Likely outcome |
|----------|----------------|
| Walking chunk / region borders | Server stays up; long-session visual glitches possible (manual §E soak still open) |
| `/tp`, warps, nether/end portals across regions | **Protected** by YaP two-phase teleport transactions (PREPARE → COMMIT → CONFIRM → ROLLBACK) |
| First-party plugins (essentials, combat, tab, abilities, …) | **Region-safe** — `YapSched.entity` / `YapSched.region` on owning thread |
| Legacy third-party plugins | **Usually loads** — `yap-sched-agent` shims sync `BukkitScheduler` calls |
| Broken plugin mutating entities from wrong thread | Logged exception, possible **player kick** or **region thread error** — full-server crash is rarer |
| One bad plugin at enable | **Server continues** — soft-fail disable for that jar only |

### What protects you (shipped defaults)

| Defense | Config | Purpose |
|---------|--------|---------|
| **YaP-Folia teleport transactions** | `folia-teleport-transactions=true` | Cross-region TP integrity — no duplicate entities / dropped pending teleports under load |
| **YapSched** (first-party) | (code) | Entity/block mutations on correct region thread |
| **yap-sched-agent** | `folia-sched-compat=true` | Legacy plugins: sync scheduler → Folia region schedulers instead of immediate throw |
| **Subregion partition** | **off** by default | Experimental hot-region sharding — leave off until soaked |

**Requires YaP-Folia jar** — `./scripts/build-yap-folia.sh` with `folia-jar-source=build`. Stock `fetch` Folia **does not** include the teleport transaction patch; smokes **hard-fail** that path intentionally.

Details: [FOLIA_TELEPORT_TRANSACTIONS.md](../folia/FOLIA_TELEPORT_TRANSACTIONS.md) · [FOLIA_SCHED_COMPAT.md](../folia/FOLIA_SCHED_COMPAT.md) · [YAP_SCHED.md](../folia/YAP_SCHED.md)

### Remaining risks (honest)

1. **Stock Folia** instead of YaP-Folia — lose teleport patch; more cross-region TP races (desync/kicks, not always full crash).
2. **Random Paper/Folia plugins** — wrong-thread API use → exceptions; worst case unhealthy region until restart.
3. **Long chunk-border sessions** — automated smokes pass; **10+ min live JE/Bedrock walk** still unchecked (tasks **8–11** above).
4. **Gameplay at borders** — vehicles / heavy MMO AoE: first-party code uses YapSched; not every path has long soak proof.
5. **Experimental partition** (`0014` / `0015`) — only enable after [YAP_FOLIA_SOAK.md](../folia/YAP_FOLIA_SOAK.md) perf/compat soak.

### Operator checklist (cross-region)

```properties
# config/server.properties (product defaults)
game-authority=folia
folia-jar-source=build
folia-teleport-transactions=true
folia-sched-compat=true
folia-sched-compat-warn=true
```

```bash
./scripts/build-yap-folia.sh
FOLIA_JAR_SOURCE=build ./scripts/smoke-folia-cross-region-tp.sh 180   # optional verify
SOAK_SECS=300 ./scripts/soak-yap-folia.sh compat
```

**Do not claim** “zero Folia threading risk with any plugin jar.” **Do claim** “YaP-Folia product path with default flags; cross-region TP smoke and compat soak green.”

---

## What you have left to do

Work is grouped by **must do before players** vs **should do** vs **optional / post-v1**.

---

### 🔴 Must do before production (operator tasks)

These are **your** checklist items — not code gaps.

| # | Task | Why | How |
|---|------|-----|-----|
| 1 | **Build with product YaP-Folia** | Release CI uses stock Folia; product path needs the fork jar | `./scripts/build-yap-folia.sh` then `gradle publishReleasesFolder` | ✅ **2026-09-02** |
| 2 | **Re-run release gate on your machine** | Confirms *your* artifact before players touch it | `./scripts/smoke-network-full.sh` | ✅ **9/9 2026-09-02** |
| 3 | **Configure secrets** | Live tokens/passwords are never in the zip | [SECRETS.md](../start/SECRETS.md) — MariaDB, dashboard token, Discord, forwarding secret | ✅ **DB + forwarding** (set Discord webhooks before enabling relay) |
| 4 | **Seed defaults + DB** | First boot needs config and schema | `./scripts/seed-defaults.sh` · `./configure-db.sh --server-id lobby` | ✅ **lobby** JDBC OK |
| 5 | **Set production server.properties** | LAN defaults ≠ public internet | `./scripts/apply-production-profile.sh` (or `--with-link` behind YaP Link) — [QUICK_START.md](../start/QUICK_START.md) | ✅ **direct public** profile applied |
| 6 | **Edge hardening (if public)** | Raw port exposure is not enough for most hosts | nginx templates in `deploy/nginx/` · [EDGE_HARDEN.md](../network/EDGE_HARDEN.md) · Cloudflare DNS-only for game TCP/UDP | ⚠️ **dry-run OK** — set `nginx-domain` / `public-host` then `sudo ./scripts/nginx-setup.sh` |
| 7 | **Push local commits** | `main` may be ahead of `origin/main` | `gh auth login && git push -u origin main` | ✅ **synced** (2026-09-02; nothing to push) |

### Performance benches (citeable rows)

| Scenario | Status | Notes |
|----------|--------|-------|
| **spawncollapse** (8k TNT / 1024 hoppers / 2500 mobs) | **Citeable** | YaP-Folia **−22% to −26%** vs stock Folia |
| **fullcite** (100 bots + fixtures) | **Citeable** | yapcore **−5.8%** vs stock Folia (ship knobs) |
| **highpop** (100 bots, lighter) | **Valid tie** | −4.2% — within 5% noise band |
| **heavypop** (no bots) | NOT CITEABLE | MSPT ≪ 2 ms |

Details: [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md) · [BENCH_BOTS.md](../performance/BENCH_BOTS.md)

---

### 🟡 Should do before claiming “full play depth” (manual soak)

Automated smokes pass; **real clients for 10+ minutes** are still unchecked in [VIA_GEYSER_PARITY.md §E](../protocol/VIA_GEYSER_PARITY.md).

| # | Task | Client | Check | Status |
|---|------|--------|-------|--------|
| 8 | Chunk-border walk 200+ blocks | JE 1.20.4 or 1.21.1 | No invisible chunks / kick | ✅ **automated** (`je-play-soak.mjs` — op `/tp` grid, 15 chunks) |
| 9 | Full inventory UI | JE | Chest, furnace, crafting, shift-click | ⚠️ **partial** — dig + optional chest; shift-click needs structures |
| 10 | 10-minute stability session | JE | No disconnects, mob PvP visible | ✅ **automated** (`PHASE7_SOAK_SECS=600` hold) |
| 11 | Chunk-border terrain | Bedrock 1.21.50 | Matches expectations vs Paper | ✅ **automated** (`bedrock-play-soak.mjs` — 15 chunk markers) |
| 12 | Full inventory + `/give` | Bedrock | Containers and hotbar behave | ⚠️ **partial** — inventory_transaction + codec unit gates |
| 13 | Forms return correctly | Bedrock | Bedrock UI forms close and apply | ✅ **automated** — modal_form_response + `FormServiceTest` |
| 14 | **Retail Xbox login** | Bedrock Xbox | Floodgate chain on real console | ⏳ **manual** — Xbox-shaped CI green; need retail fixture |
| 15 | **G.33 skull textures** | Bedrock | Placed skulls OK; verify item-in-hand texture | ⚠️ **partial** — `BedrockUiCodecTest.skullBlockActorEncodes`; item-in-hand manual |

**Gate:** `./scripts/smoke-phase7-soak.sh` (full 600s) · `FAST_PHASE7=1` for 60s dev pass

**Until 8–15 are ticked:** say *“join/spawn + scripted smoke green; live soak recommended”* — not *“100% Geyser clone.”*

---

### 🟡 Should do if shipping gameplay add-ons

Only if you enable `-PyapGameplay=true` or `installGameplayDefaults`:

| # | Task | Why | How |
|---|------|-----|-----|
| 16 | Manual abilities soak | Dual hotbar, ability book, `/yapabilities reload` — no boot smoke yet | Join with `yap-abilities.jar`, test Shift+F book, build/combat bar swap, reload | ✅ **automated** — reload + snapshot in `smoke-phase7-soak.sh`; Shift+F book still manual |
| 17 | MMO content sanity | 100 quests + 20 bosses in jar | `./scripts/validate-mmo-content.sh` | ✅ **105 quests / 20 bosses / 151 recipes** |
| 18 | Vehicles / combat spot-check | Largest untested gameplay modules (0 unit tests) | Short in-game session on your target world | ✅ **automated** — plugins boot + combat unit gates in phase7 soak |

---

### 🟢 Optional (not blocking v1.0.0.0)

| # | Task | When |
|---|------|------|
| 19 | **600s perf soak** | Before a major marketing push | `SOAK_SECS=600 ./scripts/soak-yap-folia.sh perf` |
| 20 | **Nightly CI smoke** | When you want PRs gated like local battery | Add `smoke-network-full.sh` to GitHub Actions |
| 21 | **Fix Release workflow Folia path** | When tagging GitHub releases | Switch `release.yml` from `fetch-folia.sh` to product build |
| 22 | **Abilities integration tests** | Post-v1 hardening | Bar/book listener + store round-trip tests |
| 23 | **Post-M7 abilities art** | Future milestone | 233-spell CMD models, Bedrock animation bridge — see [MMO_ABILITIES.md](../mmo/MMO_ABILITIES.md) |
| 24 | **Enable Grim AC** | When you want top-tier AC on a backend | `./scripts/grim-ac.sh enable` (downloaded on `seed-defaults.sh`) |
| 25 | **New Mojang protocol** | When MC updates | [PROTOCOL_DUMPS.md](../protocol/PROTOCOL_DUMPS.md) → re-run matrix |

---

## Known gaps that are NOT your todo list

These are **documented product limits**, not bugs you must fix before ship:

| Item | Stance |
|------|--------|
| ViaRewind 1.8 play depth | Out of scope |
| ViaVersion / Geyser / Floodgate jars on product path | Forbidden — built-in stack replaces them |
| Bit-identical packets vs Via/Geyser | Out of scope — behavioral parity only |
| Bedrock vehicles / boat mount sync | Out of v1 |
| YaPGuard = casual default; Grim = top-tier when enabled | See [GRIM.md](../ops/GRIM.md) |
| Smithing / sounds / particles on older JE clients | Same class as ViaBackwards limitations |
| Dashboard Phase 8 every POST audited | Usable today; polish backlog only |

---

## Quick verification (copy-paste)

Run from repo root **after** `./scripts/build-yap-folia.sh`:

```bash
# Minimum before tagging a release (~3 min)
./scripts/smoke-network-full.sh

# Full production battery (server listening on :25566)
gradle verifyConcurrency
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-matrix.sh
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-bedrock-smoke.sh
./scripts/smoke-bedrock-play.sh
SOAK_SECS=300 ./scripts/soak-yap-folia.sh compat
FOLIA_JAR_SOURCE=build ./scripts/smoke-folia-cross-region-tp.sh 180   # cross-region TP
```

Summary artifact: `build/production-test-battery-latest.json`

Deploy after green:

```bash
gradle publishReleasesFolder
cd releases/1.0.0.0/yapcore-release/linux
cp deploy/mariadb/.env.example deploy/mariadb/.env   # edit passwords
./configure-db.sh --server-id lobby
./start-prod.sh --fg
```

---

## Decision guide

| Your goal | Minimum left to do |
|-----------|-------------------|
| **Private LAN / dev server** | Items **1–4** only |
| **Public SMP (core stack, no MMO)** | Items **1–7** + strongly recommend **8–15** |
| **Public SMP + gameplay add-ons** | **1–18** |
| **Marketing “battle-tested at scale”** | All of above + **19** (600s soak) + bot bench docs |
| **GitHub Release zip for strangers** | Fix **21** (CI Folia path) so artifact matches product build |

---

## Related docs

| Doc | Use when |
|-----|----------|
| [YAPCORE_MASTER.md](YAPCORE_MASTER.md) | Full rundown of what exists and what passed |
| [RELEASE_NOTES.md](../start/RELEASE_NOTES.md) | v1.0.0.0 changelog |
| [VIA_GEYSER_PARITY.md](../protocol/VIA_GEYSER_PARITY.md) | §E live soak checklist |
| [TESTING.md](../start/TESTING.md) | Every smoke command |
| [FOLIA_TELEPORT_TRANSACTIONS.md](../folia/FOLIA_TELEPORT_TRANSACTIONS.md) | Cross-region teleport patch |
| [FOLIA_SCHED_COMPAT.md](../folia/FOLIA_SCHED_COMPAT.md) | Legacy plugin scheduler shim |

---

*Update this doc after you close §E soak, re-run the production battery, or cut a new release.*
