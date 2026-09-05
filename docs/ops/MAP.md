# YaP Map (yap-map)

Flat Leaflet map + optional BlueMap-class **3D voxel mesh** viewer. Served from the dashboard (`/map/`) or YaPcore pack HTTP when `http.use-yapcore-server: true`.

| Item | Value |
|------|--------|
| Product jar | `plugins/yap-map.jar` |
| Config | `plugins/YaPMap/config.yml` |
| Tiles | `plugins/YaPMap/map/tiles/{world}/{layer}/{zoom}/{x}_{z}.png` |
| Meshes | `plugins/YaPMap/map/meshes/{world}/{layer}/{chunkX}_{chunkZ}.json` + `manifest.json` |
| POI | `plugins/YaPMap/poi.json` (example copied from jar on first enable) |
| Markers | `GET /map/markers.json` (players; optional NPC/region/claim/POI) |
| Telemetry | `plugins/YaPMap/render-status.json` (dashboard map snapshot) |

## Scope

- **Flat:** world + layer (surface / cave) + Leaflet tiles + live markers
- **3D:** greedy-merged boxes (format **v2**) per chunk, streamed by camera distance; layers `full` / `surface` / `cave`; Three.js InstancedMesh + orbit controls
- Configurable render origin: `render-origin.mode: spawn|fixed`
- Sample window of `sample-chunk-radius²` chunks (same for tiles and meshes)
- Dirty-chunk incremental re-render on block break/place (tiles + meshes)
- Tile **and mesh** retention via config + `/yapmap prune`
- Telemetry: `lastRenderTime`, `tileDiskBytes`, `meshDiskBytes`, `meshChunkCount`, `renderStatus`, `dirtyChunkCount`

Historical time-slider / render generations are **not** shipped (prefer prune + layers). LOD and stairs models remain out of scope.

## Open the map

| View | URL |
|------|-----|
| Flat (default) | `http://127.0.0.1:<port>/map/` or `?view=flat` |
| 3D | `http://127.0.0.1:<port>/map/?view=3d` |
| 3D cave | `http://127.0.0.1:<port>/map/?view=3d&layer=cave` |

`<port>` is the **dashboard** port when browsing via admin UI, or **resource-pack-http-port** (default 8081) when `http.use-yapcore-server: true`, or `http.port` (default 8082) for the embedded map server.

Use the **View** dropdown (Flat / 3D) and **Layer** select in the map chrome. After enable, wait for the first render (~2s) or run `/yapmap render`. 3D needs mesh JSON under `/meshes/{world}/{layer}/manifest.json`.

| Asset | URL |
|-------|-----|
| UI | `/map/` |
| Tiles | `/tiles/{world}/{layer}/{zoom}/{x}_{z}.png` |
| Mesh chunk | `/meshes/{world}/{layer}/{chunkX}_{chunkZ}.json` |
| Mesh manifest | `/meshes/{world}/{layer}/manifest.json` |
| Markers | `/map/markers.json` |

## Mesh format (v2)

```yaml
mesh:
  enabled: true
  # max-y: 320   # optional; omit/0 → use max-height (nether still ≤126)
  layers:
    - full
    - surface
    - cave
  default-layer: full
  # Or a single layer:  layer: full
```

Compact chunk JSON **v2** (greedy boxes):

`{"v":2,"cx":N,"cz":N,"n":N,"d":[lx,y,lz,sx,sy,sz,rgb,…]}`

- `lx`/`lz` local 0–15; `y` world Y; `sx`/`sy`/`sz` ≥ 1; `rgb` 0xRRGGBB
- Hidden faces are culled by merging adjacent same-color solids into larger boxes (far fewer primitives than one cube per block)

**v1 compatibility:** older files `{"v":1,"d":[lx,y,lz,rgb,…]}` (unit cubes, flat path `meshes/{world}/…`) are still readable in the 3D viewer. After upgrading, run `/yapmap render` so chunks are rewritten as **v2 under `{world}/{layer}/`**. Re-render only is the supported migration; mixed trees are fine until prune removes legacy files.

### Greedy meshing

Extraction builds a dense RGB buffer per chunk, applies the mesh layer filter, then `GreedyMesher` merges same-color runs into axis-aligned boxes. Pure face-cull helpers live alongside for tests (exposed-face count ≪ naive `voxels×6`).

### Chunk streaming (viewer)

`map-3d.js` loads the layer manifest, then **lazily fetches** chunk JSON by camera-target distance / frustum (capped concurrent downloads). Each chunk is its own `InstancedMesh`. Marker polling updates player spheres only — it does **not** rebuild terrain. Orbit stays smooth at the default sample radius because the scene is incremental.

## Layers

### Flat tiles

```yaml
layers:
  surface: true
  cave: true
  cave-max-y: 48
  biome-tint: true
```

| Layer | Behavior |
|-------|----------|
| `surface` | Highest solid ≤ `max-height` (nether uses roof-aware cap ≤126) |
| `cave` | Underground slice ≤ `cave-max-y`, preferring solids with air above |

UI: `?layer=surface` / `?layer=cave`.

### 3D mesh layers

| Layer | Behavior |
|-------|----------|
| `full` | All solids in the mesh Y range (nether roof-aware) |
| `surface` | Highest solid per column (same idea as flat surface) |
| `cave` | Open cave solids ≤ `min(surfaceY−1, cave-max-y)` — same preference for air-above as flat cave |

3D UI Layer select uses `mesh.layers` / `mesh.default-layer` (`?view=3d&layer=…`).

## Retention / prune

```yaml
retention:
  max-age-days: 14   # 0 = off
  max-disk-mb: 512   # 0 = off
```

`/yapmap prune [days]` deletes aged **PNG tiles** and **mesh chunk JSON** (skips `manifest.json`), and/or oldest files until each store is under the disk budget. Pass days to override `max-age-days` for that run; disk budget still comes from config. Affected mesh world/layer manifests are rebuilt after prune.

## Markers config

```yaml
render-origin:
  mode: spawn   # or fixed
  chunk-x: 0
  chunk-z: 0
markers:
  players: true
  npcs: false           # needs yap-npcs
  regions: false        # needs yap-regions (RegionService.listRegions)
  pois: true            # plugins/YaPMap/poi.json
  claims: false         # soft-depend YaPPlayerData ClaimService
  faction-colors: false # optional tint via YaPFactions claim overlays
  poll-seconds: 5
```

### Static POI (`poi.json`)

```json
[
  {"name": "Spawn", "world": "world", "x": 0, "y": 64, "z": 0, "icon": "star"}
]
```

Both viewers poll `/map/markers.json`. Flat mode maps coords relative to the render origin; 3D places player spheres at world X/Y/Z without touching chunk meshes.

## Commands

`/yapmap reload|render|prune`

## Related

- [WEB_DASHBOARD.md](../ops/WEB_DASHBOARD.md) — Map tab
- [PLUGIN_COMPAT_MATRIX.md](../plugins/PLUGIN_COMPAT_MATRIX.md) — Dynmap / BlueMap → yap-map
