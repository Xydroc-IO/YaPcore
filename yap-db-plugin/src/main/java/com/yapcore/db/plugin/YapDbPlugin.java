package com.yapcore.db.plugin;

import com.yapcore.db.YapDb;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Owns the single shared Hikari pool for this JVM.
 */
public final class YapDbPlugin extends JavaPlugin implements YapDb {

    private HikariDataSource dataSource;
    private String jdbcUrl = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            openPool();
        } catch (Exception e) {
            getLogger().severe("Failed to open MariaDB/MySQL pool: " + e.getMessage());
            getLogger().severe("Start DB: ./scripts/db/start-mariadb.sh && ./scripts/db/configure-db.sh");
            getLogger().severe("Docs: docs/MARIADB.md · docs/YAPDB.md");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getServicesManager().register(YapDb.class, this, this, ServicePriority.Normal);
        var cmd = getCommand("yapdb");
        if (cmd != null) {
            YapDbCommand handler = new YapDbCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        getLogger().info("Shared YapDb pool ready (" + jdbcUrl + ")");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        closePool();
    }

    public void reloadPool() throws SQLException {
        reloadConfig();
        closePool();
        openPool();
        getServer().getServicesManager().unregister(YapDb.class, this);
        getServer().getServicesManager().register(YapDb.class, this, this, ServicePriority.Normal);
    }

    private void openPool() throws SQLException {
        var c = getConfig();
        jdbcUrl = c.getString("jdbc.url", "jdbc:mysql://127.0.0.1:3306/yap_playerdata");
        String user = c.getString("jdbc.user", "yap");
        String password = c.getString("jdbc.password", "change-me");
        String poolName = c.getString("pool.name", "YaPDB");
        int max = Math.max(1, c.getInt("pool.maximum-pool-size", 16));
        int minIdle = Math.max(0, c.getInt("pool.minimum-idle", 2));
        long timeout = Math.max(1000L, c.getLong("pool.connection-timeout-ms", 10_000L));

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        hc.setUsername(user);
        hc.setPassword(password);
        hc.setMaximumPoolSize(max);
        hc.setMinimumIdle(minIdle);
        hc.setConnectionTimeout(timeout);
        hc.setPoolName(poolName);
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        dataSource = new HikariDataSource(hc);

        // Fail fast if credentials/host are wrong
        try (Connection conn = dataSource.getConnection()) {
            conn.isValid(5);
        }
    }

    private void closePool() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    @Override
    public DataSource dataSource() {
        if (!isOpen()) {
            throw new IllegalStateException("YaPDB pool is not open");
        }
        return dataSource;
    }

    @Override
    public Connection connection() throws SQLException {
        if (!isOpen()) {
            throw new SQLException("YaPDB pool is not open");
        }
        return dataSource.getConnection();
    }

    @Override
    public boolean isOpen() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public String jdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public String poolName() {
        return dataSource != null ? dataSource.getPoolName() : "YaPDB";
    }
}
