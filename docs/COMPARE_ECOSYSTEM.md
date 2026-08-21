# YaPcore vs Paper, Paper forks, and Folia

**YapLabs comparison brief · August 2026**  
Audience: operators choosing a Minecraft Java Edition server core.

> Short pitch: **Paper’s game on YapEngine’s 16-thread chassis** — not a rename-only fork, and not Folia.

---

## How to read this

| Symbol | Meaning |
|--------|---------|
| ✓ | Strong / first-class |
| ~ | Partial, optional, or workload-dependent |
| ✗ | Not the model / not supported |

MSPT numbers for YaPcore vs stock Paper are from our public harness only
([BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)). **Product gate is `heavypop`** (high-pop /
heavy load) — not empty lobbies. Fork “performance” claims elsewhere are
community/lab reports — treat them as directional, not identical hardware.

---

## The field (who is who)

| Software | Lineage | What it actually is |
|----------|---------|---------------------|
| **Paper** | Spigot → PaperMC | Industry default JE core; single main tick thread; universal plugin target |
| **Purpur** | Paper (+ historically Pufferfish patches) | Drop-in Paper fork: huge gameplay/config surface |
| **Pufferfish** | Paper | Lean performance fork for high player / entity load |
| **Leaf** | Paper / Pufferfish-class | Newer high-count performance fork (newer = more variance) |
| **Folia** | PaperMC | Regionized multi-thread tick; **new plugin ABI** (`RegionScheduler`, etc.) |
| **Folia forks** | Folia | Few mature public forks; most “Folia stacks” are Folia + careful plugin picks |
| **YaPcore** | YapEngine + **Paper as game** | Fixed **16-thread** chassis; Phase 3–3.7 spatial tick (default on); dual-stack path; Paper plugins; high-pop target |

Historical notes (merged / superseded, not separate picks today): Tuinity → Paper; Airplane → Pufferfish.

---

## One-page matrix

| Dimension | Paper | Purpur | Pufferfish | Leaf | Folia | YaPcore |
|-----------|:-----:|:------:|:----------:|:----:|:-----:|:-------:|
| Paper/Bukkit plugins | ✓ | ✓ | ✓ | ✓ | ✗† | ✓ |
| Folia-only plugins | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ |
| Extra gameplay knobs | ~ | ✓ | ~ | ~ | ~ | ✓¶ |
…
¶ YaP: Paper configs via `config/` hub + `yap-gameplay-knobs` encyclopedia (API-level; not a Purpur GPL port). See [TUNE.md](TUNE.md).
| Single main-thread tick | ✓ | ✓ | ✓ | ✓ | ✗ | ✗§ |
| Multi-thread world tick | ✗ | ✗ | ~ async helpers | ~ | ✓ regions | ✓ fixed quads 3–6 |
| Deterministic thread roles | ✗ | ✗ | ✗ | ✗ | ~ pools | ✓ 16 named roles |
| Built-in JE + BE dual-stack | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ (Phase 4 polish) |
| YaP SYNC/HEAVY/UI pools | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ |
| Ops GUI / packs / crash tooling | ~ | ~ | ~ | ~ | ~ | ✓ product surface |
| Web dashboard (headless browser ops) | ~ | ~ | ~ | ~ | ~ | ✓ `:8080` |
| Shipped vehicles (non-minecart) | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ `yap-vehicles` |
| Shipped mob/item/spawner stacker | ✗†† | ~ plugins | ~ | ~ | ✗ | ✓ `yap-stacker` (PDC, no NMS) |
| Drop-in “just replace jar” | — | ✓ vs Paper | ✓ vs Paper | ~ | ✗ plugins | Product install / `assembleRelease` |

† Folia needs Folia-aware plugins; many Paper plugins break.  
‡ YaP adds product features (dual-stack, pools, chassis); not a GPL Purpur fork.
¶ YaP: `config/` hub + `yap-gameplay-knobs` encyclopedia (Paper API; see [TUNE.md](TUNE.md)).  
§ Players still coordinate with Paper main; interior tick on cores 3–6 and border entity/TE/events on T8 under DLM leases.  
†† Stock Paper has no built-in stacker; operators use third-party plugins.

---

## Threading models (the real fork in the road)

```
Paper / Purpur / Pufferfish / Leaf
  └─ one main tick thread (+ some async helpers)
       good for: almost every plugin, most SMPs

Folia
  └─ many region threads (dynamic region ownership)
       good for: huge single-world concurrency
       cost: rewrite / Folia-compatible plugins

YaPcore (YapEngine)
  └─ fixed 16 roles: watchdog, traffic, 4 spatial cores,
     DLM + boundary, compat bridge, UI, I/O, telemetry
       game = Paper; Phase 3–3.7 leases interior + border work (flags default on)
       plugins = Paper path (not Folia RegionScheduler)
```

| | Paper-class forks | Folia | YaPcore |
|--|-------------------|-------|---------|
| Parallelism style | Optimize the single tick | Shard by region | Fixed spatial quads + leases |
| Plugin story | Keep Paper plugins | New schedulers / APIs | Keep Paper plugins |
| Mental model | “Faster Paper jar” | “Different server” | “Paper game + engine chassis” |

YaPcore is **not** “better Folia.” Folia’s bet is region pools and a new plugin contract. Ours is a **named 16-thread matrix** with Paper remaining the game authority.

---

## Paper (baseline)

**Best for:** Almost every community server; plugin compatibility; boring reliability.

| Pros | Cons vs YaPcore |
|------|-----------------|
| Universal plugin target | Single main tick thread under load |
| Stable docs & ecosystem | No first-class dual-stack product |
| What everyone benchmarks against | No YapEngine chassis / Phase 3 spatial tick |

**YaPcore vs Paper (honest):** We **use** Paper for chunks, mobs, redstone, commands.
We do **not** claim “faster everywhere.” The product is aimed at **high-pop /
heavy load**. The public beat-Paper gate is **`heavypop`** — currently still a
**LOSS** with full spatial deferral on (flush overhead). Light idle may lose MSPT;
that is acceptable. See [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md).

### Local MSPT scoreboard (same machine, fair workdirs)

Java 26, Paper 26.2 stock vs YaPcore **Phase 3.6 all-on** (2026-08-21):

| Scenario | Stock MSPT | YaP MSPT | Delta | Verdict |
|----------|------------|----------|-------|---------|
| **heavypop** (1120 TNT + 256 hoppers) | 0.301 | 0.427 | −42.1% | **LOSS** (product gate) |
| idle | 0.351 | 0.406 | −15.7% | LOSS (acceptable guard) |
| entity (480 TNT) | 0.299 | 0.322 | −7.4% | LOSS (mid load) |
| farm | 0.284 | 0.303 | −6.8% | LOSS (mid load) |

Prior lean mid-load (world deferral mostly off) had idle/entity/farm WINs — not
the shipping high-pop config. Reproduce: `./scripts/bench/run-vs-paper.sh heavypop 40`.

---

## Public MSPT scoreboard

Fair `heavypop` vs **Paper / Purpur / Leaf / YaPcore**:
[`scripts/bench/run-vs-ecosystem.sh`](../scripts/bench/run-vs-ecosystem.sh)
([BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)).

Latest fair mid-density (1200 TNT): **Leaf > Paper ≈ YaPcore > Purpur**. YaP is not
ahead of Leaf on that gate yet — product claim stays “Paper plugins + spatial chassis,”
not “fastest fork.”

## Best Paper forks (still single-tick)

### Purpur

**Best for:** SMPs that want hundreds of gameplay/config toggles without writing plugins.

| | |
|--|--|
| Relation to Paper | Drop-in replacement; Paper API + extras |
| Performance | Usually inherits Pufferfish-class patches; gap vs Paper often small at low pop |
| vs YaPcore | Still one main tick thread; no YapEngine 16-role chassis; no built-in JE/BE dual-stack product |

Choose Purpur when you want **knobs**. Choose YaPcore when you want **engine threading + product edge** on top of Paper’s game.

### Pufferfish

**Best for:** High concurrent / entity-heavy servers that need TPS headroom without Folia’s plugin tax.

| | |
|--|--|
| Relation to Paper | Performance-focused Paper fork |
| Typical wins | Entity/AI/pathfinding/async helpers under heavy load |
| YaP answer | Phase 3.10 **first-party** `YapDistantBrain` + Paper EAR on spatial cores — not a Leaf patch port |
| vs YaPcore | Optimizes the classic loop; does not replace it with a fixed spatial thread matrix |

### Leaf

**Best for:** Large entity/player counts where teams will benchmark and accept newer-fork risk.

| | |
|--|--|
| Relation to Paper | Newer performance lineage (Pufferfish-class) |
| Caveat | Lab wins can reverse under real plugins; maintenance younger than Purpur/Pufferfish |
| vs YaPcore | Same class as other Paper forks: still Paper-shaped, not YapEngine |

### When a Paper fork beats YaPcore

- You only need config knobs (Purpur) or a lean TPS patch set (Pufferfish/Leaf).
- You refuse any non-stock tick path and want maximum “just Paper” familiarity.
- Your load is tiny (idle SMP) where every jar looks the same.

### When YaPcore beats a Paper fork

- You want **multi-threaded interior tick** without Folia plugin breakage.
- You want **one product** for JE + Bedrock dual-stack, packs, GUI + web dashboard, vehicles, crash tooling.
- You want **explicit thread roles** (watchdog / traffic / quads / DLM / bridge / I/O) instead of opaque jar patches.

---

## Folia and Folia-class stacks

### Folia (PaperMC)

**Best for:** Very large single-world concurrency where one main thread is the hard limit **and** your plugins support Folia.

| | |
|--|--|
| Model | Regionized multithreading |
| Plugins | Many Paper plugins **do not** work; need Folia APIs |
| Ops reality | Harder plugin matrix; huge win when it fits |

### “Best Folia forks”

Unlike Paper, Folia does **not** have a deep, stable public fork ecosystem comparable to Purpur/Pufferfish. In practice the shortlist is:

| Option | Reality check |
|--------|----------------|
| **Upstream Folia** | The default Folia choice; track PaperMC |
| **Niche / host-specific Folia builds** | Exist, but rarely a portable “everyone runs this” standard |
| **Proxy + many Paper backends** | Often the pragmatic alternative to Folia for scale |

If someone sells you “the best Folia fork,” demand: plugin list, region metrics, and a migration plan. Most networks either run **stock Folia** carefully or **split worlds/backends** on Paper-class jars.

### YaPcore vs Folia

| | Folia | YaPcore |
|--|-------|---------|
| Parallel tick | Dynamic regions | Fixed NW/NE/SW/SE cores 3–6 + leases |
| Paper plugins | Often break | **Yes** (stock Paper path) |
| Folia RegionScheduler plugins | Yes | **No** |
| Dual-stack product path | DIY (Geyser etc.) | Built into product roadmap |
| Positioning | Different server ABI | Paper game + YapEngine chassis |

**Do not** migrate Folia-only plugins to YaPcore expecting them to run.

---

## Feature deep-dive — YaPcore edge

| Area | YaPcore today |
|------|----------------|
| Product target | High-pop / heavy load (not empty lobbies) |
| Game authority | Paper 26.2 (`game-authority=paper`) |
| Chassis | YapEngine 16 threads always on |
| Phase 3 | Interior entity tick on cores 3–6 (DLM) |
| Phase 3.5–3.6 | Interior block/fluid/random + TE/redstone (**default on**) |
| Phase 3.7 | Border entities/TE/events on T8 (DLM, **default on**) |
| Beat-Paper gate | `heavypop` MSPT — not yet won |
| Plugins | One `plugins/` folder — Paper jars + YaP `yap.yml` (vehicles + knobs shipped) |
| Crossplay | JE TCP + BE UDP dual-stack (Phase 4 polish) |
| Ops | Config, resource-pack HTTP, control GUI, **web dashboard**, crash dumps, ZGC/NUMA, `assembleRelease` |
| Packs | Default `yapcore-default.zip` (Faithful 64x + YaP Vehicles) |

Architecture sketch:

```
Clients (JE TCP / BE UDP)
        │
        ▼
 DualStackGateway + Traffic Cop (2)
        │
        ▼
 Compatibility Bridge (9)
        │
        ▼
 Spatial cores 3–6 (NW/NE/SW/SE)   ← Phase 3–3.6 leased tick
        │
 DLM (7) + Boundary (8)            ← Phase 3.7 border tick on T8
 UI (10–11) · Heavy I/O (12–15) · Telemetry (16)
 Controller (1)
```

---

## Decision guide

| Your situation | Prefer |
|----------------|--------|
| Small/medium SMP, max plugin compatibility, zero drama | **Paper** (or **Purpur** if you want knobs) |
| High pop, TPS dying, must keep Paper plugins | **Pufferfish** / **Leaf** *or* evaluate **YaPcore** on `heavypop` benches |
| 100–500+ in one world, plugins already Folia-ready | **Folia** |
| Want Paper gameplay + engineered multi-thread tick + dual-stack product | **YaPcore** |
| Folia-only plugin pack | Stay on **Folia** — not YaPcore |

---

## Summary

| Claim | Verdict |
|-------|---------|
| YaPcore is “Paper with a new name” | **False** — Paper is the game; YapEngine is the chassis |
| YaPcore is “Folia but better” | **False** — different threading + plugin contract |
| YaPcore keeps Paper plugins | **True** (not Folia-only APIs) |
| YaPcore beats stock Paper on every workload | **Not claimed** — `heavypop` gate not yet won; light idle may lose |
| Purpur/Pufferfish/Leaf are still Paper-shaped | **True** — single-tick lineage with patches/config |
| Folia forks rival Purpur’s ecosystem | **False** — Folia itself is the main option |

**One sentence for decks:**  
*YaPcore runs Paper’s game on YapEngine’s fixed 16-thread design for high-pop load — Paper plugin compatibility without Folia’s region ABI, dual-stack product path, and a public `heavypop` MSPT scoreboard against stock Paper.*

---

## References (in-repo)

| Doc | Topic |
|-----|--------|
| [WHAT_WE_ARE.md](WHAT_WE_ARE.md) | Identity |
| [FULL_RUNDOWN.md](FULL_RUNDOWN.md) | Full product rundown |
| [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md) | MSPT harness & results |
| [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md) | Thread matrix |
| [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md) | Plugin compatibility |
| [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md) | Port / phase plan |
| [whitepaper/YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) | Technical whitepaper |

*Not affiliated with Mojang, Microsoft, PaperMC, PurpurMC, or Pufferfish.*
