package com.yapcore.skills.db;

import com.yapcore.db.YapDb;
import com.yapcore.skills.SkillsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class SkillDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private final SkillsConfig config;
    private HikariDataSource embedded;
    private YapDb shared;
    private boolean usingShared;

    public SkillDatabase(JavaPlugin plugin, SkillsConfig config) {
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
                plugin.getLogger().info("YaPSkills using shared YaPDB pool");
                return;
            }
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        hc.setUsername(config.jdbcUser());
        hc.setPassword(config.jdbcPassword());
        hc.setMaximumPoolSize(config.poolMax());
        hc.setMinimumIdle(config.poolMin());
        hc.setConnectionTimeout(config.poolTimeoutMs());
        hc.setPoolName("YaPSkills");
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPSkills using embedded pool — configure YaPDB for production");
    }

    public void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_skill_progress (
                      player_uuid CHAR(36) NOT NULL,
                      skill_id VARCHAR(64) NOT NULL,
                      xp DOUBLE NOT NULL DEFAULT 0,
                      level INT NOT NULL DEFAULT 1,
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
                      PRIMARY KEY (player_uuid, skill_id),
                      INDEX idx_skill_level (skill_id, level DESC)
                    )
                    """);
        }
    }

    public Connection connection() throws SQLException {
        if (usingShared) {
            return shared.connection();
        }
        if (embedded == null) {
            throw new SQLException("YaPSkills pool not open");
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
