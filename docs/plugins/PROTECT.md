# YaPProtect — product-complete scope

Folia-safe block/container audit log with rollback and restore. Not a full CoreProtect clone.

## In scope (ship)

| Capability | Commands / UI |
|------------|---------------|
| Log block break/place | `logging` + `log-blocks` in config |
| Log container inventory | `log-containers` |
| Log container open + entity kill | Lookup-only (not restorable) |
| Log explosions / liquid / fire | `logging.explosion`, `liquid-flow`, `fire` |
| Lookup by user / block / radius / time | `/yapprotect lookup …` (cursor pages) |
| Export CSV/JSON by user or time window | `/yapprotect export …` → `plugins/YaPProtect/exports/` |
| Rollback by id, radius, time, or user+window | `/yapprotect rollback …` |
| Restore (inverse of rollback) by id, time, or user+window | `/yapprotect restore …` |
| Inspect wand | `/yapprotect inspect` then click a block |
| Prune old rows | `/yapprotect prune [days]` |
| Dashboard | Protect tab: settings, user/radius lookup, rollback/restore |

Aliases: `/co`, `/coreprotect` → `/yapprotect`.

### Restorable vs lookup-only

| Change type | Logged | Rollback / restore |
|-------------|--------|--------------------|
| `BLOCK_BREAK` / `BLOCK_PLACE` | yes | yes |
| `CONTAINER_INVENTORY` | yes | yes |
| `EXPLOSION` / `LIQUID_FLOW` / `FIRE` | yes | yes |
| `CONTAINER_ACCESS` | yes | **lookup-only** |
| `ENTITY_KILL` | yes | **lookup-only** |

Natural / mob / TNT actors use names like `#Nature`, `#Creeper`, `#TNT` when no player UUID is available.

`ProtectService.restoreChanges` is on the public API (same as `rollbackChanges`).

### Pagination

Lookups are **paged** — `limits.max-lookup-limit` is the default **page size** (not a hard total cap).

```bash
/yapprotect lookup user Notch 50 1d
# …results…
# Next: /yapprotect lookup user Notch 50 1d --cursor 1700000000000:42
```

API: `lookupActorPage` / `lookupBlockPage` / `lookupRadiusPage` / `lookupTimeRangePage` return `ProtectLookupPage` with `nextCursor`. Dashboard `POST /api/protect` lookup accepts `cursor` and returns `nextCursor` + `hasMore`.

Bulk rollback/restore walks all matching rows (batched), not a single page.

### Export CSV / JSON

Staff exports walk the same batched `lookupActorAll` / `lookupTimeRangeAll` paths (not a single lookup page).

```bash
/yapprotect export user Notch 1d csv
/yapprotect export user Notch 7d json
/yapprotect export time 2h json
/yapprotect export time world 12h csv
```

Files land under `plugins/YaPProtect/exports/` with a timestamp, e.g. `user-Notch-20260905-154530.csv`. Format defaults to `csv` when omitted. Duration defaults to `7d` for user export.

Every row includes **`server_id`** / `serverId` (from `config.yml` `server-id`). Runtime lookup/rollback/prune are scoped to this server’s id when sharing YaPDB.

### Block codec / tile-entity fidelity

`BlockCodec` stores material + `BlockData` plus Paper-visible tile extras where useful:

| Captured | Notes |
|----------|--------|
| Sign text | Front + back sides (`sf0`…`sb3`, Base64) |
| Skull owner | Owner name when Paper exposes it (not full profile texture hash) |
| Banner | Base color + pattern list |
| Chests / containers | Via `CONTAINER_INVENTORY` + `InventoryCodec` (item bytes), not duplicated on the block row |

**Not** full Mojang NBT: no lectern books, no beacon/spawner/jukebox extras, no PDC blobs, no custom head textures.

### Multi-server

- Each backend sets a distinct `server-id` (e.g. `lobby`, `survival`).
- Rows are tagged at insert; in-plugin queries filter by that id.
- **Cross-server aggregation is ops-side** — query the shared DB with `WHERE server_id = …` (or `IN (…)`) yourself. YaPProtect does **not** offer a distributed multi-server lookup API.

### YaPWorld edit sessions

Soft-depend correlation with YaPWorld / WorldEdit edit sessions is **not** wired (non-trivial on Folia without attributing every WE block write). WorldEdit changes still appear as normal block events with the placing player as actor when Bukkit fires them.

## Explicitly out of scope — later backlog

- Full CoreProtect consumer API parity
- Optional mass-rollback webhook
- Distributed multi-server lookup UI (use SQL / exports by `server_id`)

## Ops examples

```bash
/yapprotect inspect
/yapprotect lookup user Notch 50 1d
/yapprotect export user Notch 1d csv
/yapprotect rollback user Notch 1h
/yapprotect restore user Notch 1h
/yapprotect rollback radius 32 30m
/yapprotect restore 1842 1843
```

Dashboard: **Protect** tab → lookup user or radius → row Rollback/Restore, or user+duration actions.
Lookup rows include `changeType`, `rolledBack`, `restorable`, and `serverId`.

## Related

- [REGIONS.md](../gameplay/REGIONS.md) — admin land flags (separate from audit log)
- [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) — `/api/protect`
