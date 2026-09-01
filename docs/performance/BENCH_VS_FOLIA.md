# MSPT scoreboard — YaP Folia chassis vs stock Folia + Folia forks (M5)

**Product gate:** `game-authority=folia` — Folia owns the game tick; YapEngine is
chassis only. **No** Phase 3 spatial tick. **No** `yap-spatial-tick.jar`.

**Gate rule:** Do not claim “faster than Folia” without a fresh row in
[`bench/results/`](../bench/results/) from
[`scripts/bench/run-vs-folia.sh`](../scripts/bench/run-vs-folia.sh).
M5 explicitly forbids day-one beat-Folia marketing — chassis overhead is expected
until Folia fork work lands.

**Peers:** stock **Folia** + **Canvas** (Folia fork @ 26.2) + **Paper / Purpur / Leaf** (Paper line @ 26.2).
**Kaiiju** is not on this board (public releases are 1.20.x). See `./scripts/bench/run-vs-all.sh` for one-shot full compare.

## Fairness contract (do not game results)

| Rule | Why |
|------|-----|
| **Same game JVM** | Every competitor’s **game** process uses `-Xms2G -Xmx4G` by default (`YAP_BENCH_GAME_XMS` / `YAP_BENCH_GAME_XMX`). YaP’s managed Folia child must match stock — not half the heap. |
| **YaP parent is minimal** | Chassis JVM uses `-Xms256m -Xmx512m` during benches so it does not steal RAM/CPU from the Folia child. |
| **Same spigot caps** | All sides get identical bench `spigot.yml` (`misc: 0`, `max-tnt-per-tick: 0`) so TNT/hoppers tick fairly — not a YaP-only tuning. |
| **Same seed & distances** | `level-seed=yap-bench-1`, view 10, sim 12 on every side. |
| **Load proofs** | TNT count, fuse drain (~20/tick×sample), hopper survival — compare rejects mismatches (exit 3). |
| **Shuffled order + cooldown** | Default shuffle + 5s cooldown between runs to reduce thermal/JIT bias. |
| **MSPT scope** | `measurement_scope=game_tick_mspt` — **Folia tick only**. YaP chassis Netty/web/I/O is **not** in MSPT; use `idle` scenario separately for overhead. |
| **Noise floor** | At MSPT &lt; 2 ms, **any** pairwise row is **NOT CITEABLE** (exit 4) — %-delta is meaningless at sub-millisecond scale. Increase load until `mspt_mean` ≥ ~2. |

## What we measure

| Scenario | Load | Role |
|----------|------|------|
| `heavypop` | Dense primed TNT + hoppers | **Primary Folia gate** (no bots) — increase entities/hoppers for citeable MSPT |
| `spawncollapse` | Single-region 3×3 chunks: TNT + hoppers + mobs | **Phase 3.1 gate** — stock Folia TPS collapse; entity-offload / hot-region target |
| `highpop` | Mineflayer bots + fixtures | Pop / network — see [BENCH_BOTS.md](BENCH_BOTS.md) |
| `fullcite` | Bots + entities + heavy hoppers | Full population cite — requires `players_ok: true` |
| `idle` | Empty-ish world | Regression — chassis overhead OK to lose slightly |

Same seed (`yap-bench-1`). Metrics: `Server.getAverageTickTime()` (MSPT) and TPS.

## Run

```bash
# Fetch Folia + Canvas once
./scripts/bench/fetch-folia-forks.sh

# Primary gate (folia + canvas + yapcore; default heavypop, 40s sample)
./scripts/bench/run-vs-folia.sh
./scripts/bench/run-vs-folia.sh heavypop 45

# Phase 3.1 — single-region overload (defaults: 800 TNT / 256 hoppers / 200 mobs)
./scripts/bench/run-vs-folia.sh spawncollapse 45
YAP_BENCH_ENTITIES=1200 YAP_BENCH_HOPPERS=384 YAP_BENCH_MOBS=400 \
  ./scripts/bench/run-vs-folia.sh spawncollapse 45

# Heavier load (citeable MSPT — tune until mspt_mean ≥ ~2 ms or compare exits 0/1 not 4)
YAP_BENCH_ENTITIES=600 YAP_BENCH_HOPPERS=128 ./scripts/bench/run-vs-folia.sh heavypop 45

# Subset
YAP_BENCH_COMPETITORS=folia,yapcore ./scripts/bench/run-vs-folia.sh heavypop 30

# Compare
python3 scripts/bench/compare-folia.py \
  bench/results/<stamp>-heavypop-folia.json \
  bench/results/<stamp>-heavypop-yapcore.json
python3 scripts/bench/compare-folia.py --rank \
  bench/results/<stamp>-spawncollapse-*.json
```

| Side | How it runs |
|------|-------------|
| **Stock Folia** | Direct `java -jar folia.jar` + `yap-mspt-bench` |
| **Canvas** | Direct `java -jar canvas.jar` + same bench plugin |
| **yapfolia** | Direct `java -jar yap-folia.jar` + same bench (Phase 3 knobs) |
| **yapcore** | YaPcore chassis + `folia-jar-source=build` managed YaP-Folia |

**MSPT sampling (Folia):** `getAverageTickTime()` is **region-local**. The bench
plugin samples on the hot region at chunk `(0,0)` via `YapSched.regionChunkTimer`
— not the near-empty global region.

## Results table (fill after runs)

### heavypop

| Stamp | Folia MSPT | Canvas MSPT | YaP Folia+chassis | Notes |
|-------|-----------:|------------:|------------------:|-------|
| `20260822T054201Z` | 0.0675 | 0.0863 | 0.0593 | **NOT CITEABLE** — fair harness (JVM 2G/4G, shuffle, 2400 TNT / 514 hoppers, fuse_ok); MSPT still &lt;2 ms |
| `20260822T053225Z` | 0.0742 | 0.0888 | 0.0710 | **NOT CITEABLE** — pre-fairness-JVM (YaP child 2G vs stock 4G); MSPT &lt;2 |
| `20260822T051035Z` | 0.0738 | 0.0809 | 0.0664 | **INVALID** — YaP fuse bug mid-stamp; do not use |

### spawncollapse (Phase 3.1+) — citeable

| Stamp | Folia MSPT | YaP-Folia plain | Delta | Notes |
|-------|-----------:|----------------:|------:|-------|
| `20260901T010804Z-budget` | **26.5446** | **20.5415** | **−22.6%** | **CITEABLE** — 8k TNT / 1024 hoppers / 2500 mobs; fuse_ok both; knobs: `entity-tick-budget=300`, `async-chunk-save=true`. Files: `bench/results/20260901T010804Z-budget-spawncollapse-{folia,yapfolia}.json` |
| `20260901T210712Z-speedtest` | **30.8734** | **22.8167** (chassis) / **23.6768** (plain) | **−26.1% / −23.3%** | **CITEABLE** — full ecosystem run (`run-vs-all.sh`): 7 peers, same 8k load; Canvas 30.42, Paper 31.32, Purpur 31.80, Leaf 25.12 (Paper-line). JSON: `bench/results/20260901T210712Z-speedtest-spawncollapse-*.json` |
| `20260901T075602Z-fullstack` (1/3) | **30.3333** | **23.9128** | **−21.2%** | Full-stack phase 1: budget+async; fuse_ok; chunks 819→819 |
| `20260901T075602Z-fullstack` (2/3) | **30.3671** | **23.3475** | **−23.1%** | + microtick 8ms + partition, carve OFF, lobe; fuse_ok |
| `20260901T075602Z-fullstack` (3/3) | **28.9300** | **23.9174** | **−17.3%** | Contiguous carve+partition; fuse_ok; experimental |
| `20260824T234919Z` | **25.2466** | **21.4509** | **−15.0%** | Prior citeable (same load profile). |

Folia-fork peers: see [FOLIA_FORKS_COMPARE.md](../folia/FOLIA_FORKS_COMPARE.md). Canvas has no citeable spawncollapse three-way yet; Kaiiju skipped on 26.2.

### highpop (Mineflayer bots) — citeable

| Stamp | Bots | Peer | Mode | Players | `players_ok` | MSPT mean | Notes |
|-------|-----:|------|------|--------:|:------------:|----------:|-------|
| `20260901T232100Z-bots100` | 100 | stock Folia | active | 100 / 100 | **true** | 10.87 | Post `patch-minecraft-data.sh`; 30s sample |
| `20260901T232700Z-bots200` | 200 | stock Folia | cite-stable | 200 / 200 | **true** | 14.04 | 2 workers; physics OFF |

### fullcite (100 bots + fixtures) — citeable

| Stamp | Peer | Players | `players_ok` | MSPT mean | Entities | Heavy hoppers |
|-------|------|--------:|:------------:|----------:|---------:|--------------:|
| `20260901T235600Z-fullcite` | stock Folia | 100 / 100 | **true** | 19.18 | 600 | 128 |
| `20260901T235600Z-fullcite` | yap-folia-chassis | 100 / 100 | **true** | 18.62 | 600 | 128 |
| `20260901T235600Z-fullcite` | stock Paper | 100 / 100 | **true** | 28.94 | 600 | 128 |

JSON: `bench/results/20260901T235600Z-fullcite-fullcite-{folia,yapcore,paper}.json`

Reproduce:

```bash
YAP_BENCH_STAMP=$(date -u +%Y%m%dT%H%M%SZ)-fullcite \
  YAP_BENCH_PLAYERS=100 YAP_BENCH_COMPETITORS=folia,yapcore,paper \
  NODE_OPTIONS="--max-old-space-size=8192" \
  ./scripts/bench/run-vs-folia.sh fullcite 40
```

**INVALID:** `20260901T210712Z-speedtest` fullcite (`players_end: 0`) — pre-fix bot protocol.

Reproduce 100-bot gate:

```bash
cd scripts/bench/bots && npm install
YAP_BENCH_STAMP=$(date -u +%Y%m%dT%H%M%SZ)-bots100 \
  YAP_BENCH_PLAYERS=100 YAP_BENCH_COMPETITORS=folia \
  ./scripts/bench/run-vs-folia.sh highpop 30
```

Full bot doc: [BENCH_BOTS.md](BENCH_BOTS.md).

### Parallel sub-regions + budget (P1)

| Stamp | Folia MSPT | YaP-Folia | Delta | fuse_ok | Notes |
|-------|-----------:|----------:|------:|:-------:|-------|
| `20260901T052718Z-fullNoCarve` | 4.15 | 3.71 | **−10.6%** | both | partition ON, carve OFF, two-phase lobe auto, budget=300 |

Reproduce P1:

```bash
YAP_BENCH_COMPETITORS=folia,yapfolia \
YAP_FOLIA_ENTITY_TICK_BUDGET=300 YAP_FOLIA_ASYNC_CHUNK_SAVE=true \
YAP_FOLIA_SUBREGION_PARTITION=true YAP_FOLIA_SUBREGION_CARVE=false \
./scripts/bench/run-vs-folia.sh spawncollapse 50
```

### Lobe parallel (partition OFF)

| Stamp | Folia MSPT | YaP-Folia | Delta | Notes |
|-------|-----------:|----------:|------:|-------|
| `20260901T050321Z-carveFair12` | 27.83 | 22.89 | **−17.7%** | two-phase lobe, budget=300, fuse_ok |

| `20260901T053935Z-p1` | 3.89 | 3.63 | **−6.8%** | both | P1: partition ON, carve OFF, lobe auto |

### Dynamic contiguous carve (P3)

| Stamp | Folia MSPT | YaP-Folia | fuse_ok (yap) | Notes |
|-------|-----------:|----------:|:-------------:|-------|
| `20260901T054520Z-p3` | 3.60 | 3.78 | **yes** | contiguous strip both sides, carve+partition, 819 chunks stable, fuse_drop=1001 |

MSPT delta not citeable at this load level; **stability gate passes** (first contiguous carve fuse_ok).

### Microtick A/B (P2)

| Stamp | microtick | MSPT | fuse_ok |
|-------|-----------|-----:|:-------:|
| `20260901T054230Z-p2a` | 0 | 2.53 | yes |
| `20260901T054353Z-p2b` | 8ms | 2.47 | yes |

Microtick adds marginal headroom at this load; use as optional tune layer.

Reproduce P3:

```bash
YAP_BENCH_CONTIGUOUS_CARVE=true \
YAP_FOLIA_SUBREGION_PARTITION=true YAP_FOLIA_SUBREGION_CARVE=true \
YAP_FOLIA_ENTITY_TICK_BUDGET=300 \
./scripts/bench/run-vs-folia.sh spawncollapse 50
```

Warmup auto-extends to 60s; partition delay 800 ticks.

Reproduce budget cite:

```bash
YAP_BENCH_SHUFFLE=0 YAP_BENCH_COMPETITORS=folia,yapfolia \
  YAP_BENCH_ENTITIES=8000 YAP_BENCH_HOPPERS=1024 YAP_BENCH_MOBS=2500 \
  YAP_FOLIA_ENTITY_TICK_BUDGET=300 \
  YAP_BENCH_GAME_XMS=4G YAP_BENCH_GAME_XMX=8G \
  YAP_BENCH_WARMUP=15 \
  ./scripts/bench/run-vs-folia.sh spawncollapse 40
python3 scripts/bench/compare-folia.py \
  bench/results/<stamp>-spawncollapse-folia.json \
  bench/results/<stamp>-spawncollapse-yapfolia.json
```

### Async save spike (Phase 3.3)

| Stamp | Stock save spike | YaP async-save spike | Notes |
|-------|-----------------:|---------------------:|-------|
| `20260825T014032Z` | **6.0712** | **5.8788** | **MISS ≤50% target** (ratio 96.8%). Smoke PASS; mid-sample `/save-all` (`-Dyap.bench.save_all_at=5`) under heavypop (600 TNT / 192 hoppers) did **not** produce a flush spike above the ~6 ms load floor on either side — region MSPT rolling average dilutes short I/O. YaP knobs: `-Dyap.folia.async-chunk-save=true`. Files: `bench/results/20260825T014032Z-asyncsave-{stock,yapfolia,compare}.json`. Follow-up: quieter baseline + dirtier region files, or wall-clock flush metric. |

Reproduce:

```bash
./scripts/smoke-folia-async-save.sh
# or SKIP_LIVE=1 for compile-only gate
```

**Do not** publish rankings from rows marked NOT CITEABLE or INVALID.

## Phase 3 product path (YaP-Folia)

Version branding: **YaP-Folia 26.2-yap.N**. Patches:
`vendor/folia/patches/0010`–`0012` (post-`applyAllPatches`). Build:
`./scripts/build-yap-folia.sh`. Verify: `./scripts/verify-yap-folia.sh`.

**Citeable claim (spawncollapse):** YaP-Folia with entity-tick-budget beats stock
Folia on region MSPT — see `20260824T234919Z` row above. Do not claim wins
without a fresh citeable JSON pair.

## If we lose

Honest chassis overhead vs stock Folia / Canvas remains possible on idle/low load.
**Spawn-collapse citeable win** is documented above (`20260824T234919Z`). Do not
revive Paper spatial tick as the product path.

## Related

- [COMPARE_ECOSYSTEM.md](../overview/COMPARE_ECOSYSTEM.md) — Folia vs YaP Folia section
- [FOLIA_FORK.md](../folia/FOLIA_FORK.md) · [FOLIA_FORK_AGENT_HANDOFF.md](../folia/FOLIA_FORK_AGENT_HANDOFF.md)
- [YAP_SCHED.md](../folia/YAP_SCHED.md) — first-party Folia scheduling
- [BENCH_BOTS.md](BENCH_BOTS.md) — Mineflayer swarm, 26.2 protocol patch, join results
- [PERF_AND_LAYOUT.md](PERF_AND_LAYOUT.md) — ≤500-line domain map
- Smoke: `./scripts/smoke-folia.sh` · `./scripts/smoke-folia-plugins.sh`
- Verify: `./scripts/verify-yap-folia.sh`
