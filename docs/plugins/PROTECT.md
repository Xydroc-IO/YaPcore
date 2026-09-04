# YaPProtect — product-complete scope

Folia-safe block/container audit log with rollback and restore. Not a full CoreProtect clone.

## In scope (ship)

| Capability | Commands / UI |
|------------|---------------|
| Log block break/place | `logging` + `log-blocks` in config |
| Log container inventory | `log-containers` |
| Lookup by user / block / radius / time | `/yapprotect lookup …` |
| Rollback by id, radius, time, or user+window | `/yapprotect rollback …` |
| Restore (inverse of rollback) by id, time, or user+window | `/yapprotect restore …` |
| Prune old rows | `/yapprotect prune [days]` |
| Dashboard | Protect tab: settings, user/radius lookup, rollback/restore |

Aliases: `/co`, `/coreprotect` → `/yapprotect`.

## Explicitly out of scope (Wave 1)

- Inspect wand / “near” interactive pick UI ecosystem
- Entity kill logging depth beyond configured change types
- Full CoreProtect consumer API parity

## Ops examples

```bash
/yapprotect lookup user Notch 50 1d
/yapprotect rollback user Notch 1h
/yapprotect restore user Notch 1h
/yapprotect rollback radius 32 30m
/yapprotect restore 1842 1843
```

Dashboard: **Protect** tab → lookup user or radius → row Rollback/Restore, or user+duration actions.

## Related

- [REGIONS.md](../gameplay/REGIONS.md) — admin land flags (separate from audit log)
- [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) — `/api/protect`
