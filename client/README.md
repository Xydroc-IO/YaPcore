# YaP client (optional Fabric mods)

Optional **Java client** mods and the render stack. These are **not** Folia/Paper
plugins — they go in the player’s `.minecraft/mods/`, never `plugins/`.

| Project | Role |
|---------|------|
| [yap-visuals](yap-visuals/) | **Recommended** one-jar install (Sodium + Iris + shaders) |
| [yap-iris](yap-iris/) | LGPL Iris fork (shader loader) |
| [yap-sodium](yap-sodium/) | Official Sodium pin docs (PolyForm Shield — no fork) |
| [yap-shaders](yap-shaders/) | First-party Iris water + skies pack |
| [yap-presence](yap-presence/) | Bedrock-feel: skins geo, emotes, movement, wardrobe UI (**P**) |
| [yap-blocks](yap-blocks/) | Bedrock catalog block HELLO (`yap:blocks`) |
| [yap-bag](yap-bag/) | Bag keybind + inventory tabs (talks to YaPPlayerData `/bag`) |
| [yap-staff](yap-staff/) | Esc / **R** full Staff GUI (ranks, economy, trolls, …) |
| [yap-ultrawide](yap-ultrawide/) | Hor+ FOV for 21:9 / 32:9 |

Build all optional client jars + the release zip from repo root:

```bash
./scripts/build-yap-client-render.sh
# → dist/client-mods/client_mods.zip   (upload this on GitHub Releases)
# → dist/client-mods/yap-*.jar
./scripts/parity/smoke-bedrock-feel.sh
```

Unzip `client_mods.zip` → `client_mods/` and drop those jars into `.minecraft/mods/`.

When `parity.bedrock-feel=true` on the server, JE clients need **yap-presence** (HELLO).  
Vanilla Java and Bedrock still join when parity mode is off.

Docs: [CLIENTS_AND_PACKS.md](../docs/network/CLIENTS_AND_PACKS.md) ·
[BEDROCK_FEEL_PARITY.md](../docs/product/BEDROCK_FEEL_PARITY.md) ·
[LICENSING.md](../docs/start/LICENSING.md)

Server plugins live under [yap-first-party/](../yap-first-party/).
