# YaPcore Admin Dashboard

Browser-based **admin control panel** for headless hosts and remote operators.
Set up the network, configure YaP Link, manage plugins, and monitor health — no SSH required for day-to-day ops.

Controls the **YaPcore chassis** in front of **YaP-Folia** (game child JVM). Build with `./scripts/build-yap-folia.sh`.

Modern **sidebar shell** with three operator modes (**Operate · Configure · Gameplay**), teal brand theme aligned with the Swing control panel, cross-mode page search, stat cards, and full plugin config editors where the backend supports it.

Colored text (ranks, MOTD, tab list, NPC names, chat format) uses **clickable Minecraft color swatches**, a custom hex picker, and bold/italic controls — plus a live preview. Hex is stored as `&#rrggbb`.

## Enable

Default on. Config (`config/server.properties`):

```properties
web-dashboard-enabled=true
web-dashboard-port=8080
web-dashboard-bind=0.0.0.0
web-dashboard-token=
web-dashboard-localhost-only=false
ops=
auto-op=false
```

Empty `web-dashboard-token` → a random token is generated on first start and
saved into the config file (also printed in the server log).

## Open

```
http://127.0.0.1:8080/
```

Paste the token on the login screen (or set `Authorization: Bearer <token>`).

**Quick access:** type `dashboard` in the YaP Control Panel console or headless stdin — prints a one-click login URL and token. In the Swing GUI, use **Web Dashboard** (header) or **Connect → Open**.

Login links include `?token=…` so the browser signs in automatically (token is stripped from the address bar after load).

## Navigation

Modes live in the sidebar (`app-shell.js`). Switching modes only changes the nav list — every tab and API stays available. Search finds pages across all modes.

| Mode | Groups | Tabs |
|------|--------|------|
| **Operate** | Now | Dashboard, Fleet, Players, Console, Connect |
| **Configure** | Network · People · Content | **Setup**, YaP Link, Network setup, Server setup, Access & ranks, Rank pack, Plugins, Plugin settings, Modules, Packs, World, Regions, NPCs, **Holograms**, **Shops** |
| **Gameplay** | Core · World & safety · Opt-in | Essentials, Chat, Tab list, Player data, Kits, Custom commands, Protect, Guard, Map, Pregen, Discord, Tebex, Skills, Factions, Disasters, Stacker |

Static assets: `src/main/resources/web/` — `app-shell.js`, `app-core.js`, `app-*-panels.js`, `style.css`.

## First-boot plugin config

CORE + NETWORK plugins are ready with typical SMP defaults (chat slow-mode, economy/homes/claims, protect, lagguard). Opt-in jars (Factions, Conquest, Skills, Stacker, Dungeons, Disasters, GameplayKnobs) show an **Opt-in** badge on the **Plugins** tab — installed, soft-off until you enable them from Plugins or Plugin settings, then Save → reload.

Secrets stay LAN-safe (`auth` off, dashboard/map binds on localhost). Walk [DEFAULTS.md](../start/DEFAULTS.md) LAN vs public before exposing the box.

## Admin tab (infrastructure)

**`/api/admin`** — operator setup without editing files by hand:

| Section | What you configure |
|---------|-------------------|
| **Dashboard access** | Port, bind, localhost-only, enable/disable, **rotate token** |
| **External access** | Internet expose, domain, public Java/Bedrock/pack ports, DNS SRV hint |
| **nginx** | Localhost assist, stream/HTTP ports, domain, config dry-run |
| **Proxy / authority** | Velocity forwarding, Link embed mode, link home path |

POST actions: `save-access`, `save-nginx`, `save-dashboard`, `save-proxy`, `rotate-token`, `nginx-dry-run`, `run-smoke`, `crashdump`.

## All tabs & API routes

| Tab | API | GET snapshot | POST actions (high level) |
|-----|-----|--------------|---------------------------|
| **Dashboard** | `/api/status` | running, heap, ticks, link process, network health | — |
| **Connect** | `/api/connect` | Java/Bedrock/crossplay join, pack URL | — |
| **Console** | `/api/console`, `/api/command` | log backlog | run command · SSE `/api/console/stream` |
| **Server setup** | `/api/config` | everyday server switches (name, who can join, RAM) | save config keys |
| **YaP Link** | `/api/link`, `/api/link/console` | proxy, backends, forced hosts, selector | start, stop, save-proxy, save-servers, command · Link SSE |
| **Players** | `/api/players` | online list, spawn, moderation flags | kick, ban, tempban, ipban, mute, warn, timeout, tp*, set-rank, promote, demote, **eco give/take/set/reset**, bal, history, check, banlist |
| **Access & ranks** | `/api/access` | ops, auto-op, default group, groups, **tracks**, server-context, **permission catalog**, **group nodes** | save-group-nodes, save-ops, save-auto-op, set-default-group, op, deop, set-group, promote/demote (+ track), **user-perm / group-perm** (+ duration, world, server), **user-perm-unset / group-perm-unset**, dump, reload, applypack |
| **Rank pack** | `/api/ranks` | pack applied, auto-apply | apply, force, reset-marker, status |
| **Plugins** | `/api/plugins` | jar list, tier, soft/hard state, compat matrix | **install**, soft/hard **enable/disable**, **uninstall** (CORE needs `force`) |
| **Plugin settings** | `/api/plugin-config` | first-party YAML with plain-language titles + Yes/No | **save** + reload, **reload** |
| **Modules** | `/api/modules` | module jars | install, remove |
| **Packs** | `/api/packs` | resource packs, active set | setActive, add, remove, clear |
| **World** | `/api/world` | schematics, brush max, load/unload flags | create (type/env/seed/generator), load, unload, reload, schem-list, **save-brush** |
| **Regions** | `/api/regions` | region table (JSON), flag names | **define** (cuboid coords), **flag-set**, list |
| **NPCs** | `/api/npcs` | npc table, quest ids | create, remove, setquest, setdialogue, setwarp, setspawn, setcommand, setplayer, shopenable, shopclear, setaction (advanced), respawn, reload, info |
| **Holograms** | `/api/holo` | hologram table, entity type, enabled | create, delete, move, setlines (pages via blank line), attach, click, see, view, reload, list |
| **Shops** | `/api/shops` | NPC catalogs + chest shops (sub-tabs) | NPC: list, setitem, presets. Chest: `chest-list`, `chest-create`, `chest-set`, `chest-remove`, `chest-info` (world/x/y/z/material/amount/price/owner, `instance` for fleet) |
| **Essentials** | `/api/essentials` | features, MOTD, rules, spawn | reload, broadcast, save-motd, save-rules, set-feature |
| **Pregen** | `/api/pregen` | job status | start, pause, resume, cancel |
| **Player data** | `/api/playerdata` | economy, auth, feature toggles | reload, save, set-feature |
| **Kits** | `/api/kits` | kits.yml definitions, items, armor slots | **save-kit**, **delete-kit**, **clone-kit**, give, grant, reload |
| **Database** | `/api/database` | YaPDB engine + Docker + JDBC | **ensure**, **start-docker**, **stop-docker**, **sync-fleet** |
| **Setup** | `/api/setup` | first-boot checklist (EULA, seed, Tebex/Grim fetch, production, nginx dry-run, Folia build) | **accept-eula**, **seed-defaults**, **link-forwarding**, **fetch-tebex**, **fetch-grim**, **enable-grim**, **production-profile**, **nginx-dry-run**, **build-folia** |
| **Custom commands** | `/api/commands` | YaPCommands commands.yml | **save-command**, **delete-command**, **clone-command**, set-require-use, reload |
| **Tebex store** | `/api/tebex` | jar present, Hub-only placement, secret masked, buy/GUI settings, store status, package recipes YAML, pending/stuck kit grants | **set-secret**, **save-settings**, **save-recipes** / upsert/delete, **cancel-grant**, reload, info, forcecheck |
| **Chat** | `/api/chat` | channels, slow mode, filter, relay | reload, clearchat, **save-settings** |
| **Tab list** | `/api/tab` | header/footer/sidebar/bossbar | save-header/footer/sidebar/settings/bossbar, reload |
| **Map** | `/api/map` | map URL, tiles, worlds, render interval | reload, render, **save-settings** |
| **Guard** | `/api/guard` | check toggles, kick threshold, decay, alerts | reload, player-status, **save-settings** |
| **Protect** | `/api/protect` | logging, retention, status | reload, prune, lookup, lookup-radius, **rollback**, **restore**, **save-settings** |
| **Stacker** | `/api/stacker` | enabled, mob/item/spawner toggles, kill mode, live stats | **save-settings**, reload, status, stats |
| **Skills** | `/api/skills` | jar presence, enable flag, thin skill packs, online sample | reload |
| **Factions** | `/api/factions` | counts, preview, power/bank settings | **save-settings**, reload, setpower, setjoin, disband |
| **Disasters** | `/api/disasters` | extremes, random schedule, volcano sites | **save-settings**, reload, start, stop, random, site-* |

Legacy routes (superseded by UI tabs): `/api/moderation` → **Players**; `/api/perms` → **Access & ranks**.

Pack HTTP stays on **:8081**. Dashboard is a separate port (**:8080**).

### Setup checklist

**Configure → Setup** (`GET/POST /api/setup`) is the first-boot control surface for Linux and Windows:

- Accept EULA, seed defaults, Link forwarding, fetch Tebex / Grim, enable Grim, production profile, nginx dry-run, build YaP-Folia
- Shows OS + bash/PowerShell availability and copy-paste Linux / Windows commands
- Database + fleet bootstrap stay on **Fleet**; full nginx install stays on Network setup / Swing **nginx**

Swing Control Panel: **Setup** button on the fleet rail (and legacy **Setup** tab).


- **Minecraft OPs** — chip list, add/remove, persisted to `server.properties`
- **Auto-op** — toggle for first join
- **Default rank** — YaPPerms default group dropdown
- **Group cards** — click to edit tag, name color, and chat color with **color swatches + custom hex** (no `&` codes required), plus suffix, weight, and inheritance. Live chat preview updates as you pick colors.
- **Rank permissions** — catalog of player / staff / vanilla / Paper commands plus **nodes discovered from installed plugin.yml**; inherit · allow · deny; any custom / wildcard node (bulk add); saved with `save-group-nodes` (permanent + global via `yapperm editor-apply`)
- **Create rank with a pack** — empty / player / staff / admin, or copy perms from an existing rank (`template`, `cloneFrom` on `create-group`)
- **Apply pack / copy perms** onto an existing rank (`apply-template`, `clone-group`)
- **Promotion track** — ladder chips + track picker; **Promote / demote** steps one rank (`promote` / `demote` with optional `track`)
- **Context + temp grants** — Players pane grants/revokes a node with optional `duration` (`1h`, `1d`, `7d`, `30d`, or custom like `1d12h`), `world`, and `server` (wires to `yapperm user|group permission set|unset`)

`POST /api/access` `{"action":"save-group-nodes","group":"vip","allow":"yapessentials.fly,…","deny":"minecraft.command.op","unset":"yapessentials.god"}` writes `plugins/YaPPerms/config.yml` (`starter-grants` + `editor-nodes`) and applies live via `yapperm editor-apply`. Refresh dumps live extras with `yapperm dump` → `editor-snapshot.yml`.

Timed / world-scoped example (does **not** go through the rank editor batch):

```json
{"action":"user-perm","player":"Steve","node":"yapessentials.fly","value":"true","duration":"7d","world":"world"}
{"action":"user-perm-unset","player":"Steve","node":"yapessentials.fly","world":"world"}
{"action":"group-perm","group":"vip","node":"yapmod.ban","value":"true","duration":"1d","server":"lobby"}
{"action":"promote","player":"Steve","track":"yap"}
```

YaPDB already stores `world`, `server_ctx`, and `expires_at` on user/group nodes; the dashboard now exposes those fields for ops.

### Kits (`yap-playerdata`)

**Gameplay → Kits** builds claim kits in `plugins/YaPPlayerData/kits.yml` (same file `/createkit` uses):

- Cooldown, max uses, economy cost, first-join, console commands (`{player}`)
- Item rows: **Material** (Bukkit id, name/lore/enchants) or **YaPItem** (custom catalog id such as `stormblade`)
- Armor / offhand slots; clone / delete; **Give now** (`kit give`) or **Grant** (`kit grant`)
- Saves YAML then runs `yapdata reload` (YAML is kept if Folia is down)
- With fleet enabled, kit saves also **push `kits.yml` to every local backend** and reload running instances (`yapdata reload` / `yapitems reload`) so definitions stay network-wide like YaPDB player state

`GET/POST /api/kits` — `save-kit`, `delete-kit`, `clone-kit`, `give`, `grant`, `reload`.  
GET includes `yapItemIds` from `plugins/YaPItems/items/**`.  
POST item lines: `MATERIAL|amount|slot|name|lore|enchants` or `YAP:<id>|amount|slot|||`.  
YAML uses `material:` or `yap-item:` (resolved by YaPItems on claim). Players still need `yapdata.kit.<id>` on Access & ranks.

Fleet: `POST /api/fleet` action `sync-shared-catalog` realigns catalog items/kits/QoL/YaPDB JDBC onto all local instances.

`/createkit` Bukkit stacks stay readable; saving from the dashboard writes material / `yap-item` form (NBT beyond name/lore/enchants is dropped — prefer YaPItem rows for custom gear).

### Custom commands (`yap-commands`)

**Gameplay → Custom commands** edits `plugins/YaPCommands/commands.yml`:

- Create / clone / delete `/name` entries
- Messages (`&` colors), player-run commands, console-run commands (`{player}`), broadcast
- Aliases, permission, cooldown, enable toggle, hide-no-permission
- Master `require-use-perm` toggle; flat `config.yml` `enabled` also under Plugin settings
- Saves YAML then runs `yapcommands reload`

`GET/POST /api/commands` — `save-command`, `delete-command`, `clone-command`, `set-require-use`, `reload`.

### Tebex store

**Gameplay → Tebex store** wires the GPLv3 Folia plugin (`plugins/tebex.jar`, **Hub / lobby only**):

- Status: jar present, Hub-only placement, secret configured (masked), `/buy`, store name/currency, pending/stuck kit grants
- **Save secret** → writes `plugins/Tebex/config.yml` and runs `tebex secret <key>`
- Buy / proxy / verbose / update checks / auto-report / GUI home title+rows → `tebex reload`
- Editable package recipes (`config/tebex-recipes.yml`) with copy cards for creator.tebex.io
- Structured **Store info** / **Force check**; kit grant cancel for undelivered rows
- Links to [creator.tebex.io](https://creator.tebex.io/) and Tebex Minecraft docs

`GET/POST /api/tebex` — `set-secret`, `save-settings`, `save-recipes`, `upsert-recipe`, `delete-recipe`,
`cancel-grant`, `reload`, `info`, `forcecheck`. Full guide: [TEBEX.md](TEBEX.md) → [INTEGRATIONS.md](INTEGRATIONS.md#tebex).

### Players

Staff moderation panel: **online** table plus **everyone who has ever joined** (username, nickname, UUID, last IP, all known IPs, first/last seen). Click a row to kick/ban/mute/IP-ban. IP bans use the stored last IP after they leave.

**Economy** on the same panel: amount field + **Give money** / **Take money** / **Set balance** / **Reset** / **Check balance** (`eco …` / `bal` via console).

Requires YaP-Folia running + `yap-moderation` / `yap-perms` / `yap-playerdata`. Snapshot is refreshed with `yapmod seen snapshot` when you hit Refresh.

### Skills (`yap-skills`)

**Gameplay → Skills** — thin progression (mining / woodcutting / strength / marathon / builder / herbalism / excavation / alchemy / health). See [PLUGINS.md](../plugins/PLUGINS.md).

### Dungeons (`yap-dungeons`)

Instanced procedural dungeons (opt-in GAMEPLAY). See [PLUGINS.md](../plugins/PLUGINS.md). Commands: `/dungeon`, `/yapdungeons`.

`GET/POST /api/skills` — jar presence, `enabled`, skill packs, online sample; reload via `yskills reload`.

### Factions (`yap-factions`)

**Gameplay → Factions** — counts + preview, YAML settings (power/bank/relations), admin console actions:

| Action | POST `/api/factions` |
|--------|----------------------|
| Save settings + reload | `{"action":"save-settings",…}` |
| Reload | `{"action":"reload"}` |
| Set power | `{"action":"setpower","faction":"Tag","power":"50","max":"60"}` |
| Set join mode | `{"action":"setjoin","faction":"Tag","mode":"invite"}` |
| Force disband | `{"action":"disband","faction":"Tag"}` |

Player create/join/claim stays in-game (`/f …`).

### Disasters (`yap-disasters`)

**Gameplay → Disasters** — extremes, random schedule, volcano sites. See `/api/disasters` POST actions in the table above.

### Stacker (`yap-stacker`)

**Gameplay → Stacker** — enable toggles, kill mode, max stacks, live `/yapstacker status|stats`.

| Action | POST `/api/stacker` |
|--------|---------------------|
| Save YAML + reload | `{"action":"save-settings",…}` |
| Reload | `{"action":"reload"}` |
| Stats | `{"action":"stats"}` |

### YAML-only by design (not dashboard gaps)

These ship as **Plugin settings** editors (or in-game hubs) on purpose — they do not need a dedicated interactive ops tab:

| Plugin / area | Why YAML / elsewhere |
|---------------|----------------------|
| LagGuard | Already on status metrics |
| YaPAdmin | In-game staff hub (`/yapadmin`) |
| gameplay-knobs | Tunables via Plugin settings |
| YaPEssentials | `block-reach` (place/break distance) under Plugin settings |
| YaPWorld | Creative climate (always day, no weather, no mobs) under Plugin settings |
| floodgate / bedrock-ui / folia-bridge | Crossplay bridge config |
| placeholderapi / plugin-compat | Expansion / soft-dep config |

### NPCs (`yap-npcs`)

Dashboard drives the plugin directly:

- Create NPC at world coordinates (console: `npc create <id> at <world> <x> <y> <z> [yaw] [name]`)
- Edit display name, quest, dialogue; structured hub actions (spawn / warp / console / player command); shop enable/clear; remove; respawn all; reload
- Hub actions owned by `/npc`: `/npc shop enable|apply|setitem|list`, `/npc setwarp`, `/npc setspawn`, `/npc setcommand`, `/npc setname`, `/npc move`
- Server spawn = Essentials `/spawn` via NPC `spawn` action — **not** `warp:spawn`
- Shop catalogs live in YaPPlayerData; there is no separate `/trader` command
- Raw `setaction` is advanced-only in the UI
- GET `/api/npcs` returns structured `npcs[]` from `npc list json`

### Shops (`/api/shops`)

One **Shops** sidebar item with two sub-tabs:

**NPC shops** — catalogs on NPCs, without splitting buy/sell into separate rows:

- Lists NPCs that already have a `shop:<id>` action (enable a shop on the **NPCs** tab first)
- **One row per item** — Buy $ (player pays) and Sell $ (player receives); blank disables that side
- Apply built-in presets (replace), clear offers, or unlink the catalog
- POST `setitem` → `npc shop setitem <npc> <material> <amount> <buy|-> <sell|-> [stock]`
- In-game: left-click buy / right-click sell + quantity totals — see [PLAYERDATA.md](../data/PLAYERDATA.md)

**Chest shops** — look-at-chest `/shop` registrations (stock in the chest, left-click to buy):

- POST `chest-list` / `chest-create` / `chest-set` / `chest-remove` / `chest-info`
- Fields: `instance` (fleet backend), world, x/y/z, material, amount, price, owner
- Place the chest in the world first, then create from this tab

### Regions (`yap-regions`)

- Define / redefine / remove admin cuboids from the UI
- Set WorldGuard-class flags (including item-drop/pickup, tnt, creeper-explosion, mob-entry, weather)
- `/region worldborder <name>` (also via console from dashboard) fits the vanilla world border to the region XZ AABB
- GET `/api/regions` returns `regions[]` with flag map from `region list json`

### Portals (`yap-portals`)

CORE+NETWORK default, **on**. Walk-through colored volumes (particles + light, not glass) send players to another fleet server via YaP Link `Connect`. Create pads in-game with `/portal wand` then `/portal create <name> <server> [color]`. Reload fill only replaces air / portal / glass. See [PORTALS.md](../network/PORTALS.md).

### Chat, Guard, Protect, Map, World

These tabs **write plugin YAML** via `save-settings` (or equivalent) and reload the plugin — not just run one-off commands.

### Network health (Dashboard tab)

`/api/status` includes `networkHealth`:

- Folia running, bedrock/crossplay/velocity flags
- Link process running (`linkProcessRunning`), config + suite completeness
- Plugin count + compat warning count
- **Ops plugins** — Phase 8 jar readiness (Protect, Chat, Moderation, Player data, Map, Discord, Tebex) with one-line detail per plugin

### Map tab

Serves tiles via YaPcore pack HTTP when `use-yapcore-server: true` (default). First render runs ~2s after plugin enable; full re-render on `render-interval-minutes`. Tune `sample-chunk-radius` and `max-height` on low-CPU hosts — see plugin `config.yml` comments.

**Wave 4 markers** — live player markers via `/map/markers.json` (poll interval configurable). Optional NPC points and region outlines when YaPNpcs / YaPRegions are installed and toggled on in the Map tab. Flat Leaflet and optional **3D** voxel mesh (`?view=3d`) share the same markers feed; see [PLUGINS.md](PLUGINS.md).

### Discord tab

Webhooks (moderation, chat, events), relay toggles, and join/leave/death/advancement event toggles. Safe setup order documented in [INTEGRATIONS.md](INTEGRATIONS.md). MC→Discord and Discord→MC stay **off** until webhooks and inbound secrets are set.

### YaP Link tab (proxy process)

Separate from the main **Console** tab (YaPcore/Folia). Requires `link-embed=false`.

**Settings UI** — configure the full proxy without editing files by hand:

- **Proxy settings** — bind, MOTD, max players, online mode, public host/port, chat relay, bedrock block
- **Backends** — add/remove servers (`hub`, `survival`, …), host:port, optional per-backend Bedrock address
- **Try order** — fallback order when a backend is down (e.g. `hub, survival`)
- **Forced hosts** — route by hostname (e.g. `hub.yourdomain.com` → `hub`)
- **Hub / server selector** — `yaplink-server-selector` hub + session lock

Saves write `link-data/link.properties`. If Link is running, the dashboard sends `reload` automatically.

| Action | POST `/api/link` body |
|--------|------------------------|
| Start Link | `{"action":"start"}` |
| Stop Link | `{"action":"stop"}` |
| Run command | `{"action":"command","command":"reload"}` |
| Enable backend forwarding | `{"action":"enable-backend-forwarding"}` |
| Save proxy settings | `{"action":"save-proxy",…}` |
| Save backends | `{"action":"save-servers","servers":[…],"try":[…],"forcedHosts":[…]}` |
| Save selector | `{"action":"save-selector","hubServer":"hub","sessionLock":"true"}` |

Live log: **GET** `/api/link/console` · **SSE** `/api/link/console/stream?token=…`

### Plugin manager (Plugins tab)

Each jar shows status from [PLUGIN_COMPAT.md](../plugins/PLUGIN_COMPAT.md):
`native`, `works`, `broken`, `folia-build`, or `unknown`, plus native alternative hint.

**Soft vs hard**

| Action | What it does | When it applies |
|--------|--------------|-----------------|
| **Soft on/off** | Writes `enabled` (or knobs `settings.enabled`) in the plugin data folder | Immediately after that plugin’s reload command when one exists; otherwise restart Folia |
| **Hard on/off** | Renames `foo.jar` ↔ `foo.jar.disabled` | **Next Folia start** (jar absent/present on the classpath). No live Folia unload. |
| **Install** | Copies a `.jar` under `YAPCORE_HOME` into `plugins/` | Next Folia start |
| **Uninstall** | Deletes the jar (or `.jar.disabled`) | Next Folia start to unload |

CORE jars (`yap-db`, `yap-folia-bridge`, perms, playerdata, essentials, chat, moderation, protect, admin) require an explicit **force** confirm to soft-disable, hard-disable, or uninstall.

API shape (flat JSON body):

```json
{"action":"enable","fileName":"yap-skills-0.0.0.1.jar","mode":"soft"}
{"action":"disable","fileName":"yap-stacker-0.0.0.1.jar","mode":"hard","force":"false"}
{"action":"install","path":"releases/yap-skills.jar"}
```

`DELETE /api/plugins` with `{"fileName":"…","force":"true"}` for uninstall. In-game mirror: `/yapplugins` — [COMMANDS.md](COMMANDS.md).

### Ranks via console / tab

```bash
gradle installProductDefaults
# after Folia/Paper is up:
ranks apply
/yapperm user Steve parent set vip
/promote Steve
```

Dashboard **Access & ranks** and **Rank pack** tabs call `/api/access` and `/api/ranks`. Reference:
[`examples/yapperms/ranks-reference.txt`](../examples/yapperms/ranks-reference.txt).
Config: `plugins/YaPPerms/config.yml`. See [PERMISSIONS.md](PERMISSIONS.md).

Optional: `yap-ranks-auto-apply=true` in `config/server.properties`.

## Security

- Treat the token like a password.
- Prefer `web-dashboard-localhost-only=true` or bind to a private IP, then put
  nginx + TLS in front for public access.
- Do not expose `:8080` to the internet without auth + TLS.
- Use **Network setup → Rotate token** if the secret may have leaked.


---

## Admin menu

Chest GUI hub for on-server staff, plus optional Fabric **yap-staff** client GUI (branded **YaP Staff** hub with sectioned tools).
Complements the [web dashboard](WEB_DASHBOARD.md) and desktop Control Panel.

Menus and chat shortcuts follow the **Staff / menu contracts** in [COMMANDS.md](COMMANDS.md) so argument order stays consistent.

## Install

Built as `yap-admin.jar` (CORE + NETWORK product default).

```bash
gradle :admin-plugin:installIntoPlugins
```

Soft-depends on YaPEssentials, YaPModeration, YaPPerms, YaPWorld, YaPStacker, YaP-QoL, YaPPlayerData, YaPSkills — tiles hide when a plugin is missing.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/yapadmin` `/staff` `/adminmenu` `/am` | `yapadmin.menu` | Open the hub chest GUI |
| `/yapadmin reload` | `yapadmin.server` | Reload `plugins/YaPAdmin/config.yml` |
| `/yapadmin give <mat> [amt] [player]` | `yapadmin.give` | Give items (client give browser) |
| `/yapadmin spawnmob <type> [amt] [player]` | `yapadmin.spawnmob` | Spawn mobs at you or on a player (`mob` / `summon` aliases) |
| `/yapadmin troll <type> <player>` | `yapadmin.troll` | Smite, launch, burn, rocket, squash, blind, confuse, slap, drop |
| `/yapadmin tp` / `tphere` / `tpspawn` | `yapessentials.teleport` | Teleports |
| `/yapadmin heal` / `feed` / `nv` / `clear` | menu | Self/target tools |
| `/yapadmin kick` / `warn` / `mute` / `tempban` | `yapmod.*` | Moderation shortcuts |
| `/yapadmin money <amt> [player]` | `yapadmin.economy` | Economy grant |
| `/yapadmin broadcast <msg>` | `yapadmin.server` | Broadcast |
| `/yapadmin chest` | `yapadmin.menu` | Open chest hub explicitly |

Also opens from:

- **`/menu` → Staff** (JE chest / Bedrock form) when the player has `yapadmin.menu` and YaPAdmin is loaded
- **Esc pause / keybind R → full Staff GUI** with the optional Fabric **yap-staff** client mod

## Hub sections (chest + client)

- **Players** — online picker → TP to/here/spawn, freeze, invsee/echest, heal/feed/**god**/clear, **walk/fly speed 1–10**, promote/demote, kick/warn/mute 1h/tempban 1d, trolls, check/history, jump to Give / money / ranks
- **Self tools** — fly, god, vanish, heal, feed, night vision, gamemodes, repair, **walk/fly speed 1–10**
- **Give** — curated presets, **armor/weapon/tool gear kits**, player kits (`/kit give`), paginated / searchable material browser (amount 1/16/64)
- **Custom items** — YaPItems create / browse / give / edit / delete / ability cooldowns ([YAPITEMS.md](../plugins/YAPITEMS.md))
- **Spawn mobs** (client) — searchable entity browser + presets; spawn at you or a selected player (`/yapadmin spawnmob`)
- **World tools** (chest) — World edit, schematics browse, browser studio, paste preview (confirm / move / cancel / undo). One hub tile; also under More….
- **World edit** (client) — YaPWorld wand/pos, clipboard, fill/set, schematics (preview confirm/cancel/here/undo), brush, worlds (`/yapworld …`)
- **Trolls** — smite, launch, burn, rocket, squash, blind, confuse, slap, drop hand (`yapadmin.troll`)
- **Moderation** — same player picker (actions gated by `yapmod.*`)
- **Server** — broadcast presets, status, weather/disasters, reloads
- **Economy** — money grants via YaPPlayerData deposit (`/yapadmin money`; Folia entity-thread safe)
- **Ranks & perms** (client) — searchable YaPPerms editor (primary/parents, node allow/deny/unset, tracks); chest still deep-links `/yapperm gui`
- **Deep links / More…** — ranks, **World tools** (same panel as hub), pregen, stacker, **QoL tools** (timber/excavator toggle + give), menu, skills, …

## Permissions

| Node | Default | Notes |
|------|---------|-------|
| `yapadmin.menu` | op | Open hub |
| `yapadmin.give` | op | Presets / materials / kits |
| `yapadmin.spawnmob` | op | Spawn entities at self or another player |
| `yapadmin.server` | op | Broadcast + reload |
| `yapadmin.economy` | op | Money grants |
| `yapadmin.troll` | op | Staff trolls |
| `yapadmin.plugins` | op | `/yapplugins` |

Per-action nodes from other plugins still apply (`yapessentials.teleport`, `yapmod.kick`, `yapdata.kit.give`, `yapperm.admin`, …). Grant `yapadmin.menu` (and give/server/troll as needed) on `mod` / `admin` ranks. Owner has `yapadmin.*`. **OP** also unlocks all `default: op` nodes even if YaPPerms primary is `default`.

## Config

`plugins/YaPAdmin/config.yml` — kit ids, money amounts, broadcast presets, curated item presets.

## Folia

Teleports and inventory mutations use `YapSched.entity`. Moderation DB calls go through `ModerationService` then hop back to the entity thread for feedback. **Economy** deposits via `PlayerDataService` on the target’s region thread (do not dispatch `/eco` on the global scheduler).

## Client mod

See [`client/yap-staff/README.md`](../../client/yap-staff/README.md) — native Screens for every hub section (sectioned layout, searchable player select, ranks, spawn mobs, world edit, **custom items**). Jar **1.0.25+**.
