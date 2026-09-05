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
    private int maxLevel = 99;
    private double xpMultiplier = 1.0;
    private boolean actionBarXp = true;
    private boolean levelUpTitle = true;
    private boolean levelUpChat = true;
    private boolean preferOverJobs = true;
    private String skillsDirectory = "skills";
    private String combatAttackSkill = "attack";
    private String combatStrengthSkill = "strength";
    private String combatDefenceSkill = "defence";
    private String combatHitpointsSkill = "hitpoints";

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
        maxLevel = c.getInt("xp-table.max-level", 99);
        xpMultiplier = c.getDouble("xp-table.multiplier", 1.0);
        actionBarXp = c.getBoolean("feedback.action-bar-xp", true);
        levelUpTitle = c.getBoolean("feedback.level-up-title", true);
        levelUpChat = c.getBoolean("feedback.level-up-chat", true);
        preferOverJobs = c.getBoolean("prefer-over-jobs", true);
        skillsDirectory = c.getString("skills-directory", "skills");
        combatAttackSkill = c.getString("combat-level.attack-skill", "attack");
        combatStrengthSkill = c.getString("combat-level.strength-skill", "strength");
        combatDefenceSkill = c.getString("combat-level.defence-skill", "defence");
        combatHitpointsSkill = c.getString("combat-level.hitpoints-skill", "hitpoints");
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

    public boolean actionBarXp() {
        return actionBarXp;
    }

    public boolean levelUpTitle() {
        return levelUpTitle;
    }

    public boolean levelUpChat() {
        return levelUpChat;
    }

    public boolean preferOverJobs() {
        return preferOverJobs;
    }

    public String skillsDirectory() {
        return skillsDirectory;
    }

    public String combatAttackSkill() {
        return combatAttackSkill;
    }

    public String combatStrengthSkill() {
        return combatStrengthSkill;
    }

    public String combatDefenceSkill() {
        return combatDefenceSkill;
    }

    public String combatHitpointsSkill() {
        return combatHitpointsSkill;
    }
}
