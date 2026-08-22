package com.yapcore.combat.status;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StatusEffectRegistry {

    private static final Logger LOG = Logger.getLogger("YaPCombat");

    private Map<String, StatusEffectDefinition> effects = Map.of();

    public void load(Path file) {
        Map<String, StatusEffectDefinition> loaded = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            effects = Map.copyOf(loaded);
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection root = yaml.getConfigurationSection("effects");
            if (root != null) {
                for (String id : root.getKeys(false)) {
                    ConfigurationSection section = root.getConfigurationSection(id);
                    if (section == null) {
                        continue;
                    }
                    StatusEffectDefinition def = parse(id, section);
                    if (def != null) {
                        loaded.put(id, def);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load status effects from " + file, e);
        }
        effects = Map.copyOf(loaded);
    }

    public Map<String, StatusEffectDefinition> effects() {
        return effects;
    }

    public StatusEffectDefinition get(String id) {
        return effects.get(id);
    }

    static StatusEffectDefinition parse(String id, ConfigurationSection section) {
        try {
            StatusEffectKind kind = StatusEffectKind.valueOf(
                    section.getString("kind", "DEBUFF").toUpperCase());
            return new StatusEffectDefinition(
                    id,
                    section.getString("name", id),
                    kind,
                    section.getInt("duration-seconds", 6),
                    section.getInt("max-stacks", 3),
                    section.getInt("tick-interval-seconds", 2),
                    section.getInt("damage-per-tick", 0),
                    section.getInt("heal-per-tick", 0),
                    section.getInt("attack-modifier", 0),
                    section.getInt("strength-modifier", 0),
                    section.getInt("defence-modifier", 0),
                    section.getDouble("damage-taken-multiplier", 1.0),
                    section.getDouble("movement-scale", 1.0),
                    section.getBoolean("blocks-attacks", false));
        } catch (Exception e) {
            LOG.warning("Invalid status effect " + id + ": " + e.getMessage());
            return null;
        }
    }
}
