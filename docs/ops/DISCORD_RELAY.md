# Discord relay (YaPDiscord)

Optional bridge between Minecraft and Discord. **Off by default** — enable only after webhooks and secrets are configured.

## Components

| Path | Role |
|------|------|
| `plugins/yap-discord.jar` | Plugin |
| `plugins/YaPDiscord/config.yml` | Webhooks, relay toggles, inbound HTTP |
| Dashboard **Discord** tab | Edit config without SSH (`/api/discord`) |

## Recommended setup order

1. **Moderation webhook** — Discord channel → Integrations → Webhooks → copy URL. Paste in dashboard or `webhooks.moderation`. Use **Test mod webhook**.
2. **Chat webhook** — separate channel for in-game chat mirror. Paste in `webhooks.chat`. Test.
3. **MC → Discord** — set `relay.mc-to-discord: true` (dashboard: MC → Discord chat = on). Requires chat webhook URL.
4. **Discord → MC** (optional) — only for bots posting into MC via HTTP:
   - Set `inbound.secret` to a long random string (not `change-me`).
   - Set `inbound.enabled: true` and `relay.discord-to-mc: true`.
   - Point your bot at `http://<host>:8765/discord/inbound` with the secret header.

Leave **Discord → MC off** until inbound is hardened. Public servers should bind inbound to localhost and reverse-proxy with auth if exposed.

## Defaults (product)

```yaml
webhooks:
  moderation: ""
  chat: ""

relay:
  mc-to-discord: false
  discord-to-mc: false

inbound:
  enabled: false
  port: 8765
  secret: "change-me"
```

## Dashboard quick actions

| Action | POST `/api/discord` |
|--------|---------------------|
| Save mod webhook | `{"action":"save-webhook","key":"moderation","url":"..."}` |
| Save chat webhook | `{"action":"save-webhook","key":"chat","url":"..."}` |
| Save relay | `{"action":"save-relay","mcToDiscord":"true","discordToMc":"false"}` |
| Reload plugin | `{"action":"reload"}` |

## Related

- [WEB_DASHBOARD.md](WEB_DASHBOARD.md) — Discord tab
- [COMMANDS.md](COMMANDS.md) — in-game moderation (feeds mod webhook when configured)
