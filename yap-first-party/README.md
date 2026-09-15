# YaPcore first-party sources

All YaP-built plugins, APIs, fine-tune modules, and YaP Link live here.
**Runtime install folders stay at the repo root:** `plugins/` (jars) and `modules/` (packaging).

Gradle project names are unchanged (`:chat-plugin`, `:yap-link-native`, …); only `projectDir`
paths moved under this tree. Rebuild with the same tasks as before.

## Layout (mirrors release tiers)

| Folder | Gradle tier | What’s inside |
|--------|-------------|---------------|
| [`core-network/`](core-network/) | CORE + NETWORK | Default server plugins (db, chat, packs, pregen, …) |
| [`gameplay/`](gameplay/) | GAMEPLAY (opt-in) | Skills, dungeons, stacker, qol, gameplay knobs, disasters |
| [`api/`](api/) | API jars | Shared interfaces for plugins & Link plugins |
| [`modules/`](modules/) | Fine-tune packaging | `finetune-modules/` → install to `modules/` |
| [`link/`](link/) | YaP Link stack | Native proxy, **Bedrock session module**, protocol, API, link plugins |
| [`engine/`](engine/) | Shared engine helpers | `yap-sched` (Folia scheduler bridge) |
| [`dev/`](dev/) | Bench / smoke | MSPT bench, compat smoke (not shipped in release) |

Domain `.java` files must stay **≤500 lines** ([CONTRIBUTING.md](../CONTRIBUTING.md)).

## Build & release

```bash
gradle installProductDefaults          # CORE plugins → plugins/
gradle installGameplayDefaults         # + GAMEPLAY plugins
gradle installFineTuneModules          # fine-tune jars → modules/
gradle assemblePluginDist              # build/dist/yap-plugins/{core-network,gameplay,api,…}
gradle assembleRelease                 # full linux/windows release trees
gradle checkDomainLineLimits           # ≤500-line domain gate
```

**License:** first-party sources here are **[GPLv3](../LICENSE)** — [docs/start/LICENSING.md](../docs/start/LICENSING.md).  
**AI disclosure:** [docs/start/AI_TRANSPARENCY.md](../docs/start/AI_TRANSPARENCY.md).

See [`plugins/README.md`](../plugins/README.md) for jar names and tiers.
See [`docs/plugins/PLUGINS.md`](../docs/plugins/PLUGINS.md) for module packaging.

## YaP Link

| Path | Gradle project |
|------|----------------|
| `link/native/` | `:yap-link-native` |
| `link/bedrock/` | `:yap-link-bedrock` |
| `link/api/` | `:yap-link-api` |
| `link/protocol/` | `:yap-protocol` |
| `link/plugins/*` | `:yap-link-plugin-*` |

Jar: `yap-first-party/link/native/build/libs/yap-link.jar`  
Docs: [`docs/network/YAP_LINK.md`](../docs/network/YAP_LINK.md) · [`docs/network/CROSSPLAY.md`](../docs/network/CROSSPLAY.md)
