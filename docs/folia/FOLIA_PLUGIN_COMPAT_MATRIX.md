# Folia plugin compat matrix

Status of common plugin patterns on YaP-Folia 26.2 with YaPcore product defaults
(`folia-sched-compat=true`, plugin ASM rewrite ON).

| Status | Meaning |
|--------|---------|
| **Works** | Folia-native or first-party (`folia-supported` + region schedulers / YapSched) |
| **Shimmed** | Loads with YaP helpers (sched agent, ASM rewrite, bridge) — verify per release |
| **Broken** | Needs rewrite or will crash / corrupt data |

## First-party (Works)

| Plugin | Notes |
|--------|-------|
| yap-folia-bridge | GlobalRegionScheduler probe |
| yap-skills / yap-combat / yap-crafting | YapSched only |
| yap-chat / yap-playerdata / yap-db | Folia-safe |
| yap-stacker / yap-vehicles | Folia-safe |
| yap-plugin-compat | STARTUP rewrite helper |

## Synthetic / soak cases (Agent 2)

| Case | Status | Evidence |
|------|--------|----------|
| `yap-legacy-sched-smoke` — `runTask` / `runTaskLater` / `runTaskTimer` / `scheduleSyncDelayedTask` | **Shimmed** | `./scripts/smoke-folia-sched-compat.sh` → `YaP-LEGACY-SCHED-SMOKE all-ok` |
| Legacy sync schedule with **no** entity/location context | **Shimmed** | Agent → `GlobalRegionScheduler` + one warn per plugin |
| Rapid cross-region teleport + `folia-teleport-transactions=true` on **yap-folia** | **Shimmed → Works** | patch `0001` + `smoke-folia-cross-region-tp.sh` on `FOLIA_JAR_SOURCE=build` |
| Same teleport flag on **stock Fill** (`folia-jar-source=fetch`) | **Broken** (misconfig) | smoke hard-FAIL; FoliaKernel severe log |
| Plugin missing `folia-supported: true` | **Broken** | Folia refuses load (not sched-agent) |

## Scheduler patterns

| Pattern | Status | Helper |
|---------|--------|--------|
| `YapSched.*` | Works | first-party |
| Folia `EntityScheduler` / `RegionScheduler` / `GlobalRegionScheduler` | Works | Paper API |
| `Bukkit.getScheduler().runTask*` sync | Shimmed | [yap-sched-agent](FOLIA_SCHED_COMPAT.md) → global/entity/region |
| `Bukkit.getScheduler().runTaskAsynchronously*` | Works | Folia async path |
| `scheduleSyncDelayedTask` / `scheduleSyncRepeatingTask` | Shimmed | same agent |
| Sync world/entity mutation from async thread | Broken | must hop to region/entity |

## Teleport / regions

| Pattern | Status | Helper |
|---------|--------|--------|
| `player.teleportAsync(...)` | Works | Folia |
| Sync `teleport()` under region threading | Broken / throws | use async |
| Rapid cross-region `/tp` | Shimmed → Works with YaP patch | [FOLIA_TELEPORT_TRANSACTIONS.md](FOLIA_TELEPORT_TRANSACTIONS.md) |
| Vehicles / passenger trees | Partial | Folia 0009 + YaP confirm; stress separately |

## Scoreboard / BossBar

| Pattern | Status | Owner |
|---------|--------|-------|
| Global scoreboard create/team | Broken on stock Folia | Agent 3 restore |
| Per-player boards (careful) | Partial | Agent 3 |

## ASM back-compat (1.20–1.21 jars)

| Pattern | Status | Helper |
|---------|--------|--------|
| Enchantment / Potion / Particle field renames | Shimmed | [PLUGIN_BACKCOMPAT.md](../plugins/PLUGIN_BACKCOMPAT.md) |
| Versioned `craftbukkit.v1_20_R*` packages | Shimmed | same |
| Deep NMS / Mojang intermediary | Broken | rewrite plugin |

## How to extend

1. Reproduce with `./scripts/smoke-folia-sched-compat.sh` or a minimal jar.
2. Classify Works / Shimmed / Broken in this file.
3. Cheap wins: agent warn + metrics, ASM remap, FoliaBridge log.
4. Expensive wins: Folia patch (coordinate Agents 2/3 ownership).

## Shim fire counter

When the sched agent is active, `SchedCompatMetrics.shimFires()` increments per rewritten
sync schedule. Optional dashboard surface: search logs for `yap-sched-agent:` or expose via
folia-bridge later.
