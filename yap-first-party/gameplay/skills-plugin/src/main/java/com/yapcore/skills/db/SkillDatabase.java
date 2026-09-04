package com.yapcore.skills.db;

import com.yapcore.db.YapDb;
import com.yapcore.db.YapDbBootstrap;
import com.yapcore.db.YapDbEngine;
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
        YapDbBootstrap.Settings settings = new YapDbBootstrap.Settings(
                "YaPSkills",
                config.jdbcUrl(),
                config.jdbcUser(),
                config.jdbcPassword(),
                config.poolMax(),
                config.poolMin(),
                config.poolTimeoutMs(),
                config.useSharedYapdb(),
                true);
        var sharedOpt = YapDbBootstrap.openSharedOrEmpty(settings, YapDbBootstrap.warnTo(plugin.getLogger()));
        if (sharedOpt.isPresent()) {
            shared = sharedOpt.get();
            dialect = shared.dialect();
            usingShared = true;
            migrate();
            plugin.getLogger().info("YaPSkills using shared YaPDB pool (" + dialect.engine() + ")");
            return;
        }
        usingShared = false;
        HikariConfig hc = new HikariConfig();
        dialect = YapDbBootstrap.configureEmbedded(hc, settings);
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
