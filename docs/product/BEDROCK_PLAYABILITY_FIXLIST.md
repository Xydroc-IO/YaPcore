# Bedrock playability fix list

Shared-world blockers for Java + Bedrock on the same Folia world.
**Agents: pick from this list; mark status when done. Do not drop items without a fix or an explicit defer note.**

| ID | Status | Symptom | Suspected area | Notes |
|----|--------|---------|----------------|-------|
| BE-PING-01 | done | Bedrock server list always shows **“loading ping”** | Link UDP bind / nginx steal / `bedrock-enabled` default | **Live verified** 2026-09-12: UDP `:19132`/`:25565` → `0x1c`, 13 MOTD fields, trailing ports OK, color codes stripped (`YaPcore Test Server`). Wiring: dual-bind seed, identity mirror, nginx omits UDP in native, `sanitizeMotdPart`. |
| BE-COMBAT-01 | in_progress | **Cannot attack mobs** on Bedrock | Miss-swing racing entity attack (MC-255058) + Grim | Grim Floodgate OK. Probe showed `MISSED_SWING` before every `BE→JE attack` (resets AttackStrengthTicker + early `client_tick_end`). Fix: defer miss swing on event-loop; cancel on entity attack; miss = swing only (no tick-end). Deployed Link 2026-09-13 00:51. **Remaining:** in-game mob hit → expect `java_hurt→be` on target. |
| BE-BREAK-01 | done | Breaking blocks **sounds like a tool breaking** | LevelSoundEvent extraData | Dig extraData never `-1` (`BedrockDigEffects.resolveDigExtraData` + `BedrockDigEffectsTest`). |

## Working rules for subagents

1. Treat this file as the backlog for Bedrock feel/playability.
2. When starting work, set the row to `in_progress` and note the agent/task briefly in Notes.
3. When fixed, set Status to `done` and add the key file(s) changed.
4. Prefer Link-native path (`bedrock-mode=native`) — that is the product default.
5. JE and BE must share the same world/auth/MOTD identity (see `LinkIdentityMirror`).

## Related code (starting points)

- Ping: `yap-first-party/link/bedrock/.../BedrockSessionHost.java`, `.../raknet/RakNetUnconnected.java`, `src/.../gateway/BedrockUdpBoot.java`
- Combat: `BedrockCombat.java`, `BedrockActionTranslator.java`, `JavaPlayWire.java`
- Break sounds: `BedrockDigEffects.java`, `JavaSoundTranslator.java`
