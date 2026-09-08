# yap-staff

Fabric **client** mod for Minecraft **26.2** (version **1.0.24**). Branded **YaP Staff** admin GUI with sectioned tools.

Opens with **R** (Controls → Miscellaneous → Open YaP Staff) or **Esc → Staff menu**.

Menus **scroll** and **scale** to the game window (1–3 button columns). Option clicks **keep scroll position**.

## What it includes

| Section | Actions |
|---------|---------|
| **Players** | Searchable online list → manage one player |
| **Self tools** | God, fly, vanish, heal, feed, NV, gamemodes, repair, speed, workbench, echest |
| **Give** | Presets, kits, searchable item browser, amount 1/16/64 |
| **Custom items** | YaPItems browse / give / create / edit / delete / ability cooldowns |
| **Spawn mobs** | Entity browser + presets; spawn at you or a player |
| **World edit** | YaPWorld selection, clipboard, fill, schematics, brush, worlds |
| **Teleport** | TP to / here / spawn, back, near, world tools |
| **Trolls** | Smite, launch, burn, rocket, squash, blind, confuse, slap, drop, freeze |
| **Moderation** | Kick, warn, mute, tempban, unmute/unban, check, history, alts, protect |
| **Server** | Broadcasts, weather, disasters, staff chat, reloads |
| **Economy** | Grant money to a selected player |
| **Ranks & perms** | Ranks editor, permission nodes, tracks |
| **More…** | Stacker, regions, map, chest GUIs |
| **Chest menu** | Folia `/yapadmin` chest hub |

### Custom items create (1.0.24+)

- Concrete bases by category (weapons, tools, gems, props)
- Abilities filtered to the base group, listed by category (Combat / Movement / Self / Gathering / Utility)
- Per-ability keybinds; break reach / blast / max blocks; self vs enemy potion options
- **Glow** and **Unbreakable** toggles
- Create uses compact `--abilities` + `--nameb64` over an unsigned command packet (avoids chat_command decode kicks)

Requires server **yap-items.jar** + **yap-admin.jar**. See [YAPITEMS.md](../../docs/plugins/YAPITEMS.md).

**Player targeting:** **Select player…** returns to the calling tool. Hub → Players opens the full manage menu.

The **server** enforces permissions. Command shapes follow [Staff / menu contracts](../../docs/ops/COMMANDS.md).

## Install

1. Fabric Loader **0.19.5+** for Minecraft **26.2**
2. Drop `yap-staff-1.0.24.jar` into `.minecraft/mods/` (only one `yap-staff-*.jar`)
3. Join YaPcore / Folia with YaPAdmin + YaPItems loaded

Also shipped in `client_mods.zip` from `./scripts/build-yap-client-render.sh`.

## Build

```bash
cd client/yap-staff
./gradlew build
```

Jar: `build/libs/yap-staff-1.0.24.jar`

## Config

`.minecraft/config/yap-staff.json`

| Key | Default | Meaning |
|-----|---------|---------|
| `enabled` | `true` | Master switch |
| `pauseButton` | `true` | Esc pause → Staff menu |
| `keybind` | `true` | R key opens hub |
