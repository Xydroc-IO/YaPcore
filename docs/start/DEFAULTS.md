# Shipped defaults & first boot

YaPcore ships **jar-embedded** plugin YAML plus a tracked **`config/defaults/`** pack
that `./scripts/seed-defaults.sh` (and `start.sh`) copy into place **only when missing**.

**Game path:** product defaults use **YaP-Folia** (`game-authority=folia`, `folia-jar-source=build`). Build with `./scripts/build-yap-folia.sh`.

## What works without editing

| Layer | OOTB |
|-------|------|
| Chat, Tab (sidebar), Guard, LagGuard, Packs, PluginCompat, PlaceholderAPI | Yes |
| Admin menu, World tools, Regions, **Portals (on)**, Npcs, Protect, Moderation, Pregen, Floodgate, Map | Yes (SQL plugins need MariaDB / shared YaPDB) |
| YaP-Folia + first-party plugin jars | Yes (after `installAllProductDefaults` / release zip + `lib/yap-folia-*.jar`) |
| YaP Link + link plugins | Yes once `link.properties` seeded; **modern forwarding ON by default** (skins) — join **:25565** |
| Resource pack prompt | Yes — JE pulls `yapcore-default.zip` from the **0.0.0.1** GitHub prerelease |
| Premium Java skins | Yes via Link offline texture lookup + `velocity:player_info` (requires Link in front) |
| YaPPerms starter ranks | Yes (`apply-starter-pack-on-first-boot` + `yap-ranks-auto-apply`) |
| YaPFactions / guilds | **Off** (`enabled: false`) — opt in for faction servers · [GAMEPLAY.md](../gameplay/GAMEPLAY.md) |
| YaPConquest / chunk land | **Off** (`enabled: false`) — hardcore grid; needs YaPFactions · [GAMEPLAY.md](../gameplay/GAMEPLAY.md) |
| YaP-QoL (timber / excavator) | **On** — product default · VIP kit grants tools |
| YaPItems | **On** — product default |
| YaPSkills / YaPLeveledMobs | **On** in shipped defaults (thin skills + distance mob levels) — turn off in plugin config if undesired |
| YaPStacker / Dungeons / Disasters / GameplayKnobs | **Off** until opted in (`enabled: false` / knobs settings) |
| YaPMap 3D mesh | **Off** (`mesh.enabled: false`) — enable + `/yapmap render` for BlueMap-class 3D |
| Economy / claims / moderation / SQL plugins | **Needs SQL** (MariaDB default; Postgres or SQLite OK — [YAPDB.md](../data/YAPDB.md)) |
| Discord webhooks | Needs your webhook URLs |

**Passwords & secrets:** every credential is operator-owned. See [SECRETS.md](SECRETS.md)
for MariaDB, dashboard token, forwarding secret, Discord inbound, and auth.
| Floodgate key identity | Optional `key.pem` (UUID heuristic works without it) |

## Fresh install (recommended)

```bash
./scripts/build-yap-folia.sh        # once — lib/yap-folia-26.2.jar
./scripts/seed-defaults.sh          # or just ./start.sh (seeds automatically)
./configure-db.sh --server-id lobby # starts Docker MariaDB + writes JDBC
# or: ./scripts/db/ensure-postgres.sh --server-id lobby
# or: ./scripts/db/configure-db.sh --engine sqlite --server-id lobby
./start.sh --fg
```

`configure-db.sh` / `ensure-db.sh` patch `plugins/YaPDB/config.yml` (and PlayerData)
to match `deploy/mariadb/.env` — including port **3316** if host `:3306` was busy.
Postgres uses `deploy/postgres/.env`; SQLite writes `data/yap.db`.

## Defaults pack layout

```
config/defaults/
  README.md
  server.properties          → config/server.properties
  link.properties            → link-data/link.properties
  plugins/YaPDB/config.yml
  plugins/YaPPlayerData/…    # auth.enabled=false for LAN
  plugins/YaPDiscord/…       # inbound off
  plugins/YaP-QoL/…           # product default timber/excavator
  plugins/YaPItems/…
  plugins/YaPTailor/…         # skins/wardrobe; skin-host URL empty until set
  plugins/YaPTebex/…          # webhook inbound off until secret set
  plugins/YaPPortals/…        # product default on — fleet walk-through portals
  plugins/YaPSkills/…         # enabled: true (thin skills)
  plugins/YaPLeveledMobs/…    # enabled: true
  plugins/YaPStacker|Dungeons|Disasters/…  # enabled: false (opt-in)
  plugins/YaPGameplayKnobs/knobs.yml    # settings.enabled: false
  plugins/YaPMap/…            # mesh.enabled: false (3D opt-in)
  plugins/YaPFactions/…      # enabled: false (opt-in)
  plugins/YaPConquest/…      # enabled: false (opt-in chunk land)
  plugins/YaPModeration|Admin|Protect|World|Regions|Portals|Npcs|Floodgate|Pregen/…
  plugins/PlaceholderAPI|YaPPluginCompat/…
  plugins/YaPPerms|Chat|Tab|Essentials|Guard|LagGuard|Packs|Commands/…
```

**N/A (no YAML seed):** YaPBedrockUI, YaPFoliaBridge, YaPBedrockBlocks, WorldEdit shim.

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
| Chat `slow-mode-seconds` | `3` | `0` for LAN/dev; keep or raise on busy public |
| Claims `tax.enabled` | `false` | `true` only if you want claim upkeep |
| Map `markers.claims` | `true` | leave on for SMP claim overlay |
| Discord inbound / relay | `false` | webhook → `relay.mc-to-discord: true` → reload |
| YaPWorld editor.bind | `127.0.0.1` | `0.0.0.0` + firewall if remote editors |
| YaPMap mesh.enabled | `false` | `true` when you want 3D tiles |

**Existing installs:** seeds never overwrite operator files. To pick up new shipped defaults, delete `plugins/<Name>/config.yml` (or the whole folder) and re-run `./scripts/seed-defaults.sh`.

## Release note

`assembleRelease` copies `config/` (including `defaults/` and `*.example`).
Plugin **jars** only go under `plugins/` — configs are created on first boot via seed + jar.
Ship or build `lib/yap-folia-*.jar` for the product game path.
Typical SMP knobs (chat slow-mode, claim tax off, map claim markers) live in seeds + jar resources; opt-in plugins stay `enabled: false`.

## YaP-Folia ship knobs

Seeded in `config/defaults/server.properties` (and forwarded as `-Dyap.folia.*`):

| Knob | Default |
|------|---------|
| `folia-subregion-partition` | **true** |
| `folia-subregion-carve` | **true** |
| `folia-regionizer-cut` | **true** |
| `folia-grid-exponent` | **3** |
| `folia-aligned-microticks` | **true** |
| `folia-physics-substeps` | **true** |
| `folia-ticket-hygiene` | **true** |
| `folia-portal-couple` | **true** |

Lab-only: `-Dyap.folia.scheduler-probe` (patch `0033`). **35** patches on pin `14b7fee` (`0000`–`0042`): `0034`–`0042` are Folia-itself (tickets, ownership, portal couple, split, teleport events, map autosave, debug CME, packed-spawn cut, async brain + end-vehicle spawn). **Professional bar** (live contiguous split that the regionizer holds, no second clock) is reachable with `0041` and **not yet proven** on **0.0.0.1**. Inventory: [YAP_FOLIA_PATCHES.md](../folia/YAP_FOLIA_PATCHES.md).
