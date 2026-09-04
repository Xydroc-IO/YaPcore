# YaPcore release notes

Product version **1.0.0.0** · YaP Link **0.6.0-phase6** · YaP-Folia **26.2**

For build commands and zip layout see [RELEASES.md](RELEASES.md). For live status see
[YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md).

---

## v1.0.0.0 — 2026-09-02

First shippable **YaP-Folia** network product release. This is the starting version —
all first-party artifacts stay on **1.0.0.0**. Native plugin stack, first-party Java +
Bedrock crossplay, YaP Link proxy, web dashboard, and the operator SMP commands below.

### Highlights

| Area | What shipped |
|------|----------------|
| **Game authority** | YaP-Folia 26.2 (`folia-jar-source=build`) — regionized tick, not stock Paper/Folia |
| **Crossplay** | First-party ViaBackwards-class JE (1.20.2+) + Geyser-class Bedrock — **no Via\* / Geyser jars** |
| **Network** | YaP Link native proxy, dual-stack gateway, Floodgate-class identity; offline-mode Mojang skins |
| **Plugins** | Full CORE+NETWORK stack: perms, chat, moderation, essentials, claims, regions, protect, world, tab, discord, guard, map, NPCs, … |
| **Operator SMP** | `/bag` (3/5/7/9 pages), `/gm` + `/item`, `/eco`, per-rank name/chat colors, `/yapmod seen` |
| **Gameplay (in the full box)** | MMO (100 quests, 20 bosses, abilities book + hotbar), vehicles, stacker, factions, guilds, **YaP Encyclopedia** (Purpur-inspired knobs) |
| **Ops** | Web dashboard (`:8080`) — ranks, kit builder, plugin YAML editors, Swing GUI, seed defaults, MariaDB/Postgres Docker packages, SQLite single-node |
| **Clients (optional)** | Fabric 26.2 under [`client/`](../../client/): **yap-visuals** (Sodium+Iris+shaders in one jar), **yap-bag**, **yap-ultrawide** — vanilla and Bedrock stay supported without them |
| **Packs** | `yapcore-default` (Faithful + skies + vehicles + MMO icons) |
| **Integrations** | Optional fetch scripts for **Grim AC** and **Tebex** (GPLv3, not bundled by default) |

### Protocol & crossplay

- **JE matrix 4/4 spawn** under compression (1.20.4, 1.21.1, and pinned mid bands).
- **Optional resource packs** auto-acked when `resource-pack-forced=false` — fixes mid-client join timeouts.
- **Bedrock 1.21.50** — RakNet login, spawn, dig/place, chat, commands; play-depth smoke green.
- **Paper column stream** default for Bedrock terrain (flat chunks opt-in only).
- **G.33** placed-skull block-actor sync + item-in-hand SkullOwner Name NBT; full profile-hash textures remain Stretch.
- **Specialty Bedrock containers** — anvil, smithing, loom, stonecutter, cartography open via Paper-backed P4.6 bridge (**Green** best-effort); recipe-picker / anvil rename remain Stretch.
- Limitations documented in [CROSSPLAY.md](../network/CROSSPLAY.md).

### Ops & configuration

- **`config/defaults/`** + `./scripts/seed-defaults.sh` — LAN-friendly first boot.
- **[SECRETS.md](SECRETS.md)** — where owners set MariaDB passwords, dashboard token, Discord webhooks, forwarding secret.
- **Web dashboard** — ranks (name/chat colors), kit builder, players, regions, guard, map, Discord, Link, packs, plugin YAML editors.
- **Release zips** ship defaults/examples only — live operator tokens are never packaged.
- **Privacy / terms templates** for public server operators: [PRIVACY_POLICY.md](PRIVACY_POLICY.md), [TERMS_OF_USE.md](TERMS_OF_USE.md).

### Tier 4 & roadmap closure

Completion backlog **Tiers 1–4 Done** (with live-soak caveats):

- Tier 1: YaPTab sidebar, claim flags, admin menus, combat PvE defaults.
- Tier 2: Dashboard polish, web map, Discord relay, tune docs.
- Tier 3: RS quest/boss roster, abilities, Bedrock skill UI, TAB cross-server sync.
- Tier 4: Protocol phases 4A→4F — dual-stack join, Bedrock play depth, limitation docs.

Roadmap phases **8–17 Done** (dashboard, TAB, Discord, regions, map, guard, NPCs, Bedrock depth, release polish).

### Build & install

```bash
./scripts/build-yap-folia.sh
gradle publishReleasesFolder
# → releases/1.0.0.0/yapcore-release-linux.zip (+ windows, suite zips)

cd releases/1.0.0.0/yapcore-release/linux
cp deploy/mariadb/.env.example deploy/mariadb/.env   # edit passwords
./configure-db.sh --server-id lobby
./start.sh --fg
```

Gameplay (vehicles, stacker, MMO) is included in the full box. Slim CORE+NETWORK: `gradle assembleRelease -PyapGameplay=false`.

### Breaking / migration notes

| From | To |
|------|-----|
| Stock Folia / Paper as default | **YaP-Folia** — rebuild with `./scripts/build-yap-folia.sh` |
| ViaVersion + Geyser + Floodgate jars | **Remove** — use built-in protocol stack |
| LuckPerms / EssentialsX / TAB / DiscordSRV | **Optional** — native YaP plugins cover typical SMP |
| Tracked `folia-kernel/server.properties` | **`server.properties.example`** — live file gitignored; seeded on first start |
| Bench JSON in repo | **Removed** — results are local/gitignored |

### Known limitations (honest)

Not blockers for release; documented for operators:

- **Retail Xbox / full inv UI** — validate with real clients before marketing “full play depth.”
- **ViaRewind 1.8 play depth** — out of product scope.
- **Bedrock specialty UI polish** — recipe pickers (stonecutter/loom) and anvil rename text are Stretch; open + slot sync ship as Green best-effort.
- **Sounds / particles** on older JE clients — same class of issues as ViaBackwards; see limitations doc.
- **YaPGuard** — lightweight native AC; use optional **Grim** for gold-standard checks.
- **Full Geyser feature matrix** — intentional Out; YaP ships depth, not a 1:1 Geyser clone.

### Contributors & license

YaPcore first-party code: **GNU GPLv3** — see [LICENSING.md](LICENSING.md).  
Not affiliated with Mojang, Microsoft, ViaVersion, or GeyserMC.

---

## Earlier milestones (pre-1.0.0.0)

Summarized from git history; not separate tagged releases.

| Period | Themes |
|--------|--------|
| **Link 0.6** | Native Velocity-class proxy phases 0–6; frame+zlib encoder fixes; link plugin suite |
| **YaP-Folia fork** | Managed Folia 26.2 build, sched compat agent, teleport transactions, region pool knobs |
| **MMO content** | Tiered quest compendium (100 quests, 20 bosses), ability VFX, CLAY_BALL icon pack |
| **World & admin** | YaPWorld in-game edit GUI, admin menu, kits, claim flags, regions plugin |
| **Docs regroup** | `docs/` topic folders, whitepaper v0.3 (Markdown source of truth; PDFs not tracked) |

---

## After 1.0.0.0 (same version — not a bump)

Product stays on **1.0.0.0**. Rebuild / republish artifacts with `gradle publishReleasesFolder`
when cutting a refreshed zip; do **not** change Gradle `version` until a real tag bump.

### Shipped on 1.0.0.0 line (post-tag refresh)

| Area | Change |
|------|--------|
| **YaPWorld** | FAWE-class phases 1–5: masks/patterns, brushes (+erode/raise/lower/melt/fill/forest), entity clipboard + paste `-a/-e/-b/-o/-s`, `//generate` + expression deform, `//fixlighting`, `//limit`, `.yschem` / `.schem` export + `.schematic`/`.litematic` import, WE shim clipboard surfaces — [YAPWORLD.md](../plugins/YAPWORLD.md) |
| **YaPTab** | Folia-safe Bukkit scoreboard sidebar (removed megavex packet path that kicked on join) |
| **YaPChat** | Secure-chat login rewrite fixed for YaP-Folia 26.2 (`ClientboundLoginPacket`) |
| **Economy** | Native `PlayerDataService` balance (deposit/withdraw/set) — crafting `/sell`, games rewards drop Vault fallback |
| **Dashboard** | Kit builder, player eco/perm actions, Tebex/plugin YAML editors, friendlier forms |
| **Packs / clients** | YaP Abilities icons in default pack; skies/water texture refresh; optional **yap-visuals** / Iris / Sodium client stack docs |
| **Mechanics** | Optional water-wave visuals |
| **Ops docs** | Public hostname `yapcoremc.yaplabs.us`, packs via nginx `:80`, grey-cloud game DNS |
| **Ability VFX (V1–V4)** | Engine primitives (`at:`, shake, arcs, motion trails, new shapes), element/archetype kits for 227 bulk abilities, 12 hero casts, unique icons, Folia soak gates — [MMO_ABILITY_VFX.md](../mmo/MMO_ABILITY_VFX.md) |
| **Ops Waves 1–5** | Folia-safe pregen/protect/regions; Bedrock inventory fidelity; quest PLAYTIME/ECONOMY/… objectives; Discord event webhooks; map markers; dashboard Access context/temp + social/stacker panels; cite fixtures −5.53% fullcite (peak −12.4%) — [REAL_GAINS.md](../folia/REAL_GAINS.md) |
| **YaP Encyclopedia** | Real Purpur-inspired `knobs.yml` surface (original YaP code): full attributes, ride perms, per-mob specials, gameplay/blocks wiring, `/yapknobs status`; optional Folia NMS crop/fluid hooks (`0025-yap-encyclopedia-hooks`) — [TUNE.md](../ops/TUNE.md) |
| **Cite vs Canvas** | Heavypop campaign `20260904T065505Z`: YaP **−8.09% vs Canvas** (citeable ≥5%), **−16.56% vs stock** under disclosed ship knobs — [CANVAS_PARITY.md](../folia/CANVAS_PARITY.md) · [REAL_GAINS.md](../folia/REAL_GAINS.md) |
| **vs Paper/Purpur** | Honest scale/product framing (regionized + encyclopedia + suite); no single-thread MSPT claim — [PAPER_PURPUR_SCALE.md](../folia/PAPER_PURPUR_SCALE.md) |
| **Bedrock specialty containers** | Anvil, smithing, loom, stonecutter, cartography — Paper-backed open + slot sync on native UDP (**Green** best-effort); recipe-picker / rename Stretch — [CROSSPLAY.md](../network/CROSSPLAY.md) |
| **Repo layout** | Optional Fabric client mods nested under [`client/`](../../client/) (`yap-visuals`, `yap-bag`, `yap-ultrawide`, Iris/Sodium/shaders) |
| **YaPCommands** | YAML custom `/commands` (`yap-commands.jar`) with dashboard **Custom commands** CRUD — messages, player/console runs, aliases, cooldowns — [COMMANDS.md](../ops/COMMANDS.md) · [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) |
| **YaP Vehicles pack** | Fleet HD bodies replaced with [Automobility](https://github.com/FoundationGames/Automobility) MIT meshes (re-import: `scripts/import-automobility-vehicles.py`); trademark display names retired; `yapcore-default` GAMEPLAY pack refreshed — [VEHICLES.md](../plugins/VEHICLES.md) · [CREDITS.md](../../resourcepacks/CREDITS.md) |
| **Packs / CDN** | Default `resource-pack-url` → GitHub `releases/latest/download/{file}`; SHA-1 hashed from the remote bytes clients download; `public-pack-port` 80/443 honored for nginx edge — [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) |
| **GitHub release assets** | Tag `1.0.0.0` ships OS zips, suites, `yapcore-default.zip`, and optional Fabric clients (`yap-visuals`, `yap-bag`, `yap-ultrawide`) |
| **Docs hygiene** | Generated PDFs / office dumps gitignored — publish Markdown only |

### Still open (not a version bump)

- Manual §E live checklist ([CROSSPLAY.md](../network/CROSSPLAY.md)) — include specialty-station smoke
- Bedrock recipe-picker / anvil rename polish (Stretch)
- Next-protocol dump when Mojang ships a new JE build ([VANILLA_CLIENTS.md](../network/VANILLA_CLIENTS.md))
- YaPWorld NMS section placement / FAWE CFI (intentionally out of scope)
- Finish live **12h soak-long** + in-game VFX / encyclopedia smoke before marketing the refreshed zip as soak-proven
- Rebuild YaP-Folia with `0025` encyclopedia NMS patch when enabling `crop-growth-nms` / `tick-fluids=false` in production

`releases/1.0.0.0/` was republished after pack CDN/SHA sync + client visuals refresh (`gradle publishReleasesFolder` + `./scripts/build-yap-client-render.sh`, 2026-09-04). Prior same-day refresh covered specialty-container + Automobility vehicles + YaPCommands / YaPWorld.

---

*1.0.0.0 remains the ship version. Bump only when cutting a later tagged release.*
