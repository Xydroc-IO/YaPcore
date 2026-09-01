# Vanilla JE protocol dumps

Packet ID dumps power first-party Via\* remaps (`PacketIdDump` / Mid / Forward).

| File | Role |
|------|------|
| [`index.json`](index.json) | Protocol → resource map (P4.10 source of truth) |
| `*/packets.json` | Per-version play/login/config IDs |

Add a new Mojang protocol: see [`docs/PROTOCOL_DUMPS.md`](../../../../docs/PROTOCOL_DUMPS.md).
Dumps are checked in; regenerate offline and commit when Mojang ships past the pin.

Honesty limits: [`docs/VIA_BACKWARDS_LIMITATIONS.md`](../../../../docs/VIA_BACKWARDS_LIMITATIONS.md).
