package com.yapcore.mmocontent.area;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SkillAreaLoader {

    private static final Logger LOG = Logger.getLogger("YaPMmoContent");

    private Map<String, SkillAreaDefinition> areas = Map.of();

    public void load(Path file) {
        Map<String, SkillAreaDefinition> loaded = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            areas = Map.copyOf(loaded);
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection root = yaml.getConfigurationSection("areas");
            if (root == null) {
                areas = Map.copyOf(loaded);
                return;
            }
            for (String id : root.getKeys(false)) {
                ConfigurationSection a = root.getConfigurationSection(id);
                if (a == null) {
                    continue;
                }
                SkillAreaDefinition.Type type = SkillAreaDefinition.Type.valueOf(
                        a.getString("type", "MINING_GUILD").toUpperCase(Locale.ROOT));
                List<SkillAreaDefinition.OreNode> nodes = new ArrayList<>();
                for (Map<?, ?> raw : a.getMapList("nodes")) {
                    Material ore = Material.IRON_ORE;
                    Object oreRaw = raw.get("ore");
                    if (oreRaw != null) {
                        Material matched = Material.matchMaterial(String.valueOf(oreRaw));
                        if (matched != null) {
                            ore = matched;
                        }
                    }
                    nodes.add(new SkillAreaDefinition.OreNode(
                            intVal(raw, "x", 0),
                            intVal(raw, "y", 64),
                            intVal(raw, "z", 0),
                            ore));
                }
                loaded.put(id, new SkillAreaDefinition(
                        id,
                        type,
                        a.getString("world", "world"),
                        a.getInt("min-x", 0),
                        a.getInt("min-y", 0),
                        a.getInt("min-z", 0),
                        a.getInt("max-x", 0),
                        a.getInt("max-y", 255),
                        a.getInt("max-z", 0),
                        Math.max(5, a.getInt("respawn-seconds", 30)),
                        Math.max(1.0, a.getDouble("xp-multiplier", 1.0)),
                        List.copyOf(nodes)));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load skill areas from " + file, e);
        }
        areas = Map.copyOf(loaded);
    }

    public Map<String, SkillAreaDefinition> areas() {
        return areas;
    }

    public SkillAreaDefinition get(String id) {
        return areas.get(id);
    }

    private static int intVal(Map<?, ?> raw, String key, int fallback) {
        Object v = raw.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }
}
