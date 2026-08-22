package com.yapcore.guilds;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GuildsConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private boolean useSharedYapdb = true;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private int poolMax = 6;
    private int poolMin = 1;
    private long poolTimeoutMs = 10_000;
    private int maxLevel = 50;
    private long baseXpToLevel = 1000;
    private double xpGrowth = 1.15;
    private int baseMaxMembers = 5;
    private int membersPerLevel = 1;
    private double baseBankCap = 10_000;
    private double bankCapPerLevel = 5000;
    private double skillLevelUpGuildXp = 25;
    private double bossKillGuildXp = 500;
    private long onlineTickXp = 1;
    private long onlineIntervalMinutes = 15;
    private int inviteExpireHours = 48;
    private String guildChatFormat;
    private String officerChatFormat;
    private String allyChatFormat;
    private int nameMin = 3;
    private int nameMax = 24;
    private int tagMin = 2;
    private int tagMax = 6;
    private int descriptionMax = 200;
    private int motdMax = 200;
    private boolean bankEnabled = true;
    private double bankMinDeposit = 1.0;
    private double bankMinWithdraw = 1.0;
    private Map<Integer, String> perkDescriptions = new LinkedHashMap<>();

    public GuildsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        useSharedYapdb = c.getBoolean("use-shared-yapdb", true);
        jdbcUrl = c.getString("jdbc.url", "jdbc:mysql://127.0.0.1:3306/yap");
        jdbcUser = c.getString("jdbc.user", "yap");
        jdbcPassword = c.getString("jdbc.password", "change-me");
        poolMax = c.getInt("pool.maximum-pool-size", 6);
        poolMin = c.getInt("pool.minimum-idle", 1);
        poolTimeoutMs = c.getLong("pool.connection-timeout-ms", 10_000);
        maxLevel = Math.max(1, c.getInt("guild-xp.max-level", 50));
        baseXpToLevel = Math.max(1, c.getLong("guild-xp.base-xp-to-level", 1000));
        xpGrowth = Math.max(1.0, c.getDouble("guild-xp.xp-growth", 1.15));
        baseMaxMembers = Math.max(1, c.getInt("guild-xp.base-max-members", 5));
        membersPerLevel = Math.max(0, c.getInt("guild-xp.members-per-level", 1));
        baseBankCap = c.getDouble("guild-xp.base-bank-cap", 10_000);
        bankCapPerLevel = c.getDouble("guild-xp.bank-cap-per-level", 5000);
        skillLevelUpGuildXp = c.getDouble("guild-xp.skill-level-up", 25);
        bossKillGuildXp = c.getDouble("guild-xp.boss-kill", 500);
        onlineTickXp = Math.max(0, c.getLong("guild-xp.online-tick", 1));
        onlineIntervalMinutes = Math.max(1, c.getLong("guild-xp.online-interval-minutes", 15));
        inviteExpireHours = Math.max(1, c.getInt("invites.expire-hours", 48));
        guildChatFormat = c.getString("chat.guild-format",
                "§7[§dG§7] §f%tag% §7| §f%player%§7: §f%message%");
        officerChatFormat = c.getString("chat.officer-format",
                "§7[§6O§7] §f%tag% §7| §f%player%§7: §f%message%");
        allyChatFormat = c.getString("chat.ally-format",
                "§7[§bA§7] §f%tag% §7| §f%player%§7: §f%message%");
        nameMin = c.getInt("limits.name-min", 3);
        nameMax = c.getInt("limits.name-max", 24);
        tagMin = c.getInt("limits.tag-min", 2);
        tagMax = c.getInt("limits.tag-max", 6);
        descriptionMax = c.getInt("limits.description-max", 200);
        motdMax = c.getInt("limits.motd-max", 200);
        bankEnabled = c.getBoolean("bank.enabled", true);
        bankMinDeposit = c.getDouble("bank.min-deposit", 1.0);
        bankMinWithdraw = c.getDouble("bank.min-withdraw", 1.0);
        perkDescriptions = new LinkedHashMap<>();
        var perks = c.getConfigurationSection("perks");
        if (perks != null) {
            for (String key : perks.getKeys(false)) {
                if (key.startsWith("level-")) {
                    try {
                        int level = Integer.parseInt(key.substring("level-".length()));
                        perkDescriptions.put(level, perks.getString(key, ""));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    public GuildXpCalculator.Config xpConfig() {
        return new GuildXpCalculator.Config(
                maxLevel,
                baseXpToLevel,
                xpGrowth,
                baseMaxMembers,
                membersPerLevel,
                baseBankCap,
                bankCapPerLevel,
                skillLevelUpGuildXp,
                bossKillGuildXp);
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

    public int inviteExpireHours() {
        return inviteExpireHours;
    }

    public String guildChatFormat() {
        return guildChatFormat;
    }

    public String officerChatFormat() {
        return officerChatFormat;
    }

    public String allyChatFormat() {
        return allyChatFormat;
    }

    public int nameMin() {
        return nameMin;
    }

    public int nameMax() {
        return nameMax;
    }

    public int tagMin() {
        return tagMin;
    }

    public int tagMax() {
        return tagMax;
    }

    public int descriptionMax() {
        return descriptionMax;
    }

    public int motdMax() {
        return motdMax;
    }

    public boolean bankEnabled() {
        return bankEnabled;
    }

    public double bankMinDeposit() {
        return bankMinDeposit;
    }

    public double bankMinWithdraw() {
        return bankMinWithdraw;
    }

    public double skillLevelUpGuildXp() {
        return skillLevelUpGuildXp;
    }

    public double bossKillGuildXp() {
        return bossKillGuildXp;
    }

    public long onlineTickXp() {
        return onlineTickXp;
    }

    public long onlineIntervalTicks() {
        return onlineIntervalMinutes * 60L * 20L;
    }

    public Map<Integer, String> perkDescriptions() {
        return perkDescriptions;
    }
}
