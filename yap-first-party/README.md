# YaPcore first-party sources

All YaP-built plugins, APIs, fine-tune modules, and YaP Link live here.
**Runtime install folders stay at the repo root:** `plugins/` (jars) and `modules/` (packaging).

Gradle project names are unchanged (`:chat-plugin`, `:yap-link-native`, …); only `projectDir`
paths moved under this tree. Rebuild with the same tasks as before.

## Layout (mirrors release tiers)

| Folder | Gradle tier | What’s inside |
|--------|-------------|---------------|
| [`core-network/`](core-network/) | CORE + NETWORK | Default server plugins (db, chat, packs, pregen, …) |
| [`gameplay/`](gameplay/) | GAMEPLAY (opt-in) | Skills, stacker, gameplay knobs, disasters |
| [`api/`](api/) | API jars | Shared interfaces for plugins & Link plugins |
| [`modules/`](modules/) | Fine-tune packaging | `finetune-modules/` → install to `modules/` |
| [`link/`](link/) | YaP Link stack | Native proxy, protocol, API, link plugins |
| [`engine/`](engine/) | Shared engine helpers | `yap-sched` (Folia scheduler bridge) |
| [`dev/`](dev/) | Bench / smoke | MSPT bench, compat smoke (not shipped in release) |

## Build & release

```bash
gradle installProductDefaults          # CORE plugins → plugins/
gradle installGameplayDefaults         # + GAMEPLAY plugins
gradle installFineTuneModules          # fine-tune jars → modules/
gradle assemblePluginDist              # build/dist/yap-plugins/{core-network,gameplay,api,…}
gradle assembleRelease                 # full linux/windows release trees
```

**License:** first-party sources here are **[GPLv3](../LICENSE)** — [docs/start/LICENSING.md](../docs/start/LICENSING.md).

See [`plugins/README.md`](../plugins/README.md) for jar names and tiers.
See [`docs/plugins/MODULES_AND_API.md`](../docs/plugins/MODULES_AND_API.md) for module packaging.

## YaP Link

| Path | Gradle project |
|------|----------------|
| `link/native/` | `:yap-link-native` |
| `link/api/` | `:yap-link-api` |
| `link/protocol/` | `:yap-protocol` |
| `link/plugins/*` | `:yap-link-plugin-*` |

Jar: `yap-first-party/link/native/build/libs/yap-link.jar`  
Docs: [`docs/network/YAP_LINK_NATIVE.md`](../docs/network/YAP_LINK_NATIVE.md)
