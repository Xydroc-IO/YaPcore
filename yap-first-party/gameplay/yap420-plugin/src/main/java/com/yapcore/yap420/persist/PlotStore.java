package com.yapcore.yap420.persist;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.plant.PlotRegistry;
import com.yapcore.yap420.plant.PlotState;
import com.yapcore.yap420.plant.StrainId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;
import java.util.logging.Level;

/** HEAVY persistence for plant plots. */
public final class PlotStore {

    private final JavaPlugin plugin;
    private final PlotRegistry registry;
    private final File file;

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
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            try {
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
                        sec.getString("world", "world"),
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
        plugin.getLogger().info("Loaded " + registry.size() + " YaP420 plots");
    }

    public void saveSync() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlotState plot : registry.all()) {
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

    public void saveAsync() {
        YapSched.async(plugin, this::saveSync);
    }
}
