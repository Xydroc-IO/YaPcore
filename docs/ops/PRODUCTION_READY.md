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

## Phase 2 — Ops sign-off (human)

1. Push main so remote CI runs on Java 25
2. [SECRETS.md](../start/SECRETS.md) production order
3. [EDGE_HARDEN.md](../network/EDGE_HARDEN.md) binds (dashboard/metrics localhost)
4. Tick [CROSSPLAY.md §E](../network/CROSSPLAY.md)
5. Finish / record 12h soak ([YAP_FOLIA_SOAK.md](../folia/YAP_FOLIA_SOAK.md))

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
