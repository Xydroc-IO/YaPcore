# YaP Messages (shared UX kit)

Shared Adventure messaging for first-party plugins. Part of the **P0/P1 polish** program
(one product voice, permission errors with nodes, reload/DB UX, light help).

## Module

| Artifact | Path |
|----------|------|
| `yap-messages-api.jar` | `yap-first-party/api/yap-messages-api/` |

Plugins should **shade/embed** this API in their jar (Folia isolated classloaders), same as `yap-sched` / other small APIs.

## API

```java
import com.yapcore.messages.YapText;
import com.yapcore.messages.YapMessageBundle;
import com.yapcore.messages.YapMessages;
import com.yapcore.messages.YapHelp;
import com.yapcore.messages.YapConfigReload;

YapText.component("&aHello &f{player}", "player", name);

YapMessageBundle msg = YapMessageBundle.fromSection(config.getConfigurationSection("messages"));
msg.noPermission(sender, "yapchat.admin");
msg.playersOnly(sender);
msg.reloaded(sender, "YaPChat");

YapMessages.profileLoading(player);      // sync still applying
YapMessages.databaseNotReady(sender);    // pool down / misconfigured
YapMessages.commandFailed(sender, ex);   // maps pool errors cleanly

YapHelp.simple(sender, "YaPPerms", "/yapperm …");

YapConfigReload.Result r = YapConfigReload.run(() -> { plugin.reloadConfig(); config.reload(); });
YapConfigReload.report(sender, plugin.getLogger(), "YaPPlayerData", r);
```

### Standard keys

| Key | Default |
|-----|---------|
| `prefix` | _(empty)_ |
| `no-permission` | `&cNo permission.&7 Need: &f{node}` |
| `players-only` | `&cPlayers only.` |
| `reloaded` | `&a{plugin} reloaded.` |
| `failed` | `&c{reason}` |

### P1 ops helpers

| Helper | When |
|--------|------|
| `profileLoading` | Player profile sync not ready |
| `databaseNotReady` | YaPDB / SQL pool unavailable |
| `commandFailed` | Prefer DB-not-ready over raw SQLException text |
| `YapConfigReload` | Catch reload parse failures → console + sender |
| `YapHelp` | Shared header/usage voice |

Dashboard YAML editor rejects invalid numbers with **HTTP 400** (`PluginConfigIo.coerce`) instead of silently keeping the old value.

## Migration status

| Plugin | Status |
|--------|--------|
| YaPChat | **Done** — `YapMessageBundle` + `messages.*` (reference) |
| CORE+NETWORK + gameplay cmds | **Done** — `YapMessages` denials / reload |
| YaPPlayerData | **Done** — `SyncService.requireReady` + DB failure mapping |
| Catalog reloads | **Done** — admin/pregen/floodgate/dungeons wired; bedrock-ui/folia-bridge/compat intentional blank |
| PlaceholderAPI shim | Own `Msg` helper (upstream-style) |

## Operator tip

After updating YaPChat, add the new `messages.*` keys (or delete `plugins/YaPChat/config.yml` and re-seed) so `no-permission` includes `{node}`.
