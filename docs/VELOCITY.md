# Velocity proxy support

YaPcore is a **game server** (Paper + YapEngine). [Velocity](https://papermc.io/software/velocity)
is a **proxy** in front of one or more backends. This doc makes YaPcore a clean Velocity backend.

We do **not** embed Velocity. We configure Paper for **modern player-info forwarding** so Velocity
can hand off real UUID / IP / skin data safely.

---

## Quick setup

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

On boot, YaPcore writes:

| File | Change |
|------|--------|
| `paper-kernel/server.properties` | `online-mode=false`, `prevent-proxy-connections=false`, optional `server-ip=127.0.0.1` |
| `paper-kernel/config/paper-global.yml` | `proxies.velocity.enabled=true` + matching `secret` + `online-mode` |
| `paper-kernel/spigot.yml` | `settings.bungeecord=false` (required — Bungee + Velocity modern conflict) |

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

For networks, put **Geyser on Velocity** (or Floodgate), not as the public YaPcore Bedrock listener.

Typical behind-Velocity JE network:

```properties
bedrock-enabled=false
crossplay-enabled=false
```

If you still want YaPcore’s built-in Bedrock on the same host, bind it separately and don’t expose
it as the main public join path while Velocity owns Java.

---

## ViaVersion

Via\* plugins go on **Velocity** and/or the backend (`plugins/`) as usual —
same as any Paper network. YaPcore’s built-in `ProtocolBand` multi-version applies to the
**native** authority path, not the default Paper JE port.

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

- [NETWORKING.md](NETWORKING.md) — domains / public ports  
- [CROSSPLAY.md](CROSSPLAY.md) — JE + BE  
- [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md) — Paper authority path  
- PaperMC: [Player information forwarding](https://docs.papermc.io/velocity/player-information-forwarding/)
