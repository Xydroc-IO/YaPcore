# Native Geyser JOIN path port (YaP first-party)

**Do not add Geyser as a Maven dependency or run the Geyser plugin.**  
This document maps Geyser source → YaP `com.yapcore.crossplay.bedrock.geyserport`.

Reference: `vendor/geyser-ref/` (shallow clone) and CFR extracts in this directory.  
Source jar (optional): `/home/xydroc/Downloads/Geyser-Spigot.jar`

## Honest deliverable

**Full Geyser = the entire translator tree** (inventory, entities, world, UI, …).  
**This ship = full JOIN path port** — encrypt → empty packs → `connect()` → `setServerRenderDistance` → chunks → wait `SetLocalPlayerAsInitialized`.  
Inventory / entity / gameplay translators are **not** ported yet.

Join owner: **`YapGeyserSession`** (sole path after pack COMPLETED).  
`BedrockLoginFlow` is thin: Floodgate auth → `LoginEncryptionUtils` → packs → `YapGeyserSession.join()`.

---

## Class / method map

| Geyser | YaP |
|--------|-----|
| `GeyserSession` (join subset) | `YapGeyserSession` |
| `GeyserSession.connect` | `YapGeyserSession.connect` |
| `GeyserSession.startGame` | `YapGeyserSession.startGame` |
| `GeyserSession.buildStartGamePacket` | `YapGeyserSession.buildStartGamePacket` |
| `GeyserSession.configureExperiments` | `YapGeyserSession.configureExperiments` |
| `GeyserSession.syncEntityProperties` | `YapGeyserSession.syncEntityProperties` |
| `GeyserSession.sendRegistryDefinitions` | `YapGeyserSession.sendRegistryDefinitions` |
| `GeyserSession.sendInitialPlayerState` | `YapGeyserSession.sendInitialPlayerState` |
| `GeyserSession.sendInitialGameRules` | `YapGeyserSession.sendInitialGameRules` |
| `GeyserSession.resetTimeParameters` / `synchronizeTime` | same names on `YapGeyserSession` |
| `GeyserSession.setServerRenderDistance` / `recalculateBedrockRenderDistance` | same on `YapGeyserSession` |
| `LoginEncryptionUtils.startEncryptionHandshake` | `LoginEncryptionUtils.startEncryptionHandshake` |
| UpstreamPacketHandler LOGIN_SUCCESS + empty packs | `LoginEncryptionUtils.sendLoginSuccessAndEmptyPacks` |
| `ChunkUtils.sendEmptyChunks` / `sendEmptyChunk` / `squareToCircle` / `updateChunkPosition` | `ChunkUtils` (same names) |
| `JavaLoginTranslator` (Bedrock S2C after connect) | `JavaLoginTranslator.afterConnect` |
| `BedrockRequestChunkRadiusTranslator` | `BedrockRequestChunkRadiusTranslator.translate` |
| `BedrockSetLocalPlayerAsInitializedTranslator` | `BedrockSetLocalPlayerAsInitializedTranslator.translate` |
| Wire encode | `CloudburstSession.encode` / palette helpers in `cloudburst.*` |

---

## Join order (locked to Geyser)

1. Encrypt: handshake → `enableEncryption` → **immediate** LOGIN_SUCCESS + empty `ResourcePacksInfo`
2. Pack responses → empty `ResourcePackStack` → on COMPLETED → `YapGeyserSession.join()`
3. `join()` → `connect()` then `JavaLoginTranslator.afterConnect(DEFAULT_JAVA_VIEW)`
4. `connect()` = Geyser wire (vanilla height −64..320), including `ChunkUtils.sendEmptyChunks(pos, 0, false)`
5. `setServerRenderDistance(view)` → `ChunkRadiusUpdated(squareToCircle(view))`
6. Columns: Paper hashed LevelChunks when available; else **`ChunkUtils.sendEmptyChunks(pos, view, false)`** (Geyser API — not a flat stand-in)
7. Wait **real** `SetLocalPlayerAsInitialized` (0x71) — **no soft-init**
8. On 0x71: mark ready + `ChunkUtils.updateChunkPosition`
9. `RequestChunkRadius` = store client distance only (`BedrockRequestChunkRadiusTranslator`)

### `connect()` packet types (vanilla −64..320)

1. VoxelShapes (26.20+)  
2. StartGame  
3. ItemComponent  
4. LevelChunk ×1 (spawn column, Geyser EMPTY_CHUNK)  
5. BiomeDefinitionList  
6. AvailableEntityIdentifiers  
7. CameraPresets  
8. CreativeContent  
9. PlayStatus PLAYER_SPAWN  
10. SetCommandsEnabled  
11. UpdateAttributes (movement)  
12. GameRulesChanged (initial)  
13. SetTime(0)  
14. GameRulesChanged (dodaylightcycle=false)  

**Not in connect():** CraftingData, PlayerList, Inventory, Respawn, MovePlayer, ChunkRadiusUpdated, NetworkChunkPublisherUpdate, AvailableCommands.

---

## Codec footnotes (not architecture)

| Topic | Note |
|-------|------|
| `AuthoritativeMovementMode` | Geyser leaves unset; v818+ **omits** mode on wire. YaP Cloudburst encode for proto 2169 still needs explicit **CLIENT** — set in `buildStartGamePacket` with that comment. |
| Empty chunks after radius | Geyser streams Java LevelChunks after `setServerRenderDistance`. Without Paper columns, YaP calls **Geyser** `ChunkUtils.sendEmptyChunks` — same EMPTY_CHUNK payload. First login uses `forceUpdate=false` (dim-switch uses `true`). |
| Soft-init from Move/AuthInput | Never — wait real 0x71 only. |
| Dual join paths | Removed — only `YapGeyserSession`. No `GeyserJoinSequence` / soft-init / bisect flags. |

---

## NOT ported yet (full Geyser tree)

- Java ↔ Bedrock inventory translators  
- Entity spawn / metadata / attribute translators  
- Full Java LevelChunk translator (beyond Paper snapshot → hashed LevelChunk)  
- Dimension switch / portal flows  
- Forms / Cumulus beyond existing YaP FormService  
- Command translators / crafting / recipes beyond join stubs  
- Skin / cape upload paths beyond Floodgate defaults  

---

## Success markers (logs)

1. `BE ServerToClientHandshake + enableEncryption` … `(LoginEncryptionUtils)`  
2. `BE LOGIN_SUCCESS + ResourcePacksInfo empty` … `pack=empty-handshake`  
3. `BE login … path=yap-geyser-session`  
4. `BE YapGeyserSession.join/connect codec=v…`  
5. `BE YapGeyserSession.connect complete …`  
6. `BE JavaLoginTranslator afterConnect … cols=…`  
7. `BE REQUEST_CHUNK_RADIUS store-only (BedrockRequestChunkRadiusTranslator)`  
8. `BE SetLocalPlayerAsInitialized mark-ready …`  
9. `BE JOIN_OK path=yap-geyser-session-join …`  

Session should last **>10s** without IC-90 / client disconnect.

---

## Removed / forbidden on join path

- Soft-init from PlayerAuthInput / MovePlayer  
- Init-fallback JOIN_OK timers  
- “JavaLogin stand-in” / “prefer 22:18 over Geyser” as architecture  
- Competing `GeyserJoinSequence` vs session dual paths  
- LevelChunk flood from `RequestChunkRadius`  
- Flat solid columns as the primary join fill (use Geyser EMPTY_CHUNK or Paper hashed)
