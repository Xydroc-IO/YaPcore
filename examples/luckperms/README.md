# Legacy LuckPerms group pack (deprecated)

**Use native YaPPerms instead:** `yap-perms.jar` + `docs/ops/PERMISSIONS.md`.

This folder kept only for operators migrating from an older YaPcore tree that used
LuckPerms. New installs should use:

```bash
gradle installProductDefaults
ranks apply
/yapperm user Steve parent set vip
```

See [`examples/yapperms/`](../yapperms/) for the native reference commands.

## Legacy LP pack

[`apply-yap-ranks.txt`](apply-yap-ranks.txt) was the old LuckPerms command list.
Do not use unless you manually install LuckPerms yourself outside the product path.
