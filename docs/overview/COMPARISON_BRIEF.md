# YaPcore — no-hyperbole comparison brief

**YapLabs · August 2026**  
Audience: operators and partners deciding where YaPcore fits.

Tone rule: **state what is true today, what is in progress, and what we do not claim.** Numbers are from in-repo benches only ([BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md)).

---

## 1. Where we stand

| Fact | Status |
|------|--------|
| Product identity | **YaP-Folia** (our Folia 26.2 fork) for game tick + YapEngine **slim chassis** + **YaP Link** |
| Game authority | YaP-Folia **26.2** (`game-authority=folia`, `folia-embed=true`, `folia-jar-source=build`) |
| Stock Folia | Fallback only (`folia-jar-source=fetch`) — not the product jar |
| Phase 3 Paper spatial | **Done as code** — **defaults off**; no Phase 3 on Folia path |
| Folia-aware plugins | **Yes** on product path (same ABI expectations as Folia) |
| Paper plugins (classic) | Legacy `game-authority=paper` for benches only |
| Fair highpop MSPT cite | **~100 active bots** |
| 250 keepalive holds | **HOLD-ONLY** — not an MSPT win |
| Dual-stack (JE + BE) | Join/spawn + play-depth smoke green — **not** full Geyser clone yet |
| YaP Link | **Shipped (phases 0–6)** — `0.6.0-phase6`; stock Velocity optional |
| Network stack shipped | `yap-db`, `yap-playerdata`, packs, chat, floodgate, lagguard, web dashboard; vehicles/stacker GAMEPLAY opt-in |

**One sentence:** YaPcore is **next-gen server software** — **YaP-Folia** for gameplay, **first-party YaP plugins** instead of Paper + LuckPerms + Essentials + Velocity + Geyser, plus **YaP Link** and dual-stack.

**What we are not claiming today**

- Faster than Paper (or Leaf) on every workload
- “Stock Folia but better” as a slogan without naming **YaP-Folia**
- That we ship upstream Folia as the product jar
- A **16-thread parallel game tick** — **YaP-Folia** owns world tick; YapEngine is edge/I/O only
- Phase 3 spatial default-on
- Full ViaVersion / Geyser play parity finished
- 250 keepalive as an MSPT win

---

## 2. Why choose us

### Choose YaPcore when

| Need | Why YaP fits |
|------|----------------|
| **Complete server without Paper glue** | Natives for perms, essentials, protect, world, chat, mod, map, tab, DB, playerdata |
| **YaP-Folia** game + engineered product edge | Regionized tick + our patches; chassis + Link; no Velocity/Geyser jar stack |
| **One product** for JE + Bedrock | Built-in dual-stack |
| Network ops without assembling ten plugins | Shared MariaDB, offline auth, claims, ranks, packs HTTP, web dashboard `:8080` |
| High-pop / entity-heavy target | Fair cites at ~100 active |

### Choose something else when

| Situation | Prefer |
|-----------|--------|
| One exotic Paper-only plugin with no Folia/YaP path | **Paper** (niche) |
| Hundreds of Purpur gameplay toggles only | **Purpur** |
| Classic single-tick TPS patches | **Pufferfish** / **Leaf** |
| Stock Folia jar only, DIY every plugin | **Upstream Folia** |
| Already happy DIY Folia + Velocity + Geyser | Stay DIY — or migrate to product natives |

### Where we beat (or are a clearer fit)

| vs | Honest edge for YaP | Honest edge for them |
|----|---------------------|----------------------|
| **Paper + plugin stack** | YaP-Folia + shipped YaP natives + Link + dual-stack | Rare Paper-only plugin niches |
| **Purpur / Pufferfish / Leaf** | Multi-thread YaP-Folia game + first-party crossplay/ops | Still “just replace the jar”; Leaf often wins mid-density MSPT on Paper-path cites |
| **Stock Folia** | YaP-Folia patches + YapEngine chassis + YaP Link + dual-stack | Simpler if you only want the upstream jar |
| **DIY Folia + Velocity** | First-party Link (phases 0–6) + shipped network plugins | DIY if you prefer upstream jars |

---

## 3. Next-gen capabilities — ranked

| Rank | Capability | Maturity | What it means |
|:----:|------------|----------|---------------|
| **1** | **YaP-Folia** as default game | Shipped · default on | `folia-jar-source=build` — regionized tick + YaP patches |
| **2** | YapEngine **slim chassis** | Shipped · always on | Edge/I/O — **not** world tick |
| **3** | **YaP Link** | Shipped (phases 0–6) | Native Velocity-class proxy |
| **4** | **Network product stack** | Shipped | One Hikari pool, offline `/login`, claims, ranks, packs |
| **5** | **Ops surface** | Shipped | Web dashboard `:8080`; `assembleRelease` |
| **6** | **Gameplay modules** | Shipped (opt-in) | Vehicles, stacker, knobs, MMO |
| **7** | **JE + Bedrock dual-stack** | Join DoD green | Join/spawn + play-depth smoke; fidelity partial |
| **8** | Fair highpop MSPT (~100 active) | Active gate | 250 keepalive = HOLD-ONLY |
| **9** | Phase 3 Paper spatial | Legacy · off | Benches only |

```
YaP-Folia game          →  regionized world tick (product default)
Chassis                 →  slim edge/I/O (not a second game tick)
YaP Link                →  multi-backend front door
Network + ops stack     →  multi-server / headless / cross-platform ops
Dual-stack              →  one world story for JE + Bedrock
```

---

## 4. Where we fit in the Minecraft ecosystem

```
Minecraft JE server cores
│
├── Spigot / Paper          — single main tick
│     ├── Purpur / Pufferfish / Leaf
│
├── Folia (PaperMC)         — upstream regionized tick
│     └── YaP-Folia ★       — YapLabs fork (product game jar)
│           └── YaPcore     — YaP-Folia + YapEngine chassis + YaP Link + product surface
│
└── Proxies
      ├── Velocity
      └── YaP Link
```

| Slot | YaPcore position |
|------|------------------|
| Ecosystem class | **Next-gen YaP-Folia product** — game fork + native plugin pool + Link |
| Not competing as | Fastest rename-fork · finished Velocity clone |
| Closest neighbors | Upstream Folia for the game lineage; Velocity for DIY proxy |
| Proxy story | YaP Link native (`0.6.0-phase6`); stock Velocity optional |

### Decision snapshot

| Your priority | Best default pick |
|---------------|-------------------|
| Compatibility and calm | Paper |
| Config knobs | Purpur |
| Single-thread TPS headroom | Pufferfish / Leaf |
| Upstream Folia jar only | Folia |
| YaP-Folia + complete native stack + Link + dual-stack | **YaPcore** |

---

## 5. Scoreboard context (no spin)

| Claim | Verdict |
|-------|---------|
| YaPcore always beats Paper MSPT | **False** — fair cite ~100 active |
| 250 keepalive is an MSPT win | **False** — HOLD-ONLY |
| YaPcore keeps Folia-aware plugins | **True** (product path) |
| YaPcore is stock Folia | **False** — product jar is **YaP-Folia** |
| YaPcore rejected Folia lineage | **False** — we fork Folia |
| YaPcore is a 16-thread game engine | **False** — YaP-Folia regions run the game |
| Phase 3 is product default | **False** |
| YaPcore is only branding on Folia | **False** — patches + chassis + Link + dual-stack + YaP plugins |

**Deck line (accurate):**  
*YaPcore is **next-gen server software** — **YaP-Folia’s game**, a **shipped YaP plugin stack**, **YaP Link**, and dual-stack join + play-depth smoke green. Honest highpop cites ~100 active.*

---

## References

| Doc | Use |
|-----|-----|
| [COMPARE_ECOSYSTEM.md](COMPARE_ECOSYSTEM.md) | Full competitor matrix |
| [FOLIA_FORK.md](../folia/FOLIA_FORK.md) | YaP-Folia patches |
| [WHAT_WE_ARE.md](WHAT_WE_ARE.md) | Identity |
| [YAP_LINK.md](../network/YAP_LINK.md) | YaP Link |
| [BENCH_VS_FOLIA.md](../performance/BENCH_VS_FOLIA.md) | MSPT methods |

*Not affiliated with Mojang, Microsoft, PaperMC, PurpurMC, or Pufferfish.*
