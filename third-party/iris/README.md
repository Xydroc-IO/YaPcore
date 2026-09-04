# Iris (YaP fork source)

| | |
|--|--|
| License | **LGPL-3.0-only** (Iris) |
| Upstream | https://github.com/IrisShaders/Iris (branch `26.2`) |
| YaP tree | [`yap-iris/`](../../client/yap-iris/) |
| Build | `./scripts/build-yap-client-render.sh` |

## Corresponding source

The YaPcore repository directory `client/yap-iris/` **is** the corresponding source for
binaries named `yap-iris-fabric-*.jar` produced by the build script. Upstream Iris
history: https://github.com/IrisShaders/Iris

## AGPL dependency warning

Iris embeds **glsl-transformer** (AGPL-3.0). See
[`yap-iris/LICENSE-DEPENDENCIES`](../../client/yap-iris/LICENSE-DEPENDENCIES) and
[LICENSING.md](../../docs/start/LICENSING.md). Distributing YaP Iris jars requires
complying with AGPL for that component (source offer includes glsl-transformer or
its upstream).
