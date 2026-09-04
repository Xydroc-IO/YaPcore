# Code elegance — follow-up tracks

Post–≤500-line domain splits ([CONTRIBUTING.md](../../CONTRIBUTING.md)). Those splits improve navigability; they do **not** fix DB bootstrap duplication, dual package roots, or thin plugin tests. This document plans those three tracks.

**Scope:** engineering hygiene after structural splits. Not Folia MSPT / cite work ([REAL_GAINS.md](../folia/REAL_GAINS.md)).

**Prerequisite:** domain-line gate green; no behavior changes mixed into these tracks.

---

## Track 1 — DB copy-paste → shared bootstrap

### Goal

One supported path for “open shared YaPDB or fall back to embedded Hikari,” owned by `yap-db-api` (and/or a tiny helper jar), so first-party `*Database` classes keep only **schema migrate + query** logic.

### Inventory

Thirteen first-party `*Database` classes under `yap-first-party/` now open via `YapDbBootstrap` (`yap-db-api`): prefer shared YaPDB, else configure caller-owned embedded Hikari. Plugins keep only schema migrate + query logic. See [YAPDB.md](../data/YAPDB.md).

### Non-goals

- Changing YaPDB wire protocol, JDBC engines, or dialect semantics.
- Forcing every third-party addon onto the helper (document + recommend only).
- Merging per-plugin DDL into one mega-migration.
- Removing embedded fallback (solo / no-YaPDB still must work).

### Phased steps

1. **Extract API** — Done: `YapDbBootstrap` on `yap-db-api` (caller-owned `HikariConfig` for relocate safety). `YapDb` / `YapDbProvider` unchanged; provider is Bukkit-null-safe for unit tests.
2. **Pilot** — Done: protect + playerdata.
3. **Rollout** — Done: all thirteen classes.
4. **Docs** — Done: [YAPDB.md](../data/YAPDB.md) documents bootstrap.
5. **Guard** — Optional: grep CI if new raw Hikari setup blocks reappear under `yap-first-party/**/db/`.

### Risk

| Risk | Mitigation |
|------|------------|
| Subtle pool-size / SQLite idle differences | Copy defaults from one golden plugin into the helper; golden-file or unit-test Hikari property mapping |
| Plugin config flag names differ slightly | Adapter per plugin at call site; do not rename YAML keys in the same PR |
| Shadow/relocate of `com.yapcore.db` | Keep “do not relocate” rule; helper lives in `yap-db-api` |

### Done bar

- All 13 plugins open via the shared bootstrap; no duplicated Hikari setup blocks in plugin sources.
- Shared-on and shared-off paths still open and migrate for the pilot pair (and spot-check two more).
- `yap-db-api` tests cover dialect selection + “shared missing → embedded” branch without a live Bukkit server where practical (mock/`Optional` injection if needed).

---

## Track 2 — Dual packages (`com.yapcore.*` vs `com.yaplabs.yapengine.*`)

### Goal

A deliberate package story: product surface under `com.yapcore`, concurrency chassis branded `yapengine`, with a written migration path so the tree no longer reads like two stitched codebases.

### Inventory (current)

- ~35–38 Java files under `com.yaplabs.yapengine` (chassis: spatial, sequencing, sync/lease/boundary, sandbox, network traffic/compression, `YapEngine`).
- Large `com.yapcore` tree (protocol, crossplay, web, paper glue, first-party plugins).
- Cross-imports already exist (e.g. `YaPcoreEngine` → `YapEngine`, gateway → `NativeEventLoops`).

### Non-goals

- Big-bang relocate of all chassis types in one commit.
- Renaming the runtime product / jar marketing names mid-release without a release-note plan.
- Moving first-party plugins into `yapengine`.

### What stays under the yapengine brand

Keep (or explicitly re-export) as the **concurrency / spatial chassis**:

- `YapEngine`, `EngineController`, `CompatibilityBridge`
- `core.spatial.*`, `sequencing.*`, `sync.*` (lease, DLM, handoff, boundary)
- Sandbox / IO roles used by the chassis (`PluginSandbox`, heavy/UI pools)
- Chassis-adjacent network helpers still owned by the engine (`NativeEventLoops`, `TrafficCop`, compressors) until a dedicated `com.yapcore.network` cut is justified

Product/protocol/ops stay `com.yapcore` (gateway, crossplay, dashboard, Paper kernel glue, plugins).

### Migration strategy

**Prefer gradual re-export over hard move.**

| Phase | Action |
|-------|--------|
| **A — Policy** | Done: ownership table in [CONTRIBUTING.md](../../CONTRIBUTING.md); detail in this file. Stop adding new dual homes. |
| **B — Facades** | Where `yapcore` already wraps chassis (`YaPcoreEngine`, thin adapters), keep facades as the stable entry; deprecate direct deep imports from plugins if any appear. |
| **C — Optional hard move** | Only for leaf types with zero external consumers: move package + leave `deprecated` type-forwarding stubs for one release. No hard move of `SequenceToken` / lease types without a compatibility window. |
| **D — Brand decision** | Either (1) keep `yapengine` forever as the named chassis module, or (2) schedule a major-version package rename to `com.yapcore.engine.*` with stubs. Decide before Phase C expands. **Default recommendation:** keep `com.yaplabs.yapengine` as the chassis brand; do not rename unless shipping a major break anyway. |

### Risk

| Risk | Mitigation |
|------|------------|
| Broken reflective/class-name assumptions | Prefer facades; grep for FQCN strings before moves |
| Review noise from mass import churn | One package cluster per PR; no logic changes |
| Accidental new `yapcore` copies of chassis logic | CONTRIBUTING ownership + review checklist |

### Done bar

- Written ownership map (above) accepted; no new files that duplicate chassis concepts under the wrong root.
- Cross-import graph is intentional (product → chassis), not bidirectional spaghetti.
- If any hard moves landed: deprecated stubs compile for one release; changelog notes the FQCN change.

---

## Track 3 — Thin tests (high-value plugin coverage)

### Goal

Raise confidence on operator-critical plugins that today rely on manual smoke, without diluting chassis Fray/jcstress investment.

### Inventory (current)

| Area | Strength |
|------|----------|
| Chassis (`src/test`, yapengine) | Unit + Fray + jcstress (lease, boundary, sequencing) |
| APIs / some plugins | `yap-db-api`, combat, abilities, playerdata, perms, … have some tests |
| Gaps | **chat**, **factions**, **essentials**, **protect**, **world** (beyond WorldEdit-related paths), plus guilds/regions/moderation and others with **zero** `src/test` |

### Non-goals

- Matching chassis concurrency-test density on every plugin.
- Full Bukkit integration suite in CI for all plugins (prefer pure unit + narrow fakes).
- Replacing soak/cite ([REAL_GAINS.md](../folia/REAL_GAINS.md)) with unit tests.

### Priority order (high value first)

1. **protect** — claim/flag rules, bypass permissions, change-log invariants (grief = high blast radius).
2. **factions** — claim overlap, relation rules, power/bank edge cases (logic-heavy; already split from god classes).
3. **essentials** — home/warp/kit permission gates and serialization round-trips.
4. **chat** — format pipeline, mute/ignore, channel routing (pure string/state tests).
5. **world** — selection/clipboard invariants and command parsing **without** full WE; leave WE shim as thin smoke if needed.
6. Stretch: **regions**, **guilds**, **moderation** permission/ persistence helpers.

### Phased steps

1. **Harness** — Shared test fixtures pattern (temp dir, fake config, in-memory or H2/SQLite where SQL is involved); mirror `yap-db-api` dialect tests where useful.
2. **Protect + factions** — Small JUnit sets on pure domain types extracted by the ≤500 splits; wire into existing `gradle test`.
3. **Essentials + chat** — Same; no Folia server required for first slice.
4. **World** — Parser / op dispatch unit tests; optional smoke only for WE integration.
5. **Gate** — Document expected modules in PR template test plan; do not block CI on coverage %.

### Risk

| Risk | Mitigation |
|------|------------|
| Tests coupled to Bukkit | Test domain objects and static helpers first; mock only at edges |
| Flaky SQL tests | Prefer dialect/unit over live MariaDB in default CI |
| Slow suite | Keep Fray/jcstress chassis-only; plugin tests stay fast unit |

### Done bar

- Protect, factions, essentials, chat, and world each have a non-empty `src/test` with ≥3 meaningful assertions each (not smoke-only “constructs”).
- `gradle test` (product-relevant modules) green in CI with the new suites.
- Chassis Fray/jcstress jobs unchanged in scope and still required.

---

## Sequencing vs other work

| Order | Why |
|-------|-----|
| After ≤500 splits + line-limit gate | Stable extraction targets for DB helpers and testable types |
| Track 1 ∥ Track 3 early slices | Low coupling; both improve plugin maintainability |
| Track 2 policy (Phase A) anytime; hard moves last | Package churn is release-sensitive |
| Never block cite/soak PRs on these tracks | Performance evidence stays on [REAL_GAINS.md](../folia/REAL_GAINS.md) |

## Out of scope (later elegance)

Dashboard HTTP monolith factoring, god-method cleanup below 500 lines, and third-party shim debt are **not** part of these three tracks.
