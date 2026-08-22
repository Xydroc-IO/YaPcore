package com.yapcore.mmocontent;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MmoContentConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private boolean useSharedYapdb = true;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private int poolMax = 4;
    private int poolMin = 1;
    private long poolTimeoutMs = 10_000L;
    private int hiscorePageSize = 50;
    private int hiscorePreviewLimit = 10;
    private boolean level99Broadcast = true;
    private String bossesDir = "bosses";
    private String areasFile = "areas.yml";
    private String questsDir = "quests";

    public MmoContentConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        useSharedYapdb = c.getBoolean("use-shared-yapdb", true);
        jdbcUrl = c.getString("jdbc-url", "jdbc:mysql://127.0.0.1:3306/yap");
        jdbcUser = c.getString("jdbc-user", "yap");
        jdbcPassword = c.getString("jdbc-password", "yap");
        poolMax = c.getInt("pool-max", 4);
        poolMin = c.getInt("pool-min", 1);
        poolTimeoutMs = c.getLong("pool-timeout-ms", 10_000L);
        hiscorePageSize = Math.max(1, c.getInt("hiscores.page-size", 50));
        hiscorePreviewLimit = Math.max(1, c.getInt("hiscores.preview-limit", 10));
        level99Broadcast = c.getBoolean("link.level-99-broadcast", true);
        bossesDir = c.getString("content.bosses-dir", c.getString("content.bosses-file", "bosses"));
        areasFile = c.getString("content.areas-file", "areas.yml");
        questsDir = c.getString("content.quests-dir", "quests");
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

    public int hiscorePageSize() {
        return hiscorePageSize;
    }

    public int hiscorePreviewLimit() {
        return hiscorePreviewLimit;
    }

    public boolean level99Broadcast() {
        return level99Broadcast;
    }

    public String bossesDir() {
        return bossesDir;
    }

    public String areasFile() {
        return areasFile;
    }

    public String questsDir() {
        return questsDir;
    }
}
