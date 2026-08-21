# YaPcore — no-hyperbole comparison brief

**YapLabs · August 2026**  
Audience: operators and partners deciding where YaPcore fits.

Tone rule for this document: **state what is true today, what is in progress, and what we do not claim.** Numbers are from in-repo benches only ([BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)).

---

## 1. Where we stand

| Fact | Status |
|------|--------|
| Product identity | Paper for the game + YapEngine 16-thread chassis — not a rename-only Paper fork, not Folia |
| Game authority | Paper **26.2** (`game-authority=paper`) |
| Spatial tick (Phases 3–3.7+) | **Shipped, default on** — interior work on cores 3–6; border work on T8 under DLM |
| Paper / Spigot plugins | **Yes** — same `plugins/` folder; not Folia’s RegionScheduler ABI |
| Beat-Paper `heavypop` MSPT gate | **Won on fair denser cite** (`…T190712Z` Yap 4.114 vs Leaf 4.383; also beats Paper) — keep re-validating after engine changes |
| Mid-density ecosystem MSPT | Leaf ahead of Paper ≈ YaPcore; Purpur usually behind — **we do not claim “fastest fork”** |
| Dual-stack (JE + BE) | Product path in place; Phase 4 Via\* / Geyser **parity still in progress** (join/spawn smoke green) |
| Network stack shipped | `yap-db`, `yap-playerdata`, packs, chat, floodgate, vehicles, stacker, LuckPerms pack, web dashboard |

**One sentence:** YaPcore is a high-pop–oriented Minecraft Java server product that keeps Paper gameplay and plugins while moving interior tick work onto a fixed spatial chassis — with a dual-stack and network-ops surface that Paper forks leave as DIY.

**What we are not claiming today**

- Faster than Paper (or Leaf) on every workload
- “Folia but better”
- Full ViaVersion / Geyser feature parity finished
- Drop-in superiority for small idle SMPs

---

## 2. Why choose us (and where we are the better use case)

### Choose YaPcore when

| Need | Why YaP fits |
|------|----------------|
| Keep **Paper plugins**, want multi-thread interior tick | Spatial quads + DLM without Folia’s plugin rewrite |
| **One product** for JE + Bedrock join path | Built-in dual-stack roadmap; no “install Via + Geyser yourself” as the product story |
| Network ops without assembling ten plugins | Shared MariaDB (`yap-db`), offline auth / claims / traders (`yap-playerdata`), ranks pack, packs HTTP, web dashboard `:8080` |
| Explicit engine model | Named 16 roles (watchdog, traffic, NW/NE/SW/SE, DLM, bridge, UI, I/O, telemetry) — not opaque fork patches |
| High-pop / entity-heavy target | Product gate and defaults are aimed at busy worlds, not empty lobbies |

### Choose something else when

| Situation | Prefer |
|-----------|--------|
| Small / medium SMP, maximum boring reliability | **Paper** |
| Hundreds of gameplay config toggles | **Purpur** |
| Lean TPS patches, still classic single-tick Paper | **Pufferfish** / **Leaf** (benchmark your own load) |
| Huge single-world concurrency **and** Folia-ready plugins | **Folia** |
| Folia-only plugin pack | Stay on **Folia** — YaPcore will not run those APIs |

### Where we beat (or are a clearer fit)

| vs | Honest edge for YaP | Honest edge for them |
|----|---------------------|----------------------|
| **Paper** | Spatial chassis + dual-stack / network product surface | Simpler mental model; universal default; we still lose some MSPT benches |
| **Purpur / Pufferfish / Leaf** | Multi-thread interior tick + first-party crossplay/ops stack | Still “just replace the jar”; Leaf often wins mid-density MSPT today |
| **Folia** | Keep Paper plugins; fixed 16-role model; dual-stack product path | Higher single-world concurrency when Folia plugins exist |

---

## 3. Next-gen capabilities — ranked

Ranked by **how distinctive and shippable they are today** for someone evaluating YaPcore against the rest of the ecosystem. Maturity: **Shipped** / **Default on** / **In progress** / **Active gate**.

| Rank | Capability | Maturity | What it means in practice |
|:----:|------------|----------|---------------------------|
| **1** | YapEngine **16-thread chassis** + Phase **3–3.7+** spatial tick | Shipped · default on | Fixed NW/NE/SW/SE cores + DLM/border path; Paper remains game authority |
| **2** | **Paper plugin compatibility** on a multi-thread tick path | Shipped | Not Folia ABI; Paper jars + YaP (`yap.yml`) in one `plugins/` folder |
| **3** | **Network product stack** (DB, playerdata, ranks, packs, chat, floodgate) | Shipped | One Hikari pool, offline `/login`, claims/traders, LuckPerms pack, multi-pack HTTP |
| **4** | **Ops surface** (web dashboard, control GUI, crash tooling, release packaging) | Shipped | Headless browser ops on `:8080`; `assembleRelease` Linux/Windows |
| **5** | **Gameplay modules** (vehicles, stacker, gameplay knobs, pregen, PlaceholderAPI) | Shipped | First-party jars — not required to use, but part of the product box |
| **6** | **JE + Bedrock dual-stack** (first-party Via\* / Geyser parity) | In progress | Join/spawn green; play-depth checklist in [VIA_GEYSER_PARITY.md](VIA_GEYSER_PARITY.md) (P4.1–P4.11) |
| **7** | **Beat-Paper / Leaf denser `heavypop` MSPT** | **Won (cite)** | `…T190712Z` Yap 4.114 vs Leaf 4.383 (+6.5%); re-bench after engine changes |

### Capability fit (what each is *for*)

```
Chassis + spatial tick  →  busy worlds that outgrow one tick thread
Paper plugins           →  keep existing plugin investment
Network + ops stack     →  multi-server / headless / cross-platform ops
Dual-stack              →  one world story for JE + Bedrock (still polishing)
heavypop denser MSPT win → proof under real pressure (`…T190712Z` vs Leaf; keep validating)
```

---

## 4. Where we fit in the Minecraft ecosystem

```
Minecraft JE server cores (operator view)
│
├── Mojang vanilla          — reference rules; not the plugin ecosystem
│
├── Spigot / Paper          — industry default; single main tick thread
│     ├── Purpur            — Paper + large config / gameplay surface
│     ├── Pufferfish / Leaf — Paper + performance patches (still single-tick)
│     └── YaPcore ★         — Paper game + YapEngine chassis + product surface
│
└── Folia (+ rare forks)    — regionized multi-thread; new plugin contract
```

| Slot | YaPcore position |
|------|------------------|
| Ecosystem class | **Paper-compatible game core with a different threading chassis** |
| Not competing as | Fastest rename-fork · Folia replacement · vanilla drop-in |
| Closest neighbors | Paper forks for plugins; Folia for “multi-thread tick” — we sit between them on purpose |
| Client story | Modern JE target; Bedrock via product dual-stack (Phase 4) |
| Proxy story | Works in Velocity-class networks; Floodgate identity supported |

### Decision snapshot

| Your priority | Best default pick |
|---------------|-------------------|
| Compatibility and calm | Paper |
| Config knobs | Purpur |
| Single-thread TPS headroom | Pufferfish / Leaf |
| Folia plugins + region scale | Folia |
| Paper plugins + engineered multi-thread tick + dual-stack / network box | **YaPcore** |

---

## 5. Scoreboard context (no spin)

Same-machine public harness (see [COMPARE_ECOSYSTEM.md](COMPARE_ECOSYSTEM.md) / [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)):

| Claim | Verdict |
|-------|---------|
| YaPcore always beats Paper MSPT | **False** — cite denser fair `heavypop`; mid-density / idle can still lose |
| YaPcore is ahead of Leaf on mid-density MSPT | **Not claimed** — denser 2400/512 cite wins; mid-density Leaf often still leads |
| YaPcore is ahead of Leaf on denser heavypop | **True on `…T190712Z`** — re-validate after tracker/engine changes |
| YaPcore keeps Paper plugins | **True** |
| YaPcore is Folia | **False** |
| YaPcore is only branding on Paper | **False** — YapEngine chassis + Phase 3+ spatial tick + product stack |

**Deck line (accurate):**  
*YaPcore runs Paper’s game on YapEngine’s fixed 16-thread design for high-pop load — Paper plugins without Folia’s region ABI, a dual-stack product path, shipped network ops, and a public denser `heavypop` MSPT scoreboard we have won vs Leaf on the `…T190712Z` cite (re-validate after changes).*

---

## References

| Doc | Use |
|-----|-----|
| [COMPARE_ECOSYSTEM.md](COMPARE_ECOSYSTEM.md) | Full competitor matrix |
| [WHAT_WE_ARE.md](WHAT_WE_ARE.md) | Identity |
| [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md) | MSPT methods and results |
| [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md) | Dual-stack parity plan |

*Not affiliated with Mojang, Microsoft, PaperMC, PurpurMC, or Pufferfish.*
