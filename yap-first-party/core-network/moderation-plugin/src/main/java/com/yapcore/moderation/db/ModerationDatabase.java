package com.yapcore.moderation.db;

import com.yapcore.db.YapDb;
import com.yapcore.moderation.ModerationConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class ModerationDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final ModerationConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;

    public ModerationDatabase(JavaPlugin plugin, ModerationConfig config) {
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
                plugin.getLogger().info("YaPModeration using shared YaPDB pool");
                return;
            }
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        hc.setUsername(config.jdbcUser());
        hc.setPassword(config.jdbcPassword());
        hc.setMaximumPoolSize(config.poolMax());
        hc.setMinimumIdle(config.poolMinIdle());
        hc.setConnectionTimeout(config.connectionTimeoutMs());
        hc.setPoolName("YaPModeration");
        embedded = new HikariDataSource(hc);
        migrate();
    }

    private void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_mod_punishments (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      type VARCHAR(16) NOT NULL,
                      target_uuid CHAR(36) NULL,
                      target_name VARCHAR(16) NOT NULL,
                      actor_uuid CHAR(36) NULL,
                      actor_name VARCHAR(16) NOT NULL,
                      reason VARCHAR(512) NOT NULL,
                      ip_address VARCHAR(45) NULL,
                      created_at BIGINT NOT NULL,
                      expires_at BIGINT NOT NULL DEFAULT 0,
                      active TINYINT(1) NOT NULL DEFAULT 1,
                      INDEX idx_target (target_uuid, active, type),
                      INDEX idx_ip (ip_address, active, type)
                    )
                    """);
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPModeration pool not open");
        }
        return embedded.getConnection();
    }

    @Override
    public void close() {
        if (embedded != null) {
            embedded.close();
            embedded = null;
        }
    }
}
