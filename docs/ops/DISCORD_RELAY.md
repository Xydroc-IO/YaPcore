# Discord relay

Two separate products share a Discord theme — do not confuse them.

| Product | Path | Role |
|---------|------|------|
| **YaP Link Discord** (`yap-link-discord`) | Link proxy plugin | Proxy-side **moderation webhook only** (forwards `yap:mod` plugin messages from backends). No JDA, no chat relay, no inbound HTTP. |
| **YaPDiscord** (`yap-discord.jar`) | Backend Bukkit/Folia plugin | Webhooks (mod / chat / events) + optional **JDA bot** + optional **inbound HTTP** for Discord↔MC. |

## YaPDiscord (backend)

Optional bridge between Minecraft and Discord. **Off by default** — enable only after webhooks / bot token and secrets are configured.

| Path | Role |
|------|------|
| `plugins/yap-discord.jar` | Plugin (shadow JAR includes JDA when built) |
| `plugins/YaPDiscord/config.yml` | Webhooks, relay, events, inbound HTTP, bot, filters, text-commands, channel-updater, console-channel |
| `api/yap-discord-api.jar` | Slash extensibility API (`SlashCommandProvider`, `DiscordSlashRegistrar`) |
| Dashboard **Discord** tab | Edit config without SSH (`/api/discord`) |

### Recommended setup order

1. **Moderation webhook** — Discord channel → Integrations → Webhooks → copy URL. Paste in dashboard or `webhooks.moderation`. Use **Test mod webhook**.
2. **Chat webhook** — separate channel for in-game chat mirror. Paste in `webhooks.chat`. Test. (Skip if you will use the JDA bot for chat.)
3. **Events webhook** (optional) — join/leave/death/advancement. Paste in `webhooks.events`, or leave blank to reuse the chat webhook. Test with **Test events webhook**.
4. **Event toggles** — enable `events.join` / `leave` / `death` / `advancement` as needed (dashboard checkboxes or YAML). The same toggles gate bot-channel posts when the bot is used.
5. **MC → Discord** — set `relay.mc-to-discord: true`. Uses the **bot chat channel** when the bot is connected; otherwise the chat webhook.
6. **Discord → MC** — prefer the **JDA bot** (below). Inbound HTTP is for external automation only:
   - Set `inbound.secret` to a long random string (**not** `change-me`).
   - Set `inbound.enabled: true`, `inbound.bind: 127.0.0.1` (default), and `relay.discord-to-mc: true`.
   - Point your automation at `http://127.0.0.1:8765/discord/inbound` with the secret header.

Leave **Discord → MC off** until inbound/bot is hardened. Public servers should keep inbound on localhost and reverse-proxy with auth if exposed. Enabling inbound with secret `change-me` is rejected at start.

### JDA bot (optional)

```yaml
bot:
  enabled: true
  token: "BOT_TOKEN"
  guild-id: "GUILD_SNOWFLAKE"
  chat-channel-id: "CHANNEL_SNOWFLAKE"
  slash-commands: true
  slash-roles:
    admin: []   # role snowflakes for /broadcast; empty = Discord Administrator only

filters:
  block-everyone: true
  max-length: 500

relay:
  mc-to-discord: true
  discord-to-mc: true
```

1. Create a Discord application → Bot → copy token into `bot.token`.
2. Enable **Message Content Intent** in the Developer Portal (required to read chat / text triggers).
3. Invite the bot to your guild with **Send Messages**, **Use Application Commands**, and (for channel topic updater) **Manage Channels**.
4. Set `guild-id` and `chat-channel-id`, then `bot.enabled: true` and `/yapdiscord reload`.
5. Slash commands (ephemeral where noted):

| Slash | Who | Purpose |
|-------|-----|---------|
| `/status` | anyone | Online count + MSPT/TPS |
| `/players` | anyone | Online name list |
| `/link <code>` | anyone | Complete account link (ephemeral) |
| `/unlink` | self | Remove your link (ephemeral) |
| `/linked` | self | Show MC name/UUID if linked (ephemeral) |
| `/resync` | self (if linked) | Re-apply Discord roles → YaPPerms |
| `/broadcast <message>` | `bot.slash-roles.admin` or Discord Administrator | Post to bot chat channel / chat webhook |

Registered to the guild when `guild-id` is set.
6. Check connection: `/yapdiscord bot status`.
7. For **role sync**, enable **Server Members Intent** in the Developer Portal (bot requests `GUILD_MEMBERS`).

JDA callbacks never touch Bukkit directly — Discord→MC, slash replies, text triggers, and account-link completion are scheduled via YapSched (Folia-safe global/region threads).

### Account link (Minecraft ↔ Discord)

Pairs a Minecraft UUID with a Discord snowflake in table **`yap_discord_links`** (`mc_uuid`, `discord_id`, `linked_at`, `verified`). Role sync and optional nickname sync are product features (see below).

| Step | Where | Action |
|------|--------|--------|
| 1 | In-game | `/discord link` — prints a short code (TTL from `link.code-ttl-seconds`, default 10m) |
| 2 | Discord | Slash **`/link <code>`**, or **DM the bot** the same code |
| 3 | In-game | `/yapdiscord linked` — shows your pairing |
| 4 | Either side | `/yapdiscord unlink` or Discord `/unlink` — removes the pairing |

| In-game command | Permission | Notes |
|-----------------|------------|-------|
| `/yapdiscord link` | `yapdiscord.link` | Generate code |
| `/yapdiscord unlink` | `yapdiscord.unlink` | Self unlink |
| `/yapdiscord linked [player]` | `yapdiscord.linked` / `yapdiscord.linked.others` | Self vs other (admin child) |
| `/yapdiscord resync [player\|all]` | `yapdiscord.resync` | Re-run role sync |
| `/yapdiscord broadcast <msg>` | `yapdiscord.broadcast` | Send to Discord chat channel/webhook |

Storage: shared **YaPDB** when `link.use-shared-yapdb: true` and `yap-db.jar` is loaded; otherwise local SQLite at `plugins/YaPDiscord/discord-links.db`.

#### Role sync (optional)

```yaml
link:
  enabled: true
  use-shared-yapdb: true
  code-ttl-seconds: 600
  role-sync:
    enabled: true
    roles:
      "DISCORD_ROLE_SNOWFLAKE": "vip"
      "ANOTHER_ROLE_ID": "mod"
```

On a successful link (and on `/resync`), if **YaPPerms** is present and `role-sync.enabled`, matching Discord roles are applied with console `yapperm user <name> parent add <group>`. Without YaPPerms the map is still stored in config (no sync at runtime).

#### Nickname sync (optional)

```yaml
link:
  nickname-sync:
    enabled: true
    direction: mc-to-discord   # or discord-to-mc
    interval-minutes: 30       # 0 = only on successful link
```

- **mc-to-discord** — sets the member’s guild nickname from the Minecraft username (bot needs **Manage Nicknames** and a role above the member).
- **discord-to-mc** — sets the online player’s display / tab list name from the Discord nickname (or username). Offline players are skipped until the next periodic pass or re-link.
- Failures are non-fatal (logged at fine) so link / JDA chat keep working.

### Text commands (guild chat triggers)

```yaml
text-commands:
  enabled: true
  playerlist: true
  console-prefix: "!c"   # empty to disable
  console-whitelist: ["tps", "list"]
  require-roles: []      # Discord role IDs for !c; empty = !c disabled
  listen-all-guild: false  # false = only bot.chat-channel-id
```

- In the mapped chat channel (or all guild channels when `listen-all-guild: true`), `playerlist` / `!playerlist` replies with online names (does not relay that message to MC).
- `!c <cmd>` runs `Bukkit.dispatchCommand` on **YapSched.global** when the author has a role in `require-roles` and the first token is in `console-whitelist`. Reply is truncated captured output when possible.
- Empty `require-roles` or blank `console-prefix` disables console triggers.

### Channel topic updater

```yaml
channel-updater:
  enabled: false
  channel-id: ""
  interval-seconds: 60
  topic-template: "Online: {online} | MSPT: {mspt}"
```

Placeholders: `{online}`, `{mspt}`, `{max}`.

**Bot permission required:** **Manage Channels** (`MANAGE_CHANNEL`) on the target channel (invite scope / role hierarchy). Without it the updater logs a warning and skips edits.

### Console channel (DiscordSRV-class)

Dedicated channel for server console — **not** the chat relay channel. Off by default.

```yaml
console-channel:
  enabled: false
  channel-id: ""
  inbound: true          # Discord → run as console
  outbound: true         # Minecraft log lines → Discord
  inbound-roles: []      # Discord role IDs; empty = require Administrator
  command-whitelist: []  # empty = allow all (dangerous); prefer ["tps","list","whitelist"]
  outbound-filter: ["INFO", "WARN", "SEVERE"]
  rate-limit-per-10s: 20
```

| Direction | Behavior |
|-----------|----------|
| **Inbound** | Messages in `channel-id` (guild) are **not** chat-relayed. Content is run via `Bukkit.dispatchCommand` on **YapSched.global** with captured/truncated reply. Role gate: `inbound-roles`, or Discord Administrator when empty. |
| **Outbound** | A lightweight `java.util.logging.Handler` on the Bukkit logger forwards matching levels to Discord (async JDA `queue`, rate-limited). **Can be noisy** — use a private channel and a tight filter. JDA / YaPDiscord loggers are excluded to reduce feedback loops. |

Prefer an explicit `command-whitelist`. Empty whitelist allows **all** console commands for authorized roles.

### Slash extensibility API

Other Bukkit plugins can contribute slash commands without forking YaPDiscord.

1. Soft-depend `YaPDiscord` and compile against `yap-discord-api` (+ JDA compileOnly for `CommandData` / events).
2. Implement `com.yapcore.discord.api.SlashCommandProvider`.
3. Register either:
   - Soft: `DiscordSlashServices.find().ifPresent(r -> r.register(provider))`, or
   - Services: `Bukkit.getServicesManager().register(SlashCommandProvider.class, provider, plugin, ServicePriority.Normal)` then call `DiscordSlashRegistrar#refresh()` if the bot is already up.
4. In `onSlashCommand`, schedule any Bukkit work with **YapSched** (never touch Bukkit on the JDA thread).

Built-in commands (`/status`, `/players`, link cmds, `/broadcast`) win on name collision; colliding provider commands are skipped with a warning. YaPDiscord re-registers the merged set on bot ready and whenever soft providers register/unregister.

Example:

```java
public final class PingSlash implements SlashCommandProvider {
    @Override
    public Collection<? extends CommandData> commands() {
        return List.of(Commands.slash("ping", "Extension smoke ping"));
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        YapSched.global(plugin, () ->
                event.getHook().sendMessage("pong (online="
                        + Bukkit.getOnlinePlayers().size() + ")").queue());
    }
}

// onEnable (after YaPDiscord):
DiscordSlashServices.find().ifPresent(r -> r.register(new PingSlash()));
```

### Defaults (product)

```yaml
webhooks:
  moderation: ""
  chat: ""
  events: ""

relay:
  mc-to-discord: false
  discord-to-mc: false

events:
  join: false
  leave: false
  death: false
  advancement: false

inbound:
  enabled: false
  bind: "127.0.0.1"
  port: 8765
  secret: "change-me"
  max-body-bytes: 8192

bot:
  enabled: false
  token: ""
  guild-id: ""
  chat-channel-id: ""
  slash-commands: true
  slash-roles:
    admin: []

filters:
  block-everyone: true
  max-length: 500

link:
  enabled: true
  use-shared-yapdb: true
  code-ttl-seconds: 600
  role-sync:
    enabled: true
    roles: {}
  nickname-sync:
    enabled: false
    direction: mc-to-discord
    interval-minutes: 0

text-commands:
  enabled: true
  playerlist: true
  console-prefix: "!c"
  console-whitelist: ["tps", "list"]
  require-roles: []
  listen-all-guild: false

channel-updater:
  enabled: false
  channel-id: ""
  interval-seconds: 60
  topic-template: "Online: {online} | MSPT: {mspt}"

console-channel:
  enabled: false
  channel-id: ""
  inbound: true
  outbound: true
  inbound-roles: []
  command-whitelist: []
  outbound-filter: ["INFO", "WARN", "SEVERE"]
  rate-limit-per-10s: 20
```

### Server events

When an event toggle is on, YaPDiscord posts an embed to the **bot chat channel** (if connected) or else `webhooks.events` (or `webhooks.chat` if events URL is blank):

| Toggle | Bukkit event |
|--------|----------------|
| `events.join` | Player join |
| `events.leave` | Player quit |
| `events.death` | Player death (death message) |
| `events.advancement` | Advancement with a display title (hidden recipe unlocks skipped) |

### Dashboard quick actions

| Action | POST `/api/discord` |
|--------|---------------------|
| Save mod webhook | `{"action":"save-webhook","key":"moderation","url":"..."}` |
| Save chat webhook | `{"action":"save-webhook","key":"chat","url":"..."}` |
| Save events webhook | `{"action":"save-webhook","key":"events","url":"..."}` |
| Save relay | `{"action":"save-relay","mcToDiscord":"true","discordToMc":"false"}` |
| Save event toggles | `{"action":"save-events","join":"true","leave":"true","death":"false","advancement":"false"}` |
| Save inbound | `{"action":"save-inbound","enabled":"true","port":"8765","secret":"..."}` |
| Test webhook | `{"action":"test-webhook","key":"moderation\|chat\|events"}` |
| Reload plugin | `{"action":"reload"}` |

In-game: `/yapdiscord link|unlink|linked|resync|broadcast|reload|bot status|test …`

### Hardening notes

- Webhook URLs must match Discord’s webhook URL pattern; invalid URLs are dropped.
- Webhook POSTs retry on HTTP 429 / 5xx (bounded attempts, respects `Retry-After`).
- Inbound binds to `inbound.bind` (default `127.0.0.1`), enforces `max-body-bytes`, and refuses to start with secret `change-me`.
- Discord `!c` is role-gated + whitelist-only; keep `require-roles` empty unless you intend console from Discord.
- Console channel: keep `command-whitelist` non-empty in production; outbound log mirror can flood Discord — rate limit + private channel.

## Related

- [WEB_DASHBOARD.md](WEB_DASHBOARD.md) — Discord tab
- [COMMANDS.md](COMMANDS.md) — in-game moderation (feeds mod webhook when configured)
