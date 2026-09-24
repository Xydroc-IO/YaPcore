# How to use YaPNpcs

**Jar:** `yap-npcs.jar`  
Hub NPCs with dialogue, quests, warps, server transfers, player/console commands, and material shops.

## What it does

Place humanoid NPCs players right-click. Each NPC has an **id** (stable) and a **display name** (nametag). Actions open shops, run commands, Connect to fleet servers, or turn in quests.

## Quick start — welcome NPC

```text
/npc create guide Welcome Guide
/npc setdialogue guide &aWelcome to the hub! &7Use the portals behind me.
/npc setserver guide survival
```

Or create at coords:

```text
/npc create guide at world 0 64 0 180 Welcome Guide
```

## Lifecycle commands

| Command | Purpose |
|---------|---------|
| `/npc create <id> [name]` | At your feet |
| `/npc create <id> at <world> <x> <y> <z> [yaw] [name]` | Exact place |
| `/npc remove <id>` | Delete |
| `/npc list` | All NPCs |
| `/npc info <id>` | Actions, shop, skin |
| `/npc move <id>` | To you |
| `/npc move <id> at <world> <x> <y> <z> [yaw]` | Coords |
| `/npc setname <id> <name…>` | Nametag only (id unchanged) |
| `/npc respawn` | Respawn all |
| `/npc reload` | Reload from DB |

## Actions (right-click)

| Helper | Effect | Example |
|--------|--------|---------|
| `/npc setplayer <id> <cmd…>` | Player runs command | `/npc setplayer dealer yap420 sell` |
| `/npc setcommand <id> <cmd…>` | Console-style (`{player}` ok) | `/npc setcommand reward eco give {player} 10` |
| `/npc setwarp <id> <warp>` | Warp | `/npc setwarp miner mines` |
| `/npc setspawn <id>` | This server spawn | |
| `/npc setserver <id> <serverId>` | Fleet Connect | `/npc setserver guide survival` |
| `/npc setaction <id> …` | Raw (`player:` `command:` `shop:` `server:`) | |
| `/npc setdialogue <id> <text>` | Chat line on click | |
| `/npc setquest <id> <questId>` | Quest turn-in | |
| `/npc setskin <id> <url\|clear>` | Skin | |
| `/npc setskinslim <id> true\|false` | Slim arms | |

**Blazed Boutique (YaP420):** use `player:yap420 sell` — do not rebuild the cannabis market with `/npc shop`. Theme example (Bob Marley–style herbalist skin + nametag): [`examples/yap-npcs/blazed-boutique.txt`](../../../examples/yap-npcs/blazed-boutique.txt).

# Kits Shop:** opens the shared kits GUI (tier armor/weapon sets + rank kits). Locked rank kits still appear; claim/buy needs the matching rank. Example: [`examples/yap-npcs/kits-shop.txt`](../../../examples/yap-npcs/kits-shop.txt).

```text
/npc create kits_shop Kits Shop
/npc setplayer kits_shop kits
```

**Auction House:** opens `/ah` with browse (buy), sell (price picker), and my-listings (cancel). Example: [`examples/yap-npcs/auction-house.txt`](../../../examples/yap-npcs/auction-house.txt).

```text
/npc create auction_house Auction House
/npc setplayer auction_house ah
```

**Mail:** opens `/mail` desk — inbox (read / reply), send (online pick or type name), clear, refresh. Example: [`examples/yap-npcs/mail-clerk.txt`](../../../examples/yap-npcs/mail-clerk.txt).

```text
/npc create mail_clerk Mail
/npc setplayer mail_clerk mail
```

Feature GUIs (kits / AH / mail / …) use **Close** only — they never open YaP Menu. Use `/menu` for the hub.

## NPC shops (vanilla materials)

Needs YaPPlayerData `features.traders: true`.

```text
/npc shop enable blacksmith
/npc shop presets
/npc shop apply weapons blacksmith --replace
/npc shop setitem blacksmith IRON_SWORD 1 50 15
/npc shop addbuy blacksmith DIAMOND 1 100
/npc shop addsell blacksmith COBBLESTONE 64 2
/npc shop list blacksmith
/npc shop clearoffers blacksmith
/npc shop clear blacksmith
```

Presets: `weapons`, `armor`, `tools`, `food`, `blocks`, `redstone`, `crafting`, `enchants`, …  
Dashboard **Shops** tab edits the same catalogs.

## Troubleshoot

| Symptom | Fix |
|---------|-----|
| Click does nothing | `/npc info <id>` — set an action |
| Shop empty | `/npc shop enable` + apply preset; traders feature on |
| `/yap420 sell` unknown | Install `yap-420.jar` on **this** server |
| Wrong server transfer | `setserver` id must match Link `servers.*` |

## Related

- [yap420.md](yap420.md) · [yapportals.md](yapportals.md) · [yapplayerdata.md](yapplayerdata.md)
- Examples: `examples/yap-npcs/compendium-npc-bindings.txt`
- [PLAYERDATA.md](../../data/PLAYERDATA.md)
