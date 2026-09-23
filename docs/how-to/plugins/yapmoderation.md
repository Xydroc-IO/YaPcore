# How to use YaPModeration

**Jar:** `yap-moderation.jar`  
Ban, tempban, IP ban, mute, warn, kick, history, and alt checks.

## Quick start

```text
/warn Steve Griefing
/tempmute Steve 2h Spam
/tempban Steve 7d Cheating
/modhistory Steve
/modcheck Steve
```

## Commands

| Action | Command | Notes |
|--------|---------|-------|
| Ban | `/ban <player> [reason]` | Permanent |
| Temp ban | `/tempban <player> <dur> [reason]` | `30m` `2h` `7d` `1w` |
| Unban | `/unban <player>` | |
| IP ban | `/ipban <player\|ip> [reason]` | |
| Unban IP | `/unbanip <ip>` | |
| Mute | `/mute` `/tempmute` `/unmute` | |
| Warn / kick | `/warn` `/kick` | |
| History | `/modhistory <player> [limit]` | Alias `/history` |
| Alts | `/modcheck <player>` | Alias `/alts` — not `/check` |
| Ban list | `/banlist [limit]` | |
| Reload | `/yapmod reload` | |
| Seen | `/yapmod seen` | Join history / dashboard snapshot |

`/check` is Essentials inspect — use `/modcheck` for alts.

## Staff tips

- Prefer temp punishments first; document reasons.
- YaPAdmin menu and yap-staff also call these shapes.
- Cross-server: shared SQL via YaPDB — bans apply network-wide when DB is shared.

## Related

[COMMANDS.md](../../ops/COMMANDS.md) · [PERMISSIONS.md](../../ops/PERMISSIONS.md) · [yapadmin.md](yapadmin.md)
