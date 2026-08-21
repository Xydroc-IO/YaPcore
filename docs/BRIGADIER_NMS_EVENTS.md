# Brigadier, NMS, and Paper events

YaPcore ships a **Paper-compatible compile/runtime surface** so plugins can use
Brigadier, Craft/NMS casts, and the Paper event catalog without bundling Paper.

## Brigadier

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

## Paper / Bukkit events (~430+)

Generated from **paper-api 1.21.4** sources into `org.bukkit.event.**`,
`io.papermc.paper.event.**`, and `com.destroystokyo.paper.event.**`.

- Listen with `@EventHandler` as usual
- Stubs include `HandlerList` + optional `Cancellable`
- Payload getters are deepened over time; constructors accept `Object... ctx` for firing

Regenerate:

```bash
chmod +x scripts/generate-paper-event-stubs.sh
./scripts/generate-paper-event-stubs.sh
gradle compileJava
```

## Deep NMS / CraftBukkit

YaPcore provides **facades** (not Mojang obfuscated jars):

| Cast / API | Type |
|------------|------|
| `((CraftPlayer) player).getHandle()` | `net.minecraft.server.level.ServerPlayer` |
| `((CraftWorld) world).getHandle()` | `net.minecraft.server.level.ServerLevel` |
| `MinecraftServer.getServer()` | `net.minecraft.server.MinecraftServer` |
| `NmsAccess.get()` | helper entry |

Online players are constructed as `CraftPlayer` (extends `BridgedPlayer`).
Default world is `CraftWorld`.

**Honest limit:** this is a stable facade for reflection and common plugin patterns.
It is not a drop-in replacement for every Yarn/Mojmap NMS method on a given
Minecraft build. Extend facades as plugin imports demand.

## Coverage

See `com.yapcore.api.ApiCoverage` and [MODULES_AND_API.md](MODULES_AND_API.md).
