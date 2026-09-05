package com.yapcore.dungeons.loot;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LootTable {

    public record Entry(Material material, int amount, int weight, boolean named) {
    }

    public record LevelLoot(int economy, List<Entry> guaranteed, List<Entry> rare) {
    }

    private final Map<Integer, LevelLoot> byLevel = new HashMap<>();

    public void reload(JavaPlugin plugin, File file) {
        byLevel.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection levels = yaml.getConfigurationSection("levels");
        if (levels == null) {
            return;
        }
        for (String key : levels.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            ConfigurationSection sec = levels.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            byLevel.put(level, new LevelLoot(
                    sec.getInt("economy", 50),
                    parseList(sec, "guaranteed"),
                    parseList(sec, "rare")));
        }
        plugin.getLogger().info("Loaded " + byLevel.size() + " loot levels");
    }

    private static List<Entry> parseList(ConfigurationSection parent, String path) {
        List<Entry> out = new ArrayList<>();
        List<Map<?, ?>> raw = parent.getMapList(path);
        for (Map<?, ?> map : raw) {
            Material mat = mat(String.valueOf(map.get("material")), Material.IRON_INGOT);
            int amount = map.get("amount") instanceof Number n ? n.intValue() : 1;
            int weight = map.get("weight") instanceof Number n ? n.intValue() : 10;
            Object namedObj = map.get("named");
            boolean named = namedObj != null && Boolean.parseBoolean(String.valueOf(namedObj));
            out.add(new Entry(mat, Math.max(1, amount), Math.max(1, weight), named));
        }
        return List.copyOf(out);
    }

    private static Material mat(String raw, Material fb) {
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return fb;
        }
    }

    public LevelLoot get(int level) {
        return byLevel.getOrDefault(level, new LevelLoot(50,
                List.of(new Entry(Material.IRON_INGOT, 2, 100, false)),
                List.of(new Entry(Material.GOLDEN_APPLE, 1, 20, true))));
    }
}
