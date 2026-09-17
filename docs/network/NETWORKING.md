# Internet & domain pointing

YaPcore separates **listen** addresses from **public** addresses so you can bind
locally while advertising a domain or NAT/nginx-mapped ports to players.

Production domain for this repo: **`yapcoremc.yaplabs.us`**.

For Cloudflare + nginx edge setup, prefer [NETWORKING.md](NETWORKING.md).

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

- [NETWORKING.md](NETWORKING.md)
- [NETWORKING.md](NETWORKING.md)
- [CLIENTS_AND_PACKS.md](CLIENTS_AND_PACKS.md)

## Same-machine clients

Join `127.0.0.1` (or `localhost`) on the game/Link port — not the public WAN hostname (hairpin NAT).
Public nginx / Cloudflare: [NETWORKING.md](NETWORKING.md).



---

## Fleet (local multi-instance)

One chassis GUI/dashboard operates an entire JE+BE network: live backend health, local Folia instances, remote agents, deploy, and bootstrap.

## Defaults (safe)

- `fleet-enabled=false` — single `folia-kernel` path unchanged.
- Enable via **Fleet → Enable fleet** (web or Swing) or `POST /api/fleet` `{ "action": "enable-fleet" }`.

## Enable / migrate

On first enable:

1. Moves `folia-kernel` → `fleet/instances/lobby` (idempotent).
2. Sets `folia-dir=fleet/instances/lobby` and `fleet-enabled=true`.
3. Writes `fleet/fleet.json` and rewrites Link `servers.*` / `try` / `bedrock-backend`.

Layout:

```
fleet/
  fleet.json
  instances/
    lobby/
    survival/
  agents/
    node-2.token    # bearer token; never commit
```

## APIs

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/fleet` | Registry + live state + backend health + aggregated players |
| POST | `/api/fleet` | `enable-fleet`, `create`, `start`, `stop`, `restart`, `delete`, `set-auto-start`, `sync-link`, `command`, `write-node-token`, `probe-node`, `deploy`, `bootstrap`, `install-plugin`, `update-settings`, … |
| GET/POST | `/api/fleet/instances/{id}/settings` | Read/write Folia `server.properties` + fleet.json fields (port, max-players, **ramMb**, MOTD, …); port changes sync Link; RAM applies on next Start/Restart |
| GET/POST | `/api/fleet/instances/{id}/plugins` | List / enable / disable / uninstall plugins on that instance |
| POST | `/api/plugins/install` | `{ "jar", "instances": "lobby,survival", "restartPolicy" }` — catalog → multi-target install |
| GET | `/api/fleet/console?id=` | Recent console text |
| GET | `/api/fleet/console/stream?id=&token=` | SSE console |

Phase 1 health also appears on `GET /api/link` and `GET /api/status` → `networkHealth.backendHealth` (from Link `GET /backends` or TCP fallback).

## Default plugins (CORE+NETWORK)

Every new or healed fleet instance gets the **CORE+NETWORK** suite from the root
catalog (`plugins/*.jar`) — the same set as `gradle installProductDefaults`
(see [`plugins/README.md`](../plugins/README.md)). Gameplay jars (skills, dungeons, …)
and third-party (Grim, Tebex) are **opt-in** via Plugins → Install.

Empty instance `plugins/` dirs are auto-healed on fleet enable, chassis start, and
when listing plugins. Manual: `POST /api/fleet` `{ "action": "install-core-network", "id": "lobby" }`.

## GUI (fleet-first)

- **Left rail** — **YaP Link** (network edge for all servers) then game servers (lobby, …).
- **Fleet home** — cards for every server with Start / Stop / Restart, plugin count, Plugins / Setup.
- **Click a game server** — workspace opens on **Plugins** first.
- **Access / nginx** — helpers under the rail footer (not Link).
- **Plugins** — catalog (root `plugins/`) + install to selected servers; per-instance installed list + **Install CORE+NETWORK**.
- Web and Swing share the same fleet APIs.
## Remote agents

Build/run:

```bash
gradle :yap-fleet-agent:jar
java -jar yap-first-party/fleet/yap-fleet-agent/build/libs/yap-fleet-agent.jar \
  --home /var/yap-agent --bind 0.0.0.0 --port 9095 --node node-2
```

Copy `agent.token` into chassis `fleet/agents/node-2.token`, then **Add node** in Fleet UI (`baseUrl=http://host:9095`).

Agent endpoints: `/v1/health`, `/v1/instances`, `/v1/instances/{id}/start|stop`, `/v1/command`, `/v1/deploy`, `/v1/players`, `/v1/console/stream`.

## Bootstrap wizard

Creates lobby (+ optional survival), optionally runs `scripts/db/ensure-db.sh` / `ensure-postgres.sh` per `server-id`, enables velocity, syncs Link. Does not start JVMs unless requested.

**Database only:** Fleet → **Database (YaPDB)** (web) or **Database…** (Swing) — pick engine, start Docker, write JDBC, sync catalog without creating instances. See [YAPDB.md](../data/YAPDB.md).

## UI

- Web: **Fleet** tab (`app-fleet.js`)
- Swing: **Fleet** tab (`FleetPanel`)

Both call the same `FleetService` facade.

## Related

- [YAP_LINK.md](YAP_LINK.md) — proxy backends
- [PORTALS.md](PORTALS.md) — YaPPortals fleet walk-through transfers
- [PLAYERDATA.md](../data/PLAYERDATA.md) — unique `server-id` per instance


---

## Edge harden & rate limits

Harden a **public** YaPcore edge without reading the whole wiki. Builds on
[NETWORKING.md](NETWORKING.md),
and [PLUGINS.md](../plugins/PLUGINS.md).

## Target architecture

```text
Internet players
      │  TCP(+UDP) :25565
      ▼
DNS-only A/AAAA ──► nginx stream ──► YaP Link :25565-local OR YaPcore :25566
(grey cloud)         (optional)         dashboard :8080 → 127.0.0.1 only
                                        metrics  :9091 → 127.0.0.1 only
                                        YaP-Folia game (child JVM)
```

| Surface | Public? | Bind |
|---------|---------|------|
| Game JE/BE | Yes (`:25565`) | nginx → Link or YaPcore → **YaP-Folia** |
| Packs HTTP | Yes (`:80`/`:443`) via nginx | YaPcore `:8081` localhost |
| Web dashboard `:8080` | **No** | `web-dashboard-bind=127.0.0.1` |
| Link `/metrics` `:9091` | **No** | `metrics-http-bind=127.0.0.1` |

## Safe defaults

### Public internet

```properties
# link.properties
connect-rate-limit-enabled=true
connect-rate-per-ip=20
connect-rate-window-ms=10000
handshake-rate-limit-enabled=true
handshake-rate-per-ip=40
login-rate-limit-enabled=true
login-rate-per-ip=10
max-concurrent-per-ip-enabled=true
max-concurrent-per-ip=8
rate-limit-exempt-loopback=true
metrics-http-bind=127.0.0.1
```

```properties
# config/server.properties
web-dashboard-enabled=true
web-dashboard-bind=127.0.0.1
web-dashboard-localhost-only=true
internet-exposed=true
```

### LAN / lab

Raise limits or disable concurrent caps if many clients share a NAT:

```properties
connect-rate-per-ip=100
max-concurrent-per-ip=32
# or max-concurrent-per-ip-enabled=false for LAN only
```

Keep loopback exemption **on** for local admin tools and `./scripts/start.sh`.

## Verify throttles

Watch metrics after stressing the edge:

```text
yap_link_connect_throttled_total
yap_link_handshake_dropped_total
yap_link_login_dropped_total
yap_link_connect_concurrent_dropped_total
```

Or `GET /api/status` → `observability.linkMetricsHint`.

## nginx stream

Templates: `deploy/nginx/yapcore-stream.conf.template`  
Hardened example (conn limits): `deploy/nginx/yapcore-stream-hardened.conf.example`

```bash
./scripts/nginx-setup.sh --dry-run
sudo ./scripts/nginx-setup.sh
```

Point **grey-cloud** DNS at the origin. Orange-cloud Minecraft TCP needs Spectrum.

## Cloudflare notes

1. Game hostname: **DNS only** (grey).
2. Packs/web: orange OK behind nginx `:80`/`:443`.
3. Do not publish `:8080`, `:8081`, or Link `:9091` to the world.
4. Optional Spectrum: TCP proxy to origin `:25565` if you must orange-cloud game traffic.

## Fail-closed checklist (under attack)

1. Confirm drops rising on Link `/metrics` (`*_throttled` / `*_dropped`).
2. Tighten: `connect-rate-per-ip=8`, `max-concurrent-per-ip=4`, shorter windows.
3. At nginx: enable `limit_conn` from the hardened stream example; reload nginx.
4. Host firewall: allow only `:25565` (and `:80`/`:443` for packs) from the internet.
5. Temporarily set `internet-exposed=false` / move DNS to maintenance if needed.
6. Do **not** bind dashboard or metrics to `0.0.0.0` to “debug” under fire.

Example host firewall / fail2ban snippets (docs only): see bottom of this file.

## Lag machines

Install LagGuard (`gradle installProductDefaults`). Survival defaults ship in
`plugins/YaPLagGuard/config.yml` — [PLUGINS.md](../plugins/PLUGINS.md).

---

## Optional host snippets (examples only)

### nftables (allow game + packs)

```nft
table inet yap_edge {
  chain input {
    type filter hook input priority 0; policy drop;
    ct state established,related accept
    iif lo accept
    tcp dport { 25565, 80, 443 } accept
    udp dport 25565 accept
  }
}
```

### fail2ban (jail sketch — tune to your log path)

```ini
[yap-link-throttle]
enabled = true
filter = yap-link-throttle
logpath = /var/log/yap-link/link.log
maxretry = 1
findtime = 60
bantime = 3600
```

Filter would match `connect throttled ip=` lines when Link logging is raised to INFO for those events. Prefer Link rate limits + nginx `limit_conn` before fail2ban.


---

## Cloudflare & nginx

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
| Hardened stream example | `deploy/nginx/yapcore-stream-hardened.conf.example` |
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

## Dashboard & metrics (keep private)

```properties
web-dashboard-bind=127.0.0.1
web-dashboard-localhost-only=true
```

Link metrics (when using YaP Link): `metrics-http-bind=127.0.0.1` in `link.properties`.
Do not orange-cloud or publish `:8080` / `:9091`. See [NETWORKING.md](NETWORKING.md).

## Related

- [NETWORKING.md](NETWORKING.md) — public edge checklist, fail-closed, nftables/fail2ban examples
- [NETWORKING.md](NETWORKING.md) — publicity keys + boot banner
- [NETWORKING.md](NETWORKING.md) — localhost + script flags + STATUS vs LOGIN
- [NETWORKING.md](NETWORKING.md) — Link rate limits + Prometheus
- GUI Connect / nginx tabs — live endpoints after save
