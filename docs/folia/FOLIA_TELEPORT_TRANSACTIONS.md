# Folia teleport transactions (cross-region integrity)

## Problem

Folia already uses `Entity.teleportAsync` + `ServerLevel.PendingTeleport` so entities
do not accept teleports out-of-region. Rapid cross-region teleports (player `/tp`,
portals) can still race: duplicate entity views, inventory desync, or dropped
pending teleports under load.

## YaP two-phase commit

Patch: `vendor/folia/patches/0001-yap-teleport-transactions.patch`

| Phase | Where | Action |
|-------|-------|--------|
| **PREPARE** | source region | Snapshot UUID, inventory hash, vehicle tree; push `PendingTeleport` on **both** origin and destination |
| **COMMIT** | destination region | Apply position/velocity; accept passenger tree |
| **CONFIRM** | destination | Verify entity present once; clear origin pending |
| **ROLLBACK** | source (on timeout/fail) | Cancel destination pending; restore origin position if COMMIT never confirmed |

Config (`server.properties`):

```properties
folia-teleport-transactions=true
```

JVM flag (injected by `FoliaKernel` when enabled):

```text
-Dyap.folia.teleport-transactions=true
```

**Requires YaP-Folia build jar** (`folia-jar-source=build`). Stock Fill Folia has no
`YapTeleportTransaction`. With `folia-teleport-transactions=true` and
`folia-jar-source=fetch`, `FoliaKernel` logs a **severe** error pointing at
`./scripts/build-yap-folia.sh`. The live smoke **hard-fails** on `FOLIA_JAR_SOURCE=fetch`.

## Scope (Phase 2)

1. Player `/tp` and plugin `teleportAsync` for players
2. Nether/end portal transitions
3. Vehicles: best-effort via Folia `0009-Teleport-desynced-passengers-to-root-vehicle` + CONFIRM; full vehicle trees are stretch

## Metrics

When patched jar + flag are active, expect log lines:

- `YaP-TP-TX prepare`
- `YaP-TP-TX commit`
- `YaP-TP-TX confirm`
- `YaP-TP-TX rollback`

## Smoke / soak

```bash
FOLIA_JAR_SOURCE=build ./scripts/smoke-folia-cross-region-tp.sh 180
# SKIP_LIVE=1 — patch + docs only
# FOLIA_JAR_SOURCE=fetch → FAIL (intentional)
```

Class presence: paperclip binary deltas hide class names; smokes accept sibling
`folia-server-*.jar` / bundler embed, or `lib/yap-folia-{ver}.patches.txt` listing
`0001-yap-teleport-transactions.patch` (written by `build-yap-folia.sh`).

### Soak results (Agent 2 — compat soak)

| Gate | Result |
|------|--------|
| Patch file + docs | PASS |
| Stock `fetch` jar | FAIL (intentional hard fail) |
| Boot + flag on `FOLIA_JAR_SOURCE=build` | PASS |

## Ownership

- **Agent 2** owns this patch and smoke.
- Do **not** co-edit `TickRegionScheduler` / region merge with Agent 3 in the same PR.
- Apply via `./scripts/folia-patch.sh` / `./scripts/build-yap-folia.sh`.

## PR note

> Teleport transactions landed — Agent 3 clear for region work.
