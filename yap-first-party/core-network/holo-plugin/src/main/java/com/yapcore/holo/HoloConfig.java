package com.yapcore.holo;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class HoloConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private boolean persist = true;
    private double lineSpacing = 0.28;
    private double viewDistance = 48;
    private int refreshTicks = 10;
    private String entity = "armor_stand";
    private boolean placeholders = true;
    private int clickCooldownTicks = 5;

    public HoloConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        persist = c.getBoolean("persist", true);
        lineSpacing = Math.max(0.05, c.getDouble("line-spacing", 0.28));
        viewDistance = Math.max(8.0, c.getDouble("view-distance", 48.0));
        refreshTicks = Math.max(2, c.getInt("refresh-ticks", 10));
        entity = c.getString("entity", "armor_stand");
        if (entity == null || entity.isBlank()) {
            entity = "armor_stand";
        }
        placeholders = c.getBoolean("placeholders", true);
        clickCooldownTicks = Math.max(1, c.getInt("click-cooldown-ticks", 5));
    }

    public boolean hologramsEnabled() {
        return enabled;
    }

    public boolean hologramsPersist() {
        return persist;
    }

    public double lineSpacing() {
        return lineSpacing;
    }

    public double viewDistance() {
        return viewDistance;
    }

    public int refreshTicks() {
        return refreshTicks;
    }

    public boolean preferTextDisplay() {
        return !"armor_stand".equalsIgnoreCase(entity);
    }

    public String entity() {
        return entity;
    }

    public boolean placeholders() {
        return placeholders;
    }

    public int clickCooldownTicks() {
        return clickCooldownTicks;
    }
}
