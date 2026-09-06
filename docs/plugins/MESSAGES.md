# YaP Messages (shared UX kit)

Shared Adventure messaging for first-party plugins. Part of the **P0 polish** program
(one product voice, permission errors with nodes, configurable `messages.*`).

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

// Parse & / § / hex
YapText.component("&aHello &f{player}", "player", name);

// Bundle from config.yml → messages:
YapMessageBundle msg = YapMessageBundle.fromSection(config.getConfigurationSection("messages"));
msg.noPermission(sender, "yapchat.admin");   // shows Need: yapchat.admin
msg.playersOnly(sender);
msg.reloaded(sender, "YaPChat");
msg.send(sender, "muted", "reason", "spam");
```

### Standard keys

| Key | Default |
|-----|---------|
| `prefix` | _(empty)_ |
| `no-permission` | `&cNo permission.&7 Need: &f{node}` |
| `players-only` | `&cPlayers only.` |
| `reloaded` | `&a{plugin} reloaded.` |
| `failed` | `&c{reason}` |

Placeholders: `{key}` and `%key%`.

### Folia note

Always send via `Player.sendMessage(Component)` / `YapMessageBundle.sendSystem` so chat stays **unsigned system chat** (no “Chat messages cannot be verified”).

## Migration status (P0)

| Plugin | Status |
|--------|--------|
| YaPChat | **Done** — `YapMessageBundle` + `messages.*` in config (reference) |
| YaPEssentials | **Done** — `YapMessages` via command support |
| YaPPerms | **Done** — commands + ranks GUI |
| YaPPlayerData | **Done** — `Perms.require` passes node; commands |
| YaPFactions / YaPConquest | **Done** |
| YaPModeration / YaPAdmin / YaPGuard / YaPLagGuard | **Done** |
| YaPWorld / YaPProtect / YaPRegions / YaPNpcs / YaPMap | **Done** |
| YaPDiscord / YaPTab / YaPCommands / YaPPacks / YaPPregen / YaPDB | **Done** |
| YaPSkills / Stacker / Disasters / Dungeons / GameplayKnobs | **Done** |
| PlaceholderAPI shim | Own `Msg` helper (upstream-style); not migrated |

## Operator tip

After updating YaPChat, add the new `messages.*` keys (or delete `plugins/YaPChat/config.yml` and re-seed) so `no-permission` includes `{node}`.
