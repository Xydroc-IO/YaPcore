# YaPcore vs Paper, Paper forks, Folia, and DIY Velocity

**YapLabs comparison · September 2026**  
Audience: operators choosing a Minecraft Java Edition server core.

Folia-line MSPT vs peers: [FOLIA_FORKS_COMPARE.md](../folia/FOLIA_FORKS_COMPARE.md).

> Short pitch: **YaP-Folia’s game + YapEngine’s slim chassis + YaP Link** — not stock Folia, and not “DIY Folia + Velocity.”

Product MSPT gate: `./scripts/smoke-folia.sh`, `./scripts/bench/run-vs-folia.sh`, [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md).

---

## How to read this

| Symbol | Meaning |
|--------|---------|
| ✓ | Strong / first-class |
| ~ | Partial, optional, or workload-dependent |
| ✗ | Not the model / not supported |

Fair population cite: **fullcite** (100 active bots + fixtures). **Citeable win:** yapcore −5.8% vs stock Folia with ship knobs (`20260902T005200Z-fullcite-knobs2`). **highpop** at 100 bots = valid join + **tie** (−4.2%). **250 keepalive = HOLD-ONLY.**

---

## The field (who is who)

| Software | Lineage | What it actually is |
|----------|---------|---------------------|
| **Paper** | Spigot → PaperMC | Industry default JE core; single main tick thread |
| **Purpur** | Paper | Drop-in Paper fork: large gameplay/config surface |
| **Pufferfish** | Paper | Lean performance fork for high player / entity load |
| **Leaf** | Paper / Pufferfish-class | Newer high-count performance fork |
| **Folia** | PaperMC | Upstream regionized multi-thread tick |
| **Canvas** | Folia fork | Peer Folia fork @ 26.2 — DIY product stack |
| **Kaiiju** | Folia fork | Public releases **1.20.x** only — not on 26.2 board |
| **YaP-Folia** | Folia + YapLabs patches | **Product game jar** — `lib/yap-folia-26.2.jar` |
| **Velocity** | PaperMC | Network proxy; DIY with Folia backends is common |
| **YaPcore** | YapEngine + **YaP-Folia** + **YaP Link** | Forked regionized game + chassis + Link + dual-stack + YaP plugins |

---

## One-page matrix

| Dimension | Paper | Purpur | Pufferfish | Leaf | Folia | DIY Folia+Velocity | YaPcore |
|-----------|:-----:|:------:|:----------:|:----:|:-----:|:------------------:|:-------:|
| Paper/Bukkit plugins | ✓ | ✓ | ✓ | ✓ | ✗† | ✗† | ~†† |
| Folia-aware plugins | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Extra gameplay knobs | ~ | ✓ | ~ | ~ | ~ | ~ | ✓¶ |
| Single main-thread tick | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ |
| Multi-thread world tick | ✗ | ✗ | ~ | ~ | ✓ regions | ✓ regions | ✓ **YaP-Folia** |
| Deterministic edge roles | ✗ | ✗ | ✗ | ✗ | ~ | ~ | ✓ slim chassis |
| Built-in JE + BE dual-stack | ✗ | ✗ | ✗ | ✗ | ✗ | DIY | ✓ |
| First-party proxy | ✗ | ✗ | ✗ | ✗ | ✗ | Velocity DIY | ✓ YaP Link |
| Patched game fork | ✗ | ✓ (Paper) | ✓ | ✓ | ✗ | ✗ | ✓ **YaP-Folia** |
| Ops GUI / web dashboard | ~ | ~ | ~ | ~ | ~ | ~ | ✓ `:8080` |
| Shipped vehicles / stacker | ✗ | ~ plugins | ~ | ~ | ✗ | ✗ | ✓ GAMEPLAY opt-in |
| Shared MariaDB pool | ✗ | ✗ | ✗ | ✗ | ✗ | DIY | ✓ `yap-db` |
| Offline `/login` + claims | ✗ | ~ plugins | ~ | ~ | ✗ | DIY | ✓ `yap-playerdata` |

† Folia needs Folia-aware plugins.  
†† Product path is YaP-Folia — Folia-aware jars yes; plain Paper plugins may break.  
¶ `yap-gameplay-knobs` + config hub — [TUNE.md](../ops/TUNE.md).

---

## Threading models

```
Paper / Purpur / Pufferfish / Leaf
  └─ one main tick thread (+ some async helpers)

Upstream Folia
  └─ many region threads

DIY Folia + Velocity
  └─ Folia regions + separate Velocity JVM

YaPcore (YapEngine + YaP-Folia + YaP Link)
  └─ YaP-Folia owns the game (regions + YaP patches);
     YapEngine slim edge/I/O chassis;
     YaP Link fronts backends
```

| | Paper-class forks | Upstream Folia / DIY | YaPcore |
|--|-------------------|----------------------|---------|
| Parallelism | Optimize single tick | Shard by region | YaP-Folia regions + named chassis |
| Plugin story | Keep Paper plugins | Folia-aware | Folia-aware + YaP natives |
| Mental model | “Faster Paper jar” | “Different server + DIY proxy” | “YaP-Folia + chassis + Link” |

YaPcore is **not** “stock Folia with branding.” We **fork Folia**, patch it, and wrap it with chassis + Link + dual-stack + YaP plugins. See [FOLIA_FORK.md](../folia/FOLIA_FORK.md).

---

## Paper (baseline)

**Best for:** Almost every community server; plugin compatibility; boring reliability.

**YaPcore vs Paper (honest):** Default product uses **YaP-Folia** for chunks, mobs, redstone, commands. We do **not** claim “faster everywhere.” Population cite uses **fullcite** (100 bots + fixtures); Paper was ~55% higher MSPT than Folia on that load — architecture mismatch, not a Folia marketing claim.

### Local MSPT scoreboard (product — YaP-Folia)

```bash
./scripts/bench/run-vs-folia.sh heavypop 40
./scripts/bench/run-vs-folia.sh spawncollapse 45
```

| Workstream | Status |
|------------|--------|
| `spawncollapse` cite | **Win** `20260901T010804Z-budget` — stock 26.54 → YaP 20.54 (**−22.6%**); reconfirmed `20260901T075602Z-fullstack` (**−21.2%**) with budget=300 + async-save |
| vs Canvas / Kaiiju | Canvas peer jar present — **no citeable three-way yet**; Kaiiju not on 26.2 — [FOLIA_FORKS_COMPARE.md](../folia/FOLIA_FORKS_COMPARE.md) |
| Hot-region entity budget | `0012-yap-entity-tick-budget.patch` |
| Async chunk save | `0010-yap-async-chunk-save.patch` |
| Scoreboard SWMR | `0011-yap-scoreboard-swmr.patch` |
| Region pool / microtick | `0013` |
| Subregion partition | `0014`–`0019` (opt-in; carve experimental) |

### Legacy Paper + Phase 3 MSPT

Retired as product gate. Some mid-density Paper-path cites still show Leaf ahead. Product claim stays “YaP-Folia + chassis + Link,” not “fastest Paper fork.”

---

## Best Paper forks (still single-tick)

### Purpur / Pufferfish / Leaf

Still one main tick thread. Choose them for knobs or classic TPS patches. Choose YaPcore for **YaP-Folia gameplay + chassis + Link + dual-stack**.

### When a Paper fork beats YaPcore

- You only need config knobs or a lean TPS patch set.
- You refuse Folia’s plugin ABI.
- Your load is tiny (idle SMP).

### When YaPcore beats a Paper fork

- You want **regionized tick** plus a **product edge chassis** and **YaP Link**.
- You want **one product** for JE + Bedrock, packs, dashboard, DB/playerdata, ranks.
- You want **explicit chassis roles** and Folia-aware / YaP plugins.

---

## Folia and Folia-class stacks

### Upstream Folia (PaperMC)

**Best for:** Large single-world concurrency when your plugins already support Folia and you will DIY the rest.

### YaP-Folia (YapLabs)

| | |
|--|--|
| Model | Folia regions + Yap patches (teleport TX, optional budgets/partition, branding) |
| Default | `folia-jar-source=build` → `lib/yap-folia-26.2.jar` |
| Docs | [FOLIA_FORK.md](../folia/FOLIA_FORK.md) |

### YaPcore vs stock Folia / DIY Folia+Velocity

| | Stock Folia | DIY Folia + Velocity | YaPcore |
|--|-------------|----------------------|---------|
| Game jar | Upstream | Upstream | **YaP-Folia** |
| Edge / dual-stack | DIY | DIY | **YapEngine chassis** |
| Proxy | DIY | Velocity | **YaP Link** |
| Plugin / ops stack | DIY | DIY | **Shipped YaP natives** |

---

## Velocity and proxies

| | Velocity DIY | YaP Link |
|--|--------------|----------|
| Implementation | PaperMC Velocity | First-party Netty (`0.6.0-phase6`) |
| Product default | No | Yes for multi-backend |
| Link plugins | Velocity plugins | `yap-link-api` + shipped bridges |

Stock Velocity remains a valid stand-in. See [YAP_LINK.md](../network/YAP_LINK.md) · [YAP_LINK_NATIVE.md](../network/YAP_LINK_NATIVE.md).

---

## Decision tree (short)

1. Need classic Paper plugins only? → **Paper** (or Purpur knobs).  
2. Need single-tick TPS patches only? → **Pufferfish / Leaf**.  
3. Need upstream Folia jar only and will DIY everything? → **Folia**.  
4. Need regionized game **plus** chassis, Link, dual-stack, and shipped natives? → **YaPcore (YaP-Folia)**.

---

## Scoreboard honesty

| Claim | Verdict |
|-------|---------|
| YaPcore always beats Paper MSPT | False |
| Product game is stock Folia | **False** — **YaP-Folia** |
| YaPcore is Folia lineage | **True** — we fork Folia |
| 16-thread game tick | False — regions + chassis |
| Link replaces every Velocity deploy overnight | False — optional stand-in remains |

**Deck line:** *YaPcore = YaP-Folia game + YapEngine chassis + YaP Link + shipped YaP stack — not stock Folia, not Paper + ten jars.*

---

## Related

- [YAPCORE_MASTER.md](YAPCORE_MASTER.md) — identity and status
- [FOLIA_FORK.md](../folia/FOLIA_FORK.md) — patches & build
- [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md) — MSPT methods

*Not affiliated with Mojang, Microsoft, PaperMC, PurpurMC, or Pufferfish.*
