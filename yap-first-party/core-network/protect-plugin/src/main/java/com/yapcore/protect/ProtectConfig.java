package com.yapcore.protect;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectConfig {

    private final JavaPlugin plugin;
    private boolean loggingEnabled = true;
    private boolean logBlockBreak = true;
    private boolean logBlockPlace = true;
    private boolean logContainerAccess = true;
    private boolean logContainerInventory = true;
    private boolean logEntityChange = true;
    private boolean useSharedYapDb = true;
    private String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/yap?useSSL=false&allowPublicKeyRetrieval=true";
    private String jdbcUser = "yap";
    private String jdbcPassword = "yap";
    private int poolMax = 6;
    private int poolMinIdle = 1;
    private long connectionTimeoutMs = 10_000L;
    private int pruneDays = 30;
    private int maxRollbackRadius = 32;
    private int maxLookupLimit = 200;
    private String serverId = "lobby";

    public ProtectConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        loggingEnabled = c.getBoolean("logging.enabled", true);
        logBlockBreak = c.getBoolean("logging.block-break", true);
        logBlockPlace = c.getBoolean("logging.block-place", true);
        logContainerAccess = c.getBoolean("logging.container-access", true);
        logContainerInventory = c.getBoolean("logging.container-inventory", true);
        logEntityChange = c.getBoolean("logging.entity-change", true);
        useSharedYapDb = c.getBoolean("database.use-shared-yapdb", true);
        jdbcUrl = c.getString("database.jdbc-url", jdbcUrl);
        jdbcUser = c.getString("database.user", jdbcUser);
        jdbcPassword = c.getString("database.password", jdbcPassword);
        poolMax = c.getInt("database.pool-max", poolMax);
        poolMinIdle = c.getInt("database.pool-min-idle", poolMinIdle);
        connectionTimeoutMs = c.getLong("database.connection-timeout-ms", connectionTimeoutMs);
        pruneDays = Math.max(0, c.getInt("retention.prune-days", pruneDays));
        maxRollbackRadius = Math.max(1, c.getInt("limits.max-rollback-radius", maxRollbackRadius));
        maxLookupLimit = Math.max(1, Math.min(500, c.getInt("limits.max-lookup-limit", maxLookupLimit)));
        serverId = c.getString("server-id", serverId);
    }

    public boolean loggingEnabled() {
        return loggingEnabled;
    }

    public boolean logBlockBreak() {
        return logBlockBreak;
    }

    public boolean logBlockPlace() {
        return logBlockPlace;
    }

    public boolean logContainerAccess() {
        return logContainerAccess;
    }

    public boolean logContainerInventory() {
        return logContainerInventory;
    }

    public boolean logEntityChange() {
        return logEntityChange;
    }

    public int maxRollbackRadius() {
        return maxRollbackRadius;
    }

    public int maxLookupLimit() {
        return maxLookupLimit;
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

    public int pruneDays() {
        return pruneDays;
    }

    public String serverId() {
        return serverId;
    }
}
