package com.yapcore.moderation;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModerationConfig {

    private final JavaPlugin plugin;
    private boolean useSharedYapDb = true;
    private String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/yap_playerdata";
    private String jdbcUser = "yap";
    private String jdbcPassword = "change-me";
    private int poolMax = 8;
    private int poolMinIdle = 2;
    private long connectionTimeoutMs = 10_000L;
    private String banLoginMessage = "&cYou are banned.";
    private String ipBanLoginMessage = "&cYour IP is banned.";
    private String muteChatMessage = "&cYou are muted.";
    private String kickMessage = "&cYou were kicked.";
    private String warnMessage = "&cWarning: {reason}";
    private boolean altNotifyStaff = true;

    public ModerationConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        useSharedYapDb = c.getBoolean("use-shared-yapdb", true);
        jdbcUrl = c.getString("jdbc.url", jdbcUrl);
        jdbcUser = c.getString("jdbc.user", jdbcUser);
        jdbcPassword = c.getString("jdbc.password", jdbcPassword);
        poolMax = Math.max(1, c.getInt("pool.maximum-pool-size", 8));
        poolMinIdle = Math.max(0, c.getInt("pool.minimum-idle", 2));
        connectionTimeoutMs = Math.max(1000L, c.getLong("pool.connection-timeout-ms", 10_000L));
        banLoginMessage = c.getString("messages.ban-login", banLoginMessage);
        ipBanLoginMessage = c.getString("messages.ipban-login", ipBanLoginMessage);
        muteChatMessage = c.getString("messages.mute-chat", muteChatMessage);
        kickMessage = c.getString("messages.kick", kickMessage);
        warnMessage = c.getString("messages.warn", warnMessage);
        altNotifyStaff = c.getBoolean("alt.notify-staff-on-join", true);
    }

    public boolean useSharedYapDb() {
        return useSharedYapDb;
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

    public int poolMinIdle() {
        return poolMinIdle;
    }

    public long connectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public String banLoginMessage() {
        return banLoginMessage;
    }

    public String ipBanLoginMessage() {
        return ipBanLoginMessage;
    }

    public String muteChatMessage() {
        return muteChatMessage;
    }

    public String kickMessage() {
        return kickMessage;
    }

    public String warnMessage() {
        return warnMessage;
    }

    public boolean altNotifyStaff() {
        return altNotifyStaff;
    }
}
