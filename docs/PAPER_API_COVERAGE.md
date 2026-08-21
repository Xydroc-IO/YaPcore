# Paper API coverage

**Product claim:** under default `game-authority=paper`, YaPcore gives plugins
**complete Paper API coverage** — the same `paper-api` that ships inside the
embedded Paperclip (`lib/paper-26.2-yap.jar`), matching stock Paper **26.2**.

YaPcore does **not** reimplement Bukkit for the product path. Paper is the game
and the API. YapEngine is the chassis around it.

## How coverage works

| Path | Who provides `org.bukkit.*` / `io.papermc.*` | Complete? |
|------|-----------------------------------------------|-----------|
| **Default** (`game-authority=paper`) | Real Paper (`paper-api` 26.2 inside Paperclip) | **Yes** — same as stock Paper |
| Non-Paper authority / YaP facade | In-tree stubs | **No** — soft-fail only |

Isolation: `Phase3PaperClassLoader` parents the **platform** loader so YaP’s
stub types never shadow Paper’s classes.

Plugins: all `plugin.yml` / `paper-plugin.yml` jars in [`plugins/`](../plugins/)
are loaded by **Paper**. YaP only loads `yap.yml` jars from that same folder.

## Pin / versions

| Item | Value |
|------|--------|
| Paper product | `26.2` (`vendor/paper.pin` build 112) |
| Published `paper-api` | `26.2.build.112-stable` (`gradle.properties` → `paperApiVersion`) |
| Nested in Paperclip | `paper-api-26.2.local-SNAPSHOT.jar` (must match published class set) |
| First-party plugin compile | `compileOnly("io.papermc.paper:paper-api:${paperApiVersion}")` (Java **25+** toolchain) |

## Verify

```bash
./scripts/verify-paper-api-coverage.sh   # nested API == published PaperMC API
./scripts/smoke-paper-plugins.sh         # real Paper enable + CraftServer + surfaces
./scripts/check-plugin-layout.sh         # unified plugins/ folder
```

Runtime matrix: `com.yapcore.api.ApiCoverage`.

## What “complete” does *not* mean

- **Folia** region APIs — unsupported (YaPcore is not Folia).
- **Every NMS reflection** against a different mapping year — same caveats as stock Paper.
- **Phase 3 threading** edge cases — rare plugins that assume single-thread entity
  tick; report breaks (see [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md)).
- **Facade stubs** under non-Paper authority — not the product path; do not use
  them as a completeness measure.

## Authoring plugins

Compile against Paper’s API (same as writing for Paper 26.2):

```kotlin
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
}
```

Drop the jar in `plugins/` and run YaPcore. See [PLUGINS.md](PLUGINS.md) and
[PLUGIN_COMPAT.md](PLUGIN_COMPAT.md).

## Facade stubs (maintainers only)

Historical Compatibility Bridge types under `src/main/java/org/bukkit/**` etc.
Regenerate event skeletons from current sources if needed:

```bash
./scripts/generate-paper-event-stubs.sh   # paper-api 26.2 sources
```

These are **not** the product API surface.
