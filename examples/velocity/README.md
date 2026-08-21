# Example — Velocity in front of YaPcore

YaPcore is the **backend**. Velocity is the **proxy** players join.

## 1. Create a shared secret

```bash
# From the Velocity directory (or copy into YaPcore root)
openssl rand -base64 32 | tr -d '\n' > forwarding.secret
cp forwarding.secret /path/to/YaPcore/forwarding.secret
```

## 2. YaPcore `config/server.properties`

```properties
velocity-enabled=true
velocity-secret-file=forwarding.secret
velocity-online-mode=true
velocity-bind-localhost=true
online-mode=false
port=25566
```

## 3. Velocity `velocity.toml` (excerpt)

```toml
bind = "0.0.0.0:25565"

[servers]
yapcore = "127.0.0.1:25566"

try = ["yapcore"]

player-info-forwarding-mode = "modern"
# forwarding-secret-file = "forwarding.secret"   # Velocity default path
online-mode = true
```

## 4. Run

```bash
# Terminal A — game
cd /path/to/YaPcore && ./scripts/start.sh --fg

# Terminal B — proxy
cd /path/to/velocity && java -jar velocity.jar
```

Players connect to `your.domain:25565` (Velocity). YaPcore stays on loopback `:25566`.

See [docs/VELOCITY.md](../../docs/VELOCITY.md).
