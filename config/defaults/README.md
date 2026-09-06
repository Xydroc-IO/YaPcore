# Shipped release defaults

First-boot / fresh-install configs copied by `./scripts/seed-defaults.sh`
(also invoked from `start.sh` via `yap_ensure_dirs`).

**Never overwrites** existing operator files — only fills gaps.

| Path | Purpose |
|------|---------|
| `server.properties` | Full product profile (packs, ranks, Folia, dashboard) |
| `plugins/YaPDB/config.yml` | JDBC aligned with `deploy/mariadb/.env.example` |
| `plugins/YaPPlayerData/config.yml` | LAN-friendly auth off; shared YaPDB |
| `plugins/YaPDiscord/config.yml` | Discord inbound off until webhooks set |
| `plugins/YaPFactions/config.yml` | Factions/guilds **off** (`enabled: false`) until opted in |
| `plugins/YaPConquest/config.yml` | Chunk conquest **off** (`enabled: false`) until opted in |
| `plugins/YaPSkills/config.yml` | Skills RPG **off** until opted in |
| `plugins/YaPStacker/config.yml` | Mob/item stacker **off** until opted in |
| `plugins/YaPDungeons/config.yml` | Instanced dungeons **off** until opted in |
| `plugins/YaPGameplayKnobs/knobs.yml` | Encyclopedia knobs **off** (`settings.enabled: false`) |
| `plugins/YaPModeration\|Admin\|Protect\|World\|Regions\|Npcs\|Map\|Floodgate\|Pregen/…` | Core+network seeds (LAN-safe binds / passwords) |
| `plugins/PlaceholderAPI\|YaPPluginCompat/…` | Compat / placeholders |
| `plugins/YaPPerms\|Chat\|Tab\|Essentials\|Guard\|LagGuard\|Packs\|Commands\|Disasters/…` | Core gameplay UX |
| `link.properties` | YaP Link single-backend + plugins on |

**No config file (N/A):** `YaPBedrockUI`, `YaPFoliaBridge`, `WorldEdit` shim — jar-only / no YAML seed.

After MariaDB is up, run `./configure-db.sh --server-id lobby` (or
`./scripts/db/ensure-db.sh`) so JDBC host/port/password match the live `.env`.

**Secrets:** change passwords in `.env` before first start — see [docs/start/SECRETS.md](../docs/start/SECRETS.md).

See [docs/start/DEFAULTS.md](../docs/start/DEFAULTS.md).
