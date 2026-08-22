# YaPcore web dashboard (headless control)

Browser control panel that mirrors the Swing GUI — for `--nogui` / headless hosts
and operators who prefer a remote UI.

## Enable

Default on. Config (`config/server.properties`):

```properties
web-dashboard-enabled=true
web-dashboard-port=8080
web-dashboard-bind=0.0.0.0
web-dashboard-token=
web-dashboard-localhost-only=false
```

Empty `web-dashboard-token` → a random token is generated on first start and
saved into the config file (also printed in the server log).

## Open

```
http://127.0.0.1:8080/
```

Paste the token on the login screen (or set `Authorization: Bearer <token>`).

## Features

| Tab | API route | Actions |
|-----|-----------|---------|
| **Status** | `/api/status` | Running state, heap, JE/BE counts, pack, **network health** summary |
| **Console** | `/api/console`, `/api/command` | Live log (SSE) + run commands |
| **Connect** | `/api/connect` | Join addresses + pack URL |
| **Settings** | `/api/config` | Identity, ports, editions, limits |
| **Plugins** | `/api/plugins` | List/add/remove + **compat matrix badges** |
| **Modules** | `/api/modules` | Fine-tune module jars |
| **Packs** | `/api/packs` | Active pack management |
| **Vehicles** | `/api/vehicles` | Fleet spawn / shop (gameplay opt-in) |
| **Pregen** | `/api/pregen` | Chunk pre-gen |
| **Ranks** | `/api/ranks` | YaPPerms pack apply |
| **Essentials** | `/api/essentials` | Homes/warps/tpa snapshot |
| **Link** | `/api/link` | YaP Link config + suite status |
| **Protect** | `/api/protect` | Block log / rollback |
| **World** | `/api/world` | Worlds, schem, brush stats |
| **Chat** | `/api/chat` | Channels, relay, filters |
| **Mod** | `/api/moderation` | Bans/mutes summary |
| **Perms** | `/api/perms` | Groups/tracks |
| **Data** | `/api/playerdata` | Economy/claims/sync flags |
| **Discord** | `/api/discord` | Webhook config (when plugin loaded) |
| **Tab** | `/api/tab` | TAB/scoreboard config (when plugin loaded) |
| **Map** | `/api/map` | Tile render status + map HTTP URL (`/map/` iframe) |
| **Guard** | `/api/guard` | Anti-cheat checks, alerts, player violations |
| **Regions** | `/api/regions` | Admin region list + flag reference |
| **NPCs** | `/api/npcs` | NPC list + quest pack count |

Pack HTTP stays on **:8081**. Dashboard is a separate port (**:8080**).

### Network health (Status tab)

`/api/status` includes `networkHealth`:

- Folia running, bedrock/crossplay/velocity flags
- Link config + suite completeness
- Plugin count + compat warning count
- Last smoke artifact timestamps (`build/bedrock-play-smoke-latest.json`, `build/smoke-network-full-latest.json`)

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

Dashboard **Ranks** tab calls `/api/ranks`. Reference:
[`examples/yapperms/ranks-reference.txt`](../examples/yapperms/ranks-reference.txt).
Config: `plugins/YaPPerms/config.yml`. See [PERMISSIONS.md](PERMISSIONS.md).

Optional: `yap-ranks-auto-apply=true` in `config/server.properties`.

## Release smoke

Full network gate:

```bash
./scripts/smoke-network-full.sh
```

## Security

- Treat the token like a password.
- Prefer `web-dashboard-localhost-only=true` or bind to a private IP, then put
  nginx + TLS in front for public access.
- Do not expose `:8080` to the internet without auth + TLS.
