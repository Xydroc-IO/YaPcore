package com.yapcore.combat.prayer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PrayerBookLoader {

    private static final Logger LOG = Logger.getLogger("YaPCombat");

    private Map<String, PrayerDefinition> prayers = Map.of();

    public void load(Path file) {
        Map<String, PrayerDefinition> loaded = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            prayers = Map.copyOf(loaded);
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection root = yaml.getConfigurationSection("prayers");
            if (root == null) {
                prayers = Map.copyOf(loaded);
                return;
            }
            for (String id : root.getKeys(false)) {
                ConfigurationSection p = root.getConfigurationSection(id);
                if (p == null) {
                    continue;
                }
                PrayerEffectType effectType = parseEffect(p.getString("effect", "defence_boost"));
                loaded.put(id, new PrayerDefinition(
                        id,
                        p.getString("name", id),
                        Math.max(1, p.getInt("min-prayer-level", 1)),
                        Math.max(1, p.getInt("drain-per-tick", 1)),
                        p.getString("group", effectType.name().toLowerCase(Locale.ROOT)),
                        effectType,
                        Math.max(0, p.getInt("boost", 0)),
                        Math.max(0, Math.min(1, p.getDouble("reduction", 0)))));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load prayers " + file, e);
        }
        prayers = Map.copyOf(loaded);
    }

    public Map<String, PrayerDefinition> prayers() {
        return prayers;
    }

    public PrayerDefinition get(String id) {
        return prayers.get(id);
    }

    private static PrayerEffectType parseEffect(String raw) {
        if (raw == null || raw.isBlank()) {
            return PrayerEffectType.DEFENCE_BOOST;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "attack_boost", "attack" -> PrayerEffectType.ATTACK_BOOST;
            case "strength_boost", "strength" -> PrayerEffectType.STRENGTH_BOOST;
            case "defence_boost", "defence", "defense_boost" -> PrayerEffectType.DEFENCE_BOOST;
            case "ranged_boost", "ranged" -> PrayerEffectType.RANGED_BOOST;
            case "magic_boost", "magic" -> PrayerEffectType.MAGIC_BOOST;
            case "protect_melee", "melee" -> PrayerEffectType.PROTECT_MELEE;
            case "protect_missiles", "missiles", "ranged_protect" -> PrayerEffectType.PROTECT_MISSILES;
            case "protect_magic", "magic_protect" -> PrayerEffectType.PROTECT_MAGIC;
            default -> PrayerEffectType.DEFENCE_BOOST;
        };
    }
}
