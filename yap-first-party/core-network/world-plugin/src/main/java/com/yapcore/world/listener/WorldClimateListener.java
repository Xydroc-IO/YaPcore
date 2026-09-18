package com.yapcore.world.listener;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creative-hub climate: always noon, no weather cycle, no natural mob spawns.
 * On when {@code climate.enabled} is true, or when unset and this instance is {@code creative}.
 */
public final class WorldClimateListener implements Listener {

    private static final long NOON = 6000L;

    private final JavaPlugin plugin;

    public WorldClimateListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        if (!enabled()) {
            return;
        }
        World world = event.getWorld();
        YapSched.global(plugin, () -> applyWorld(world));
    }

    public void applyAll() {
        if (!enabled()) {
            return;
        }
        YapSched.global(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                try {
                    applyWorld(world);
                } catch (Throwable t) {
                    plugin.getLogger().warning("climate " + world.getName() + ": " + t.getMessage());
                }
            }
        });
    }

    boolean enabled() {
        FileConfiguration c = plugin.getConfig();
        if (c.isSet("climate.enabled")) {
            return c.getBoolean("climate.enabled");
        }
        return "creative".equalsIgnoreCase(resolveServerId());
    }

    private String resolveServerId() {
        String yaml = plugin.getConfig().getString("server-id", "");
        String hint = readInstanceServerId();
        if (hint != null && !hint.isBlank()) {
            if (yaml == null || yaml.isBlank() || "default".equalsIgnoreCase(yaml)
                    || "lobby".equalsIgnoreCase(yaml)) {
                return hint;
            }
        }
        if (yaml != null && !yaml.isBlank()) {
            return yaml;
        }
        return hint == null ? "" : hint;
    }

    private String readInstanceServerId() {
        try {
            Path data = plugin.getDataFolder().toPath();
            Path hint = data.getParent() != null && data.getParent().getParent() != null
                    ? data.getParent().getParent().resolve("yap-server-id.txt")
                    : null;
            if (hint != null && Files.isRegularFile(hint)) {
                String line = Files.readString(hint).trim();
                int nl = line.indexOf('\n');
                return nl < 0 ? line.trim() : line.substring(0, nl).trim();
            }
        } catch (Exception ignored) {
            // YAML server-id
        }
        return null;
    }

    private void applyWorld(World world) {
        if (world == null) {
            return;
        }
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.SPAWN_PATROLS, false);
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        world.setGameRule(GameRules.SPAWN_WARDENS, false);
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        world.setFullTime(NOON);
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE / 4);
    }
}
