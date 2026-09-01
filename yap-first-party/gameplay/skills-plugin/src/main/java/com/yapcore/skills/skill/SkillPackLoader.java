package com.yapcore.skills.skill;

import com.yapcore.mmo.SkillDefinition;
import com.yapcore.mmo.SkillId;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SkillPackLoader {

    private static final Logger LOG = Logger.getLogger(SkillPackLoader.class.getName());

    private final Path skillsDir;
    private Map<SkillId, SkillDefinition> skills = Map.of();

    public SkillPackLoader(Path skillsDir) {
        this.skillsDir = skillsDir;
    }

    public void reload() {
        Map<SkillId, SkillDefinition> loaded = new HashMap<>();
        if (!Files.isDirectory(skillsDir)) {
            skills = Map.copyOf(loaded);
            return;
        }
        try (var stream = Files.list(skillsDir)) {
            List<Path> files = stream
                    .filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
            for (Path path : files) {
                parseFile(path).ifPresent(def -> loaded.put(def.id(), def));
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list skills in " + skillsDir, e);
        }
        skills = Map.copyOf(loaded);
    }

    public Map<SkillId, SkillDefinition> skills() {
        return skills;
    }

    public SkillDefinition get(SkillId id) {
        return skills.get(id);
    }

    private java.util.Optional<SkillDefinition> parseFile(Path path) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
            String idRaw = yaml.getString("id");
            if (idRaw == null || idRaw.isBlank()) {
                idRaw = path.getFileName().toString().replace(".yml", "");
            }
            SkillId id = SkillId.of(idRaw);
            String display = yaml.getString("display", id.id());
            boolean enabled = yaml.getBoolean("enabled", true);
            Material icon = Material.matchMaterial(yaml.getString("icon", "CLAY_BALL"));
            if (icon == null) {
                icon = Material.CLAY_BALL;
            }
            int iconCmd = yaml.getInt("icon-cmd", yaml.getInt("icon_cmd", 0));
            Map<Material, SkillDefinition.BreakAction> breakActions = parseBreak(yaml.getConfigurationSection("break"));
            Map<String, SkillDefinition.FishAction> fishActions = parseFish(yaml.getConfigurationSection("fish"));
            Map<Material, SkillDefinition.SmeltAction> smeltActions = parseSmelt(yaml.getConfigurationSection("smelt"));
            SkillDefinition.CombatDealtAction combatDealt = parseCombatDealt(yaml.getConfigurationSection("combat-dealt"));
            SkillDefinition.CombatDealtAction rangedDealt = parseCombatDealt(yaml.getConfigurationSection("ranged-dealt"));
            SkillDefinition.CombatDealtAction magicDealt = parseCombatDealt(yaml.getConfigurationSection("magic-dealt"));
            SkillDefinition.CombatTakenAction combatTaken = parseCombatTaken(yaml.getConfigurationSection("combat-taken"));
            SkillDefinition.HitpointsRatio hitpointsRatio = parseHitpoints(yaml.getConfigurationSection("combat-hitpoints"));
            SkillDefinition.PrayerDrainAction prayerDrain = parsePrayerDrain(yaml.getConfigurationSection("prayer-drain"));
            return java.util.Optional.of(new SkillDefinition(
                    id, display, icon, iconCmd, enabled, breakActions, fishActions, smeltActions,
                    combatDealt, rangedDealt, magicDealt, combatTaken, hitpointsRatio, prayerDrain));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load skill " + path.getFileName(), e);
            return java.util.Optional.empty();
        }
    }

    private static Map<Material, SkillDefinition.BreakAction> parseBreak(ConfigurationSection section) {
        Map<Material, SkillDefinition.BreakAction> out = new EnumMap<>(Material.class);
        if (section == null) {
            return Map.copyOf(out);
        }
        for (String key : section.getKeys(false)) {
            Material mat = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (mat == null) {
                continue;
            }
            ConfigurationSection row = section.getConfigurationSection(key);
            double xp;
            int minLevel = 1;
            if (row != null) {
                xp = row.getDouble("xp", 0);
                minLevel = row.getInt("min-level", 1);
            } else {
                xp = section.getDouble(key, 0);
            }
            if (xp > 0) {
                out.put(mat, new SkillDefinition.BreakAction(xp, minLevel));
            }
        }
        return Map.copyOf(out);
    }

    private static Map<String, SkillDefinition.FishAction> parseFish(ConfigurationSection section) {
        Map<String, SkillDefinition.FishAction> out = new HashMap<>();
        if (section == null) {
            return Map.copyOf(out);
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            double xp;
            int minLevel = 1;
            if (row != null) {
                xp = row.getDouble("xp", 0);
                minLevel = row.getInt("min-level", 1);
            } else {
                xp = section.getDouble(key, 0);
            }
            if (xp > 0) {
                out.put(key.toUpperCase(Locale.ROOT), new SkillDefinition.FishAction(xp, minLevel));
            }
        }
        return Map.copyOf(out);
    }

    private static Map<Material, SkillDefinition.SmeltAction> parseSmelt(ConfigurationSection section) {
        Map<Material, SkillDefinition.SmeltAction> out = new EnumMap<>(Material.class);
        if (section == null) {
            return Map.copyOf(out);
        }
        for (String key : section.getKeys(false)) {
            Material mat = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
            if (mat == null) {
                continue;
            }
            ConfigurationSection row = section.getConfigurationSection(key);
            double xp;
            int minLevel = 1;
            if (row != null) {
                xp = row.getDouble("xp", 0);
                minLevel = row.getInt("min-level", 1);
            } else {
                xp = section.getDouble(key, 0);
            }
            if (xp > 0) {
                out.put(mat, new SkillDefinition.SmeltAction(xp, minLevel));
            }
        }
        return Map.copyOf(out);
    }

    private static SkillDefinition.CombatDealtAction parseCombatDealt(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        double xpPerDamage = section.getDouble("xp-per-damage", 0);
        if (xpPerDamage <= 0) {
            return null;
        }
        double share = section.getDouble("share", 1.0);
        return new SkillDefinition.CombatDealtAction(xpPerDamage, share);
    }

    private static SkillDefinition.CombatTakenAction parseCombatTaken(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        double xpPerDamage = section.getDouble("xp-per-damage", 0);
        if (xpPerDamage <= 0) {
            return null;
        }
        return new SkillDefinition.CombatTakenAction(xpPerDamage);
    }

    private static SkillDefinition.HitpointsRatio parseHitpoints(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        double ratio = section.getDouble("ratio-of-combat-xp", 0);
        if (ratio <= 0) {
            return null;
        }
        return new SkillDefinition.HitpointsRatio(ratio);
    }

    private static SkillDefinition.PrayerDrainAction parsePrayerDrain(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        double xpPerPoint = section.getDouble("xp-per-point", 0);
        if (xpPerPoint <= 0) {
            return null;
        }
        return new SkillDefinition.PrayerDrainAction(xpPerPoint);
    }
}
