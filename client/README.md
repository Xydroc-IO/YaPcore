# YaP client (optional Fabric mods)

Optional **Java client** mods and the render stack. These are **not** Folia/Paper
plugins — they go in the player’s `.minecraft/mods/`, never `plugins/`.

| Project | Role |
|---------|------|
| [yap-visuals](yap-visuals/) | **Recommended** one-jar install (Sodium + Iris + shaders) |
| [yap-iris](yap-iris/) | LGPL Iris fork (shader loader) |
| [yap-sodium](yap-sodium/) | Official Sodium pin docs (PolyForm Shield — no fork) |
| [yap-shaders](yap-shaders/) | First-party Iris water + skies pack |
| [yap-bag](yap-bag/) | Bag keybind + inventory tabs (talks to YaPPlayerData `/bag`) |
| [yap-ultrawide](yap-ultrawide/) | Hor+ FOV for 21:9 / 32:9 |

Build all optional client jars + the release zip from repo root:

```bash
./scripts/build-yap-client-render.sh
# → dist/client-mods/client_mods.zip   (upload this on GitHub Releases)
# → dist/client-mods/yap-visuals-*.jar, yap-bag-*.jar, yap-ultrawide-*.jar
```

Unzip `client_mods.zip` → `client_mods/` and drop those jars into `.minecraft/mods/`.

Vanilla Java and Bedrock still join YaPcore without any of these mods.

Docs: [CLIENTS_AND_PACKS.md](../docs/network/CLIENTS_AND_PACKS.md) ·
[LICENSING.md](../docs/start/LICENSING.md)

Server plugins live under [yap-first-party/](../yap-first-party/).
