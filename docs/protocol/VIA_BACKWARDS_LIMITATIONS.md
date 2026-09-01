# ViaBackwards-class limitations (P4.11 honesty notes)

**Audience:** operators and partners comparing YaPcore’s first-party multi-version path
to ViaVersion + ViaBackwards.

YaPcore’s JE floor is **1.20.2+ → Paper 26.2**. We aim for **behavioral** parity with
ViaBackwards on that floor — not a bytecode clone, and not “every edge case Via has
ever filed.”

This page lists **known limitations** so we do not oversell.

---

## Product floor vs best-effort

| Clients | Stance |
|---------|--------|
| **1.20.2 – current** onto Paper 26.2 | Product DoD — join/spawn matrix + mid play remaps |
| **1.19.4** | Canary join/spawn |
| **Pre-1.19 / ViaRewind (1.8–1.16)** | Best-effort join only — **not** play-depth DoD |

---

## Limitations we share with (or inherit from) ViaBackwards-class translation

These are inherent to translating newer worlds to older clients (or mid bands), not
YaP-only bugs:

| Area | Honest behavior |
|------|-----------------|
| **Smithing table (1.20+ templates)** | Older mid clients may not drive the full smithing UI the way a same-version client does. Same class of issue ViaBackwards documents for &lt;1.19.4 on 1.20+ servers. Prefer same-era clients for smithing workflows, or accept degraded UI. |
| **Sound mappings** | Incomplete / approximate. Missing or remapped sounds may be silent or play a substitute. Unmapped sound packets are **dropped** (never kick). Do not rely on exact sound IDs across bands. |
| **New blocks / items / entities** | Shown as placeholders or nearest older equivalents when the client catalog lacks the name. Unknown entity types map to **pig** (else armor_stand). Unknown block states map to **stone** (else air). Hitboxes/AI remain server-authoritative on Paper. |
| **Component items (1.20.5+) ↔ NBT (≤1.20.4)** | Cross-era remaps strip opaque component/NBT payloads when tables are incomplete — type + count preserved; enchant/custom data may drop. |
| **Particles / exotic metadata** | Fail-soft: metadata loop may truncate rather than corrupt the packet stream. |
| **Inventory click edge cases** | Mid `window_click` / creative bodies are remapped; rare desyncs under rapid shift-click or modded containers can still occur (also reported upstream in Via\* ecosystems). |
| **Chat signing / report flows** | Unsigned / unsigned-chat product path (`yap-chat`) may differ from vanilla signed-chat expectations on older clients. |
| **Resource packs** | Config-phase prompts are **auto-acked** when `resource-pack-forced=false` so mid clients join without blocking on optional packs. Set `resource-pack-forced=true` to require client download. Play-phase YaPPacks pushes unchanged. |

---

## Limitations that are YaP-specific (not Via jars)

| Area | Honest behavior |
|------|-----------------|
| **No Via\* plugin API** | Plugins that *require* ViaVersion/ViaBackwards APIs will not see those jars. Use YaP FormService / FloodgateAuth / Paper APIs instead. |
| **Dump lag behind Mojang** | Until a new dump lands in `index.json`, brand-new clients use nearest-dump + heuristics ([PROTOCOL_DUMPS.md](PROTOCOL_DUMPS.md)). |
| **Rewind play depth** | Explicitly out of product DoD — do not market 1.8 PvP parity. |
| **Bit-identical packets** | Out of scope — we remap by name/layout, not via Via’s exact transformers. |

---

## Claim language (copy/paste)

**Allowed**

- “First-party ViaBackwards-class for **1.20.2+** onto Paper 26.2; no Via\* jars.”
- “Join/spawn matrix green under compression; mid play remaps dump-backed.”
- “Newer-than-server clients use ForwardTransformer when dumps exist; otherwise nearest dump.”

**Forbidden**

- “Full ViaRewind play parity.”
- “Identical to ViaBackwards in every edge case (smithing, sounds, particles).”
- “Supports every future snapshot the day Mojang cuts it” (until P4.10 dump is checked in).

---

## Operator guidance

1. Pin public advertising to **1.20.2+** (or your tested subset).
2. For economy / smithing / heavy inventory UIs, encourage clients near the server version.
3. When Mojang ships a new protocol: follow [PROTOCOL_DUMPS.md](PROTOCOL_DUMPS.md), then re-run the JE matrix before claiming forward parity.
4. Keep Via\* / Geyser jars **out** of the YaPcore product `plugins/` path.

---

## Related

| Doc | Topic |
|-----|--------|
| [VIA_GEYSER_PARITY.md](VIA_GEYSER_PARITY.md) | Full feature checklist |
| [PROTOCOL_DUMPS.md](PROTOCOL_DUMPS.md) | P4.10 dump workflow |
| [PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md) | Live matrix / slices |
| [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) | Client matrix |
| [XBOX_RETAIL_CAPTURE.md](XBOX_RETAIL_CAPTURE.md) | Optional live Mojang Xbox JWT capture |

*Not affiliated with Mojang, Microsoft, or the ViaVersion project.*
