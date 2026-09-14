# Link-native Bedrock port (Geyser → Link)

Checklist for the phased port. Mark a phase **Done** only when its criteria pass. See [ADR-001](ADR-001-link-native-geyser.md).

## PLAYABLE BASELINE (locked — do not regress)

Ship gate for “Bedrock playable on same Folia as JE” (Link → `127.0.0.1:25566` Via → Folia).

| Setting | Locked value | Why |
|---------|--------------|-----|
| Backend | `127.0.0.1:25566` (`try=lobby`) | Same world as JE |
| `connect()` CreativeContent | **empty** (~4B) | Full catalog → IC-90 |
| `blockNetworkIdsHashed` | **true** | Match LevelChunk `network_id` |
| `inventoriesServerAuthoritative` | **false** | E opens inventory |
| `serverAuthoritativeBlockBreaking` | **true** | Digs on AuthInput `playerActions` |
| Post-0x71 abilities + AvailableCommands | **on** | Interact / `/` list; includes `OPEN_CONTAINERS` |
| PlayerList ADD-self / remotes | Steve+geometry via `LinkTrustedSkin`; encode sanity gate; kill-switch only `-Dyap.link.bedrock.skipPlayerList=true` | Empty-geo ~16KB → IC-90 (probe `051738`) |
| Miss→block | **air**, not stone | All-stone world after hashed remap |
| Stand-on | lift / punch-air; **no 3×3** stone platform (size=1 timeout only) | Don’t fight REAL terrain |

**Probe success criteria** (`link-data/logs/bedrock-join/*.log`):

1. `JOIN_OK got0x71` and `got0x71_SetLocalPlayerAsInitialized=true`
2. SUMMARY `java_backend=127.0.0.1:25566` (not `?`)
3. `post_0x71 PlayerList ADD-self … geometry=ok` (not `SKIPPED sanity=` / not gated)
4. `AvailableCommands` ≫ 9B (vanilla ~2KB+)
5. `UpdateAbilities` present post-0x71
6. No disconnect within ~30s after JOIN_OK (IC-90)
7. `uniqueRt` > 1 / not all-stone; stand-on `platform=false` when solids present
8. If session stays open: `auth blockActions` / `BE→JE dig` events

Do **not** flip kill-switches without a matching probe event + comment.

---

## Phase 0 — Repo + config foundation — **Done (module/config)**

- [x] Add `yap-link-bedrock` module + wire into `settings.gradle.kts` / Link shadow jar.
- [x] Config: `bedrock-mode=native | forwarder | geyser-backup` in LinkConfig + dashboard/applier.
- [x] Refresh/confirm `vendor/geyser-ref` checkout; document license/rewrite policy (ADR-001).

**Landed:** module compiles into `yap-link.jar`; modes selectable; `forwarder` unchanged.

## Phase 1 — Link RakNet session + Floodgate (no Folia yet) — **Done (handshake)**

- [x] Connected RakNet on Link (OCR → frames → encrypt).
- [x] Port encrypt + empty pack handshake / Floodgate.

**Landed:** phone reaches `ResourcePack` COMPLETED on Link; JOIN_PROBE on Link shows encrypt + packs; no chassis Bedrock UDP required for that handshake.

## Phase 2 — Java downstream client — **code landed (phone gate open)**

- [x] Per Bedrock session: JE connection Link → Folia (`bedrock-backend`, default `127.0.0.1:25566`).
- [x] Offline handshake with Floodgate username+UUID; Velocity modern inject from `linkHome/forwarding.secret` via `yap-protocol` `ModernForwarding`.
- [x] Login → Configuration → Play enough to log Login Success, accept config, log Login Play + LevelChunk.
- [x] Session wiring: packs COMPLETED → `JavaDownstreamClient` + `LinkBedrockSession`.

**Landed in tree:** `com.yapcore.link.bedrock.downstream.JavaDownstreamClient`; depends on `:yap-protocol` (`projectDir` = `yap-first-party/link/protocol`).  
**Not marked Done for ops until:** live logs show Java `Login Success` / config complete / chunks on a real Folia backend.

## Phase 3 — Join translators until real `0x71` — **code path landed; phone gate required**

- [x] Port join chain into Link packages: `LinkBedrockSession.connect` / StartGame / `ChunkUtils` empties radius 0.
- [x] `setServerRenderDistance` after connect (on Java Login Play via `JavaLoginTranslator.afterConnect`).
- [x] Java LevelChunk → Bedrock LevelChunk (Cloudburst EMPTY_CHUNK payload; full JE→BE remap = Phase 4).
- [x] Wait **real** `SetLocalPlayerAsInitialized` (0x71) → `JOIN_OK` + JOIN_PROBE finish; publisher only after 0x71 (no soft-init).
- [x] **Hard Done (ops):** phone → real `0x71` → `JOIN_OK` → session **>60s** without IC-90. JOIN_PROBE `got0x71=true`.

**Landed in tree:** `LinkBedrockSession`, `ChunkUtils`, `JavaLoginTranslator`, `JavaLevelChunkTranslator`, `BedrockSetLocalPlayerAsInitializedTranslator`, Cloudburst palettes under `yap-link-bedrock` resources; `BedrockSessionHost` feeds Java packets into translators and Bedrock C2S into 0x71 / RequestChunkRadius (store-only).  
**M1 certify (2026-09-13):** newest probes pass PLAYABLE BASELINE — phone `20260912-171913-Xydroc` (`JOIN_OK`, `got0x71=true`, `java_backend=127.0.0.1:25566`, PlayerList `geometry=ok`, AvailableCommands 8952B, UpdateAbilities, `uniqueRt=32`, `platform=false`, durationMs≈65s, dig/auth events) and bot `20260913-010948-BeCombatBot` (same join gate, durationMs≈60s).

## Phase 4 — World + movement — **code landed; M1 join gate passed 2026-09-13**

- [x] `JavaBlockUpdateTranslator` — JE `block_update` / `section_blocks_update` → Bedrock `UpdateBlock` (per-cell section remap with hashed network ids).
- [x] `BedrockMoveTranslator` — `PlayerAuthInput` / `MovePlayer` → JE `move_player_pos_rot`.
- [x] `JavaMoveTranslator` — JE `player_position` / self entity teleport → Bedrock `MovePlayer` + accept teleport; mid-game REJECT_BURIED stand-on TELEPORT disabled after SPAWNED.
- [x] `JavaLevelChunkTranslator` — passes dimension + chunk X/Z; REAL pre-0x71; forceUpdate stone poke only when no REAL yet.
- [x] Wired in `BedrockSessionHost` / `LinkBedrockSession` + expanded `JavaDownstreamClient` play dispatch.

**M3 dig/place (2026-09-13):** section cells → UpdateBlock; dig without optimistic air; place uses AuthInput/InventoryTransaction click hit + face; dig FX from cached block runtime; size=1 timeout platform retained only as emergency fallback.

## Phase 5 — Inventory + break/place + combat — **M2/M4 code landed 2026-09-13**

- [x] `BedrockInventoryTranslator` / `BedrockItemStackRequests` — full ItemStackRequest take/place/swap/drop + ItemStackResponse; MobEquipment held sync.
- [x] `JavaInventoryTranslator` — JE `container_set_content` / `container_set_slot` → real items via `JeItemRegistry` identifiers (+ chest window cache).
- [x] `BedrockInventoryOpen` — E open anytime + ContainerClose echo + JE container_close + snapshot push.
- [x] `JavaOpenScreenTranslator` — chest/`open_screen` + slot sync.
- [x] `BedrockActionTranslator` — dig/place with correct face/hit; AuthInput ItemStackRequest.
- [x] `BedrockCombat` + `JavaEntityCombatTranslator` — BE→JE `SB_ATTACK`; JE hurt/set_health; mob HP via set_entity_data/attributes; death remove.
- [x] Unit tests: `JeItemRegistryTest` (dirt/oak_log), `JavaPlayWireAttackTest` / `JavaPlayInventoryWireTest` (attack id + container_click).

**Playtest gate:** phone open/close E, move stacks, chest; break/place dirt/stone/log; melee zombie + JE player.

## Phase 6 — Entities, chat, commands, forms — **N1–N4 code landed 2026-09-13**

- [x] `JavaEntityTranslator` — add/remove + relative moves + SetEntityMotion + metadata nametags.
- [x] `ChatTranslator` — Bedrock Text ↔ JE chat / system_chat (sanitized).
- [x] `BedrockCommandTranslator` + `JavaCommandsTranslator` — CommandRequest → JE + AvailableCommands from JE `commands` tree.
- [x] `BedrockFormBridge` — Floodgate `floodgate:form` → ModalFormRequest; ModalFormResponse → plugin channel (not stub).
- [x] `JavaSoundTranslator` — explicit break/place/hurt/ambient registry + heuristics.
- [x] Wired into session packet dispatch.

**Playtest gate:** forms from Folia plugins; mobs path; `/` lists plugin cmds; break/hurt sounds.

## Phase 7 — Product cutover — **defaults switched to native**

- [x] Default `bedrock-mode=native` in `LinkConfig`, `config/defaults/link.properties`, `LinkProcessManager` seed, `ProtocolEdgeConfig`, `config/defaults/server.properties` (chassis `bedrock-enabled=false`).
- [x] `BedrockModeApplier.normalize`: blank / unknown → **native**; `forwarder` / `first-party` still map to forwarder.
- [x] Dashboard / ProtocolEdgeConfig accept `native`; Link tab shows native | forwarder | geyser-backup; status exposes mode.
- [x] Chassis: `isBedrockEnabled()` false when mode is `native` or `geyser-backup` — DualStack does not bind Bedrock UDP.
- [x] Docs: this file + CROSSPLAY / YAP_LINK note Link-native as product Bedrock path.

**Ops Done still requires:** phone join works with chassis Bedrock UDP off on the product path.

## Non-goals (until after Phase 7)

- Porting every Geyser translator in one PR.
- Calling Phase 0–2 “complete Bedrock.”
- Stock Geyser jar as the product answer.
- Claiming Phases 4–6 playtest-complete without phone gate.
