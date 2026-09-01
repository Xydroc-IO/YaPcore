package com.yapcore.abilities.load;

import com.yapcore.abilities.AbilityCategory;
import com.yapcore.abilities.AbilityCosts;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityEffect;
import com.yapcore.abilities.ProjectileSpec;
import com.yapcore.abilities.TargetMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class AbilityPackLoader {

    private static final Logger LOG = Logger.getLogger("YaPAbilities");

    private Map<String, AbilityDefinition> abilities = Map.of();

    public void loadDirectory(Path dir) {
        Map<String, AbilityDefinition> loaded = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) {
            abilities = Map.copyOf(loaded);
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .forEach(file -> loadFile(file, loaded));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list abilities dir " + dir, e);
        }
        abilities = Map.copyOf(loaded);
        LOG.info("Loaded " + abilities.size() + " abilities from " + dir);
    }

    private static void loadFile(Path file, Map<String, AbilityDefinition> loaded) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection root = yaml.getConfigurationSection("abilities");
            if (root == null) {
                return;
            }
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                loaded.put(id, parseAbility(id, s));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load ability pack " + file, e);
        }
    }

    private static AbilityDefinition parseAbility(String id, ConfigurationSection s) {
        Map<String, Integer> minLevels = readMinLevels(s.getConfigurationSection("min-level"));
        AbilityCosts costs = readCosts(s.getConfigurationSection("costs"), s);
        TargetMode target = TargetMode.parse(s.getString("target", "raycast"));
        List<AbilityEffect> cast = EffectParser.parseSectionList(s, "cast");
        List<AbilityEffect> hit = EffectParser.parseSectionList(s, "on-hit");
        ProjectileSpec projectile = readProjectile(s.getConfigurationSection("projectile"));
        List<com.yapcore.abilities.CastCondition> conditions = readConditions(s.getMapList("conditions"));
        return new AbilityDefinition(
                id,
                s.getString("name", id),
                AbilityCategory.parse(s.getString("category")),
                minLevels,
                costs,
                Math.max(0, s.getInt("cooldown", s.getInt("cooldown-ticks", 0))),
                s.getDouble("range", 20.0),
                target,
                s.getString("target-filter", ""),
                conditions,
                cast,
                hit,
                projectile,
                s.getInt("icon-cmd", s.getInt("icon_cmd", 0)));
    }

    private static List<com.yapcore.abilities.CastCondition> readConditions(List<Map<?, ?>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<com.yapcore.abilities.CastCondition> out = new java.util.ArrayList<>();
        for (Map<?, ?> map : raw) {
            Object typeObj = map.get("type");
            com.yapcore.abilities.ConditionKind kind = com.yapcore.abilities.ConditionKind.parse(
                    typeObj == null ? null : String.valueOf(typeObj));
            Map<String, String> params = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if ("type".equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                    continue;
                }
                if (entry.getValue() != null) {
                    params.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            out.add(new com.yapcore.abilities.CastCondition(kind, params));
        }
        return List.copyOf(out);
    }

    private static Map<String, Integer> readMinLevels(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            out.put(key.toLowerCase(), Math.max(1, section.getInt(key, 1)));
        }
        return Map.copyOf(out);
    }

    private static AbilityCosts readCosts(ConfigurationSection costs, ConfigurationSection ability) {
        int prayer = 0;
        Map<Material, Integer> runes = Map.of();
        Material staff = null;
        if (costs != null) {
            prayer = Math.max(0, costs.getInt("prayer", 0));
            runes = readRunes(costs.getConfigurationSection("runes"));
            staff = parseMaterial(costs.getString("required-staff"));
        }
        if (staff == null) {
            staff = parseMaterial(ability.getString("required-staff"));
        }
        return new AbilityCosts(prayer, runes, staff);
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
            runes.put(mat, Math.max(1, section.getInt(key, 1)));
        }
        return Map.copyOf(runes);
    }

    private static ProjectileSpec readProjectile(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        ConfigurationSection trail = section.getConfigurationSection("trail");
        String trailParticle = "";
        int trailCount = 0;
        int trailInterval = 2;
        if (trail != null) {
            trailParticle = trail.getString("particle", "");
            trailCount = trail.getInt("count", 2);
            trailInterval = trail.getInt("interval", 2);
        }
        return new ProjectileSpec(
                section.getString("entity", "SNOWBALL"),
                section.getDouble("speed", 1.2),
                section.getInt("max-ticks", 40),
                trailParticle,
                trailCount,
                trailInterval,
                section.getBoolean("homing", false),
                section.getDouble("turn-rate", 0.15),
                section.getDouble("splash-radius", section.getDouble("splash", 0)),
                section.getInt("icon-cmd", 0),
                section.getBoolean("hide", section.getBoolean("hide-entity", true)),
                (float) section.getDouble("scale", section.getDouble("display-scale", 0.85)));
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Material.matchMaterial(raw);
    }

    public Map<String, AbilityDefinition> abilities() {
        return abilities;
    }

    public AbilityDefinition get(String id) {
        return abilities.get(id);
    }
}
