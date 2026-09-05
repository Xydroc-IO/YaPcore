package com.yapcore.dungeons;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class DungeonsConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private boolean useSharedYapdb = true;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private int poolMax = 6;
    private int poolMin = 1;
    private long poolTimeoutMs = 10_000;
    private int inviteExpireMinutes = 30;
    private int maxPartySize = 4;
    private int baseLives = 3;
    private int livesPerExtraMember = 1;
    private int livesCap = 6;
    private int idleGcMinutes = 5;
    private int clearGcSeconds = 45;
    private int dailyClearSoftCap = 8;
    private double dailyEconomyScale = 0.35;
    private String packsDirectory = "dungeons";
    private Material portalBlock = Material.END_PORTAL_FRAME;
    private Material portalItemMaterial = Material.END_PORTAL_FRAME;
    private List<String> portalRecipe = List.of(
            "OBSIDIAN", "ENDER_EYE", "OBSIDIAN",
            "DEEPSLATE", "ENDER_EYE", "DEEPSLATE",
            "OBSIDIAN", "ENDER_EYE", "OBSIDIAN");
    private boolean structureEnabled = true;
    private Material structureFrame = Material.OBSIDIAN;
    private Material structureInterior = Material.NETHER_PORTAL;
    private Material structureActivateItem = Material.ENDER_EYE;
    private int structureWidth = 4;
    private int structureHeight = 5;

    public DungeonsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", false);
        useSharedYapdb = c.getBoolean("use-shared-yapdb", true);
        jdbcUrl = c.getString("jdbc.url", "jdbc:mysql://127.0.0.1:3306/yap");
        jdbcUser = c.getString("jdbc.user", "yap");
        jdbcPassword = c.getString("jdbc.password", "change-me");
        poolMax = c.getInt("pool.maximum-pool-size", 6);
        poolMin = c.getInt("pool.minimum-idle", 1);
        poolTimeoutMs = c.getLong("pool.connection-timeout-ms", 10_000);
        inviteExpireMinutes = Math.max(1, c.getInt("invites.expire-minutes", 30));
        maxPartySize = Math.max(1, Math.min(8, c.getInt("party.max-size", 4)));
        baseLives = Math.max(1, c.getInt("lives.base", 3));
        livesPerExtraMember = Math.max(0, c.getInt("lives.per-extra-member", 1));
        livesCap = Math.max(1, c.getInt("lives.cap", 6));
        idleGcMinutes = Math.max(1, c.getInt("instance.idle-gc-minutes", 5));
        clearGcSeconds = Math.max(10, c.getInt("instance.clear-gc-seconds", 45));
        dailyClearSoftCap = Math.max(1, c.getInt("anti-farm.daily-clear-soft-cap", 8));
        dailyEconomyScale = Math.max(0.0, Math.min(1.0, c.getDouble("anti-farm.daily-economy-scale", 0.35)));
        packsDirectory = c.getString("packs-directory", "dungeons");
        portalBlock = material(c.getString("portal.block", "END_PORTAL_FRAME"), Material.END_PORTAL_FRAME);
        portalItemMaterial = material(c.getString("portal.item", "END_PORTAL_FRAME"), Material.END_PORTAL_FRAME);
        List<String> recipe = c.getStringList("portal.recipe");
        if (recipe != null && recipe.size() == 9) {
            portalRecipe = List.copyOf(recipe);
        }
        structureEnabled = c.getBoolean("portal.structure.enabled", true);
        structureFrame = material(c.getString("portal.structure.frame", "OBSIDIAN"), Material.OBSIDIAN);
        structureInterior = material(c.getString("portal.structure.interior", "NETHER_PORTAL"), Material.NETHER_PORTAL);
        structureActivateItem = material(c.getString("portal.structure.activate-item", "ENDER_EYE"), Material.ENDER_EYE);
        structureWidth = Math.max(3, c.getInt("portal.structure.width", 4));
        structureHeight = Math.max(4, c.getInt("portal.structure.height", 5));
    }

    private static Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean useSharedYapdb() {
        return useSharedYapdb;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String jdbcUser() {
        return jdbcUser;
    }

    public String jdbcPassword() {
        return jdbcPassword;
    }

    public int poolMax() {
        return poolMax;
    }

    public int poolMin() {
        return poolMin;
    }

    public long poolTimeoutMs() {
        return poolTimeoutMs;
    }

    public int inviteExpireMinutes() {
        return inviteExpireMinutes;
    }

    public int maxPartySize() {
        return maxPartySize;
    }

    public int baseLives() {
        return baseLives;
    }

    public int livesPerExtraMember() {
        return livesPerExtraMember;
    }

    public int livesCap() {
        return livesCap;
    }

    public int idleGcMinutes() {
        return idleGcMinutes;
    }

    public int clearGcSeconds() {
        return clearGcSeconds;
    }

    public int dailyClearSoftCap() {
        return dailyClearSoftCap;
    }

    public double dailyEconomyScale() {
        return dailyEconomyScale;
    }

    public String packsDirectory() {
        return packsDirectory;
    }

    public Material portalBlock() {
        return portalBlock;
    }

    public Material portalItemMaterial() {
        return portalItemMaterial;
    }

    public List<String> portalRecipe() {
        return new ArrayList<>(portalRecipe);
    }

    public boolean structureEnabled() {
        return structureEnabled;
    }

    public Material structureFrame() {
        return structureFrame;
    }

    public Material structureInterior() {
        return structureInterior;
    }

    public Material structureActivateItem() {
        return structureActivateItem;
    }

    public int structureWidth() {
        return structureWidth;
    }

    public int structureHeight() {
        return structureHeight;
    }
}
