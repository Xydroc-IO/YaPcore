# How to use YaPTebex / store

**Jar:** `yap-tebex.jar + optional tebex.jar`  
Store package delivery on Hub — ranks and kits.

## Setup

```bash
./scripts/plugins/fetch-tebex.sh
# or: gradle fetchTebex
```

Hub only. Wire packages to:

```text
yapperm user {username} parent add vip
kit grant {username} vip
```

Examples: `examples/tebex/`.

## Related

[INTEGRATIONS.md](../../ops/INTEGRATIONS.md) · [TEBEX.md](../../ops/TEBEX.md) · [yapperms.md](yapperms.md)
