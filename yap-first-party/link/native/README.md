# YaP Link (native)

First-party **Velocity-class** network proxy — `com.yapcore.link.*`, own JVM, no Velocity fork.

**Roadmap:** [docs/YAP_LINK_NATIVE.md](../../../docs/YAP_LINK_NATIVE.md)

## Build

```bash
gradle :yap-link-native:shadowJar
gradle :yap-link-plugin-chat-bridge:installIntoLinkPlugins   # optional
gradle :yap-link-plugin-mod-sync:installIntoLinkPlugins
gradle :yap-link-plugin-server-selector:installIntoLinkPlugins
```

Jar: `yap-first-party/link/native/build/libs/yap-link.jar`

## Run

```bash
./scripts/start-yap-link.sh          # builds jar + plugins, seeds link.properties
java -jar yap-link.jar --home ../link-data
```

Config: `link.properties` or `link.toml` + `forwarding.secret`. Example multi-backend:
[`../../../link-data/link.properties.example`](../../../link-data/link.properties.example).

Plugins: `link-data/plugins/*.jar` — loaded when `plugins-enabled=true`.

## Smoke gates

```bash
./scripts/smoke-yap-link-folia.sh
./scripts/smoke-yap-link-plugins.sh
./scripts/smoke-yap-link-bedrock.sh
./scripts/smoke-yap-link-two-backend.sh   # Phase 6
```

## Console

`help` · `reload` · `list` · `servers` · `say <msg>` · `stop`

## Modules

| Path | Role |
|------|------|
| `yap-first-party/link/native/` | Native proxy (this project) |
| `yap-first-party/link/api/` | YaP Link plugin API |
| `yap-first-party/link/protocol/` | Shared MC codec / Floodgate wire |
| `yap-first-party/link/plugins/` | chat-bridge, mod-sync, server-selector |

See [`../../README.md`](../../README.md) for the full first-party tree.
