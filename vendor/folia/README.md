# YaP Folia vendor tree

Upstream: [PaperMC/Folia](https://github.com/PaperMC/Folia) (`ver/26.2.x`)  
License: **GNU GPL v3** (same as Folia / Paper)  
Build requires: **JDK 25+** (Temurin/OpenJDK), Git, network for Paperweight downloads

## Layout

| Path | Tracked? | Purpose |
|------|----------|---------|
| `UPSTREAM.lock` | yes | Pinned branch + commit SHA |
| `patches/` | yes | Ordered YaP patches (`0000`–`0079`, 72 files). 500 hold: `20260919T165559Z` against stock `20260919T171015Z` ending at 119 |
| `README.md` | yes | This file |
| `work/` | **no** (gitignored) | Shallow clone of Folia |
| `work/build/` | no | Gradle outputs |

## Quick start

```bash
./scripts/folia/vendor-folia.sh          # clone/pin into vendor/folia/work
./scripts/folia/folia-patch.sh           # apply YaP patches (after Folia applyAllPatches)
./scripts/folia/build-yap-folia.sh       # → lib/yap-folia-26.2.jar
```

Product config (prefer built jar):

```properties
folia-jar-source=build
folia-version=26.2
```

Stock Fill download remains available via `folia-jar-source=fetch` and `./scripts/folia/fetch-folia.sh`.

## License note

Distributing `yap-folia-*.jar` (a Folia derivative) requires GPL compliance:
provide corresponding source (this vendor tree + patches + build scripts) and
license notices. See Folia’s `LICENSE` and YaPcore [`docs/start/LICENSING.md`](../../docs/start/LICENSING.md).
