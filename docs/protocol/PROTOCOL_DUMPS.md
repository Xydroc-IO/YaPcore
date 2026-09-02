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

Protocol dumps under `src/main/resources/protocol/vanilla/` are **checked in**.
When Mojang ships past the pin, regenerate dumps with `minecraft-data` (or Mojang’s
packet specs), commit the new `packets.json` + `index.json` entry, then verify:

```bash
HOST=127.0.0.1 PORT=25566 ./scripts/protocol-matrix/run-matrix.sh
```

Optional: add a `companions` entry in the index when two dumps share a protocol but
use different packet names (see 775/776 merge).

## Until the next protocol exists

- Clients with protocol **≥777** use `ProtocolBand.V_FUTURE` and nearest dump (**776** today).
- Heuristic keepalive/chunk/spawn layouts remain the no-dump fallback in `ForwardTransformer`.
- Do **not** claim full ViaVersion parity for unreleased protocols.

## Related

- [VIA_GEYSER_PARITY.md](VIA_GEYSER_PARITY.md) — feature checklist  
- [VIA_BACKWARDS_LIMITATIONS.md](VIA_BACKWARDS_LIMITATIONS.md) — honesty notes (P4.11)  
- [VIA_GEYSER_PARITY.md](VIA_GEYSER_PARITY.md) — live matrix  
