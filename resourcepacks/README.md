# Resource packs

**Source of truth for the server pack CDN:**

| File | Built by |
|------|----------|
| `yapcore-default.zip` | `./scripts/packs/build-default-resourcepack.sh` |
| `yapcore-default.mcpack` | `./scripts/packs/build-default-bedrock-pack.sh` |

Overlays live in `yap-skies/`, `yap-portals/`, `yap-items/`, `yap-bedrock-blocks/`.

After rebuilding, stage for GitHub:

```bash
./scripts/release/stage-github-upload.sh
# then upload the repo-root UPLOAD/ folder only
```

Do **not** upload from this folder directly, and do not keep hash-suffixed
`yapcore-default-*.zip` copies here (they are stale CDN leftovers).

Server settings: `resource-pack-file=yapcore-default.zip`,
`resource-pack-bedrock-file=yapcore-default.mcpack`.
Docs: [CLIENTS_AND_PACKS.md](../docs/network/CLIENTS_AND_PACKS.md).
