# Internet & domain pointing

YaPcore separates **listen** addresses from **public** addresses so you can bind
locally while advertising a domain or NAT-mapped ports to players.

## Quick setup

1. Point DNS `A`/`AAAA` (or Cloudflare proxy off for game ports) at your server IP:
   - `play.example.com → 203.0.113.10`
2. Edit `config/server.properties`:

```properties
internet-exposed=true
bind-host=0.0.0.0
server-domain=play.example.com
public-host=play.example.com
# Optional NAT remap (0 = same as listen ports)
public-port=25565
public-bedrock-port=19132
public-pack-port=8081
srv-enabled=true
```

3. Forward on the router/firewall:
   - **TCP** `public-port` (Java) → host `port`
   - **UDP** `public-bedrock-port` → host `bedrock-port`
   - **TCP** `public-pack-port` → host `resource-pack-http-port` (pack downloads)

4. Restart: `./scripts/start.sh` — logs print join URLs. Or GUI → **Network** tab.

## Player join strings

| Client | Address |
|--------|---------|
| Java | `play.example.com:25565` (or whatever `public-port` is) |
| Bedrock | `play.example.com` port `19132` (UDP) |
| Packs | `http://play.example.com:8081/pack/<file>` |

Console:

```text
expose on
domain play.example.com
public
```

## Optional Java DNS SRV

So players can add just `play.example.com` without a port:

```text
_minecraft._tcp.play.example.com. 3600 IN SRV 0 5 25565 play.example.com.
```

(`public` / Network tab prints the exact line for your config.)

## Config keys

| Key | Meaning |
|-----|---------|
| `internet-exposed` | Prefer `0.0.0.0` bind + print public banner |
| `server-domain` | Domain you own |
| `public-host` | Host/IP advertised (defaults toward domain) |
| `public-port` | Advertised Java port after NAT (`0` = listen `port`) |
| `public-bedrock-port` | Advertised Bedrock UDP port |
| `public-pack-port` | Advertised pack HTTP port |
| `resource-pack-public-host` | Override pack URL host (auto-filled from domain when empty) |

Code: `com.yapcore.network.publicity.PublicEndpoint`.
