# YaP Link native plugins

Native YaP Link plugins — **not** Velocity API.

Build & install into `link-data/plugins/`:

```bash
gradle :yap-link-plugin-chat-bridge:installIntoLinkPlugins
gradle :yap-link-plugin-mod-sync:installIntoLinkPlugins
gradle :yap-link-plugin-server-selector:installIntoLinkPlugins
gradle :yap-link-plugin-tab-bridge:installIntoLinkPlugins
gradle :yap-link-plugin-discord:installIntoLinkPlugins
```

Each jar ships a `link-plugin.json` descriptor. See [`../api/`](../api/) for the plugin API.

See [`../../README.md`](../../README.md) for the full first-party layout.
