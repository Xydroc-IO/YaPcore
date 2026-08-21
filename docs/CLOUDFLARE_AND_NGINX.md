# Cloudflare + nginx for YaPcore

Domain used by this repo: **`yapcoremc.yaplabs.us`**

## Architecture

```text
Players (Java/Bedrock)
        │  TCP+UDP :25565
        ▼
  DNS-only A/AAAA ──► nginx stream ──► YaPcore :25566
  (grey cloud)

Browsers / Minecraft pack fetch
        │  HTTPS :443
        ▼
  Proxied hostname ──► Cloudflare ──► nginx :80 ──► YaPcore pack HTTP :8081
  (orange cloud OK)
```

| Traffic | Cloudflare | nginx | YaPcore |
|---------|------------|-------|---------|
| Java TCP / Bedrock UDP | **DNS only** (grey) | stream `:25565` | `:25566` |
| Resource packs HTTP(S) | Proxied OK | http `:80` `/pack/` | `:8081` |

Minecraft **cannot** use orange-cloud proxy unless you pay for **Spectrum**. Grey-cloud the game hostname (or a dedicated `play.` record).

## Config keys (`config/server.properties`)

```properties
server-domain=yapcoremc.yaplabs.us
public-host=yapcoremc.yaplabs.us
nginx-domain=yapcoremc.yaplabs.us
nginx-public-port=25565
nginx-pack-port=80
public-port=25565
public-bedrock-port=25565
public-pack-port=443
resource-pack-public-host=yapcoremc.yaplabs.us
resource-pack-http-port=8081
port=25566
```

Example file: [`config/server.properties.example`](../config/server.properties.example)  
DNS checklist: [`deploy/cloudflare/dns-records.example`](../deploy/cloudflare/dns-records.example)

## Install nginx on the origin

```bash
./scripts/nginx-setup.sh --dry-run
sudo ./scripts/nginx-setup.sh --install-pkg   # first time
sudo ./scripts/nginx-setup.sh                # apply configs
```

Or use the GUI **nginx** tab.

Generated files: `deploy/nginx/generated/`.

## Cloudflare dashboard checklist

1. Zone **yaplabs.us** — add `yapcoremc` **A** (and optional **AAAA**) → origin IP, **DNS only**.
2. Add **SRV**: service `_minecraft`, proto `_tcp`, name `yapcoremc`, priority `0`, weight `5`, port `25565`, target `yapcoremc.yaplabs.us`.
3. Optional: turn **Proxied** on the same name for web/packs only (Spectrum not required for packs).
4. SSL/TLS → **Full** if origin serves HTTPS, or **Flexible** if origin is HTTP `:80` only (current default).
5. Open origin firewall: **TCP+UDP 25565**, **TCP 80** (and **443** if you add local TLS later).

## Player join strings

| Who | Address |
|-----|---------|
| Internet (with SRV) | `yapcoremc.yaplabs.us` |
| Internet (explicit) | `yapcoremc.yaplabs.us:25565` |
| Same PC as the server | `127.0.0.1:25566` |

## Related

- [NGINX_AND_LOCALHOST.md](NGINX_AND_LOCALHOST.md) — localhost + script flags
- GUI Connect / nginx tabs — live endpoints after save
