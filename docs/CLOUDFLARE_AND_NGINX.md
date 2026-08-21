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

Minecraft **cannot** use orange-cloud proxy unless you pay for **Spectrum**. Grey-cloud the game hostname.

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

| Artifact | Path |
|----------|------|
| Example properties | [`config/server.properties.example`](../config/server.properties.example) |
| DNS checklist | [`deploy/cloudflare/dns-records.example`](../deploy/cloudflare/dns-records.example) |
| nginx templates | `deploy/nginx/*.template` |
| Generated configs | `deploy/nginx/generated/` |

## Install nginx on the origin

```bash
./scripts/nginx-setup.sh --dry-run
sudo ./scripts/nginx-setup.sh --install-pkg   # first time
sudo ./scripts/nginx-setup.sh                # apply configs
```

Or use the GUI **nginx** tab. Restart YaPcore after saving domain/ports so the
boot banner advertises `:25565` / HTTPS packs (not the raw bind `:25566` / `:8081`).

## Cloudflare dashboard checklist

1. Zone **yaplabs.us** — add `yapcoremc` **A** (and optional **AAAA**) → origin IP, **DNS only**.
2. Add **SRV**: service `_minecraft`, proto `_tcp`, name `yapcoremc`, priority `0`, weight `5`, port `25565`, target `yapcoremc.yaplabs.us`.
3. Optional: **Proxied** for web/packs only (Spectrum not required for packs).
4. SSL/TLS → **Full** if origin serves HTTPS, or **Flexible** if origin is HTTP `:80` only (default with current nginx HTTP template).
5. Firewall: **TCP+UDP 25565**, **TCP 80** (and **443** if you terminate TLS on origin later). Do not expose `:8081` publicly if nginx fronts packs.

## Player join strings

| Who | Address |
|-----|---------|
| Internet (with SRV) | `yapcoremc.yaplabs.us` |
| Internet (explicit) | `yapcoremc.yaplabs.us:25565` |
| Same PC as the server | `127.0.0.1:25566` |
| Packs (public) | `https://yapcoremc.yaplabs.us/pack/<file>` |
| Packs (same-PC client) | `http://127.0.0.1:8081/pack/<file>` |

## Related

- [NETWORKING.md](NETWORKING.md) — publicity keys + boot banner
- [NGINX_AND_LOCALHOST.md](NGINX_AND_LOCALHOST.md) — localhost + script flags + STATUS vs LOGIN
- GUI Connect / nginx tabs — live endpoints after save
