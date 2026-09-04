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

## Phase 0 — Baseline (done)

- Domain ≤500 gate + splits
- Shared `YapDbBootstrap` for first-party SQL pools ([YAPDB.md](../data/YAPDB.md))
- Package ownership policy ([CONTRIBUTING.md](../../CONTRIBUTING.md))
- SECURITY / [EDGE_HARDEN.md](../network/EDGE_HARDEN.md) / [SECRETS.md](../start/SECRETS.md)

## Phase 1 — Engineering gates (this phase)

- [x] `gradle checkDbBootstrapHygiene` + CI step
- [x] CI / release workflows on **Java 25**
- [x] High-value unit suites: protect, factions (+ API), essentials, chat, world
- [x] PR template points at these modules

Verify locally:

```bash
gradle checkDomainLineLimits checkDbBootstrapHygiene
gradle :protect-plugin:test :factions-plugin:test :essentials-plugin:test \
  :chat-plugin:test :world-plugin:test :yap-factions-api:test :yap-db-api:test
```

## Phase 2 — Ops sign-off (solo operator)

You do **not** need a QA team. One person + this box is enough for ops-signed.
Retail Xbox / multi-player grief load stays optional before marketing “full play depth.”

### 2a — Push & CI (agent / you)

```bash
git push origin main
```

Confirm GitHub Actions CI is green on Java 25 (line limits, DB hygiene, plugin suites, shadowJar, concurrency).

### 2b — Edge / secrets (solo, ~15 min)

Walk [SECRETS.md](../start/SECRETS.md) production order once. Confirm on this host:

- [ ] `web-dashboard-bind=127.0.0.1` (or SSH tunnel only)
- [ ] Link metrics not public ([EDGE_HARDEN.md](../network/EDGE_HARDEN.md))
- [ ] Forwarding secret / DB passwords set and **not** in git
- [ ] Public game edge is intentional (`exposed=true` only if you want public)

### 2c — §E solo checklist (you + one JE client; Bedrock if you have it)

Do **not** wait for other humans. Tick in [CROSSPLAY.md §E](../network/CROSSPLAY.md) as you go:

1. JE modern client → `yapcoremc.yaplabs.us:25565` (or LAN) — dig/place/chat/one command  
2. Accept or decline resource pack  
3. Open chest + furnace + anvil (minimum specialty set); note Stretch gaps  
4. Optional same session: Bedrock Android/Win on native UDP if you have a device  
5. Optional: `/yapknobs status`, one ability cast, `/bag` page  

Xbox retail = later marketing bar, not soft-launch.

### 2d — Soak (already runnable alone)

`./scripts/yapctl soak-long 12` samples heap/threads while the server stays up.
No players required. When the log prints PASS (or you hit 8h floor), record in
[REAL_GAINS.md](../folia/REAL_GAINS.md) / [RELEASE_NOTES.md](../start/RELEASE_NOTES.md).

Check progress:

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

- Dual-package facades / hard moves ([CODE_ELEGANCE_FOLLOWUP.md](CODE_ELEGANCE_FOLLOWUP.md) Track 2 B–D)
- Extra plugin suites (regions, guilds, moderation)
- Do **not** gate CI on coverage %

## Related

- [RELEASE_NOTES.md](../start/RELEASE_NOTES.md) — still-open ops items
- [CODE_ELEGANCE_FOLLOWUP.md](CODE_ELEGANCE_FOLLOWUP.md) — DB / packages / thin tests
