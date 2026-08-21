package com.yapcore.vehicles.engine;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VehiclesConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private int tickPeriod = 1;
    private int emptyDespawnSeconds;
    private boolean dropItemOnDestroy = true;
    private boolean fuelEnabled = true;
    private boolean damageEnabled = true;
    private boolean builtinBuggy = true;
    private boolean builtinHoverbike = true;

    private String fuelItem = "COAL";
    private double fuelPerItem = 200;
    private double fuelBurnMultiplier = 1.0;
    private boolean fuelRequireSneak = true;

    private boolean upgradesEnabled = true;
    private boolean upgradesCraftEnabled = true;
    private boolean upgradesShopEnabled = true;
    private boolean builtinFleet = true;
    private boolean highResModels = true;
    private boolean highResKeepBlockBody = false;

    private boolean compatEnabled = true;
    private boolean compatClaimMinecarts = true;
    private boolean compatClaimBoats = true;
    private boolean compatClaimOtherVehicles = false;
    private boolean compatRequireMarker = true;
    private boolean compatIgnoreVanilla = true;
    private boolean compatPreserveModel = true;
    private String compatDefaultType = "chassis";
    private final Map<String, String> compatNameMap = new HashMap<>();
    private final Map<String, String> compatTagMap = new HashMap<>();
    private List<String> compatKnownPlugins = List.of();

    public VehiclesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        tickPeriod = Math.max(1, c.getInt("tick-period", 1));
        emptyDespawnSeconds = Math.max(0, c.getInt("empty-despawn-seconds", 0));
        dropItemOnDestroy = c.getBoolean("drop-item-on-destroy", true);
        fuelEnabled = c.getBoolean("fuel-enabled", true);
        damageEnabled = c.getBoolean("damage-enabled", true);
        builtinBuggy = c.getBoolean("builtins.buggy", true);
        builtinHoverbike = c.getBoolean("builtins.hoverbike", true);
        builtinFleet = c.getBoolean("builtins.fleet", true);
        highResModels = c.getBoolean("visuals.high-res-models", true);
        highResKeepBlockBody = c.getBoolean("visuals.high-res-keep-block-body", false);

        fuelItem = c.getString("fuel.item", "COAL");
        fuelPerItem = Math.max(1, c.getDouble("fuel.per-item", 200));
        fuelBurnMultiplier = Math.max(0.05, c.getDouble("fuel.burn-multiplier", 1.0));
        fuelRequireSneak = c.getBoolean("fuel.require-sneak", true);

        upgradesEnabled = c.getBoolean("upgrades.enabled", true);
        upgradesCraftEnabled = c.getBoolean("upgrades.craft-enabled", true);
        upgradesShopEnabled = c.getBoolean("upgrades.shop-enabled", true);

        compatEnabled = c.getBoolean("compat.enabled", true);
        compatClaimMinecarts = c.getBoolean("compat.claim-minecarts", true);
        compatClaimBoats = c.getBoolean("compat.claim-boats", true);
        compatClaimOtherVehicles = c.getBoolean("compat.claim-other-vehicles", false);
        compatRequireMarker = c.getBoolean("compat.require-marker", true);
        compatIgnoreVanilla = c.getBoolean("compat.ignore-vanilla", true);
        compatPreserveModel = c.getBoolean("compat.preserve-model", true);
        compatDefaultType = c.getString("compat.default-type", "chassis");

        compatNameMap.clear();
        ConfigurationSection names = c.getConfigurationSection("compat.name-map");
        if (names != null) {
            for (String key : names.getKeys(false)) {
                compatNameMap.put(key.toLowerCase(Locale.ROOT), names.getString(key));
            }
        }

        compatTagMap.clear();
        ConfigurationSection tags = c.getConfigurationSection("compat.tag-map");
        if (tags != null) {
            for (String key : tags.getKeys(false)) {
                compatTagMap.put(key.toLowerCase(Locale.ROOT), tags.getString(key));
            }
        }

        compatKnownPlugins = c.getStringList("compat.known-plugins");
        if (compatKnownPlugins == null) {
            compatKnownPlugins = List.of();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public int tickPeriod() {
        return tickPeriod;
    }

    public int emptyDespawnSeconds() {
        return emptyDespawnSeconds;
    }

    public boolean dropItemOnDestroy() {
        return dropItemOnDestroy;
    }

    public boolean fuelEnabled() {
        return fuelEnabled;
    }

    public boolean damageEnabled() {
        return damageEnabled;
    }

    public boolean builtinBuggy() {
        return builtinBuggy;
    }

    public boolean builtinHoverbike() {
        return builtinHoverbike;
    }

    public boolean builtinFleet() {
        return builtinFleet;
    }

    /** Attach CustomModelData ItemDisplay bodies from yap-vehicles pack. */
    public boolean highResModels() {
        return highResModels;
    }

    /** If true, also keep colored BlockDisplay body panels under HD models. */
    public boolean highResKeepBlockBody() {
        return highResKeepBlockBody;
    }

    public String fuelItem() {
        return fuelItem;
    }

    public double fuelPerItem() {
        return fuelPerItem;
    }

    public double fuelBurnMultiplier() {
        return fuelBurnMultiplier;
    }

    public boolean fuelRequireSneak() {
        return fuelRequireSneak;
    }

    public boolean upgradesEnabled() {
        return upgradesEnabled;
    }

    public boolean upgradesCraftEnabled() {
        return upgradesCraftEnabled;
    }

    public boolean upgradesShopEnabled() {
        return upgradesShopEnabled;
    }

    public boolean compatEnabled() {
        return compatEnabled;
    }

    public boolean compatClaimMinecarts() {
        return compatClaimMinecarts;
    }

    public boolean compatClaimBoats() {
        return compatClaimBoats;
    }

    public boolean compatClaimOtherVehicles() {
        return compatClaimOtherVehicles;
    }

    public boolean compatRequireMarker() {
        return compatRequireMarker;
    }

    public boolean compatIgnoreVanilla() {
        return compatIgnoreVanilla;
    }

    public boolean compatPreserveModel() {
        return compatPreserveModel;
    }

    public String compatDefaultType() {
        return compatDefaultType;
    }

    public Map<String, String> compatNameMap() {
        return compatNameMap;
    }

    public Map<String, String> compatTagMap() {
        return compatTagMap;
    }

    public List<String> compatKnownPlugins() {
        return compatKnownPlugins;
    }
}
