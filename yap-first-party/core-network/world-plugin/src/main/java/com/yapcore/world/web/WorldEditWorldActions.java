package com.yapcore.world.web;

import com.yapcore.world.pregen.PregenBridge;
import com.yapcore.world.service.SelectionServiceImpl;
import com.yapcore.world.service.WorldManagerServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** World load/unload/tp and pregen actions for {@link WorldEditActionHandler}. */
final class WorldEditWorldActions {

    private final WorldManagerServiceImpl worldManager;
    private final SelectionServiceImpl selection;

    WorldEditWorldActions(WorldManagerServiceImpl worldManager, SelectionServiceImpl selection) {
        this.worldManager = worldManager;
        this.selection = selection;
    }

    void worldLoad(Player player, String body,
                   AtomicReference<Map<String, Object>> result, AtomicInteger status) throws Exception {
        if (!player.hasPermission("yapworld.load")) {
            WorldEditActionHandler.err(result, status, 403, "No permission");
            return;
        }
        String name = WorldEditJson.parseField(body, "world");
        boolean loaded = worldManager.loadWorld(name).get(30, TimeUnit.SECONDS);
        if (loaded) {
            WorldEditActionHandler.ok(result, status, Map.of("message", "Loaded world " + name));
        } else {
            WorldEditActionHandler.err(result, status, 400, "Failed to load " + name);
        }
    }

    void worldUnload(Player player, String body,
                     AtomicReference<Map<String, Object>> result, AtomicInteger status) throws Exception {
        if (!player.hasPermission("yapworld.unload")) {
            WorldEditActionHandler.err(result, status, 403, "No permission");
            return;
        }
        String name = WorldEditJson.parseField(body, "world");
        boolean unloaded = worldManager.unloadWorld(name).get(30, TimeUnit.SECONDS);
        if (unloaded) {
            WorldEditActionHandler.ok(result, status, Map.of("message", "Unloaded world " + name));
        } else {
            WorldEditActionHandler.err(result, status, 400, "Failed to unload " + name);
        }
    }

    void worldTp(Player player, String body,
                 AtomicReference<Map<String, Object>> result, AtomicInteger status) throws Exception {
        if (!player.hasPermission("yapworld.teleport")) {
            WorldEditActionHandler.err(result, status, 403, "No permission");
            return;
        }
        String name = WorldEditJson.parseField(body, "world");
        boolean teleported = worldManager.teleportToWorldSpawn(player.getUniqueId(), name).get(30, TimeUnit.SECONDS);
        if (teleported) {
            WorldEditActionHandler.ok(result, status, Map.of("message", "Teleported to " + name + " spawn"));
        } else {
            WorldEditActionHandler.err(result, status, 400, "Teleport failed — is the world loaded?");
        }
    }

    void pregenSelection(Player player, AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        if (!PregenBridge.available()) {
            WorldEditActionHandler.err(result, status, 400, "YaPPregen not loaded");
            return;
        }
        var opt = selection.selection(player.getUniqueId());
        if (opt.isEmpty()) {
            WorldEditActionHandler.err(result, status, 400, "Set selection first");
            return;
        }
        World world = Bukkit.getWorld(opt.get().world());
        if (world == null) {
            WorldEditActionHandler.err(result, status, 400, "World not loaded");
            return;
        }
        WorldEditActionHandler.ok(result, status, Map.of("message", PregenBridge.startSelection(world, opt.get())));
    }

    void pregenRadius(Player player, String body,
                      AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        if (!PregenBridge.available()) {
            WorldEditActionHandler.err(result, status, 400, "YaPPregen not loaded");
            return;
        }
        int radius = WorldEditActionHandler.parseInt(WorldEditJson.parseField(body, "radius"), 128);
        var loc = player.getLocation();
        WorldEditActionHandler.ok(result, status, Map.of("message", PregenBridge.startRadius(
                player.getWorld(), loc.getBlockX(), loc.getBlockZ(), radius)));
    }

    void pregenCmd(String cmd, String body,
                   AtomicReference<Map<String, Object>> result, AtomicInteger status) {
        if (!PregenBridge.available()) {
            WorldEditActionHandler.err(result, status, 400, "YaPPregen not loaded");
            return;
        }
        String target = WorldEditJson.parseField(body, "world");
        if (target.isBlank()) {
            target = "all";
        }
        String msg = switch (cmd) {
            case "pause" -> PregenBridge.pause(target);
            case "resume" -> PregenBridge.resume(target);
            case "cancel" -> PregenBridge.cancel(target);
            default -> PregenBridge.status(target);
        };
        WorldEditActionHandler.ok(result, status, Map.of("message", msg));
    }

    List<String> discoverWorlds() {
        Set<String> names = new LinkedHashSet<>(worldManager.loadedWorlds());
        File container = Bukkit.getWorldContainer();
        File[] files = container.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && new File(file, "level.dat").isFile()) {
                    names.add(file.getName());
                }
            }
        }
        return new ArrayList<>(names);
    }
}
