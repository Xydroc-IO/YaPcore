package com.yapcore.dungeons.gate;

import com.yapcore.dungeons.DungeonGate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Loads per-level skill gates from gates.yml. */
public final class GateTable {

    private final Map<Integer, DungeonGate> byLevel = new HashMap<>();

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
            byLevel.put(level, new DungeonGate(
                    sec.getInt("overall", 10),
                    sec.getInt("mining", 0),
                    sec.getInt("strength", 0)));
        }
        plugin.getLogger().info("Loaded " + byLevel.size() + " dungeon gate levels");
    }

    public DungeonGate gate(int level) {
        return byLevel.getOrDefault(level, new DungeonGate(10, 0, 0));
    }
}
