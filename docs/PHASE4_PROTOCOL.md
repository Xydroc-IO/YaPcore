# Phase 4 — Full Via + Geyser parity (first-party)

**Product rule:** YaPcore ships **complete** Java multi-version and Bedrock
crossplay **in our own code**. Operators must **not** install ViaVersion,
ViaBackwards, ViaRewind, Geyser, or Floodgate to make the product work.

This supersedes earlier “Via-class / Geyser-class polish” language: Phase 4’s
protocol DoD is **feature parity** with those stacks, implemented as YaP
modules — not plugin jars.

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
| **4.V3** | ViaRewind-equivalent: 1.8.x / early 1.9 deep remaps | **Landed (depth pass)** — `Rewind18Transformer` + legacy chunk path; validate via `scripts/protocol-matrix/run-matrix.sh` |
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
| **4.G3** | Floodgate-class Xbox/auth linking as **built-in** (not Floodgate jar) | **Landed** — `XboxChainValidator` ES384 chain walk to Mojang root + `FloodgateAuth` |
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

Catalogs (items/blocks/entities per band) and Xbox ES384 chain validation are
**in-tree**. Packet *layout* remaps and every BE codec body still deepen from
**client matrix failures**.

### Live matrix (2026-08-21)

Ran against public Via front `:25566` → Paper `:25567` (proto **776**):

| Result | Count |
|--------|------:|
| Status ping OK | **7 / 7** |
| Login / spawn OK | **4 / 7** |

Artifact: `build/protocol-matrix-latest.json`.

**Spawn green:** 1.8.9 (Rewind), 1.16.5, 1.19.4, 1.21.4.  
**Still failing:** 1.12.2 (`socketClosed`), 1.20.4 / 1.21.1 (config timeout).

**Landed this pass:**

- Manual Minecraft framing on Via↔Paper (Epoll `MessageToByteEncoder` was dropping C2S)
- Backend `AUTO_READ` + inbound auto-read so login_success is not stuck unread
- Universal `LoginSuccessRewriter` (GameProfile + optional session UUID strip; 1.8 string UUID)
- Legacy config-skip (auto Login ACK / known packs / finish toward Paper)
- Status JSON `version.protocol` rewritten to the probing client
- Handshake port rewritten to Paper listen port
- Config `client_information` rebuilt with particle status for Paper 776
- Resource pack: drop `add_resource_pack` toward mid clients + auto-ack Paper (or disable pack for matrix)
- Set Compression: flush uncompressed first, then install zlib; compressed writes go via `mc-compress` → `frame-enc`

**Matrix tip:** `resource-pack-enabled=false` (or Via pack auto-ack) — forced packs stall mid-band config FSM.

**Next remaps:** 1.12.2 login/config path; 1.20.4 / 1.21.1 config finish (known packs / client_info edge cases).

### Client matrix (how to re-run)

```bash
# With YaPcore/Paper already listening (default product port):
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-matrix.sh
```

Offline JE bots via `minecraft-protocol` at 1.8.9 / 1.12.2 / 1.16.5 / 1.19.4 /
1.20.4 / 1.21.x. Exit code 2 lists spawn gaps. Dump-backed mid remaps live under
`protocol/vanilla/{1.21.1,1.21.4,1.21.6,1.21.10,1.21.11,26.1,26.2}/packets.json`.

Regenerate catalogs:

```bash
cd scripts/bench/bots && npm i minecraft-data
node scripts/generate-protocol-catalogs.mjs
```

## Delivery order (still one Phase 4 goal)

1. **Unblock matrix spawn** — compression framing + login/config remaps, then re-run matrix
2. **4.V1 + 4.V2 + 4.V4** — deepen dump-backed play remaps from remaining gaps
3. **4.G1 + 4.G2** — Bedrock playable; entities + Paper-backed BREAK/PLACE
4. **4.V3 / 4.G3 / 4.G4** — harden Rewind + Xbox/skin against real clients

Plus: YaP SYNC/HEAVY/UI plugin pools polished under Phase 3 leases (unchanged).

## Related

- [CROSSPLAY.md](CROSSPLAY.md) — ports / hub
- [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md) — client matrix
- [PAPER_YAPENGINE_PORT.md](PAPER_YAPENGINE_PORT.md) — phase map
- [VELOCITY.md](VELOCITY.md) — no Via\* on YaPcore backend
