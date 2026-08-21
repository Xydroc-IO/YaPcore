# Phase 4 — Full Via + Geyser parity (first-party)

**Product rule:** YaPcore ships **complete** Java multi-version (supported bands)
and Bedrock crossplay **in our own code**. Operators must **not** install
ViaVersion, ViaBackwards, ViaRewind, Geyser, or Floodgate to make the product work.

**Supported JE floor: 1.20.2+** (config-era clients) onto Paper 26.2.
Pre-1.19 / Rewind-depth is **best-effort** (join matrix optional via
`MATRIX_FULL=1`), not a Phase 4 play-remap blocker.

This supersedes earlier “full ViaRewind parity” language for the product DoD.

## Authority

Paper remains the game (`game-authority=paper`). Translators sit on the public
edge and speak Paper’s current protocol (26.2 / ~776) inward.

```
Older JE clients ──► ViaStyleRemapper (+ full remap pipeline) ──► Paper JE
Bedrock clients  ──► GeyserStyleTranslator (+ full RakNet/BE) ──► shared world / Paper
Modern JE        ──► passthrough ──► Paper JE
```

## DoD — Via parity (own code)

Parity target = what ViaVersion + ViaBackwards + ViaRewind provide for a modern
Paper backend:

| Slice | Scope | Status |
|-------|--------|--------|
| **4.V0** | Bands registry, join accounting, no Via\* jars in product path | **Done** |
| **4.V1** | ViaVersion-equivalent: newer JE clients → server protocol (when server lags a build) | **Landed** — dump-backed `ForwardTransformer` when packet dumps exist; else keepalive/chunk/spawn layouts |
| **4.V2** | ViaBackwards-equivalent: older JE (1.9+) → Paper 26.2 — packets, items, blocks, entities, chunks | **Landed (modern mid)** — `PacketIdDump` + `MidBandTransformer` remap **all** play IDs by name (774/775/769/…→776); login session-UUID strip; legacy bands still catalog+PlayRemapper |
| **4.V3** | ViaRewind-equivalent: 1.8.x / early 1.9 deep remaps | **Best-effort** — join may work; **not** product DoD for play depth (floor is 1.20.2+) |
| **4.V4** | Paper JE port path — remapper in front of embed Paper | **Done (wiring)** — public JE → `ViaProxyHandler` → Paper on `paper-port` (`protocol-via-enabled=true`) |

Classes: `ProtocolCompat`, `ViaStyleRemapper`, `ViaBootstrap`, `ViaProxyHandler`,
`ViaSession`, `PacketTransformer`, `ForwardTransformer`, `PacketIdTable`, `PlayPacketRemapper`,
`Rewind18Transformer`, remappers under `com.yapcore.protocol.via.remap`,
`ProtocolBand`, `PacketFactory`.

## DoD — Geyser parity (own code)

Parity target = what Geyser (+ Floodgate-class auth where we need it) provides:

| Slice | Scope | Status |
|-------|--------|--------|
| **4.G0** | Dual-stack gateway, CrossplayHub, shared port, UnifiedPlayer | **Done** |
| **4.G1** | Full Bedrock login / spawn / movement / chat / inventory on shared world | **Landed** — NetworkSettings/Login split; chunks; auth-input; `ITEM_STACK_RESPONSE`; empty inventory content |
| **4.G2** | Combat, blocks, entities, UI, scoring — gameplay parity with JE on same map | **Landed (depth)** — ADD/REMOVE player+actor + move fanout; BREAK/PLACE→`BedrockPaperWorldSync` (Paper main thread) while BE roster still DualStack/YapEngine |
| **4.G3** | Floodgate-class Xbox/auth linking as **built-in** (not Floodgate jar) | **Landed** — `XboxChainValidator` + `FloodgateAuth`; Velocity path: `yap-floodgate` + `VelocityFloodgateDecoder` (`UUID(0,xuid)`) |
| **4.G4** | Skin / pack / form UX parity | **Landed** — `SkinService`, `FormService` |

Classes: `GeyserStyleTranslator`, `CrossplayHub`, `DualStackGateway`,
`RakNetUnconnected`, `RakNetReliability`, `RakNetSessionManager`,
`BedrockSessionManager`, `BedrockPacketCodec`, `BedrockGameplayBridge`,
`FloodgateAuth`, `SkinService`, `FormService`.

## Config

```properties
protocol-via-enabled=true
protocol-geyser-enabled=true
paper-port=25567
```

With Via on + Paper authority: Paper listens on `127.0.0.1:paper-port`; YaPcore
owns public `port` with Via proxy. MSPT benches auto-disable Via front
(`yap.bench.scenario` set).

## Explicit non-goals (plugins)

| Jar | Product stance |
|-----|----------------|
| ViaVersion / ViaBackwards / ViaRewind | **Forbidden** as the YaPcore answer |
| Geyser / Floodgate | **Forbidden** as the YaPcore answer |
| Velocity Via\* for *other* backends | Outside YaPcore backend DoD |

Bench/scripts must not copy those jars into `plugins/` for the product path.

## Honesty bar (parity vs pipeline)

**Replace claim (join/spawn):** With JE matrix **7/7 under zlib-on** and Bedrock
`geyserParitySmoke=true`, YaPcore can replace **Via\* + Geyser** on the product
path — operators do not need those jars for multi-version JE or Bedrock join.

Catalogs (items/blocks/entities per band) and Xbox ES384 chain validation are
**in-tree**. Soft BE depth (entity metadata richness, dump-backed biome/creative NBT,
retail Xbox soak fixture) still hardens from live clients — they are **not**
join/spawn blockers (`start_game` itemstates + flat chunks + empty inventory parse on 1.21.50).

### Live matrix (2026-08-21)

#### Java (Via front) — compression **on** (`network-compression-threshold=256`)

Ran against public Via front `:25566` → Paper `:25567` (proto **776**):

| Result | Count |
|--------|------:|
| Status ping OK | **7 / 7** |
| Login / spawn OK | **7 / 7** |

Artifact: `build/protocol-matrix-latest.json`.

**All green:** 1.8.9 (Rewind), 1.12.2, 1.16.5, 1.19.4, 1.20.4, 1.21.1, 1.21.4.

#### Bedrock (Geyser path) — smoke

`HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-bedrock-smoke.sh`

| Check | Result |
|-------|--------|
| RakNet ping / MOTD | **OK** |
| RakNet join + spawn (1.21.50) | **OK** |
| Text JOIN fallback | **OK** |
| `geyserParitySmoke` | **true** |

Artifact: `build/bedrock-smoke-latest.json`.

**Landed this pass:**

- Manual Minecraft framing on Via↔Paper (no double zlib pipeline Encoder)
- Explicit Set Compression rebuild — matrix green **with zlib on**
- RakNet frame-set IDs **0x80–0x8f** (was truncating at 0x8d)
- NetworkSettings threshold **65535** (threshold 0 was forcing client deflate)
- Deflate batch inflate path + 1.21.50 resource-pack info/stack codecs
- CRA 20 system addresses; protocol from RequestNetworkSettings
- **BE depth:** `start_game` rewritten to full 1.21.50 79-field layout (zigzag64 /
  varint64 / BlockCoordinates / Experiments li32 / empty **littleVarint** network NBT /
  world_template uuid); `inventory_content` empty slots + FullContainerName;
  `level_chunk` / `network_chunk_publisher_update` zigzag + saved_chunks
- **Login / Floodgate:** varint-encapsulated `LoginTokens` parse (fixes always-`BedrockPlayer`);
  offline self-signed JWT → real `displayName`; XUID `0` → synthetic Floodgate XUID
- **Post-login bodies:** `update_attributes`, `set_time` / `set_difficulty` /
  `set_commands_enabled`, **full** `creative_content` from itemstates, `player_list` + minimal Skin
- **BE depth:** `start_game` itemstates (~1599); Paper-backed `level_chunk` (fallback flat);
  dump-backed biome/entity NBT; dense `add_player`; Xbox multi-hop soak + optional retail fixture
- Smoke: `raknetError=null`, spawn with **no** PartialReadError

**Soft gaps (still stub / deepen later — not join/spawn blockers):**

| Packet / area | Status |
|---------------|--------|
| `start_game` itemstates / block_properties | **Itemstates landed** — `protocol/bedrock/1.21.50/itemstates.json` (~1599); regen via `node scripts/generate-bedrock-itemstates.mjs`; block_properties still empty (custom blocks only) |
| `level_chunk` payload | **Paper-backed** when `BedrockPaperWorldSync` attached (air/solid→hashed ids); else flat palettes; `-Dyapcore.bedrock.flat-chunks` forces flat |
| `add_player` / actor / metadata / abilities | **Dense metadata** (flags, health, nametag, air, scale, AABB) + full ability layer |
| Creative / biome defs / entity identifiers | **Full creative** from itemstates; **dump-backed** `biome_definitions.nbt` + `available_entity_identifiers.nbt` |
| Xbox chain on retail clients | Offline JWT + Certificate unwrap; **multi-hop Mojang-rooted soak** (stand-in root unit test); optional `xbox/retail-chain.json` for real Mojang JWTs |

**Next:** richer JE→BE block-state catalog (beyond air/stone/dirt/grass/bedrock); Paper chunk radius streaming beyond 3×3; Via matrix under resource-pack-on.


### Client matrix (how to re-run)

```bash
# JE (product compression is fine now):
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-matrix.sh

# Bedrock Geyser smoke:
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-bedrock-smoke.sh
```

Offline JE bots via `minecraft-protocol` at **1.19.4 (canary) / 1.20.4 /
1.21.1 / 1.21.4**. Full legacy matrix: `MATRIX_FULL=1`. Exit code 2 lists spawn
gaps. Dump-backed mid remaps live under
`protocol/vanilla/{1.21.1,1.21.4,1.21.6,1.21.10,1.21.11,26.1,26.2}/packets.json`.

Regenerate catalogs:

```bash
cd scripts/bench/bots && npm i minecraft-data
node scripts/generate-protocol-catalogs.mjs
```

## Delivery order (still one Phase 4 goal)

1. **JE matrix DoD (1.20.2+ + 1.19.4 canary)** — spawn green under compression + pack ✅
2. **Forced pack soak** — `resource-pack-forced=true` through Via ✅ (matrix bots auto-ack)
3. **Play-phase pack remap** — YaPPacks extras via Via play auto-ack + ID aliases ✅ (enable live)
4. **Mid play bodies (1.20–1.21)** — paletted chunk no-op + component-era item id remap landed; metadata/window_items deepen ongoing
5. **Bedrock smoke + Paper world stream** — smoke green; Paper spawn + JE player mirror; flat chunks opt-in (`-Dyapcore.bedrock.flat-chunks=true`); true Paper column stream still next
6. **4.G depth** — BE inventory/combat deepen (BREAK→Paper already live)
7. **PlayerData MariaDB** — plugin stays enabled in release

Rewind (1.8–1.16) deepen is explicitly **out of product DoD** unless reopened.

## Related

- [CROSSPLAY.md](CROSSPLAY.md) — ports / hub
- [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md) — client matrix
- [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md) — phase map
- [VELOCITY.md](VELOCITY.md) — no Via\* on YaPcore backend
