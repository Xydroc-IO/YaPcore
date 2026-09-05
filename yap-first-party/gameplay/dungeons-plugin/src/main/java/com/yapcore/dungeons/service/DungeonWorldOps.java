package com.yapcore.dungeons.service;

import com.yapcore.dungeons.DungeonsConfig;
import com.yapcore.sched.YapSched;
import com.yapcore.world.WorldCreateOptions;
import com.yapcore.world.WorldManagerService;
import com.yapcore.world.WorldServices;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class DungeonWorldOps {

    private final JavaPlugin plugin;
    private final DungeonsConfig config;

    public DungeonWorldOps(JavaPlugin plugin, DungeonsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public CompletableFuture<Boolean> createFlat(String worldName, long seed) {
        Optional<WorldManagerService> wm = WorldServices.worldManager();
        if (wm.isPresent()) {
            WorldCreateOptions opts = WorldCreateOptions.builder()
                    .type("FLAT")
                    .environment("NORMAL")
                    .seed(seed)
                    .generateStructures(false)
                    .build();
            return wm.get().createWorld(worldName, opts);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        YapSched.global(plugin, () -> {
            try {
                if (Bukkit.getWorld(worldName) != null) {
                    future.complete(true);
                    return;
                }
                WorldCreator creator = new WorldCreator(worldName);
                creator.type(WorldType.FLAT);
                creator.generateStructures(false);
                creator.seed(seed);
                World world = Bukkit.createWorld(creator);
                future.complete(world != null);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "createFlat failed", e);
                future.complete(false);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> delete(String worldName) {
        Optional<WorldManagerService> wm = WorldServices.worldManager();
        if (wm.isPresent()) {
            return wm.get().deleteWorld(worldName);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        YapSched.global(plugin, () -> {
            try {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    for (Player p : world.getPlayers()) {
                        World fb = Bukkit.getWorlds().stream().filter(w -> !w.equals(world)).findFirst().orElse(null);
                        if (fb != null) {
                            p.teleport(fb.getSpawnLocation());
                        }
                    }
                    Bukkit.unloadWorld(world, false);
                }
                Path folder = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
                if (Files.exists(folder)) {
                    try (var walk = Files.walk(folder)) {
                        walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
                    }
                }
                future.complete(true);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "delete world failed", e);
                future.complete(false);
            }
        });
        return future;
    }

    public void cleanupOrphanWorldFolders() {
        YapSched.global(plugin, () -> {
            Path root = plugin.getServer().getWorldContainer().toPath();
            try (var stream = Files.list(root)) {
                stream.filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .filter(n -> n.startsWith("yd_"))
                        .filter(n -> Bukkit.getWorld(n) == null)
                        .forEach(n -> delete(n));
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "orphan scan failed", e);
            }
        });
    }
}
