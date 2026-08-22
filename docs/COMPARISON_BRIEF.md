# YaPcore — no-hyperbole comparison brief

**YapLabs · August 2026**  
Audience: operators and partners deciding where YaPcore fits.

Tone rule for this document: **state what is true today, what is in progress, and what we do not claim.** Numbers are from in-repo benches only ([BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)).

---

## 1. Where we stand

| Fact | Status |
|------|--------|
| Product identity | **Folia** for the game + YapEngine 16-thread chassis + **YaP Link** — not a rename-only fork |
| Game authority | Folia **26.2** (`game-authority=folia`, `folia-embed=true`) |
| Phase 3 Paper spatial | **Done as code** — **defaults off**; legacy / opt-in for Paper benches only. Folia path has **no** Phase 3 spatial tick |
| Folia-aware plugins | **Yes** on product path — same Folia ABI expectations as stock Folia |
| Paper plugins (classic) | Legacy `game-authority=paper` for benches; not the product default |
| Fair highpop MSPT cite | **~100 active bots** — [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md) |
| 250 keepalive holds | **HOLD-ONLY** — not an MSPT win; do not market as such |
| Dual-stack (JE + BE) | Join/spawn green; play depth deepening — **not** full Geyser play parity yet |
| YaP Link | **Shipped** — complete Velocity fork (`yap-link/`); Velocity plugins load; stock Velocity optional |
| Network stack shipped | `yap-db`, `yap-playerdata`, packs, chat, floodgate, vehicles, stacker, LuckPerms pack, web dashboard |

**One sentence:** YaPcore is a high-pop–oriented Minecraft Java server product that uses Folia for gameplay, YapEngine for a fixed chassis, and YaP Link for multi-backend networks — with dual-stack and network-ops surface that DIY Folia+Velocity leave as glue work.

**What we are not claiming today**

- Faster than Paper (or Leaf) on every workload
- “Folia but better” as a slogan (we **use** Folia)
- “We are not Folia” / “Folia rejected”
- Phase 3 spatial default-on as product truth
- Full ViaVersion / Geyser play parity finished
- That Link replaces every DIY Velocity deployment overnight (still optional stand-in)
- Drop-in superiority for small idle SMPs
- 250 keepalive as an MSPT win

---

## 2. Why choose us (and where we are the better use case)

### Choose YaPcore when

| Need | Why YaP fits |
|------|----------------|
| **Folia** game + engineered product edge | Regions for the world; YapEngine for chassis; Link for the front door |
| **One product** for JE + Bedrock join path | Built-in dual-stack roadmap; no “install Via + Geyser yourself” as the product story |
| Network ops without assembling ten plugins | Shared MariaDB (`yap-db`), offline auth / claims / traders (`yap-playerdata`), ranks pack, packs HTTP, web dashboard `:8080` |
| Explicit engine model | Named 16 roles (watchdog, traffic, NW/NE/SW/SE, DLM, bridge, UI, I/O, telemetry) — chassis always on |
| High-pop / entity-heavy target | Fair cites at ~100 active; product aimed at busy worlds, not empty lobbies |

### Choose something else when

| Situation | Prefer |
|-----------|--------|
| Small / medium SMP, maximum boring reliability | **Paper** |
| Hundreds of gameplay config toggles | **Purpur** |
| Lean TPS patches, still classic single-tick Paper | **Pufferfish** / **Leaf** (benchmark your own load) |
| Stock Folia alone, no Yap product surface | **Folia** |
| Already happy with DIY Folia + Velocity | Stay DIY until Link depth matches your needs |

### Where we beat (or are a clearer fit)

| vs | Honest edge for YaP | Honest edge for them |
|----|---------------------|----------------------|
| **Paper** | Folia regions + chassis + Link + dual-stack / network product surface | Simpler mental model; universal Paper plugins |
| **Purpur / Pufferfish / Leaf** | Multi-thread Folia game + first-party crossplay/ops stack | Still “just replace the jar”; Leaf often wins mid-density MSPT on Paper-path cites |
| **Stock Folia** | YapEngine chassis + YaP Link + dual-stack product path | Simpler if you only want the Folia jar |
| **DIY Folia + Velocity** | First-party Link (full fork) + shipped network plugins | Same Velocity plugin ABI; DIY if you prefer upstream jars |

---

## 3. Next-gen capabilities — ranked

Ranked by **how distinctive and shippable they are today** for someone evaluating YaPcore against the rest of the ecosystem. Maturity: **Shipped** / **Default on** / **In progress** / **Active gate** / **Legacy**.

| Rank | Capability | Maturity | What it means in practice |
|:----:|------------|----------|---------------------------|
| **1** | **Folia** as default game authority | Shipped · default on | `game-authority=folia`, `folia-embed=true` — regionized world tick |
| **2** | YapEngine **16-thread chassis** | Shipped · always on | Fixed roles for watchdog, traffic, spatial cores, DLM, bridge, UI, I/O, telemetry |
| **3** | **YaP Link** (complete Velocity fork) | Shipped | Modern forwarding, online-mode, compression, `/server`, Velocity plugin API |
| **4** | **Network product stack** (DB, playerdata, ranks, packs, chat, floodgate) | Shipped | One Hikari pool, offline `/login`, claims/traders, LuckPerms pack, multi-pack HTTP |
| **5** | **Ops surface** (web dashboard, control GUI, crash tooling, release packaging) | Shipped | Headless browser ops on `:8080`; `assembleRelease` Linux/Windows |
| **6** | **Gameplay modules** (vehicles, stacker, gameplay knobs, pregen, PlaceholderAPI) | Shipped | First-party jars — not required to use, but part of the product box |
| **7** | **JE + Bedrock dual-stack** (first-party Via\* / Geyser parity) | In progress | Join/spawn green; play depth deepening — not full Geyser play parity |
| **8** | Fair highpop MSPT (~100 active) | Active gate | Cite ~100; 250 keepalive = HOLD-ONLY |
| **9** | Phase 3 Paper spatial tick | Legacy · default off | Opt-in for Paperclip benches only; **not** on Folia path |

### Capability fit (what each is *for*)

```
Folia game              →  regionized world tick (product default)
Chassis                 →  named 16 roles around the game
YaP Link                →  multi-backend front door (full Velocity fork)
Network + ops stack     →  multi-server / headless / cross-platform ops
Dual-stack              →  one world story for JE + Bedrock (join green; play deepening)
~100 active cite        →  honest highpop MSPT; not 250 keepalive marketing
Phase 3 Paper spatial   →  legacy benches only
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
│     └── Pufferfish / Leaf — Paper + performance patches (still single-tick)
│
├── Folia (+ rare forks)    — regionized multi-thread; Folia plugin contract
│     └── YaPcore ★         — Folia game + YapEngine chassis + YaP Link + product surface
│
└── Proxies
      ├── Velocity          — DIY edge for Folia/Paper backends
      └── YaP Link          — first-party complete Velocity fork
```

| Slot | YaPcore position |
|------|------------------|
| Ecosystem class | **Folia-backed game core with a YapEngine chassis and first-party Link** |
| Not competing as | Fastest rename-fork · “Folia but better” slogan · finished Velocity clone |
| Closest neighbors | Stock Folia for the game; Velocity for DIY proxy; Paper forks for single-tick plugins |
| Client story | Modern JE target; Bedrock via product dual-stack (join green; play depth deepening) |
| Proxy story | YaP Link full fork; stock Velocity remains optional |

### Decision snapshot

| Your priority | Best default pick |
|---------------|-------------------|
| Compatibility and calm | Paper |
| Config knobs | Purpur |
| Single-thread TPS headroom | Pufferfish / Leaf |
| Folia jar only | Folia |
| Folia game + chassis + Link + dual-stack / network box | **YaPcore** |

---

## 5. Scoreboard context (no spin)

Same-machine public harness (see [COMPARE_ECOSYSTEM.md](COMPARE_ECOSYSTEM.md) / [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md)):

| Claim | Verdict |
|-------|---------|
| YaPcore always beats Paper MSPT | **False** — fair cite ~100 active; mid-density / idle / legacy Paper spatial can lose |
| 250 keepalive is an MSPT win | **False** — HOLD-ONLY |
| YaPcore keeps Folia-aware plugins | **True** (product path) |
| YaPcore is Folia | **True for the game** — Folia default; YapEngine + Link are the rest |
| YaPcore rejected Folia | **False** |
| Phase 3 is product default | **False** |
| YaPcore is only branding on Folia | **False** — YapEngine chassis + Link + dual-stack + YaP plugins |

**Deck line (accurate):**  
*YaPcore runs Folia’s game on YapEngine’s fixed 16-thread chassis, with YaP Link for multi-backend networks — Folia-aware plugins, dual-stack join green (play depth deepening), and honest highpop cites (~100 active; no 250 keepalive MSPT marketing).*

---

## References

| Doc | Use |
|-----|-----|
| [COMPARE_ECOSYSTEM.md](COMPARE_ECOSYSTEM.md) | Full competitor matrix |
| [WHAT_WE_ARE.md](WHAT_WE_ARE.md) | Identity |
| [YAP_LINK.md](YAP_LINK.md) | YaP Link status |
| [BENCH_VS_PAPER.md](BENCH_VS_PAPER.md) | MSPT methods and results |
| [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md) | Dual-stack parity plan |

*Not affiliated with Mojang, Microsoft, PaperMC, PurpurMC, or Pufferfish.*
