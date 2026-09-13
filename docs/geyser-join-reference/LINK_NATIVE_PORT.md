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
- [ ] **Hard Done (ops):** phone → real `0x71` → `JOIN_OK` → session **>60s** without IC-90. JOIN_PROBE `got0x71=true`.

**Landed in tree:** `LinkBedrockSession`, `ChunkUtils`, `JavaLoginTranslator`, `JavaLevelChunkTranslator`, `BedrockSetLocalPlayerAsInitializedTranslator`, Cloudburst palettes under `yap-link-bedrock` resources; `BedrockSessionHost` feeds Java packets into translators and Bedrock C2S into 0x71 / RequestChunkRadius (store-only).  
**Do not claim join works until the phone gate above passes.**

## Phase 4 — World + movement — **code landed; playtest gate pending**

- [x] `JavaBlockUpdateTranslator` — JE `block_update` / `section_blocks_update` → Bedrock `UpdateBlock` or LevelChunk refresh (best-effort; non-air → stone runtime).
- [x] `BedrockMoveTranslator` — `PlayerAuthInput` / `MovePlayer` → JE `move_player_pos_rot`.
- [x] `JavaMoveTranslator` — JE `player_position` / self entity teleport → Bedrock `MovePlayer` + accept teleport.
- [x] `JavaLevelChunkTranslator` — passes dimension + chunk X/Z; EMPTY_CHUNK until full remap; after 0x71 spawn column empty + stone `forceUpdate` (Geyser dim-switch style).
- [x] Wired in `BedrockSessionHost` / `LinkBedrockSession` + expanded `JavaDownstreamClient` play dispatch.

**Playtest gate (not Done):** walk, see terrain update, no kick for 5+ minutes on empty/overworld spawn.

## Phase 5 — Inventory + break/place + combat — **code landed; playtest gate pending**

- [x] `BedrockInventoryTranslator` — `MobEquipment` / `ItemStackRequest` / `InventoryTransaction` → JE held-item basics.
- [x] `JavaInventoryTranslator` — JE `container_set_content` / `container_set_slot` → Bedrock inventory packets (air-filled; item remap deferred).
- [x] `BedrockActionTranslator` — `PlayerAction` dig + `InventoryTransaction` place/attack → JE `player_action` / `use_item_on` / `interact`.
- [x] Wired into session packet dispatch.

**Playtest gate (not Done):** hotbar, break/place dirt/stone, hit a mob or player.

## Phase 6 — Entities, chat, commands, forms — **code landed; playtest gate pending**

- [x] `JavaEntityTranslator` — JE `add_entity` / `remove_entities` → Bedrock `AddEntity` / `RemoveEntity` (simplified identity).
- [x] `ChatTranslator` — Bedrock `Text` ↔ JE chat / system_chat (best-effort unsigned).
- [x] `BedrockCommandTranslator` — `CommandRequest` → JE `chat_command` + system ack.
- [x] `BedrockFormBridge` — `ModalFormResponse` → log + optional FormService hook stub + ack text.
- [x] Wired into session packet dispatch.

**Playtest gate (not Done):** see other players/mobs, chat both ways, run `/help` or YaP command, open one form.

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
