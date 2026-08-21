package com.yapcore.stacker;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/** Loads {@code config.yml}. */
public final class StackerConfig {

    public enum KillMode {
        DECREMENT,
        INSTANT
    }

    public record MobRule(
            boolean enabled,
            Integer maxStack,
            KillMode killMode,
            double lootMultiplier,
            double xpMultiplier,
            Boolean requireSameColor,
            Boolean preserveSlimeSize
    ) {
        public static MobRule defaults() {
            return new MobRule(true, null, null, 1.0, 1.0, null, null);
        }
    }

    private final JavaPlugin plugin;

    private boolean enabled = true;
    private Set<String> worlds = Set.of();
    private EnumSet<EntityType> whitelist = EnumSet.noneOf(EntityType.class);
    private EnumSet<EntityType> blacklist = EnumSet.noneOf(EntityType.class);

    private boolean mobsEnabled = true;
    private double mergeRadius = 5.0;
    private int maxStack = 100;
    private KillMode killMode = KillMode.DECREMENT;
    private String mobNametag = "<red>{type} <yellow>x{size}";
    private boolean skipNamed = true;
    private boolean skipTamed = true;
    private boolean skipLeashed = true;
    private boolean requireSameAge = true;
    private boolean requireSameSheepColor = true;
    private boolean requireSameSlimeSize = true;
    private boolean remergeOnChunkLoad = true;
    private long wanderMergeIntervalTicks = 100L;
    private int wanderMergeMaxPerTick = 64;

    private boolean itemsEnabled = true;
    private double itemMergeRadius = 2.5;
    private int itemMaxStack = 1000;
    private String itemNametag = "<aqua>{type} <yellow>x{size}";
    private boolean enhanceVanillaMerge = true;

    private boolean spawnersEnabled = true;
    private double spawnerMergeRadius = 3.0;
    private int spawnerMaxStack = 64;
    private boolean stackOnPlace = true;
    private boolean breakOne = true;

    private boolean killAuraEnabled = true;
    private double killAuraRadius = 4.0;
    private long killAuraIntervalTicks = 10L;
    private int killAuraKillsPerPulse = 1;
    private Material wandMaterial = Material.BLAZE_ROD;
    private Material toolMaterial = Material.GOLDEN_HOE;
    private Material auraMaterial = Material.NETHER_STAR;

    private boolean skipCitizens = true;
    private boolean skipMythicMobs = true;
    private boolean placeholderApi = true;
    private boolean metricsEnabled = true;

    private Map<EntityType, MobRule> mobRules = Map.of();

    public StackerConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);

        worlds = readStringSet(c.getStringList("worlds"));
        whitelist = parseTypes(c.getStringList("whitelist"), "whitelist");
        blacklist = parseTypes(c.getStringList("blacklist"), "blacklist");

        ConfigurationSection mobs = c.getConfigurationSection("mobs");
        if (mobs != null) {
            mobsEnabled = mobs.getBoolean("enabled", true);
            mergeRadius = Math.max(0.5, mobs.getDouble("merge-radius", 5.0));
            maxStack = Math.max(1, mobs.getInt("max-stack", 100));
            killMode = parseKillMode(mobs.getString("kill-mode", "DECREMENT"));
            mobNametag = nullToEmpty(mobs.getString("nametag", "<red>{type} <yellow>x{size}"));
            skipNamed = mobs.getBoolean("skip-named", true);
            skipTamed = mobs.getBoolean("skip-tamed", true);
            skipLeashed = mobs.getBoolean("skip-leashed", true);
            requireSameAge = mobs.getBoolean("require-same-age", true);
            requireSameSheepColor = mobs.getBoolean("require-same-sheep-color", true);
            requireSameSlimeSize = mobs.getBoolean("require-same-slime-size", true);
            remergeOnChunkLoad = mobs.getBoolean("remerge-on-chunk-load", true);
            wanderMergeIntervalTicks = Math.max(20L, mobs.getLong("wander-merge-interval-ticks", 100L));
            wanderMergeMaxPerTick = Math.max(1, mobs.getInt("wander-merge-max-per-tick", 64));
        }

        ConfigurationSection items = c.getConfigurationSection("items");
        if (items != null) {
            itemsEnabled = items.getBoolean("enabled", true);
            itemMergeRadius = Math.max(0.5, items.getDouble("merge-radius", 2.5));
            itemMaxStack = Math.max(1, items.getInt("max-stack", 1000));
            itemNametag = nullToEmpty(items.getString("nametag", "<aqua>{type} <yellow>x{size}"));
            enhanceVanillaMerge = items.getBoolean("enhance-vanilla-merge", true);
        }

        ConfigurationSection spawners = c.getConfigurationSection("spawners");
        if (spawners != null) {
            spawnersEnabled = spawners.getBoolean("enabled", true);
            spawnerMergeRadius = Math.max(0.5, spawners.getDouble("merge-radius", 3.0));
            spawnerMaxStack = Math.max(1, spawners.getInt("max-stack", 64));
            stackOnPlace = spawners.getBoolean("stack-on-place", true);
            breakOne = spawners.getBoolean("break-one", true);
        }

        ConfigurationSection tools = c.getConfigurationSection("tools");
        if (tools != null) {
            ConfigurationSection aura = tools.getConfigurationSection("kill-aura");
            if (aura != null) {
                killAuraEnabled = aura.getBoolean("enabled", true);
                killAuraRadius = Math.max(1.0, aura.getDouble("radius", 4.0));
                killAuraIntervalTicks = Math.max(1L, aura.getLong("interval-ticks", 10L));
                killAuraKillsPerPulse = Math.max(1, aura.getInt("kills-per-pulse", 1));
            }
            wandMaterial = parseMaterial(tools.getString("wand-material"), Material.BLAZE_ROD);
            toolMaterial = parseMaterial(tools.getString("tool-material"), Material.GOLDEN_HOE);
            auraMaterial = parseMaterial(tools.getString("aura-material"), Material.NETHER_STAR);
        }

        ConfigurationSection hooks = c.getConfigurationSection("hooks");
        if (hooks != null) {
            skipCitizens = hooks.getBoolean("skip-citizens", true);
            skipMythicMobs = hooks.getBoolean("skip-mythic-mobs", true);
            placeholderApi = hooks.getBoolean("placeholderapi", true);
        }

        metricsEnabled = c.getBoolean("metrics.enabled", true);

        Map<EntityType, MobRule> rules = new HashMap<>();
        ConfigurationSection ruleSec = c.getConfigurationSection("mob-rules");
        if (ruleSec != null) {
            for (String key : ruleSec.getKeys(false)) {
                ConfigurationSection rs = ruleSec.getConfigurationSection(key);
                if (rs == null) {
                    continue;
                }
                try {
                    EntityType type = EntityType.valueOf(key.trim().toUpperCase(Locale.ROOT));
                    rules.put(type, new MobRule(
                            rs.getBoolean("enabled", true),
                            rs.contains("max-stack") ? rs.getInt("max-stack") : null,
                            rs.contains("kill-mode") ? parseKillMode(rs.getString("kill-mode")) : null,
                            rs.getDouble("loot-multiplier", 1.0),
                            rs.getDouble("xp-multiplier", 1.0),
                            rs.contains("require-same-color") ? rs.getBoolean("require-same-color") : null,
                            rs.contains("preserve-slime-size") ? rs.getBoolean("preserve-slime-size") : null
                    ));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().log(Level.WARNING, "Unknown mob-rules type: " + key);
                }
            }
        }
        mobRules = Collections.unmodifiableMap(rules);
    }

    public MobRule rule(EntityType type) {
        return mobRules.getOrDefault(type, MobRule.defaults());
    }

    public int maxStackFor(EntityType type) {
        MobRule r = rule(type);
        return r.maxStack() != null ? Math.max(1, r.maxStack()) : maxStack;
    }

    public KillMode killModeFor(EntityType type) {
        MobRule r = rule(type);
        return r.killMode() != null ? r.killMode() : killMode;
    }

    public boolean mobTypeEnabled(EntityType type) {
        return rule(type).enabled();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Set<String> readStringSet(List<String> list) {
        Set<String> out = new HashSet<>();
        for (String w : list) {
            if (w != null && !w.isBlank()) {
                out.add(w.trim());
            }
        }
        return Collections.unmodifiableSet(out);
    }

    private KillMode parseKillMode(String raw) {
        if (raw == null) {
            return KillMode.DECREMENT;
        }
        String n = raw.trim().toUpperCase(Locale.ROOT);
        if ("INSTANT".equals(n) || "KILL_ALL".equals(n) || "ALL".equals(n) || "B".equals(n)) {
            return KillMode.INSTANT;
        }
        return KillMode.DECREMENT;
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material: " + raw);
            return fallback;
        }
    }

    private EnumSet<EntityType> parseTypes(List<String> names, String label) {
        EnumSet<EntityType> out = EnumSet.noneOf(EntityType.class);
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            try {
                out.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Unknown entity type in " + label + ": " + name);
            }
        }
        return out;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean mobsEnabled() {
        return mobsEnabled;
    }

    public double mergeRadius() {
        return mergeRadius;
    }

    public int maxStack() {
        return maxStack;
    }

    public KillMode killMode() {
        return killMode;
    }

    public String mobNametag() {
        return mobNametag;
    }

    /** @deprecated use {@link #mobNametag()} */
    @Deprecated
    public String nametag() {
        return mobNametag;
    }

    public Set<String> worlds() {
        return worlds;
    }

    public EnumSet<EntityType> whitelist() {
        return whitelist;
    }

    public EnumSet<EntityType> blacklist() {
        return blacklist;
    }

    public boolean skipNamed() {
        return skipNamed;
    }

    public boolean skipTamed() {
        return skipTamed;
    }

    public boolean skipLeashed() {
        return skipLeashed;
    }

    public boolean requireSameAge() {
        return requireSameAge;
    }

    public boolean requireSameSheepColor() {
        return requireSameSheepColor;
    }

    public boolean requireSameSlimeSize() {
        return requireSameSlimeSize;
    }

    public boolean remergeOnChunkLoad() {
        return remergeOnChunkLoad;
    }

    public long wanderMergeIntervalTicks() {
        return wanderMergeIntervalTicks;
    }

    public int wanderMergeMaxPerTick() {
        return wanderMergeMaxPerTick;
    }

    public boolean itemsEnabled() {
        return itemsEnabled;
    }

    public double itemMergeRadius() {
        return itemMergeRadius;
    }

    public int itemMaxStack() {
        return itemMaxStack;
    }

    public String itemNametag() {
        return itemNametag;
    }

    public boolean enhanceVanillaMerge() {
        return enhanceVanillaMerge;
    }

    public boolean spawnersEnabled() {
        return spawnersEnabled;
    }

    public double spawnerMergeRadius() {
        return spawnerMergeRadius;
    }

    public int spawnerMaxStack() {
        return spawnerMaxStack;
    }

    public boolean stackOnPlace() {
        return stackOnPlace;
    }

    public boolean breakOne() {
        return breakOne;
    }

    public boolean killAuraEnabled() {
        return killAuraEnabled;
    }

    public double killAuraRadius() {
        return killAuraRadius;
    }

    public long killAuraIntervalTicks() {
        return killAuraIntervalTicks;
    }

    public int killAuraKillsPerPulse() {
        return killAuraKillsPerPulse;
    }

    public Material wandMaterial() {
        return wandMaterial;
    }

    public Material toolMaterial() {
        return toolMaterial;
    }

    public Material auraMaterial() {
        return auraMaterial;
    }

    public boolean skipCitizens() {
        return skipCitizens;
    }

    public boolean skipMythicMobs() {
        return skipMythicMobs;
    }

    public boolean placeholderApi() {
        return placeholderApi;
    }

    public boolean metricsEnabled() {
        return metricsEnabled;
    }

    public Map<EntityType, MobRule> mobRules() {
        return mobRules;
    }

    public boolean worldEnabled(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName);
    }
}
