# How to use YaPEssentials

**Jar:** `yap-essentials.jar`  
Everyday player QoL (spawn, TPA, gamemode) plus the **command front-end** for PlayerData homes, warps, kits, economy, bag, shops, and claims.

## What it does

YaPEssentials is the “EssentialsX-shaped” command surface. Storage for homes/warps/kits/money/bag lives in **YaPPlayerData**; this jar registers the commands players type.

| Role | Examples |
|------|----------|
| Players | `/spawn` `/tpa` `/home` `/bal` `/kit` `/bag` `/rtp` |
| Staff | `/gm` `/fly` `/vanish` `/tp` `/eco` `/setspawn` `/setwarp` |
| Ops | Feature toggles in config, reach/water-waves, keep-inventory |

## Prerequisites

- `yap-essentials.jar` on each Folia backend
- For homes/warps/kits/money/bag/shop: `yap-playerdata.jar` + YaPDB + `economy.enabled` / `features.*` as needed
- Unique `server-id` per fleet instance (`lobby`, `survival`, …)

## Quick start — set server spawn

On the destination world (e.g. survival), stand where players should land:

```text
/setspawn
/spawn
```

Cross-server portals land here by default (see [yapportals.md](yapportals.md)).

Hub-style “always spawn on join”:

```yaml
# plugins/YaPEssentials/config.yml
spawn:
  teleport-on-join: true   # lobby often true; survival usually false
  scope: server            # per server-id (not global)
```

Then `/yapess reload`.

## Player workflows

### Teleport & spawn

| Goal | Commands |
|------|----------|
| Go to spawn | `/spawn` |
| Random wild | `/rtp` or `/wild` |
| Ask to visit | `/tpa Steve` → they `/tpaccept` or `/tpdeny` |
| Pull someone to you | `/tpahere Steve` |
| Undo last TP/death | `/back` |

### Homes

```text
/sethome
/sethome cabin
/home
/home cabin
/homes
/delhome cabin
```

Max homes: `plugins/YaPPlayerData/config.yml` → `homes.max` (often rank-gated via perms).

### Warps (player use / staff create)

```text
/warp mines
/warps
/setwarp mines     # staff
/delwarp mines     # staff
```

### Money

```text
/bal
/bal Steve
/pay Steve 50
/eco give Steve 1000    # staff
/eco take Steve 100
/eco set Steve 0
/eco reset Steve
```

Needs `economy.enabled: true` in YaPPlayerData.

### Kits

```text
/kits                 # GUI with cooldowns (shows locked rank kits too)
/kit starter
/createkit vip 3600   # staff — save inv+armor, 1h delay
/delkit vip
/showkit vip
kit grant Steve vip   # console/queue across fleet
kit give Steve vip    # this backend only, online
```

Premade defs in `kits.yml`: starter, adventurer, wood→netherite gear, enchanted_iron/diamond/netherite, and rank kits vip/mvp/elite.

- Rank kits: **1 free / 6h** (`delay-seconds: 21600`); `extra-cost` buys another while on cooldown — both need `yapdata.kit.<id>`.
- Cash gear kits (wood→netherite + enchanted_*): pay `cost` anytime (no delay).
- Kits NPC: `/npc setplayer kits_shop kits` — see `examples/yap-npcs/kits-shop.txt`.

After updating the jar, `/yapdata reload` (or restart) merges premade kits from the jar even if an old `kits.yml` only had starter/adventurer/vip.

### Bag & shops

```text
/bag
/bag 2
/bag see Steve 1      # staff
/shop create 25       # look at a chest
/ah
/jobs                 # if jobs feature on (often off when YaPSkills present)
/claim                # land claim UX
/menu                 # hub GUI
```

## Staff workflows

| Need | Command |
|------|---------|
| Gamemode | `/gms` `/gmc` `/gma` `/gmsp` or `/gm 1 Steve` |
| Fly / god / speed | `/fly` `/god` `/speed 5` `/speed fly 3` |
| Heal / feed / repair | `/heal` `/feed` `/repair all` |
| Vanish / invsee / echest | `/vanish` `/invsee Steve` `/echest` `/echest Steve` |
| Give items | `/i diamond 64` `/i diamond_sword Steve` |
| Freeze / inspect | `/freeze Steve` `/check Steve` |
| Broadcast | `/broadcast Maintenance in 5m` |
| Weather / time (client) | `/weather` `/ptime day` `/pweather clear` |

Reload: `/yapess reload`.

## Config map

| File | Knobs |
|------|--------|
| `plugins/YaPEssentials/config.yml` | `spawn.*`, `features.*`, RTP radii, water-waves, keep-inventory, `server-id` |
| `plugins/YaPPlayerData/config.yml` | `economy`, `features.homes/warps/kits/…`, `homes.max` |
| `plugins/YaPPlayerData/kits.yml` | Kit contents |

Spawn persists to YAML + optional DB table `yap_essentials_spawn` (scope = `server-id`).

## Troubleshoot

| Symptom | Fix |
|---------|-----|
| `/home` unknown | YaPPlayerData missing or `features.homes: false` |
| `/bal` does nothing useful | `economy.enabled: false` |
| Portal does not land at setspawn | Setspawn on **destination**; update portals jar; see [yapportals.md](yapportals.md) |
| RTP fails | Check `features.rtp`, world name list, claims blocking |
| Kit empty on other server | Sync `kits.yml` across fleet |

## Related

- [yapplayerdata.md](yapplayerdata.md) · [PLAYERDATA.md](../../data/PLAYERDATA.md)
- [yapmoderation.md](yapmoderation.md) · [yapchat.md](yapchat.md)
- [COMMANDS.md](../../ops/COMMANDS.md)
