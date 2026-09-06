# YaP Map (yap-map)

Flat Leaflet map + optional BlueMap-class **3D voxel mesh** viewer. Served from the dashboard (`/map/`) or YaPcore pack HTTP when `http.use-yapcore-server: true`.

| Item | Value |
|------|--------|
| Product jar | `plugins/yap-map.jar` |
| Config | `plugins/YaPMap/config.yml` |
| Tiles | `plugins/YaPMap/map/tiles/{world}/{layer}/{zoom}/{x}_{z}.png` |
| Meshes | `plugins/YaPMap/map/meshes/{world}/{layer}/lod{N}/{chunkX}_{chunkZ}.json` + `.ymesh` + `manifest.json` |
| POI | `plugins/YaPMap/poi.json` (example copied from jar on first enable) |
| Markers | `GET /map/markers.json` (players; optional NPC/region/claim/POI) |
| Telemetry | `plugins/YaPMap/render-status.json` (dashboard map snapshot) |

## Scope

- **Flat:** world + layer (surface / cave) + Leaflet tiles + live markers
- **3D:** greedy-merged boxes (format **v2**, milliblock units) per chunk, streamed by camera distance; layers `full` / `surface` / `cave`; stairs/slab/carpet/fence **models**; LOD0–2 pyramid; binary `.ymesh`; Three.js InstancedMesh + orbit controls
- Configurable render origin: `render-origin.mode: spawn|fixed`
- Sample window of `sample-chunk-radius²` chunks (tiles); mesh may use `mesh.extra-radius` / `mesh.follow-players`
- Dirty-chunk incremental re-render on block break/place (tiles + meshes)
- Tile **and mesh** retention via config + `/yapmap prune`
- Telemetry: `lastRenderTime`, `tileDiskBytes`, `meshDiskBytes`, `meshChunkCount`, `renderStatus`, `dirtyChunkCount`

Historical time-slider / render generations are **not** shipped (prefer prune + layers).

## Open the map

| View | URL |
|------|------|
| Flat (default) | `http://127.0.0.1:<port>/map/` or `?view=flat` |
| 3D | `http://127.0.0.1:<port>/map/?view=3d` |
| 3D cave | `http://127.0.0.1:<port>/map/?view=3d&layer=cave` |

`<port>` is the **dashboard** port when browsing via admin UI, or **resource-pack-http-port** (default 8081) when `http.use-yapcore-server: true`, or `http.port` (default 8082) for the embedded map server.

Use the **View** dropdown (Flat / 3D) and **Layer** select in the map chrome. After enable, wait for the first render (~2s) or run `/yapmap render`. 3D needs mesh assets under `/meshes/{world}/{layer}/manifest.json`.

| Asset | URL |
|-------|-----|
| UI | `/map/` |
| Tiles | `/tiles/{world}/{layer}/{zoom}/{x}_{z}.png` |
| Mesh chunk (JSON) | `/meshes/{world}/{layer}/lod{N}/{chunkX}_{chunkZ}.json` |
| Mesh chunk (binary) | `/meshes/{world}/{layer}/lod{N}/{chunkX}_{chunkZ}.ymesh` |
| Mesh manifest | `/meshes/{world}/{layer}/manifest.json` |
| Markers | `/map/markers.json` |

## Mesh config

```yaml
mesh:
  enabled: true
  # max-y: 320   # optional; omit/0 → use max-height (nether still ≤126)
  layers:
    - full
    - surface
    - cave
  default-layer: full
  models: true          # stairs / slabs / carpets / fences as multi-box shapes
  binary: true          # write .ymesh alongside JSON
  max-lod: 2            # 0–2 server LOD pyramid
  follow-players: false # re-center / expand toward players near window edge
  extra-radius: 0       # extra chunks beyond sample-chunk-radius for mesh
```

## Mesh format (v2)

Compact chunk JSON **v2** (milliblock boxes):

`{"v":2,"u":1000,"cx":N,"cz":N,"n":N,"d":[lx,y,lz,sx,sy,sz,rgb,…]}`

- Positions and sizes are **milliblocks** (`u=1000` → 1.0 block). Viewer divides by `u`.
- `rgb` 0xRRGGBB; multiple boxes per block for stairs/fences
- Hidden faces culled by merging adjacent same-color solids (full cubes only; models stay separate)

**Binary `.ymesh`:** little-endian `YMSH` + version/flags + cx/cz + boxCount + unit + packed `i32×7` per box. Prefer binary when present; JSON kept for tooling/compat.

**v1 compatibility:** older files `{"v":1,"d":[lx,y,lz,rgb,…]}` (unit cubes, flat path `meshes/{world}/…`) are still readable. After upgrading, run `/yapmap render` so chunks are rewritten as **v2 under `{world}/{layer}/lod{N}/`**.

### Greedy meshing + block models (Phase 4)

Extraction builds dense RGB + model-kind/state buffers, applies the mesh layer filter, then `GreedyMesher`:

1. Special materials (stairs, slabs, carpets, fences/walls) emit simplified multi-box models from `BlockModelRegistry`
2. Remaining solids greedy-merge into axis-aligned milliblock boxes

Done bar: stairs/slabs/fences look distinct from full cubes in the 3D viewer.

### LOD pyramid (Phase 5)

| LOD | Path | Behavior |
|-----|------|----------|
| 0 | `…/lod0/` | Full greedy + models |
| 1 | `…/lod1/` | Drop thin features (carpets/fence bars), merge adjacent same-color boxes |
| 2 | `…/lod2/` | Coarser quantize + larger thin-feature threshold |

`map-3d.js` picks LOD by camera–chunk distance (near → LOD0, mid → LOD1, far → LOD2).

### Binary transport (Phase 6)

Server writes JSON + `.ymesh` when `mesh.binary: true`. HTTP serves both (`application/octet-stream` for `.ymesh`). Viewer tries `.ymesh` first, falls back to JSON. Typical savings: tens of percent smaller than JSON v2 for the same boxes.

### Beyond sample window (Phase 7)

| Setting | Effect |
|---------|--------|
| `mesh.follow-players: true` | When average player chunk drifts ≥ ~½ window from center, re-center mesh origin and enqueue the new window |
| `mesh.extra-radius: N` | Mesh sample radius = `sample-chunk-radius + N`; also pads dirty renders toward players near the edge |

New chunks run on a **background queue** (async drain, small budget per turn) so Folia region threads are not blocked by a full re-mesh burst—only the usual per-chunk `regionChunk` read.

#### Ops cost

| Knob | Cost note |
|------|-----------|
| `sample-chunk-radius` | Flat tiles + base mesh window; cost ∝ radius² × Y span |
| `mesh.extra-radius` / `follow-players` | Extra mesh disk + CPU; can grow beyond the tile window |
| `mesh.max-lod` | ≈ +(LOD count)× write amplification (LOD1/2 are cheaper than LOD0) |
| `mesh.binary` | ~2× files on disk vs JSON-only; bandwidth drops when clients use `.ymesh` |
| `mesh.models` | Slightly more boxes in builds with stairs/fences; still far below unit-cube |

For a small VPS prefer `sample-chunk-radius: 4–6`, `max-lod: 1`, `follow-players: false`. Dedicated hosts can raise radius and enable follow with `extra-radius: 2–4`.

### Chunk streaming (viewer)

`map-3d.js` loads the layer manifest, then **lazily fetches** chunk `.ymesh`/JSON by camera-target distance / frustum (capped concurrent downloads), switching LOD as you orbit. Each chunk is its own `InstancedMesh`. Marker polling updates player spheres only — it does **not** rebuild terrain.

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

`/yapmap prune [days]` deletes aged **PNG tiles** and **mesh chunk JSON/ymesh** (skips `manifest.json`), and/or oldest files until each store is under the disk budget. Pass days to override `max-age-days` for that run; disk budget still comes from config. Affected mesh world/layer manifests are rebuilt after prune.

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
