# MSPT scoreboard — YaPcore vs stock Paper

**Product target:** high-population / heavy-load Minecraft networks — not empty
lobby boxes. Spatial tick (entities, block entities, redstone, block/fluid) stays
**on** for that class of server.

**Gate rule:** Do not claim “faster than Paper under load” without a fresh
`heavypop` (or agreed heavy) row in [`bench/results/`](../bench/results/) from
[`scripts/bench/run-vs-paper.sh`](../scripts/bench/run-vs-paper.sh).

## What we measure

| Scenario | Load | Role |
|----------|------|------|
| `heavypop` | Dense primed TNT + hoppers across 4 interior quads | **Primary beat-Paper gate** |
| `entity` | 120 primed TNT × 4 quads | Mid load / entity path |
| `farm` | Wheat on farmland × 4 quads | Block/random stress |
| `idle` | Empty-ish world | Regression guard only — overhead OK to lose slightly |

Same seed (`yap-bench-1`). Sample window defaults to 30s after 15s warmup.
Metrics: `Server.getAverageTickTime()` (MSPT) and `Server.getTPS()[0]`.

## High-pop proof (bots + world fixtures)

Default: **500** Mineflayer clients. Not TNT-only.

| Piece | Role |
|-------|------|
| Mineflayer swarm | Movement, combat attempts, chests, digs, quad-border crossings |
| World fixtures | Farms, hoppers, villagers, animals, redstone, border chests |
| **Shared on every competitor** | `yap-pop-sim`, **`yap-placeholderapi`**, **`yap-gameplay-knobs`**, **`yap-vehicles`** (same jars YaP ships) |
| **Stock forks only** | ViaVersion + ViaBackwards + ViaRewind — stand-in for YaP **native** JE multi-version |
| **YaP only** | `yap-spatial-tick` (product under test; not mirrored onto forks) + no Via* jars |
| Metrics | `players_*` (≥80% of target), chunks, entities, MSPT/TPS |

```bash
./scripts/bench/fetch-parity-plugins.sh   # Via* into lib/
./scripts/bench/run-highpop.sh            # default 500 players
./scripts/bench/run-highpop.sh 500 45
# Heap: YAP_BENCH_XMS=8G YAP_BENCH_XMX=16G (defaults)
```

Fairness: `compare-highpop.py` rejects insufficient / mismatched online counts.

Same load proofs as above — ranks fair MSPT across forks:

```bash
./scripts/bench/fetch-competitors.sh          # paper + purpur + leaf @ paper-version
YAP_BENCH_ENTITIES=300 YAP_BENCH_HOPPERS=64 \
  ./scripts/bench/run-vs-ecosystem.sh heavypop 30
```

Optional: `YAP_BENCH_COMPETITORS=paper,purpur,leaf,yapcore` (subset OK).
Compare: `scripts/bench/compare-ecosystem.py` (ranks fair runs; rejects fuse/TNT mismatch).

## How to run

```bash
# Java 25+, lib/paper-26.2.jar (stock) + lib/paper-26.2-yap.jar (YaP)
# Primary gate for high-pop product:
./scripts/bench/run-vs-paper.sh heavypop 40

# Secondary
./scripts/bench/run-vs-paper.sh entity 30
./scripts/bench/run-vs-paper.sh farm 30
./scripts/bench/run-vs-paper.sh idle 20

python3 scripts/bench/compare-results.py \
  bench/results/<stamp>-heavypop-stock.json \
  bench/results/<stamp>-heavypop-yapcore.json
```

Tune density:

```bash
YAP_BENCH_ENTITIES=320 YAP_BENCH_HOPPERS=96 ./scripts/bench/run-vs-paper.sh heavypop 40
```

(`run-vs-paper.sh` forwards these as `-Dyap.bench.entities` / `-Dyap.bench.hoppers`.)

Exit code of `compare-results.py`: `0` = win/tie **and** load proofs OK,
`1` = MSPT loss, `3` = **fairness fail** (unequal load / TNT not ticking) — do not claim.

## Fairness rules (anti-gaming)

MSPT is **main-thread** tick time (what players feel as lag). Moving work to
spatial cores is the product — that is allowed — but we must prove the **same
work still ran**:

| Check | Requirement |
|-------|-------------|
| Same scenario / warmup / sample | Identical on both JSONs |
| TNT alive start/end | Within 2% across stock vs YaP; ≥98% of expected spawn |
| Fuse drain | Mean fuse drops ≈ `20 × sample_seconds` (±15%) — proves TNT ticked |
| Hoppers (heavypop) | Present and stable on both sides |
| `max-tnt-per-tick` | Bench sets **0** (unlimited) — default 100 would game entity load |
| `max-tick-time` entity/tile | Bench sets **0** (disabled) — default 50ms aborts mid-list |
| Dense TNT layout | ≤~600 primed TNT/chunk; extra piles stay deep-interior (not border halo) |
| Order bias | Optional `YAP_BENCH_ORDER=yap-first` — win should hold either order |

Missing load fields → `FAIRNESS: FAIL` (re-run with current bench plugin).

```bash
# Primary (stock first)
./scripts/bench/run-vs-paper.sh heavypop 45

# Confirm no order bias
YAP_BENCH_ORDER=yap-first ./scripts/bench/run-vs-paper.sh heavypop 45
```

## Win condition (Beat Paper)

**Primary:** on `heavypop` dense enough that **Paper/Leaf MSPT climbs toward the
tick budget** (not ~2ms noise), YaPcore with Phase 3–3.7 spatial flags **on** shows
**lower `mspt_mean`** than stock Paper **and** competitive forks on the same machine.

Mid-density (~2ms MSPT) is a **warm-up / regression** gate only — single-threaded
forks still have headroom there, so multithreading cannot show its purpose.
Target regime: Paper/Leaf under real pressure (teens of mspt+, TPS risk), with
equal fuse drain proofs.

**Secondary:** `entity` / `farm` wins are nice; not required to ship high-pop story.

**Idle:** may lose a little MSPT with full spatial deferral (offer/flush cost).
That is acceptable for a high-pop product — do not optimize for empty worlds at
the expense of loaded ones.

## Phase mapping

| Phase | Role |
|-------|------|
| 3 | Interior entity tick on cores 3–6 |
| 3.5 | Interior block/fluid/random under leases (**default on**) |
| **3.6** | Interior block entities + redstone block events on quads (**default on**) |
| **3.7** | Border entities / TE / events on T8 under DLM (**default on**) |
| **3.8** | Non-player tracker `sendChanges` by quad / T8 (**default on**) |
| **3.9** | Skip-clean tracker + `ServerEntity` early-out (**default on**); players/track-untrack stay on main |
| **3.10** | Coalesce entity+BE+events barriers; Paper EAR on spatial tick; first-party distant brain/path throttle (**default on**) |
| **3.11** | Deepen distant AI: `createPath` + `Mob.serverAiStep` + `Brain.tick`; no-players=far; tinier tracker barrier; start=24/far=80 |
| 4 | Dual-stack + YaP plugins polish |

## Results table

### Spatial harden + tracker (Phase A/B) — 2026-08-21 ~05:22–05:48

Changes: always offload single-quad flush to spatial cores; coalesce entity+BE flush;
O(1) border chunk test; `ChunkMap.newTrackerTick` non-player `sendChanges` on 3–6/T8
(`spatial-tracker`). Bench ports **25670/25671** (avoid highpop on 25570).
15s warmup / 45s sample. All rows: fuseΔ=900, TNT 1200→1200, hoppers=258, **FAIRNESS OK**.

| Stamp | Order / flags | Stock MSPT | YaP MSPT | Delta | Verdict |
|-------|---------------|------------|----------|-------|---------|
| `…T122214Z` | stock-first · spatial A (tracker off) | 2.589 | 2.407 | **+7.0%** | **WIN** |
| `…T123645Z` | yap-first · spatial A (tracker off) | 2.608 | 2.766 | −6.0% | LOSS (order/cold) |
| `…T124116Z` | stock-first · **tracker on** | 2.821 | 2.174 | **+22.9%** | **WIN** |
| `…T124411Z` | **yap-first** · **tracker on** | 2.746 | 2.199 | **+19.9%** | **WIN** (no order bias) |

**Read:** Phase A alone wins stock-first; yap-first without tracker can lose to
offer/flush tax. With **spatial-tracker**, both orders win ~20%+ MSPT — product
gate for beating Paper under this density. Tracker remains players-on-main;
`moonrise$tick` / track-untrack stay on Paper main.

**Phase 3.9 (follow-on):** `spatial-tracker-skip-clean` + `ServerEntity` early-out
cut empty packet work on main (players) and spatial (mobs) without Folia-style
player tick. Rebuild YaP Paperclip after vendor hooks:
`./scripts/build-vendor-paper.sh`.

**Phase 3.11:** deepen YaP-owned Leaf-class opts — `PathNavigation.createPath`,
`Mob.serverAiStep` goal skip, `Brain.tick` sensor/behavior skip; distant brain
throttles only when **at least one player** is far (empty worlds / MSPT benches
full-tick so fuse/hopper proofs stay honest); tracker flush uses main for ≤12
offers (skip latch tax). Players / `moonrise$tick` stay on Paper main.

### Retest after updates — 2026-08-21 ~08:14

Fixed: empty-server distant brain was treating all entities as infinitely far
(interval throttle → fuseΔ≈45, FAIRNESS FAIL / false MSPT wins). Bench harness:
no stdin + dashboard off under `yap.bench.scenario`.

| Stamp | Gate | Notes | Verdict |
|-------|------|-------|---------|
| `…T151456Z` | vs Paper 1200 TNT / 256 hoppers | stock 2.707 → Yap 2.558 (**+5.5%**), fuseΔ=900 both | **WIN (fair)** |
| `…T150931Z` | denser eco 2400 TNT / 512 hoppers | Leaf 4.265 · **Yap 4.516 (#2, −5.9% vs Leaf)** · Paper 5.121 · Purpur 5.384; fuseΔ=900 all | Yap **#2/4** |

### JFR Leaf-gap slice — 2026-08-21 denser heavypop

Same load (2400 TNT / 512 hoppers). Profiles:
`bench/profiles/20260821T154415Z-heavypop-leaf.jfr`,
`bench/profiles/20260821T155727Z-heavypop-yapcore.jfr`.
Fair MSPT: Leaf **4.314** · Yap **4.386** (**−1.7%** — essentially tied).

| Side | What JFR shows |
|------|----------------|
| **Leaf heavier on main** | `tickNonPassenger`, `ChunkMap.newTrackerTick` / `tick`, entity forEach |
| **Yap heavier** | TNT collision (`checkInsideBlocks`, `BlockGetter` travel), `InteriorWorldTickBridge.tickNmsEntity` / `runTrackerSend`, `yapOfferTrackerSendChanges` |
| **Read** | Offload works (main tickNonPassenger nearly gone). Remaining gap is **collision density + tracker offer/flush tax**, not “Leaf magic skips alone”. Next slice: cheaper tracker barrier / less per-entity offer reflect. |

Harness: `YAP_BENCH_JFR=1 ./scripts/bench/run-vs-ecosystem.sh heavypop 45` →
`bench/profiles/<stamp>-heavypop-<id>.jfr`; summarize with `scripts/bench/summarize-jfr.sh`.

### Fair heavypop cite (classic heavy piles) — 2026-08-21 ~03:09

Hopper snapshot fixed to scan `HEAVY_PILES`. 15s warmup / 45s sample. FAIRNESS OK.

| Stamp | Density | Stock MSPT | YaP MSPT | Delta | Verdict |
|-------|---------|------------|----------|-------|---------|
| `…T100922Z` | **1200 TNT + 256 hoppers** (300/64) | 2.610 | 2.548 | **+2.4%** | **WIN** |

600/128 stock side failed again (port bind); YaP-only `…T101341Z` mspt≈4.78 fuse OK — not citeable without stock.

### Fair heavypop harden + 2× scale (2026-08-21 ~01:21–01:28)

Phase 3–3.7 spatial **on**. `max-tnt-per-tick: 0`. 15s warmup / **45s** sample.
All rows: fuseΔ=900 (=20×45), TNT/hoppers matched, **FAIRNESS OK**.

| Stamp | Order / density | Stock MSPT | YaP MSPT | Delta | Verdict |
|-------|-----------------|------------|----------|-------|---------|
| `…T082104Z` | stock-first · **1200 TNT + 256 hoppers** (300/64) | 2.551 | 2.238 | **+12.3%** | **WIN** |
| `…T082335Z` | **yap-first** · same 1200/256 | 2.395 | 2.188 | **+8.6%** | **WIN** (no order bias) |
| `…T082605Z` | stock-first · **2400 TNT + 512 hoppers** (600/128) | 4.924 | 4.154 | **+15.6%** | **WIN** (gap widens at 2×) |

Idle guard `…T082834Z`: stock 0.261 / YaP 0.300 (−14.7%) — **LOSS OK** (offer/flush tax).

**≥3600 TNT in one chunk/quad:** fuseΔ fails on **both** stock and YaP (~600–650 fully
ticking per chunk). Not a YaP bug. Bench uses deep-interior multi-pile spawn
(`HEAVY_PILES`, cap 600/chunk). A border-adjacent 3×3 halo made fuse fair but
regressed YaP MSPT badly (~27ms vs ~8ms) — do not use.

**JFR (YaP 2400 TNT win path, `…T085326Z`):** Server thread ~24% of samples; spatial
T3–T6 do PrimedTnt/collision. Main leftovers: **entity tracking / packets**
(`ServerEntity.sendChanges`, `SynchedEntityData`). Profile:
`bench/profiles/20260821T085326Z-heavypop-yap.jfr`.

**JFR (dense fair loss / halo, `…T092738Z`):** same tracker dominance; also
`tickNonPassenger` still on main for some paths. **Addressed (Phase 3.8):**
non-player `sendChanges` offloaded via `ChunkMap.newTrackerTick` +
`InteriorWorldTickBridge.flushTrackerSendChanges` — fair WIN ~+20% both orders
(`…T124116Z` / `…T124411Z`).

### Phase 3.6 all-on — light scenarios (2026-08-21 ~23:23)

Flags: all spatial world flags **true**. Light loads — **not** the product gate.

| Scenario | Stock MSPT | YaP MSPT | Delta | Verdict |
|----------|------------|----------|-------|---------|
| idle | 0.351 | 0.406 | −15.7% | LOSS (acceptable as guard) |
| entity (480 TNT) | 0.299 | 0.322 | −7.4% | LOSS (mid load) |
| farm | 0.284 | 0.303 | −6.8% | LOSS (mid load) |

Artifacts: `bench/results/20260821T062317Z-*`, `…T062458Z-*`, `…T062657Z-*`.

### Prior lean mid-load (world deferral mostly off)

| Scenario | Stock MSPT | YaP MSPT | Delta | Verdict |
|----------|------------|----------|-------|---------|
| idle | 0.247 | 0.245 | +1.0% | WIN |
| entity | 0.260 | 0.248 | +4.7% | WIN |
| farm | 0.248 | 0.241 | +2.9% | WIN |

### Ecosystem ceiling hunt — 2026-08-21 ~02:04 (still not the failure regime)

8000 TNT + 800 hoppers, near-spawn interiors, sim-distance 12. Absolute fuse proof
failed (shared ~189/600 drain ≈ **~2500 TNT ticking/tick** across all jars — likely
EAR / tick-cap, not YaP-specific). Directional MSPT only:

| Server | MSPT | Notes |
|--------|------|-------|
| YaPcore | 5.13 | Slightly ahead |
| Leaf | 5.30 | |
| Purpur | 6.55 | |
| Paper | 6.58 | |

**Still ~5ms — nowhere near single-thread failure (~30–50ms / TPS risk).**
Multithreading has not yet been tested in the regime that justifies it.
Next: force full entity tick (EAR off / uncapped TNT) until Paper/Leaf MSPT climbs hard.

### Ecosystem heavypop (fair mid-density + Phase 3.10) — 2026-08-21 ~07:00

Stamp `20260821T140008Z`. Same load: **1200 TNT + 256 hoppers**, fuseΔ=800 all sides.
YaP with Phase **3.10** (coalesce + EAR + distant-brain + tracker 3.8/3.9) **on**.

| Rank | Server | MSPT | vs Leaf | Fair |
|------|--------|------|---------|------|
| 1 | **Leaf 26.2** | **2.261** | — | OK |
| 2 | **YaPcore** | **2.501** | +10.7% | OK |
| 3 | Paper 26.2 | 2.529 | +11.9% | OK |
| 4 | Purpur 26.2 | 2.664 | +17.9% | OK |

**Read:** YaP still #2 (beats Paper/Purpur); Leaf still ahead at mid-density.
vs prior tracker-only stamp (`…T124719Z`, YaP **2.279**): this run is **not** a
clear 3.10 win — machine/order noise + TNT-heavy load (misc EAR=0) under-exercises
distant-brain/EAR. 3.10’s payoff remains denser / mob-AI regimes, not this gate.

### Ecosystem heavypop (fair mid-density + tracker) — 2026-08-21 ~05:47

Stamp `20260821T124719Z`. Same load: **1200 TNT + 256 hoppers**, fuseΔ=600 all sides.
YaP with Phase 3.8 spatial-tracker **on**.

| Rank | Server | MSPT | vs Leaf | Fair |
|------|--------|------|---------|------|
| 1 | **Leaf 26.2** | **2.109** | — | OK |
| 2 | **YaPcore** | **2.279** | +8.1% | OK |
| 3 | Purpur 26.2 | 2.553 | +21.1% | OK |
| 4 | Paper 26.2 | 2.588 | +22.7% | OK |

**Read:** YaP beats Paper/Purpur; Leaf still ahead at this mid-density. Do not claim
“faster than Leaf” yet — stretch gate remains denser / higher MSPT regime.

### Ecosystem heavypop (fair mid-density) — 2026-08-21 ~01:32

Same load: **1200 TNT + 256 hoppers**, `max-tnt-per-tick: 0`, fuseΔ=600 all sides,
view-distance=6. Stamp `20260821T083206Z`.

| Rank | Server | MSPT | vs Leaf | Fair |
|------|--------|------|---------|------|
| 1 | **Leaf 26.2** | **2.282** | — | OK |
| 2 | Paper 26.2 | 2.381 | +4.3% | OK |
| 3 | YaPcore Phase 3 | 2.416 | +5.9% | OK |
| 4 | Purpur 26.2 | 2.681 | +17.5% | OK |

**Read:** On this mid-density gate, Leaf wins; YaP is roughly with Paper (slightly behind),
ahead of Purpur. Spatial tick is not yet beating Leaf’s single-thread patches here —
do not claim “faster than Leaf/Purpur” from vibes. Re-run after denser load / more opts.

```bash
./scripts/bench/fetch-competitors.sh
YAP_BENCH_ENTITIES=300 YAP_BENCH_HOPPERS=64 ./scripts/bench/run-vs-ecosystem.sh heavypop 30
```

### Fair heavypop vs Paper only (load proofs + unlimited TNT) — 2026-08-21 ~01:02

**1200 primed TNT + 256 hoppers** (`YAP_BENCH_ENTITIES=300` `HOPPERS=64`),
`max-tnt-per-tick: 0`, force-loaded interiors; 15s warmup / 30s sample.
Both sides: `fuseΔ=600` (= 20×30), TNT 1200→1200, FAIRNESS OK.

| Scenario | Stock MSPT | YaP MSPT | Delta | TPS | Verdict |
|----------|------------|----------|-------|-----|---------|
| heavypop | **2.345** | **2.117** | **+9.7%** | 19.17 / 19.17 | **WIN (fair)** |

Artifacts: `bench/results/20260821T080238Z-heavypop-*`.

### heavypop (Phase 3.6 all-on + flush opts) — 2026-08-21 ~00:45 — **INVALID**

**4800 primed TNT + 1024 hoppers** claimed; 15s warmup / 45s sample.

| Scenario | Stock MSPT | YaP MSPT | Delta | Verdict |
|----------|------------|----------|-------|---------|
| heavypop | 1.078 | 0.898 | +16.7% | **INVALID — do not cite** |

Artifacts: `bench/results/20260821T074549Z-heavypop-*`.

**Why invalid (fairness audit 2026-08-21):**

1. **No load proofs** — JSON lacked `tnt_start` / `fuse_drop`; compare now returns exit 3.
2. **Spigot `max-tnt-per-tick` default = 100** — only ~100 TNT ticked per world tick, so
   “4800 TNT” was mostly idle entities. Stock fuse drain during warmup matched
   `100/4800 × ticks` (~6 fuse), not full tick-all.
3. **YaP fair re-run: `fuse_drop = 0`** — primed TNT fuse stayed at 12000 through
   warmup+sample → entities were **not ticking** on the spatial path (offer could
   fail while still skipping main-thread tick). That is gaming / a correctness bug,
   not a win.

**Fixes landed:** load proofs in bench plugin; `max-tnt-per-tick: 0` in bench
`spigot.yml`; offer returns boolean and only skips main tick on success; entity
flush no longer deferred behind BEs. See fair row above.

### Prior under-loaded heavypop (for contrast)

| Stamp | Stock | YaP | Verdict |
|-------|-------|-----|---------|
| `…T065154Z` (1120 TNT / 256 hoppers) | 0.301 | 0.427 | LOSS −42% |
| `…T074237Z` (2400 TNT / 640 hoppers, no force-load) | 0.334 | 0.472 | LOSS −41% |
