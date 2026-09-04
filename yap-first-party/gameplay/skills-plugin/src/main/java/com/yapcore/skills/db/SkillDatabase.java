package com.yapcore.skills.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbEngine;
import com.yapcore.db.YapDbProvider;
import com.yapcore.db.YapSqlDialect;
import com.yapcore.db.YapSqlDialects;
import com.yapcore.skills.SkillsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
    private YapSqlDialect dialect = YapSqlDialects.mysql();

    public SkillDatabase(JavaPlugin plugin, SkillsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public YapSqlDialect dialect() {
        return dialect;
    }

    public void open() throws SQLException {
        if (config.useSharedYapdb()) {
            var found = YapDbProvider.find();
            if (found.isPresent()) {
                shared = found.get();
                dialect = shared.dialect();
                usingShared = true;
                migrate();
                plugin.getLogger().info("YaPSkills using shared YaPDB pool (" + dialect.engine() + ")");
                return;
            }
        }
        usingShared = false;
        dialect = YapSqlDialects.fromJdbcUrl(config.jdbcUrl());
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        if (dialect.engine() != YapDbEngine.SQLITE) {
            hc.setUsername(config.jdbcUser());
            hc.setPassword(config.jdbcPassword());
        }
        hc.setMaximumPoolSize(dialect.preferMaxPoolSize(config.poolMax()));
        hc.setMinimumIdle(dialect.engine() == YapDbEngine.SQLITE ? 1 : config.poolMin());
        hc.setConnectionTimeout(config.poolTimeoutMs());
        hc.setPoolName("YaPSkills");
        if (dialect.preferMysqlPrepStmtCache()) {
            hc.addDataSourceProperty("cachePrepStmts", "true");
            hc.addDataSourceProperty("prepStmtCacheSize", "250");
            hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }
        embedded = new HikariDataSource(hc);
        migrate();
        plugin.getLogger().warning("YaPSkills using embedded pool (" + dialect.engine()
                + ") — configure YaPDB for production");
    }

    public void migrate() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS yap_skill_progress (
                      player_uuid CHAR(36) NOT NULL,
                      skill_id VARCHAR(64) NOT NULL,
                      xp DOUBLE NOT NULL DEFAULT 0,
                      level INT NOT NULL DEFAULT 1,
                      %s,
                      PRIMARY KEY (player_uuid, skill_id)
                    )
                    """.formatted(dialect.timestampTouchColumn("updated_at")));
            try {
                st.execute("CREATE INDEX IF NOT EXISTS idx_skill_level ON yap_skill_progress (skill_id, level DESC)");
            } catch (SQLException ignored) {
            }
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
