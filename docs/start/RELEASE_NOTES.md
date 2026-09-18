# YaPcore release notes

Product version **1.0.0.0** · YaP Link **0.6.0-phase6** · YaP-Folia **26.2**

For build commands and zip layout see [RELEASES.md](RELEASES.md). For live status see
[YAPCORE_WHITEPAPER.md](../whitepaper/YAPCORE_WHITEPAPER.md).

YaP-Folia provenance polish + `UPSTREAM.lock` refresh — [YAP_FOLIA_PATCHES.md](../folia/YAP_FOLIA_PATCHES.md).

---

## After 1.0.0.0 — Creative climate, NPC Folia spawn, reach, Bedrock, ultrawide (2026-09-17)

Same ship version (no product bump). Rebuild with `gradle publishReleasesFolder -PyapGameplay=true`.

| Area | Change |
|------|--------|
| **YaPNpcs** | Folia region-thread spawn (no global `spawnEntity` NPE); `server-id: default` falls back to fleet `yap-server-id.txt`; shop professions; no sync teleport |
| **YaPWorld** | Creative-hub climate: always noon, no weather cycle, no natural mobs (`climate.enabled`, auto-on for instance `creative`) |
| **YaPEssentials** | `block-reach` (survival 6.5 / creative 8); optional hub spawn-on-join |
| **YaPPortals** | Pad fill only replaces air / portal / glass — does not overwrite signs or builds |
| **Fleet** | Default **creative** instance; flat swap sets peaceful + no-spawn; per-instance PlayerData inventory profile |
| **Dashboard** | Kit item fields + YaPItems catalog; fleet world-swap-flat; plugin hints for reach/climate |
| **YaP Link / Bedrock** | JE→BE block remapper, dimension/join, entity list, Bungee Connect pending |
| **yap-ultrawide** | Hor+ HUD shares world frustum; viewmodel + view-bob scaled so placement matches the crosshair |
| **Docs** | [YAPWORLD.md](../plugins/YAPWORLD.md) · [COMMANDS.md](../ops/COMMANDS.md) · [PORTALS.md](../network/PORTALS.md) · [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) · [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) |

Build: `gradle publishReleasesFolder -PyapGameplay=true` → `releases/1.0.0.0/`. Upload with `--clobber` per [RELEASES.md](RELEASES.md).

---

## After 1.0.0.0 — NPC shops UX + dashboard Shops + fleet polish (2026-09-17)

Same ship version (no product bump). Rebuild with `gradle publishReleasesFolder -PyapGameplay=true`.

| Area | Change |
|------|--------|
| **YaPPlayerData / YaPNpcs** | Built-in shop presets (`weapons`/`armor`/`tools`/`food`/`blocks`/`redstone`/`crafting`/`enchants`); unlimited stock; buyback ≈38%; enchanted buy-only |
| **In-game shop GUI** | One icon per item — left-click buy / right-click sell; quantity picker with running totals |
| **`/npc shop`** | `apply` / `presets` / `setitem` (buy+sell upsert) / `setoffer` / `clearoffers`; `/npc setname` / `move` |
| **Dashboard** | **Shops** tab — one row per item (Buy $ + Sell $); `/api/shops` (`setitem`, presets, list) |
| **YaPPortals** | Arrival lands at destination spawn; catalog mirror + wand polish |
| **YaPRegions** | `/region worldborder` fits vanilla border to region XZ AABB |
| **YaPItems** | Fleet catalog propagate + watch on create (network-wide custom items) |
| **Link / PlayerData** | Faster session unlock on soft-switch / hub transfers; lock release on quit |
| **Fleet** | Console command dispatch captures Folia JSON; heal broken lobby `plugins` symlink |
| **Domain ≤500** | Split SoftSwitch handlers, `NpcTraderTradeGui`, `NpcShopCatalogOps` |
| **Docs** | [PLAYERDATA.md](../data/PLAYERDATA.md) · [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) · [COMMANDS.md](../ops/COMMANDS.md) · [PORTALS.md](../network/PORTALS.md) · [YAPITEMS.md](../plugins/YAPITEMS.md) · [GAMEPLAY.md](../gameplay/GAMEPLAY.md) |

Build: `gradle publishReleasesFolder -PyapGameplay=true` → `releases/1.0.0.0/`. Upload with `--clobber` per [RELEASES.md](RELEASES.md).

---

## After 1.0.0.0 — Portals colors + hub region flags (2026-09-17)

Same ship version (no product bump). Rebuild with `gradle publishReleasesFolder -PyapGameplay=true`.

| Area | Change |
|------|--------|
| **YaPPortals** | Walk-through colored pads (LIGHT + dye particles) — no solid glass; `/portal setcolor` / create `[color]` |
| **YaPRegions** | Hub flags: `damage`, `use`, `hunger`, `farmland-trample`, `item-frame`, `armor-stand`, `leaf-decay`, `pistons`, `vehicle-place`/`destroy`; `/region gamemode`; immediate apply + OP-bypass tip |
| **Claims** | Softened doors/plates via `use` (parkour-friendly); shared `damage` flag |
| **YaPAdmin** | Give menu armor/weapon/tool gear kits |
| **YaPPerms** | VIP keep-inventory starter grant + backfill |
| **YaPNpcs** | `server:` / `/npc setserver` → YaPPortals Connect |
| **Docs** | [PORTALS.md](../network/PORTALS.md) · [GAMEPLAY.md](../gameplay/GAMEPLAY.md) regions |

Build: `gradle publishReleasesFolder -PyapGameplay=true` → `releases/1.0.0.0/`. Upload with `--clobber` per [RELEASES.md](RELEASES.md).

---

## After 1.0.0.0 — YaPPortals fleet transfers (2026-09-14)

| Area | Change |
|------|--------|
| **YaPPortals** | CORE+NETWORK default plugin, **on** — walk-through portals → Link `Connect` (`yap-portals.jar`) |
| **YaPNpcs** | `server:` / `/npc setserver` soft-dep on YaPPortals |
| **Link** | `/hub` still from `yaplink-server-selector`; portals are the Folia UX layer |
| **Docs** | [PORTALS.md](../network/PORTALS.md) |

---

## After 1.0.0.0 — Link /hub command intercept (2026-09-16)

| Area | Change |
|------|--------|
| **YaP Link** | Play relay now dispatches registered plugin commands (`/hub`, `/server`) from JE `chat_command` — previously registered but never intercepted (fell through to Folia) |
| **Transfer packet** | Play `transfer` id for 26.2/776 is `0x81` (was stale `0x7A` → client “failed to decode packet”); localhost clients Transfer to `127.0.0.1` |
| **system_chat** | 26.2 expects NBT text components (not JSON strings) — fixes `/server` DecoderException on `minecraft:system_chat` |

---

## After 1.0.0.0 — CI Folia smoke / ≤500 domain splits (2026-09-14)

| Area | Change |
|------|--------|
| **Folia CI** | Nightly/main `folia-fork.yml` runs boot smoke after jar build (`SKIP_SMOKE` removed) |
| **Domain ≤500** | Split FleetService, ControlPanel, DashboardLinkSnapshot, AdminMenusOps, ProtectServiceImpl, TailorServiceImpl |

---

## After 1.0.0.0 — plugin ship gaps / combat XP / defaults (2026-09-14)

| Area | Change |
|------|--------|
| **Release / dist** | `yap-tailor.jar` + `yap-bedrock-blocks.jar` in `assembleRelease` + `assemblePluginDist` |
| **YaPItems ↔ YaPSkills** | Gear-only `CombatService` no longer suppresses Skills combat XP (`ownsCombatXp()`) |
| **Defaults** | Seed `YaPTailor/` + `YaPTebex/`; Tailor jar default clears public skin-host URL |
| **YaPNpcs** | `unlock_recipe` / `teleport_unlock` warn honestly (no fake `yapmmo` dispatch) |

---

## After 1.0.0.0 — QoL defaults / Protect sessions / SNAPPY / map (2026-09-14)

| Area | Change |
|------|--------|
| **YaP-QoL** | `config/defaults/plugins/YaP-QoL/` seeded; dashboard blurb is product-default (not opt-in) |
| **YaPSkills** | Removed display-only combat level (PAPI/menu/calculator) |
| **YaPProtect ↔ YaPWorld** | WorldEdit apply batches log with `edit_op_id`; `/yapprotect lookup\|rollback session <uuid>` |
| **Bedrock SNAPPY** | Link + chassis inflate method=1 (snappy-java) instead of dropping batches |
| **YaPMap** | Config fallbacks aligned; [MAP.md](../ops/MAP.md) restored |
| **GameplayKnobs / Folia 0025** | Crop accelerate (`extraCropGrowthAttempts`) wired in patch + work tree |
| **Defaults docs** | Skills/LeveledMobs/QoL/Map tiers match shipped YAML |

---

## After 1.0.0.0 — YaP-QoL / fleet ensure preserve (2026-09-14)

Same ship version (no product bump). Rebuild **linux** + **windows** + suites with
`gradle publishReleasesFolder -PyapGameplay=true`.

| Area | Change |
|------|--------|
| **YaP-QoL** | Timber axe + area excavator (3×3/6×6/9×9); VIP kit; staff `/yapqol` + admin menu — [PLUGINS.md](../plugins/PLUGINS.md) |
| **Defaults** | `yap-items.jar` + `yap-qol.jar` seed on every fleet instance and ship in the release box (not slim-stripped) |
| **Fleet ensure** | Start/ensure **merges** `ops.json` (keeps in-game OPs); never deletes/overwrites installed plugin jars or `.jar.disabled` |
| **GUI** | Opening Control GUI does not rewrite instance trees — layout prep runs on create / Start / enable-fleet only |
| **Packaging** | `assembleRelease` / `assembleGameplaySuite` / `assemblePluginDist` include `yap-qol.jar` |

Build: `gradle publishReleasesFolder -PyapGameplay=true` → `releases/1.0.0.0/`. Upload GitHub assets with `--clobber` per [RELEASES.md](RELEASES.md).

---

## After 1.0.0.0 — Fleet GUI / dashboard / branding (2026-09-14)


Same ship version (no product bump). Rebuild **linux** + **windows** packages with
`gradle publishReleasesFolder -PyapGameplay=true` so operators get current chassis + web assets.

| Area | Change |
|------|--------|
| **Fleet defaults** | New instances seed **CORE+NETWORK** plugins; empty lobby/plugins heal on enable/list |
| **Fleet GUI** | Fleet-first rail: **YaP Link** + game servers; Plugins-first per server; Connect under Link |
| **Settings persist** | Instance MOTD / max-players / view-distance no longer clobbered on ensure/start |
| **Web branding** | Favicon / login / sidebar use `branding/yapcore-icon.png` + `yapcore-mark.png` |
| **Dashboard Start/Stop** | Badge and controls follow **fleet game servers**, not chassis DualStack alone |
| **Docs / DB copy** | YaPDB wording: MariaDB/MySQL · PostgreSQL · SQLite (not MariaDB-only) |

Build: `gradle publishReleasesFolder -PyapGameplay=true` → `releases/1.0.0.0/yapcore-release-{linux,windows}.zip`.

---

## After 1.0.0.0 — Packs / Link MOTD / GUI launch (2026-09-14)

Same ship version (no product bump). Ops-facing correctness:

| Area | Change |
|------|--------|
| **Default JE pack** | Faithful **64× held items restored** (no longer stripped to vanilla sprites) — rebuild `yapcore-default.zip` and upload GitHub release assets |
| **Pack CDN** | Product path is GitHub `releases/latest/download/{file}` — do not advertise a dead public-host `/pack/` URL |
| **Link MOTD max** | Aggregate max = **sum of UP backends only**; `max-players` in `link.properties` is a ceiling, not a floor |
| **`gui.sh`** | Fast launch by default (existing `yapcore.jar`); pass `--build` after chassis/GUI source changes |

Rebuild Link: `gradle :yap-link-native:shadowJar` → copy to root `yap-link.jar`. Publish packs from `releases/1.0.0.0/` or `resourcepacks/`.

---

## After 1.0.0.0 — Physics sub-steps (2026-09-13)

Same ship version (no product bump). Internal movement/combat integration rate:

| Area | Change |
|------|--------|
| **Physics sub-steps** | Patch `0031` — `YapTravelSubstep` + `YapMoveSubstep` (gravity/friction scaled; plugin tick unchanged) |
| **Ship defaults** | `folia-physics-substeps=true`, `folia-physics-substep-count=4`, `folia-physics-substep-min-move=0.02` |
| **Domain** | Helpers ≤500 lines (`YapPhysicsSubsteps` / move / travel) |
| **Docs** | YAP_FOLIA_PATCHES / SOAK / REAL_GAINS — feel vs capacity |

Build: `./scripts/build-yap-folia.sh`. Rollback: `folia-physics-substeps=false`.

---

## After 1.0.0.0 — Aligned micro/sub-ticks (2026-09-13)

Same ship version (no product bump). Real YaP-Folia micro/sub-tick phases across regions:

| Area | Change |
|------|--------|
| **Aligned microticks** | Patches `0026`–`0030` — `YapMicroPhase` waves + soft epoch barriers + universal RTQ phase tagging |
| **Ship defaults** | `folia-aligned-microticks=true`, `folia-micro-phases=4`, `folia-tick-wave-max-wait-ms=2` |
| **Docs** | README / YAP_FOLIA_PATCHES — AI time-slice vs aligned phases |
| **Cite** | Re-run `./scripts/yapctl cite-fullcite` with `knob_aligned_microticks` disclosed |

Build: `./scripts/build-yap-folia.sh` · smoke: `YAP_FOLIA_ALIGNED_MICROTICKS=true ./scripts/smoke-folia.sh`.

---

## After 1.0.0.0 — Docs / AI transparency (2026-09-13)

Same ship version (no product bump). Documentation refresh for GitHub and operators:

| Area | Change |
|------|--------|
| **README** | Product surface includes Link-native Bedrock, Bedrock-feel, Tailor / presence / blocks |
| **AI disclosure** | [AI_TRANSPARENCY.md](AI_TRANSPARENCY.md) — AI-assisted development under human accountability |
| **Whitepaper** | **v0.5** — native join, parity, ≤500 domain gate, AI note |
| **Indexes** | Wiki / docs README / LICENSING / SECURITY / CONTRIBUTING / plugins lists updated |
| **PDFs** | `./scripts/export-docs-pdf.sh` priority set expanded (local / gitignored) |

---

## After 1.0.0.0 — Bedrock-feel + native join + ≤500 domains (2026-09-13)

Same ship version (no product bump). Crossplay join path, Bedrock-feel pillars, and domain hygiene:

| Area | Change |
|------|--------|
| **Native join** | Link-native Bedrock join path — [YAP_LINK.md](../network/YAP_LINK.md) · [CROSSPLAY.md](../network/CROSSPLAY.md) |
| **Bedrock-feel** | Parity catalogs/matrix (phases 0–6), movement/emotes/skin glue; Tailor + **yap-presence** / **yap-blocks** clients — [BEDROCK_FEEL_PARITY.md](../product/BEDROCK_FEEL_PARITY.md) |
| **Domain ≤500** | Chassis + first-party oversize classes split to same-package helpers; `gradle checkDomainLineLimits` green |
| **client_mods** | Zip now includes **yap-presence** + **yap-blocks** alongside visuals/bag/staff/ultrawide |

Docs: [CROSSPLAY.md](../network/CROSSPLAY.md) · [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) · [RELEASES.md](RELEASES.md).

Build: `./scripts/parity/smoke-bedrock-feel.sh` · `./scripts/build-yap-client-render.sh` · `gradle publishReleasesFolder -PyapGameplay=true` · copy shadow Link jar → repo-root `yap-link.jar`.

---

## After 1.0.0.0 — World tools + Bedrock pack CDN (2026-09-08 evening)

Same ship version (no product bump). Operator build tools + crossplay pack path:

| Area | Change |
|------|--------|
| **YaPAdmin World tools** | Single hub tile for world edit + schematics + live paste preview (confirm / move / rotate / flip / undo); Bedrock form hub entry |
| **YaPWorld schem preview** | Interactive **Rotate 90°** (shift = CCW) and **Flip**; outline colour/yaw tip; `//schem rotate` / `flip`; `//rotate` prefers active preview |
| **Bedrock packs** | Offer GitHub `releases/latest/download/{file}` (same CDN as JE); pack handshake defers JOIN/forms until after StartGame; mcpack `min_engine_version` pinned to 1.21.60 |
| **yap-staff 1.0.30** | More… → World & build links for schematics + admin World tools |

Docs: [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) · [YAPWORLD.md](../plugins/YAPWORLD.md) · [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md).

Build: `./scripts/build-yap-client-render.sh` · `gradle publishReleasesFolder -PyapGameplay=true` · restart Folia after jar/plugin swap.

---

## After 1.0.0.0 — YaPItems + staff create + shaders (2026-09-08)


Same ship version (no product bump). Custom items + client polish:

| Area | Change |
|------|--------|
| **YaPItems** | New gameplay plugin: YAML registry, multi-ability keybinds, furniture, recipes, kits `yap-item:`, create/delete/cooldown CLI — [YAPITEMS.md](../plugins/YAPITEMS.md) |
| **YaPAdmin** | Hub **Custom items** create wizard (categorized abilities, glow/unbreakable, enchant picker, break volume, potion options) |
| **yap-staff 1.0.27** | Custom items + enchant picker; `yap:staff` payload channel for long create/edit (avoids chat_command kick); `--nameb64` / `--abilities` |
| **YaP Shaders** | Glass ≠ water; waterfall cascade path; wind on leaves/grass/vines only (log builds stay still) |
| **Pack / kits** | `yap-items` overlay in `yapcore-default`; kit YAML `yap-item:` rows |

Docs: [YAPITEMS.md](../plugins/YAPITEMS.md) · [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) · [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) · [yap-staff/README.md](../../client/yap-staff/README.md) · [yap-shaders/README.md](../../client/yap-shaders/README.md).

Build: `gradle installGameplayDefaults` · `./scripts/build-yap-client-render.sh` · full Folia restart after jar swap.

---

## After 1.0.0.0 — staff client + pack/Via polish (2026-09-07)

Same ship version (no product bump). Operator / client staff UX:

| Area | Change |
|------|--------|
| **yap-staff 1.0.4** | Full Fabric staff GUI (players, give, trolls, mod, economy, **ranks/perms**, deep links); scrollable + window-scaled layout; searchable player picker returns to calling tool; **fix empty scroll body** (`arrangeElements` before `setMaxHeight`) |
| **YaPAdmin** | Trolls / give / money subcommands; economy deposits via `PlayerDataService` on the entity region thread (Folia-safe) |
| **yap-bag 1.0.2** | Mixins target `AbstractContainerScreen` for MC 26.2; page tabs above 6/5-row panel; **`yap:bag` HELLO** → server omits bottom item-nav (45-slot GUI) |
| **Via packs** | Forward optional Paper resource-pack prompts to modern JE (no longer auto-accept-only when unforced) |
| **YaPTab** | Sidebar/footer resolve `{balance}` and `${balance}` |
| **Packs / scripts** | GitHub Releases pack offer sync; Control Panel / `gui.sh` home resolution |

Docs: [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) · [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) · [yap-staff/README.md](../../client/yap-staff/README.md) · [yap-bag/README.md](../../client/yap-bag/README.md).

Build clients: `./scripts/build-yap-client-render.sh`. Rebuild admin/tab: `gradle :admin-plugin:jar :tab-plugin:shadowJar`. Republish trees: `gradle publishReleasesFolder -PyapGameplay=true`.

---

## v1.0.0.0 — product polish P0/P1 (2026-09-05)

Same ship version (no bump). Operator-facing consistency and ops reload parity:

| Area | Change |
|------|--------|
| **Shared messages** | `yap-messages-api` — Adventure text, permission errors with nodes, `YapConfigReload` / `YapHelp` — [PLUGINS.md](../plugins/PLUGINS.md) |
| **Defaults pack** | Full `config/defaults/plugins/` coverage for CORE+NETWORK + gameplay seeds (BedrockUI / FoliaBridge / WorldEdit shim N/A) — [DEFAULTS.md](DEFAULTS.md) |
| **Bedrock hubs** | `/menu`, kits/homes/warps, ranks, admin forms via YaPBedrockUI (JE keeps chests) — [CROSSPLAY.md](../network/CROSSPLAY.md) |
| **Ops / dashboard** | Catalog reload parity (admin, pregen, floodgate, dungeons, conquest, …); DB-not-ready / profile-loading UX; invalid YAML numbers → HTTP 400 |
| **Factions / Conquest** | Survival guild polish + opt-in YaPConquest chunk land (still `enabled: false` by default) — [GAMEPLAY.md](../gameplay/GAMEPLAY.md) · [GAMEPLAY.md](../gameplay/GAMEPLAY.md) |

Rebuild release trees: `./scripts/build-yap-folia.sh && gradle publishReleasesFolder -PyapGameplay=true`.

---

## v1.0.0.0 — client visuals refresh (2026-09-05)

Optional Fabric / pack polish on the same ship version (no version bump):

| Area | Change |
|------|--------|
| **Release asset** | Upload **`client_mods.zip`** (yap-visuals + yap-bag + yap-staff + yap-ultrawide) |
| **YaP Shaders** | Multi-dir Gerstner water; weather-driven species foliage wind; softer distance fog; leaf cutout path |
| **Default pack** | Denser Faithful-based leaves (`strict_cutout`); water/weather overlay refresh |
| **yap-ultrawide** | Separate **21:9** and **32:9** Hor+ profiles (`match_16_9` / `match_21_9` / `fixed_hfov` + HFOV cap) |
| **yap-bag** | Screen mixins updated for MC 26.2 chest / inventory layout — use **1.0.2+** (HELLO → no item-nav row) |
| **yap-staff** | Esc / **R** full Staff GUI (scroll/scale, ranks, searchable pick) — **1.0.4+** |

Build: `./scripts/build-yap-client-render.sh` · `./scripts/build-default-resourcepack.sh`.
Docs: [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md), [RELEASES.md](RELEASES.md).

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
| **Gameplay (opt-in)** | Thin skills (mining/woodcutting/strength), disasters, stacker, factions, **YaP Encyclopedia** (Purpur-inspired knobs). Gameplay jars default **off** until enabled. |
| **Ops** | Web dashboard (`:8080`) — ranks, kit builder, plugin YAML editors, Swing GUI, seed defaults, MariaDB/Postgres Docker packages, SQLite single-node |
| **Clients (optional)** | Fabric 26.2 under [`client/`](../../client/): **yap-visuals** (Sodium+Iris+shaders in one jar), **yap-bag**, **yap-staff**, **yap-ultrawide** — vanilla and Bedrock stay supported without them |
| **Packs** | `yapcore-default` (Faithful + skies) |
| **Integrations** | Optional fetch scripts for **Grim AC** and **Tebex** (GPLv3, not bundled by default) |

### Protocol & crossplay

- **JE matrix 4/4 spawn** under compression (1.20.4, 1.21.1, and pinned mid bands).
- **Optional packs on Via** — modern JE (≥1.20.2) get the Folia login Yes/No prompt; Via no longer auto-acks optional packs (that hid downloads). Mid-band clients still auto-ack.
- **Bedrock 1.21.50** — RakNet login, spawn, dig/place, chat, commands; play-depth smoke green.
- **Paper column stream** default for Bedrock terrain (flat chunks opt-in only).
- **G.33** placed-skull block-actor sync + item-in-hand SkullOwner Name NBT; full profile-hash textures remain Stretch.
- **Specialty Bedrock containers** — anvil, smithing, loom, stonecutter, cartography open via Paper-backed P4.6 bridge; **recipe pick** wired for stonecutter/loom/smithing/cartography (CRAFT_RECIPE_OPTIONAL); anvil rename via FILTER_TEXT (deploy chassis after soak).
- Limitations documented in [CROSSPLAY.md](../network/CROSSPLAY.md).

### Ops & configuration

- **`config/defaults/`** + `./scripts/seed-defaults.sh` — LAN-friendly first boot.
- **[SECRETS.md](SECRETS.md)** — where owners set MariaDB passwords, dashboard token, Discord webhooks, forwarding secret.
- **Web dashboard** — ranks (name/chat colors), kit builder, players, regions, guard, map, Discord, Link, packs, plugin YAML editors.
- **Release zips** ship defaults/examples only — live operator tokens are never packaged.
- **Privacy / terms templates** for public server operators: [PRIVACY_POLICY.md](PRIVACY_POLICY.md), [TERMS_OF_USE.md](TERMS_OF_USE.md).

### Tier 4 & roadmap closure

Completion backlog **Tiers 1–4 Done** (with live-soak caveats):

- Tier 1: YaPTab sidebar, claim flags, admin menus.
- Tier 2: Dashboard polish, web map, Discord relay, tune docs.
- Tier 3: TAB cross-server sync, optional skills/disasters/stacker.
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

Gameplay (skills, disasters, stacker, knobs) is included when `-PyapGameplay=true`. Slim CORE+NETWORK: `gradle assembleRelease -PyapGameplay=false`.

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
- **Bedrock specialty UI** — recipe pick wired (stonecutter/loom/smithing/cartography); anvil rename FILTER_TEXT pending chassis deploy after soak. Retest on Bedrock before marketing full play depth.
- **Sounds / particles** on older JE clients — same class of issues as ViaBackwards; see limitations doc.
- **YaPGuard** — lightweight movement heuristics only; **competitive / PvP requires Grim** (`./scripts/grim-ac.sh enable` + Folia restart) — [GRIM.md](../ops/GRIM.md)
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
| **YaP-Folia fork** | Managed YaP-Folia 26.2 build, sched compat agent, teleport transactions, region pool knobs |
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
| **Economy** | Native `PlayerDataService` balance (deposit/withdraw/set) |
| **Dashboard** | Kit builder, player eco/perm actions, Tebex/plugin YAML editors, friendlier forms |
| **Packs / clients** | Skies/water texture refresh; optional **yap-visuals** / Iris / Sodium client stack docs |
| **Essentials** | Optional water-wave visuals (`features.water-waves`) |
| **Ops docs** | Public hostname `yapcoremc.yaplabs.us`, packs via nginx `:80`, grey-cloud game DNS |
| **Ops Waves 1–5** | Folia-safe pregen/protect/regions; Bedrock inventory fidelity; Discord event webhooks; map markers; dashboard Access context/temp + social/stacker panels; cite fixtures −5.53% fullcite (peak −12.4%) — [YAP_FOLIA_PATCHES.md](../folia/YAP_FOLIA_PATCHES.md) |
| **YaP Encyclopedia** | Purpur-**inspired** event-wired `knobs.yml` (original YaP code): attributes, ride perms, per-mob specials, gameplay/blocks; crop/fluid **NMS opt-in** only after YaP-Folia `0025` + soak — not full Purpur without that — [TUNE.md](../ops/TUNE.md) |
| **Bedrock specialty containers** | Anvil, smithing, loom, stonecutter, cartography — open + slot sync + **recipe pick**; anvil rename FILTER_TEXT pending deploy — [CROSSPLAY.md](../network/CROSSPLAY.md) |
| **Repo layout** | Optional Fabric client mods nested under [`client/`](../../client/) (`yap-visuals`, `yap-bag`, `yap-staff`, `yap-ultrawide`, Iris/Sodium/shaders) |
| **YaPCommands** | YAML custom `/commands` (`yap-commands.jar`) with dashboard **Custom commands** CRUD — messages, player/console runs, aliases, cooldowns — [COMMANDS.md](../ops/COMMANDS.md) · [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) |
| **Packs / CDN** | Default `resource-pack-url` → GitHub `releases/latest/download/{file}`; SHA-1 hashed from the remote bytes clients download; `public-pack-port` 80/443 honored for nginx edge — [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md) |
| **GitHub release assets** | Tag `1.0.0.0` ships OS zips, suites, `yapcore-default.zip`, and optional Fabric `client_mods.zip` (yap-visuals + yap-bag + yap-staff + yap-ultrawide) |
| **Docs hygiene** | Generated PDFs / office dumps gitignored — publish Markdown only |
| **Typical SMP defaults** | Chat slow-mode 3; claim tax off; map claim markers on; Disasters opt-in off; CHANGE_ME command links; generic Tab branding; dashboard **Opt-in** badge; seed↔jar drift CI — [DEFAULTS.md](DEFAULTS.md) · [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) |

### Still open (not a version bump)

- **Manual §E live checklist** — checklist lives in [CROSSPLAY.md](../network/CROSSPLAY.md); operator must tick join + specialty stations on a live box (cannot automate Xbox)
- Bedrock specialty recipe picks (stonecutter / loom / smithing / cartography) implemented via Paper CRAFT_RECIPE_OPTIONAL — deploy chassis after soak; JE already signed off
- Anvil rename FILTER_TEXT path started (codec + Paper hook — deploy after soak)
- Next-protocol dump when Mojang ships a new JE build ([CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md))
- YaPWorld NMS section placement / FAWE CFI (intentionally out of scope)
- **12h soak-long PASS** (`logs/soak/soak-long-20260905T031507Z.log`) — zip may be marketed as **soak-proven**; heap/thread slope flat (folia heap median early≈1012MB late≈1082MB; threads 137→137) per [YAP_FOLIA_PATCHES.md](../folia/YAP_FOLIA_PATCHES.md)
- Rebuild YaP-Folia with `0025` encyclopedia NMS patch when enabling `crop-growth-nms` / `tick-fluids=false` in production (defaults stay **off**)
`releases/1.0.0.0/` republished 2026-09-13 with Bedrock-feel + native join, Link Bedrock module, presence/blocks clients, and domain ≤500 splits (`./scripts/build-yap-client-render.sh` then `gradle publishReleasesFolder -PyapGameplay=true`). Prior: World tools + schem rotate/flip + Bedrock pack CDN (2026-09-08 evening); YaPItems/staff **1.0.27** same day; staff/bag polish (2026-09-07); typical SMP defaults (2026-09-06); pack CDN/SHA + client visuals (2026-09-04).

---

*1.0.0.0 remains the ship version. Bump only when cutting a later tagged release.*
