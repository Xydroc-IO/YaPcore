# Protocol packet dumps (P4.10)

**Paper pin:** protocol **776** (Minecraft **26.2**).  
Older JE clients remap **backwards** onto 776. Newer clients (when Mojang ships past the pin) remap **forward** onto 776 via `ForwardTransformer`.

## Layout

```
src/main/resources/protocol/vanilla/
  index.json          ← authoritative protocol → resource map
  1.20.2/packets.json
  …
  26.2/packets.json
```

`PacketIdDump` loads `index.json` first. Dropping a new dump + index entry is enough for
mid/forward ID remaps — no Java switch edit required for P4.10 future protocols.

## Add the next Mojang protocol (when shipped)

```bash
cd scripts/bench/bots && npm i minecraft-data
cd ../../..

# After minecraft-data (or Mojang) publishes the new PC version:
node scripts/generate-protocol-dump.mjs latest
# or explicit:
node scripts/generate-protocol-dump.mjs --protocol 778
node scripts/generate-protocol-dump.mjs 26.3

# Verify Forward path (newer client → Paper 776):
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-matrix.sh

# Catalogs (items/blocks/entities) if the band needs name bridges:
node scripts/generate-protocol-catalogs.mjs
```

Commit `packets.json` + updated `index.json`. Optional: add a `companions` entry in the index
when two dumps share a protocol but use different packet names (see 775/776 merge).

## Until the next protocol exists

- Clients with protocol **≥777** use `ProtocolBand.V_FUTURE` and nearest dump (**776** today).
- Heuristic keepalive/chunk/spawn layouts remain the no-dump fallback in `ForwardTransformer`.
- Do **not** claim full ViaVersion parity for unreleased protocols.

## Related

- [VIA_GEYSER_PARITY.md](VIA_GEYSER_PARITY.md) — feature checklist  
- [VIA_BACKWARDS_LIMITATIONS.md](VIA_BACKWARDS_LIMITATIONS.md) — honesty notes (P4.11)  
- [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md) — live matrix  
