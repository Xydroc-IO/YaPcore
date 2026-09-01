# YaP Vehicle Addon Example

Third-party Paper plugin that builds **`cargo_truck`** on `ChassisKit.truck()`.

## Requirements

1. `plugins/yap-vehicles.jar` (YaPVehicles) enabled
2. This jar in `plugins/`

## Build

```bash
gradle :yap-vehicle-addon:jar
cp examples/yap-vehicle-addon/build/libs/yap-vehicle-addon-example.jar plugins/
```

## Author pattern

```java
ChassisBlueprint frame = ChassisKit.truck(); // or bare() / car() / bike()
api.registerType(VehicleType.builder("my_car")
    .chassis(frame)
    .bodyPanel(frame, "cabin", Material.BLUE_CONCRETE, 1.2, 0.6, 1.0)
    .traction(1.0)
    .lateralGrip(0.3)
    .build());
```

Soft-depend:

```yaml
softdepend: [YaPVehicles]
```

See [docs/plugins/VEHICLES.md](../../docs/plugins/VEHICLES.md).
