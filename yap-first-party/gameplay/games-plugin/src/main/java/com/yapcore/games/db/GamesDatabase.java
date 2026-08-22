package com.yapcore.games.db;

import com.yapcore.db.YapDb;
import com.yapcore.games.GamesConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class GamesDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final GamesConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;

    public GamesDatabase(JavaPlugin plugin, GamesConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open() throws SQLException {
        if (config.useSharedYapdb()) {
            var reg = Bukkit.getServicesManager().getRegistration(YapDb.class);
            if (reg != null && reg.getProvider().isOpen()) {
                shared = reg.getProvider();
                usingShared = true;
                migrate();
                plugin.getLogger().info("YaPGames using shared YaPDB pool");
                return;
            }
        }
        usingShared = false;
        if (config.jdbcUrl() == null || config.jdbcUrl().isBlank()) {
            plugin.getLogger().warning("YaPGames stats disabled — no JDBC URL");
            return;
        }
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        hc.setUsername(config.jdbcUser());
        hc.setPassword(config.jdbcPassword());
        hc.setMaximumPoolSize(config.poolMax());
        hc.setMinimumIdle(config.poolMin());
        hc.setConnectionTimeout(config.poolTimeoutMs());
        hc.setPoolName("YaPGames");
        embedded = new HikariDataSource(hc);
        migrate();
    }

    public void migrate() throws SQLException {
        if (!isOpen()) {
            return;
        }
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_games_stats (
                      player_uuid CHAR(36) NOT NULL,
                      mode_id VARCHAR(32) NOT NULL,
                      wins INT NOT NULL DEFAULT 0,
                      kills INT NOT NULL DEFAULT 0,
                      deaths INT NOT NULL DEFAULT 0,
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (player_uuid, mode_id),
                      INDEX idx_mode_wins (mode_id, wins DESC)
                    )
                    """);
        }
    }

    public boolean isOpen() {
        if (usingShared) {
            return shared != null && shared.isOpen();
        }
        return embedded != null && !embedded.isClosed();
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPGames pool not open");
        }
        return embedded.getConnection();
    }

    @Override
    public void close() {
        if (!usingShared && embedded != null) {
            embedded.close();
            embedded = null;
        }
    }
}
