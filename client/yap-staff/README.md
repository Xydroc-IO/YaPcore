# yap-staff

Fabric **client** mod for Minecraft **26.2** (version **1.0.4**). Full staff admin GUI — not a one-button shim.

Opens with **R** (Controls → Miscellaneous → Open YaP Staff) or **Esc → Staff menu**.

Menus **scroll** and **scale** to the game window (1–3 button columns). No need to resize Minecraft to fit.

## What it includes

| Section | Actions |
|---------|---------|
| **Players** | Searchable / letter-jump / paginated online list → manage one player |
| **Self tools** | God, fly, vanish, heal, feed, NV, gamemodes, repair, speed, workbench, echest |
| **Give / spawn** | Curated presets, kits, searchable **all-items** browser, amount 1/16/64, target player |
| **Teleport** | TP to / here / spawn, back, spawn, near, world GUI |
| **Trolls** | Smite, launch, burn, rocket, squash, blind, confuse, slap, drop hand, freeze |
| **Moderation** | Kick, warn, mute, tempban, unmute/unban, banlist, check, history, alts, protect |
| **Server** | Broadcasts, weather, disasters, staff/admin chat, reloads, plugins list, guard, knobs |
| **Economy** | Select player (searchable picker) → grant amounts (`yapadmin.economy`) |
| **Ranks & perms** | Set primary / parents, promote/demote, searchable allow/deny/unset catalog (user + group), create/manage ranks, tracks, reload/applypack |
| **Deep links** | World, stacker, menu, skills, dungeons, optional server ranks chest GUI |
| **Server chest** | Optional Folia `/yapadmin` chest GUI |

**Player targeting:** tools use **Select player…** → searchable picker → returns to the same tool (does not dump you into the full per-player menu). Hub → Players still opens the full manage menu.

The **server** still enforces every permission (`yapadmin.*`, `yapessentials.*`, `yapmod.*`, `yapperm.*`, …). Install/update **yap-admin.jar** with troll + give + Folia-safe money deposit. Ranks UI needs **yapperm.admin** (promote/demote need `yapperm.promote` / `yapperm.demote`).

## Install

1. Fabric Loader **0.19.3+** for Minecraft **26.2**
2. Drop `yap-staff-1.0.4.jar` into `.minecraft/mods/` (only one `yap-staff-*.jar`)
3. Join YaPcore / Folia with YaPAdmin loaded

Also shipped in `client_mods.zip` from `./scripts/build-yap-client-render.sh`.

## Build

```bash
cd client/yap-staff
./gradlew build
```

Jar: `build/libs/yap-staff-1.0.4.jar`

## Config

`.minecraft/config/yap-staff.json`

| Key | Default | Meaning |
|-----|---------|---------|
| `enabled` | `true` | Master switch |
| `pauseButton` | `true` | Esc pause → Staff menu |
| `keybind` | `true` | R key opens hub |
