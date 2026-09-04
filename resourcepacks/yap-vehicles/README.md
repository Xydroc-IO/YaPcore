# YaP Vehicles resource pack

Custom icons for upgrade parts (CustomModelData **77101–77123** on `paper`)
and high-res fleet bodies (**77200–77210**).

## Install

1. Zip this folder (or use the generated `yap-vehicles.zip` next to it)
2. Drop into `resourcepacks/` and set active in YaPcore / Paper
   OR send to clients via your pack HTTP / `server.properties`

## CustomModelData map — upgrades

| CMD | Upgrade |
|-----|---------|
| 77101 | turbo_engine |
| 77102 | eco_carburetor |
| 77103 | sport_tires |
| 77104 | reinforced_armor |
| 77105 | fuel_tank_xl |
| 77106 | racing_suspension |
| 77107 | nitro_kit |
| 77110–77117 | tire compounds / wheel sizes |
| 77120–77123 | lift kits |

## High-res vehicle models

CustomModelData **77200–77210** on `paper` → full 3D body models
(`assets/yapvehicles/models/vehicle/`). Bodies are converted from
[Automobility](https://github.com/FoundationGames/Automobility) (**MIT**) —
see `AUTOMOBILITY_LICENSE.txt` and `../CREDITS.md`.

| CMD | Model id | In-game name (default) |
|-----|----------|------------------------|
| 77200 | chassis | YaP Chassis (shopping cart mesh) |
| 77201 | buggy | Buggy (orange standard) |
| 77202 | truck_4x4 | 4×4 Truck (green tractor) |
| 77203 | monster_truck | Monster Truck (red tractor) |
| 77204 | sport_car | Sport Car (red standard) |
| 77205 | hypercar | Open Racer |
| 77206 | lambo | Lime Speeder |
| 77207 | ferrari | Magenta Coupe |
| 77208 | mclaren | Copper Motorcar |
| 77209 | porsche | Steel Motorcar |
| 77210 | hoverbike | Amethyst Rickshaw |

Enable in plugin: `visuals.high-res-models: true`

Re-import / refresh zip:

```bash
python3 scripts/import-automobility-vehicles.py
```

## Model showcase (demo images)

Preview PNGs for every fleet body live in [`showcase/`](showcase/).
