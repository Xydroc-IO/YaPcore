package com.yapcore.world.service;

import com.yapcore.sched.YapSched;
import com.yapcore.world.WorldConfig;
import com.yapcore.world.WorldManagerService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class WorldManagerServiceImpl implements WorldManagerService {

    private final JavaPlugin plugin;
    private WorldConfig config;

    public WorldManagerServiceImpl(JavaPlugin plugin, WorldConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void setConfig(WorldConfig config) {
        this.config = config;
    }

    @Override
    public Collection<String> loadedWorlds() {
        return Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<Boolean> loadWorld(String name) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!config.allowLoad()) {
            future.complete(false);
            return future;
        }
        YapSched.global(plugin, () -> {
            if (Bukkit.getWorld(name) != null) {
                future.complete(true);
                return;
            }
            World world = Bukkit.createWorld(new WorldCreator(name));
            future.complete(world != null);
        });
        return future;
    }

    @Override
    public CompletableFuture<Boolean> unloadWorld(String name) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!config.allowUnload()) {
            future.complete(false);
            return future;
        }
        YapSched.global(plugin, () -> {
            World world = Bukkit.getWorld(name);
            if (world == null) {
                future.complete(false);
                return;
            }
            future.complete(Bukkit.unloadWorld(world, true));
        });
        return future;
    }

    @Override
    public CompletableFuture<Boolean> teleportToWorldSpawn(UUID playerUuid, String worldName) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Player player = Bukkit.getPlayer(playerUuid);
        World world = Bukkit.getWorld(worldName);
        if (player == null || world == null) {
            future.complete(false);
            return future;
        }
        Location spawn = world.getSpawnLocation();
        YapSched.entity(plugin, player, () -> {
            player.teleport(spawn);
            future.complete(true);
        });
        return future;
    }
}
