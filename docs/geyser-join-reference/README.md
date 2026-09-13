# Geyser join reference (CFR + vendor/geyser-ref shallow clone)

Source jar: local Geyser Spigot jar (operator lab)  
Upstream clone: `vendor/geyser-ref/` (sparse; gitignored)

**Native first-party port:** see **[`NATIVE_PORT.md`](NATIVE_PORT.md)** — package  
`com.yapcore.crossplay.bedrock.geyserport` / **`YapGeyserSession`** is THE chassis join path.  
Product default Bedrock path is **YaP Link-native** (`yap-link-bedrock`) — [LINK_NATIVE_PORT.md](LINK_NATIVE_PORT.md) · [YAP_LINK_NATIVE.md](../network/YAP_LINK_NATIVE.md).

## Honest scope

**Full Geyser = entire translator tree.** This ship = **full JOIN path port** only.  
Inventory / entity / gameplay translators are not ported yet.

## Methods ported into YaP

| Geyser | YaP |
|--------|-----|
| `GeyserSession` join subset | `YapGeyserSession` |
| `connect` / `startGame` / `buildStartGamePacket` / `configureExperiments` | same method names on `YapGeyserSession` |
| `syncEntityProperties` / `sendRegistryDefinitions` / `sendInitialPlayerState` / `sendInitialGameRules` / `resetTimeParameters` | same on `YapGeyserSession` |
| `setServerRenderDistance` | `YapGeyserSession.setServerRenderDistance` → ChunkRadiusUpdated |
| `ChunkUtils.sendEmptyChunks` / `squareToCircle` / `updateChunkPosition` | `ChunkUtils` (Geyser EMPTY_CHUNK payload) |
| `LoginEncryptionUtils` | `LoginEncryptionUtils` |
| `JavaLoginTranslator` (Bedrock S2C after connect) | `JavaLoginTranslator.afterConnect` |
| `BedrockRequestChunkRadiusTranslator` | `BedrockRequestChunkRadiusTranslator` |
| `BedrockSetLocalPlayerAsInitializedTranslator` | `BedrockSetLocalPlayerAsInitializedTranslator` |

`BedrockLoginFlow` is thin: Floodgate → encrypt → empty packs → `YapGeyserSession.join()`.

## connect() packet order

See `NATIVE_PORT.md`. Empty chunks after `setServerRenderDistance` are **Geyser `ChunkUtils.sendEmptyChunks`**, or Paper hashed LevelChunks when available — not a flat stand-in.
