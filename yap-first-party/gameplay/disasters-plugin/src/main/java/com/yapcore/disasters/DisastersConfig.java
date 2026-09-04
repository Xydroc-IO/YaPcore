package com.yapcore.disasters;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DisastersConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private boolean broadcastStart = true;
    private boolean broadcastEnd = true;
    private boolean grief = false;
    private boolean realLightning = false;
    private boolean protectClaims = true;
    private boolean protectRegions = true;
    private int defaultDurationSeconds = 120;
    private final Set<String> allowedWorlds = new HashSet<>();

    private boolean randomEnabled = false;
    private boolean randomRequirePlayers = true;
    private int randomMinIntervalSeconds = 900;
    private int randomMaxIntervalSeconds = 2400;
    private int randomDurationSeconds = 120;
    private int warningSeconds = 30;
    private boolean warningsEnabled = true;
    private final Map<DisasterType, Integer> randomWeights = new EnumMap<>(DisasterType.class);

    private boolean volcanoSitesAmbient = true;
    private double volcanoSiteSnapBlocks = 48.0;

    public DisastersConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        broadcastStart = c.getBoolean("broadcast-start", true);
        broadcastEnd = c.getBoolean("broadcast-end", true);
        grief = c.getBoolean("grief", false);
        realLightning = c.getBoolean("real-lightning", false) || grief;
        protectClaims = c.getBoolean("protect-claims", true);
        protectRegions = c.getBoolean("protect-regions", true);
        defaultDurationSeconds = Math.max(5, c.getInt("default-duration-seconds", 120));
        allowedWorlds.clear();
        List<String> worlds = c.getStringList("allowed-worlds");
        for (String w : worlds) {
            if (w != null && !w.isBlank()) {
                allowedWorlds.add(w.trim().toLowerCase(Locale.ROOT));
            }
        }

        randomEnabled = c.getBoolean("random.enabled", false);
        randomRequirePlayers = c.getBoolean("random.require-players", true);
        randomMinIntervalSeconds = Math.max(30, c.getInt("random.min-interval-seconds", 900));
        randomMaxIntervalSeconds = Math.max(randomMinIntervalSeconds,
                c.getInt("random.max-interval-seconds", 2400));
        randomDurationSeconds = Math.max(10, c.getInt("random.duration-seconds", 120));
        warningSeconds = Math.max(0, c.getInt("random.warning-seconds", 30));
        warningsEnabled = c.getBoolean("warnings.enabled", true);
        if (c.isSet("random.warnings-enabled")) {
            warningsEnabled = c.getBoolean("random.warnings-enabled", warningsEnabled);
        }

        randomWeights.clear();
        ConfigurationSection weights = c.getConfigurationSection("random.weights");
        if (weights != null) {
            for (String key : weights.getKeys(false)) {
                DisasterType type = DisasterType.parse(key);
                if (type != null) {
                    randomWeights.put(type, Math.max(0, weights.getInt(key, 0)));
                }
            }
        }
        if (randomWeights.isEmpty()) {
            randomWeights.put(DisasterType.THUNDER, 10);
            randomWeights.put(DisasterType.HURRICANE, 6);
            randomWeights.put(DisasterType.TORNADO, 5);
            randomWeights.put(DisasterType.EARTHQUAKE, 5);
            randomWeights.put(DisasterType.VOLCANO, 4);
            randomWeights.put(DisasterType.BLIZZARD, 6);
            randomWeights.put(DisasterType.DROUGHT, 4);
            randomWeights.put(DisasterType.METEOR, 5);
            randomWeights.put(DisasterType.TSUNAMI, 3);
        }

        volcanoSitesAmbient = c.getBoolean("volcano-sites-ambient", true);
        volcanoSiteSnapBlocks = Math.max(8.0, c.getDouble("volcano-site-snap-blocks", 48.0));
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean broadcastStart() {
        return broadcastStart;
    }

    public boolean broadcastEnd() {
        return broadcastEnd;
    }

    public boolean grief() {
        return grief;
    }

    public boolean realLightning() {
        return realLightning;
    }

    public boolean protectClaims() {
        return protectClaims;
    }

    public boolean protectRegions() {
        return protectRegions;
    }

    public int defaultDurationSeconds() {
        return defaultDurationSeconds;
    }

    public boolean worldAllowed(String worldName) {
        if (allowedWorlds.isEmpty()) {
            return true;
        }
        return worldName != null && allowedWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public boolean typeEnabled(DisasterType type) {
        if (type == null) {
            return false;
        }
        return plugin.getConfig().getBoolean("disasters." + type.configKey() + ".enabled", true);
    }

    public long periodTicks(DisasterType type, long fallback) {
        if (type == null) {
            return fallback;
        }
        return Math.max(2L, plugin.getConfig().getLong(
                "disasters." + type.configKey() + ".period-ticks", fallback));
    }

    public long volcanoLavaTicks() {
        return Math.max(20L, plugin.getConfig().getLong("disasters.volcano.temporary-lava-ticks", 60L));
    }

    public long blizzardSnowTicks() {
        return Math.max(20L, plugin.getConfig().getLong("disasters.blizzard.temporary-snow-ticks", 80L));
    }

    public long meteorFireTicks() {
        return Math.max(20L, plugin.getConfig().getLong("disasters.meteor.temporary-fire-ticks", 40L));
    }

    public long droughtDryTicks() {
        return Math.max(20L, plugin.getConfig().getLong("disasters.drought.temporary-dry-ticks", 100L));
    }

    public long tsunamiWaterTicks() {
        return Math.max(20L, plugin.getConfig().getLong("disasters.tsunami.temporary-water-ticks", 80L));
    }

    public int tsunamiWaveRadius() {
        return Math.max(4, plugin.getConfig().getInt("disasters.tsunami.wave-radius", 14));
    }

    public int tsunamiFloodHeight() {
        return Math.max(1, Math.min(6, plugin.getConfig().getInt("disasters.tsunami.flood-height", 2)));
    }

    public boolean randomEnabled() {
        return randomEnabled;
    }

    public boolean randomRequirePlayers() {
        return randomRequirePlayers;
    }

    public int randomMinIntervalSeconds() {
        return randomMinIntervalSeconds;
    }

    public int randomMaxIntervalSeconds() {
        return randomMaxIntervalSeconds;
    }

    public int randomDurationSeconds() {
        return randomDurationSeconds;
    }

    public int warningSeconds() {
        return warningSeconds;
    }

    public boolean warningsEnabled() {
        return warningsEnabled;
    }

    public Map<DisasterType, Integer> randomWeights() {
        return randomWeights;
    }

    public boolean volcanoSitesAmbient() {
        return volcanoSitesAmbient;
    }

    public double volcanoSiteSnapBlocks() {
        return volcanoSiteSnapBlocks;
    }
}
