# YaPcore concurrency & leak testing

> **Commands below are retired.** Paper Phase 3 / Paperclip bench scripts were removed with the Folia product path. Prefer `./scripts/smoke-folia.sh`, `./scripts/bench/run-vs-folia.sh` (if present), and `./scripts/bench/fetch-competitors.sh`.


Layered strategy for the YapEngine **chassis** (edge/I/O sandboxes). Folia owns
game tick on the product path. Java hides raw pointers behind HotSpot, so we combine
static analysis, deterministic interleaving, stress, and heap sampling.

## Quick commands

**Konsole / Dolphin:** drop these onto a terminal (they find the project root themselves and pause at the end):

| Script | What it runs |
|--------|----------------|
| `./tests.sh` or `./scripts/tests.sh` | Interactive menu |
| `./test-unit.sh` | `gradle test` |
| `./test-fray.sh` | `gradle frayTest` |
| `./test-all.sh` | `gradle verifyConcurrency` |
| `./test-endurance.sh [secs] [bots]` | Months-long readiness → **`logs/endurance/latest.html`** |
| `./test-gui.sh` | Test Lab GUI (buttons + console) |
| `./scripts/test-jcstress.sh` | `gradle jcstress` |
| `./scripts/test-stress.sh` | Boundary stress |
| `./scripts/soak-jfr.sh` | Soak + JFR |
| `(retired)` | **Pop ladder** — play-soak + denser heavypop MSPT + bots 50/100/150/200 |

> **`build/reports/problems/problems-report.html`** is Gradle’s toolchain deprecation UI. It is **not** a server soak report. Use **`logs/endurance/latest.html`** for FAIL codes with fix hints aimed at months-long uptime.

**Test Lab GUI:** `./test-gui.sh` or Control Panel → **Test Lab**.

| Phase | Goal | Command |
|-------|------|---------|
| Build-time | Missing locks / static races | `./scripts/test-spotbugs.sh` |
| Optional RacerD | Meta Infer data races | `./scripts/infer-racerd.sh` |
| Unit | Lease + boundary + retention + Phase 3 coordinator | `./test-unit.sh` |
| Deterministic races | Forced interleavings | `./test-fray.sh` |
| Low-level atomics | Visibility / reordering | `./scripts/test-jcstress.sh` |
| Endurance | LIVE/stream/lease/heap plateaus | `./test-endurance.sh 300 64` |
| Day soak | 24h readiness | `./test-endurance.sh 86400 64` |
| Post-mortem | Retention leaks | `./scripts/heap-dump.sh` → MAT |
| TSan | Dynamic HB violations + JNI | `YAP_TSAN_JAVA=…/java ./scripts/run-tsan.sh` |
| All CI-ish | SpotBugs + unit + Fray | `./test-all.sh` |

### Endurance FAIL codes → fixes

| Code | Meaning | Fix |
|------|---------|-----|
| `LIVE_TOKEN_RETENTION` | `SequenceToken.LIVE` not draining | `forget()` in `finally`; prune orphans |
| `STREAM_KEY_LEAK` | Unique stream keys per event | Stable keys + `forgetStream` on disconnect |
| `QUEUE_BACKLOG` | T7/T8 not draining | Lease deadlock / logging flood / missing backpressure |
| `HANDOFF_LOSS` | processed ≪ submitted | Same as backlog |
| `METRIC_CARDINALITY` | Unbounded `ThreadMetrics` keys | Fixed action names (no player/sku in key) |
| `HEAP_GROWTH` / `THREAD_GROWTH` | WARN | JFR / MAT / executor shutdown |

## 1. Static analysis

### SpotBugs + JCIP

Sync hot-path classes are annotated with `@ThreadSafe` / `@GuardedBy`. SpotBugs is scoped to:

- `com.yaplabs.yapengine.sync.*`
- `com.yaplabs.yapengine.sequencing.*`
- `com.yaplabs.yapengine.core.spatial.*`

Reports: `build/reports/spotbugs/`.

### Meta Infer (RacerD)

If `infer` is on `PATH`, `./scripts/infer-racerd.sh` runs RacerD over a fresh compile.
Otherwise SpotBugs covers the in-repo CI path.

## 2. Deterministic concurrency (Fray)

CMU Fray (`org.pastalab.fray.gradle` 0.9.0) instruments bytecode and explores schedules.

```bash
gradle frayTest
```

Key tests:

- `AtomicLeaseManagerFrayTest` — mutual exclusion under 8-way contention
- `BoundarySyncTest#testEntityBoundaryHandoff` — entities crossing quad-trees with shared inventory keys

Regular `gradle test` excludes `@FrayTest` and `@Tag("soak")` so CI stays fast.

## 3. JCStress

Targeted tests under `src/jcstress/java` for `AtomicLeaseManager`:

- mutual exclusion (exactly one acquirer while both hold)
- release visibility (arbiter must re-acquire after release)

```bash
gradle jcstress
```

Results land under `build/jcstress-results/`.

## 4. Load, JFR, heap dumps

### In-process bot swarm

`BoundaryStressMain` (Gradle task `boundaryStress`) submits rapid handoffs — a stand-in until
Mineself / McProtocolLib protocol bots are wired against a live `--nogui` server.

```bash
gradle boundaryStress -Dyap.stress.bots=100 -Dyap.stress.seconds=60
./scripts/soak-jfr.sh --bots=100 --seconds=300
```

Open `logs/jfr/yapcore_soak.jfr` in **JDK Mission Control** → **Old Object Sample**.
Watch for steep growth in Entity / Chunk / PluginClassLoader after disconnects.

### Heap dump (MAT)

```bash
./scripts/heap-dump.sh <pid>            # or uses yapcore.pid
# jcmd <pid> GC.heap_dump /tmp/yapcore_heap.hprof
```

Open the `.hprof` in Eclipse MAT → **Leak Suspects Report**.

### OpenJDK TSan

```bash
YAP_TSAN_JAVA=/path/to/jdk-tsan/bin/java ./scripts/run-tsan.sh
```

Requires an experimental OpenJDK build with `-XX:+ThreadSanitizer`.

## Execution plan

| Phase | Goal | Toolchain |
|-------|------|-----------|
| Build time | Lock annotations + static races | SpotBugs + Meta Infer (RacerD) |
| Unit / CI | Force races & deadlocks | Fray + JCStress |
| Soak | 100+ movers over hours | Boundary swarm + JFR / JMC |
| Post-mortem | Object retention | `jcmd` + Eclipse MAT |
| Native/JNI | Dynamic HB violations | OpenJDK TSan |

## Annotated types

| Class | Annotation |
|-------|------------|
| `AtomicLeaseManager` | `@ThreadSafe`, fields `@GuardedBy("this")` |
| `ChunkSyncDlm` | `@ThreadSafe` |
| `BoundaryArbitrator` | `@ThreadSafe` |

## Fray notes

- Put setup/teardown **inside** `@FrayTest` methods — Fray re-enters the method across
  iterations and does not reliably re-run `@BeforeEach` / `@AfterEach`.
- Do not start the dedicated T7/T8 poll loops under Fray; they use timed `poll` and hit
  `TargetTerminateException`. Use Fray for `AtomicLeaseManager` + concurrent `submitHandoff`;
  use `BoundarySyncTest` / `boundaryStress` for end-to-end pipeline completion.
- Fray found a real lease race: `expireIfStale` could free a lock between CAS and lease
  publish when `grantedAtNanos` was still `0`. Fixed by publishing TTL immediately after CAS
  and skipping expiry while `lease == null`.

## MMO smoke scripts (gameplay tier)

Run with `SKIP_LIVE=1` for compile/unit-only (no Folia boot):

```bash
./scripts/smoke-mmo-m0.sh   # skill kernel
./scripts/smoke-mmo-m1.sh   # 13 skills + GUI
./scripts/smoke-mmo-m2.sh   # combat
./scripts/smoke-mmo-m3.sh   # crafting
./scripts/smoke-mmo-m4.sh   # content
./scripts/smoke-mmo-m5.sh   # Bedrock MMO UI
./scripts/smoke-mmo-m6.sh   # ability engine (≥200 abilities)
./scripts/smoke-mmo-m7.sh   # AoE/homing + spell icons
```

Requires `gradle installGameplayDefaults` or `-PyapGameplay=true` first. See [MMO_PHASES.md](MMO_PHASES.md).
