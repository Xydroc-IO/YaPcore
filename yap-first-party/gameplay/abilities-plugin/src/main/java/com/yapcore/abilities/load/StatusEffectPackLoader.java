package com.yapcore.abilities.load;

import com.yapcore.abilities.AbilityEffect;
import com.yapcore.abilities.StatModifiers;
import com.yapcore.abilities.StatusEffectDefinition;
import com.yapcore.abilities.StatusKind;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class StatusEffectPackLoader {

    private static final Logger LOG = Logger.getLogger("YaPAbilities");

    private Map<String, StatusEffectDefinition> effects = Map.of();

    public void loadDirectory(Path dir) {
        Map<String, StatusEffectDefinition> loaded = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) {
            effects = Map.copyOf(loaded);
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .forEach(file -> loadFile(file, loaded));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list effects dir " + dir, e);
        }
        effects = Map.copyOf(loaded);
        LOG.info("Loaded " + effects.size() + " status effects from " + dir);
    }

    private static void loadFile(Path file, Map<String, StatusEffectDefinition> loaded) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection root = yaml.getConfigurationSection("effects");
            if (root == null) {
                return;
            }
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                loaded.put(id, parseEffect(id, s));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load status pack " + file, e);
        }
    }

    private static StatusEffectDefinition parseEffect(String id, ConfigurationSection s) {
        ConfigurationSection mods = s.getConfigurationSection("modifiers");
        StatModifiers modifiers = readModifiers(mods);
        List<AbilityEffect> tick = EffectParser.parseSectionList(s, "tick");
        List<AbilityEffect> expire = EffectParser.parseSectionList(s, "expire");
        return new StatusEffectDefinition(
                id,
                s.getString("name", id),
                StatusKind.parse(s.getString("kind")),
                s.getInt("max-stacks", 1),
                s.getInt("duration-ticks", 100),
                s.getString("group", ""),
                modifiers,
                tick,
                s.getInt("tick-interval", 20),
                expire);
    }

    private static StatModifiers readModifiers(ConfigurationSection s) {
        if (s == null) {
            return StatModifiers.empty();
        }
        return new StatModifiers(
                s.getInt("attack", 0),
                s.getInt("strength", 0),
                s.getInt("defence", 0),
                s.getInt("ranged", 0),
                s.getInt("magic", 0),
                s.getDouble("speed", 1.0),
                s.getDouble("damage-taken", 1.0));
    }

    public Map<String, StatusEffectDefinition> effects() {
        return effects;
    }

    public StatusEffectDefinition get(String id) {
        return effects.get(id);
    }
}
