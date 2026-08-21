# Resource packs (auto-download on join)

Drop a Java `.zip` (or Bedrock `.mcpack`) here, set it active, restart.

With `game-authority=paper`, YaPcore syncs the active pack into Paper
`server.properties`. On join, the client gets Minecraft’s resource-pack prompt
and **downloads automatically** from the pack HTTP/edge URL.

```properties
resource-pack-enabled=true
resource-pack-file=yapcore-default.zip
resource-pack-forced=true
```

- **Forced** (`resource-pack-forced=true`): decline → kick
- **Default pack:** `yapcore-default.zip` = Faithful 64x + YaP Vehicles
  (built by `gradle prepareClientPack` / `shadowJar`)
- Standalone vehicles overlay: [`yap-vehicles.zip`](yap-vehicles.zip)
- Shaders / realistic skies are **not** resource packs — see
  [docs/CLIENTS_AND_PACKS.md](../docs/CLIENTS_AND_PACKS.md)
