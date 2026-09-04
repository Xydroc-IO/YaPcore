# Licensing

## YaPcore (this repository)

**License:** [GNU General Public License v3.0](../LICENSE) (or later)

Copyright (c) 2026 YapLabs / YaPcore contributors.

You may use, modify, and distribute YaPcore source and first-party plugins under
**GPLv3**. If you distribute binaries or modified versions, you must provide
corresponding source under the same license. See [LICENSE](../LICENSE).

This matches the license family used by **Paper / Folia** and optional store
plugins such as **Tebex**.

---

## Third-party components

YaPcore integrates with or bundles components under **other licenses**. You are
responsible for complying with each when you ship or run a server.

| Component | License | Notes |
|-----------|---------|-------|
| **Folia / Paper** | **GPLv3** | Downloaded separately (`fetch-folia.sh`); not redistributed in-repo |
| **Tebex** (optional store plugin) | **GPLv3** | `./scripts/fetch-tebex.sh` → `plugins/tebex.jar`; notices in `third-party/tebex/` |
| **Grim Anticheat** (optional AC) | **GPLv3** | `./scripts/fetch-grim.sh` → `plugins/grim.jar`; notices in `third-party/grim/` |
| **Minecraft server software** | Mojang EULA | You must accept `eula=true`; not open source |
| **Faithful 64x** (default pack) | See `resourcepacks/FAITHFUL_LICENSE.txt` | Bundled in release zips when present |
| **Sodium** (optional client) | **PolyForm Shield 1.0.0** | Official jar pin only — [yap-sodium/](../../client/yap-sodium/); notices in `third-party/sodium/` |
| **YaP Iris** (optional client) | **LGPL-3.0** (+ **AGPL-3.0** glsl-transformer) | Fork under [yap-iris/](../../client/yap-iris/); notices in `third-party/iris/` |
| **YaP Shaders** (optional client) | **GPLv3** | First-party Iris pack [yap-shaders/](../../client/yap-shaders/) |
| **MariaDB Docker image** | GPLv2 (MariaDB) | Optional DB via `deploy/mariadb/` |
| **PlaceholderAPI compat layer** | **GPLv3** (YaP reimplementation) | `yap-placeholderapi.jar` |

### Folia / Paper (GPLv3)

YaPcore does **not** fork or ship Folia/Paper source in this repository’s primary
tree as upstream Paper. Operators download official builds or build **YaP-Folia**.
When you distribute a server package that includes Folia/Paper/YaP-Folia binaries,
GPLv3 obligations apply — consult the
[Folia](https://github.com/PaperMC/Folia) / [Paper](https://github.com/PaperMC/Paper)
licenses and your legal counsel.

### Tebex (GPLv3, optional)

The official [Tebex Minecraft plugin](https://github.com/tebexio/Tebex-Minecraft) is
**GPLv3**. Fetch with `./scripts/fetch-tebex.sh` or `gradle fetchTebex`; ship notices
from `third-party/tebex/`. Setup: [TEBEX.md](../ops/TEBEX.md).

### Grim Anticheat (GPLv3, optional)

The official [Grim Anticheat](https://github.com/GrimAnticheat/Grim) plugin is
**GPLv3**. Fetch with `./scripts/fetch-grim.sh` or `gradle fetchGrim`; ship notices
from `third-party/grim/`. Setup: [GRIM.md](../ops/GRIM.md).

### Optional client render stack (Sodium / YaP Iris / YaP Shaders)

Java players who want realistic **water** and **shader skies** can install the
optional Fabric client stack (MC **26.2**). It is **not** a Folia plugin and is
never forced by the server.

| Piece | License | Notes |
|-------|---------|-------|
| **Sodium** | PolyForm Shield 1.0.0 | Official unmodified jar only. A YaP fork would compete with Sodium and is **not** allowed under PolyForm Shield. Pin + fetch: [yap-sodium/](../../client/yap-sodium/), `./scripts/fetch-sodium.sh` |
| **YaP Iris** | LGPL-3.0 | LGPL fork of [Iris](https://github.com/IrisShaders/Iris) in [yap-iris/](../../client/yap-iris/). Embeds **glsl-transformer** (**AGPL-3.0**) — distributing the jar requires AGPL compliance for that dependency ([LICENSE-DEPENDENCIES](../../client/yap-iris/LICENSE-DEPENDENCIES)). Corresponding source: `client/yap-iris/` in this repo. |
| **YaP Shaders** | GPLv3 | First-party Iris-format pack (water + skies). Not a Complementary/BSL fork. |

Build / bundle: `./scripts/build-yap-client-render.sh` → `dist/client-mods/`.
Docs: [CLIENTS_AND_PACKS.md](../network/CLIENTS_AND_PACKS.md).

YaPcore first-party code (chassis, plugins, Link) is **GPLv3** unless a file says
otherwise.

### Minecraft / Mojang

Minecraft is a trademark of Mojang Studios / Microsoft. YaPcore is an independent
project and is **not** affiliated with or endorsed by Mojang or Microsoft.

Running a public server requires accepting the
[Minecraft EULA](https://www.minecraft.net/en-us/eula).

Operator-facing legal docs (templates, not legal advice):

- [Privacy Policy](PRIVACY_POLICY.md)  
- [Terms of Use](TERMS_OF_USE.md)

---

## Default resource pack

The default client pack may include **Faithful 64x**, **YaP Skies**, and YaP vehicle overlays.
Credits and pack-specific licenses: `resourcepacks/CREDITS.md`,
`resourcepacks/FAITHFUL_LICENSE.txt`.

---

## Questions

For licensing questions about YaPcore first-party code, open a GitHub issue or
see [CONTRIBUTING.md](../../CONTRIBUTING.md).
