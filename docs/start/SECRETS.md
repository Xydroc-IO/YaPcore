# Passwords & secrets (server owner guide)

Every credential in YaPcore is **operator-owned**. Shipped defaults use placeholders
(`change-me`, empty webhooks). **Never commit** live `.env`, `forwarding.secret`,
`key.pem`, or generated dashboard tokens.

Quick setup:

```bash
cp deploy/mariadb/.env.example deploy/mariadb/.env   # edit passwords first
./scripts/db/ensure-db.sh --server-id lobby          # writes JDBC from .env
./scripts/start.sh --fg                              # auto-generates dashboard token if empty
```

---

## Where to set each secret

| Secret | Set here | Dashboard? | Notes |
|--------|----------|--------------|-------|
| **MariaDB app password** | `deploy/mariadb/.env` → `YAP_DB_PASSWORD` | No | `./configure-db.sh` copies into `plugins/YaPDB/config.yml` |
| **MariaDB root password** | `deploy/mariadb/.env` → `YAP_DB_ROOT_PASSWORD` | No | Docker only; change before exposing DB |
| **Postgres password** | `deploy/postgres/.env` → `YAP_DB_PASSWORD` | No | `./configure-db.sh --engine postgres` |
| **JDBC password (fallback)** | `plugins/YaPPlayerData/config.yml` → `jdbc.password` | No | Used only if YaPDB missing |
| **Web dashboard token** | `config/server.properties` → `web-dashboard-token` | **Yes** — Admin → rotate token | Auto-generated on first boot if empty |
| **Minecraft OPs** | `config/server.properties` → `ops` | **Yes** — Access & ranks | Comma-separated names |
| **Velocity forwarding secret** | `forwarding.secret` (repo root or link dir) | Partial — Network setup | `./scripts/setup-velocity-forwarding.sh` |
| **YaP Link forwarding** | `link-data/link.properties` → `forwarding-secret-file` | **Yes** — YaP Link tab | Points at `forwarding.secret` |
| **Floodgate / Bedrock key** | `floodgate-key.pem` or `key.pem` | No | Optional; offline UUID works without |
| **Discord webhooks** | `plugins/YaPDiscord/config.yml` → `webhooks.*` | **Yes** — Discord tab | Paste webhook URLs |
| **Discord inbound secret** | `plugins/YaPDiscord/config.yml` → `inbound.secret` | **Yes** — Discord tab | Required when inbound enabled |
| **Player auth passwords** | `plugins/YaPPlayerData/config.yml` → `auth.*` | Partial — Player data tab | AuthMe-class; off by default on LAN |
| **Folia RCON** | `folia-kernel/server.properties` → `rcon.password` | No | Keep `enable-rcon=false` unless needed |
| **Folia management API** | `folia-kernel/server.properties` → `management-server-secret` | No | Leave empty; Folia generates locally |

---

## Recommended production order

1. Copy and edit `deploy/mariadb/.env` — **change both passwords** from `change-me` / `yaproot`.
2. Run `./scripts/db/ensure-db.sh --server-id lobby`.
3. Set `online-mode=true` (or Link + forwarding) in `config/server.properties` before going public.
4. Bind dashboard to localhost: `web-dashboard-bind=127.0.0.1` / `web-dashboard-localhost-only=true` — [EDGE_HARDEN.md](../network/EDGE_HARDEN.md).
5. Enable PlayerData auth if using offline-mode public: `auth.enabled: true`.
6. Start server once; copy the generated **dashboard token** from the log (or rotate in Admin tab).
7. Set Discord webhooks before enabling relay; set a strong `inbound.secret` if enabling inbound HTTP.
8. Run `./scripts/setup-velocity-forwarding.sh` when using YaP Link or Velocity in front of Folia.
9. Walk [CROSSPLAY.md §E](../network/CROSSPLAY.md) live checklist; start `./scripts/yapctl soak-long 12` in the background.

---

## What stays out of git

Enforced by [`.gitignore`](../../.gitignore):

- `deploy/mariadb/.env` (not `.env.example`)
- `config/server.properties` (live operator copy)
- `link-data/link.properties`
- `forwarding.secret`, `*.pem`, `*.key`
- `plugins/**` live configs (seed from `config/defaults/` or jar defaults)
- Generated dashboard tokens in local trees

Tracked templates use placeholders only: `config/defaults/`, `*.example`, `deploy/mariadb/.env.example`.

---

## Rotating credentials

| Credential | How to rotate |
|------------|----------------|
| Dashboard token | Web dashboard → Admin → **rotate token** (persists to `config/server.properties`) |
| DB password | Update `.env`, `ALTER USER` in MariaDB, re-run `./configure-db.sh` |
| Forwarding secret | Re-run `./scripts/setup-velocity-forwarding.sh`; restart Link + backends |
| Discord inbound | Change `inbound.secret` in dashboard or YAML; update your Discord bot/webhook caller |

---

## Related

| Doc | Topic |
|-----|--------|
| [DEFAULTS.md](DEFAULTS.md) | First-boot seed layout |
| [MARIADB.md](../data/MARIADB.md) | Docker MariaDB setup |
| [POSTGRES.md](../data/POSTGRES.md) | Docker Postgres setup |
| [SQLITE.md](../data/SQLITE.md) | Single-node SQLite |
| [YAPDB.md](../data/YAPDB.md) | Shared pool + engines |
| [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) | Token login, Admin tab |
| [DISCORD_RELAY.md](../ops/DISCORD_RELAY.md) | Webhook + inbound setup |
| [EDGE_HARDEN.md](../network/EDGE_HARDEN.md) | Public exposure checklist |
