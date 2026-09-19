package com.yapcore.skills;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkillsConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private boolean useSharedYapdb = true;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private int poolMax = 6;
    private int poolMin = 1;
    private long poolTimeoutMs = 10_000;
    private int maxLevel = 120;
    private double xpMultiplier = 1.0;
    private int overallMaxLevel = 120;
    private double overallXpShare = 0.5;
    private double overallXpMultiplier = 1.0;
    private double overallMaxedXpShare = 0.75;
    private boolean actionBarXp = true;
    private boolean levelUpTitle = true;
    private boolean levelUpChat = true;
    private boolean levelUpSound = true;
    private boolean preferOverJobs = true;
    private String skillsDirectory = "skills";

    public SkillsConfig(JavaPlugin plugin) {
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
        maxLevel = c.getInt("xp-table.max-level", 120);
        xpMultiplier = c.getDouble("xp-table.multiplier", 1.0);
        overallMaxLevel = Math.max(2, c.getInt("overall.max-level", 120));
        overallXpShare = Math.max(0.0, c.getDouble("overall.xp-share", 0.5));
        overallXpMultiplier = Math.max(0.01, c.getDouble("overall.multiplier", 1.0));
        overallMaxedXpShare = Math.max(0.0, c.getDouble("overall.maxed-xp-share", 0.75));
        actionBarXp = c.getBoolean("feedback.action-bar-xp", true);
        levelUpTitle = c.getBoolean("feedback.level-up-title", true);
        levelUpChat = c.getBoolean("feedback.level-up-chat", true);
        levelUpSound = c.getBoolean("feedback.level-up-sound", true);
        preferOverJobs = c.getBoolean("prefer-over-jobs", true);
        skillsDirectory = c.getString("skills-directory", "skills");
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

    public int maxLevel() {
        return maxLevel;
    }

    public double xpMultiplier() {
        return xpMultiplier;
    }

    public int overallMaxLevel() {
        return overallMaxLevel;
    }

    public double overallXpShare() {
        return overallXpShare;
    }

    public double overallXpMultiplier() {
        return overallXpMultiplier;
    }

    /** When a skill is already maxed, this share feeds overall (keeps endgame actions useful). */
    public double overallMaxedXpShare() {
        return overallMaxedXpShare;
    }

    public boolean actionBarXp() {
        return actionBarXp;
    }

    public boolean levelUpTitle() {
        return levelUpTitle;
    }

    public boolean levelUpChat() {
        return levelUpChat;
    }

    public boolean levelUpSound() {
        return levelUpSound;
    }

    public boolean preferOverJobs() {
        return preferOverJobs;
    }

    public String skillsDirectory() {
        return skillsDirectory;
    }
}
