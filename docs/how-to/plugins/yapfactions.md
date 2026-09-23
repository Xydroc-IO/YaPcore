# How to use YaPFactions

**Jar:** `yap-factions.jar`  
Guilds/factions overlay on the claims system. Optional **YaPConquest** hardcore chunk land.

## Status

Often **disabled by default**. Enable in `plugins/YaPFactions/config.yml` (and conquest config if used).
Set `server-id` to match YaPPlayerData on that backend (e.g. `survival`).

## Players

```text
/f
/guild
```

### Homes (two different systems)

| Command | Whose? | Notes |
|---------|--------|--------|
| `/sethome` · `/home` | **You only** | Personal bed-style homes (YaPPlayerData). Never shared. |
| `/f sethome` · `/f home` | **Whole faction** | Base home. Officers+ set; all members teleport. Aliases: `setbase` / `base`. |

Typical loop: create faction → invite → claim land → `/f sethome` at base → members use `/f home`.

## Conquest mode

```text
/c
/yapconquest
```

Chunk conquest land — `enabled: false` by default. Needs factions. See [GAMEPLAY.md](../../gameplay/GAMEPLAY.md).

## Ops checklist

1. Decide: soft factions vs conquest  
2. Enable on survival only (`enabled: true`, `server-id: survival`)  
3. Align with YaPClaims limits so players are not double-taxed confusingly  
4. Document `/f` and `/home` vs `/f home` in `/rules`  

## Related

- [GAMEPLAY.md](../../gameplay/GAMEPLAY.md) · [yapclaims.md](yapclaims.md) · [yapplayerdata.md](yapplayerdata.md)
