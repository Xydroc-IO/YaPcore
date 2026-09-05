# Resource packs (auto-download on join)

Drop a Java `.zip` (or Bedrock `.mcpack`) here, set it active, restart.

With `game-authority=folia` (default) or `game-authority=paper` (legacy), YaPcore
syncs the active pack into the game’s `server.properties`. On join, the client gets
Minecraft’s resource-pack prompt and **downloads automatically** from the pack
HTTP/edge URL.

```properties
resource-pack-enabled=true
resource-pack-file=yapcore-default.zip
resource-pack-forced=true
```

- **Forced** (`resource-pack-forced=true`): decline → kick
- **Default pack:** `yapcore-default.zip` = Faithful 64x + **YaP Skies** + **YaP Water**
  (built by `gradle prepareClientPack` / `shadowJar`)
- Skies overlay: [`yap-skies/`](yap-skies/) (`python3 scripts/generate-yap-skies.py`)
- Water/weather: `python3 scripts/generate-yap-water.py` (into `yap-skies/`)
- Default pack is Faithful 64x + YaP Skies + YaP Water (no vehicles/abilities overlays)
- Iris / Complementary volumetric shaders are still client-only — YaP ships an
  optional Fabric stack (official Sodium pin + YaP Iris + YaP Shaders); see
  [docs/network/CLIENTS_AND_PACKS.md](../docs/network/CLIENTS_AND_PACKS.md)
