# YaP LuckPerms group pack

Starter ranks for YaP first-party plugins: groups, **track `yap`**, **weights**,
**prefix/suffix meta**, and permission nodes.

Full map: [docs/PERMISSIONS.md](../../docs/PERMISSIONS.md).

## Install LuckPerms

```bash
./scripts/install-luckperms.sh
```

(Downloads the Bukkit jar into `plugins/` — not vendored in-repo.)

## Apply the pack

With the server running and LuckPerms enabled:

```text
ranks apply
```

Or: dashboard **Ranks** tab → Apply pack.  
Or: paste [`apply-yap-ranks.txt`](apply-yap-ranks.txt) into the console.  
Or: `yap-ranks-auto-apply=true` in `config/server.properties` (once).

`ranks apply force` / dashboard **Force re-apply** re-runs after clearing the
`config/yap-ranks-applied` marker (`ranks reset-marker`).

## Assign / promote

```text
lp user Steve parent set default
lp user Steve parent set vip
lp user Alex parent set mod
lp user AdminName parent set admin

lp user Steve promote yap
lp user Steve demote yap
```

## Customize

Edit `apply-yap-ranks.txt`, then `ranks apply force`. Or use `lp editor`.
