# Velocity / YaP Link proxy support

YaPcore (Folia/Paper) is a **game backend**. The public edge is a **proxy**:

- **[YaP Link](YAP_LINK.md)** — YapLabs’ **native Velocity-class proxy** ([YAP_LINK_NATIVE.md](YAP_LINK_NATIVE.md), phased)
- Stock Velocity — same forwarding contract (optional stand-in)

---

## Quick setup (YaP Link)

```bash
./scripts/setup-velocity-forwarding.sh --enable
# sets game-authority=folia, velocity-enabled=true, velocity-online-mode=false
./scripts/start.sh
./scripts/start-yap-link.sh
# players → Link :25565 → Folia loopback
```

Smoke: `./scripts/smoke-yap-link-folia.sh`

When Folia `proxies.velocity.enabled` is **false** (local default in some trees), Link
still completes login without `velocity:player_info`. Enable forwarding for production
multi-proxy as above. Game jar remains **YaP-Folia** (`folia-jar-source=build`) unless
you explicitly switch to stock Fill (`fetch`).

Full Link docs: [YAP_LINK.md](YAP_LINK.md).

---

## Quick setup (stock Velocity stand-in)

```bash
# From YaPcore root — creates forwarding.secret, wires config (does not enable yet)
./scripts/setup-velocity-forwarding.sh

# When Velocity is actually in front:
./scripts/setup-velocity-forwarding.sh --enable
# then restart YaPcore; players join Velocity only
```

### 1. YaPcore (`config/server.properties`)

```properties
velocity-enabled=true
# Prefer a file (same contents as Velocity's forwarding.secret):
velocity-secret-file=forwarding.secret
# Or inline (avoid committing secrets):
# velocity-secret=your-long-random-secret

# Must match Velocity's online-mode
velocity-online-mode=true

# Bind Paper to loopback so only the proxy on this host can reach the game port
velocity-bind-localhost=true

# Paper must not Mojang-auth when Velocity does (forced automatically when velocity-enabled=true)
online-mode=false
```

On boot, YaPcore writes (Folia or Paper kernel dir):

| File | Change |
|------|--------|
| `folia-kernel/` or `paper-kernel/server.properties` | `online-mode=false`, `prevent-proxy-connections=false`, optional `server-ip=127.0.0.1` |
| `…/config/paper-global.yml` | `proxies.velocity.enabled=true` + matching `secret` + `online-mode` |
| `…/spigot.yml` | `settings.bungeecord=false` (required — Bungee + Velocity modern conflict) |

### 2. Velocity (`velocity.toml`)

```toml
[servers]
# Point at YaPcore's Paper JE port (default YaPcore port=25566)
lobby = "127.0.0.1:25566"

try = ["lobby"]

player-info-forwarding-mode = "modern"
# forwarding.secret file next to velocity.toml — same value as YaPcore
```

Put the same secret in Velocity’s `forwarding.secret` and in YaPcore’s
`velocity-secret` / `velocity-secret-file`.

Example snippet: [examples/velocity/](../examples/velocity/).

### 3. Restart both

1. Start YaPcore (`./scripts/start.sh`)  
2. Start Velocity  
3. Players join **Velocity’s** public port — not YaPcore directly (when `velocity-bind-localhost=true`)

---

## Config keys

| Key | Default | Meaning |
|-----|---------|---------|
| `velocity-enabled` | `false` | Sync Paper for Velocity modern forwarding |
| `velocity-secret` | *(empty)* | Inline secret |
| `velocity-secret-file` | *(empty)* | Path to secret file (preferred); overrides inline when set |
| `velocity-online-mode` | `true` | Must match Velocity `online-mode` |
| `velocity-bind-localhost` | `true` | Paper JE listens on `127.0.0.1` only |

Missing secret with `velocity-enabled=true` **fails boot** (fail-closed).

---

## Bedrock / Geyser

For networks, put **Geyser (+ Floodgate) on Velocity**, not as the public YaPcore Bedrock listener.

YaPcore does **not** need the Floodgate jar on the backend. Ship **`yap-floodgate.jar`** instead:

1. On Velocity: Geyser + Floodgate, `send-floodgate-data: true`, shared `key.pem`
2. Copy `key.pem` → `plugins/YaPFloodgate/key.pem` on YaPcore
3. Enable modern forwarding (same as above)

`yap-floodgate` decrypts the hostname Floodgate blob and/or recognizes
`UUID(0, xuid)` players, exposing Bedrock XUID / link without the Floodgate plugin.

Typical behind-Velocity JE network:

```properties
bedrock-enabled=false
crossplay-enabled=false
velocity-enabled=true
```

If you still want YaPcore’s built-in Bedrock on the same host, bind it separately and don’t expose
it as the main public join path while Velocity owns Java.

Native Bedrock UDP (no Velocity) already uses first-party `FloodgateAuth` with the same
`UUID(0, xuid)` scheme.

---

## ViaVersion / ViaBackwards / ViaRewind / Geyser

**Do not use.** Phase 4 DoD is **full** Via\* + Geyser feature parity in YaPcore
code ([PHASE4_PROTOCOL.md](PHASE4_PROTOCOL.md)). Dropping those jars into
`plugins/` is not the supported path.

Proxy networks that still run Via on Velocity for *other* backends are outside
YaPcore’s product surface; the YaPcore backend itself expects built-in remap.

---

## Checklist

- [ ] Same forwarding secret on Velocity and YaPcore  
- [ ] `player-info-forwarding-mode = "modern"`  
- [ ] `velocity-online-mode` matches Velocity `online-mode`  
- [ ] `spigot.yml` `bungeecord: false` (YaPcore sets this when enabled)  
- [ ] Players connect to Velocity, not the backend port  
- [ ] Firewall: backend port not public when using localhost bind  

---

## Related

- [PLAYERDATA.md](PLAYERDATA.md) — cross-server inventory / money sync (`yap-playerdata`)
- [NETWORKING.md](NETWORKING.md) — domains / public ports  
- [CROSSPLAY.md](CROSSPLAY.md) — JE + BE  
- [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md) — Paper authority path  
- PaperMC: [Player information forwarding](https://docs.papermc.io/velocity/player-information-forwarding/)
