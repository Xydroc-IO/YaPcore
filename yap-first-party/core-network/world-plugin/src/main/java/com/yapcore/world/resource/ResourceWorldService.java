package com.yapcore.world.resource;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import com.yapcore.world.ResourceWorldSpec;
import com.yapcore.world.WorldConfig;
import com.yapcore.world.WorldCreateOptions;
import com.yapcore.world.service.WorldManagerServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Scheduled full wipe + recreate for configured resource worlds (bases stay on main).
 */
public final class ResourceWorldService {

    private final JavaPlugin plugin;
    private final WorldManagerServiceImpl worlds;
    private volatile WorldConfig config;
    private final Map<String, Long> lastResetMs = new ConcurrentHashMap<>();
    private final Map<String, Integer> announcedFor = new ConcurrentHashMap<>();
    private final AtomicBoolean resetting = new AtomicBoolean(false);
    private YapTask tickTask;
    private final File stateFile;

    public ResourceWorldService(JavaPlugin plugin, WorldManagerServiceImpl worlds, WorldConfig config) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.config = config;
        this.stateFile = new File(plugin.getDataFolder(), "resource-world-state.yml");
        loadState();
    }

    public void setConfig(WorldConfig config) {
        this.config = config;
    }

    public void start() {
        stop();
        tickTask = YapSched.globalTimer(plugin, this::tick, 20L * 30L, 20L * 30L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        saveState();
    }

    public boolean resetNow(CommandSender sender, String worldName) {
        var specOpt = config.resourceWorld(worldName);
        if (specOpt.isEmpty()) {
            sender.sendMessage("§cUnknown resource world §f" + worldName
                    + "§c — add it under resource.worlds in YaPWorld config.");
            return false;
        }
        ResourceWorldSpec spec = specOpt.get();
        if (!resetting.compareAndSet(false, true)) {
            sender.sendMessage("§cA resource reset is already running.");
            return false;
        }
        sender.sendMessage("§eResetting resource world §f" + spec.name() + "§e…");
        Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                "Resource world " + spec.name() + " is resetting — you will be moved if inside.",
                net.kyori.adventure.text.format.NamedTextColor.GOLD));
        runReset(spec);
        return true;
    }

    private void tick() {
        WorldConfig cfg = config;
        if (cfg == null || cfg.resourceWorlds().isEmpty() || resetting.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ResourceWorldSpec spec : cfg.resourceWorlds().values()) {
            if (spec.resetIntervalHours() <= 0) {
                continue;
            }
            String key = spec.name().toLowerCase(Locale.ROOT);
            long intervalMs = spec.resetIntervalHours() * 3_600_000L;
            if (!lastResetMs.containsKey(key)) {
                lastResetMs.put(key, now);
                continue;
            }
            long last = lastResetMs.get(key);
            long dueAt = last + intervalMs;
            long remainingMs = dueAt - now;
            if (remainingMs <= 0) {
                if (!resetting.compareAndSet(false, true)) {
                    return;
                }
                Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                        "Resource world " + spec.name() + " is resetting now.",
                        net.kyori.adventure.text.format.NamedTextColor.GOLD));
                runReset(spec);
                announcedFor.remove(key);
                return;
            }
            int remainingMin = (int) Math.ceil(remainingMs / 60_000.0);
            for (int announce : spec.announceMinutes()) {
                if (remainingMin == announce) {
                    int prev = announcedFor.getOrDefault(key, -1);
                    if (prev != announce) {
                        announcedFor.put(key, announce);
                        Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                                "Resource world " + spec.name() + " resets in " + announce + " minute(s).",
                                net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                    }
                    break;
                }
            }
        }
    }

    private void runReset(ResourceWorldSpec spec) {
        String name = spec.name();
        String key = name.toLowerCase(Locale.ROOT);
        WorldCreateOptions opts = spec.createOptions();
        worlds.deleteWorld(name).thenCompose(deleted -> {
            if (!Boolean.TRUE.equals(deleted)) {
                plugin.getLogger().warning("Resource reset: could not delete world " + name);
                return java.util.concurrent.CompletableFuture.completedFuture(false);
            }
            return worlds.createWorld(name, opts);
        }).whenComplete((created, err) -> YapSched.global(plugin, () -> {
            resetting.set(false);
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Resource world reset failed: " + name, err);
                return;
            }
            if (!Boolean.TRUE.equals(created)) {
                plugin.getLogger().warning("Resource reset: could not recreate world " + name);
                return;
            }
            lastResetMs.put(key, System.currentTimeMillis());
            saveState();
            plugin.getLogger().info("Resource world reset complete: " + name);
            Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                    "Resource world " + name + " is ready again.",
                    net.kyori.adventure.text.format.NamedTextColor.GREEN));
        }));
    }

    private void loadState() {
        if (!stateFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        var section = yaml.getConfigurationSection("last-reset");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            lastResetMs.put(key.toLowerCase(Locale.ROOT), section.getLong(key));
        }
    }

    private void saveState() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var e : lastResetMs.entrySet()) {
            yaml.set("last-reset." + e.getKey(), e.getValue());
        }
        try {
            File parent = stateFile.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            yaml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.FINE, "resource-world-state save", e);
        }
    }
}
