# How to use YaPPerms

**Jar:** `yap-perms.jar`  
Native permissions: groups, tracks, prefixes, name/chat colors. LuckPerms-class for YaP.

## What it does

Controls what players can run and how their name looks in chat. Dashboard rank editor + `/yapperm` CLI.

## Quick start — starter ranks

```text
# console or in-game admin
/yapperm applypack
# or YaPcore console:
ranks apply
```

Then assign:

```text
/yapperm user Steve parent set default
/yapperm user Steve parent set vip
/promote Steve
/demote Steve
```

## Common workflows

### Inspect

```text
/yapperm user Steve info
/yapperm group list
/yapperm group info vip
/yapperm track list
```

### Create a group

```text
/yapperm group create helper
/yapperm group setprefix helper &a[Helper]
/yapperm group setnamecolor helper GREEN
/yapperm group setchatcolor helper GRAY
/yapperm group permission set helper yapmod.kick true
```

### User overrides

```text
/yapperm user Steve permission set yapessentials.fly true
/yapperm user Steve meta set &b[Donor]
/yapperm user Steve parent add vip
/yapperm user Steve parent remove default
/yapperm user Steve parent set staff
```

### Dashboard

Edit ranks on web dashboard → apply with `/yapperm editor-apply` or dump with `/yapperm dump`.

## Commands

| Command | Purpose |
|---------|---------|
| `/yapperm user …` | Info, parents, permissions, meta |
| `/yapperm group …` | CRUD, prefix, colors, nodes |
| `/yapperm track …` | Promotion ladders |
| `/yapperm applypack` | Seed starter pack |
| `/yapperm reload` | Reload config |
| `/promote` `/demote` | Move on track `yap` |

Aliases: `/yperms`, `/perms`.

## Troubleshoot

| Symptom | Fix |
|---------|-----|
| No prefix in chat | YaPChat format uses YaPPerms meta — check group prefix + chat plugin |
| Command denied | `/yapperm user <p> info` — missing node or wrong parent |
| Pack already applied | `ranks apply force` or reset marker |

## Related

[PERMISSIONS.md](../../ops/PERMISSIONS.md) · [yapchat.md](yapchat.md) · [COMMANDS.md](../../ops/COMMANDS.md)
