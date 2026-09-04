# Discord relay (YaPDiscord)

Optional bridge between Minecraft and Discord. **Off by default** — enable only after webhooks and secrets are configured.

## Components

| Path | Role |
|------|------|
| `plugins/yap-discord.jar` | Plugin |
| `plugins/YaPDiscord/config.yml` | Webhooks, relay toggles, event toggles, inbound HTTP |
| Dashboard **Discord** tab | Edit config without SSH (`/api/discord`) |

## Recommended setup order

1. **Moderation webhook** — Discord channel → Integrations → Webhooks → copy URL. Paste in dashboard or `webhooks.moderation`. Use **Test mod webhook**.
2. **Chat webhook** — separate channel for in-game chat mirror. Paste in `webhooks.chat`. Test.
3. **Events webhook** (optional) — join/leave/death/advancement. Paste in `webhooks.events`, or leave blank to reuse the chat webhook. Test with **Test events webhook**.
4. **Event toggles** — enable `events.join` / `leave` / `death` / `advancement` as needed (dashboard checkboxes or YAML).
5. **MC → Discord** — set `relay.mc-to-discord: true` (dashboard: MC → Discord chat = on). Requires chat webhook URL.
6. **Discord → MC** (optional) — only for bots posting into MC via HTTP:
   - Set `inbound.secret` to a long random string (not `change-me`).
   - Set `inbound.enabled: true` and `relay.discord-to-mc: true`.
   - Point your bot at `http://<host>:8765/discord/inbound` with the secret header.

Leave **Discord → MC off** until inbound is hardened. Public servers should bind inbound to localhost and reverse-proxy with auth if exposed.

## Defaults (product)

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
  port: 8765
  secret: "change-me"
```

## Server events

When an event toggle is on, YaPDiscord posts an embed to `webhooks.events` (or `webhooks.chat` if events URL is blank):

| Toggle | Bukkit event |
|--------|----------------|
| `events.join` | Player join |
| `events.leave` | Player quit |
| `events.death` | Player death (death message) |
| `events.advancement` | Advancement with a display title (hidden recipe unlocks skipped) |

## Dashboard quick actions

| Action | POST `/api/discord` |
|--------|---------------------|
| Save mod webhook | `{"action":"save-webhook","key":"moderation","url":"..."}` |
| Save chat webhook | `{"action":"save-webhook","key":"chat","url":"..."}` |
| Save events webhook | `{"action":"save-webhook","key":"events","url":"..."}` |
| Save relay | `{"action":"save-relay","mcToDiscord":"true","discordToMc":"false"}` |
| Save event toggles | `{"action":"save-events","join":"true","leave":"true","death":"false","advancement":"false"}` |
| Test webhook | `{"action":"test-webhook","key":"moderation\|chat\|events"}` |
| Reload plugin | `{"action":"reload"}` |

In-game: `/yapdiscord test moderation|chat|events`

## Related

- [WEB_DASHBOARD.md](WEB_DASHBOARD.md) — Discord tab
- [COMMANDS.md](COMMANDS.md) — in-game moderation (feeds mod webhook when configured)
