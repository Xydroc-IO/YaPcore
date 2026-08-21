# Internet & domain pointing

YaPcore separates **listen** addresses from **public** addresses so you can bind
locally while advertising a domain or NAT/nginx-mapped ports to players.

Production domain for this repo: **`yapcoremc.yaplabs.us`**.

For Cloudflare + nginx edge setup, prefer [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md).

## Quick setup (domain + nginx)

1. Point DNS **A/AAAA** at your origin IP (**DNS only / grey cloud** for Minecraft TCP+UDP).
2. Edit `config/server.properties` (see also `config/server.properties.example`):

```properties
internet-exposed=true
bind-host=0.0.0.0
allow-localhost=true
server-domain=yapcoremc.yaplabs.us
public-host=yapcoremc.yaplabs.us
nginx-domain=yapcoremc.yaplabs.us
# Advertised = nginx stream front (not the local bind port)
public-port=25565
public-bedrock-port=25565
# Packs via Cloudflare HTTPS → nginx :80 → YaPcore :8081
public-pack-port=443
nginx-public-port=25565
nginx-pack-port=80
resource-pack-public-host=yapcoremc.yaplabs.us
resource-pack-http-port=8081
port=25566
srv-enabled=true
```

3. Install nginx edge:

```bash
./scripts/nginx-setup.sh --dry-run
sudo ./scripts/nginx-setup.sh --install-pkg   # first time
sudo ./scripts/nginx-setup.sh
```

4. Forward on the router/firewall to the **origin**:
   - **TCP+UDP** `25565` → nginx stream → YaPcore `:25566`
   - **TCP** `80` (packs) → nginx → YaPcore `:8081`
5. Restart: `./scripts/gui.sh` or `./scripts/start.sh` — boot banner prints join URLs.

## What the boot banner means

| Line | Meaning |
|------|---------|
| `Same-PC → 127.0.0.1:25566` | Local bind port (always use this on the same machine) |
| `Java / Bedrock → yapcoremc.yaplabs.us:25565` | Public / nginx / SRV port |
| `Resource packs → https://yapcoremc.yaplabs.us/pack/...` | Public pack URL (`public-pack-port=443`) |
| `nginx edge → stream :25565 → local :25566` | How traffic is remapped |

If `public-port` is `0` but `nginx-domain` / `server-domain` is set, YaPcore still
advertises **`nginx-public-port`** (default `25565`) for join/SRV.

## Player join strings

| Client | Address |
|--------|---------|
| Same PC | `127.0.0.1:25566` |
| Internet (with SRV) | `yapcoremc.yaplabs.us` |
| Internet (explicit) | `yapcoremc.yaplabs.us:25565` |
| Packs (public) | `https://yapcoremc.yaplabs.us/pack/<file>` |
| Packs (same-PC client) | `http://127.0.0.1:8081/pack/<file>` (auto-offered) |

Console / GUI Network tab:

```text
expose on
domain yapcoremc.yaplabs.us
public
```

## Optional Java DNS SRV

```text
_minecraft._tcp.yapcoremc.yaplabs.us. 3600 IN SRV 0 5 25565 yapcoremc.yaplabs.us.
```

(`public` / Connect tab prints the exact line for your config.)

## Config keys

| Key | Meaning |
|-----|---------|
| `internet-exposed` | Prefer `0.0.0.0` bind + print public banner |
| `server-domain` / `public-host` | Domain players use |
| `public-port` | Advertised Java port after NAT/nginx (`0` → nginx port or listen `port`) |
| `public-bedrock-port` | Advertised Bedrock UDP port |
| `public-pack-port` | Advertised pack port (`443` → `https://` without `:443`) |
| `nginx-domain` / `nginx-public-port` / `nginx-pack-port` | Edge proxy settings |
| `resource-pack-public-host` | Override pack URL host |

Code: `com.yapcore.network.publicity.PublicEndpoint`.

## Related

- [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md)
- [NGINX_AND_LOCALHOST.md](NGINX_AND_LOCALHOST.md)
- [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md)
