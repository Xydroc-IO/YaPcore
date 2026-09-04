package com.yapcore.world.service;

import com.yapcore.sched.YapSched;
import com.yapcore.world.WorldConfig;
import com.yapcore.world.WorldCreateOptions;
import com.yapcore.world.WorldManagerService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
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
        return createWorld(name, WorldCreateOptions.DEFAULTS);
    }

    @Override
    public CompletableFuture<Boolean> createWorld(String name, WorldCreateOptions options) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!config.allowLoad()) {
            future.complete(false);
            return future;
        }
        String worldName = sanitizeName(name);
        if (worldName == null) {
            future.complete(false);
            return future;
        }
        WorldCreateOptions opts = options != null ? options : WorldCreateOptions.DEFAULTS;
        YapSched.global(plugin, () -> {
            try {
                if (Bukkit.getWorld(worldName) != null) {
                    future.complete(true);
                    return;
                }
                WorldCreator creator = new WorldCreator(worldName);
                applyOptions(creator, opts);
                World world = Bukkit.createWorld(creator);
                future.complete(world != null);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to create/load world " + worldName, e);
                future.complete(false);
            }
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
        String worldName = sanitizeName(name);
        if (worldName == null) {
            future.complete(false);
            return future;
        }
        YapSched.global(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
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

    static void applyOptions(WorldCreator creator, WorldCreateOptions opts) {
        try {
            creator.type(WorldType.valueOf(opts.type()));
        } catch (IllegalArgumentException e) {
            creator.type(WorldType.NORMAL);
        }
        try {
            creator.environment(World.Environment.valueOf(opts.environment()));
        } catch (IllegalArgumentException e) {
            creator.environment(World.Environment.NORMAL);
        }
        if (opts.seed() != null) {
            creator.seed(opts.seed());
        }
        if (opts.generator() != null) {
            creator.generator(opts.generator());
        }
        creator.generateStructures(opts.generateStructures());
    }

    /** Safe world folder name: letters, digits, underscore, hyphen. */
    public static String sanitizeName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim();
        if (n.isEmpty() || n.length() > 64) {
            return null;
        }
        if (!n.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        // Avoid clobbering server roots / traversal
        String lower = n.toLowerCase(Locale.ROOT);
        if (".".equals(n) || "..".equals(n) || lower.contains("..")) {
            return null;
        }
        return n;
    }
}
