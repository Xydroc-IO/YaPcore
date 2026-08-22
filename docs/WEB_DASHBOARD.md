# YaPcore Admin Dashboard

Browser-based **admin control panel** for headless hosts and remote operators.
Set up the network, configure YaP Link, manage plugins, and monitor health — no SSH required for day-to-day ops.

Modern **sidebar shell** (Overview · Server · People · Content · Gameplay) with dark theme, stat cards, and full plugin config editors where the backend supports it.

## Enable

Default on. Config (`config/server.properties`):

```properties
web-dashboard-enabled=true
web-dashboard-port=8080
web-dashboard-bind=0.0.0.0
web-dashboard-token=
web-dashboard-localhost-only=false
ops=
auto-op=false
```

Empty `web-dashboard-token` → a random token is generated on first start and
saved into the config file (also printed in the server log).

## Open

```
http://127.0.0.1:8080/
```

Paste the token on the login screen (or set `Authorization: Bearer <token>`).

## Navigation

| Group | Tabs |
|-------|------|
| **Overview** | Dashboard (status), Network setup, Connect |
| **Server** | Console, Settings, YaP Link |
| **People** | Players, Access & ranks, Rank pack |
| **Content** | Plugins, Modules, Packs, World, Regions, NPCs |
| **Gameplay** | Essentials, Vehicles, Pregen, Player data, Chat, Tab list, Map, Guard, Protect, Discord |

Static assets: `src/main/resources/web/` — `app-shell.js`, `app-core.js`, `app-*-panels.js`, `style.css`.

## Admin tab (infrastructure)

**`/api/admin`** — operator setup without editing files by hand:

| Section | What you configure |
|---------|-------------------|
| **Monitoring** | Crash dump, run `smoke-network-full.sh`, refresh health |
| **Dashboard access** | Port, bind, localhost-only, enable/disable, **rotate token** |
| **External access** | Internet expose, domain, public Java/Bedrock/pack ports, DNS SRV hint |
| **nginx** | Localhost assist, stream/HTTP ports, domain, config dry-run |
| **Proxy / authority** | Velocity forwarding, Link embed mode, link home path |

POST actions: `save-access`, `save-nginx`, `save-dashboard`, `save-proxy`, `rotate-token`, `nginx-dry-run`, `run-smoke`, `crashdump`.

## All tabs & API routes

| Tab | API | GET snapshot | POST actions (high level) |
|-----|-----|--------------|---------------------------|
| **Dashboard** | `/api/status` | running, heap, ticks, link process, network health | — |
| **Network setup** | `/api/admin` | dashboard, nginx, proxy, smoke | save-*, rotate-token, run-smoke, crashdump |
| **Connect** | `/api/connect` | Java/Bedrock/crossplay join, pack URL | — |
| **Console** | `/api/console`, `/api/command` | log backlog | run command · SSE `/api/console/stream` |
| **Settings** | `/api/config` | server.properties fields, ops, auto-op | save config keys |
| **YaP Link** | `/api/link`, `/api/link/console` | proxy, backends, forced hosts, selector | start, stop, save-proxy, save-servers, command · Link SSE |
| **Players** | `/api/players` | online list, spawn, moderation flags | kick, ban, tempban, ipban, mute, warn, timeout, tp*, set-rank, promote, demote, history, check, banlist |
| **Access & ranks** | `/api/access` | ops, auto-op, default group, groups, tracks | save-ops, save-auto-op, set-default-group, op, deop, set-group, promote, demote, group/user perm, reload, applypack |
| **Rank pack** | `/api/ranks` | pack applied, auto-apply | apply, force, reset-marker, status |
| **Plugins** | `/api/plugins` | jar list + compat matrix | install, remove |
| **Modules** | `/api/modules` | module jars | install, remove |
| **Packs** | `/api/packs` | resource packs, active set | setActive, add, remove, clear |
| **World** | `/api/world` | schematics, brush max, load/unload flags | load, unload, reload, schem-list, **save-brush** |
| **Regions** | `/api/regions` | region table (JSON), flag names | **define** (cuboid coords), **flag-set**, list |
| **NPCs** | `/api/npcs` | npc table, quest ids | create, remove, setquest, setdialogue, respawn, reload, info |
| **Essentials** | `/api/essentials` | features, MOTD, rules, spawn | reload, broadcast, save-motd, save-rules, set-feature |
| **Vehicles** | `/api/vehicles` | type list | spawn, shop, list, types, upgrades |
| **Pregen** | `/api/pregen` | job status | start, pause, resume, cancel |
| **Player data** | `/api/playerdata` | economy, auth, feature toggles | reload, save, set-feature |
| **Chat** | `/api/chat` | channels, slow mode, filter, relay | reload, clearchat, **save-settings** |
| **Tab list** | `/api/tab` | header/footer/sidebar/bossbar | save-header/footer/sidebar/settings/bossbar, reload |
| **Map** | `/api/map` | map URL, tiles, worlds, render interval | reload, render, **save-settings** |
| **Guard** | `/api/guard` | check toggles, kick threshold, decay, alerts | reload, player-status, **save-settings** |
| **Protect** | `/api/protect` | logging, retention, status | reload, prune, lookup, **rollback**, **save-settings** |
| **Discord** | `/api/discord` | webhooks, relay, inbound | save-webhook, save-relay, save-inbound, test-webhook, reload |

Legacy routes (superseded by UI tabs): `/api/moderation` → **Players**; `/api/perms` → **Access & ranks**.

Pack HTTP stays on **:8081**. Dashboard is a separate port (**:8080**).

### Access & ranks

Full operator control without `/op` and `/yapperm` by hand:

- **Minecraft OPs** — chip list, add/remove, persisted to `server.properties`
- **Auto-op** — toggle for first join
- **Default rank** — YaPPerms default group dropdown
- **Group cards** — click for group info; set permission nodes on group or player
- **Promote / demote** — track-based rank changes

### Players

Staff moderation panel: online table (name, UUID, IP, location), kick/ban/mute/timeout by **username, UUID, or IP**, teleport, rank assign, history/check/banlist.

Requires game server running + `yap-moderation` / `yap-perms`.

### NPCs (`yap-npcs`)

Dashboard drives the plugin directly:

- Create NPC at world coordinates (console: `npc create <id> at <world> <x> <y> <z> [yaw] [name]`)
- Edit display name, quest, dialogue; remove; respawn all; reload config
- GET `/api/npcs` returns structured `npcs[]` from `npc list json`

### Regions (`yap-regions`)

- Define admin cuboids from the UI (console: `region define <name> at <world> x1 y1 z1 x2 y2 z2`)
- Set WorldGuard-class flags (pvp, build, entry, …) allow/deny per region
- GET `/api/regions` returns structured `regions[]` from `region list json`

### Chat, Guard, Protect, Map, World

These tabs **write plugin YAML** via `save-settings` (or equivalent) and reload the plugin — not just run one-off commands.

### Network health (Dashboard tab)

`/api/status` includes `networkHealth`:

- Folia running, bedrock/crossplay/velocity flags
- Link process running (`linkProcessRunning`), config + suite completeness
- Plugin count + compat warning count
- Last smoke artifact timestamps

### YaP Link tab (proxy process)

Separate from the main **Console** tab (YaPcore/Folia). Requires `link-embed=false`.

**Settings UI** — configure the full proxy without editing files by hand:

- **Proxy settings** — bind, MOTD, max players, online mode, public host/port, chat relay, bedrock block
- **Backends** — add/remove servers (`hub`, `survival`, …), host:port, optional per-backend Bedrock address
- **Try order** — fallback order when a backend is down (e.g. `hub, survival`)
- **Forced hosts** — route by hostname (e.g. `hub.yourdomain.com` → `hub`)
- **Hub / server selector** — `yaplink-server-selector` hub + session lock

Saves write `link-data/link.properties`. If Link is running, the dashboard sends `reload` automatically.

| Action | POST `/api/link` body |
|--------|------------------------|
| Start Link | `{"action":"start"}` |
| Stop Link | `{"action":"stop"}` |
| Run command | `{"action":"command","command":"reload"}` |
| Enable backend forwarding | `{"action":"enable-backend-forwarding"}` |
| Save proxy settings | `{"action":"save-proxy",…}` |
| Save backends | `{"action":"save-servers","servers":[…],"try":[…],"forcedHosts":[…]}` |
| Save selector | `{"action":"save-selector","hubServer":"hub","sessionLock":"true"}` |

Live log: **GET** `/api/link/console` · **SSE** `/api/link/console/stream?token=…`

### Plugin compat (Plugins tab)

Each jar shows status from [PLUGIN_COMPAT_MATRIX.md](PLUGIN_COMPAT_MATRIX.md):
`native`, `works`, `broken`, `folia-build`, or `unknown`, plus native alternative hint.

### Ranks via console / tab

```bash
gradle installProductDefaults
# after Folia/Paper is up:
ranks apply
/yapperm user Steve parent set vip
/promote Steve
```

Dashboard **Access & ranks** and **Rank pack** tabs call `/api/access` and `/api/ranks`. Reference:
[`examples/yapperms/ranks-reference.txt`](../examples/yapperms/ranks-reference.txt).
Config: `plugins/YaPPerms/config.yml`. See [PERMISSIONS.md](PERMISSIONS.md).

Optional: `yap-ranks-auto-apply=true` in `config/server.properties`.

## Release smoke

Full network gate:

```bash
./scripts/smoke-network-full.sh
```

Or trigger from **Network setup** tab → **Run network smoke**.

## Security

- Treat the token like a password.
- Prefer `web-dashboard-localhost-only=true` or bind to a private IP, then put
  nginx + TLS in front for public access.
- Do not expose `:8080` to the internet without auth + TLS.
- Use **Network setup → Rotate token** if the secret may have leaked.
