# YaPcore vs Paper, Paper forks, Folia, and DIY Velocity

> **Commands below are retired.** Paper Phase 3 / Paperclip bench scripts were removed with the Folia product path. Prefer `./scripts/smoke-folia.sh`, `./scripts/bench/run-vs-folia.sh` (if present), and `./scripts/bench/fetch-competitors.sh`.


**YapLabs comparison brief · August 2026**  
Audience: operators choosing a Minecraft Java Edition server core.

> Short pitch: **Folia’s game + YapEngine’s slim chassis (edge/I/O) + YaP Link** — not a rename-only fork, and not “DIY Folia + Velocity.”

---

## How to read this

| Symbol | Meaning |
|--------|---------|
| ✓ | Strong / first-class |
| ~ | Partial, optional, or workload-dependent |
| ✗ | Not the model / not supported |

MSPT numbers for YaPcore vs stock Paper are from our public harness only
([BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)). **Fair highpop cite ~100 active bots.**
**250 keepalive holds = HOLD-ONLY**, not an MSPT win. Fork “performance” claims
elsewhere are community/lab reports — treat them as directional, not identical hardware.

---

## The field (who is who)

| Software | Lineage | What it actually is |
|----------|---------|---------------------|
| **Paper** | Spigot → PaperMC | Industry default JE core; single main tick thread; universal plugin target |
| **Purpur** | Paper (+ historically Pufferfish patches) | Drop-in Paper fork: huge gameplay/config surface |
| **Pufferfish** | Paper | Lean performance fork for high player / entity load |
| **Leaf** | Paper / Pufferfish-class | Newer high-count performance fork (newer = more variance) |
| **Folia** | PaperMC | Regionized multi-thread tick; Folia-aware plugin ABI (`RegionScheduler`, etc.) |
| **Folia forks** | Folia | Few mature public forks; most “Folia stacks” are Folia + careful plugin picks |
| **Velocity** | PaperMC | Network proxy; DIY with Folia backends is the common scale path |
| **YaPcore** | YapEngine chassis + **Folia as game** + **YaP Link** | **Folia regions** = world tick; chassis = edge/I/O (16 logical channels); Link = multi-backend; dual-stack; Folia-aware plugins; high-pop target. Legacy Paper + Phase 3 spatial = benches only |

Historical notes (merged / superseded, not separate picks today): Tuinity → Paper; Airplane → Pufferfish.

---

## One-page matrix

| Dimension | Paper | Purpur | Pufferfish | Leaf | Folia | DIY Folia+Velocity | YaPcore |
|-----------|:-----:|:------:|:----------:|:----:|:-----:|:------------------:|:-------:|
| Paper/Bukkit plugins | ✓ | ✓ | ✓ | ✓ | ✗† | ✗† | ~†† |
| Folia-aware plugins | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Extra gameplay knobs | ~ | ✓ | ~ | ~ | ~ | ~ | ✓¶ |
| Single main-thread tick | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ |
| Multi-thread world tick | ✗ | ✗ | ~ async helpers | ~ | ✓ regions | ✓ regions | ✓ Folia regions |
| Deterministic thread roles | ✗ | ✗ | ✗ | ✗ | ~ pools | ~ | ✓ 16 named roles |
| Built-in JE + BE dual-stack | ✗ | ✗ | ✗ | ✗ | ✗ | DIY | ✓ (Phase 4; join green, play depth deepening) |
| First-party proxy | ✗ | ✗ | ✗ | ✗ | ✗ | Velocity DIY | ✓ YaP Link (full fork) |
| YaP SYNC/HEAVY/UI pools | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ |
| Ops GUI / packs / crash tooling | ~ | ~ | ~ | ~ | ~ | ~ | ✓ product surface |
| Web dashboard (headless browser ops) | ~ | ~ | ~ | ~ | ~ | ~ | ✓ `:8080` |
| Shipped vehicles (non-minecart) | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ `yap-vehicles` |
| Shipped mob/item/spawner stacker | ✗††† | ~ plugins | ~ | ~ | ✗ | ✗ | ✓ `yap-stacker` (PDC, no NMS) |
| Shared MariaDB pool (one Hikari) | ✗ | ✗ | ✗ | ✗ | ✗ | DIY | ✓ `yap-db` |
| Offline `/login` + session lock + claims | ✗ | ~ plugins | ~ | ~ | ✗ | DIY | ✓ `yap-playerdata` |
| LuckPerms starter ranks + dashboard | ✗ | ~ DIY | ~ | ~ | ✗ | DIY | ✓ pack + `ranks apply` |
| Multi-active resource packs | ~ | ~ | ~ | ~ | ~ | ~ | ✓ core + `yap-packs` |
| Drop-in “just replace jar” | — | ✓ vs Paper | ✓ vs Paper | ~ | ✗ plugins | Product assemble | Product install / `assembleRelease` |

† Folia needs Folia-aware plugins; many Paper plugins break.  
†† Product path is Folia — Folia-aware jars yes; plain Paper plugins may break (same as stock Folia). Legacy `game-authority=paper` keeps Paper plugins for benches.  
¶ YaP: `config/` hub + `yap-gameplay-knobs` encyclopedia (API-level; not a Purpur GPL port). See [TUNE.md](TUNE.md).  
††† Stock Paper has no built-in stacker; operators use third-party plugins.

---

## Threading models (the real fork in the road)

```
Paper / Purpur / Pufferfish / Leaf
  └─ one main tick thread (+ some async helpers)
       good for: almost every plugin, most SMPs

Folia (alone)
  └─ many region threads (dynamic region ownership)
       good for: huge single-world concurrency
       cost: Folia-compatible plugins; DIY Velocity for multi-backend

DIY Folia + Velocity
  └─ Folia regions + separate Velocity JVM
       good for: networks that already assemble their own edge

YaPcore (YapEngine + Folia + YaP Link)
  └─ Folia owns the game (regions); YapEngine fixed 16 roles for chassis;
     YaP Link fronts backends (complete Velocity fork)
       plugins = Folia-aware on product path
       Phase 3 Paper spatial quads = legacy / opt-in benches only (no Phase 3 on Folia path)
```

| | Paper-class forks | Folia / DIY stack | YaPcore |
|--|-------------------|-------------------|---------|
| Parallelism style | Optimize the single tick | Shard by region | Folia regions + named chassis |
| Plugin story | Keep Paper plugins | Folia-aware | Folia-aware (product); Paper path legacy |
| Mental model | “Faster Paper jar” | “Different server + DIY proxy” | “Folia game + engine chassis + Link” |

YaPcore is **not** “better Folia” as a slogan. Folia’s bet is region pools and a Folia plugin contract — we **use that game** and add YapEngine + YaP Link + dual-stack + YaP plugins.

---

## Paper (baseline)

**Best for:** Almost every community server; plugin compatibility; boring reliability.

| Pros | Cons vs YaPcore |
|------|-----------------|
| Universal plugin target | Single main tick thread under load |
| Stable docs & ecosystem | No first-class dual-stack product |
| What everyone benchmarks against | No YapEngine chassis / Link; no Folia regions |

**YaPcore vs Paper (honest):** Default product uses **Folia** for chunks, mobs, redstone, commands.
We do **not** claim “faster everywhere.” The product is aimed at **high-pop /
heavy load**. Fair cites focus on **~100 active bots**. See
[BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md). Legacy Paper + Phase 3 spatial remains
for benches only ([BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)).

### Local MSPT scoreboard (product — Folia)

Primary gate: stock Folia vs YaP Folia chassis —

```bash
./scripts/bench/run-vs-folia.sh heavypop 40
```

Fill [BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md) after runs. No day-one beat-Folia claim.

### Legacy MSPT scoreboard (Paper + Phase 3)

Java 26, Paper 26.2 stock vs YaPcore **legacy Phase 3.6 all-on** (2026-08-21) —
Paper-path benches only:

| Scenario | Stock MSPT | YaP MSPT | Delta | Verdict |
|----------|------------|----------|-------|---------|
| **heavypop** (1120 TNT + 256 hoppers) | 0.301 | 0.427 | −42.1% | **LOSS** (legacy Paper spatial gate) |
| idle | 0.351 | 0.406 | −15.7% | LOSS (acceptable guard) |
| entity (480 TNT) | 0.299 | 0.322 | −7.4% | LOSS (mid load) |
| farm | 0.284 | 0.303 | −6.8% | LOSS (mid load) |

Reproduce: Paper Phase 3 benches **retired** — Folia product path only. Competitor jars: `./scripts/bench/fetch-competitors.sh`.

---

## Public MSPT scoreboard

Fair `heavypop` vs **Paper / Purpur / Leaf / YaPcore**:
[`scripts/bench/run-vs-ecosystem.sh`](.`(retired)`)
([BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)).

Latest fair mid-density (1200 TNT): **Leaf > Paper ≈ YaPcore > Purpur** on some
Paper-path cites. Product claim stays “Folia game + chassis + Link,” not “fastest fork.”
**250 keepalive = HOLD-ONLY.**

## Best Paper forks (still single-tick)

### Purpur

**Best for:** SMPs that want hundreds of gameplay/config toggles without writing plugins.

| | |
|--|--|
| Relation to Paper | Drop-in replacement; Paper API + extras |
| Performance | Usually inherits Pufferfish-class patches; gap vs Paper often small at low pop |
| vs YaPcore | Still one main tick thread; no Folia regions; no YapEngine / Link product edge |

Choose Purpur when you want **knobs**. Choose YaPcore when you want **Folia gameplay + engine chassis + Link + dual-stack**.

### Pufferfish

**Best for:** High concurrent / entity-heavy servers that need TPS headroom without Folia’s plugin tax.

| | |
|--|--|
| Relation to Paper | Performance-focused Paper fork |
| Typical wins | Entity/AI/pathfinding/async helpers under heavy load |
| vs YaPcore | Optimizes the classic loop; does not replace it with Folia regions + Yap chassis |

### Leaf

**Best for:** Large entity/player counts where teams will benchmark and accept newer-fork risk.

| | |
|--|--|
| Relation to Paper | Newer performance lineage (Pufferfish-class) |
| Caveat | Lab wins can reverse under real plugins; maintenance younger than Purpur/Pufferfish |
| vs YaPcore | Still Paper-shaped single-tick; YaP product path is Folia + chassis |

### When a Paper fork beats YaPcore

- You only need config knobs (Purpur) or a lean TPS patch set (Pufferfish/Leaf).
- You refuse Folia’s plugin ABI and want maximum “just Paper” familiarity.
- Your load is tiny (idle SMP) where every jar looks the same.

### When YaPcore beats a Paper fork

- You want **Folia regionized tick** plus a **product edge chassis** (Netty, dual-stack, I/O sandboxes) and **YaP Link**.
- You want **one product** for JE + Bedrock dual-stack, packs, GUI + web dashboard, vehicles, shared MariaDB / playerdata, ranks, crash tooling.
- You want **explicit chassis roles** (watchdog / traffic / bridge / I/O) instead of opaque jar patches — and are ready for Folia-aware plugins.

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
| **Proxy + many Folia backends** | Normal scale path — DIY Velocity, or **YaP Link** |

If someone sells you “the best Folia fork,” demand: plugin list, region metrics, and a migration plan. Most networks either run **stock Folia** carefully or **split worlds/backends**.

### YaPcore vs stock Folia / DIY Folia+Velocity

| | Stock Folia | DIY Folia + Velocity | YaPcore |
|--|-------------|----------------------|---------|
| Game | Folia regions | Folia regions | **Folia regions (default)** |
| Chassis | Folia pools | Folia pools | YapEngine **edge/I/O chassis** (16 logical channels) |
| Proxy | DIY | Velocity | **YaP Link** (complete Velocity fork; Velocity plugins load) |
| Dual-stack product path | DIY (Geyser etc.) | DIY | Built into product (join green; play depth deepening) |
| Phase 3 Paper spatial | N/A | N/A | Legacy / opt-in only — **not** on Folia path |
| Positioning | Game jar | Game + DIY edge | Folia game + Yap chassis + Link |

**Do** expect Folia-aware plugins on the product path. Legacy Paper authority still runs Paper plugins for benches.

---

## Feature deep-dive — YaPcore edge

| Area | YaPcore today |
|------|----------------|
| Product target | High-pop / heavy load (not empty lobbies) |
| Game authority | **Folia 26.2** (`game-authority=folia`, `folia-embed=true`) |
| Chassis | YapEngine **edge/I/O** (always on; **not** Folia game tick) |
| Phase 3 Paper spatial | **Defaults off** — legacy benches only; Folia path has **no** Phase 3 spatial tick |
| Fair highpop cite | **~100 active bots**; 250 keepalive = HOLD-ONLY |
| Proxy | **YaP Link** full fork — [YAP_LINK.md](YAP_LINK.md); stock Velocity optional stand-in |
| Plugins | One `plugins/` folder — Folia-aware + YaP (`yap-db`, playerdata, packs, chat, floodgate, vehicles, …) |
| Crossplay | JE TCP + BE UDP dual-stack — join/spawn green; play depth deepening (Phase 4) |
| Network SQL | Docker MariaDB + shared `yap-db` Hikari; playerdata auth/lock/claims/traders |
| Ranks | LuckPerms pack (`default`→`vip`→`mod`→`admin`) + dashboard Ranks tab |
| Ops | Config, multi-pack HTTP, control GUI, **web dashboard**, crash dumps, ZGC/NUMA, `assembleRelease` |
| Packs | Default `yapcore-default.zip` + multi-active extras |

Architecture sketch:

```
Clients (JE TCP / BE UDP)
        │
        ▼
 YaP Link (optional) :25565     ← complete Velocity fork
        │
 DualStackGateway + Traffic Cop (2)
        │
 Compatibility Bridge (9)
        │
 Folia regions (default game)   ← world / entity / redstone tick
   or legacy Paper + Phase 3    ← opt-in benches only
        │
 DLM (7) + Boundary (8)         ← Paper spatial path only
 UI (10–11) · Heavy I/O (12–15) · Telemetry (16)
 Controller (1)
```

---

## Decision guide

| Your situation | Prefer |
|----------------|--------|
| Small/medium SMP, max plugin compatibility, zero drama | **Paper** (or **Purpur** if you want knobs) |
| High pop, TPS dying, must keep classic Paper plugins | **Pufferfish** / **Leaf** *or* YaPcore **legacy Paper path** for benches |
| 100–500+ in one world, plugins already Folia-ready | **Folia** *or* **YaPcore** (Folia + chassis + Link) |
| Want Folia gameplay + engineered chassis + dual-stack + Link | **YaPcore** |
| Already happy assembling Folia + Velocity yourself | Stay DIY — or switch to product Link (same plugin ABI) |

---

## Summary

| Claim | Verdict |
|-------|---------|
| YaPcore is “Paper with a new name” | **False** — Folia is the default game; YapEngine is the chassis |
| YaPcore is “Folia but better” as a slogan | **False** — we **use Folia**; we add chassis + Link + dual-stack |
| YaPcore rejects Folia | **False** — Folia is the product game path |
| YaPcore keeps Folia-aware plugins | **True** (product path) |
| Phase 3 spatial is product default | **False** — retired; opt-in for Paper benches only |
| YaPcore beats stock Paper on every workload | **Not claimed** — fair cite ~100 active; 250 keepalive ≠ MSPT win |
| Purpur/Pufferfish/Leaf are still Paper-shaped | **True** — single-tick lineage with patches/config |
| Folia forks rival Purpur’s ecosystem | **False** — Folia itself is the main option |

**One sentence for decks:**  
*YaPcore runs **Folia’s game** on a **YapEngine edge/I/O chassis**, with YaP Link for multi-backend networks — Folia-aware plugins, dual-stack product path, and honest highpop cites (~100 active; no 250 keepalive MSPT marketing).*

---

## References (in-repo)

| Doc | Topic |
|-----|--------|
| [WHAT_WE_ARE.md](WHAT_WE_ARE.md) | Identity |
| [FULL_RUNDOWN.md](FULL_RUNDOWN.md) | Full product rundown |
| [YAP_LINK.md](YAP_LINK.md) | YaP Link proxy |
| [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md) | MSPT harness & results |
| [YAPENGINE_16THREAD.md](YAPENGINE_16THREAD.md) | Thread matrix |
| [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md) | Plugin compatibility |
| [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md) | Legacy Paper Phase 3 port plan |
| [whitepaper/YAPCORE_WHITEPAPER.md](whitepaper/YAPCORE_WHITEPAPER.md) | Technical whitepaper |

*Not affiliated with Mojang, Microsoft, PaperMC, PurpurMC, or Pufferfish.*
