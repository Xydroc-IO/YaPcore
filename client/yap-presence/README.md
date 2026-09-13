# yap-presence

Fabric **client** mod for Minecraft **26.2** (version **1.0.0**). It does not go on YaPcore or Folia.

Renders Bedrock-ported player geometry (and skin texture URL) pushed by YaPTailor over
plugin channel **`yap:presence`**. Vanilla / Bedrock clients ignore this channel.

## Install

1. Fabric Loader **0.19.3+** for Minecraft **26.2**
2. Drop `yap-presence-1.0.0.jar` into `.minecraft/mods/` (only one `yap-presence-*.jar`)
3. Join YaPcore / Folia as usual
4. Server needs YaPTailor with `yap:presence` (HELLO → SKIN)

## Channel `yap:presence`

Raw UTF-8 payloads (same style as `yap:bag`):

| Direction | Payload |
|-----------|---------|
| Client→Server | `HELLO` — on join; request presence skins |
| Server→Client | `SKIN\|<uuid>\|<slim 0\|1>\|<skinPngUrl>\|<geometryJsonBase64>` |

- `geometryJsonBase64` is Base64 of Bedrock `minecraft:geometry` JSON or `yap.geometry/1`
- Client stores a `PresenceSkin` per UUID (including local player) and renders cubes with the downloaded PNG

## Build

```bash
cd client/yap-presence
./gradlew build
```

Jar: `build/libs/yap-presence-1.0.0.jar`

Or via repo script: `scripts/build-yap-client-render.sh`
