# Drop fine-tune modules here (`.jar` / `.yapmod`). See [docs/plugins/MODULES_AND_API.md](../docs/plugins/MODULES_AND_API.md).

**Sources:** [`yap-first-party/modules/`](../yap-first-party/README.md) (`finetune-modules/`).

Modules are **operator packaging**: they declare `provides` / `requires`, check that the
matching Paper plugin is online (when applicable), and write `FINE_TUNE.txt` under
`modules/<Name>/` pointing at the real config knobs. Engines stay in `plugins/`.

## Install

```bash
gradle installProductDefaults      # CORE plugins + CORE fine-tune modules
gradle installGameplayDefaults     # + skills/stacker/knobs/disasters + GAMEPLAY modules
gradle installFineTuneModules      # all fine-tune jars only → modules/
gradle assemblePluginDist          # build/dist/yap-plugins/{…,modules/core,modules/gameplay}
```

## CORE modules (default product)

| Jar | `provides` | Points at |
|-----|------------|-----------|
| `yap-playerdata-module.jar` | `playerdata` | `plugins/YaPPlayerData/config.yml` |
| `yap-economy-module.jar` | `economy` | money profile (`requires: [playerdata]`) |
| `yap-packs-module.jar` | `packs` | `server.properties` + YaPPacks |
| `yap-highpop-module.jar` | `highpop` | `config/templates/highpop` |
| `yap-ops-dashboard-module.jar` | `web-dashboard` | `web-dashboard-*` |
| `yap-pregen-module.jar` | `pregen` | YaPPregen |
| `yap-chat-module.jar` | `chat` | YaPChat |
| `yap-floodgate-module.jar` | `floodgate` | YaPFloodgate |
| `yap-db-module.jar` | `yapdb` | YaPDB |

## GAMEPLAY modules (opt-in)

| Jar | `provides` | Points at |
|-----|------------|-----------|
| `yap-stacker-module.jar` | `stacker` | YaPStacker |
| `yap-gameplay-knobs-module.jar` | `gameplay-knobs` | `knobs.yml` encyclopedia |

Remove a jar from `modules/` (and restart) to drop that packaging node from
`provides` / Modules GUI — configs and Paper plugins remain until you remove those too.

See [docs/ops/TUNE.md](../docs/ops/TUNE.md) · [docs/plugins/SKILLS.md](../docs/plugins/SKILLS.md) · [docs/plugins/STACKER.md](../docs/plugins/STACKER.md).
