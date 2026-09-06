# Production readiness — phased closeout

Operator + engineering checklist to claim soft-launch, ops-signed, or soak-proven.
Not Folia MSPT cite work ([REAL_GAINS.md](../folia/REAL_GAINS.md)).

## Claims

| Claim | Meaning |
|-------|---------|
| **Soft-launch ready** | Engineering gates green; secrets/edge docs followed; release assets current |
| **Ops-signed** | [CROSSPLAY.md §E](../network/CROSSPLAY.md) ticked on a live box |
| **Soak-proven** | `./scripts/yapctl soak-long 12` PASS recorded |

Soft-launch does **not** require soak-proven marketing language.

## Enterprise hygiene standing (accepted)

Internal engineering scorecard (policies, CI, ≤500 domain structure, DB bootstrap, tests, package ownership, production claims) — **~90% accepted** (target band **90–95%**).

| Bar | State |
|-----|--------|
| Soft-launch / ops-signed / soak-proven | **3/3 met** |
| Domain ≤500 + DB bootstrap + package ownership | **Met** (Track 1–2) |
| Former zero-test plugin smokes + DualTrafficCop contract | **Met** |
| CI high-value (protect/world/map/regions/discord/lagguard + smokes) | **Met** |
| YaP-Folia provenance (YapLabs authors, patch inventory docs, fresh pin) | **Met** — [YAP_FOLIA_PATCHES.md](../folia/YAP_FOLIA_PATCHES.md) |
| Path to ~95% | Deeper suites on protect/map/regions/discord (not more zeros); selective crossplay adapters |
| Product polish P0/P1 | **Met** — shared messages, defaults pack, Bedrock hubs, catalog reload/DB UX — [MESSAGES.md](../plugins/MESSAGES.md) · [RELEASE_NOTES.md](../start/RELEASE_NOTES.md) |

This is **not** a claim vs Paper MSPT. See [CODE_ELEGANCE_FOLLOWUP.md](CODE_ELEGANCE_FOLLOWUP.md) and whitepaper §13.

## Phase 0 — Baseline (done)

- Domain ≤500 gate + splits
- Shared `YapDbBootstrap` for first-party SQL pools ([YAPDB.md](../data/YAPDB.md))
- Package ownership policy ([CONTRIBUTING.md](../../CONTRIBUTING.md))
- SECURITY / [EDGE_HARDEN.md](../network/EDGE_HARDEN.md) / [SECRETS.md](../start/SECRETS.md)

## Phase 1 — Engineering gates (this phase)

- [x] `gradle checkDbBootstrapHygiene` + CI step
- [x] CI / release workflows on **Java 25**
- [x] High-value unit suites: protect, factions (+ API), essentials, chat, world, **map, regions, discord, lagguard**, **conquest (+ API)**
- [x] Former zero-test plugin smokes (admin, commands, floodgate, folia-bridge, packs, plugin-compat, tab, worldedit-shim, yap-db, disasters)
- [x] Dual TrafficCop contract test (keep two — product GameEvent + chassis sequencer)
- [x] PR template points at these modules

Verify locally:

```bash
gradle checkDomainLineLimits checkDbBootstrapHygiene
gradle :protect-plugin:test :factions-plugin:test :essentials-plugin:test \
  :chat-plugin:test :world-plugin:test :yap-factions-api:test :yap-db-api:test \
  :yap-conquest-api:test :conquest-plugin:test \
  :map-plugin:test :regions-plugin:test :discord-plugin:test :lagguard-plugin:test
gradle :test --tests 'com.yapcore.network.DualTrafficCopContractTest'
```

## Phase 2 — Ops sign-off (solo operator)

You do **not** need a QA team. One person + this box is enough for ops-signed.
Retail Xbox / multi-player grief load stays optional before marketing “full play depth.”

**Gameplay ship bar (current):** thin skills + dungeons + stacker + encyclopedia knobs + disasters — see [SKILLS.md](../plugins/SKILLS.md), [DUNGEONS.md](../plugins/DUNGEONS.md), and [plugins/README.md](../../plugins/README.md). Full MMO / vehicles / abilities packs were removed from the product path.

### 2a — Push & CI (agent / you)

```bash
git push origin main
```

Confirm GitHub Actions CI is green on Java 25 (line limits, DB hygiene, plugin suites, shadowJar, concurrency).

### 2b — Edge / secrets (solo, ~15 min)

Walk [SECRETS.md](../start/SECRETS.md) production order once. Confirm on this host:

- [x] `web-dashboard-bind=127.0.0.1` (or SSH tunnel only)
- [x] Link metrics not public ([EDGE_HARDEN.md](../network/EDGE_HARDEN.md))
- [x] Forwarding secret / DB passwords set and **not** in git
- [x] Public game edge is intentional (`exposed=true` only if you want public)

### 2c — §E solo checklist (you + one JE client; Bedrock if you have it)

**Done (ops-signed minimum, 2026-09-04):** JE join via Link `:25565`, dig/place/chat/command, resource pack prompt, chest/furnace/hopper + anvil.

**JE specialty set (same day):** smithing, loom, stonecutter, cartography, enchant — open + slots OK on Java.

Still optional before marketing full play depth: Bedrock device + Bedrock specialty stations, `/yapknobs status`, `/skills`.

Do **not** wait for other humans. Tick in [CROSSPLAY.md §E](../network/CROSSPLAY.md) as you go:

1. [x] JE modern client → `yapcoremc.yaplabs.us:25565` (or LAN) — dig/place/chat/one command  
2. [x] Accept or decline resource pack  
3. [x] Open chest + furnace + anvil (minimum specialty set); note Stretch gaps  
4. Optional same session: Bedrock Android/Win on native UDP if you have a device  
5. Optional: `/yapknobs status`, `/skills`  

Xbox retail = later marketing bar, not soft-launch.

### 2d — Soak (already runnable alone)

`./scripts/yapctl soak-long 12` samples heap/threads while YaP-Folia stays up.
No players required.

**Recorded PASS (2026-09-05):** `logs/soak/soak-long-20260905T031507Z.log` — **soak-proven**. Heap/thread slope within bounds; Folia pid locked 12h with ship knobs on.

Check progress on a future soak:

```bash
tail -5 logs/soak/soak-long-*.log
./scripts/yapctl status   # or ./scripts/status.sh
```

### Solo “ops-signed” bar

| Required | Optional later |
|----------|----------------|
| 2a CI green | Xbox retail Bedrock |
| 2b edge/secrets | Multi-player load party |
| 2c JE join + packs + 2–3 containers | Full specialty station matrix |
| 2d soak 8h+ floor (12h preferred) | Live VFX spam party |

## Phase 3 — Tag / marketing polish

- Version bump checklist only when cutting a new tag ([RELEASES.md](../start/RELEASES.md))
- Rebuild Folia with encyclopedia NMS patch only if enabling those knobs
- Stretch Bedrock UI / FAWE CFI stay backlog

## Phase 4 — Deferred elegance

- Dual TrafficCops stay dual (contract-tested); no merge — Track 2 Phase D
- Deeper suites on protect/map/regions/discord (path ~90% → ~95%)
- Selective crossplay Paper reflection → compile-time adapters
- Do **not** gate CI on coverage %

## Related

- [RELEASE_NOTES.md](../start/RELEASE_NOTES.md) — still-open ops items
- [CODE_ELEGANCE_FOLLOWUP.md](CODE_ELEGANCE_FOLLOWUP.md) — DB / packages / thin tests
- [YAP_FOLIA_PATCHES.md](../folia/YAP_FOLIA_PATCHES.md) — YaP-Folia patch inventory
