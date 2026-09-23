# Shipped release defaults

First-boot / fresh-install configs copied by `./scripts/setup/seed-defaults.sh`
(also invoked from `start.sh` via `yap_ensure_dirs`).

**Profile:** typical SMP knobs (chat slow-mode, claim tax off, map claim markers on);
opt-in gameplay stays **off**. Secrets/binds stay LAN-safe.

**Never overwrites** existing operator files — only fills gaps.

| Path | Purpose |
|------|---------|
| `server.properties` | Full product profile (packs, ranks, Folia, dashboard) |
| `plugins/YaPDB/config.yml` | Shared SQL (default MySQL URL; Postgres/SQLite via `jdbc.engine`) — [`YAPDB.md`](../docs/data/YAPDB.md) |
| `plugins/YaPPlayerData/config.yml` | LAN-friendly auth off; shared YaPDB |
| `plugins/YaPDiscord/config.yml` | Discord inbound off until webhooks set |
| `plugins/YaPFactions/config.yml` | Factions/guilds **off** (`enabled: false`) until opted in |
| `plugins/YaPConquest/config.yml` | Chunk conquest **off** (`enabled: false`) until opted in |
| `plugins/YaPTailor/config.yml` | Skins/wardrobe; `skin-host-public-base-url` empty until set |
| `plugins/YaPTebex/config.yml` | Webhook inbound **off** until secret set |
| `plugins/YaPSkills/config.yml` | Skills RPG **on** by default |
| `plugins/YaPStacker/config.yml` | Mob/item stacker **off** until opted in |
| `plugins/YaPDungeons/config.yml` | Instanced dungeons **on** by default (needs YaPSkills + SQL) |
| `plugins/YaPDisasters/config.yml` | Extreme weather **off** (`enabled: false`) until opted in |
| `plugins/YaPGameplayKnobs/knobs.yml` | Encyclopedia knobs **off** (`settings.enabled: false`) |
| `plugins/YaPModeration\|Admin\|Protect\|World\|Regions\|Npcs\|Map\|Floodgate\|Pregen/…` | Core+network seeds (LAN-safe binds / passwords; typical SMP knobs) |
| `plugins/PlaceholderAPI/…` | Placeholders |
| `plugins/YaPPerms\|Chat\|Tab\|Essentials\|Guard\|LagGuard\|Packs\|Commands/…` | Core gameplay UX (opt-ins stay off) |
| `link.properties` | YaP Link single-backend + plugins on |

**No config file (N/A):** `YaPBedrockUI`, `YaPFoliaBridge`, `WorldEdit` shim — jar-only / no YAML seed.

After the database is up (MariaDB, PostgreSQL, or SQLite), run
`./scripts/db/configure-db.sh --engine mysql|postgres|sqlite --server-id lobby`
(or `./scripts/db/ensure-db.sh`) so JDBC host/port/password match. See
[docs/data/YAPDB.md](../docs/data/YAPDB.md).

**Secrets:** change passwords in `.env` before first start — see [docs/start/SECRETS.md](../docs/start/SECRETS.md).

See [docs/start/DEFAULTS.md](../docs/start/DEFAULTS.md).
