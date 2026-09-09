package com.yapcore.leveledmobs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LeveledMobsConfig {

    public enum Strategy {
        DISTANCE_FROM_SPAWN,
        RANDOM
    }

    private final JavaPlugin plugin;

    private boolean enabled;
    private Strategy strategy;
    private int minLevel;
    private int maxLevel;
    private double blocksPerLevel;
    private double healthPerLevel;
    private double damagePerLevel;
    private double outgoingDamagePerLevel;
    private double incomingDamageReductionPerLevel;
    private double xpPerLevel;
    private double dropQuantityPerLevel;
    private boolean nametagEnabled;
    private String nametagTemplate;
    private boolean nametagAlwaysVisible;
    private Set<String> worlds;
    private Set<EntityType> allowTypes;
    private Set<EntityType> denyTypes;
    private boolean skipCitizens;
    private boolean skipMythic;
    private boolean skipYapBosses;
    private boolean skipDungeon;
    private Set<CreatureSpawnEvent.SpawnReason> allowSpawnReasons;
    private Set<CreatureSpawnEvent.SpawnReason> skipSpawnReasons;

    public LeveledMobsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        var cfg = plugin.getConfig();
        enabled = cfg.getBoolean("enabled", true);
        strategy = parseStrategy(cfg.getString("strategy", "DISTANCE_FROM_SPAWN"));
        minLevel = Math.max(1, cfg.getInt("min-level", 1));
        maxLevel = Math.max(minLevel, cfg.getInt("max-level", 50));
        blocksPerLevel = Math.max(1.0, cfg.getDouble("blocks-per-level", 80.0));
        healthPerLevel = Math.max(0.0, cfg.getDouble("health-per-level", 0.08));
        damagePerLevel = Math.max(0.0, cfg.getDouble("damage-per-level", 0.06));
        outgoingDamagePerLevel = Math.max(0.0, cfg.getDouble("outgoing-damage-per-level", 0.04));
        incomingDamageReductionPerLevel = Math.max(0.0, cfg.getDouble("incoming-damage-reduction-per-level", 0.01));
        xpPerLevel = Math.max(0.0, cfg.getDouble("xp-per-level", 0.05));
        dropQuantityPerLevel = Math.max(0.0, cfg.getDouble("drop-quantity-per-level", 0.02));

        ConfigurationSection nametag = cfg.getConfigurationSection("nametag");
        nametagEnabled = nametag == null || nametag.getBoolean("enabled", true);
        nametagTemplate = nametag == null
                ? "&eLv.{level} &f{type}"
                : nametag.getString("template", "&eLv.{level} &f{type}");
        nametagAlwaysVisible = nametag == null || nametag.getBoolean("always-visible", true);

        worlds = toLowerSet(cfg.getStringList("worlds"));
        allowTypes = parseTypes(cfg.getStringList("allow-types"));
        denyTypes = parseTypes(cfg.getStringList("deny-types"));

        ConfigurationSection skip = cfg.getConfigurationSection("skip");
        skipCitizens = skip == null || skip.getBoolean("citizens", true);
        skipMythic = skip == null || skip.getBoolean("mythic-mobs", true);
        skipYapBosses = skip == null || skip.getBoolean("yap-bosses", true);
        skipDungeon = skip == null || skip.getBoolean("dungeon-mobs", true);

        allowSpawnReasons = parseReasons(cfg.getStringList("allow-spawn-reasons"));
        skipSpawnReasons = parseReasons(cfg.getStringList("skip-spawn-reasons"));
    }

    private static Strategy parseStrategy(String raw) {
        if (raw == null) {
            return Strategy.DISTANCE_FROM_SPAWN;
        }
        try {
            return Strategy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Strategy.DISTANCE_FROM_SPAWN;
        }
    }

    private static Set<String> toLowerSet(List<String> list) {
        if (list == null || list.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String s : list) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(out);
    }

    private static Set<EntityType> parseTypes(List<String> list) {
        if (list == null || list.isEmpty()) {
            return EnumSet.noneOf(EntityType.class);
        }
        EnumSet<EntityType> out = EnumSet.noneOf(EntityType.class);
        for (String s : list) {
            if (s == null || s.isBlank()) {
                continue;
            }
            try {
                out.add(EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // skip unknown
            }
        }
        return out;
    }

    private static Set<CreatureSpawnEvent.SpawnReason> parseReasons(List<String> list) {
        if (list == null || list.isEmpty()) {
            return EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        }
        EnumSet<CreatureSpawnEvent.SpawnReason> out = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        for (String s : list) {
            if (s == null || s.isBlank()) {
                continue;
            }
            try {
                out.add(CreatureSpawnEvent.SpawnReason.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // skip unknown
            }
        }
        return out;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
        plugin.getConfig().set("enabled", value);
        plugin.saveConfig();
    }

    public Strategy strategy() {
        return strategy;
    }

    public void setStrategy(Strategy value) {
        this.strategy = value == null ? Strategy.DISTANCE_FROM_SPAWN : value;
        plugin.getConfig().set("strategy", strategy.name());
        plugin.saveConfig();
    }

    public Strategy cycleStrategy() {
        Strategy next = strategy == Strategy.DISTANCE_FROM_SPAWN
                ? Strategy.RANDOM
                : Strategy.DISTANCE_FROM_SPAWN;
        setStrategy(next);
        return next;
    }

    public int minLevel() {
        return minLevel;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public void setMinLevel(int value) {
        minLevel = Math.max(1, value);
        if (maxLevel < minLevel) {
            maxLevel = minLevel;
            plugin.getConfig().set("max-level", maxLevel);
        }
        plugin.getConfig().set("min-level", minLevel);
        plugin.saveConfig();
    }

    public void setMaxLevel(int value) {
        maxLevel = Math.max(minLevel, value);
        plugin.getConfig().set("max-level", maxLevel);
        plugin.saveConfig();
    }

    public double blocksPerLevel() {
        return blocksPerLevel;
    }

    public void setBlocksPerLevel(double value) {
        blocksPerLevel = Math.max(1.0, value);
        plugin.getConfig().set("blocks-per-level", blocksPerLevel);
        plugin.saveConfig();
    }

    public void setNametagEnabled(boolean value) {
        nametagEnabled = value;
        plugin.getConfig().set("nametag.enabled", value);
        plugin.saveConfig();
    }

    public double healthPerLevel() {
        return healthPerLevel;
    }

    public double damagePerLevel() {
        return damagePerLevel;
    }

    public double outgoingDamagePerLevel() {
        return outgoingDamagePerLevel;
    }

    public double incomingDamageReductionPerLevel() {
        return incomingDamageReductionPerLevel;
    }

    public double xpPerLevel() {
        return xpPerLevel;
    }

    public double dropQuantityPerLevel() {
        return dropQuantityPerLevel;
    }

    public boolean nametagEnabled() {
        return nametagEnabled;
    }

    public String nametagTemplate() {
        return nametagTemplate;
    }

    public boolean nametagAlwaysVisible() {
        return nametagAlwaysVisible;
    }

    public boolean worldAllowed(String worldName) {
        if (worlds.isEmpty()) {
            return true;
        }
        return worlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public boolean typeAllowed(EntityType type) {
        if (denyTypes.contains(type)) {
            return false;
        }
        if (allowTypes.isEmpty()) {
            return true;
        }
        return allowTypes.contains(type);
    }

    public boolean skipCitizens() {
        return skipCitizens;
    }

    public boolean skipMythic() {
        return skipMythic;
    }

    public boolean skipYapBosses() {
        return skipYapBosses;
    }

    public boolean skipDungeon() {
        return skipDungeon;
    }

    public boolean spawnReasonAllowed(CreatureSpawnEvent.SpawnReason reason) {
        if (reason == null) {
            return true;
        }
        if (!skipSpawnReasons.isEmpty() && skipSpawnReasons.contains(reason)) {
            return false;
        }
        if (!allowSpawnReasons.isEmpty()) {
            return allowSpawnReasons.contains(reason);
        }
        return true;
    }

    public int clamp(int level) {
        return Math.max(minLevel, Math.min(maxLevel, level));
    }
}
