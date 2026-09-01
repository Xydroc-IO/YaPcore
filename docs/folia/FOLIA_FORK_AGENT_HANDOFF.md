# Folia fork — Agent 2 / 3 handoff (Phase 1)

Pinned upstream: see `vendor/folia/UPSTREAM.lock` (`ver/26.2.x`).  
Clone: `./scripts/vendor-folia.sh` → `vendor/folia/work/`.  
After `./gradlew applyAllPatches`, generated trees live under `folia-server/` / `folia-api/` (and Paperweight paper-server copies).

**Merge order:** A1 (done) → **A2** → **A3**. Do not co-edit the same patch files.

---

## Scheduler APIs

| Concern | Package / type | Where in Folia tree |
|---------|----------------|---------------------|
| Bukkit region API | `io.papermc.paper.threadedregions.scheduler.RegionScheduler` | `folia-api/paper-patches/features/0002-Region-scheduler-API.patch` |
| Global region API | `…scheduler.GlobalRegionScheduler` | same |
| Async scheduler | `…scheduler.AsyncScheduler` | same (deprecates classic `BukkitScheduler` for region work) |
| Classic Bukkit | `org.bukkit.scheduler.BukkitScheduler` / `CraftScheduler` | paper-server; Folia throws / redirects unsafe sync use |
| Tick owner | `ca.spottedleaf.moonrise…` + Folia `TickRegionScheduler` | `folia-server/minecraft-patches/features/0001-Region-Threading-Base.patch` (~line introducing `TickRegionScheduler`) |
| Regionized server | `RegionizedServer` | same patch (minecraft) |
| Watchdog | `TickRegionScheduler` hooks | `0008-Add-watchdog-thread.patch` |

**YaP product side (do not confuse):** `com.yapcore.sched.YapSched` / `yap-sched` — plugin-facing Folia-safe wrappers. A2 may add a **compat shim** in Folia *or* thicken YapSched; prefer Folia patches only when Bukkit plugins bypass YapSched.

**Agent 2 entry points:** `0002-Region-scheduler-API.patch`, CraftScheduler call sites in `0001-Region-Threading-Base.patch` (paper).

---

## Player teleport / region handoff

| Concern | Type / API | Patch |
|---------|------------|-------|
| Bukkit async TP | `Entity.teleportAsync` / `Player.teleportAsync` | paper `0001-Region-Threading-Base.patch` (throws if sync teleport under region threading) |
| NMS async TP | `Entity.teleportAsync(...)` | minecraft `0001-Region-Threading-Base.patch` |
| Pending queue | `ServerLevel.PendingTeleport` record + `pushPendingTeleport` / `removeAllRegionTeleports` | minecraft `0001-…` (~PendingTeleport) |
| Cross-region accept | deny out-of-region accept | `0010-Do-not-allow-out-of-region-teleport-accept.patch` |
| Passenger desync | root vehicle re-TP | `0009-Teleport-desynced-passengers-to-root-vehicle.patch` |

**Agent 2:** transactional teleport (queue, failure rollback, vehicle trees) — start at `PendingTeleport` + Bukkit `teleportAsync` guard.

---

## Regionizer / region thread pool

| Concern | Type | Patch |
|---------|------|-------|
| Tick scheduler | `TickRegionScheduler` | minecraft `0001-Region-Threading-Base.patch` |
| Regionized server loop | `RegionizedServer` | same |
| Profiling | region profiler timers | `0007-Region-profiler.patch` |
| Docs | region logic | upstream https://docs.papermc.io/folia/reference/region-logic |

Moonrise / chunk system symbols also appear in save paths (`MoonriseRegionFileIO`).

**Agent 3:** pool sizing, steal/queue metrics — `TickRegionScheduler` + profiler patch. Coordinate with A2 before changing teleport completion on region threads.

---

## Chunk / anvil save pipeline

| Concern | Symbol | Patch |
|---------|--------|-------|
| Flush I/O | `MoonriseRegionFileIO.flush` / `flushRegionStorages` | minecraft `0001-Region-Threading-Base.patch` |
| Chunk save | `saveChunk(...)` | `0007-Region-profiler.patch` (wraps save) |
| Region file types | `MoonriseRegionFileIO.RegionFileType` | profiler / moonrise |

Paper/Moonrise anvil path remains the base; Folia adds region-owned save completion.

**Agent 3:** async save offload / batching — touch Moonrise flush + region unload save in `0001` carefully (merge conflicts with A2 teleport pending-flush).

---

## Scoreboard / Team / BossBar (global state)

| Concern | Type | Patch |
|---------|------|-------|
| Scoreboard | `CraftScoreboard`, `CraftScoreboardManager` | paper `0001-Region-Threading-Base.patch` (thread checks / global access) |
| Player board swap | `setPlayerBoard` | same |
| Main scoreboard | NMS `ServerScoreboard` | via CraftScoreboardManager |
| BossBar | Bukkit `BossBar` / CraftBossBar | search paper tree after `applyAllPatches`; often global main-thread assumptions |

**Agent 3 (restore / mirror):** CraftScoreboard* first; BossBar after scoreboard pattern established. Avoid holding region locks while mutating global boards.

---

## Branding (Phase 1 — do not regress)

| File | Change |
|------|--------|
| `folia-server/build.gradle.kts.patch` | `Brand-Name` / titles → `YaP-Folia`, id `yaplabs:yap-folia` |
| `folia-server/paper-patches/features/0002-Build-changes.patch` | `ServerBuildInfoImpl` fallbacks |

Owned by `vendor/folia/patches/0000-yap-branding.patch`.

---

## Build / smoke gates

```bash
./scripts/vendor-folia.sh
./scripts/build-yap-folia.sh
FOLIA_JAR_SOURCE=build ./scripts/smoke-folia.sh
# or
./scripts/verify-yap-folia.sh
```

Product: `folia-jar-source=build` → `server/lib/yap-folia-26.2.jar`.

---

## Ownership cheat-sheet

| Area | Owner | Primary patch files |
|------|-------|---------------------|
| Branding / pipeline | A1 | `0000-yap-branding.patch`, scripts |
| Scheduler + teleport | A2 | api `0002-Region-scheduler-API`, paper/minecraft `0001-Region-Threading-Base`, `0009`/`0010` teleport |
| Region pool + save + scoreboard | A3 | `TickRegionScheduler`, `0007` profiler, Moonrise flush, CraftScoreboard* |

When in doubt: open the patch file under `vendor/folia/work/…` and search the symbol before editing generated sources.
