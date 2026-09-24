# How to use YaPChat

**Jar:** `yap-chat.jar`  
Private messages, channels, staff/admin chat, ignore list, clear chat.

Default chat is **global** (cross-server when Link + `network.enabled` are on). Relayed lines show `[server-id]` from other backends.

## Player use

```text
/msg Steve hello
/r on my way
/channel              # clickable [Global] [Local] [Trade]… + action-bar hint
/channel local
/ch global
!anyone nearby?          # one-shot local while in global
/ignore Griefer
/unignore Griefer
/ignorelist
```

Click a channel button in chat (or use `/ch <name>`) — no client mod required.

## Staff

```text
/staffchat
/sc Need help at spawn
/adminchat
/clearchat
/yapchat reload
```

Grant `yapchat.socialspy` for PM spy (no Essentials `/socialspy`).

## Colors & format

Chat colors come from **YaPPerms** rank `name-color` / `chat-color`.

Format tokens: `{prefix}{namecolor}{player}{suffix}&7: {chatcolor}{message}`

## Related

[yapperms.md](yapperms.md) · [COMMANDS.md](../../ops/COMMANDS.md)
