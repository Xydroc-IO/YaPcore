# YaPMap

Flat Leaflet tiles + optional BlueMap-class **3D voxel mesh**. Full reference: [PLUGINS.md § Map](PLUGINS.md#map).

## Shipped defaults

| Setting | Default | Notes |
|---------|---------|--------|
| Flat tiles | On | `/yapmap render` |
| `markers.claims` | **true** | PlayerData claim overlays |
| `mesh.enabled` | **false** | Opt-in 3D (CPU/disk) |

## Enable 3D

```yaml
# plugins/YaPMap/config.yml
mesh:
  enabled: true
  layers: [surface]   # start light; add full/cave later
```

Then `/yapmap render` and open the dashboard Map tab (`?view=3d`).

## Finish-out status

Mesher, LOD, `.ymesh` binary, viewer, dirty re-render are **implemented**. Config fallbacks match shipped YAML (`mesh.enabled` false, `markers.claims` true).

Dashboard `/map/` serves plugin `web/` + `map/tiles/` (same origin). Prefer plugin `markers.json` (claims, POIs, spawn origin) over the thin dashboard-only builder. Embedded map HTTP also serves disk `web/` so `map-config.js` is not a 404.
