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

## Scope (Phase 2)

1. Player `/tp` and plugin `teleportAsync` for players
2. Nether/end portal transitions
3. Vehicles: best-effort via Folia `0009-Teleport-desynced-passengers-to-root-vehicle` + CONFIRM; full vehicle trees are stretch

## Metrics (folia-bridge / agent)

When patched jar + flag are active, expect log lines:

- `YaP-TP-TX prepare`
- `YaP-TP-TX commit`
- `YaP-TP-TX confirm`
- `YaP-TP-TX rollback`

## Smoke

```bash
./scripts/smoke-folia-cross-region-tp.sh
# SKIP_LIVE=1 — validates patch file + docs present without boot
```

Live gate: **100** rapid cross-region teleports, **0** inventory loss / duplicate UUID errors in log.

## Ownership

- **Agent 2** owns this patch and smoke (ship before Agent 3 regionizer edits).
- Do **not** co-edit `TickRegionScheduler` / region merge with Agent 3 in the same PR.
- Apply via Agent 1 pipeline: `./scripts/folia-patch.sh` / `./scripts/build-yap-folia.sh` when present.

## PR note

> Teleport transactions landed — Agent 3 clear for region work.
