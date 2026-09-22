package com.yapcore.yap420.press;

import com.yapcore.sched.YapSched;
import com.yapcore.yap420.persist.StoreIo;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;
import java.util.logging.Level;

/** HEAVY persistence for packaging presses. */
public final class PressStore {

    private final JavaPlugin plugin;
    private final PressRegistry registry;
    private final File file;

    public PressStore(JavaPlugin plugin, PressRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.file = new File(plugin.getDataFolder(), "presses.yml");
    }

    public void loadSync() {
        registry.clear();
        YamlConfiguration yaml = StoreIo.load(file);
        ConfigurationSection root = yaml.getConfigurationSection("presses");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            try {
                UUID entity = null;
                String entityRaw = sec.getString("entity");
                if (entityRaw != null && !entityRaw.isBlank()) {
                    entity = UUID.fromString(entityRaw);
                }
                registry.put(new PressState(
                        sec.getString("world", "world"),
                        sec.getInt("x"),
                        sec.getInt("y"),
                        sec.getInt("z"),
                        entity));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Bad press entry " + key, e);
            }
        }
        plugin.getLogger().info("Loaded " + registry.size() + " YaP420 packaging presses");
    }

    public void saveSync() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PressState press : registry.all()) {
            String path = "presses." + press.key();
            yaml.set(path + ".world", press.world());
            yaml.set(path + ".x", press.x());
            yaml.set(path + ".y", press.y());
            yaml.set(path + ".z", press.z());
            if (press.entityUuid() != null) {
                yaml.set(path + ".entity", press.entityUuid().toString());
            }
        }
        StoreIo.saveAtomic(plugin, file, yaml);
    }

    public void saveAsync() {
        YapSched.async(plugin, this::saveSync);
    }
}
