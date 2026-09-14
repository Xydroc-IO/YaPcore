# YaP Fleet — multi-server control plane

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

## UI

- Web: **Fleet** tab (`app-fleet.js`)
- Swing: **Fleet** tab (`FleetPanel`)

Both call the same `FleetService` facade.

## Related

- [YAP_LINK.md](YAP_LINK.md) — proxy backends
- [PLAYERDATA.md](../data/PLAYERDATA.md) — unique `server-id` per instance
