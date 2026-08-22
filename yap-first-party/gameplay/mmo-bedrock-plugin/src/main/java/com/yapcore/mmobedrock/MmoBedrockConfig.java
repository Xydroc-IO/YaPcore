package com.yapcore.mmobedrock;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MmoBedrockConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private int recipePageSize = 8;
    private int hiscorePageSize = 10;
    private long sidebarRefreshTicks = 100L;
    private String sidebarObjective = "yapmmo";
    private boolean interceptSkillsCommand = true;

    public MmoBedrockConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        recipePageSize = c.getInt("forms.recipe-page-size", 8);
        hiscorePageSize = c.getInt("forms.hiscore-page-size", 10);
        sidebarRefreshTicks = c.getLong("sidebar.refresh-ticks", 100L);
        sidebarObjective = c.getString("sidebar.objective-id", "yapmmo");
        interceptSkillsCommand = c.getBoolean("intercept-skills-command", true);
    }

    public boolean enabled() {
        return enabled;
    }

    public int recipePageSize() {
        return recipePageSize;
    }

    public int hiscorePageSize() {
        return hiscorePageSize;
    }

    public long sidebarRefreshTicks() {
        return sidebarRefreshTicks;
    }

    public String sidebarObjective() {
        return sidebarObjective;
    }

    public boolean interceptSkillsCommand() {
        return interceptSkillsCommand;
    }
}
