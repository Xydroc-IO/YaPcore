# YaPcore — no-hyperbole comparison brief

**YapLabs · August 2026**  
Audience: operators and partners deciding where YaPcore fits.

Tone rule for this document: **state what is true today, what is in progress, and what we do not claim.** Numbers are from in-repo benches only ([BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md)).

---

## 1. Where we stand

| Fact | Status |
|------|--------|
| Product identity | **Folia** for game tick + YapEngine **slim chassis** (edge/I/O) + **YaP Link** — not a rename-only fork |
| Game authority | Folia **26.2** (`game-authority=folia`, `folia-embed=true`) |
| Phase 3 Paper spatial | **Done as code** — **defaults off**; legacy / opt-in for Paper benches only. Folia path has **no** Phase 3 spatial tick |
| Folia-aware plugins | **Yes** on product path — same Folia ABI expectations as stock Folia |
| Paper plugins (classic) | Legacy `game-authority=paper` for benches; not the product default |
| Fair highpop MSPT cite | **~100 active bots** — [BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md) |
| 250 keepalive holds | **HOLD-ONLY** — not an MSPT win; do not market as such |
| Dual-stack (JE + BE) | Join/spawn + play-depth smoke green — **not** full Geyser clone yet |
| YaP Link | **Shipped (phases 0–6)** — native proxy (`yap-first-party/link/`); stock Velocity optional stand-in |
| Network stack shipped | `yap-db`, `yap-playerdata`, packs, chat, floodgate, vehicles, stacker, LuckPerms pack, web dashboard |

**One sentence:** YaPcore is **next-gen server software** for most survival and network operators — **Folia** for gameplay, **first-party YaP plugins** instead of assembling Paper + LuckPerms + Essentials + Velocity + Geyser, plus **YaP Link** and dual-stack so DIY glue work goes away.

**What we are not claiming today**

- Faster than Paper (or Leaf) on every workload
- “Folia but better” as a slogan (we **use** Folia)
- “We are not Folia” / “Folia rejected”
- A **16-thread parallel game tick** — **Folia** owns world/entity/redstone tick; YapEngine is edge/I/O only
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
| **Complete server without Paper glue** | Natives for perms, essentials, protect, world, chat, mod, map, tab, link, DB, playerdata — [PLUGIN_COMPAT_MATRIX.md](PLUGIN_COMPAT_MATRIX.md) |
| **Folia** game + engineered product edge | Regionized tick; chassis + Link; no Velocity/Geyser jar stack |
| **One product** for JE + Bedrock join path | Built-in dual-stack — no “install Via + Geyser yourself” |
| Network ops without assembling ten plugins | Shared MariaDB, offline auth, claims, ranks, packs HTTP, web dashboard `:8080` |
| High-pop / entity-heavy target | Fair cites at ~100 active; aimed at busy worlds |

### Choose something else when

| Situation | Prefer |
|-----------|--------|
| One exotic Paper-only plugin with no Folia/YaP path | **Paper** (niche) |
| Hundreds of Purpur gameplay toggles only | **Purpur** |
| Classic single-tick TPS patches, Paper plugin universe | **Pufferfish** / **Leaf** |
| Stock Folia jar only, you will DIY every plugin | **Folia** |
| Already happy DIY Folia + Velocity + Geyser stack | Stay DIY — or migrate to product natives |

### Where we beat (or are a clearer fit)

| vs | Honest edge for YaP | Honest edge for them |
|----|---------------------|----------------------|
| **Paper + plugin stack** | Next-gen product: Folia + shipped YaP natives + Link + dual-stack | Still valid for rare Paper-only plugin niches |
| **Purpur / Pufferfish / Leaf** | Multi-thread Folia game + first-party crossplay/ops stack | Still “just replace the jar”; Leaf often wins mid-density MSPT on Paper-path cites |
| **Stock Folia** | YapEngine chassis + YaP Link + dual-stack product path | Simpler if you only want the Folia jar |
| **DIY Folia + Velocity** | First-party Link (native proxy, phases 0–6) + shipped network plugins | DIY if you prefer upstream jars |

---

## 3. Next-gen capabilities — ranked

Ranked by **how distinctive and shippable they are today** for someone evaluating YaPcore against the rest of the ecosystem. Maturity: **Shipped** / **Default on** / **In progress** / **Active gate** / **Legacy**.

| Rank | Capability | Maturity | What it means in practice |
|:----:|------------|----------|---------------------------|
| **1** | **Folia** as default game authority | Shipped · default on | `game-authority=folia`, `folia-embed=true` — regionized world tick |
| **2** | YapEngine **slim chassis** | Shipped · always on | Edge/I/O: traffic, bridge, dual-stack, UI/Heavy sandboxes, telemetry — **not** world tick |
| **3** | **YaP Link** (native Velocity-class proxy) | Shipped (phases 0–6) | Modern forwarding, online-mode, compression, `/server`, YaP Link plugin API, Bedrock UDP edge, release bundle |
| **4** | **Network product stack** (DB, playerdata, ranks, packs, chat, floodgate) | Shipped | One Hikari pool, offline `/login`, claims/traders, LuckPerms pack, multi-pack HTTP |
| **5** | **Ops surface** (web dashboard, control GUI, crash tooling, release packaging) | Shipped | Headless browser ops on `:8080`; `assembleRelease` Linux/Windows |
| **6** | **Gameplay modules** (vehicles, stacker, gameplay knobs, pregen, PlaceholderAPI) | Shipped | First-party jars — not required to use, but part of the product box |
| **7** | **JE + Bedrock dual-stack** (first-party Via\* / Geyser parity) | Join DoD green | Join/spawn + play-depth smoke green; emotes/custom skulls partial — not full Geyser clone |
| **8** | Fair highpop MSPT (~100 active) | Active gate | Cite ~100; 250 keepalive = HOLD-ONLY |
| **9** | Phase 3 Paper spatial tick | Legacy · default off | Opt-in for Paperclip benches only; **not** on Folia path |

### Capability fit (what each is *for*)

```
Folia game              →  regionized world tick (product default)
Chassis                 →  slim edge/I/O around Folia (not a second game tick)
YaP Link                →  multi-backend front door (native Velocity-class proxy)
Network + ops stack     →  multi-server / headless / cross-platform ops
Dual-stack              →  one world story for JE + Bedrock (join + play-depth smoke green)
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
      └── YaP Link          — first-party native Velocity-class proxy
```

| Slot | YaPcore position |
|------|------------------|
| Ecosystem class | **Next-gen Folia-backed product** — game + native plugin pool + Link (Paper assembly not required) |
| Not competing as | Fastest rename-fork · “Folia but better” slogan · finished Velocity clone |
| Closest neighbors | Stock Folia for the game; Velocity for DIY proxy; Paper forks for single-tick plugins |
| Client story | Modern JE target; Bedrock via product dual-stack (join + play-depth smoke green) |
| Proxy story | YaP Link native proxy (phases 0–6); stock Velocity remains optional |

### Decision snapshot

| Your priority | Best default pick |
|---------------|-------------------|
| Compatibility and calm | Paper |
| Config knobs | Purpur |
| Single-thread TPS headroom | Pufferfish / Leaf |
| Folia jar only | Folia |
| Folia game + complete native stack + Link + dual-stack | **YaPcore** |

---

## 5. Scoreboard context (no spin)

Same-machine public harness (see [COMPARE_ECOSYSTEM.md](COMPARE_ECOSYSTEM.md) / [BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md)):

| Claim | Verdict |
|-------|---------|
| YaPcore always beats Paper MSPT | **False** — fair cite ~100 active; mid-density / idle / legacy Paper spatial can lose |
| 250 keepalive is an MSPT win | **False** — HOLD-ONLY |
| YaPcore keeps Folia-aware plugins | **True** (product path) |
| YaPcore is Folia | **True for the game** — Folia default; YapEngine + Link are the rest |
| YaPcore rejected Folia | **False** |
| YaPcore is a 16-thread game engine | **False** — Folia region threads run the game; chassis is edge/I/O only |
| Phase 3 is product default | **False** |
| YaPcore is only branding on Folia | **False** — YapEngine chassis + Link + dual-stack + YaP plugins |

**Deck line (accurate):**  
*YaPcore is **next-gen server software** for most operators — **Folia’s game**, a **shipped YaP plugin stack** (not Paper + ten jars), **YaP Link**, and dual-stack join + play-depth smoke green. Honest highpop cites ~100 active; no 250 keepalive MSPT marketing.*

---

## References

| Doc | Use |
|-----|-----|
| [COMPARE_ECOSYSTEM.md](COMPARE_ECOSYSTEM.md) | Full competitor matrix |
| [WHAT_WE_ARE.md](WHAT_WE_ARE.md) | Identity |
| [YAP_LINK.md](YAP_LINK.md) | YaP Link status |
| [BENCH_VS_FOLIA.md](BENCH_VS_FOLIA.md) | MSPT methods and results |
| [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md) | Dual-stack parity plan |

*Not affiliated with Mojang, Microsoft, PaperMC, PurpurMC, or Pufferfish.*
