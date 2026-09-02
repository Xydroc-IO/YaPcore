# Vanilla & modded Java clients

**Default product path (`game-authority=folia`, `folia-jar-source=build`):**
**YaP-Folia** owns the public JE protocol after boot (not stock Fill). Legacy
`game-authority=paper` uses Paper the same way. YaPcore’s native multi-version
bands apply when `game-authority=native`. See [QUICK_START.md](../start/QUICK_START.md),
[CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md), and [CROSSPLAY.md](../network/CROSSPLAY.md).

YaPcore’s native path speaks **real Minecraft Java protocol** with **built-in multi-version bands**
(`ProtocolBand`). Each client gets its own codec path — no external translators.

## Compatibility (default)

| Setting | Default | Effect |
|---------|---------|--------|
| `backwards-compatible` | `true` | Accept registered JE/BE protocol IDs; map unknowns to nearest band |

Native bands cover **1.8 → 26.2**. Fabric/Quilt OK; Forge/NeoForge client noise is tolerated.

## What can connect

| Client | Expected |
|--------|----------|
| **Vanilla** | Server list ping + join (offline-mode) |
| **Fabric / Quilt** | Same as vanilla for client-side mods |
| **NeoForge / Forge** | Vanilla path with loader-compat |

## Requirements

```properties
online-mode=false
java-enabled=true
backwards-compatible=true
port=25566
```

1. Start (`./scripts/gui.sh` or `./scripts/start.sh`) — **you** start Link from the GUI/dashboard when using the proxy path
2. Connect:
   - **Through Link (product path):** `127.0.0.1:25565`
   - **Direct Via edge:** `127.0.0.1:25566`
3. MOTD / player counts should appear in the server list

### Logs: STATUS ping vs join

Server-list refreshes use handshake **intent=1** (STATUS). The client closes after
the ping — that is normal and is **not** logged as `JE JOIN FAILED` anymore.
Actual joins use **intent=2** (LOGIN) and should reach PLAY.

### Link join: `Frame length cannot be zero`

Vanilla rejects outer frames whose length VarInt is `0`. Link must send Set Compression
**uncompressed**, then enable zlib on the **same** outbound handler that adds length
prefixes (`McOutboundPacketEncoder`). After Link protocol changes, refresh
**repo-root** `yap-link.jar` (GUI preference) via shadowJar + copy or
`gradle publishReleasesFolder`.

Public domain / nginx: [NETWORKING.md](NETWORKING.md), [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md).

## Configuration dumps (why joins used to fail)

Modern JE clients (1.20.2+) require a **vanilla-complete** Configuration phase:
full synchronized registry ID lists + **all** Update Tags. Tags are never loaded
from known packs — missing one (`infiniburn_*`, `sulfur_cube_archetype/*`, …)
→ client `Network Protocol Error` at Finish Configuration.

YaPcore ships official dumps under `src/main/resources/protocol/vanilla/<version>/`:

| File | Source |
|------|--------|
| `registryEntries.json` | Entry IDs from that release’s server jar datapack |
| `networkTags.nbt` | Full Update Tags (IDs from `registries.json` + datapack) |

**26.2** packet dumps live under `src/main/resources/protocol/vanilla/` (checked in).
When Mojang ships a newer protocol, regenerate dumps with `minecraft-data` / Mojang specs,
commit `packets.json` + `index.json`, then commit under `src/main/resources/protocol/vanilla/`.
Drop files in `protocol/vanilla/<ver>/` for additional versions.

Threading does not affect this path — join is protocol content, not YapEngine layout.

## Pipeline

Native YaPcore JE: `McFrameCodec` → `JavaProtocolHandler` (client `ProtocolBand`) → `CrossplayHub`.

YaP Link JE: `McFrameCodec.Decoder` → (optional) `McCompressionCodec.Decoder` → session;
outbound `McOutboundPacketEncoder` (zlib+length). See [YAP_LINK.md](YAP_LINK.md).

## Honest limits

- Chunk / entity sync is still minimal (teleport below world clears loading terrain).
- Online-mode Mojang auth not implemented (`online-mode=false`).
- Play packet IDs for very old eras are best-effort and improve over time.

See [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md) and [CROSSPLAY.md](CROSSPLAY.md).
