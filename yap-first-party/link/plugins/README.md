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

## Portals between fleet servers (Paper/Folia familiar)

`yap-link-server-selector` registers the **BungeeCord** plugin-message channel
(`bungeecord:main` + legacy `BungeeCord`) and handles `Connect` / `ConnectOther`
the same way Velocity does. That means backend portal plugins
(AdvancedPortals in Bungee/Velocity mode, etc.) installed on **lobby** (and
other Folia instances) can send players across fleet servers without a
Link-specific API.

Checklist:

1. `plugins-enabled=true` in `link.properties`, selector jar in `link-data/plugins/`
2. `public-host` / `public-port=25565` point at the Link edge players reconnect to
3. Portal plugin set to **BungeeCord / Velocity proxy** mode (not direct-connect)
4. Target names match Link `servers.*` ids (`lobby`, `survival`, …)
5. Restart Link after installing/updating the selector jar

See [`../../README.md`](../../README.md) for the full first-party layout.
