package com.yapcore.crafting;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftingConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private String recipesDirectory = "recipes";
    private String sellPricesFile = "sell-prices.yml";
    private String gearTiersFile = "gear-tiers.yml";
    private boolean economyEnabled = true;
    private boolean requirePlayerData = true;
    private boolean xpActionBar = true;
    private boolean unlockActionBar = true;

    public CraftingConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        enabled = cfg.getBoolean("enabled", true);
        recipesDirectory = cfg.getString("recipes-directory", "recipes");
        sellPricesFile = cfg.getString("sell-prices-file", "sell-prices.yml");
        gearTiersFile = cfg.getString("gear-tiers-file", "gear-tiers.yml");
        economyEnabled = cfg.getBoolean("economy.enabled", true);
        requirePlayerData = cfg.getBoolean("economy.require-playerdata", true);
        xpActionBar = cfg.getBoolean("feedback.xp-action-bar", true);
        unlockActionBar = cfg.getBoolean("feedback.unlock-action-bar", true);
    }

    public boolean enabled() {
        return enabled;
    }

    public String recipesDirectory() {
        return recipesDirectory;
    }

    public String sellPricesFile() {
        return sellPricesFile;
    }

    public String gearTiersFile() {
        return gearTiersFile;
    }

    public boolean economyEnabled() {
        return economyEnabled;
    }

    public boolean requirePlayerData() {
        return requirePlayerData;
    }

    public boolean xpActionBar() {
        return xpActionBar;
    }

    public boolean unlockActionBar() {
        return unlockActionBar;
    }
}
