package com.yapcore.yapblock.level;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

public final class BlockValueTable {

    private final Map<Material, Integer> values = new EnumMap<>(Material.class);

    public void reload(JavaPlugin plugin) {
        values.clear();
        File file = new File(plugin.getDataFolder(), "block-values.yml");
        if (!file.exists()) {
            plugin.saveResource("block-values.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("values");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key.toUpperCase());
                values.put(mat, Math.max(0, section.getInt(key, 0)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public int valueOf(Material material) {
        return values.getOrDefault(material, 0);
    }
}
