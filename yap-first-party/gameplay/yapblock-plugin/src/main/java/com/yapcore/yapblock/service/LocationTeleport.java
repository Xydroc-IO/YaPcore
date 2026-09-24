package com.yapcore.yapblock.service;

import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class LocationTeleport {

    private LocationTeleport() {
    }

    public static CompletableFuture<Boolean> teleport(JavaPlugin plugin, Player player, Location dest) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        YapSched.entity(plugin, player, () ->
                player.teleportAsync(dest).thenAccept(ok ->
                        YapSched.entity(plugin, player, () -> future.complete(Boolean.TRUE.equals(ok)))));
        return future;
    }

    public static void teleport(JavaPlugin plugin, Player player, Location dest, Consumer<Boolean> then) {
        teleport(plugin, player, dest).thenAccept(ok -> YapSched.entity(plugin, player, () -> then.accept(ok)));
    }
}
