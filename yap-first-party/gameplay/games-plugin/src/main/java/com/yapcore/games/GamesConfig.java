package com.yapcore.games;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class GamesConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private String arenasDirectory = "arenas";
    private String modesDirectory = "modes";
    private String kitsFile = "kits.yml";
    private String lobbyWorld = "world";
    private int defaultCountdown = 10;
    private boolean resetDrops = true;
    private boolean useSharedYapdb = true;
    private String jdbcUrl = "";
    private String jdbcUser = "yap";
    private String jdbcPassword = "";
    private int poolMax = 4;
    private int poolMin = 1;
    private long poolTimeoutMs = 10_000;
    private boolean blockSkillXp = true;
    private boolean enforceBoundary = true;
    private boolean spectatorsOnElimination = true;
    private boolean scoreboard = true;
    private boolean countdownTitles = true;
    private boolean rewardsEnabled = true;
    private double ffaWinReward = 25.0;
    private double duelWinReward = 10.0;
    private boolean signsEnabled = true;
    private List<String> signLines = List.of("[Queue]", "%mode%");

    public GamesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        enabled = cfg.getBoolean("enabled", true);
        arenasDirectory = cfg.getString("arenas-directory", "arenas");
        modesDirectory = cfg.getString("modes-directory", "modes");
        kitsFile = cfg.getString("kits-file", "kits.yml");
        lobbyWorld = cfg.getString("lobby-world", "world");
        defaultCountdown = cfg.getInt("countdown-seconds", 10);
        resetDrops = cfg.getBoolean("reset-drops", true);
        useSharedYapdb = cfg.getBoolean("use-shared-yapdb", true);
        jdbcUrl = cfg.getString("jdbc.url", "");
        jdbcUser = cfg.getString("jdbc.user", "yap");
        jdbcPassword = cfg.getString("jdbc.password", "");
        poolMax = cfg.getInt("pool.maximum-pool-size", 4);
        poolMin = cfg.getInt("pool.minimum-idle", 1);
        poolTimeoutMs = cfg.getLong("pool.connection-timeout-ms", 10_000L);
        blockSkillXp = cfg.getBoolean("match.block-skill-xp", true);
        enforceBoundary = cfg.getBoolean("match.enforce-arena-boundary", true);
        spectatorsOnElimination = cfg.getBoolean("match.spectators-on-elimination", true);
        scoreboard = cfg.getBoolean("match.scoreboard", true);
        countdownTitles = cfg.getBoolean("match.countdown-titles", true);
        rewardsEnabled = cfg.getBoolean("rewards.enabled", true);
        ffaWinReward = cfg.getDouble("rewards.ffa-win", 25.0);
        duelWinReward = cfg.getDouble("rewards.duel-win", 10.0);
        signsEnabled = cfg.getBoolean("signs.enabled", true);
        signLines = cfg.getStringList("signs.lines");
        if (signLines.isEmpty()) {
            signLines = List.of("[Queue]", "%mode%");
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public String arenasDirectory() {
        return arenasDirectory;
    }

    public String modesDirectory() {
        return modesDirectory;
    }

    public String kitsFile() {
        return kitsFile;
    }

    public String lobbyWorld() {
        return lobbyWorld;
    }

    public int defaultCountdown() {
        return defaultCountdown;
    }

    public boolean resetDrops() {
        return resetDrops;
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

    public boolean blockSkillXp() {
        return blockSkillXp;
    }

    public boolean enforceBoundary() {
        return enforceBoundary;
    }

    public boolean spectatorsOnElimination() {
        return spectatorsOnElimination;
    }

    public boolean scoreboard() {
        return scoreboard;
    }

    public boolean countdownTitles() {
        return countdownTitles;
    }

    public boolean rewardsEnabled() {
        return rewardsEnabled;
    }

    public double ffaWinReward() {
        return ffaWinReward;
    }

    public double duelWinReward() {
        return duelWinReward;
    }

    public boolean signsEnabled() {
        return signsEnabled;
    }

    public List<String> signLines() {
        return signLines;
    }
}
