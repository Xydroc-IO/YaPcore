# Brigadier, NMS, and Paper events

## Product path (default)

Under `game-authority=paper`, Brigadier, Craft/NMS, and the full Paper event
catalog come from **real Paper** — complete API coverage. See
[PAPER_API_COVERAGE.md](PAPER_API_COVERAGE.md).

Compile plugins against `io.papermc.paper:paper-api:26.2.build.112-stable` and
drop jars in `plugins/`.

## Facade path (non-Paper authority only)

YaPcore also ships skeletal `org.bukkit.*` / `io.papermc.*` stubs for the
Compatibility Bridge when Paper is **not** the game authority. That path is
**not** bit-identical Paper. Prefer Paper authority for any real plugin work.

### Brigadier (facade)

- Dependency: Mojang `com.mojang:brigadier`
- Registrar: `io.papermc.paper.command.brigadier.Commands#register`
- Dispatcher: `com.yapcore.command.BrigadierGateway`
- Source stack: `YaPCommandSourceStack` implements `CommandSourceStack`

```java
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;

Commands.register(
    LiteralArgumentBuilder.<CommandSourceStack>literal("yapping")
        .executes(ctx -> {
            ctx.getSource(); // YaPCommandSourceStack
            return 1;
        })
        .build()
);
```

Adventure ↔ Brigadier: `io.papermc.paper.brigadier.PaperBrigadier.message(Component)`.

### Paper / Bukkit events (facade stubs)

Generated from **paper-api 26.2** sources into `org.bukkit.event.**`,
`io.papermc.paper.event.**`, and `com.destroystokyo.paper.event.**`.

- Listen with `@EventHandler` as usual
- Stubs include `HandlerList` + optional `Cancellable`
- Payload getters are deepened over time; constructors accept `Object... ctx` for firing

Regenerate:

```bash
> **Retired (Folia product path):** Paperclip / Phase 3 vendor scripts (`vendor-paper.sh`, `build-vendor-paper.sh`, `apply-yap-paper-hooks.sh`, `smoke-paper-plugins.sh`, `verify-paper-api-coverage.sh`, Paper Phase 3 benches) were removed. Use `./scripts/fetch-folia.sh` and `./scripts/build-yap-folia.sh` instead.

# Paper event stub generator removed with Paperclip tooling
gradle compileJava
```

### Deep NMS / CraftBukkit (facade)

YaPcore provides **facades** (not Mojang obfuscated jars):

| Cast / API | Type |
|------------|------|
| `((CraftPlayer) player).getHandle()` | `net.minecraft.server.level.ServerPlayer` |
| `((CraftWorld) world).getHandle()` | `net.minecraft.server.level.ServerLevel` |
| `MinecraftServer.getServer()` | `net.minecraft.server.MinecraftServer` |
| `NmsAccess.get()` | helper entry |

**Honest limit:** facade only. Under Paper authority you get real CraftBukkit.

## Coverage

See `com.yapcore.api.ApiCoverage`, [PAPER_API_COVERAGE.md](PAPER_API_COVERAGE.md),
and [MODULES_AND_API.md](MODULES_AND_API.md).
