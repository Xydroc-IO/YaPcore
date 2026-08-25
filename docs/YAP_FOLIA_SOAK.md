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
  ./scripts/soak-yap-folia.sh perf
```

```bash
./scripts/soak-yap-folia.sh list
```

## Profiles

| Profile | Script arg | Jar | sched-compat | teleport transactions | Perf knobs |
|---------|------------|-----|--------------|----------------------|------------|
| **soak-compat** | `compat` | `FOLIA_JAR_SOURCE=build` | on | on | **OFF** |
| **soak-perf** | `perf` | same | on | on | env: `YAP_FOLIA_ENTITY_TICK_BUDGET`, `YAP_FOLIA_ASYNC_CHUNK_SAVE` |

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
| `SKIP_LIVE=1` | compat hook | sched-compat unit path only (default in soak) |
| `YAP_FOLIA_ENTITY_TICK_BUDGET` | **perf** | → `-Dyap.folia.entity-tick-budget` (A3) |
| `YAP_FOLIA_ASYNC_CHUNK_SAVE` | **perf** | → `-Dyap.folia.async-chunk-save` (A3) |

Perf knobs stay **unset in compat**. FoliaKernel forwards any `-Dyap.folia.*` from
the chassis JVM into the managed Folia process.

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
| After A2 soak-compat green | Agent 1 flips example + defaults to `build` |

Do **not** flip product default to `build` from A2/A3 branches — Agent 1 owns that change.

## Operator path

```bash
./scripts/build-yap-folia.sh          # → lib/yap-folia-26.2.jar
# config:
folia-jar-source=build
```

If `folia-jar-source=build` and the jar is missing, YaPcore fails with a clear
error pointing at `./scripts/build-yap-folia.sh`. Stock fallback: `folia-jar-source=fetch`.

See [FOLIA_FORK.md](FOLIA_FORK.md) · [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md).
