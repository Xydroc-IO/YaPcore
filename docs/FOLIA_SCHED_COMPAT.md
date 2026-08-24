# Folia scheduler compat (yap-sched-agent)

Legacy Paper plugins call `Bukkit.getScheduler().runTask*` / `scheduleSync*`.
On Folia those sync paths throw in `CraftScheduler.handle()` (`if (true) throw new UnsupportedOperationException()`).

YaPcore ships a **JVM javaagent** that rewrites `handle` to route tasks onto Folia region schedulers.

## Enable

`config/server.properties` (defaults ON):

```properties
folia-sched-compat=true
folia-sched-compat-warn=true
```

`FoliaKernel.buildCommand()` injects:

```text
--add-opens=java.base/java.lang=ALL-UNNAMED
-javaagent:server/lib/yap-sched-agent.jar=warn=true,metrics=true
```

(`--add-opens` lets the agent define helper classes into Paper's URLClassLoader.)

Build / install agent:

```bash
gradle :yap-sched-agent:installAgent
```

## Routing

| Context | Target |
|---------|--------|
| `SchedCompatContext.setEntity(entity)` | `EntityScheduler` |
| `SchedCompatContext.setLocation(loc)` | `RegionScheduler` (chunk of location) |
| else | `GlobalRegionScheduler` + one-time warning |

Async `runTaskAsynchronously*` is unchanged (Folia already allows the async CraftScheduler path).

First-party plugins should still prefer [`YapSched`](YAP_SCHED.md). This agent is for **third-party jars** that never call YapSched.

## Metrics

`com.yapcore.sched.agent.SchedCompatMetrics`:

- `shimFires()` — total rewritten sync schedules
- `globalFallbacks()` / `entityRoutes()` / `regionRoutes()`

Smoke logs look for `yap-sched-agent: rewritten CraftScheduler.handle`.

## Smoke

```bash
./scripts/smoke-folia-sched-compat.sh
# SKIP_LIVE=1 ./scripts/smoke-folia-sched-compat.sh   # unit + jar only
```

Synthetic plugin: `yap-legacy-sched-smoke.jar` (`legacy-sched-smoke-plugin`) — must print
`YaP-LEGACY-SCHED-SMOKE all-ok` without `UnsupportedOperationException`.

## Limits

- Does not invent thread-safety. Global fallback is “loads”, not “correct under contention”.
- Plugins that mutate arbitrary entities from a global task can still race — set entity/location context or migrate to YapSched.
- Folia still requires `folia-supported: true` in `plugin.yml` to load at all (separate from this shim).

## Related

- [YAP_SCHED.md](YAP_SCHED.md) — first-party API
- [FOLIA_PLUGIN_COMPAT_MATRIX.md](FOLIA_PLUGIN_COMPAT_MATRIX.md) — Works / Shimmed / Broken
- [PLUGIN_BACKCOMPAT.md](PLUGIN_BACKCOMPAT.md) — ASM field renames (not Folia sched)
