package com.yapcore.protect.db;

import com.yapcore.db.YapDb;
import com.yapcore.protect.ProtectConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class ProtectDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final ProtectConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;

    public ProtectDatabase(JavaPlugin plugin, ProtectConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open() throws SQLException {
        if (config.useSharedYapDb()) {
            var reg = Bukkit.getServicesManager().getRegistration(YapDb.class);
            if (reg != null && reg.getProvider().isOpen()) {
                shared = reg.getProvider();
                usingShared = true;
                migrate();
                plugin.getLogger().info("YaPProtect using shared YaPDB pool");
                return;
            }
            plugin.getLogger().warning("use-shared-yapdb=true but YaPDB unavailable — embedded pool");
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        hc.setUsername(config.jdbcUser());
        hc.setPassword(config.jdbcPassword());
        hc.setMaximumPoolSize(config.poolMax());
        hc.setMinimumIdle(config.poolMinIdle());
        hc.setConnectionTimeout(config.connectionTimeoutMs());
        hc.setPoolName("YaPProtect");
        hc.addDataSourceProperty("cachePrepStmts", "true");
        embedded = new HikariDataSource(hc);
        migrate();
    }

    private void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_protect_changes (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      server_id VARCHAR(64) NOT NULL,
                      change_type VARCHAR(24) NOT NULL,
                      actor_uuid CHAR(36) NULL,
                      actor_name VARCHAR(32) NOT NULL,
                      world VARCHAR(64) NOT NULL,
                      x INT NOT NULL,
                      y INT NOT NULL,
                      z INT NOT NULL,
                      block_before VARCHAR(256) NOT NULL,
                      block_after VARCHAR(256) NOT NULL,
                      epoch_ms BIGINT NOT NULL,
                      rolled_back TINYINT(1) NOT NULL DEFAULT 0,
                      INDEX idx_actor (actor_uuid, epoch_ms),
                      INDEX idx_block (world, x, y, z, epoch_ms),
                      INDEX idx_epoch (epoch_ms)
                    )
                    """);
            widenPayloadColumns(st);
        }
    }

    private static void widenPayloadColumns(Statement st) throws SQLException {
        try {
            st.execute("""
                    ALTER TABLE yap_protect_changes
                      MODIFY block_before MEDIUMTEXT NOT NULL,
                      MODIFY block_after MEDIUMTEXT NOT NULL
                    """);
        } catch (SQLException ignored) {
            // already widened or unsupported DDL in test env
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        return embedded.getConnection();
    }

    public boolean isOpen() {
        return usingShared ? shared.isOpen() : embedded != null && !embedded.isClosed();
    }

    @Override
    public void close() {
        if (!usingShared && embedded != null) {
            embedded.close();
        }
    }
}
