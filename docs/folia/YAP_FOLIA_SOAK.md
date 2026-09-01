# YaP-Folia soak harness

Shared soak profiles for **YaP-Folia** (`lib/yap-folia-*.jar`). Agent 1 owns the
harness; **Agents 2 and 3 plug checks into these profiles** — do not invent
parallel long-boot scripts.

## Quick start

```bash
# Build/verify jar once (or let soak call verify)
./scripts/build-yap-folia.sh

# Compat soak (unblocks A2 sign-off for default=build)
./scripts/soak-yap-folia.sh compat

# Perf soak (A3 knobs via env — defaults OFF)
YAP_FOLIA_ENTITY_TICK_BUDGET=300 YAP_FOLIA_ASYNC_CHUNK_SAVE=true \
  YAP_FOLIA_MICROTICK_BUDGET_MS=8 \
  ./scripts/soak-yap-folia.sh perf
```

```bash
./scripts/soak-yap-folia.sh list
```

## Profiles

| Profile | Script arg | Jar | sched-compat | teleport transactions | Perf knobs |
|---------|------------|-----|--------------|----------------------|------------|
| **soak-compat** | `compat` | `FOLIA_JAR_SOURCE=build` | on | on | **OFF** |
| **soak-perf** | `perf` | same | on | on | env: budget / async-save / microtick / steal |

Defaults durations: **compat = 300s**, **perf = 600s** (override with argv or `SOAK_SECS=`).

## Pass / fail

**PASS** when:

1. `lib/yap-folia-{ver}.jar` exists (built via `verify-yap-folia.sh` / `build-yap-folia.sh`).
2. Optional hooks for the profile succeed when present (see below).
3. `./scripts/smoke-folia.sh` boots with `folia-jar-source=build`, becomes ready, and
   **holds** until the soak window ends (dies after ready → FAIL). Non-soak smoke
   still exits as soon as ready.

**FAIL** when the jar is missing, smoke does not become ready, or a required hook exits non-zero.

Results JSON: `bench/results/<stamp>-yap-folia-soak-<profile>.json`.

## Env knobs

| Env | Applies | Meaning |
|-----|---------|---------|
| `SOAK_SECS` | both | Hold-ready seconds |
| `SKIP_VERIFY=1` | both | Skip rebuild; require existing `yap-folia` jar |
| `FORCE_REBUILD=1` | both | Always run `verify-yap-folia.sh` (build only) |
| `SKIP_HOOKS=1` | both | Skip A2/A3 hook scripts |
| `SKIP_LIVE=1` | compat hook | unit path only (**soak default is live `0`**) |
| `SOAK_HOOK_SECS` | compat | Seconds for A2 hook smokes (default 300 / 180) |
| `YAP_FOLIA_ENTITY_TICK_BUDGET` | **perf** | → `-Dyap.folia.entity-tick-budget` (A3) |
| `YAP_FOLIA_ASYNC_CHUNK_SAVE` | **perf** | → `-Dyap.folia.async-chunk-save` (A3) |
| `YAP_FOLIA_MICROTICK_BUDGET_MS` | **perf** | → `-Dyap.folia.microtick-budget-ms` (same-thread AI deadline) |
| `YAP_FOLIA_STEAL_THRESHOLD_MS` | **perf** | → steal threshold (needs `scheduler: WORK_STEALING`) |
| `YAP_FOLIA_TASK_SLICE_MS` | **perf** | → task slice (needs `WORK_STEALING`) |
| `YAP_FOLIA_GRID_EXPONENT` | **perf** | → override `threaded-regions.grid-exponent` |
| `YAP_FOLIA_SUBREGION_PARTITION` | **perf** | → force-partition parallel shards (`true`) |
| `YAP_FOLIA_SUBREGION_SHARDS` | **perf** | → shard count (2–4) |
| `YAP_FOLIA_SUBREGION_MSPT_THRESHOLD` | **perf** | → MSPT ms to request partition |
| `YAP_FOLIA_SUBREGION_MIN_ENTITIES` | **perf** | → min entities before partition (default 32) |
| `YAP_FOLIA_SUBREGION_COALESCE_QUIET_TICKS` | **perf** | → post-partition quiet before coalesce cool-count |
| `YAP_FOLIA_SUBREGION_CARVE` | **perf** | → corridor carve before partition (`false` for lobe/pre-gap) |
| `YAP_FOLIA_SUBREGION_PARTITION_DELAY_TICKS` | **perf** | → hot ticks before partition request (default 600) |
| `YAP_FOLIA_SUBREGION_GAP_MAINTAIN_INTERVAL` | **perf** | → post-partition corridor sweep interval |

Perf knobs stay **unset in compat**. FoliaKernel forwards any `-Dyap.folia.*` from
the chassis JVM into the managed Folia process.

## Region pool / Phase 4–5

| Blueprint item | Delivery |
|----------------|----------|
| Sub-region micro-ticking (deferral) | Same-thread time-slice (`microtick-budget-ms`) + count budget |
| **True parallel sub-region ticking** | Force-partition (`folia-subregion-partition=true`) into Folia shards with merge-inhibit |
| Dynamic merge/split | Upstream + YaP force-split/coalesce + `YapRegionPoolMetrics` |
| Thread stealing | `threaded-regions.scheduler: WORK_STEALING` + steal/slice knobs |
| Steal/queue metrics | Merges, splits, migrations, force-partitions, inhibited merges, coalesces |

```bash
# Parallel sub-regions (lobe/pre-gap layout — carve OFF)
YAP_FOLIA_ENTITY_TICK_BUDGET=300 YAP_FOLIA_ASYNC_CHUNK_SAVE=true \
YAP_FOLIA_SUBREGION_PARTITION=true YAP_FOLIA_SUBREGION_CARVE=false \
  ./scripts/soak-yap-folia.sh perf

# Full stack + microtick tune layer
YAP_FOLIA_MICROTICK_BUDGET_MS=8 \
  ./scripts/soak-yap-folia.sh perf
```

Previously:
```bash
YAP_FOLIA_SUBREGION_PARTITION=true YAP_FOLIA_SUBREGION_MSPT_THRESHOLD=15 \
  ./scripts/soak-yap-folia.sh perf
```

## Hooks (plug-in points)

| Profile | Script (if present) | Owner |
|---------|---------------------|-------|
| compat | `smoke-folia-sched-compat.sh` | A2 |
| compat | `smoke-folia-cross-region-tp.sh` | A2 |
| perf | `smoke-folia-async-save.sh` | A3 |

Soak reuses `smoke-folia.sh` for the long boot. Prefer adding assertions to the
hook scripts rather than forking a second Folia life-cycle.

## Coordination — default `folia-jar-source`

| State | Default in example / `FoliaAuthorityConfig` |
|-------|-----------------------------------------------|
| Until A2 reports **soak-compat green** | `fetch` (stock Fill) — **recommended: build** |
| After A2 soak-compat green | **`build`** (YaP-Folia) — Agent 1 flipped |

Shipped default is **`build`**. Stock Fill: set `folia-jar-source=fetch`.

Do **not** change this default from A2/A3 branches — Agent 1 owns it.

## Operator path

```bash
./scripts/build-yap-folia.sh          # → lib/yap-folia-26.2.jar
# config:
folia-jar-source=build
```

If `folia-jar-source=build` and the jar is missing, YaPcore fails with a clear
error pointing at `./scripts/build-yap-folia.sh`. Stock fallback: `folia-jar-source=fetch`.

See [FOLIA_FORK.md](FOLIA_FORK.md) · [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md).

## Agent 2 sign-off — soak-compat

> **compat soak GREEN — clear to flip build default**

Agent 2 verified on `FOLIA_JAR_SOURCE=build` with `folia-sched-compat=true`,
`folia-teleport-transactions=true`, and A3 perf knobs **OFF**:

- `./scripts/smoke-folia-sched-compat.sh` live PASS (300s soak hold)
- `./scripts/smoke-folia-cross-region-tp.sh` live PASS (`fetch` hard-FAIL)
- `./scripts/smoke-folia.sh` / soak-compat hold PASS

**Done (Agent 1):** `folia-jar-source` example + `FoliaAuthorityConfig` default → `build`.
