# How to use YaPPacks

**Jar:** `yap-packs.jar`  
Multi-active resource pack helpers (`/yappacks`). Primary pack usually comes from Paper `server.properties`.

## Ops workflow

1. Build: `./scripts/packs/build-default-resourcepack.sh`
2. Serve zip (YaPcore `:8081/pack/…` or CDN)
3. Set `resource-pack` URL + matching `resource-pack-sha1` on each Folia `server.properties`
4. Restart backends; players reconnect and accept

YaPPacks can push **extra** packs beyond the login prompt.

## Troubleshoot

| Symptom | Fix |
|---------|-----|
| Missing custom item textures | SHA1 mismatch — update props to match zip |
| Prompt every join | Normal if hash changes; keep hash in sync after rebuilds |

## Related

[CLIENTS_AND_PACKS.md](../../network/CLIENTS_AND_PACKS.md)
