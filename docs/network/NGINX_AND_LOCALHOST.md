# Localhost joins + nginx

## Same-machine Minecraft clients

On the PC running YaPcore, Direct Connect to:

```text
127.0.0.1:25566
```

(or `localhost:25566`)

Do **not** use your public WAN IP or `yapcoremc.yaplabs.us` from the same machine —
hairpin NAT often fails. LAN devices should use your LAN IP (Connect tab).

`allow-localhost=true` (default) keeps `bind-host=0.0.0.0` so loopback works.
Sockets also set `SO_REUSEADDR`.

### Server list vs join (log noise)

| Handshake `intent` | Meaning | Log |
|--------------------|---------|-----|
| `1` | STATUS (server-list ping) | Normal; connection closes after pong — **not** a join failure |
| `2` | LOGIN (actual join) | Real join path |

Older builds logged STATUS closes as `JE JOIN FAILED`. Current builds only warn
for real join aborts before PLAY.

## Domain + Cloudflare

Production domain: **`yapcoremc.yaplabs.us`**.

Full checklist: [CLOUDFLARE_AND_NGINX.md](CLOUDFLARE_AND_NGINX.md)  
DNS template: [`deploy/cloudflare/dns-records.example`](../deploy/cloudflare/dns-records.example)  
Publicity overview: [NETWORKING.md](NETWORKING.md)

## nginx (public front)

### Linux

```bash
./scripts/nginx-setup.sh --dry-run
sudo ./scripts/nginx-setup.sh
sudo ./scripts/nginx-setup.sh --install-pkg   # also install nginx package
sudo ./scripts/nginx-setup.sh --uninstall
```

### Windows

Same templates (`deploy/nginx/`). See [WINDOWS.md](../start/WINDOWS.md).

```powershell
.\scripts\Nginx-Setup.ps1 -DryRun
$env:NGINX_HOME = "C:\nginx"   # nginx.exe with stream module
.\scripts\Nginx-Setup.ps1
.\scripts\Nginx-Setup.ps1 -Uninstall
```

Templates: `deploy/nginx/`. Generated: `deploy/nginx/generated/`.

| Path | Role |
|------|------|
| stream | Public TCP+UDP `:25565` → `127.0.0.1:25566` |
| http | Packs `/pack/` → YaPcore `:8081` (+ Cloudflare `CF-Connecting-IP`) |

GUI **nginx** tab: save domain/ports, dry-run, or install with sudo/`pkexec`.

### Config keys

```properties
allow-localhost=true
nginx-public-port=25565
nginx-pack-port=80
nginx-domain=yapcoremc.yaplabs.us
server-domain=yapcoremc.yaplabs.us
public-host=yapcoremc.yaplabs.us
public-port=25565
public-bedrock-port=25565
public-pack-port=443
```

`nginx-setup.sh` falls back to `server-domain` / `public-host` when `nginx-domain` is empty.

### After changing ports/domain

Restart the server so the boot banner and pack offers pick up new
`public-*` / nginx values. Same-PC clients keep getting
`http://127.0.0.1:8081/pack/...` automatically.
