package com.yapcore.mechanics;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public final class MechanicsConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private boolean staminaEnabled = true;
    private double staminaMax = 100;
    private double staminaRegen = 2.5;
    private double breakCost = 1;
    private double fishCost = 2;
    private double sprintDrain = 0.8;
    private boolean exhaustedMessage = true;
    private boolean toolsEnabled = true;
    private boolean toolsEnforce = true;
    private String toolsFile = "tools.yml";
    private boolean nodesEnabled = true;
    private String nodesFile = "nodes.yml";
    private boolean farmingEnabled = true;
    private String farmingFile = "farming.yml";
    private boolean physicsEnabled = true;
    private String physicsFile = "physics.yml";
    private double fishingBonus = 1.25;
    private int minFishWait = 40;
    private int maxFishWait = 600;

    public MechanicsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        staminaEnabled = c.getBoolean("stamina.enabled", true);
        staminaMax = c.getDouble("stamina.max", 100);
        staminaRegen = c.getDouble("stamina.regen-per-second", 2.5);
        breakCost = c.getDouble("stamina.break-cost", 1);
        fishCost = c.getDouble("stamina.fish-cost", 2);
        sprintDrain = c.getDouble("stamina.sprint-drain-per-second", 0.8);
        exhaustedMessage = c.getBoolean("stamina.exhausted-message", true);
        toolsEnabled = c.getBoolean("tools.enabled", true);
        toolsEnforce = c.getBoolean("tools.enforce", true);
        toolsFile = c.getString("tools.file", "tools.yml");
        nodesEnabled = c.getBoolean("nodes.enabled", true);
        nodesFile = c.getString("nodes.file", "nodes.yml");
        farmingEnabled = c.getBoolean("farming.enabled", true);
        farmingFile = c.getString("farming.file", "farming.yml");
        physicsEnabled = c.getBoolean("physics.enabled", true);
        physicsFile = c.getString("physics.file", "physics.yml");
        fishingBonus = c.getDouble("fishing.perfect-catch-bonus-multiplier", 1.25);
        minFishWait = c.getInt("fishing.min-wait-ticks", 40);
        maxFishWait = c.getInt("fishing.max-wait-ticks", 600);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean staminaEnabled() {
        return staminaEnabled;
    }

    public double staminaMax() {
        return staminaMax;
    }

    public double staminaRegen() {
        return staminaRegen;
    }

    public double breakCost() {
        return breakCost;
    }

    public double fishCost() {
        return fishCost;
    }

    public double sprintDrain() {
        return sprintDrain;
    }

    public boolean exhaustedMessage() {
        return exhaustedMessage;
    }

    public boolean toolsEnabled() {
        return toolsEnabled;
    }

    public boolean toolsEnforce() {
        return toolsEnforce;
    }

    public Path toolsPath() {
        return plugin.getDataFolder().toPath().resolve(toolsFile);
    }

    public boolean nodesEnabled() {
        return nodesEnabled;
    }

    public Path nodesPath() {
        return plugin.getDataFolder().toPath().resolve(nodesFile);
    }

    public boolean farmingEnabled() {
        return farmingEnabled;
    }

    public Path farmingPath() {
        return plugin.getDataFolder().toPath().resolve(farmingFile);
    }

    public boolean physicsEnabled() {
        return physicsEnabled;
    }

    public Path physicsPath() {
        return plugin.getDataFolder().toPath().resolve(physicsFile);
    }

    public double fishingBonus() {
        return fishingBonus;
    }

    public int minFishWait() {
        return minFishWait;
    }

    public int maxFishWait() {
        return maxFishWait;
    }
}
