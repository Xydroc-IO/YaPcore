package com.yapcore.dungeons.gen;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class DifficultyTable {

    public record LevelDiff(
            int rooms,
            int mobsPerRoom,
            double hpMult,
            double damageMult,
            double eliteChance,
            double bossMaxHealth,
            double trapDamage) {
    }

    private final Map<Integer, LevelDiff> byLevel = new HashMap<>();

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
            byLevel.put(level, new LevelDiff(
                    sec.getInt("rooms", 6),
                    sec.getInt("mobs-per-room", 4),
                    sec.getDouble("hp-mult", 1.0),
                    sec.getDouble("damage-mult", 1.0),
                    sec.getDouble("elite-chance", 0.05),
                    sec.getDouble("boss-max-health", 60),
                    sec.getDouble("trap-damage", 4)));
        }
        plugin.getLogger().info("Loaded " + byLevel.size() + " difficulty levels");
    }

    public LevelDiff get(int level) {
        return byLevel.getOrDefault(level, new LevelDiff(6, 4, 1.0, 1.0, 0.05, 60, 4));
    }
}
