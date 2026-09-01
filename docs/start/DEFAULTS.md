# Shipped defaults & first boot

YaPcore ships **jar-embedded** plugin YAML plus a tracked **`config/defaults/`** pack
that `./scripts/seed-defaults.sh` (and `start.sh`) copy into place **only when missing**.

**Game path:** product defaults use **YaP-Folia** (`game-authority=folia`, `folia-jar-source=build`). See [FOLIA_FORK.md](../folia/FOLIA_FORK.md).

## What works without editing

| Layer | OOTB |
|-------|------|
| Chat, Tab (sidebar), Guard, LagGuard, Packs, PluginCompat, PlaceholderAPI | Yes |
| YaP-Folia + first-party plugin jars | Yes (after `installAllProductDefaults` / release zip + `lib/yap-folia-*.jar`) |
| YaP Link + link plugins | Yes once `link.properties` seeded |
| Resource pack prompt | Yes when `server.properties` comes from defaults/example |
| YaPPerms starter ranks | Yes (`apply-starter-pack-on-first-boot` + `yap-ranks-auto-apply`) |
| Economy / claims / moderation / SQL plugins | **Needs MariaDB** |
| Discord webhooks | Needs your webhook URLs |
| Floodgate key identity | Optional `key.pem` (UUID heuristic works without it) |

## Fresh install (recommended)

```bash
./scripts/build-yap-folia.sh        # once — lib/yap-folia-26.2.jar
./scripts/seed-defaults.sh          # or just ./start.sh (seeds automatically)
./configure-db.sh --server-id lobby # starts Docker MariaDB + writes JDBC
./start.sh --fg
```

`configure-db.sh` / `ensure-db.sh` patch `plugins/YaPDB/config.yml` (and PlayerData)
to match `deploy/mariadb/.env` — including port **3316** if host `:3306` was busy.

## Defaults pack layout

```
config/defaults/
  README.md
  server.properties          → config/server.properties
  link.properties            → link-data/link.properties
  plugins/YaPDB/config.yml
  plugins/YaPPlayerData/…    # auth.enabled=false for LAN
  plugins/YaPDiscord/…       # inbound off
  plugins/YaPPerms|Chat|Tab|Essentials|Guard|LagGuard|Packs/…
```

Operator files are never overwritten. To reset a plugin to ship defaults, delete its
`plugins/<Name>/config.yml` and re-run `seed-defaults.sh` (or delete the whole folder
and let YaP-Folia re-extract from the jar after seed).

## LAN vs public

| Setting | Shipped default | Public tip |
|---------|-----------------|------------|
| `online-mode` | `false` | `true` (or Link online-mode + forwarding) |
| `auth.enabled` (PlayerData) | `false` | `true` on offline-mode public servers |
| `internet-exposed` | `false` | `true` + nginx/Cloudflare |
| `folia-jar-source` | `build` | keep `build` (YaP-Folia) |
| Discord inbound | `false` | enable + strong `secret` |

## Release note

`assembleRelease` copies `config/` (including `defaults/` and `*.example`).
Plugin **jars** only go under `plugins/` — configs are created on first boot via seed + jar.
Ship or build `lib/yap-folia-*.jar` for the product game path.
