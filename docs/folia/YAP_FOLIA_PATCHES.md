# YaP-Folia patch inventory

Ordered deltas under [`vendor/folia/patches/`](../../vendor/folia/patches/). Authors: **YapLabs** (`folia@yaplabs.com`).

**Apply:** `folia-patch.sh pre` → Folia `applyAllPatches` → `folia-patch.sh post`  
(see [`scripts/build-yap-folia.sh`](../../scripts/build-yap-folia.sh)).

Upstream pin: [`vendor/folia/UPSTREAM.lock`](../../vendor/folia/UPSTREAM.lock) — refresh with `./scripts/vendor-folia.sh --update-lock` then rebuild and re-verify cites.

## Patches

| File | Workstream | Status |
|------|------------|--------|
| `0000-yap-branding.patch` | Brand-Id `yaplabs:yap-folia` | landed |
| `0001-yap-teleport-transactions.patch` | Cross-region TP PREPARE/COMMIT/CONFIRM | landed |
| `0010-yap-async-chunk-save.patch` | Moonrise flush off region thread | landed |
| `0011-yap-scoreboard-swmr.patch` | CraftScoreboard SWMR | landed |
| `0012-yap-entity-tick-budget.patch` | Hot-region Mob AI count budget | landed |
| `0013-yap-region-pool-and-microtick.patch` | Pool metrics, steal knobs, microtick, grid override | landed |
| `0014-yap-subregion-force-partition.patch` | Force-partition + merge-inhibit | landed |
| `0015-yap-cross-region-neighbor-defer.patch` | Defer cross-shard neighbor/shape updates | landed |
| `0016-yap-partition-stability-gates.patch` | Min-entities + coalesce quiet + null-safe split | landed |
| `0017-yap-partition-empty-buffer-required.patch` | Refuse force-partition without empty-buffer cut | landed |
| `0018-yap-corridor-carve-before-partition.patch` | Unload Folia-wide corridor then force-partition | landed |
| `0019-yap-post-partition-gap-hold.patch` | Maintain corridor gap after partition | landed |
| `0020-yap-version-fetcher.patch` | Version fetcher branding | landed |
| `0021-yap-advertise-secure-chat.patch` | Secure-chat advertise UX | landed |
| `0022-yap-hopper-tick-budget.patch` | Hopper BE transfer budget | landed |
| `0023-yap-smart-entity-budget-microtick.patch` | MSPT-gated budget + anti-starve | landed |
| `0024-yap-subregion-partition-harden.patch` | Engage hysteresis + coalesce wall + cuts | landed |
| `0025-yap-encyclopedia-hooks.patch` | Encyclopedia NMS hooks (**defaults off**) | landed |

Scheduler shim is **not** a Folia patch — it is `yap-sched-agent` (`-javaagent`). See [MODULES_AND_API.md](../plugins/MODULES_AND_API.md).

## Knobs

| System property | Default | Effect |
|-----------------|---------|--------|
| `-Dyap.folia.async-chunk-save=true` | **true** (product) | Enqueue Moonrise flush off region thread |
| `-Dyap.folia.scoreboard-swmr=true` | true (product) | Bukkit scoreboard mutations under write lock |
| `-Dyap.folia.teleport-transactions=true` | true (product) | Cross-region teleport integrity |
| `-Dyap.folia.entity-tick-budget=N` | **400** (product) | Max Mob AI ticks/region tick when hot; 0=off |
| `-Dyap.folia.budget-mspt-threshold=M` | **12** | Engage entity/microtick only if prior MSPT ≥ M |
| `-Dyap.folia.entity-tick-max-deferred=A` | **40** | Force-tick after A consecutive skips |
| `-Dyap.folia.hopper-tick-budget=N` | **64** (product) | Max hopper transfers per region tick |
| `-Dyap.folia.microtick-budget-ms=N` | **8** (product) | Soft ms deadline for Mob AI phase |
| `-Dyap.folia.subregion-partition=true` | **true** (product) | Force-partition hot regions into parallel shards |
| `-Dyap.folia.subregion-mspt-clear=N` | **16** | Hysteresis vs engage threshold |
| `-Dyap.folia.subregion-coalesce-min-wall-ms=N` | **30000** | Min wall-clock ms after partition before coalesce |

Operator soak profiles: [YAP_FOLIA_SOAK.md](YAP_FOLIA_SOAK.md). Citeable MSPT: [REAL_GAINS.md](REAL_GAINS.md) · [CANVAS_PARITY.md](CANVAS_PARITY.md).

## Region model (short)

Folia’s invariant: **one tick thread owns one region**. YaP force-partitions hot contiguous areas into independent Folia regions with merge-inhibition so they stay schedulable in parallel. Neighbor updates across shard cuts may lag by up to one region tick (`0015`). Official population cites use the **ship profile** and disclose `knob_*` in JSON.
