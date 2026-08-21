# Localhost joins + nginx

## Same-machine Minecraft clients

On the PC running YaPcore, Direct Connect to:

```text
127.0.0.1:25566
```

(or `localhost:25566`)

Do **not** use your public WAN IP from the same machine — hairpin NAT often
fails. LAN devices should use your LAN IP (shown on the Connect tab).

`allow-localhost=true` (default) keeps `bind-host=0.0.0.0` so loopback works.
Sockets also set `SO_REUSEADDR`.

## Domain + Cloudflare

Production domain for this project: **`yapcoremc.yaplabs.us`**.

Full Cloudflare DNS + SSL checklist: [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md)  
DNS record template: [`deploy/cloudflare/dns-records.example`](../deploy/cloudflare/dns-records.example)

## nginx (optional public front)

Universal installer (Debian / Ubuntu / Fedora / RHEL / Arch / Manjaro):

```bash
# preview
./scripts/nginx-setup.sh --dry-run

# install configs (sudo)
sudo ./scripts/nginx-setup.sh

# also install nginx package
sudo ./scripts/nginx-setup.sh --install-pkg

# remove
sudo ./scripts/nginx-setup.sh --uninstall
```

Templates live in `deploy/nginx/`. Generated files: `deploy/nginx/generated/`.

| Path | Role |
|------|------|
| stream | Public TCP+UDP game port → `127.0.0.1:port` |
| http | Pack downloads `/pack/` → YaPcore pack HTTP (+ Cloudflare real-IP) |

GUI **nginx** tab can dry-run / save ports / launch the script.

### Config keys

```properties
allow-localhost=true
nginx-public-port=25565
nginx-pack-port=80
nginx-domain=yapcoremc.yaplabs.us
server-domain=yapcoremc.yaplabs.us
public-host=yapcoremc.yaplabs.us
public-port=25565
public-pack-port=443
```
