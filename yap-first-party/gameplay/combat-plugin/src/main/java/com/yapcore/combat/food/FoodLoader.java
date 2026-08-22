package com.yapcore.combat.food;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class FoodLoader {

    public record FoodDef(int heal, int restorePrayer) {
    }

    private Map<Material, FoodDef> foods = Map.of();

    public void reload(JavaPlugin plugin, Path foodFile) throws IOException {
        if (!Files.exists(foodFile)) {
            plugin.saveResource("food.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(foodFile.toFile());
        Map<Material, FoodDef> loaded = new EnumMap<>(Material.class);
        var section = yaml.getConfigurationSection("items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat == null) {
                    plugin.getLogger().warning("Unknown food material: " + key);
                    continue;
                }
                var entry = section.getConfigurationSection(key);
                int heal = 0;
                int restorePrayer = 0;
                if (entry != null) {
                    heal = entry.getInt("heal", 0);
                    restorePrayer = entry.getInt("restore-prayer", 0);
                } else {
                    heal = section.getInt(key, 0);
                }
                if (heal > 0 || restorePrayer > 0) {
                    loaded.put(mat, new FoodDef(heal, restorePrayer));
                }
            }
        }
        foods = Collections.unmodifiableMap(loaded);
    }

    public Map<Material, FoodDef> foods() {
        return foods;
    }
}
