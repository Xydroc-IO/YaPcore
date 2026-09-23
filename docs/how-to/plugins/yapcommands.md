# How to use YaPCommands

**Jar:** `yap-commands.jar`  
Define custom player commands in YAML (or dashboard) without writing a plugin.

## Quick start — Discord link command

`plugins/YaPCommands/commands.yml`:

```yaml
discord:
  enabled: true
  aliases: [dc, discordlink]
  permission: yapcommands.cmd.discord
  actions:
    - "msg: &9Discord: &fhttps://discord.gg/your-invite"
```

```text
/yapcommands reload
/discord
```

## Admin commands

```text
/yapcommands list
/yapcommands info discord
/yapcommands toggle discord
/yapcommands reload
```

Aliases: `/ycmd`, `/customcmd`.

## Placeholders

| Token | Meaning |
|-------|---------|
| `{player}` | Name |
| `{uuid}` | UUID |
| `{display}` | Display name |
| `{world}` `{x}` `{y}` `{z}` | Location |
| `{args}` `{args0}` … | Arguments |

Actions can message the player, run player commands, or console commands (see YAML schema / dashboard).

## Dashboard

**Custom commands** tab → edit → save → reload on backends.

## Troubleshoot

| Symptom | Fix |
|---------|-----|
| Unknown command | Reload; check `enabled: true`; permission node |
| Permission denied | Grant `yapcommands.cmd.<name>` or disable require-use-perm |
| Args empty | Player must supply args; use `{args0}` carefully |

## Related

- [COMMANDS.md](../../ops/COMMANDS.md) · [WEB_DASHBOARD.md](../../ops/WEB_DASHBOARD.md)
