# Drop fine-tune modules here (.jar / .yapmod). See docs/MODULES_AND_API.md

## Shipped by default

`gradle shadowJar` / `assembleRelease` installs:

| Jar | Role |
|-----|------|
| `yap-vehicles-module.jar` | `provides: [vehicles]` (engine is still `plugins/yap-vehicles.jar`) |

See [docs/VEHICLES.md](../docs/VEHICLES.md).
