# YaP Map (yap-map)

Flat web map: Leaflet + PNG chunk tiles, served from the dashboard (`/map/`) or YaPcore pack HTTP when `http.use-yapcore-server: true`.

| Item | Value |
|------|--------|
| Product jar | `plugins/yap-map.jar` |
| Config | `plugins/YaPMap/config.yml` |
| Tiles | `plugins/YaPMap/map/tiles/{world}/0/{x}_{z}.png` |
| Markers | `GET /map/markers.json` (live players; optional NPC/region on dashboard) |

## Wave 4 scope

- Flat tile map + **live player markers**
- Optional **NPC** / **region** overlays (dashboard path when toggled)
- Dashboard Map tab: render schedule, worlds, marker toggles

**Not in Wave 4:** BlueMap-style **3D** mesh / isometric viewer. That is **Stretch / out until later**. YaP Map is the Dynmap/BlueMap *flat* replacement, not a 3D parity clone — see [PLUGIN_COMPAT_MATRIX.md](../plugins/PLUGIN_COMPAT_MATRIX.md).

## Markers config

```yaml
markers:
  players: true
  npcs: false      # needs yap-npcs; enriched on dashboard /map/markers.json
  regions: false   # needs yap-regions
  poll-seconds: 5
```

Leaflet polls `/map/markers.json`. Coordinates map to the sampled chunk grid (origin corner `0…sample-chunk-radius`). Players outside that window will not appear on the current tile plane.

## Commands

`/yapmap reload|render`

## Related

- [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) — Map tab
- [PLUGIN_COMPAT_MATRIX.md](../plugins/PLUGIN_COMPAT_MATRIX.md) — Dynmap / BlueMap → yap-map
