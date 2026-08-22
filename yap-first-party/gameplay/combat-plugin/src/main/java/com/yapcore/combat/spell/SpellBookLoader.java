package com.yapcore.combat.spell;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SpellBookLoader {

    private static final Logger LOG = Logger.getLogger("YaPCombat");

    private Map<String, SpellDefinition> spells = Map.of();

    public void load(Path file) {
        Map<String, SpellDefinition> loaded = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            spells = Map.copyOf(loaded);
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection root = yaml.getConfigurationSection("spells");
            if (root == null) {
                spells = Map.copyOf(loaded);
                return;
            }
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                loaded.put(id, new SpellDefinition(
                        id,
                        s.getString("name", id),
                        Math.max(1, s.getInt("min-magic-level", 1)),
                        Math.max(0, s.getInt("prayer-cost", 0)),
                        Math.max(1, s.getInt("max-hit", 4)),
                        s.getDouble("cast-xp", 10),
                        s.getDouble("damage-xp-multiplier", 2.0),
                        readRunes(s.getConfigurationSection("runes")),
                        parseMaterial(s.getString("required-staff")),
                        s.getString("target-filter"),
                        s.getString("applies-effect"),
                        Math.max(1, s.getInt("effect-stacks", 1))));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load spell book " + file, e);
        }
        spells = Map.copyOf(loaded);
    }

    public Map<String, SpellDefinition> spells() {
        return spells;
    }

    public SpellDefinition get(String id) {
        return spells.get(id);
    }

    private static Map<Material, Integer> readRunes(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<Material, Integer> runes = new EnumMap<>(Material.class);
        for (String key : section.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) {
                continue;
            }
            int count = Math.max(1, section.getInt(key, 1));
            runes.put(mat, count);
        }
        return Map.copyOf(runes);
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Material.matchMaterial(raw);
    }
}
