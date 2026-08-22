# Folia / Paper API coverage

**Product claim:** under default `game-authority=folia`, YaPcore gives plugins
**Folia API coverage** from the embedded Folia clip — same Folia surface operators
expect on stock Folia **26.2** (including region schedulers where Folia exposes them).

**Legacy Paper path:** under `game-authority=paper`, YaPcore gives plugins
**complete Paper API coverage** — the same `paper-api` that ships inside the
embedded Paperclip (`lib/paper-26.2-yap.jar`), matching stock Paper **26.2**.

YaPcore does **not** reimplement Bukkit for either game path. Folia (default) or
Paper (legacy) is the game and the API. YapEngine is the chassis around it.

## How coverage works

| Path | Who provides `org.bukkit.*` / PaperMC APIs | Complete? |
|------|-----------------------------------------------|-----------|
| **Default** (`game-authority=folia`) | Real Folia | **Yes** — Folia APIs (incl. region APIs) |
| **Legacy** (`game-authority=paper`) | Real Paper (`paper-api` 26.2 inside Paperclip) | **Yes** — same as stock Paper |
| Non-game authority / YaP facade | In-tree stubs | **No** — soft-fail only |

Isolation (Paper path): `Phase3PaperClassLoader` parents the **platform** loader so YaP’s
stub types never shadow Paper’s classes.

Plugins: all `plugin.yml` / `paper-plugin.yml` jars in [`plugins/`](../plugins/)
are loaded by **Folia** (default) or **Paper** (legacy). YaP only loads `yap.yml`
jars from that same folder.

## Pin / versions

| Item | Value |
|------|--------|
| Folia product | `26.2` (see Folia fetch scripts) |
| Paper legacy product | `26.2` (`vendor/paper.pin` build 112) |
| Published `paper-api` | `26.2.build.112-stable` (`gradle.properties` → `paperApiVersion`) |
| Nested in Paperclip | `paper-api-26.2.local-SNAPSHOT.jar` (must match published class set) |
| First-party plugin compile | Folia or `compileOnly("io.papermc.paper:paper-api:…")` (Java **25+** toolchain) |

## Verify

```bash
./scripts/smoke-folia.sh                 # Folia product path
./scripts/verify-paper-api-coverage.sh   # nested API == published PaperMC API (Paper path)
./scripts/smoke-paper-plugins.sh         # real Paper enable + CraftServer + surfaces
./scripts/check-plugin-layout.sh         # unified plugins/ folder
```

Runtime matrix: `com.yapcore.api.ApiCoverage`.

## What “complete” does *not* mean

- **Paper-only plugins on Folia** — many break (same as stock Folia); use Folia-aware jars.
- **Every NMS reflection** against a different mapping year — same caveats as stock Folia/Paper.
- **Phase 3 threading** edge cases — legacy Paper path only; rare plugins that assume
  single-thread entity tick; report breaks (see [PLUGIN_COMPAT.md](PLUGIN_COMPAT.md)).
- **Facade stubs** under non-game authority — not the product path; do not use
  them as a completeness measure.
- **Full Geyser play parity** — dual-stack join/spawn is separate from API coverage.

## Authoring plugins

**Product path:** compile against Folia / Folia-aware APIs (same as writing for Folia 26.2).

**Legacy Paper path:** compile against Paper’s API:

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
