# YaP-Folia vs Folia forks — comparison

**YapLabs · 1 September 2026**  
Audience: operators choosing among Folia-line game jars.

> Product claim: **YaP-Folia** beats **stock Folia** on citeable spawncollapse MSPT with ship knobs. Peer Folia forks (Canvas) are on the harness; **no fresh citeable three-way** at this load yet. Kaiiju is not on 26.2.

Methods: [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md). Raw JSON: `bench/results/`.

---

## Who is who (Folia line only)

| Software | Lineage | 26.2 jar? | What it is |
|----------|---------|:---------:|------------|
| **Folia** (PaperMC) | Paper → Folia | ✓ | Upstream regionized multi-thread tick |
| **Canvas** | Folia fork | ✓ (`lib/canvas-26.2.jar`) | Peer Folia fork — performance / ops focus |
| **Kaiiju** | Folia fork | ✗ | Public releases stay on **1.20.x** — not on this board |
| **YaP-Folia** | Folia + YapLabs patches | ✓ (`lib/yap-folia-26.2.jar`) | **Product game jar** for YaPcore |
| **YaPcore** | YaP-Folia + YapEngine + YaP Link | — | Full product (game + chassis + proxy + natives) |

Paper / Purpur / Leaf are **single-tick** Paper-line — see [COMPARE_ECOSYSTEM.md](../overview/COMPARE_ECOSYSTEM.md). They are not Folia peers.

---

## Capability matrix

| Dimension | Stock Folia | Canvas | Kaiiju (1.20) | YaP-Folia | YaPcore |
|-----------|:-----------:|:------:|:-------------:|:---------:|:-------:|
| Regionized world tick | ✓ | ✓ | ✓ | ✓ | ✓ (via YaP-Folia) |
| Upstream-aligned 26.2 | ✓ | ✓ | ✗ | ✓ | ✓ |
| Entity tick budget (hot region) | ✗ | ~ | ? | ✓ opt-in | ✓ opt-in |
| Async chunk save | ✗ | ~ | ? | ✓ opt-in | ✓ opt-in |
| Microtick (same-thread AI slice) | ✗ | ✗ | ? | ✓ opt-in | ✓ opt-in |
| Force-partition / sub-regions | ✗ | ? | ? | ✓ opt-in | ✓ opt-in |
| Cross-shard neighbor defer | ✗ | ? | ? | ✓ with partition | ✓ |
| Teleport transactions | ✗ | ? | ? | ✓ default on | ✓ |
| Built-in JE+BE dual-stack | ✗ | ✗ | ✗ | ✗ | ✓ |
| First-party proxy | ✗ | ✗ | ✗ | ✗ | ✓ YaP Link |
| Shipped ops / DB / playerdata | ✗ | ✗ | ✗ | ✗ | ✓ |

`~` / `?` = fork may have related knobs; we do not claim their internals here.

---

## MSPT scoreboard (citeable)

**Scenario:** `spawncollapse` — single hot region, 8k TNT / 1024 hoppers / 2500 mobs (cite load), fair JVM + fuse proofs. Lower MSPT wins. All rows below: **fuse_ok=True**, chunks stable.

### Primary cite — stock Folia vs YaP-Folia (budget + async)

| Stamp | Stock Folia | YaP-Folia | Delta | Knobs |
|-------|------------:|----------:|------:|-------|
| `20260901T010804Z-budget` | **26.54 ms** | **20.54 ms** | **−22.6%** | budget=300, async-save |
| `20260901T075602Z-fullstack` (phase 1/3) | **30.33 ms** | **23.91 ms** | **−21.2%** | same ship profile |

**Ship defaults for that win:** `folia-entity-tick-budget=300`, `folia-async-chunk-save=true`. Partition / carve / microtick **off**.

### Full stack (same stamp `20260901T075602Z-fullstack`)

| Phase | Stock Folia | YaP-Folia | Delta | YaP knobs |
|-------|------------:|----------:|------:|-----------|
| 1 Budget + async | 30.33 | 23.91 | **−21.2%** | budget + async |
| 2 + microtick + partition (carve OFF, lobe) | 30.37 | 23.35 | **−23.1%** | + microtick 8ms + partition |
| 3 Contiguous carve + partition | 28.93 | 23.92 | **−17.3%** | carve ON (experimental) |

### Older cite

| Stamp | Stock Folia | YaP-Folia | Delta |
|-------|------------:|----------:|------:|
| `20260824T234919Z` | 25.25 | 21.45 | −15.0% |

### Canvas / Kaiiju on this board

| Peer | Status |
|------|--------|
| **Canvas @ 26.2** | Jar present; prior `heavypop` three-ways were **NOT CITEABLE** (MSPT ≪ 2 ms). **No fresh spawncollapse three-way** in `bench/results/` as of this date. |
| **Kaiiju** | Skipped — no public 26.2-class release. |

Reproduce Folia vs YaP (cite profile):

```bash
YAP_BENCH_SHUFFLE=0 YAP_BENCH_COMPETITORS=folia,yapfolia \
  YAP_BENCH_ENTITIES=8000 YAP_BENCH_HOPPERS=1024 YAP_BENCH_MOBS=2500 \
  YAP_FOLIA_ENTITY_TICK_BUDGET=300 YAP_FOLIA_ASYNC_CHUNK_SAVE=true \
  YAP_BENCH_GAME_XMS=4G YAP_BENCH_GAME_XMX=8G \
  ./scripts/bench/run-vs-folia.sh spawncollapse 40
```

Three-way when Canvas is included:

```bash
./scripts/bench/fetch-folia-forks.sh
YAP_BENCH_COMPETITORS=folia,canvas,yapfolia \
  # same cite load env as above
  ./scripts/bench/run-vs-folia.sh spawncollapse 40
```

---

## Where we stand (plain English)

1. **vs stock Folia** — Citeable win on hot-region spawncollapse with budget+async (**~21–23%** lower MSPT). That is the honest product MSPT claim today.
2. **vs Canvas** — Same Folia-class peer; we have the jar and harness, but **no citeable three-way at spawncollapse load yet**. Do not claim “faster than Canvas” until that row exists.
3. **vs Kaiiju** — Different Minecraft generation; not comparable on 26.2.
4. **Parallel sub-regions / microtick** — Implemented and fuse-stable in soaks; they are **optional**. Most of the measured win is still **entity tick budget** (+ async save). Carve+partition is experimental.
5. **Product vs jar** — YaPcore’s edge over DIY Folia/Canvas is not only MSPT: **chassis + YaP Link + dual-stack + shipped natives**. A bare Canvas/Folia deploy still needs Velocity/Geyser/plugins DIY.

---

## Decision snapshot

| Your priority | Pick |
|---------------|------|
| Upstream Folia jar only, DIY everything | **Stock Folia** |
| Folia fork peer at 26.2, DIY product stack | **Canvas** (or stay DIY Folia) |
| Folia on 1.20.x only | **Kaiiju** (legacy gen) |
| Citeable hot-region MSPT knobs + YaP patches | **YaP-Folia** |
| Regionized game + proxy + dual-stack + ops stack | **YaPcore** |

---

## Honesty rules

| Claim | Verdict |
|-------|---------|
| YaP-Folia always beats Folia on every workload | **False** — idle/low load can show chassis noise; cite spawncollapse |
| YaP-Folia beats Canvas on citeable spawncollapse | **Unproven** — run three-way first |
| YaPcore ships stock Folia | **False** — product jar is YaP-Folia |
| Kaiiju is on the 26.2 scoreboard | **False** |
| Parallel sub-regions alone explain the −21% win | **False** — budget is primary |

**Deck line:** *YaP-Folia is a Folia 26.2 fork with measured hot-region MSPT wins vs stock Folia; Canvas is a peer pending a citeable three-way; YaPcore wraps YaP-Folia with Link, chassis, and natives.*

---

## Related

- [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md) — fairness contract & full tables  
- [COMPARE_ECOSYSTEM.md](../overview/COMPARE_ECOSYSTEM.md) — Paper forks + Velocity too  
- [COMPARISON_BRIEF.md](../overview/COMPARISON_BRIEF.md) — partner brief  
- [FOLIA_FORK.md](FOLIA_FORK.md) — patches & build  

*Not affiliated with Mojang, Microsoft, PaperMC, or CanvasMC.*
