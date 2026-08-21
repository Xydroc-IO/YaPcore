# Via\* + Geyser feature parity (first-party) — full checklist

**YapLabs · August 2026**  
Companion to [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md).

**Product rule:** YaPcore ships complete multi-version JE (supported bands) and
Bedrock crossplay **in our own code**. Operators must **not** install ViaVersion,
ViaBackwards, ViaRewind, Geyser, or Floodgate for the product path to work.

**Parity definition:** For every row below, “Done” means a supported client can
complete that feature on a Paper 26.2 backend **without** those jars, verified by
the listed gate (matrix / smoke / unit / live soak). “Partial” means join works
or a subset works; deepen is tracked. “Out” means explicitly not product DoD.

---

## Scope boundaries

| Band | Direction | Product stance |
|------|-----------|----------------|
| **JE 1.20.2 → current** onto Paper 26.2 (~776) | ViaBackwards-class | **DoD** |
| **JE newer than server** (when server lags a build) | ViaVersion-class | **DoD** when dumps exist |
| **JE 1.19.4** | Canary | Join/spawn matrix; play deepen as capacity allows |
| **JE 1.8–1.16 (Rewind)** | ViaRewind-class | **Best-effort join** — play depth **out** of DoD |
| **Bedrock 1.21.50** (current pin) | Geyser-class | **DoD** for vanilla gameplay on shared Paper world |
| **Floodgate identity** | Floodgate-class | **DoD** in core + `yap-floodgate` on Velocity |

```
Older JE  ──► MidBandTransformer / PlayPacketRemapper / Rewind18 ──► Paper 776
Newer JE  ──► ForwardTransformer ──► Paper 776
Modern JE ──► passthrough ──► Paper 776
Bedrock   ──► RakNet + BedrockGameplayBridge + GeyserStyleTranslator ──► Paper world
```

Classes live under `com.yapcore.protocol.via*` and `com.yapcore.crossplay*`
(no Via\* / Geyser jars; no `protocol/geyser` package — Geyser surface is crossplay).

---

## Status legend

| Tag | Meaning |
|-----|---------|
| **Done** | Meets parity bar for supported bands; gate green |
| **Partial** | Wired / useful; body depth or edge cases remain |
| **Gap** | Required for product DoD; not yet at bar |
| **Out** | Explicit non-goal for Phase 4 product DoD |
| **N/A** | Does not apply to our architecture |

---

## A. ViaVersion parity (newer client → older/current server) — slice 4.V1

Target: what **ViaVersion** does when a client protocol is ahead of (or needs
forward rewrite onto) the server protocol.

| # | Feature | YaP owner | Status | Gate |
|---|---------|-----------|--------|------|
| V1.1 | Protocol / band detect from handshake | `ViaSession`, `ProtocolBand` | **Done** | Matrix handshake |
| V1.2 | Play packet ID remap by name (dumps) | `ForwardTransformer` + `PacketIdDump` | **Done** when dumps | Dump presence |
| V1.3 | Heuristic keepalive / chunk / spawn / item layouts (no dump) | `ForwardTransformer` | **Partial** | Forward layout unit tests |
| V1.4 | Config-phase ID / known-packs skip | `ConfigPacketRemapper`, `ViaProxyHandler` | **Done** | Matrix config |
| V1.5 | Login finished / compression framing | `LoginSuccessRewriter`, `ViaProxyHandler` | **Done** | Matrix zlib-on |
| V1.6 | Proxy front (public JE → Paper loopback) | `ViaProxyHandler` | **Done** | `protocol-via-enabled` |
| V1.7 | Join accounting / band attach | `ViaStyleRemapper` | **Done** (accounting only) | Logs |
| V1.8 | Future protocol ≥778 nearest-dump / index drop-in | `PacketIdDump` + `index.json` | **Done** (plumbing) | [PROTOCOL_DUMPS.md](PROTOCOL_DUMPS.md) — add dump when Mojang ships |
| V1.9 | Block / item / entity **content** forward (new → old server) | `CatalogStore` + remappers | **Partial** | Catalog regen |
| V1.10 | ViaVersion API / plugin hooks for third parties | — | **Out** | N/A (first-party only) |

**4.V1 Done when:** dumps exist for client+server **or** heuristic path keeps
keepalive/chunk/spawn stable; matrix spawn green for pinned forward cases.

---

## B. ViaBackwards parity (older client → Paper 26.2) — slice 4.V2

Target: what **ViaBackwards** does for older JE on a modern Paper backend.
**Product floor: 1.20.2+.** Rows below that floor are canary / best-effort.

### B1 — Connection & state machine

| # | Feature | YaP owner | Status | Gate |
|---|---------|-----------|--------|------|
| VB.1 | Handshake → login → config → play | `ViaProxyHandler`, `PacketTransformer` | **Done** | Matrix 7/7 zlib-on |
| VB.2 | Login session-UUID strip/append | `MidBandTransformer`, `LoginSuccessRewriter` | **Done** | Mid bands |
| VB.3 | Set Compression (manual framing, no double zlib) | `ViaProxyHandler` | **Done** | Matrix compression on |
| VB.4 | Config registry / known packs auto-ack | `ConfigPacketRemapper`, proxy | **Done** | Matrix |
| VB.5 | Resource pack forced / play auto-ack | Via play aliases + packs | **Done** | `run-matrix-pack-on.sh` 4/4 |
| VB.6 | Drop unknown play IDs (never same-ID passthrough) | `MidBandTransformer` | **Done** | Mid path |

### B2 — Packet ID & body remaps (modern mid 764–775 → 776)

| # | Feature | YaP owner | Status | Gate |
|---|---------|-----------|--------|------|
| VB.10 | Remap **all** play IDs by name (dumps) | `MidBandTransformer` + `PacketIdDump` | **Done** | Dump coverage |
| VB.11 | Wire dumps for every supported band | `protocol/vanilla/*/packets.json` | **Done** | 764–776 exact + 1.20.3 merge + 766→1.20.5 |
| VB.12 | Slot / window_items / set_slot / equipment | `SlotCodec` | **Done** (kick-safe) | Unknown components strip; type+count kept |
| VB.13 | Entity metadata reshape | Mid + `EntityRemapper` + `EntityMetadataSkip` | **Done** | Copy/skip serializers 0–40; item_stack remap |
| VB.14 | Chunk section / palette reshape | `ChunkRemapper`, `ChunkPaletteCodec`, `ChunkLightCodec` | **Done** | Mid paletted passthrough; rebuild appends full-bright light |
| VB.15 | Spawn entity / player body reshape | Mid + remappers | **Partial** | Matrix spawn OK; play soak |
| VB.16 | `window_click` / creative click **bodies** | Mid + `SlotCodec.remapWindowClick` / `remapCreativeSlot` | **Done** | Unit: `SlotCodecClickTest` |
| VB.17 | Sounds / particles / recipes / advancements | ID remap + catalogs | **Done** (kick-safe) | Incomplete maps drop/silent; never kick |
| VB.18 | Chat / signed chat / player info | Mid ID + passthrough bodies | **Partial** | `yap-chat` unsigned fix on Paper |

### B3 — Content maps (blocks / items / entities)

| # | Feature | YaP owner | Status | Gate |
|---|---------|-----------|--------|------|
| VB.20 | Item name ↔ id catalogs per band | `CatalogStore` | **Done** | `CatalogStoreTest` |
| VB.21 | Block name / state bridge | `BlockRemapper` | **Partial** | Flat-state best-effort |
| VB.22 | Entity type bridge + substitute display | `EntityRemapper` | **Partial** | Newer mobs → older stand-in |
| VB.23 | Placeholder policy (unknown → safe id) | remappers | **Partial** | Document + enforce |
| VB.24 | World height &lt;1.17 on 1.18+ maps | — | **Out** for floor 1.20.2+ (clients already 1.18+) | N/A |
| VB.25 | Smithing / new UI screens on older mid | Mid | **Partial** | Same ViaBackwards caveats |

### B4 — Legacy bands (pre-dump / pre-1.20.2)

| # | Feature | YaP owner | Status | Gate |
|---|---------|-----------|--------|------|
| VB.30 | Catalog + `PlayPacketRemapper` heuristics | `PlayPacketRemapper` | **Partial** | Best-effort |
| VB.31 | 1.19.4 canary join/spawn | Matrix | **Done** join | Matrix canary |
| VB.32 | Deep play on 1.12–1.19 | — | **Out** of product DoD | Optional deepen |

**4.V2 Done when (product):** every **1.20.2+** band in the JE matrix spawns under
compression + pack; mid play survives inventory open/click, chunk load, and entity
view without kick for 10+ minutes on a live client (soak checklist §E).

---

## C. ViaRewind parity (1.8 / early 1.9) — slice 4.V3

| # | Feature | YaP owner | Status | Gate |
|---|---------|-----------|--------|------|
| VR.1 | Keepalive int↔long | `Rewind18Transformer` | **Partial** | `MATRIX_FULL=1` |
| VR.2 | Dig / place / position / join | Rewind | **Partial** | Join may green |
| VR.3 | Chunk → 1.8 column | Rewind + `ChunkRemapper` | **Partial** | |
| VR.4 | Spawn without UUID | Rewind | **Partial** | |
| VR.5 | Full inventory / combat / metadata depth | — | **Out** | Not product DoD |
| VR.6 | 1.7.x | — | **Out** | |

**Product line:** Rewind join in the full matrix is a **nice-to-have smoke**, not a
ship blocker. Do not market “1.8 play parity.”

---

## D. Geyser parity (Bedrock ↔ Paper world) — slices 4.G0–4.G4

Target: what **Geyser** provides for Bedrock players on a Java server — without
the Geyser jar. Pin: **Bedrock 1.21.50**.

### D1 — Transport & session (4.G0)

| # | Feature | YaP owner | Status | Gate |
|---|---------|-----------|--------|------|
| G.1 | Dual-stack gateway (JE TCP + BE UDP) | `DualStackGateway` | **Done** | Shared port |
| G.2 | Crossplay hub + `UnifiedPlayer` roster | `CrossplayHub` | **Done** | Hub attach |
| G.3 | RakNet unconnected ping / MOTD | `RakNetUnconnected` | **Done** | Bedrock smoke |
| G.4 | RakNet reliability frame-set **0x80–0x8f** | `RakNetReliability` | **Done** | Smoke / unit |
| G.5 | Session manager + batch inflate | `RakNetSessionManager`, codec | **Done** | Smoke |

### D2 — Login / spawn / world (4.G1)

| # | Feature | YaP owner | Status | Gate |
|---|---------|-----------|--------|------|
| G.10 | NetworkSettings + Login token parse | `BedrockPacketCodec`, Floodgate | **Done** | Smoke |
| G.11 | `start_game` full 1.21.50 layout + itemstates | Codec + `BedrockItemStates` | **Done** | ~1599 itemstates |
| G.12 | `block_properties` (custom blocks) | start_game empty | **Out** until custom BE blocks | Vanilla OK empty |
| G.13 | Resource pack info / stack | Codec | **Done** | Smoke |
| G.14 | `level_chunk` Paper-backed + continuous stream | `BedrockColumnStreamer`, hashes | **Done** | Paper default; flat via `-Dyapcore.bedrock.flat-chunks=true` |
| G.15 | Chunk radius / publisher update | Bridge | **Done** | Smoke |
| G.16 | Biome defs + entity identifier NBT | `BedrockNbtDumps` | **Done** | Dump-backed |
| G.17 | Creative content from itemstates | Codec | **Done** | |
| G.18 | Attributes / time / difficulty / commands_enabled | Codec | **Done** | |
| G.19 | Player list + minimal skin | Codec + `SkinService` | **Done** | |

### D3 — Gameplay (4.G2)

| # | Feature | YaP owner | Status | Gate |
|---|---------|-----------|--------|------|
| G.20 | Auth input → movement | Bridge + `GeyserStyleTranslator` | **Done** | Smoke + live |
| G.21 | Chat / command → Paper | Bridge + `PaperCommandBridge` | **Done** | Commands |
| G.22 | BREAK / PLACE → Paper main | `BedrockPaperWorldSync` | **Done** | Live dig/place |
| G.23 | Attack / interact intents | Translator + INTERACT action split | **Done** | action 1/4 → ATTACK; else INTERACT / container |
| G.24 | ADD / REMOVE / MOVE players + actors | `BedrockEntityTracker` | **Done** | Fanout |
| G.25 | Dense entity metadata / abilities | Codec / tracker | **Done** (harden) | Player + actor dense metadata; live soak remaining |
| G.26 | Inventory authority (shadow + stack response) | `BedrockInventoryAuthority` | **Partial** | JE recipe craft via Paper; armor/craft slots; creative craft |
| G.27 | Full Paper inventory inject for **pure BE** players | `BedrockPaperPlayerInject` + vault fallback | **Done** | placeNewPlayer on login; vault if inject fails |
| G.28 | `/give` `/clear` mirror hints | Inventory + Paper | **Partial** | |
| G.29 | Available commands rich + COMMAND_REQUEST | `BedrockAvailableCommands` | **Done** | |
| G.30 | Containers / villager / enchanting UIs | `BedrockContainerBridge` | **Partial** | Trade execute + PLAYER_ENCHANT_OPTIONS apply; chest/workbench mutate |
| G.31 | Scoring / boss bars / titles | Codec | **Partial** | |
| G.32 | Emotes | — | **Gap** (low) | Optional |
| G.33 | Custom skulls / player heads display | — | **Partial** | |
| G.34 | Bedrock resource packs (server-offered) | Packs path | **Partial** | JE pack story primary |

### D4 — Floodgate-class auth (4.G3)

| # | Feature | YaP owner | Status | Gate |
|---|---------|-----------|--------|------|
| G.40 | Offline / self-signed JWT → displayName | `FloodgateAuth` | **Done** | Smoke |
| G.41 | XUID → `UUID(0, xuid)` identity | `FloodgateAuth` | **Done** | |
| G.42 | Xbox ES384 chain to Mojang root | `XboxChainValidator` | **Done** | Multi-hop + retail-shaped CI; live capture optional |
| G.43 | Velocity `^Floodgate^` AES-GCM hostname | `VelocityFloodgateDecoder`, `FloodgateCipher` | **Done** | Unit + Velocity docs |
| G.44 | Global Linking / Global API | — | **Out** | Local identity only |
| G.45 | Floodgate Spigot/Velocity **jars** | Forbidden | **N/A** | Use `yap-floodgate` |

### D5 — Forms & skins (4.G4)

| # | Feature | YaP owner | Status | Gate |
|---|---------|-----------|--------|------|
| G.50 | Modal / simple / custom forms | `FormService` + builder | **Done** | Cumulus-class + result handlers |
| G.51 | Skin conversion / registry | `SkinService` | **Done** | JE textures property + Paper apply |
| G.52 | Plugin API to send forms to BE players | FormService | **Done** | `custom()` / `sendSimple` / handlers |

**4.G Done when:** Bedrock smoke `geyserParitySmoke=true`; pure BE player has full
Paper-backed inventory; dig/place/move/chat/command stable; retail Xbox chain
validated at least once in CI or recorded soak; no Geyser/Floodgate jars required.

---

## E. Acceptance gates (how we prove parity)

| Gate | Command / artifact | Covers |
|------|-------------------|--------|
| JE matrix (product) | `HOST=… PORT=… ./scripts/protocol-matrix/run-matrix.sh` → `build/protocol-matrix-latest.json` | VB.1–5, V1.* |
| JE pack soak | `./scripts/protocol-matrix/run-matrix-pack-on.sh` | VB.5 |
| JE full legacy | `MATRIX_FULL=1` same script | VR.* join only |
| BE smoke | `./scripts/protocol-matrix/run-bedrock-smoke.sh` → `build/bedrock-smoke-latest.json` | G.1–19 |
| Xbox soak | `./scripts/protocol-matrix/xbox-chain-soak.sh` | G.42 |
| Play soak (auto + checklist) | `./scripts/protocol-matrix/play-soak.sh` (`--matrix` / `--be` / `--all`) | §E + P4.5 |
| Catalog unit | `CatalogStoreTest`, band completeness tests | VB.20 |
| Mid click soak | Manual / bot: open inv, shift-click, creative | VB.16 code ✅ — live soak |
| BE Paper inv | Pure BE join → `/give` visible + hotbar sync | G.27 code ✅ — live soak |
| BE column stream | Walk chunk borders; terrain matches Paper (not flat) | G.14 ✅ code; live tick |
| Live retail Xbox | Capture with `-Dyap.floodgate.dumpChain=true` — see [XBOX_RETAIL_CAPTURE.md](XBOX_RETAIL_CAPTURE.md) | G.42 retail |

### Play soak checklist (mark Done only when passed)

**JE mid (1.20.4 and 1.21.1 minimum):**

- [ ] Join under compression + optional forced pack
- [ ] Walk 200+ blocks across chunk borders
- [ ] Open chest / furnace / crafting; shift-click stack
- [ ] Hotbar select + place/break 32 blocks
- [ ] Attack mob; see other players move
- [ ] Run `/help` and one plugin command
- [ ] No disconnect for 10 minutes

**Bedrock 1.21.50:**

- [ ] RakNet ping + login + spawn (`geyserParitySmoke`)
- [ ] Move across chunk borders — terrain matches Paper (**P4.5**, not flat void)
- [ ] Move, jump, sprint; chat visible to JE
- [ ] Break/place mirrored on Paper; column refreshes
- [ ] Inventory open; take/place; `/give` appears (**G.27**)
- [ ] Command from BE → Paper executes
- [ ] Form opens and returns (if UI used)
- [ ] Offline + Xbox path (retail when fixture available)

Automated preamble: `./scripts/protocol-matrix/play-soak.sh` (add `--all` when Via front + BE listener are up).
Flat terrain is **opt-in only**: `-Dyapcore.bedrock.flat-chunks=true`.

---

## F. Delivery order (remaining to claim “full parity”)

Still one Phase 4 goal. Order is dependency-aware, not marketing order.

| Step | Work | Closes |
|------|------|--------|
| **P4.1** | Wire remaining vanilla dumps in `PacketIdDump` (1.20.3, 1.20.5, any missing mid) | VB.11 ✅ |
| **P4.2** | Deepen `SlotCodec` + Mid `window_click` / creative **bodies** | VB.12, VB.16 ✅ |
| **P4.3** | Mid entity metadata + chunk light harden for 1.20.2–1.21.x | VB.13–15 ✅ |
| **P4.4** | BE→Paper **player inject** + inventory vault fallback for pure BE | G.27 ✅ |
| **P4.5** | Default Paper column stream (retire flat-chunk as default) | G.14 ✅ |
| **P4.6** | Container / villager / enchant UI parity pass | G.30 ✅ |
| **P4.7** | Combat / interact soak + metadata harden | G.23, G.25 ✅ (combat split; G.25 still live-harden) |
| **P4.8** | Forms API completeness + skin JE visibility soak | G.50–52 ✅ |
| **P4.9** | Retail Xbox JWT fixture in soak CI | G.42 ✅ (shaped CI + optional live capture) |
| **P4.10** | Forward dump for next Mojang protocol when shipped | V1.8 ✅ (index + generator; dump when Mojang ships) |
| **P4.11** | Document known ViaBackwards-class limitations (smithing, sound maps) | Honesty ✅ → [VIA_BACKWARDS_LIMITATIONS.md](VIA_BACKWARDS_LIMITATIONS.md) |

Rewind deepen stays **out** unless product reopens VR DoD.

---

## G. Explicit non-goals

| Item | Stance |
|------|--------|
| Shipping ViaVersion / ViaBackwards / ViaRewind / Geyser / Floodgate jars | **Forbidden** on product path |
| ViaVersion/Geyser **plugin APIs** for third-party plugins expecting those jars | **Out** — offer YaP FormService / FloodgateAuth instead |
| Bit-identical packet streams vs Via\* / Geyser | **Out** — behavioral parity, not bytecode clone |
| 1.8 play depth / 1.7 clients | **Out** |
| Bedrock custom block_properties without a product custom-block story | **Out** until we ship one |
| Floodgate Global Linking / Global API | **Out** |

---

## H. Claim language (use this)

| Allowed when gates green | Forbidden until gates green |
|--------------------------|-----------------------------|
| “First-party ViaBackwards-class for 1.20.2+; no Via\* jars” | “Full ViaRewind play parity” |
| “First-party Geyser-class Bedrock join on shared Paper world” | “100% Geyser feature clone” |
| “Floodgate-class identity in core / yap-floodgate on Velocity” | “Install Floodgate” as the answer |
| “JE matrix N/N spawn; Bedrock smoke green” | “Faster than Via” / unbenchmarked claims |

**Replace claim (operators):** With JE product matrix green **and** Bedrock
`geyserParitySmoke=true` **and** P4.1–P4.4 + P4.6–P4.11 closed, YaPcore can replace
Via\* + Geyser + Floodgate on the product path for supported bands — subject to
[VIA_BACKWARDS_LIMITATIONS.md](VIA_BACKWARDS_LIMITATIONS.md). Live soak
checklists in §E still recommended before marketing full play depth.

Until live soaks close remaining edge cases, say: **join/spawn + inventory
inject + containers/forms/skins/Xbox-shaped CI + Paper column stream green;
denser live metadata still hardening from retail clients.** Forward dumps for
post-776 clients land via [PROTOCOL_DUMPS.md](PROTOCOL_DUMPS.md) when Mojang ships.
Run `./scripts/protocol-matrix/play-soak.sh` before marketing full play depth.

---

## Related

| Doc | Topic |
|-----|--------|
| [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md) | Slice status + live matrix notes |
| [PROTOCOL_DUMPS.md](PROTOCOL_DUMPS.md) | P4.10 next-protocol dump workflow |
| [VIA_BACKWARDS_LIMITATIONS.md](VIA_BACKWARDS_LIMITATIONS.md) | P4.11 honesty / claim language |
| [CROSSPLAY.md](CROSSPLAY.md) | Ports / hub |
| [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md) | Client matrix + packs |
| [VELOCITY.md](VELOCITY.md) | yap-floodgate; no Via\* on backend |
| [VANILLA_CLIENTS.md](VANILLA_CLIENTS.md) | JE bands |

*Not affiliated with Mojang, Microsoft, ViaVersion, or GeyserMC.*
