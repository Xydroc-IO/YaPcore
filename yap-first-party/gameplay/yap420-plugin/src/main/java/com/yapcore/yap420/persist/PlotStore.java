package com.yapcore.yap420.persist;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.plant.PlotRegistry;
import com.yapcore.yap420.plant.PlotState;
import com.yapcore.yap420.plant.StrainId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** HEAVY persistence for plant plots. */
public final class PlotStore {

    private final JavaPlugin plugin;
    private final PlotRegistry registry;
    private final File file;
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final Object saveLock = new Object();

    public PlotStore(JavaPlugin plugin, PlotRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.file = new File(plugin.getDataFolder(), "plots.yml");
    }

    public void loadSync() {
        registry.clear();
        YamlConfiguration yaml = StoreIo.load(file);
        ConfigurationSection root = yaml.getConfigurationSection("plots");
        if (root == null) {
            return;
        }
        int skippedEphemeral = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            try {
                String world = sec.getString("world", "world");
                if (isEphemeralWorld(world)) {
                    skippedEphemeral++;
                    continue;
                }
                StrainId strain = StrainId.parse(sec.getString("strain")).orElse(null);
                if (strain == null) {
                    continue;
                }
                UUID entity = null;
                String entityRaw = sec.getString("entity");
                if (entityRaw != null && !entityRaw.isBlank()) {
                    entity = UUID.fromString(entityRaw);
                }
                PlotState plot = new PlotState(
                        world,
                        sec.getInt("x"),
                        sec.getInt("y"),
                        sec.getInt("z"),
                        strain,
                        sec.getInt("stage", 0),
                        sec.getLong("planted-at", System.currentTimeMillis()),
                        entity);
                registry.put(plot);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Bad plot entry " + key, e);
            }
        }
        plugin.getLogger().info("Loaded " + registry.size() + " YaP420 plots"
                + (skippedEphemeral > 0 ? " (dropped " + skippedEphemeral + " ephemeral-world)" : ""));
        if (skippedEphemeral > 0) {
            saveSync();
        }
    }

    public void saveSync() {
        synchronized (saveLock) {
            YamlConfiguration yaml = new YamlConfiguration();
            for (PlotState plot : registry.all()) {
                if (isEphemeralWorld(plot.world())) {
                    continue;
                }
                String path = "plots." + plot.key();
                yaml.set(path + ".world", plot.world());
                yaml.set(path + ".x", plot.x());
                yaml.set(path + ".y", plot.y());
                yaml.set(path + ".z", plot.z());
                yaml.set(path + ".strain", plot.strain().id());
                yaml.set(path + ".stage", plot.stage());
                yaml.set(path + ".planted-at", plot.plantedAtMs());
                if (plot.entityUuid() != null) {
                    yaml.set(path + ".entity", plot.entityUuid().toString());
                }
            }
            StoreIo.saveAtomic(plugin, file, yaml);
        }
    }

    /** Coalesce bursty callers (growth / wild spawn) into one async write. */
    public void saveAsync() {
        if (!saveQueued.compareAndSet(false, true)) {
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                saveSync();
            } finally {
                saveQueued.set(false);
            }
        });
    }

    /** Drop in-memory plots that belong to deleted dungeon / temp worlds. */
    public int purgeEphemeral() {
        List<PlotState> drop = new ArrayList<>();
        for (PlotState plot : registry.all()) {
            if (isEphemeralWorld(plot.world())) {
                drop.add(plot);
            }
        }
        for (PlotState plot : drop) {
            registry.remove(plot.world(), plot.x(), plot.y(), plot.z());
        }
        if (!drop.isEmpty()) {
            saveAsync();
        }
        return drop.size();
    }

    public static boolean isEphemeralWorld(String world) {
        if (world == null || world.isBlank()) {
            return false;
        }
        String n = world.toLowerCase(Locale.ROOT);
        return n.startsWith("yd_") || n.startsWith("yap_tmp_") || n.startsWith("tmp_");
    }
}
