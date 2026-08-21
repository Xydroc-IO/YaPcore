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

| Area | Actions |
|------|---------|
| Status | Running state, players, heap, JE/BE counts, active pack |
| Console | Live log (SSE) + run any server command (incl. LuckPerms rank pack) |
| Connect | Join addresses + pack URL |
| Settings | Identity, ports, editions, limits, public host |
| Plugins / Modules | List, remove, install from host path |
| Packs | List, set active, remove |
| Vehicles | Spawn fleet types, shop/list/upgrades via `/yapvehicle` |
| Pregen | Chunk pre-gen via `/yappregen` / Pregen tab |
| Ranks | LuckPerms YaP pack status + Apply / Force re-apply |
| Start / Stop | Server lifecycle from the header |

Pack HTTP stays on **:8081**. Dashboard is a separate port (**:8080**).

### Ranks via console / tab

```bash
./scripts/install-luckperms.sh
# after Paper is up:
ranks apply
lp user Steve parent set vip
```

Dashboard **Ranks** tab calls `/api/ranks`. Pack file:
[`examples/luckperms/apply-yap-ranks.txt`](../examples/luckperms/apply-yap-ranks.txt).
See [PERMISSIONS.md](PERMISSIONS.md).

Optional: `yap-ranks-auto-apply=true` in `config/server.properties`.

## Security

- Treat the token like a password.
- Prefer `web-dashboard-localhost-only=true` or bind to a private IP, then put
  nginx + TLS in front for public access.
- Do not expose `:8080` to the internet without auth + TLS.
