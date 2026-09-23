# How to use YaPClaims

**Jar:** `yap-claims.jar`  
Player land claims in shared SQL. Most players use **`/claim`** from YaPEssentials; this jar owns the claim data plane.

## What it does

- Lets players protect land from grief
- Stores claims in YaPDB (network-aware with unique `server-id`)
- Optional tax via YaPPlayerData economy

## Prerequisites

- `yap-claims.jar` + `yap-playerdata.jar` + YaPDB
- `features.claims: true` in PlayerData
- Economy on if claim tax is enabled
- Correct `server-id` on each Folia instance

## Player workflow

```text
/claim              # menu
/claim claim        # claim the plot you stand in
/claim expand       # claim the next plot in look direction (must touch your land)
/claim expand north # or south / east / west
```

Typical flow:

1. Stand in the area you want and run `/claim claim` (or shovel-click)
2. Face empty land next to your claim and run `/claim expand`
3. Trust friends with `/claim trust <player>`
4. Nether, End, YaP End doors, and dungeon portals in claims are private by default — trusted players only (`/claim flag set nether-portal allow` to open them)
5. By default, mobs cannot power pressure plates (iron doors stay shut). Set `claims.mobs-activate-pressure-plates: true` for vanilla.

Staff inspecting grief should also use [yapprotect.md](yapprotect.md).

## Ops checklist

1. Confirm claims enabled in PlayerData config  
2. Set claim limits / prices in claims + PlayerData configs  
3. Ensure survival (not lobby) is where claims matter  
4. After DB changes, restart or reload as documented  

## Troubleshoot

| Symptom | Fix |
|---------|-----|
| `/claim` unknown | Essentials/PlayerData claims feature off |
| Claims not saving | YaPDB down; check `server-id` |
| Tax errors | Economy disabled |
| Overlap fights | Check priority vs YaPRegions admin regions |

## Related

- [PLAYERDATA.md](../../data/PLAYERDATA.md) · [GAMEPLAY.md](../../gameplay/GAMEPLAY.md)
- [yapessentials.md](yapessentials.md) · [yapregions.md](yapregions.md) · [yapfactions.md](yapfactions.md)
