# YaP Link native plugins

Native YaP Link plugins — **not** Velocity API.

Build & install into `link-data/plugins/`:

```bash
gradle :yap-link-plugin-chat-bridge:installIntoLinkPlugins
gradle :yap-link-plugin-mod-sync:installIntoLinkPlugins
gradle :yap-link-plugin-server-selector:installIntoLinkPlugins
gradle :yap-link-plugin-tab-bridge:installIntoLinkPlugins
gradle :yap-link-plugin-discord:installIntoLinkPlugins
gradle :yap-link-plugin-op-sync:installIntoLinkPlugins
```

Each jar ships a `link-plugin.json` descriptor. See [`../api/`](../api/) for the plugin API.

## Portals between fleet servers

**First-party:** install **YaPPortals** (`yap-portals.jar`) on Folia backends (usually lobby).
It sends BungeeCord `Connect` messages; `yap-link-server-selector` routes them.

See **[docs/network/PORTALS.md](../../../../docs/network/PORTALS.md)**.

Third-party portal plugins (AdvancedPortals in Bungee/Velocity mode, etc.) still work the
same way against the selector’s Connect handler.

Checklist:

1. `plugins-enabled=true` in `link.properties`, selector jar in `link-data/plugins/`
2. `public-host` / `public-port=25565` point at the Link edge players reconnect to
3. YaPPortals (or another portal plugin) on the backend; target names match Link `servers.*`
4. Restart Link after installing/updating the selector jar

See [`../../README.md`](../../README.md) for the full first-party layout.
