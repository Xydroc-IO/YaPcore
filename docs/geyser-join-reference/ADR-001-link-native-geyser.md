# ADR-001: Link-native Bedrock (Geyser model, YaP rewrite)

## Status

Accepted (Phase 0)

## Context

Bedrock join must live on YaP Link (proxy), not as a chassis UDP product. Geyser’s session model (Bedrock upstream ↔ Java downstream) is the right architecture, but shipping a stock Geyser jar is not acceptable as the product path.

## Decision

1. **Geyser model on Link** — Port the Geyser-style session and translator pipeline into `yap-link-bedrock`, owned by Link.
2. **YaP rewrite from `vendor/geyser-ref` (MIT)** — GeyserMC’s reference tree is MIT-licensed. Use the checkout for behavior and wire shape; rewrite into first-party YaP code (study + reimplement under that MIT/rewrite policy; do not ship vendor Geyser as product).
3. **No Geyser jar as product** — `geyser-backup` may exist for emergency ops only; default and documented product path is Link-native.
4. **Phases gated** — Work proceeds Phase 0–7 with explicit Done criteria in `LINK_NATIVE_PORT.md`. Join is not claimed until Phase 3.

## Consequences

- New module `yap-link-bedrock` compiles into Link; modes `forwarder` | `native` | `geyser-backup`.
- Chassis Bedrock join path is temporary / fallback until cutover (Phase 7).
- Each phase must meet its Done checklist before the next is treated as complete.
