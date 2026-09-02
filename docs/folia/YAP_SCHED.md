# YapSched — Folia-first plugin scheduling

First-party plugins depend on `:yap-sched` and call `com.yapcore.sched.YapSched`
instead of `Bukkit.getScheduler()` sync APIs (which throw on Folia).

| Call | Folia target |
|------|----------------|
| `YapSched.global` / `globalLater` / `globalTimer` | `GlobalRegionScheduler` |
| `YapSched.async` / `asyncLater` / `asyncTimer` | `AsyncScheduler` |
| `YapSched.entity` / `entityLater` | `EntityScheduler` |
| `YapSched.region` | `RegionScheduler` |

Falls back to `BukkitScheduler` only when region schedulers are missing (legacy Paper).

```java
YapTask t = YapSched.globalTimer(this, this::tick, 1L, 20L);
// …
t.cancel();

YapSched.entity(this, player, () -> player.sendMessage("hi"));
```

Smoke: `./scripts/smoke-folia-plugins.sh`

Legacy third-party plugins that still call `Bukkit.getScheduler()` sync APIs:
see [FOLIA_SCHED_COMPAT.md](FOLIA_SCHED_COMPAT.md) (`yap-sched-agent` javaagent).
