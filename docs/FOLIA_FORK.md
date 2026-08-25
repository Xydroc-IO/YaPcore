# YaP Folia fork

Phase 1 bootstrap: vendor PaperMC Folia 26.2, apply ordered YaP patches, build
`yap-folia-26.2.jar`, and teach YaPcore to prefer it.

**Zero gameplay / perf changes in Phase 1** — branding only (`YaP-Folia`).

## Layout

```
vendor/folia/
  README.md           # license + overview
  UPSTREAM.lock       # REPO / BRANCH / COMMIT / MC_VERSION
  patches/
    0000-yap-branding.patch
  work/               # gitignored clone (scripts/vendor-folia.sh)
scripts/
  vendor-folia.sh
  folia-patch.sh
  build-yap-folia.sh
  verify-yap-folia.sh
```

## Build

```bash
./scripts/vendor-folia.sh          # clone + pin
./scripts/build-yap-folia.sh       # patch → applyAllPatches → jar
# → lib/yap-folia-26.2.jar
```

Requires **JDK 25+**, Git, and network (Paperweight downloads Minecraft + Paper).

## Product config

```properties
game-authority=folia
folia-version=26.2
folia-jar-source=build    # build | fetch | path | auto
folia-jar-path=           # optional absolute/relative jar when source=path
folia-jar-url=            # optional Fill override when source=fetch
```

Resolution order in `FoliaFiles.ensureFoliaJar()`:

1. `folia-jar-path` (if set and usable)
2. `lib/yap-folia-{version}.jar` when `folia-jar-source=build`
3. existing `folia-kernel/folia-{version}.jar` / `lib/folia-{version}.jar`
4. Fill download (`folia-jar-url` or PaperMC Fill)

Default is **`build`** (YaP-Folia) after Agent 2 soak-compat green. Stock Fill:
set `folia-jar-source=fetch`.

```bash
./scripts/build-yap-folia.sh
./scripts/soak-yap-folia.sh compat
```

See [YAP_FOLIA_SOAK.md](YAP_FOLIA_SOAK.md).

## Changelog (default jar source)

| When | Default |
|------|---------|
| Phase 1 bootstrap | `fetch` (stock Fill) |
| Pre soak-compat green | `fetch` + loud **recommended: `build`** |
| After A2 soak-compat green | **`build`** (YaP-Folia) — Agent 1 flipped |

## Adding a patch (Agents 2 / 3)

1. `./scripts/vendor-folia.sh` and ensure clean pin (`git -C vendor/folia/work status`).
2. Prefer editing Folia’s **tracked** patch inputs under:
   - `folia-server/paper-patches/`
   - `folia-server/minecraft-patches/`
   - `folia-api/paper-patches/`
   - `folia-server/build.gradle.kts.patch`
3. Or after `./gradlew applyAllPatches`, edit generated sources and fold changes back with Folia’s `./rb.sh` (upstream workflow).
4. Export an ordered patch against the pin:

   ```bash
   git -C vendor/folia/work diff > vendor/folia/patches/0001-short-name.patch
   ```

5. Name files `000N-description.patch` (zero-padded). `folia-patch.sh` applies in sort order **before** `applyAllPatches`.
6. `./scripts/folia-patch.sh --check` then `./scripts/build-yap-folia.sh`.
7. Smoke: `FOLIA_JAR_SOURCE=build ./scripts/smoke-folia.sh`

**Do not** edit the same region/teleport/scheduler files as another agent in the same PR (see `docs/FOLIA_FORK_AGENT_HANDOFF.md`).

## Refresh upstream

```bash
./scripts/vendor-folia.sh --update-lock   # tip of ver/26.2.x → UPSTREAM.lock
./scripts/folia-patch.sh --check          # re-validate YaP patches
# Fix conflicts, then:
./scripts/build-yap-folia.sh
```

Commit the updated `UPSTREAM.lock` + any patch rebases together.

## Verify

```bash
./scripts/verify-yap-folia.sh             # build + smoke with source=build
SKIP_SMOKE=1 ./scripts/verify-yap-folia.sh
./scripts/soak-yap-folia.sh compat       # shared soak (docs/YAP_FOLIA_SOAK.md)
```

## License

Folia is **GPL-3.0**. Shipping `yap-folia-*.jar` requires offering corresponding
source (this tree + patches + build scripts). See Folia `LICENSE` and YaPcore
`docs/LICENSING.md`.

## Next phases

| Agent | Work |
|-------|------|
| 2 | Scheduler shim + teleport transactions |
| 3 | Regionizer / save / scoreboard perf |

Handoff map: [`FOLIA_FORK_AGENT_HANDOFF.md`](FOLIA_FORK_AGENT_HANDOFF.md).
